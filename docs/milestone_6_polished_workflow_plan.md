# Milestone 6 plan: Polished workflow

Closed on 2026-11-18 after the developer's phone test and feedback round 1 (`feedback_round_1.md`).

Goal from CLAUDE.md: shapes, text styling, image adjustments, snapping, alignment, distribution, accessibility.

Built on 2026-11-17, at the developer's request for speed, before the milestone 5 phone test was run. Both tests are still open.

## What was built

| Area | What it does | PR |
| --- | --- | --- |
| Shapes | Rectangle, rounded rectangle, ellipse, line, arrow as editable layers; **Edit shape** for fill, outline, thickness, size, corner rounding | #25 |
| Add menu | One **Add** button: Image, Text, Shape, Sticker from collection | #25 |
| Alignment | **Actions → Align**: left, center, right, top, middle, bottom. One layer aligns to the canvas, several to each other | #25 |
| Even spacing | **Actions → Space evenly** across or down, for three or more layers | #25 |
| Snapping | A one-finger drag snaps to the canvas and other layers with guide lines; **⋮ → Snap while dragging** turns it off | #25 |
| Text styling | Five built-in fonts, bold, italic, line and letter spacing, outline, background box, shadow | final PR |
| Image adjustments | **Adjust image**: brightness, contrast, saturation, flip, crop, reset | final PR |
| Opacity | **Actions → Opacity** for any single layer | final PR |
| Exact placement | **Actions → Position and size**: typed center, size percent, angle; one-pixel nudge buttons | final PR |
| Project copies | **Duplicate** on the project list; **⋮ → Save a checkpoint copy** in the editor | final PR |

## Architecture decisions

- **Everything stays editable.** Shapes, text effects, adjustments, and crop are stored as values and applied by the shared renderer, so the editor, export, captures, and thumbnails agree. Image bytes are never changed.
- **Built-in fonts only.** Text uses the font families every Android phone has, so a project never depends on font files. Bundled or imported fonts remain a later extension.
- **Effects scale with the text.** Outline thickness, background padding, and shadow are fractions of the font size.
- **Crop keeps the picture still.** A layer's position is the center of what it shows, so cropping moves the layer by the same amount the visible center moved.
- **Arrangement math is plain Kotlin** (`Arrange`, `PrecisionInput`, `ImageEdits`): alignment, even spacing, snapping, typed values, and crop are JVM-tested.
- **Format versions 4 and 5** add shapes, then text styling and image settings. Versions 1 to 4 migrate on open.
- **A checkpoint is a project copy.** It has its own images and undo history, which is what "open a checkpoint as a project copy" needs, without a second storage format.

## Accessibility

Every control has a spoken description, touch targets are at least 48 dp, sheets scroll and follow the system text size, and the status bars announce changes. The layers panel gives a non-gesture route to select, reorder (Move up and down), hide, lock, rename, and delete; the Actions menu gives typed placement, alignment, and opacity. Drawing an area and dragging handles still need touch; there is no spoken alternative for those yet.

## Deferred

- Snapping inside a group (top level only for now).
- Dragging a row into or out of a group in the layers panel.
- Custom color entry, an eyedropper, recent colors, favorite fonts, and copy and paste style.
- Image masks.

These go to the next planning pass with the developer rather than being dropped.
