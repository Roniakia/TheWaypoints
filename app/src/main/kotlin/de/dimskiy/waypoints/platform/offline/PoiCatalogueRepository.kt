package de.dimskiy.waypoints.platform.offline

import android.util.AtomicFile
import com.google.crypto.tink.subtle.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

class PoiCatalogueRepository(
    private val file: File,
    private val transport: PackTransport,
    private val seed: () -> Pair<ByteArray, ByteArray>,
) {
    @Synchronized fun load(): VerifiedCatalogue {
        val atomic = AtomicFile(file)
        try {
            atomic.openRead().use { input ->
                val bytes = input.readBytes()
                require(bytes.size <= PoiCatalogueVerifier.MAX_BYTES * 2)
                val envelope = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                return PoiCatalogueVerifier.verify(Base64.decode(envelope.get("catalogue").asString),
                    Base64.decode(envelope.get("signature").asString))
            }
        } catch (_: Exception) {
            // A missing/corrupt cache cannot replace the independently signed bundled seed.
            val (bytes, signature) = seed()
            return PoiCatalogueVerifier.verify(bytes, signature)
        }
    }

    @Synchronized fun refresh(checkCancelled: () -> Unit = {}): VerifiedCatalogue {
        val releases = JsonParser.parseString(transport.read(RELEASES_URL, 2 * 1024 * 1024, checkCancelled)
            .toString(Charsets.UTF_8)).asJsonArray
        val release = releases.map { it.asJsonObject }.filter {
            it.get("tag_name").asString.startsWith("poi-data-") && !it.get("draft").asBoolean
        }.maxByOrNull { it.get("published_at").asString } ?: error("No POI data release is available")
        val assets = release.getAsJsonArray("assets").map { it.asJsonObject }
        fun url(name: String): String = assets.single { it.get("name").asString == name }
            .get("browser_download_url").asString.also(PoiCatalogueVerifier::requireReleaseAsset)
        val bytes = transport.read(url("catalog.json"), PoiCatalogueVerifier.MAX_BYTES, checkCancelled)
        val signature = transport.read(url("catalog.json.sig"), 200, checkCancelled)
        val verified = PoiCatalogueVerifier.verify(bytes, signature)
        checkCancelled()
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            val envelope = JsonObject().apply {
                addProperty("catalogue", Base64.encode(bytes)); addProperty("signature", Base64.encode(signature))
            }
            output.write(envelope.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) { atomic.failWrite(output); throw failure }
        return verified
    }

    companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Roniakia/TheWaypoints/releases?per_page=100"
    }
}
