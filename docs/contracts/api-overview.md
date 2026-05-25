# API 契约总览

更新时间：2026-05-25

## 状态

- 本文档描述当前 Android `REAL` 模式与 `YingShi-Server` 的联调基线
- 照片主链路、回收站、上传主链路已经可以稳定联调
- 仍有少量“后端已提供但 Android UI 尚未消费”的能力，见“当前缺口”

## 通用规则

- 当前服务端统一使用：
  - 成功响应：`{ requestId, data, page? }`
  - 失败响应：`{ requestId, error }`
- JSON 字段使用 `camelCase`
- 资源标识统一使用字符串 ID，如 `mediaId`、`postId`、`commentId`
- 时间字段统一使用毫秒时间戳
- 受保护接口统一使用：

```http
Authorization: Bearer <accessToken>
```

## 当前已实现接口范围

### 认证

- `POST /api/auth/login`
- `POST /api/auth/refresh-token`
- `GET /api/auth/me`
- `PATCH /api/auth/me/profile`
- `POST /api/auth/logout`

### 健康检查

- `GET /api/health`

### 相册 / 帖子 / 媒体

- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}?variant=original|preview|cover`
- `DELETE /api/media/{mediaId}`

### 评论

- `GET /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/posts/{postId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

### 回收站

- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`

### 上传

- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `POST /api/uploads/{uploadId}/confirm`
- `POST /api/uploads/{uploadId}/cancel`

### 后端已提供、Android UI 尚未接入的附加接口

- `POST /api/auth/me/avatar`
- `GET /api/auth/avatar/{userId}`
- `GET /api/notifications`
- `GET /api/notifications/{notificationId}`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`

## Android 侧当前已打通的 REAL Repository

- `AuthRepository`
- `AlbumRepository`
- `MediaRepository`
- `PostRepository`
- `CommentRepository`
- `TrashRepository`
- `UploadRepository`

## 当前缺口

- 通知中心当前仍使用本地 fake 数据，尚未切到真实通知接口
- 头像上传与头像图片展示的 Android UI 尚未接入
- refresh-token 接口已可用，但全局自动续期与失败请求重放策略尚未完整接好
- 对象存储直连、转码 / CDN、远程内容离线同步不在当前阶段

## 关键联调约定

- Android 默认可以在 `FAKE / REAL` 两种模式间切换
- 切换 `Base URL` 会清空旧 token 并立即重建 Retrofit
- 照片流只消费已经进入 App 内容主链路的媒体
- Viewer 图片预览优先级为：
  - `thumbnailUrl`
  - `mediaUrl`
  - `originalUrl`
- 系统媒体 Viewer 不消费帖子专属字段
- 上传返回的媒体即使暂时没有挂帖，也允许进入真实媒体流
- `confirmUpload` 是上传任务收尾 / 状态确认接口，不会再次创建媒体

## 分页说明

- `GET /api/media/feed` 当前同时兼容：
  - 不带参数时直接返回列表
  - 带 `cursor / pageSize` 时返回 `page.nextCursor / page.hasMore`
- `GET /api/trash/items` 当前使用页码分页：`page / size`
- 评论列表当前也使用页码分页
- `GET /api/notifications` 当前使用 `limit`

## 常见错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `ALBUM_NOT_FOUND`
- `POST_NOT_FOUND`
- `MEDIA_NOT_FOUND`
- `COMMENT_NOT_FOUND`
- `DELETE_CONFLICT`
- `RESTORE_CONFLICT`
- `REMOVE_FROM_TRASH_CONFLICT`
- `UNDO_REMOVE_EXPIRED`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
