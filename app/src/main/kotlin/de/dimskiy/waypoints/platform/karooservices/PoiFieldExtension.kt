package de.dimskiy.waypoints.platform.karooservices

import android.app.PendingIntent
import android.widget.RemoteViews
import android.view.View
import de.dimskiy.waypoints.platform.ui.screens.poiCategoryIcon
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import de.dimskiy.waypoints.BuildConfig
import de.dimskiy.waypoints.R
import de.dimskiy.waypoints.platform.di.BaseModule
import de.dimskiy.waypoints.platform.offline.*
import de.dimskiy.waypoints.platform.ui.MainActivity
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider

internal const val POI_FIELD_ID = "nearest-poi"
internal const val OPEN_POIS = "de.dimskiy.waypoints.OPEN_POIS"
internal const val OPEN_POIS_ACTION_ID = "open-pois"
// KarooExtension rejects dotted IDs; this is deliberately distinct from the app ID.
internal const val EXTENSION_ID = "waypoints"

internal fun poiListIntent(context: Context) = Intent(context, MainActivity::class.java)
    .setAction(OPEN_POIS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@AndroidEntryPoint
class PoiFieldExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {
    @Inject lateinit var store: Provider<RegionPackStore>
    @Inject lateinit var selectedPoi: SelectedPoiRepository
    @Inject lateinit var locations: PoiFieldLocations
    @Inject @BaseModule.DispatcherIO lateinit var io: CoroutineDispatcher

    override val types: List<DataTypeImpl> by lazy {
        PoiFieldPresentation.entries.flatMap { presentation ->
            listOf(
                PoiFieldType(store, io, locations::observe, presentation = presentation),
                PoiFieldType(store, io, locations::observe, selectedPoi, presentation = presentation),
            )
        }
    }

    override fun onBonusAction(actionId: String) {
        if (actionId == OPEN_POIS_ACTION_ID) startActivity(poiListIntent(this))
    }
}

internal data class PoiFieldFix(val position: PoiPosition, val elapsedMillis: Long)

/** Each stream owns its connection; failure emits an explicit missing fix. */
class PoiFieldLocations @Inject constructor(@ApplicationContext private val context: Context) {
    internal fun observe(): Flow<PoiFieldFix?> = callbackFlow {
        trySend(null)
        val service = KarooSystemService(context)
        val consumer = service.addConsumer<OnLocationChanged>(
            onError = { Timber.w("POI field location service: %s", it); trySend(null) },
            onEvent = { event ->
                val point = runCatching { PoiPosition(event.lat, event.lng) }.getOrNull()
                trySend(point?.let { PoiFieldFix(it, SystemClock.elapsedRealtime()) })
            })
        try {
            service.connect { connected -> if (!connected) trySend(null) }
        } catch (failure: Exception) {
            Timber.w(failure, "POI field could not bind Karoo services")
        }
        awaitClose {
            service.removeConsumer(consumer)
            runCatching { service.disconnect() }.onFailure { Timber.w(it, "POI field disconnect failed") }
        }
    }.conflate()
}

internal data class PoiFieldReading(
    val distanceMetres: Double? = null,
    val message: Int? = R.string.poi_field_no_gps,
    val name: String? = null,
    val categories: Set<String> = emptySet(),
) {
    fun streamState(dataTypeId: String): StreamState = distanceMetres
        ?.takeIf { it.isFinite() && it >= 0 }
        ?.let { StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to it))) }
        ?: StreamState.NotAvailable
}

internal enum class PoiFieldPresentation(val suffix: String) {
    NATIVE(""), GRAPHICAL("-tappable"), EXPERIMENTAL_COMPACT("-compact-tappable")
}

/** Explicit activity PendingIntent works without a broadcast/service launch trampoline. */
internal fun tappablePoiView(
    context: Context, reading: PoiFieldReading, preview: Boolean,
    selected: Boolean = false, compact: Boolean = false,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.poi_field_tappable).apply {
        val title = context.getString(if (selected) R.string.poi_field_selected_name else R.string.poi_field_name)
        setTextViewText(R.id.poi_field_title, title)
        val text = reading.message?.let(context::getString) ?: reading.distanceMetres?.let {
            if (it < 1000) "${it.toInt()} m" else String.format(java.util.Locale.getDefault(), "%.1f km", it / 1000)
        } ?: context.getString(R.string.poi_field_unavailable)
        val category = PoiTypes.labels.keys.firstOrNull { it in reading.categories }?.let(PoiTypes::label)
        val name = reading.name?.ifBlank { category ?: "POI" }
        setTextViewText(R.id.poi_field_name, name.orEmpty())
        setViewVisibility(R.id.poi_field_name, if (name == null) View.GONE else View.VISIBLE)
        setImageViewResource(R.id.poi_field_category, poiCategoryIcon(reading.categories))
        setViewVisibility(R.id.poi_field_category, if (name == null) View.GONE else View.VISIBLE)
        setTextViewText(R.id.poi_field_distance, text)
        setTextViewTextSize(R.id.poi_field_distance, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (compact) 16f else if (reading.message == null) 24f else 20f)
        setContentDescription(R.id.poi_field_root,
            listOfNotNull(title, name, category, text, context.getString(R.string.poi_field_open)).joinToString(", "))
        if (!preview) setOnClickPendingIntent(R.id.poi_field_root,
            PendingIntent.getActivity(context, 100, poiListIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

internal class PoiFieldType(
    private val store: Provider<RegionPackStore>,
    private val io: CoroutineDispatcher,
    private val locations: () -> Flow<PoiFieldFix?>,
    private val selectedPoi: SelectedPoiRepository? = null,
    private val elapsedTime: () -> Long = SystemClock::elapsedRealtime,
    private val presentation: PoiFieldPresentation = PoiFieldPresentation.NATIVE,
) : DataTypeImpl(EXTENSION_ID, (if (selectedPoi == null) POI_FIELD_ID else "selected-poi") + presentation.suffix) {
    private val reading = MutableStateFlow(PoiFieldReading())

    override fun startStream(emitter: Emitter<StreamState>) {
        // The host receives a state even if binding or GPS never succeeds.
        emitter.onNext(StreamState.NotAvailable)
        val job = observeReadings { emitter.onNext(it) }
        emitter.setCancellable { job.cancel() }
    }

    private fun observeReadings(emit: (StreamState) -> Unit): Job = CoroutineScope(io).launch {
        coroutineScope {
            val fix = AtomicReference<PoiFieldFix?>(null)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                locations().catch { failure ->
                    if (failure is CancellationException) throw failure
                    Timber.w(failure, "POI field location stream failed")
                    emit(null)
                }.collect { fix.set(it) }
            }
            val arrival = PoiArrivalTracker()
            try {
                while (isActive) {
                    val result = try {
                        val current = fix.get()?.takeIf { elapsedTime() - it.elapsedMillis in 0 until 30_000 }
                        val point = current?.position
                        if (selectedPoi != null) {
                            val target = selectedPoi.selected.first()
                            val distance = if (target != null && point != null) poiDistanceMetres(point, target.position) else null
                            when {
                                target == null -> PoiFieldReading(message = R.string.poi_field_select)
                                arrival.arrived(target.token, distance, current?.elapsedMillis) -> {
                                    selectedPoi.clearIfSelected(target.token)
                                    // Re-read after the atomic clear: a newer selection always wins.
                                    val replacement = selectedPoi.selected.first()
                                    if (replacement == null) PoiFieldReading(message = R.string.poi_field_select)
                                    else selectedReading(replacement, point)
                                }
                                else -> selectedReading(target, point)
                            }
                        } else {
                            val packs = store.get()
                            when {
                                packs.installed().isEmpty() -> PoiFieldReading(message = R.string.poi_field_no_pois)
                                point == null -> PoiFieldReading()
                                else -> packs.search(PoiSearch(position = point, pageSize = 1)) { ensureActive() }
                                    .items.firstOrNull()?.let { PoiFieldReading(it.distanceMetres, null, it.name, it.categories) }
                                    ?: PoiFieldReading(message = R.string.poi_field_no_pois)
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        Timber.w(failure, "POI field refresh failed")
                        PoiFieldReading(message = R.string.poi_field_unavailable)
                    }
                    reading.value = result
                    emit(result.streamState(dataTypeId))
                    delay(5_000)
                }
            } finally { collector.cancel() }
        }
    }

    private fun selectedReading(target: SelectedPoi, point: PoiPosition?): PoiFieldReading =
        if (point == null) PoiFieldReading(name = target.name, categories = target.categories)
        else PoiFieldReading(poiDistanceMetres(point, target.position), null, target.name, target.categories)

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (presentation != PoiFieldPresentation.NATIVE) {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            // Compact custom rendering on a numeric registration is deliberately experimental.
            // Karoo controls whether it accepts this RemoteViews and its click target.
            emitter.updateView(tappablePoiView(context, reading.value, config.preview,
                selected = selectedPoi != null, compact = presentation == PoiFieldPresentation.EXPERIMENTAL_COMPACT))
            // A graphical view is not guaranteed to subscribe to its numeric stream.
            // Own the reading job here so GPS/search and arrival work without a native field.
            val streamJob = if (config.preview) null else observeReadings { }
            val job = CoroutineScope(io).launch {
                reading.collect {
                    // The real SDK drops RemoteViews updates less than 900ms apart.
                    // Keep the newest state, including a fast initial Select POI/No POIs.
                    delay(1_000)
                    emitter.updateView(tappablePoiView(context, reading.value, config.preview,
                        selected = selectedPoi != null, compact = presentation == PoiFieldPresentation.EXPERIMENTAL_COMPACT))
                }
            }
            emitter.setCancellable { job.cancel(); streamJob?.cancel() }
            return
        }
        emitter.onNext(UpdateNumericConfig(DataType.Type.DISTANCE))
        val job = CoroutineScope(io).launch {
            reading.map { it.message }.distinctUntilChanged().collect { message ->
                emitter.onNext(ShowCustomStreamState(message?.let(context::getString), null))
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
