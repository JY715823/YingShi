# Current Task - Stage 6.2 Media Management Page v1

## Goal
Build the first media management page under Gear Edit.

## Scope
This stage covers:
- media management entry from Gear Edit
- independent media management page
- two-column media grid
- cover marker
- delete mode shell
- sort mode shell
- set-cover local behavior
- add-media placeholder
- edit-media-time placeholder
- minimal related doc updates

## Product intent
- Media management is part of Gear Edit.
- It manages media inside the current post.
- This stage builds page structure and local state only.
- Formal delete semantics will be implemented later.
- Cover changes should write back locally so post detail and album card can reflect the new cover.
- Delete / sort / add-media / edit-time first establish stable mode shells and placeholders.

## Do not do in this stage
- no real MediaStore
- no real system picker
- no formal directory delete / system delete
- no recycle bin integration
- no empty-post protection
- no backend / Room / Retrofit
- no large unrelated refactor

## Done when
- Gear Edit opens media management
- Media management page uses two-column grid
- Delete mode can select media
- Sort mode shell exists
- Set cover works locally
- Add media and edit time entries exist as placeholders
- App builds and runs
- Docs are minimally synchronized
