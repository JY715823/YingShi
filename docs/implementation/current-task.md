# Current Task: Photo Feed Thumbnail Loading Polish

## Background

The backend now serves clearer preview-v2 images and optional video cover images. The Android photo feed should request preview sizes that match the current grid density, keep scrolling smooth in dense modes, and reuse already loaded preview/cover assets when entering Viewer where possible.

## Goals

1. Use density-aware image request sizes for 2/3/4/8/16-column photo feed grids.
2. Keep large cells clear while avoiding oversized decodes for dense 8/16-column grids.
3. Reduce thumbnail flicker by sharing disk cache and avoiding tiny memory-cache entries from poisoning Viewer previews.
4. Prefer backend video cover images in Viewer before falling back to video-source poster extraction.
5. Avoid changes to paging, upload center, trash, post detail, and Viewer playback controls.

## Scope

- Photo feed thumbnail request sizing and prefetch sizing.
- App-content thumbnail memory/disk cache key behavior.
- Viewer image/video poster prefetch and display source selection.
- Compatibility with backend `?variant=preview` and `?variant=cover` URLs.

## Acceptance

1. Photo feed 2/3/4/8/16-column modes use appropriate preview request sizes.
2. Image and video cover loading remains stable while scrolling.
3. Viewer can reuse or quickly load feed preview/cover assets.
4. Android `assembleDebug` passes.
