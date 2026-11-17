# Milestone 4 phone test

One pass on the Pixel closes Reusable compositions. It takes about 10 minutes. Install the newest `layers-dev.apk` from the `dev` release over the current one; no uninstall is needed.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share). If the app crashes, reopen it and share the crash report it offers.

## Existing projects

1. Open a project you made before this update. It opens as you left it, and Undo still works on its earlier steps.

## Group and ungroup

2. In a project with a photo and some text, tap **Multi**, tap both, then **Actions → Group**. One box now surrounds both, and the bar shows "1 layer selected".
3. Turn **Multi** off. Drag, pinch, and rotate the group: photo and text move as one.
4. Tap **Layers**. The group is one row ("Group · 2 layers"). Tap its folder icon: the two layers appear indented below it. Tap the eye on the group row: both disappear from the canvas. Show it again.
5. Select the group and tap **Actions → Ungroup**. Nothing moves on the canvas, and the photo and text are separate layers again, in the same stacking order.
6. Tap **Undo** twice: first the group comes back, then it is gone and the layers are as they were before grouping.

## Editing inside a group

7. Group the photo and text again. Double-tap the group. A bar reads "Editing inside: Group" and a dashed frame marks it.
8. Drag only the text. It moves on its own; the photo stays. Double-tap the text and change its words.
9. Tap **Done**. The group is selected again as one object, and moving it carries the edited text along.
10. Rotate and enlarge the group, double-tap it, and drag the text again. It follows your finger exactly, even though the group is rotated.

## Duplicate

11. Tap **Done**, select the group, and tap **Actions → Duplicate**. A copy appears slightly offset and is selected.
12. Double-tap the copy and change its text. The original group's text is unchanged.
13. Tap **Undo** until the copy is gone; the original is untouched.

## Stickers

14. Select the group and tap **Actions → Make sticker**. A new object named "Sticker" appears slightly offset; the original stays where it was.
15. Select the group and tap **Actions → Save to sticker collection**. Name it and tap **Save**. "Saved to your sticker collection" appears.
16. Go back to the project list and open (or create) a different project. Tap **Sticker**. The saved sticker is listed with its name and thumbnail. Tap it: it appears in the middle of the canvas, selected.
17. Double-tap it and change its text. Tap **Done**. Tap **Sticker** and insert it again: the second one has the original text.
18. Go back to the first project: its group still has its own text.
19. Tap **Sticker**, then the **⋮** on the sticker's row: rename it, then delete it. The stickers already placed in projects stay.

## Save and reopen

20. Swipe the app away, reopen it, and open the project with the groups. Groups, their contents, and Undo are all as you left them. Export a PNG: it matches the canvas.
