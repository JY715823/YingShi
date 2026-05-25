# Auth API Contract

更新时间：2026-05-25

## 状态

- 已按当前 `YingShi-Server` 代码同步
- 可用于本地开发和 Android `REAL` 模式联调
- 当前没有 `/v1` 前缀

## 基础规则

- 基础路径：`/api/auth`
- `POST /login`、`POST /refresh-token` 公开访问
- `GET /me`、`PATCH /me/profile`、`POST /logout`、`POST /me/avatar`、`GET /avatar/{userId}` 都要求：

```http
Authorization: Bearer <accessToken>
```

- Android `REAL` 模式会把 token 持久化到本地，并在应用启动时主动校验 `/api/auth/me`
- `refresh-token` 接口已经可用；当前 Android 已在 `RealAuthRepository` 中完成对接并回写新 token
- 头像上传 / 头像读取接口已经在后端可用，但 Android UI 暂未接入

## 1. `POST /api/auth/login`

请求：

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

响应 `data`：

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "映世小屋",
  "avatarUrl": null,
  "bio": "把两个人的日常安静收进这里。",
  "libraryId": "library_shared",
  "libraryDisplayName": "我们的小空间",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "另一半",
    "avatarUrl": null,
    "bio": "把生活里的闪光片段，也把安静和想念一起留下来。"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000,
  "accessToken": "access-token-placeholder",
  "refreshToken": "refresh-token-placeholder",
  "accessTokenExpireAtMillis": 1760001800000,
  "refreshTokenExpireAtMillis": 1760604800000
}
```

说明：

- 登录响应已经包含当前用户资料、共享空间信息和搭子资料
- Android 登录成功后会立刻保存 token 并进入主壳层

## 2. `POST /api/auth/refresh-token`

请求：

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

响应 `data`：

```json
{
  "accessToken": "access-token-placeholder-new",
  "refreshToken": "refresh-token-placeholder-new",
  "accessTokenExpireAtMillis": 1760005400000,
  "refreshTokenExpireAtMillis": 1760608400000
}
```

说明：

- 成功后客户端应覆盖本地保存的 token bundle
- 当前 Android 已接通 Repository 层，但尚未实现“所有 401 自动刷新并重放原请求”的全局策略

## 3. `GET /api/auth/me`

响应 `data`：

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "映世小屋",
  "avatarUrl": null,
  "bio": "把两个人的日常安静收进这里。",
  "libraryId": "library_shared",
  "libraryDisplayName": "我们的小空间",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "另一半",
    "avatarUrl": null,
    "bio": "把生活里的闪光片段，也把安静和想念一起留下来。"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000
}
```

说明：

- `/me` 是 Android 启动恢复会话的核心接口
- `401` 会被客户端视为登录失效并清空本地会话

## 4. `PATCH /api/auth/me/profile`

请求：

```json
{
  "displayName": "映世小屋",
  "bio": "把两个人的日常安静收进这里。"
}
```

校验规则：

- `displayName` 必填，最大 `80` 个字符
- `bio` 可为空，最大 `280` 个字符

响应：

- 返回更新后的 `CurrentUser` 数据结构
- 字段结构与 `GET /api/auth/me` 一致

说明：

- 当前接口只能修改“当前登录用户”自己的昵称和简介
- 不支持通过 body 或 path 指向其他用户
- Android “编辑资料”页已直接接这个接口

## 5. `POST /api/auth/logout`

请求体可为空，也可以带一个占位 `refreshToken`：

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

当前响应 `data`：

```json
{
  "success": true
}
```

说明：

- 当前后端还没有服务端 token 吊销逻辑
- Android 退出登录后会本地清 token 并回到登录页

## 6. `POST /api/auth/me/avatar`

请求：

- content type: `multipart/form-data`
- 表单字段名必须是 `file`

响应：

- 返回更新后的 `CurrentUser` 数据
- 成功后 `avatarUrl` 会变成 `/api/auth/avatar/{userId}`

说明：

- 当前仅支持图片文件
- 服务端会统一转成 JPEG 并写入当前存储 provider
- Android 当前尚未提供头像上传入口

## 7. `GET /api/auth/avatar/{userId}`

响应：

- `200 image/jpeg`

说明：

- 当前要求 bearer auth
- 只有同一共享空间内的成员才允许读取该头像
- 若目标用户未上传头像，返回 `404`

## Android 当前映射关系

- 登录页 -> `POST /api/auth/login`
- 应用启动恢复会话 -> `GET /api/auth/me`
- 我的 / 个人主页 / 搭子资料 -> `GET /api/auth/me`
- 编辑资料 -> `PATCH /api/auth/me/profile`
- 退出登录 -> `POST /api/auth/logout`
- refresh-token -> `RealAuthRepository.refreshToken()`

## 种子账号

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

## 当前未提供的认证能力

- 注册
- 忘记密码
- 第三方登录

## 错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
