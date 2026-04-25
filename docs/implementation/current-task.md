# Current Task - Stage 5.3 Comment Item Actions v1

## Goal
Add basic local item-level actions for post comments and media comments.

## Scope
This stage covers:
- long press comment item
- comment action menu
- local edit comment
- local delete comment
- basic select state
- basic copy behavior
- viewer overlay position adjustment
- comment bubble and preview panel alignment
- minimal related doc updates

## Product intent
- Post comments and media comments remain separate.
- Item-level comment actions should work locally first.
- Editing or deleting a post comment must not affect media comments.
- Editing or deleting a media comment must not affect post comments.
- Same mediaId should share the same media comment state across photo-flow viewer and in-post viewer.

## Viewer adjustment
- Photo-flow viewer overlay actions should move slightly further downward.
- In-post viewer overlay actions should sit closer to the segmented indicator but must not overlap it.
- The segmented indicator must remain clearly visible.
- Comment preview panel should align with the comment bubble and feel like it expands from the bubble.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real permission system
- no replies
- no nested comments
- no quoted replies
- no complex rich text editor
- no notification system

## Done when
- Post comments support local item actions
- Media comments support local item actions
- Edit / delete / select / copy work at a basic local level
- Post and media comment operations do not mix
- Viewer overlay actions are repositioned naturally
- Comment bubble and preview panel align visually
- App builds and runs
- Docs are minimally synchronized