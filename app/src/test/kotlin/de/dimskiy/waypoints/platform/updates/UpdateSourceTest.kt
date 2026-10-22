package de.dimskiy.waypoints.platform.updates

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.dimskiy.waypoints.platform.offline.PackTransport
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class UpdateSourceTest {
    private val transport = mockk<PackTransport>()
    private val bodies = mutableMapOf<String, String>()
    private val releases = JsonArray()
    private val api = "https://api.github.com/repos/Roniakia/TheWaypoints/releases?per_page=100"
    private val pkg = "io.github.roniakia.waypoints"
    private fun release(tag: String, code: Int, beta: Boolean = false, draft: Boolean = false): JsonObject {
        val base = "https://github.com/Roniakia/TheWaypoints/releases/download/$tag/"
        val assets = JsonArray()
        for (name in listOf("manifest-karoo.json", "app.apk")) assets.add(JsonObject().apply {
            addProperty("name", name); addProperty("browser_download_url", base + name)
            addProperty("size", 100); addProperty("digest", "sha256:" + "ab".repeat(32))
        })
        bodies[base + "manifest-karoo.json"] = """{"packageName":"$pkg","latestVersion":"0.96","latestVersionCode":$code,"latestApkUrl":"${base}app.apk"}"""
        return JsonObject().apply {
            addProperty("tag_name", tag); addProperty("draft", draft); addProperty("prerelease", beta)
            addProperty("body", "Release notes"); add("assets", assets)
            releases.add(this)
        }
    }
    private fun latest(channel: UpdateChannel): AppRelease? {
        every { transport.read(any(), any(), any()) } answers {
            val url = firstArg<String>()
            (if (url == api) releases.toString() else bodies.getValue(url)).toByteArray()
        }
        return UpdateSource(transport).latest(channel, pkg)
    }
    @Test fun stableExcludesBetasDraftsAndPoiDataAndUsesVersionCode() {
        release("v0.95-beta.2", 7) // Promoted beta is stable regardless of its retained tag.
        release("v0.96-beta.1", 8, beta = true)
        release("v0.97", 9, draft = true)
        release("poi-data-2026-test", 100)
        assertEquals(7L, latest(UpdateChannel.STABLE)!!.code)
        assertEquals(8L, latest(UpdateChannel.BETA)!!.code)
    }
    @Test fun betaAlsoOffersNewerStableAndDoesNotRelyOnApiOrder() {
        release("v0.96-beta.1", 8, beta = true)
        release("v0.97", 10)
        release("v0.95", 7)
        assertEquals(10L, latest(UpdateChannel.BETA)!!.code)
        assertEquals("Release notes", latest(UpdateChannel.BETA)!!.notes)
    }
    @Test fun acceptsHistoricalRepositoryAliasInImmutableManifest() {
        release("v0.96", 8)
        val key = bodies.keys.single()
        bodies[key] = bodies.getValue(key).replace("Roniakia/TheWaypoints/", "Roniakia/WaypointsKaroo/")
        assertTrue(latest(UpdateChannel.STABLE)!!.url.startsWith("https://github.com/Roniakia/TheWaypoints/"))
    }
    @Test fun rejectsExternalApkAndWrongPackage() {
        release("v0.96", 8)
        val key = bodies.keys.single()
        val valid = bodies.getValue(key)
        for (bad in listOf(valid.replace(pkg, "evil.package"), valid.replace("app.apk", "../evil.apk"))) {
            bodies[key] = bad
            try { latest(UpdateChannel.STABLE); fail("Unsafe release accepted") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
        }
    }
    @Test fun rejectsMissingDigestAndOversizeApk() {
        val release = release("v0.96", 8)
        val apk = release.getAsJsonArray("assets")[1].asJsonObject
        apk.remove("digest")
        try { latest(UpdateChannel.STABLE); fail() } catch (_: IllegalArgumentException) { }
        apk.addProperty("digest", "sha256:" + "ab".repeat(32))
        apk.addProperty("size", 300L * 1024 * 1024)
        try { latest(UpdateChannel.STABLE); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun missingManifestIsNotAnAppCandidate() {
        release("v0.96", 8).getAsJsonArray("assets").remove(0)
        assertNull(latest(UpdateChannel.STABLE))
    }
}
