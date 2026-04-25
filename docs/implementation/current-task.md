# Current Task - Stage 5.2 Comment Input and Local Add v1

## Goal
Add local comment input and local append behavior for post comments and media comments.

## Scope
This stage covers:
- comment input bar
- local add post comment by postId
- local add media comment by mediaId
- in-memory fake comment source updates shared across viewer entry points
- immediate UI refresh after sending
- latest-first ordering
- default latest 10 comments
- expand more / collapse to latest 10
- minor viewer overlay offset adjustment
- minimal related doc updates

## Product intent
- Post comments and media comments remain separate.
- Post comments belong only to posts.
- Media comments belong only to media.
- Same mediaId should share the same media comments across photo-flow viewer and in-post viewer.
- Input should be simple, lightweight, and not a rich-text editor yet.

## Viewer adjustment
- Photo-flow viewer overlay actions should move slightly downward for a more natural visual balance.
- In-post viewer overlay actions must still avoid overlapping the bottom segmented indicator.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real pagination
- no comment edit / delete / select / copy
- no reply / nested reply / quote
- no notification system
- no rich text editor

## Done when
- Post detail can add local post comments
- Viewer can add local media comments
- Post and media comments do not mix
- Comments show latest-first
- Latest 10 / expand more / collapse works at a basic level
- Viewer overlay actions are repositioned without overlapping in-post segmented indicator
- App builds and runs
- Docs are minimally synchronized
