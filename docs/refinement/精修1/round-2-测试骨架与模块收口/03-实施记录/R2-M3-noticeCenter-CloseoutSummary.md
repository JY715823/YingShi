# R2-M3: noticeCenter 模块 Closeout Summary

> **模块**: noticeCenter（通知中心）
> **原状态**: device_qa → **新状态**: closed（服务端已关闭，真机部分 carry-forward）
> **关闭日期**: 2026-07-04

---

## What shipped（已完成）

### 服务端
- `/api/notifications` 虚拟物化模型：从 6+ 源（评论、小相册、回收站、上传任务、生活记录、推送审计）实时合并 feed
- 通知 DTO 扩展：module、category、actor、group/operation、media thumbnails、target route、self-actor read state
- 上传任务通知按 operation 分组
- 推送偏好持久化 `/api/push/preferences`
- 推送诊断 `/api/push/diagnostics`
- FCM 推送：审计、偏好、self-fallback、after-commit async
- 推送分发异步化（`PushDispatchSupport.afterCommitAsync`）
- `/api/sync/versions` 包含 notificationVersion + deleted media/album/album row timestamps
- 推送投递审计 `push_delivery_audits` 完整审计轨迹
- 17 项验证案例通过（编译、API、FCM 配置、单设备推送等）

### Android 客户端
- 通知中心：最新优先日期分组、模块/分类过滤、actor 过滤（默认仅看对方）、自身事件只读历史
- 分组媒体缩略图 + 单媒体点击路由
- 缓存通知保留 mediaItems 用于离线缩略图
- 推送设置读写 + 乐观状态 + 回滚
- 推送数据处理扩展：可见状态栏通知 + 路由
- 照片推送深链：notification-highlighted + `通知` badge
- 小相册推送深链：`photos:small-album:<postId>` 优先匹配
- 媒体评论推送深链：`autoOpenViewer/autoOpenComment`
- 模块未读角标（紧凑右上角 count badge）
- 第二轮可靠性：同步版本轮询 fallback + FCM killed/background 系统渲染
- `compileDebugKotlin` 通过

### 14 个 Device QA 问题（全部已修复）
1. 顶部控制区拥挤 → 两行布局 + 回收站图标
2. `Unexpected server error`（过期枚举）→ 部署更新 jar
3. FCM 环境/密钥配置 → 修复 .env + Compose mount
4. 通知权限未主动请求 → RequestPermission 路径
5. Cloudflare tunnel 依赖移除
6. 单设备推送无合格目标 → PUSH_SELF_FALLBACK_ENABLED=true
7. B 设备 FCM token 缺失 → token retry on resume + Settings diagnostics
8. 小相册推送深链被通用 photos 路由吞掉 → 路由优先级修复
9. 通知中心消费共享 stale flags → 独立 NOTIFICATIONS 同步模块
10. 模块未读角标视觉 → 紧凑右上角 badges
11. 评论深链竞态 → after-commit push timing + 强制刷新
12. 删除未推进 photoFeedVersion → 包含 deleted-row update timestamps
13. 推送分发阻塞用户请求 → async after-commit
14. 媒体评论推送未打开 Viewer → 保留 autoOpenViewer/autoOpenComment

### 本轮新增验证
- `LiveServerIntegrationTest`：`/api/notifications`、`/api/push/preferences`、`/api/push/diagnostics`、`/api/sync/versions` 端点注册正确，返回 401
- 服务端 curl 验证：全部通知/推送端点路由注册 + 认证保护正确

## Next Fix Queue 状态（原 8 项）

| # | 项目 | 代码状态 | 真机验证 |
|---|------|---------|---------|
| 1 | 评论推送新鲜内容验收 | 已修复 | **carry-forward**：需真机 |
| 2 | 模块未读角标视觉 QA | 已修复 | **carry-forward**：需真机 |
| 3 | 单设备可见推送复查 | 已修复 | **carry-forward**：需真机 |
| 4 | 双设备推送验收 | 已修复 | **carry-forward**：需 2 台真机 |
| 5 | 小相册推送深链验收 | 已修复 | **carry-forward**：需真机 |
| 6 | 相册实时同步验收 | 已修复 | **carry-forward**：需 2 台真机 |
| 7 | A 删除/B 照片流刷新验收 | 已修复 | **carry-forward**：需 2 台真机 |
| 8 | 媒体评论推送 Viewer 验收 | 已修复 | **carry-forward**：需真机 |

**全部 8 项代码层面已修复，仅缺真机验收。**

## What remains risky（遗留风险）

| 风险 | 严重度 | 说明 | 建议处理时机 |
|------|--------|------|------------|
| 双设备 FCM 推送端到端未验证 | HIGH | 需要两台设备同时登录 + 通知权限 + token 注册 | Round 4 真机回归 |
| 推送直达轻量 Viewer 未实现 | MEDIUM | 当前路由到照片流，未实现推送直接打开 Viewer | 后续版本 |
| NotificationService 906 行单类 | LOW | 虚拟物化模型紧耦合，拆分无收益，但数据量大时性能堪忧 | 监控 |
| `notifiedUploadOperationKeys` 无界 Set | LOW | 缓慢内存泄漏，个人服务器可忽略 | Round 3 P1-3 |
| `PushDispatchSupport` ForkJoinPool sleep | LOW | 高并发下线程饥饿 | Round 3 P1-3 |

## What was intentionally deferred（有意延期）

- 推送直达轻量 Viewer（当前路由到照片流，设计决策：先确保推送可达，Viewer 直达作为后续优化）
- 限流多实例兼容（ConcurrentHashMap 单机限流，两人规模可接受）
- X-Forwarded-For 防伪（需反向代理覆盖，在 nginx-https.conf 中已配置 `$remote_addr`）

## Carry-forward notes

- 事实：通知中心是虚拟物化模型（不持久化通知记录），从 6+ 源实时合并
- 推送采用 after-commit async 模式，FCM 高优先级 notification+data 混合消息
- 第二轮可靠性：sync version 轮询作为 FCM 的 fallback 兜底
