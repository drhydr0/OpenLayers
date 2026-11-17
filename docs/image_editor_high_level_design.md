# Mobile Image Editor High Level Design

## Product purpose

Build a general-purpose Android image editor centered on editable layers, expressive text, reusable stickers, and direct touch manipulation. A user should be able to combine words, photographs, shapes, and existing artwork into a composition, rearrange everything freely, and confidently undo changes.

The interface should feel approachable on a phone. Common actions should take a tap or gesture, while precise controls remain available when needed. The same tools should support graphics such as seasonal banners, collages, invitations, memes, labels, and tournament brackets. These examples use the general editor and do not define its structure.

This document proposes the product behavior, high-level architecture, delivery sequence, and acceptance criteria. Implementation choices and performance targets require validation during development.

## Operating constraints

| Area | Design requirement |
| --- | --- |
| Development computer | Arch Linux laptop with 8 GB total RAM |
| Development workflow | Codex working with a local project and command-line build tools |
| Build and installation | Standalone Android SDK, pinned Gradle wrapper, compatible JDK, and APK installation over ADB |
| Primary test device | One physical Pixel 9 Pro |
| Application | Native Android app with local project storage and offline core editing |
| Layer capacity | No arbitrary small layer-count cap. Practical capacity depends on content, device memory, and storage |
| Editing model | Preserve editable objects and original image assets throughout ordinary editing |

## Core product principles

- Keep the canvas central. Show controls relevant to the current selection.
- Make text, images, and composite stickers behave consistently when moved, scaled, rotated, duplicated, and arranged.
- Preserve the user's ability to change individual components later.
- Make each completed action reversible, including operations on multiple layers.
- Provide visible buttons and precise controls alongside gestures.
- Save work automatically and recover the latest committed edits after interruption.
- Keep layer capacity and history practical through efficient storage and rendering.

## Workspace and navigation

The project screen shows recent projects with thumbnails, names, and last-edited times. Users can create, duplicate, rename, delete, import, and export projects. Creating a canvas offers common aspect ratios, custom pixel dimensions, background color, and transparency. Opening an image can create a canvas matching its dimensions.

The editor has a large central canvas, a compact top bar for project navigation, undo, redo, and export, and a bottom toolbar for adding content, selection, and layers. Contextual controls appear when an object is selected. Font browsing, color selection, and detailed settings use expandable bottom sheets with a usable portion of the canvas still visible.

The layers panel shows thumbnails, names, object types, visibility, locks, and nested groups. Search, collapse, and multiselect make large documents manageable. Users can select objects through the panel even when those objects are covered or off the visible canvas.

A visible canvas navigation control switches to pan and zoom mode. Fit to screen and reset view are always available. Zooming the view preserves the composition's object positions and sizes.

## Document and layer model

Each project has one finite canvas, an ordered tree of layers, project-owned assets, reusable style references, and persistent edit history. The viewport can pan beyond the canvas so users can reposition objects that extend outside its bounds. Exports are clipped to the canvas.

| Object | Editable properties and behavior |
| --- | --- |
| Text | Content, font, size, weight where available, color, alignment, line spacing, letter spacing, outline, background, and shadow |
| Image | Source asset, position, scale, rotation, opacity, crop, flip, and mask |
| Shape | Rectangle, rounded rectangle, ellipse, line, and arrow with fill, stroke, and dimensions |
| Group | Ordered child layers with a shared transform, opacity, visibility, and lock state |
| Sticker | A reusable image or editable composition inserted as an independent object or group |
| Background | Canvas color or an image layer that can be locked and arranged like other image content |

Every layer has a stable identity, name, transform, visibility, lock state, and position in its parent's stacking order. Group transforms compose with child transforms. Text and shapes retain their editable definitions. Crops and masks retain the source image.

Users can add, rename, hide, lock, delete, duplicate, reorder, group, and ungroup layers. Multiselection supports moving, scaling, rotating, copying, duplicating, deleting, aligning, and grouping several layers in one operation. Duplicating several layers preserves their relative positions and stacking order.

## Touch interaction

| Input | Expected result |
| --- | --- |
| Tap an object | Select the topmost visible, unlocked object under the finger |
| Drag the selected object | Move it with immediate visual feedback |
| Pinch and twist on the selected object | Scale and rotate it around a stable gesture pivot |
| Drag a corner handle | Scale with the aspect ratio preserved by default |
| Drag a rotation handle | Rotate with an optional angle snap |
| Double tap text | Open its text editor and keyboard |
| Double tap a group | Enter the group to select and edit its children |
| Tap empty canvas | Clear the object selection |
| Long press overlapping content | Open a compact chooser of objects at that location |
| Enable multiselect and tap objects | Add or remove objects from the selection |
| Drag a layer row by its handle | Change its stacking order or group membership |
| Use canvas navigation mode | Pan, pinch to zoom, and return to fit view |

Gesture ownership is established when a gesture starts. An object gesture continues to manipulate that object until all fingers lift. Canvas navigation mode explicitly routes gestures to the viewport. This avoids unexpected changes when a second finger lands or the selected object moves beneath the fingers.

Multiselections display one bounding box and transform around a shared pivot. Locked objects are excluded from direct manipulation and remain selectable for inspection through the layers panel. Group editing shows a breadcrumb and a clear exit action.

Alignment guides appear near canvas edges, centers, and nearby objects. Snapping can be disabled. A precision sheet offers position, dimensions, rotation, opacity, nudging, alignment, and distribution. Controls use large touch targets, readable labels, high contrast, screen-reader descriptions, and support for system text scaling.

## Text and visual styling

Text is a first-class editable object. Users can choose from a curated set of bundled fonts, see previews, save favorite fonts, and reuse recent colors and text styles. Font files required by a project are bundled or retained with the project when permitted. Font fallback is visible if an imported project lacks a required font.

Color controls include a palette, recent colors, custom color entry, transparency, and a canvas eyedropper. Common text treatments include fill, outline, shadow, alignment, and a rounded background. Copy style and paste style let users apply a consistent appearance across objects while preserving their content.

Text-box width controls wrapping. Scaling a text object changes its overall size while preserving its layout. A separate width handle changes wrapping. A user can replace a name, date, or phrase in a duplicate while keeping the chosen styling.

Basic image controls include crop, rotate, flip, opacity, brightness, contrast, and saturation. Adjustments are stored as editable parameters. Image import handles orientation and transparency consistently.

## Stickers and reusable compositions

The user can create a sticker from one object, several objects, or a selected area. The creation sheet offers an editable composition or an image sticker, with a preview of the result. Editable composition is the default for whole-object selections.

| Action | Result |
| --- | --- |
| Group selection | Existing layers become a group that can move together and be reopened for editing |
| Duplicate selection | Independent copies appear slightly offset, with their relative arrangement preserved |
| Make editable sticker | A new independent group copies the selected objects, their styles, and their arrangement |
| Make image sticker | A transparent raster image captures the selected content at a stated resolution |
| Save to sticker collection | A reusable asset is available across projects, with a name and thumbnail |
| Insert saved sticker | A new independently editable instance is added to the active project |

Creating a sticker copies the content and keeps its source components in place. Changes to a sticker instance apply only to that instance. Updating a saved sticker affects future insertions. Existing instances retain their saved content.

Editable stickers preserve nested text, images, shapes, crops, masks, and transformations. Entering a sticker group exposes its children for editing. Image stickers have transparent surroundings where appropriate and behave like other image layers.

Immutable image bytes may be shared internally between duplicates to save space. Layer identities, text values, transforms, styles, and masks are independently editable. A later asset-changing operation creates a new asset version.

## Area selection and copying

Selection supports rectangle and freehand lasso. Before drawing, the user chooses the scope from the whole visible canvas, the active layer, or selected layers. A highlighted scope indicator remains visible during selection.

- Canvas scope captures the visible composite within the selected region, including the background when enabled.
- Active-layer scope captures only that layer's visible pixels within the region.
- Selected-layer scope captures the selected layers in stacking order while excluding unrelated layers.

The selection can be adjusted before committing. Copy as sticker creates an independent transparent image bounded by the selection. Pixels outside the selection are transparent. The result is inserted at the same canvas position and selected immediately. The copy retains its source pixels at document resolution, subject to the displayed output dimensions.

Whole-object selection provides the editable-sticker route. Selecting a region through part of a text object or image produces an image sticker by default. The creation sheet states the chosen representation so the user understands how the result can be edited.

Hidden content is excluded from a visible-canvas capture. Explicit layer captures honor selected-layer visibility and show an accurate preview. A locked layer can contribute to a canvas capture because copying leaves its content intact. Empty selections give a clear message and create no layer or history entry.

## Undo and redo

Undo and redo are persistent project features. Every completed document edit records enough information to restore its previous and next state. A drag, pinch, slider adjustment, or completed text-editing session becomes one meaningful history action. Multilayer operations undo as a unit.

A history sheet lists readable actions such as Move 3 layers, Change text, Duplicate group, and Create sticker. Users can jump to a retained state. Navigation, selection changes, export, and autosave do not consume document undo steps.

After an undo, a new edit starts a new history branch and clears the redo path. Explicit named checkpoints let the user preserve an important version before experimenting. A checkpoint can be opened as a project copy.

Store history as compact commands and changed properties, supported by periodic document snapshots. Keep large immutable assets once and reference them from document states. Retain assets while any live document, history entry, checkpoint, or saved sticker needs them.

History persists across app restarts. Its practical retention is governed by a configurable disk budget. Compaction may replace old commands with a baseline snapshot. The interface identifies the oldest available undo state before the user chooses to trim history, and keeps the current document and named checkpoints intact. Storage pressure pauses new changes that cannot be safely saved and presents recovery or cleanup options.

The goal is deep, dependable history within available storage. Layer count and history depth must not be advertised as literally infinite.

## Saving and export

The app autosaves each committed edit through a serialized transaction. A saved indicator appears only after the document update and history record are durable. On restart, the app restores the latest committed transaction and ignores incomplete writes. Save failure keeps the last committed version recoverable and shows an actionable message.

Project files include a versioned document manifest, referenced assets, necessary font data where permitted, and retained history. A portable project export allows backup, transfer, and continued editing. Project duplication gives the user an independent composition.

Image export offers PNG with optional transparency and JPEG with a selected background color and quality. The user can choose output dimensions, preview the result, save to the gallery, and share through Android's share sheet. PNG is also available for exporting individual stickers.

Exports use an immutable snapshot of the document taken when export begins. Rendering proceeds in the background with progress and cancellation. Failed or canceled exports clean up partial output. Large exports validate their resource needs before rendering and offer smaller dimensions if the requested output exceeds the tested capacity.

## High-level architecture

| Component | Responsibility |
| --- | --- |
| Editor interface | Project navigation, toolbars, sheets, layers panel, text input, and accessibility |
| Interaction controller | Hit testing, gesture ownership, selection, snapping, and transform previews |
| Document engine | Layer tree, transforms, grouping, styles, crops, masks, and asset references |
| Command history | Atomic edits, undo, redo, checkpoints, and retention |
| Renderer | Canvas previews, thumbnails, selection captures, and final exports |
| Persistence | Project transactions, autosave, recovery, asset lifetime, and schema migrations |
| Platform integration | Photo import, file access, gallery export, sharing, and app lifecycle |

Proposed implementation uses Kotlin, standard Android Views for interface controls, and a custom canvas View for the document. A small native application module keeps the initial build and dependency graph manageable. Architecture boundaries can be packages within that module.

The renderer consumes document state independently of the editing controls. Preview, sticker capture, and export share geometry, compositing rules, text layout, and clipping behavior. This reduces discrepancies between what the user edits and what they save.

Document changes flow through the command engine. Gesture previews stay temporary until committed. Import, export, image decoding, thumbnails, and persistence run outside the main UI thread. Results carry a document or asset version so stale background work cannot replace newer edits.

## Capacity and performance

Store image layers as asset references plus editing properties. Reuse decoded assets when appropriate and maintain bounded caches. Decode images at preview resolution for the current view, and release unused previews under memory pressure. Cull offscreen objects and generate layer thumbnails lazily. Large layer lists use recycled rows.

Final image output can require substantial memory even when previews are small. Introduce tiling or incremental output where the chosen renderer and encoder support it. Set output limits from measured peak memory and preserve the project if an export fails. Group effects and selection captures also require bounded intermediate surfaces.

Proposed validation targets include a 300-layer mixed composition, nested editable stickers, and 1,000 compact history actions with save and reopen. These are initial test workloads, not promised capacity ceilings. Measure gesture responsiveness, import latency, export time, disk growth, and peak memory on the Pixel 9 Pro. Use progressively larger workloads to identify practical limits.

## Development within 8 GB RAM

Use terminal builds with the Gradle wrapper and direct installation on the Pixel. Start with one Gradle worker, parallel project execution disabled, and approximately 1 GB of Gradle heap. Account separately for Kotlin compilation, native build processes, the operating system, and other applications. These initial settings require measurement and may need adjustment.

Pin compatible tool versions and keep dependencies focused. Establish a successful clean build, incremental build, APK install, and crash-log workflow before expanding the editor. Record peak memory for the complete build process tree and available system memory during each milestone. The laptop must remain usable without sustained swapping or memory-related termination.

Use physical-device interaction checks, ADB logs, screenshots, and targeted automated checks for document transformations, history, save recovery, and export consistency. The existing computer is the required development environment. Additional device coverage can be considered when broader distribution becomes a goal.

## Delivery sequence

| Milestone | Usable outcome and completion gate |
| --- | --- |
| Build foundation | APK builds within measured laptop resources and launches on the Pixel |
| Editable canvas | Import images, add text, transform objects, manage basic layers, save, reopen, and export |
| Reliable editing | Persistent undo and redo, atomic multilayer operations, autosave recovery, locks, and visibility |
| Reusable compositions | Grouping, independent duplicates, editable stickers, sticker collection, and nested editing |
| Selection tools | Rectangle and lasso scope selection, canvas or layer capture, transparent image stickers |
| Polished workflow | Shapes, text styling, image adjustments, snapping, alignment, distribution, and accessibility |
| Capacity verification | Large-document tests, history retention checks, export consistency, and build-memory validation |

The complete first release includes all of these milestones. Later extensions can include custom font import, more advanced masks, and additional image effects, driven by actual editing needs.

## Acceptance scenarios

1. Create a composition containing text, images, and shapes. Reorder, duplicate, hide, lock, and delete individual or multiple layers. Undo and redo each operation accurately.
2. Combine text and an image into an editable sticker. Duplicate it, change the duplicate's wording, and move it independently. Verify each instance retains its own content and transformations.
3. Select a rectangular or freehand region across overlapping objects. Capture either the visible canvas, active layer, or selected layers. Verify transparency, alignment, resolution, and stacking in the new sticker.
4. Perform a long gesture and undo once. Verify the object returns to its pre-gesture position, scale, and angle. Repeat with a multiselection and a nested group.
5. Save a sticker, reopen the application, and insert it into another project. Edit its text and verify earlier uses preserve their content.
6. Interrupt the app during saving and reopen it. Verify recovery reaches a consistent committed state with matching undo history and intact assets.
7. Export a transparent PNG and an opaque JPEG. Compare placement, text wrapping, crop boundaries, colors, and clipping with the editor preview at the chosen dimensions.
8. Build and install on the 8 GB Arch laptop, then exercise the large-document workload on the Pixel. Record memory and responsiveness before claiming practical capacity.

## Technical references

The product behavior above is a proposed design. These official references support the platform and resource-management approach.

- Android command-line builds and APK installation. https://developer.android.com/build/building-cmdline
- Gradle build memory and worker configuration. https://docs.gradle.org/current/userguide/build_environment.html
- Android image decoding and preview memory. https://developer.android.com/topic/performance/graphics/load-bitmap
- Android bitmap caching. https://developer.android.com/topic/performance/graphics/cache-bitmap
- Android Photo Picker. https://developer.android.com/training/data-storage/shared/photo-picker
- Android shared media storage. https://developer.android.com/training/data-storage/shared/media
