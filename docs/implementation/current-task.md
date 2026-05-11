# Current Task: Photo Feed Paging And Positioning Wrap-Up

## Background

The photo feed already supports cursor paging in REAL mode and bottom incremental loading. This wrap-up pass focuses on making the paging stage feel stable in daily use: refresh should preserve position, explicit target jumps should still work across pages, and loading states should not loop or feel stuck.

## Goals

1. Preserve the current photo-feed scroll position during normal refreshes.
2. Keep Viewer return and Transfer Center return from forcing unwanted jumps.
3. Continue cross-page target loading for explicit jumps such as upload/import success or Transfer Center "view".
4. Stop cross-page loading safely when the target cannot be found.
5. Add basic bottom loading states: loading, retry after failure, and no-more-data.
6. Keep FAKE and REAL modes usable without changing Viewer playback, upload flow, trash, or post detail behavior.

## Scope

- Android photo feed scroll state persistence.
- Android REAL photo feed refresh behavior.
- Android photo feed cross-page target handling.
- Android bottom paging status and retry UI.
- System media Viewer return position parity.

## Non Goals

- No database-level feed paging change.
- No advanced refresh animation or full loading-state redesign.
- No Viewer playback control changes.
- No upload center, trash detail, post detail, or backend API changes.

## Acceptance

1. Normal photo-feed refresh does not jump to top without reason.
2. Returning from Viewer or Transfer Center generally restores the previous feed position.
3. Upload/import success and Transfer Center "view" still locate the target across loaded pages.
4. Missing targets do not cause infinite loading or random jumps.
5. First-page loading, bottom loading, retry after load-more failure, and no-more-data states are visible and stable.
6. Time grouping, density switching, multi-select, video preview, and time scrubber keep working.
7. Android `assembleDebug` passes.
