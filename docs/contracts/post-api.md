# Post API Draft

## Status
- Stage 11.4 draft only
- no live backend required
- current app still defaults to fake local post data

## Purpose
Define post list, post detail, post create, basic info update, cover update, and media-order update contracts for future backend integration.

## Endpoints

### `GET /v1/posts`
- use case: global post list
- auth: required
- query:
  - `page`
  - `size`

Response draft:

```json
{
  "requestId": "req_posts",
  "data": [
    {
      "postId": "post_001",
      "title": "Night Walk",
      "summary": "A quiet walk home",
      "contributorLabel": "You and Me",
      "displayTimeMillis": 1777412800000,
      "albumIds": ["album_001"],
      "coverMediaId": "media_001",
      "mediaCount": 6
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

### `GET /v1/posts/{postId}`
- use case: post detail
- auth: required

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
        "originalUrl": null,
        "videoUrl": null,
        "width": 1440,
        "height": 1920,
        "aspectRatio": 0.75,
        "displayTimeMillis": 1777412800000,
        "commentCount": 4,
        "isCover": true,
        "videoDurationMillis": null
      }
    ]
  }
}
```

### `POST /v1/posts`
- use case: create post shell
- auth: required

Request draft:

```json
{
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"],
  "initialMediaIds": ["media_001", "media_002"]
}
```

### `PATCH /v1/posts/{postId}`
- use case: update post basic info
- auth: required

Request draft:

```json
{
  "title": "Night Walk Updated",
  "summary": "A quiet walk home with one more note",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"]
}
```

### `PATCH /v1/posts/{postId}/cover`
- use case: set post cover media
- auth: required

Request draft:

```json
{
  "coverMediaId": "media_002"
}
```

### `PATCH /v1/posts/{postId}/media-order`
- use case: update media order inside one post
- auth: required

Request draft:

```json
{
  "orderedMediaIds": ["media_002", "media_001", "media_003"]
}
```

## Field Notes
- post list and post detail are different DTO shapes
- `coverMediaId` is preferred over duplicating full cover blocks on summaries
- post detail media order belongs to the post itself
- album membership update now lives in `album-api.md`

## Error Code Placeholders
- `POST_NOT_FOUND`
- `POST_MEDIA_NOT_FOUND`
- `POST_MEDIA_ORDER_INVALID`
- `POST_COVER_INVALID`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
- `NOT_IMPLEMENTED`

## Stage 11.4 Draft-Only Notes
- no post delete contract in this stage
- no collaborator/member mutation in this stage
- no rich post editor payload in this stage
