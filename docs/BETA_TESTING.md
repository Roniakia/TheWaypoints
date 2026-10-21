# TheWaypoints 0.96 Beta 1 — device testing pending

Candidate `v0.96-beta.1`, version **0.96 (code 8)**. Stable remains 0.95.
Upgrade the existing independent app without uninstalling to preserve favorites
and downloaded packs. Share the APK URL itself with Hammerhead Companion:

https://github.com/Roniakia/TheWaypoints/releases/download/v0.96-beta.1/waypoints-0.96-beta.1.apk

This candidate includes Favorites, pack-only search, category icons, automatic
paging, Karoo yellow controls, refined Regions guidance and a channel updater.
The release's build-info.json records the exact source commit, APK SHA-256 and
signing certificate. Device acceptance is pending; do not promote it to stable
until this exact APK is accepted.

1. Confirm installation stays at 0.96/code 8 and launcher label TheWaypoints.
   Record Karoo model and KOS version. Confirm existing favorites/packs remain.
2. Open POIs with Skåne installed. Check rounded search, clear action, category
   icons and filters. Search `water`, then scroll to load more results. Empty
   search results should not recommend downloads if your region is installed.
3. Favorite POIs, open Favorites and test its text/category filters and sorting.
   Switch pages and confirm their search choices remain independent. Unfavorite
   a place and check it disappears. Saved places remain after pack removal.
4. Open Regions: no download invitation or Recommended section. Test hierarchy,
   search, selection count inside the full-width bottom button, download/update,
   cancellation/retry and removal. Content should scroll above the floating action.
5. With no packs installed, POIs should offer Choose regions. With GPS inside an
   available but missing region, it should also offer region download guidance.
   With a pack installed and no position, no location-based prompt is expected.
6. Open Settings. Check Stable/Beta radio selection, persisted channel, last-check
   time and floating Check for updates. Stable excludes prereleases; Beta includes
   both. No update is expected until a candidate with a higher code is published.
7. On a future update, check release notes, download progress/cancel/retry and the
   Android install confirmation. Grant unknown-source permission only if you
   choose to install. If unsupported on Karoo, use the APK link with Companion.
   Switching back to Stable must not downgrade the beta or erase local data.
8. With real GPS, check nearest sorting and native Karoo map handoff. Try offline
   pack search with connectivity disabled. Online Photon search is removed.

Known limitations: only Skåne prototype data (19,159 POIs; one source-wide OSM
area omitted), English UI and metric offline distances. No map renderer/routing.
API 23 runtime, Karoo power management, app install permissions and update handoff
need device validation. Local emulator builds cannot install production updates;
a higher-version local fixture tested system confirmation without installing.

---

# 0.95 Beta 2 — accepted and promoted to stable

On 2026-10-21 the user confirmed this candidate works on their Karoo and
authorized stable release, accepting current UI roughness. Stable 0.95 uses the
identical Beta 2 APK, tag and source commit; reinstalling is unnecessary for
Beta 2 users. Device model/KOS were not recorded. The steps below are retained
as the original testing record.

---

# 0.95 Beta 2 installation fix

Candidate `v0.95-beta.2`: app version 0.95, code 7. Share the actual versioned
APK link to Hammerhead Companion, not the GitHub page or a JSON manifest:
https://github.com/Roniakia/TheWaypoints/releases/download/v0.95-beta.2/waypoints-0.95-beta.2.apk

This candidate embeds its own update manifest URL. Confirm Karoo continues to
show 0.95 (7) through download and installation, then opens Regions with the
menu beside search. Keep the existing app installed to preserve bookmarks.
Record device/KOS and any remaining installer issue. Native Karoo installer
behavior remains unverified until the user tests it. Continue the offline-flow
steps below after installation. Beta 1 notes are retained as history.

---

# 0.95 Beta 1 proposal — not yet published

Planned candidate: `v0.95-beta.1`, Android version 0.95 (code 6), signed with the
existing application identity. Publication and physical installation await the
user's acceptance of this beta proposal. The source commit, APK hash and signing
certificate will be recorded with the candidate before publication.

1. Upgrade the existing independent Waypoints app; keep existing bookmarks.
2. On first launch, search `skane` in Regions, select Skåne and use Update selected.
   Check progress, cancellation/retry, and downloaded-first placement on reopening.
3. Switch to POIs using the menu beside search. Disable connectivity, search
   `water` or a place name, try filters and scrolling beyond 30 results.
4. With GPS available, check nearest ordering; without GPS, check name fallback.
   Test the small-screen keyboard and filter sheet on the actual device.
5. Bookmark a downloaded POI, restart, remove Skåne, and confirm the saved POI is
   still available under the star button. Re-download and check update behavior.
6. Tap a POI to open Karoo's map. Try Search online with connectivity restored.
7. Report model/KOS, candidate tag, failures and explicit acceptance when satisfied.

Known limits: one published Skåne prototype (19,159 POIs), one invalid source-wide
OSM relation omitted; new UI English, offline distances metric; no offline map
renderer or routing. API 23 runtime and Karoo background/power-management behavior
have not been validated. Multi-region UI cases use synthetic emulator fixtures.
Never uninstall the existing app merely to work around an upgrade error.

---

# 0.94 Beta 1 device testing

**Accepted and promoted on 2026-10-21.** The user reported that it works great
on their real Karoo and authorized stable publication. Device model and OS were
not specified. Stable 0.94 reuses the original tag and every attached asset;
no rebuild or app-version change occurred. The checklist below records the beta
process, not a new request to retest.

Candidate: `v0.94-beta.1`, Android version 0.94 (code 5), application and Karoo
extension ID `io.github.roniakia.waypoints`. The release's build-info.json records
the exact source commit, APK SHA-256 and signing certificate fingerprint.

Download `app-release.apk` from the GitHub prerelease. Our independent app and key
allow it to coexist with the original Waypoints. Existing original-app bookmarks
are not migrated. Both apps may currently display the same name/icon; check the
application ID when identifying them. This beta is release-built and signed with
our dedicated private key, not the Android debug key.

The SDK release notes list KOS 1.634.2440+ and do not explicitly list Karoo 2.
Local emulator tests cannot establish Karoo service compatibility. Record the
Karoo model and OS version with your results. If installation fails, share the
error message rather than deleting your existing app or bookmarks.

## Test steps

1. Install the downloaded APK using the device's supported sideloading method.
   Keep the original app installed if you want to compare behavior.
2. Open our app. Confirm the screen and keyboard work at device size.
3. Run a global search for a known place. Try a second query; check search focus,
   result names/addresses, empty results, and network-error behavior.
4. Enable nearby search with a GPS fix. Check nearby ordering and the units
   selected in your Karoo profile. Distances are straight-line distances.
5. Bookmark a place, close/reopen the app, and confirm the bookmark remains.
   Disable connectivity and confirm saved bookmarks are still available.
6. Restore connectivity, select a place, and verify the native Karoo map opens at
   its coordinates. Route calculation is handled by Karoo.
7. Delete a bookmark and confirm it remains deleted after reopening.

Report what passed, failures, device/OS, candidate tag and any error messages.
Do not post private coordinates or searches if they are unnecessary to reproduce
an issue. Say explicitly when this candidate is accepted for stable release.

## Promotion

After the user's explicit acceptance and release authorization, promote this
GitHub prerelease (or publish the identical assets at a stable tag on the same
commit). Do not rebuild, change signing identity, edit embedded versions, or
substitute a newer APK. Any changed distributed candidate gets a new tag and
increasing versionCode and must be tested again.

The private signing key and password stay outside Git and release assets. Keep
a private backup; future updates must use the same key. No publishing workflow
that pushes directly to protected main should be used.
