# First release summary

Completed 2026-11-18. Every milestone in CLAUDE.md and every acceptance scenario in the design doc passed on the Pixel 9 Pro.

## What the app does

- **Projects:** create from preset or custom sizes, or from a photo; rename, duplicate, delete; thumbnails; checkpoint copies.
- **Layers:** images, text, shapes, groups, and stickers in a layer tree with visibility, locks, opacity, reordering, and moving into and out of groups.
- **Editing:** touch move, pinch, rotate, free-resize handles, snapping, alignment, even spacing, nudging, typed position and size, multiselect.
- **Styling:** 19 fonts, text outline, background, shadow, spacing; shape fill and outline; image brightness, contrast, saturation, flip, crop. Every sheet previews live.
- **Reuse:** duplicates, editable stickers, a sticker collection shared across projects, image stickers from rectangle or lasso areas.
- **Reliability:** every edit saved with its undo history in one crash-safe file; undo and redo survive restarts; history list with jump.
- **Output:** PNG (with transparency) and JPEG export at 50 to 200 percent, to the gallery or the share sheet.
- **Self-reporting:** crash reports, diagnostics screen, commit hash on screen.

## Measured on the Pixel

300 layers and 1,000 undo steps work smoothly; a 16384 px export takes about 15 s. Details: `milestone_7_capacity_verification_plan.md`.

## Known limits and ideas for later

Listed in the milestone plans: snapping inside groups, custom colors and an eyedropper, copy and paste style, image masks, imported fonts, a history disk budget, and tiled export beyond the phone's free memory.
