# Current Task: Trash Media Grid And Viewer Refinement

## Background

Trash category actions are now menu-based. This pass refines the media-oriented trash categories so media deletion and media removal read like a media grid, while post deletion keeps its existing list/detail shape.

## Goals

1. Replace the long restore-current-category label with a compact icon action.
2. Render `媒体删除` as a media grid grouped by trash-entry month.
3. Render `媒体移除` as a media grid with only the source post title under each item.
4. Show days-in-trash on media grid cards, turning red after 25 days.
5. Show video play marker and duration when media metadata is available.
6. Provide a dedicated trash media viewer with top-right restore/delete and bottom-right original-load action.
7. Keep media-deletion viewer free of related-post entry, and keep media-removal viewer free of normal photo-flow Viewer complexity.

## Scope

- Android fake and REAL trash list rendering for media categories.
- Android fake and REAL trash media detail/viewer surfaces.
- Minimal Server trash DTO metadata extension for source media type, size, aspect ratio, duration, and mime type.

## Non Goals

- No post-deletion grid redesign.
- No upload center, photo-feed pagination, preview cache, or normal Viewer playback changes.
- No broad Server trash purge behavior changes.

## Acceptance

1. Trash restore-current-category action is icon-sized instead of long text.
2. `媒体删除` shows a media grid grouped by deleted month.
3. `媒体移除` shows a media grid, and each cell only shows the source post title below it.
4. Media grid cells show days in trash, with days above 25 highlighted red.
5. Videos show play marker and duration when metadata is available.
6. Media trash viewer shows restore/delete at top right and original-load at bottom right.
7. Android `assembleDebug` passes; Server tests pass when Server changed.
