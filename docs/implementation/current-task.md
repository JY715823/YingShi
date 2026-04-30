# Current Task - Stage 11B-4 Real Feature Completion

## Goal
Complete more REAL-mode backend integration for Gear Edit, media management, upload/import refresh, and trash state consistency.

## Scope
- REAL Gear Edit integration
- update post title / description / displayTime / albums
- REAL media management integration
- set cover
- save media order
- directory delete
- system delete
- upload/import flow verification
- trash refresh after delete / restore / remove / undo
- minimal docs update
- REAL pages should refresh after save, delete, restore, and import mutations

## Product intent
- FAKE mode remains the default safe path.
- REAL mode should gradually cover all core app-content flows.
- Gear Edit and media management must work against backend data.
- Delete and restore should refresh related pages.
- UI polish belongs to Stage 12, not this stage.

## Do not do
- no removal of fake repositories
- no forced REAL default
- no real OSS
- no real Android system delete
- no real notification implementation
- no broad UI redesign
- no large unrelated refactor

## Done when
- FAKE mode still works
- REAL Gear Edit can save post basic info
- REAL media management can set cover, sort, directory delete, and system delete
- REAL upload/import results show in app pages
- REAL trash refreshes after state changes
- Android builds

## Implementation notes
- Gear Edit REAL uses `PATCH /api/posts/{postId}` and keeps the existing page shell.
- Media management REAL uses `PATCH /api/posts/{postId}/cover`, `PATCH /api/posts/{postId}/media-order`, and `DELETE /api/posts/{postId}/media/{mediaId}`.
- REAL photo feed, album page, post detail, system import, and trash page now share one lightweight backend mutation refresh signal.
