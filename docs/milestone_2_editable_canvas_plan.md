# Milestone 2 plan: Editable canvas

Goal from CLAUDE.md: import images, add text, transform objects, basic layers, save, reopen, export.

Milestone 1 (Build foundation) closed on 2026-11-17: green CI, signed `dev` release, update-install verified on the Pixel (build 10 to 12), crash report and diagnostics export verified on the Pixel.

## Scope

In scope:

- Project screen: list, create (size presets, custom size, background color), open, rename, delete. "Open image" creates a canvas matching the image.
- Image layers from the Android photo picker. Original bytes stored once, immutable, orientation handled.
- Text layers: content, size, color, alignment, and box width (wrapping).
- Transform by touch: tap to select, drag to move, pinch and twist to scale and rotate, corner scale handle, rotation handle, tap empty canvas to deselect.
- Basic layers panel: select, reorder, delete, rename.
- Canvas navigation: pan and zoom mode, fit to screen.
- Save after each committed edit, reopen after restart.
- Export PNG (optional transparency) or JPEG (quality), chosen dimensions, save to gallery, share.
- Diagnostics show real layer count and history size.

Out of scope here (later milestones): persistent undo history, autosave recovery testing, locks and visibility UI (M3); groups, duplicates, stickers (M4); area selection (M5); shapes, fonts, text effects, image adjustments, snapping, precision sheet (M6). The document model includes `visible`, `locked`, and `opacity` fields now, so later milestones don't need a format migration for them.

## Architecture decisions

- **Geometry.** Project-owned `Vec2`, `Rect`, `Matrix` (2D affine), and `Color` in `document`. A layer's `Transform` is stored as position, scale, rotation, and flip, not a raw matrix, so gestures and later precise controls edit meaningful values. The renderer turns it into a matrix.
- **Document state.** Immutable data classes. `Document` holds canvas size, background, an ordered layer list (bottom to top), and asset references. Layers have stable `LayerId`s. The list is a flat root list now, shaped so M4 can add groups as a tree.
- **Commands.** Every change is a `history` command with before and after values (add, remove, reorder, transform one or many layers, edit text, rename). An in-memory `History` gives undo and redo with branch clearing. M3 makes it persistent.
- **Gestures.** A gesture edits a temporary preview transform. One command is committed when all fingers lift.
- **Assets.** Image bytes are stored by SHA-256 content hash. They are never modified, and identical imports share one file.
- **File format.** `projects/<id>/manifest.json` (versioned schema, with a migration hook from v1) plus `projects/<id>/assets/<hash>`. The manifest is written to a temp file and renamed, so a crash mid-save keeps the previous version.
- **Rendering.** One `DocumentRenderer` draws onto an `android.graphics.Canvas` for the on-screen preview, thumbnails, and export, so they can't drift apart. Text uses `StaticLayout`. Images are decoded with `ImageDecoder`, which applies EXIF orientation, at preview resolution for the screen and full resolution for export.
- **Threads.** Decoding, import, save, thumbnails, and export run on background executors. Results carry the document version, and stale results are dropped.
- **Export sharing.** Saving to the gallery uses `MediaStore` (no permission needed). Sharing uses the saved gallery item's URI, so no `FileProvider` is needed.

## Steps

Milestone closed on 2026-11-17 after the developer ran the phone test. All 15 steps are built. Related steps were combined into one PR where that saved a round of CI.

| # | Step | PR | Verified by |
| --- | --- | --- | --- |
| 1 | Close Build foundation, add this plan | #6 | Review |
| 2 | Geometry types, transforms, layer and document model | #7 | JVM tests |
| 3 | History engine and core commands, undo and redo incl. multilayer | #8 | JVM tests |
| 4 | Manifest format v1: serialize, parse, version check | #9 | JVM tests |
| 5 | Project storage: folders, asset store, atomic save, interrupted-write recovery | #11 | JVM tests |
| 6 | Project screen: list, create, rename, delete, open | #12 | Phone test |
| 7 | Renderer and canvas view | #12 | Phone test |
| 8 | Image import, "open image as canvas", save after every edit | #13 | JVM tests, phone test |
| 9 | Text layers | #14 | Phone test |
| 10 | Selection and touch transforms, undo and redo buttons | #14 | JVM tests, phone test |
| 11 | Layers panel | #15 | Phone test |
| 12 | Canvas navigation | #15 | JVM tests, phone test |
| 13 | Thumbnails, diagnostics counts (saving moved to step 8) | final PR | JVM tests, phone test |
| 14 | Export PNG and JPEG, gallery, share | final PR | JVM tests, phone test |
| 15 | Docs and the combined phone test | final PR | Phone test |

Changes from the original plan:

- Saving after every edit moved from step 13 to step 8, so nothing added from step 8 on is ever unsaved.
- Project-list thumbnails were added in step 13; the design doc asks for them, and they reuse the renderer.
- #10 added a CI check that every APK runs on 64-bit-only phones (no native code today).
- Rendering, gestures, and export pixels have no automated tests yet. Emulator tests are planned for M7, so the phone test in `milestone_2_phone_test.md` covers them for now.

## Decisions (2026-11-17)

1. **JSON library:** add `kotlinx-serialization-json` (JetBrains, pure Kotlin, works in JVM tests). Android's built-in `org.json` is not usable in JVM unit tests, and a hand-written parser is a risk for the save format.
2. **Layers panel list:** add `androidx.recyclerview` at step 11. M3 to M4 need drag reordering and nested groups, where RecyclerView is much better than `ListView`.
3. **Undo and redo buttons:** shown in M2. M3 makes history persistent.

## Workflow

The developer wants minimal input. Claude merges its own PRs once CI passes when they need no phone test, and asks for a phone check only where it is truly needed.
