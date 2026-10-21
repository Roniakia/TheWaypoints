# 0.94 Beta 1 device testing

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
