# Waypoints: project notes

Inspected 2026-10-21 at baseline `3f3ed9c` (`master`). These are source-based
observations, not a claim that the app has been executed successfully.

## Purpose and current scope

Android launcher app integrated with Hammerhead Karoo. Search Photon for POIs,
optionally bias results by device location, bookmark results in local storage,
and open a pin in Karoo's native map. Offline support means saved POIs remain
available; online search and route calculation are separate concerns. The app
itself does not calculate routes or render a navigation map.

## Build and packaging

| Item | Repository value |
| --- | --- |
| Module / package | `app` / `de.dimskiy.waypoints` |
| Version | `0.94`, code `5` in `gradle.properties` |
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
This unreleased batch is 0.94 (5), including the prior dependency/build updates.

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

## Behavior and data flow

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

Room database `app_database`, version 2, table `waypoints`. Autogenerated local
ID, unique `serverId`, server type (currently PHOTON), name, address fields,
latitude/longitude. Insert uses REPLACE; duplicate server IDs replace rows.
The unique index covers serverId only, despite DAO lookup also accepting type.

Migration 1 → 2 rebuilds the table, preserves existing values, adds nullable
`house` initialized to NULL, and recreates the unique index. Exported schemas
are under `app/schemas/`. Preferences file is `location_settings`.

## Integration and delivery

Manifest declares a launcher activity, INTERNET permission, extension metadata,
and an upstream release manifest URL; it does not declare a custom
`KarooExtension` service for ride data fields. UI resources include English,
German, Spanish, French, and Italian strings. Preview configuration targets
Karoo 2 at 480×800 pixels / 295 dpi.

`assembleRelease` finalizes tasks generating the external Karoo manifest and
copying an icon under `app/build/outputs/apk/release/assets/`; description comes
from the README. README release notes describe the unreleased 0.94 batch and retain historical notes.

Both GitHub workflows are manual (`workflow_dispatch`), run variant-specific
unit tests, and publish release assets. Instrumentation is not active there.
**Release-workflow.yml commits and pushes version changes.** Do not dispatch it
without explicit user authorization. Stage publishes a prerelease as well.

## Review targets before implementation

These are static review findings to investigate with focused tests, not
verified device failures or changes made during this analysis.

- Service connection wait has no timeout. The units request can wait indefinitely;
  geo-enabled ViewModel processing asks for units even for an empty ready list.
  A stock emulator may therefore stall state updates without Karoo services.
- Location/profile consumers are removed after normal suspension returns, but
  lack cancellation cleanup. Repeated events can try to resume a continuation
  more than once. HTTP consumer cleanup also needs timeout/cancellation coverage.
- GPS timeout wraps connection plus location work, but the catch is inside
  `getLocation`; connection timeout can escape its fallback path. Returning a
  fallback from an already cancelled timeout context also needs verification.
- Service reference counting increments/decrements under a mutex, while the
  connection/disconnection decisions occur outside it; concurrency deserves tests.
- Photon provider catches broad Exception, including ordinary coroutine
  cancellation. DTO coordinate indexing and mapping happen outside the API
  exception wrapper, so malformed coordinates can escape as exceptions.
- Search bookmark matching and unique storage IDs omit OSM object type; investigate
  possible identity collisions before introducing another POI source.
- Persistence JSON decoding and database failures have no explicit recovery UI.
- Generated release manifest task reads README/version properties but declares
  only the template as an input; incremental rebuild freshness needs checking.

## References

Local source is authoritative for this pinned app. Current upstream docs may
cover newer SDK versions and must not be assumed identical to the pinned version.

- [Hammerhead SDK](https://github.com/hammerheadnav/karoo-ext)
- [Hammerhead SDK API](https://hammerheadnav.github.io/karoo-ext/index.html)
- [Android testing in Studio](https://developer.android.com/studio/test/test-in-android-studio)
- Local testing details: [LOCAL_TESTING.md](LOCAL_TESTING.md)
