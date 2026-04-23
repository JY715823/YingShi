# Current Task - Stage 3.1 Viewer Shell v1

## Goal
Build the first real viewer shell for media opened from the photo page.

## Scope
This stage covers:
- viewer route / screen for photo-page media flow
- open viewer from current selected media
- immersive dark viewer shell
- horizontal swipe across the global media feed
- overlay placeholders:
  - comment entry
  - time capsule
  - load original placeholder
  - related posts placeholder
- minimal related doc updates

## Product intent
- This viewer is entered from the photo page, so it belongs to the global media-flow context.
- It should feel immersive, quiet, dark, and image-first.
- It is not the post-detail page and not the in-post viewer.
- From this entry path, no segmented white bars should appear.

## Current shell assumption
Use the latest confirmed navigation structure:
- single-row top area
- left: 照片 / 相册 / 回收站
- right: 系统媒体 + 铃铛
- right-side actions visible in all three secondary pages

## Do not do in this stage
- no real comment system
- no real comment preview layer
- no real full comment sheet
- no real original-image loading
- no real related-post navigation
- no in-post viewer
- no backend / Room / Retrofit

## Done when
- Tapping media from the photo page opens a real viewer shell
- Viewer opens based on the current global media set and tapped position
- Viewer swipes across the global media flow
- Viewer has immersive dark structure and light edge overlays
- Viewer has the first edge action shell: left comment entry, right time/original/related-post placeholders
- Photo-page viewer does not show post-segment white bars
- System back exits the current viewer state first
- App builds and runs
- Docs are minimally synchronized
