package de.dimskiy.waypoints.platform.karooservices

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import de.dimskiy.waypoints.R
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import de.dimskiy.waypoints.platform.offline.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PoiFieldTest {
    @Test fun previewFitsSmallCellWithoutOpeningPacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val emitter = mockk<ViewEmitter>(relaxed = true)
        val remote = slot<RemoteViews>()
        every { emitter.updateView(capture(remote)) } just Runs
        PoiFieldType(javax.inject.Provider { error("Preview must not open storage") }, Dispatchers.IO)
            .startView(context, ViewConfig(20 to 10, 160 to 100, 22, preview = true), emitter)
        verify { emitter.onNext(UpdateGraphicConfig(showHeader = false)) }
        instrumentation.runOnMainSync {
            val view = remote.captured.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 160, 100)
            val distance = view.findViewById<TextView>(R.id.poi_field_distance)
            assertEquals("250m", distance.text.toString())
            assertEquals(0, distance.layout.getEllipsisCount(0))
            assertTrue(view.isClickable)
            val image = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            File(context.cacheDir, "poi-field-preview.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun tappingFieldLaunchesPoisAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val emitter = mockk<ViewEmitter>(relaxed = true)
        val remote = slot<RemoteViews>()
        every { emitter.updateView(capture(remote)) } just Runs
        PoiFieldType(javax.inject.Provider { error("Preview must not open storage") }, Dispatchers.IO)
            .startView(context, ViewConfig(20 to 10, 160 to 100, 22, preview = true), emitter)
        val monitor = instrumentation.addMonitor(
            de.dimskiy.waypoints.platform.ui.MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                assertTrue(remote.captured.apply(context, FrameLayout(context)).performClick())
            }
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull(activity)
            assertEquals(OPEN_POIS, activity.intent.action)
            instrumentation.runOnMainSync { activity.finish() }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun selectedFieldShowsEmptyOrMissingGpsWithoutOpeningPacksAndStopsOnCancel() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val selected = SelectedPoi("selection", "Water", PoiPosition(55.6, 13.0), setOf("water.drinking_water"))
        for ((target, expected) in listOf(null to "Select POI", selected to "No GPS")) {
            val repository = mockk<SelectedPoiRepository>()
            every { repository.selected } returns flowOf(target)
            val emitter = mockk<ViewEmitter>(relaxed = true)
            val remote = slot<RemoteViews>()
            val cancel = slot<() -> Unit>()
            every { emitter.updateView(capture(remote)) } just Runs
            every { emitter.setCancellable(capture(cancel)) } just Runs
            val field = PoiFieldType(javax.inject.Provider { error("Selected field must not open packs") },
                StandardTestDispatcher(testScheduler), repository)
            assertTrue(field.dataTypeId.endsWith("selected-poi"))
            field.startView(context, ViewConfig(20 to 10, 160 to 100, 22), emitter)
            runCurrent()
            instrumentation.runOnMainSync {
                val view = remote.captured.apply(context, FrameLayout(context))
                assertEquals(expected, view.findViewById<TextView>(R.id.poi_field_distance).text.toString())
            }
            cancel.captured.invoke()
            advanceUntilIdle()
            verify(exactly = 1) { emitter.updateView(any()) }
        }
    }

    @Test fun selectedPreviewDoesNotReadStoredTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = mockk<SelectedPoiRepository>()
        val emitter = mockk<ViewEmitter>(relaxed = true)
        PoiFieldType(javax.inject.Provider { error("Preview must not open packs") }, Dispatchers.IO, repository)
            .startView(context, ViewConfig(20 to 10, 160 to 100, 22, preview = true), emitter)
        verify(exactly = 1) { emitter.updateView(any()) }
        verify { repository wasNot Called }
    }

    @Test fun extensionServiceIsDiscoverable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = context.packageManager.queryIntentServices(
            android.content.Intent("io.hammerhead.karooext.KAROO_EXTENSION").setPackage(context.packageName),
            android.content.pm.PackageManager.GET_META_DATA)
        val service = services.single().serviceInfo
        assertTrue(service.exported)
        assertEquals(R.xml.karoo_extension_info,
            service.metaData.getInt("io.hammerhead.karooext.EXTENSION_INFO"))
    }
}
