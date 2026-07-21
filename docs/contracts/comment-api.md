# Comment API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- post comments and media comments are separate streams
- bearer auth required for all endpoints
- base paths:
  - `/api/small-albums/{smallAlbumId}/comments`
  - `/api/media/{mediaId}/comments`
  - `/api/comments/{commentId}`
- default ordering is newest first
- default pagination is `page=1`, `size=10`

## Comment DTO

```json
{
  "commentId": "comment_post_001",
  "targetType": "SMALL_ALBUM",
  "smallAlbumId": "post_001",
  "mediaId": null,
  "authorId": "user_demo_a",
  "authorName": "小雨",
  "content": "今天阳光很好，散步回来心情也慢下来了�?,
  "createdAtMillis": 1777412800000,
  "updatedAtMillis": 1777412860000,
  "isDeleted": false
}
```

Notes:
- `targetType` values are uppercase: `SMALL_ALBUM`, `MEDIA`
- `smallAlbumId` is only set for small album comments
- `mediaId` is only set for media comments
- after soft delete, `isDeleted=true` and `content` may be `null`
- comment create still records the real author
- comment update/delete is currently allowed for any authenticated member inside the same `libraryId`
- TODO: when a non-author edits/deletes a comment, notify the original author in a later stage

## Endpoints

### `GET /api/small-albums/{smallAlbumId}/comments`
### `GET /api/media/{mediaId}/comments`

Response data:

```json
{
  "comments": [
    {
      "commentId": "comment_post_001",
      "targetType": "SMALL_ALBUM",
      "smallAlbumId": "post_001",
      "mediaId": null,
      "authorId": "user_demo_a",
      "authorName": "小雨",
      "content": "今天阳光很好，散步回来心情也慢下来了�?,
      "createdAtMillis": 1777412800000,
      "updatedAtMillis": 1777412860000,
      "isDeleted": false
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "hasMore": false
}
```

### `POST /api/small-albums/{smallAlbumId}/comments`
### `POST /api/media/{mediaId}/comments`

Request:

```json
{
  "content": "这张照片让我想起那天的风�?
}
```

Response:
- returns one `CommentDto`

### `PATCH /api/comments/{commentId}`

Request:

```json
{
  "content": "补一句，这个角度也很适合放进日常相册�?
}
```

Response:
- returns one `CommentDto`

### `DELETE /api/comments/{commentId}`

Response:
- returns one soft-deleted `CommentDto`

## Stage 12.2 Refresh Notes
- 小相册评论新增 / 编辑 / 删除后，Android 至少要刷新当前小相册评论线程；如果页面上展示了小相册评论计数，也要同步刷新对应计数来源
- 媒体评论新增 / 编辑 / 删除后，Android 需要刷新当前评论线程、当前媒体气泡与评论计数，以及同一 `mediaId` 在照片流 / 小相册详情 / 媒体管理中的可见计数
- 评论 mutation 失败时，接口没有额外补偿字段；Android 应保留原列表并显示中文错误提示或重试入口�?

## Error Codes
- `COMMENT_NOT_FOUND`
- `COMMENT_TARGET_NOT_FOUND`
- `COMMENT_SCOPE_MISMATCH`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
