# 登录与会话

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `login_and_session`
- Status: `closed`
- Last updated: `2026-06-09`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 让双人账号登录更像正式产品，支持真实 QQ 邮箱验证码验证，会话失效时能保留缓存只读并给出清晰反馈。
- Business or product intent: 在部署前把登录入口、认证链路、会话恢复和失效提示一次性收口到可交付状态。
- Success criteria:
  - 两个 QQ 账号可完成“账号密码 + 邮箱验证码”双重验证登录
  - 手动退出后，同一设备在短期有效期内可用账号密码直接重登，不再重复收验证码
  - 登录页视觉、交互和连接入口完成精修
  - token 刷新与已有会话恢复继续可用
  - token 丢失或失效时不再静默账号密码重登，而是保留缓存只读并提示重新登录

## Current State
- What exists today:
  - Android 已改成双步登录状态机，`LoginScreen` 先请求 challenge，再校验邮箱验证码完成建会话
  - Server 已切换为 `POST /api/auth/login/challenge`、`/challenge/resend`、`/login/verify`
  - 同机短期免验证码重登已追加为独立 remembered-login 链路，退出只撤销当前 session，不清本机短期信任
  - `BackendAutoLoginManager` 已不再重放账号密码，只负责“会话仍有效则恢复，否则明确要求重新登录”
  - `AuthSessionManager.clearTokensPreservingReadCache()` 已用于 token 失效场景，缓存只读与彻底登出已分离
- Known constraints:
  - Android 与 Server 工作树都已存在大量非本模块改动，本轮只触碰认证、登录、会话和直接联动文件
  - 真实 QQ 发信依赖本地/环境中的 QQ SMTP 授权码，当前仓库只提供代码和配置位
  - `currentUserSnapshot` 只保存轻量身份资料，不是业务总快照
- Relevant code or docs:
  - Android: `app/src/main/java/com/example/yingshi/feature/auth/LoginScreen.kt`
  - Android app shell: `app/src/main/java/com/example/yingshi/app/YingShiApp.kt`
  - Android auth repo/session: `app/src/main/java/com/example/yingshi/data/repository/RealRepositories.kt`, `app/src/main/java/com/example/yingshi/data/remote/auth/AuthSessionManager.kt`
  - Server auth: `src/main/java/com/yingshi/server/controller/AuthController.java`, `src/main/java/com/yingshi/server/service/auth/AuthService.java`
  - Contracts: `docs/contracts/auth-api.md`, `YingShi-Server/docs/contracts/auth-api.md`

## Your Current Ideas
- Idea 1: 登录页背景太丑，需要重做
- Idea 2: 两个账号改成 QQ 邮箱，加入邮箱验证码验证
- Idea 3: 回答 `currentUserSnapshot`、记账/聊天快照和云端媒体存储问题
- Open preference: 由 Codex 主导补充更多部署前应该一起收掉的登录与会话问题

## Codex Recommendations
### Recommended to finish in this module
- 把登录改成双步认证而不是只换账号
  - Why it is worth considering: 这是用户明确选定方向，且会直接决定客户端状态机和服务端契约
  - Impact on usability, robustness, or smoothness: 登录安全性、会话解释性、上线后可维护性都会提升
- 把“token 失效但仍有缓存内容”的路径做成正式只读模式
  - Why it is worth considering: 这是本轮会话模块和前一轮壳层自愈的关键衔接点
  - Impact on usability, robustness, or smoothness: 避免直接把用户打回错误页或偷偷静默重登
- 重做登录页视觉并弱化服务地址入口
  - Why it is worth considering: 当前登录页仍有明显调试页气质，且波浪线条不符合最终质感
  - Impact on usability, robustness, or smoothness: 首屏观感、信息层级和连接设置可理解性都会更好

### Defer only with explicit acceptance
- 注册、密码找回、第三方登录
  - Why it would otherwise belong in this module: 都属于认证体系完整度的一部分
  - Why it might still be deferred: 用户明确只做双账号收尾，不引入新用户体系
- 聊天导入附件云端化
  - Why it would otherwise belong in this module: 用户问到了聊天/媒体是否真正在云端
  - Why it might still be deferred: 当前模块只回答现状，不重做聊天资源模型

## Key Questions
- [x] 登录方式是否改为账号密码 + QQ 邮箱验证码
- [x] 部署范围是否只按本地开发部署处理
- [x] 服务地址入口是否弱化保留

## Scope Boundaries
### In scope
- Android/Server 双步登录链路
- QQ 邮箱验证码发送、重发、验证、频率限制
- 登录页视觉与交互重做
- 会话失效、缓存只读、会话恢复文案与行为收口
- 种子账号、契约文档、核心联调脚本与测试更新

### Out of scope
- 注册
- 找回密码
- 第三方登录
- 记账/聊天快照结构重构
- 聊天附件云端化

### Non-negotiables
- 两个账号固定为 `1085060329@qq.com` 和 `2926315047@qq.com`
- 本轮密码临时统一为 `123456`
- 登录必须使用真实 QQ 发信，不做纯模拟验证码主流程
- 服务地址入口保留，但不再占主视觉

### Failure and fallback expectations
- Failure states to support:
  - 账号密码错误
  - 验证码错误、过期、挑战失效、重发过快、超出频率限制
  - SMTP 未配置或发信失败
  - 发码前/发码中/验码前/验码中断网
  - token 过期、session 失效、base URL 变更
- Rollback or fallback behavior:
  - 有缓存内容时保留缓存只读，不静默重登
  - 有 refresh token 时继续走现有 refresh 恢复链路
  - 手动退出和修改服务地址仍清空会话并回到登录入口

## Related Modules
- Module: `shell_and_baseEquipment`
  - Relationship: 共享离线只读、自愈、连接状态与缓存提示策略
  - Recheck before ship: token 丢失后的只读保留、网络恢复后的 UI 提示和刷新行为
- Module: `me/profile`
  - Relationship: 依赖当前用户快照、会话恢复和个人资料刷新
  - Recheck before ship: 缓存资料显示与重新登录提示
- Module: `photos/viewer/trash/notifications`
  - Relationship: 多处真实页面在 token 缺失时仍会尝试静默补登录
  - Recheck before ship: 缺 session 时是否改成清晰失败或只读回退

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories:
  - `LoginScreen`
  - `YingShiApp`
  - `AuthRepository`, `AuthApi`, `RealAuthRepository`, `FakeAuthRepositoryShell`
  - `BackendAutoLoginManager`, `AuthSessionManager`, `AuthRefreshCoordinator`

### Server endpoints and payloads
- Controllers, services, DTOs, contracts:
  - `POST /api/auth/login/challenge`
  - `POST /api/auth/login/challenge/resend`
  - `POST /api/auth/login/verify`
  - Existing `refresh-token`, `me`, `logout`, `me/profile`, `me/avatar`
  - New login-code persistence + sender infrastructure

### Shared rules
- Auth, permissions, identity, ordering, time, copy:
  - 验证码 6 位
  - 有效期 5 分钟
  - 重发冷却 60 秒
  - 单账号 30 分钟最多发 5 次
  - 单挑战最多输错 5 次
  - token 刷新逻辑保持不变
  - token 丢失后不能再静默补账号密码登录

## UI and Visual Details
- Layout or information hierarchy: 登录页改为正式首屏层级，账号密码与验证码两阶段清晰分离
- Components and states: 快速填充账号、密码显隐、验证码输入、重发倒计时、连接设置底部弹层
- Motion or transitions: 保持柔和过渡，但登录专用背景去掉当前波浪线条
- Copy notes: 用“重新登录”“缓存只读”“连接设置”替代开发态或 demo 态表述

## Interaction Feedback
- Loading: 发码中、验码中、等待网络恢复、会话校验中
- Empty: 不适用
- Error: 账号密码错误、验证码错误/过期/失效、服务不可用、发信失败
- Success: 发码成功、登录成功、地址已保存
- Permission denial: 不适用
- Offline or retry: 断网时保持等待，恢复后自动重试当前登录动作

## Deployment Readiness
- Release-critical expectations:
  - 真正能跑通 QQ 验证码登录
  - 会话失效不再直接把缓存一起清掉
  - 契约、测试和文档与双步登录一致
- Anything that must be true before moving to the next module:
  - Android 和 Server 编译通过
  - 关键认证测试通过
  - 登录页和会话恢复路径可在真机上验证
- Acceptable defers, if any:
  - 注册/找回密码/第三方登录
  - 聊天附件云端化

## Hidden Impact Checklist
- Notifications: 依赖 session 恢复和 cached current user，需复查 token 丢失后的只读回退。
- Auth: 直接主模块，需重做契约、状态机、缓存保留和错误码。
- Upload: 头像上传仍走现有 bearer auth，需确保双步登录后的 token 不影响现有链路。
- Comments: 依赖受保护接口和 token refresh，需确保 session 失效时不出现静默补登录假设。
- Viewer: 原图加载和详情页依赖会话与缓存，需复查 token 丢失后的只读提示。
- Settings: 服务地址入口位置与文案会变化，切地址仍要清会话。
- Analytics or logging: 无单独埋点体系，但验证码发信/验证失败需保留服务端日志。
- Cache or offline: 本轮重点之一，需把会话清理与只读缓存分离。
- Permissions: 无额外系统权限变化。
- Copy and empty states: 登录、会话失效、等待网络恢复、重新登录等文案都要更新。

## Plan Self-check
- Recommendation quality: 已把双步认证、缓存只读保留、登录页重做三项作为主建议，而不只是扩写用户原始意见。
- Scope pressure test: 范围大但集中在登录与会话主链路，未扩展到注册、找回密码或聊天资源重构。
- Contract and dependency pressure test: Android auth repo、Server auth contract、会话恢复与缓存自愈依赖都已识别。
- UX state pressure test: 已覆盖发码、验码、断网、会话失效、缓存只读、服务地址切换等关键状态。
- Deployment readiness pressure test: SMTP 配置、双步契约和缓存保留是最关键落地点。
- Risks to watch in implement: 静默补登录残留调用点、缓存被错误清空、真实邮件发送配置、登录 UI 状态机复杂度。

## Implementation Notes
### Client
- `AuthApi` / `AuthRepository` / `RealRepositories` 已切到 challenge / resend / verify 三段式契约，`verify` 成功后才落 token 与 `currentUserSnapshot`。
- `AuthSessionManager` 新增本机 `deviceId` 与按账号存储的 remembered-login token；手动退出会清 session 和受保护缓存，但保留短期同机重登资格，切换服务地址时会一并清掉。
- 本轮补修了 remembered-login 没有命中的关键原因：登录页现在会记住上一次成功登录的账号，退出后回到该账号，再优先尝试本机短期直登，不再总是回退到默认账号 A。
- `LoginScreen` 在 challenge 前新增 remembered-login 直登分支：本机短期信任有效时，输入账号密码可直接建会话；失效或过期时会自动回退到发验证码，不需要再多点一次。
- `LoginScreen` 已继续收口验证码阶段 UI：去掉“完成登录”下方的掩码邮箱简写，并把发码成功提示改成更克制的通用文案。
- `YingShiApp`、`BackendAutoLoginManager`、诊断页等相关文案已从“重新完成邮箱验证”收口为更准确的“重新登录”，避免和 remembered-login 新行为冲突。
- 这轮再加了一层登录态硬化：认证相关 SharedPreferences 改成同步持久化，避免“刚签发 remembered token 就立即退出/杀进程”时还没稳妥落盘。
- `YingShiApp` 现在会按 `authSessionVersion + lastSignedInAccount` 强制重建登录页，确保退出后重新进入登录态时一定读取最新账号和 remembered-login 凭据，而不是复用旧的 `rememberSaveable` 表单状态。

### Server
- 新增邮件与验证码配置：`AuthMailProperties`、`AuthLoginCodeProperties`、SMTP sender、验证码 challenge 持久化实体/仓库与 Flyway 表。
- 新增 remembered-login 配置、实体、仓库和迁移：`app.auth.remembered-login.*`、`auth_remembered_logins`、`AuthRememberedLoginService`。
- `AuthService` / `AuthController` 现已同时支持 `challenge / verify` 与 `login/remembered`：verify 成功会签发本机短期重登 token，remembered-login 会校验账号密码 + 本机 token 后直接创建新 session。
- dev 种子账号已改成两个 QQ 邮箱，密码统一为 `123456`；自动化测试已通过捕获型 sender 覆盖 challenge、verify、refresh、logout 与现有业务链路。
- 本地 `docker` 容器这轮已热替换到新版 jar；过程中修掉了验证码表迁移号与既有 `V11` 撞车的问题，最终 `POST /api/auth/login/challenge` 已从 `404` 变成真实业务响应。
- 当前可运行容器状态已 `docker commit` 回本地 `yingshi-server-server:latest`，后续只要不强制重建镜像，就不会因为重建容器而掉回旧版 jar。

### Design
- 登录页背景已再次精修为更成品级的雾面渐变、极光层、玻璃感轨迹与细碎高光，不再只是几条发光线的半成品观感。
- 服务地址入口保留但下沉为次级连接设置，不再占用主视觉。

### Interaction Feedback
- 发码中、验码中、remembered-login 直登中、等待网络恢复、验证码重发冷却、验证码过期/失效、发信失败、会话失效转缓存只读等状态都已有明确反馈文案。
- remembered-login 失效或过期时，前端会立即清掉本机短期凭据并自动回退到发验证码，而不是停在失败态等用户重试。
- 连接设置保存地址后会清掉旧会话与 remembered-login 凭据，避免沿用旧 server 的假会话。

## Post-implement Self-check
- Validation run:
  - Android: `gradlew.bat :app:compileDebugKotlin` 通过。
  - Server: `mvnw.cmd -Dtest=YingshiServerApplicationTests test` 通过，`28` 个测试全绿，包含 remembered-login 重登回归。
- New behavior sanity:
  - 双步登录继续保持原契约；退出后同机 remembered-login 会优先直登，失效时自动回退到 challenge，不会出现“还要再点一次登录”的断层。
  - 登录页新增受信任直登分支后，缓存只读、会话失效与服务地址切换语义仍保持为“只清 session，不偷偷重放凭据，不串环境”。
- Contract sanity:
  - Android DTO / repository / UI 状态机 与 Server controller / service / error code / remembered-login 配置项 已保持一致；`verify` 请求体、登录响应和契约文档都已同步新增 `deviceId` / remembered-login 字段。
- Test plan quality:
  - 已覆盖 Android 编译、Server 主链回归和 remembered-login 重登回归；剩下主要是你真机上的 QQ 发信、退出后同机直登和最终视觉验收。
- Known gaps:
  - 还未在你真机上实测“退出后同机几天内免验证码重登”的完整体感。
  - remembered-login 当前是本机安装级短期信任，不包含“手动撤销此设备”单独入口；如果后面需要显式设备管理，再单开模块做。

## New Coupling Recheck
- Module:
  - What was rechecked: `shell_and_baseEquipment` 的离线只读策略、`me/profile` 的缓存资料恢复，以及服务地址切换时的会话清理与 remembered-login 清理边界。
  - Result: token 丢失时仍只会进入缓存只读或重新登录提示；只有用户主动在登录页输入账号密码时，remembered-login 才会参与直登，不会回到旧的隐式自动补登语义。
- Module:
  - What was rechecked: close 阶段再次抽查了 `YingShiApp`、`MyScreen`、`NotificationCenterScreen`、`RealBackendUiSupport` 与 `BackendDebugConfig` 的只读回退、重新登录提示和切地址清会话语义。
  - Result: `Me` 仍显示“缓存只读”身份态，通知/照片相关真实页仍优先走 cached fallback 或 token-missing 提示，服务地址保存仍会清掉旧 session 与 remembered-login，没有发现和本模块收尾结论相冲突的残留逻辑。

## Implement Test Plan
### Locally validated
- Android auth/session 与登录页改动已通过 `compileDebugKotlin`。
- Server 认证主链、刷新、登出与 remembered-login 重登回归已通过 `YingshiServerApplicationTests`。

### Linked-module regression checks
- `Me`：会话失效后是否显示缓存资料并提示重新登录。
- `Photos / Albums / Trash / Notifications`：缺 token 时是否走缓存只读或清晰失败，而不是假装自动登录成功。
- `Avatar upload`：双步登录后的 bearer token 仍要能正常上传与读取头像。

### Real-device checks for the user
- 用 `1085060329@qq.com / 123456` 走一遍：发码、收 QQ 邮件、验码登录、进入首页、退出，再次输入账号密码确认是否直接登录成功且不再收验证码。
- 用 `2926315047@qq.com / 123456` 再走一遍完整流程，确认两个账号都能正常发码、建会话，以及各自 remembered-login 互不串号。
- 特别确认：退出后登录页预填的账号是不是刚刚那个账号；如果是，再点登录应该直接成功，不应该再走发验证码。
- 分别验证断网发生在发码前、发码中、验码前、验码中时，前端是否给出正确提示并在恢复网络后续跑。
- 改一次服务地址，确认旧会话和 remembered-login 都被清掉，回到重新登录状态。
- 人为让 token 失效后，确认 `Me / Photos / Albums / Trash / Notifications` 是否保留缓存只读并提示重新登录。
- 观察登录页新版背景、按钮下方动作区和发码成功提示，确认成品质感与信息克制程度符合你的预期。

### Still unverified
- remembered-login 的几天有效期体验还没经过你真机上的跨天验证。
- 真机上邮件到达速度、验证码冷却体验、remembered-login 直登速度和新版背景观感是否完全符合你的预期。

## Validation Snapshot
### Verified
- Android 与 Server 双步登录代码已接通并通过构建。
- Server 认证回归测试通过。
- Android 登录页已去掉验证码阶段的掩码邮箱简写，并补上同机短期免验证码重登分支。
- 登录页背景已完成第二轮成品级精修。
- 本地 Docker 服务已切到含验证码接口的新 jar，`/api/auth/login/challenge` 已可命中。
- Android 会话恢复单测通过。
- 登录页视觉、连接设置入口和会话失效只读语义已落到代码。
- 真机上登录验证码链路已可正常工作，用户已确认“现在已经可以了”。
- 运行中的 PostgreSQL / server 记录已确认同机免验证码链路真实命中过：`2026-06-09 12:06:50` 创建了新 `auth_session`，且前面没有新的 `auth_login_challenge`，说明这次重登走的是 remembered-login 而不是重新发码。
- 同机重登链路又补了两层前端硬化：关键 auth 持久化同步落盘、登录页按最新会话版本强制重建。
- 登录页验证码阶段掩码邮箱简写已移除，背景也已按成品级方向做过第二轮精修。
- close 阶段复查了 `Me`、通知中心、照片真实页和服务地址切换语义，仍与“缓存只读 + 重新登录提示 + 不静默补登”的最终行为一致。

### Pending
- None. 当前没有阻止进入下一模块的未处理项。

### Blocked
- None currently.

## Next Fix Queue
- None for this module. 后续只保留 carry-forward 关注项。

## Closeout Summary
- What shipped:
  - 登录与会话主链已收口为正式双步认证：QQ 邮箱账号密码发起 challenge，邮箱验证码 verify 后建 session，并保留 refresh / me / logout / profile / avatar 既有契约。
  - 同机短期免验证码重登已落地到 Android + Server：verify 成功签发 remembered-login token，手动退出只撤销当前 session，不清本机短期信任；再次输入账号密码时会优先直登，失效再自动回退到发验证码。
  - 登录页视觉已重做为更完整的成品化首屏，连接设置下沉为次级入口；验证码阶段的多余辅助信息已清掉。
  - 会话失效语义已和壳层打通：不再静默重放账号密码，而是保留缓存只读、提示重新登录，并在恢复有效 session 后继续实时同步。
- What was validated:
  - Android `compileDebugKotlin` 通过；Server `YingshiServerApplicationTests` 通过，包含 remembered-login 回归。
  - 真机上 QQ 验证码登录已跑通，用户确认当前版本“差不多了”。
  - 运行中 backend / PostgreSQL 记录已证明 remembered-login 至少真实命中过一次，不是只停留在代码路径上。
  - close 阶段对 `Me`、通知中心、照片真实页与服务地址切换做了轻量耦合复查，仍与本模块的最终只读/重新登录策略一致。
- What remains risky:
  - remembered-login 的“跨天几天内体验”还没有做长期真机观察，但不构成当前部署前继续推进其他模块的阻塞。
  - QQ 发信速度和登录页审美满意度仍会受真实设备与环境影响，后续如再抠体验，可在别的模块顺手复看。
- What was intentionally deferred:
  - 注册、找回密码、第三方登录。
  - `currentUserSnapshot` 扩成业务总快照。
  - 记账 / 聊天 / 今日痕迹的数据模型重构，以及聊天附件云端化。

## Carry-forward Notes
- Fact future modules must remember:
  - `currentUserSnapshot` 依然只是轻量身份缓存，不代表记账、聊天或 life 数据已并入统一业务快照。
  - `BackendAutoLoginManager` 现已明确不再重放账号密码；后续任何模块都应遵守“无有效 token 时只进缓存只读或重新登录提示”的边界。
  - remembered-login 是“本机安装级短期信任”，会在切服务地址时被一并清掉，不等于完整的设备管理体系。
- Adjacent module to revisit later:
  - `shell_and_baseEquipment` 若再动网络自愈或只读策略，要顺手回归 auth 失效提示与缓存恢复路径。
  - `photos / viewer / trash / notifications` 若再改 token-missing 或 fallback 逻辑，要复查是否重新引入了静默补登录假设。
  - `life / ledger / chat` 进入各自收尾时，需要单独回答它们的后端快照与媒体存储边界，不要误以为本模块已经覆盖。

## Closeout Self-check
- Brief completeness: 最终 scope、认证契约、视觉收尾、会话失效策略、验证结果和 accepted defers 都已落到文档；`Pending` 已清零并改为显式残余风险。
- Remaining risk clarity: 跨天 remembered-login 体感、QQ 发信速度和审美主观项仍有不确定性，但都已明确为非阻塞 residual risk，没有隐性挂起项。
- Carry-forward quality: 后续模块最容易踩坑的三条边界已经留下：`currentUserSnapshot` 不是业务总快照、禁止恢复旧式静默补登、切地址会清 remembered-login。
