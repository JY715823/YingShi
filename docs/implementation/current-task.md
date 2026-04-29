# Current Task - Stage 9.4 Cache State And Clear Entry Placeholders

## Goal
Establish a shared cache-state model and clear-cache entry placeholders for app-content media.

## Scope
- Add app-content media cache state keyed by `mediaId`.
- Model four lightweight fields for the current stage:
  - `previewCached`
  - `originalCached`
  - `videoCached`
  - `cacheSizeLabel`
- Keep cache state local-first and fake-only for now, with no real disk scan or file deletion.
- Share the same media cache state across photo-flow viewer, in-post viewer, and post detail.
- Add a single-media clear-cache entry inside both viewer entry contexts.
- Add a post-level clear-cache entry placeholder in the post-detail / gear-edit chain.
- Add a global cache-management placeholder entry or route with fake total-size summary.
- Reset `originalLoadState` back to not-loaded after clearing original cache.
- Keep photo feed cards clean and keep system-media tools outside the app-content cache strategy.
- Sync the minimum required PRD / UI / current-task docs together with code.

## Product intent
- Cache state belongs to app-content media only, not to system-media tools.
- Cache state is shared by the same `mediaId` across app-content surfaces so users do not see conflicting cache status.
- Stage 9.4 builds management boundaries and entry points first, before any real downloader, cache directory, or cleanup worker exists.
- Clearing cache should affect only cache-related local state, not media bodies, comments, or ownership relations.

## Do not do
- no real disk cache scan
- no real file deletion
- no WorkManager cleanup jobs
- no backend / OSS / Room / Retrofit
- no full settings page implementation
- no major viewer / post-detail / gear-edit architecture refactor

## Acceptance
- Media-level cache state exists and is keyed by `mediaId`.
- Single-media clear-cache entry works in photo-flow viewer and in-post viewer.
- Clearing original cache resets original-load state to not-loaded.
- A post-level clear-cache entry exists.
- A global cache-management placeholder entry or reserved route exists.
- Photo feed cards do not show cache state.
- System-media tools do not show app-content cache state.
- Related docs are minimally updated in the same change.
- The project builds and remains runnable.

## Follow-up after Stage 9.4
- Real preview / original / video cache directory management
- Real file-size calculation and disk usage scan
- Real file deletion and cleanup confirmation strategy
- Background cleanup jobs and retry handling
- OSS / downloader / player cache integration
- Settings-page integration and deeper cache diagnostics
