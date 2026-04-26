# Current Task - Stage 9.1 Original Loading Foundation

## Goal
Build the first local-state version of original media loading for app content media.

## Scope
- originalLoadState per app media
- single-media load original action
- shared original state by mediaId
- post-level load all originals
- simple loading / loaded / failed UI states
- shared state across photo-flow viewer, in-post viewer, and post detail
- post detail supports a local fake "load all originals" flow
- minimal doc updates

## Product intent
- App content media uses preview-first and original-on-demand.
- Original loading state belongs to app media, not system media.
- The same mediaId should share original state across photo-flow viewer, in-post viewer, and post detail.
- Photo feed cards remain clean and do not show original state.

## Do not do
- no real download
- no real cache
- no backend / Room / Retrofit / OSS
- no WorkManager
- no long-image tiling
- no video original handling

## Done when
- Single media original-load state works
- Same mediaId shows shared state in multiple app-content contexts
- Post detail can load all originals for current post
- System media does not show original-load actions
- App builds and runs
