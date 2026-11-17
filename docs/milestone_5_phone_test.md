# Milestone 5 phone test

One pass on the Pixel closes Selection tools. It takes about 8 minutes. Install the newest `layers-dev.apk` from the `dev` release over the current one; no uninstall is needed.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share).

Use a project with a white background, a photo, and some text that overlaps the photo.

## Rectangle, everything visible

1. Tap **⋮ → Select area…**. Keep **Rectangle** and **Everything visible**, and tap **Start**. A bar appears saying "Drag to draw a rectangle".
2. Drag a rectangle across part of the photo and part of the text. A dashed box appears, and the bar names the shape and what is copied.
3. Drag inside the box: it moves. Drag outside it: a new box replaces it.
4. Tap **Copy as sticker**. A preview shows that piece of the picture, with a note that it is a flattened picture and its size in pixels.
5. Tap **Create**. Nothing seems to change at first: the new image sits exactly over what it copied, and it is selected. Drag it away: it is a copy of that area, including the white background, and the original photo and text are untouched.
6. Tap **Undo** once: the sticker is gone. **⋮ → History** lists it as "Create sticker".

## Without the background

7. Select an area again, but untick **Include the canvas background**. Copy and create, then drag the result over the photo: where there was only background, it is see-through.

## Lasso

8. **⋮ → Select area…**, choose **Lasso**, and **Start**. Draw a loop around part of the photo. Copy and create. Drag it away: the picture follows your loop with a smooth edge, and everything outside the loop is see-through.

## One layer, several layers

9. Tap the photo to select it, then **⋮ → Select area…** and choose **The selected layer only**. Draw a rectangle over the spot where the text overlaps the photo. Copy and create, then drag it away: it shows the photo without the text.
10. Tap **Multi**, select the photo and the text, then **⋮ → Select area…** → **The selected layers only**. Copy an area: it has both, with the text on top, and no background.

## Nothing to copy

11. Select the text only, choose **The selected layer only**, and draw a rectangle far from the text. **Copy as sticker** says there is nothing to copy. No layer is added, and Undo has no new step.
12. Start an area selection and tap **Cancel**: the bar goes away and normal editing is back.

## Zoomed in, and reuse

13. Zoom in with the hand button, turn the hand off, and copy a small area. The sticker is sharp: it has the picture's own pixels, not the screen's.
14. Select an image sticker and use **Actions → Save to sticker collection**. Insert it into another project from **Add → Sticker from collection**.
15. Swipe the app away and reopen the project: the image stickers are still there, and Undo still works.
