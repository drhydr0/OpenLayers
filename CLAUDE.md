# CLAUDE.md

Instructions for Claude Code working in this repository. Read this first in every session.

## Project

A native Android image editor built around editable layers, text, reusable stickers, and direct touch manipulation. The full product and architecture spec is `docs/image_editor_high_level_design.md`. When this file and the design doc disagree, ask before acting. Do not resolve the conflict silently.

**Current milestone:** Editable canvas. All steps are built; waiting on the phone test in `docs/milestone_2_phone_test.md`. Plan: `docs/milestone_2_editable_canvas_plan.md`. Update this line when a milestone's gate is met.

## How this project is developed

- The developer works **only from an Android phone** (Pixel 9 Pro), through the Claude mobile app. There is no local computer, no Android Studio, and no ADB.
- You work in a cloud VM. You can build and run JVM tests here. You cannot run the app on a device or emulator in the session.
- GitHub Actions is the authoritative build. The phone installs APKs from the `dev` GitHub Release.
- The developer reviews diffs on a phone screen. Keep changes small and focused, with one feature or fix per branch. Avoid unrelated reformatting or drive-by refactors, because they make phone review painful.
- When you finish a task, say in plain language what the developer should test on the phone, and how.

## Commands

Run from the repository root with the Gradle wrapper. Never use a system `gradle`.

| Purpose | Command |
| --- | --- |
| Unit tests (JVM) | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Debug APK | `./gradlew assembleDebug` |
| Full pre-push check | `./gradlew testDebugUnitTest lintDebug assembleDebug && scripts/check-apk-abis.sh app/build/outputs/apk/debug/app-debug.apk` |
| Single test class | `./gradlew testDebugUnitTest --tests "<fully.qualified.ClassName>"` |

Before pushing, run the full pre-push check and make sure it passes. If a clean build exceeds the foreground command limit, run it in the background and check on it. Don't skip it.

If the Android SDK is missing (`sdkmanager` or `ANDROID_HOME` not found), the cloud environment's setup script or network settings are wrong. Stop and tell the developer. Don't try to work around it. The environment needs Custom network access that includes `dl.google.com`.

## Tech stack

- Kotlin, JDK 21 toolchain, single `app` module.
- Standard Android Views for UI controls. A custom `View` draws the document canvas. No Jetpack Compose unless the developer decides otherwise.
- Every version is pinned in `gradle/libs.versions.toml`: Android Gradle Plugin, Kotlin, SDK levels, and libraries. Never use dynamic versions (`+`, `latest.release`).
- Keep dependencies minimal. Before adding a library, explain why in the PR description and confirm that the platform can't do the job reasonably.
- The app must run on 64-bit-only phones (Pixel 7 and later, Samsung S26, and similar). Prefer libraries without native code. Any native library must ship an `arm64-v8a` build. CI enforces this with `scripts/check-apk-abis.sh`.

## Architecture boundaries

Packages under the app's root package, matching the design doc's components:

| Package | Responsibility | Android classes allowed? |
| --- | --- | --- |
| `document` | Layer tree, transforms, groups, styles, crops, masks, asset references | **No** |
| `history` | Commands, undo/redo, checkpoints, retention, compaction | **No** |
| `persistence.format` | Manifest schema, serialization, migrations | **No** |
| `persistence` | Transactions, autosave, recovery, asset storage on disk | Yes |
| `render` | Canvas preview, thumbnails, captures, export | Yes |
| `interaction` | Hit testing, gesture ownership, selection, snapping, transform previews | Yes |
| `ui` | Screens, toolbars, sheets, layers panel, accessibility | Yes |
| `platform` | Photo picker, files, gallery, share sheet, lifecycle | Yes |
| `diagnostics` | Crash handler, log buffer, diagnostics screen and export | Yes |

Rules:

- `document`, `history`, and `persistence.format` must stay plain Kotlin with no `android.*` or `androidx.*` imports, so they can be tested on the JVM here. Represent geometry with project-owned types such as a matrix, rect, or color.
- Every document change goes through a `history` command. UI and interaction code never mutate the document directly. Gesture previews stay temporary until committed as one command.
- Preview, sticker capture, and export share one rendering path. Never fork geometry, text layout, or clipping logic between them.
- Image decoding, import, export, thumbnails, and disk I/O run off the main thread. Results carry a document or asset version, so stale work never overwrites newer edits.
- Layers have stable IDs. Never use list indices as identity.
- Image bytes are immutable once stored. Any edit that changes an asset creates a new asset version.

## Testing

- New logic in `document`, `history`, and `persistence.format` needs JVM unit tests in the same change. Cover undo and redo for every new command, including multilayer and nested-group cases.
- Save and recovery code needs tests that simulate an interrupted write and verify that the last committed state loads.
- Tests that need Android graphics, such as rendering, export pixels, and gestures, are instrumented tests that run on an emulator in CI. Mark them clearly, and tell the developer the result comes from CI, not from this session.
- Don't claim something works on the device. Say what you verified (tests, build) and what still needs a phone check.

## CI, releases, and signing

- `.github/workflows/pr.yml` runs unit tests, lint, and `assembleDebug` on pull requests, and uploads the APK as an artifact.
- `.github/workflows/main.yml` builds on pushes to `main` and replaces the APK on the `dev` pre-release.
- Builds are signed with one stable key held in GitHub Actions secrets. **Never** generate a new keystore, commit a keystore or passwords, or change signing config without explicit approval. A changed signature forces an uninstall and wipes the developer's projects.
- Never change `applicationId`, for the same reason.
- When CI fails, read the logs with `gh api` (REST), fix the cause, and push again. Don't disable or loosen a check to make it pass.

## Diagnostics (there is no ADB)

The app must be able to report on itself, because the developer can't read logcat:

- An uncaught-exception handler saves the stack trace, version name, commit hash, and device info. The next launch offers to share the report.
- A diagnostics screen shows recent app log lines, memory, cache sizes, layer count, and history size, and exports them as text through the share sheet.
- Debug builds show the commit hash on the project screen.
- Log through the project's logging wrapper in `diagnostics`, never bare `android.util.Log`, so lines reach the in-app buffer.
- When the developer shares a crash report, diagnostics file, or screenshot, start by locating the commit hash and the matching code.

## Coding conventions

- Kotlin official style. Keep functions small and name things after the design doc's terms: layer, group, sticker, checkpoint, scope.
- Prefer immutable data for document state. Commands hold before and after values, or enough to compute them.
- No `!!` in production code without a comment explaining why it's safe.
- User-facing strings go in `res/values/strings.xml`. Every interactive control gets a content description.
- Touch targets are at least 48dp. Support system text scaling.
- Comments explain *why*, not *what*.

## Git and pull requests

- One branch per task. The commit message summarizes the change in the first line.
- PR descriptions are short and phone-readable: what changed, how it was tested, what to check on the device, and any open questions.
- Update `docs/` when behavior or architecture changes. Update the "Current milestone" line above when a milestone's gate is met.

## Milestones

Work through these in order. Don't start features from a later milestone without being asked.

1. **Build foundation.** Cached SDK setup, green CI, signed APK on the `dev` release, update-install over a previous build without data loss, crash report and diagnostics export.
2. **Editable canvas.** Import images, add text, transform objects, basic layers, save, reopen, export.
3. **Reliable editing.** Persistent undo and redo, atomic multilayer operations, autosave recovery, locks, visibility.
4. **Reusable compositions.** Grouping, independent duplicates, editable stickers, sticker collection, nested editing.
5. **Selection tools.** Rectangle and lasso with scope, canvas or layer capture, transparent image stickers.
6. **Polished workflow.** Shapes, text styling, image adjustments, snapping, alignment, distribution, accessibility.
7. **Capacity verification.** Large-document tests, history retention, export consistency, emulator tests in CI.

The acceptance scenarios in the design doc define "done" for the first release.
