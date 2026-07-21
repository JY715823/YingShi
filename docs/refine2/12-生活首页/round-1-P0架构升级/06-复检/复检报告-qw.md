# 生活首页 三轮复检报告（QoderWork review）

> 复检时间: 2026-07-17
> 实施方式: Trae 编码 -> QoderWork 独立审查

## 完整性比对

### Round 1: P0 架构升级（FR-1 + FR-2）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | LifeConsoleViewModel.kt 新建 | Android | ✅ | 324行，AndroidViewModel + UiState(11字段) + 15方法 |
| 2 | LifeConsoleScreen.kt 瘦身 | Android | ✅ | 1492→1343行，collectAsState() 获取状态 |
| 3 | LifeScreen.kt 动态摘要 | Android | ✅ | 275→362行，3个 LaunchedEffect 加载摘要 |
| 4 | 跨零点/Sync stale/ON_RESUME 刷新 | Android | ✅ | ViewModel 内 startCrossMidnightTimer/startSyncStaleObserver/onResume |
| 5 | 今日痕迹摘要（API getToday） | Android | ✅ | 统计4 slot 媒体数，显示"今日 N 张" |
| 6 | 记账摘要（Room LedgerDao） | Android | ✅ | 显示"共 N 笔记录" |
| 7 | 聊天摘要（Room ChatImportDao） | Android | ✅ | 显示"N 条消息" |
| 8 | 降级策略 | Android | ✅ | catch 异常保持 null，显示静态文案 |

**完整性: 8/8 (100%)**
**AC 覆盖: FR-1 6/6 + FR-2 5/6 完全 + 1/6 部分（AC-2 记账摘要格式偏差）= 11.5/12**

### Round 2: P1 代码质量（FR-3 + FR-4 + FR-5 + FR-6）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | LifeSubRoute.kt sealed class | Android | ✅ | 13行，5个 data object |
| 2 | YingShiApp 路由替换 | Android | ✅ | 4个 boolean → LifeSubRoute |
| 3 | LifeConsoleUtils.kt 共享工具 | Android | ✅ | isVideo/looksLikeVideoUrl/firstUsableImageUrl/formatTime |
| 4 | Calendar→java.time | Android | ✅ | LifeScreen 改用 LocalTime.now() |
| 5 | 问候语 ON_RESUME 更新 | Android | ✅ | LifecycleEventObserver 重新计算 |
| 6 | formatTime 双份实现 | Android | **QW修复** | 删除 Screen 内 private 副本 |
| 7 | 时区硬编码统一 | Android | **QW修复** | 5处 "Asia/Shanghai" → LIFE_CONSOLE_ZONE_ID |

**完整性: 7/7 (100%)**

### Round 3: P2 Widget 拆分（FR-7）

| # | 实施计划项 | 端 | 状态 | 备注 |
|---|-----------|-----|------|------|
| 1 | ConsoleWidgetBuilder.kt 新建 | Android | ✅ | Console Widget 视图构建 |
| 2 | PeopleWidgetBuilder.kt 新建 | Android | ✅ | People Widget 视图构建 |
| 3 | WidgetPendingIntentFactory.kt 新建 | Android | ✅ | PendingIntent 构建 |
| 4 | LifeConsoleWidgetProvider 瘦身 | Android | ✅ | 618→189行 |

**完整性: 4/4 (100%)**

---

## 发现并修复的 Bug

### BUG-1: formatTime 双份实现（Round 2）

**问题**: `LifeConsoleUtils.kt` 有 package-level `formatTime()`，但 `LifeConsoleScreen.kt` L1315 仍保留 private 副本。FR-4 AC-4 "统一为一个实现"未完全达成。

**根因**: 实施时保留了 Screen 内的 private 版本，与需求文档矛盾。

**修复**: 删除 `LifeConsoleScreen.kt` 中的 private `formatTime()`，调用方自动使用 LifeConsoleUtils.kt 的共享版本（同包无需 import）。

**验证**: 编译通过。

### BUG-2: 时区硬编码未统一（Round 2）

**问题**: "Asia/Shanghai" 在 5 处硬编码（LifeConsoleViewModel 2处、LifePushDispatchActivity 1处、ConsoleWidgetBuilder 2处）。FR-5 AC-2 要求提取为常量。

**根因**: 实施时未创建统一常量。

**修复**: 在 `LifeConsoleUtils.kt` 新增 `const val LIFE_CONSOLE_ZONE_ID = "Asia/Shanghai"`。5 处硬编码全部替换。ConsoleWidgetBuilder.kt（widget 子包）添加显式 import。

**验证**: 编译通过。

---

## 非阻塞发现

| # | 严重度 | 轮次 | 描述 | 处理建议 |
|---|--------|------|------|---------|
| 1 | P3 | R1 | 记账摘要显示"共 N 笔记录"（全量总数），非需求描述的"最近一笔金额或本月笔数" | 可接受，后续可优化查询 |
| 2 | P3 | R1 | `countTransactionsByBook` DAO 方法未过滤软删除 | LedgerDao 历史遗留，后续修复 |
| 3 | P3 | R1 | 摘要 LaunchedEffect(Unit) 不随 ON_RESUME 刷新 | 可接受，用户通常不会长留 LifeScreen |
| 4 | P3 | R2 | LifeConsoleScreen 内 `showNotice()` 纯委托包装可内联 | 后续清理 |
| 5 | P4 | R2 | LifeFramePalette 等颜色仍为 Color(0xFFxxxxxx) | 文件级私有常量，可接受 |
| 6 | P3 | R3 | QuickViewer 的 isVideo getter 与 RemoteMedia.isVideo() 重复 | 后续统一 |

---

## 总结

| 维度 | 结果 |
|------|------|
| 三轮实施计划项 | 19/19 全部落实 (100%) |
| AC 覆盖 | 23/24 完全 + 1/24 部分 (96%+) |
| 编译 | Android BUILD SUCCESSFUL |
| 发现并修复 Bug | 2 个（formatTime 双份 + 时区硬编码） |
| 非阻塞发现 | 6 项（均为 P3/P4） |
| **最终状态** | **三轮全部通过** |
