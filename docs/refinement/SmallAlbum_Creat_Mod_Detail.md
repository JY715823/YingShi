# SmallAlbum Create / Edit / Detail Refinement

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `SmallAlbum_Creat_Mod_Detail`
- Status: `closed`
- Last updated: `2026-06-17`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: make small album create, edit, and detail feel polished, controllable, and visually consistent.
- Business or product intent: finish the small-album flow to deployment-ready quality with cleaner ownership semantics and stronger interaction quality.
- Success criteria:
  - create page media list can auto-apply without an extra save boundary
  - ownership is explicit in create/edit/detail and persists correctly
  - detail page title, ownership, spacing, and density zoom feel aligned with the photo feed
  - contributor-based placeholder behavior no longer leaks into summary or ownership display

## Current State
- What exists today:
  - create page has explicit create action, but media-list subpage still requires save
  - edit page uses a different large-album chip style and has no explicit ownership management
  - detail page still shows a fixed "小相册详情" title and milder visuals
  - participant ids already exist in backend DTOs, but create/edit do not explicitly manage them
- Known constraints:
  - current post contract still uses a single `albumId`
  - system-media create flow must keep background upload create behavior
  - fake and real repositories both power active product surfaces
- Relevant code or docs:
  - `app/src/main/java/com/example/yingshi/feature/photos/PostComposerScreen.kt`
  - `app/src/main/java/com/example/yingshi/feature/photos/GearEditScreen.kt`
  - `app/src/main/java/com/example/yingshi/feature/photos/PostDetailScreen.kt`
  - `app/src/main/java/com/example/yingshi/feature/photos/RealPostEditingViewModels.kt`
  - `app/src/main/java/com/example/yingshi/feature/photos/FakeAlbumRepository.kt`
  - `app/src/main/java/com/example/yingshi/data/model/PostAlbumRepositoryModels.kt`
  - `app/src/main/java/com/example/yingshi/data/remote/dto/PostDto.kt`
  - `YingShi-Server/src/main/java/com/yingshi/server/dto/content/CreatePostRequest.java`
  - `YingShi-Server/src/main/java/com/yingshi/server/dto/content/UpdatePostRequest.java`
  - `YingShi-Server/src/main/java/com/yingshi/server/service/content/PostService.java`

## Your Current Ideas
- Idea 1: create-page media list should auto-save and support one-tap remove plus clear-all.
- Idea 2: edit-page album chips should be fixed and ownership should be manageable.
- Idea 3: detail page should be more vivid, tighter, and support photo-feed-like density zoom.

## Codex Recommendations
### Recommended to finish in this module
- Explicitly promote `participantUserIds` to the user-facing ownership model across create/edit/detail.
  - Why it is worth considering: the field already exists end to end and only needs contract and UI closure.
  - Impact on usability, robustness, or smoothness: removes drifting ownership display and makes state predictable.
- Split media-list behavior by mode instead of globally changing it.
  - Why it is worth considering: create needs auto-apply while edit/media management still benefit from an explicit commit.
  - Impact on usability, robustness, or smoothness: achieves the requested create experience without regressing other surfaces.
- Reuse photo-feed density logic directly in detail media grid.
  - Why it is worth considering: the repo already has the exact density system and spacing rules.
  - Impact on usability, robustness, or smoothness: creates a familiar, high-quality zoom interaction with lower implementation drift.

### Defer only with explicit acceptance
- Extending ownership avatars to every small-album list/card variant beyond touched screens.
  - Why it would otherwise belong in this module: it improves consistency across the module.
  - Why it might still be deferred: some adjacent card surfaces may require extra layout rebalancing.

## Key Questions
- [x] create auto-save scope is only the selected-media subpage, not the whole create screen
- [x] ownership should reuse `participantUserIds`
- [x] detail zoom means in-page density zoom with 2 / 3 / 4 / 8 / 16 levels

## Scope Boundaries
### In scope
- create page media-list auto-apply mode and media CTA polish
- ownership selection and persistence in create/edit/detail
- edit-page visual and save-feedback polish
- detail-page title, ownership, summary/meta hierarchy, denser visual treatment, and density zoom feedback
- client/server contract updates needed for explicit ownership

### Out of scope
- multi-large-album assignment for small albums
- removing the final create action from the create screen
- broader viewer protocol redesign

### Non-negotiables
- create still keeps a final create boundary
- ownership can be one or two people, but never zero
- fake and real behavior must stay aligned

### Failure and fallback expectations
- Failure states to support:
  - missing login or album seed failure on create/edit
  - backend create or save failure
  - invalid ownership selection attempts
  - detail refresh and add-media failures
- Rollback or fallback behavior:
  - create/edit should preserve local draft state on request failure
  - density zoom should gracefully fall back to the current density if gesture state is interrupted

## Related Modules
- Module: `PhotoFeed`
  - Relationship: shares density zoom levels, spacing rules, and gesture expectations
  - Recheck before ship: detail page density transitions and thumbnail sizing
- Module: `Trash`
  - Relationship: edit/detail media removal still routes through trash behavior
  - Recheck before ship: remove-from-post semantics and detail empty-after-delete handling
- Module: `LargeAlbum_Dir`
  - Relationship: create/edit both select a parent large album
  - Recheck before ship: album chips, directory dialog, and selected-album refresh

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories:
  - create route/draft/ui state
  - gear edit fake + real state
  - post detail info section and media grid
  - fake and real post repositories

### Server endpoints and payloads
- Controllers, services, DTOs, contracts:
  - `POST /api/posts`
  - `PATCH /api/posts/{postId}`
  - request DTOs now need explicit `participantUserIds`

### Shared rules
- Auth, permissions, identity, ordering, time, copy:
  - ownership comes only from explicit participant selection
  - summary fallback should not use contributor label copy
  - media ordering and cover updates remain explicit

## UI and Visual Details
- Layout or information hierarchy:
  - create becomes a memory-arrangement flow
  - edit becomes a control-console flow
  - detail prioritizes title, compact meta, ownership avatars, then summary and album chip
- Components and states:
  - collaborator avatar chips for ownership selection
  - avatar stack in detail header/meta
  - auto-apply media-list mode for create
- Motion or transitions:
  - detail density zoom reuses photo-feed gesture levels
  - density feedback uses a brief inline overlay
- Copy notes:
  - remove contributor-style placeholder wording
  - success feedback should distinguish rename vs summary vs general save

## Interaction Feedback
- Loading: keep existing backend loading cards and draft loading states.
- Empty: create/detail media empty states get stronger CTA wording.
- Error: preserve inline notices and avoid losing current local edits.
- Success: use short-lived inline notices for ownership/media summary changes and detail density hints.
- Permission denial: no new permission surface beyond existing photo/media access.
- Offline or retry: preserve existing retry entry points for detail and save failures.

## Deployment Readiness
- Release-critical expectations:
  - ownership must persist and render consistently across fake and real data
  - create subpage auto-apply must not regress edit/media-management save behavior
  - detail density zoom must not break open-viewer, selection mode, or add-media CTA
- Anything that must be true before moving to the next module:
  - post contract and docs must match the shipped request shapes
- Acceptable defers, if any:
  - broader avatar exposure on every adjacent list card can defer if untouched layouts become unstable

## Hidden Impact Checklist
- Notifications: no direct rule change found.
- Auth: create/edit real flows still depend on login and token recovery.
- Upload: system-media create path must carry ownership through queued create draft.
- Comments: detail comment entry points remain, but title/meta hierarchy changes above them.
- Viewer: detail density zoom must not interfere with open-viewer behavior.
- Settings: no impact found.
- Analytics or logging: no dedicated logging surface found.
- Cache or offline: local drafts should remain intact on failed create/edit requests.
- Permissions: no new permission rule found.
- Copy and empty states: summary fallback and create/detail empty copy change directly.

## Plan Self-check
- Recommendation quality: turned the module into a cohesive create/edit/detail pass instead of a loose list of tweaks.
- Scope pressure test: still focused on one entity and one contract family despite spanning client and server.
- Contract and dependency pressure test: ownership now explicitly touches request DTOs, repositories, edit VM, upload finalize flow, detail UI, and fake data.
- UX state pressure test: create, edit, detail, empty, save-failure, delete-after-empty, and login-missing paths were all rechecked.
- Risks to watch in implement: create media-list mode must not regress edit/media-management flows; density zoom must stay stable with selection mode.

## Implementation Notes
### Client
- 创建页媒体子页已切到 `AUTO_APPLY` 模式，排序、设封面、单张移除都会即时回写；创建页新增一键清空确认、所属摘要头、所属头像勾选区与更强的媒体主区。
- 创建页所属默认值改为当前账号，不再默认双选；新增 `normalizeOwnedCollaboratorSelection(...)`，避免“小相册所属”继续复用筛选场景里的“空即全选”语义。
- 创建页与编辑页的“整理这条记忆”摘要卡片已去掉；创建页媒体区也去掉了“已选媒体会直接参与封面和创建结果...”说明。
- 编辑页补回媒体预览和“全部”入口，所属选择与创建页统一，所属大相册 chips 与创建页共用一套样式；底部保存/删除按钮间距已收紧。
- 创建态媒体列表子页在 `AUTO_APPLY` 模式下改为单张删除直接生效，顶栏新增“一键清空”入口并保留一次确认边界。
- 详情页顶栏改成“小相册标题 + 所属头像”，信息区去掉重复大标题，主信息收敛到“时间 + 张数 + 所属”；简介改成更有存在感的高亮承载块；媒体网格继续使用 `2 / 3 / 4 / 8 / 16` 档位，并补上更接近照片流的缩放预览/回弹/提交过渡，去掉列数提示，左右边距进一步贴边。
- 详情页空媒体态文案与 CTA 已升级，所属大相册胶囊在无标题时不再显示空占位。
- 追加收尾：详情页信息白区简介去掉包裹框，直接融入背景层；主标签只保留时间和张数，所属完全交给标题旁头像表达。编辑页 fake / real 两条链都补了“详情页或别处新增媒体后同步进编辑态”的合并刷新逻辑，优先保留未保存草稿，再吸收外部新媒体；保存/删除按钮间距也进一步压紧。
- 继续收尾：编辑页点开的媒体列表改为真正的 `AUTO_APPLY` 行为，单张删除和“设为封面”都会即时回写编辑页，不再依赖右上角保存；封面预览语义也从“单张封面”收敛成“前两张媒体组成双拼预览，首张仍作为主封面 id”。
- 小相册详情页媒体网格补回照片流同系能力：视频自动播放、播放按钮、视频剩余时长角标、横滑多选命中逻辑都按照片流手法对齐；详情背景也改成和照片流同系的多层渐变光晕，而不是单层蓝底。
- 本轮继续收口：编辑页媒体列表 `AUTO_APPLY` 场景已去掉右上角“完成”，恢复“单删确认 + 自动回写”，而创建页媒体列表仍保持单删直达；双封面改成“已有两张时按更早设置的那张轮流替换”，不再一直覆盖第一个封面位。
- 详情页媒体网格与照片流进一步对齐：媒体区外层和单卡都补了 `clipToBounds()`，缩略图 / 内联视频 / 媒体框统一走 `matchParentSize()` 结构，避免视频自动播放时撑出原有网格尺寸；编辑页 fake / real 保存成功提示也已改成约 `2.6s` 自动消失。
- 本轮继续收口（第二波）：双封面替换从“易被自动回写重置的槽位”改成“封面替换队列”，避免每次都顶掉第一个；详情页背景改成更强的背景底板 + 半透明信息浮层，避免外层淡渐变在真机上看起来仍像旧蓝底。
- 本轮继续收口（第三波）：封面/缩略图缓存拆成“内存刷新 key + 稳定磁盘 key”，相册卡片、小相册详情、照片流预取与查看器预取都统一按媒体稳定身份落盘，降低断网后封面消失的概率；详情页背景再次加强为高饱和暖光、青绿、蓝紫多层光场，并在照片区背后单独加氛围底板，避免真机上仍像旧蓝底。
- 本轮继续收口（第四波）：确认离线大相册目录纯色封面的核心原因是读缓存只持久化 `RemotePostSummary`，缺少卡片封面所需的 `previewMedia/mediaSource`；`CachedAlbumDirectory` 新增 `previewMediaByPostId`，联网补齐封面详情后会把前两张预览媒体写入读缓存，离线冷启动、断网切相册、请求失败 fallback 都会用该缓存恢复真实封面。封面预热条件也从“必须有 `coverMediaId`”放宽为“有媒体即可”，避免默认第一张媒体当封面的小相册仍然离线纯色。详情页背景从上一版高饱和独立配色收回为照片流同源的 `glowWash / memoryContainer / sectionBackground` 光晕体系，保留层次但减少与照片流的风格差。
- 本轮补齐必要编辑能力：新建小相册和编辑小相册都新增“小相册时间”可点击行，直接复用照片查看器的 `ViewerTimeEditorSheet`；创建页确认后更新草稿 `displayTimeMillis`，编辑页 fake / real 确认后标记为未保存改动，并沿用既有 `CreatePostPayload` / `UpdatePostBasicInfoPayload` 的 `displayTimeMillis` 保存合同。

### Server
- `participantUserIds` 已贯通到 create / update 请求合同与 `PostService`，并保持“至少一位所属”的校验。
- `addMediaToPost()` 不再自动把操作者合并进所属集合，避免所属被悄悄污染。
- `post-api.md` 已补充 `participantUserIds` 请求字段示例，和当前客户端合同对齐。
- 2026-06-17 追补后端同步审计：`CreatePostRequest` / `UpdatePostRequest` 增加所属集合非空校验，`PostService` 移除 create/update 的 fallback 补人逻辑，空所属现在由服务端明确返回 `VALIDATION_ERROR`。

### Design
- 创建页更偏“记忆编排台”，编辑页更偏“整理控制台”，详情页更偏“记忆现场”，三页信息层级与 ownership 视觉语义已经打通。

### Interaction Feedback
- 创建页清空、所属切换、媒体移除提供短提示；编辑页 real 分支回显更细粒度保存成功文案；详情页去掉列数提示，改为更安静的照片流式缩放过渡。

## Post-implement Self-check
- Validation run: `:app:compileDebugKotlin` 已通过，使用 Windows 侧 `java.exe` 直接拉起 Gradle wrapper 编译；`mvnw.cmd -q -DskipTests compile` 也已通过。
- New behavior sanity: 创建/编辑/详情三条主线已按本轮 scope 收口，创建页保留最终创建边界，媒体子页才做自动应用。
- Contract sanity: `participantUserIds` 已在 client state、DTO、fake/real repo、queued upload finalize 与 server service 对齐；详情 ownership 展示改为只认该集合。
- Test plan quality: 已补本地验证、联动回归和真机检查项，覆盖所属、媒体自动应用、详情缩放和 linked modules。
- Known gaps: 还没有在真机上验证双指缩放、选择模式与回收站联动的手感。
- 本轮补充验证：编辑页外部媒体同步与详情白区样式调整后的 `:app:compileDebugKotlin` 已再次通过；尚未做真机回归来确认“详情加媒体后回到编辑页”的实际手感和时序。
- 再次补充验证：详情页视频自动播放、横滑多选命中、编辑页媒体列表自动应用与双封面预览调整后的 `:app:compileDebugKotlin` 已通过；这轮仍未做真机多指/视频滚动回归。
- 最新补充验证：编辑页“单删确认但自动回写”、创建页“单删直达”、双封面轮替替换、详情页视频裁切与背景层继续对齐后的 `:app:compileDebugKotlin` 已再次通过；仍需真机确认多选横滑手感和视频自动播放观感是否完全达到照片流同款。
- 最新补充验证：封面替换队列与详情背景底板改造后的 `:app:compileDebugKotlin` 已通过；仍需真机确认第二次/第三次设封面是否按旧封面位轮流替换，以及详情页背景是否已经明显摆脱旧蓝底。
- 最新补充验证：封面离线缓存稳定 disk key、视频 poster 稳定落盘 key、详情页高饱和背景光场后的 `:app:compileDebugKotlin` 已通过；仍需真机断网确认已经显示过的相册封面是否能从磁盘缓存回显，以及背景是否足够明显。
- 最新补充验证：相册目录 `previewMediaByPostId` 持久化与详情页照片流同源背景调整后的 `:app:compileDebugKotlin` 已通过；需要真机先联网打开一次相册目录完成封面预热，再断网/杀进程重进确认封面是否保留。
- 最新补充验证：新建/编辑小相册时间选择入口接入 `ViewerTimeEditorSheet` 后，`:app:compileDebugKotlin` 已通过；仍需真机确认时间 sheet 交互、保存后大相册排序/详情时间是否回显正确。
- 2026-06-17 后端同步追验：`cmd.exe /c mvnw.cmd -q -DskipTests compile` 通过；`cmd.exe /c mvnw.cmd -q -Dtest=YingshiServerApplicationTests#contentMutationApisWorkForCurrentSpace test` 通过，覆盖 create/update 所属写入与空所属拒绝。整套 `mvnw.cmd -q test` 仍被既有 migration 路径测试和大相册目录测试阻断，非本轮小相册合同变更引入。

## New Coupling Recheck
- Module: `PhotoFeed`
  - What was rechecked: 详情页缩放档位、row spacing、thumbnail request size 直接复用照片流实现。
  - Result: Android Kotlin 编译通过，说明复用链路成立；仍需真机确认缩放锚点与手感。
- Module: `LargeAlbum_Dir`
  - What was rechecked: 创建/编辑页所属大相册 chips 视觉统一，目录弹窗入口仍保留。
  - Result: 编译通过，创建与编辑都共用相同 chips 风格。

## Implement Test Plan
### Locally validated
- Check: `:app:compileDebugKotlin`
- Result: 成功，通过 Windows 侧 JDK 启动 Gradle wrapper 完成 Kotlin 主编译。
- Check: participant ownership 相关符号清理
- Result: `mergePostParticipant` 残留已清掉，共享 ownership 组件和 helper 已可被创建/编辑/详情共同引用。

### Linked-module regression checks
- Module: `PhotoFeed`
  - What to recheck: 详情页双指缩放后，滚动锚点、打开大图、添加媒体按钮与过渡动画是否仍顺手。
  - Why it can regress: 详情页现在不只复用档位和 spacing，还补了同方向的缩放预览/回弹/提交过渡。
- Module: `Trash`
  - What to recheck: 详情页选择模式移出媒体后，回收站记录与空相册返回逻辑是否稳定。
  - Why it can regress: 本轮改了详情页媒体删除后的空态跳转和提示节奏。
- Module: `LargeAlbum_Dir`
  - What to recheck: 创建/编辑页选择所属大相册后的回显和返回刷新。
  - Why it can regress: 本轮统一了 chips 组件并保留目录弹窗切换。

### Real-device checks for the user
- Scenario: 创建页媒体自动应用
  - Steps: 新建小相册，进入“全部”，调整顺序、改封面、移除一张，再返回创建页。
  - Expected result: 无需再点保存，创建页预览、封面标识和媒体计数立即同步。
- Scenario: 创建/编辑页所属
  - Steps: 分别在创建页和编辑页切换“我 / 女朋友”勾选，尝试取消最后一个勾选，再保存并重新进入。
  - Expected result: 默认只勾当前账号；最后一个勾选不可取消；保存后详情页标题旁头像和所属主信息条正确回显。
- Scenario: 详情页缩放与贴边
  - Steps: 在详情页媒体区双指缩放多次，切换到 2 / 3 / 4 / 8 / 16 列，并尝试进入选择模式、分享、移出媒体、再加媒体。
  - Expected result: 不再显示列数提示；网格更贴边；缩放过渡更接近照片流；缩放、选择模式和添加媒体按钮不会互相打架。
- Scenario: 详情加媒体后编辑页同步
  - Steps: 先进入一个小相册详情页添加 1 张媒体，再返回编辑页或重新打开编辑页。
  - Expected result: 编辑页媒体预览和“全部”列表都能看到新加的媒体；如果编辑页里还有未保存的标题/简介改动，这些草稿不应被外部刷新冲掉。
- Scenario: 编辑页媒体列表自动应用与双封面
  - Steps: 在编辑页进入“全部”，依次删除 1 张、把某张设为封面、再把另一张也设封面、最后再点第三张设封面。
  - Expected result: 每次操作都立即回写编辑页；大卡片预览按前两张拼接；第三次设封面会替换掉原先第 1 张封面位，同时保留原第 2 张。
- Scenario: 创建页与编辑页删除语义分叉
  - Steps: 分别进入创建页媒体列表和编辑页媒体列表，各自点击单张删除。
  - Expected result: 创建页单删直接生效并即时回写；编辑页单删先弹确认框，确认后才自动回写，不再出现右上角“完成”。
- Scenario: 详情页视频自动播放与横滑多选
  - Steps: 打开包含视频的小相册详情页，滚动到视频居中位置，再进入多选模式做横向滑选。
  - Expected result: 居中的视频会自动播放；多选模式下横滑可以连续选中；不会再出现“只能点选、不能横滑刷选”的退化。

### Still unverified
- Risk: 详情页双指缩放与选择模式、添加媒体按钮在真实触屏设备上的手感可能仍需细调。
  - Why it remains open: 本轮只完成了本地编译和代码级联动检查，没有真实多指手势验证。
  - Best next verification path: 按本 brief 的真机检查项在 Android 设备上连续测试缩放、长按选择、移出媒体和继续加媒体。

## Real-device Issue Log
### Issue 2026-06-16-01
- Date: `2026-06-16`
- Build version: `unknown`
- Device / OS: `unknown`
- Module key: `SmallAlbum_Creat_Mod_Detail`
- Test environment: `real-device QA`
- Repro steps:
  1. 进入“新建小相册”或“编辑小相册”页面。
  2. 观察顶部摘要区与创建页媒体区说明文字。
  3. 对照预期确认页面是否仍保留“整理这条记忆”卡片和“已选媒体会直接参与封面和创建结果...”说明。
- Expected result:
  - 创建页和编辑页都不再显示“整理这条记忆”摘要卡片。
  - 创建页媒体区不再显示“已选媒体会直接参与封面和创建结果...”说明。
- Actual result:
  - 创建页与编辑页仍显示“整理这条记忆”卡片。
  - 创建页媒体区仍显示“已选媒体会直接参与封面和创建结果...”说明。
- Evidence: `missing`
- Severity: `polish`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area:
  - `PostComposerScreen.kt`
  - `GearEditScreen.kt`
- Next action:
  - 删除创建/编辑页的摘要卡片。
  - 去掉创建页媒体区说明文案，仅保留必要交互按钮和预览。

### Issue 2026-06-16-02
- Date: `2026-06-16`
- Build version: `unknown`
- Device / OS: `unknown`
- Module key: `SmallAlbum_Creat_Mod_Detail`
- Test environment: `real-device QA`
- Repro steps:
  1. 进入小相册详情页。
  2. 在下方照片区进行双指缩放，观察缩放过渡、边距和缩放提示。
  3. 返回信息白区，观察简介的存在感与层级。
- Expected result:
  - 详情页照片区缩放应与照片流一致，包含相同的过渡动画、密度变换体验和贴边感。
  - 不显示“多少列”缩放提示。
  - 照片区左右距离屏幕边缘要更近。
  - 信息白区中的简介层级应更明确，不应显得过弱。
- Actual result:
  - 缩放时仍显示列数提示。
  - 缩放动画和照片流不一致，未达到“直接抄照片流”的效果。
  - 照片区左右边距仍偏大。
  - 简介存在感偏低。
- Evidence: `missing`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area:
  - `PostDetailScreen.kt`
  - `DiscreteZoomGesture.kt`
  - `PhotoFeedScreen.kt`
- Next action:
  - 移除详情页自定义列数提示。
  - 重新对齐照片流的密度切换动画、边距与过渡节奏，而不是只复用档位枚举。
  - 提升简介的视觉权重与层级。

### Issue 2026-06-16-03
- Date: `2026-06-16`
- Build version: `unknown`
- Device / OS: `unknown`
- Module key: `SmallAlbum_Creat_Mod_Detail`
- Test environment: `real-device QA`
- Repro steps:
  1. 进入“新建小相册”页面。
  2. 点击进入“小相册媒体列表”子页。
  3. 点击单张删除，检查是否仍弹二次确认；检查是否存在一键清空入口。
- Expected result:
  - 在创建态媒体列表子页中，单张删除应直接执行，不再二次确认。
  - 子页内应提供一键清空入口，符合创建态“自动应用”语义。
- Actual result:
  - 单张删除仍然有二次确认。
  - 子页内还没有一键清空入口。
- Evidence: `missing`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area:
  - `PostMediaListScreen.kt`
  - `PostComposerScreen.kt`
- Next action:
  - 为 `AUTO_APPLY` 模式单独收口删除交互，移除单张删除确认。
  - 在创建态媒体列表顶栏加入一键清空入口，并保留一次确认边界。

### Issue 2026-06-16-04
- Date: `2026-06-16`
- Build version: `unknown`
- Device / OS: `unknown`
- Module key: `SmallAlbum_Creat_Mod_Detail`
- Test environment: `real-device QA`
- Repro steps:
  1. 进入“编辑小相册”页面。
  2. 滚动到底部，观察“保存”和“删除整个小相册”按钮的布局。
- Expected result:
  - 两个底部按钮应更靠近，形成一个更紧凑的操作区。
- Actual result:
  - 保存和删除按钮之间的距离过大，中间留白过多。
- Evidence: `missing`
- Severity: `polish`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area:
  - `GearEditScreen.kt`
- Next action:
  - 收紧底部按钮区间距，避免过大的垂直空白。

## Validation Snapshot
### Verified
- Case: 编译与合同主干
- Result: Android `:app:compileDebugKotlin` 与服务端 `mvnw.cmd -q -DskipTests compile` 仍通过，当前问题集中在交互与视觉收口。
- Case: 所属持久化主链路
- Result: 本轮未收到“所属丢失、保存失败、详情头像错误”的新设备端反馈，ownership 主链路暂未暴露回归。
- Case: 编辑页媒体列表保存边界
- Result: 代码层已经恢复“编辑页单删确认、创建页单删直达”的分叉语义，并去掉编辑页 `AUTO_APPLY` 右上角“完成”；本地 Kotlin 编译通过。
- Case: 双封面轮替与短提示
- Result: 双封面改为按更早设置位置轮流替换，编辑页 fake / real 保存提示均改成短驻留后自动消失；本地 Kotlin 编译通过。
- Case: 封面缓存与详情页背景
- Result: 代码层已统一公共缩略图、照片流预取、查看器预取的视频/图片封面稳定磁盘缓存 key，并把目录卡片所需的 `previewMedia` 写入读缓存；详情页背景已收回照片流同源光晕体系；本地 Kotlin 编译通过。
- Case: 新建/编辑时间修改
- Result: 创建页和编辑页都已复用 `ViewerTimeEditorSheet` 选择小相册时间，保存合同沿用现有 `displayTimeMillis` 字段；本地 Kotlin 编译通过。

### Pending
- Case: 创建/编辑页摘要卡片和创建页媒体说明文案
- What still needs checking: 真机确认去掉卡片和说明后，页面层级是否自然，尤其是创建页首屏是否仍然顺手。
- Case: 详情页照片区与照片流一致性
- What still needs checking: 缩放过渡、边距、简介层级是否在真机上真正达到照片流同款体验。
- Case: 创建态媒体列表删除与清空交互
- What still needs checking: 创建页单删是否直达生效、编辑页单删是否恢复确认、清空是否具备合适确认边界，并且返回上层后预览立即回写。
- Case: 编辑页底部按钮区间距
- What still needs checking: 收紧后是否兼顾操作区稳定性与误触风险。
- Case: 详情页视频与横滑多选
- What still needs checking: 视频自动播放是否完全限制在原网格尺寸内，多选横滑是否已经和照片流一样稳定。
- Case: 详情页背景和封面轮替
- What still needs checking: 背景是否在真机上显著变成新底板，封面第二次/第三次替换是否还会顶回第一个。
- Case: 断网封面回显
- What still needs checking: 升级后先联网打开一次相册目录，等待封面加载完成，再断网/杀进程重进大相册目录、小相册详情和编辑媒体列表，封面是否能稳定从读缓存 + 磁盘缓存回显，尤其是视频封面。
- Case: 新建/编辑时间修改
- What still needs checking: 真机选择日期和时间后，新建小相册、编辑保存小相册是否按新时间排序，并且详情页时间胶囊、大相册卡片时间都正确回显。

### Blocked
- None.

## Closed Regression Watchlist
1. `详情页缩放与贴边手感` - 代码已经补上照片流方向的过渡，但多指手势、滚动锚点和选择模式仍应在下一次真机扫尾时复核。
2. `详情页视频自动播放与横滑多选` - 代码已补 `clipToBounds()`、同结构卡片和自动播放限制，后续需要真机确认视频不撑出网格、横滑多选命中稳定。
3. `详情页背景和双封面轮替` - 背景已收回照片流同源光晕体系；双封面替换队列已落地，后续真机可复核第三次设封面是否按旧位轮替。
4. `断网封面缓存` - 升级后先在线进入目录完成封面预热，再断网重进目录和详情，确认图片封面、视频封面都不再空白。
5. `小相册时间修改` - 新建和编辑各改一次时间，确认保存、排序、详情回显一致。

## Closeout Summary
- What shipped:
  - 创建页升级为小相册编排入口：媒体主区、一键清空、创建态媒体列表自动应用、所属头像勾选、所属大相册选择、时间修改、最终创建边界都已打通。
  - 编辑页升级为整理控制台：媒体预览与自动应用媒体列表、单删确认、双封面预览/轮替、所属管理、大相册 chips、时间修改、保存/删除紧凑操作区、短驻留保存反馈都已落地。
  - 详情页升级为记忆现场：标题替代固定“小相册详情”，标题旁展示显式所属头像，简介/时间/张数层级重做，背景改为照片流同源光晕，媒体网格对齐照片流的密度档位、视频自动播放和选择模式能力。
  - 所属语义收口：`participantUserIds` 成为小相册所属集合，create/update DTO、fake/real repository、server service 和文档都已对齐；操作者不再被自动 merge 进所属。
  - 离线封面链路加强：公共缩略图、视频 poster、照片流/查看器预取改为稳定磁盘 key；相册目录读缓存新增 `previewMediaByPostId`，联网预热后离线可恢复真实封面。
- What was validated:
  - Android `:app:compileDebugKotlin` 多轮通过，最近一次在接入新建/编辑时间选择后通过。
  - 服务端 `mvnw.cmd -q -DskipTests compile` 在 ownership 合同改动后通过。
  - 后端追补验证：小相册内容变更集成测试单测通过，服务端已锁住“创建/更新空所属必须失败”的回归。
  - 代码层已确认 create / edit / detail / fake / real / server DTO 主链路一致。
- What remains risky:
  - 详情页双指缩放、横滑多选、视频自动播放和断网封面回显仍依赖真机手势与缓存状态验证。
  - 离线封面修复需要升级后先联网进入目录完成一次封面预热，旧读缓存本身没有 `previewMediaByPostId` 时仍只能显示纯色。
  - 小相册时间修改已编译通过，但仍需真机确认排序、详情页时间胶囊和大相册卡片时间回显。
- What was intentionally deferred:
  - 不扩展到多大相册归属。
  - 不把 ownership 头像强制铺到所有未触达的小相册卡片变体，后续遇到相邻卡片布局再统一补齐。
  - 不重做查看器协议，只复用现有 `ViewerTimeEditorSheet` 与照片流密度系统。

## Carry-forward Notes
- Fact future modules must remember: ownership display now depends on explicit participant ids, not contributor-label heuristics.
- Fact future modules must remember: `CachedAlbumDirectory.previewMediaByPostId` is required for real image covers in offline large-album directory views; first online prewarm after upgrade is expected.
- Fact future modules must remember: small-album create/edit time uses `displayTimeMillis` and `ViewerTimeEditorSheet`; changing post time should recheck large-album sort order and detail time chips.
- Adjacent module to revisit later: small-album list card layouts if avatar exposure expands further.
- Adjacent module to revisit later: photo feed density/selection changes can regress small-album detail because the detail grid intentionally mirrors photo-feed behavior.
- Adjacent module to revisit later: trash delete/restore should recheck small-album detail empty-after-delete and cached cover recovery.

## Closeout Self-check
- Brief completeness: final scope, shipped behavior, validation history, remaining real-device watchpoints, and cross-module contracts are captured.
- Remaining risk clarity: unresolved items are explicitly listed as closed regression watchlist items rather than hidden blockers.
- Carry-forward quality: future-critical facts are short and specific, especially ownership semantics, offline cover prewarm, time editing, photo-feed coupling, and trash coupling.
