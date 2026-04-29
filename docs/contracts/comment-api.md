# Comment API Draft

## Purpose
Support separate post comments and media comments without mixing their target scopes.

## Endpoints

### `GET /v1/posts/{postId}/comments`
- use case: post comment list
- query draft:
  - `page`
  - `pageSize`
  - `cursor`

### `GET /v1/media/{mediaId}/comments`
- use case: media comment list
- query draft:
  - `page`
  - `pageSize`
  - `cursor`

### `POST /v1/comments`
- use case: create comment

Request draft:

```json
{
  "targetType": "post",
  "targetId": "post_001",
  "content": "A placeholder comment"
}
```

### `PATCH /v1/comments/{commentId}`
- use case: edit comment

### `DELETE /v1/comments/{commentId}`
- use case: delete comment

Response item draft:

```json
{
  "commentId": "comment_001",
  "targetType": "media",
  "targetId": "media_001",
  "authorName": "Me",
  "content": "A placeholder comment",
  "createdAtMillis": 1777412800000,
  "updatedAtMillis": 1777412860000,
  "isDeleted": false
}
```

## Field Notes
- `targetType`: `post` or `media`
- `targetId` must match `targetType`
- comments remain flat in Stage 11.1; thread/reply depth is not locked

## Error Code Placeholders
- `COMMENT_NOT_FOUND`
- `INVALID_TARGET`
- `CONTENT_EMPTY`
- `CONTENT_TOO_LONG`
- `NOT_IMPLEMENTED`

## Pagination Placeholder
- latest-first order is preferred
- server-side sort options are not finalized

## Stage 11.1 Draft-Only Notes
- reply threading
- moderation state
- rich text / attachment support
