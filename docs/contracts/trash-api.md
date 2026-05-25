# Trash API Contract

更新时间：2026-05-25

## 状态

- 已按当前 `YingShi-Server` 代码同步
- Android `REAL` 模式已接入全部现有 trash 接口

## 基础规则

- 基础路径：`/api/trash`
- 所有接口都要求 bearer auth
- 列表默认分页：`page=1`、`size=10`
- 排序规则：按 `deletedAtMillis` 倒序
- 当前后端已经支持直接永久删除 `purge`

## Trash Item DTO

```json
{
  "trashItemId": "trash_001",
  "itemType": "mediaSystemDeleted",
  "state": "inTrash",
  "sourcePostId": null,
  "sourceMediaId": "media_001",
  "commentTargetMediaId": "media_001",
  "title": "海边散步",
  "previewInfo": "媒体已移入回收站",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": [],
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
  - `postDeleted`
  - `mediaRemoved`
  - `mediaSystemDeleted`
- `state`
  - `inTrash`
  - `pendingCleanup`
  - `restored`
- `sourcePostId`
  - 删除帖子或从帖子移除媒体时可用于定位原帖子
- `sourceMediaId`
  - 全局删除媒体时可用于渲染已删媒体详情
- `commentTargetMediaId`
  - 已删媒体评论仍需要指向哪个媒体目标
- `relatedPostIds / relatedMediaIds`
  - Android 用来决定详情页和恢复后的刷新范围
- `sourceMedia*`
  - Android 用来在真实回收站详情页渲染图片 / 视频基础信息

## 1. `GET /api/trash/items`

查询参数：

- `itemType`：可选
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
    "itemType": "postDeleted",
    "state": "inTrash",
    "sourcePostId": "post_001",
    "sourceMediaId": null,
    "commentTargetMediaId": null,
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

## 3. `POST /api/trash/items/{trashItemId}/restore`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

语义：

- `postDeleted`：恢复帖子、帖子评论和帖子媒体关系
- `mediaRemoved`：恢复帖子与媒体之间的关系，不恢复全局媒体删除
- `mediaSystemDeleted`：恢复媒体本体及相关关系

## 4. `POST /api/trash/items/{trashItemId}/remove`

- 请求体：无
- 响应：返回 `PendingCleanupDto`

示例：

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
    "commentTargetMediaId": null,
    "title": "春日散步",
    "previewInfo": "帖子待彻底移出回收站",
    "deletedAtMillis": 1777412800000,
    "relatedPostIds": ["post_001"],
    "relatedMediaIds": ["media_001", "media_002"]
  }
}
```

说明：

- `remove` 的语义是“移出回收站并进入 24 小时待清理窗口”
- Android 当前会把这部分内容展示为 `24h 可撤销`

## 5. `POST /api/trash/items/{trashItemId}/purge`

- 请求体：无
- 响应：返回被永久删除前的 `TrashItemDto`

当前后端行为：

- `postDeleted`
  - 删除 trash item
  - 删除已删除帖子记录、帖子评论和帖子关系
  - 清理该帖子关联的 `mediaRemoved` trash 记录
  - 不直接删除全局媒体文件
- `mediaRemoved`
  - 仅最终确认“帖子和媒体的关系删除”
  - 不删除媒体本体和物理文件
- `mediaSystemDeleted`
  - 删除 trash item
  - 删除媒体记录和媒体评论
  - 删除媒体拥有的本地原图 / 预览 / 封面文件

## 6. `POST /api/trash/items/{trashItemId}/undo-remove`

- 请求体：无
- 响应：返回一个 `TrashItemDto`

语义：

- 把 `pendingCleanup` 中的条目撤回到 `inTrash`
- 超过 `undoDeadlineMillis` 后会失败

## 7. `GET /api/trash/pending-cleanup`

- 响应：返回 `List<PendingCleanupDto>`

## Android 当前对接说明

- `RealTrashRepository` 已接入：
  - 列表
  - 详情
  - 恢复
  - 移出
  - 永久删除
  - 撤销移出
  - 待清理列表
- 回收站详情页当前会基于 `sourceMediaId / relatedMediaIds` 去请求媒体文件接口，渲染真实删除内容
- 删除后的页面刷新范围由 Android 根据 `itemType` 和 `related*Ids` 决定，不依赖额外后端提示字段

## 错误码

- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `DELETE_CONFLICT`
- `MEDIA_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
- `SERVER_ERROR`
