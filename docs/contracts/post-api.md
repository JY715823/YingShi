# Post API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/posts`
- bearer auth required for all endpoints
- current backend has no `GET /api/posts` list endpoint
- create, update, cover update, media order update, and add-media all return the same `PostDetailDto`
- Android REAL Gear Edit uses `PATCH /api/posts/{postId}`
- Android REAL media management uses `PATCH /cover`, `PATCH /media-order`, and `DELETE /api/posts/{postId}/media/{mediaId}`

## Post Detail DTO

```json
{
  "postId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"],
  "coverMediaId": "media_001",
  "mediaCount": 3,
  "mediaItems": [
    {
      "sortOrder": 0,
      "isCover": true,
      "media": {
        "mediaId": "media_001",
        "mediaType": "image",
        "type": "image",
        "url": "/api/media/files/media_001",
        "mediaUrl": "/api/media/files/media_001",
        "previewUrl": "/api/media/files/media_001",
        "thumbnailUrl": "/api/media/files/media_001",
        "originalUrl": "/api/media/files/media_001",
        "videoUrl": null,
        "coverUrl": null,
        "mimeType": "image/jpeg",
        "sizeBytes": 3145728,
        "width": 1440,
        "height": 1920,
        "aspectRatio": 0.75,
        "durationMillis": null,
        "displayTimeMillis": 1777412800000,
        "postIds": ["post_001"]
      }
    }
  ]
}
```

Android REAL compatibility notes for nested `media`:
- the same `mediaType` or `type`, `url` or `mediaUrl`, and `previewUrl` or `thumbnailUrl` compatibility rules from the media contract apply here as well
- Android uses these nested media URLs directly for post detail media cards and Gear Edit media management thumbnails
- for video items, Android prefers `thumbnailUrl`, `previewUrl`, or `coverUrl` as the poster and does not treat `videoUrl` as a normal image URL
- in-post Viewer uses the same app-content Viewer image rules as the photo-flow Viewer: preview first, original only after the original action, with state isolated by `mediaId`
- in-post Viewer video now resolves the playable source with `videoUrl -> mediaUrl -> originalUrl`, and all loading, error, progress, and playing state remains isolated by `mediaId`
- Stage 12.7-Hotfix: post detail single-original and `加载全帖原图` actions only process image media with a meaningful original candidate. Videos are skipped by the original-image action and keep using normal video playback source resolution.

## Endpoints

### `GET /api/posts/{postId}`

Response:
- returns one `PostDetailDto`

### `POST /api/posts`

Request:

```json
{
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"],
  "initialMediaIds": ["media_001", "media_002"],
  "coverMediaId": "media_001"
}
```

Response:
- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}`

Request:

```json
{
  "title": "Night Walk Updated",
  "summary": "A quiet walk home with one more note",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001", "album_002"]
}
```

Response:
- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}/cover`

Request:

```json
{
  "coverMediaId": "media_002"
}
```

Response:
- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}/media-order`

Request:

```json
{
  "orderedMediaIds": ["media_002", "media_001", "media_003"]
}
```

Response:
- returns one `PostDetailDto`

### `POST /api/posts/{postId}/media`

Request:

```json
{
  "mediaIds": ["media_uploaded_001", "media_uploaded_002"],
  "coverMediaId": "media_uploaded_001"
}
```

Response:
- returns one `PostDetailDto`

### `DELETE /api/posts/{postId}`

Behavior:
- soft deletes the post
- keeps relations and comments restorable
- creates one trash item with `itemType = postDeleted`

Response:
- returns one `TrashItemDto`

## Stage 12.2 Refresh Notes
- `PATCH /api/posts/{postId}` 成功后，Android 需要刷新相册页帖子卡、帖子详情、照片流入口文案，以及系统媒体“加入已有帖子”目标列表。
- `POST /api/posts`、`POST /api/posts/{postId}/media`、`PATCH /api/posts/{postId}/media-order`、`PATCH /api/posts/{postId}/cover` 成功后，Android 需要至少刷新照片流、相册页、帖子详情媒体区、Gear Edit 媒体管理。
- `DELETE /api/posts/{postId}` 成功后，Android 需要同时刷新相册页、照片流、回收站，以及所有仍引用该 `postId` 的详情入口。
- `DELETE /api/posts/{postId}/media/{mediaId}` 成功后，Android 需要把同一 `mediaId` 从帖子详情、Viewer、媒体管理和照片流中移除；若该删除导致封面、顺序或帖子可见性变化，也要重拉帖子详情。

### `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`

Behavior:
- `directory`: remove only this post-media relation and create `mediaRemoved`
- `system`: system delete the media globally and create `mediaSystemDeleted`
- current backend allows the post to remain with zero media after deletion; Android should refresh detail, feed, album, and trash state after the mutation

Response:
- returns one `TrashItemDto`

## Error Codes
- `POST_NOT_FOUND`
- `POST_ALREADY_DELETED`
- `POST_MEDIA_ORDER_INVALID`
- `POST_COVER_INVALID`
- `ALBUM_ASSIGNMENT_INVALID`
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`

## Stage 12.6 Add-Post Notes

- Android 新增帖子表单目前直接消费 `POST /api/posts`，字段收口为：
  - `title`
  - `summary`
  - `displayTimeMillis`
  - `albumIds`
  - `initialMediaIds`
  - `coverMediaId`
- 当系统媒体走 `发成新帖子` 时，客户端会先上传媒体，再调用 `POST /api/posts`；只有这一步成功后才视为最终成功。
- 当系统媒体走 `加入已有帖子` 时，客户端会先上传媒体，再调用 `POST /api/posts/{postId}/media`；只有追加成功后才刷新主链路。
- 如果后端创建帖子或追加媒体失败，客户端会把这次操作标记为失败并提供重试，不会把未挂帖媒体当成主照片流成功数据。
