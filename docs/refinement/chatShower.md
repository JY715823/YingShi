# ChatShower — 聊天记录查看器

> 一个模块的精修 brief。保持更新以便后续 turn 从这里恢复。

- Module key: `chatShower`
- Status: `device_qa`
- Last updated: `2026-07-02`
- Primary surfaces: `android + server`
- Linked server brief: `E:\Study\App\YingShi-Server\docs\refinement\chatShower-server.md`

## Module Goal

- User value: 提供 QQ 级别的聊天记录浏览体验，支持大规模导入（10GB+），多设备同步查看，自动化导入流程
- Business or product intent: 聊天记录是映世"生活"板块的核心内容之一，需要和 QQ 原生体验对齐的视觉质量、交互流畅度、数据可靠性
- Success criteria:
  1. UI 视觉和交互达到 QQ 水平（气泡尾巴、连续消息合并、微阴影、过渡动画、群聊彩色昵称）
  2. 后端从 snapshot blob 拆为 5 张关系表 + 行级增量同步 + 独立媒体通道
  3. 10GB ZIP 导入可稳定完成，支持断点续传
  4. 本地定时脚本自动将 QCE 导出推送到 Server，支持补传漏掉的导出
  5. 轻量桌面查看器可视化上传历史
  6. 手机端变为纯查看器，从 Server 拉取数据

## Current State

### Android 端

- **UI**: `ImportedChatScreen.kt` 单文件 4362 行，~40 个 Composable，功能齐全但视觉层面和 QQ 有差距
- **ViewModel**: `ImportedChatViewModel.kt` 862 行，状态管理完善
- **数据层**: Room 5 张表（chats/messages/participants/resources/search），`ImportedChatRepository.kt` 2601 行
- **导入管线**: 全量解压 → 逐条消息事务（含文件 IO）→ 双遍解析，10GB 级 ZIP 有严重性能瓶颈
- **同步**: `ChatSyncBridge` 全量 snapshot push/pull，媒体文件不同步
- **QQ 表情**: `QFaceCatalog.kt` 完整实现，内联渲染
- **媒体播放**: Coil 图片 + ExoPlayer 视频 + SILK 音频解码
- **导航**: 嵌入 Life tab，boolean 切换，无 Compose Navigation

### Server 端

- **存储**: `chat_snapshots` 表，每 library 一条，`payload_json` TEXT 存整个 DB 序列化 JSON
- **API**: `GET/PUT /api/chat/imported/snapshot`，全量 blob push/pull
- **媒体**: 无。Server 不存储任何聊天媒体文件
- **同步**: 不在 `SyncService` 的模块版本列表中，独立 versionMillis 机制

### QCE 导出工具

- 位于 `E:\YuLe\NapCat-Framework-QCE-v5.5.64`
- 内置 cron 定时导出（SimpleCronScheduler），支持 daily/weekly/monthly/custom
- 导出格式：chunked JSONL + ZIP（manifest.json + chunks/*.jsonl + resources/ + avatars.json）
- 定时导出目录：`%USERPROFILE%\.qq-chat-exporter\scheduled-exports\`
- REST API 在 localhost:40653（QCE WebUI）
- 支持时间范围过滤增量导出

## Your Current Ideas

1. UI 做到 QQ 同等水平
2. 后端拆表（和 ledger 统一架构），媒体资源独立同步，和照片模块存储隔离
3. 导入优化：10GB ZIP 稳定导入
4. 自动化：QCE 定时导出 → 脚本自动上传 Server → 手机纯查看器
5. 其余可优化的地方多发掘

## Codex Recommendations

### Recommended to finish in this module

#### R1: UI — QQ 级视觉升级

- **气泡尾巴**: 用 `Path` + `Modifier.drawBehind` 绘制 QQ 标志性三角尾巴。左消息尾巴朝左，右消息朝右。尾巴和气泡圆角平滑衔接
  - 为什么值得做: 这是 QQ 最标志性的视觉元素，没有尾巴一眼就能看出"不是 QQ"
- **连续消息头像合并**: 同发送者连续消息（间隔 < 5min）合并显示。只在第一条显示头像和昵称，后续消息缩进对齐但不显示头像
  - 为什么值得做: 减少视觉噪声，群聊中尤其明显。当前每条消息都显示头像，信息密度低
- **气泡微阴影**: 1dp `shadowElevation` + 微妙 `tonalElevation`，摆脱纯平面感
  - 为什么值得做: 当前所有 Surface 都是 0dp elevation，视觉上像 Web 页面而非原生 App
- **群聊彩色昵称**: 根据 `senderStableKey.hashCode()` 从预定义调色板分配颜色
  - 为什么值得做: 群聊中快速区分发言人，QQ 核心体验
- **页面过渡动画**: 列表→详情 `AnimatedContent` + slideIn(End) / fadeOut；媒体查看器 shared-element transition
  - 为什么值得做: 当前列表→详情是瞬间切换，体验突兀
- **搜索高亮脉冲动画**: 跳转到目标消息时背景高亮有呼吸→渐隐效果（2.4s）
  - 为什么值得做: 当前是瞬间变色，用户难以定位哪条消息被高亮
- **图片加载骨架屏**: 图片加载中显示 shimmer 占位
  - 为什么值得做: 当前加载时无任何反馈，大图片加载时视觉空白
- **Material 3 DatePicker**: 替换平台原生 `DatePickerDialog`
  - 为什么值得做: 原生 DatePicker 在新版 Android 上视觉不一致
- **聊天背景纹理**: 用淡色 pattern（细点/网格）替换纯 flat 背景色
  - 为什么值得做: QQ 的聊天背景有微妙纹理，纯 flat 色显得廉价
- **消息长按多选模式**: 进入选择模式后可批量选择消息，支持批量复制/分享
  - 为什么值得做: QQ 基础功能，用户有批量操作需求
- **媒体查看器保存图片**: 图片长按菜单增加"保存到相册"
  - 为什么值得做: 基础功能缺失

#### R2: Server 拆表 + 行级同步

- 从 snapshot blob 拆为 5 张关系表：`imported_chats` / `imported_messages` / `imported_participants` / `imported_resources` / `imported_message_search`
- 走类似 ledger 的行级增量同步协议：`POST /api/chat/imported/sync`
- 客户端 changelog 跟踪变更，每次导入后触发增量同步
- 媒体文件不在行级同步中传输，走独立媒体通道

#### R3: 独立媒体通道

- Server 新增：
  - `POST /api/chat/imported/media/upload` (multipart, 支持批量)
  - `GET /api/chat/imported/media/{key}` (流式下载, Content-Disposition)
  - `HEAD /api/chat/imported/media/{key}` (存在性检查, 用于去重)
- 存储路径: `chat-imports/{libraryId}/{chatStableKey}/resources/{storedFileName}`
- 和照片模块的 `media/` / `albums/` 存储路径完全隔离
- MD5 去重: 上传前 HEAD 检查，相同 MD5 跳过
- 客户端导入完成后，后台批量上传本地媒体（Semaphore 限制并发 4）
- 另一设备 hydrate 时：拉取元数据后，按需懒加载媒体（Coil 缓存 + 本地持久化）

#### R4: 10GB 导入管线优化

- **流式 ZIP 处理**: 不全部解压到临时目录。manifest.json 和 chunks/*.jsonl 直接从 ZipInputStream 流式读取。资源文件按需提取到永久存储
- **批量 DB 操作**: 按 chunk 文件为单位，一个 chunk 一个事务，批量 INSERT（`insertMessages(List)`）
- **文件 IO 移出事务**: 先并行复制资源文件到永久存储，再做 DB 写入。事务只包含纯 SQL
- **断点续传**: SharedPreferences 记录已导入的 chunk 文件名 + 消息偏移，中断后从断点恢复
- **单遍解析**: 合并双遍为单遍。sender 收集融入主循环，不再单独 first-pass
- **并行资源复制**: Semaphore(4) 限制并发度，并行复制资源文件
- **解压进度报告**: ZIP entry 级别报告解压进度
- **WakeLock 动态续期**: 接近 2hr 上限时自动续期
- **Service 改为 START_STICKY**: 系统杀后可重启

#### R5: 自动化脚本 + 轻量查看器

- Python 脚本 `chat-sync-script.py`：
  - 扫描 QCE 定时导出目录 `%USERPROFILE%\.qq-chat-exporter\scheduled-exports\`
  - 维护 `processed.json` 记录已处理的 ZIP 文件（路径 + 大小 + mtime + MD5 前 8 位 + 上传时间 + 上传结果 + Server 返回统计）
  - 每次运行时，对比目录中所有 ZIP 和 processed.json，找出未处理的（包括电脑关机期间漏掉的）
  - 逐个调用 Server `POST /api/chat/imported/upload-zip` 上传
  - 上传成功后更新 processed.json（含消息数/资源数/耗时）
  - 支持 `--dry-run` 预览模式
  - 配合 Windows 任务计划程序，每小时或每天自动运行
- Server 端 `POST /api/chat/imported/upload-zip`：
  - 接收 ZIP 文件，服务端解析（Java ZipInputStream）
  - 独立实现 QCE manifest + JSONL 解析（Java，不依赖 Android 代码）
  - 写入 5 张关系表（批量 INSERT）
  - 提取媒体到 ObjectStorageService 存储（key 前缀 `chat-imports/{libraryId}/{chatStableKey}/resources/`）
  - 返回统计信息（chats/messages/resources/mediaStored）
- 轻量桌面查看器（Python + 本地 Web UI）：
  - 读取 `processed.json` 展示上传历史表格（时间/文件名/大小/消息数/资源数/状态/耗时）
  - 简单统计面板（总导入次数、总消息数、总资源数、最近一次上传时间）
  - 手动触发"立即同步"按钮（调用脚本的上传逻辑）
  - Flask/FastAPI 起本地 HTTP + 单页 HTML，浏览器打开 `localhost:PORT` 查看
  - 不用 Tauri——功能太简单，不值得起 Rust 项目

#### R6: 代码拆分

- `ImportedChatScreen.kt` (4362 行) 拆分为：
  - `ChatListScreen.kt` — 聊天列表
  - `ChatDetailScreen.kt` — 消息时间线主界面
  - `ChatBubbleComponents.kt` — 气泡、尾巴、头像、昵称
  - `ChatMediaComponents.kt` — 图片/视频/文件/音频卡片
  - `ChatSearchComponents.kt` — 搜索栏、结果面板、日期跳转
  - `ChatMediaViewer.kt` — 全屏媒体查看器（图片/视频 pager + zoom）
  - `ChatAudioPlayer.kt` — 音频播放状态机
  - `ChatPdfPreview.kt` — PDF 预览
  - `ChatTopBar.kt` — 顶栏 + 管理按钮

### Defer only with explicit acceptance

- **暗色模式**: 当前 app 整体无暗色主题，聊天模块单独做暗色意义不大。等 app 层面统一支持时再跟进
- **桌面端 Tauri 导出工具**: 先用 Python 脚本 + 轻量 Web 查看器方案，完整 Tauri 桌面工具以后再做
- **"@我" 高亮**: 需要解析消息中的 @mention 结构，当前 QCE 导出的 JSONL 中 @信息可能混在纯文本里，解析规则需要调研。可以后续做
- **消息分享**: 需要系统 ShareSheet 集成，优先级低于核心功能

## Key Questions

- [x] 后端拆表还是保持 blob？ → **拆表，5 张关系表 + 行级同步**
- [x] 暗色模式？ → **暂不做**
- [x] 导入优化程度？ → **全部做（流式 ZIP + 批量 DB + 断点续传 + 并行复制 + 单遍解析）**
- [x] 代码拆分？ → **拆分**
- [x] 自动化方案？ → **B+C 组合：Server 端 ZIP 上传解析 + 本地 Python 脚本定时推送**
- [x] 桌面工具？ → **做轻量 Web 查看器（Python + Flask/FastAPI），不做 Tauri**
- [x] Server 端 ZIP 解析是否要复用 Android 端的 QCE 解析逻辑？还是独立实现？
  - → Server 用 Java 独立实现 manifest + JSONL 解析（逻辑不复杂），不依赖 Android 代码
- [x] 媒体文件是 Server 解析 ZIP 时直接提取存储，还是客户端上传？
  - → Server 解析 ZIP 时直接提取媒体到 ObjectStorageService（一次 IO，省掉客户端上传带宽）。客户端媒体上传通道仅作为 fallback
- [x] 行级同步的初始 hydrate 是否要支持分页？聊天消息量可能很大
  - → 首次 sync 返回全量元数据（不含媒体文件），媒体走懒加载
- [x] 媒体存储位置？
  - → 同一个 MinIO 桶 `yingshi-media`，key 前缀 `chat-imports/` 和照片 `originals/` 隔离。共用 ObjectStorageService

## Scope Boundaries

### In scope

- UI 视觉升级到 QQ 水平（11 项具体改进）
- Server 拆表（5 张关系表）+ 行级增量同步
- 独立媒体通道（上传/下载/去重）
- 10GB 导入管线全面优化
- Server 端 ZIP 上传 + 解析端点
- 本地 Python 自动化脚本（补传机制）+ 轻量 Web 查看器（上传历史可视化）
- ImportedChatScreen.kt 拆分为 9 个文件
- Android 端适配新的行级同步协议（替换 ChatSyncBridge）

### Out of scope

- 暗色模式
- 桌面端 Tauri 导出工具
- @我 高亮
- 消息分享/转发
- 消息选择后的批量转发/删除（多选模式只做 UI 框架和复制）
- 实时消息（本模块是只读查看器）

### Non-negotiables

- 聊天媒体存储路径必须和照片模块完全隔离
- 断点续传必须可靠（10GB 导入可能跨数小时）
- 行级同步必须和 ledger 架构模式统一（changelog 表 + 增量请求）
- Server 端 ZIP 解析必须兼容 QCE v5.5.64 的 manifest 格式

### Failure and fallback expectations

- 导入中断: 断点续传从上次位置恢复，不丢数据不重复
- 媒体上传失败: 记录失败列表，下次打开 app 时重试
- 同步冲突: Server-wins 策略（聊天数据是只读导入的，不存在双向编辑冲突）
- Server ZIP 解析失败: 返回明确错误信息，客户端可 fallback 到本地解析 + 上传

## Related Modules

- **ledger**: 架构参考。行级同步协议、changelog 表、SyncBridge 模式都从 ledger 复制
  - Recheck: 确保 chat 的 sync 不影响 ledger 的 sync 版本追踪
- **photos/albums**: 媒体存储需要隔离。不能复用照片模块的 MediaController 路径
  - Recheck: 确认存储路径不冲突，ObjectStorageService 的 key 前缀隔离
- **life_home**: 入口模块。chatShower 从 Life 页面进入
  - Recheck: 确保入口回调 `onOpenChatViewer` 不受影响
- **noticeCenter**: 推送通知中有 chat 类别（默认关闭）
  - Recheck: 如果 Server 端导入完成后要推送通知，需走 PushNotificationService 的 chat 类别
- **syncVersions**: 当前 chat 不在 SyncService 的模块版本列表中
  - Recheck: 拆表后需要将 chat 加入 SyncVersionsResponse，或保持独立 sync 端点

## Frontend and Backend Contracts

### Client state and entry points

- Screens: `ImportedChatScreen`（入口）→ `ImportedChatListScreen` / `ImportedChatDetailScreen`
- ViewModel: `ImportedChatViewModel`
- Repository: `ImportedChatRepository`
- Sync: `ChatSyncBridge` → 改为 `RemoteChatSyncBridge`（行级同步）
- Import: `ChatImportRuntime` + `ChatImportForegroundService`
- Navigation: Life tab → boolean `chatViewerRouteActive`

### Server endpoints and payloads

**新增端点：**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat/imported/sync` | 行级增量同步（类似 ledger） |
| `POST` | `/api/chat/imported/upload-zip` | 上传 ZIP，Server 端解析入库 |
| `POST` | `/api/chat/imported/media/upload` | 上传单个媒体文件（multipart） |
| `GET` | `/api/chat/imported/media/{key}` | 下载媒体文件（流式） |
| `HEAD` | `/api/chat/imported/media/{key}` | 检查媒体是否存在（MD5 去重） |

**保留端点（deprecated，向后兼容）：**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/chat/imported/snapshot` | 旧版全量快照拉取 |
| `PUT` | `/api/chat/imported/snapshot` | 旧版全量快照推送 |

**Sync 请求 DTO（`ChatImportedSyncRequest`）：**
```
{
  lastSyncVersionMillis: long,
  changes: {
    chats: List<ChatRow>,
    messages: List<MessageRow>,
    participants: List<ParticipantRow>,
    resources: List<ResourceRow>,
    messageSearch: List<MessageSearchRow>,
    deletedRowIds: List<DeletedRowRef>
  }
}
```

**Sync 响应 DTO（`ChatImportedSyncResponse`）：**
```
{
  versionMillis: long,
  changes: {
    chats: List<Map<String, Object>>,
    messages: List<Map<String, Object>>,
    participants: List<Map<String, Object>>,
    resources: List<Map<String, Object>>,
    messageSearch: List<Map<String, Object>>,
    deletedRowIds: List<String>
  }
}
```

**Upload ZIP 请求：**
```
POST /api/chat/imported/upload-zip
Content-Type: multipart/form-data
Body: file=<zip-file>

Response: { success: true, stats: { chats: N, messages: N, resources: N, mediaStored: N } }
```

### Shared rules

- Auth: 所有端点 `@AuthRequired`，scoped to `currentUser.libraryId()`
- 媒体存储 key 格式: `chat-imports/{libraryId}/{chatStableKey}/resources/{storedFileName}`
- 同步版本: 使用 `SyncVersionTracker` 标记 chat 模块本地变更时间

## UI and Visual Details

### 气泡设计

- 形状: 圆角矩形（18dp 外角，6dp 内角）+ 三角尾巴
- 尾巴: 用 `Path.cubicTo` 绘制平滑曲线三角形，宽 8dp，高 10dp
- 颜色: 自己 = `softGreenContainer`，他人 = `raisedSurface`
- 阴影: 1dp shadow, 0.08 alpha
- 最大宽度: `min(300dp, screenWidth * 0.72)` 自适应

### 连续消息合并

- 判断条件: 同一 senderStableKey + 时间间隔 < 5 分钟 + 非系统消息
- 合并效果: 第一条显示头像 + 昵称，后续消息缩进到头像右边缘对齐
- 间距: 连续消息间距 2dp，非连续 8dp

### 群聊彩色昵称

- 调色板: 8 种预设颜色（红/橙/黄/绿/青/蓝/紫/粉）
- 分配: `palette[senderStableKey.hashCode() % palette.size]`
- 仅群聊生效，私聊不显示昵称

### 过渡动画

- 列表→详情: `AnimatedContent` + `slideInHorizontally(initialOffsetX = { it })` + `fadeIn`, duration 300ms
- 媒体查看器: `SharedTransitionLayout` + `sharedElement` 从气泡缩略图到全屏查看器
- 搜索高亮: `animateColorAsState` 从 highlight 色渐变到 transparent, duration 2400ms

### 聊天背景

- 使用 `Canvas` 绘制淡色点阵 pattern（点大小 1dp，间距 16dp，颜色 `appBackground` 深 3%）
- 或加载 drawable 资源纹理

## Interaction Feedback

- Loading: 聊天列表加载显示 skeleton 占位（3-5 个卡片骨架）
- Empty: 空列表显示插画（待设计）+ "还没有导入聊天记录" + 导入按钮
- Error: 导入失败显示具体原因 + "重试"按钮
- Success: 导入完成显示统计摘要（成功/跳过/失败/资源数/耗时）
- Import progress: 通知栏进度条 + 阶段文字（解压中/解析消息/写入数据库/上传媒体）
- Sync error: 顶部 Snackbar "同步失败，将在下次打开时重试"
- Media loading: 图片 shimmer 骨架屏，视频显示 poster frame + 加载指示器
- Offline: 已缓存数据可离线浏览，未缓存媒体显示占位符

## Deployment Readiness

- 所有 5 张 Server 表 migration 就绪
- 行级同步协议端到端测试通过
- 媒体上传/下载通道测试通过
- 10GB ZIP 导入压力测试通过
- 自动化脚本在 Windows 任务计划程序下稳定运行
- 向后兼容旧版 snapshot API（过渡期）
- Android 端 `ChatSyncBridge` 切换到行级同步后，旧 snapshot 数据能迁移

## Hidden Impact Checklist

- Notifications: Server 端 ZIP 导入完成后，可通过 PushNotificationService 的 `chat` 类别推送通知。默认关闭，不影响
- Auth: 无变化，所有新端点 `@AuthRequired`
- Upload: 新增媒体上传通道，和照片模块的 UploadController 完全隔离。需确认 ObjectStorageService 的 key 前缀不冲突
- Comments: 无影响
- Viewer: 聊天媒体查看器独立于照片 Viewer。共享 ExoPlayer 和 Coil 依赖但不共享状态
- Settings: 无新设置项
- Analytics or logging: 导入统计可作为后续分析数据源，本次不实现
- Cache or offline: 聊天数据本地 Room 缓存，媒体文件本地持久化。离线可浏览已缓存内容
- Permissions: 导入需要存储权限（已有），媒体保存到相册需要 WRITE_EXTERNAL_STORAGE（Android < 29）
- Copy and empty states: 空列表需要插画和引导文案，导入失败需要明确错误信息

## Plan Self-check

- Recommendation quality: 11 项 UI 改进 + 5 项架构改进 + 8 项导入优化，覆盖用户所有需求点。UI 改进基于对 QQ 实际界面的分析，不是泛泛而谈
- Scope pressure test: 范围较大但可分批次执行。建议实施顺序：R6(代码拆分) → R2(Server 拆表) → R3(媒体通道) → R4(导入优化) → R1(UI 升级) → R5(自动化脚本)
- Contract and dependency pressure test: 行级同步协议直接参考 ledger 已验证的模式。媒体通道和照片模块存储隔离通过 key 前缀保证。旧 snapshot API 保留向后兼容
- UX state pressure test: 覆盖 loading/empty/error/success/offline/import-progress 六种状态。媒体懒加载的 offline 场景通过本地缓存兜底
- Deployment readiness pressure test: 关键阻塞项是 Server 拆表 + 同步协议，这是所有其他改进的基础
- Risks to watch in implement:
  1. Server 端 ZIP 解析需要完整复现 Android 端的 QCE 解析逻辑（消息类型、资源类型、表情处理）
  2. 行级同步首次 hydrate 数据量可能很大（百万级消息），需要考虑分页或流式传输
  3. 断点续传的状态管理需要仔细设计，避免部分导入导致数据不一致
  4. 媒体文件去重（MD5）在大量小文件场景下可能有性能问题

## Implementation Notes

### Client

#### R6: 代码拆分 (completed)

`ImportedChatScreen.kt` (4362 行) 拆分为 10 个文件，编译通过：

- `ChatInternals.kt` — 共享类型、工具函数、格式化器、image request builders、MissingMediaPlaceholder
- `ChatAudioPlayer.kt` — 音频播放状态机 (ChatAudioPlayerState, rememberChatAudioPlayer)
- `ChatPdfPreview.kt` — PDF 预览 (ImportedChatPdfPreview, PdfPageImage)
- `ChatTopBar.kt` — 顶栏 + 按钮组件 (ImportedChatTopBar, ChatIconActionButton, ChatDialogActionButton, ChatSheetActionButton)
- `ChatBubbleComponents.kt` — 气泡、回复预览、内联文本渲染、头像 (ChatMessageBubble, InlineMessageText, AvatarBadge)
- `ChatMediaComponents.kt` — 图片/视频/文件/音频/JSON/通话/未知消息卡片 + 视频封面缓存
- `ChatSearchComponents.kt` — 搜索栏、结果面板、导航条 (ChatSearchBar, SearchResultsPanel, SearchNavigationBar)
- `ChatMediaViewer.kt` — 全屏媒体查看器 + 缩放手势 + 视频播放 (ImportedChatMediaViewer, ChatViewerImageCanvas, LocalVideoPlayer)
- `ChatDetailScreen.kt` — 主入口 + 消息时间线 + 边缘拉取分页 (ImportedChatScreen [public], ImportedChatDetailScreen, buildTimelineItems)
- `ChatListScreen.kt` — 聊天列表 + 摘要卡片 + 管理面板 (ImportedChatListScreen, ImportedChatSummaryCard, ChatManagementSheet, ImportInfoDialog)

跨文件可见性：被多个文件调用的类型/函数标记为 `internal`，文件内部使用的保持 `private`。

#### R2: Server 拆5张关系表 + 行级增量同步 (completed)

**新增文件：**
- `V25__chat_imported_tables.sql` — 5 张表 DDL + 性能索引
- `domain/chat/` — 5 个 Entity (ImportedChatEntity, ImportedMessageEntity, ImportedParticipantEntity, ImportedResourceEntity, ImportedMessageSearchEntity)
- `repository/chat/` — 5 个 Repository (含 findByLibraryIdAndUpdatedAtAfter 同步增量查询)
- `dto/chat/` — 5 个 DTO (ChatImportedSyncRequest/Response, ClientChangesDto, ChangesDto, SyncRows)
- `service/chat/ChatImportedSyncService.java` — 行级同步算法 (和 LedgerSyncService 同模式)

**修改文件：**
- `ChatSnapshotController.java` — 新增 `POST /api/chat/imported/sync` 端点
- `SyncVersionsResponse.java` — 新增 `chatVersion` 字段
- `SyncService.java` — 注入 ImportedChatRepository，计算 chatVersion，纳入 notificationVersion

#### R3: 独立媒体通道 (completed)

**Server 新增：**
- `ChatMediaService.java` — upload (MD5 去重) / download / exists
- `ChatMediaController.java` — `POST /upload` + `GET /**` + `HEAD /**`
- `ImportedResourceRepository` — 新增 `findFirstByLibraryIdAndMd5AndStoredObjectKeyIsNotNull`

**Client 新增：**
- `ChatMediaApi.kt` — Retrofit 接口 (upload/download/HEAD)
- `ChatMediaSyncBridge.kt` — 媒体上传/下载桥接 (isResourceStored, uploadResource, downloadResource)
- `RemoteServiceFactory.kt` — 新增 chatMediaApi 属性

存储 key 格式: `chat-imports/{libraryId}/{chatStableKey}/resources/{storedFileName}`，和照片 `originals/` 完全隔离。

#### R4: 10GB 导入管线全面优化 (completed)

**修改文件：**
- `ImportedChatRepository.kt` — 单遍解析 (消除 first pass)、批量 DB 操作 (每 chunk 一个事务)、文件 IO 移出事务、断点续传 (SharedPreferences checkpoint)、预加载 identity maps
- `ChatImportDao.kt` — 新增批量查询方法 (findParticipantsByStableKeys, findMessageIdentitiesByStableKeys, deleteResourcesForMessages, deleteMessageSearches)
- `ChatImportForegroundService.kt` — START_STICKY、WakeLock 90分钟自动续期

**性能提升：** 10GB/100K消息场景，从 ~100K 个独立事务 → ~10 个批量事务 (每 chunk 一个)，预期 DB 写入阶段 5-20x 加速。

#### R1: UI 升级 QQ 水平 (completed)

11 项改进全部实现：
1. 气泡微阴影 — shadowElevation 1dp
2. 群聊彩色昵称 — 8 色调色板，hash 分配
3. 连续消息头像合并 — isConsecutive 标记，隐藏头像+昵称
4. 气泡尾巴 — drawBehind + Path.cubicTo，入/出方向各不同
5. 搜索高亮脉冲动画 — animateColorAsState 2400ms
6. 图片加载骨架屏 — shimmer 渐变动画
7. Material 3 DatePicker — 替换原生 DatePickerDialog
8. 聊天背景纹理 — Canvas 点阵 pattern
9. 页面过渡动画 — AnimatedContent + slideInHorizontally
10. 媒体查看器保存图片 — 长按菜单 + MediaStore
11. 消息长按多选模式 — selectionMode 状态 + 批量复制

### Server

#### R2-R3, R5 Server 端 (completed)
- 5 张关系表 + 行级同步 + 媒体通道 + ZIP 上传解析端点全部就绪
- `ChatImportedZipService.java` — QCE manifest + JSONL 解析，批量写入 5 表，媒体提取到 ObjectStorageService
- `ChatMediaService.java` + `ChatMediaController.java` — upload/download/HEAD
- `POST /api/chat/imported/upload-zip` — 接收 ZIP，服务端解析入库

#### R5: 自动化脚本 + 桌面查看器 (completed)
- `tools/chat-sync-script.py` — 扫描 QCE 导出目录，维护 processed.json，上传未处理 ZIP，支持 --dry-run
- `tools/chat-sync-viewer.py` — Flask 本地 Web UI (:5199)，上传历史表格 + 统计面板 + "立即同步"按钮
- `tools/chat-sync-config.template.json` — 配置模板

### Design
- (待实施)

### Interaction Feedback
- (待实施)

## Post-implement Self-check

### 新增行为验证
- R6 代码拆分: 10 个文件编译通过，原文件已删除，所有跨文件引用使用 `internal` 可见性
- R2 Server 拆表: V25 migration + 5 entity + 5 repository + sync service + controller endpoint 编译通过
- R3 媒体通道: upload/download/HEAD 端点 + 客户端 API + SyncBridge 编译通过
- R4 导入优化: 单遍解析 + 批量事务 + 断点续传 + START_STICKY + WakeLock 续期编译通过
- R1 UI 升级: 11 项改进编译通过（气泡尾巴、彩色昵称、连续消息合并、阴影、动画等）
- R5 自动化: Python 脚本 + Flask 查看器 + Server ZIP 解析端点编译通过

### 数据一致性
- 行级同步协议和 ledger 模式一致：updated_at 时间戳追踪，双向 sync 在一次调用中完成
- 媒体存储 key 前缀 `chat-imports/` 和照片 `originals/` 完全隔离，共用 ObjectStorageService
- 断点续传基于 chunk 文件名 checkpoint，不丢数据不重复

### 新耦合检查
- SyncVersionsResponse 新增 chatVersion 字段 — 客户端需要适配新字段（向后兼容：旧客户端忽略新字段）
- ChatSnapshotController 新增 sync 和 upload-zip 端点 — 旧 snapshot API 保留向后兼容
- ImportedChatRepository.kt 的 importFromZip 方法签名未变，内部优化透明

## Implement Test Plan

### 已本地验证
- Android 端 compileDebugKotlin 编译通过（10 个拆分文件 + UI 改进 + 导入优化 + 媒体 API）
- Server 端 mvn clean compile 编译通过（272 source files，5 表 + sync + media + zip 端点）

### 需要真机回归测试
- [ ] 导入一个小型 ZIP（<100MB），验证消息/资源/头像完整导入
- [ ] 导入一个大型 ZIP（>1GB），验证批量事务和断点续传
- [ ] 导入中断后恢复，验证 checkpoint 跳过已处理 chunk
- [ ] 群聊中验证彩色昵称和连续消息头像合并
- [ ] 气泡尾巴在不同屏幕密度下显示正常
- [ ] 搜索高亮脉冲动画效果
- [ ] 媒体查看器中保存图片到相册
- [ ] 多选模式批量复制
- [ ] 页面过渡动画流畅度

### Server 端测试
- [ ] 通过 POST /api/chat/imported/upload-zip 上传 QCE 导出 ZIP，验证 5 表写入
- [ ] 通过 POST /api/chat/imported/sync 测试行级增量同步
- [ ] 通过 POST/GET/HEAD /api/chat/imported/media 测试媒体上传/下载/去重
- [ ] 验证 GET /api/sync/versions 返回 chatVersion 字段

### 自动化脚本测试
- [ ] `python chat-sync-script.py --dry-run` 预览未处理 ZIP
- [ ] `python chat-sync-viewer.py` 启动查看器，验证历史表格和统计面板
- [ ] 配合 Windows 任务计划程序定时运行

### 未验证风险
- 10GB ZIP 实际导入性能未压力测试
- QCE v5.5.64 JSONL 格式细节可能需要根据实际数据微调解析逻辑
- 气泡尾巴在低 DPI 设备上的渲染效果需确认

## Real-device Issue Log

### V-001: 导入后数据被清空 (severity: critical, fixed)
- 日期: 2026-07-02
- 复现: 重新上传 ZIP 时，之前导入的数据全部丢失
- 根因: `ChatImportRuntime.startImport()` 在导入前调用 `hydrateFromRemoteIfNeeded()`，触发 `RemoteChatSyncBridge.hydrate()` → `replaceLocalSnapshot()` 清空所有 5 张表。每次 startImport 创建新的 RemoteChatSyncBridge 实例，hydrated 标志不复用，导致每次导入都执行全量替换
- 修复: 导入流程改用 `NoOpChatSyncBridge`，移除导入前的 hydrate 调用。hydration 只在 ViewModel init 时执行（查看场景），不在导入场景执行
- 文件: `ChatImportRuntime.kt`

### V-002: 上传时列表出现重复项 (severity: high, fixed)
- 日期: 2026-07-02
- 复现: 导入过程中聊天列表短暂显示重复消息
- 根因: 当 `resolvedChatTarget.existingChat` 为 null（新会话首次导入），identity map 为空 (`emptyMap()`)。所有消息被分类为"新建"（messageLocalId=0），INSERT OR REPLACE 触发 UNIQUE 约束冲突，SQLite 删除旧行并插入新行（新的 messageLocalId），导致级联删除 resources 和 search entries。reactive Flow 在这个 delete-insert 交叉窗口发出重复状态
- 修复: identity map 改用 `dao.getMessageIdentityRows(chatId)` 加载（chatId 在 chat upsert 后始终有效），participant map 同理
- 文件: `ImportedChatRepository.kt`

### V-003: 系统通知进度永远不结束 (severity: high, fixed)
- 日期: 2026-07-02
- 复现: 导入完成后通知栏进度条永远不消失
- 根因: `START_STICKY` 导致进程被杀后服务重启，`ChatImportRuntime.state` 残留 `isRunning=true`，observer 盲目信任该状态并持续显示进度通知
- 修复: 恢复 `START_NOT_STICKY`
- 文件: `ChatImportForegroundService.kt`

### V-004: 头像/表情/图片不可用 (severity: critical, fixed)
- 日期: 2026-07-02
- 复现: 所有图片显示"图片当前不可用"，表情显示为文字标签
- 根因: V-001 的数据清空 + checkpoint 残留的连锁反应。DB 被 hydrate 清空后，checkpoint 文件仍然记录之前的 chunk 完成状态。重新导入同一个 ZIP 时，所有 chunk 被跳过（认为已完成），资源文件不会被复制。DB 中有消息记录但资源文件不存在于磁盘
- 修复: 添加 checkpoint 有效性验证——如果目标会话在 DB 中没有消息，说明 checkpoint 已过期，自动清空并强制完整重新导入
- 文件: `ImportedChatRepository.kt`, `ChatImportDao.kt` (新增 getMessageCountForChat)

### V-005: 回复消息点击无法跳转 (severity: high, fixed)
- 日期: 2026-07-02
- 复现: 点击回复预览卡片不跳转到目标消息
- 根因: `LaunchedEffect` 中 `onConsumePendingJump()` 和 `onClearJumpContext()` 在 `if (index >= 0)` 之外无条件执行。当 timelineItems 尚未更新（race condition），indexOfFirst 返回 -1，但 pending jump 已被清除。后续 timelineItems 更新时 LaunchedEffect 重新触发，但 pendingJumpMessageLocalId 已为 null
- 修复: 将 consume/clear 移入 `if (index >= 0)` 分支，仅在成功找到并滚动到目标消息后才清除 pending jump
- 文件: `ChatDetailScreen.kt`

### V-006: 新导入消息的资源和搜索全部失效 (severity: critical, fixed)
- 日期: 2026-07-03
- 复现: 新导入的消息中图片显示"不可用"，搜索无法找到新消息
- 根因: R4 批量优化引入的回归。`dao.insertMessages(newMessages)` 返回 `Unit`（丢弃了 Room 自增 ID），导致新消息的 resource 和 search 实体的 `messageLocalId = 0L`。资源行指向不存在的消息 ID=0，搜索行同理。合并消息（已有 ID）不受影响
- 修复: (1) `insertMessages` 返回类型改为 `List<Long>` 捕获自增 ID；(2) 新消息的 resource/search 实体延迟到 `insertMessages()` 之后构建，使用生成的 ID；(3) 合并消息保持原有逻辑（循环内构建）
- 文件: `ChatImportDao.kt`, `ImportedChatRepository.kt`

## Validation Snapshot
### Verified
- (待验证)

### Pending
- (待验证)

### Blocked
- (待验证)

## Closeout Summary
- (待关闭时填写)

## Carry-forward Notes
- (待关闭时填写)

## Closeout Self-check
- (待关闭时填写)
