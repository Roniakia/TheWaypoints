package de.dimskiy.waypoints.platform.updates

import com.google.gson.JsonParser
import de.dimskiy.waypoints.platform.offline.HttpsPackTransport
import de.dimskiy.waypoints.platform.offline.PackTransport

enum class UpdateChannel { STABLE, BETA }
data class AppRelease(val version: String, val code: Long, val tag: String, val notes: String,
    val url: String, val bytes: Long, val sha256: String)

/** Use immutable release assets, never GitHub's rolling latest/download APK alias. */
class UpdateSource(private val transport: PackTransport = HttpsPackTransport()) {
    fun latest(channel: UpdateChannel, packageName: String, check: () -> Unit = {}): AppRelease? {
        val releases = JsonParser.parseString(transport.read(
            "https://api.github.com/repos/Roniakia/TheWaypoints/releases?per_page=100", 4 * 1024 * 1024, check)
            .toString(Charsets.UTF_8)).asJsonArray
        var best: AppRelease? = null
        releases.forEach { entry ->
            check()
            val release = entry.asJsonObject
            val tag = release.get("tag_name").asString
            if (release.get("draft").asBoolean || !tag.matches(Regex("v[0-9]+\\.[0-9]+(?:-beta\\.[0-9]+)?")) ||
                (channel == UpdateChannel.STABLE && release.get("prerelease").asBoolean)) return@forEach
            val assets = release.getAsJsonArray("assets").map { it.asJsonObject }
            val manifest = assets.singleOrNull { it.get("name").asString == "manifest-karoo.json" } ?: return@forEach
            val base = "https://github.com/Roniakia/TheWaypoints/releases/download/$tag/"
            val manifestUrl = manifest.get("browser_download_url").asString
            require(manifestUrl == base + "manifest-karoo.json") { "Unexpected update manifest URL" }
            val metadata = JsonParser.parseString(transport.read(manifestUrl, 128 * 1024, check).toString(Charsets.UTF_8)).asJsonObject
            require(metadata.get("packageName").asString == packageName) { "Update package does not match" }
            val code = metadata.get("latestVersionCode").asLong
            require(code > 0) { "Invalid update version code" }
            if (code <= (best?.code ?: 0)) return@forEach
            // Published manifests retain the original repository name after its rename.
            val apkUrl = metadata.get("latestApkUrl").asString.replace(
                "https://github.com/Roniakia/WaypointsKaroo/", "https://github.com/Roniakia/TheWaypoints/")
            val apk = assets.singleOrNull { it.get("browser_download_url").asString == apkUrl &&
                it.get("name").asString.endsWith(".apk") } ?: error("Release APK is missing")
            require(apkUrl == base + apk.get("name").asString && !apk.get("name").asString.contains('/')) { "Unexpected APK URL" }
            val digest = apk.get("digest")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            require(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "Release is missing its SHA-256 digest" }
            val bytes = apk.get("size").asLong
            require(bytes in 1..200L * 1024 * 1024) { "Invalid update size" }
            best = AppRelease(metadata.get("latestVersion").asString, code,
                tag, release.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(32_000),
                apkUrl, bytes, digest.removePrefix("sha256:").lowercase())
        }
        return best
    }
}
