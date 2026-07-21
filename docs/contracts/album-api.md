# Album API

> 大相册（Album）相关 API 端点文档
> 对应代码: `AlbumController.java`、`AlbumService.java`
> 更新时间: 2026-07-11

## 端点清单

| 方法 | 路径 | 描述 | 认证 |
|------|------|------|------|
| POST | `/api/albums` | 创建大相册 | Bearer Token |
| GET | `/api/albums` | 列出共享库中的大相册 | Bearer Token |
| GET | `/api/albums/{albumId}/small-albums` | 列出大相册下的小相册 | Bearer Token |
| PATCH | `/api/albums/{albumId}` | 重命名大相册 | Bearer Token |
| PATCH | `/api/albums/{targetAlbumId}/move-small-albums` | 移动小相册到目标大相册 | Bearer Token |
| DELETE | `/api/albums/{albumId}` | 删除大相册（软删除，进入回收站）| Bearer Token |

所有端点均需 `Authorization: Bearer <accessToken>` 请求头。

---

## POST /api/albums — 创建大相册

### 请求体

```json
{
  "title": "2026 夏日旅行",
  "subtitle": "可选副标题"
}
```

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `title` | String | 是 | 1-120 字符 | 大相册标题 |
| `subtitle` | String | 否 | ≤255 字符 | 副标题 |

### 响应 `200 OK`

```json
{
  "requestId": "req-abc123",
  "data": {
    "albumId": "album_xxx",
    "title": "2026 夏日旅行",
    "subtitle": "可选副标题",
    "coverMediaId": null,
    "systemKey": null,
    "includeInPhotoFeed": true,
    "smallAlbumCount": 0
  }
}
```

### AlbumDto 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `albumId` | String | 大相册 ID |
| `title` | String | 标题 |
| `subtitle` | String | 副标题（可为 null）|
| `coverMediaId` | String | 封面媒体 ID（可为 null）|
| `systemKey` | String | 系统键（如 "daily" 等预设相册，可为 null）|
| `includeInPhotoFeed` | boolean | 是否包含在照片流中 |
| `smallAlbumCount` | long | 小相册数量 |

### 错误码

| HTTP | 错误码 | 说明 |
|------|--------|------|
| 400 | VALIDATION_ERROR | title 为空或超过 120 字符 |
| 401 | UNAUTHORIZED | 未认证 |

---

## GET /api/albums — 列出大相册

### 请求

无请求体，无查询参数。

### 响应 `200 OK`

```json
{
  "requestId": "req-abc123",
  "data": [
    {
      "albumId": "album_xxx",
      "title": "2026 夏日旅行",
      "subtitle": null,
      "coverMediaId": "media_yyy",
      "systemKey": null,
      "includeInPhotoFeed": true,
      "smallAlbumCount": 5
    }
  ]
}
```

返回当前用户所在共享库的所有未删除大相册列表。

---

## GET /api/albums/{albumId}/small-albums — 列出小相册

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `albumId` | String | 大相册 ID |

### 响应 `200 OK`

```json
{
  "requestId": "req-abc123",
  "data": [
    {
      "smallAlbumId": "post_xxx",
      "title": "第一天：海滩",
      "summary": "阳光沙滩",
      "contributorLabel": "用户A",
      "creatorUserId": "user_demo_a",
      "participantUserIds": ["user_demo_a", "user_demo_b"],
      "displayTimeMillis": 1780000000000,
      "eventStartedAtMillis": null,
      "eventEndedAtMillis": null,
      "displayTimeSource": "MANUAL",
      "albumId": "album_xxx",
      "systemKey": null,
      "coverMediaId": "media_yyy",
      "mediaCount": 12
    }
  ]
}
```

### PostSummaryDto 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `smallAlbumId` | String | 小相册（Post）ID |
| `title` | String | 标题 |
| `summary` | String | 摘要 |
| `contributorLabel` | String | 贡献者标签 |
| `creatorUserId` | String | 创建者用户 ID |
| `participantUserIds` | List<String> | 参与者用户 ID 列表 |
| `displayTimeMillis` | Long | 展示时间戳 |
| `eventStartedAtMillis` | Long | 事件开始时间（可为 null）|
| `eventEndedAtMillis` | Long | 事件结束时间（可为 null）|
| `displayTimeSource` | String | 展示时间来源（MANUAL/AUTO 等）|
| `albumId` | String | 所属大相册 ID |
| `systemKey` | String | 系统键（可为 null）|
| `coverMediaId` | String | 封面媒体 ID（可为 null）|
| `mediaCount` | long | 媒体数量 |

### 错误码

| HTTP | 错误码 | 说明 |
|------|--------|------|
| 404 | NOT_FOUND | 大相册不存在或已删除 |
| 401 | UNAUTHORIZED | 未认证 |

---

## PATCH /api/albums/{albumId} — 重命名大相册

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `albumId` | String | 大相册 ID |

### 请求体

```json
{
  "title": "新标题",
  "subtitle": "新副标题"
}
```

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `title` | String | 是 | 1-120 字符 | 新标题 |
| `subtitle` | String | 否 | ≤255 字符 | 新副标题 |

### 响应 `200 OK`

```json
{
  "requestId": "req-abc123",
  "data": {
    "albumId": "album_xxx",
    "title": "新标题",
    "subtitle": "新副标题",
    "coverMediaId": "media_yyy",
    "systemKey": null,
    "includeInPhotoFeed": true,
    "smallAlbumCount": 5
  }
}
```

### 错误码

| HTTP | 错误码 | 说明 |
|------|--------|------|
| 400 | VALIDATION_ERROR | title 为空或超过 120 字符；subtitle 超过 255 字符 |
| 404 | NOT_FOUND | 大相册不存在或已删除 |
| 401 | UNAUTHORIZED | 未认证 |

---

## DELETE /api/albums/{albumId} — 删除大相册

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `albumId` | String | 大相册 ID |

### 请求

无请求体。

### 响应 `200 OK`

```json
{
  "requestId": "req-abc123",
  "data": {
    "trashItemId": "trash_xxx",
    "itemType": "largeAlbumDeleted",
    "state": "inTrash",
    "actorUserId": "user_xxx",
    "sourceSmallAlbumId": null,
    "sourceMediaId": null,
    "commentTargetMediaId": null,
    "title": "被删除的大相册标题",
    "previewInfo": "3 个小相册",
    "deletedAtMillis": 1720612800000,
    "relatedSmallAlbumIds": ["post_a", "post_b", "post_c"],
    "relatedMediaIds": ["media_1", "media_2", "media_3"],
    "sourceMediaType": null,
    "sourceMediaWidth": null,
    "sourceMediaHeight": null,
    "sourceMediaAspectRatio": null,
    "sourceMediaDurationMillis": null,
    "sourceMediaMimeType": null
  }
}
```

### TrashItemDto 字段

> 对应代码: `TrashItemDto.java` (record)、`TrashMapper.toTrashItemDto()`

| 字段 | 类型 | 说明 |
|------|------|------|
| `trashItemId` | String | 回收站条目 ID |
| `itemType` | String | 条目类型（大相册删除为 `largeAlbumDeleted`）|
| `state` | String | 状态（`inTrash`/`pendingCleanup`/`restored`）|
| `actorUserId` | String | 执行删除操作的用户 ID |
| `sourceSmallAlbumId` | String | 关联小相册 ID（大相册删除时为 null）|
| `sourceMediaId` | String | 关联媒体 ID（大相册删除时为 null）|
| `commentTargetMediaId` | String | 评论目标媒体 ID（仅媒体删除类型有值，大相册删除为 null）|
| `title` | String | 大相册标题 |
| `previewInfo` | String | 预览信息（如小相册数量摘要）|
| `deletedAtMillis` | Long | 删除时间（epoch 毫秒）|
| `relatedSmallAlbumIds` | List<String> | 关联小相册 ID 列表 |
| `relatedMediaIds` | List<String> | 关联媒体 ID 列表 |
| `sourceMediaType` | String | 源媒体类型（大相册删除时为 null）|
| `sourceMediaWidth` | Integer | 源媒体宽度（大相册删除时为 null）|
| `sourceMediaHeight` | Integer | 源媒体高度（大相册删除时为 null）|
| `sourceMediaAspectRatio` | Double | 源媒体宽高比（大相册删除时为 null）|
| `sourceMediaDurationMillis` | Long | 源媒体时长（大相册删除时为 null）|
| `sourceMediaMimeType` | String | 源媒体 MIME 类型（大相册删除时为 null）|

### 删除行为说明

- **软删除**: 大相册的 `deletedAt` 字段被设置为当前时间，不从数据库物理删除
- **级联软删除**: 大相册下所有小相册（PostEntity）的 `deletedAt` 也被设置
- **进入回收站**: 创建 `TrashItemEntity` 记录（state=`inTrash`），可恢复
- **移出回收站**: 移至待清理状态（state=`pendingCleanup`）后仍有 24 小时撤销窗口
- **媒体本体保留**: 媒体文件本身不被删除，仅标记关联关系为已删除

### 错误码

| HTTP | 错误码 | 说明 |
|------|--------|------|
| 404 | NOT_FOUND | 大相册不存在或已删除 |
| 401 | UNAUTHORIZED | 未认证 |

---

## PATCH /api/albums/{targetAlbumId}/move-small-albums — 移动小相册

将一批小相册（Post）从其当前所属大相册移动到目标大相册。用于「切换小相册所属大相册」功能。

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `targetAlbumId` | String | 目标大相册 ID（小相册将被移入此相册）|

### 请求体

```json
{
  "smallAlbumIds": ["post_a", "post_b", "post_c"]
}
```

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `smallAlbumIds` | List<String> | 是 | 1-100 个元素；不可含 null/空白字符串（服务端会过滤去重）| 待移动的小相册 ID 列表 |

### 响应 `200 OK`

返回移动完成后这些小相册的最新摘要（`albumId` 字段已更新为 `targetAlbumId`）。

```json
{
  "requestId": "req-abc123",
  "data": [
    {
      "smallAlbumId": "post_a",
      "title": "第一天：海滩",
      "summary": "阳光沙滩",
      "contributorLabel": "用户A",
      "creatorUserId": "user_demo_a",
      "participantUserIds": ["user_demo_a", "user_demo_b"],
      "displayTimeMillis": 1780000000000,
      "eventStartedAtMillis": null,
      "eventEndedAtMillis": null,
      "displayTimeSource": "MANUAL",
      "albumId": "album_target",
      "systemKey": null,
      "coverMediaId": "media_yyy",
      "mediaCount": 12
    }
  ]
}
```

> 响应数据项结构与 [`GET /api/albums/{albumId}/small-albums`](#get-apialbumsalbumidsmall-albums--列出小相册) 的 `PostSummaryDto` 完全一致。

### 移动行为说明

- **校验目标大相册**: 目标大相册必须存在、未删除、且属于当前用户所在共享库
- **校验小相册**: 所有 `smallAlbumIds` 必须存在、未软删除、属于同一共享库；服务端会过滤 null/空白并去重
- **原子更新**: 每个小相册的 `albumId` 被更新为 `targetAlbumId`，同时记录 `lastModifiedByUserId`（执行人）并 `touch()` 更新 `updatedAt`
- **批量保存**: 通过 `saveAll` 一次性持久化，事务保证一致性
- **媒体不动**: 小相册下的媒体本体、封面、参与者等关联关系不发生变化，仅 `albumId` 归属切换
- **顺序保留**: 返回列表顺序与请求 `smallAlbumIds` 去重后的顺序一致

### 错误码

| HTTP | 错误码 | 说明 |
|------|--------|------|
| 400 | VALIDATION_ERROR | `smallAlbumIds` 为空、超过 100 个、或去重后为空 |
| 404 | NOT_FOUND | 目标大相册不存在或已删除 |
| 404 | SMALL_ALBUM_NOT_FOUND | 部分 `smallAlbumIds` 不存在、已删除或不属于当前共享库（响应 message 会列出缺失的 ID）|
| 401 | UNAUTHORIZED | 未认证 |

---

## 通用响应格式

### ApiResponse<T>

所有端点返回统一的 ApiResponse 包装格式:

```json
{
  "requestId": "req-xxx",
  "data": <T>
}
```

### 错误响应

```json
{
  "requestId": "req-xxx",
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述",
    "details": {}
  }
}
```
