# Release acceptance test

The design doc's eight acceptance scenarios define "done" for the first release. This turns them into steps on the Pixel. About 20 minutes. Use the newest `layers-dev.apk`.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share).

## 1. Build a composition and undo everything

1. New project. **Add** a photo, two texts, and a rectangle and an ellipse.
2. Reorder layers in the layers panel, **Duplicate** one, hide one, lock one, and delete one. Then select three layers and do the same to them together: move, duplicate, hide, delete.
3. Tap **Undo** step by step back to the empty canvas, checking each step reverses exactly one action (several layers at once count as one). Then **Redo** all the way forward: everything comes back exactly.

## 2. Editable sticker with independent copies

4. Select a photo and a text, **Actions → Make sticker**.
5. **Actions → Duplicate** the sticker. Double-tap the copy, change its text, and move it.
6. The original sticker keeps its own text and position; the copy keeps its changes.

## 3. Area capture

7. Overlap a photo, a text, and a shape. **Select → Rectangle → Everything visible**, draw across all three, **Copy as sticker → Create**. Drag it away: the stacking (text on top) and the cut edges match what was there, and the surroundings are see-through.
8. Repeat with **Lasso** and **The selected layers only** (select two of the three first). Only those two appear, in order, inside the loop.
9. Zoom in and check the captured sticker is as sharp as the original.

## 4. A long gesture undoes as one step

10. Drag, pinch, and twist a photo in one long gesture without lifting your fingers. One **Undo** puts it back exactly: same position, size, and angle.
11. Repeat with three layers selected together, and with a layer inside a group (double-tap the group first).

## 5. Saved sticker in another project

12. Select a composition and **Actions → Save to sticker collection**.
13. Swipe the app away, reopen it, open a different project, and **Add → Sticker from collection**. Edit its text.
14. Go back to where the first copy was used: its text is unchanged. Insert the sticker again: it has the saved text.

## 6. Interrupted save

15. Make an edit, and while "Saving…" shows (or right after), swipe the app away.
16. Reopen the project. It opens with either the edit or the state just before it, never something broken. **Undo** works from there, and every photo is present.
17. Repeat once more with a large edit (for example, delete many layers at once).

## 7. Exports match the editor

18. Export a transparent-background PNG and a JPEG at 100%.
19. Open both in Photos next to the editor. Compare placement, text wrapping, crop edges, colors, and that nothing outside the canvas edges appears. The PNG is see-through where the canvas was transparent; the JPEG is white there.

## 8. Large document on the phone

20. Run `docs/milestone_7_phone_test.md` (the 300-layer test project) and send the diagnostics report. This records the measured capacity before the release claims any.
