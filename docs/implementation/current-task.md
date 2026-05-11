# Current Task: Delete Semantics Confirmation Polish

## Background

Delete actions currently exist in photo feed, Viewer, post media management, system media, and trash. This pass focuses on making the user-facing semantics explicit and adding clear confirmation copy without changing restore, trash detail layout, or physical file deletion behavior.

## Goals

1. Photo feed App-media delete is presented as global App media delete into App trash.
2. Viewer App-media delete uses the same global App media delete semantics.
3. Post media management clearly separates current-post relation removal from global media delete.
4. System media trash actions clearly describe Android system album trash semantics and stay separate from App trash.
5. Trash permanent-delete wording is unified as a future physical cleanup path while this pass keeps the existing pending-cleanup behavior.

## Scope

- Android confirmation dialogs and action copy.
- Existing fake and REAL delete paths.
- No recovery, batch restore, trash detail redesign, upload, pagination, cache, or playback-control changes.
- No Server changes.

## Acceptance

1. Photo feed and Viewer delete App media with clear confirmation and trash semantics.
2. Post media management makes remove-from-post and global delete readable before mutation.
3. System media delete / trash copy makes clear it is a system album operation.
4. Trash permanent delete copy explains the future physical `local-storage` cleanup semantics without implementing file removal.
5. Android `assembleDebug` passes.
