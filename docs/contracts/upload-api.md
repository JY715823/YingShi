# Upload API Contract

更新时间：2026-05-25

## 状态

- 已按当前 `YingShi-Server` 代码同步
- 当前仅支持服务端托管的本地文件存储
- Android `REAL` 模式已接入上传主链路和上传任务收尾接口

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
  "sourceFingerprint": "sha256:example"
}
```

说明：

- `capturedAtMillis`、`importedAtMillis`、`displayTimeSource`、`sourceFingerprint` 都是可选字段
- 如果 `displayTimeMillis` 为空，服务端会优先回退到 `capturedAtMillis`，再回退到 `importedAtMillis`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "provider": "local",
  "uploadUrl": "/api/uploads/upload_001/file",
  "expireAtMillis": 1777417000000,
  "state": "waiting"
}
```

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
  "errorMessage": null
}
```

说明：

- `state` 当前可能是 `waiting / success / cancelled`
- `progressPercent` 当前是服务端任务视角，不是实时字节流上传进度

## 4. `POST /api/uploads/{uploadId}/confirm`

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

## 5. `POST /api/uploads/{uploadId}/cancel`

响应：

- 返回 `UploadTaskResponse`

说明：

- 未完成任务可取消，取消后状态变为 `cancelled`
- 已经 `success` 的任务不能再取消
- Android 传输中心在 REAL 模式下已接通取消接口

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
