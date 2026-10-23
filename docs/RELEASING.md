# Preparing, publishing and promoting releases

## Ask Codex to prepare a beta

“Prepare beta” means local preparation, without GitHub publication or tag pushes.
The package is placed in `app/build/beta/<tag>/`:

- `upload/`: upload every file in this folder as a GitHub release asset.
- `GITHUB_RELEASE.md`: the exact title and copyable Markdown description.
- `PUBLISHING.md`: candidate-specific steps and source-finalization command.
- `finalize.py`: records the clean committed source and regenerates checksums,
  without rebuilding or modifying the signed APK.

These build outputs are local and can be deleted by Gradle clean. Keep a private
backup of the complete candidate folder until publication. Never upload signing
keys, passwords, local configuration, debug APKs, or the entire build directory.

Each changed distributed candidate needs a unique tag and a higher versionCode.
The app's embedded Karoo manifest URL and generated APK/icon URLs must all point
to that same tag. Changing just the GitHub tag/name after building is insufficient.

## Publish a beta yourself

1. Review the changes and local test results. Commit source/version changes with
   a descriptive subject and a body explaining behavior, verification and limits.
   Push the feature branch and open a PR targeting protected `main`. Do not push
   source/version changes directly to `main` or use the legacy release workflow.
2. If the prepared source was uncommitted, run the candidate's `finalize.py` from
   the repository root after committing. It verifies that the clean committed
   files match the prepared snapshot, writes the exact source commit to
   `upload/build-info.json`, and refreshes `upload/SHA256SUMS`. If it rejects a
   changed snapshot, ask for a fresh preparation; do not relabel a changed build.
3. Confirm `build-info.json` says `sourceState: committed`. Use its source commit
   as the release tag target. The PR may remain open while testing a beta; the
   tag must identify the feature commit, not an older `main` commit.
4. Create/push the candidate tag at that exact commit, or create it through GitHub
   with that exact target. For example, substitute the package's tag and SHA:

   ```sh
   git tag -a v0.98-beta.1 SOURCE_COMMIT -m "TheWaypoints 0.98 Beta 1"
   git push origin v0.98-beta.1
   ```

   Never move or overwrite a previously distributed tag.
5. In [GitHub Releases](https://github.com/Roniakia/TheWaypoints/releases), choose
   **Draft a new release**, select the candidate tag, and copy the title and
   description from `GITHUB_RELEASE.md` into their respective fields.
6. Attach **all files inside `upload/`**: the versioned signed APK,
   `manifest-karoo.json`, `app_icon.webp`, `build-info.json`, `SHA256SUMS`,
   `release-notes.md`, and `beta-testing.md`.
7. Select **Pre-release**. Leave **Latest** off so the accepted stable release
   remains the stable update target. Publish the release.
8. Confirm the uploaded APK's GitHub SHA-256 matches `build-info.json`, and that
   its filename matches `manifest-karoo.json`'s `latestApkUrl`. Open that manifest
   URL and check versionName/versionCode and candidate-specific asset URLs.
9. Share the APK asset URL with Hammerhead Companion, or choose Beta in the app's
   Settings and check for updates. Upgrade without uninstalling to retain data.
   Follow `beta-testing.md` and record model/KOS, tag, APK checksum and outcome.

## When a beta is good: promote it to stable

1. Explicitly accept the **specific tag and APK** after real Karoo testing and
   authorize stable publication. Review and merge the source/version PR into
   `main` using the repository's normal protected-branch process.
2. Edit the existing GitHub beta release. Change its title/description to stable,
   record the accepted tag/test outcome, select **Latest**, and remove its
   **Pre-release** label (the UI may present these as mutually exclusive choices).
   Publish/save the edit. Keep its existing tag, source commit and every binary
   asset. The APK may retain `-beta.1` in its filename; that is intentional.
3. **Do not rebuild, re-sign, bump versionName/versionCode, replace the APK,
   rename its asset, or move its tag.** Promotion changes release metadata only.
   Keeping tag and filenames preserves the APK's embedded update URLs.
4. Confirm `/releases/latest` resolves to the promoted release and that the APK
   digest is unchanged. Stable-channel users can now discover the accepted APK.
5. Record acceptance and stable status in `RELEASE_NOTES.md`, project notes and
   the local testing record through a documentation PR to `main`. Preserve the
   original build-info record and its historical pending-acceptance value; the
   release description and acceptance notes record subsequent approval.

If anything needs changing after a beta was distributed, prepare a new beta with
a new tag and higher versionCode, test that APK, and promote the accepted one.
Do not change an existing candidate's bytes while keeping its identity.
