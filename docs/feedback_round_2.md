# Feedback round 2 (after the first release)

Changes the developer asked for on 2026-11-20.

| Request | What changed |
| --- | --- |
| Two-finger navigation instead of a pan button | Two fingers pan and zoom the view, unless the first finger landed on the current selection: then they move, pinch, and turn it as before. The hand button is gone. Fit to screen stays in the **⋮** menu. While drawing a selection area, two fingers always move the view. |
| New text keeps the last text's settings | The style chosen for a text (font, size, colors, outline, background, shadow, spacing, alignment, opacity) is remembered when it is added or edited, and new text starts with it. It is kept in the app's settings, so it survives restarts and works across projects. |
| Select several overlapped the layers list | The button sits in its own strip; the list starts below it. |
| Remove the top bar | The system title bar ("Layers") is gone on every screen. The diagnostics screen shows its own heading instead. |
| History that includes undos | The history list now records every change in order: each edit, each **Undo …**, each **Redo …**, and each jump (**Go back to: …**). Tapping any entry returns to the state right after it; that jump is recorded too and can itself be undone, so no state is ever lost, even one a later edit replaced. The Undo and Redo buttons still step back and forward as usual. The list keeps the newest 1,000 entries. |

## How the full history works

Undo and redo keep their usual stacks. Alongside them, a journal stores every change with the command that made it (an undo stores the reverse of what it undid). Any earlier state is reached by reversing the journal entries after it, which is what **Go back to** does, as one new, undoable step. Images that any journal entry can bring back are kept on disk. Projects from before this change start with an empty journal (save format version 6).
