package de.dimskiy.waypoints.platform.karooservices

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import de.dimskiy.waypoints.BuildConfig
import de.dimskiy.waypoints.R
import de.dimskiy.waypoints.platform.di.BaseModule
import de.dimskiy.waypoints.platform.offline.*
import de.dimskiy.waypoints.platform.ui.MainActivity
import de.dimskiy.waypoints.platform.ui.screens.poiCategoryIcon
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider

internal const val POI_FIELD_ID = "nearest-poi"
internal const val OPEN_POIS = "de.dimskiy.waypoints.OPEN_POIS"
private const val EXTENSION_ID = "io.github.roniakia.waypoints"

@AndroidEntryPoint
class PoiFieldExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {
    @Inject lateinit var store: Provider<RegionPackStore>
    @Inject lateinit var selectedPoi: SelectedPoiRepository
    @Inject @BaseModule.DispatcherIO lateinit var io: CoroutineDispatcher

    override val types: List<DataTypeImpl> by lazy { listOf(PoiFieldType(store, io), PoiFieldType(store, io, selectedPoi)) }
}

internal class PoiFieldType(
    private val store: Provider<RegionPackStore>,
    private val io: CoroutineDispatcher,
    private val selectedPoi: SelectedPoiRepository? = null,
) : DataTypeImpl(EXTENSION_ID, if (selectedPoi == null) POI_FIELD_ID else "selected-poi") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        fun render(label: String, icon: Int = R.drawable.ic_poi_pin, description: String = label) {
            val intent = Intent(context, MainActivity::class.java).setAction(OPEN_POIS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val click = PendingIntent.getActivity(context, 10, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val metrics = context.resources.displayMetrics
            val textPixels = minOf(22, config.textSize).coerceAtLeast(1) * metrics.scaledDensity
            val paint = android.graphics.Paint().apply {
                textSize = textPixels
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            // Leave room for the category icon, gap and root padding in narrow cells.
            val available = (config.viewSize.first - 28 * metrics.density).coerceAtLeast(1f)
            val fittedPixels = textPixels * minOf(1f, available / paint.measureText(label).coerceAtLeast(1f))
            val view = RemoteViews(context.packageName, R.layout.poi_data_field).apply {
                setTextViewText(R.id.poi_field_distance, label)
                setTextViewTextSize(R.id.poi_field_distance, android.util.TypedValue.COMPLEX_UNIT_PX, fittedPixels)
                setImageViewResource(R.id.poi_field_category, icon)
                setContentDescription(R.id.poi_field_root, description)
                setOnClickPendingIntent(R.id.poi_field_root, click)
            }
            emitter.updateView(view)
        }
        if (config.preview) {
            render("250m", R.drawable.ic_poi_water, "${if (selectedPoi == null) "Nearest" else "Selected"} POI, drinking water, 250 metres. Open POIs")
            return
        }
        // Each view owns its connection. Closing it cannot disconnect the app's shared service.
        val service = KarooSystemService(context)
        val fix = java.util.concurrent.atomic.AtomicReference<Pair<PoiPosition, Long>?>(null)
        val consumer = service.addConsumer<OnLocationChanged>(
            onError = { fix.set(null) },
            onEvent = { event ->
                runCatching { PoiPosition(event.lat, event.lng) }.getOrNull()?.let {
                    fix.set(it to SystemClock.elapsedRealtime())
                }
            })
        service.connect { connected -> if (!connected) fix.set(null) }
        val arrival = PoiArrivalTracker()
        val job = CoroutineScope(SupervisorJob() + io).launch {
            while (isActive) {
                try {
                    val currentFix = fix.get()?.takeIf { SystemClock.elapsedRealtime() - it.second < 30_000 }
                    val point = currentFix?.first
                    if (selectedPoi != null) {
                        val target = selectedPoi.selected.first()
                        val distance = if (target != null && point != null) poiDistanceMetres(point, target.position) else null
                        when {
                            target == null -> render(context.getString(R.string.poi_field_select))
                            arrival.arrived(target.token, distance, currentFix?.second) -> {
                                selectedPoi.clearIfSelected(target.token)
                                // Re-read on the next tick: an intervening selection always wins.
                                render(context.getString(R.string.poi_field_select))
                            }
                            point == null -> render(context.getString(R.string.poi_field_no_gps), poiCategoryIcon(target.categories))
                            else -> render(poiFieldDistance(distance), poiCategoryIcon(target.categories),
                                "${target.name}, ${poiFieldDistance(distance)}. Open POIs")
                        }
                        delay(5_000)
                        continue
                    }
                    val packs = store.get()
                    when {
                        packs.installed().isEmpty() -> render(context.getString(R.string.poi_field_no_pois))
                        point == null -> render(context.getString(R.string.poi_field_no_gps))
                        else -> {
                            val poi = packs.search(PoiSearch(position = point, pageSize = 1)) { ensureActive() }.items.firstOrNull()
                            if (poi == null) render(context.getString(R.string.poi_field_no_pois))
                            else render(poiFieldDistance(poi.distanceMetres), poiCategoryIcon(poi.categories),
                                "${poi.name}, ${poiFieldDistance(poi.distanceMetres)}. Open POIs")
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { render(context.getString(R.string.poi_field_unavailable)) }
                delay(5_000)
            }
        }
        emitter.setCancellable {
            job.cancel()
            service.removeConsumer(consumer)
            service.disconnect()
        }
    }
}

internal fun poiFieldDistance(metres: Double?): String = when {
    metres == null || !metres.isFinite() || metres < 0 -> "—"
    metres < 1000 -> "${metres.toInt()}m"
    else -> "${"%.1f".format(java.util.Locale.ROOT, metres / 1000)}km"
}
