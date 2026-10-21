# Waypoints for Hammerhead Karoo

![Waypoints on Karoo](https://github.com/user-attachments/assets/86102699-c721-4e09-a4a6-bebeafb6c89d)

## Description

Waypoints searches for points of interest, saves bookmarks on your device, and
opens selected places in Karoo's native map. Online search uses Photon; saved
bookmarks remain available offline. Route calculation and navigation are handled
by Karoo.

This project is evolving into a new app, starting from
[dimskiy/WaypointsKaroo](https://github.com/dimskiy/WaypointsKaroo). Credit to the
original author for the idea and initial codebase; further development follows
this project's own direction.
Version **0.95 (code 7)** is stable. Download the APK from the
[latest release](https://github.com/Roniakia/WaypointsKaroo/releases/latest).
It is the identical APK accepted during beta testing; existing beta users do not
need to reinstall.

## Features

- Search for restaurants, shops, cafes, and other points of interest.
- Optionally bias searches toward your device location.
- Open a selected place in Karoo's native map.
- Save and delete bookmarks in local storage.
- Display straight-line distances in metric or imperial units.
- Use Karoo's available connectivity for online search.

Offline bookmarks do not provide offline search or route calculation.

## Privacy

This source contains no Firebase Analytics, Crashlytics, usage tracking, or
remote crash reporting. Debug logs stay on the device, and no Firebase account
or configuration file is required to build the app.

Online searches send the query and language to Photon through Karoo's HTTP
service. Nearby searches also send location coordinates to bias the results.
Bookmarks and the cached search location are stored locally. In development 0.95,
regional packs and signed catalogue updates download directly from GitHub. Region
recommendations use position or locale locally; no analytics is added.

## Compatibility

The app targets Hammerhead Karoo and depends on its system services for HTTP,
location, units, and map launch. The Android minimum is API 23. Our application ID is
`io.github.roniakia.waypoints`, so it installs alongside the original app and
keeps separate bookmarks. Existing upstream bookmarks are not imported automatically.

The pinned karoo-ext 1.1.9 release specifies KOS 1.634.2440 or later and does not
explicitly list Karoo 2 support. This version has passed local tests, but its
first beta was also accepted by the user after real Karoo testing. The device
model and OS were not recorded, so this does not establish compatibility with
every Karoo model, including Karoo 2.
See the [SDK release notes](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9).

## Installation and releases

Download test builds from [this repository's releases](https://github.com/Roniakia/WaypointsKaroo/releases).
Betas are GitHub prereleases; the latest accepted beta can become stable using
the identical tested APK after device-test acceptance and release approval.

The [upstream APK releases](https://github.com/dimskiy/WaypointsKaroo/releases)
are maintained separately and do not include this project's changes.
The privacy statements above describe this source, not those upstream APKs.

For sideloading instructions, see Hammerhead's
[Companion app guide](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading)
or the older [Android developer mode guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
Check device and signing compatibility before replacing an existing installation
if you need to preserve its bookmarks.

## Development

Use **JDK 17**, Android SDK platform **35**, and build tools **35.0.0**. Configure
`JAVA_HOME` and `ANDROID_HOME` locally, then use the included Gradle wrapper:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
The Karoo package repository is configured to read `GITHUB_USER` and
`GITHUB_TOKEN` if authentication is required; keep credentials out of tracked
files and logs. No release signing credentials are needed for debug builds.

For a separate debug application ID, add `-PlocalBuild=true`:

```sh
./gradlew -PlocalBuild=true :app:assembleDebug
```

This enables only debug and uses `io.github.roniakia.waypoints.local`. It is not a Karoo
simulator: a normal Android emulator has no Karoo system service, so full-app
HTTP, location, units, and map actions need real services or future substitutes.

With a local emulator running, execute the Android regression tests:

```sh
./gradlew :app:connectedDebugAndroidTest
```

For 0.94, **12 JVM tests and 18 API 35 emulator tests passed**, along with lint
and debug assembly. Coverage includes search coordination, units formatting,
Room storage and migration, search focus, settings persistence, and pin-drop
serialization. These results do not establish device integration or release
signing compatibility.

Read the [project notes](docs/PROJECT_NOTES.md),
[local testing guide](docs/LOCAL_TESTING.md), and [working agreement](AGENTS.md)
for architecture, setup details, and development conventions. Target pull
requests to `main` and include meaningful changelogs. The manually dispatched
publishing workflows are not build verification shortcuts: both publish
artifacts, and the release workflow also commits and pushes version changes.

## Release notes

### v0.95 — stable, 2026-10-21

Promoted the accepted `v0.95-beta.2` without rebuilding. The tag, versioned APK
filename, signing key, app version/code and source commit are unchanged.
Known UI roughness was accepted for this pre-1.0 release.

Beta 2 (`v0.95-beta.2`, code 7) fixes the beta's embedded Karoo update URL:
it now resolves the candidate's own manifest rather than stable 0.94. Generated
metadata targets the versioned `waypoints-0.95-beta.2.apk` filename. Beta 1 was
code 6; the app version remains 0.95. The user confirmed it works on their Karoo and authorized stable release. No bookmark/database migration is required.

- Add compact POIs and Regions screens, with the page menu beside search and a
  first-run region download invitation.
- Browse searchable region hierarchies, downloaded regions first and local
  recommendations next. Update selected regions (including missing downloads),
  update all downloaded regions, cancel downloads, or remove a region.
- Search downloaded POIs by name, English transliteration, category and synonyms;
  preserve native-script names and queries. Use expandable type filters,
  nearest/name ordering, and pagination beyond 30 results.
- Verify signed catalogues and pack checksums; download in the background with
  resume/retry and atomic replacement. Bookmarks survive region removal.
- Preserve Photon online search and native Karoo map handoff. Fix the wasted
  platform title bar and location callback cancellation/duplicate events.
- Add WorkManager 2.10.5 and Tink 1.18.0 without changing Compose/Lifecycle pins.
- Initial catalogue offers the Skåne prototype only. New screens are English and
  offline distances are metric; Karoo device testing and API 23 runtime testing
  remain pending. This does not add offline maps or routing.

### v0.94 — stable, 2026-10-21

Promoted from the user-accepted `v0.94-beta.1` without rebuilding or replacing
the APK. The original tag is retained to preserve the tested source and artifact.

- Remove Firebase Analytics, Crashlytics, and all usage reporting.
- Update karoo-ext to 1.1.9, Android Gradle Plugin to 8.9.2, Room to 2.8.5,
  and DataStore to 1.2.1.
- Enable KSP2 and align the serialization runtime used by the app and migration tests.
- Give this app its own identity, signing key, and repository update links.
- Add an optional separate-ID local debug mode.
- Align Compose test dependencies and fix Android test packaging.
- Add regression coverage for search focus, settings persistence, and pin-drop serialization.
- Add architecture, local testing, privacy, versioning, and changelog guidance.

### v0.84 — upstream history

- Fix imperial/metric units detection.
- Fix missing house numbers in search results and bookmarks.
- Minor bug fixes and visual improvements.
