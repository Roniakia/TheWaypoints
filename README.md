# Waypoints for Hammerhead Karoo

![Waypoints on Karoo](https://github.com/user-attachments/assets/86102699-c721-4e09-a4a6-bebeafb6c89d)

## Description

Waypoints searches for points of interest, saves bookmarks on your device, and
opens selected places in Karoo's native map. Online search uses Photon; saved
bookmarks remain available offline. Route calculation and navigation are handled
by Karoo.

This development fork builds on [dimskiy/WaypointsKaroo](https://github.com/dimskiy/WaypointsKaroo).
Version **0.94 (code 5)** is currently unreleased.

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
Bookmarks and the cached search location are stored locally.

## Compatibility

The app targets Hammerhead Karoo and depends on its system services for HTTP,
location, units, and map launch. The Android minimum is API 23.

The pinned karoo-ext 1.1.9 release specifies KOS 1.634.2440 or later and does not
explicitly list Karoo 2 support. This version has passed local tests, but its
integration with physical Karoo hardware, including Karoo 2, is not yet verified.
See the [SDK release notes](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9).

## Installation and releases

This fork has no published APK release yet. Future builds will appear under
[this repository's releases](https://github.com/Roniakia/WaypointsKaroo/releases).

The [upstream APK releases](https://github.com/dimskiy/WaypointsKaroo/releases)
are maintained separately and do not include this fork's unreleased changes.
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

This enables only debug and uses `de.dimskiy.waypoints.local`. It is not a Karoo
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

### v0.94 — unreleased

- Remove Firebase Analytics, Crashlytics, and all usage reporting.
- Update karoo-ext to 1.1.9, Android Gradle Plugin to 8.9.2, Room to 2.8.5,
  and DataStore to 1.2.1.
- Enable KSP2 and align the serialization runtime used by the app and migration tests.
- Add an optional separate-ID local debug mode.
- Align Compose test dependencies and fix Android test packaging.
- Add regression coverage for search focus, settings persistence, and pin-drop serialization.
- Add architecture, local testing, privacy, versioning, and changelog guidance.

### v0.84 — upstream history

- Fix imperial/metric units detection.
- Fix missing house numbers in search results and bookmarks.
- Minor bug fixes and visual improvements.
