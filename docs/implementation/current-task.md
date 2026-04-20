# Current Task - Stage 2.1 Photo Feed v1

## Goal
Build the first usable version of the photo page as a global media feed.

## Scope
This stage covers:
- fake media data
- fake repository / provider for photo feed
- deduplicated global media feed screen
- sort by `media_display_time` desc
- grouping by time
- density switching for `2 / 3 / 4 / 8 / 16` columns
- lightweight thumbnail-only media cards
- long-press placeholder for later multi-select entry

## Product intent
- The photo page is a global media stream, not a post feed.
- The user should first see media itself.
- The page should feel light, clean, time-aware, and system-gallery-like.
- Media relationship info should stay out of the card layer.

## Current shell assumption
Use the latest confirmed navigation structure:
- single-row top area
- left: `照片 / 相册 / 回收站`
- right: `系统媒体 + 铃铛`
- right-side actions visible in all three secondary pages

## Temporary implementation choice for this stage
- density switching can use a lightweight local control instead of pinch zoom
- albums / trash stay as shell placeholders
- system media / bell stay as UI entry placeholders only

## Do not do in this stage
- no real backend
- no Room / Retrofit / MediaStore
- no real Viewer
- no comment system
- no Time Scrubber final implementation
- no full multi-select implementation

## Done when
- Photo page clearly behaves as a global media feed
- Feed uses fake deduplicated media data
- Sorting and grouping work
- Density can switch between `2 / 3 / 4 / 8 / 16`
- Cards only show thumbnails
- App builds and runs
