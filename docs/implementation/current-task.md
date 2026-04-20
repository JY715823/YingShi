# Current Task - Stage 2.2 Photo Feed Interactions

## Goal
Refine the current Stage 2.2 interaction shell so the photo page feels closer to the confirmed media-flow behavior:
- a lightweight Time Scrubber with a follow bubble
- a clearer multi-select context shell
- a placeholder entry route into viewer

## Scope
This stage covers:
- Time Scrubber v1 refinement
- multi-select mode shell refinement
- click-to-viewer placeholder route
- minimal related doc updates
- recording the next-stage back behavior requirements without fully implementing them

## Product intent
- The photo page is still a global media stream, not a tool panel.
- Interactions should stay light and should not overpower media itself.
- The scrubber should feel like a right-side positioning aid, not a floating tool widget.
- Long press enters multi-select mode.
- Tap enters media-flow viewer context.

## Current shell assumption
Use the latest confirmed navigation structure:
- single-row top area
- left: 照片 / 相册 / 回收站
- right: 系统媒体 + 铃铛
- right-side actions visible in all three secondary pages

## Refinement points in this round
- Time Scrubber uses a fuller right-side vertical interaction column.
- The date bubble follows the current scrubber position instead of staying fixed in the middle.
- Multi-select mode switches the photo top bar into a context bar: `取消` + `已选 x 项`.
- Multi-select actions appear in an extra bar above the global bottom navigation.
- Exiting multi-select restores the regular top bar and regular bottom shell.

## Back behavior note for later stages
- Future formal behavior: system back should exit the current transient state first.
- Examples:
  - viewer -> photo feed
  - multi-select mode -> normal photo feed state
- App exit should not happen too easily.
- Future target: first back gives a light prompt, second back exits.
- This stage only records that requirement and allows minimal state-flow fixes when clearly needed.

## Do not do in this stage
- no real viewer implementation
- no comment system
- no real delete / export flow
- no real system media integration
- no backend / Room / Retrofit
- no final pinch-to-zoom density switching
- no full back system redesign

## Done when
- Time Scrubber stays lightweight, is hidden by default, and fades out after interaction
- Time Scrubber date bubble follows the current scrubber position
- Time Scrubber interaction area is a fuller right-side vertical column
- Long press enters multi-select mode
- Multi-select switches the top bar into context mode
- Multi-select shows a lightweight action bar above the global bottom nav
- Tap enters a viewer placeholder route
- Related docs are minimally synchronized with the current interaction rules
- App builds and runs
