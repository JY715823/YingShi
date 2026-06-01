# 映世 App 浅色系颜色系统细化

Updated: 2026-05-27

本文档是 `ui-design-v2.md` 的浅色系细化补充，专门回答以下问题：

- 在保留“纪念、记录、共同生活”气质的前提下，如何把整体配色做得更清透、更明亮
- 如何让`页面空白区域`也参与配色，而不是只在按钮和标签上见到颜色
- 如何让浅色主系统、较深蓝色结构色、绿色辅助分区和香槟金点缀形成一整套可复用的规则
- 如何为 Stitch 提供可以直接测试的完整提示词，并验证这套 UI 在`非配套内容`下是否仍然成立

## 1. 总方向

本轮固定方向如下：

- 主方向：`奶雾清透`
- 全局主背景：`浅蓝大底`
- 主色关系：`浅蓝主导 + 绿在照片页和生活页都常用 + 香槟金轻点缀`
- 深蓝用途：仅用于信息密度高页面、标题/图标强调、主按钮和局部承托
- 选中态形状：顶栏/底栏统一用`圆形或圆角圆片承托`
- Viewer：继续深色沉浸，但改为更轻的`雾夜蓝`体系，不再使用过重旧深蓝

本轮不要求立即改 Android 代码；优先产出`可直接试稿、可直接落 token、可直接指导后续实现`的设计规范。

## 2. 全局共用规则

### 2.1 页面背景

- 所有浅色页面默认使用 `appBackground` 作为整页壳层背景。
- 页面空白区、滚动区、卡片外留白、顶栏下留白、底栏上留白，全部继续吃到 `appBackground`，不允许退回纯白。
- `raisedSurface` 只用于真正抬升的信息卡片、正文块、设置项卡片，不承担整个页面背景角色。

### 2.2 结构色关系

- `selectedPillBg` 是浅蓝圆片承托底，用于顶栏/底栏当前态和少量轻量胶囊状态。
- `titleAccent` 是较深蓝色结构色，用于标题、图标、当前态文字、信息密度较高区域。
- `primaryAction` 是主按钮色，必须比 `selectedPillBg` 明显更深，避免浅蓝按钮发飘。
- `softGreenContainer` 是浅绿容器，只负责辅助分区、生活感模块、相册和工具容器，不可抢走蓝色主导地位。
- `goldAccent` 只用于日期、精选标签、数字摘要、局部装饰线和极少量状态点亮。

### 2.3 Viewer 深色规则

- Viewer 背景绝不使用纯黑。
- Viewer 主背景使用偏雾感的深蓝灰，和浅蓝体系保持连续性。
- Viewer 高光使用低亮浅蓝，不使用高饱和荧光蓝。
- 香槟金在 Viewer 里只保留极少量点缀，例如页码高光、少量状态点、装饰分隔。

### 2.4 内容适配测试规则

所有 Stitch 测试必须满足以下条件：

- 一次至少输出 `风格板 + 5 个页面`
- 页面中的示例图片不得刻意匹配蓝、绿、金色系
- 示例内容优先使用自然混合素材：
  - 人物日常
  - 室内
  - 食物
  - 街景
  - 旅行
  - 小物件
  - 植物
  - 宠物
- 示例文案、标签、统计数字不得围绕配色写
- 验收重点不是“图和 UI 刚好同色”，而是“普通真实内容放进去后，UI 依然成立”

## 3. Token 语义

后续无论是 Stitch 试稿还是 Android 落地，都统一按这组语义命名：

| Token | 含义 | Android 建议映射 |
| --- | --- | --- |
| `appBackground` | 整页浅蓝壳层背景 | `colorScheme.background` |
| `sectionBackground` | 分区背景、列表块、浅色承托层 | `colorScheme.surfaceVariant` |
| `raisedSurface` | 抬升卡片、正文卡片、设置项卡片 | `colorScheme.surface` |
| `selectedPillBg` | 顶栏/底栏当前态圆片承托底 | 新增设计 token |
| `primaryAction` | 主按钮、主 CTA | `colorScheme.primary` |
| `primaryActionPressed` | 主按钮按压态 | 新增设计 token |
| `titleAccent` | 标题、图标、当前态文字、结构强调 | `colorScheme.onSurface` 之外的自定义强调色 |
| `softGreenContainer` | 生活页/相册页辅助浅绿容器 | 新增设计 token |
| `goldAccent` | 日期、精选标签、数字高光 | 新增设计 token |
| `dividerSoft` | 弱分割线、边界、输入框线 | `colorScheme.outline` |
| `viewerBackground` | Viewer 雾夜蓝底色 | Viewer 自定义 token |
| `viewerAccent` | Viewer 浅蓝高光 | Viewer 自定义 token |
| `viewerSurface` | Viewer 浮层、评论预览、操作面板背景 | Viewer 自定义 token |

## 4. 方案 A：奶雾天青（主推）

### 4.1 气质

- 最温柔、最耐看
- 最适合“纪念 + 共同生活”
- 清透但不过冷
- 页面颜色存在感明确，但不过分跳

### 4.2 精确色值

| Token | Hex |
| --- | --- |
| `appBackground` | `#EDF9FF` |
| `sectionBackground` | `#DFF4FC` |
| `raisedSurface` | `#FCFEFD` |
| `selectedPillBg` | `#BDEFFF` |
| `primaryAction` | `#248FB3` |
| `primaryActionPressed` | `#1B7694` |
| `titleAccent` | `#176D90` |
| `softGreenContainer` | `#E3F8D7` |
| `goldAccent` | `#E9CC81` |
| `dividerSoft` | `#D6ECF5` |
| `viewerBackground` | `#182A35` |
| `viewerAccent` | `#9FDFF6` |
| `viewerSurface` | `#223845` |

### 4.3 页面级配色分工

- 照片主页：
  - 整页使用 `appBackground`
  - 时间分组容器使用 `sectionBackground`
  - 顶栏/底栏当前态使用 `selectedPillBg + titleAccent`
  - 深蓝仅用于图标、当前态标题、重点按钮
  - 绿色仅少量进入筛选区和辅助块

- 相册页 / 帖子目录页：
  - 保持 `appBackground`
  - 相册组块、帖子目录块可增加 `softGreenContainer`
  - 日期和精选态使用 `goldAccent`

- 帖子详情页：
  - 页面底色仍为 `appBackground`
  - 正文区使用 `raisedSurface`
  - 媒体区和评论预览区可混用 `raisedSurface` 与 `sectionBackground`
  - 元信息、标题和图标使用 `titleAccent`

- 生活页：
  - 整页仍为 `appBackground`
  - 功能入口卡片更高比例使用 `softGreenContainer`
  - 主 CTA、入口按钮和状态图标使用 `primaryAction`
  - 数字摘要和小高光使用 `goldAccent`

- 我的 / 设置 / 通知：
  - 页面壳层继续用 `appBackground`
  - 信息卡用 `raisedSurface`
  - 局部状态或分组提示可以使用 `softGreenContainer`
  - 标题、账号状态、重点信息使用 `titleAccent`

- Viewer：
  - 底色使用 `viewerBackground`
  - 浮层和评论预览使用 `viewerSurface`
  - 高光图标、页码、轻状态使用 `viewerAccent`
  - 金色只用于极少量重点状态

### 4.4 Stitch 测试提示词

```text
为一款中文 Android app 设计 1 张风格板 + 5 张高保真移动端页面。主题是“纪念、记录、共同生活”，整体气质要清透、明亮、奶雾感、温柔、有高级感，但不要深沉、不要企业风、不要普通白底模板。

全部界面文案使用简体中文。

请使用以下颜色系统，并且让颜色明显体现在整页背景、空白区域、分区背景、按钮、导航、标签、评论区和内容卡片上，而不是只放在小图标里：
appBackground #EDF9FF
sectionBackground #DFF4FC
raisedSurface #FCFEFD
selectedPillBg #BDEFFF
primaryAction #248FB3
titleAccent #176D90
softGreenContainer #E3F8D7
goldAccent #E9CC81
dividerSoft #D6ECF5

页面大背景必须是浅蓝色体系，不允许大面积纯白留空。页面空白区、滚动区、卡片外留白、顶部和底部留白都必须参与浅蓝背景设计。顶栏和底栏当前态使用圆形或圆角圆片的浅蓝承托。主按钮使用较深蓝色，不要用最浅蓝做主按钮。绿色用于辅助分区和生活感容器，香槟金只做轻点缀。

请生成以下 5 个页面：
1. 照片主页：顶部有“照片 / 相册 / 回收站”，右上角有通知和系统媒体，主体是按时间分组的照片网格。整页浅蓝底，时间分组和筛选区域有浅蓝分区承托。
2. 相册页：相册目录或相册下的帖子列表，比照片主页更柔和，可加入浅绿容器，但大背景仍然是浅蓝。
3. 帖子详情页：包含封面图、标题、日期、相册标签、正文、媒体区域、评论预览。正文区、媒体区、评论区必须拆成不同浅色层级，不要一页白到底。
4. 生活页：包含“记账”和“聊天记录查看器”两个入口。生活页可更偏浅绿分区，但整页基础底仍是浅蓝。
5. 我的 / 设置页：安静、可信、层次清楚，但不能单调。使用浅蓝底、暖白卡片、少量浅绿状态块。

示例图片不要刻意选择蓝绿金色调。请使用普通真实内容：人物日常、室内、街景、食物、植物、旅行、小物件、宠物等，颜色自然混合。示例文案和数据也不要围绕配色写。

整体要求：背景色必须有存在感，颜色系统必须肉眼可见，但仍然高级、轻盈、耐看，不要赛博风、玻璃拟态、荧光色和营销海报感。
```

## 5. 方案 B：晨雾水感（更透、更亮）

### 5.1 气质

- 更接近参考图的通透感
- 更轻、更亮、更偏水感青
- 适合先在 Stitch 中快速看“亮度上限”
- 风险是更容易发飘，需要深蓝压结构

### 5.2 精确色值

| Token | Hex |
| --- | --- |
| `appBackground` | `#F1FBFF` |
| `sectionBackground` | `#E4F7FF` |
| `raisedSurface` | `#FEFFFE` |
| `selectedPillBg` | `#C9F2FF` |
| `primaryAction` | `#1C9FC8` |
| `primaryActionPressed` | `#167F9F` |
| `titleAccent` | `#15749A` |
| `softGreenContainer` | `#E8FCDD` |
| `goldAccent` | `#EDD58D` |
| `dividerSoft` | `#D9EEF6` |
| `viewerBackground` | `#162B36` |
| `viewerAccent` | `#A7E8FF` |
| `viewerSurface` | `#213844` |

### 5.3 页面级配色分工

- 照片主页：
  - 用最亮的浅蓝背景测试整体通透感
  - 时间组块和筛选组块使用更淡的 `sectionBackground`
  - 深蓝主要用于标题、图标、主按钮和当前态

- 相册页 / 帖子目录页：
  - 在浅蓝大背景上加入更少量、更轻的浅绿容器
  - 避免绿色面积过大导致页面发散

- 帖子详情页：
  - 正文区用接近白但不纯白的 `raisedSurface`
  - 元信息、标签、状态使用 `titleAccent` 和极轻 `goldAccent`

- 生活页：
  - 绿色参与比照片页更高
  - 仍需要让浅蓝背景持续可见，避免整页变成浅绿工具页

- 我的 / 设置 / 通知：
  - 优先用浅蓝大底和极轻暖白卡片，保持干净
  - 绿色只作点状辅助

- Viewer：
  - 雾夜蓝保持轻薄，不做厚重夜幕
  - 强化 `viewerAccent`，减弱金色存在感

### 5.4 Stitch 测试提示词

```text
设计一套中文 Android app 的 1 张风格板 + 5 张高保真页面，主题是“纪念与共同生活记录”。整体要更清透、更亮、更有空气感，接近晨雾和水感，但不能做成没有结构的浅色模板。

全部界面文案使用简体中文。

请使用以下颜色系统：
appBackground #F1FBFF
sectionBackground #E4F7FF
raisedSurface #FEFFFE
selectedPillBg #C9F2FF
primaryAction #1C9FC8
titleAccent #15749A
softGreenContainer #E8FCDD
goldAccent #EDD58D
dividerSoft #D9EEF6

要求所有页面的大背景都使用浅蓝色，不允许纯白大面积留空。空白区域、滚动背景、卡片外留白都必须参与颜色设计。选中态使用圆形或圆角圆片浅蓝承托。主按钮使用更深蓝。绿色用于辅助分区，香槟金只做轻点缀。

请生成以下页面：
1. 照片主页
2. 相册页
3. 帖子详情页
4. 生活页
5. 通知中心或设置页

示例图片和数据不要刻意贴合蓝绿金色系。请使用普通真实内容：人物、室内、街景、食物、植物、旅行、小物件、宠物等。我要测试这套 UI 在非配套内容下的适配度。

视觉要求：比普通极简 app 更有背景色和分区存在感，但仍然清透、轻盈、明亮，不要营销页、赛博风、玻璃拟态、深色块主导。
```

## 6. 方案 C：晴空果雾（更年轻、更有颜色）

### 6.1 气质

- 更年轻、更活泼
- 蓝和绿存在感更强
- 更容易在 Stitch 里做出“明显有颜色系统”的结果
- 风险是更容易花，需要严格限制金色面积

### 6.2 精确色值

| Token | Hex |
| --- | --- |
| `appBackground` | `#EAF8FF` |
| `sectionBackground` | `#D5F0FD` |
| `raisedSurface` | `#FEFEFB` |
| `selectedPillBg` | `#B7ECFF` |
| `primaryAction` | `#1F97C2` |
| `primaryActionPressed` | `#18799E` |
| `titleAccent` | `#0F6F95` |
| `softGreenContainer` | `#DAF6C9` |
| `goldAccent` | `#E6C56E` |
| `dividerSoft` | `#CDE7F2` |
| `viewerBackground` | `#172834` |
| `viewerAccent` | `#96DDF7` |
| `viewerSurface` | `#233A48` |

### 6.3 页面级配色分工

- 照片主页：
  - 蓝色分区和浅蓝背景层次更明显
  - 适合测试“颜色存在感是否足够”

- 相册页 / 帖子目录页：
  - 绿色容器可以比 A/B 更大胆一点
  - 但标题和结构仍由深蓝压住

- 帖子详情页：
  - 评论区和摘要区可以更明显拆层
  - 需要注意避免过多彩色块同时出现

- 生活页：
  - 绿色使用量最高，但大底仍是浅蓝
  - 数字和时间点缀少量金色，防止整页过淡

- 我的 / 设置 / 通知：
  - 保持浅蓝底，不要突然回白
  - 绿色辅助块更克制，避免工具页也显得太跳

- Viewer：
  - 用略更有张力的雾夜蓝
  - 让浅蓝高光更明显，但仍避免赛博感

### 6.4 Stitch 测试提示词

```text
为一款中文 Android app 设计 1 张风格板 + 5 张高保真页面。主题是“照片、纪念、生活记录”，整体要比普通极简 app 更有颜色、更有层次，但同时保持清透、明亮、轻盈和高级。

全部界面文案使用简体中文。

请使用以下颜色系统：
appBackground #EAF8FF
sectionBackground #D5F0FD
raisedSurface #FEFEFB
selectedPillBg #B7ECFF
primaryAction #1F97C2
titleAccent #0F6F95
softGreenContainer #DAF6C9
goldAccent #E6C56E
dividerSoft #CDE7F2

强制要求：
所有页面主背景必须是浅蓝色大底。
按钮、卡片、标签之外的空白区域也要有设计，主要通过浅蓝背景、浅蓝分区承托和浅色层级完成。
顶栏和底栏当前态使用圆片浅蓝承托，文字和图标用更深蓝。
绿色用于辅助分区和生活感模块，金色只做极少量时间与状态点缀。

请生成以下页面：
1. 照片主页
2. 相册页
3. 帖子详情页
4. 生活页
5. 我的 / 设置页

页面中的示例图片不要故意选成蓝绿金调，不要让图片配合界面颜色。请使用普通真实混合内容：人物、街景、食物、旅行、小物件、植物、宠物、室内等。示例文案和数据也不要刻意围绕颜色系统写。

整体效果要一眼看出“浅蓝主背景 + 绿分区 + 金点缀”的完整系统，但不要荧光、不要深色主导、不要玻璃拟态、不要网红营销海报感。
```

## 7. 页面落地建议

如果下一轮开始进入 Android 实现，建议顺序如下：

1. 先把 `Theme.kt` 和颜色 token 扩展成“浅蓝壳层 + 浅蓝分区 + 深蓝交互 + Viewer 雾夜蓝”四层体系。
2. 先替换页面壳层背景和底栏/顶栏当前态，不要一开始就全量重刷所有卡片。
3. 第二步再统一改：
   - 我的 / 设置 / 通知
   - 生活页入口
   - 照片模块外壳和分区层
4. 最后再收 Viewer、评论预览层、帖子详情这些更精细的层级。

## 8. 验收清单

以下 7 条同时成立，说明这套浅色系统合格：

1. 页面空白背景明显参与设计，而不是退回纯白。
2. 顶栏/底栏当前态的浅蓝圆片清楚、轻盈、有记忆点。
3. 页面颜色丰富时，深蓝仍能稳住结构而不显重。
4. 照片页、生活页、我的页有气质分工，但仍像同一 app。
5. 示例图片不配色时，页面仍协调。
6. 金色只做轻点缀，没有变成廉价主色。
7. Viewer 深色与整套浅色系统连贯，不再像旧方案那样突兀。
