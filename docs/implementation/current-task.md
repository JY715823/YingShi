# Current Task: Preview Quality And Cover Compatibility

## Background

The backend is improving local preview generation and may return true image URLs for video covers through `?variant=cover`. Android already consumes `previewUrl`, `thumbnailUrl`, and `coverUrl`; this pass only keeps the model resolution compatible with those backend URLs.

## Goals

1. Continue using backend `previewUrl` / `thumbnailUrl` for image thumbnails.
2. Treat backend `?variant=cover` and `?variant=preview` URLs as valid video poster images.
3. Keep the existing fallback to video-source poster extraction when no image cover is available.
4. Avoid changes to photo-feed paging, positioning, upload center, trash, post detail, and Viewer playback controls.

## Scope

- Android media source URL resolution for app-content thumbnails.
- Compatibility with backend cover/preview variant URLs.

## Acceptance

1. Photo feed can display backend image previews.
2. Video media can use backend cover images when provided.
3. Existing video poster fallback stays available.
4. Android `assembleDebug` passes.
