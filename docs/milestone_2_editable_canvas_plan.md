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

## Steps (one PR each)

| # | Step | Verified by |
| --- | --- | --- |
| 1 | Close Build foundation, add this plan | Review |
| 2 | Geometry types, transforms, layer and document model | JVM tests |
| 3 | History engine and core commands, undo and redo incl. multilayer | JVM tests |
| 4 | Manifest format v1: serialize, parse, version check | JVM round-trip tests |
| 5 | Project storage: project folders, asset store, atomic save, interrupted-write recovery | JVM tests |
| 6 | Project screen: list, create, rename, delete, open (replaces start screen; debug keeps commit hash) | Phone |
| 7 | Renderer and canvas view: background, images, text, fit to screen | Phone |
| 8 | Image import from photo picker; "open image as canvas" | Phone |
| 9 | Text layers: add and edit content, size, color, alignment, width | Phone |
| 10 | Selection and touch transforms with handles, one command per gesture, undo and redo buttons | JVM tests for math, phone |
| 11 | Layers panel: select, reorder, delete, rename | Phone |
| 12 | Canvas navigation: pan and zoom mode, fit, reset | Phone |
| 13 | Save after each edit, reopen, diagnostics counts | JVM tests, phone |
| 14 | Export PNG and JPEG, dimensions, gallery, share | Phone |
| 15 | Milestone check against the goal, docs update | Phone |

## Open questions

1. **JSON library.** Recommendation: add `kotlinx-serialization-json` (JetBrains, pure Kotlin, works in JVM tests). Android's built-in `org.json` is not usable in JVM unit tests, and a hand-written parser is a risk for the save format.
2. **Layers panel list.** Recommendation: add `androidx.recyclerview` when step 11 arrives. The platform `ListView` would work for a basic panel, but M3 to M4 need drag reordering and nested groups, where RecyclerView is much better.
3. **Undo buttons now.** The in-memory undo and redo is needed anyway because every edit is a command. Recommendation: show undo and redo buttons in M2. They are cheap, and M3 then only makes history persistent.
