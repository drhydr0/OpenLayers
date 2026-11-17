# Milestone 2 phone test

One pass on the Pixel closes Editable canvas. It takes about 10 minutes. Install the newest `layers-dev.apk` from the `dev` release over the current one; no uninstall is needed.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share). If the app crashes, reopen it and share the crash report it offers.

## Projects

1. Tap **New project**, pick **Portrait**, background **White**, and tap **Create**. The editor opens with a white canvas fitted to the screen.
2. Go back. The project is in the list with "Edited just now".
3. From the row menu (⋮), **Rename** it, then make a second project with **Transparent** background. Its canvas shows a checkerboard.
4. Tap **Open image** and pick a photo taken in portrait orientation. A new project opens with the canvas the photo's shape and the photo upright, filling it.

## Images and text

5. In the editor tap **Image** and add a photo. It appears centered, smaller than the canvas, and selected (blue outline with handles).
6. Tap **Text**, type two lines, pick a color, size, and alignment, and tap **Done**. The text appears centered and selected.
7. Double-tap the text. The sheet opens with its words and style; change the words and tap **Done**.

## Touch

8. Drag the photo. It follows your finger.
9. With two fingers on the photo, pinch and twist. It scales and rotates around your fingers and stays under them.
10. Drag a corner handle: the photo resizes, keeping its shape. Drag the round handle above it: it rotates, snapping when it nears straight.
11. Select the text and drag its side handle (the middle of the right edge): the text rewraps.
12. Move the photo partly off the canvas. The part outside is dimmed.
13. Tap empty canvas: the selection clears. Tap the photo where the text overlaps it: the text (on top) is selected.

## Undo, layers, navigation

14. Tap **Undo** repeatedly. Each tap reverses exactly one gesture or edit, back to an empty canvas. **Redo** brings them back.
15. Tap **Layers**. The text is above the photo. Drag the photo's handle (≡) to the top; the photo now covers the text. Rename and delete a layer from the row menu, then undo the delete.
16. Tap the hand button and pinch to zoom and drag to pan; layers don't move. Tap the hand again, then the fit button to reset the view.

## Save, reopen, export

17. Swipe the app away from recents, reopen it, and open the project. Everything is as you left it, and the project list shows a thumbnail.
18. Tap **Export**, choose **PNG** at 100%, and tap **Save**. "Saved to Pictures/Layers" appears; the image is in Google Photos and matches the canvas (text wrapping, positions, nothing outside the canvas).
19. Export again as **JPEG** and tap **Share**; the share sheet opens with the image.
20. In the transparent project, export a PNG with **Transparent background** checked; the image has no background.
21. Open **Diagnostics**: "Last open project" shows a layer count and history entries.
