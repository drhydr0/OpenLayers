# Milestone 3 plan: Reliable editing

Goal from CLAUDE.md: persistent undo and redo, atomic multilayer operations, autosave recovery, locks, visibility.

Milestone 2 (Editable canvas) closed on 2026-11-17 after the developer's phone test.

## Scope

In scope:

- Undo and redo survive closing and reopening the app.
- Each committed edit saves the document and its history record together. After an interruption, the app reopens to the last committed edit with matching undo history.
- A "Saved" indicator that appears only once the edit is durable, and a clear message when saving fails.
- Lock and visibility for layers, as undoable edits, with toggles in the layers panel. Locked layers can't be moved by touch but can be selected in the panel.
- Multiselect: move, scale, rotate, and delete several layers as one undo step, with one bounding box.
- A history sheet listing actions ("Move 3 layers", "Change text") with jump to any retained state.
- Images are kept while the document or any history entry needs them, and removed once nothing does.

Deferred, with reasons:

- Named checkpoints and "open as project copy": they build on project duplication, which arrives with duplicates in M4.
- Disk budget, compaction, and trim UI: M7 measures real history sizes first. Until then history keeps the newest 500 steps per project.

## Architecture decisions

- **Commands become data.** Each command gets a serializable form in `persistence.format` (layer snapshots and before/after values, as today). Commands stay the only way to change a document.
- **One commit file.** The manifest gains the history: undo and redo stacks stored next to the document in one atomically replaced file (format version 2, with a migration from version 1 that starts with empty history). One file means the document and its history can never disagree after a crash. If M7 shows large histories make saves slow, the stacks move to an append-only journal behind the same interface.
- **History cap.** The oldest steps beyond 500 are dropped at save time. The current document is never affected.
- **Asset cleanup.** An image file is deleted only when no layer and no retained history step references it, and never during a save.
- **Selection is a set.** The touch controller and overlay work on a set of layer IDs. Group transforms apply around the shared bounding-box center through the existing `TransformLayers` command.

## Steps

| # | Step | Verified by |
| --- | --- | --- |
| 1 | Close Editable canvas, add this plan | Review |
| 2 | Serializable commands and manifest format v2 with history, v1 migration | JVM tests |
| 3 | Save and load history with the document; history cap; interrupted-write tests | JVM tests |
| 4 | Editor restores history on open; "Saved" indicator and save-failure message | JVM tests, phone test |
| 5 | Lock and visibility commands, panel toggles, touch rules | JVM tests, phone test |
| 6 | Multiselect with shared transforms and multi-delete | JVM tests, phone test |
| 7 | History sheet with jump to state | JVM tests, phone test |
| 8 | Asset cleanup that respects history | JVM tests |
| 9 | Docs and the combined phone test | Phone test |

As in M2, steps that need no phone check are merged once CI passes, and the phone checks are collected into one test at the end.
