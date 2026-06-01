# 映世首版颜色系统 v1.1

Updated: 2026-05-31

本文档把首版视觉方向里的主方案正式收成一套可直接出图、可直接进 Figma、可直接映射到 Android Compose 的颜色系统。

本版不追求一次性定义完整品牌色阶，而是先把首轮真正会用到的颜色层、页面分配规则、组件用色规则和 Viewer 深色规则定下来。

## 1. 本版目标

本版只解决 4 件事：

1. 统一首版主方向的语义 token
2. 统一不同页面该怎么分配颜色
3. 统一常用组件的固定用色
4. 给 Android + Jetpack Compose + Material 3 一个稳定映射口径

本版暂时不做：

- 完整 50 / 100 / 200 / 300 色阶
- 品牌插画扩展色
- 节日主题
- 动态主题
- 暗色全局主题

## 2. 总方向

首版固定方向：

- 主方向：`奶雾天青`
- 页面大底：`浅蓝壳层`
- 页面层次：`浅蓝分区 + 暖白抬升卡`
- 结构强调：`深蓝标题 / 深蓝主按钮`
- 辅助分区：`浅绿容器`
- 轻点缀：`香槟金`
- Viewer：`雾夜蓝深色沉浸`

要达到的效果不是“只有按钮有颜色”，而是整页空白区、滚动区、卡片外留白和分区承托都能看出完整系统。

## 3. 核心语义 Token

| Token | Hex | 作用 | Compose / Material 3 建议映射 |
| --- | --- | --- | --- |
| `appBackground` | `#EDF9FF` | 整页浅蓝壳层背景 | `colorScheme.background` |
| `sectionBackground` | `#DFF4FC` | 分区承托层、列表区、轻浅区块 | `colorScheme.surfaceVariant` |
| `raisedSurface` | `#FCFEFD` | 抬升卡片、表单卡片、正文卡片 | `colorScheme.surface` |
| `selectedPillBg` | `#BDEFFF` | 顶栏 / 底栏当前态圆片承托底 | 自定义扩展 token |
| `primaryAction` | `#248FB3` | 主按钮、主 CTA、关键确认动作 | `colorScheme.primary` |
| `primaryActionPressed` | `#1B7694` | 主按钮按压态、激活态 | 自定义扩展 token |
| `titleAccent` | `#176D90` | 标题、图标、结构强调文字 | 自定义扩展 token |
| `softGreenContainer` | `#E3F8D7` | 相册 / 小相册 / 生活模块辅助分区 | 自定义扩展 token |
| `goldAccent` | `#E9CC81` | 日期、精选标签、数字点缀 | 自定义扩展 token |
| `dividerSoft` | `#D6ECF5` | 浅分割线、边界、输入框默认边线 | `colorScheme.outlineVariant` 或扩展 token |
| `textPrimary` | `#183B4B` | 主文本、正文、列表标题 | `colorScheme.onBackground` 近似映射 |
| `textSecondary` | `#5F7D89` | 次文本、说明文字、辅助信息 | `colorScheme.onSurfaceVariant` 近似映射 |

## 4. 页面分配规则

### 4.1 登录页

- 整页背景使用 `appBackground`
- 表单容器使用 `raisedSurface`
- 主登录按钮使用 `primaryAction`
- 标题与关键图标使用 `titleAccent`
- 不能回到纯白开发面板感

### 4.2 主页

- 页面底色使用 `appBackground`
- 总览卡、快捷入口卡主用 `raisedSurface`
- 轻分区或最近回忆块可少量使用 `sectionBackground`
- 如果有共享空间摘要，可加入极轻 `softGreenContainer`

### 4.3 照片首页

- 页面底色始终是 `appBackground`
- 时间分组区、筛选区、密度切换承托区用 `sectionBackground`
- 顶栏和底栏当前态使用 `selectedPillBg + titleAccent`
- 深蓝主要用于标题、图标和关键动作
- 不给照片卡片加彩色大底，不做卡片流

### 4.4 相册页

- 页面底色继续用 `appBackground`
- 相册目录和小相册列表可以比照片页更多使用 `softGreenContainer`
- 目录列表外层承托可用 `sectionBackground`
- 小相册卡片主体仍以 `raisedSurface` 为主

### 4.5 小相册详情页

- 页面底色继续用 `appBackground`
- 标题、日期、结构信息由 `titleAccent` 负责
- 正文区和介绍区使用 `raisedSurface`
- 媒体区与评论预览区用 `raisedSurface` 和 `sectionBackground` 做轻拆层
- 所属相册、小标签、轻状态可少量用 `softGreenContainer` 或 `goldAccent`

### 4.6 生活页

- 页面底色继续用 `appBackground`
- `记账` 与 `聊天记录查看器` 入口卡允许明显使用 `softGreenContainer`
- 关键按钮仍必须回到 `primaryAction`
- 不允许整页刷成绿色工具页

### 4.7 我的页与设置页

- 页面底色使用 `appBackground`
- 主卡片使用 `raisedSurface`
- 分组提示或状态块可少量使用 `softGreenContainer`
- 重要信息、分组标题、入口图标用 `titleAccent`

### 4.8 Viewer

- 独立使用深色沉浸体系，不沿用浅色壳层
- 深色规则见第 6 节

## 5. 组件固定用色规则

### 5.1 按钮

- 主按钮：`primaryAction`
- 主按钮文字：白色
- 主按钮按压态：`primaryActionPressed`
- 次按钮：`raisedSurface + titleAccent`
- 描边按钮边框：`dividerSoft`
- 危险按钮不占本轮主视觉，只在局部用标准危险色

### 5.2 输入框

- 默认背景：`raisedSurface`
- 默认边框：`dividerSoft`
- 聚焦边框：`titleAccent`
- 主文本：`textPrimary`
- 占位提示：`textSecondary`
- 不使用纯白输入框漂在浅蓝大底上

### 5.3 卡片

- 主信息卡：`raisedSurface`
- 分区卡：`sectionBackground`
- 生活感辅助卡：`softGreenContainer`
- 卡片文字默认不使用纯黑，用 `textPrimary`
- 卡片边界优先靠层差和留白，不靠重阴影

### 5.4 顶栏与底栏

- 默认壳层继续吃到页面背景
- 当前态圆片：`selectedPillBg`
- 当前态图标 / 文字：`titleAccent`
- 非当前态文字：`textSecondary`
- 不做大面积深色导航条

### 5.5 标签、状态、摘要

- 日期、小摘要、精选态：可用 `goldAccent`
- 轻状态标签：优先用 `softGreenContainer`
- 蓝色优先负责结构，金色只负责少量点亮
- 金色不能进入主按钮、主导航和大面积卡片背景

## 6. Viewer 深色规则

| Token | Hex | 作用 |
| --- | --- | --- |
| `viewerBackground` | `#182A35` | Viewer 整体背景 |
| `viewerSurface` | `#223845` | 评论预览层、浮层、轻面板 |
| `viewerAccent` | `#9FDFF6` | 轻高光、当前页码、交互点亮 |
| `viewerText` | `#F3FAFD` | 主文字 |
| `viewerTextSecondary` | `#A9C3CF` | 次文字 |

Viewer 规则：

- 不用纯黑
- 不用荧光蓝
- 不做赛博夜店风
- 图片或视频永远是第一主角
- 浮层要薄，要轻，要像附着在空气里
- 金色只允许极少量点缀页码、日期或轻状态

## 7. Compose / Material 3 落地建议

首版建议的映射原则：

- `background` -> `appBackground`
- `surface` -> `raisedSurface`
- `surfaceVariant` -> `sectionBackground`
- `primary` -> `primaryAction`
- `outlineVariant` -> `dividerSoft`
- `onBackground` -> `textPrimary`
- `onSurfaceVariant` -> `textSecondary`

以下颜色建议作为扩展 token 保留在自定义设计系统里，而不是强行塞进默认 `ColorScheme`：

- `selectedPillBg`
- `primaryActionPressed`
- `titleAccent`
- `softGreenContainer`
- `goldAccent`
- `viewerBackground`
- `viewerSurface`
- `viewerAccent`

这能保证：

- 标准 Material 3 组件仍然可复用
- 页面不会被硬编码颜色写散
- 后续进 Figma 和 Compose 时口径一致

## 8. 首轮出图固定要求

无论是 `image2` 还是 Figma 手动搭稿，首轮都固定遵守：

- 页面大底必须看出浅蓝壳层
- 空白区也要参与配色，不回纯白
- 不做社交帖子流产品感
- 更适合“相册浏览 / 分类 / 进入小相册”
- 页面要真实能做，不做概念炫技稿
- 真实图片素材不需要刻意配合 UI 颜色

## 9. 使用顺序

推荐顺序：

1. 先用本文件锁住颜色系统
2. 再看 `01-core-page-specs.md` 确认页面边界
3. 再用 `02-image2-page-prompts.md` 跑单页原型
4. 选图后再进 Figma 做组件化和细修

首版默认方案：

- 主方案：`奶雾天青 · 目录感增强版`
- 备选方案：更轻更透的 `晨雾水感`
- 如果后续要强化目录结构，再从主方案向“蓝绿分区更明显”微调，不重新换品牌方向
