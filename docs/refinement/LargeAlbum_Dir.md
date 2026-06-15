# Large Album Directory

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `LargeAlbum_Dir`
- Status: `closed`
- Last updated: `2026-06-15`
- Primary surfaces: `shared`
- Linked server brief: `none`

## Module Goal
- User value: 大相册目录切换更顺、更像已经整理过的空间，同时补齐真正可用的管理入口。
- Business or product intent: 让大相册和小相册一样进入正式内容管理体系，而不是只支持创建和浏览。
- Success criteria:
  - 三横线菜单可对当前大相册执行重命名和删除。
  - 大相册删除进入独立回收站分类，并支持整册整组恢复。
  - 切换已加载过的大相册时先直出已有内容，再后台刷新，不再反复清空和误报 reconnect 文案。

## Current State
- What exists today:
  - Android 端已有大相册切换、搜索、新建、缓存只读和持久化 read-cache。
  - 服务端只有 `GET /api/albums`、`POST /api/albums`、`GET /api/albums/{albumId}/small-albums`。
  - 回收站只覆盖 `smallAlbumDeleted`、`mediaRemoved`、`mediaSystemDeleted`。
- Known constraints:
  - 需要同时改 Android 与 Server。
  - 服务器仓库当前有大量非本模块未提交改动，本轮只围绕相册/回收站相关文件落地。
  - 缓存只读边界已被登录、照片流、回收站等模块复用，不能破坏现有语义。
- Relevant code or docs:
  - `AlbumPageScreen.kt`
  - `RealPhotoViewModels.kt`
  - `AlbumController.java`
  - `AlbumService.java`
  - `TrashService.java`
  - `docs/contracts/album-api.md`
  - `docs/contracts/trash-api.md`

## Your Current Ideas
- Idea 1: 三横线菜单加删除按钮，大相册删除时里面的小相册也要一起删。
- Idea 2: 切大相册不要每次都重新加载，第一次加载后应尽量直接呈现。
- Open preference: 目录管理和信息表达一起精修，不只补一个按钮。

## Codex Recommendations
### Recommended to finish in this module
- Recommendation: 把大相册纳入独立回收站类型，支持重命名、整组删除、整组恢复。
  - Why it is worth considering: 这能把大相册从“只能创建的分类壳子”升级成正式可管理实体。
  - Impact on usability, robustness, or smoothness: 删除行为和现有内容删除体系一致，降低误删焦虑。
- Recommendation: 把切册体验改成 warm switch，先展示已有内容再静默刷新。
  - Why it is worth considering: 当前体验最大痛点不是“慢”，而是每次切换都像重新进页。
  - Impact on usability, robustness, or smoothness: 页面稳定性更强，离线/弱网状态也更自然。
- Recommendation: 菜单升级成“当前大相册管理入口”，同时补齐空态 CTA 和轻量状态文案。
  - Why it is worth considering: 管理动作、目录浏览、空态引导现在分散且不完整。
  - Impact on usability, robustness, or smoothness: 目录理解成本更低，第一次用也更清楚。

### Defer only with explicit acceptance
- Item: 大相册副标题编辑和排序能力。
  - Why it would otherwise belong in this module: 它们同属大相册管理能力。
  - Why it might still be deferred: 当前前后端都没有相关 contract，本轮先补标题重命名更稳。

## Key Questions
- [x] 大相册删除是否进入回收站：是，新增独立分类。
- [x] 恢复粒度是否整册整组恢复：是。
- [x] 管理能力是否收重命名：是，当前支持改标题和大相册简介。

## Scope Boundaries
### In scope
- 大相册重命名与简介编辑。
- 大相册删除、回收站展示、恢复、清理。
- 大相册目录切换缓存直出和后台刷新。
- 目录空态、删除确认、轻量文案收口。

### Out of scope
- 大相册排序/置顶。
- 新的媒体删除语义。

### Non-negotiables
- 删除大相册不删除媒体本体。
- 缓存只读下不能执行重命名和删除。
- FAKE / REAL 两套行为尽量一致。

### Failure and fallback expectations
- Failure states to support:
  - 重命名失败。
  - 删除失败。
  - 恢复失败。
  - 首次加载失败。
  - 弱网下后台刷新失败。
- Rollback or fallback behavior:
  - 已缓存的大相册优先保留可见内容，只把失败作为轻提示，不清空。
  - 回收站失败时保持现有记录和分类不乱跳。

## Related Modules
- Module: `photos_root`
  - Relationship: 新建大相册、notice、回收站入口、全局 mutation bus 都在这里汇流。
  - Recheck before ship: 创建后选中态、删除后回收站分类跳转、notice 文案是否一致。
- Module: `photo_stream`
  - Relationship: 大相册整组删除会影响照片流内容可见性。
  - Recheck before ship: 删除/恢复后照片流刷新是否同步。
- Module: `trash`
  - Relationship: 需要新增大相册删除分类与详情语义。
  - Recheck before ship: 分类、详情、恢复、purge 文案和行为是否完整。
- Module: `post composer / post editing / system media destinations`
  - Relationship: 它们都读大相册列表。
  - Recheck before ship: 重命名和删除后列表是否及时同步。

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories:
  - `AlbumPageScreen.kt`
  - `RealPhotoViewModels.kt`
  - `PhotosRootScreen.kt`
  - `Contracts.kt`
  - `RealRepositories.kt`
  - `AlbumApi.kt`

### Server endpoints and payloads
- Controllers, services, DTOs, contracts:
  - add `PATCH /api/albums/{albumId}`
  - add `DELETE /api/albums/{albumId}`
  - extend trash item type with `largeAlbumDeleted`

### Shared rules
- Auth, permissions, identity, ordering, time, copy:
  - bearer auth 继续沿用。
  - 删除确认明确“不删除媒体本体，可在回收站整组恢复”。
  - 普通切册不再使用“网络已恢复”文案。

## UI and Visual Details
- Layout or information hierarchy: 菜单从“纯选册器”升级为“选册 + 当前大相册管理”。
- Components and states: 新增重命名对话框、删除确认、回收站新分类。
- Motion or transitions: 切册保留现有内容，不再先闪空再进加载卡。
- Copy notes: 强调“小相册会一并移入回收站”“媒体本体不会删除”。

## Interaction Feedback
- Loading: 首次冷加载才显示加载卡；warm switch 走静默刷新。
- Empty: 空大相册显示“新建小相册”；无大相册显示“新建大相册”。
- Error: 失败走 notice 或 inline notice，不抢掉已有内容。
- Success: 重命名、删除、恢复给清晰 notice。
  - 大相册 rename/delete 成功提示需在数秒内自动消失；仅改简介时文案不能误报“已改名为”。
- Permission denial: 无额外系统权限变化。
- Offline or retry: 缓存只读下可浏览已缓存内容，不可执行管理动作。

## Deployment Readiness
- Release-critical expectations:
  - REAL contract、回收站语义、Android 分类解析必须一致。
  - 删除/恢复不能破坏照片流与小相册详情联动。
- Anything that must be true before moving to the next module:
  - 服务端测试至少覆盖 rename/delete/restore/purge 主链路。
  - Android 至少完成编译级验证和关键状态自检。
- Acceptable defers, if any:
  - 大相册副标题编辑。
  - 大相册排序。

## Hidden Impact Checklist
- Notifications: no direct push change expected; recheck if trash notifications surface new type labels.
- Auth: continue bearer auth and cached read-only fallback.
- Upload: album destination lists must refresh after rename/delete.
- Comments: large album delete should not invent new comment semantics; child small-album trash semantics remain the source of truth.
- Viewer: opening a deleted child small album should continue to follow existing unavailable/trash paths.
- Settings: no direct settings change beyond existing cached read-only behavior.
- Analytics or logging: no dedicated analytics found; no new logging requirement chosen.
- Cache or offline: album directory warm-switch and read-cache behavior are core scope.
- Permissions: no new Android permission or server role rule.
- Copy and empty states: new delete confirmation, trash labels, and warm refresh copy must stay consistent.

## Plan Self-check
- Recommendation quality: 已把回收站语义、warm switch、完整管理入口作为本轮主建议，而不只是扩写“加删除按钮”。
- Scope pressure test: 本轮聚焦重命名、删除回收站、切册体验三件事；副标题编辑和排序显式延后。
- Contract and dependency pressure test: 已确认需要同步 Android repository/API、server album/trash contract、回收站分类与照片流联动。
- UX state pressure test: 已覆盖首次加载、warm switch、空态、失败、缓存只读、删除确认和恢复。
- Risks to watch in implement: 回收站新类型的恢复/purge 事务一致性，以及切册静默刷新时状态不串册。

## Implementation Notes
### Client
- `AlbumPageScreen.kt`
  - 三横线菜单升级为“选册 + 当前大相册管理”，补齐大相册信息编辑对话框、删除确认，并把当前大相册管理卡片压轻量。
  - 管理卡片去掉“当前大相册”强调式标题，整体高度再压低一档；卡片正文优先展示大相册说明。
  - 目录列表项不再展示“会同步到照片...”类说明，只保留更轻的数量副文案。
  - 空大相册改为直接给“新建小相册” CTA；无大相册时给“新建大相册” CTA。
  - 普通切册不再显示“网络已恢复…”类文案。
  - 预览封面改为稳定 `refreshKey`，不再在每次切换大相册时人为拼接新 nonce，避免两张封面图反复重刷。
  - 顶部成功提示补自动消失；大相册 chips 改为显式“前 4 字 + ...”而不是依赖布局省略。
  - 为避免顶栏 chips 再被三等分挤成“3 字 + ...”，切为横向可滚动的固定最小宽度胶囊，不再对已格式化标题做二次 `Ellipsis`。
- `RealPhotoViewModels.kt`
  - 大相册切换改成 warm switch：命中过往缓存时直接展示缓存内容，再静默刷新。
  - 用 `postsByAlbumId.containsKey(albumId)` 区分“未加载”和“已知为空”。
  - 去掉会干扰封面复用的 album-level refresh nonce，warm switch 优先复用已缓存的小相册卡片与封面预览。
  - 新增大相册标题/简介编辑、delete 调用、缓存更新、mutation bus 通知与缓存只读限制。
  - posts 成功回写缓存时，保留真实大相册 description/systemKey/includeInPhotoFeed，不再把 UI 里的“共 X 个小相册”误写回远端 subtitle 语义。
  - rename 成功后本地目录状态优先采用本次提交的标题/简介，避免管理卡片和目录弹窗仍显示旧说明；成功提示按“改名 / 改简介 / 同时更新信息”区分文案。
  - 额外加一层 pending override：即使紧随其后的 `GET /api/albums` 短暂回旧 subtitle，当前页和本地缓存仍先保住刚提交的大相册标题/简介，直到列表读到一致值为止。
- `TrashModels.kt` / `RealPhotoUiMappers.kt` / `RealTrashViewModels.kt`
  - Android 端接通 `largeAlbumDeleted` 新类型解析与请求映射。
  - 回收站请求失败时稳定保留错误态，不再和“当前分类为空”来回切换。
  - REAL 小相册删除详情补精简数据兜底，避免详情页在无完整快照时闪退。
  - `largeAlbumDeleted` 额外兼容旧后端/旧网关上报的 `Unsupported trash item type(s)` 变体，必要时自动退回到全量列表后本地过滤。
  - `ReconnectRefreshEffect` 只在真正经历“断线后重连”时才自动刷新，避免错误态页面因历史网络变更被反复触发刷新而闪屏。
- `FakeAlbumRepository.kt` / `FakeTrashRepository.kt` / `FakeRepositories.kt`
  - FAKE 侧补齐大相册 rename/delete/snapshot/restore，与 REAL 行为尽量保持一致。
  - 小相册 restore 允许空媒体快照恢复，避免整册恢复时因为空集被拦住。
  - FAKE 大相册简介改为真正落在 `description` 字段，不再错写进数量副文案 `subtitle`。

### Server
- 服务端主链路已在本轮前段实现：
  - 大相册 `PATCH /api/albums/{albumId}`、`DELETE /api/albums/{albumId}`
  - `PATCH` 当前同时支持更新 `title` 与 `subtitle`
  - `albums.deleted_at`
  - `largeAlbumDeleted` 回收站类型
  - 整册整组 restore / purge
  - 不再用 `systemKey` 拦截大相册 rename/delete；若 Life Console 已重建同类相册，restore 时自动清掉旧册 `systemKey`，避免重复 active key

### Design
- 管理入口保持在目录菜单内部，不把 rename/delete 拆到额外页面。
- 当前选中大相册在目录弹窗内被提升成单独的管理卡片，强化“我现在正在管理哪一本”的感知。
- 大相册目录行只保留“会同步到照片流 / 仅目录整理”这类轻量信息，不再把首册抬成系统目录语气。

### Interaction Feedback
- 冷切换：显示加载卡。
- 热切换：保留旧内容直出或已知空态，后台静默刷新。
- 删除确认：明确“小相册会一起进回收站，媒体本体不会删除，可整组恢复”。
- 缓存只读：目录仍可浏览，但禁用 rename/delete/new-create 管理动作。

## Post-implement Self-check
- Validation run:
  - Android：`cmd.exe /c "set \"JAVA_HOME=E:\Soft\JDK\" && cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"`
  - Server：`powershell.exe -NoProfile -Command 'Set-Location "E:\Study\App\YingShi-Server"; $env:JAVA_HOME="E:\Soft\JDK"; & .\mvnw.cmd --% -q -Dtest=YingshiServerApplicationTests,PostgresqlMigrationSafetyTest test'`
- New behavior sanity:
  - REAL / FAKE 都已支持大相册 rename/delete。
  - 已加载过的大相册切换不再先清空再加载，封面预览也优先复用已缓存卡片。
  - 已知空大相册切换时直接展示空态，不再误进 blocking loading。
  - `人物记录` 这类带 `systemKey` 的大相册也可直接 rename/delete，不再被目录菜单拦住。
- Contract sanity:
  - Android repository / API / mapper / trash type 与 server contract 已对齐到 `largeAlbumDeleted`。
  - server 回归测试已覆盖 `largeAlbumDeleted` 列表筛选，以及 Life Console 大相册 rename/delete/restore 冲突分支。
  - 2026-06-15 新核验：本地新 server 运行体在 `18080` 上对 `PATCH /api/albums/{albumId}`、`DELETE /api/albums/{albumId}` 返回 `401`，已不再是旧运行体的 `404`。
- Test plan quality:
  - 本地已覆盖编译级校验与 server 主链路测试；真机留给 `verify` 阶段集中回收体验问题。
- Known gaps:
  - 尚未做真机 QA。
  - REAL 回收站的大相册详情仍以摘要和相关媒体为主，不提供 server-side child snapshot 逐条展开。
  - 旧 docker / 旧本地运行体仍停留在不含大相册 `PATCH/DELETE` 的版本；真机要真正生效，必须切到已完成 `V14__large_album_directory_support.sql` 迁移的新后端。
  - Server 这轮暴露出 Flyway 冲突：`V12__large_album_directory_support.sql` 与 `V12__add_auth_login_challenges.sql` 重号；已改为 `V14__large_album_directory_support.sql`，需要用 `clean package` 后的新产物接管现有服务。

## New Coupling Recheck
- Module: `photo_stream`
  - What was rechecked: 大相册 delete / restore 的 mutation bus 作用域是否覆盖照片流。
  - Result: delete 走 `notifyRealBackendContentChanged(...)`，会联动照片流、回收站与目的地列表刷新。
- Module: `system media destinations`
  - What was rechecked: 大相册 rename/delete 后目的地列表是否会刷新。
  - Result: rename 走 `notifyRealBackendAlbumsChanged()`；delete 走 content change，均覆盖该模块。
- Module: `trash`
  - What was rechecked: 新类型解析、筛选、行文案、详情分支是否齐。
  - Result: Android 端主要入口已接通；REAL 详情展示以摘要为主。
- Module: `life_console`
  - What was rechecked: 带 `systemKey` 的大相册放开 rename/delete 后，删除再重建再 restore 是否会撞出重复 active system key。
  - Result: 服务端 restore 分支会在检测到 replacement album 后清掉旧册 `systemKey`，避免 Life Console lookup 歧义。

## Implement Test Plan
### Locally validated
- Check: Android Kotlin 编译
- Result: 通过
- Check: server rename/delete/restore/purge 主链路测试
- Result: 通过

### Linked-module regression checks
- Module: `photo_stream`
  - What to recheck: 删除大相册后，相关小相册入口是否从照片流联动消失；恢复后是否回来。
  - Why it can regress: 整册删除通过回收站批量影响子小相册。
- Module: `trash`
  - What to recheck: 大相册分类筛选、详情、恢复、purge 文案与行为。
  - Why it can regress: 新增了第四种 `itemType`。
- Module: `post composer / system media destinations`
  - What to recheck: 大相册 rename/delete 后目的地列表是否立刻同步。
  - Why it can regress: 这些入口依赖相册列表缓存和 mutation bus。

### Real-device checks for the user
- Scenario: 热切换大相册
  - Steps: 连续切换两个已经打开过的大相册，再切回第一个。
  - Expected result: 内容直接出现，不闪空，不出现“网络已恢复...”类文案。
- Scenario: 删除大相册
  - Steps: 在目录菜单中删除一个包含多个小相册的大相册，再去回收站看对应分类。
  - Expected result: 出现“大相册删除”分类项；删除确认文案清楚说明“媒体本体不会删除”。
- Scenario: 整组恢复
  - Steps: 从回收站恢复刚删除的大相册。
  - Expected result: 大相册和同批小相册一起恢复，目录和相关内容列表同步回来。
- Scenario: `人物记录` 大相册
  - Steps: 打开首个“人物记录”大相册目录菜单，尝试重命名和删除。
  - Expected result: 可以正常 rename/delete，不再提示“由空间自动维护”。
- Scenario: 大相册删除分类
  - Steps: 打开回收站 `largeAlbumDeleted` 分类。
  - Expected result: 分类列表正常返回，不出现请求失败。

### Still unverified
- Risk: 真机上的目录菜单层级、对话框节奏和 notice 停留时长是否自然。
  - Why it remains open: 目前只做了编译级验证，未看实际触控节奏。
  - Best next verification path: 进入 `verify LargeAlbum_Dir`，按五个 real-device scenario 逐项回报。

## Validation Snapshot
### Verified
- Planning decisions and scope are locked.
- Android Kotlin 编译通过，并在 2026-06-15 再次复编通过。
- Server rename/delete/restore/purge 主链路测试通过。
- Docs/contracts 已同步到 album + trash 新 contract。
- Life Console 大相册 rename/delete 已放开，并补了 restore 防重 system key 保护。
- `largeAlbumDeleted` server list filter 已有回归测试覆盖。
- 目录管理卡片已切到真实 description 作为说明层。
- 大相册热切换现在保留小相册卡片预览，不再只保留列表骨架。
- 本地 `8080` 运行体仍命中新后端：`PATCH /api/albums/{albumId}`、`GET /api/trash/items?itemType=largeAlbumDeleted` 均返回 `401` 而非旧版缺路由 `404`。
- 回收站重连刷新已改为只在真实“断线后重连”时触发，避免错误态页面自循环刷新。

### Pending
- 真机仍建议回归当前大相册管理卡片、回收站列表/详情，以及 warm-switch 下封面预览是否彻底稳定。
- 若设备仍出现 rename/delete 无效，优先检查真机连接的后端是否已切到包含 `PATCH /api/albums/{albumId}` 与 `DELETE /api/albums/{albumId}` 的新运行体。

### Blocked
- None.

## Closeout Summary
- What shipped: album rename/delete, subtitle editing, large-album trash type, warm-switch caching, and chips / notices polish.
- What was validated: Android compile, server feature tests, and the live backend route checks already recorded above.
- What remains risky: real-device UX recheck for current album management, trash/detail flows, and cover-preview warm-switch behavior.
- What was intentionally deferred: subtitle ordering / reordering support.

## Carry-forward Notes
- Large album warm-switch must preserve both list state and fetched cover previews.
- Do not use `systemKey` as the large-album rename/delete gate on either client or server.
- If a deleted Life Console large album is restored after a replacement album was recreated, keep only one active `systemKey` owner.
- Recheck `photo_stream`, `trash`, and `system media destinations` when they next touch album-list caching.

## Closeout Self-check
- Brief completeness: high
- Remaining risk clarity: high
- Carry-forward quality: high
