# Current Task: Trash Lists And Viewer Refinement

## Background

The trash module already has three categories and dedicated media/post viewing routes. This pass refines the list and viewer behavior so trash feels closer to the main photo flow and post detail surfaces while keeping trash-specific restore/delete semantics.

## Goals

1. Make media-deleted and media-removed trash month headers visually closer to the photo flow.
2. Use photo-flow-level preview request sizes for trash media grids so deleted-media thumbnails are not blurry.
3. Add multi-select to all three trash categories with selected restore and selected permanent delete actions.
4. Keep the category menu and existing restore-current-category / clear-current-category actions intact.
5. Keep media-deleted and media-removed viewers single-item only, without swipe/zoom behavior.
6. Move trash media viewer top actions away from the system status bar.
7. Show existing media comments in trash media viewers.
8. Make deleted-post detail closer to the normal post detail layout: title, summary, media area, and post comments, without normal edit/settings actions.

## Scope

- Android fake and REAL trash category lists.
- Android fake and REAL trash media viewer overlays.
- Android fake and REAL trash deleted-post detail surfaces.
- Trash-only preview sizing and selection UI.

## Non Goals

- No upload center, photo-feed pagination, cache-management, normal Viewer playback, or backend-unrelated API changes.
- No broad trash model or permanent-delete server behavior rewrite.
- No density switch for trash media grids.

## Acceptance

1. `媒体删除` / `媒体移除` month headers are prominent and closer to photo-flow grouping.
2. `媒体删除` grid thumbnails use the same clear preview tier as a 3-column photo flow.
3. All three categories support multi-select and selected restore/delete.
4. Media trash viewers remain single-item viewers and do not add swipe/zoom.
5. Media trash viewer top buttons do not overlap the status bar.
6. Media trash viewers show existing media comments.
7. Deleted-post detail shows title, summary, media area, and post comments without normal edit/settings actions.
8. Android `assembleDebug` passes; Server is unchanged unless explicitly required.
