# Guided tour

The app has a 13-step tour that runs in its own practice project named "Tutorial". The app offers the tour once, the first time the projects screen opens. You can start it again at any time from **Tutorial** on the projects screen.

A card above the canvas shows "Step n of 13", the instruction, and **Back**, **Next** or **Skip**, and **End tour**. When you do what a step asks, the card says "Done" and moves to the next step. If you leave the editor or close the app, the tour picks up at the same step the next time you open that project.

| # | Step | Moves on when |
| --- | --- | --- |
| 1 | Screen tour: top and bottom bars, autosave | You tap Next |
| 2 | Add an image | An image is added |
| 3 | Move, pinch, rotate, stretch | A layer is transformed by touch |
| 4 | Two-finger pan and zoom, fit to screen | You pan or zoom, or use Fit |
| 5 | Add and style text | A text is added |
| 6 | Add a shape | A shape is added |
| 7 | Layers panel: reorder, hide, lock, rename | One of those is done |
| 8 | Select several, group, align, space evenly | A group is made |
| 9 | Position and size, nudge | Exact values are applied or a nudge is committed |
| 10 | Image adjustments and crop | An image edit is applied |
| 11 | Stickers and area select | A sticker is made, saved, inserted, or copied from an area |
| 12 | Undo, redo, history | History is opened |
| 13 | Export and share | An export finishes |

The step logic is in `ui/Tutorial.kt`, which has JVM tests. The card is `ui/TutorialCard.kt`. Progress is saved in `ui/TutorialProgress.kt`.
