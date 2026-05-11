# Current Task: Trash Category Action Bar Refinement

## Background

Trash restore and permanent-delete closure is in place. This pass refines the Android trash category operation area so categories are chosen from a menu, current-category batch operations are obvious, and old pending-cleanup wording no longer appears in the client.

## Goals

1. Replace trash category chips with a compact top action row.
2. Default the trash page to the media-deletion category.
3. Provide a hamburger category menu for media deletion, post deletion, and media removal.
4. Support restoring all items in the current category with partial-failure reporting.
5. Support clearing the current category after an irreversible confirmation.
6. Remove client-facing `24h` undo / pending-cleanup wording.

## Scope

- Android fake and REAL trash list top action area.
- Android trash category names and notification/navigation copy related to trash.
- Android current-category batch restore and current-category permanent purge wiring.

## Non Goals

- No trash grid redesign, post grid redesign, or Viewer refinement.
- No upload center, pagination-core, preview cache, or playback changes.
- No Server API changes in this pass.

## Acceptance

1. Trash opens to `媒体删除` by default and no longer shows chips.
2. The action row has a left hamburger menu, empty middle space, and right-side restore / clear actions.
3. The category menu includes `媒体删除`, `帖子删除`, and `媒体移除`; the selected row is highlighted and checked.
4. Restore current category preserves failures and shows a clear result.
5. Clear current category asks for confirmation and only purges the selected category.
6. Client UI no longer displays `24h 可撤销` or `待清理`.
7. Android `assembleDebug` passes.
