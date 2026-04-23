# Current Task - Stage 3.3 Viewer Comment Entry and Related Posts States

## Status
Stage 3.2 Viewer Interactions v1 is complete. Current work is Stage 3.3.

## Goal
Complete the first viewer-level entry loop for comments and strengthen the related-posts entry states.

## Scope
This stage covers:
- tap comment bubble -> full comment panel placeholder
- tap preview comment -> full comment panel placeholder
- comment panel state and back behavior inside viewer
- related-posts button states:
  - none
  - single
  - multiple
- minimal related doc updates

## Product intent
- This viewer still belongs to the global media-flow context from the photo page.
- Comment experience should keep the two-layer structure:
  - preview layer
  - fuller bottom comment panel
- Related-posts entry should reflect relationship state without forcing real navigation yet.
- Viewer remains immersive and image-first.
- Keep the confirmed vertical layout relationship: canvas > comment detail > comment preview.

## Do not do in this stage
- no real comment system
- no comment edit / delete / copy
- no real related-post navigation
- no real original-image loading
- no in-post viewer
- no backend / Room / Retrofit

## Done when
- Comment bubble opens a fuller comment panel placeholder
- Preview comments can also open that panel
- Related-posts button has none / single / multiple states
- Viewer back behavior handles comment-panel-first closing
- Photo-page viewer still does not show in-post segmented white bars
- App builds and runs
- Docs are minimally synchronized
