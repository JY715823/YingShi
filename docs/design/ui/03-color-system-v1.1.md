# 映世颜色系统 v1.1

Updated: 2026-06-02

本版把映世颜色系统推进为 `珍珠玉蓝 · 温暖记忆点缀版`。它服务的是双人私密相册与生活记录 App：真实照片、相册目录、生活记录和 Viewer 永远是主角，颜色负责给界面建立安静、亲密、可读、可信的秩序。

核心结构仍是珍珠白、浅玉绿、清透浅蓝光面和石墨黑文字。新增的“温暖记忆色”只负责关键状态和情绪记忆点：精选、纪念、未读、同步结果、关系状态、重要提醒、小数字和局部 badge。它不是第二主色，不是分类色，也不进入主按钮和主导航大面积背景。

## 1. 设计目标

本版解决 6 件事：

1. 统一 `珍珠玉蓝` 的主系统语义 token。
2. 让主按钮保持浅蓝光面、石墨文字、轻玻璃边。
3. 把标题、图标、正文从旧深蓝结构迁到石墨系统。
4. 新增 `温暖记忆色`，让界面有更明确的情绪记忆点。
5. 收窄旧金角色，保留日期、数量和细节质感。
6. 让调色台、文档和 Compose token 共享同一套口径。

本版暂时不做：

- 完整 50 / 100 / 200 / 300 色阶。
- 全局暗色主题。
- 节日主题。
- 多色分类系统。
- 高饱和插画扩展色。
- 把微玻璃或暖色铺满普通内容卡片。

## 2. 总方向

固定方向：

- 主方向：`珍珠玉蓝 · 温暖记忆点缀版`
- 页面大底：`珍珠浅蓝`
- 分区承托：`浅玉蓝绿`
- 卡片主体：`珍珠白`
- 主操作：`浅蓝光面 + 石墨字 + 轻玻璃边`
- 结构强调：`石墨标题 / 石墨图标`
- 辅助分区：`浅玉绿`
- 日期细节：`可读旧金`
- 情绪状态：`温暖记忆色`
- Viewer：`独立深色沉浸`

核心判断标准：

- 不是廉价浅蓝工具页。
- 不是炫彩 AI 海报。
- 不是深蓝按钮白字的传统 Material 套壳。
- 普通内容卡片保持清爽，微玻璃只出现在导航、浮层、选中态和主按钮。
- 温暖记忆色只点亮关键状态，不能变成第二套主视觉。

## 3. 核心语义 Token

| Token | Hex | 作用 | Compose / Material 3 建议映射 |
| --- | --- | --- | --- |
| `appBackground` | `#F1FBFD` | 珍珠浅蓝页面底 | `colorScheme.background` |
| `sectionBackground` | `#DFF5F4` | 浅玉蓝绿分区、列表承托区 | `colorScheme.surfaceVariant` |
| `raisedSurface` | `#FFFFFC` | 珍珠白卡片、表单、普通内容面 | `colorScheme.surface` |
| `primaryContainer` | `#BDEFFF` | 浅蓝主操作容器、选中承托 | `colorScheme.primaryContainer` |
| `onPrimaryContainer` | `#1F2933` | 主操作文字和图标，石墨黑 | `colorScheme.onPrimaryContainer` |
| `selectedPillBg` | `#BDEFFF` | 兼容旧字段，当前态圆片底 | 自定义扩展 token |
| `primaryAction` | `#BDEFFF` | 兼容旧字段，指向浅蓝主操作底 | 自定义扩展 token |
| `primaryActionPressed` | `#A7E9FF` | 主操作按压态浅蓝 | 自定义扩展 token |
| `titleAccent` | `#26313A` | 标题、图标、结构强调 | `colorScheme.primary` 可读兼容映射 |
| `softGreenContainer` | `#D8F2E6` | 浅玉绿辅助分区 | `colorScheme.secondaryContainer` |
| `goldAccent` | `#9A6A2A` | 日期、数量、旧金细节 | 自定义扩展 token |
| `memoryAccent` | `#A94C42` | 温暖记忆强调色，用于小字、图标、状态点 | 自定义扩展 token |
| `memoryContainer` | `#FFE1DA` | 温暖状态浅底，用于 badge、精选、纪念提示 | 自定义扩展 token |
| `onMemoryContainer` | `#3A2724` | 温暖浅底上的文字 | 自定义扩展 token |
| `memoryWash` | `#FFF1EE` | 极轻情绪洗色，只用于登录页、纪念状态、局部提示背景 | 自定义扩展 token |
| `dividerSoft` | `#C7E6EC` | 轻边界、分割线 | `colorScheme.outlineVariant` |
| `glassStroke` | `#A9E5F2` | 微玻璃边线、主按钮边、浮层边 | `colorScheme.outline` |
| `glowWash` | `#E7FAFF` | 轻流光洗色、背景水光 | 自定义扩展 token |
| `textPrimary` | `#1F2933` | 正文石墨黑 | `colorScheme.onBackground` |
| `textSecondary` | `#5E7580` | 可读蓝灰辅助文字 | `colorScheme.onSurfaceVariant` |

Viewer 独立深色 token：

| Token | Hex | 作用 |
| --- | --- | --- |
| `viewerBackground` | `#101F26` | Viewer 整体背景 |
| `viewerSurface` | `#1D333C` | Viewer 浮层、评论预览层 |
| `viewerAccent` | `#BDEFFF` | Viewer 清透高光 |
| `viewerText` | `#F4FBFC` | Viewer 主文字 |
| `viewerTextSecondary` | `#A9C3CC` | Viewer 次文字 |

## 4. Token 使用规则

### 4.1 浅蓝

- `primaryContainer` 是浅蓝主视觉，不是文字色。
- `primaryAction` 只作为旧字段兼容，语义等同 `primaryContainer`。
- 浅蓝可用于主按钮、选中态、轻 badge 底、导航当前态和局部流光。
- 浅蓝不能用于正文、标题、小字、普通图标 tint。

### 4.2 石墨

- `textPrimary` 负责正文和普通标题。
- `titleAccent` 负责页面标题、入口图标、当前态文字和结构强调。
- `onPrimaryContainer` 负责浅蓝容器上的按钮文字和图标。
- 页面里需要“强调但不是按钮”的内容，优先用 `titleAccent`。

### 4.3 玉绿

- `softGreenContainer` 用于生活模块、相册辅助分区、轻状态区。
- 玉绿不承担主 CTA。
- 玉绿上文字使用 `textPrimary` 或 `titleAccent`，避免浅色字。

### 4.4 旧金

- `goldAccent` 收窄为日期、数量、旧金细线和极低频质感细节。
- 旧金可以作为小字色，但必须只落在浅底上。
- 旧金不再优先承担“精选、纪念、未读、关系状态”等情绪状态。
- 旧金不进入主按钮、主导航和大面积卡片背景。

### 4.5 温暖记忆色

- `memoryAccent` 用于小图标、小数字、状态点、未读点、同步成功提示、关系标签、重要提醒文字。
- `memoryContainer + onMemoryContainer` 用于 badge、pill、精选标签、纪念日提示、新导入、刚更新。
- `memoryWash` 只能用于登录页、纪念状态提示、未读行或局部情绪提示背景。
- 温暖记忆色不是分类色，不用于普通内容卡片大底。
- 温暖记忆色不进入主按钮、不进入主导航大面积背景、不替代错误色。
- 错误仍使用 Material error。

### 4.6 微玻璃

- `glassStroke` 负责轻玻璃边线。
- `glowWash` 负责很轻的流光洗色。
- 微玻璃只用于导航、浮层、选中态和主按钮。
- 普通内容卡片仍用 `raisedSurface + dividerSoft`，不做玻璃拟态堆叠。

## 5. 页面分配规则

### 5.1 登录页

- 页面底色使用 `appBackground`。
- 表单容器使用 `raisedSurface`。
- 主登录按钮使用 `primaryContainer + onPrimaryContainer + glassStroke`。
- 标题和关键图标使用 `titleAccent`。
- 可少量使用 `memoryWash` 做“进入共同空间”的情绪洗色。
- 错误、校验和状态文字不得使用浅蓝，错误仍用 Material error。

### 5.2 主页

- 页面底色使用 `appBackground`。
- 总览卡、快捷入口卡使用 `raisedSurface`。
- 共享空间摘要和最近回忆承托可少量使用 `sectionBackground`。
- 最近回忆的状态点、重要提醒、未读点可以使用 `memoryAccent`。
- 日期和普通数量仍可使用 `goldAccent`。

### 5.3 照片首页

- 页面底色使用 `appBackground`。
- 筛选区、分组区、顶部工具承托可使用 `sectionBackground`。
- 当前态和选择操作使用 `primaryContainer + titleAccent`。
- 新导入、未读、传输进行中使用 `memoryContainer / memoryAccent`。
- 上传失败、删除失败等错误不使用记忆色，继续使用 Material error。
- 照片本身不加彩色大底，真实媒体优先。

### 5.4 相册页

- 页面底色使用 `appBackground`。
- 相册目录和小相册列表可比照片页更多使用 `softGreenContainer`。
- 小相册卡片主体仍以 `raisedSurface` 为主。
- 刚更新、精选、纪念提示优先使用 `memoryContainer + onMemoryContainer`。
- 数量、更新时间、日期细节可少量使用 `goldAccent`。

### 5.5 小相册详情页

- 页面底色使用 `appBackground`。
- 标题、操作入口、结构标签使用 `titleAccent`。
- 正文、简介、评论预览使用 `raisedSurface`。
- 新媒体、新评论、重要关系提示使用 `memoryAccent / memoryContainer`。
- 标签 chip 可用 `primaryContainer`、`softGreenContainer` 或 `memoryContainer` 做浅底，文字用对应深色。

### 5.6 生活页

- 页面底色使用 `appBackground`。
- 账本、聊天记录、生活入口允许明显使用 `softGreenContainer`。
- 关键动作仍使用 `primaryContainer + onPrimaryContainer`。
- 纪念日、同步结果、重要提醒可少量使用 `memoryAccent`。
- 不允许整页刷成绿色或暖红工具页。

### 5.7 我的页与设置页

- 页面底色使用 `appBackground`。
- 主卡片使用 `raisedSurface`。
- 关系、同步状态、另一半标签可以少量使用 `memoryAccent`。
- 入口图标和标题用 `titleAccent`。
- 退出、危险操作和错误提示不使用记忆色。

### 5.8 Viewer

- Viewer 使用独立深色 token，不沿用浅色壳层。
- 图片和视频永远是 Viewer 主角。
- 浮层薄、轻、克制，不能变成重色面板。
- Viewer 不使用温暖记忆色作为大面积背景。
- 如需点亮“新评论”等小状态，只允许极少量使用 `memoryAccent` 或 `viewerAccent`，并优先保证图片沉浸。

## 6. 组件固定用色

### 6.1 主按钮

- 背景：`primaryContainer`
- 文字：`onPrimaryContainer`
- 边线：`glassStroke`
- 按压：`primaryActionPressed`
- 光面洗色：`glowWash` 和少量珍珠白

主按钮禁用：

- 不用白字。
- 不用深青字。
- 不用深蓝底。
- 不使用 `memoryAccent`。
- 不把 `MaterialTheme.colorScheme.primary` 当按钮背景直接使用。

### 6.2 次按钮和文本按钮

- 次按钮：`raisedSurface + titleAccent`
- 文本按钮：`titleAccent`
- 描边按钮边框：`dividerSoft` 或 `glassStroke`
- 危险操作使用标准危险色，不借用本系统主色或记忆色。

### 6.3 Badge / Pill

- 普通选中：`primaryContainer + onPrimaryContainer`
- 温暖状态：`memoryContainer + onMemoryContainer`
- 温暖状态边线：`memoryAccent.copy(alpha = 0.20f)`
- 未读点、小数字、小图标：`memoryAccent`
- 日期数量细节：`goldAccent`

### 6.4 输入框

- 背景：`raisedSurface`
- 默认边线：`dividerSoft`
- 聚焦边线：`glassStroke`
- 正文：`textPrimary`
- 占位提示：`textSecondary`

### 6.5 卡片

- 普通内容卡：`raisedSurface + dividerSoft`
- 分区承托：`sectionBackground`
- 生活辅助卡：`softGreenContainer`
- 纪念状态提示：可局部使用 `memoryWash` 或 `memoryContainer`
- 卡片文字默认使用 `textPrimary` 和 `textSecondary`
- 卡片不依赖大阴影和过度玻璃感。

### 6.6 顶栏和底栏

- 浮层主体：`raisedSurface`
- 当前态底：`primaryContainer`
- 当前态文字和图标：`titleAccent`
- 浮层边线：`glassStroke`
- 非当前态：`textSecondary`
- 未读或同步角标：`memoryContainer + onMemoryContainer`

## 7. Compose / Material 3 落地

当前代码落地原则：

| Material / Custom | 映射 |
| --- | --- |
| `colorScheme.background` | `appBackground` |
| `colorScheme.surface` | `raisedSurface` |
| `colorScheme.surfaceVariant` | `sectionBackground` |
| `colorScheme.primary` | `titleAccent` |
| `colorScheme.onPrimary` | `raisedSurface` |
| `colorScheme.primaryContainer` | `primaryContainer` |
| `colorScheme.onPrimaryContainer` | `onPrimaryContainer` |
| `colorScheme.secondaryContainer` | `softGreenContainer` |
| `colorScheme.outline` | `glassStroke` |
| `colorScheme.outlineVariant` | `dividerSoft` |
| `colorScheme.onBackground` | `textPrimary` |
| `colorScheme.onSurfaceVariant` | `textSecondary` |

注意：`colorScheme.primary` 暂时映射为可读的石墨结构强调色，而不是浅蓝，也不是温暖记忆色。原因是现有 Compose 代码中大量 `MaterialTheme.colorScheme.primary` 被用于文字、图标和轻高亮。如果直接把 Material `primary` 改成浅蓝或记忆色，会造成可读性和语义问题。

新增或重构组件时，浅蓝主操作必须优先使用：

```kotlin
YingShiThemeTokens.colors.primaryContainer
YingShiThemeTokens.colors.onPrimaryContainer
YingShiThemeTokens.colors.glassStroke
YingShiThemeTokens.colors.glowWash
```

温暖记忆状态必须优先使用：

```kotlin
YingShiThemeTokens.colors.memoryAccent
YingShiThemeTokens.colors.memoryContainer
YingShiThemeTokens.colors.onMemoryContainer
YingShiThemeTokens.colors.memoryWash
```

旧字段兼容规则：

- `primaryAction` 保留，但等同浅蓝主操作底。
- `primaryAction` 不再可用作文字色、图标色或边框强调色。
- `titleAccent` 不再是深青，而是石墨结构强调。
- `selectedPillBg` 保留，等同浅蓝当前态承托。
- `memoryAccent` 不映射为 Material `primary`、`secondary` 或 `error`。

## 8. 对比度要求

必须通过 AA 的组合：

- `textPrimary` on `appBackground`
- `textSecondary` on `appBackground`
- `textPrimary` on `raisedSurface`
- `onPrimaryContainer` on `primaryContainer`
- `goldAccent` on `raisedSurface`
- `memoryAccent` on `raisedSurface`
- `memoryAccent` on `appBackground`
- `onMemoryContainer` on `memoryContainer`
- `textPrimary` on `memoryWash`
- `viewerText` on `viewerBackground`
- `viewerTextSecondary` on `viewerBackground`
- `viewerAccent` on `viewerBackground`

如果某个页面需要在 `primaryContainer` 上放置小字号辅助信息，文字必须用 `onPrimaryContainer`，不要使用 `textSecondary` 或白色。

如果某个页面需要在 `memoryContainer` 上放置文字，必须用 `onMemoryContainer`，不要使用白色或普通灰字。

## 9. 验收口径

首轮验收看 5 个场景：

1. 登录：主按钮仍是浅蓝光面和石墨字，局部情绪洗色可以更暖，但表单不能像纯白开发面板。
2. 照片：真实照片优先，选中态是浅蓝系统，未读、新导入和同步状态有温暖记忆点。
3. 相册：浅玉绿辅助目录感，刚更新和精选提示有温暖状态，但不变成暖红卡片堆。
4. 生活：入口温和清晰，纪念或同步提示能被看见，关键动作仍回到浅蓝主操作。
5. Viewer：深色沉浸独立成立，浮层不干扰照片，记忆色不进入大面积背景。

最终结果应该是安静、亲密、可反复进入的产品 UI。它可以有明确记忆点，但不能靠炫彩背景、深色按钮、过度玻璃或暖色泛滥支撑视觉秩序。
