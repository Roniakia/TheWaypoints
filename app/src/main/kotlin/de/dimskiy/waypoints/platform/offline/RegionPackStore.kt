package de.dimskiy.waypoints.platform.offline

import android.database.sqlite.SQLiteDatabase
import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.*

/**
 * Own one instance per app process and call it on the injected IO dispatcher.
 * A future downloader must supply a descriptor from a verified signed catalogue.
 * This class performs no network access and never touches the bookmark database.
 */
class RegionPackStore(private val root: File) {
    private val lock = Any()
    private data class RankedPoi(val poi: OfflinePoi, val nameKey: String)

    init {
        check(root.isDirectory || root.mkdirs())
        synchronized(lock) { discardUnreferencedSnapshots() }
    }

    fun installed(): List<InstalledRegion> = synchronized(lock) {
        snapshots().map { (id, folder) ->
            val metadata = metadata(folder)
            InstalledRegion(id, metadata.get("name").asString, metadata.get("revision").asString,
                metadata.get("poi_count").asLong, File(folder, "archive.sha256").takeIf { it.isFile }?.readText(), metadata.get("data_date")?.asString)
        }.sortedBy { PoiSearchText.normalize(it.name) }
    }

    /** Cancellation is checked while hashing/extracting and immediately before the atomic commit. */
    fun install(archive: File, expected: PackDescriptor, checkCancelled: () -> Unit = {}) = synchronized(lock) {
        require(archive.length() == expected.downloadBytes) { "Incorrect download size" }
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == expected.sha256) { "Incorrect pack checksum" }
        val folder = File(root, "snapshot-${UUID.randomUUID()}")
        check(folder.mkdir())
        var committed = false
        try {
            val limits = mapOf("region.sqlite" to expected.databaseBytes,
                "metadata.json" to 2L * 1024 * 1024, "attribution.txt" to 64L * 1024)
            val seen = mutableSetOf<String>()
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    checkCancelled()
                    val entry = zip.nextEntry ?: break
                    val limit = limits[entry.name]
                    require(limit != null && !entry.isDirectory && seen.add(entry.name)) { "Unexpected pack entry" }
                    FileOutputStream(File(folder, entry.name)).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytes = 0L
                        while (true) {
                            checkCancelled()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= limit) { "Pack entry exceeds its limit" }
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    zip.closeEntry()
                }
            }
            require(seen == limits.keys) { "Incomplete pack" }
            validate(folder, expected)
            checkCancelled()
            FileOutputStream(File(folder, "archive.sha256")).use {
                it.write(expected.sha256.toByteArray(Charsets.US_ASCII)); it.fd.sync()
            }
            val pointer = AtomicFile(File(root, "${expected.id}.current"))
            val stream = pointer.startWrite()
            try {
                stream.write(folder.name.toByteArray(Charsets.UTF_8))
                pointer.finishWrite(stream)
                committed = true
            } catch (failure: Throwable) {
                pointer.failWrite(stream)
                throw failure
            }
            discardUnreferencedSnapshots()
        } finally {
            if (!committed) folder.deleteRecursively()
        }
    }

    fun remove(id: String) = synchronized(lock) {
        require(id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")))
        AtomicFile(File(root, "$id.current")).delete()
        discardUnreferencedSnapshots()
    }

    /** Iterates matching rows without imposing a 30-result limit. Pages use a stable total ordering. */
    fun search(request: PoiSearch, checkCancelled: () -> Unit = {}): PoiPage = synchronized(lock) {
        if (request.categories?.isEmpty() == true) return@synchronized PoiPage(emptyList(), 0, false)
        val query = PoiSearchText.ftsQuery(request.text)
        val native = PoiSearchText.nativeWords(request.text)
        // Nonblank input which cannot be represented must not accidentally return every POI.
        if (request.text.isNotBlank() && query.isEmpty() && native.isEmpty()) return@synchronized PoiPage(emptyList(), 0, false)
        val comparator = if (request.sort == PoiSort.NEAREST && request.position != null) {
            compareBy<RankedPoi> { it.poi.distanceMetres }.thenBy { it.nameKey }.thenBy { it.poi.osmId }
        } else compareBy<RankedPoi> { it.nameKey }.thenBy { it.poi.osmId }
        val keep = request.offset + request.pageSize
        val best = java.util.PriorityQueue(minOf(keep + 1, 201), java.util.Collections.reverseOrder(comparator))
        val identities = HashSet<String>()
        for ((_, folder) in snapshots()) {
            checkCancelled()
            open(folder).use { db ->
                val args = mutableListOf<String>()
                val clauses = mutableListOf<String>()
                if (query.isNotEmpty()) {
                    clauses += "p.id IN (SELECT docid FROM poi_search WHERE search_text MATCH ?)"
                    args += query
                }
                request.categories?.let { categories ->
                    clauses += "EXISTS (SELECT 1 FROM poi_category c WHERE c.poi_id=p.id AND c.category IN (${categories.joinToString(",") { "?" }}))"
                    args += categories.sorted()
                }
                val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
                db.rawQuery("SELECT p.osm_id,p.name,p.lat,p.lon,p.tags_json," +
                    "(SELECT group_concat(category) FROM poi_category WHERE poi_id=p.id) FROM poi p$where", args.toTypedArray()).use { rows ->
                    while (rows.moveToNext()) {
                        checkCancelled()
                        val id = rows.getString(0)
                        if (native.isNotEmpty() && !PoiSearchText.matchesNative("${rows.getString(1)} ${rows.getString(4)}", native)) continue
                        if (!identities.add(id)) continue
                        val point = PoiPosition(rows.getDouble(2), rows.getDouble(3))
                        val poi = OfflinePoi(id, rows.getString(1), point,
                            rows.getString(5)?.split(',')?.toSet().orEmpty(), rows.getString(4),
                            request.position?.let { distance(it, point) })
                        val displayName = poi.name.ifBlank { poi.categories.firstOrNull()?.let(PoiTypes::label) ?: "POI" }
                        best.add(RankedPoi(poi, PoiSearchText.sortKey(displayName)))
                        if (best.size > keep) best.poll()
                    }
                }
            }
        }
        val items = best.toList().sortedWith(comparator).drop(request.offset).map { it.poi }
        PoiPage(items, identities.size, request.offset.toLong() + items.size < identities.size)
    }

    private fun validate(folder: File, expected: PackDescriptor) {
        val meta = metadata(folder)
        require(meta.get("format_version").asInt == 1) { "Unsupported pack format" }
        require(meta.get("id").asString == expected.id && meta.get("revision").asString == expected.revision) { "Incorrect region or revision" }
        require(meta.get("poi_count").asLong == expected.poiCount && meta.get("database_bytes").asLong == expected.databaseBytes)
        require(File(folder, "region.sqlite").length() == expected.databaseBytes)
        require(meta.get("name").asString.isNotBlank() && meta.get("licence").asString == "ODbL-1.0")
        require(File(folder, "attribution.txt").readText().isNotBlank())
        // Android's FTS4 integrity check needs a writable connection. This is still an
        // uncommitted staging snapshot; all installed search connections are read-only.
        open(folder, readOnly = false).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use {
                val result = if (it.moveToFirst()) it.getString(0) else "No result"
                require(result == "ok") { "SQLite integrity check failed: $result" }
            }
            // The host's optional RTree is not supported by stock Android SQLite.
            // Android reads the ordinary poi coordinate columns instead.
            for (table in listOf("poi", "poi_search")) {
                db.rawQuery("SELECT count(*) FROM $table", null).use { require(it.moveToFirst() && it.getLong(0) == expected.poiCount) }
            }
            db.rawQuery("SELECT osm_id,name,lat,lon,tags_json FROM poi LIMIT 0", null).close()
            db.rawQuery("SELECT poi_id,category FROM poi_category LIMIT 0", null).close()
            db.rawQuery("SELECT docid FROM poi_search WHERE search_text MATCH 'water*' LIMIT 1", null).close()
            db.rawQuery("SELECT 1 FROM poi WHERE lat NOT BETWEEN -90 AND 90 OR lon NOT BETWEEN -180 AND 180 OR osm_id NOT GLOB 'node/[0-9]*' AND osm_id NOT GLOB 'way/[0-9]*' AND osm_id NOT GLOB 'relation/[0-9]*' LIMIT 1", null).use { require(!it.moveToFirst()) }
        }
    }

    private fun metadata(folder: File): JsonObject = JsonParser.parseString(File(folder, "metadata.json").readText()).asJsonObject
    private fun open(folder: File, readOnly: Boolean = true): SQLiteDatabase = SQLiteDatabase.openDatabase(
        File(folder, "region.sqlite").path, null,
        (if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE) or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    private fun snapshots(): List<Pair<String, File>> = root.listFiles().orEmpty()
        .filter { it.name.endsWith(".current") || it.name.endsWith(".current.bak") }
        .map { it.name.removeSuffix(".bak") }.distinct().sorted().map { name ->
            val snapshot = AtomicFile(File(root, name)).openRead().use { it.readBytes().toString(Charsets.UTF_8) }
            require(snapshot.matches(Regex("snapshot-[0-9a-f-]{36}"))) { "Invalid installed region pointer" }
            val folder = File(root, snapshot)
            require(folder.isDirectory) { "Missing installed region" }
            name.removeSuffix(".current") to folder
        }

    private fun discardUnreferencedSnapshots() {
        val referenced = snapshots().map { it.second.name }.toSet()
        root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("snapshot-") && it.name !in referenced }
            .forEach { it.deleteRecursively() }
    }

    private fun distance(a: PoiPosition, b: PoiPosition): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
