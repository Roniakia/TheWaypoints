# Release notes

### v0.99 — Beta 4, 2026-10-24

Version 0.99/code 14; candidate `v0.99-beta.4`; device acceptance pending.

- Retain native one-cell Nearest POI and Selected POI fields and the Open POIs controller action.
- Add tappable graphical variants for both modes, opening the POI list even without GPS.
- Add separately named experimental compact tappable variants for both modes.
  Karoo may ignore custom rendering/taps on these numeric registrations; one-cell operation is unverified.
- Graphical/compact tiles display metres/kilometres; native fields retain Karoo unit formatting.
  Nearest searches downloaded POIs; selected uses the saved target and existing arrival clearing.
- No data migration or dependency change. Actual ride tapping and host sizing need Karoo testing.

### v0.99 — Beta 3, 2026-10-23

Version 0.99/code 13; candidate `v0.99-beta.3`; device acceptance pending. Earlier beta assets remain unchanged.

- Move the yellow POI distance badge to the right-hand position formerly used by
  the favorite toggle, leaving more room beside the POI name.
- Show a gold star immediately after a favorited POI name. Unfavorited POIs
  have no star. Long names can wrap while retaining a visible favorite marker.
- Swipe a POI from right to left to add/remove it from Favorites. A completed
  swipe toggles once; partial or reverse swipes do not. The row returns to rest.
- Apply the same gesture in Favorites, where removing an item removes its row.
  Accessibility services get named Add to favorites/Remove from favorites actions.
- Swipe cues show muted Hammerhead-colored +star when adding and muted-red minus-star
  when removing. A first-use swipe-and-return hint on the first POI demonstrates
  the gesture only for users with no Favorites; its shown state is remembered.
- Tapping the row or distance still selects the POI and opens Karoo’s map.
  Native single-cell ride fields and Open POIs controller action are retained.

### v0.99 — Beta 2, 2026-10-23

Version 0.99/code 12; candidate `v0.99-beta.2`. Published Beta 1 remains unchanged.

- Replace graphical POI tiles with native numeric distance fields so each can use
  one normal cell. Karoo formats straight-line distances in the rider’s units;
  the custom pin/category layout and field-tap action are removed.
- Add Open POIs as a Karoo controller BonusAction. It opens the POI list without
  requiring GPS; tapping a POI/Favorite still saves it before native-map launch.
- Show No GPS, No POIs or Select POI explicitly, without blocking field setup
  on GPS or a service connection. Invalid/stale fixes never become zero distance.
- Selected-target arrival confirmation now follows the native stream lifecycle;
  two distinct fresh fixes within 50m clear the target, preserving replacements.
- After upgrading, remove/re-add the two fields if Karoo retains the old graphical
  layout. Bind Open POIs in the Karoo controller configuration. Actual firmware
  layout and controller dispatch still require a device retest.

### v0.99 — Beta 1, 2026-10-23

Version 0.99 (code 11), candidate `v0.99-beta.1`; real Karoo acceptance pending.

- Add the Nearest POI Karoo ride data field, designed for a single cell: a small
  pin above the nearest downloaded POI category icon and compact metric distance.
- Add a separate Selected POI field. Tapping a POI or Favorite saves it as the
  destination and opens the native Karoo map. The selection survives restarts
  and region removal; selecting another POI replaces it.
- Automatically clear the selected destination after two distinct fresh GPS
  fixes within 50 metres while the field is active. An old arrival check cannot
  clear a replacement selection. An empty field says Select POI.
- Tap either field to open the app on POIs. The profile editor shows a sample; the
  live field reports missing GPS or POIs and clears GPS older than 30 seconds.
- Uses all downloaded categories independently of app filters; distances are
  straight-line, refreshed every five seconds while visible, with no network
  requests. Nearest POI excludes Favorites; Selected POI supports them. Version
  0.99/code 11.

### v0.98 — unreleased (Beta 1 prepared locally)

- Settings replaces Check for updates with Download and install when the chosen
  channel has an update. Verified downloads open the Android installer; downloaded
  APKs can be installed again from the same bottom action.

- Move release history out of README and provide a manual beta publication/stable
  promotion guide. Beta preparation now creates local upload files and copyable
  release text without publishing to GitHub.

### v0.97 — Beta 1, 2026-10-23

Version 0.97 (code 9), candidate `v0.97-beta.1`; device acceptance pending.

- Addresses use street, house number and city without bracketed country codes.
- POI rows show distance beside the name, type below, then address and coordinates.
- Empty Favorites distinguishes no saved places from no search/filter matches,
  without a Browse POIs action.
- Sidebar items have side margins, boxier corners and a bright Karoo yellow
  selection with black text.
- Region download guidance uses a Karoo yellow arrow. Disabled bottom actions
  have an opaque gray fill so scrolling content cannot show through.
- Saved POI stars are gold. Scrolling dismisses the search keyboard; tap the
  field again to resume typing.
- Search fields are slightly shorter with margins around the page header.
- POI and Favorites rows show compact straight-line distances, such as `100m`
  and `1.2km`, in a Hammerhead yellow label when a location is available.


### v0.96 — stable, 2026-10-22

Promoted the accepted `v0.96-beta.1` without rebuilding: version 0.96 (code 8),
source `a9bcdbad60f9a5a1633ac66941db66a0a4bbba71`. The tag, APK filename,
signing identity and all release assets retain their tested beta identity.
Existing Beta 1 users do not need to reinstall.

- Add Favorites with search, category filters, sorting, automatic paging, and
  independent search/filter choices from POIs. Favorites survive pack removal.
- Search downloaded regional packs and saved Favorites locally; remove the
  online Photon search entry from the app.
- Add category icons, rounded clearable search fields, and automatic next-page
  loading with a manual retry action.
- Start on POIs. Show download guidance only when no regions are installed or
  the current catalogued region is missing; simplify Regions to downloaded packs
  and the geographic hierarchy.
- Standardize full-width floating bottom actions with counts inside, consistent
  margins and a subtle bottom fade. Keep the separate circular filter icon.
- Add Settings with Stable/Beta radio selection, daily launch checks, manual
  checks, release notes, progress/cancellation, and verified APK downloads.
  Installation uses Android's confirmation screen, with a Companion link fallback.
- Use Hammerhead Lemon Yellow accents and black button text. Rename the app and
  Karoo listing to TheWaypoints and update links after the repository rename.
- Preserve application ID, signing identity, bookmarks and downloaded packs.
  No dependency upgrade or Room migration is included in this batch.

Only Skåne prototype data is available. New screens are English and offline
straight-line distances are metric. The user accepted Beta 1 on 2026-10-22; Karoo model/KOS were not specified.
Installer permission, future update handoff and device performance are not
comprehensively verified across devices; no maps/routing engine is
included. Stable-channel selection never downgrades an installed beta.

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

### POI data 2026-10-prototype.2 — prerelease, 2026-10-21

- Add reusable OSM extraction, administrative boundaries, category mapping,
  transliteration/alias search, spatial indexing, and immutable regional SQLite packs.
- Provide the first Skåne pack, source/pack checksums, attribution, and a signed
  catalogue with a separate Ed25519 data-signing identity.
- Add generation/download validation and ten server tests. One invalid source-wide
  swimming-area geometry is documented for review.
- Data-only prototype: no APK or app version change was included in that release.
- [Download the data prerelease](https://github.com/Roniakia/TheWaypoints/releases/tag/poi-data-2026-10-prototype.2).
  All seven public assets, their checksums and the pinned-key catalogue signature
  were verified after publication. App 0.94 remains the latest stable release.
- The separate Android foundation ([draft PR #3](https://github.com/Roniakia/TheWaypoints/pull/3))
  reads the real pack on API 35; 17 JVM tests, 25 emulator tests and lint pass.
  Android uses regular coordinates/FTS4 because stock SQLite lacks RTree.
  Those were intermediate foundation results; later Android download/UI checks
  are recorded in [LOCAL_TESTING.md](docs/LOCAL_TESTING.md).


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
