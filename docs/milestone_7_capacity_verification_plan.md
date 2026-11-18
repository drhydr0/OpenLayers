# Milestone 7 plan: Capacity verification

Goal from CLAUDE.md: large-document tests, history retention, export consistency, emulator tests in CI.

Built on 2026-11-17, at the developer's request for speed, before the milestone 5 and 6 phone tests were run.

## What was built

- **Large-project test (JVM, every PR).** `LargeProjectTest` builds the design doc's workload: 300 mixed layers including nested stickers, and 1,000 history actions. It saves, reloads, checks the reloaded project equals the original, undoes all 1,000 actions back to an empty canvas, redoes them, and saves again. It prints the sizes and times to the CI log.
- **History retention test.** A history longer than its cap keeps the newest steps, saves, reloads, and undoes exactly those.
- **On-device rendering tests (emulator, every PR).** `RenderingDeviceTest` checks real pixels: export equals the editor's drawing pixel for pixel; an area capture equals the same area of the export; transparent PNG, JPEG, double-size export, lasso transparency, layer-only capture, empty capture, crop, flip, adjustments, hidden layers, and group transforms.
- **Stress project on the phone.** Debug builds have **Diagnostics → Make test project**, which creates the same 300-layer, 1,000-step project with six generated photo-sized images, for measuring on the Pixel.

## Measured so far (cloud VM, not a phone)

For 300 layers and 1,000 undo steps: project file about 2.5 MB; save about 0.1 to 0.2 s; reload about 0.2 to 0.3 s; undoing all 1,000 steps about 0.1 s; a save after one more edit under 0.1 s.

A phone is slower than this machine, so these numbers only show the design has room. The one-file save (document and history together) stays; an append-only history journal is not needed at this size.

## Measured on the Pixel 9 Pro (2026-11-17, build 73, commit 77819a6b20ab)

| Measurement | Result |
| --- | --- |
| Create the 300-layer, 1,000-step test project | 0.9 s |
| Export a 230-layer project at 200% | 2.6 s |
| Export a 4320 px image at 200% (8640 px) | 5 s |
| Export an 8192 px image at 200% (16384 px, about 1 GB of pixels) | 15 s |
| Import a 6144 × 8160 photo | Works |
| Free phone memory during the run | 2.1 GB |
| App Java memory right after building the test project | 253.5 of 256 MB |

The Java memory reading was close to the app's limit. It was taken right after building the test project, so part of it was likely uncollected garbage. Two changes followed: the diagnostics screen now clears garbage before measuring, and the app asks for Android's large memory pool (`largeHeap`, about 512 MB on this phone), as image editors commonly do.

## Still to measure on the Pixel

`docs/milestone_7_phone_test.md` covers what only the phone can show: how the editor feels with 300 layers, memory in the diagnostics report, and export time and success at each size. The design doc asks that capacity is not claimed before these are recorded.

## Not done

- A configurable disk budget and a trim-history screen: history is capped at 500 steps per project, which the measurements above show is small on disk. Revisit if real projects grow far beyond that.
- Tiled or incremental export for sizes beyond memory: export refuses sizes that would not fit instead.
- Gesture tests on the emulator: the gesture math is JVM-tested; touch handling is covered by the phone tests.
