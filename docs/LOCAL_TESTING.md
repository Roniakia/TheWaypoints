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

## Stable 0.96 promotion (2026-10-22)

The user accepted v0.96-beta.1 and authorized stable publication. Promotion changes
release metadata only and retains its exact APK, signing identity, version/code,
tag and source commit. No rebuild or test rerun is needed for promotion. The
original 47 debug/47 release JVM and 35-pass/four-skip emulator results remain
the recorded local checks. Real-device model/KOS were not specified.

## POI distance labels (0.97 / 9, unreleased)

All 47 debug JVM tests, debug lint and debug APK assembly passed on 2026-10-23.
POI/Favorites distances now use a separate Karoo yellow badge with compact metric
formatting. Emulator instrumentation and visual inspection were not performed;
the sandbox blocked the attempted ADB server startup. No physical Karoo install,
commit, push or publishing occurred.

Emulator follow-up: launched the API 35 emulator and built/installed the isolated
local-ID app. The existing POI filter/list screen test passed with sample distances;
its captured light-theme screen was visually inspected for yellow distance badges.
The real app remains open, but stock Android cannot supply Karoo GPS distances.
The Gradle connected-test runner removed the local-ID app/data after its run;
the app was reinstalled for the retained screenshot run. The signed normal-ID app
was not changed.

## Search margins, selected stars and scroll keyboard (0.97 / 9)

Search capsules are 52 dp high within 8 dp header margins. All five OfflineHomeTest
emulator cases and debug lint passed; the header was visually inspected. The
normal-ID 47-test JVM suite passed. An initial local-ID JVM invocation failed
three updater tests at the intentional production-download guard; normal-ID is
the appropriate variant for those tests.

After adding gold saved stars and scroll focus dismissal, all five emulator
screen cases and debug lint passed again. The paging regression now taps search,
swipes the list, asserts focus is cleared, and taps search to regain focus.
The updated isolated app was reinstalled/opened on the visible emulator. No
physical device installation, commit or publication occurred. Version stays 0.97/9.

## Region guidance arrow and opaque disabled bottom actions (0.97 / 9)

All 47 normal-ID debug JVM tests, seven local-ID emulator screen tests
(OfflineHomeTest and AppUpdateSettingsTest), and debug lint passed. The local
APK was rebuilt by instrumentation and the visible emulator refreshed. The shared
disabled capsule now uses an opaque composited gray; its surrounding fade and
scroll clearance are unchanged. Region guidance retains its accessible label
with a yellow forward arrow. No physical Karoo testing or publication occurred.

## Boxier sidebar (0.97 / 9)

All five OfflineHomeTest emulator tests and debug lint passed. Reinstalled the
local-ID app, opened its drawer and inspected a fresh 480×800 screenshot: the
selected POIs row has yellow fill, black text, modest corners and clear side
margins. The emulator is left with the drawer open. Version remains 0.97/9;
no physical device install, commit or publication occurred.

Guidance copy follow-up: local debug assembly passed. Refreshed the emulator and
visually confirmed Download regions for POIs fits on one line beside the yellow
arrow at 480×800. Both guidance variants use one line with ellipsis fallback.
No test rerun was needed for this copy/layout-only change; version stays 0.97/9.

Full-width region guidance follow-up: all six OfflineHomeTest emulator cases
and debug lint passed. New coverage checks the row spans the root width and
that tapping its far right edge opens Regions. Arrow now sits 8 dp after the
text; the single clickable row retains the Choose regions accessibility label.
The visible emulator was refreshed. Version remains 0.97/9.

Favorites empty-state follow-up: all six OfflineHomeTest emulator cases and
debug lint passed. Favorites coverage now checks default/all categories with
blank search, entered/cleared search, restricted/empty category sets, and absence
of Browse POIs. The emulator app was refreshed. Version remains 0.97/9.

POI text layout follow-up: all seven OfflineHomeTest emulator tests and debug
lint passed. New coverage verifies distance before the name, type below the
name, address · coordinates below the type, and coordinates without an address
or known distance. A retained focused run passed for screenshot inspection;
480×800 rendering confirms the layout, with detail wrapping on narrow rows.
The updated app is open in the emulator. Version remains 0.97/9.

Natural address formatting follow-up: all 47 normal-ID JVM tests, the focused
POI text layout emulator test, and debug lint passed. The screen test now checks
street + house number, city · coordinates while supplying an SE country code,
and still checks the no-address coordinate fallback. The updated local app was
opened on the emulator. Version remains 0.97/9; stored data/pack format unchanged.

## 0.97 Beta 1 candidate verification (2026-10-23)

For v0.97-beta.1 (0.97/code 9), all 47 debug and 47 release JVM tests passed,
along with release lint and optimized signed assembly. The full API 35 local-ID
suite XML reports 41 tests: 37 passed, four optional live/fixture cases skipped,
zero failures/errors. The skipped cases cover public download, real Skåne fixture,
live launch/download and higher-version installer confirmation. The Gradle console
reported 45 including repeated skip notifications; XML is the count used here.
The exact signed beta upgraded/launched the existing production-ID emulator app.

APK package/version and candidate-specific MANIFEST_URL match the generated
manifest APK/icon URLs. Signature matches the established SHA-256 certificate
ce73d47787626c776d4d578d62c98a7de4eb814bf5b701a58679df8ffdb002f6.
Candidate APK SHA-256: b3121725cc679fbc54c339432d01d760eba1bd1075b34a9733e767cff302c8d1.
Source commit, checksum and test details accompany the beta in build-info.json
and SHA256SUMS. Real Karoo acceptance remains pending; stable 0.96 is unchanged.
No physical device was installed and the legacy release workflow was not used.

## Settings available-update action (unreleased 0.98 / 10)

All 47 normal-ID debug JVM tests, three AppUpdateSettingsTest API 35 emulator
cases, and debug lint passed. ViewModel coverage exercises combined download,
revalidation and automatic installer-event delivery, failed-download retry and
cancellation without installation. Screen coverage verifies check/download/install
callbacks, busy progress, cancellation and reverting to Check for updates when a
channel has no candidate. Initial screen cancellation assertion tapped behind
the floating action; the test now scrolls through trailing padding to expose it.
The final rerun passed. The local-ID APK was built and emulator refreshed.

Production APK installation remains disabled for local-ID builds. UI tests inject
installation availability and fake callbacks, without opening a real installer.
Physical Karoo behavior remains untested; published 0.97 Beta 1 is unchanged.
The release-notes documentation move is preserved in this branch; no commit,
push or new publication was performed for this batch.

## Local 0.98 Beta 1 package (2026-10-23)

Prepared app/build/beta/v0.98-beta.1/ with seven upload artifacts, copyable
GitHub title/description and publication/promotion instructions. All 47 debug
and 47 release JVM tests, release lint, signed optimized assembly and three
Settings emulator tests passed. Package/version, established certificate and
candidate-specific manifest/APK/icon URLs verified. Full emulator suite and
physical device installation were not rerun for this candidate.

APK SHA-256: d5269795d21fe38a695716c069ee6422b97632407c708cc05a4c1d006209b49b.
Source is uncommitted: build-info.json intentionally has sourceCommit null and
sourceState uncommitted. Local finalize.py verifies the prepared source snapshot
and artifact checksums, then records the clean commit after source is committed,
without changing APK bytes. No commit/tag/push/upload/publication performed.

## Karoo POI ride fields (unreleased 0.99 / 11, 2026-10-23)

All 58 normal-ID debug JVM tests passed. New coverage verifies metric distance
formatting, persistent selected-target snapshots across DataStore reopen, safe
conditional clearing after replacement/reselection, corrupt selection fallback,
arrival radius/distinct-fix/reset behavior, date-line distance, and selection
before the native-map call. No Room schema change or migration was needed.

All 12 focused API 35 local-ID emulator tests passed: five PoiFieldTest cases
and seven OfflineHomeTest cases. Field checks cover exported service discovery,
small-cell RemoteViews preview without reading packs, the actual PendingIntent
launch action, selected preview without target reads, empty/No GPS target states,
pack independence and stopping refresh on cancellation. The preview was rendered
at 160x100 pixels and visually inspected: small pin above category icon + 250m.
Distance text scales to the configured width. These tests use stock Android,
not Karoo ride UI or live Karoo location events.

Final normal-ID debug lint and assembly passed. An initial build caught stale
candidate-tag metadata after the app bump, then compilation caught the public
list exposing an internal field type; both were corrected. Initial lint caught
missing translations; field labels/descriptions now cover existing app locales.
Final checks have no failures. No full emulator suite, signed release build,
physical Karoo test, commit, push, tag or publication was performed.

Arrival confirmation runs while the selected field is active: two distinct fresh
fixes within 50m; stale/outside/missing fixes reset it. Actual ride-screen binding,
smallest-cell sizing, GPS/arrival behavior and in-ride app launch require hardware
validation. Large-pack search polling also needs a device performance check.
Suggested beta checks: add both fields to single cells; compare nearest distance;
tap a POI/Favorite and verify Selected POI changes; restart app; approach the target
and verify it clears; select a new target during arrival; test GPS loss and pack
removal. Beta preparation/publication requires the user's next authorization.

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

## Native single-cell field correction (0.99 / 12, Beta 2 unreleased)

The user reports Beta 1 live-field taps failed and graphical fields occupied two
cells on a Karoo 3 with their latest OS (exact build unknown). They chose native
numeric distance fields and an Open POIs controller action. Beta 1 is not accepted
for stable promotion. Its public APK/tag have not been modified.

All 62 normal-ID debug JVM tests passed. New native-stream tests verify an
immediate unavailable state, no nearest search without GPS, raw metre distances
for Karoo unit formatting, collector cancellation, distinct fresh-fix arrival
confirmation, stale-GPS safety and recovery after a failed snapshot search.
Persistent destination and atomic replacement tests remain passing.

All 11 focused API 35 local-ID emulator tests passed: four PoiFieldTest cases and
seven OfflineHomeTest cases. Checks cover both graphical=false registrations,
Open POIs BonusAction metadata, native DISTANCE configuration, No GPS and Select
POI state messages without RemoteViews/GPS/packs, cancellation, and opening the
POIs intent. This does not execute actual Karoo controller dispatch or prove
firmware cell allocation. The old direct preview-PendingIntent test was removed;
it did not reproduce Beta 1's failing live-host interaction.

Final normal-ID debug lint and assembly passed. No full emulator
suite, release build, physical installation, commit, push or new publication was
performed for this correction. Beta 2 reserves version 0.99/code 12 and tag
v0.99-beta.2, retaining the name for the cohesive feature batch while increasing
the distributed candidate code. Published Beta 1 remains 0.99/code 11.

Karoo retest: upgrade Beta 2, remove/re-add existing POI fields if their old layout
persists, verify each native field can use one cell, bind Open POIs to a controller,
open the app without GPS, select a POI/Favorite, then test distance and arrival
outdoors. Numeric fields do not provide custom category graphics or field taps.
The default profile-editor sample is controlled by Karoo. Arrival runs while the
native stream is subscribed. No location fix is fabricated or assumed from a
window test; this change handles missing fixes rather than changing GNSS reception.

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

## POI trailing distances and swipe Favorites (0.99 Beta 3 / code 13)

All 62 normal-ID debug JVM tests, debug lint and debug assembly passed. All 13
focused API 35 local-ID emulator tests passed: nine OfflineHomeTest cases and
four PoiFieldTest cases. The new gesture cases check single add/remove toggles,
reverse/partial swipes, long-name favorite visibility, removal from Favorites,
right-side distance placement, distance tapping, no map launch during swipes,
and the named accessibility action. Existing paging/search/focus tests pass.

480x800 screenshots of a saved POI with distance and a long saved name without
distance were visually inspected. The badge is right-aligned, the saved star
follows the name and remains visible with ellipsis, and type/coordinates remain
below. Background swipe hints are hidden from accessibility; named row actions
provide the toggle. The local emulator was restarted after disconnecting before
the initial instrumentation attempt, which ran no tests. A duplicate fixture
name/category selector and a custom-accessibility test API mismatch were fixed;
the final suite has zero failures/skips. No physical Karoo installation occurred.

The existing native fields and controller action still need actual Karoo layout,
controller dispatch, unit and GPS/arrival retesting. The full emulator suite was
not rerun for this candidate. Signed-release checks are recorded below when done.

### Muted cues and persisted first-use hint (same Beta 3 / code 13)

The follow-up adds compact +star and minus-star cues in muted Hammerhead yellow
and muted red. A cosmetic first-row offset demonstrates a left swipe once for
users with no Favorites. Favorites and preferences must both load first; the
hint is not consumed on an empty list. The shown flag survives DataStore reopen,
and removing all Favorites later does not re-enable it. Motion uses the Compose
animation clock, returns to zero and is interrupted by touch without toggling.
The swipe component's state is never changed by the hint.

The added JVM persistence case passed (63 debug tests total). Initial animation
verification used clipped bounds; it now checks the actual position and advances
the animation clock. A first visual pass found the plus/minus could be clipped
at a small reveal; compact cue sizing keeps the complete symbol pair visible.
Final full focused-suite and release results are recorded with the candidate.

Final hint/cue verification: all 16 focused API 35 emulator tests passed (12 POI
screen cases plus four native-field cases). Add and remove cue screenshots were
visually inspected at 480x800: the full plus/minus-star pair is visible during a
small reveal. The first-row hint moves only that row, returns to rest, remembers
its shown state and stops on touch; it never toggles or opens a POI itself. All
63 debug JVM tests, debug lint and debug assembly passed on the final source.

## 0.99 Beta 3 release preparation (2026-10-23)

Final candidate v0.99-beta.3 (0.99/code 13) passed 63 debug and 63 release JVM
tests, 16 focused API 35 emulator tests, debug/release lint, debug assembly and
optimized signed release assembly. Add/remove cue, first-row hint, saved-row and
long-name screenshots were visually inspected at 480x800. Candidate package,
version/code, established signing certificate, immutable URLs and APK SHA-256
are verified before upload and recorded with the clean source commit.

The user explicitly authorized the POI row changes, muted cues, one-time hint,
commit/build/publication and updating PR #9. Source is prepared on a new feature
branch and fast-forwarded into the existing PR's head, preserving review to main.
Stable 0.98 and Beta 1/Beta 2 assets remain unchanged. Beta 3 device acceptance is
pending; no physical installation, full emulator suite or legacy workflow run.

## Tappable field variants — 2026-10-24, unreleased Beta 4

Passed 63 debug JVM tests and six focused PoiFieldTest cases on the local API 35
emulator, with zero failures/skips. Debug lint and debug assembly passed. The
focused cases cover all six registrations, native distance/status behavior,
selected no-GPS state, direct activity PendingIntent launch from an applied tile,
non-clickable previews, custom-view refresh without a numeric subscription, and
cancellation of the custom view's location flow. Initial lint found missing new
translations and appcompat's tint rule; translations were added and the tint
rule is locally suppressed because RemoteViews requires platform widgets.

These tests do not run inside Karoo's ride host. On Karoo 3, add nearest and
selected graphical variants independently, then tap during an active ride with
and without GPS. Confirm POI-list navigation and that choosing a POI updates the
selected field while nearest continues searching downloaded packs. Confirm
arrival clearing still works with only a graphical selected field present.
Repeat for compact experimental registrations, recording actual grid size,
whether custom rendering is accepted and whether the tap reaches the activity.
If Karoo ignores those custom views, use the retained native fields plus Open
POIs controller action. Preview taps must not open the app. No physical device
installation, signed beta build, commit, push or publication was performed.

## 0.99 Beta 4 release preparation (2026-10-24)

Candidate v0.99-beta.4 uses 0.99/code 14. Passed 63 debug and 63 release JVM tests,
six focused API 35 PoiFieldTest cases, debug/release lint, debug assembly and
optimized signed release assembly. The applied RemoteViews click test verifies
the app intent locally; actual Karoo ride host dispatch and graphical/compact
allocation are still device checks. Custom-view lifecycle tests cover refresh
without a native subscription and cancellation of location collection.

The user authorized updating PR #9 and publishing this beta. Source and version
changes stay on the PR branch targeting main. Publication uses the exact committed
source and established signing identity with candidate-specific metadata and
checksums. No physical installation or full emulator suite was performed. Device
acceptance is pending; stable/latest and all earlier candidate assets are retained.

## Extension startup failure — 2026-10-24, unreleased Beta 5

Device feedback on Beta 4: all tested variants showed No Sensor and did not open
the app. A new emulator constructor regression reproduced the exact exception
`IllegalStateException: extension ID cannot contain '.'` before the fix. Previous
field tests bypassed KarooExtension construction and could not catch this.

Replace the package-shaped extension ID with `waypoints` in the service, data-type
identity and XML, keeping application ID/storage unchanged. Device upgrade steps
must explicitly remove/re-add the POI fields and rebind Open POIs because Karoo
stores the extension identity in those bindings. Keep all six nearest/selected
presentations; do not claim compact rendering/taps are established by this fix.

The expanded integration test binds the actual manifest service, checks its SDK
version and dispatches all registered streams/views through IKarooExtension. A
real ViewEmitter regression checks that the initial Select POI update survives
SDK throttling. Custom updates now wait 1000ms and send the latest reading.
The first eight-case focused suite passed; additional real-emitter/stream test
coverage required correcting callback names to match the pinned SDK. Final test
results are recorded below. No physical Karoo installation or beta publication
has occurred for this fix.

Final Beta 5 local checks: 63 debug JVM tests and all nine focused API 35 emulator
cases passed, zero failures/skips; debug lint and debug assembly passed. Service
construction, actual manifest service binding and all six stream/view dispatches
now pass. The real SDK emitter delivers the delayed Select POI tile. An intermediate
integration-test assertion raced the stream's valid No POIs result against its
initial No GPS preview; the test now verifies the preview before starting the
stream. No release build, commit, push, tag, publication, full emulator suite or
physical Karoo test was performed for this fix. Actual Karoo taps and compact
allocation remain pending; a corrected Beta 5 is proposed for device retesting.

## 0.99 Beta 5 release preparation (2026-10-24)

Candidate v0.99-beta.5 uses version 0.99/code 15 and the established signing key.
Passed 63 debug and 63 release JVM tests, nine focused API 35 emulator tests,
debug/release lint, debug assembly and optimized signed release assembly. The
previous constructor failure was reproduced before the fix; actual manifest
service binding, all six stream/view dispatches and the real SDK emitter status
transition now pass. Karoo ride-host taps/sizing and GPS/arrival retesting remain
pending. No physical installation or full emulator suite was performed.

The user authorized updating PR #9 and publishing this candidate. Source/version
changes stay on the PR to main. Candidate artifacts record the exact committed
source, certificate, immutable URLs and APK checksum. Remove/re-add all POI fields
and rebind Open POIs after upgrading because the extension ID changed; keep app
storage by upgrading without uninstalling. Stable/latest and earlier assets are
preserved. Device acceptance is pending; no stable promotion is authorized.

## POI tile details — 2026-10-24, unreleased Beta 6

Passed 63 debug JVM tests, all 11 focused PoiFieldTest cases on the local API 35
emulator, debug lint and debug assembly. The new view cases verify nearest name/
category refresh when distance is unchanged, selected identity without GPS, and
clearing name/icon with selection. Existing real service construction/binding,
all six stream/view dispatches, real emitter throttling and tap tests still pass.
An initial test fixture used the wrong copy parameter name; it was corrected to
OfflinePoi.osmId before the successful run.

Emulator-rendered RemoteViews previews were visually inspected at 240x100dp and
120x80dp (480x200px and 240x160px). The small map pin sits above an ellipsized name;
the yellow category icon sits beside the distance below. Both sizes retain readable
name/distance and the full category icon. The descriptions document profile-editor
replacement and unavailable long-press editing in English, German, French, Spanish
and Italian. Category icons reuse the existing list mapping.

Beta 5 feedback says the fields seem to work; this does not imply stable-release
acceptance or verification of every hardware scenario. Beta 6 still needs on-device
layout/tap checking, particularly the compact experimental format. The valid
extension ID and all six type IDs remain unchanged from Beta 5. No physical
installation, full emulator suite, release build, commit, push or publication was
performed for this batch. A Beta 6 candidate is proposed for device retesting.

### Native-style tile alignment follow-up (same Beta 6 / code 16)

The supplied Karoo distance-field reference prompted a top-left header: 12dp
outline pin beside an ellipsized name, followed by a left-aligned category icon
and 28sp distance. Missing-state text remains 20sp. Updated RemoteViews previews
were visually inspected at 240x100dp and 120x80dp; the full outline pin and category
icon are visible and the 1.2 km value remains readable in the compact example.

All 11 focused API 35 emulator tests passed on the final layout; debug lint,
debug assembly and the 63 debug JVM tests passed for this follow-up. The first
instrumentation attempt exited before running any cases, and the retry hit
emulator property-fetch timeouts. The stalled test client/emulator were stopped
and the existing AVD restarted without wiping data; the final run completed with
zero failures/skips. No physical installation, release build, commit or publication.

### Full-name / right-distance revision (same Beta 6 / code 16)

The latest requested layout supersedes the left-distance mockup: the outline pin
and POI name stay at the left, the category icon stays at the lower left, and the
distance aligns to the lower right. The name uses available height, wraps without
ellipsis and auto-sizes from 14sp to 10sp. All 11 focused API 35 tests, debug lint
and debug assembly passed. The preview test explicitly checks multiline layout,
the last visible character, vertical fit, zero ellipsis and right distance gravity
at 240x100dp and 120x80dp. The full long-name sample is visible in both rendered
previews, which were inspected. No new version increment, commit or publication.

### Separate field header and POI body (same Beta 6 / code 16)

The final requested structure uses a small outline pin and fixed, localized
Nearest POI/Selected POI header. The body row places category icon/full wrapping
name at the left and distance at the right. Compact distance is 16sp to reserve
name width; graphical distance is 24sp. Name auto-sizing ranges from 8–14sp.
The label remains visible even when the selected destination clears.

All 11 focused API 35 emulator cases, debug lint and debug assembly passed.
Tests assert the nearest and selected header labels separately from POI names,
and verify the full long-name sample fits without ellipsis in 240x100dp and
120x80dp previews. Both final previews were visually inspected. The compact
example uses smaller body text to fit its full name. No commit or publication.

## 0.99 Beta 6 release preparation (2026-10-24)

Candidate v0.99-beta.6 uses 0.99/code 16. Passed 63 debug and 63 release JVM tests,
11 focused API 35 emulator cases, debug/release lint, debug assembly and optimized
signed release assembly. Final previews at 240x100dp and 120x80dp were visually
inspected: a fixed field label sits above category/name/distance; the full long-name
sample fits without ellipsis and distance aligns right. Compact uses smaller text.
Actual Karoo layout/readability/tap retesting and the full emulator suite remain
pending; no physical installation occurred.

The user approved the final layout and authorized updating PR #9 and publishing
this beta. That is not stable-promotion acceptance. Source/version changes remain
on the PR to main. Candidate records include the exact committed source, signing
certificate, immutable URLs and APK SHA-256. Extension/type IDs remain unchanged
from Beta 5; upgrades from Beta 4 or older require field/controller rebinding.
Stable/latest and all earlier candidate assets are retained.
