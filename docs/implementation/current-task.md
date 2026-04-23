# Current Task - Stage 3.2 Viewer Interactions v1

## Status
Stage 3.1 Viewer Shell v1 is complete. Current work is Stage 3.2.

## Goal
Enhance the photo-page viewer with the first real interaction layer.

## Scope
This stage covers:
- basic zoom / pan
- hide or weaken overlays while zoomed
- comment preview layer shell
- refined right-bottom action states
- minimal related doc updates

## Product intent
- This viewer still belongs to the global media-flow context from the photo page.
- It should feel immersive, quiet, dark, and image-first.
- Zooming should shift priority from navigation to detailed viewing.
- Comment preview should feel light and attached to the canvas, not like a heavy sheet.
- Current layout tuning: media canvas sits slightly higher with safe distance from the top bar; preview is roomier but remains smaller than the canvas; comment detail is higher than preview but not a heavy panel.

## Do not do in this stage
- no real comment system
- no full comment feature set
- no real original-image loading
- no real related-post navigation
- no in-post viewer
- no backend / Room / Retrofit

## Done when
- Viewer supports basic zoom / pan
- Zoomed state stops horizontal media switching
- Overlays hide or weaken while zoomed
- Comment preview layer shell exists
- Viewer layout keeps hierarchy: canvas > comment detail > comment preview
- Comment bubble can open a placeholder full comment area for later real media comments
- Original-image action shows placeholder states: not loaded / loading / loaded
- Related-post action is shown only when the current media has related posts
- Photo-page viewer still does not show in-post segmented white bars
- App builds and runs
- Docs are minimally synchronized
