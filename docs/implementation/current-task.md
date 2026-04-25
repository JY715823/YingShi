# Current Task - Stage 5.5 Comment System Polish and Boundary Check

## Goal
Polish and stabilize the current local comment system before moving to Gear Edit and media management.

## Scope
This stage covers:
- fixing text selection keyboard behavior
- keeping text selection read-only and keyboard-free
- separating read-only selection state from editable input/edit state
- checking post/media comment boundaries
- checking comment input and local append flow
- checking latest 10 / expand more / collapse behavior
- checking viewer preview/detail comment flow
- checking inline action menu behavior
- minimal related doc updates

## Product intent
- Comment text selection should not open the keyboard.
- Comment text selection stays read-only; only edit/input states may open the keyboard.
- Keyboard should only appear for comment input or comment editing.
- Post comments and media comments must remain separate.
- Media comments are keyed by mediaId and shared across viewer contexts.
- Comment preview and comment detail should stay stable and lightweight.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real pagination
- no replies
- no nested comments
- no quoted replies
- no notification system
- no complex rich text editor
- no large unrelated refactor

## Done when
- Selecting comment text does not show the keyboard
- Editing comments still shows the keyboard
- Comment input still shows the keyboard
- Post and media comments do not mix
- Existing comment flows remain stable
- App builds and runs
- Docs are minimally synchronized
