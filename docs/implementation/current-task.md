# Current Task - Stage 6.1 Gear Edit Basic Info

## Goal
Build the Gear Edit main page and support local editing of post basic information.

## Scope
This stage covers:
- Gear Edit entry from post detail
- independent Gear Edit route / screen
- edit title
- edit description
- basic edit post_display_time
- basic edit album chips / album relation placeholder
- media management entry placeholder
- delete semantics entry placeholder
- local fake repository / ViewModel update
- minimal related doc updates

## Product intent
- Gear Edit enters editing mode directly.
- It is a post-level editing page.
- It should not show global bottom navigation.
- It should not show photo-module secondary navigation.
- This stage edits post metadata only.
- Media management and delete semantics are prepared as entries for later stages.
- Cancel / back should dismiss the local draft without accidental save.
- Save writes back to the local fake repository so post detail can refresh immediately.

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no full media management
- no media deletion
- no media sorting
- no cover setting
- no full post deletion flow
- no recycle bin integration
- no complex date picker
- no real album management

## Done when
- Gear Edit opens from post detail
- Gear Edit is an independent page
- Title / description can be edited locally
- Basic display time and album relation editing or placeholders exist
- Saving updates post detail
- Media management and delete entries exist as placeholders
- App builds and runs
- Docs are minimally synchronized
