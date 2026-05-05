# Current Task: Stage 12.8 - Shared Library, Time Fields, Local Storage Layout

## Background

YingShi is now treated as a private two-person shared app, not a multi-space product. The old public-facing `spaceId` concept is removed from the contract and replaced by `libraryId`, which represents the one shared library used by both seed users.

Media and posts also need two separate time ideas:

- intrinsic time: when the media was captured, or when the post/memory happened
- display time: where the item appears in the app timeline

## Android Scope

1. Consume auth `libraryId` / `libraryDisplayName`.
2. Send upload time metadata from system-media imports when available.
3. Receive media time metadata: `capturedAtMillis`, `importedAtMillis`, `displayTimeSource`.
4. Receive and forward post event time metadata: `eventStartedAtMillis`, `eventEndedAtMillis`, `displayTimeSource`.
5. Keep upload/import, transfer center, original loading, comments, posts, trash, FAKE, and REAL behavior intact.

## Local Storage Contract

Server-managed files now use:

```text
local-storage/
  originals/yyyy/MM/{mediaId}.{ext}
  previews/yyyy/MM/{mediaId}-720.jpg
  test/photos|long|videos/...
  tmp/uploads/...
  videos/posters/...
```

Android never assumes this path directly; it uses returned URLs from the server.

## Acceptance

1. Upload/import sends stable media time metadata.
2. App DTOs tolerate and preserve new media/post time fields.
3. Import-only media with empty `postIds` remains valid in photo feed and Viewer.
4. `assembleDebug` passes.
