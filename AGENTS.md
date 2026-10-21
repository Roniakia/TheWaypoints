# Working agreement

- Never commit changes unless the user explicitly asks. Do not push, merge, tag,
  publish a release, or trigger a workflow that commits/pushes without explicit authorization.
- Start every new feature on a new Git branch before editing implementation.
  Use `codex/<feature-name>` by default. Check `git status` first; preserve all
  existing work and do not move unrelated edits between branches silently.
- Use `main` as the integration branch and target pull requests to `main`.
  Keep feature work on its own branch until reviewed and merged.
- Prefer local verification before trying changes on a physical Karoo. Device
  installation and testing should wait until the user requests that phase.
- Read [the project notes](docs/PROJECT_NOTES.md) and
  [the local testing plan](docs/LOCAL_TESTING.md) before implementation.
- Update these notes when architecture, build setup, or testing behavior changes.
- Keep credentials, Firebase configuration, signing keys, and local SDK paths out
  of tracked files. Never print tokens or passwords while diagnosing builds.

# Repository conventions

Single Android/Kotlin app module, Jetpack Compose UI, Hilt dependency injection,
coroutines/Flow, Room storage, DataStore settings, and Hammerhead karoo-ext.
Use provider interfaces and injected dispatchers/time for testable behavior.
Preserve Room schema history and add migration coverage for database changes.
Do not upgrade dependencies incidentally: the version catalog documents a
Compose/Lifecycle combination that caused search focus problems.

# Verification

With prerequisites available, start with `./gradlew :app:testDebugUnitTest`.
Use `:app:connectedDebugAndroidTest` for Room/migration/Compose instrumentation
on a local emulator. Full app behavior currently depends on Karoo services;
see the testing plan for the proposed simulator, which is not implemented yet.
Report blocked checks honestly; never claim passing tests without results.

The manually dispatched release workflow contains `git commit` and `git push`.
Do not run it as a build verification shortcut.

# Privacy and version maintenance

- Do not add external analytics, usage tracking, or remote crash reporting unless
  explicitly requested. Keep Timber logging local. Photon search and native Karoo
  integration remain authorized core functionality.
- Use the personal `waypoints-versioning` skill for functional, dependency, and
  build changes. Bump once per cohesive unreleased batch, reuse an existing bump
  during follow-ups, and keep README release notes and project notes coherent.
  Documentation-only changes do not require an app bump. This authorizes no
  commit, push, release, or physical device installation.

# Commit and release changelogs

- Every authorized commit must have a descriptive subject and a body summarizing
  what changed, why, relevant verification, and any material limitations.
- Every release must include a versioned changelog covering changes since the
  previous release: features, fixes, dependency/build changes, removals, and any
  compatibility or migration requirements that apply. Describe user impact;
  avoid generic messages such as "updates" or a raw list of commit subjects.
- Keep README release notes and published release notes consistent with the
  actual diff and verified results. Preserve previous changelog entries.
- These requirements do not authorize committing or publishing.

# Product direction and beta releases

- Treat this as an independently evolving app, with substantial new behavior and
  design expected. The upstream fork supplied the initial idea and codebase;
  credit the original author and preserve applicable license/copyright notices.
  Do not describe ongoing development as merely maintaining the upstream fork.
- After a meaningful change batch passes local checks, proactively propose a
  tagged GitHub beta prerelease with a downloadable APK for the user to install
  and test on their real Karoo. Include changes, test steps, and known limitations.
- Proposing a beta does not authorize creating tags, uploading, publishing, or
  installing on a device. Proceed when the user accepts that proposal.
- After the user explicitly accepts the device-test results and authorizes the
  release, promote the latest accepted beta to a stable GitHub release using the
  exact tested APK. Preserve its bytes, signing identity, versionName/versionCode,
  and source commit; do not rebuild or silently substitute a newer candidate.
- Give each changed, distributed beta a distinct tag and increasing versionCode.
  Promotion alone does not require a new app version. Track tag, commit, APK hash,
  changelog, and the user's acceptance so the tested candidate is unambiguous.
- All source/version changes go through PRs to protected `main`, including release
  preparation. The old direct-push release workflow needs adaptation before use.
