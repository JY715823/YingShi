# R2-M4: chatShower 模块 Closeout Summary

> **模块**: chatShower（聊天查看器）
> **原状态**: device_qa → **新状态**: closed（服务端已关闭，真机部分 carry-forward）
> **关闭日期**: 2026-07-04

---

## What shipped（已完成）

### 服务端（R2: 表拆分 + 行级同步）
- V25 Flyway 迁移：5 张关系表（`imported_chats`、`imported_messages`、`imported_participants`、`imported_resources`、`imported_message_search`）
- `POST /api/chat/imported/sync`：行级增量同步（仿 ledger 架构）
- `ChatImportedSyncService`：应用客户端 upsert/delete → 查询服务端变更 → 返回差异
- `SyncVersionsResponse` 新增 `chatVersion` 字段
- `SyncService` 注入 `ImportedChatRepository`

### 服务端（R3: 独立媒体通道）
- `ChatMediaService`：上传（MD5 去重）/ 下载 / HEAD 检查
- `ChatMediaController`：`POST /upload`、`GET /{key}`、`HEAD /{key}`
- 存储路径：`chat-imports/{libraryId}/{chatStableKey}/resources/{storedFileName}`
- 与照片模块存储完全隔离（同一 MinIO bucket，key 前缀隔离）

### 服务端（R5: ZIP 上传解析）
- `POST /api/chat/imported/upload-zip`：接收 QCE ZIP，服务端 Java 解析 manifest + JSONL
- 写入 5 张表，提取媒体到 ObjectStorageService
- 兼容 QCE v5.5.64 manifest 格式

### Android 客户端（R6: 代码拆分）
- `ImportedChatScreen.kt`（4362 行）拆分为 10 个文件，全部编译通过：
  - `ChatInternals.kt`、`ChatAudioPlayer.kt`、`ChatPdfPreview.kt`、`ChatTopBar.kt`
  - `ChatBubbleComponents.kt`、`ChatMediaComponents.kt`、`ChatSearchComponents.kt`
  - `ChatMediaViewer.kt`、`ChatDetailScreen.kt`、`ChatListScreen.kt`

### Android 客户端（R4: 导入优化）
- 流式 ZIP 处理（不完全解压）
- 批量 DB 操作（每个 chunk 一个事务）
- 文件 IO 移出事务
- SharedPreferences 断点续传
- 单次扫描解析（合并双通道）
- 并行资源复制（Semaphore(4)）
- WakeLock 90 分钟自动续期
- START_NOT_STICKY（V-003 修复后）

### Android 客户端（R1: UI 升级，11 项）
- 气泡尾巴（Path + drawBehind）
- 连续消息合并（< 5min 间隔）
- 气泡微阴影（1dp elevation）
- 群聊彩色昵称（8 色调色板，hash 分配）
- 页面转场动画（AnimatedContent）
- 搜索高亮脉冲（2.4s）
- 图片加载骨架屏（shimmer）
- Material 3 DatePicker
- 聊天背景纹理（点阵图案）
- 消息长按多选模式
- 媒体查看器保存到相册

### Android 客户端（R5: 自动化）
- 客户端 `ChatMediaApi.kt` + `ChatMediaSyncBridge.kt`

### 5 个真机 Bug 修复
1. **V-001**（critical）：导入清空已有数据 → `NoOpChatSyncBridge` + hydrate 仅在 ViewModel init
2. **V-002**（high）：上传时列表重复 → identity map 从 `dao.getMessageIdentityRows(chatId)` 加载
3. **V-003**（high）：系统通知进度不结束 → START_NOT_STICKY
4. **V-004**（critical）：头像/表情/图片不可用 → checkpoint 有效性验证 + 自动清除
5. **V-005**（high）：回复消息点击无法跳转 → consume/clear 移入 `if (index >= 0)` 分支

### 本轮新增验证
- `LiveServerIntegrationTest`：`/api/chat/imported/snapshot` 端点注册正确，返回 401
- 服务端 curl 验证：chat imported 相关端点路由注册 + 认证保护正确

## What remains risky（遗留风险）

| 风险 | 严重度 | 说明 | 建议处理时机 |
|------|--------|------|------------|
| 10GB ZIP 实际导入性能未压测 | HIGH | 无 QCE 测试数据，实际性能（耗时/内存/CPU）未知 | 有 QCE 数据后 |
| QCE v5.5.64 JSONL 格式细节可能需微调 | MEDIUM | 服务端解析基于文档格式，实际数据可能有差异 | 有 QCE 数据后 |
| 断点续传真机验证 | HIGH | SharedPreferences 断点续传在真机中断/恢复未验证 | Round 4 真机回归 |
| 群聊彩色昵称真机显示 | LOW | Light/Dark 主题下可读性未确认 | Round 4 真机回归 |
| 多 DPI 气泡尾巴适配 | MEDIUM | 320dpi/480dpi/640dpi 下无错位未验证 | Round 4 真机回归 |
| 搜索高亮脉冲动画 | LOW | 真机流畅度未验证 | Round 4 真机回归 |

## What was intentionally deferred（有意延期）

- 暗色模式（不在本轮范围）
- Tauri 桌面工具（改用轻量 Flask Web 查看器）
- @me 高亮（不在本轮范围）
- 消息分享/转发（不在本轮范围）
- 批量转发/删除（不在本轮范围）
- 实时消息（不在本轮范围）
- Python 自动化脚本 + Flask 查看器编译验证（代码已完成，Windows Task Scheduler 集成待用户配置）

## Carry-forward notes

- 事实：聊天媒体存储路径 `chat-imports/` 与照片模块 `originals/` 完全隔离
- 事实：行级同步仿 ledger 架构，`ChatImportedSyncService` 独立于 `LedgerSyncService`
- 事实：R6 代码拆分后 10 个文件使用 `internal` 可见性互相引用
- 相邻模块待复访：ledger 的同步模式已被 chatShower 复用为行级同步模板
