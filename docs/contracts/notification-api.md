# Notification API Contract

更新时间：2026-05-25

## 状态

- 后端通知接口已经在 `YingShi-Server` 中提供
- 当前 Android 通知中心 UI 仍使用本地 fake 数据
- 本文档主要用于说明后端现有通知契约，便于后续 Android 真通知接入

## 基础规则

- 基础路径：`/api/notifications`
- 所有接口都要求 bearer auth
- 当前通知列表是“服务端聚合事件流”，来源包括：
  - 评论
  - 帖子内容更新
  - 回收站删除 / 恢复状态变化
  - 上传任务完成 / 取消

## Notification DTO

```json
{
  "notificationId": "comment:comment_001",
  "type": "comment",
  "title": "另一位成员 评论了帖子",
  "body": "这张照片真好看",
  "createdAtMillis": 1777416400000,
  "isRead": false,
  "targetSummary": "Night Walk",
  "targetType": "POST",
  "postId": "post_001",
  "mediaId": null,
  "trashItemId": null
}
```

## 1. `GET /api/notifications`

查询参数：

- `limit` 可选，默认由服务端决定

响应：

- 返回 `List<NotificationDto>`

说明：

- 当前服务端会按时间倒序返回聚合后的通知事件

## 2. `GET /api/notifications/{notificationId}`

响应：

- 返回一个 `NotificationDto`

## 3. `POST /api/notifications/{notificationId}/read`

响应：

- 返回已更新为 `isRead = true` 的 `NotificationDto`

## 4. `POST /api/notifications/read-all`

响应 `data`：

```json
{
  "success": true,
  "affectedCount": 12
}
```

## Android 现状

- 顶部铃铛、通知中心列表、通知详情页目前都还是本地 fake 数据
- 后续 Android 接真实通知时，可直接按本契约切换到远端接口
