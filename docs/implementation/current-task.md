# Current Task - Stage 6.3 Media Management Actions v1

## Goal
Complete the first local version of core media management actions.

## Scope
This stage covers:
- multi-select delete mode
- directory delete
- system delete
- empty-post protection
- basic media sorting
- cancel sorting rollback
- set-cover stabilization
- delete-post entry placeholder
- minimal related doc updates

## Product intent
- Media deletion inside a post must ask for deletion semantics.
- Directory delete only removes the media from the current post.
- System delete removes the media globally from the app content model.
- Empty posts should not remain after deletion.
- Sorting affects only the current post media order.
- Formal recycle bin behavior comes later in Stage 7.
- If deletion would leave the current post empty, the user must choose between deleting the whole post or cancelling.
- Sorting cancel should restore the order from when sort mode was entered.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real MediaStore delete
- no real system gallery delete
- no formal recycle bin
- no 24h undo
- no large unrelated refactor

## Done when
- Delete mode supports multi-select
- Delete semantic choice is shown before deletion
- Directory delete and system delete behave differently in local state
- Empty-post protection works
- Sorting can be completed or cancelled
- Set cover remains stable
- App builds and runs
- Docs are minimally synchronized
