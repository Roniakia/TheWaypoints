# Waypoints: project notes

Inspected 2026-10-21 at baseline `3f3ed9c` (`master`). These are source-based
observations, not a claim that the app has been executed successfully.

## Purpose and current scope

Product direction: develop an independently evolving app with substantial future
changes. The upstream project supplied the initial idea and starting codebase;
retain original-author credit and applicable license/copyright notices. The
GitHub fork relationship describes provenance, not the intended product scope.

Android launcher app integrated with Hammerhead Karoo. Search Photon for POIs,
optionally bias results by device location, bookmark results in local storage,
and open a pin in Karoo's native map. Offline support means saved POIs remain
available; online search and route calculation are separate concerns. The app
itself does not calculate routes or render a navigation map.

## Build and packaging

| Item | Repository value |
| --- | --- |
| Module / application ID | `app` / `io.github.roniakia.waypoints` |
| Kotlin namespace | `de.dimskiy.waypoints` (internal source namespace retained) |
| Development version | `0.95`, code `7` in `gradle.properties`; stable is 0.95 (7) |
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
([PR #2](https://github.com/Roniakia/WaypointsKaroo/pull/2)); the Android feature is
on `codex/offline-poi-regions` ([PR #3](https://github.com/Roniakia/WaypointsKaroo/pull/3)).
The published signed prototype catalogue currently contains only Skåne.

`OfflineHome` replaces the launcher content with compact POIs/Regions screens,
a menu beside search, first-run invitation, downloaded-first region hierarchy,
local position/locale recommendations, persistent hidden selections, and
expandable category filters. Online Photon search remains a secondary action.
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
and this repository's release manifest URL; it does not declare a custom
`KarooExtension` service for ride data fields. UI resources include English,
German, Spanish, French, and Italian strings. Preview configuration targets
Karoo 2 at 480×800 pixels / 295 dpi.

`assembleRelease` finalizes tasks generating the external Karoo manifest and
copying an icon under `app/build/outputs/apk/release/assets/`; description comes
from the README. README release notes describe stable 0.94 and retain historical notes.

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
