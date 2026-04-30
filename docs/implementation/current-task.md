# Current Task - Stage 11B-3 Real Feed Upload Trash Integration

## Goal
Continue Android real-backend integration for photo feed, upload/import, and trash while keeping FAKE as the default safe path.

## Scope
- real media feed
- real photo-flow viewer media context
- real media comments from viewer context
- upload/import backend shell integration
- system media create-post / add-to-post real backend path
- real trash list/detail/restore/remove/undo
- Chinese REAL data display check
- same-space comment edit/delete policy alignment
- minimal docs update

## Product intent
- FAKE mode remains the safe default.
- REAL mode should gradually replace fake data per module.
- Photo feed in REAL mode should come from backend media feed.
- Trash in REAL mode should use backend trash APIs.
- Upload/import can remain local-dev backend flow before OSS.
- Real backend seed data should be natural Chinese for app preview.

## Do not do
- no full UI redesign
- no removal of fake repositories
- no real OSS
- no real Android system delete
- no complex permission system
- no real notification implementation

## Done when
- FAKE mode still works
- REAL mode photo feed loads backend media
- REAL mode viewer opens backend media context
- REAL mode viewer comments work with mediaId
- REAL mode upload/import path is wired or has safe error states
- REAL mode trash flow works
- Chinese backend seed data is visible
- Same-space users can edit/delete each other's comments
- Android builds

## Stage Notes
- development REAL data should read naturally in Chinese, not placeholder English
- comment edit/delete follows space membership, not author-only restriction
- notification to original author after non-author edit/delete stays as TODO for a later stage
