# Current Task - Stage 9.2 Long Image and Zoom Basics

## Goal
Support basic long-image viewing and stabilize zoom/pan behavior in app-content viewers.

## Scope
- long-image detection
- fake long-image samples
- long-image viewer layout
- vertical browsing for long images
- zoom/pan mode stabilization
- reset zoom/scroll state on media switch
- record follow-up long-image performance TODOs
- minimal doc updates

## Product intent
- Long images should be readable, not squeezed into one screen.
- Viewer remains immersive and image-first.
- Zoomed state should prioritize detailed viewing over media switching.
- Heavy long-image optimization comes later.

## Do not do
- no real tiling
- no real original download
- no video playback
- no system media long-image special handling
- no backend / Room / Retrofit

## Done when
- Long images can be opened in Viewer
- Long images fit width and scroll vertically
- Normal images still zoom/pan
- Zoomed state blocks accidental media switching
- Switching media resets abnormal zoom/scroll state
- App builds and runs
