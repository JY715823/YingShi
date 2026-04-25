# Current Task - Stage 7.1 Trash Foundation and Delete Flow Integration

## Goal
Build the local trash foundation and connect existing delete flows to trash entries.

## Scope
This stage covers:
- trash item model
- three trash categories
  - deleted posts
  - media removed from post
  - globally deleted media
- trash page category switch
- local post delete to trash
- local directory delete to trash
- local system delete to trash
- photo-page multi-select delete into system-delete trash
- minimal trash detail placeholder
- minimal related doc updates

## Product intent
- Delete actions should no longer simply disappear without trace.
- Post delete, directory delete, and system delete have different meanings.
- Trash page is divided into three categories.
- Trash remains local-first and in-memory in this stage.
- Formal restore, delete-detail pages, and 24h undo come later.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real Android system media delete
- no formal restore flow
- no 24h undo
- no physical cleanup
- no large unrelated refactor

## Done when
- Trash page has three categories
- Deleted posts appear in deleted-post trash
- Directory-deleted media appears in removed-media trash
- System-deleted media appears in system-deleted-media trash
- Normal pages reflect local delete effects
- App builds and runs
- Docs are minimally synchronized
