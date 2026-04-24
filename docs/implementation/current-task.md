# Current Task - Stage 4.1 Album Page v1

## Status
Stage 3.4 Viewer Polish and Tunability is complete. Current work is Stage 4.1.

## Goal
Build the first usable version of the album page as a post-browsing and content-archive page.

## Scope
This stage covers:
- viewer comment-entry rule sync:
  - preview is collapsed by default
  - first bubble tap expands preview
  - second bubble tap collapses preview
  - only tapping a comment opens the fuller comment panel with highlight
- photo-module top bar cleanup:
  - tighter top spacing
  - single-row horizontal layout
  - left secondary tabs + right tools on one baseline
- photo-page density interaction:
  - keep explicit density switcher
  - add basic pinch-to-zoom switching for `2 / 3 / 4 / 8 / 16`
- fake album and post-summary data
- album-switch chips
- 2-column post grid
- album-page density interaction:
  - support `2 / 3 / 4` columns
  - add basic pinch-to-zoom switching
  - present album chips as two rows
- post card structure
- placeholder route into post detail
- treat post detail as an independent page shell without the photo-module top bar or global bottom bar
- album-manage placeholder entry
- minimal related doc updates

## Product intent
- The album page is not the default browsing entry.
- It should feel like a memorial album directory / content archive page.
- It should be softer and more content-oriented than the photo page.
- It should not look like a pure media feed and should not look like a heavy social feed.

## Current shell assumption
Use the latest confirmed global shell and navigation structure.

## Do not do in this stage
- no full post detail implementation
- no post comment system
- no real edit / delete / export
- no backend / Room / Retrofit / MediaStore

## Done when
- Photo-module top bar stays horizontal, tighter, and visually unified
- Photo page supports basic pinch switching between `2 / 3 / 4 / 8 / 16`
- Album chips can switch current album
- Album page supports `2 / 3 / 4` columns with basic pinch switching
- Album chips present as two rows
- Post cards have cover + light info band + media count
- Tapping a post opens a placeholder post-detail route
- Post detail is shown as an independent page shell
- App builds and runs
- Docs are minimally synchronized
