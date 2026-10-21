package de.dimskiy.waypoints.platform.offline

import java.text.Normalizer
import java.util.Locale

data class PackDescriptor(
    val id: String,
    val revision: String,
    val sha256: String,
    val downloadBytes: Long,
    val databaseBytes: Long,
    val poiCount: Long,
) {
    init {
        require(id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        require(revision.isNotBlank() && revision.length <= 100)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(downloadBytes in 1..512L * 1024 * 1024)
        require(databaseBytes in 1..2L * 1024 * 1024 * 1024)
        require(poiCount > 0)
    }
}

data class InstalledRegion(val id: String, val name: String, val revision: String, val poiCount: Long, val sha256: String? = null, val dataDate: String? = null)

data class PoiPosition(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class OfflinePoi(
    // Namespaced OSM identities distinguish node/1, way/1 and relation/1.
    val osmId: String,
    val name: String,
    val position: PoiPosition,
    val categories: Set<String>,
    val tagsJson: String,
    val distanceMetres: Double?,
)

enum class PoiSort { NEAREST, NAME }

data class PoiSearch(
    val text: String = "",
    // Null means all categories; an empty selection means no categories.
    val categories: Set<String>? = null,
    val position: PoiPosition? = null,
    val sort: PoiSort = PoiSort.NEAREST,
    val offset: Int = 0,
    val pageSize: Int = 50,
) {
    init {
        require(pageSize in 1..200)
        require(offset >= 0 && offset.toLong() + pageSize < Int.MAX_VALUE)
        require(text.length <= 500)
        require(categories == null || categories.size <= 100)
    }
}

data class PoiPage(val items: List<OfflinePoi>, val total: Int, val hasMore: Boolean)

/** Pack text is transliterated by the generator. English queries also match non-Latin names. */
object PoiSearchText {
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^a-z0-9]+")
    private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")
    private fun folded(value: String): String {
        val folded = value.lowercase(Locale.ROOT).replace("ß", "ss").replace("æ", "ae")
            .replace("ø", "o").replace("ł", "l").replace("œ", "oe").replace("đ", "d")
        return Normalizer.normalize(folded, Normalizer.Form.NFKD)
            .replace(marks, "")
    }
    fun normalize(value: String): String = folded(value).replace(separators, " ").trim()
    fun nativeWords(value: String): List<String> = folded(value).split(wordSeparators)
        .filter { word -> word.any { it.code > 127 && it.isLetterOrDigit() } }
    fun matchesNative(value: String, words: List<String>): Boolean = words.isEmpty() || folded(value).let { text -> words.all { it in text } }
    fun sortKey(value: String): String = normalize(value).ifBlank { folded(value) }

    // Only normalized tokens enter FTS syntax; punctuation/operators never execute.
    fun ftsQuery(value: String): String = normalize(value).split(' ').filter(String::isNotEmpty)
        .joinToString(" AND ") { "$it*" }
}
