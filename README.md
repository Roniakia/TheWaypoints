# TheWaypoints for Hammerhead Karoo

![TheWaypoints on Karoo](https://github.com/user-attachments/assets/86102699-c721-4e09-a4a6-bebeafb6c89d)

## Description

TheWaypoints searches for points of interest, saves bookmarks on your device, and
opens selected places in Karoo's native map. Version 0.96 searches
downloaded regional data packs and saved Favorites locally. Route calculation
and navigation are handled by Karoo.

This project is evolving into a new app, starting from
[dimskiy/WaypointsKaroo](https://github.com/dimskiy/WaypointsKaroo). Credit to the
original author for the idea and initial codebase; further development follows
this project's own direction.
Version **0.96 (code 8)** is stable. Download the APK from the
[latest release](https://github.com/Roniakia/TheWaypoints/releases/latest).
It is the identical APK accepted during beta testing; existing beta users do not
need to reinstall.

## Features

- Search for restaurants, shops, cafes, and other points of interest.
- Filter downloaded POIs and Favorites by category; sort by name or distance.
- Open a selected place in Karoo's native map.
- Save and delete bookmarks in local storage.
- Display straight-line distances from your device location.
- Download regional packs for offline POI search.

The app does not calculate routes. Previous 0.95 included Photon online search; 0.96 uses pack-only search.

## Privacy

This source contains no Firebase Analytics, Crashlytics, usage tracking, or
remote crash reporting. Debug logs stay on the device, and no Firebase account
or configuration file is required to build the app.

In 0.96, POI search queries and location-based sorting stay on the
device. Regional packs and signed catalogue updates download from GitHub.
Favorites remain in local storage. App update checks contact GitHub on launch
at most once daily and when Settings opens; downloading/installing requires an
explicit action. Previous 0.95 sent online search queries
and language to Photon, plus coordinates when nearby search is enabled.

## Compatibility

The app targets Hammerhead Karoo and depends on its system services for
location and map launch. The Android minimum is API 23. Our application ID is
`io.github.roniakia.waypoints`, so it installs alongside the original app and
keeps separate bookmarks. Existing upstream bookmarks are not imported automatically.

The pinned karoo-ext 1.1.9 release specifies KOS 1.634.2440 or later and does not
explicitly list Karoo 2 support. This version has passed local tests, but its
first beta was also accepted by the user after real Karoo testing. The device
model and OS were not recorded, so this does not establish compatibility with
every Karoo model, including Karoo 2.
See the [SDK release notes](https://github.com/hammerheadnav/karoo-ext/releases/tag/1.1.9).

## Installation and releases

Download test builds from [this repository's releases](https://github.com/Roniakia/TheWaypoints/releases).
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

## Offline POI data development

The reusable [regional pack generator](server/poi_packs/README.md) runs on a laptop
or server. The first Skåne candidate contains 19,159 POIs in a 2.32 MB download.
See the [measurements and limitations](docs/OFFLINE_POI_PROTOTYPE.md).
Data releases use separate `poi-data-*` tags and never replace the latest app release.
The Android integration is implemented in this PR; its verification is recorded
in the local testing guide.

## Release notes

See [RELEASE_NOTES.md](RELEASE_NOTES.md) for version history and changelogs.
For manual beta publishing and stable promotion, see [the release guide](docs/RELEASING.md).
