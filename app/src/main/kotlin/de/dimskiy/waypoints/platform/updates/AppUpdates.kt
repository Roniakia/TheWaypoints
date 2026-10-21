package de.dimskiy.waypoints.platform.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import de.dimskiy.waypoints.BuildConfig
import de.dimskiy.waypoints.platform.di.BaseModule
import de.dimskiy.waypoints.platform.offline.HttpsPackTransport
import de.dimskiy.waypoints.platform.offline.PackTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppUpdates internal constructor(private val context: Context,
    private val preferences: DataStore<Preferences>, private val io: CoroutineDispatcher,
    private val clock: Provider<Long>, private val source: UpdateSource, private val transport: PackTransport) {
    @Inject constructor(@ApplicationContext context: Context, preferences: DataStore<Preferences>,
        @BaseModule.DispatcherIO io: CoroutineDispatcher, @BaseModule.TimeProvider clock: Provider<Long>) :
        this(context, preferences, io, clock, UpdateSource(), HttpsPackTransport())
    private val channelKey = stringPreferencesKey("appUpdateChannel")
    private val checkedKey = longPreferencesKey("appUpdateAttemptMillis")
    private val successKey = longPreferencesKey("appUpdateSuccessMillis")
    suspend fun channel() = UpdateChannel.values().firstOrNull { it.name == preferences.data.first()[channelKey] } ?: UpdateChannel.STABLE
    suspend fun channel(value: UpdateChannel) { preferences.edit { it[channelKey] = value.name; it.remove(checkedKey) } }
    suspend fun lastChecked() = preferences.data.first()[successKey]
    suspend fun check(force: Boolean): Pair<Boolean, AppRelease?> = withContext(io) {
        val now = clock.get()
        val previous = preferences.data.first()[checkedKey]
        if (!force && previous != null && now >= previous && now - previous < 86_400_000) return@withContext false to null
        preferences.edit { it[checkedKey] = now }
        val job = currentCoroutineContext()
        val candidate = source.latest(channel(), BuildConfig.APPLICATION_ID.removeSuffix(".local")) { job.ensureActive() }
        preferences.edit { it[successKey] = now }
        true to candidate?.takeIf { it.code > BuildConfig.VERSION_CODE }
    }
    suspend fun download(release: AppRelease, progress: (Int) -> Unit): File = withContext(io) {
        check(!BuildConfig.APPLICATION_ID.endsWith(".local")) { "Local test builds cannot install production updates." }
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        // Each attempt owns its file so cancellation cannot remove a subsequent download.
        directory.listFiles()?.filter { clock.get() - it.lastModified() > 7 * 86_400_000L }?.forEach { it.delete() }
        val file = File.createTempFile("candidate-", ".apk", directory)
        try {
            val job = currentCoroutineContext()
            transport.download(release.url, file, release.bytes,
                { progress((it * 100 / release.bytes).toInt()) }, { job.ensureActive() })
            verifyDigest(file, release) { job.ensureActive() }
            validate(file, release)
            file
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    suspend fun verify(file: File, release: AppRelease) = withContext(io) {
        val job = currentCoroutineContext()
        verifyDigest(file, release) { job.ensureActive() }
        validate(file, release)
    }

    private fun verifyDigest(file: File, release: AppRelease, check: () -> Unit) {
        require(file.length() == release.bytes) { "APK size does not match" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { check(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "APK checksum does not match" }
    }

    @Suppress("DEPRECATION")
    private fun validate(file: File, release: AppRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) apk.longVersionCode else apk.versionCode.toLong()
        require(apk.packageName == context.packageName && code == release.code && code > BuildConfig.VERSION_CODE && apk.versionName == release.version) { "APK identity/version does not match" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return values.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        val expected = signatures(installed)
        require(expected.isNotEmpty() && signatures(apk) == expected) { "APK signing certificate does not match the installed app" }
    }
}
