package de.dimskiy.waypoints.platform.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import de.dimskiy.waypoints.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import de.dimskiy.waypoints.platform.ui.components.FloatingBottomAction
import de.dimskiy.waypoints.platform.updates.AppUpdateViewModel
import de.dimskiy.waypoints.platform.updates.UpdateUiState
import de.dimskiy.waypoints.domain.model.Waypoint
import de.dimskiy.waypoints.platform.offline.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

data class OfflineActions(
    val page: (OfflinePage) -> Unit = {}, val regionQuery: (String) -> Unit = {}, val poiQuery: (String) -> Unit = {},
    val select: (Set<String>, Boolean) -> Unit = { _, _ -> }, val clearSelection: () -> Unit = {},
    val refresh: () -> Unit = {}, val update: () -> Unit = {}, val skip: () -> Unit = {},
    val remove: (String) -> Unit = {}, val cancel: (String) -> Unit = {},
    val open: (OfflineRow) -> Unit = {}, val bookmark: (OfflineRow) -> Unit = {}, val more: () -> Unit = {},
    val filters: (Set<String>, PoiSort) -> Unit = { _, _ -> }, val preview: (Set<String>, PoiSort) -> Unit = { _, _ -> },
    val clearMessage: () -> Unit = {}, val swipeHintShown: () -> Unit = {},
)

@Composable
fun OfflineHome(viewModel: OfflineViewModel = hiltViewModel(), updates: AppUpdateViewModel = hiltViewModel(), openPois: Boolean = false) {
    val updateState by updates.state.collectAsState()
    LaunchedEffect(openPois) { if (openPois) viewModel.page(OfflinePage.POIS) }
    val state by viewModel.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { viewModel.refreshPosition(); delay(60_000) }
        }
    }
    OfflineHomeContent(state, OfflineActions(viewModel::page, viewModel::regionQuery, viewModel::poiQuery,
        viewModel::select, viewModel::clearSelection, { viewModel.refreshCatalogue() }, { viewModel.updateRegions() }, { viewModel.skip() },
        { viewModel.remove(it) }, viewModel::cancel, { viewModel.open(it) }, { viewModel.bookmark(it) },
        { viewModel.search(more = true) }, viewModel::applyFilters, viewModel::previewFilters, viewModel::clearMessage, viewModel::swipeHintShown), updateState) { AppUpdateSettings(updates) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineHomeContent(state: OfflineUiState, actions: OfflineActions = OfflineActions(),
    updateState: UpdateUiState = UpdateUiState(), settings: @Composable () -> Unit = {}) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    var filters by remember { mutableStateOf(false) }
    val dismissSearchOnScroll = remember(focus) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y != 0f) focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.clearMessage() }
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.widthIn(max = 240.dp)) {
            Spacer(Modifier.height(8.dp))
            listOf(OfflinePage.POIS to "POIs", OfflinePage.FAVORITES to "Favorites",
                OfflinePage.REGIONS to "Regions", OfflinePage.SETTINGS to "Settings").forEach { (page, label) ->
                NavigationDrawerItem(label = { Text(label) }, selected = state.page == page,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary),
                    onClick = { actions.page(page); scope.launch { drawer.close() } })
            }
            Spacer(Modifier.weight(1f))
            Text("© OpenStreetMap contributors\nODbL 1.0", Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall)
        }
    }) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
            if (state.page == OfflinePage.POIS || state.page == OfflinePage.FAVORITES) FloatingActionButton(onClick = { focus.clearFocus(); filters = true }, shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(56.dp).semantics { contentDescription = "Filters" }) { Icon(painterResource(R.drawable.ic_filter_list), contentDescription = null) }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).nestedScroll(dismissSearchOnScroll)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { focus.clearFocus(); scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Choose page") }
                    if (state.page == OfflinePage.SETTINGS) Text("Settings", style = MaterialTheme.typography.titleMedium)
                    else {
                        val query = if (state.page == OfflinePage.REGIONS) state.regionQuery else state.poiQuery
                        OutlinedTextField(value = query,
                            onValueChange = if (state.page == OfflinePage.REGIONS) actions.regionQuery else actions.poiQuery,
                            shape = CircleShape,
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = {
                                        if (state.page == OfflinePage.REGIONS) actions.regionQuery("") else actions.poiQuery("")
                                    }) { Icon(Icons.Default.Close, "Clear search") }
                                }
                            } else null,
                            singleLine = true, placeholder = { Text(if (state.page == OfflinePage.REGIONS) "Search regions" else if (state.page == OfflinePage.FAVORITES) "Search favorites" else "Search POIs", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) },
                            textStyle = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).height(52.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                        if (state.page == OfflinePage.REGIONS) IconButton(onClick = actions.refresh, enabled = !state.refreshing) {
                            Icon(Icons.Default.Refresh, "Refresh region list")
                        }
                    }
                }
                if (state.page != OfflinePage.SETTINGS && updateState.release != null) TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { actions.page(OfflinePage.SETTINGS) }) {
                    Text("Update available · ${updateState.release.version}")
                }
                if (!state.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
                when (state.page) {
                    OfflinePage.SETTINGS -> settings()
                    OfflinePage.REGIONS -> RegionList(state, actions)
                    else -> PoiList(state, actions)
                }
            }
        }
    }
    if (filters) PoiFilters(state, actions, onDismiss = { filters = false })

}

@Composable
private fun ColumnScope.RegionList(state: OfflineUiState, actions: OfflineActions) {
    val regions = state.catalogue?.regions.orEmpty()
    val installed = state.installed.associateBy { it.id }
    val visible = regions.filter { it.matches(state.regionQuery) }
    val downloaded = visible.filter { it.descriptor.id in installed }
    val remaining = visible.filter { it.descriptor.id !in installed }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    FloatingBottomAction(
        label = if (state.selected.isEmpty()) "Update all downloaded" else "Update selected · ${state.selected.size}",
        onClick = actions.update,
        enabled = state.catalogue != null && (state.selected.isNotEmpty() || state.installed.isNotEmpty()),
        modifier = Modifier.weight(1f)) { scrollPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentPadding = scrollPadding) {
            if (downloaded.isNotEmpty()) item { SectionLabel("Downloaded") }
            items(downloaded, key = { it.descriptor.id }) { RegionRow(it, installed[it.descriptor.id], state, actions) }
            val unlisted = state.installed.filter { stored -> regions.none { it.descriptor.id == stored.id } &&
                PoiSearchText.normalize(stored.name).contains(PoiSearchText.normalize(state.regionQuery)) }
            items(unlisted, key = { "unlisted-${it.id}" }) { stored ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${stored.name} · downloaded\nNot in current catalogue", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { actions.remove(stored.id) }) { Text("Remove") }
                }
            }
            remaining.groupBy { it.continent }.toSortedMap().forEach { (continent, countries) ->
                val continentKey = "continent:$continent"
                item(key = continentKey) { TreeParent(continent, countries.map { it.descriptor.id }.toSet(), state.selected,
                    continentKey in expanded || state.regionQuery.isNotBlank(), actions.select) {
                    expanded = if (continentKey in expanded) expanded - continentKey else expanded + continentKey
                } }
                if (continentKey in expanded || state.regionQuery.isNotBlank()) countries.groupBy { it.country }.toSortedMap().forEach { (country, children) ->
                    val countryKey = "$continent/$country"
                    item(key = countryKey) { Box(Modifier.padding(start = 8.dp)) {
                        TreeParent(country, children.map { it.descriptor.id }.toSet(), state.selected,
                            countryKey in expanded || state.regionQuery.isNotBlank(), actions.select) {
                            expanded = if (countryKey in expanded) expanded - countryKey else expanded + countryKey
                        }
                    } }
                    if (countryKey in expanded || state.regionQuery.isNotBlank()) items(children.sortedBy { it.searchName }, key = { it.descriptor.id }) {
                        Box(Modifier.padding(start = 16.dp)) { RegionRow(it, null, state, actions) }
                    }
                }
            }
            if (visible.isEmpty() && unlisted.isEmpty() && state.ready) item { Text("No matching regions.", Modifier.padding(12.dp)) }
            item { Text(state.catalogue?.attribution ?: "© OpenStreetMap contributors · ODbL 1.0", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun SectionLabel(label: String) { Text(label, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }

@Composable
private fun TreeParent(label: String, ids: Set<String>, selected: Set<String>, expanded: Boolean,
    select: (Set<String>, Boolean) -> Unit, toggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val checked = ids.count { it in selected }
        TriStateCheckbox(state = when (checked) { 0 -> ToggleableState.Off; ids.size -> ToggleableState.On; else -> ToggleableState.Indeterminate },
            onClick = { select(ids, checked != ids.size) }, modifier = Modifier.semantics { contentDescription = "Select $label" })
        Row(Modifier.weight(1f).clickable(onClick = toggle).padding(vertical = 12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(if (expanded) "−" else "+", Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun RegionRow(region: CatalogueRegion, installed: InstalledRegion?, state: OfflineUiState, actions: OfflineActions) {
    val id = region.descriptor.id
    val status = state.downloads[id]
    val busy = status != null && !status.state.isFinished
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = id in state.selected, onCheckedChange = { actions.select(setOf(id), it) },
                modifier = Modifier.semantics { contentDescription = "Select ${region.name}" })
            Column(Modifier.weight(1f)) {
                Text(region.name, style = MaterialTheme.typography.bodyMedium)
                Text("${region.country} · ${"%.1f".format(Locale.ROOT, region.descriptor.downloadBytes / 1_000_000.0)} MB", style = MaterialTheme.typography.labelSmall)
                Text(if (installed == null) "${region.descriptor.poiCount} POIs · ${region.dataDate}" else
                    if (installed.sha256 == region.descriptor.sha256) "Downloaded · ${region.dataDate}" else "Downloaded · update available",
                    style = MaterialTheme.typography.labelSmall)
            }
            if (installed != null && !busy) IconButton(onClick = { actions.remove(id) }, modifier = Modifier.semantics { contentDescription = "Remove ${region.name}" }) { Text("×") }
        }
        if (busy) Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (status!!.state != WorkInfo.State.RUNNING) "Queued" else if (status.phase == "verify") "Checking data…" else "${status.percent}% · downloading", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { actions.cancel(id) }) { Text("Cancel") }
        }
        if (status?.state == WorkInfo.State.FAILED) Text(status.error ?: "Download failed. Select to retry.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.error)
        if (region.qualityReviewRequired) Text("Prototype · source has one omitted area", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiSwipeRow(row: OfflineRow, actions: OfflineActions, showHint: Boolean = false, onHintFinished: () -> Unit = {}) {
    val hintOffset = remember { Animatable(0f) }
    val hintDistance = with(LocalDensity.current) { 48.dp.toPx() }
    var hintInterrupted by remember(showHint) { mutableStateOf(false) }
    val latestHintFinished by rememberUpdatedState(onHintFinished)
    LaunchedEffect(showHint, hintInterrupted) {
        if (!showHint || hintInterrupted) {
            hintOffset.snapTo(0f)
            if (showHint) latestHintFinished()
            return@LaunchedEffect
        }
        // Use the animation clock for the short pause as well as the motion.
        hintOffset.animateTo(0f, tween(350))
        hintOffset.animateTo(-hintDistance, tween(220))
        hintOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 350f))
        latestHintFinished()
    }
    val latestRow by rememberUpdatedState(row)
    val latestBookmark by rememberUpdatedState(actions.bookmark)
    val swipe = rememberSwipeToDismissBoxState()
    val favorite = row.waypoint is Waypoint.Stored
    val cueColor = if (favorite) lerp(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.background, 0.3f)
        else lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant, 0.4f)
    val toggleLabel = if (favorite) "Remove from favorites" else "Add to favorites"
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
            latestBookmark(latestRow)
            swipe.reset()
        }
    }
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = Modifier.testTag("poi-row-${row.waypoint.serverId}"),
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(lerp(MaterialTheme.colorScheme.background, cueColor, 0.12f))
                .clearAndSetSemantics {}.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(if (favorite) "−" else "+", color = cueColor, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Default.Star, contentDescription = null, tint = cueColor, modifier = Modifier.size(18.dp))
                }
            }
        },
    ) {
        Row(Modifier.offset { IntOffset(hintOffset.value.roundToInt(), 0) }
            .testTag("poi-content-${row.waypoint.serverId}")
            .pointerInput(showHint) {
                if (showHint) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    hintInterrupted = true
                }
            }
            .fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .clickable { actions.open(row) }
            .semantics {
                stateDescription = if (favorite) "Favorite" else "Not favorited"
                customActions = listOf(CustomAccessibilityAction(toggleLabel) {
                    actions.bookmark(row)
                    true
                })
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(poiCategoryIcon(row.categories)), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp).size(24.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.waypoint.name, modifier = Modifier.weight(1f, fill = false), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (favorite) Text("★", modifier = Modifier.padding(start = 4.dp),
                        color = Color(0xFFFFD700), style = MaterialTheme.typography.bodyMedium)
                }
                val type = row.categories.firstOrNull()?.let(PoiTypes::label) ?: "POI"
                val address = row.waypoint.address.getFormatted().takeIf(String::isNotBlank)
                val coordinate = "%.4f, %.4f".format(Locale.ROOT, row.waypoint.latitude, row.waypoint.longitude)
                Text(type, style = MaterialTheme.typography.labelSmall)
                Text(listOfNotNull(address, coordinate).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            }
            row.distanceMetres?.let { metres ->
                val distance = if (metres < 1000) "${metres.toInt()}m" else "${"%.1f".format(Locale.ROOT, metres / 1000)}km"
                Text(distance, modifier = Modifier.padding(start = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ColumnScope.PoiList(state: OfflineUiState, actions: OfflineActions) {
    val listState = rememberLazyListState()
    var hintRow by remember { mutableStateOf<String?>(null) }
    var hintAttempted by remember { mutableStateOf(false) }
    val firstRow = state.rows.firstOrNull()?.waypoint?.serverId
    LaunchedEffect(state.page, state.ready, state.favoritesLoaded, state.hasFavorites, state.swipeHintSeen, firstRow) {
        if (state.page != OfflinePage.POIS || state.hasFavorites) hintRow = null
        else if (state.ready && state.favoritesLoaded && !state.swipeHintSeen && firstRow != null && !hintAttempted) {
            hintAttempted = true
            hintRow = firstRow
            actions.swipeHintShown()
        }
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    // Request once per loaded prefix; failures retain the manual retry below.
    var requestedSize by remember(state.poiQuery, state.types, state.sort, state.page, state.installed) { mutableStateOf(-1) }
    LaunchedEffect(nearEnd, state.rows.size, state.searching, state.hasMore) {
        if (nearEnd && state.hasMore && !state.searching && requestedSize != state.rows.size) {
            requestedSize = state.rows.size
            actions.more()
        }
    }
    if (state.needsRegionDownload()) Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.page(OfflinePage.REGIONS) }
            .semantics { contentDescription = "Choose regions" }
            .heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.installed.isEmpty()) "Download regions for POIs" else "Download this region",
            Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
    }
    if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("poi-list"), state = listState, contentPadding = PaddingValues(bottom = 80.dp)) {
        items(state.rows, key = { it.waypoint.serverId }) { row ->
            PoiSwipeRow(row, actions, showHint = hintRow == row.waypoint.serverId, onHintFinished = { hintRow = null })
            HorizontalDivider()
        }
        if (state.rows.isEmpty() && !state.searching && state.ready) item {
            Column(Modifier.padding(12.dp)) {
                val filteredFavorites = state.poiQuery.isNotBlank() ||
                    (state.types != PoiTypes.defaults && !state.types.containsAll(PoiTypes.labels.keys))
                val message = when {
                    state.page != OfflinePage.FAVORITES -> "No matching downloaded POIs."
                    filteredFavorites -> "No matching favorite POIs."
                    else -> "No favorites added yet."
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.hasMore) item { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = actions.more, enabled = !state.searching) { Text("Load more · ${state.total} matches") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoiFilters(state: OfflineUiState, actions: OfflineActions, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(state.types) }
    var sort by remember { mutableStateOf(state.sort) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(selected, sort) { actions.preview(selected, sort) }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("poi-filter-sheet"), sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(.9f).padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = sort == PoiSort.NEAREST, onClick = { sort = PoiSort.NEAREST }, label = { Text("Nearest") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = sort == PoiSort.NAME, onClick = { sort = PoiSort.NAME }, label = { Text("Name A–Z") })
            }
            if (state.position == null && sort == PoiSort.NEAREST) Text("No position: sorted by name.", style = MaterialTheme.typography.labelSmall)
            LazyColumn(Modifier.weight(1f)) {
                PoiTypes.labels.keys.groupBy { it.substringBefore('.') }.forEach { (group, types) ->
                    item(key = group) { TreeParent(PoiTypes.groups[group] ?: group, types.toSet(), selected, group in expanded,
                        { ids, check -> selected = if (check) selected + ids else selected - ids }) {
                        expanded = if (group in expanded) expanded - group else expanded + group
                    } }
                    if (group in expanded) items(types, key = { it }) { type ->
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = type in selected, onCheckedChange = { checked -> selected = if (checked) selected + type else selected - type },
                                modifier = Modifier.semantics { contentDescription = PoiTypes.label(type) })
                            Text(PoiTypes.label(type), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { selected = PoiTypes.defaults; sort = PoiSort.NEAREST }) { Text("Reset") }
                Button(onClick = { actions.filters(selected, sort); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(state.filterCount?.let { "Apply · $it POIs" } ?: "Apply")
                }
            }
        }
    }
}
