package de.dimskiy.waypoints.platform.karooservices

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.dimskiy.waypoints.DataResult
import de.dimskiy.waypoints.R
import de.dimskiy.waypoints.domain.model.DeviceLocation
import de.dimskiy.waypoints.domain.model.DomainError
import de.dimskiy.waypoints.domain.providers.LocationsProvider
import de.dimskiy.waypoints.platform.di.BaseModule
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnLocationChanged
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Duration.Companion.seconds

private const val GPS_SEARCH_TIMEOUT_SEC = 10

class KarooLocationsProvider @Inject constructor(
    private val karooServiceProvider: KarooServiceProvider,
    @ApplicationContext private val context: Context,
    @BaseModule.DispatcherDefault private val coroutineDispatcher: CoroutineDispatcher,
    @BaseModule.TimeProvider private val currentTimeProvider: Provider<Long>
) : LocationsProvider {

    private val lastLocationCache = MutableStateFlow<DataResult<DeviceLocation>>(
        DataResult.loading(context.getString(R.string.message_finding_the_location))
    )

    override fun observeDeviceLocations(): Flow<DataResult<DeviceLocation>> = channelFlow {
        withContext(coroutineDispatcher) {
            val result = withTimeoutOrNull(GPS_SEARCH_TIMEOUT_SEC.seconds) {
                var location: DataResult<DeviceLocation>? = null
                karooServiceProvider.ensureConnected { service ->
                    location = getLocation(service)
                }
                location
            }
            send(result ?: getCachedResultOrError())
        }
        awaitCancellation()
    }.onStart {
        emit(DataResult.loading(context.getString(R.string.message_finding_the_location)))
    }

    private suspend fun getLocation(service: KarooSystemService): DataResult<DeviceLocation> {
        Timber.d("Starting device location discovery")

        val locationResult = getCurrentLocationResult(service).mapResult {
            DeviceLocation(
                latitude = it.lat,
                longitude = it.lng,
                timestamp = currentTimeProvider.get()
            )
        }
        lastLocationCache.emit(locationResult)
        Timber.d("Location cache update: $locationResult")
        return locationResult
    }

    private suspend fun getCurrentLocationResult(service: KarooSystemService): DataResult<OnLocationChanged> {
        return locationEvents(register = { emit ->
            service.addConsumer<OnLocationChanged>(
                onError = {
                    emit(
                        DataResult.error<OnLocationChanged>(
                            DomainError.LocationServiceError(message = it)
                        )
                    )
                },
                onEvent = { location ->
                    Timber.d("Location discovered: $location")
                    emit(DataResult.ready(location))
                }
            )
        }, remove = service::removeConsumer).first()
    }

    private fun getCachedResultOrError(): DataResult<DeviceLocation> = lastLocationCache.value
        .takeIf(DataResult<*>::isReady)
        ?.let { cachedLocation ->
            Timber.d("Using cached location: $cachedLocation")
            cachedLocation
        }
        ?: DataResult.error(
            DomainError.LocationServiceError(
                message = context.getString(R.string.error_msg_no_location)
            )
        )
}

internal fun locationEvents(register: ((DataResult<OnLocationChanged>) -> Unit) -> String,
    remove: (String) -> Unit): Flow<DataResult<OnLocationChanged>> = callbackFlow {
    val id = register { trySend(it) }
    awaitClose { remove(id) }
}
