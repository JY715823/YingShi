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
