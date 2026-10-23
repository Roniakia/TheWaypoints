# TheWaypoints: project notes

Inspected 2026-10-21 at baseline `3f3ed9c` (`master`). These are source-based
observations, not a claim that the app has been executed successfully.

## Purpose and current scope

Product direction: develop an independently evolving app with substantial future
changes. The upstream project supplied the initial idea and starting codebase;
retain original-author credit and applicable license/copyright notices. The
GitHub fork relationship describes provenance, not the intended product scope.

Android launcher app integrated with Hammerhead Karoo. Development 0.96 searches
downloaded regional POI packs and local Favorites, sorts by name or device
distance, and opens pins in Karoo's native map. Stable 0.95 also included Photon
search; that app entry has been removed in development. The app
itself does not calculate routes or render a navigation map.

## Build and packaging

| Item | Repository value |
| --- | --- |
| Module / application ID | `app` / `io.github.roniakia.waypoints` |
| Kotlin namespace | `de.dimskiy.waypoints` (internal source namespace retained) |
| Development version | `0.99`, code `12` in `gradle.properties`; stable is 0.98 (10) |
| Gradle / Android plugin | `8.13` / `8.9.2` |
| Kotlin / KSP | `2.1.10` / `2.1.10-1.0.31` |
| Android SDK | min 23, compile/target 35 |
| karoo-ext | `1.1.9` (local unit tests and debug assembly verified 2026-10-21) |
| UI | Compose, Material 3, Navigation Compose |
| Persistence | Room 2.8.5; DataStore preferences 1.2.1 |
| DI | Hilt 2.56 |
| Build JVM baseline | JDK 17 used by CI; Java/Kotlin bytecode target 1.8 |

KSP2 is explicitly enabled via `ksp.useKSP2=true`; newer Room processing failed
with a serialization linkage error under KSP1. Room schema history and database
version remain unchanged. Serialization JSON 1.8.1 is explicitly shared by the
app and Room migration tests to avoid a runtime mismatch with Karoo's older
transitive serialization dependency.

`gradle/libs.versions.toml` is the dependency catalog. Its explicit warning says
Lifecycle 2.9.0 plus Compose BOM 2025.05.00 caused search focus issues; current
pins are Lifecycle 2.8.7 and Compose BOM 2024.03.00.

Variants: debug, release, stage, profiling. Stage/profiling inherit release
behavior but use the debug signing key; they are not equivalent to debug.
All normal variants share the application ID. The optional
`-PlocalBuild=true` mode enables debug only with suffix `.local`. Release signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD`.

`settings.gradle.kts` fetches Karoo SDK from GitHub Packages using `GITHUB_USER`
and `GITHUB_TOKEN`; other repositories include JitPack, Google, Maven Central.
Firebase, Analytics, Crashlytics, reporting interfaces/events, and CI Firebase
configuration steps have been removed. Normal builds require no Firebase config.
`-PlocalBuild=true` remains only as a separate-ID debug convenience; every variant
is telemetry-free. Local Timber logs remain on the device. Photon online search
and Karoo service integration are the app's functional external boundaries; no
telemetry replacement was introduced. The local mode is not a service simulator.

Version metadata is maintained with the personal `waypoints-versioning` skill.
Current stable version is 0.94 (5), including the dependency/build updates.
The user accepted Beta 1 on a real Karoo and authorized stable promotion on
2026-10-21. The release retains tag `v0.94-beta.1`, commit
`620218cae8781499108cf1b241f39aad54bfeb1c`, and the identical signed APK.
APK SHA-256: `fedac57e7149e0c11edd7a05ba919712d2e18449691f3dc8bc2d39f8ae83f968`.
Release metadata records acceptance; build-info.json preserves its original
build-time pending status. Device model/OS were not specified.

## Source map

All paths below are relative to `app/src/main/kotlin/de/dimskiy/waypoints/`.

| Area | Main files | Responsibility |
| --- | --- | --- |
| Startup | `platform/App.kt`, `platform/LoggingInitializer.kt`, `platform/ui/MainActivity.kt`, `platform/ui/SplashScreenViewModel.kt` | Hilt app, logging, single screen navigation; splash waits for storage emission plus 200 ms |
| Presentation | `platform/ui/screens/waypointslist/` | Search widget, bookmarks, state, user intents, ViewModel |
| Shared UI | `platform/ui/components/`, `platform/ui/theme/`, `platform/ui/ComposeHelpers.kt` | Rows/swipes, placeholders, navigation overlay, theme, units formatting and Karoo 2 previews |
| Search coordination | `domain/cases/ObserveSearchResultsCase.kt` | Location selection, search, reactive bookmark substitution |
| Search provider | `domain/providers/photonservice/` | Photon DTOs, API interface, mapping and request timeout |
| Device adapters | `platform/karooservices/`, `platform/network/KarooPhotonApiService.kt` | Karoo connection, GPS, units and HTTP |
| Persistence | `platform/storage/`, `domain/waypointsrepository/` | DAO/entity/domain mapping and Room migration |
| Settings | `platform/settings/SettingsProviderImpl.kt` | Last location JSON and geo-search flag |
| Wiring | `domain/di/DomainModule.kt`, `platform/di/` | Provider bindings, database, dispatchers, clock |

The domain directory is not a pure Kotlin module: it imports Android Context,
platform DI/storage/UI models, and Karoo SDK classes in `OpenExternalMap`.
This matters when selecting test boundaries.

## Photon online-search behavior and data flow

1. Search is submitted explicitly from `SearchWidget`; typing alone does not
   call the service. Input language becomes the HTTP `Accept-Language` header.
2. ViewModel cancels its previous search job when a new search starts; an empty
   query cancels and clears results. Settings default geo-search to false.
3. `ObserveSearchResultsCase` reacts to the geo-search setting. With it enabled,
   a cached location younger than ten minutes is reused; otherwise the location
   provider is consulted and successful locations are saved in DataStore.
4. Photon search requests up to 30 results with a 20-second timeout. Geo requests
   pass zoom 14 and location bias scale 0.0 to `https://photon.komoot.io/`.
   HTTP is delegated to Karoo `OnHttpResponse`, supporting device connectivity.
5. Mapping requires an OSM ID and name; GeoJSON coordinates are longitude first.
   Results are joined with stored POIs by `serverId`, so bookmarking updates the
   displayed type reactively without another network search.
6. Global results sort by name. Geo results use great-circle distance from the
   saved device location and sort by distance; these are not riding distances.
   Units come from the Karoo user profile. Saved bookmarks themselves are
   alphabetized by the DAO, not continuously sorted by live GPS distance.
7. Bookmark toggling inserts a discovered POI or deletes a stored one. Selecting
   a POI dispatches `LaunchPinDrop(Symbol.POI(...))` to the native Karoo map.

`DataResult` represents Loading, Ready, or Error; `DomainError` distinguishes
network, location and environment failures.

## Storage

### Regional offline POIs (0.95, Beta 1 code 6 / Beta 2 code 7)

The generator remains on separate branch `codex/poi-data-pipeline`
([PR #2](https://github.com/Roniakia/TheWaypoints/pull/2)); the Android feature is
on `codex/offline-poi-regions` ([PR #3](https://github.com/Roniakia/TheWaypoints/pull/3)).
The published signed prototype catalogue currently contains only Skåne.

`OfflineHome` replaces the launcher content with compact POIs/Favorites/Regions screens,
a menu beside search, first-run invitation, downloaded-first region hierarchy,
local position/locale recommendations, persistent hidden selections, and
expandable category filters. Online Photon search was a secondary action in 0.95; it is removed in 0.96.
No offline map renderer or routing is included. New UI is English; offline
straight-line distances currently use metric units. Karoo device validation is
pending.

`PoiCatalogueRepository` verifies Ed25519 signatures with pinned-key Tink 1.18.0
before parsing; a signed bundled seed and atomic cache survive network failures.
Discovery inspects the latest 100 public GitHub releases for `poi-data-*` assets.
Catalogue size is capped at 8 MiB. GitHub API limits can delay refreshes; the last
verified catalogue remains usable. Only repository release URLs and approved
GitHub redirect hosts are accepted. Packs use direct device internet, independently
of the Karoo HTTP proxy used for Photon.

WorkManager 2.10.5 runs connected, uniquely named region downloads with bounded
retries, progress, cancellation and Range-based partial-file recovery. Matching
revision/hash downloads and older catalogue dates are skipped. Explicit removal
cancels the install generation before deleting data. No periodic network polling
is added. Position refresh runs once per minute only while the screen is started.

`RegionPackStore` verifies size/SHA-256, exact ZIP entries, extraction limits,
metadata, schema, row counts and integrity before switching a synced AtomicFile
snapshot pointer. Failure/cancellation preserves the old snapshot. Startup
restores pointer backups and removes orphan snapshots. A local hash receipt
supports unchanged checks. Store calls run on injected IO dispatchers.

Search opens snapshots read-only, binds FTS4/category queries, deduplicates typed
OSM identities across regions and retains the requested page prefix while
counting matches. Nearest ordering uses great-circle distance with name fallback.
There is no 30-result cutoff. English transliterations/category aliases are
provided by the generator; native-script tokens scan original names/tags.
Full multilingual transliteration is limited by the generated aliases. Match
identity memory and native-script scans should be benchmarked on larger packs.

Stock API 35 SQLite lacks RTree: the optional desktop `poi_geo` table is ignored.
FTS4 integrity validation requires a writable staging connection; installed
searches remain read-only. New bookmark IDs include OSM object type. Legacy
Photon numeric IDs are preserved without guessing types, so cross-source legacy
duplicates are possible. Pack removal never deletes Room bookmarks. No Room
schema or migration changes are required.

Room database `app_database`, version 2, table `waypoints`. Autogenerated local
ID, unique `serverId`, server type (currently PHOTON), name, address fields,
latitude/longitude. Insert uses REPLACE; duplicate server IDs replace rows.
The unique index covers serverId only, despite DAO lookup also accepting type.

Migration 1 → 2 rebuilds the table, preserves existing values, adds nullable
`house` initialized to NULL, and recreates the unique index. Exported schemas
are under `app/schemas/`. Preferences file is `location_settings`.

## Integration and delivery

Manifest declares a launcher activity, INTERNET permission, extension metadata,
and this repository's release manifest URL; it declares `PoiFieldExtension`, a custom
`KarooExtension` service for the graphical Nearest POI and Selected POI ride data fields. UI resources include English,
German, Spanish, French, and Italian strings. Preview configuration targets
Karoo 2 at 480×800 pixels / 295 dpi.

`assembleRelease` finalizes tasks generating the external Karoo manifest and
copying an icon under `app/build/outputs/apk/release/assets/`; description comes
from the README description. Versioned changelogs live in `RELEASE_NOTES.md`;
the README links to that file. Historical entries are preserved.

Both GitHub workflows are manual (`workflow_dispatch`), run variant-specific
unit tests, and publish release assets. Instrumentation is not active there.
**Release-workflow.yml commits and pushes version changes.** Do not dispatch it
without explicit user authorization. Stage publishes a prerelease as well.

### Intended beta-first delivery

After meaningful work and successful local checks, propose a tagged GitHub beta
prerelease with a downloadable APK and device-test instructions. Publish only
after the user accepts the proposal; the user performs real-device testing.
Record the candidate tag, source commit, APK checksum, app version/code, signing
identity, changelog, and the user's acceptance of the results.

After explicit acceptance and release authorization, promote the latest accepted
beta to stable using the same tested APK and source commit. Do not rebuild,
increment app versions, or replace assets during promotion. Changed distributed
beta candidates need unique tags and increasing version codes. If stable tagging
is used, point it to that same commit and reuse the identical APK.

This is the desired workflow, not implemented release automation. `main` now
requires PRs with no bypass; the existing workflow's direct commit/push conflicts
with that rule. Adapt release preparation and beta promotion before dispatching
publishing workflows. Do not equate general positive feedback with acceptance
of a specific beta or authorization to publish it as stable.

## Review targets before implementation

These are static review findings to investigate with focused tests, not
verified device failures or changes made during this analysis.

- Service connection wait has no timeout. The units request can wait indefinitely;
  geo-enabled ViewModel processing asks for units even for an empty ready list.
  A stock emulator may therefore stall state updates without Karoo services.
- Profile consumer cancellation and HTTP consumer timeout/cancellation cleanup
  still need focused coverage. GPS consumers now clean up with callbackFlow.
- GPS now times out connection plus location work, returns fallback outside the
  timeout context, and cleans up callback consumers on cancellation. Duplicate
  callbacks are covered by focused JVM tests.
- Service reference counting increments/decrements under a mutex, while the
  connection/disconnection decisions occur outside it; concurrency deserves tests.
- Photon provider catches broad Exception, including ordinary coroutine
  cancellation. DTO coordinate indexing and mapping happen outside the API
  exception wrapper, so malformed coordinates can escape as exceptions.
- Search bookmark matching and unique storage IDs omit OSM object type; investigate
  possible identity collisions before introducing another POI source.
- Persistence JSON decoding and database failures have no explicit recovery UI.
- Release manifest generation tracks its README, template, app ID and version
  inputs so changed beta metadata regenerates correctly.

## References

Local source is authoritative for this pinned app. Current upstream docs may
cover newer SDK versions and must not be assumed identical to the pinned version.

- [Hammerhead SDK](https://github.com/hammerheadnav/karoo-ext)
- [Hammerhead SDK API](https://hammerheadnav.github.io/karoo-ext/index.html)
- [Android testing in Studio](https://developer.android.com/studio/test/test-in-android-studio)
- Local testing details: [LOCAL_TESTING.md](LOCAL_TESTING.md)

## Independent app identity

Application and Karoo extension ID are `io.github.roniakia.waypoints`; the
internal Kotlin namespace is retained to avoid an unrelated source rename.
The app installs alongside upstream and starts with independent storage.
Update metadata points to this repository. A dedicated private signing key is
kept outside the repository; preserve a private backup for future updates.

## Beta 2 update metadata correction

`APP_RELEASE_TAG=v0.95-beta.2` binds the APK's MANIFEST_URL and generated Karoo
manifest asset URLs to the same immutable release. Candidate APK filenames are
versioned (`waypoints-0.95-beta.2.apk`). The generator tracks these values as
task inputs. Without a release tag the rolling stable /latest/download behavior
is retained; publishing a tagged candidate requires its matching tag/property.
Beta 1 incorrectly embedded the stable update URL. The user's device showed
0.95 briefly and then 0.94; metadata refresh is the suspected mechanism, not a
verified trace of Karoo internals. This build removes that mismatch. It does not
change APK identity/signing or Room storage. Stable 0.94 remains unchanged.

## Stable 0.95 acceptance (2026-10-21)

The user reported Beta 2 works on their Karoo and explicitly authorized release,
accepting remaining UI roughness for the pre-1.0 app. Model/KOS were not given.
Promoted the existing release to stable/latest, retaining tag `v0.95-beta.2`,
source `45018deb21988b2e2b41e18be7472351bb2bbb56`, version 0.95/code 7,
and identical APK SHA-256
`0253fb271115a6d0e6385be87aedb6687e9039a396c02dd66de98c220a676fce`.
No rebuild, asset replacement or tag move occurred. Build-time records attached
to the release retain their historical pending status; release metadata and this
record capture subsequent user acceptance. No new device compatibility claims
or comprehensive coverage of all test steps are inferred from the acceptance.

## Server prototype and early Android milestones (historical)

Agreed scope is recorded in [OFFLINE_POI_PLAN.md](OFFLINE_POI_PLAN.md).
Server-only prototype lives in `server/poi_packs` on `codex/poi-data-pipeline`;
Android implementation must use a separate branch. Local OSM parsing, category
mapping, normalized FTS4 search, optional host RTree, and immutable ZIP generation
are implemented. The signed data prerelease `poi-data-2026-10-prototype.2` is
published with seven verified assets. The separate Ed25519 data public key is
tracked at `server/poi_packs/config/poi-data-public.pem`; its PEM SHA-256 is
`43294ee8ec4b93e2b219a03cca29d23bab5830aa16d0ae39da8468555f05c28f`.
Private key material stays outside Git. At that publication milestone the app
update manifest was 0.94 (5).

The real Skåne prototype contains 19,159 POIs in a 2.32 MB download; generation
took 146.512 seconds with 2.89 GB peak RSS. Full measurements and source-quality
limitations: [OFFLINE_POI_PROTOTYPE.md](OFFLINE_POI_PROTOTYPE.md).

Android storage work is separate on `codex/offline-poi-regions`, draft PR #3,
commit `ae78419a9af5ee325e59f6d3de20716f1d9917ae`, reserving unreleased 0.95 (6).
Its real-pack compatibility and 17 JVM/25 API 35 tests plus lint pass. Stock Android
lacks RTree; the client reads ordinary coordinates/FTS4. Writable integrity checks
run only on staging copies. At that foundation milestone, signature/download lifecycle and compact screens
were pending. The later Android implementation and checks are described above.

## TheWaypoints rename (unreleased 0.96 / 8)

Repository renamed to `Roniakia/TheWaypoints`. Launcher label, Karoo listing,
Gradle project name and maintained links use TheWaypoints. Application ID and
internal Kotlin namespace stay unchanged to preserve updates and local data.
Original-author credit and license notices remain. Published signed catalogues
and historical APKs retain their original bytes and may contain the former URL;
the repository redirect supports those historical links. Local checkout folder
remains `WaypointsKaroo` so existing workspace attachments keep working.

## Search and scrolling refinement (unreleased 0.96 / 8)

POI and region search fields use capsule outlines with inline clear actions.
The POI header spans the available width; saved POIs have a Favorites destination.
POIs request the next page near the list end, once per loaded prefix, while
retaining Load more for manual retry after an error.

The Regions download/update action floats at the bottom via Scaffold, spans
the screen with 16 dp side margins and includes the selected-region count; the region list reserves bottom space to keep its final
items reachable.

## Favorites page (unreleased 0.96 / 8)

Favorites is a dedicated drawer destination backed by Room bookmarks. It reuses
the POI list, clearable search, category/sort sheet and automatic paging. Each
POI destination remembers its own search/filter choices for the ViewModel lifetime.
Unfavoriting refreshes the Favorites results reactively. Region removal does not
delete favorites; no storage migration is needed. The previous saved-only chip
is replaced by this destination. Legacy bookmarks without categories remain
included when at least one category is selected, as in the previous saved-only view.

## Region download guidance (unreleased 0.96 / 8)

The launcher starts on POIs. Download guidance appears only there, after loading,
when no packs are installed or a catalogued region containing the current
position has no installed coverage. Polygon holes are excluded. Unknown location
or location outside the catalogue does not suggest a nearby region when packs
are already installed. Empty search results alone never prompt downloads.
Regions has neither the invitation nor a Recommended section; its downloaded
section and geographic hierarchy remain. Favorites never shows download guidance.

## Pack-only search (unreleased 0.96 / 8)

Removed the Search online footer and Photon search dialog from OfflineHome.
The launcher exposes only downloaded-pack POIs, local Favorites and Regions.
Catalogue refreshes/downloads remain network operations; POI queries stay local.
Legacy Photon implementation and its regression tests remain internal, without
an app navigation entry. No bookmark data, Room schema or dependency changed.

## POI category icons (unreleased 0.96 / 8)

POIs and Favorites share local vector category icons on the left of each row.
All category groups have an icon, cafes have a cup, and unknown/legacy categories
use a location pin. Multiple categories resolve in stable catalogue order.
Icons use theme colors and open the same POI as its title; bookmarking remains
on the right. No network asset, new dependency or storage change is required.

## App update channels (unreleased 0.96 / 8)

Settings is a new drawer destination. DataStore persists Stable (default) or
Beta. The updater checks the latest 100 repository releases, ignores drafts and
POI-data releases, reads each eligible immutable Karoo manifest, and selects the
highest numeric versionCode. Stable excludes prereleases; Beta includes both.
Promotion's retained beta tag is supported through GitHub's prerelease flag.
Historical repository aliases in published immutable manifests are accepted.
Switching to Stable never downgrades; it waits for a higher stable versionCode.

Checks run on launch at most once per 24 hours, including failed attempts, and
explicitly when Settings opens or the user presses Check for updates. Channel
changes check immediately. Successful check time is displayed; errors allow a
manual retry. An available update adds a Settings link above the current page.
No periodic worker, telemetry, silent download or silent installation is added.

APK downloads use the bounded HTTPS transport and private, per-attempt cache
files. Release URLs must reference this repository/tag, with an APK asset digest
and a maximum 200 MiB size. Before installation, the app checks exact size,
SHA-256, package name, versionName/versionCode and the installed app's signing
certificate(s). It rechecks the file before granting a scoped FileProvider URI to
the system installer. REQUEST_INSTALL_PACKAGES is declared; Android 8+ may ask
for permission via system settings. Installation still needs system confirmation.
A selectable APK link supports Companion fallback when the installer/settings
activity is unavailable. Native Karoo behavior requires actual device testing.

Local `.local` builds can check production release metadata but cannot download
production updates over their different package identity. The Settings UI and
installer handoff are tested separately with local fixtures. The existing static
MANIFEST_URL remains pinned for Karoo's own app metadata; channel selection is
owned by our updater. Future releases must include manifest-karoo.json and a
matching immutable APK asset with GitHub SHA-256 digest. The legacy committing
release workflow is not used. Version 0.96 / 8 is reused for this cohesive batch.

Settings channel selection uses a single radio group. Check for updates floats
at the bottom with 16 dp side/bottom margins, matching the Regions action. Its
scrolling content uses trailing padding so final settings can clear the action.

## Shared floating bottom action rule

Regions and Settings use FloatingBottomAction: a capsule button with 16 dp
side/bottom margins, no visible wrapper or shadow. Content scrolls behind the
action; measured trailing scroll padding lets final items clear the button.
This replaces fixed viewport insets that looked like a footer panel. Counts and
loading labels stay inside the button. The circular POI filter FAB is excluded.
The rule is also recorded in AGENTS.md for future screens.

The shared bottom action includes a subtle linear theme-background fade: 24%
opacity at the bottom, transparent at the measured button top edge. It follows
the button height and bottom margin, with no panel, shadow or touch interception.

## Karoo yellow accents (unreleased 0.96 / 8)

Light and dark themes use Hammerhead Lemon Yellow (#FFE900) for primary/tertiary
accents, with black content on filled yellow buttons. Selected containers use
yellow tints instead of Material's default purple. Text-only actions use onSurface
for readability on white; surface variants are neutral. Source: Hammerhead's
brand guide at https://www.sram.com/globalassets/publicsites/logo-guidelines/h_brandstyleguide.pdf.

## 0.96 Beta 1 preparation (2026-10-22)

The user authorized a beta prerelease and PR. Candidate v0.96-beta.1 reuses the
unreleased 0.96/code 8 batch, above stable 0.95/code 7. APP_RELEASE_TAG pins
packaged Karoo metadata and generated assets to the same immutable release.
The existing signing certificate and package ID are retained. Stable remains
unchanged; device acceptance is pending. Exact source/hash are recorded in
release build-info.json and SHA256SUMS. Source changes target main via a PR;
no legacy publishing workflow or direct push to main is used.

## Stable 0.96 acceptance (2026-10-22)

The user reported Beta 1 works well and authorized release. Promote the existing
release to stable/latest while retaining tag v0.96-beta.1 and source
a9bcdbad60f9a5a1633ac66941db66a0a4bbba71. The exact accepted APK is version
0.96/code 8, SHA-256 1c0bda0909b35a1fda3e221a2187a7b9d6d261063be5d7c420061eb22d1b660c,
signed by the established certificate ce73d47787626c776d4d578d62c98a7de4eb814bf5b701a58679df8ffdb002f6.
No rebuild, asset replacement, tag move or version increment is needed.
Original build-info/test assets retain their build-time pending status; release
metadata and this record capture later acceptance. Model/KOS were not supplied,
and comprehensive validation of every test step or device is not inferred.
Acceptance documentation goes through PR #5 alongside the feature source.

## POI distance labels (unreleased 0.97 / 9)

POIs and Favorites show the existing straight-line distance in a separate compact
Hammerhead yellow badge with black text (`100m`, `1.2km`). Unknown distances stay
hidden; existing location refresh and sorting behavior are unchanged. Development
builds no longer point at the previously distributed 0.96 beta manifest.

Search header refinement: POIs, Favorites and Regions share 52 dp capsule inputs
with 8 dp horizontal/vertical header margins so borders clear adjacent edges.
Menu, clear and refresh controls remain centered with their existing touch targets.

Saved POI stars use gold (#FFD700). User scroll gestures clear search focus and
dismiss the keyboard across page lists; tapping an input focuses it again.

Region download guidance uses a Karoo yellow forward arrow retaining the
Choose regions accessibility label. FloatingBottomAction precomposites its
disabled gray fill over opaque theme background; content cannot show through
the disabled capsule on Regions or Settings. The surrounding fade is unchanged.

Sidebar refinement (0.97 / 9): navigation items use 12 dp side margins, 3 dp
vertical spacing and 6 dp corners. Selected items use full Karoo yellow with
black text in both themes. The four destinations share this styling.

Region guidance uses one-line copy: Download regions for POIs / Download this
region, with ellipsis for unusually large font settings or narrow screens.

Region guidance is a full-width, minimum 48 dp clickable row. Its yellow arrow
follows the one-line text with an 8 dp gap; the whole row opens Regions.

Favorites empty state: No favorites added yet for blank search and default/all
categories; No matching favorite POIs for a search or custom category restriction
(including no active categories). Sort alone does not affect the message. Removed
the Browse POIs action. Version remains in the pending 0.97 / 9 UI batch.

POI/Favorites rows now put the distance badge before the name, category on its
own line (POI fallback), and address · latitude, longitude below. Coordinates
always show, including when distance/address are available; absent addresses do
not add a leading separator. Detail text wraps to preserve address/coordinates.

Address formatting now uses street + house number, city (for example
Bogesholmsvägen 1, Kävlinge), omitting country codes and bracket syntax. Blank
parts are trimmed/omitted; coordinates remain available for missing addresses.
Only presentation changes; pack tags and stored address fields are preserved.

## 0.97 Beta 1 candidate (2026-10-23)

Candidate v0.97-beta.1 is 0.97/code 9, with the established signing identity and
candidate-specific update URLs. Changes are confined to POI/address presentation
and UI refinements; no dependency/storage/data-pack change is included. Stable
0.96 remains unchanged and real Karoo acceptance is pending. Candidate identity
is recorded in release build-info.json/SHA256SUMS and LOCAL_TESTING.md.

## Release notes location

Version history moved intact from README.md to root RELEASE_NOTES.md. README
links to that file and AGENTS.md points changelog maintenance there. This is a
documentation-only change; app version and published beta assets are unchanged.

## Settings update action (unreleased 0.98 / 10)

The shared floating action checks when no release is offered, downloads and opens
Android installation for an available release, and retries installation for an
already verified APK. The separate inline download/install buttons are removed.
Download progress disables the same action; cancellation remains in the scroll
content. Channel changes still clear the previous candidate before checking.
Local-ID production installation remains blocked; candidate 0.97 is unchanged.

## Manual beta delivery

Prepare beta means local packaging under app/build/beta/<tag>/ with upload/
artifacts, copyable GITHUB_RELEASE.md, and publication/promotion instructions.
No automatic GitHub tagging/upload/publication. Record uncommitted source honestly
and finalize provenance after commit. See RELEASING.md.

## Nearest POI ride field (unreleased 0.99 / 11)

Feature branch: codex/karoo-poi-field in an isolated managed worktree. Existing
uncommitted stable-release documentation in the original checkout is preserved.
The exported KarooExtension service registers two graphical DataTypes using the
existing extension ID. RemoteViews hides the standard header and centers a small
map pin above a category icon and distance, designed for one profile cell.
Karoo profile layout controls the actual field allocation; larger cells also work.

A field view owns a separate Karoo connection and location consumer, removed on
emitter cancellation. Searches run on the injected IO dispatcher every five
seconds and use the existing region-pack store with cancellation checks. All
downloaded categories participate, independently of app filters; Favorites do not.
Distance uses metric straight-line formatting, consistent with the offline list.
No GPS fix within 30 seconds shows No GPS; missing/empty packs show No POIs.
The editor preview does not connect to Karoo or open packs. Whole-cell PendingIntent
uses CLEAR_TOP to recreate MainActivity and explicitly select POIs, including when
the app was previously on Settings/Favorites/Regions. No Room/dependency changes.

The current nearest search scans installed POIs; five-second polling should be
benchmarked with large multi-region datasets on hardware. Stock Android cannot
validate Karoo ride-cell sizing, service binding, live fixes or in-ride launching.

### Selected POI follow-up (same 0.99 / 11 batch)

SelectedPoiRepository stores a destination snapshot (name, coordinates, categories,
unique selection token) in the existing DataStore. POI/Favorite taps persist this
selection before launching the native map; a map-service timeout retains it.
No Room schema changes. It remains independent of pack removal or unbookmarking.
The selected field uses the same single-cell layout and POIs launch action, with
Select POI for an empty target and its category icon plus No GPS without a fresh
fix. The nearest field remains independent of the selected destination.

Arrival confirmation requires two different location-event timestamps within
50 metres, checked on the field's five-second refresh. Reusing a cached fix
cannot confirm arrival. An outside/missing/stale fix resets confirmation. A new
selection token resets confirmation even for the same coordinates. Clearing is
an atomic DataStore comparison against that token, preserving a later selection.
Monitoring runs while the selected field view is active; it does not add an
always-on background location service. Passing the destination while the field
is inactive is not recorded. Real-device GPS/arrival behavior remains unverified.

Validation: all 58 debug JVM tests, 12 focused API 35 emulator tests, debug lint
and debug assembly passed. The 160x100 RemoteViews preview was visually inspected.
Details and real-device beta scenarios are recorded in LOCAL_TESTING.md. No
commit, publication or physical device installation occurred.

## 0.99 Beta 1 candidate preparation (2026-10-23)

For v0.99-beta.1, all 58 release JVM tests passed, along with release lint and
optimized signed assembly. The earlier 58 debug JVM and 12 focused local-ID
API 35 emulator tests also passed. Full emulator instrumentation was not rerun;
stock Android does not exercise live Karoo ride-screen binding or arrival fixes.
Version 0.99/code 11 uses the established signing identity and immutable candidate
URLs. Public package metadata, certificate and SHA-256 are verified before upload
and recorded with the exact source commit in the beta build-info.json.

Source/version preparation goes through a PR to main. The beta is published from
its exact feature commit; stable 0.98 remains unchanged. Real Karoo acceptance is
pending. No physical device installation or legacy release workflow is used.

## Beta 1 device feedback and native fields (0.99 Beta 2 / code 12, unreleased)

The user tested Beta 1 on a Karoo 3 with their latest OS and reported that live
ride taps did not open the app, the graphical field used two cells, and no GPS
was available near a window. An exact KOS build was not supplied. Beta 1 is not
accepted for stable promotion. Its tag/APK remain immutable.

The user chose native one-cell distance fields with an Open POIs controller
action instead of the custom icon layout. Both existing type IDs are retained
but registered graphical=false. startStream emits FIELD_SINGLE in metres;
startView configures native DISTANCE formatting and explicit missing-data messages.
Karoo handles metric/imperial presentation, headers and cell sizing. Custom
RemoteViews and their tap action are removed. The pinned SDK declares custom
views graphical and exposes no minimum-width attribute. Actual native cell
placement remains a firmware/device check; the Android emulator cannot prove it.

Open POIs is a BonusAction routed by the extension to MainActivity's POIs intent.
It is a controller binding, not a tap handler on a native numeric field. Users
may need to remove/re-add old fields after upgrading to refresh their host layout.
The editor uses Karoo's own numeric sample treatment rather than our old preview.

GPS collection has an independently cancellable connection per native stream.
Missing/disconnected/stale (>30s) GPS yields No GPS; nearest searches need packs,
and no selected target yields Select POI without GPS or any pack read. Exceptions
are locally logged and do not fabricate a location or zero distance. No permission
or external location fallback is added. Selecting a destination from POIs/Favorites
still persists before native-map launch, independently of a GPS fix.

Arrival monitoring follows the native stream subscription, rather than a graphical
view. It only observes fixes while that stream is subscribed; no always-on location
service is added. Shared messages between the native view and stream are StateFlow.
The Open POIs controller action and native cell allocation need hardware retesting.

References: pinned DataTypeImpl, ViewEvent and official sample Extension BonusAction
implementation in hammerheadnav/karoo-ext 1.1.9. Native editor sample limitations are
tracked in SDK issue #93. The prior preview-click emulator test is replaced by
native stream/lifecycle and controller-intent coverage; it did not establish that
Beta 1’s ride-screen tap worked. No dependency or Room schema changes.

Native-field correction validation: 62 debug JVM tests, 11 focused API 35 emulator
tests, debug lint and debug assembly passed. Changes remain local and uncommitted;
Beta 2 is not published. Karoo retest instructions are in LOCAL_TESTING.md.

## 0.99 Beta 2 release preparation (2026-10-23)

For v0.99-beta.2 (0.99/code 12), all 62 release JVM tests, release lint and optimized
signed assembly passed. The earlier 62 debug JVM and 11 focused API 35 emulator
tests plus debug lint/assembly passed. Full emulator instrumentation was not rerun.
The candidate uses the established signing identity and immutable Beta 2 URLs;
package/version, certificate, APK hash and public upload checksums are verified
before publication and recorded with the clean source commit in build-info.json.

PR #9 targets main and is updated for native fields and the Open POIs controller
action. Stable 0.98 and distributed Beta 1 remain unchanged. Beta 2 device testing
is pending, including actual one-cell sizing, controller dispatch, units and
outdoor GPS/arrival behavior. No physical device install or legacy workflow is used.
