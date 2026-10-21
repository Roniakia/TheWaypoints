# Local testing before Karoo hardware

Status: 2026-10-21. JDK 17 and a local API 35 emulator are available. Karoo
service simulation is not implemented. Current stable version: 0.95 (7).

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

## Regional POI server prototype

Use an isolated Python environment and `server/poi_packs/requirements.txt`.
Run `PYTHONPATH=server/poi_packs python -m unittest discover -s server/poi_packs/tests -v`.
Ten server tests pass, covering signature tampering, catalogue validation, safe
fetch/checksum failure, category safety/membership, transliteration/aliases, empty
region cleanup, out-of-order area events, and synthetic OSM-to-ZIP integration
including polygons, multipolygons, geometry omissions, FTS, RTree and nearby sorting.
These do not establish Karoo performance; real-data measurements are recorded
separately in [OFFLINE_POI_PROTOTYPE.md](OFFLINE_POI_PROTOTYPE.md).

The initial Android unit-test command hit sandbox cache/socket restrictions. With
the existing temporary JDK/SDK/cache and approved local execution,
`:app:testDebugUnitTest` completed successfully with all 31 tasks up to date; this
reused existing outputs rather than executing fresh Android tests. App code is unchanged.

## TheWaypoints rename verification (0.96 / 8, unreleased)

On 2026-10-21, all 31 debug JVM tests, debug lint and debug APK assembly passed.
Generated Karoo metadata was checked for TheWaypoints, 0.96 (8), the unchanged
application ID and the renamed repository URLs. APK badging confirms the launcher
label TheWaypoints and application ID `io.github.roniakia.waypoints`.
Catalogue coverage verifies the unchanged published signature and accepts both
the historical and renamed repository asset paths. No signed catalogue bytes
were edited. Emulator instrumentation was not rerun for this branding change;
no physical Karoo installation or release publishing was performed.

## Search and scrolling refinement (0.96 / 8, unreleased)

On 2026-10-21, all 31 debug JVM tests, debug lint and debug assembly passed.
All four OfflineHomeTest cases passed on the API 35 emulator with the separate
local app ID. Coverage includes clearing the search input and triggering only
one automatic page request per loaded prefix, including a failed-load state.
Search marks itself busy before its debounce so automatic paging cannot cancel
an incoming query change. Existing region hierarchy and filter tests also pass.
No physical Karoo test or publishing was performed.

Regions floating action follow-up: the download/update button and selection
clear action now stay at the bottom. All 31 JVM tests passed; all four API 35
OfflineHomeTest cases passed after reserving viewport space above the floating
controls. The initial content-padding-only layout obscured a checkbox and failed
the hierarchy test; the final list inset resolves it. The POI filter action now
uses a bundled vector icon with its existing accessibility label.

## Favorites destination (0.96 / 8, unreleased)

All 32 JVM tests and debug lint passed. The new ViewModel regression covers
favorites without installed regions, 55-result pagination, text/category filters,
reactive bookmark removal and separate POI/Favorites search choices. All five
OfflineHomeTest cases passed on the API 35 emulator with the local app ID,
including Favorites drawer navigation, clear search and filter/sort application.
No physical Karoo testing or release publishing was performed.

Floating Regions action visual follow-up: removed the outer Surface/background
and its shadow, retaining the button, selection clear action and list inset.
Local debug APK assembly passed and the PC emulator was updated for inspection.

Regions button width/count refinement: the floating action is centered, spans
available width with 16 dp side margins, and includes the selected count in its
label. The separate counter/clear button is removed; regions can be deselected
through their checkboxes. All five API 35 screen tests passed, including the
updated action/count assertion and selection callback. Emulator app refreshed.

Search placeholder correction: an empty trailing-icon slot reserved width even
without a clear button, wrapping Search regions. The slot is now null for empty
queries and placeholders explicitly use one line with ellipsis. All five API 35
screen tests passed. The visible emulator was updated; UI bounds confirm the
Regions placeholder height decreased from 74 px (two lines) to 37 px (one line).

## POIs-only download guidance (0.96 / 8)

All 33 JVM tests, debug lint and all five OfflineHomeTest emulator cases passed.
Coverage checks include no installed regions, unknown location, a missing current
region, installed coverage, polygon holes, outside-catalogue locations and empty
search results. The Regions hierarchy test now expands Europe/Sweden to find
undownloaded regions after removal of the Recommended section. Initial execution
used its old assertion before that test update; final rerun passed. The local
emulator was refreshed. Karoo GPS behavior was not tested on physical hardware.

## Pack-only app search (0.96 / 8)

Removed the online search footer and dialog from the app flow. All 33 JVM tests,
all five OfflineHomeTest API 35 emulator tests, and debug lint passed. The debug
APK was built as part of instrumentation and the visible emulator refreshed.
Legacy Photon classes/tests remain internal with no app navigation entry.
Pack downloads/catalogue refresh still require connectivity. No physical device
installation or publishing occurred.

## POI category icons (0.96 / 8)

All 33 JVM tests, five API 35 OfflineHomeTest screen tests and debug lint passed
with the bundled vector icons. A final focused filter/list screen test passed
after grouping icon and title into one accessible map-opening target. The local
APK was rebuilt and the visible emulator refreshed. Icons cover every category
group with a pin fallback for unknown categories. No physical Karoo tested.

## App update channels (0.96 / 8, unreleased)

All 47 normal debug JVM tests and final debug lint passed. New coverage includes
Stable/Beta release selection, promoted-beta tags, historical repository URLs,
malformed/mismatched metadata, missing digests and download limits, daily check
throttling including failures, manual retry, channel persistence, no downgrade,
APK size/hash/package/version/certificate checks, pre-install revalidation,
ViewModel download retry/cancellation, and installer-event delivery.

On the API 35 emulator, the two Settings tests and five existing offline screen
tests passed using `-PlocalBuild=true`. The initial normal-ID run was refused by
the installed signed release's certificate, so it ran no tests and the existing
release was preserved. The isolated local-ID rerun passed.

A separate optional instrumentation test verified a real debug-signed higher-code
APK (temporary local-only 0.97/code 9 fixture) against the installed local
0.96/code 8 app using API 35 signingInfo, then opened the system installer and
observed its Update confirmation button. It did not press Update or install the
fixture. An initial fixture lookup skipped because it used the target asset
context; the test now reads test-package assets. Resetting the install app-op
inside instrumentation terminated the test process; cleanup is now left to the
host/test-package uninstall. The final real-APK confirmation test passed.

To reproduce the optional test, build a `.local` debug APK with command-line
`-PAPP_VERSION_NAME=0.97 -PAPP_VERSION_CODE=9` overrides and copy it outside the
repository as `update-candidate.apk`. Restore normal version properties by
omitting those overrides, then run `:app:connectedDebugAndroidTest` with
`-PlocalBuild=true -PpoiCompatibilityAssets=<fixture-directory>` and runner class
`de.dimskiy.waypoints.platform.updates.UpdateInstallerTest`. Without the fixture
this test skips. Only Android-test assets include it; production APKs do not.
If using an execution mode that keeps the target installed, reset its app-op
from the host after instrumentation: `adb shell appops set
io.github.roniakia.waypoints.local REQUEST_INSTALL_PACKAGES default`.

Read-only verification of published releases confirmed the existing stable APK
has a GitHub SHA-256 asset digest and its immutable manifest uses the historical
repository alias handled by the updater. Final local debug assembly and emulator
refresh use 0.96/code 8. No physical Karoo test, new release, tag, commit or push
was performed. Real-device unknown-source permission and installer support remain
unverified; the Settings page offers the APK link for Companion fallback.

The visible emulator was left on Settings. A live Stable check against GitHub
completed successfully and displayed its last-check time; no update is offered
because development 0.96/code 8 exceeds the published stable candidate. A fresh
screenshot was inspected at 480×800: channel chips, installed version, check time
and the manual check button render correctly in the scrollable page. Final APK
inspection confirms the higher-version installer fixture is absent.

Settings control refinement: Stable/Beta use mutually exclusive radio rows with
radio-group accessibility semantics. Check for updates is a full-width floating
button with 16 dp side/bottom margins, and the scrollable content reserves space
above it. Both Settings emulator tests passed, covering exclusive selection,
disabled controls during checks, and unchanged button bounds after scrolling.
The visible local emulator was updated. Version remains 0.96/code 8.

Shared floating bottom action refinement: Regions and Settings now use one
FloatingBottomAction component. Only the capsule button paints a background;
content scrolls behind it with measured trailing padding, replacing the fixed
viewport inset. Buttons share 16 dp margins, minimum 48 dp touch height, label
counts/loading and focus clearing. The rule is recorded in AGENTS.md; the round
POI filter FAB is exempt. All seven Regions/Settings screen tests passed. A final
focused Regions test passed after preserving focus clearing, and debug lint
passed. The visible emulator was refreshed. Version remains 0.96/code 8.

Shared bottom-action fade: local debug assembly passed for a decorative linear
background gradient, 24% opacity at the bottom and transparent at the exact
measured button top edge. Existing button layout/touch behavior is unchanged;
no additional interaction tests were needed for this visual-only refinement.
The emulator was refreshed. Version remains 0.96/code 8.

Karoo yellow accents: all seven Settings/Regions/POI screen tests and debug lint
passed. Both resource themes use Hammerhead Lemon Yellow #FFE900 and black text
on filled accent buttons. Selected containers use yellow tints; text-only actions
use onSurface for readable contrast. The adaptive launcher background also uses
yellow. Final local debug assembly includes that launcher resource; no dependency
or app version change was needed for this follow-up within 0.96/code 8.

## 0.96 Beta 1 release verification (2026-10-22)

For v0.96-beta.1, all 47 debug and 47 release JVM tests passed, along with release
lint and optimized signed release assembly. The full API 35 local-ID emulator
suite reported 39 tests: 35 passed, four optional tests skipped (live download,
real Skåne asset, live launcher flow and higher-version installer fixture). The
installer confirmation fixture was separately verified during updater work; it
was not included in this final full-suite invocation.

APK badging confirms 0.96/code 8 and package io.github.roniakia.waypoints. Signature
verification matches the established SHA-256 certificate ce73d47787626c776d4d578d62c98a7de4eb814bf5b701a58679df8ffdb002f6.
Packaged MANIFEST_URL and generated APK/icon URLs all reference v0.96-beta.1;
APK filename is waypoints-0.96-beta.1.apk. The final source commit and exact APK
hash are attached to the GitHub beta as build-info.json and SHA256SUMS. The
release is a prerelease; stable 0.95 remains unchanged, with device acceptance
pending. The release workflow was not used and no physical device was installed.
