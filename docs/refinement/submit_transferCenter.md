# 上传与传输中心

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `submit_transferCenter`
- Status: `closed`
- Last updated: `2026-06-24`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 上传和导入任务可恢复、可筛选、可暂停/取消/重试，退出 App 后仍能看到最近传输记录。
- Business or product intent: 让系统媒体导入、底部加号导入、新建/加入小相册成为可靠的照片沉淀入口。
- Success criteria: 批量任务按一次操作分组；已完成记录默认可折叠；30 天内记录可恢复；导入完成不强行跳走；视频上传与 Viewer 拖动进度更稳。

## Current State
- What exists today: Android 已用 `operationId` 把一次多选任务分组，服务端已有 `upload_tasks` 持久化和单任务查询。
- Known constraints: 当前上传不是分片协议，暂停不能做真正断点续传；重启后正在传的 HTTP 请求无法继续原连接。
- Relevant code or docs: `TransferCenterScreen.kt`、`LocalSystemMediaBridgeRepository.kt`、`UploadController.java`、`UploadService.java`、`docs/contracts/upload-api.md`。

## Your Current Ideas
- 视频上传不要有各种人为限制，排查此前视频上传容易失败的问题。
- 传输中心记录要持久化，退出 App 再进入不能丢。
- 右上角三横线分类菜单；清空当前分类按钮常驻。
- 一次多选作为一次任务；进行中展开，完成后可折叠，下次进入默认折叠。
- 从条目跳转回来传输中心要保留之前的位置。
- 导入完成不要打断当前页面；回照片流时再定位到新媒体。
- Viewer 视频拖动进度条不能闪回开头、卡顿或闪退。

## Codex Recommendations
### Recommended to finish in this module
- 后端暴露上传历史列表和隐藏记录接口，保存 operation 元数据。
- Android 本地持久化传输中心 UI 状态、分类、折叠和滚动位置。
- 上传队列限制并发，暂停按“停止当前流/等待队列，继续时重传该项”处理。
- Viewer seek 期间冻结目标进度显示，避免海报和进度条闪回。

### Defer only with explicit acceptance
- 真正断点续传/分片上传：需要新协议和临时块清理，后续单独做。

## Scope Boundaries
### In scope
- 上传/传输中心前后端契约、传输中心 UI、导入完成定位、Viewer 视频 seek 稳定性。

### Out of scope
- 云存储直传完整改造、视频转码、CDN、后台长期传输服务。

### Non-negotiables
- 清空记录不删除媒体。
- 导入完成不强制切走用户当前页面。
- 当前轮暂停语义不是断点续传。

### Failure and fallback expectations
- Failure states to support: token 创建失败、文件读取失败、上传失败、取消/暂停、服务端历史读取失败。
- Rollback or fallback behavior: 历史接口失败时仍展示本机内存/本地快照任务；记录清理只做软隐藏。

## Related Modules
- Photos root/photo feed: 导入完成定位和传输入口 badge 可能受影响。
- System media: 导入、建小相册、加入小相册全部走同一上传队列。
- Viewer: 视频播放和拖动进度条稳定性纳入本轮。
- Notifications: 通知中心可进入传输中心，需保留分类/滚动状态。

## Frontend and Backend Contracts
### Client state and entry points
- `TransferCenterScreen`、`LocalSystemMediaBridgeRepository`、`PhotoFeedPageStateStore`、`PhotoViewerScreen`。

### Server endpoints and payloads
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `GET /api/uploads`
- `POST /api/uploads/{uploadId}/dismiss`
- `POST /api/uploads/dismiss-batch`

### Shared rules
- Auth required; history scoped to current shared library and current uploader; state filter supports waiting/success/failed/cancelled.

## UI and Visual Details
- Layout or information hierarchy: 顶栏为返回、标题/汇总、清空当前分类、分类菜单。
- Components and states: operation card 支持展开/折叠；完成组下次进入默认折叠；运行中始终展开。
- Motion or transitions: 保持现有轻量卡片和按钮节奏，不新增重动效。
- Copy notes: “暂停”用于停止队列/当前项，“继续”用于重传。

## Interaction Feedback
- Loading: 上传进度按单项百分比和组汇总显示。
- Empty: 当前分类无记录时显示分类相关空态。
- Error: 单项展示失败原因，组可一键重试。
- Success: 成功记录可查看照片或目标小相册。
- Permission denial: 本地媒体读取失败提示重新选择。
- Offline or retry: 失败/暂停项保留继续入口。

## Hidden Impact Checklist
- Notifications: 通知中心进入传输中心后保留位置和分类。
- Auth: 历史接口按当前 bearer/session 查询。
- Upload: 直接影响上传 token、任务状态、取消/隐藏、历史列表。
- Comments: 无直接影响。
- Viewer: 视频 seek 稳定性本轮修复。
- Settings: 不新增设置项。
- Analytics or logging: 保留 `SystemMediaUpload` 调试日志。
- Cache or offline: 本地快照作为历史接口失败的兜底。
- Permissions: 读取系统媒体失败必须落入可理解失败态。
- Copy and empty states: 分类空态和清理提示需说明不会删除媒体。

## Plan Self-check
- Recommendation quality: 已优先处理持久化、折叠分组、非打断跳转和视频 seek 四个高影响点。
- Scope pressure test: 真正断点续传明确 defer，本轮按当前协议完成可发版语义。
- Contract and dependency pressure test: 需要 Android + Server 同步 DTO 和上传状态。
- UX state pressure test: 覆盖 loading/empty/error/success/permission/offline-retry。
- Deployment readiness pressure test: 需本地测试和真机两段验证。
- Risks to watch in implement: 脏工作区较多；需要局部 patch，避免覆盖无关改动。

## Implementation Notes
### Client
- 扩展上传 DTO、Repository 契约和 REAL/FAKE 实现，支持传输中心历史列表、单项/批量隐藏、operation 分组字段、`failed` 状态兼容。
- `LocalSystemMediaBridgeRepository` 现在为真实上传写入 operation 元数据，限制真实上传并发为 2，读取远端历史并与本地快照合并；本地快照保留 30 天，重启后进行中任务会恢复为已暂停/已取消语义，不伪装断点续传。
- 传输中心增加状态+来源分类菜单、当前分类清空、列表滚动位置记忆、完成组折叠/展开、折叠缩略图堆叠、单项进度条和百分比。
- 传输中心二次打磨：任务卡去掉重复标题和整卡跳转，时间提升到标题右侧；底部“查看照片/查看结果”按钮移除，改为单击具体媒体缩略图跳到对应媒体；日期分块标题加大；进入传输中心时完成任务默认重新折叠。
- 导入完成后不再强制切回照片流；只设置照片流待定位目标，用户回到照片流时再定位。
- 照片流默认关闭列表自动播放，列表只预取/展示服务端封面，避免滚动遇到视频时先黑屏和抽视频流造成卡顿。
- Viewer 视频 seek 期间保留释放后的目标进度，避免显示进度先闪回 0；Viewer 不再显示“暂无封面”文案，封面缺失时保持安静占位。
- Viewer 首屏视频封面改为直接使用服务端 `cover/preview` poster URL 与对应 cache key；seek 请求增加 pending target 状态，播放器缓冲或上报旧位置时控制条继续显示用户松手位置，直到真实进度追上目标。
- 传输中心单媒体缩略图跳转改为优先使用被点击 tile 的 `resultMediaId`；小相册任务只有在该 tile 没有媒体结果时才回退到帖子 route，避免总是跳到一次任务的第一个媒体。
- 相册页恢复网络成功后先清理 `OfflineAccessManager` 全局离线状态，再写回正常 UI 状态；帖子列表刷新成功同样清离线，避免“网络已断开，当前显示缓存内容...”在联网后残留。
- 传输中心主动点击媒体缩略图只负责切回照片流并定位/高亮目标 mediaId，不再自动打开 Viewer。
- 视频 poster 选择进一步收紧为 `coverUrl` 优先，服务端视频 DTO 的 `access` 同时暴露 `preview` 和 `cover`，两者都指向同一个 cover jpg，兼容只消费 preview 的旧组件和消费 cover 的新组件。
- 视频封面客户端兜底恢复为“服务端 cover 优先，cover URL 缺失或加载失败时再用视频 URL 抽一张静态 poster 并缓存到 `video-posters`”；照片流列表不恢复自动播放，只做静态帧兜底，Viewer 未播放时也能显示同一兜底 poster。
- 2026-06-22 热修：上传请求体进度回调不再吞掉取消异常；`shouldCancel` 会在每个 chunk 读写前后检查任务状态并抛出 `IOException`，让 OkHttp 流真正中断，而不是只把 UI 改成已取消。
- 2026-06-22 热修：远端历史合并时本地 `CANCELLED` 状态优先，远端迟到的旧成功/进行中记录不能把重启后的取消项复活；只允许补回缩略图/预览信息。
- 2026-06-22 交互修正：对方上传导致照片流版本变更时，正在浏览照片流的一端不再自动刷新列表；只显示“有新内容，点击刷新”提示，用户点击后再刷新并清除 stale 标记，避免打断当前位置。
- 2026-06-22 系统媒体/传输中心联动修复：App 启动后授权可用时后台预热系统媒体 metadata；系统媒体进入/恢复/手动刷新/后端内容变更时都强制刷新 MediaStore + import-status，强刷不会被旧 refresh job 吞掉；手动刷新会显式失效 metadata cache。
- 2026-06-22 导入去重修复：删除 App 媒体、清空/永久删除回收站项时会立刻清理本地 import overlay，并用 `invalidatedAppMediaIds` 压掉旧 metadata cache 里的 `importedAppMediaId`，避免已进回收站/已删除媒体继续显示“已导入”或被本地误判重复；恢复回收站项只失效 cache 并触发服务器 import-status 重查，由后端真实 active media 状态恢复“已导入”。
- 2026-06-22 传输中心暂停修复：暂停任务仍可重试，同时保留取消入口；组级取消现在包含“已暂停可重试”项，暂停项不再被进度统计当作真正完成。传输中心缩略图优先使用本地 `previewUri` 抽帧/缩略图，远端缩略图迟到或缺失时图片和视频都不应只剩纯色占位。
- 2026-06-22 新建小相册复用修复：从系统媒体新建小相册时，如果选中项全是已导入媒体，入口提示改为“复用已导入媒体”，桥接层会记回 source -> appMediaId 并发布明确操作结果，避免无上传任务时表现为没有反应或闪退。
- 2026-06-22 推送核验：服务端上传完成走 `UploadService.afterCommitAsync(... CATEGORY_PHOTOS_CONTENT_UPDATE ...)`，新建/加入/更新小相册走 `PostService.notifyContentUpdated()` after-commit，删除走 `TrashService.notifyDeleted()`；照片删除推送默认偏好为关闭，真机若要看到删除类系统推送需在推送设置里开启照片删除类别。
- 2026-06-24 推送与刷新提示修复：FCM 前台展示和同步兜底通知都会读取本地推送偏好，删除类关闭时不再由客户端兜底绕过；兜底继续排除 `actorIsCurrentUser`。照片流版本只对应 active media 可见性变化，评论/小相册变更不再污染照片流 banner。
- 2026-06-24 页面级刷新提示收窄：纯评论、加入/编辑小相册、相册媒体排序/封面等本地操作只吸收 `ALBUMS/NOTIFICATIONS`，不再标记 `PHOTO_FEED`；小相册详情和相册媒体管理页的 stale banner 改归属 `ALBUMS`。照片流只保留真实照片流变化的 banner，切回照片流时若已有 stale 会自动刷新并标记已刷新。
- 2026-06-24 系统媒体 import-status 联动：系统媒体页现在监听 `PHOTO_FEED/TRASH` 远端 stale，只失效本地 metadata cache 并强制刷新系统媒体/import-status，不替照片流或回收站清 stale，避免删除/恢复后必须退出重进才对齐“已导入”。
- 2026-06-24 追修：相册媒体管理页残留的 `photoFeedStale` 自动刷新/标记逻辑已移除，避免相册操作仍牵动照片页刷新提示；`SyncVersionTracker.markLocalMutation` 改为 30 秒吸收窗口，本机操作在服务端提交稍慢或重连后同步版本推进时，不再被当成远端更新提示。
- 2026-06-24 追修：同步兜底通知按 `notificationId` 持久去重，同一通知即使 `notificationVersion` 后续因别的事件前进也不重复弹；状态栏 dedupe TTL 从 2 分钟延长到 24 小时，并优先使用 `notificationId/operationId/groupId` 作为稳定去重键。
- 2026-06-24 追修：系统媒体 overlay 不再回退使用已 invalidated 的缓存 `importedAppMediaId`；App 媒体移入回收站后，即使系统媒体首屏来自旧 metadata cache，也会立刻显示为未导入。

### Server
- `upload_tasks` 增加 operation 元数据、错误信息和 `dismissed_at`，新增 `FAILED` 状态和 `V16__upload_transfer_center_history.sql` 迁移。
- 新增 `GET /api/uploads`、`POST /api/uploads/{uploadId}/dismiss`、`POST /api/uploads/dismiss-batch`，历史按当前共享库+上传者隔离，默认保留最近 30 天，隐藏为软隐藏。
- `UploadTaskResponse` 返回 operation 元数据和创建/更新/完成时间；token 请求可携带 source item 与 operation 字段。
- 视频上传完成后会尽力预热 `?variant=cover` 封面并写入 `coverObjectKey/previewObjectKey`；只使用系统 `ffmpeg` 或自动发现的 `imageio-ffmpeg` Windows 可执行文件抽帧；抽帧失败不阻塞上传，客户端保持封面优先和占位兜底。
- 启动 warmup 不再只看数据库 cover 字段，会实际 `ensureVideoCover()` 校验/补齐对象；当前运行容器里仍可读取既有 `cover-v1` 对象，新代码部署后会改用 `cover-v2` 重新生成。
- 视频 cover key 升级为 `cover-v2`，让旧的可能方向错误的 `cover-v1` 不再被复用；视频抽帧不再使用 JCodec fallback 生成封面，因为它不可靠处理手机视频旋转元数据，避免继续生成倒置封面。没有可用 ffmpeg 时宁可不生成 cover，等待部署环境修复 ffmpeg 后重新生成。
- 2026-06-22 热修：`POST /api/uploads/{uploadId}/file` 和 direct confirm 在完成前多次重新检查任务状态；最终写 SUCCESS 改为 `state = WAITING` 的条件更新，取消请求抢先落库后，同一个上传请求不能再把任务覆盖成成功。
- 2026-06-22 热修：视频上传完成不再同步等待服务端抽帧；服务端先登记 cover/preview object key 并后台 warmup，减少前端卡在 98% “服务器正在确认接收”的时间。
- 2026-06-22 热修补充：照片上传卡 98% 的主要风险不是视频抽帧，而是上传完成后同步调用 FCM 推送。上传完成通知现在改为事务提交后后台执行，FCM 网络超时只影响推送日志，不再拖住上传接口响应。
- 2026-06-24 同步版本和推送兜底修复：`SyncService.photoFeedVersion` 收窄为媒体表 active/deleted 状态，不再包含评论、postMedia 或相册更新时间；`PushNotificationService.targetTokensFor` 不再对 `photos/delete` 使用 self fallback，删除推送仍按接收者偏好过滤。
- 2026-06-24 追修：上传完成推送改为 after-commit 后延迟重查 operation tasks，只有同一 operation 的 expected count 个任务都已创建且全部终态后才发送；同一 server 进程内按 `libraryId:operationId` 去重，避免两个媒体上传时先推 1 个、后推 2 个。

### Design
- 顶栏保留返回、标题摘要、常驻“清空”按钮和三横线分类菜单。
- 完成后的批量任务可以折叠，折叠时用第一张缩略图叠放后续媒体；进行中任务保持展开，保留每项独立缩略图、进度和图标控制，不用文件名列表。
- 分类包含全部、进行中、失败/可重试、已完成、已取消、导入照片流、新建小相册、加入小相册。

### Interaction Feedback
- 清空当前分类会先暂停/取消非终态可见任务，再从传输中心隐藏记录；不会删除已导入媒体或小相册内容。
- 失败、取消和暂停任务保留重试入口；重启后因无分片协议，不承诺真正断点续传。
- 历史接口失败时仍保留本机快照作为兜底显示。

## Post-implement Self-check
- Validation run: Android `:app:compileDebugKotlin` + `TransferCenterBehaviorTest` + `MediaCacheKeyTest` 通过；Server 上传历史/隐藏、视频上传 focused tests 通过；`mvnw.cmd -DskipTests package` 已重新打包 jar；当前 docker profile `/api/health` 为 UP，但运行容器未确认已加载新 jar。
- New behavior sanity: 已覆盖传输中心分组/折叠/分类/清空/滚动记忆、上传历史恢复、非打断导入定位和 Viewer seek 闪回修复；范围仍限于上传传输中心与声明的照片流/Viewer 耦合。
- Contract sanity: Android DTO/Repository、Server request/response、迁移和双端 `upload-api.md` 已同步；视频 DTO 现在保持 `previewUrl/coverUrl` 指向封面变体，客户端缩略图不再回退到远端视频流。
- Test plan quality: 已给本地验证、关联模块回归和真机大视频场景分别列出检查项。
- Known gaps: 真正分片断点续传仍未实现；需要真机确认两三分钟视频上传、播放拖动和系统媒体权限路径；生产部署环境必须显式安装/配置可用 `ffmpeg`，否则新策略会跳过封面生成而不是生成可能倒置的封面。
- 2026-06-22 hotfix validation: Android `:app:compileDebugKotlin` 通过；focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest` 通过；Server focused upload/history/video tests 通过；`mvnw.cmd -DskipTests package` 通过；新 jar 已 `docker cp` 到 `yingshi-server` 并重启，`GET /api/health` 返回 UP。
- 2026-06-22 follow-up validation: server `mvnw.cmd -DskipTests package` 通过；focused `YingshiServerApplicationTests#uploadHistoryKeepsOperationMetadataAndDismissesRecords` 通过；最终 jar 已部署到 `yingshi-server`，`GET /api/health` 返回 UP。
- 2026-06-22 photo-feed stale UX validation: Android `:app:compileDebugKotlin` 通过；照片流 stale 自动刷新监听已移除，保留 banner 点击刷新。
- 2026-06-22 system-media/upload-sync validation: Android `:app:compileDebugKotlin` 通过；局部 `git diff --check` 通过。后端本轮未改代码，只核验了上传、相册内容更新、删除三个推送触发点仍存在。
- 2026-06-24 push/page-refresh/system-media validation: Server `mvnw.cmd -q -DskipTests compile` 通过；Android `:app:compileDebugKotlin` 第二次通过，第一次失败是 build 输出目录 `Permission denied`，清理 Kotlin 增量输出和停止 Gradle daemon 后消失；除 `MediaManagementScreen.kt` 既有 CRLF/尾随空白脏差异外，本轮相关文件 `git diff --check` 通过。
- 2026-06-24 follow-up validation: Server `mvnw.cmd -q -DskipTests compile` 通过；Android `:app:compileDebugKotlin` 通过；本轮改动文件局部 `git diff --check` 通过，`MediaManagementScreen.kt` 仍有既有 CRLF/尾随空白历史差异，本轮未做整文件格式化。

## New Coupling Recheck
- Module: Photos root/photo feed.
  - What was rechecked: 导入完成事件只写 pending locate，不在非照片流页面强制跳转。
  - Result: 需要真机回到照片流时确认定位和高亮。
- Module: Viewer.
  - What was rechecked: 视频 pending seek 时不把 UI 进度清零，减少海报覆盖。
  - Result: Viewer poster 判断改为服务端图片真正加载成功才算可用；失败/缺失后走视频静态帧兜底。需要真机拖动长视频确认不卡顿/不闪退。
- Module: Upload API.
  - What was rechecked: 历史列表、隐藏、operation 元数据、`failed` 状态、迁移字段和视频封面变体。
  - Result: Focused server tests 通过；视频封面生成策略收束为 ffmpeg-only，避免 JCodec 忽略旋转元数据导致倒置封面。
- Module: System media/import-status.
  - What was rechecked: 本地 metadata cache、import overlay、App 内容删除/回收站恢复/永久删除后的 `已导入` 状态。
  - Result: 客户端强刷队列、cache 失效和 overlay invalidation 已接入；真实状态仍需真机按“删除 -> 再导入 -> 恢复 -> 再导入”链路确认。
- Module: Notifications/push.
  - What was rechecked: 上传完成、小相册创建/加入/更新、删除三类服务端推送触发点。
  - Result: content_update 与 delete 触发点存在；删除推送默认偏好关闭，若测试删除推送需要先开启设置。
- Module: Notifications/push preferences.
  - What was rechecked: 服务端目标 token、客户端 FCM 前台展示、同步兜底通知。
  - Result: delete 类别关闭时不再被 self fallback 或客户端 fallback 绕过；兜底排除当前用户自己的通知。
- Module: Page stale banners.
  - What was rechecked: 照片流、相册页、小相册详情、相册媒体管理、回收站的 stale module 归属。
  - Result: 评论/相册操作归 `ALBUMS/NOTIFICATIONS`，照片流只响应媒体可见性；切回照片流时自动刷新已存在的照片流 stale。
- Module: Upload and notification dedupe.
  - What was rechecked: 上传 operation 完成推送、同步兜底通知、状态栏去重键、本机操作版本吸收。
  - Result: 批量上传只在 operation 完整终态后推一次；同一 notification/operation 24 小时内不重复弹；本机 mutation 在 30 秒窗口内持续吸收服务端版本推进。

## Implement Test Plan
### Locally validated
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.example.yingshi.feature.photos.TransferCenterBehaviorTest --tests com.example.yingshi.feature.photos.MediaCacheKeyTest"`
- Result: Pass.
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -Dtest=YingshiServerApplicationTests#localVideoUploadReturnsJsonEnvelope+localVideoUploadWarmsCoverWhenVideoFrameExtractorIsAvailable+uploadHistoryKeepsOperationMetadataAndDismissesRecords test"`
- Result: Pass; 3 tests run, 0 failures, 0 skipped.
- Check: docker-local MinIO video cover audit.
- Result: 运行容器仍可读取既有 `*-cover-v1-1280.jpg` 对象；新 jar 部署后会使用 `cover-v2` key 重新生成，旧倒置封面不会被复用。
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat --no-daemon -Dorg.gradle.jvmargs=-Xmx6144m -Pkotlin.daemon.jvmargs=-Xmx4096m :app:compileDebugKotlin"`
- Result: Pass. 常规 2G Gradle/Kotlin daemon 在当前大 Compose 工程里可能 OOM，串行高内存参数通过。
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat --no-daemon -Dorg.gradle.jvmargs=-Xmx6144m -Pkotlin.daemon.jvmargs=-Xmx4096m :app:testDebugUnitTest --tests com.example.yingshi.feature.photos.MediaCacheKeyTest --tests com.example.yingshi.feature.photos.TransferCenterBehaviorTest"`
- Result: Pass.

### Linked-module regression checks
- Module: Photo feed.
  - What to recheck: 从系统媒体和底部加号导入后，停留其他页面不被打断；回照片流后定位到新媒体。
  - Why it can regress: 导入完成事件现在改为 pending locate，不再立即刷新跳转。
- Module: Viewer.
  - What to recheck: 两三分钟视频拖动进度条，进度不先闪回 0，播放不卡顿，不闪退。
  - Why it can regress: Viewer seek 显示和海报遮罩逻辑被调整。
- Module: Notifications/entry points.
  - What to recheck: 从通知中心或其他入口进入传输中心后，分类和滚动位置仍保留。
  - Why it can regress: 传输中心新增全局状态存储。

### Real-device checks for the user
- Scenario: 批量导入 20 个图片/视频混合媒体。
  - Steps: 从系统媒体多选导入 App，上传中打开传输中心，观察每项缩略图、暂停、进度；全部完成后折叠/展开，再退出重开 App。
  - Expected result: 上传中默认展开；完成后可折叠；重进 App 后完成组默认折叠，历史仍在。
- Scenario: 分类和清空。
  - Steps: 切换全部/进行中/失败/已完成/来源分类，点击右上角清空当前分类。
  - Expected result: 只隐藏当前分类可见记录，不删除照片流媒体或小相册内容。
- Scenario: 非打断导入定位。
  - Steps: 从系统选择器导入后立刻切到其他页面，等待完成，再回照片流。
  - Expected result: 当前页面不被强制切走；回照片流时定位并高亮新导入媒体。
- Scenario: 视频上传和播放 seek。
  - Steps: 上传两个 2-3 分钟视频，完成后进入播放，反复拖动进度条。
  - Expected result: 上传不因时长被拦；拖动时进度不闪回开头，不卡顿，不闪退。
- Scenario: 系统媒体启动和刷新。
  - Steps: 启动 App 后不要先进入系统媒体，等待几秒再打开系统媒体；随后新增/删除本机媒体并点刷新。
  - Expected result: 首次进入能直接看到最近媒体；手动刷新按钮立即转动并拉最新 MediaStore，不再等几秒只多一张。
- Scenario: App 删除、回收站、恢复、去重。
  - Steps: 导入一个视频，确认系统媒体显示已导入；从 App 删除进回收站，回系统媒体；再导入同一个本机视频；然后从回收站恢复，再尝试导入。
  - Expected result: 删除进回收站后系统媒体变未导入；再导入不会被本地旧 overlay 误判重复；恢复后 import-status 重查恢复已导入；恢复后再导入按 active media 去重。
- Scenario: 暂停任务取消和缩略图。
  - Steps: 上传图片和视频混合任务，在传输中心暂停某项，再观察 tile 控制和缩略图。
  - Expected result: 暂停项同时有重试和取消入口；图片/视频 tile 有本地缩略图或视频帧，不退化成纯色块。
- Scenario: 上传/相册操作推送。
  - Steps: B 设备开启照片内容更新推送；A 上传媒体、新建小相册、加入小相册。若要测删除推送，先在设置里打开照片删除推送。
  - Expected result: B 收到 content_update 推送；删除推送只有开启删除类别后才出现。

### Still unverified
- Risk: 真机大视频上传耗时和 Android 系统媒体 URI 权限差异。
  - Why it remains open: 本地单测无法模拟真实相册 URI、长视频 IO 和设备解码。
  - Best next verification path: 用用户常用真机和真实视频跑一次完整导入、重启、播放 seek。
- Risk: 服务端真实视频帧封面在生产环境仍依赖可用的视频解码器。
  - Why it remains open: 新策略不再用 JCodec 兜底；如果部署环境没有可用 `ffmpeg`，封面会保持缺失而不是生成错误方向。
  - Best next verification path: 在部署服务器显式配置 `YINGSHI_FFMPEG_PATH` 或安装系统 `ffmpeg`，再上传一条竖屏/HEVC 视频确认 `cover-v2` 方向正确。
- Risk: 全量服务端测试当前仍有其他模块既有失败。
  - Why it remains open: `YingshiServerApplicationTests` 全量此前已因相册/life console JSONPath 失败；迁移安全测试也有既有硬编码 `V12__large_album_directory_support.sql` 但当前文件为 `V14`。
  - Best next verification path: 在对应模块精修时修正那些旧测试；本轮已跑上传相关 focused tests。

## Real-device Issue Log
- 2026-06-18: 用户反馈传输中心任务卡重复标题、时间不明显、全卡/查看按钮跳转不符合预期、缩略图为浅蓝占位、完成组重新进 App 仍展开；照片流和首页视频缺封面/黑屏，列表自动播放造成卡顿；Viewer 播放和拖动进度条卡顿/闪回；服务器 8080 被旧进程占用。
  - Resolution: 任务卡重做为单标题+显眼时间+单媒体缩略图跳转；重进传输中心完成组默认折叠；照片流默认关闭 inline autoplay 并只展示服务端封面；Viewer seek 显示锁定目标进度；旧服务进程清理并重启；视频 cover warmup 补齐 MinIO 11/11 个视频封面。
- 2026-06-18: 用户复测反馈 Viewer 进入仍无封面、拖动进度条仍先弹回开头且画面不显示；传输中心点某个媒体缩略图仍跳一次任务第一项；相册页恢复网络后离线缓存提示残留。
  - Resolution: Viewer 改为直接读视频 poster/cover 并保持 pending seek target；传输中心点击分支改为被点媒体 id 优先；相册页远端刷新成功时先清全局离线状态再更新 UI。
- 2026-06-18: 用户继续复测反馈传输中心仍像跳第一项、Viewer 仍无封面，并追问数据库和 MinIO 生成/读取是否稳定。
  - Resolution: 传输中心点击改为照片流定位后自动打开 Viewer 精确 index；客户端视频 poster 改为 cover 优先；服务端视频 `access.preview` 与 `access.cover` 均指向 cover jpg；复查本地数据库 11 条视频与 MinIO 11 个 cover 对象一致。服务端 focused tests 已通过并已打包新 jar；当前 Docker CLI 返回 API 500，live 容器暂未重建，但 live `/api/health` 仍为 UP，客户端可直接消费现有 `coverUrl`。
- 2026-06-18: 用户反馈传输中心点击缩略图现在会自动打开 Viewer，期望只回照片流定位；部分视频封面生成倒置。
  - Resolution: 移除传输中心 pending open viewer，只保留照片流定位/高亮；视频 cover 升级 `cover-v2` 并禁用 JCodec fallback，避免旧倒置封面被复用或继续生成方向错误的新封面。服务端 focused tests 通过并已重新打包 jar。
- 2026-06-18: 用户反馈照片流列表视频又没有封面，Viewer 未播放时也不显示封面。
  - Resolution: 客户端恢复静态 poster 兜底：服务端 `cover/preview` 图片缺失或加载失败时，照片流缩略图和 Viewer 会用视频 URL 抽取第一帧并缓存；Viewer 只在服务端 poster 图片加载成功后才把它视为可展示封面。补齐 `VideoPreloadWorker` 缺失 import，避免应用启动处已有调度代码编译失败。
- 2026-06-22: 用户反馈媒体上传长期卡在 98%；取消任务无效，过一会儿仍上传成功；退出重进后取消任务缩略图变纯色且仍像进行中。
  - Root cause: 客户端 `ProgressInputStreamRequestBody` 的进度回调原本用 `runCatching` 吞掉取消异常，只改 UI 不会真正中断 OkHttp 上传流；服务端上传请求入口只检查一次 `WAITING`，取消和上传完成竞态时上传请求可以最后覆盖成 `SUCCESS`；视频 cover 同步 warmup 会拖住上传响应，表现为 98% 停很久。
  - Resolution: 客户端请求体 chunk 读写前后检查 `shouldCancel` 并抛出 `IOException`；服务端完成前刷新状态并把最终成功落库改为 `WHERE state = WAITING` 条件更新，失败则清理已写对象；视频 cover 改后台 warmup；远端历史合并保护本地 `CANCELLED` 状态，避免旧远端记录复活取消 UI。
- 2026-06-22: 用户补充小照片也会卡在 98%，说明问题不只在视频。
  - Root cause: 上传完成后 `notifyUploadOperationIfCompleted()` 同步调用 FCM。若服务器访问 Google token/FCM 超时，文件和 DB 已完成但 HTTP 响应迟迟不返回，客户端就停在 98% 等“服务器确认接收”。
  - Resolution: 上传完成推送改为 after-commit 后台任务；推送异常只记录 `Async upload completion side effect failed`，不影响上传接口返回。
- 2026-06-22: 用户反馈 A 上传时 B 正在照片流页不应立刻刷新，应该提示有新内容，点击后再刷新。
  - Root cause: `RealPhotoFeedPage` 同时渲染 `StaleBanner` 和监听 `photoFeedStale` 自动 `viewModel.refresh()`，提示一出现就被自动刷新打断。
  - Resolution: 移除照片流 stale 自动刷新监听；`StaleBanner` 点击仍执行 `viewModel.refresh()` 和 `SyncVersionTracker.markRefreshed(PHOTO_FEED)`。

## Validation Snapshot
### Verified
- Android compile, transfer center unit test, and media cache key tests passed.
- Android revalidation after latest fixes passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after transfer-center precise open and cover-priority fix passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after removing transfer-center auto-open Viewer passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after client-side video poster fallback passed with high-memory single-use Gradle daemon: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android hotfix revalidation passed: `:app:compileDebugKotlin`.
- Android focused hotfix tests passed: `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Server upload history/dismiss focused test passed.
- Server video upload focused test passed with video-frame extractor path; no skip; video DTO now includes both `access.preview` and `access.cover` for the cover jpg.
- Server focused tests passed after `cover-v2` and ffmpeg-only cover generation change; `mvnw.cmd -DskipTests package` rebuilt `YingShi-Server/target/yingshi-server-0.0.1-SNAPSHOT.jar`.
- Server hotfix focused tests passed: `YingshiServerApplicationTests#localVideoUploadReturnsJsonEnvelope+localVideoUploadWarmsCoverWhenVideoFrameExtractorIsAvailable+uploadHistoryKeepsOperationMetadataAndDismissesRecords`.
- Server hotfix package passed and final jar was deployed to Docker container `yingshi-server`; `/api/health` returned `UP` with database and storage checks UP after restart.
- Server follow-up focused test passed after async upload push change: `YingshiServerApplicationTests#uploadHistoryKeepsOperationMetadataAndDismissesRecords`.
- Server follow-up package passed and final jar was redeployed to `yingshi-server`; `/api/health` returned `UP` with database and storage checks UP.
- Android photo-feed stale UX compile passed: `:app:compileDebugKotlin`.
- docker-local server is running on port 8080; `/api/health` reports `UP`, but the running container has not been confirmed rebuilt with the latest jar.
- Existing live MinIO/DB may still expose old `cover-v1` objects until the rebuilt server is deployed and warmup/upload generation writes `cover-v2`.
- Live Docker server rebuild is pending because Windows Docker CLI previously returned `500 Internal Server Error` for Docker Engine API calls; the rebuilt server jar exists at `YingShi-Server/target/yingshi-server-0.0.1-SNAPSHOT.jar`.
- Migration list checked manually: versions are `V1` through `V16` with no duplicate version numbers.

### Pending
- Broad real-device smoke remains recommended before production freeze: batch upload, app restart, transfer center history, photo-feed locate, video poster fallback, and Viewer seek.
- Deploy the rebuilt server jar/container before relying on server-side `cover-v2` generation in docker-local/live environments.

### Blocked
- None for this module. Broader full-suite validation is limited by existing unrelated failures in album/life console and migration safety tests.

## Closeout Summary
- Shipped: transfer center now persists upload history, groups one multi-select import into one operation, sorts newest first, supports status/source filters, clears visible records without deleting media, keeps completed operations collapsible, restores scroll/category state, shows media thumbnails, and lets paused tasks be cancelled or retried.
- Shipped: upload completion no longer stalls on synchronous side effects. Client cancellation interrupts the request stream, server success writes are guarded as `WAITING -> SUCCESS`, video cover warmup runs asynchronously, and upload push dispatch runs after commit.
- Shipped: batch upload operation notifications are delayed until expected tasks exist and all are terminal, then deduped by operation. This avoids the intermittent "uploaded 1 media" followed by "uploaded 2 media" double notification.
- Shipped: system media import status is now treated as active-media state. App start/entry/refresh preloads MediaStore and import-status; delete/trash/permanent-delete invalidates local overlay immediately; restore triggers import-status recheck; duplicate import is ultimately decided by server active fingerprint/import-status rather than stale local overlay.
- Shipped: push filtering is layered on server preferences, FCM foreground display, and sync fallback display. Delete notifications respect the disabled photo-delete preference, actor-self notifications are excluded, and repeated fallback/status-bar notifications are deduped with stable `notificationId/operationId/groupId` keys.
- Shipped: page refresh prompts are scoped by module. Album/comment operations dirty `ALBUMS/NOTIFICATIONS`, not the photo feed; photo feed only reacts to real media visibility changes; local mutations are absorbed for a short window so the current device does not show remote-style "new content" prompts for its own actions.
- Shipped: video handling remains hardened across upload, photo feed, transfer center, and Viewer: server cover uses `cover-v2` with ffmpeg-only generation, Android has a client static-poster fallback, feed autoplay stays off by default, and Viewer seek display avoids jumping back to zero while the player catches up.
- Validated: Android `:app:compileDebugKotlin` passed after the latest push/page-refresh/system-media fixes; earlier focused `TransferCenterBehaviorTest` and `MediaCacheKeyTest` also passed for the transfer-center/media-cache behavior.
- Validated: Server `mvnw.cmd -q -DskipTests compile` passed after the latest upload-notification and push/sync fixes; earlier focused upload/history/video-cover tests passed for the server upload path.
- Validated: latest doc closeout check passed with `git diff --check -- docs/refinement/submit_transferCenter.md`; code-format check remains noisy only in pre-existing `MediaManagementScreen.kt` CRLF/trailing-whitespace history, which this closeout did not reformat.
- Remaining risk: broad real-device smoke is still recommended before freezing this area: two-device push, delete/restore/import-status, reconnect dedupe, transfer-center thumbnails, and long-video upload/seek should be exercised together.
- Remaining risk: true resumable/chunked upload remains intentionally out of scope; pause/resume still means stop/retry under the current protocol.
- Remaining risk: production/server environments need a real ffmpeg path or install. Without ffmpeg, the server skips cover generation rather than generating rotated covers; Android's poster fallback keeps the UI from going blank.
- Remaining risk: broad server suite still has unrelated failures outside this module; this closeout relies on targeted upload/video checks plus Android/server compile validation.
- Remaining risk: if the latest server code must be verified inside a live container, confirm deployment/restart separately; the code compiles, but container freshness is an environment/deployment check.

## Carry-forward Notes
- If upload is revisited, design a real chunked/resumable protocol instead of stretching the current pause/retry semantics.
- If deployment or media infrastructure is touched, install/configure ffmpeg explicitly and verify one portrait/HEVC upload writes a correctly oriented `cover-v2` object.
- If Viewer/photo feed is touched, preserve the three-layer video poster order: server cover first, client-extracted static frame second, quiet fallback last.
- If photo feed or stale-banner logic changes, preserve the final routing rule: album/comment changes must not show a photo-feed refresh prompt; only real photo-feed media visibility changes should.
- If sync tracking changes, preserve either the current local-mutation absorb window or replace it with stronger actor-aware version tracking so local actions do not show remote refresh banners.
- If push or notification-center code changes, keep preference filtering in all three layers: server targeting, FCM foreground presentation, and sync fallback presentation. Keep stable dedupe ids in notification data.
- If upload notification delivery needs once-only guarantees across server restarts or multiple instances, replace the current in-process operation dedupe with a persistent outbox/audit table.
- If system media/import-status changes, never trust cached `importedAppMediaId` after that app media id has been invalidated; active server media/import-status remains the source of truth.
- If photo feed navigation changes, preserve non-interrupting import behavior: imports should not force a route switch, and transfer center media taps should locate in the feed rather than opening Viewer.
- If transfer center state changes, preserve completed-operation default folding on fresh entry, per-media tile actions while active, and "clear record does not delete media" copy/behavior.
- If build tooling is cleaned up, raise Kotlin/Gradle daemon memory or split very large Compose files; current default 2G settings can OOM during `compileDebugKotlin`.

## Closeout Self-check
- Brief completeness: Final shipped behavior now covers transfer center, upload completion, operation push dedupe, notification filtering, page-scoped stale prompts, system-media import-status, video covers, and the latest validation snapshot.
- Remaining risk clarity: Real-device smoke, chunked upload defer, ffmpeg deployment, broad suite failures, and live-container freshness are explicit rather than implied.
- Carry-forward quality: Future-sensitive notes are short and tied to concrete modules: upload protocol, media infra, push/dedupe, sync stale routing, system media, Viewer/photo feed, transfer center state, and build tooling.
