package de.dimskiy.waypoints.platform.offline

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.dimskiy.waypoints.platform.di.BaseModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineWorkerServices {
    fun catalogue(): PoiCatalogueRepository
    fun store(): RegionPackStore
    fun transport(): PackTransport
    fun downloads(): RegionDownloads
    @BaseModule.DispatcherIO fun ioDispatcher(): CoroutineDispatcher
}

class RegionDownloadWorker @JvmOverloads constructor(context: Context, parameters: WorkerParameters,
    private val servicesOverride: OfflineWorkerServices? = null) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val services = servicesOverride ?: EntryPointAccessors.fromApplication(applicationContext, OfflineWorkerServices::class.java)
        return withContext(services.ioDispatcher()) {
            val coroutine = currentCoroutineContext()
            val id = inputData.getString(REGION_ID) ?: return@withContext Result.failure()
            val generation = services.downloads().token(id)
            val checkCancelled = {
                coroutine.ensureActive()
                if (services.downloads().token(id) != generation) throw kotlinx.coroutines.CancellationException("Download cancelled")
            }
            var partial: File? = null
            try {
                val region = services.catalogue().load().regions.single { it.descriptor.id == id }
                val descriptor = region.descriptor
                if (regionsToUpdate(VerifiedCatalogue(listOf(region), ""), services.store().installed(), setOf(id)).isEmpty())
                    return@withContext Result.success()
                val folder = services.downloads().directory.apply { check(isDirectory || mkdirs()) }
                // Revision/hash changes get a separate partial file; no stale bytes are appended.
                val file = File(folder, "$id-${descriptor.sha256}.part").also { partial = it }
                folder.listFiles().orEmpty().filter { it.name.startsWith("$id-") && it != file }.forEach { it.delete() }
                require(folder.usableSpace >= descriptor.databaseBytes + (descriptor.downloadBytes - file.length()).coerceAtLeast(0) + 1024 * 1024) {
                    "Not enough free space for ${region.name}"
                }
                var lastPercent = -1
                services.transport().download(region.url, file, descriptor.downloadBytes, { bytes ->
                    val percent = (bytes * 100 / descriptor.downloadBytes).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        setProgressAsync(workDataOf("percent" to percent, "phase" to "download"))
                    }
                }, checkCancelled)
                setProgressAsync(workDataOf("percent" to 100, "phase" to "verify"))
                services.store().install(file, descriptor, checkCancelled)
                file.delete()
                Result.success()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                if (services.downloads().token(id) != generation) partial?.delete()
                throw cancelled
            }
            catch (network: IOException) {
                // The system may stop work/processes; immutable partials are resumed on retry.
                if (runAttemptCount < 4) Result.retry()
                else Result.failure(workDataOf("error" to "Download interrupted. Tap Update selected to retry."))
            } catch (failure: Exception) {
                timber.log.Timber.w(failure, "Offline region installation failed")
                partial?.delete() // Bad hashes, ranges, metadata or SQLite never become installed data.
                Result.failure(workDataOf("error" to (failure.message ?: "Region could not be installed")))
            }
        }
    }

    companion object {
        const val REGION_ID = "regionId"
        const val TAG = "offline-region-download"
        fun name(id: String) = "offline-region-$id"
    }
}

class RegionDownloads(private val work: WorkManager, internal val directory: File, private val clock: () -> Long) {
    private val generations = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    internal fun token(id: String): Long = generations.getOrPut(id) { java.util.concurrent.atomic.AtomicLong() }.get()
    fun enqueue(regions: List<CatalogueRegion>) {
        regions.forEach { region ->
            val id = region.descriptor.id
            val request = OneTimeWorkRequestBuilder<RegionDownloadWorker>()
                .setInputData(workDataOf(RegionDownloadWorker.REGION_ID to id))
                .addTag(RegionDownloadWorker.TAG).addTag("region:$id")
                .addTag("queued:${clock()}")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            work.enqueueUniqueWork(RegionDownloadWorker.name(id), ExistingWorkPolicy.KEEP, request)
        }
    }
    fun observe() = work.getWorkInfosByTagFlow(RegionDownloadWorker.TAG)
    fun removePartials(id: String) { directory.listFiles().orEmpty().filter { it.name.startsWith("$id-") }.forEach { it.delete() } }
    fun cancel(id: String): Operation {
        generations.getOrPut(id) { java.util.concurrent.atomic.AtomicLong() }.incrementAndGet()
        return work.cancelUniqueWork(RegionDownloadWorker.name(id))
    }
}
