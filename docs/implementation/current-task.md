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
- viewer top-structure reset:
  - left-edge back mark
  - centered time capsule
  - right-side settings placeholder
- centered adaptive canvas that fills width whenever possible
- collapsed-by-default preview comments entry flow
- better tunability for future layout tweaks
- minimal related doc updates

## Product intent
- The viewer still belongs to the global media-flow context from the photo page.
- It should remain immersive, quiet, dark, and image-first.
- Preview comments should be usable without becoming a heavy panel.
- Layout parameters should become easier to tweak later.
- Keep the vertical relationship: canvas > fuller comment panel > preview comment area.
- Canvas should feel like the clear main actor: centered, adaptive, and width-first without card-like gutters.
- Preview comments should stay collapsed by default so the viewer opens cleaner, then follow the chain: bubble -> preview -> full panel.
- Viewer top should stay quiet and unified with the dark status-bar area: left back mark, centered time capsule, right settings placeholder.

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
- Preview comments are collapsed by default and only expand after the first bubble tap
- Second bubble tap opens the fuller comment panel
- Time capsule sits at the top center, with back and settings completing the top structure
- Viewer top is simplified and visually unified with the dark status-bar area
- Photo-page viewer still does not show in-post segmented white bars
- Docs are minimally synchronized
- App builds and runs
