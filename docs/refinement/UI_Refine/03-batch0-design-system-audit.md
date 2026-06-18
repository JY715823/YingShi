# 第 0 批：设计系统对齐报告

> 日期：2026-06-17
> 对照规范：`docs/design/ui/03-color-system-v1.1.md`
> 对照代码：`ui/theme/Color.kt` + `ui/theme/Tokens.kt` + `ui/theme/Theme.kt` + 全项目 feature 代码

---

## 一、Token 值偏差（P0 — 必须立即修复）

逐行比对了规范第 3 节的 20 个浅色 token + 5 个 Viewer token，发现 **2 个值不一致**：

| Token | 规范 Hex | 代码 Hex | 偏差说明 |
|---|---|---|---|
| `memoryAccent` | `#A94C42` | `#A2473D` | 暖红偏暗，饱和度不足 |
| `textSecondary` | `#5E7580` | `#556B75` | 蓝灰偏暗，对比度有差异 |

其余 23 个 token 值完全一致 ✅

### 修复方式

**Color.kt 第 17 行**：
```kotlin
// 改前
val YingShiMemoryAccent = Color(0xFFA2473D)
// 改后
val YingShiMemoryAccent = Color(0xFFA94C42)
```

**Color.kt 第 25 行**：
```kotlin
// 改前
val YingShiTextSecondary = Color(0xFF556B75)
// 改后
val YingShiTextSecondary = Color(0xFF5E7580)
```

> 注意：`LedgerUiSupport.kt:123` 里有一个 `LedgerExpenseRed = Color(0xFFA94C42)`，这个值恰好等于规范的 `memoryAccent`。说明开发时可能手动抄了规范值到 ledger 模块，但 Color.kt 里的主 token 反而没更新。修复 Color.kt 后，LedgerExpenseRed 应改为引用 `YingShiMemoryAccent`。

---

## 二、Material 3 映射审查（Theme.kt）

对照规范第 7 节，逐条检查 `LightColorScheme` 映射：

| 规范要求 | Theme.kt 实际 | 状态 |
|---|---|---|
| `background` → `appBackground` | `YingShiAppBackground` | ✅ |
| `surface` → `raisedSurface` | `YingShiRaisedSurface` | ✅ |
| `surfaceVariant` → `sectionBackground` | `YingShiSectionBackground` | ✅ |
| `primary` → `titleAccent` | `YingShiTitleAccent` | ✅ |
| `onPrimary` → `raisedSurface` | `YingShiRaisedSurface` | ✅ |
| `primaryContainer` → `primaryContainer` | `YingShiPrimaryContainer` | ✅ |
| `onPrimaryContainer` → `onPrimaryContainer` | `YingShiOnPrimaryContainer` | ✅ |
| `secondaryContainer` → `softGreenContainer` | `YingShiSoftGreenContainer` | ✅ |
| `outline` → `glassStroke` | `YingShiGlassStroke` | ✅ |
| `outlineVariant` → `dividerSoft` | `YingShiDividerSoft` | ✅ |
| `onBackground` → `textPrimary` | `YingShiTextPrimary` | ✅ |
| `onSurfaceVariant` → `textSecondary` | `YingShiTextSecondary` | ✅ |

**规范外扩展映射**（代码有、规范未提及，但合理）：

| Material 3 字段 | 映射值 | 评价 |
|---|---|---|
| `secondary` | `YingShiTitleAccent` | 合理，石墨结构色 |
| `onSecondary` | `YingShiRaisedSurface` | 合理 |
| `onSecondaryContainer` | `YingShiTextPrimary` | 合理 |
| `tertiary` | `YingShiMemoryAccent` | 合理，温暖记忆色 |
| `tertiaryContainer` | `YingShiMemoryContainer` | 合理 |
| `onTertiaryContainer` | `YingShiOnMemoryContainer` | 合理 |

**DarkColorScheme 问题**（P2）：

| 字段 | 代码值 | 问题 |
|---|---|---|
| `surfaceVariant` | `Color(0xFF203A44)` | 硬编码，不在任何 token 中 |
| `outlineVariant` | `Color(0xFF2F4850)` | 硬编码，等于 `YingShiNightDivider` 但未引用 |

建议：将 Viewer 深色 token 补充 `viewerSurfaceVariant` 和 `viewerDivider` 到 Color.kt，DarkColorScheme 引用 token 而非硬编码。

---

## 三、冗余 Token（P2）

Color.kt 中存在规范未定义的额外 token：

| Token | 值 | 问题 |
|---|---|---|
| `YingShiSoftGreenAction` | `#26313A` | 值等于 `titleAccent`，语义重复。规范 4.3 节明确"玉绿上文字使用 textPrimary 或 titleAccent"，不需要单独的 action token |
| `YingShiNightDivider` | `#2F4850` | 规范未定义，但 DarkColorScheme 需要。建议提升为正式 token `viewerDivider` |

**Legacy 别名**（Color.kt 第 33-51 行）：

这些是旧字段名指向新 token 的别名（如 `YingShiBlue = YingShiPrimaryContainer`）。保留不删，但应逐步在代码中替换为正式名称。

---

## 四、硬编码颜色统计（P1 — 核心问题）

全项目搜索 `Color(0x` 硬编码，按严重程度分类：

### 4.1 占位渐变色板 — 四重复制（P1 严重）

同一套 6 色渐变色板在 **4 个文件**中重复定义：

| 文件 | 色板数量 |
|---|---|
| `FakePhotoFeedRepository.kt` | 6 组 (18 个 Color) |
| `FakeAlbumRepository.kt` | 12+ 组 (36+ 个 Color) |
| `SystemMediaRepository.kt` | 6 组 (18 个 Color) |
| `LocalSystemMediaBridgeRepository.kt` | 6 组 (18 个 Color) |
| `RealPhotoUiMappers.kt` | 6 组 (18 个 Color) |

这些色板使用完全脱离品牌系统的颜色（如 `#B8D8F8`、`#7EA6DF`、`#F5D2C3` 等），用于照片占位渐变。

**影响**：修改任何一组需要改 4 个文件。且颜色不在 token 系统中，无法统一管理。

**建议**：抽取为 `YingShiPlaceholderPalette` 数据类，定义在 `ui/theme/` 下，所有仓库引用同一份。

### 4.2 Aurora 背景 — 100+ 硬编码色（P1 中）

`YingShiAuroraBackdrop.kt` 包含约 100+ 个硬编码 `Color(0x...)` 值，按 5 个页面变体（HOME/AUTH/LIFE/ME/PHOTOS）各定义一组。

这些是装饰性渐变光晕的颜色，大部分带 alpha 通道（如 `Color(0xD6FBFFFF)`）。

**评价**：Aurora 背景本质上是"绘画"而非"UI 组件"，硬编码有一定合理性（token 匡不住这么细的渐变层次）。但 5 组配置完全独立、颜色微妙差异无规律，维护成本高。

**建议**：暂不 token 化，但应提取到独立的 `AuroraPalettes.kt` 文件，与逻辑代码分离。

### 4.3 HomeScreen 装饰色 — 10 个硬编码（P1 中）

`HomeScreen.kt` 中有 10 个硬编码 `Color(0x...)`，用于照片拼贴卡和账本卡的渐变叠层：

```
Color(0xCC5E708A).copy(alpha = 0.42f)   // 紫蓝叠层
Color(0xFFB9D6F5).copy(alpha = 0.92f)   // 浅蓝
Color(0xFFEAF5FF).copy(alpha = 0.96f)   // 极浅蓝
Color(0xFFFFE8D9).copy(alpha = 0.92f)   // 暖桃
Color(0xFFE9F6D8).copy(alpha = 0.90f)   // 浅绿
Color(0xFFF1E4FF).copy(alpha = 0.88f)   // 浅紫
Color(0xFFD8E8FF).copy(alpha = 0.90f)   // 浅蓝
Color(0xFFEAF7F3).copy(alpha = 0.94f)   // 浅绿
Color(0xFFFFF2E8).copy(alpha = 0.88f)   // 暖桃
Color(0xFFFFF5ED).copy(alpha = 0.64f)   // 暖桃
```

**问题**：这些颜色与品牌 token 系统完全脱节。`#F1E4FF`（浅紫）甚至不在你的色系范围内。

**建议**：替换为 token 衍生色：`primaryContainer.copy(alpha = ...)`、`softGreenContainer.copy(alpha = ...)`、`memoryWash.copy(alpha = ...)` 等。

### 4.4 PhotoFeedScreen 大气色板 — 40+ 硬编码（P1 中）

`PhotoFeedScreen.kt:3180-3251` 定义了 8 组"大气色板"（atmosphere palette），每组 5 个颜色（base/glint/glow/shadow/mist），共 40 个硬编码 Color。

这些用于照片流卡片的装饰性光效。

**建议**：与 4.1 占位色板统一管理，或缩减为 2-3 组（目前 8 组过多，视觉差异不明显）。

### 4.5 其他散落硬编码

| 文件 | 硬编码 | 用途 |
|---|---|---|
| `LifeConsoleScreen.kt:1285-1287` | 3 个 | 账本卡片渐变 |
| `AlbumPageScreen.kt:1218,1234` | 2 个 | 红色状态（`#F7D7D7` / `#9E2A2B`） |
| `LedgerUiSupport.kt:122-123` | 2 个 | 收入绿 `#3F8067` + 支出红 `#A94C42` |

---

## 五、Alpha Copy 滥用统计（P1）

全项目搜索 `.copy(alpha =`，发现 **200+ 处**。

### 5.1 合理使用（保留）

- 浮层背景透明：`raisedSurface.copy(alpha = 0.94f)` 用于卡片浮在 Aurora 背景上时的半透明效果
- 选中态叠层：`primaryContainer.copy(alpha = 0.42f)` 用于选中指示器
- 边框弱化：`dividerSoft.copy(alpha = 0.70f)` 用于次要边框

### 5.2 问题使用（需统一）

**问题 A：同一 token 的 alpha 值不统一**

`raisedSurface.copy(alpha = ...)` 出现的 alpha 值：
```
0.70f, 0.72f, 0.74f, 0.76f, 0.78f, 0.82f, 0.86f, 0.88f, 
0.92f, 0.94f, 0.96f, 0.98f
```
共 12 种不同的透明度！同样的"卡片底色"在不同组件里透明度从 0.70 到 0.98 不等。

`dividerSoft.copy(alpha = ...)` 出现的 alpha 值：
```
0.44f, 0.48f, 0.52f, 0.54f, 0.56f, 0.58f, 0.62f, 0.64f, 
0.66f, 0.68f, 0.70f, 0.72f, 0.74f, 0.82f
```
共 14 种！

**建议**：定义 3 档标准透明度 token：
```kotlin
// 在 Tokens.kt 中新增
object YingShiAlpha {
    val cardStrong = 0.94f    // 主要卡片
    val cardMedium = 0.88f    // 次要卡片 / 浮层
    val cardSubtle = 0.72f    // 背景层 / 次要浮层
    val borderStrong = 0.72f  // 主要边框
    val borderMedium = 0.56f // 次要边框
    val borderSubtle = 0.36f // 弱化边框
}
```

**问题 B：不必要的透明度**

很多卡片用了 `raisedSurface.copy(alpha = 0.94f)` 而非纯 `raisedSurface`。如果背景不是 Aurora（如设置页、列表页），完全不需要透明。这会导致卡片底色看起来"脏"。

---

## 六、Spacing / Radius / Motion Token 审查

### Spacing（YingShiSpacing）

| Token | 值 | 评价 |
|---|---|---|
| xxs | 4.dp | ✅ |
| xs | 8.dp | ✅ |
| sm | 12.dp | ✅ |
| md | 16.dp | ✅ |
| lg | 20.dp | ✅ |
| xl | 24.dp | ✅ |
| xxl | 32.dp | ✅ |

7 档间距，覆盖 4-32dp，合理。需检查代码中是否有硬编码 dp 值绕过 token。

### Radius（YingShiRadius）

| Token | 值 | 评价 |
|---|---|---|
| sm | 12.dp | ✅ |
| md | 18.dp | ✅ |
| lg | 24.dp | ✅ |
| xl | 28.dp | ✅ |
| capsule | 999.dp | ✅ |

**问题**：评审发现 HomeScreen 照片卡使用 `32.dp` 圆角，超出 token 范围（最大 28dp）。需排查硬编码 RoundedCornerShape。

### Motion（YingShiMotion）

参数精细（17 个时长 + 8 个缩放比 + 3 个 alpha），系统化程度高。未发现明显问题。

---

## 七、修复优先级排序

| 优先级 | 任务 | 影响范围 | 工作量 |
|---|---|---|---|
| **P0** | 修复 `memoryAccent` 和 `textSecondary` 的 hex 值 | Color.kt 2 行 | 2 分钟 |
| **P0** | `LedgerExpenseRed` 改为引用 `YingShiMemoryAccent` | LedgerUiSupport.kt 1 行 | 1 分钟 |
| **P1** | 统一 alpha 值为 3 档标准 | 全项目 200+ 处 | 2-3 小时 |
| **P1** | HomeScreen 硬编码装饰色替换为 token 衍生 | HomeScreen.kt 10 处 | 30 分钟 |
| **P1** | 占位渐变色板去重，抽取为公共定义 | 5 个文件 | 1 小时 |
| **P2** | DarkColorScheme 硬编码替换为 token | Theme.kt 2 处 | 10 分钟 |
| **P2** | 移除 `YingShiSoftGreenAction` 冗余 token | Color.kt + Tokens.kt + 引用处 | 30 分钟 |
| **P2** | 排查并修复硬编码 RoundedCornerShape | 全项目 | 1 小时 |
| **P3** | Aurora 背景色提取到独立文件 | YingShiAuroraBackdrop.kt | 1 小时 |
| **P3** | PhotoFeedScreen 大气色板缩减至 2-3 组 | PhotoFeedScreen.kt | 1 小时 |

---

## 八、给 Codex AI 的修复指令

将以下指令发给 Codex AI，让它批量执行 P0 + P1 修复：

### 指令 1：修复 Token 值（P0）

```
打开 ui/theme/Color.kt，做以下修改：
1. 第 17 行：val YingShiMemoryAccent = Color(0xFFA2473D) 改为 Color(0xFFA94C42)
2. 第 25 行：val YingShiTextSecondary = Color(0xFF556B75) 改为 Color(0xFF5E7580)

打开 feature/ledger/LedgerUiSupport.kt，做以下修改：
1. 第 123 行：val LedgerExpenseRed = Color(0xFFA94C42) 改为 val LedgerExpenseRed = YingShiMemoryAccent
   并确保 import 了 com.example.yingshi.ui.theme.YingShiMemoryAccent
```

### 指令 2：修复 Theme.kt 硬编码（P2）

```
打开 ui/theme/Theme.kt，做以下修改：
1. 第 27 行：surfaceVariant = Color(0xFF203A44) 改为 surfaceVariant = YingShiViewerSurface
2. 第 30 行：outlineVariant = Color(0xFF2F4850) 改为 outlineVariant = YingShiNightDivider
```

### 指令 3：HomeScreen 硬编码色替换（P1）

```
打开 feature/home/HomeScreen.kt，将以下硬编码颜色替换为 token 引用：

1. Color(0xFFB9D6F5).copy(alpha = 0.92f) → colors.primaryContainer.copy(alpha = 0.92f)
2. Color(0xFFEAF5FF).copy(alpha = 0.96f) → colors.glowWash.copy(alpha = 0.96f)
3. Color(0xFFFFE8D9).copy(alpha = 0.92f) → colors.memoryWash.copy(alpha = 0.92f)
4. Color(0xFFE9F6D8).copy(alpha = 0.90f) → colors.softGreenContainer.copy(alpha = 0.90f)
5. Color(0xFFF1E4FF).copy(alpha = 0.88f) → colors.glowWash.copy(alpha = 0.88f)
6. Color(0xFFD8E8FF).copy(alpha = 0.90f) → colors.primaryContainer.copy(alpha = 0.90f)
7. Color(0xFFEAF7F3).copy(alpha = 0.94f) → colors.softGreenContainer.copy(alpha = 0.94f)
8. Color(0xFFFFF2E8).copy(alpha = 0.88f) → colors.memoryWash.copy(alpha = 0.88f)
9. Color(0xFFFFF5ED).copy(alpha = 0.64f) → colors.memoryWash.copy(alpha = 0.64f)
10. Color(0xCC5E708A).copy(alpha = 0.42f) → colors.titleAccent.copy(alpha = 0.42f)
```

---

## 九、验收清单

修复完成后，用以下方式验收：

- [ ] 在 Color.kt 中搜索 `0xFFA2473D`，结果为 0
- [ ] 在 Color.kt 中搜索 `0xFF556B75`，结果为 0
- [ ] 在全项目搜索 `Color(0x` 在 `feature/` 目录下（排除 Repository 和 AuroraBackdrop），结果接近 0
- [ ] HomeScreen.kt 中搜索 `Color(0x`，结果为 0
- [ ] Theme.kt 中搜索 `Color(0x`，结果为 0（DarkColorScheme 也用 token）
- [ ] 编译通过，App 启动后各页面颜色无明显变化（token 值差异极小）
- [ ] 截图对比修复前后，`memoryAccent` 使用处（如未读点、纪念日提示）颜色略偏暖

---

## 十、总结

| 维度 | 评分 | 说明 |
|---|---|---|
| Token 值准确度 | 23/25 = 92% | 2 个值偏差，但差异极小 |
| Material 3 映射 | 12/12 = 100% | 完全对齐规范 |
| Token 覆盖率 | 良好 | 间距/圆角/动效系统完善 |
| 硬编码控制 | 差 | 200+ 处 alpha copy + 100+ 处硬编码 Color |
| 一致性 | 差 | 同一 token 12 种不同 alpha 值 |

**一句话**：Token 定义层 90 分，但代码执行层只有 50 分——硬编码和 alpha 滥用是最大的设计债。

先修 P0（2 个 hex 值），再逐步清理 P1。P0 修复后这个批次的地基就算打好了，可以进入第 1 批（登录页精修）。
