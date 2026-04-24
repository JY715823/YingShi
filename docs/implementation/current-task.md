# Current Task - Stage 3.4 Viewer Polish and Tunability

## Status
Stage 3.3 Viewer Comment Entry and Related Posts States is complete. Current work is Stage 3.4.

## Goal
Polish the viewer layout and make its core vertical proportions easier to tune.

## Scope
This stage covers:
- scrollable preview comments area
- slight viewer layout refinements
- slight height adjustments for:
  - media canvas
  - preview comment area
  - fuller comment panel
- better tunability for future layout tweaks
- minimal related doc updates

## Product intent
- The viewer still belongs to the global media-flow context from the photo page.
- It should remain immersive, quiet, dark, and image-first.
- Preview comments should be usable without becoming a heavy panel.
- Layout parameters should become easier to tweak later.
- Keep the vertical relationship: canvas > fuller comment panel > preview comment area.

## Do not do in this stage
- no real comment system
- no real post navigation
- no real original-image loading
- no in-post viewer
- no backend / Room / Retrofit

## Done when
- Preview comments area can scroll internally
- Canvas is moved slightly downward
- Preview area is slightly taller
- Full comment panel is slightly lower in height
- Layout remains balanced and immersive
- Photo-page viewer still does not show in-post segmented white bars
- Docs are minimally synchronized
- App builds and runs
