# Milestone 4 plan: Reusable compositions

Goal from CLAUDE.md: grouping, independent duplicates, editable stickers, sticker collection, nested editing.

Milestone 3 (Reliable editing) closed on 2026-11-17 after the developer's phone test.

## Scope

In scope:

- Groups: several layers become one object that moves, scales, rotates, hides, locks, and fades together, and can be ungrouped without anything shifting.
- Nested editing: double-tap a group (or pick a child in the layers panel) to edit inside it, with a bar naming the group and a Done button.
- Duplicate: independent copies of the selection, slightly offset, in the same arrangement and stacking order.
- Editable stickers: "Make sticker" turns the selection into a new independent group, leaving the originals in place.
- Sticker collection: save a sticker with a name and thumbnail, and insert it into any project as a new independent copy.
- The layers panel shows groups as expandable rows.

Deferred, with reasons:

- Image (raster) stickers: they need selection capture, which is milestone 5.
- Dragging a row into or out of a group in the layers panel: Group and Ungroup cover membership for now; drag-to-regroup is polish for milestone 6.
- Named checkpoints and project duplication (carried over from M3): planned for milestone 6 with the rest of the project-screen polish.

## Architecture decisions

- **Layer tree.** `GroupLayer` holds child layers in its own coordinates. `Document` finds, inserts, removes, replaces, and reorders layers anywhere in the tree, rebuilding only the path to the change.
- **Commands.** Placements carry the parent group's ID. One new command, `ReplaceLayers`, swaps a set of layers for another in one step and covers group and ungroup. Duplicate is an `AddLayers`.
- **Ungroup is exact** while a group is scaled evenly and not flipped, which is all the gestures produce: each child takes on the group's move, scale, rotation, opacity, and visibility.
- **Editing scope.** The touch controller works on the layers directly inside one group (or the top level). Finger positions are converted into that group's coordinates, so the same gesture math serves every depth.
- **Format version 3** adds the group layer, parent IDs, and the new command; versions 1 and 2 migrate on open.
- **Sticker collection.** Stored outside projects in `stickers/<id>/` with its own versioned file, image copies, and thumbnail. Inserting copies the images into the project and gives every layer a new ID, so later changes to a saved sticker never touch existing uses.

## Steps

All 6 steps are built.

| # | Step | PR | Verified by |
| --- | --- | --- | --- |
| 1 | Close Reliable editing, add this plan | #21 | Review |
| 2 | Layer tree, group/ungroup/duplicate commands, format v3 | #21 | JVM tests |
| 3 | Draw, hit-test, and export groups; layers panel with expandable groups | #21 | JVM tests, phone test |
| 4 | Group, Ungroup, Duplicate actions; editing inside a group | #21 | JVM tests, phone test |
| 5 | Make sticker; sticker collection: save, list, insert, rename, delete | final PR | JVM tests, phone test |
| 6 | Docs and the combined phone test | final PR | Phone test |

Notes:

- New images, text, and inserted stickers always go on the top level; the editor steps out of any group first.
- To change a saved sticker, save the edited version as a new sticker and delete the old one. Stickers already placed are never affected.
- Rendering and gestures still have no automated tests (emulator tests are planned for M7), so `milestone_4_phone_test.md` covers them.
