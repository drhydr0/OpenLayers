# Feedback round 1 (after the milestone 5 and 6 phone tests)

Changes the developer asked for on 2026-11-18, and how each was done.

| Request | What changed |
| --- | --- |
| Multi toggle into the layers panel; Select on the main bar | The bottom bar now has **Select** (area selection) where Multi was. **Select several** sits at the top of the layers panel. Select area is no longer in the **⋮** menu. |
| Long press toggles multiselect | Long-pressing a layer on the canvas or in the layers panel turns Select several on and adds that layer. Long-pressing again turns it off and keeps only that layer selected. |
| Default outline thickness | New rectangles, rounded rectangles, and ellipses start with a 4 px black outline. |
| Exact values | Every slider's heading (marked ✎) can be tapped to type an exact number. |
| Free transform with the corner handles | A single shape, image, or sticker (group) gets free-resize handles: the corners stretch both ways and the new side squares stretch one way, with the opposite side staying put. Shapes change their own width and height, so outlines keep their thickness. Pinching still scales evenly. Text and multiple selections keep proportional corners. |
| Opacity under Edit | One **Edit** button for any single layer. Text, shape, and image sheets have an Opacity slider; groups and stickers get a sheet with Opacity. Opacity is gone from Actions. |
| Drag into and out of groups | In the layers panel, a dragged row joins the group of the row just above where it lands (or goes inside an open group when dropped right under its row). Moving between groups keeps the layer where it was on the canvas. |
| Ungroup one element | **Actions → Remove from group** (while editing inside a group) and **Remove from group** in a child row's menu take just that layer out, leaving the rest grouped. If no layers would be left, the group goes too. |
| 200% export "too large" | The limit compared the image with the app's Java memory, but image pixels haven't lived there since Android 8. It now compares with the phone's actual free memory (and Android's 2 GB limit for one image). |
| More fonts, names in their own font | 14 new fonts: system Sans Light, Condensed, Black; bundled Lato (with real bold and italic), Lobster, Pacifico, Great Vibes, Permanent Marker, Bebas Neue, Abril Fatface, Bangers, Special Elite, Press Start, Creepster. The font list shows each name in its font. Licenses are in `docs/font-licenses`. |
| See-through sheets with live preview | Edit sheets sit on the lower half of the screen, slightly transparent, without darkening the canvas, and every change shows on the canvas as you make it. Cancel puts it back. |
| Space evenly | Layers are ordered by their centers and the two outermost stay put. When they overlap too much for equal gaps, their centers are spaced evenly instead of piling up. |
| Pixel nudging with a canvas preview | **Actions → Nudge** hides the bars and the layers panel and shows only arrow buttons, a 1 px / 10 px step switch, Cancel, and Done. Every tap moves the selection live; Done saves it as one undo step. The arrows are gone from the Position and size sheet. |
