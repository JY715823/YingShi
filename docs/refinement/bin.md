# Recycle Bin Refinement

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `bin`
- Status: `implementing`
- Last updated: `2026-06-25`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 回收站删除、恢复、待清理和离线查看都要可信，不误导用户，也不让媒体或相册数据残留在错误状态。
- Business or product intent: 把回收站收口成可上线的数据安全模块，减少“闪一下”“删不干净”“恢复后重复”的信任损耗。
- Success criteria: 分类切换不闪空；列表/详情删除先进入待清理；待清理可撤销或永久删除；永久删除按媒体/相册类型执行物理或结构清理；恢复后照片流去重。

## Current State
- What exists today: Android REAL 回收站已有四类列表、详情、恢复、remove、undo-remove、purge、pending-cleanup 接口；服务端已有 24 小时 pending cleanup scheduler。
- Known constraints: 工作区有大量既有未提交改动，不能回滚无关文件；WSL 缺少可用 Java，验证需走 Windows `cmd.exe` wrapper。
- Relevant code or docs: `RealTrashScreens.kt`、`RealTrashViewModels.kt`、`TrashService.java`、`YingshiServerApplicationTests.java`、`docs/contracts/trash-api.md`。

## Your Current Ideas
- Idea 1: 分类切换应缓存上次视图，避免闪空。
- Idea 2: 从回收站移除要能最终物理删除数据库和 MinIO/COS 对象，但主删除先进入待清理。
- Idea 3: 恢复后如有重复上传由服务端兜底去重。
- Idea 4: 用户筛选放左侧，分类菜单放右侧，并把待清理入口放入分类菜单。
- Idea 5: 媒体、小相册、大相册详情要按原模块体验修好，断网也能进已缓存详情。

## Codex Recommendations
### Recommended to finish in this module
- Recommendation: 保留两阶段删除，主入口 `remove`，待清理页 `purge`。
  - Why it is worth considering: 用户有撤销窗口，同时永久删除语义集中，风险更低。
  - Impact on usability, robustness, or smoothness: 避免误删和文案误导，便于回归测试。
- Recommendation: 服务端允许 `PENDING_CLEANUP` 直接 `purge`。
  - Why it is worth considering: 待清理页的“永久删除”否则会稳定 409。
  - Impact on usability, robustness, or smoothness: 合同与新 UI 对齐。
- Recommendation: 分类列表和详情继续走内存/本地缓存，切换分类展示旧内容并后台刷新。
  - Why it is worth considering: 解决“闪一下”和断网无法进入查看态。
  - Impact on usability, robustness, or smoothness: 让回收站像普通媒体页一样可读、可回看。

### Defer only with explicit acceptance
- Item: 非 REAL fake 回收站完全复刻新待清理页。
  - Why it would otherwise belong in this module: 演示模式也应一致。
  - Why it might still be deferred: 当前上线风险在 REAL Android + Server，fake 页面不是生产路径。

## Key Questions
- [x] 主删除是否直接物理删除？结论：不是，主删除进入待清理。
- [x] 待清理入口位置？结论：分类菜单内，仅有待清理条目时显示。
- [x] 相册类 purge 是否删除全局媒体文件？结论：不删，只删相册结构、评论、关系和 trash 记录。

## Scope Boundaries
### In scope
- REAL 回收站列表、详情、分类菜单、待清理页、remove/undo/purge 合同。
- Server trash purge 状态规则、关键回归测试、Android 合同文档。
- 恢复后的前端合并提示和服务端照片流去重兜底。

### Out of scope
- 改造所有 fake/demo 回收站交互。
- 重做相册/小相册业务模块本身。
- 真机 MinIO/COS 实例上的手动数据核验。

### Non-negotiables
- 不把“移出回收站”误说成已经永久删除。
- `mediaSystemDeleted` 永久删除必须清理媒体记录、评论、关系和 original / preview / cover 对象。
- `largeAlbumDeleted`、`smallAlbumDeleted` 永久删除不删全局媒体文件。

### Failure and fallback expectations
- Failure states to support: 离线只读、token 缺失、remove/undo/purge 失败、待清理为空。
- Rollback or fallback behavior: remove 失败条目留在回收站；purge 失败条目留在待清理；undo 失败不改变本地选择。

## Related Modules
- Module: photo_stream
  - Relationship: 恢复和永久删除会影响照片流展示、去重和刷新。
  - Recheck before ship: 恢复系统媒体后照片流不重复。
- Module: albums / small album detail
  - Relationship: 大/小相册删除详情和 purge 规则依赖相册结构。
  - Recheck before ship: 相册页、小相册详情、媒体关系数量。
- Module: SystemMedia / upload / transfer center
  - Relationship: 系统媒体导入状态、重复上传和删除恢复同步相关。
  - Recheck before ship: 重新上传同 fingerprint、导入状态刷新、上传任务去重。

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories: `PhotosRootScreen -> TrashPageScreen -> RealTrashPageScreen` 透传 `showPendingCleanup`；`RealTrashListViewModel` 同步刷新 trash items 与 pending cleanup；详情页 `remove` 调用 `moveTrashItemOut`。

### Server endpoints and payloads
- Controllers, services, DTOs, contracts: `POST /api/trash/items/{id}/remove`、`undo-remove`、`purge`、`GET /api/trash/pending-cleanup`；canonical item types 为 `largeAlbumDeleted`、`smallAlbumDeleted`、`mediaRemoved`、`mediaSystemDeleted`。

### Shared rules
- Auth, permissions, identity, ordering, time, copy: 所有接口 bearer auth；待清理按 deadline 展示；Android 兼容 `sourcePostId / relatedPostIds`，合同文档同步 `sourceSmallAlbumId / relatedSmallAlbumIds`。

## UI and Visual Details
- Layout or information hierarchy: 用户筛选在左侧贴边，分类菜单在右侧；待清理是独立页面。
- Components and states: 分类菜单显示四类和有内容时才出现的待清理；待清理行展示预览、类型、倒计时、删除影响、撤销和永久删除。
- Motion or transitions: 不新增装饰动效，重点减少切换闪空。
- Copy notes: 主流程用“移出回收站”；待清理恢复用“撤销移出”；物理清理用“永久删除”。

## Interaction Feedback
- Loading: 有缓存时保留旧视图并后台刷新。
- Empty: 待清理为空时页内展示空态，菜单不显示入口。
- Error: remove/undo/purge 失败显示明确失败文案，条目保留在原状态。
- Success: remove 成功提示可在待清理撤销或永久删除；恢复系统媒体提示重复内容由服务端合并展示。
- Permission denial: token 缺失走现有登录/错误提示。
- Offline or retry: 离线缓存只读可看列表和详情，不允许恢复、移出、撤销、永久删除。

## Deployment Readiness
- Release-critical expectations: Android 编译、server trash 定向测试、真机离线缓存和 MinIO/COS 对象清理核验。
- Anything that must be true before moving to the next module: 待清理页永久删除不能 409；相册 purge 不删全局媒体；系统媒体 purge 删除对象。
- Acceptable defers, if any: fake/demo 回收站新 UI 完全一致可后续统一。

## Hidden Impact Checklist
- Notifications: trash 状态变化仍可能触发推送文案，需回归 pending cleanup 通知语义。
- Auth: bearer auth 不变。
- Upload: 恢复后重复上传由服务端去重兜底。
- Comments: 系统媒体 purge 删除媒体评论；相册 purge 删除相册评论。
- Viewer: 媒体删除详情保留沉浸查看器和只读评论预览。
- Settings: 无新增设置。
- Analytics or logging: 无新增埋点。
- Cache or offline: 列表/详情缓存是本轮重点。
- Permissions: 复用现有库权限和登录态。
- Copy and empty states: 已区分移出、撤销移出、永久删除。

## Plan Self-check
- Recommendation quality: 已从用户点子扩展为两阶段删除、合同状态、缓存和详情体验的完整收口方案。
- Scope pressure test: 聚焦 bin 模块 REAL 路径，fake/demo 不扩大。
- Contract and dependency pressure test: Android/Server 合同统一到四类 canonical item type，并明确 small-album 字段。
- UX state pressure test: 覆盖 loading、empty、error、success、offline。
- Risks to watch in implement: Kotlin 编译环境、server 定向测试选择器、真机对象存储核验。

## Implementation Notes
### Client
- `RealTrashListViewModel.refresh` 同时拉取 pending cleanup 并保留分类视图缓存。
- 主列表和详情删除改为 `moveTrashItemOut`，待清理页才调用 `purgeTrashItem`。
- pending cleanup UI 增加独立页面、倒计时、撤销移出、永久删除二次确认。
- `RemotePendingCleanup` 映射使用服务端 `undoDeadlineMillis`，不再本地重算。
- 系统媒体恢复成功文案提示重复内容由服务端合并展示。

### Server
- `TrashService.purgeTrashItem` 允许 `IN_TRASH` 和 `PENDING_CLEANUP`。
- 新增 `pendingCleanupItemCanBePurgedImmediately` 覆盖 remove 后立即 purge，并验证小相册 purge 不删除全局媒体文件。

### Design
- 顶部用户筛选左置，分类菜单右置。
- 待清理入口只在 `pendingEntries` 非空时显示在分类菜单内。
- 按产品 UI 原则保持熟悉控件，不引入装饰式大改。

### Interaction Feedback
- 主删除确认统一为“移出回收站？”。
- 待清理页永久删除文案按类型说明影响范围。

## Post-implement Self-check
- Validation run: Android `:app:compileDebugKotlin` 通过；Server 待清理立即 purge、24h scheduler purge、系统媒体物理删除、恢复去重定向测试通过。
- New behavior sanity: 主删除与永久删除语义已分离，待清理页可撤销或 purge。
- Contract sanity: Android 合同和 Server 合同已同步 `sourceSmallAlbumId / relatedSmallAlbumIds` 与四类 item type。
- Test plan quality: 包含本地编译/定向测试、相关模块回归和真机路径。
- Known gaps: 真机离线缓存和真实 MinIO/COS 对象清理仍需设备/环境验证。

## New Coupling Recheck
- Module: photo_stream
  - What was rechecked: 服务端已有恢复后照片流去重测试，客户端恢复提示补充合并反馈。
  - Result: `restoredDeletedMediaDoesNotDuplicatePhotoFeedAfterReupload` 通过。
- Module: albums / small album detail
  - What was rechecked: 新增 pending cleanup purge 小相册测试确认结构删除、媒体文件保留。
  - Result: 新增定向测试通过。
- Module: SystemMedia
  - What was rechecked: mediaSystemDeleted purge 路径仍保留物理文件删除测试。
  - Result: `permanentDeleteSystemDeletedMediaRemovesRecordAndLocalFiles` 通过。

## Implement Test Plan
### Locally validated
- Check: `cmd.exe /c gradlew.bat --no-daemon -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :app:compileDebugKotlin`
- Result: 通过。
- Check: `cmd.exe /c mvnw.cmd -Dtest=YingshiServerApplicationTests#pendingCleanupItemCanBePurgedImmediately test`
- Result: 通过，覆盖 remove -> pendingCleanup -> purge 和小相册 purge 不删媒体文件。
- Check: `cmd.exe /c mvnw.cmd -Dtest=YingshiServerApplicationTests#pendingCleanupSchedulerPurgesExpiredItems test`
- Result: 通过，覆盖 24 小时到期自动清理路径。
- Check: `cmd.exe /c mvnw.cmd -Dtest=YingshiServerApplicationTests#permanentDeleteSystemDeletedMediaRemovesRecordAndLocalFiles test`
- Result: 通过，覆盖系统媒体永久删除后记录、评论目标和 local original / preview 物理文件清理。
- Check: `cmd.exe /c mvnw.cmd -Dtest=YingshiServerApplicationTests#restoredDeletedMediaDoesNotDuplicatePhotoFeedAfterReupload test`
- Result: 通过，覆盖恢复后照片流去重。

### Linked-module regression checks
- Module: photo_stream
  - What to recheck: 恢复系统媒体后照片流不重复。
  - Why it can regress: restore 和 upload dedupe 同时影响 feed representative item。
- Module: albums / small album detail
  - What to recheck: 大相册、小相册删除详情、恢复、永久删除后的数量与评论。
  - Why it can regress: purge 会清理 album/post/comment/relation。
- Module: SystemMedia / upload
  - What to recheck: 系统媒体导入状态、删除后重新导入、同 fingerprint 上传。
  - Why it can regress: 永久删除和恢复都会影响本地导入状态和服务端去重。

### Real-device checks for the user
- Scenario: 分类切换缓存
  - Steps: 打开回收站四类各看一次，断网或弱网后反复切换分类。
  - Expected result: 先保留上次内容，不闪空；后台失败时显示只读/错误提示。
- Scenario: 待清理两阶段删除
  - Steps: 任意回收站条目点移出回收站，打开分类菜单里的待清理，撤销一次，再移出并永久删除一次。
  - Expected result: 菜单仅有待清理内容时出现入口；撤销回到回收站；永久删除后待清理移除。
- Scenario: 类型化永久删除
  - Steps: 分别对系统媒体、小相册、大相册执行待清理永久删除。
  - Expected result: 系统媒体 DB/评论/关系/对象删除；相册类只删相册结构/评论/关系/trash，不删全局媒体文件。
- Scenario: 离线详情
  - Steps: 在线打开媒体、小相册、大相册删除详情后断网，再从缓存进入。
  - Expected result: 已缓存详情可查看；写操作提示只读不可执行。

### Still unverified
- Risk: 真实对象存储 MinIO/COS 删除。
  - Why it remains open: 本地单测覆盖 local storage，未连接真实对象存储环境。
  - Best next verification path: 真机或测试环境执行 mediaSystemDeleted pending cleanup purge 后核对 object keys。
- Risk: 真机离线缓存查看态。
  - Why it remains open: 本地编译无法替代设备断网和图片/视频缓存行为。
  - Best next verification path: 在线打开媒体、小相册、大相册回收站详情后断网重进。

## Real-device Issue Log
- None yet. Add entries using `references/device-qa-template.md`.

## Validation Snapshot
### Verified
- Server remove -> pendingCleanup -> immediate purge for small album.
- Small-album purge keeps global media file accessible.
- Server 24h scheduler purges expired pending cleanup items.
- Server mediaSystemDeleted purge removes media record/comment target and local original/preview files.
- Server restore dedupe keeps photo feed from duplicating reuploaded content.
- Android `:app:compileDebugKotlin`.

### Pending
- Real-device offline cache and object storage verification.

### Blocked
- None.

## Closeout Summary
- What shipped:
- What remains risky:
- What was intentionally deferred:

## Carry-forward Notes
- Fact future modules must remember: 回收站主删除是 `remove`，不是物理删除；永久删除只属于待清理或明确二次确认。
- Adjacent module to revisit later: fake/demo 回收站如需演示一致性，可后续补齐待清理页。

## Closeout Self-check
- Brief completeness:
- Remaining risk clarity:
- Carry-forward quality:
