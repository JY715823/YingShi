# Current Task - Stage 4.2 Post Detail v1

## Status
Stage 4.1 Album Page v1 is complete. Current work is Stage 4.2.

## Goal
Build the first usable post detail page and refine the photo module top navigation.

## Scope
This stage covers:
- photo module top bar refinement:
  - closer to the system status bar while respecting safe area
  - current secondary title larger and more prominent
  - inactive secondary titles smaller and weaker
  - `系统媒体` shortened to `系统`
  - notification shown as a bell icon button
- secondary root-page swipe switching:
  - only for `照片 / 相册 / 回收站`
  - not for post detail, Viewer, system media, or other independent pages
- independent post detail route / screen
- post media area with same-post media switching shell
- media info row
- post info white section
- post comment section shell
- minimal related doc updates

## Product intent
- The photo module root pages should have a compact and polished top bar.
- Photo / Album / Trash root pages may switch by horizontal swipe.
- Independent pages such as post detail and Viewer must not participate in root-page swipe switching.
- Post detail is a context page, not a Viewer and not an album grid.
- Post comments and media comments stay separated.

## Do not do in this stage
- no real backend
- no Room / Retrofit / MediaStore
- no real export / save
- no real Gear Edit
- no full comment system
- no in-post Viewer final implementation

## Done when
- Photo module top bar is compact and visually improved
- Current secondary title is prominent
- Secondary root pages support horizontal swipe switching
- Post detail opens as an independent page
- Post detail hides global bottom nav and photo module top nav
- Post detail has media area, media info row, info white section, and post comment shell
- App builds and runs
- Docs are minimally synchronized
