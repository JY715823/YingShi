# life_home 精修 Brief

> 状态: **CLOSED** ✅
> 模块: 生活首页（LifeScreen）
> 日期: 2026-06-29

## 模块目标

将生活首页从"功能正确但视觉朴素"提升到"与其他精修页面一致的质感和氛围"。保持三卡片三入口的结构不变，重点做视觉美化和设计系统对齐。

## 当前状态

- LifeScreen 结构简洁：标题区（"映世 生活"）+ 3 个纵向等权 LifeEntryCard（记账/聊天记录/今日痕迹）
- 背景 `YingShiMistBackground(showWaves=false, variant=LIFE)` — 无波浪装饰，其他精修页面均已启用 showWaves=true
- 卡片用裸 `Surface` + 自定义 LifeEntryCard composable，未使用 YingShiMistCard
- 硬编码 padding（16.dp/14.dp/12.dp/18.dp），未使用 YingShiThemeTokens.spacing
- 无任何页面进入/内容揭示动画（无 yingShiRouteReveal / yingShiSoftReveal）
- 标题区极简，仅有两行文字，无问候语或日期装饰
- 每个卡片有独立色调（BLUE/GREEN/WARM），但视觉层次较平，缺少渐变或光晕点缀
- status pill + ChevronRight 信息密度略高，视觉上有些拥挤

## 用户输入

- 感觉现在还行，没啥大改的
- 就一个首页，三个卡片对应三个入口
- 可以优化美化一下界面

## Codex 建议

### 推荐本轮做

1. **背景启用 showWaves=true** — 与 CacheManagement / BackendDiagnostics 对齐，LIFE variant 已有
2. **全面使用 spacing/radius tokens** — 替换所有硬编码 dp 值，与设计系统一致
3. **卡片容器迁移到 YingShiMistCard** — 保留自定义内容（icon + accent），但外壳用 MistCard 统一阴影/圆角/边框
4. **加 yingShiRouteReveal / yingShiSoftReveal 动画** — 标题区 softReveal，卡片逐个 routeReveal（stagger）
5. **标题区增加时间问候** — 根据当前时间显示"早安/午安/晚安" + 日期（如"6月29日 周日"），让首页有温度感
6. **卡片视觉层次提升** — 每张卡片背景加一层极淡的对应色调 radialGradient（类似 YingShiStateLayer 的做法），让 BLUE/GREEN/WARM 色调更自然地渗透出来，而不是只在 icon 和 pill 上

### 明确不做

- 不改变三卡片结构和导航逻辑
- 不加新功能或新入口
- 不改 LifeEntryAccent 枚举和 onClick 回调
- 不加滚动（当前 weight 均分已经够用）

## Scope Boundaries

- 只改 LifeScreen.kt 的 UI 层
- 不改 LifeConsoleScreen / LifeConsoleUploadBridge / WidgetMediaEntryActivity 等任何其他文件
- 不改导航路由和回调签名
- 不加新依赖

## Related Modules

- MyScreen（同级底部导航页，视觉风格应一致）
- SettingsScreen（已精修，视觉标准参考）
- CacheManagementScreen（已精修，showWaves + MistCard 参考）
- YingShiApp.kt（路由入口不变）

## Contracts

- 无 API 契约变更（纯 UI 页面）
- 回调签名不变：onOpenLifeConsole / onOpenLedger / onOpenChatViewer

## UI 细节

### 标题区升级
- 保留"映世"小标签 + "生活"大标题结构
- 新增：根据时间显示问候语（"早安" / "午安" / "晚安"）+ 日期（"M月d日 EEEE"）
- 问候语用 labelLarge + textSecondary，日期用 bodyMedium + textSecondary
- 标题区整体加 yingShiSoftReveal 动画

### 卡片升级
- 外壳：Surface → YingShiMistCard（统一 shadow/border/radius）
- 背景色调渗透：每张卡片在 MistCard 内叠加一层极淡的 accent radialGradient（alpha 0.06-0.08），让 BLUE/GREEN/WARM 色调自然渗出
- icon 圆形徽章保留，但用对应 accent 色调
- status pill 保留，但简化为更轻的视觉（去掉 ChevronRight，pill 本身已暗示可点击）
- 卡片整体加 yingShiRouteReveal 动画，三张卡片 stagger 进入

### 背景
- showWaves = true（启用波浪装饰）
- variant = LIFE（不变）

### Spacing 对齐
- 所有硬编码 dp → YingShiThemeTokens.spacing（xs/sm/md/lg/xl）
- 所有硬编码 radius → YingShiThemeTokens.radius（sm/md/lg/xl/capsule）

## Plan Self-Check

- [x] 范围明确：1 个页面 UI 美化
- [x] 契约不变：无 API 变更，回调签名不变
- [x] 无新依赖：全部用现有设计系统组件
- [x] 动画尊重 reduced-motion
- [x] 回滚安全：不改任何数据/导航/状态

## Hidden Impact List

- notifications: 无影响
- auth/session: 无影响
- upload: 无影响
- viewer: 无影响
- settings: 无影响
- cache/offline: 无影响
- permissions: 无影响
- copy: 新增问候语 + 日期（纯 UI 装饰，不影响现有文案）
- empty states: 无影响（首页无空状态）
- life-console: 无影响（只改入口卡片 UI，不改 LifeConsoleScreen）
- widget: 无影响

## Implement Notes

### LifeScreen
- Background: showWaves=true (was false), variant=LIFE unchanged
- Header: added time-based greeting ("早安/午安/晚安") + date ("M月d日 周X") via Calendar
- Header has yingShiSoftReveal animation
- All hardcoded dp values replaced with spacing tokens (spacing.xs/sm/md/lg/xl)
- All hardcoded radius replaced with radius tokens (radius.lg/capsule)

### LifeEntryCard
- Container: raw Surface → YingShiMistCard (unified shadow/border/radius)
- Background: added accent radialGradient (alpha 0.07 → 0.04 → transparent) per card color
- Icon badge: retained CircleShape with accent colors, size reduced 30dp→28dp, padding uses spacing.sm
- Status pill: retained, removed ChevronRight icon (pill alone implies clickable), alpha reduced for lighter visual
- Each card has yingShiRouteReveal animation
- Spacing between cards: spacing.sm (was 12.dp hardcoded)

### Removed
- ChevronRight import (no longer used)
- Hardcoded dp padding values
- Raw Surface card container (replaced by YingShiMistCard)

## Post-Implement Self-Check

### Newly added behavior
- Time-based greeting + date display in header
- showWaves=true for LIFE backdrop
- yingShiSoftReveal on header, yingShiRouteReveal on each card
- Accent radialGradient background per card (BLUE/GREEN/WARM)
- YingShiMistCard as card container

### Contract consistency
- No API changes
- No navigation changes
- Callback signatures unchanged: onOpenLifeConsole / onOpenLedger / onOpenChatViewer
- LifeEntryAccent enum unchanged

### Verified locally
- All imports verified present
- Component APIs match: YingShiMistCard(modifier, shape?, color?, borderColor?, content)
- Animation modifiers: yingShiRouteReveal(visible, motionEnabled), yingShiSoftReveal(visible, enterScale, motionEnabled)
- No new dependencies
- All motion respects reduced-motion via rememberYingShiMotionEnabled()

### Should regression-check on device
- Greeting text displays correctly for current time
- Date format displays correctly (e.g. "6月29日 周日")
- Card accent gradient visible but subtle
- Status pill without ChevronRight still looks balanced
- Animations smooth on entry
- showWaves=true with LIFE variant looks good
- Test with reduced-motion enabled
## Close Summary

**Closed:** 2026-06-29

**改动文件：**
- `LifeScreen.kt` — 视觉升级：showWaves=true、时间问候+日期标题、YingShiMistCard 卡片容器、accent radialGradient 背景渗透、yingShiSoftReveal/yingShiRouteReveal 动画、spacing tokens 对齐、去掉 ChevronRight

**未变更：**
- 无 API / 数据模型 / 导航结构改动
- 回调签名不变（onOpenLifeConsole / onOpenLedger / onOpenChatViewer）
- LifeEntryAccent 枚举不变
- 无新依赖

**待设备回归验证：**
- 问候语 + 日期显示正确
- 卡片 accent 渐变可见但不突兀
- 去掉 ChevronRight 后 status pill 视觉平衡
- 动画流畅 + reduced-motion 降级
- showWaves + LIFE variant 背景效果
