# Current Task: Trash List Detail Preview Polish

## Background

Delete semantics are now confirmed before mutation. This pass improves the trash surfaces so deleted content remains visible, readable, and clearly separated by source and delete type.

## Goals

1. Deleted App media and posts should appear in trash lists without requiring app restart or repeated navigation.
2. Trash rows should clearly distinguish post deletion, remove-from-post records, and global media deletion records.
3. Trash detail should show media previews / video covers, delete time, delete type, source position, and related post / album context.
4. Trash media previews should reuse the existing preview / cover loading path whenever a media source is available.
5. Existing restore / permanent-delete wording from the previous pass should stay clear and unchanged.

## Scope

- Android fake and REAL trash list rows.
- Android fake and REAL trash detail preview / metadata surfaces.
- Trash snapshot media-source propagation for stable preview / cover rendering.
- No restore loop, batch restore, physical `local-storage` deletion, upload, pagination, cache, or playback-control changes.
- No Server changes.

## Acceptance

1. Trash list updates when delete mutations write records.
2. Trash rows show distinct type and source language.
3. Trash detail shows media preview / cover, delete time, type, source, and related post / album context where available.
4. Preview / cover loading remains compatible with backend `?variant=preview` / `?variant=cover`.
5. Android `assembleDebug` passes.
