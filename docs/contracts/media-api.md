# Media API Draft

## Purpose
Serve app-content media metadata for the global photo stream and related media detail views.

## Endpoints

### `GET /v1/media/feed`
- use case: global app-content media stream
- query draft:
  - `page`
  - `pageSize`
  - `cursor`
  - `albumId` optional
  - `updatedAfter` optional

Response draft:

```json
{
  "requestId": "req_media_feed",
  "data": [
    {
      "mediaId": "media_001",
      "mediaType": "image",
      "previewUrl": "https://placeholder/media_001_preview.jpg",
      "originalUrl": "https://placeholder/media_001_original.jpg",
      "videoUrl": null,
      "coverUrl": null,
      "width": 1440,
      "height": 1920,
      "aspectRatio": 0.75,
      "displayTimeMillis": 1777412800000,
      "commentCount": 4,
      "postIds": ["post_001"]
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

### `GET /v1/media/{mediaId}`
- use case: single media detail
- path:
  - `mediaId`

Response draft:

```json
{
  "requestId": "req_media_detail",
  "data": {
    "mediaId": "media_001",
    "mediaType": "video",
    "previewUrl": "https://placeholder/media_001_cover.jpg",
    "originalUrl": null,
    "videoUrl": "https://placeholder/media_001.mp4",
    "coverUrl": "https://placeholder/media_001_cover.jpg",
    "width": 1920,
    "height": 1080,
    "aspectRatio": 1.7778,
    "displayTimeMillis": 1777412800000,
    "commentCount": 2,
    "postIds": ["post_001", "post_002"]
  }
}
```

## Field Notes
- `mediaType`: `image` or `video`
- `previewUrl`: preview or thumbnail resource
- `originalUrl`: original image resource, null for video-only items
- `videoUrl`: actual playable asset, null for images
- `coverUrl`: optional video cover

## Pagination Placeholder
- `page/pageSize` and `cursor` are both kept as placeholders in Stage 11.1

## Error Code Placeholders
- `MEDIA_NOT_FOUND`
- `INVALID_MEDIA_TYPE`
- `UNAUTHORIZED`
- `NOT_IMPLEMENTED`

## Stage 11.1 Draft-Only Notes
- preview/original/video cache state is not part of the remote contract yet
- real CDN, signed URL, and transformed thumbnail strategy remain follow-up work
- system-media import should only enter app-content media after upload-confirm success
