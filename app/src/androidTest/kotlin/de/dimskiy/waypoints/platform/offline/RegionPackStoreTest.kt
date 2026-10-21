package de.dimskiy.waypoints.platform.offline

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RegionPackStoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = File(instrumentation.targetContext.cacheDir, "packs-test-${UUID.randomUUID()}")
    private val store = RegionPackStore(root)
    private val scratch = File(instrumentation.targetContext.cacheDir, "pack-input-${UUID.randomUUID()}").apply { mkdirs() }

    @After fun clean() { root.deleteRecursively(); scratch.deleteRecursively() }

    @Test fun pythonPackSupportsAndroidFtsTypesPagingAndRestart() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        assertEquals("Skåne test", RegionPackStore(root).installed().single().name)
        assertEquals("node/1", store.search(PoiSearch("kyiv")).items.single().osmId)
        assertEquals("way/1", store.search(PoiSearch("tokyo")).items.single().osmId)
        assertEquals("Київ", store.search(PoiSearch("coffee", categories = setOf("bicycle.pump"))).items.single().name)
        assertEquals(64, store.search(PoiSearch("water")).total)
        assertEquals(64, store.search(PoiSearch("skane")).total)
        val first = store.search(PoiSearch(pageSize = 40))
        val second = store.search(PoiSearch(offset = 40, pageSize = 40))
        assertEquals(66, first.total)
        assertEquals(40, first.items.size)
        assertEquals(26, second.items.size)
        assertTrue(first.hasMore)
        assertFalse(second.hasMore)
        assertEquals(66, (first.items + second.items).map { it.osmId }.toSet().size)
        assertEquals(0, store.search(PoiSearch(categories = emptySet())).total)
        assertEquals(0, store.search(PoiSearch("\"***")).total)
        assertEquals("node/1", store.search(PoiSearch(position = PoiPosition(55.0, 13.0))).items.first().osmId)
    }

    @Test fun failedChecksumMetadataAndCancellationPreserveInstalledRegion() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        expectFailure { store.install(archive, descriptor.copy(sha256 = "0".repeat(64))) }
        expectFailure { store.install(archive, descriptor.copy(revision = "wrong")) }
        var checks = 0
        try {
            store.install(archive, descriptor) { if (++checks > 5) throw CancellationException() }
            fail("Cancellation expected")
        } catch (_: CancellationException) { }
        assertEquals(descriptor.revision, RegionPackStore(root).installed().single().revision)
        assertEquals(66, store.search(PoiSearch()).total)
        assertEquals(1, root.listFiles()!!.count { it.isDirectory })
    }

    @Test fun rejectsTraversalAndOversizeEntriesWithoutReplacingOldPack() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        for (extra in listOf("../escape", "oversize")) {
            val modified = File(scratch, "$extra.zip".replace('/', '_'))
            ZipOutputStream(modified.outputStream()).use { output ->
                ZipInputStream(archive.inputStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        output.putNextEntry(ZipEntry(entry.name))
                        if (extra == "oversize" && entry.name == "attribution.txt") output.write(ByteArray(65537))
                        else input.copyTo(output)
                        output.closeEntry()
                    }
                }
                if (extra != "oversize") {
                    output.putNextEntry(ZipEntry(extra)); output.write(1); output.closeEntry()
                }
            }
            val changed = descriptor.copy(sha256 = hash(modified), downloadBytes = modified.length())
            expectFailure { store.install(modified, changed) }
        }
        assertEquals(66, store.search(PoiSearch()).total)
        assertFalse(File(root.parentFile, "escape").exists())
    }

    @Test fun atomicReplacementAndRemoval() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        store.install(archive, descriptor)
        assertEquals(1, store.installed().size)
        assertEquals(1, root.listFiles()!!.count { it.isDirectory })
        store.remove(descriptor.id)
        assertTrue(store.installed().isEmpty())
        assertEquals(0, store.search(PoiSearch()).total)
    }

    @Test fun restartRecoversAtomicPointerAndCleansUncommittedSnapshot() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        // AtomicFile's backup is the previous committed state after an interrupted replacement.
        val pointer = File(root, "${descriptor.id}.current")
        assertTrue(pointer.renameTo(File(root, "${descriptor.id}.current.bak")))
        val orphan = File(root, "snapshot-${UUID.randomUUID()}").apply { mkdirs() }
        File(orphan, "region.sqlite").writeText("incomplete")
        val recovered = RegionPackStore(root)
        assertEquals(descriptor.revision, recovered.installed().single().revision)
        assertEquals(66, recovered.search(PoiSearch()).total)
        assertFalse(orphan.exists())
    }

    @Test fun overlappingRegionsDeduplicateTypedOsmIds() {
        val (archive, descriptor) = fixture()
        store.install(archive, descriptor)
        val second = File(scratch, "second.zip")
        ZipOutputStream(second.outputStream()).use { output ->
            ZipInputStream(archive.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "metadata.json") {
                        val meta = JsonParser.parseString(input.readBytes().toString(Charsets.UTF_8)).asJsonObject
                        meta.addProperty("id", "se-other")
                        output.write(meta.toString().toByteArray())
                    } else input.copyTo(output)
                    output.closeEntry()
                }
            }
        }
        store.install(second, descriptor.copy(id = "se-other", downloadBytes = second.length(), sha256 = hash(second)))
        assertEquals(2, store.installed().size)
        assertEquals(66, store.search(PoiSearch()).total)
        store.remove("se-other")
        assertEquals(66, store.search(PoiSearch()).total)
    }

    @Test fun optionalRealSkanePackCompatibility() {
        assumeTrue("Supply -PpoiCompatibilityAssets=<folder> containing real.zip and real-descriptor.json",
            instrumentation.context.assets.list("")!!.contains("real.zip"))
        val (archive, descriptor) = fixture("real.zip", "real-descriptor.json")
        store.install(archive, descriptor)
        val allStart = System.nanoTime()
        assertEquals(19159, store.search(PoiSearch()).total)
        android.util.Log.i("PoiCompatibility", "Skane all by name elapsedMs=${(System.nanoTime()-allStart)/1_000_000}")
        val start = System.nanoTime()
        val water = store.search(PoiSearch("water", position = PoiPosition(55.605, 13.004)))
        assertTrue(water.items.isNotEmpty())
        assertTrue(water.items.zipWithNext().all { (a,b) -> a.distanceMetres!! <= b.distanceMetres!! })
        android.util.Log.i("PoiCompatibility", "Skane water matches=${water.total}, page=${water.items.size}, elapsedMs=${(System.nanoTime()-start)/1_000_000}")
    }

    private fun fixture(zip: String = "poi-packs/fixture.zip", meta: String = "poi-packs/descriptor.json"): Pair<File, PackDescriptor> {
        val archive = File(scratch, UUID.randomUUID().toString() + ".zip")
        instrumentation.context.assets.open(zip).use { input -> archive.outputStream().use(input::copyTo) }
        val json = instrumentation.context.assets.open(meta).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        return archive to PackDescriptor(json.get("id").asString, json.get("revision").asString,
            json.get("sha256").asString, json.get("download_bytes").asLong,
            json.get("database_bytes").asLong, json.get("poi_count").asLong)
    }

    private fun hash(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun expectFailure(block: () -> Unit) {
        try { block(); fail("Invalid pack accepted") }
        catch (_: IllegalArgumentException) { }
        catch (_: java.util.zip.ZipException) { }
    }
}
