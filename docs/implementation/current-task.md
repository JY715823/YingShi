# Current Task - Stage 9.3 Video Viewer Basics

## Goal
Let app-content Viewer start supporting video media in the same flow as photos.

## Scope
- Add or standardize `mediaType: image / video` in app-content media models.
- Add a few fake video samples into app-content fake data.
- Make photo feed, album/post cover, post detail media area, and in-post viewer all recognize video media.
- Show a lightweight video marker on video cards and video covers.
- Let photo-flow viewer and in-post viewer both render a video viewing shell.
- Keep video controls minimal: basic play / pause plus simple progress placeholder or lightweight progress.
- Reset / stop current video session state when switching media, leaving the current video, or exiting the viewer.
- Keep comments, original-load entry, related-post entry, and in-post white segment indicator usable.
- Sync the minimum required PRD / UI / current-task docs together with code.

## Product intent
- Video belongs to the same app-content media stream as images, not a separate viewer type.
- Viewer stays immersive and dark even when current media is a video.
- Video state is session-local only and should never leak across media items.
- We prefer a small, fake-safe, local-first playback shell now over a heavy real player integration.

## Do not do
- no complex player controls
- no background playback
- no real video cache
- no speed control
- no fullscreen secondary player
- no volume control
- no real upload / download
- no backend / Room / Retrofit
- no large viewer architecture refactor

## Acceptance
- Fake videos appear in the photo feed and in post media sequences.
- Video cards and video covers show a lightweight video marker.
- Tapping a video opens Viewer and shows a clear video viewing area.
- Play / pause is minimally usable or clearly represented in the local fake shell.
- Switching between image and video does not crash and does not mix playback state.
- Leaving the current video stops playback state.
- Related docs are minimally updated in the same change.
- The project builds and remains runnable.

## Follow-up after Stage 9.3
- Real player engine integration
- Real decoded video frames / thumbnails
- Seek scrubbing, buffered progress, and duration accuracy
- Background playback
- Speed / mute / volume / fullscreen controls
- Real upload / download / cache pipelines
