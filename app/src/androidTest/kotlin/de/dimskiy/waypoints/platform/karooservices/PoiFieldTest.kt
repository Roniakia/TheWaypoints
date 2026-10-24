package de.dimskiy.waypoints.platform.karooservices

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import de.dimskiy.waypoints.R
import de.dimskiy.waypoints.platform.offline.*
import de.dimskiy.waypoints.platform.ui.MainActivity
import io.hammerhead.karooext.internal.*
import io.hammerhead.karooext.models.*
import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PoiFieldTest {
    @Test fun nativeAndTappableVariantsAreRegisteredAndOpenPoisActionExists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = context.packageManager.queryIntentServices(
            Intent("io.hammerhead.karooext.KAROO_EXTENSION").setPackage(context.packageName), PackageManager.GET_META_DATA)
        val service = services.single().serviceInfo
        assertTrue(service.exported)
        assertEquals(R.xml.karoo_extension_info, service.metaData.getInt("io.hammerhead.karooext.EXTENSION_INFO"))
        val ids = mutableSetOf<String>();val actions = mutableSetOf<String>()
        context.resources.getXml(R.xml.karoo_extension_info).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "DataType") {
                    val id = xml.getAttributeValue(null, "typeId")
                    assertEquals(if (id.endsWith("-tappable") && !id.endsWith("-compact-tappable")) "true" else "false",
                        xml.getAttributeValue(null, "graphical"))
                    ids += xml.getAttributeValue(null, "typeId")
                }
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "BonusAction")
                    actions += xml.getAttributeValue(null, "actionId")
                xml.next()
            }
        }
        assertEquals(setOf("nearest-poi", "selected-poi", "nearest-poi-tappable", "selected-poi-tappable",
            "nearest-poi-compact-tappable", "selected-poi-compact-tappable"), ids)
        assertEquals(setOf(OPEN_POIS_ACTION_ID), actions)
    }

    @Test fun nativeFieldFormatsAsDistanceAndDisplaysMissingGpsWithoutRemoteViews() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val emitter = mockk<ViewEmitter>(relaxed = true)
        val cancel = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancel)) } just Runs
        val field = PoiFieldType(Provider { error("View configuration must not read packs") },
            StandardTestDispatcher(testScheduler), { flowOf(null) })
        field.startView(context, ViewConfig(30 to 10, 240 to 100, 22), emitter)
        runCurrent()
        verify { emitter.onNext(UpdateNumericConfig(DataType.Type.DISTANCE)) }
        verify { emitter.onNext(ShowCustomStreamState(context.getString(R.string.poi_field_no_gps), null)) }
        verify(exactly = 0) { emitter.updateView(any()) }
        cancel.captured(); advanceUntilIdle()
    }

    @Test fun selectedFieldCanPromptSelectionWithoutGpsAndStopsAfterCancellation() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = mockk<SelectedPoiRepository>()
        every { repo.selected } returns flowOf(null)
        val field = PoiFieldType(Provider { error("Selected field must not read packs") },
            StandardTestDispatcher(testScheduler), { flowOf(null) }, repo)
        val stream = mockk<Emitter<StreamState>>(relaxed = true)
        val view = mockk<ViewEmitter>(relaxed = true)
        val cancelStream = slot<() -> Unit>();val cancelView = slot<() -> Unit>()
        every { stream.setCancellable(capture(cancelStream)) } just Runs
        every { view.setCancellable(capture(cancelView)) } just Runs
        field.startView(context, ViewConfig(30 to 10, 240 to 100, 22), view)
        field.startStream(stream)
        runCurrent()
        verify { view.onNext(ShowCustomStreamState(context.getString(R.string.poi_field_select), null)) }
        cancelStream.captured();cancelView.captured();advanceUntilIdle()
        verify(exactly = 2) { stream.onNext(any()) }
    }

    @Test fun customViewsRefreshSelectedTargetWithoutNativeSubscriptionAndCancel() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (presentation in listOf(PoiFieldPresentation.GRAPHICAL, PoiFieldPresentation.EXPERIMENTAL_COMPACT)) {
            val repo = mockk<SelectedPoiRepository>()
            every { repo.selected } returns flowOf(null)
            var locationStopped = false
            val field = PoiFieldType(Provider { error("Selected view must not read packs") },
                StandardTestDispatcher(testScheduler), {
                    flow<PoiFieldFix?> {
                        try { emit(null); kotlinx.coroutines.awaitCancellation() }
                        finally { locationStopped = true }
                    }
                }, repo, presentation = presentation)
            val view = mockk<ViewEmitter>(relaxed = true)
            val cancel = slot<() -> Unit>()
            val rendered = mutableListOf<android.widget.RemoteViews>()
            every { view.setCancellable(capture(cancel)) } just Runs
            every { view.updateView(capture(rendered)) } just Runs
            field.startView(context, ViewConfig(1 to 1, 120 to 80, 22), view)
            runCurrent()
            val tile = rendered.last().apply(context, null)
            assertEquals(context.getString(R.string.poi_field_select),
                tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
            cancel.captured(); advanceUntilIdle()
            assertTrue(locationStopped)
            val updates = rendered.size
            advanceTimeBy(30_000); runCurrent()
            assertEquals(updates, rendered.size)
        }
    }

    @Test fun renderedTileLaunchesPoiListWithoutGpsButPreviewDoesNot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                val preview = tappablePoiView(context, PoiFieldReading(), true).apply(context, null)
                assertFalse(preview.findViewById<android.view.View>(R.id.poi_field_root).performClick())
                val tile = tappablePoiView(context, PoiFieldReading(), false).apply(context, null)
                assertEquals(context.getString(R.string.poi_field_no_gps),
                    tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
                assertTrue(tile.findViewById<android.view.View>(R.id.poi_field_root).performClick())
            }
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull(activity)
            assertEquals(OPEN_POIS, activity.intent.action)
            instrumentation.runOnMainSync { activity.finish() }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun controllerActionIntentOpensPoiList() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            context.startActivity(poiListIntent(context))
            val activity = instrumentation.waitForMonitorWithTimeout(monitor, 10_000)
            assertNotNull(activity)
            assertEquals(OPEN_POIS, activity.intent.action)
            instrumentation.runOnMainSync { activity.finish() }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
