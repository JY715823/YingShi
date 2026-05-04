# Trash API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/trash`
- bearer auth required for all endpoints
- list pagination defaults to `page=1`, `size=10`
- list sort order is newest `deletedAtMillis` first
- current backend has no direct purge endpoint

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "postDeleted",
  "state": "inTrash",
  "sourcePostId": "post_001",
  "sourceMediaId": null,
  "title": "春日散步",
  "previewInfo": "帖子已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": ["post_001"],
  "relatedMediaIds": ["media_001", "media_002"]
}
```

Item types:
- `postDeleted`
- `mediaRemoved`
- `mediaSystemDeleted`

State values:
- `inTrash`
- `pendingCleanup`
- `restored`

## Endpoints

### `GET /api/trash/items`

Query:
- `itemType` optional
- `page`
- `size`

Response data:

```json
{
  "items": [
    {
      "trashItemId": "trash_001",
      "itemType": "postDeleted",
      "state": "inTrash",
      "sourcePostId": "post_001",
      "sourceMediaId": null,
      "title": "春日散步",
      "previewInfo": "帖子已移入回收站",
      "deletedAtMillis": 1777412800000,
      "relatedPostIds": ["post_001"],
      "relatedMediaIds": ["media_001", "media_002"]
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "hasMore": false
}
```

### `GET /api/trash/items/{trashItemId}`

Response data:

```json
{
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
    "state": "inTrash",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
    "title": "春日散步",
    "previewInfo": "帖子已移入回收站",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  },
  "canRestore": true,
  "canMoveOutOfTrash": true,
  "pendingCleanup": null
}
```

### `POST /api/trash/items/{trashItemId}/restore`

Request:
- no request body

Response:
- returns one `TrashItemDto`

### `POST /api/trash/items/{trashItemId}/remove`

Response data:

```json
{
  "trashItemId": "trash_001",
  "removedAtMillis": 1777412900000,
  "undoDeadlineMillis": 1777499300000,
  "item": {
    "trashItemId": "trash_001",
    "itemType": "postDeleted",
    "state": "pendingCleanup",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
    "title": "春日散步",
    "previewInfo": "帖子待彻底移出回收站",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  }
}
```

Notes:
- Android REAL mode maps `postDeleted` / `mediaRemoved` / `mediaSystemDeleted` directly from backend
- `remove` means move to `pendingCleanup`, and `undo-remove` is the 24h撤销入口
- Post-12.7 targeted fix: Android places the `24h 可撤销` entry in the trash category row and renders deleted-state detail media from `sourceMediaId` / `relatedMediaIds` through the media file endpoint.

### `POST /api/trash/items/{trashItemId}/undo-remove`

Request:
- no request body

Response:
- returns one `TrashItemDto`

### `GET /api/trash/pending-cleanup`

Response:
- returns `List<PendingCleanupDto>`

## Stage 12.2 Refresh Notes
- `restore` 成功后，Android 需要把恢复结果同步回照片流、相册页、帖子详情入口和回收站列表。
- `remove` 或 `undo-remove` 成功后，Android 需要刷新回收站列表本身，并同步刷新仍受该条目影响的帖子 / 媒体列表。
- 回收站接口没有额外下发“推荐刷新哪些页面”的字段；当前约定由 Android 依据 `itemType`、`sourcePostId`、`sourceMediaId` 和 `related*Ids` 决定刷新范围。

## Error Codes
- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `AUTH_UNAUTHORIZED`
## Stage 12.3 约定补充

- 系统删进入 `MEDIA_SYSTEM_DELETED`，目录删进入 `MEDIA_REMOVED`。
- 恢复后相关照片流、帖子详情、Gear Edit、系统媒体目标列表都要重新出现。
- 24h 撤销入口继续以回收站待清理区为准，不要求长期占据底部提示位。
