# API Overview

Updated: 2026-05-25

## Status

- this document describes the Android-facing backend contract that is already usable in `REAL` mode
- auth, photos, posts, comments, notifications, uploads, and trash are all connected enough for daily Android integration
- the remaining gaps are now mostly large offline sync polish, cache hydration details, and later product redesign decisions, not missing core backend APIs

## Common Rules

- success envelope: `{ requestId, data, page? }`
- error envelope: `{ requestId, error }`
- JSON fields use `camelCase`
- identifiers are string IDs such as `mediaId`, `postId`, `commentId`, and `notificationId`
- time fields use epoch millis
- protected endpoints require:

```http
Authorization: Bearer <accessToken>
```

## Current Implemented API Areas

### Auth

- `POST /api/auth/login/challenge`
- `POST /api/auth/login/challenge/resend`
- `POST /api/auth/login/verify`
- `POST /api/auth/refresh-token`
- `GET /api/auth/me`
- `PATCH /api/auth/me/profile`
- `POST /api/auth/logout`
- `POST /api/auth/me/avatar`
- `GET /api/auth/avatar/{userId}`

### Health

- `GET /api/health`

### Albums / Posts / Media

- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}?variant=original|preview|cover`
- `DELETE /api/media/{mediaId}`

### Comments

- `GET /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/posts/{postId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

### Notifications

- `GET /api/notifications`
- `GET /api/notifications/{notificationId}`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`

### Trash

- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`

### Uploads

- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `POST /api/uploads/{uploadId}/confirm`
- `POST /api/uploads/{uploadId}/cancel`
- `GET /api/ledger/snapshot`
- `PUT /api/ledger/snapshot`
- `GET /api/chat/imported/snapshot`
- `PUT /api/chat/imported/snapshot`

## Android Real Repository Coverage

- `AuthRepository`
- `AlbumRepository`
- `MediaRepository`
- `PostRepository`
- `CommentRepository`
- `NotificationRepository`
- `TrashRepository`
- `UploadRepository`
- `LedgerRepository` via snapshot sync bridge
- `ImportedChatRepository` via snapshot sync bridge

## Current Gaps

- ledger snapshot sync is now backed by `/api/ledger/snapshot`, and imported chat snapshot sync is backed by `/api/chat/imported/snapshot`
- direct object-storage access, transcoding/CDN polish, and large offline sync are outside the current stage
