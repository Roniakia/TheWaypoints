package de.dimskiy.waypoints.platform.offline

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import de.dimskiy.waypoints.domain.cases.OpenExternalMap
import de.dimskiy.waypoints.domain.model.Waypoint
import de.dimskiy.waypoints.domain.providers.LocationsProvider
import de.dimskiy.waypoints.domain.waypointsrepository.WaypointsRepository
import de.dimskiy.waypoints.platform.di.BaseModule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.*

enum class OfflinePage { POIS, FAVORITES, REGIONS, SETTINGS }
data class OfflineRow(val waypoint: Waypoint, val categories: Set<String>, val distanceMetres: Double?)
data class RegionDownloadStatus(val state: WorkInfo.State, val percent: Int, val error: String?, val phase: String = "download")
data class OfflineUiState(
    val ready: Boolean = false, val page: OfflinePage = OfflinePage.POIS, val invitation: Boolean = true,
    val catalogue: VerifiedCatalogue? = null, val installed: List<InstalledRegion> = emptyList(),
    val recommended: List<String> = emptyList(), val selected: Set<String> = emptySet(), val regionQuery: String = "",
    val downloads: Map<String, RegionDownloadStatus> = emptyMap(), val refreshing: Boolean = false,
    val poiQuery: String = "", val types: Set<String> = PoiTypes.defaults, val sort: PoiSort = PoiSort.NEAREST,
    val rows: List<OfflineRow> = emptyList(), val total: Int = 0, val hasMore: Boolean = false,
    val searching: Boolean = false, val filterCount: Int? = null, val position: PoiPosition? = null, val message: String? = null,
)

@HiltViewModel
class OfflineViewModel @Inject constructor(
    private val catalogue: PoiCatalogueRepository, private val storeProvider: Provider<RegionPackStore>,
    private val downloads: RegionDownloads, private val bookmarks: WaypointsRepository,
    private val locations: LocationsProvider, private val openMap: OpenExternalMap,
    private val preferences: DataStore<Preferences>,
    @BaseModule.DispatcherIO private val io: CoroutineDispatcher,
) : ViewModel() {
    private val mutable = MutableStateFlow(OfflineUiState())
    val state = mutable.asStateFlow()
    @Volatile private var saved = emptyList<Waypoint.Stored>()
    private var searchJob: Job? = null
    private var previewJob: Job? = null
    private val dismissed = booleanPreferencesKey("offlineRegionsInvitationDismissed")

    init {
        viewModelScope.launch {
            try {
                val initial = withContext(io) { catalogue.load() to storeProvider.get().installed() }
                val invitation = preferences.data.first()[dismissed] != true
                mutable.update { it.copy(ready = true, catalogue = initial.first, installed = initial.second,
                    invitation = invitation, page = it.page,
                    recommended = recommendations(initial.first, it.position)) }
                search()
                refreshCatalogue(announceFailure = false)
            } catch (failure: Exception) { report(failure); mutable.update { it.copy(ready = true) } }
        }
        viewModelScope.launch {
            bookmarks.observeWaypointsStored().catch { report(it as? Exception ?: RuntimeException(it)) }.collect { values ->
                saved = values
                if ((mutable.value.page == OfflinePage.FAVORITES)) search() else mutable.update { state -> state.copy(rows = state.rows.map { row ->
                    val waypoint = saved.firstOrNull { it.serverId == row.waypoint.serverId } ?: row.waypoint.let {
                        Waypoint.Discovered(it.serverId, it.name, it.address, it.latitude, it.longitude)
                    }
                    row.copy(waypoint = waypoint)
                }) }
            }
        }
        viewModelScope.launch {
            var finished = emptySet<java.util.UUID>()
            downloads.observe().catch { report(it as? Exception ?: RuntimeException(it)) }.collect { infos ->
                val latest = infos.groupBy { info -> info.tags.firstOrNull { it.startsWith("region:") }?.removePrefix("region:") }
                    .filterKeys { it != null }.mapKeys { it.key!! }.mapValues { (_, values) ->
                        values.maxBy { info -> info.tags.firstOrNull { it.startsWith("queued:") }?.removePrefix("queued:")?.toLongOrNull() ?: 0 }
                    }
                mutable.update { state -> state.copy(downloads = latest.mapValues { (_, info) ->
                    RegionDownloadStatus(info.state, info.progress.getInt("percent", 0), info.outputData.getString("error"), info.progress.getString("phase") ?: "download")
                }) }
                val terminal = latest.values.filter { it.state.isFinished }.map { it.id }.toSet()
                if (terminal != finished) { finished = terminal; refreshInstalled() }
            }
        }
    }

    suspend fun refreshPosition() {
            try {
                val location = withTimeoutOrNull(12_000) { locations.observeDeviceLocations().first { it.isReady() || it.isError() }.data }
                if (location != null) {
                    val point = PoiPosition(location.latitude, location.longitude)
                    mutable.update { it.copy(position = point, recommended = it.catalogue?.let { c -> recommendations(c, point) }.orEmpty()) }
                    search()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Locale recommendations and alphabetical search remain usable. */ }
    }

    private data class SearchOptions(val query: String, val types: Set<String>, val sort: PoiSort)
    private val pageSearches = mutableMapOf<OfflinePage, SearchOptions>()

    fun page(page: OfflinePage) {
        val current = mutable.value
        if (current.page == page) return
        if (current.page == OfflinePage.POIS || current.page == OfflinePage.FAVORITES) {
            pageSearches[current.page] = SearchOptions(current.poiQuery, current.types, current.sort)
        }
        val options = pageSearches[page] ?: SearchOptions("", PoiTypes.defaults, PoiSort.NEAREST)
        previewJob?.cancel()
        mutable.update { it.copy(page = page, poiQuery = options.query, types = options.types,
            sort = options.sort, rows = emptyList(), total = 0, hasMore = false, filterCount = null) }
        if (page == OfflinePage.POIS || page == OfflinePage.FAVORITES) search() else searchJob?.cancel()
    }
    fun regionQuery(query: String) { mutable.update { it.copy(regionQuery = query) } }
    fun select(ids: Set<String>, checked: Boolean) { mutable.update { it.copy(selected = if (checked) it.selected + ids else it.selected - ids) } }
    fun clearSelection() { mutable.update { it.copy(selected = emptySet()) } }
    fun poiQuery(query: String) { mutable.update { it.copy(poiQuery = query) }; search() }
    fun applyFilters(types: Set<String>, sort: PoiSort) { mutable.update { it.copy(types = types, sort = sort) }; search() }
    fun clearMessage() { mutable.update { it.copy(message = null) } }
    fun skip() = viewModelScope.launch { preferences.edit { it[dismissed] = true }; mutable.update { it.copy(invitation = false, page = OfflinePage.POIS) } }

    fun refreshCatalogue(announceFailure: Boolean = true) = viewModelScope.launch {
        if (mutable.value.refreshing) return@launch
        mutable.update { it.copy(refreshing = true) }
        try {
            val job = currentCoroutineContext()
            val updated = withContext(io) { catalogue.refresh { job.ensureActive() } }
            mutable.update { it.copy(catalogue = updated, recommended = recommendations(updated, it.position)) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (announceFailure) mutable.update { it.copy(message = "Region list could not refresh. Showing the last verified list.") } }
        finally { mutable.update { it.copy(refreshing = false) } }
    }

    fun updateRegions() = viewModelScope.launch {
        val state = mutable.value
        val catalogue = state.catalogue ?: return@launch
        val targets = regionsToUpdate(catalogue, state.installed, state.selected)
        val requested = state.selected.ifEmpty { state.installed.map { it.id }.toSet() }
        val unavailable = requested - catalogue.regions.map { it.descriptor.id }.toSet()
        downloads.enqueue(targets)
        preferences.edit { it[dismissed] = true }
        mutable.update { it.copy(invitation = false, message = if (unavailable.isNotEmpty()) "${unavailable.size} region(s) are unavailable in this catalogue."
            else if (targets.isEmpty()) "Selected regions are up to date." else null) }
    }

    fun cancel(id: String) { downloads.cancel(id) }
    fun remove(id: String) = viewModelScope.launch {
        downloads.cancel(id)
        withContext(io) { storeProvider.get().remove(id); downloads.removePartials(id) }
        refreshInstalled()
    }

    private suspend fun refreshInstalled() {
        try {
            val installed = withContext(io) { storeProvider.get().installed() }
            mutable.update { it.copy(installed = installed) }; search()
        }
        catch (failure: Exception) { report(failure) }
    }

    fun search(more: Boolean = false) {
        searchJob?.cancel()
        mutable.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            if (!more) delay(200)
            val state = mutable.value
            val offset = if (more) state.rows.size else 0
            try {
                val job = currentCoroutineContext()
                val (rows, total, hasMore) = withContext(io) {
                    if ((state.page == OfflinePage.FAVORITES)) {
                        val all = savedRows(state.poiQuery, state.types, state.sort, state.position)
                        Triple(all.drop(offset).take(50), all.size, offset + 50 < all.size)
                    } else {
                        val page = storeProvider.get().search(PoiSearch(state.poiQuery, state.types, state.position, state.sort, offset, 50)) { job.ensureActive() }
                        Triple(page.items.map { it.asRow() }, page.total, page.hasMore)
                    }
                }
                mutable.update { it.copy(rows = if (more) it.rows + rows else rows, total = total, hasMore = hasMore, searching = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { report(failure); mutable.update { it.copy(searching = false) } }
        }
    }

    fun previewFilters(types: Set<String>, sort: PoiSort) {
        previewJob?.cancel(); mutable.update { it.copy(filterCount = null) }
        previewJob = viewModelScope.launch {
            try {
            delay(150)
            val state = mutable.value
            val job = currentCoroutineContext()
            val count = withContext(io) {
                if ((state.page == OfflinePage.FAVORITES)) savedRows(state.poiQuery, types, sort, state.position).size
                else storeProvider.get().search(PoiSearch(state.poiQuery, types, state.position, sort, pageSize = 1)) { job.ensureActive() }.total
            }
            mutable.update { it.copy(filterCount = count) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { report(failure) }
        }
    }

    fun bookmark(row: OfflineRow) = viewModelScope.launch {
        try {
            val existing = saved.firstOrNull { it.serverId == row.waypoint.serverId }
            if (existing == null) bookmarks.setWaypointStored(row.waypoint) else bookmarks.deleteWaypointStored(existing)
        } catch (failure: Exception) { report(failure) }
    }
    fun open(row: OfflineRow) = viewModelScope.launch {
        try { withTimeout(15_000) { openMap(row.waypoint) } }
        catch (cancelled: TimeoutCancellationException) { mutable.update { it.copy(message = "Karoo map service is unavailable.") } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { report(failure) }
    }

    private fun OfflinePoi.asRow(): OfflineRow {
        val tags = JsonParser.parseString(tagsJson).asJsonObject
        fun tag(key: String) = tags.get(key)?.asString
        val waypoint = saved.firstOrNull { it.serverId == "osm:$osmId" } ?: Waypoint.Discovered("osm:$osmId",
            name.ifBlank { categories.firstOrNull()?.let(PoiTypes::label) ?: "POI" },
            Waypoint.Address(tag("addr:country"), tag("addr:city"), tag("addr:postcode"), tag("addr:street"), tag("addr:housenumber"),
                categories.joinToString(" · ") { PoiTypes.label(it) }, listOfNotNull(tag("name:en"), tag("alt_name"), tag("brand")).joinToString(" ")),
            position.latitude, position.longitude)
        return OfflineRow(waypoint, categories, distanceMetres)
    }

    private fun savedRows(query: String, types: Set<String>, sort: PoiSort, point: PoiPosition?): List<OfflineRow> {
        val words = PoiSearchText.normalize(query).split(' ').filter(String::isNotEmpty)
        val native = PoiSearchText.nativeWords(query)
        if (query.isNotBlank() && words.isEmpty() && native.isEmpty()) return emptyList()
        return saved.map { bookmark ->
            val categories = bookmark.address.qualifier1.orEmpty().split(';', '·').mapNotNull { value ->
                PoiTypes.labels.entries.firstOrNull { it.key == value.trim() || it.value == value.trim() }?.key
            }.toSet()
            val distance = point?.let {
                val a = Math.toRadians(bookmark.latitude - it.latitude); val b = Math.toRadians(bookmark.longitude - it.longitude)
                val h = sin(a/2).pow(2) + cos(Math.toRadians(it.latitude)) * cos(Math.toRadians(bookmark.latitude)) * sin(b/2).pow(2)
                6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
            }
            OfflineRow(bookmark, categories, distance)
        }.filter { row ->
            val text = PoiSearchText.normalize("${row.waypoint.name} ${row.waypoint.address.qualifier2.orEmpty()} ${row.categories.joinToString(" ") { PoiTypes.aliases(it) }}")
            words.all { it in text } && PoiSearchText.matchesNative("${row.waypoint.name} ${row.waypoint.address.qualifier2.orEmpty()}", native) &&
                types.isNotEmpty() && (row.categories.isEmpty() || row.categories.any { it in types })
        }.sortedWith(if (sort == PoiSort.NEAREST && point != null) compareBy<OfflineRow> { it.distanceMetres }.thenBy { it.waypoint.serverId }
            else compareBy<OfflineRow> { PoiSearchText.sortKey(it.waypoint.name) }.thenBy { it.waypoint.serverId })
    }
    private fun recommendations(catalogue: VerifiedCatalogue, point: PoiPosition?) = recommendedRegionIds(catalogue.regions, point, Locale.getDefault().country)
    private fun report(failure: Exception) { if (failure is CancellationException) throw failure; mutable.update { it.copy(message = failure.message ?: "Operation failed. Please try again.") } }
}
