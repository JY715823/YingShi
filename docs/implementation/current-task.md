# Current Task - Stage 5.1 Comment Foundation v1

## Goal
Build the foundation of the comment system and connect fake post/media comments to the existing post detail and viewer shells.

## Scope
This stage covers:
- comment UI models
- fake comment repository
- post comments by postId
- media comments by mediaId
- post detail comment section using fake post comments
- viewer media comment preview using fake media comments
- viewer media comment detail panel using fake media comments
- highlighted comment placeholder when opened from preview
- comment input placeholders
- minor in-post viewer layout fix for segment indicator and overlay buttons
- minimal related doc updates

## Product intent
- Post comments and media comments are two separate flows.
- Post comments belong only to posts.
- Media comments belong only to media.
- Media comments are shared by the same media across different post contexts.
- Viewer comment preview is closed by default.
- Tapping the comment bubble toggles the preview layer open/closed.
- Tapping a preview comment opens the full media comment panel and highlights that comment.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no real pagination
- no comment edit / delete / select / copy
- no reply / nested reply / quote
- no notification system
- no rich text editor

## Done when
- Post detail shows fake post comments only
- Viewer shows fake media comments only
- Post and media comment flows do not mix
- Preview comment tap opens detail panel and highlights the selected comment
- In-post viewer segment indicator is moved to the bottom and no longer overlaps action buttons
- App builds and runs
- Docs are minimally synchronized
