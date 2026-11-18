# Milestone 5 plan: Selection tools

Closed on 2026-11-18 after the developer's phone test.

Goal from CLAUDE.md: rectangle and lasso with scope, canvas or layer capture, transparent image stickers.

Milestone 4 (Reusable compositions) closed on 2026-11-17 after the developer's phone test.

## Scope

- **Select area** (in the **⋮** menu): choose a rectangle or a freehand lasso, and what to copy from: everything visible, the one selected layer, or the several selected layers. Canvas captures can include or leave out the canvas background.
- While selecting, a bar states the shape and what is being copied. Drag inside the area to move it; drag outside to draw a new one. Pan and zoom still work through the hand button.
- **Copy as sticker** shows a preview and says plainly that the result is a flattened picture. Creating it adds a transparent image layer exactly over what it copied, selected, as one undo step ("Create sticker").
- The picture is made at document resolution, reduced only if it would not fit in the phone's memory.
- An area that catches nothing gives a message and creates no layer and no undo step.
- An image sticker is an ordinary image layer, so it can be moved, grouped, duplicated, and saved to the sticker collection like any other.

## Architecture decisions

- **Shapes and planning are plain Kotlin** (`SelectionShape`, `CapturePlanner`): the area's bounds and emptiness, point-in-lasso, which layers a scope keeps (including layers inside groups), the whole-pixel region, the memory limit, and where the result is placed.
- **One rendering path.** `Capturer` draws through the same `DocumentRenderer` as the editor and export. For layer scopes it draws a copy of the document that keeps only the chosen layers and the groups around them, so they stay in place. Hidden layers stay hidden.
- **Smooth lasso edges.** The area is cut out by erasing with an anti-aliased path instead of a hard clip.
- **Canvas captures stop at the canvas edges**, like export. Layer captures may reach beyond them.
- **New asset, never an edit.** The picture is stored as a new PNG asset under its content hash.

## Steps

All steps are built in one PR.

| # | Step | Verified by |
| --- | --- | --- |
| 1 | Close Reusable compositions, add this plan | Review |
| 2 | Selection shapes and capture planning | JVM tests |
| 3 | Capture rendering, area selection tool, scope sheet, preview, copy as sticker | Phone test |
| 4 | Docs and the phone test | Phone test |

Capture pixels have no automated test yet (emulator tests are planned for M7), so `milestone_5_phone_test.md` covers them.
