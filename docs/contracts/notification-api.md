# Notification API Contract

Updated: 2026-05-25

## Status

- the backend notification API is already available in `YingShi-Server`
- Android notification-center list, detail, read, and mark-all-read are now wired in `REAL` mode
- base path: `/api/notifications`
- all endpoints require bearer auth

## Current Notification Sources

The backend currently materializes a merged notification feed from:

- comments
- post content updates
- trash state changes
- upload completion or cancellation

Current backend notification `type` values:

- `comment`
- `comment_edit`
- `comment_delete`
- `content_update`
- `delete_restore`
- `system`

## Notification DTO

```json
{
  "notificationId": "comment:comment_001",
  "type": "comment",
  "title": "Demo B commented on a post",
  "body": "Looks great",
  "createdAtMillis": 1777416400000,
  "isRead": false,
  "targetSummary": "Night Walk",
  "targetType": "POST",
  "postId": "post_001",
  "mediaId": null,
  "trashItemId": null
}
```

## Comment Variants

The backend now distinguishes:

- `comment` for new comments by another member
- `comment_edit` when another member edits your comment
- `comment_delete` when another member deletes your comment

## Endpoints

`GET /api/notifications`

- optional query param: `limit`
- returns `List<NotificationDto>`

`GET /api/notifications/{notificationId}`

- returns one `NotificationDto`

`POST /api/notifications/{notificationId}/read`

- returns the same notification with `isRead = true`

`POST /api/notifications/read-all`

Response `data`:

```json
{
  "success": true,
  "affectedCount": 12
}
```

## Android Current Behavior

- bell unread count is derived from the real notification feed in `REAL` mode
- tapping a notification can route into post detail, trash, transfer center, or notification detail fallback depending on the payload
- `FAKE` mode still keeps local seed notifications for shell/demo use
