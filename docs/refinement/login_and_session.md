# 登录与会话

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `login_and_session`
- Status: `implementing`
- Last updated: `2026-06-08`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 让双人账号登录更像正式产品，支持真实 QQ 邮箱验证码验证，会话失效时能保留缓存只读并给出清晰反馈。
- Business or product intent: 在部署前把登录入口、认证链路、会话恢复和失效提示一次性收口到可交付状态。
- Success criteria:
  - 两个 QQ 账号可完成“账号密码 + 邮箱验证码”双重验证登录
  - 登录页视觉、交互和连接入口完成精修
  - token 刷新与已有会话恢复继续可用
  - token 丢失或失效时不再静默账号密码重登，而是保留缓存只读并提示重新验证

## Current State
- What exists today:
  - Android 仍是单步账号密码登录，`LoginScreen` 直接调用 `AuthRepository.login()`
  - Server 仍是 `POST /api/auth/login` 直接签发 access/refresh token
  - `BackendAutoLoginManager` 和多处真实页面 ViewModel 仍会在 token 缺失时尝试静默补登录
  - `AuthSessionManager.clearTokens()` 会清掉当前用户快照和受保护读缓存，不符合“会话失效后保留缓存只读”的目标
- Known constraints:
  - Android 与 Server 工作树都已存在大量非本模块改动，本轮只触碰认证、登录、会话和直接联动文件
  - Server 当前没有 SMTP / 邮箱验证码基础设施
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
  - Recheck before ship: 缓存资料显示与重新验证提示
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
- Copy notes: 用“重新验证”“缓存只读”“连接设置”替代开发态或 demo 态表述

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
- Copy and empty states: 登录、会话失效、等待网络恢复、重新验证等文案都要更新。

## Plan Self-check
- Recommendation quality: 已把双步认证、缓存只读保留、登录页重做三项作为主建议，而不只是扩写用户原始意见。
- Scope pressure test: 范围大但集中在登录与会话主链路，未扩展到注册、找回密码或聊天资源重构。
- Contract and dependency pressure test: Android auth repo、Server auth contract、会话恢复与缓存自愈依赖都已识别。
- UX state pressure test: 已覆盖发码、验码、断网、会话失效、缓存只读、服务地址切换等关键状态。
- Deployment readiness pressure test: SMTP 配置、双步契约和缓存保留是最关键落地点。
- Risks to watch in implement: 静默补登录残留调用点、缓存被错误清空、真实邮件发送配置、登录 UI 状态机复杂度。

## Implementation Notes
### Client
- Pending

### Server
- Pending

### Design
- Pending

### Interaction Feedback
- Pending

## Post-implement Self-check
- Validation run:
- New behavior sanity:
- Contract sanity:
- Test plan quality:
- Known gaps:

## New Coupling Recheck
- Module:
  - What was rechecked:
  - Result:

## Implement Test Plan
### Locally validated
- Pending

### Linked-module regression checks
- Pending

### Real-device checks for the user
- Pending

### Still unverified
- Pending

## Real-device Issue Log
- None yet. Add entries using `references/device-qa-template.md`.

## Validation Snapshot
### Verified
- None yet.

### Pending
- Dual-step login implementation
- Session-expired cached read-only fallback
- QQ SMTP configuration on real environment

### Blocked
- None currently.

## Closeout Summary
- What shipped:
- What remains risky:
- What was intentionally deferred:

## Carry-forward Notes
- Fact future modules must remember:
- Adjacent module to revisit later:

## Closeout Self-check
- Brief completeness:
- Remaining risk clarity:
- Carry-forward quality:
