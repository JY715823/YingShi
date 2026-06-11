# 照片主入口精修

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `photos_root`
- Status: `closed`
- Last updated: `2026-06-10`
- Primary surfaces: `android`
- Linked server brief: `none`

## Module Goal
- User value: 进入照片模块后，第一眼就感到这是成品级入口，不再是普通 tab + 普通工具按钮的开发态壳层。
- Business or product intent: 在部署前把照片模块的根入口壳层、顶部分栏和根层状态面统一收口成稳定、精致、可持续迭代的正式外观。
- Success criteria:
  - `照片 / 相册 / 回收站` 不再是蓝色胶囊 tab，而是与背景融合的标题式导航
  - 右上角保留 `系统 + 传输`，但整体质感明显高于当前普通圆按钮
  - 照片主入口拥有独立背景气质，不再直接沿用普通浅雾壳
  - 根层管理的选择态、notice、底部动作栏和确认弹窗视觉更统一
  - 不新增后端接口，不改三张二级页正文主流程

## Current State
- What exists today:
  - `PhotosRootScreen.kt` 已统一承接 `照片 / 相册 / 回收站` 三个二级页、删除/恢复/加帖/新建大相册等根层行为
  - 顶栏当前是左侧蓝色胶囊式 tab，右侧两个普通圆按钮 `系统媒体 + 传输中心`
  - 根层已有 notice host、选择态栏、删除确认和新建大相册对话框
- Known constraints:
  - 这轮是部署前精修，不扩到三张二级页正文重做
  - 用户已明确保留 `系统 + 传输`，不把铃铛接回这个入口
  - 需要保持现有选择态、分页、系统媒体入口和传输中心入口行为不变
- Relevant code or docs:
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PhotosRootScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/app/YingShiApp.kt`
  - `/mnt/e/Study/App/YingShi/docs/design/ui-design-v2.md`
  - `/mnt/e/Study/App/YingShi/docs/design/light-color-system-v1.md`

## Your Current Ideas
- 顶部分栏和入口按钮要更高级，尤其三个标题不要再被蓝色圈住
- 标题希望融进背景里，能有字体颜色变化、阴影、浮雕感
- 其他由 Codex 主导补充成品级精修建议

## Codex Recommendations
### Recommended to finish in this module
- 把三标题改成“浮雕字 + 雾轨”的章节式导航
  - Why it is worth considering: 这是用户最明确的不满点，也是照片模块入口气质最核心的识别问题
  - Impact on usability, robustness, or smoothness: 当前页辨识更强，照片模块会更像成品而不是普通分段控件
- 把 `系统 + 传输` 做成一组有层级的一体工具组
  - Why it is worth considering: 现有两个独立圆按钮功能是对的，但视觉上太普通
  - Impact on usability, robustness, or smoothness: 工具入口更稳定，也更容易表达传输状态优先级
- 给照片主入口单独做背景和状态面语言
  - Why it is worth considering: 当前壳层背景过于通用，无法托起标题和工具入口的高级感
  - Impact on usability, robustness, or smoothness: 顶栏、notice、选择态和弹窗会更像同一套正式系统

### Defer only with explicit acceptance
- 三张二级页正文首屏重做
  - Why it would otherwise belong in this module: 会让整体更一体化
  - Why it might still be deferred: 这轮已明确只收根入口壳层，避免把范围扩散到照片流、相册页和回收站正文

## Key Questions
- [x] 右上角最终保留 `系统 + 传输`
- [x] 三标题改成 `浮雕字 + 雾轨`
- [x] 这轮范围锁定在 `顶栏 + 根层状态`

## Scope Boundaries
### In scope
- 照片主入口独立背景变体
- `照片 / 相册 / 回收站` 标题式导航重做
- `系统 + 传输` 工具组重做
- 根层选择态栏、底部动作栏、notice、确认弹窗和新建大相册弹窗的视觉统一

### Out of scope
- 照片页、相册页、回收站页正文重做
- Viewer、通知中心、系统媒体、传输中心独立页面重做
- 任何后端接口、DTO 或缓存契约调整

### Non-negotiables
- 顶栏仍然保持单行结构
- `系统 + 传输` 继续在三个二级页都可用
- 左侧三标题继续支持点击切换和左右滑切换
- 根层行为语义保持不变

### Failure and fallback expectations
- Failure states to support:
  - 传输中
  - 传输失败
  - 多选态
  - 删除确认
  - 新建大相册失败
- Rollback or fallback behavior:
  - 即使视觉重做，现有根层入口、notice 和选择态行为必须保持可用

## Related Modules
- Module: `photo_feed`
  - Relationship: 根层顶栏与选择态直接覆盖照片流分页与多选场景
  - Recheck before ship: 多选态切换、底部动作栏高度、左右滑切换是否受影响
- Module: `albums`
  - Relationship: 根层新建大相册弹窗和相册分页仍从这里发起
  - Recheck before ship: 新建大相册入口和分页切换是否正常
- Module: `trash`
  - Relationship: 根层回收站分页仍依赖同一顶栏与选择态切换
  - Recheck before ship: 回收站分页、多选退出和恢复入口是否被视觉层影响
- Module: `transfer_center`
  - Relationship: 右上角传输入口需要继续承接进行中与失败状态
  - Recheck before ship: 徽标、失败态和入口点击是否准确

## Frontend and Backend Contracts
### Client state and entry points
- `PhotosRootScreen`
- `YingShiApp` 中的照片根路由接线
- `LocalSystemMediaBridgeRepository.uploadTasks`
- `PhotoFeedSelectionState`
- `AlbumPageStateStore`

### Server endpoints and payloads
- 不新增接口

### Shared rules
- 三个二级页名称和分页顺序不变
- 多选态优先于二级页横滑
- 传输任务失败数和进行中数继续映射到根入口按钮

## UI and Visual Details
- Layout or information hierarchy: 顶部仍为单行，左侧章节式标题导航，右侧为一体工具组；内容区不改正文布局
- Components and states: 浮雕字标题、雾轨选中态、`系统` 薄胶囊、传输状态按钮、统一玻璃感 notice/动作栏/弹窗
- Motion or transitions: 继续使用轻 reveal 和轻状态切换，不新增长动画
- Copy notes: 顶栏只保留最必要标题与工具标签，不加解释文案

## Interaction Feedback
- Loading: 传输进行中时按钮显示数量或活跃态
- Empty: 根层不新增空态文案
- Error: 传输失败、删除失败、新建大相册失败继续给清晰 notice
- Success: 删除、加帖、新建成功仍通过根层 notice 反馈
- Permission denial: 本轮无新增权限态
- Offline or retry: 本轮不改根层离线策略

## Deployment Readiness
- Release-critical expectations:
  - 顶栏与背景达到成品级
  - 根层状态面不再显得割裂或普通
  - 现有行为零回退
- Anything that must be true before moving to the next module:
  - Android 编译通过
  - 顶栏切换、多选、传输入口和弹窗可正常运行
- Acceptable defers, if any:
  - 不重做三张二级页正文

## Hidden Impact Checklist
- Notifications: 本轮不接回铃铛，但不能误伤照片模块其他通知跳转逻辑
- Auth: 不改会话或鉴权逻辑
- Upload: 顶栏传输态继续映射上传任务状态
- Comments: 不涉及
- Viewer: 不改 Viewer 入口和沉浸式查看逻辑
- Settings: 不涉及
- Analytics or logging: 无新增
- Cache or offline: 不改现有缓存与离线语义
- Permissions: 无新增权限
- Copy and empty states: 顶栏与状态面文案更克制，但语义不缺失

## Plan Self-check
- Recommendation quality: 已把用户最明确的顶栏问题扩大成“顶栏 + 工具组 + 背景 + 状态面”一体收尾，而不是只换一个 tab 样式。
- Scope pressure test: 范围聚焦在照片主入口壳层，不扩到三张二级页正文，适合这轮部署前收尾。
- Contract and dependency pressure test: 只复用现有根层状态、分页与上传任务映射，不新增后端和共享契约。
- UX state pressure test: 重点覆盖顶栏切换、多选态、传输状态、删除确认与 notice 等根层状态。
- Risks to watch in implement: 顶栏小屏宽度、多选态下的新顶栏质感、传输按钮高低优先级和背景过强抢内容的问题。

## Implementation Notes
### Client
- `AppShellScaffold` 新增了 `bottomBarOverride`，`YingShiApp` 会在 `RootDestination.PHOTOS + PhotosTopDestination.PHOTOS + 多选激活 + 无覆盖层` 时，用照片多选底栏替换默认全局导航。
- `PhotosRootScreen.kt` 现在会向壳层发布 `PhotosRootSelectionUiState`，并接收 `PhotoSelectionShellAction`：
  - 假仓库的照片多选动作已改成由壳层底栏触发
  - 页内 `PhotoSelectionActionBarV2` 已移除
  - `PhotoFeedScreen.bottomOverlayPadding` 不再为照片多选额外留底部悬浮菜单空间
- `RealPhotoFeedPage.kt` 已同步接入同一套壳层协议：
  - 会把 `writeEnabled / isDeleting / selectedCount` 回传给照片根入口
  - 页内 `RealFeedSelectionBarV2` 和旧遗留删除条都已移除
  - 真实模式也不再为页内悬浮动作栏保留底部 padding
- `PhotosRootScreen.kt` 的多选顶栏已重组为统一表面：
  - 左侧 `取消` 改成内嵌文字动作
  - 中央 `已选 X 项` 直接落在同一块玻璃面上
  - 取消了之前中心白条和左右割裂的观感
- 背景系统已在收尾阶段统一：
  - 新增共享 `YingShiAuroraBackdrop`
  - `PhotosRootScreen` 改用 `PHOTOS` 变体
  - `PhotoFeedScreen` 去掉了遮住根层背景的纯色 `appBackground`
  - `照片 / 相册 / 回收站` 现在会共用同一套照片模块背景家族，而不是只有相册和回收站更像成品、照片正文却像另一层页面
- 照片主入口标题继续沿用独立背景变体，但 `照片 / 相册 / 回收站` 进一步去胶囊化：
  - 三标题的视觉间距继续收紧
  - 当前页标题加大、加重，并把辉光和浮雕层压到字本身
  - 非当前页标题更小、更浅、更退后
  - 选中态只保留无边界的散射光晕，不再形成可辨识的胶囊底座
- 2026-06-09 晚一轮补修继续收口了 3 个真实体验点：
  - 当前页标题重新回到左起阅读顺序，避免 `照片` 选中时视觉重心漂到中间
  - 当前页标题再强化一档，非当前页标题回调到更接近原来的可读性，不再弱到直接看不清
  - 照片多选删除弹窗去掉正文说明，只保留标题和操作按钮
  - App 壳层在照片多选时会优先拦截返回键，先退出多选，不再误触发 `再按一次退出 App`

### Server
- 无改动。

### Design
- 本轮把 verify 中最明显的“胶囊感”继续往后推，当前页标题现在是 `散射光晕 + 多层浮雕字`，而不是“胶囊亮了所以像被选中”。
- 多选态的层级关系也更成品化了：顶部上下文栏和底部壳层菜单现在处在同一视觉体系里，不再像页内临时贴了一层操作浮板。
- `系统 + 传输` 工具区维持上轮方向，不回退到普通圆按钮，也没有把视觉重点从标题区抢走。
- 最后一轮把照片模块背景统一到和登录页同一家族的 `Aurora` 体系里，但照片模块仍保持更冷静、更偏珍珠蓝的 `PHOTOS` 变体，不会和登录页抢同一个情绪强度。

### Interaction Feedback
- 只有 `照片` 分页进入媒体多选时，App 全局底栏才会被 `分享 / 新建 / 加入 / 删除` 接管；退出多选后默认底栏立即恢复。
- `回收站` 多选没有接入这套四按钮底栏，仍保持原有行为边界。
- 三标题仍支持点击切换与左右滑切换；多选态下继续锁住横滑，避免误切分页。
- 多选态按系统返回键时，优先退出多选，不进入根层双击退出流程。
- 传输按钮继续即时表达失败和进行中状态，不改原有入口行为。

## Post-implement Self-check
- Validation run: 已执行 `cmd.exe /c "cd /d E:\\Study\\App\\YingShi && gradlew.bat :app:compileDebugKotlin"`，构建通过。
- New behavior sanity: 照片分页多选现在确实由壳层底栏接管，假仓库和真实仓库两条链路都已去掉页内悬浮动作栏；回收站多选未被误接入；删除弹窗正文已移除；多选返回优先退选择态。
- Contract sanity: 新增的只有客户端壳层协议 `bottomBarOverride`、`PhotosRootSelectionUiState`、`PhotoSelectionShellAction`；无服务端改动，无上传/分页/Viewer DTO 漂移。
- Test plan quality: 已把本地编译、壳层底栏替换、真假仓库多选动作、回收站边界和标题视觉回归都写进本轮检查清单。
- Known gaps: 当前仍缺真机验证，尤其要看窄屏设备上三标题密度、从多选进入 `新建/加入` 后返回的底栏恢复，以及真实模式删除中状态的按钮禁用反馈。

## New Coupling Recheck
- Module: `app_shell`
  - What was rechecked: `AppShellScaffold` 默认底栏与覆盖底栏的切换条件、覆盖层打开时的隐藏逻辑。
  - Result: 只有照片分页多选才替换默认底栏；其他根页面与覆盖路由继续走原壳层行为。
- Module: `photo_feed`
  - What was rechecked: 假仓库 `PhotosRootScreen` 和真实仓库 `RealPhotoFeedPage` 是否都移除了页内动作栏，并把动作回调接到了壳层。
  - Result: 两条链路都已改成壳层接管；旧的页内悬浮选择条和额外底部 padding 都已去掉。
- Module: `transfer_center`
  - What was rechecked: 顶栏传输按钮仍使用 `LocalSystemMediaBridgeRepository.uploadTasks` 计算运行中数和失败态。
  - Result: 传输状态映射保持原语义，只改视觉表现。
- Module: `albums / trash`
  - What was rechecked: `相册` 切页、新建大相册入口，以及 `回收站` 多选退出是否仍然挂在根层且不被新底栏协议误伤。
  - Result: `相册` 仍走原根层入口；`回收站` 多选未接入四按钮底栏，边界保持正确。
- Module: `shared_surfaces / app shell variants`
  - What was rechecked: 照片根入口最后一轮接入共享 `YingShiAuroraBackdrop` 后，是否仍保留照片模块独立气质且不被通用浅雾背景盖回去。
  - Result: 照片模块已稳定落到共享背景家族的 `PHOTOS` 变体，且 `PhotoFeedScreen` 不再用纯色底遮挡根层背景。

## Implement Test Plan
### Locally validated
- Check: `cmd.exe /c "cd /d E:\\Study\\App\\YingShi && gradlew.bat :app:compileDebugKotlin"`
- Result: 通过。

### Linked-module regression checks
- Module: `app_shell`
  - What to recheck: 进入照片多选后默认底栏是否被完整替换，退出多选后是否立即恢复；首页/生活/我的是否完全不受影响。
  - Why it can regress: 这轮新增了壳层级 `bottomBarOverride`。
- Module: `photo_feed`
  - What to recheck: 进入照片分页后，真假仓库两种链路的多选顶栏、壳层底栏、删除确认、分享、新建、加入动作是否都正常。
  - Why it can regress: 这轮把动作入口从页内浮层改成了壳层接管。
- Module: `albums`
  - What to recheck: `相册` 分页切换和新建大相册弹窗是否仍自然、无遮挡。
  - Why it can regress: 顶栏高度、背景和对话框表面都改了。
- Module: `trash`
  - What to recheck: `回收站` 分页切换、回收站多选退出和恢复入口是否正常，且不会错误出现 `分享 / 新建 / 加入 / 删除` 四按钮底栏。
  - Why it can regress: 这轮新增了照片多选专属的壳层底栏协议。
- Module: `transfer_center`
  - What to recheck: 顶栏传输按钮在空闲、进行中、失败三态下的角标和点击跳转。
  - Why it can regress: 这轮把传输入口从普通圆按钮改成了状态化新样式。

### Real-device checks for the user
- Scenario: 顶栏成品感
  - Steps: 打开照片模块，依次看 `照片 / 相册 / 回收站` 三个标题与右上 `系统 + 传输`。
  - Expected result: 当前页标题明显更强、更亮、更大，另外两个标题更小更浅；视觉重点落在字本身，不再看到清晰胶囊边界。
- Scenario: 三分页切换
  - Steps: 依次点击 `照片 / 相册 / 回收站`，再左右滑动切换。
  - Expected result: 切换平滑、标题状态准确、不会出现半屏停留或错位。
- Scenario: 照片多选态
  - Steps: 在 `照片` 分页进入多选，观察顶部上下文栏和全局底栏，再执行分享、加入已有小相册、移入回收站。
  - Expected result: 页内悬浮菜单完全消失，底部区域直接变成 `分享 / 新建 / 加入 / 删除`，顶部和底部属于同一套视觉体系。
- Scenario: 多选返回路径
  - Steps: 在 `照片` 分页多选后点击 `新建` 或 `加入`，完成或返回，再观察底栏和选中状态。
  - Expected result: 覆盖层打开时默认底栏不会穿透；返回后多选态和底栏恢复逻辑正确，没有残留空白或错态。
- Scenario: 多选系统返回
  - Steps: 在 `照片` 分页进入多选，直接按系统返回键。
  - Expected result: 先退出多选并恢复默认底栏，不出现 `再按一次退出 App` 提示。
- Scenario: 传输状态按钮
  - Steps: 分别在无任务、有进行中任务、有失败任务时查看右上角传输按钮，再点击进入传输中心。
  - Expected result: 三态区分清楚，失败时更醒目，点击始终进入传输中心。
- Scenario: 回收站边界
  - Steps: 切到 `回收站`，进入多选，再观察底部区域。
  - Expected result: 不会出现照片分页那套四按钮底栏，回收站保持自己的原有多选行为边界。

### Still unverified
- Risk: 窄屏机型上三标题的密度、当前页强调度，以及多选态从 `新建/加入` 返回后的壳层恢复还没有真机确认。
  - Why it remains open: 当前只有编译自检，没有做 verify 阶段的下一轮设备联测。
  - Best next verification path: 用主力机和一台偏窄屏设备重点走 `照片多选 -> 分享/新建/加入/删除 -> 返回`、`切到回收站多选`、`打开传输中心再返回` 三条链路。
- Risk: `2026-06-10` 的共享背景家族接入已编译通过，但还缺单独一次“照片正文是否比相册/回收站更容易被背景抢戏”的真机目测。
  - Why it remains open: 这轮是视觉统一收尾，不涉及逻辑回归，但最后一次改动发生在用户停止继续微调之后。
  - Best next verification path: 真机依次看 `照片 / 相册 / 回收站` 三页首屏，确认照片页背景可见但不抢内容、标题可读性稳定。

## Real-device Issue Log
- None yet. Add entries using `references/device-qa-template.md`.

## Validation Snapshot
### Verified
- 计划范围、视觉方向和顶栏结构已确认。
- Android 编译通过。
- 不新增后端接口或共享契约。
- 照片多选底栏壳层接管、顶部多选栏统一表面、删除弹窗瘦身、返回优先退出多选等关键交互都已落在当前实现里。
- 照片模块三页现在共用同一套 `PHOTOS` 背景家族，照片正文不再被纯色底完全盖住。

### Pending
- None. 剩余项已转入收尾残余风险，不作为本模块继续阻塞项。

### Blocked
- None.

## Closeout Summary
- What shipped:
  - 照片主入口完成了“标题式导航 + 一体工具组 + 根层状态面 + 多选壳层底栏”的收尾，顶栏不再是蓝色胶囊 tab 的开发态观感。
  - `照片` 分页多选现在由 App 壳层底栏接管，动作固定为 `分享 / 新建 / 加入 / 删除`；`回收站` 多选仍保留原边界。
  - 多选顶部上下文栏、notice、删除确认和新建大相册弹窗的表面语言已统一。
  - 最后一轮把照片模块三页统一进共享 `YingShiAuroraBackdrop` 的 `PHOTOS` 变体，解决了照片页看起来不像和相册/回收站共用同一背景的问题。
- What remains risky:
  - 最新 `2026-06-10` 的共享背景接入只做了编译自检，缺单独一轮真机视觉确认。
  - 窄屏设备上三标题的密度、照片正文与背景的强弱关系，以及从多选进入覆盖层后返回的壳层恢复，仍建议在以后再碰照片相关模块时顺手复查。
- What was intentionally deferred:
  - 不重做 `照片 / 相册 / 回收站` 三张正文页面。
  - 不扩到 Viewer、通知中心、系统媒体、传输中心等独立页面的视觉重构。
  - 不新增任何服务端接口、缓存契约或上传链路改造。

## Carry-forward Notes
- Fact future modules must remember:
  - 照片模块的共享背景现在集中在 `ui/components/YingShiAuroraBackdrop.kt`，照片根入口使用 `PHOTOS` 变体；后续不要再给 `PhotoFeedScreen`、相册页或回收站页重新铺一层不透明 `appBackground`，否则会把这次统一背景又盖回去。
  - 照片多选依赖壳层级 `bottomBarOverride`；后面如果再动 `AppShellScaffold`、`YingShiApp` 路由覆盖层、`photo_feed` 多选或返回键逻辑，要一起复查这条接管链路。
- Adjacent module to revisit later:
  - `photo_feed`：复查窄屏标题可读性、背景可见度、选择态返回和壳层恢复。
  - `albums / trash`：复查分页切换、回收站多选边界和相册页是否继续保持同一背景语言。
  - `transfer_center`：复查右上状态按钮在空闲/进行中/失败三态下的视觉区分和点击跳转。
  - `home / life / me / shared_surfaces`：后续若继续做全局视觉统一，优先沿用 `YingShiAuroraBackdrop` 的变体体系，不要再回到每页各画一套独立背景。

## Closeout Self-check
- Brief completeness: 最终范围、已交付行为、底栏接管边界、标题视觉方向、统一背景补修和明确 defer 都已写入，`Verified / Pending / Blocked` 与当前实际状态一致。
- Remaining risk clarity: 最新视觉统一只做了编译校验、缺真机视觉复看这一点已明确保留，没有伪装成“已完全验证”。
- Carry-forward quality: 已把最容易在后续模块里被误伤的两条事实写清楚，即共享背景入口位置，以及照片多选依赖壳层底栏接管协议。
