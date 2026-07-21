# 通知中心 三轮复检报告（QoderWork review）

> 复检时间: 2026-07-16
> 实施方式: Trae 编码 -> QoderWork 独立审查

## 完整性比对

### Round 1: P0 稳定性修复（FR-1 + FR-8）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | PushPreferenceDefaults 新增 photos:system | Server | ✅ | L337 |
| 2 | PushPreferenceDefaults 新增 life:system | Server | ✅ | L341 |
| 3 | system 默认值为 false | Server | ✅ | 两处 enabled=false |
| 4 | onClick 未读仅标记已读不跳转 | Android | ✅ | 逻辑迁入 ViewModel |
| 5 | 本地路径持久化到 AppReadCacheStore | Android | ✅ | persistNotificationUiState() |
| 6 | API 成功路径写 AppReadCacheStore | Android | ✅ | writeNotification + persist |
| 7 | API 失败不强制 isRead=true | Android | ✅ | 仅设 errorMessage |
| 8 | onMediaClick 保持标记+跳转 | Android | ✅ | 乐观更新+markRead+navigate |
| 9 | markAllRead 持久化 | Android | ✅ | persistNotificationUiState |
| 10 | Server 编译通过 | Server | ✅ | |
| 11 | Android 编译通过 | Android | ✅ | |

**完整性: 11/11 (100%)**

### Round 2: P1 架构重构（FR-2 + FR-3 + FR-4 + FR-7）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | NotificationModels.kt 新建（7模型+7扩展函数） | Android | ✅ | 175行 |
| 2 | FakeNotificationRepository.kt 瘦身 | Android | ✅ | 543→366行 |
| 3 | NotificationCenterViewModel.kt 新建 | Android | ✅ | 638行，StateFlow+全部业务逻辑 |
| 4 | NotificationCenterScreen.kt 使用 ViewModel | Android | ✅ | 2080→572行 |
| 5 | NotificationCenterComponents.kt 新建 | Android | ✅ | 772行，19个Composable |
| 6 | NotificationCenterPresentation.kt 新建 | Android | ✅ | 461行，27个函数 |
| 7 | hasNotificationTargetAction 硬编码移除 | Android | ✅ | 改用 targetSummary 判断 |
| 8 | pushPostComment/MediaComment 解耦 FakeAlbumRepository | Android | ✅ | 改为 postTitle 参数 |
| 9 | YingShiApp.kt import 更新 | Android | ✅ | |
| 10 | 全部编译通过 | Android | ✅ | |

**完整性: 10/10 (100%)**

### Round 3: P2 功能增强（FR-5 + FR-6）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | Server 支持 cursor 分页参数 | Server | ✅ | NotificationController + NotificationService |
| 2 | 不传 cursor 时向后兼容 | Server | ✅ | 返回最新 N 条 |
| 3 | Android NotificationApi 新增 cursor 参数 | Android | ✅ | |
| 4 | ViewModel 管理分页状态 | Android | ✅ | hasMore + isLoadingMore |
| 5 | 滚动到底自动加载30条 | Android | ✅ | LazyColumn 检测 |
| 6 | 底部 loading 指示器 | Android | ✅ | |
| 7 | 全部加载完毕提示 | Android | ✅ | |
| 8 | Material3 PullRefresh 下拉刷新 | Android | ✅ | |
| 9 | 离线只读禁用下拉 | Android | ✅ | |
| 10 | 前后端 API 契约一致 | Both | ✅ | |

**完整性: 10/10 (100%)**

---

## AC 覆盖检查

| FR | AC总数 | 完全覆盖 | 部分覆盖 | 未覆盖 |
|----|--------|---------|---------|--------|
| FR-1 | 4 | 4 | 0 | 0 |
| FR-8 | 6 | 6 | 0 | 0 |
| FR-2 | 5 | 5 | 0 | 0 |
| FR-3 | 6 | 5 | 1 | 0 |
| FR-4 | 5 | 5 | 0 | 0 |
| FR-7 | 3 | 3 | 0 | 0 |
| FR-5 | 6 | 6 | 0 | 0 |
| FR-6 | 5 | 5 | 0 | 0 |
| **总计** | **40** | **39** | **1** | **0** |

FR-3 AC-2 部分覆盖: NotificationCenterComponents.kt 772行，略超目标上限600行（+29%），功能完整。

---

## 测试结果

| # | 测试名 | 端 | 类型 | 结果 |
|---|--------|-----|------|------|
| T-1 | Android compileDebugKotlin | Android | 编译 | PASS |
| T-2 | Server mvnw compile | Server | 编译 | PASS |

---

## 发现并修复的 Bug

### BUG-1: onClick API 路径缺少乐观更新（Round 1）

**问题**: 点击未读通知文案区域时，API 路径不会立即将 UI 更新为已读状态。用户点击后无视觉反馈，需等网络请求完成。对比 onMediaClick 有乐观更新。

**根因**: `ViewModel.markRead()` 的 API 路径在收到响应后才更新 UI state，缺少前置乐观更新。

**修复**: 在 `markRead()` 开头添加乐观更新（`isRead=true`），API Error 分支添加回滚（`isRead=false`）。

**验证**: 编译通过。

**涉及文件**: `NotificationCenterViewModel.kt` L386-435

### BUG-2: NotificationIconButton danger 分支使用 MaterialTheme.colorScheme（Round 2）

**问题**: `NotificationIconButton` 的 danger 分支直接使用 `MaterialTheme.colorScheme.errorContainer/error/onErrorContainer`（3处），违反设计约束"所有 Composable 必须使用 YingShiThemeTokens"。

**根因**: 实现时未注意到 token 系统已有 `destructiveContainer/destructive/onDestructiveContainer`。

**修复**: 替换为 `colors.destructiveContainer` / `colors.destructive` / `colors.onDestructiveContainer`。

**验证**: 编译通过，`MaterialTheme.colorScheme` 使用计数归零。

**涉及文件**: `NotificationCenterComponents.kt` L693/698/703

---

## 非阻塞发现

| # | 严重度 | 轮次 | 描述 | 处理建议 |
|---|--------|------|------|---------|
| 1 | P3 | R2 | NotificationCenterComponents.kt 772行，略超目标600行 | 功能完整，后续可拆 Preview 函数 |
| 2 | P3 | R2 | pushPostComment/MediaComment 默认 postTitle 为通用字符串 | 后续可在调用方传入实际标题 |
| 3 | P4 | R1 | 实施记录描述与实际策略不完全匹配（"回滚" vs "不乐观更新"） | 文档微调，不影响功能 |
| 4 | P4 | R3 | Round 3 修复的3个 Trae 自检 Bug 均已确认修复 | 无需额外处理 |

---

## 总结

| 维度 | 结果 |
|------|------|
| 三轮实施计划项 | 31/31 全部落实 (100%) |
| AC 覆盖 | 39/40 完全覆盖 + 1/40 部分覆盖 (97.5%+) |
| 编译 | Android + Server 双端通过 |
| 发现并修复 Bug | 2 个（1x P2 乐观更新 + 1x P3 token违规） |
| 非阻塞发现 | 4 项（均为 P3/P4，不影响功能） |
| **最终状态** | **三轮全部通过** |
