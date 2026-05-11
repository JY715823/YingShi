# Current Task: Android Media Cache Management Baseline

## Background

The Android client now uses backend preview-v2 images, video cover images, Coil disk / memory caching, local extracted video posters, and Media3 for remote App video playback. Cache management should stop being a fake-only placeholder and expose a safe baseline view of App-owned media cache usage.

## Goals

1. Show real App-owned media cache usage in cache management.
2. Distinguish thumbnail / cover cache from original / remote-video cache as safely as the current storage layout allows.
3. Allow clearing thumbnail / video-cover cache without deleting App media records, system album files, backend originals, or backend preview-v2 / cover files.
4. Allow clearing original / remote-video local cache, limited to App-generated cache files and local original-load state.
5. Keep photo feed previews, video covers, Viewer, paging, multi-select, and video preview behavior intact after cache cleanup.

## Scope

- Android cache management page.
- Coil media image cache directory and local video poster files.
- Media3 App remote-video cache directory.
- Original-load local state reset and exact Coil original disk entries when known.
- No Server changes.

## Acceptance

1. Settings / cache management shows media cache usage.
2. Clearing thumbnail / cover cache lets photo feed reload preview / cover images normally.
3. Clearing original / remote-video cache does not delete system album files, backend media records, or backend files.
4. Re-entering photo feed continues to benefit from Coil / poster / video cache.
5. Android `assembleDebug` passes.
