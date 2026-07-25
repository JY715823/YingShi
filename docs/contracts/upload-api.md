# Upload API Contract

更新时间：2026-06-18

## 状态

- 已按当前 `YingShi-Server` 代码同步
- 当前仅支持服务端托管的本地文件存储
- Android `REAL` 模式已接入上传主链路和上传任务收尾接口
- 传输中心历史记录按当前用户保留最近 30 天，清空记录为软隐藏，不删除媒体

## 基础规则

- 基础路径：`/api/uploads`
- 所有接口都要求 bearer auth
- 当前后端的上传主链路是：
  1. 申请 upload token
  2. 把文件以 multipart 上传到 `/file`
  3. 使用任务接口查询 / 确认 / 取消
- `confirmUpload` 是上传任务收尾 / 状态确认接口，不会再次创建媒体

## 1. `POST /api/uploads/token`

请求：

```json
{
  "fileName": "春日散步-01.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image",
  "width": 1440,
  "height": 1920,
  "durationMillis": null,
  "displayTimeMillis": 1777416400000,
  "capturedAtMillis": 1777416000000,
  "importedAtMillis": 1777416400000,
  "displayTimeSource": "ORIGINAL",
  "sourceFingerprint": "sha256:example",
  "operationId": "real-import-app-1777416400000",
  "operationType": "IMPORT_TO_APP",
  "operationTitle": "导入到照片流",
  "operationMediaCount": 2,
  "sourceItemId": "system-media-001"
}
```

说明：

- `capturedAtMillis`、`importedAtMillis`、`displayTimeSource`、`sourceFingerprint` 都是可选字段
- `operationId`、`operationType`、`operationTitle`、`operationMediaCount`、`sourceItemId` 用于传输中心分组和历史恢复；`operationType` 可为 `IMPORT_TO_APP / CREATE_POST / ADD_TO_EXISTING_POST / LIFE_CONSOLE`
- 如果 `displayTimeMillis` 为空，服务端会优先回退到 `capturedAtMillis`，再回退到 `importedAtMillis`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "provider": "local",
  "uploadUrl": "/api/uploads/upload_001/file",
  "expireAtMillis": 1777417000000,
  "state": "waiting",
  "uploadMethod": "POST",
  "objectKey": null,
  "headers": {},
  "confirmUrl": "/api/uploads/upload_001/confirm"
}
```

说明：

- `uploadMethod` / `objectKey` / `headers` / `confirmUrl` 仅在 `STORAGE_DIRECT_UPLOAD_ENABLED=true` 且对象存储支持 presigned-put 时有意义
- `uploadMethod` 为 `POST`（本地存储）或 `PUT`（直传对象存储）
- `objectKey` 为直传时的对象 key，本地存储为 `null`
- `headers` 为直传时附带的 HTTP headers（如 Content-Type）
- `confirmUrl` 为直传后的确认 URL

## 2. `POST /api/uploads/{uploadId}/file`

请求：

- content type: `multipart/form-data`
- 表单字段名必须是 `file`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "state": "success",
  "media": {
    "mediaId": "media_uploaded_001",
    "mediaType": "image",
    "url": "/api/media/files/media_uploaded_001",
    "previewUrl": "/api/media/files/media_uploaded_001?variant=preview",
    "originalUrl": "/api/media/files/media_uploaded_001",
    "videoUrl": null,
    "coverUrl": null,
    "mimeType": "image/jpeg",
    "width": 1440,
    "height": 1920,
    "aspectRatio": 0.75,
    "durationMillis": null,
    "displayTimeMillis": 1777416400000,
    "capturedAtMillis": 1777416000000,
    "importedAtMillis": 1777416400000,
    "displayTimeSource": "ORIGINAL",
    "postIds": []
  }
}
```

说明：

- 上传成功后会立刻创建一条 `Media` 记录
- 返回媒体允许 `postIds` 为空；“已上传但尚未挂帖”是合法状态
- Android 之后会再调用“创建帖子”或“加入已有帖子”接口完成挂帖

## 3. `GET /api/uploads/{uploadId}`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "fileName": "春日散步-01.jpg",
  "mediaType": "image",
  "objectKey": "/api/media/files/media_uploaded_001",
  "state": "success",
  "progressPercent": 100,
  "errorMessage": null,
  "operationId": "real-import-app-1777416400000",
  "operationType": "IMPORT_TO_APP",
  "operationTitle": "导入到照片流",
  "operationMediaCount": 2,
  "sourceItemId": "system-media-001",
  "createdAtMillis": 1777416400000,
  "updatedAtMillis": 1777416500000,
  "completedAtMillis": 1777416500000
}
```

说明：

- `state` 枚举值：
  - `waiting`: 任务已创建，等待文件上传
  - `uploading`: （Android 本地状态）字节流传输中，服务端无此态
  - `success`: 上传成功，media 已创建
  - `failed`: 上传失败，`errorMessage` 携带失败原因，可重试
  - `cancelled`: 任务已取消
- `progressPercent` 当前是服务端任务视角，不是实时字节流上传进度
- `errorMessage` 在 `state=failed` 时携带失败原因，其他状态为 `null`

## 4. `GET /api/uploads`

查询参数：

- `state` 可选：`waiting / success / failed / cancelled`
- `operationType` 可选：`IMPORT_TO_APP / CREATE_POST / ADD_TO_EXISTING_POST / LIFE_CONSOLE`
- `pageSize` 可选：默认 50，最大 200
- `cursor` 可选：上一页返回的 `nextCursor`，格式为 `{updatedAtMillis}:{taskId}`，用于 keyset 分页

响应：

- 返回 `UploadTaskResponse[]` + `nextCursor` + `hasMore`
- `hasMore=true` 时 `nextCursor` 非空，传入下一次请求的 `cursor` 参数加载下一页
- `hasMore=false` 时 `nextCursor` 为 `null`，表示已到最后一页
- 仅返回当前登录用户在当前共享库内、未被隐藏、最近 30 天更新过的任务

说明：

- Android 传输中心进入时会读取该接口，并与本地快照合并
- 该接口用于历史恢复，不表示实时字节进度推送
- 首页默认返回 50 条（可通过 `pageSize` 调整），超过时通过 `cursor` 分页加载

## 5. `POST /api/uploads/{uploadId}/confirm`

请求可为空，也可以附带：

```json
{
  "etag": "fake-etag-upload_001",
  "objectKey": "uploads/fake/media_001"
}
```

响应：

- 返回 `UploadTaskResponse`

说明：

- 对于已经 `success` 的任务，当前接口是幂等的状态确认
- 对于仍处于 `waiting` 的任务，服务端会校验可选的 `objectKey`
- 当前 Android 传输中心在 REAL 模式下会调用该接口完成上传任务收尾

## 6. `POST /api/uploads/{uploadId}/cancel`

响应：

- 返回 `UploadTaskResponse`

说明：

- 未完成任务可取消，取消后状态变为 `cancelled`
- 已经 `success` 的任务不能再取消
- Android 传输中心在 REAL 模式下已接通取消接口

## 7. `POST /api/uploads/{uploadId}/dismiss`

响应：

- 返回被软隐藏的 `UploadTaskResponse`

说明：

- 仅从传输中心历史列表隐藏任务，不删除媒体
- 只能隐藏当前上传者自己的任务

## 8. `POST /api/uploads/dismiss-batch`

请求：

```json
{
  "state": "success",
  "operationType": "IMPORT_TO_APP"
}
```

说明：

- `state` 和 `operationType` 都可为空
- 只软隐藏当前用户、当前共享库、最近 30 天内、未隐藏且匹配筛选条件的任务

## 本地存储说明

- 当前服务端默认把文件写到 `local-storage`
- 当前目录布局包含：
  - `local-storage/originals/yyyy/MM/{mediaId}.{ext}`
  - `local-storage/previews/yyyy/MM/{mediaId}-720.jpg`
  - `local-storage/test/...` 用于 seed/demo 媒体
  - `local-storage/tmp/uploads/...` 用于临时上传
  - `local-storage/videos/posters/...` 用于视频封面

## 当前上传限制

- 当前 Spring multipart 限制为：
  - `1024MB` per file
  - `1100MB` per request
- 以上限制来自当前后端 `application.yml`

## Android 当前对接方式

- 底部 `+ -> 上传媒体`
- 系统媒体 `导入到 App`
- 系统媒体“发成新帖子”
- 系统媒体“加入已有帖子”
- 传输中心取消任务 / 上传收尾

## 当前未实现能力

- 云存储直传
- 转码 / CDN / OSS 回调
- 多分片对象存储直传协商

## 错误码

- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_FILE_MISMATCH`
- `UPLOAD_STORAGE_ERROR`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
