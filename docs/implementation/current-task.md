# Current Task - Stage 4.4 In-post Viewer v1

## Goal
Upgrade the in-post viewer placeholder into a usable in-post viewer v1 by reusing the existing photo-flow viewer shell where appropriate.

## Scope
This stage covers:
- opening in-post viewer from post detail media area
- reusing shared viewer shell components
- switching viewer context from global media flow to current-post media list
- horizontal swipe within current post only
- lightweight segmented white indicator
- viewer back behavior returning to post detail
- minimal related doc updates

## Product intent
- In-post viewer and photo-flow viewer should feel consistent.
- In-post viewer belongs to the current post context.
- It should swipe only through media in the current post.
- It should keep the segmented white indicator at the bottom.
- It should not show global bottom navigation or photo-module secondary top navigation.

## Latest viewer comment interaction rule
- Comment preview is closed by default.
- Tapping the comment bubble opens the preview layer.
- Tapping the comment bubble again closes the preview layer.
- Only tapping a preview comment opens the full comment panel and highlights that comment.

## Do not do in this stage
- no real backend
- no Room / Retrofit / MediaStore
- no real original download
- no real post navigation
- no full comment system
- no final thumbnail-strip implementation for the segment indicator
- no large performance rewrite

## Done when
- Tapping media in post detail opens in-post viewer
- In-post viewer starts from the tapped media
- Swiping changes media only within the current post
- Segmented indicator is visible and lightweight
- Back returns to post detail
- App builds and runs
- Docs are minimally synchronized
