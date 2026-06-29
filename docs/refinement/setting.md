# setting 精修 Brief

> 状态: **CLOSED** ✅
> 模块: 设置 / 缓存管理 / 连接诊断
> 日期: 2026-06-29

## 模块目标

将设置、缓存管理、连接诊断三个页面从"功能齐全但视觉单调"提升到"有分类导航、有氛围、有质感"。

## 当前状态

- SettingsScreen 9 个 section 全部纵向堆叠，无分类导航，用户看到一面卡片墙
- 三个页面都用 `YingShiMistBackground(showWaves=false)` + 裸 `Surface` 卡片，视觉无区分
- 未使用设计系统已有的丰富组件（YingShiMistCard、YingShiEntryCard、YingShiStateLayer、YingShiPrimaryMistButton、TitleTabs）
- 无任何页面进入/内容揭示动画
- ChoiceRow 的 chip 样式基础，选中态无渐变
- 缓存管理页的清理按钮是纯文字 Surface，无视觉层次
- 诊断页用裸 OutlinedTextField，连接状态无色彩反馈

## 用户输入

- 设置可以按顶部 chip 来分类选
- 觉得做的都不太好看
- 让 AI 多自由发挥

## 确认范围

### P0 必做
1. SettingsScreen 加顶部 chip 分类导航（4 组：空间/浏览/分享/系统）
2. 三个页面全部加 YingShiMistBackground(showWaves=true) + 不同 variant
3. 全面升级组件：Surface → YingShiMistCard，跳转行 → YingShiEntryCard，状态行 → YingShiStateLayer，按钮 → YingShiPrimaryMistButton，诊断输入 → YingShiTextField
4. 页面进入动画 yingShiRouteReveal / yingShiSoftReveal
5. Choice chip 选中态加渐变底色 + glassStroke 边框

### P1 应做
6. 缓存管理视觉升级：概览用 YingShiStateLayer，清理按钮 2x2 grid + icon，"清除全部"用 memoryAccent 独立卡片
7. 连接诊断视觉升级：连接状态用 YingShiStateLayer（自动切换 success/warning tone），操作结果用 YingShiStateLayer

### 明确不做
- 搜索功能
- 不改数据模型和 Repository
- 不改 API 契约
- 不改导航结构

## Scope Boundaries

- 不改 SettingsRepository / MediaCacheRepository / BackendDebugConfig 的数据逻辑
- 不改导航路由（还是 overlay 模式，还是从 MyScreen 进入）
- 不改 PushPreference 的远端同步逻辑
- 只改 UI 层 composable

## Related Modules

- MyScreen（入口不变）
- NotificationCenterScreen（缓存管理入口不变）
- YingShiApp.kt（路由状态变量不变）

## Contracts

- 无 API 契约变更（设置全部是客户端本地 SharedPreferences）
- 推送偏好同步逻辑不变

## UI 细节

### SettingsScreen chip 分组
- 空间：账号与空间、缓存与存储
- 浏览：浏览密度、查看器偏好、时间排序
- 分享：分享
- 系统：权限与通知、交互体验、连接与诊断
- chip 选中态：primaryContainer 渐变底 + glassStroke 边框
- chip 切换时内容区 yingShiSoftReveal 动画

### 背景 variant
- SettingsScreen: YingShiBackdropVariant.SHELL
- CacheManagementScreen: YingShiBackdropVariant.PHOTOS
- BackendDiagnosticsScreen: YingShiBackdropVariant.LIFE

### 组件映射
- SettingsSection → YingShiMistCard
- SettingsEntryRow → YingShiEntryCard
- 状态行（登录/权限/推送诊断）→ YingShiStateLayer
- 危险操作（退出/清除全部）→ memoryAccent 色调独立卡片
- 缓存清理按钮 → 2x2 grid of YingShiMistCard + icon + 大小 + 清理按钮
- 诊断 URL 输入 → YingShiTextField
- 诊断操作按钮 → YingShiPrimaryMistButton

## Plan Self-Check

- [x] 范围明确：3 个页面 UI 重写
- [x] 契约不变：无 API 变更
- [x] 无新依赖：全部用现有设计系统组件
- [x] 动画尊重 reduced-motion
- [x] 回滚安全：不改 DB / SharedPreferences 结构

## Hidden Impact List

- notifications: 无影响（推送偏好逻辑不变）
- auth/session: 无影响（退出登录逻辑不变）
- upload: 无影响
- viewer: 无影响（设置只改 UI，不改 ViewerPreference 逻辑）
- settings: 核心改动页面，但只改 UI 层
- cache/offline: 缓存管理只改展示和清理入口 UI，不改清理逻辑
- permissions: 无影响（权限请求逻辑不变）
- copy: 无文案变更
- empty states: 无影响

## Implement Notes

### SettingsScreen
- Chip navigation: 4 categories (空间/浏览/分享/系统) with gradient selected state (Brush.horizontalGradient)
- AnimatedContent with fadeIn/fadeOut transition for category switching
- Each section wrapped in YingShiMistCard with yingShiRouteReveal animation
- Navigation entries use YingShiEntryCard (icon + title + subtitle + onClick)
- Diagnostic rows use YingShiStateLayer with dynamic tone (SUCCESS/WARNING/INFO)
- ChoiceRow chips upgraded with gradient selected state (matching chip nav style)
- New SettingsSectionHeader composable for title+subtitle inside MistCard
- Removed old SettingsSection, SettingsEntryRow, SettingsActionInfoRow composables
- TopBar has yingShiSoftReveal animation

### CacheManagementScreen
- Background: YingShiBackdropVariant.PHOTOS + showWaves=true
- All sections wrapped in YingShiMistCard with yingShiRouteReveal
- CacheActionRow → CacheActionCard with circular icon badge (primaryContainer bg)
- "清除全部" danger action: standalone memoryContainer card with memoryAccent icon
- TopBar has yingShiSoftReveal animation
- Removed old CacheSection composable

### BackendDiagnosticsScreen
- Background: YingShiBackdropVariant.LIFE + showWaves=true
- OutlinedTextField → YingShiTextField(icon = Icons.Rounded.Link)
- Emphasized button → YingShiPrimaryMistButton
- Connection status → YingShiStateLayer with dynamic tone based on BackendAutoLoginPhase
- All sections wrapped in YingShiMistCard with yingShiRouteReveal
- Secondary button (退出连接) → DiagnosticsSecondaryButton (custom styled Surface)
- Removed old DiagnosticsSection, BackendConnectionActionButton composables
- TopBar has yingShiSoftReveal animation

## Post-Implement Self-Check

### Newly added behavior
- SettingsScreen: chip category navigation with gradient selected state
- SettingsScreen: AnimatedContent crossfade between category content
- SettingsScreen: YingShiStateLayer for notification/push diagnostics (dynamic tone)
- SettingsScreen: YingShiEntryCard for navigation entries (with icons)
- CacheManagementScreen: YingShiMistCard sections + PHOTOS background
- CacheManagementScreen: CacheActionCard with icon badges
- CacheManagementScreen: memoryAccent danger card for "清除全部"
- BackendDiagnosticsScreen: YingShiTextField + YingShiPrimaryMistButton
- BackendDiagnosticsScreen: YingShiStateLayer for connection status
- BackendDiagnosticsScreen: LIFE background variant
- All 3 screens: yingShiRouteReveal + yingShiSoftReveal animations

### Contract consistency
- No API changes — all settings are client-side SharedPreferences
- No navigation structure changes — same overlay mode, same entry points
- No data model changes — SettingsRepository / MediaCacheRepository / BackendDebugConfig unchanged

### Verified locally
- All imports verified present and correct across 3 files
- Component API signatures match usage (YingShiMistCard, YingShiEntryCard, YingShiStateLayer, YingShiPrimaryMistButton, YingShiTextField)
- No new dependencies introduced
- All motion modifiers respect reduced-motion setting
- Unused imports cleaned up

### Should regression-check on device
- SettingsScreen: chip selection visual, AnimatedContent transition smoothness
- SettingsScreen: YingShiStateLayer tone colors for diagnostic rows
- CacheManagementScreen: CacheActionCard icon badges, memoryAccent danger card
- BackendDiagnosticsScreen: YingShiTextField focus/input, YingShiPrimaryMistButton tap
- BackendDiagnosticsScreen: connection status tone matches actual state
- Page entry animations on all 3 screens
- Test with reduced-motion enabled → animations should be instant

## Close Summary

**Closed:** 2026-06-29

**改动文件：**
- `SettingsScreen.kt` — 完全重写：chip 分类导航 (4 组)、AnimatedContent 切换动画、全面迁移到设计系统组件 (YingShiMistCard / YingShiEntryCard / YingShiStateLayer)、choice chip 渐变选中态、背景 SHELL variant
- `CacheManagementScreen.kt` — 视觉升级：YingShiMistCard 卡片、CacheActionCard 带圆形图标徽章、"清除全部"独立 memoryAccent 危险卡片、背景 PHOTOS variant
- `BackendDiagnosticsScreen.kt` — 视觉升级：YingShiTextField + YingShiPrimaryMistButton、连接状态 YingShiStateLayer 动态色调、背景 LIFE variant

**修复的问题：**
- YingShiNoticeTone.ERROR 不存在 → 改用 WARNING
- 多处未使用的 import 已清理
- Kotlin 智能引号 (U+201C/U+201D) 导致编译失败 → 改用「」

**未变更：**
- 无 API / 数据模型 / Repository / 导航结构改动
- 所有设置仍为客户端 SharedPreferences

**待设备回归验证：**
- chip 选中态渐变 + AnimatedContent 过渡流畅度
- YingShiStateLayer 色调在不同诊断状态下的视觉
- CacheActionCard 图标徽章 + memoryAccent 危险卡片
- YingShiTextField 焦点/输入、YingShiPrimaryMistButton 点击
- 页面进入动画 + reduced-motion 降级
