# Milestone 6 phone test

One pass on the Pixel closes Polished workflow. It takes about 12 minutes. Install the newest `layers-dev.apk` from the `dev` release over the current one; no uninstall is needed.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share).

## Add menu and shapes

1. In a project, tap **Add**. The menu offers Image, Text, Shape, and Sticker from collection. Add an image and some text as before.
2. **Add → Shape → Rounded rectangle**. A blue rounded square appears in the middle of the screen, selected. Move, resize, and rotate it like any layer.
3. With it selected, tap **Edit shape**. Change the fill color, pick an outline color, raise the outline thickness, and change width, height, and corner rounding. Tap **Done**: the shape updates. One **Undo** reverses the whole edit.
4. Add an **Ellipse**, a **Line**, and an **Arrow**. Edit the arrow: change its color, thickness, and length.
5. In **Edit shape** set Fill to **None** (the first, empty swatch) on the ellipse: only its outline shows.

## Align, space evenly, snap

6. Select one shape and tap **Actions → Align → Center (left to right)**. It moves to the horizontal center of the canvas.
7. Tap **Multi**, select three layers, then **Actions → Align → Top**. Their top edges line up. One **Undo** puts all three back.
8. With the three selected, **Actions → Space evenly → Across**. The gaps between them become equal; the outer two don't move.
9. Turn **Multi** off. Drag a layer slowly toward the middle of the canvas: a pink guide line appears and the layer clicks into place. Drag it near another layer's edge: it snaps there too.
10. **⋮ → Snap while dragging** to turn snapping off. Drag again: no guides, no snapping. Turn it back on.

## Text styling

11. Select the text and tap **Edit text**. Choose the **Handwriting** font, tick **Bold**, raise **Line spacing** and **Letter spacing**, pick an **Outline** color, pick a **Background box** color, and tick **Shadow**. Tap **Done**. The text shows all of it.
12. Resize the text with a corner handle: the outline, box, and shadow scale with it.

## Image adjustments

13. Select the image and tap **Adjust image**. Raise brightness, lower saturation to -100 (black and white), tick **Flip left to right**, and tap **Done**.
14. **Adjust image** again and set **Crop → Left** to about 30%. Tap **Done**. The left part is gone and the rest of the picture has not moved.
15. **Adjust image → Reset**. The original picture is back, uncropped and unflipped, in the same place.

## Opacity and exact placement

16. Select a layer, **Actions → Opacity…**, set about 50%, **Done**. It becomes see-through.
17. **Actions → Position and size…**. Type a size of 50 and a rotation of 45, tap **→** a few times, and tap **Done**. The layer is half size, turned 45 degrees, and a few pixels to the right.
18. Leave the size empty and tap **Done**: the sheet stays open with a message.

## Project copies

19. **⋮ → Save a checkpoint copy…**, name it, **Save**. Make a few more edits. Go back to the project list: the checkpoint is there as its own project. Open it: it has the state from when you saved it, and Undo works.
20. On the project list, tap a project's **⋮ → Duplicate**. A "… copy" appears. Edit the copy: the original is unchanged.

## Everything together

21. Export a PNG of the project with shapes, styled text, and the adjusted image. It matches the canvas.
22. Swipe the app away and reopen the project: everything is as you left it.
