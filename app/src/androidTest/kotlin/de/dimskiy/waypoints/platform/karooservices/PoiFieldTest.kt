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
    @Test fun extensionServiceCanBeConstructedWithRegisteredId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = PoiFieldExtension()
        context.resources.getXml(R.xml.karoo_extension_info).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) xml.next()
            assertEquals(xml.getAttributeValue(null, "id"), service.extension)
        }
        assertFalse(service.extension.contains('.'))
    }

    @Test fun manifestServiceBindsAndDispatchesEveryRegisteredView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = java.util.concurrent.CountDownLatch(1)
        var binder: android.os.IBinder? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, service: android.os.IBinder) {
                binder = service; connected.countDown()
            }
            override fun onServiceDisconnected(name: android.content.ComponentName) = Unit
        }
        val bound = context.bindService(Intent(context, PoiFieldExtension::class.java), connection,
            android.content.Context.BIND_AUTO_CREATE)
        assertTrue(bound)
        try {
            assertTrue("Extension service never connected", connected.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val extension = io.hammerhead.karooext.aidl.IKarooExtension.Stub.asInterface(binder)
            assertFalse(extension.libVersion().isNullOrEmpty())
            val ids = mutableListOf<String>()
            context.resources.getXml(R.xml.karoo_extension_info).use { xml ->
                while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "DataType")
                        ids += xml.getAttributeValue(null, "typeId")
                    xml.next()
                }
            }
            for (id in ids) {
                val event = java.util.concurrent.CountDownLatch(1)
                val views = java.util.concurrent.CopyOnWriteArrayList<android.widget.RemoteViews>()
                val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
                val handler = object : io.hammerhead.karooext.aidl.IHandler.Stub() {
                    override fun onNext(bundle: android.os.Bundle) {
                        @Suppress("DEPRECATION")
                        val view = bundle.getParcelable<android.widget.RemoteViews>("view")
                        if (view != null) views += view
                        if (!id.endsWith("-tappable") || view != null) event.countDown()
                    }
                    override fun onError(error: String) { errors += error; event.countDown() }
                    override fun onComplete() = Unit
                }
                val request = "binding-test-$id"
                try {
                    extension.startView(request, id,
                        ViewConfig(1 to 1, 120 to 80, 22, preview = true).bundleWithSerializable(context.packageName), handler)
                    assertTrue("No view callback for $id", event.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertTrue("$id errors: $errors", errors.isEmpty())
                    if (id.endsWith("-tappable")) {
                        assertEquals(1, views.size)
                        val tile = views.single().apply(context, null)
                        assertEquals(context.getString(R.string.poi_field_no_gps),
                            tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
                    }
                    val streamed = java.util.concurrent.CountDownLatch(1)
                    extension.startStream(request + "-stream", id, createConsumer<StreamState>(
                        onNextCallback = { streamed.countDown() },
                        onErrorCallback = { errors += it; streamed.countDown() }, onCompleteCallback = {}))
                    assertTrue("No stream callback for $id", streamed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertTrue("$id stream errors: $errors", errors.isEmpty())
                } finally { extension.stopView(request); extension.stopStream(request + "-stream") }
            }
        } finally { context.unbindService(connection) }
    }

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
            advanceTimeBy(1_001); runCurrent()
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

    @Test fun nearestTileUpdatesNameAndCategoryEvenWhenDistanceIsUnchanged() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val point = PoiPosition(55.6, 13.0)
        for (presentation in listOf(PoiFieldPresentation.GRAPHICAL, PoiFieldPresentation.EXPERIMENTAL_COMPACT)) {
            val store = mockk<RegionPackStore>()
            every { store.installed() } returns listOf(InstalledRegion("test", "Test", "1", 1))
            val water = OfflinePoi("node/1", "Riverside fountain", point, setOf("water.drinking_water"), "{}", 1234.5)
            val cafe = water.copy(osmId = "node/2", name = "Riverside coffee station with a very long name", categories = setOf("food.cafe"))
            every { store.search(any(), any()) } returns PoiPage(listOf(water), 1, false) andThen PoiPage(listOf(cafe), 1, false)
            val view = mockk<ViewEmitter>(relaxed = true)
            val rendered = mutableListOf<android.widget.RemoteViews>()
            val cancel = slot<() -> Unit>()
            every { view.updateView(capture(rendered)) } just Runs
            every { view.setCancellable(capture(cancel)) } just Runs
            val field = PoiFieldType(Provider { store }, StandardTestDispatcher(testScheduler),
                { flowOf(PoiFieldFix(point, 1000)) }, elapsedTime = { 1000 }, presentation = presentation)
            field.startView(context, ViewConfig(1 to 1, 120 to 80, 22), view)
            runCurrent(); advanceTimeBy(1_001); runCurrent()
            var tile = rendered.last().apply(context, null)
            assertEquals(context.getString(R.string.poi_field_name),
                tile.findViewById<android.widget.TextView>(R.id.poi_field_title).text.toString())
            assertEquals(water.name, tile.findViewById<android.widget.TextView>(R.id.poi_field_name).text.toString())
            assertEquals("1.2 km", tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
            assertTrue(tile.contentDescription.toString().contains(PoiTypes.label("water.drinking_water")))
            advanceTimeBy(5_000); runCurrent(); advanceTimeBy(1_001); runCurrent()
            tile = rendered.last().apply(context, null)
            assertEquals(cafe.name, tile.findViewById<android.widget.TextView>(R.id.poi_field_name).text.toString())
            assertEquals("1.2 km", tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
            assertTrue(tile.contentDescription.toString().contains(PoiTypes.label("food.cafe")))
            assertEquals(android.view.View.VISIBLE, tile.findViewById<android.view.View>(R.id.poi_field_category).visibility)
            saveTilePreview(context, tile, if (presentation == PoiFieldPresentation.GRAPHICAL) 240 else 120,
                if (presentation == PoiFieldPresentation.GRAPHICAL) 100 else 80, "poi-field-${presentation.name.lowercase()}.png")
            cancel.captured(); advanceUntilIdle()
        }
    }

    @Test fun selectedTileKeepsIdentityWithoutGpsAndClearsItWithSelection() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = MutableStateFlow<SelectedPoi?>(SelectedPoi("target", "Hilltop lookout", PoiPosition(55.6, 13.0), setOf("sights.viewpoint")))
        val repo = mockk<SelectedPoiRepository>()
        every { repo.selected } returns target
        val rendered = mutableListOf<android.widget.RemoteViews>()
        val view = mockk<ViewEmitter>(relaxed = true)
        val cancel = slot<() -> Unit>()
        every { view.updateView(capture(rendered)) } just Runs
        every { view.setCancellable(capture(cancel)) } just Runs
        val field = PoiFieldType(Provider { error("Selected tile must not read packs") },
            StandardTestDispatcher(testScheduler), { flowOf(null) }, repo, presentation = PoiFieldPresentation.GRAPHICAL)
        field.startView(context, ViewConfig(1 to 1, 120 to 80, 22), view)
        runCurrent(); advanceTimeBy(1_001); runCurrent()
        var tile = rendered.last().apply(context, null)
        assertEquals(context.getString(R.string.poi_field_selected_name),
            tile.findViewById<android.widget.TextView>(R.id.poi_field_title).text.toString())
        assertEquals("Hilltop lookout", tile.findViewById<android.widget.TextView>(R.id.poi_field_name).text.toString())
        assertEquals(context.getString(R.string.poi_field_no_gps), tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
        coVerify(exactly = 0) { repo.clearIfSelected(any()) }
        target.value = null
        advanceTimeBy(5_000); runCurrent(); advanceTimeBy(1_001); runCurrent()
        tile = rendered.last().apply(context, null)
        assertEquals(context.getString(R.string.poi_field_selected_name),
            tile.findViewById<android.widget.TextView>(R.id.poi_field_title).text.toString())
        assertEquals(android.view.View.GONE, tile.findViewById<android.view.View>(R.id.poi_field_name).visibility)
        assertEquals(android.view.View.GONE, tile.findViewById<android.view.View>(R.id.poi_field_category).visibility)
        assertEquals(context.getString(R.string.poi_field_select), tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString())
        cancel.captured(); advanceUntilIdle()
    }

    private fun saveTilePreview(context: android.content.Context, tile: android.view.View, widthDp: Int, heightDp: Int, name: String) {
        val density = context.resources.displayMetrics.density
        val width = (widthDp * density).toInt(); val height = (heightDp * density).toInt()
        tile.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
        tile.layout(0, 0, width, height)
        val title = tile.findViewById<android.widget.TextView>(R.id.poi_field_name)
        val lines = title.layout
        assertTrue("Long POI name should wrap", lines.lineCount > 1)
        assertEquals("Full POI name must remain visible", title.text.length, lines.getLineEnd(lines.lineCount - 1))
        assertTrue("Name must fit vertically", lines.getLineBottom(lines.lineCount - 1) <= title.height)
        for (line in 0 until lines.lineCount) assertEquals(0, lines.getEllipsisCount(line))
        assertEquals(android.view.Gravity.RIGHT,
            android.view.Gravity.getAbsoluteGravity(tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).gravity,
                android.view.View.LAYOUT_DIRECTION_LTR) and android.view.Gravity.HORIZONTAL_GRAVITY_MASK)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        tile.draw(android.graphics.Canvas(bitmap))
        java.io.File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun fastInitialSelectionStatusSurvivesRealSdkThrottle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = java.util.concurrent.CountDownLatch(1)
        val repo = mockk<SelectedPoiRepository>()
        every { repo.selected } returns flowOf(null)
        val handler = object : io.hammerhead.karooext.aidl.IHandler.Stub() {
            override fun onNext(bundle: android.os.Bundle) {
                @Suppress("DEPRECATION")
                val view = bundle.getParcelable<android.widget.RemoteViews>("view") ?: return
                val tile = view.apply(context, null)
                if (tile.findViewById<android.widget.TextView>(R.id.poi_field_distance).text.toString() ==
                    context.getString(R.string.poi_field_select)) selected.countDown()
            }
            override fun onError(error: String) = Unit
            override fun onComplete() = Unit
        }
        val emitter = ViewEmitter(context.packageName, handler)
        val field = PoiFieldType(Provider { error("Selected view must not read packs") },
            kotlinx.coroutines.Dispatchers.IO, { flowOf(null) }, repo, presentation = PoiFieldPresentation.GRAPHICAL)
        try {
            field.startView(context, ViewConfig(1 to 1, 120 to 80, 22), emitter)
            assertTrue("Real ViewEmitter dropped the initial Select POI status",
                selected.await(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { emitter.cancel() }
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
