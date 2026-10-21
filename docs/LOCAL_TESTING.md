# Local testing before Karoo hardware

Status: 2026-10-21. JDK 17 and a local API 35 emulator are available. Karoo
service simulation is not implemented. Current stable version: 0.94 (5).

## Build setup

Set `JAVA_HOME` to JDK 17 and `ANDROID_HOME` to an Android SDK containing platform
35, build tools 35.0.0, and platform-tools. The workstation setup downloaded
these into temporary directories; reconfigure the environment if they are cleaned.
Set `GRADLE_USER_HOME` when using the existing isolated Gradle cache. Gradle 8.13
is downloaded there. Machine-specific paths and credentials remain untracked.

Firebase, Analytics, Crashlytics, reporting hooks, and the Firebase CI decode
steps are removed. No `google-services.json` or reporting account is needed in
any variant. Timber logging stays local.

The Karoo SDK repository configuration accepts `GITHUB_USER` / `GITHUB_TOKEN`;
the SDK resolved successfully during local verification without adding secrets.
Supply package credentials privately if a future environment requires them.

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

For an isolated app ID, add `-PlocalBuild=true`. It enables only debug and adds
`.local` to the application ID. Both normal and local modes are telemetry-free.
Normal builds use our independent application ID, `io.github.roniakia.waypoints`.
The user has requested the first beta for their own Karoo testing; installation
on the physical device is performed by the user. Existing upstream storage stays
separate.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/lint-results-debug.html`, and
`app/build/reports/androidTests/connected/debug/index.html`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Regression coverage

| Layer | Coverage | Hardware needed? |
| --- | --- | --- |
| JVM | 6 search coordination tests, 4 distance tests, DataStore persistence, pin-drop serialization (12 total) | No |
| Android | 10 DAO tests, Room 1→2 migration, 6 distance-format tests, search focus after state changes (18 total) | No; API 35 emulator |
| Full app | Karoo connection, HTTP, GPS, units, native map launch | Real Karoo services or substitutes |

Before telemetry removal, all 12 unit tests and 18 Android tests passed with
karoo-ext 1.1.9, AGP 8.9.2, Room 2.8.5, and DataStore 1.2.1; lint and debug
assembly also passed. Final telemetry-removal results are recorded below.

KSP2 is enabled for the newer Room processor. The app explicitly uses serialization
JSON 1.8.1 so Karoo's transitive 1.6.3 cannot conflict with Room's migration helper
in the test APK. Pin-drop serialization round-trip coverage checks this shared
runtime. Room schema history and database version remain unchanged.

Android tests use Android MockK and the same Compose BOM as the app. JUnit
license resources are merged to avoid packaging collisions. Separate Compose
test pinning previously caused an AnnotatedString binary mismatch.

The API 35 ARM emulator uses 480×800 / 295 dpi, matching the Karoo 2 preview.
These tests do not establish actual device OS compatibility. Upstream karoo-ext
1.1.9 release notes specify KOS 1.634.2440+ and omit explicit Karoo 2 support.

## Proposed local simulator

The optional local build flag is not a simulator. A stock emulator has no Karoo
system service: simulated Android GPS does not supply `OnLocationChanged`.
UI may wait indefinitely for Karoo units or other service responses.

A future simulator should retain real UI, ViewModel, use cases, Room, and
DataStore while replacing these boundaries through Hilt:

| Boundary | Replacement |
| --- | --- |
| `LocationsProvider` | Controllable fixed, cached, delayed, missing and error locations |
| `EnvironmentPropertiesProvider` | Immediate metric/imperial/unknown values |
| `PhotonApiService` | Deterministic normal/empty/malformed/error fixtures |
| Native map launch | Inject an interface, record requested coordinates |

`OpenExternalMap` still directly depends on `KarooServiceProvider`; replacing only
HTTP/location providers will not isolate map clicks. Prefer fixture search first;
optional direct Photon HTTP should stay outside deterministic tests.

## Further scenarios

- Rapid search cancellation; latest results only; query language/encoding.
- Geo-search toggle and ten-minute cache boundary; stale/missing GPS fallback.
- Bookmark reactivity, duplicate identities, restart persistence, offline access.
- Long names/addresses, translations, keyboard/back/swipe behavior at Karoo size.
- Karoo callback duplication, timeout/cancellation cleanup, disconnects and
  concurrent service connection requests.
- API 23 emulator coverage for the minimum supported Android version.

When the user requests device testing, validate actual service binding, GPS,
profile units, connectivity, native pin handoff, keyboard/buttons, and rendering.
Preserve saved POIs and signing compatibility before installation.

Do not dispatch publishing workflows for verification. Both workflows publish;
the release workflow also commits and pushes. Version bumps do not authorize
those actions. Use the `waypoints-versioning` skill for future change batches.

## Telemetry-removal verification

For 0.94 (5), the normal build (without `-PlocalBuild=true`) passed
`:app:testDebugUnitTest` (12 tests), `:app:connectedDebugAndroidTest` (18 tests),
`:app:lintDebug`, and `:app:assembleDebug`. Runtime dependency and packaged
manifest checks found no Firebase, Crashlytics, or Google measurement entries.
The APK reports application ID `io.github.roniakia.waypoints`, version name 0.94 and code 5.
Both workflow files parsed as YAML after removing Firebase configuration steps.
The installed personal versioning skill passed the bundled skill validator.
No publishing workflow or physical device installation was performed.

## First independent beta verification

For candidate `v0.94-beta.1`, application ID `io.github.roniakia.waypoints`,
12 debug JVM tests, 12 release JVM tests, all 18 API 35 Android tests, release
lint and signed release assembly passed after the identity change. The release
APK's signature verifies with our dedicated RSA-3072 signing certificate and
its launchable activity resolves correctly. The source Kotlin namespace remains
internal and unchanged. The final downloadable asset is built from the tagged
commit; its checksum, source SHA and certificate fingerprint are in the release
assets. See [beta device-test instructions](BETA_TESTING.md).

## Stable promotion acceptance

On 2026-10-21 the user accepted Beta 1 after real Karoo testing and authorized
publishing and merging. The existing GitHub prerelease was promoted to stable
0.94 with the same tag, source commit, APK and other assets. No build or test
rerun was needed for promotion; subsequent changes record acceptance in docs only.
The model and KOS version were not specified.

## Offline regional storage foundation (0.95 / 6, unreleased)

On 2026-10-21, `:app:testDebugUnitTest` passed 17 tests, and the API 35 emulator
passed 25 instrumentation tests (18 existing and 7 regional storage tests).
`:app:lintDebug` passed with warnings; debug/test APK assembly succeeded as part
of instrumentation. Tests used `-PlocalBuild=true` so the `.local` test app did
not replace the existing signed beta. A first attempt with the normal app ID
was blocked by the expected signing-certificate mismatch, before running tests.

The synthetic archive is produced by the separate Python pipeline, with 66 POIs.
Coverage includes Android FTS4/category queries, English aliases for Ukrainian
and Japanese names, Latin accents, nearest ordering, two-page access beyond 30
results, typed OSM identity, deduplication across overlapping packs, atomic
replacement/removal, checksum/metadata mismatch, cancellation, ZIP traversal and
extraction limits, and restart recovery of an AtomicFile backup/orphan snapshot.
No Room schema or migration changed.

The optional real Skåne test also passed with pack
`se-skane-2026-10-prototype.2.zip` (SHA-256
`afba3859c838dbee016ff72c375977482170735496c2119801a09555b856a566`),
containing 19,159 POIs. Single illustrative emulator measurements: first
alphabetic page across all POIs 318 ms; nearest-first `water` search 4 ms,
137 matches, first 50 returned. These are not statistical benchmarks or Karoo
measurements. The test does not contact OSM or GitHub.

Stock Android SQLite lacks RTree. The pack's optional desktop RTree is unused;
Android searches regular coordinates and FTS4. FTS4 integrity validation requires
a writable staging connection on this emulator; installed databases are opened
read-only. Lint caught an API 24 Comparator call; the final implementation uses
the API 23-compatible Collections reverse comparator. Minimum-API runtime
coverage, actual Karoo performance, network/background downloads, signature
verification, and the new screens remain pending.

To include the real pack, put `real.zip` plus `real-descriptor.json` into an
external directory. The descriptor contains `id`, `revision`, `sha256`,
`download_bytes`, `database_bytes` and `poi_count` from the generator's build
report. Run on an explicitly selected emulator:

```sh
ANDROID_SERIAL=<emulator-serial> ./gradlew :app:connectedDebugAndroidTest \
  -PlocalBuild=true -PpoiCompatibilityAssets=<absolute-assets-directory>
```

Without that optional directory the real-pack test is skipped, while the
synthetic fixture and regression tests still run. Neither real nor synthetic
test packs are included in production APK assets. Region screens and a beta
with user-visible offline behavior are the next integration phase.

## Complete offline flow verification (0.95 / 6, unreleased)

On 2026-10-21, 31 debug JVM tests and all 34 API 35 emulator tests passed,
with debug lint. The emulator run included the optional real Skåne pack and
`-Pandroid.testInstrumentationRunnerArguments.offlineLive=true`: a real public
GitHub download through WorkManager and a launcher UI flow selecting Skåne,
installing it, switching to POIs and searching `water`. These opt-in tests need
internet. Without live/real-pack inputs, three optional tests skip.

Additional coverage includes signature rejection and verified-cache retention,
HTTP bounds/redirects/Range resume, interrupted worker retries and cancellation,
region hierarchy/hidden selections, category filters, bookmark preservation,
native-script queries, and location callback duplicate/cancellation cleanup.
Fresh screenshots confirmed the title-free layout, inline page menu, filter
sheet and real downloaded results. No physical Karoo installation occurred.

Earlier foundation notes describe the intermediate implementation; network,
signature and screen integration are now complete. Real Karoo GPS/native-map
handoff, device performance, API 23 runtime behavior, and background behavior
under Karoo power management remain unverified. Only one published region is
available, so multi-region hierarchy/recommendation cases use synthetic fixtures.

Release verification also passed 31 release JVM tests, release lint (warnings),
and optimized signed `:app:assembleRelease`. The APK signature verified with the
established certificate SHA-256
`ce73d47787626c776d4d578d62c98a7de4eb814bf5b701a58679df8ffdb002f6`.
The proposed 0.95 Beta 1 is not published or device-tested yet.

## Beta 2 packaging regression check

Inspect the signed APK itself, not only the source manifest: `aapt dump xmltree`
must show MANIFEST_URL ending `/v0.95-beta.2/manifest-karoo.json`; `aapt dump
badging` must report 0.95/code 7. Generated `manifest-karoo.json` must report
the same version/code and target `/v0.95-beta.2/waypoints-0.95-beta.2.apk`.
Verify the signing certificate and uploaded bytes. A stock emulator can check
launcher/region drawer but cannot reproduce the Karoo Companion installer.

Beta 2 verification on 2026-10-21 passed 31 debug and 31 release JVM tests,
release lint (warnings), and optimized signed release assembly. The packaged
APK audit confirmed 0.95/code 7, the candidate-specific MANIFEST_URL, matching
generated APK/icon URLs, and the established signing certificate. Installing
that exact release APK over Beta 1 on the API 35 emulator succeeded; launcher
inspection found Search regions and Choose page. The earlier 34-test offline
emulator suite belongs to Beta 1; it was not rerun for this metadata-only fix.
No physical Karoo was installed; Companion installer behavior remains pending.
