# 上传与传输中心

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `submit_transferCenter`
- Status: `closed`
- Last updated: `2026-06-19`
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

### Server
- `upload_tasks` 增加 operation 元数据、错误信息和 `dismissed_at`，新增 `FAILED` 状态和 `V16__upload_transfer_center_history.sql` 迁移。
- 新增 `GET /api/uploads`、`POST /api/uploads/{uploadId}/dismiss`、`POST /api/uploads/dismiss-batch`，历史按当前共享库+上传者隔离，默认保留最近 30 天，隐藏为软隐藏。
- `UploadTaskResponse` 返回 operation 元数据和创建/更新/完成时间；token 请求可携带 source item 与 operation 字段。
- 视频上传完成后会尽力预热 `?variant=cover` 封面并写入 `coverObjectKey/previewObjectKey`；只使用系统 `ffmpeg` 或自动发现的 `imageio-ffmpeg` Windows 可执行文件抽帧；抽帧失败不阻塞上传，客户端保持封面优先和占位兜底。
- 启动 warmup 不再只看数据库 cover 字段，会实际 `ensureVideoCover()` 校验/补齐对象；当前运行容器里仍可读取既有 `cover-v1` 对象，新代码部署后会改用 `cover-v2` 重新生成。
- 视频 cover key 升级为 `cover-v2`，让旧的可能方向错误的 `cover-v1` 不再被复用；视频抽帧不再使用 JCodec fallback 生成封面，因为它不可靠处理手机视频旋转元数据，避免继续生成倒置封面。没有可用 ffmpeg 时宁可不生成 cover，等待部署环境修复 ffmpeg 后重新生成。

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

## Validation Snapshot
### Verified
- Android compile, transfer center unit test, and media cache key tests passed.
- Android revalidation after latest fixes passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after transfer-center precise open and cover-priority fix passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after removing transfer-center auto-open Viewer passed: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Android revalidation after client-side video poster fallback passed with high-memory single-use Gradle daemon: `:app:compileDebugKotlin` and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest`.
- Server upload history/dismiss focused test passed.
- Server video upload focused test passed with video-frame extractor path; no skip; video DTO now includes both `access.preview` and `access.cover` for the cover jpg.
- Server focused tests passed after `cover-v2` and ffmpeg-only cover generation change; `mvnw.cmd -DskipTests package` rebuilt `YingShi-Server/target/yingshi-server-0.0.1-SNAPSHOT.jar`.
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
- Shipped: transfer center now persists upload history, groups one multi-select import into one operation, sorts newest first, supports status/source filters, clears visible records without deleting media, keeps completed operations collapsible, and restores scroll/category state.
- Shipped: each task media tile uses real thumbnails where available, exposes per-item progress/control affordances while active, and opens the selected imported media by returning to the photo feed location rather than auto-opening Viewer.
- Shipped: system media/bottom-plus imports no longer force navigation away from the current page; photo feed receives a pending locate target and highlights it when the user returns.
- Shipped: video handling is hardened across upload, photo feed, and Viewer: list autoplay is off by default, service-side covers use `cover-v2` with ffmpeg-only generation, client-side static poster fallback covers missing/failed server covers, and Viewer seek display no longer jumps back to zero while the player is catching up.
- Validated: Android compile and focused `TransferCenterBehaviorTest` + `MediaCacheKeyTest` passed; the latest client-side poster fallback required a high-memory single-use Gradle daemon because the default 2G Kotlin daemon OOMs on the current large Compose codebase.
- Validated: server focused upload/history/video cover tests passed; `mvnw.cmd -DskipTests package` rebuilt `YingShi-Server/target/yingshi-server-0.0.1-SNAPSHOT.jar`; docker-local `/api/health` reports `UP`.
- Remaining risk: true resumable/chunked upload remains intentionally out of scope; pause/resume still means stop/retry under the current protocol.
- Remaining risk: production/server environments need a real ffmpeg path or install. Without ffmpeg, the server skips cover generation rather than generating rotated covers; the Android client poster fallback keeps the UI from going blank.
- Remaining risk: the running docker container has not been confirmed rebuilt with the latest jar, so existing live data may still expose `cover-v1` until deployment/warmup writes `cover-v2`.
- Remaining risk: full server test suite still has unrelated failures in other modules; this closeout relies on targeted upload/video tests plus Android focused tests.

## Carry-forward Notes
- If upload is revisited, design a real chunked/resumable protocol instead of stretching the current pause/retry semantics.
- If deployment or media infrastructure is touched, install/configure ffmpeg explicitly and verify one portrait/HEVC upload writes a correctly oriented `cover-v2` object.
- If Viewer/photo feed is touched, preserve the three-layer video poster order: server cover first, client-extracted static frame second, quiet fallback last.
- If photo feed navigation changes, preserve non-interrupting import behavior: imports should not force a route switch, and transfer center media taps should locate in the feed rather than opening Viewer.
- If transfer center state changes, preserve completed-operation default folding on fresh entry, per-media tile actions while active, and "clear record does not delete media" copy/behavior.
- If build tooling is cleaned up, raise Kotlin/Gradle daemon memory or split very large Compose files; current default 2G settings can OOM during `compileDebugKotlin`.

## Closeout Self-check
- Brief completeness: Final shipped behavior, validation, deferred chunked upload, server deployment caveat, video cover policy, and linked photo feed/Viewer/upload API coupling are captured.
- Remaining risk clarity: Device smoke, ffmpeg deployment, docker rebuild, and unrelated full-suite failures are explicit rather than implied.
- Carry-forward quality: Future-sensitive notes are short and tied to concrete modules: upload protocol, media infra, Viewer/photo feed, transfer center state, and build tooling.
