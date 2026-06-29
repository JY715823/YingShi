# Trash API Contract

更新时间：2026-06-25

## 状态

- 已按当前 `YingShi-Server` trash 合同同步。
- Android `REAL` 模式接入列表、详情、恢复、移出回收站、撤销移出、待清理列表和永久删除。
- “从回收站删除”在客户端语义上是 `remove -> pendingCleanup`；只有待清理页或明确二次确认后的 `purge` 才是永久删除。

## 基础规则

- 基础路径：`/api/trash`
- 所有接口都要求 bearer auth
- 列表默认分页：`page=1`、`size=10`
- 回收站列表排序：按 `deletedAtMillis` 倒序
- 待清理列表按 `undoDeadlineMillis` 展示剩余时间，客户端可按最早到期优先排序

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "mediaSystemDeleted",
  "state": "inTrash",
  "sourceSmallAlbumId": null,
  "sourceMediaId": "media_001",
  "commentTargetMediaId": "media_001",
  "title": "海边散步",
  "previewInfo": "媒体已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedSmallAlbumIds": [],
  "relatedMediaIds": ["media_001"],
  "sourceMediaType": "image",
  "sourceMediaWidth": 1440,
  "sourceMediaHeight": 1920,
  "sourceMediaAspectRatio": 0.75,
  "sourceMediaDurationMillis": null,
  "sourceMediaMimeType": "image/jpeg"
}
```

字段说明：

- `itemType`
  - `largeAlbumDeleted`
  - `smallAlbumDeleted`
  - `mediaRemoved`
  - `mediaSystemDeleted`
- `state`
  - `inTrash`
  - `pendingCleanup`
  - `restored`
- `sourceSmallAlbumId`
  - 小相册删除或从小相册移除媒体时用于定位来源小相册。
  - Android 继续兼容旧别名 `sourcePostId`。
- `relatedSmallAlbumIds / relatedMediaIds`
  - Android 用来决定详情页、恢复后刷新范围和相册影响面。
  - Android 继续兼容旧别名 `relatedPostIds`。
- `sourceMedia*`
  - Android 用来在真实回收站详情页渲染图片 / 视频基础信息。

## Pending Cleanup DTO

```json
{
  "trashItemId": "trash_001",
  "removedAtMillis": 1777412900000,
  "undoDeadlineMillis": 1777499300000,
  "item": {
    "trashItemId": "trash_001",
    "itemType": "mediaSystemDeleted",
    "state": "pendingCleanup",
    "sourceSmallAlbumId": null,
    "sourceMediaId": "media_001",
    "commentTargetMediaId": "media_001",
    "title": "海边散步",
    "previewInfo": "媒体待彻底移出回收站",
    "deletedAtMillis": 1777412800000,
    "relatedSmallAlbumIds": [],
    "relatedMediaIds": ["media_001"]
  }
}
```

## 1. `GET /api/trash/items`

查询参数：

- `itemType`：可选，值为四类 `itemType`
- `page`
- `size`

响应 `data`：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "totalElements": 0,
  "hasMore": false
}
```

## 2. `GET /api/trash/items/{trashItemId}`

响应 `data`：

```json
{
  "item": {
    "trashItemId": "trash_001",
    "itemType": "smallAlbumDeleted",
    "state": "inTrash",
    "sourceSmallAlbumId": "post_001",
    "sourceMediaId": null,
    "commentTargetMediaId": null,
    "title": "春日散步",
    "previewInfo": "小相册已移入回收站",
    "deletedAtMillis": 1777412800000,
    "relatedSmallAlbumIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  },
  "canRestore": true,
  "canMoveOutOfTrash": true,
  "pendingCleanup": null
}
```

## 3. `POST /api/trash/items/{trashItemId}/restore`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

语义：

- `largeAlbumDeleted`：恢复大相册本体，以及本次整组进入回收站的小相册
- `smallAlbumDeleted`：恢复小相册、小相册评论和小相册媒体关系
- `mediaRemoved`：恢复小相册内媒体关系
- `mediaSystemDeleted`：恢复媒体本体及相关关系
- 恢复时服务端继续兜底照片流去重；如同 fingerprint / checksum 内容已重新上传，照片流只保留一个展示结果。

## 4. `POST /api/trash/items/{trashItemId}/remove`

- 请求体：无
- 响应：返回 `PendingCleanupDto`

语义：

- 将 `inTrash` 条目移入 `pendingCleanup`
- 设置 `removedAtMillis` 和 24 小时 `undoDeadlineMillis`
- 不执行物理删除

## 5. `POST /api/trash/items/{trashItemId}/undo-remove`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

语义：

- 把 `pendingCleanup` 条目撤回到 `inTrash`
- 超过 `undoDeadlineMillis` 后会失败

## 6. `POST /api/trash/items/{trashItemId}/purge`

- 请求体：无
- 响应：返回被永久删除前的 `TrashItemDto`
- 允许 `inTrash` 和 `pendingCleanup` 状态；Android 主入口只从待清理页触发

永久删除规则：

- `largeAlbumDeleted`
  - 删除 trash item
  - 清理大相册记录
  - 清理本次一起删除的小相册记录、评论和关系
  - 不删除全局媒体记录和物理文件
- `smallAlbumDeleted`
  - 删除 trash item
  - 清理小相册记录、小相册评论和小相册媒体关系
  - 清理该小相册相关的 `mediaRemoved` trash 记录
  - 不删除全局媒体记录和物理文件
- `mediaRemoved`
  - 仅最终确认小相册与媒体的关系删除
  - 不删除媒体记录和物理文件
- `mediaSystemDeleted`
  - 删除 trash item
  - 删除媒体记录、媒体评论和媒体关系
  - 删除媒体拥有的 `original / preview / cover` 对象

## 7. `GET /api/trash/pending-cleanup`

- 请求体：无
- 响应：返回 `List<PendingCleanupDto>`

## Android 当前对接说明

- 顶部用户筛选在左侧，分类菜单在右侧；分类菜单包含四类回收站和有内容时才出现的“待清理”入口。
- 列表按分类保留内存视图缓存，并继续落本地读缓存，切换分类时优先展示上次内容再后台刷新。
- 详情页继续使用本地缓存；离线时已缓存媒体、小相册和大相册详情仍可进入查看态，但恢复、移出、撤销和永久删除会进入只读提示。
- 文案区分“移出回收站”“撤销移出”“永久删除”，避免把待清理误说成已物理删除。

## 错误码

- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `DELETE_CONFLICT`
- `MEDIA_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
- `SERVER_ERROR`
