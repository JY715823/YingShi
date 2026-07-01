# today_trace 精修 Brief

> 状态: closed
> 模块: 今日痕迹（LifeConsoleScreen）
> 日期: 2026-06-29

## 模块目标

将今日痕迹页面从"功能正确但视觉朴素"提升到与其他精修页面一致的质感，同时增强用户对历史记录持久化的信任感。

## 调查结论（持久化 & 自动刷新）

- 后端数据正常持久化：person/meal → PostMediaEntity（月度相册），bowel → BowelEventEntity（JPA），均 PostgreSQL
- 午夜自动刷新已实现：`millisUntilNextLifeConsoleRefresh()` + LaunchedEffect
- ON_RESUME 也会刷新；历史排除今天是设计行为
- 用户感知"不能保存"的根因：前端缺乏视觉反馈 + 空状态简陋 + 无统计摘要

## 实施内容

改动文件：`LifeConsoleScreen.kt`（唯一）

1. **背景**：今日视图 + 历史页均启用 `YingShiMistBackground(showWaves=true, variant=LIFE)`
2. **卡片迁移**：LifeMediaFrame / BowelCard / 历史日卡片 / 大便日卡片全部 Surface → YingShiMistCard
3. **Accent gradient**：LifeMediaFrame 新增 `accentGradient` 参数，person=蓝色调 #4A7CBA，meal=暖色调 #C4874A，alpha 0.06→0.03→transparent
4. **空状态升级**：LifeConsoleTodayEmptyState（引导型文案按 category 区分）+ LifeConsoleHistoryEmptyState
5. **动画**：contentVisible state + yingShiRouteReveal（4 frame + BowelCard）
6. **统计摘要**：历史页顶部 "共 X 天记录"
7. **Token 对齐**：所有硬编码 dp → spacing/radius tokens

## 明确不做（scope 外）

- 不改数据模型 / API 契约 / UploadBridge / SyncVersionTracker / StaleBanner / Widget / 导航路由 / 乐观更新逻辑
- 不加新功能、不加新依赖

## 契约

- 无 API 变更；回调签名不变（onBack / onOpenLedgerAdd）；initialSlotKey / initialMediaId 不变

## Closeout Self-Check

- [x] 编译通过：修复了缺失的 `RemoteLifeConsoleBowelHistoryDay` import（implement 阶段遗漏，verify 阶段修复）
- [x] 组件 API 一致：MistBackground(2) / MistCard(4) / routeReveal(5) / ShellPage(1) 全匹配
- [x] 数据模型字段：8 个 data class 访问 + copy() 均与定义一致
- [x] 无逻辑变更：loadToday / loadHistory / 上传 / 删除 / 乐观更新未动
- [x] 无契约漂移：回调签名 / 参数 / 导航不变
- [x] 无新耦合：设计系统调用方式与 LifeScreen / MyScreen / CacheManagementScreen 一致

## 残留风险

- accent gradient alpha 0.06 在 AMOLED 屏可能太淡——设备 QA 时留意
- 历史统计摘要在大数据量下为 O(n) size 计算，实际场景可忽略

## Carry-Forward

- 本模块为纯 UI 精修，不影响其他模块
- 历史页"排除今天"的设计行为可能仍让部分用户困惑，未来可考虑在 UI 上加提示
