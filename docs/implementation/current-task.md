# Current Task: Trash Restore And Permanent Delete Closure

## Background

Trash list and detail previews are now readable. This pass closes the actual restore and permanent-delete loop across Android and Server.

## Goals

1. Restore single trash items and refresh active content after success.
2. Restore a whole current trash category, keeping failed items and showing a clear partial-result message.
3. After restoring App media, refresh the photo feed and target the restored media or its time group.
4. Permanently delete trash items with an explicit irreversible confirmation.
5. For globally deleted App media, Server permanently deletes the media record and the media-owned `local-storage` original / `preview-v2` / `cover-v1` files.

## Scope

- Android fake and REAL trash list/detail restore and permanent-delete actions.
- Android restore-to-photo-feed target propagation.
- Server trash `purge` endpoint and safe local-storage cleanup for `mediaSystemDeleted` items.
- Trash API contract documentation.

## Non Goals

- No upload center, pagination-core, preview cache, Viewer playback, or post-detail layout changes.
- No App physical deletion for Android system gallery files.
- No full background cleanup scheduler.

## Acceptance

1. Single restore removes the trash item and refreshes active content.
2. Batch restore succeeds per item, preserves failures, and reports partial success.
3. Restored media can jump back to the photo feed target.
4. Permanent delete requires confirmation and cannot be restored after success.
5. Server purges owned media files without deleting unrelated files or directories.
6. Android `assembleDebug` passes and Server tests pass.
