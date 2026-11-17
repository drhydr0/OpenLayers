# Milestone 3 phone test

One pass on the Pixel closes Reliable editing. It takes about 10 minutes. Install the newest `layers-dev.apk` from the `dev` release over the current one; no uninstall is needed.

If anything fails, send a screenshot or the diagnostics report (Projects → Diagnostics → Share). If the app crashes, reopen it and share the crash report it offers.

## Existing projects

1. Open a project you made before this update. It opens as you left it. (Its undo history starts empty; that is expected.)

## Saved indicator and undo after restart

2. In a project, add a photo and some text, and move each. Under the project name, "Saving…" appears briefly, then "Saved".
3. Swipe the app away from recents. Reopen it and open the project.
4. Tap **Undo** several times. Each tap reverses one of the edits from before you closed the app. Tap **Redo** to bring them back.
5. Undo twice, swipe the app away, and reopen the project. **Redo** still works.

## Lock and hide

6. Tap **Layers**. Tap the eye on the text row: the text disappears from the canvas and its row is dimmed. Tap the eye again to show it.
7. Tap the lock on the photo row. On the canvas, try to drag the photo: it doesn't move, and a tap on it selects whatever is underneath (or nothing).
8. Tap the photo's row in the list: it is outlined on the canvas without handles. Unlock it.
9. Tap **Undo**: it steps back through the lock and hide changes one at a time.

## Multiselect

10. Add a second photo. Tap **Multi**, then tap both photos. One box surrounds both and the bar shows "2 layers selected".
11. Drag inside the box: both move together. Pinch and twist with two fingers: both scale and turn as one, keeping their arrangement.
12. Drag a corner handle of the box, then the round handle above it: both resize, then rotate, around the box's center.
13. Tap **Undo** once after each of those gestures: both photos go back together.
14. With both selected, tap **Delete**. Both disappear. One **Undo** brings both back, in their original stacking order.
15. Tap **Multi** again to turn it off. Tapping a layer selects just that one.

## History list

16. Tap **⋮** (top right) → **History**. Actions are listed newest first with readable names such as "Move 2 layers" and "Delete 2 layers", and the current state is marked.
17. Tap an action a few rows down. The canvas jumps to the state right after that action. Open History again and tap the top row to come back.
18. **⋮** → **Fit canvas to screen** resets the view after zooming (this button moved into the menu).

## Storage

19. Add a photo, tap **Undo**, then add some text (this makes the photo impossible to bring back). Go back to the project list and reopen the project. Everything still looks right, and Undo still works for the remaining steps.
20. Open **Diagnostics**: "Last open project" shows the layer count and history entries.
