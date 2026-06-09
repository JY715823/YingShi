# 首页精修

> 首页本轮按“共享门厅 + 轻摘要 + 照片优先”收尾，作为部署前的正式版本，不再保留静态欢迎页和快捷入口占位。

- Module key: `home`
- Status: `implementing`
- Last updated: `2026-06-09`
- Primary surfaces: `android`
- Linked server brief: `none`

## Module Goal
- User value: 打开 App 后立刻看到更像成品的共同空间门厅，直接回到照片和账本，不再先看说明型首页。
- Business or product intent: 让首页成为轻量、高级、可进入真实内容的入口页，而不是功能导航占位页。
- Success criteria:
  - 首页不再出现欢迎文案、中间说明卡、底部三枚快捷入口卡
  - 顶部仅保留 `映世` 与通知铃铛
  - 主体变成照片优先门厅 + `最近照片` / `账本信号` 两条轻摘要
  - 首页支持缓存只读展示，不新增后端接口
  - 账本摘要支持“最近一笔 + 本月支出”并可直达记账

## Current State
- What exists today:
  - `HomeScreen.kt` 仍是静态欢迎页结构，使用通用波浪背景、说明卡和三枚快捷入口
  - 首页没有本地 summary 聚合层，也没有直达记账入口
  - 首页对缓存内容、通知未读和本地账本没有正式接法
- Known constraints:
  - 本轮不新增后端接口
  - 首页不能抢照片页风头，要服从既有浅色壳层与颜色系统
  - 账本摘要只能复用现有本地账本能力，不能扩成统计看板
- Relevant code or docs:
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/home/HomeScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/app/YingShiApp.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/data/cache/AppReadCacheStore.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/ledger/data/*`
  - `/mnt/e/Study/App/YingShi/PRODUCT.md`
  - `/mnt/e/Study/App/YingShi/docs/design/ui/03-color-system-v1.1.md`

## Your Current Ideas
- 去掉快捷入口和中间说明文字
- 背景和整体做得更好看、更绚烂，但仍然克制
- 首页这轮以照片优先为核心

## Codex Recommendations
### Recommended to finish in this module
- 重做首页为“共享门厅”:
  - Why it is worth considering: 首页将从说明页变成真实内容入口，更像最终上线版本
  - Impact on usability, robustness, or smoothness: 首屏理解成本更低，进入照片与账本更直接
- 增加本地 Home summary 聚合层:
  - Why it is worth considering: 把当前用户、照片缓存、通知未读和账本信号统一成一个轻状态模型
  - Impact on usability, robustness, or smoothness: 缓存只读时首页依旧成立，状态也更统一
- 给读缓存层补变更触发:
  - Why it is worth considering: 通知中心返回后首页角标和摘要需要跟着缓存刷新，而不是切页后才更新
  - Impact on usability, robustness, or smoothness: 首页状态更及时，不会出现明显滞后

### Defer only with explicit acceptance
- 首页跨到聊天、今日痕迹、复杂生活汇总:
  - Why it would otherwise belong in this module: 首页作为门厅有扩展空间
  - Why it might still be deferred: 这轮已明确不扩到聊天和生活重摘要，避免首页先跨模块膨胀

## Key Questions
- [x] 首页固定走 `共享门厅 + 轻摘要 + 照片优先 + 克制流光`
- [x] `账本信号` 固定为 `最近一笔 + 本月支出`
- [x] 本轮不新增后端接口，不扩到聊天 / 今日痕迹 / 重生活摘要

## Scope Boundaries
### In scope
- 重做首页布局、视觉和轻动效
- 新增首页本地 `HomeUiState / HomeSummary` 聚合层
- 接入照片缓存、通知缓存、当前用户快照和本地账本摘要
- 首页新增直达记账动作

### Out of scope
- 新增首页后端接口
- 首页接入聊天、今日痕迹、复杂生活摘要
- 改写照片、通知、账本各自模块的主流程

### Non-negotiables
- 真实照片优先
- 浅色壳层、珍珠玉蓝系统优先
- 首页不可回退为开发态说明页

### Failure and fallback expectations
- Failure states to support:
  - 无照片缓存
  - 无账本数据
  - 缓存只读 / 离线
- Rollback or fallback behavior:
  - 首页继续显示高级空态和本地摘要，不额外抛大错误

## Related Modules
- Module: `photos`
  - Relationship: 首页门厅和 `最近照片` 摘要直接跳照片页，照片素材取自照片缓存
  - Recheck before ship: 点击落点、缩略图显示、无缓存时空态是否自然
- Module: `ledger`
  - Relationship: 首页 `账本信号` 直达记账，并读取本地账本摘要
  - Recheck before ship: 默认账本选择、最近一笔与月支出口径、直达路由不打断原流程
- Module: `notifications`
  - Relationship: 顶部铃铛角标来自通知缓存
  - Recheck before ship: 通知中心读/未读变化后首页角标是否同步
- Module: `shell_and_baseEquipment`
  - Relationship: 首页继续复用全局缓存只读语义
  - Recheck before ship: 首页不能破坏只读状态和恢复逻辑

## Frontend and Backend Contracts
### Client state and entry points
- `HomeScreen`
- 首页本地 `HomeUiState / HomeSummary`
- `AppReadCacheStore`
- `AuthSessionManager`
- `OfflineAccessManager`
- `LedgerRepository + LedgerPreferencesStore`
- `YingShiApp` 首页路由接线

### Server endpoints and payloads
- 不新增接口

### Shared rules
- 只读状态沿用全局语义
- 当前用户身份沿用 `currentUserSnapshot`
- 最近照片和通知只读缓存按当前用户维度读取
- 账本信号以默认账本为准

## UI and Visual Details
- Layout or information hierarchy: 顶部品牌与铃铛，主体为照片门厅，其下是 `最近照片` 与 `账本信号`
- Components and states: 门厅拼贴、轻摘要卡、未读角标、空态占位、只读轻提示
- Motion or transitions: 只做轻 reveal 和轻 glow，不做持续装饰动画
- Copy notes: 去掉解释型首页文案，只保留轻标签和状态型文案

## Interaction Feedback
- Loading: 首页不做重 loading 展示，优先直接读本地摘要
- Empty: 无照片 / 无账本时用轻空态，但结构仍成立
- Error: 首页不额外放大错误提示
- Success: 通知角标、最近照片、账本信号能自然更新
- Permission denial: 本轮无新增权限交互
- Offline or retry: 继续支持缓存只读，不打断首页

## Deployment Readiness
- Release-critical expectations:
  - 首页视觉达到成品态
  - 首页状态不再像静态占位页
  - 首页跳转和摘要信息稳定可用
- Anything that must be true before moving to the next module:
  - 首页数据结构、视觉和点击落点稳定
- Acceptable defers, if any:
  - 不扩到聊天 / 今日痕迹 / 复杂生活摘要

## Hidden Impact Checklist
- Notifications: 首页铃铛角标与通知中心状态一致
- Auth: 当前用户与共同空间文案从当前登录态读取
- Upload: 照片摘要依赖照片缓存，上传后返回首页需要能刷新
- Comments: 本轮不新增评论摘要
- Viewer: 首页只显示缩略图，不改变 Viewer 契约
- Settings: reduced motion 仍然生效
- Analytics or logging: 本轮无新增
- Cache or offline: 首页依赖照片/通知缓存与只读状态
- Permissions: 无新增权限
- Copy and empty states: 轻文案、轻空态，不回到说明页

## Plan Self-check
- Recommendation quality: 已把首页从静态壳升级到共享门厅，并补足了缓存刷新和直达账本这两个部署前真正有价值的点。
- Scope pressure test: 范围聚焦在首页与轻耦合壳层，不扩张到聊天和生活重摘要。
- Contract and dependency pressure test: 只复用现有缓存、通知、账本与路由能力，不新增后端契约。
- UX state pressure test: 覆盖正常、空态、缓存只读和通知未读变化等关键状态。
- Deployment readiness pressure test: 如果实现完成并通过编译与设备验证，这一轮首页即可进入冻结态。
- Risks to watch in implement: 通知角标回流刷新、账本本地摘要初始化、首页视觉在小屏上的密度控制。

## Implementation Notes
### Client
- `HomeScreen` 已重做为照片优先的共享门厅：
  - 顶部只保留 `映世` 与通知铃铛
  - 删除旧欢迎文案、中间说明卡和三枚快捷入口
  - 主体改为照片拼贴门厅，点击直达照片页
  - 下方改为 `最近照片` 与 `账本信号` 两条轻摘要
- 新增 `HomeSummary.kt`：
  - 增加 `HomeUiState / HomeRecentPhotosSummary / HomeLedgerSummary`
  - 首页内部聚合当前用户快照、照片缓存、通知缓存和本地账本摘要
  - 账本信号使用默认账本最近一笔和当月支出，不扩成统计看板
- `YingShiApp` 首页路由已新增 `onOpenLedger`，支持首页直达记账
- `AppReadCacheStore` 已增加轻量 `changeVersion`，让首页在通知缓存或照片缓存变化后能自刷新
- 首页继续复用全局缓存只读语义，只显示轻提示，不额外报错

### Server
- 无改动。

### Design
- 首页背景改为独立门厅变体：
  - 珍珠浅蓝大底
  - 多层径向流光和 aurora 式雾面洗色
  - 照片拼贴成为视觉主体，不再靠说明文案撑页面
- 摘要卡保持轻量，延续 `珍珠玉蓝 · 温暖记忆点缀版`
- 铃铛角标和只读提示只做小范围强调，不抢门厅主体

### Interaction Feedback
- 门厅主视觉与 `最近照片` 摘要直达照片页
- `账本信号` 直达记账
- 通知铃铛继续打开通知中心，并显示未读角标
- 无照片缓存、无账本记录、缓存只读三种状态都给了轻量回退

## Post-implement Self-check
- Validation run: 已执行 `gradlew.bat :app:compileDebugKotlin`，构建成功。
- New behavior sanity: 首页结构、首页直达记账、通知角标缓存回流、照片缓存摘要和只读轻提示都已在代码层打通。
- Contract sanity: 未新增后端接口；首页仅复用 `AppReadCacheStore`、`AuthSessionManager`、`OfflineAccessManager`、`LedgerRepository` 现有能力。
- Test plan quality: 已补齐本地验证、关联模块回归和真机检查清单。
- Known gaps: 当前环境只做了编译自检，视觉细节、门厅点击落点和真机小屏密度仍需设备验证。

## New Coupling Recheck
- Module: `notifications`
  - What was rechecked: 首页未读角标改为依赖通知缓存变更版本刷新。
  - Result: 通过 `AppReadCacheStore.changeVersion` 与首页本地聚合层接通。
- Module: `ledger`
  - What was rechecked: 首页新增直达记账动作，并读取默认账本本地摘要。
  - Result: 壳层路由已接通，不改动现有账本服务契约。
- Module: `shell_and_baseEquipment`
  - What was rechecked: 首页继续使用全局缓存只读状态，不自建额外错误提示体系。
  - Result: 只读语义保持一致。

## Implement Test Plan
### Locally validated
- Check: `cmd.exe /c "cd /d E:\\Study\\App\\YingShi && gradlew.bat :app:compileDebugKotlin"`
- Result: 通过。

### Linked-module regression checks
- Module: `photos`
  - What to recheck: 首页门厅和 `最近照片` 摘要点击是否正确落到照片页；缩略图是否正常显示。
  - Why it can regress: 首页开始直接复用照片缓存和媒体缩略图组件。
- Module: `ledger`
  - What to recheck: 首页点 `账本信号` 是否直接进入记账首页，且不打断原有账本路由。
  - Why it can regress: 首页新增了新的记账入口。
- Module: `notifications`
  - What to recheck: 通知中心读/未读变化后，返回首页时铃铛角标是否同步变化。
  - Why it can regress: 首页新增依赖通知缓存变更版本刷新。

### Real-device checks for the user
- Scenario: 正常态首页
  - Steps: 打开 App 进入首页，确认有照片缓存、有账本记录、有未读通知。
  - Expected result: 首页显示门厅拼贴、两条轻摘要和未读角标，不再出现旧欢迎卡与快捷入口。
- Scenario: 无照片缓存
  - Steps: 在新环境或清缓存后进入首页。
  - Expected result: 门厅保留高级空态，不会像坏掉的空页面。
- Scenario: 无账本记录
  - Steps: 在没有账本交易的情况下打开首页并点 `账本信号`。
  - Expected result: 首页显示轻空态提示，点击后直接进入记账。
- Scenario: 通知回流
  - Steps: 从首页进通知中心，把未读改为已读，再返回首页。
  - Expected result: 铃铛角标同步变化，不需要切页二次刷新。
- Scenario: 缓存只读
  - Steps: 断网进入缓存只读后返回首页。
  - Expected result: 首页仍显示轻摘要，只提示 `缓存只读`，不出现大报错。

### Still unverified
- Risk: 首页在不同真机尺寸上的拼贴密度和留白是否都足够稳定。
  - Why it remains open: 当前环境没有做真机截图验证。
  - Best next verification path: 在常用主力机和一台较小屏设备上做一次首页进入、返回、断网只读与通知回流联测。

## Real-device Issue Log
- None yet. Add entries using `references/device-qa-template.md`.

## Validation Snapshot
### Verified
- 首页实现已完成并通过 Kotlin 编译。
- 首页本地 summary 层、首页直达记账和缓存刷新触发已接通。

### Pending
- 真机视觉细节与交互联调。

### Blocked
- None.

## Closeout Summary
- What shipped:
- What remains risky:
- What was intentionally deferred:

## Carry-forward Notes
- Fact future modules must remember: 首页本轮只聚合照片与账本，不扩聊天和今日痕迹。
- Adjacent module to revisit later: 若后续生活模块要做首页摘要，应基于当前 `HomeUiState` 扩展而不是重开一套首页结构。

## Closeout Self-check
- Brief completeness:
- Remaining risk clarity:
- Carry-forward quality:
