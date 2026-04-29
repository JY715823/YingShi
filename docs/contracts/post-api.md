# Post API Draft

## Purpose
Serve albums, post lists, and post detail payloads for album browsing and post-detail screens.

## Endpoints

### `GET /v1/albums`
- use case: album directory
- query draft:
  - `page`
  - `pageSize`
  - `cursor`

Response draft:

```json
{
  "requestId": "req_albums",
  "data": [
    {
      "albumId": "album_001",
      "title": "Spring Window",
      "subtitle": "Light and slow daily fragments",
      "coverMediaId": "media_001",
      "postCount": 8
    }
  ],
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

### `GET /v1/posts`
- use case: post list under album or general feed slice
- query draft:
  - `albumId` optional
  - `page`
  - `pageSize`
  - `cursor`

### `GET /v1/posts/{postId}`
- use case: post detail

Response draft:

```json
{
  "requestId": "req_post_detail",
  "data": {
    "postId": "post_001",
    "title": "Night Walk",
    "summary": "A quiet walk home",
    "contributorLabel": "You and Me",
    "displayTimeMillis": 1777412800000,
    "albumIds": ["album_001"],
    "coverMediaId": "media_001",
    "mediaItems": [
      {
        "mediaId": "media_001",
        "mediaType": "image",
        "previewUrl": "https://placeholder/media_001_preview.jpg",
        "width": 1440,
        "height": 1920,
        "aspectRatio": 0.75,
        "commentCount": 4,
        "displayTimeMillis": 1777412800000
      }
    ]
  }
}
```

## Field Notes
- post media can reuse the media DTO shape or a narrowed embedded variant
- `coverMediaId` is preferred over duplicating full cover metadata on every endpoint

## Error Code Placeholders
- `POST_NOT_FOUND`
- `ALBUM_NOT_FOUND`
- `VALIDATION_ERROR`
- `NOT_IMPLEMENTED`

## Stage 11.1 Draft-Only Notes
- post create/update/delete are not locked yet
- final collaboration/member fields remain placeholders
