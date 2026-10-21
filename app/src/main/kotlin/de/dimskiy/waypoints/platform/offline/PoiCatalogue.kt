package de.dimskiy.waypoints.platform.offline

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.gson.JsonParser
import java.net.URI
import java.security.GeneralSecurityException

data class CatalogueRegion(
    val descriptor: PackDescriptor,
    val name: String,
    val country: String,
    val continent: String,
    val searchName: String,
    val url: String,
    val bounds: List<Double>,
    val geometry: com.google.gson.JsonObject,
    val dataDate: String,
    val qualityReviewRequired: Boolean,
)

class VerifiedCatalogue internal constructor(val regions: List<CatalogueRegion>, val attribution: String)

object PoiCatalogueVerifier {
    const val MAX_BYTES = 8 * 1024 * 1024
    // Independent data-signing key, pinned from the reviewed server source, not from a download.
    private val publicKey = Base64.decode("N7wXsO7yzJ61q4Br0igg3SR7ifLELwsdu4U/xBUKozY=")

    fun verify(bytes: ByteArray, signature: ByteArray): VerifiedCatalogue {
        require(bytes.size in 1..MAX_BYTES && signature.size in 1..200) { "Invalid catalogue size" }
        try {
            Ed25519Verify(publicKey).verify(Base64.decode(signature.toString(Charsets.US_ASCII).trim()), bytes)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Catalogue signature is invalid", e)
        }
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(root.get("catalogue_format").asInt == 1) { "Update the app to read this catalogue" }
        val regions = root.getAsJsonArray("regions").map { entry ->
            val json = entry.asJsonObject
            require(json.get("format_version").asInt == 1) { "Update the app to read this pack format" }
            val descriptor = PackDescriptor(json.get("id").asString, json.get("revision").asString,
                json.get("sha256").asString, json.get("download_bytes").asLong,
                json.get("database_bytes").asLong, json.get("poi_count").asLong)
            val url = json.get("url").asString
            requireReleaseAsset(url)
            val bounds = json.getAsJsonArray("region_bounds").map { it.asDouble }
            require(bounds.size == 4 && bounds.all(Double::isFinite) &&
                bounds[0] in -180.0..180.0 && bounds[2] in bounds[0]..180.0 &&
                bounds[1] in -90.0..90.0 && bounds[3] in bounds[1]..90.0)
            CatalogueRegion(descriptor, json.get("name").asString, json.get("country").asString,
                json.get("continent").asString, json.get("search_name").asString, url, bounds,
                json.getAsJsonObject("region_geometry"), json.get("data_date").asString,
                json.get("quality_review_required")?.asBoolean ?: false)
        }
        require(regions.isNotEmpty() && regions.map { it.descriptor.id }.distinct().size == regions.size)
        return VerifiedCatalogue(regions, root.get("attribution").asString)
    }

    fun requireReleaseAsset(value: String) {
        val url = URI(value)
        require(url.scheme == "https" && url.host == "github.com" && url.port == -1 && url.userInfo == null &&
            url.rawQuery == null && url.fragment == null &&
            // Keep historical signed catalogues valid across the repository rename.
            (url.path.startsWith("/Roniakia/TheWaypoints/releases/download/poi-data-") ||
                url.path.startsWith("/Roniakia/WaypointsKaroo/releases/download/poi-data-")) &&
            url.path.split('/').size == 7 && !url.path.contains("..")) { "Unexpected data release URL" }
    }
}

/** Empty selection means update all installed; any selection also downloads missing regions. */
fun regionsToUpdate(catalogue: VerifiedCatalogue, installed: List<InstalledRegion>, selected: Set<String>): List<CatalogueRegion> {
    val targets = selected.ifEmpty { installed.map { it.id }.toSet() }
    return catalogue.regions.filter { region ->
        region.descriptor.id in targets && installed.none {
            it.id == region.descriptor.id && ((it.dataDate != null && it.dataDate > region.dataDate) ||
                (it.revision == region.descriptor.revision && it.sha256 == region.descriptor.sha256))
        }
    }
}
