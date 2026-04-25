# 映世 App UI 设计总稿（阶段性定稿版）

> 版本：v2.0  
> 日期：2026-04-18  
> 性质：基于**最新相册模块 PRD（重构版 v2）**与此前多轮 UI 讨论整理的阶段性 UI 设计总稿  
> 用途：作为后续页面出稿、Jetpack Compose 落地、视觉统一、交互细化的母文档

---

# 1. 文档说明

## 1.1 本稿作用

本稿用于系统整理目前已经讨论并基本收口的 UI 方向，重点解决两个问题：

1. **这个 App 整体应该长成什么样**；
2. **在新版相册 PRD 重构后，页面的风格分工该如何重新站稳**。

本稿不是单纯的“审美总结”，而是后续真正做界面时的统一依据。它会覆盖：

- 全局设计系统
- 全局导航与壳层
- 相册模块在新版 PRD 下的视觉与风格分工
- 系统媒体工具区的独立气质
- 页面间的风格温度差
- 后续 UI 细化时的默认标准方案

---

## 1.2 本稿与上一版的关系

上一版 UI 设计总结建立在旧相册 PRD 和旧讨论上，当时的核心倾向是：

- 照片页更偏轻帖子流
- 帖子评论与媒体评论的关系更接近混流思路
- Viewer 仍保留较强的图片下方信息行思维
- 系统媒体入口权重较弱

而新版相册 PRD 已经重构了以下核心口径：

- **照片页改成 app 内容体系内的全局媒体流**
- **评论拆成帖子评论 / 媒体评论两套，不再混流**
- **Viewer 改成沉浸式边缘操作层 + 评论预览层 / 完整评论区双层结构**
- **系统媒体升级为显眼的独立工具区**
- **一个媒体可以属于多个帖子**
- **删除语义分叉更加明确**

因此，本稿以**新版相册模块 PRD 为准**，并吸收此前所有仍然有效的整体风格讨论，形成新的统一总稿。

---

## 1.3 适用范围

本稿当前优先适用于：

- 相册模块
- 全局设计系统
- 全局导航与壳层
- 生活模块的基础风格方向（仍以此前讨论为准）

对于 Files 模块：

- 之前关于“精简版网盘气质、结构借百度网盘但更干净克制”的讨论仍然成立；
- 但由于当前最新 PRD 重点重构的是相册模块，因此本稿**不再把 Files 模块展开为本次主讨论对象**；
- 后续若 Files PRD 也发生较大调整，应另做一次针对性收口。

---

# 2. 产品整体定位与 UI 总方向

## 2.1 产品定位

映世不是一个纯效率工具，也不是一个强社交内容平台，而是一个以**两人共同长期使用**为核心的个人空间产品。

它承载的内容本质上有三类：

1. **照片 / 视频 / 帖子 / 评论 / 记忆内容**
2. **未来仍可能保留的文件管理能力**
3. **生活类功能入口**（当前暂定：纪念日、记账、聊天记录查看器）

因此，它的 UI 不能走：

- 纯办公工具风；
- 强社区 feed 风；
- 甜腻奶油恋爱风；
- 炫技型概念稿风。

它应该是：

> **有温度，但不甜腻；有审美，但不花哨；有沉浸感，但不牺牲效率。**

---

## 2.2 总体风格定义

当前阶段已经比较明确的总风格可概括为：

# **温柔纪念型 + 极简骨架 + 局部沉浸特效**

进一步凝练后的内部命名建议为：

# **映世 · 轻雾蓝设计系统**

这套系统不是简单的“蓝色主题”，而是由四层共同构成：

### （1）颜色层
- 主色：清透蓝 / 映世蓝
- 辅助色：雾青灰 / 空气蓝 / 轻灰蓝
- 中性色：月白 / 云灰 / 雾白 / 墨灰
- 深色沉浸色：深蓝黑 / 夜幕蓝黑

### （2）结构层
- 全局壳层统一
- 一级导航稳定
- 页面骨架极简
- 少分割线，主要靠留白、表面层级、字重层级建立秩序

### （3）气质层
- 安静
- 温柔
- 纪念感
- 中性克制
- 长期耐看

### （4）动效层
- 追求顺滑、柔和、沉浸、舒适
- 做局部高级感，不做全局炫技

---

## 2.3 风格边界

### 可以有的感觉
- 温柔
- 纪念感
- 轻沉浸
- 安静呼吸感
- 一点雾感与光感

### 不应该有的感觉
- 少女粉嫩
- 过重 ins 滤镜感
- 过多装饰和插画
- 霓虹感、游戏感、播放器感
- 太厚的玻璃、太强的光效、太多“看起来很设计”但不耐用的元素

一句话概括：

> **先把页面身份做对，再决定风格强度。**

---

# 3. 设计系统（标准默认方案）

## 3.1 颜色系统

### 主色策略
首版先采用：

- **默认品牌色：映世蓝**
- 后续可扩展：跟随系统 / 动态色

建议不要在首版同时铺开太多主题色，避免系统尚未稳定就进入配色细调泥潭。

### 颜色角色建议

#### Primary（品牌蓝）
用于：
- 顶部当前导航项强调
- 关键按钮
- 当前状态高亮
- 重要可点击动作

#### Secondary（雾青灰 / 蓝灰）
用于：
- 次级按钮
- 筛选态
- 弱交互高亮
- 更低优先级的交互面

#### Tertiary（保留少量点缀）
用于：
- 生活模块轻情绪表达
- 少量状态点缀

#### Neutral / Surface
用于：
- 大面积背景
- 信息白区
- 卡片底
- 系统状态块

### 用色原则
- 不靠“大面积彩色块”制造设计感；
- 不把整 app 刷成一种蓝；
- 蓝色主要承担品牌识别、选中态、轻强调和沉浸区气质支撑。

---

## 3.2 字体层级

### 总原则
- 不使用花哨字体
- 不依赖字体本身制造风格
- 靠字号、字重、字色、留白建立气质

### 风格倾向
- 页面标题：中性、现代、稳定
- 内容标题：清楚、克制
- 正文与说明：可读优先
- 辅助信息：弱化但不模糊

### 当前阶段建议
- 顶部标题式导航的当前项略大、略重、略深
- 非当前项略小、略淡，但不要夸张到像两个层级完全不同的系统
- 相册帖子标题不要太大，防止压过封面
- Files 文件名（若后续继续沿用原方向）应更偏稳定和清楚，不可文艺化

---

## 3.3 图标风格

### 建议风格
# **线性图标为主，局部填充高亮**

### 原则
- 默认状态：线性图标，中等线重
- 当前选中：可稍加重或轻填充
- 避免不同模块图标语言割裂

### 不建议
- 相册像 Google / Files 像 iOS / 生活像插画库，三套混搭
- 当前项为了“好看”换成完全另一种图标体系

---

## 3.4 圆角与形状

### 已确定方向
- 圆角强度：**中等克制**

### 解释
- 不做偏幼态的大圆角奶油风
- 也不做过直、过硬、过工具化
- 让相册、Viewer、工具区都能统一在同一套形状语言下

### 落地建议
- 内容卡 / 信息白区：中等圆角
- 底部导航：贴底、紧凑、轻边界
- 评论预览层：中等偏柔，但不厚
- 工具区按钮：偏收一点

---

## 3.5 表面层级

### 层级优先级
1. **留白与间距**
2. **表面明度差**
3. **轻边界 / 轻阴影**
4. **玻璃 / 光雾 / 模糊**

### 原则
- 玻璃感不是基础层级语言，只是点睛语言
- 不要用一堆毛玻璃解决层级问题
- 相册 / 内容区靠“轻雾白表面 + 间距”即可建立高级感

---

## 3.6 动效与反馈

### 总原则
# **追求“顺”，不追求“秀”**

### 必须做好的动效
- 页面切换淡入 / 滑动过渡
- 查看态进入 / 退出
- 评论区弹出与收起
- 顶部当前导航项切换时的光雾移动 / 淡入
- 长按进入多选

### 可以后续补的动效
- 更复杂的共享元素转场
- 更细腻的景深 / 模糊层跟随
- 更明显的呼吸感和悬浮感

### 反馈风格
建议统一为：
# **软反馈**

也就是：
- 轻微缩放
- 轻明暗变化
- 不要硬闪一下
- 面板像“浮上来”，不是机械弹出

---

# 4. 全局导航与壳层

## 4.1 当前全局一级导航

基于新版相册 PRD，当前照片相关全局底栏确定为：

`主页 / 照片 / 生活（暂定）`

说明：
- 三项固定
- 不因照片模块内页面切换而变化
- Files 模块当前不再纳入这版相册 PRD 的底栏规划

---

## 4.2 底部导航风格

### 已定方向
# **贴底紧凑表面底栏**

### 特征
- 贴近屏幕底部
- 整体高度更紧凑
- 不做明显脱离背景的悬浮感
- 不做厚重 iOS 式浮空胶囊
- 不做强玻璃、强阴影、强漂浮

### 当前项样式
- 图标与文字变为品牌蓝
- 背后只有非常淡的局部强调
- 不是强胶囊，不是大面积发光
- 整体更像稳稳贴在底部的导航条，而不是浮层

### 角色定位
- 顶部导航负责表达“当前页面与章节”
- 底部导航负责稳住全局结构
- 因此底部当前项只做轻强调，不应喧宾夺主

---

## 4.3 照片模块顶部二级导航

进入“照片”后，顶部采用标题式二级导航：

`照片 / 相册 / 回收站`

### 当前项风格
- 明显，但克制
- 背后是**横向细长光雾底**
- 更像章节标题被点亮，不像普通 tab 被填色

### 同一行的角色分工
当前口径里，照片模块顶部采用**单行结构**：

#### 左侧
- `照片 / 相册 / 回收站`
- 负责内容主导航
- 整体占据这一行左侧的大约一半多一点空间

#### 右侧
- `系统媒体` 轻主按钮
- 通知铃铛
- 在 `照片 / 相册 / 回收站` 三个二级页中都显示
- 负责工具入口，但不抢左侧标题导航的章节感

补充要求：

- `照片 / 相册 / 回收站` 必须保持横向排布，不允许逐字换行形成竖排
- 左侧二级导航与右侧工具区要处于同一水平线，视觉上像一套完整顶部栏
- 顶部整体要更贴近状态栏下沿，显得紧凑、正式，不要空出过大的上边距
- 当前二级页标题更大、更重、更清楚；非当前标题更小、更弱
- `系统媒体` 在顶部工具区先压缩为 `系统`
- 通知入口使用铃铛图标按钮
- `照片 / 相册 / 回收站` 三个二级主页支持左右滑切换
- 左右滑切换只限二级主页，不延伸到帖子详情页、Viewer、系统媒体等独立页面

---

# 5. 新版相册模块：整体风格总判断

新版相册模块和上一版最大的不同，不是“更好看了”，而是**页面身份重组了**。

旧方向更接近：
- 帖子化照片模块

新方向更接近：

# **系统相册式浏览 + 内容相册式沉淀 + 夜间沉浸式查看 + 工具化系统媒体区**

也就是说，同一个相册模块内部，现在要分出不同温度：

## 5.1 照片页
- 最轻
- 最克制
- 最像系统相册
- 强调媒体本体与时间感

## 5.2 相册页
- 更柔一点
- 更有内容感
- 承接轻帖子感

## 5.3 帖子详情页
- 更像上下文页
- 温和展开信息
- 承担帖子评论与帖子级操作

## 5.4 Viewer
- 最沉浸
- 最有氛围
- 深蓝黑 + 轻雾边缘操作层

## 5.5 系统媒体工具区
- 最工具化
- 最直给
- 主动降温
- 更接近系统相册整理区

这一点非常重要：

> **新版相册模块不是一个统一温度的单一空间，而是同一品牌系统下的多温度页面群。**

---

# 6. 照片页（全局媒体流）的 UI 风格

## 6.1 页面身份

照片页是 app 内容体系内的**全局媒体流**。

它的职责是：
- 让用户进入照片模块后先看到媒体本身；
- 提供最自然、最接近系统相册的浏览入口；
- 不在首屏展示复杂的内容关系。

因此，照片页的 UI 必须**退后**。

---

## 6.2 风格关键词
- 干净
- 轻
- 纯媒体
- 时间感
- 呼吸感
- 节奏感

---

## 6.3 标准视觉方案

### 页面背景
- 极淡雾白 / 微暖灰白
- 不用大块内容表面抢戏

### 顶部
- 单行顶部：左侧标题式二级导航，右侧工具入口（系统媒体 + 铃铛）

### 内容区
- 媒体网格本身是主角
- 日期标题要轻，不要厚重
- 网格间距比系统相册略精致一点，但不要“设计稿化”
- 不给媒体卡额外信息白区
- 开发阶段可先用轻量密度切换控件验收 `2 / 3 / 4 / 8 / 16` 列变化
- 当前阶段同时补基础双指缩放切换列数，让照片页密度调整更自然
- 密度变化后，时间分组层级也要跟着变化，而不是只改列数
- Time Scrubber 要像辅助定位层，而不是常驻工具面板
- Time Scrubber 的右侧交互区域应覆盖更完整的一整列纵向范围，日期气泡要跟随当前位置移动
- 多选模式先做轻量壳子，不要让操作栏压过媒体流主体
- 多选模式下，顶部切到上下文栏：左侧 `取消`，主体为 `已选 x 项`
- 多选模式下，全局底部导航保持不变，额外上下文操作栏出现在底部导航上方
- 返回行为的正式目标是优先退出多选态 / 查看态，再处理 app 退出；当前阶段只记录要求，不做完整返回系统改造

### 媒体卡
默认只展示：
- 缩略图

不额外展示：
- 评论图标
- 评论数
- 所属帖子
- 归属标记

### 原则
照片页的美，不该靠卡片装饰，而该靠：
- 缩略图阵列本身
- 留白
- 时间分组节奏
- 顶部单行结构的克制品牌感
- 密度切换时的整体秩序感

---

## 6.4 系统媒体按钮（照片模块顶部右侧）

### 形态
- 图标 + 文案 `系统媒体`
- 轻主按钮
- 圆角胶囊，但厚度偏薄

### 角色
- 重要工具入口
- 比普通图标显眼
- 但不能像主 CTA

### 不建议
- 太厚的胶囊
- 高饱和色块
- 强悬浮 / 强玻璃 / 强发光

正确感觉应该是：

> 看得见、点得到、不会忽略，但不会压过照片页本身。

---

# 7. 相册页（按相册浏览帖子）的 UI 风格

## 7.1 页面身份

相册页用于：
- 按相册浏览帖子
- 主题归档
- 内容回看

它不是默认浏览入口，也不是社交 feed，而是：

> **纪念册目录页 / 内容整理页**

---

## 7.2 风格关键词
- 轻帖子感
- 柔和
- 内容感
- 整理感
- 安静

---

## 7.3 标准布局建议

### 顶部
- 两行轻量 chips 作为相册切换区
- 右上角可有相册管理入口

### 主体
默认建议采用：
# **2 列轻帖子卡网格**

原因：
- 图片仍然是主角
- 能明显感知“这是帖子集合”
- 不会重得像内容社区
- 也不会轻得像普通相册网格

当前阶段补充：

- 相册页列数状态支持 `2 / 3 / 4`
- 相册页也补基础双指缩放切换列数
- 切到 `3 / 4` 列时，帖子卡仍保持封面 + 轻信息带 + 媒体数结构，只做紧凑化适配

---

## 7.4 帖子单元（标准默认方案）

### 结构
- 主体：大封面图
- 下方：很轻的信息带
- 右下角：半透明媒体数

### 信息带先只放
- 标题
- 一行摘要 / 简介
- 很轻的时间信息

### 右下角媒体数
- 半透明数字即可
- 很轻
- 主要是提醒“这不是单图，是帖子”
- 不做复杂多图堆叠感
- 不做大角标

Stage 4.1 落地时先保持最小可用 UI：

- 当前相册页主体先用 2 列轻帖子卡网格成立目录感
- 点击帖子卡先进入帖子详情占位页，重点验证入口和页面层级，不在本轮做完整详情页视觉展开
- Stage 4.2 起，点击帖子卡进入独立帖子详情页 v1，不再作为相册页内部普通块展示

### 总体感觉
像：
- 整理过的纪念册目录
- 以图为主，但明显是内容单元

不应像：
- 小红书式内容卡片
- 普通文件夹列表
- 社区 feed

---

# 8. 帖子详情页（Post Detail）的 UI 风格

## 8.1 页面身份

帖子详情页在新版结构中应被理解为：

# **内容上下文页**

而不是第二个 Viewer。

它负责：
- 看当前帖子的媒体序列
- 看标题、简介、帖子时间、所属相册
- 看帖子评论
- 做帖子级操作（例如加载全帖原图、编辑、导出/保存）

---

## 8.2 风格关键词
- 温和
- 安静
- 信息展开
- 上下文感
- 不沉重

---

## 8.3 页面结构（沿用新版 PRD）

从上到下：
1. 媒体区
2. 图片下方信息行
3. 信息白区
4. 帖子评论区

这一结构本身就决定了帖子详情页的风格应偏“阅读页 / 内容页”，而不是沉浸式查看页。

补充口径：

- 帖子详情页是独立页面，不继续挂在照片模块顶部二级导航之下
- 进入帖子详情页后，不再显示全局底部导航
- 进入帖子详情页后，不再显示照片模块顶部二级导航与右侧工具入口
- Stage 6.1 起，帖子详情页右上角齿轮进入独立的 Gear Edit 页面；Gear Edit 进入即编辑态，风格应比 Viewer 更工具化、更稳定。
- Gear Edit 本轮只编辑帖子标题、简介、显示时间和所属相册，媒体管理与删除语义先保留为清晰占位入口。
- Stage 6.2 起，Gear Edit 中的“媒体管理”进入独立媒体管理页；页面保持工具化、稳定，不显示主底栏和照片模块顶部栏。
- 媒体管理页本轮使用两列媒体网格，删除模式和排序模式先做壳子，设为封面需要本地立即生效，并尽量回写到帖子详情页与相册页的封面表现。
- Stage 6.3 起，删除模式需要先弹出目录删 / 系统删语义选择，不能点“删除（x）”后直接删掉。
- 若删除会让当前帖子变成空帖，界面应先做空帖保护，不允许留下空帖子；排序模式则保持“完成保存 / 取消回滚”的明确收口。

---

## 8.4 图片下方信息行

### 包含内容
- 左：当前媒体时间胶囊
- 右：评论图标 + 评论数小标志 + `加载原图 / 原件`

### 风格建议
- 比 Viewer 的操作层更明确一点
- 但仍然要克制
- 不做粗操作条
- 不做强按钮区

### 作用定位
- 承担当前媒体的快速操作
- 因为帖子详情页本身不是全屏沉浸查看态，所以此处保留“图片下方信息行”是合理的

---

## 8.5 信息白区

### 已定方向
- 用 **雾白 / 微暖灰白**
- 不做强玻璃感
- 不做纯冷白硬纸感

### 展示内容
- 作者 / 贡献者标识
- 标题 / 简介
- `post_display_time`
- 所属相册 chips
- `加载全帖原图`

### 视觉角色
- 像轻轻托住内容的一层纸面
- 是信息展开层，不是装饰层
- 让媒体区与评论区之间过渡更柔和

---

## 8.6 帖子评论区

### UI 原则
- 只显示帖子评论
- 不混入媒体评论
- 评论区比旧方案更干净

### 风格定位
- 平铺评论流
- 安静、可读
- 不做强社区化表达
- 不用媒体标记和跳转箭头制造信息噪音

---

# 9. Viewer（媒体查看态）的 UI 风格

## 9.1 页面身份

新版 Viewer 不是帖子详情的延长态，而是：

# **媒体本体页 / 沉浸式查看页**

它负责：
- 看媒体本体
- 看媒体评论
- 看媒体与帖子之间的关系入口
- 做当前媒体级操作

它不负责：
- 展开帖子整体信息
- 承担帖子评论
- 承担相册归档视角

---

## 9.2 风格关键词
- 深蓝黑
- 安静
- 沉浸
- 夜间翻照片感
- 边缘轻操作层

---

## 9.3 背景与整体氛围

### 背景建议
- 深蓝黑 / 夜幕蓝黑
- 不做纯黑
- 白字不要刺眼
- 分段白条亮，但不荧光

### 总体原则
# **图片永远是第一主角，所有 UI 都像附着在空气里。**

所以 Viewer 内所有元素都应：
- 边界更弱
- 表面更轻
- 贴边但不压画面
- 不像播放器 OSD
- 首个落地版本先把深色全屏骨架做对：顶部返回区、中部媒体画布、边缘轻操作层
- 不要退回成“图片下方厚信息行”的详情页思路
- 顶部和系统状态栏要看起来像同一整块深色区域

---

## 9.4 画布边缘操作层
补充口径（以下内容覆盖本节旧描述）：

- 顶部结构改为三段式：左上 `<` 返回标志，顶部中间轻量时间胶囊，右上设置按钮占位
- 顶部不再显示来源文案与页码提示
- 时间胶囊不再放在画布下方，而是作为顶部中间的轻信息元素
- 画布应以“居中 + 自适应比例 + 横向尽量铺满屏幕”的方式呈现，不保留卡片式左右留白
- 右下角“加载原图 / 所属帖子”继续留在原位置体系内

### 左下角
- 评论气泡按钮
- 评论数小标志

### 右下角
- `加载原图 / 原件`
- `所属帖子`

### 画布下方
- 时间胶囊

### 首个 Viewer Shell v1 落地要求
- 左下先建立评论气泡 + 评论数占位
- 画布下方建立时间胶囊占位，右下建立原图按钮占位、所属帖子按钮占位
- 所有操作层都应保持轻，不要做成播放器控制条或厚重工具栏
- 顶部只保留一个极简返回标志，不再放来源文案和页码提示

### 风格层级建议
#### 最轻
- 时间胶囊

#### 中间层
- 原图按钮
- 所属帖子

#### 最有存在感
- 评论气泡

原因：
- 评论是互动入口，需要略可感；
- 时间和关系入口不应显得像强操作按钮。

---

## 9.5 评论预览层（标准默认方案）
补充口径（以下内容覆盖本节旧描述）：

- 默认状态下不展开评论预览层，只显示评论气泡
- 第一次点击评论气泡：展开评论预览层
- 评论预览层已展开后，再点击评论气泡：收起评论预览层
- 只有点击预览层中的评论条目时，才进入完整评论区，并保留占位定位能力

### 当前确定的策略
- **有评论时**：默认显示预览层
- **无评论时**：只显示评论气泡，不显示预览层
- **点击预览层里的某条评论**：进入完整评论区并定位到该条
- **缩放 / 长图纵向浏览 / 横滑切媒体时**：预览层自动淡出
- **停止交互后**：若当前媒体有评论，预览层再轻量恢复

### 预览层视觉建议
- 黑色半透明
- 中等克制圆角
- 宽度不要铺满
- 高度可以比首版壳子更舒展，但仍要明显小于媒体画布，不要做成小评论区
- 内容较多时在预览层内部轻量滚动，不改变固定位置和轻量属性
- 位置上尽量贴近评论气泡，像从气泡向上发射出的评论窗
- 更像“漂浮评论窗”，而不是“半个弹层”

### 原则
评论预览层的存在，是为了让 Viewer 具备：

> “媒体本体 + 轻微陪伴感的记录层”

而不是让 Viewer 背一个默认小评论区。

---

## 9.6 完整评论区

### 风格原则
- 从底部弹出，占屏约 2/3 到 3/4
- 是深入查看和输入场所
- 风格要比预览层更清楚、更稳定
- 但不要做得像沉重面板
- 高度应高于评论预览层，让预览态到详情态的层级升级更明确
- 系统返回应先关闭完整评论区，再退出 Viewer

### 评论体系
- 只显示当前媒体评论
- 不混帖子评论
- 不做回复 / 楼中楼 / 引用

---

## 9.7 帖子内查看态与媒体流查看态

### 共通
- 画布边缘操作层尽量统一
- 评论预览层逻辑统一
- 右下角按钮组统一

### 差别
- 从照片页进入：左右滑切全局媒体流
- 从帖子进入：左右滑切同帖媒体
- 帖子内额外保留分段小白条
- 因此从照片页进入的首版 Viewer 不显示分段小白条，打开时应落在当前点击媒体对应的位置

### 分段小白条风格
- 应尽量轻
- 是节奏提示，不是视觉主角
- 与深色 Viewer 的整体雾感保持一致

---

# 10. 系统媒体工具区的 UI 风格

## 10.1 页面身份

系统媒体在新版相册 PRD 中已被明确为：

# **独立工具区**

它用于：
- 看系统媒体
- 挑选
- 发帖
- 加入已有帖子
- 移到系统回收站

它不用于：
- 评论
- 查看帖子上下文
- 使用 app 内容区的原图策略

---

## 10.2 风格关键词
- 工具化
- 直接
- 冷静
- 系统化
- 主动降温

---

## 10.3 标准默认方案

### 顶部
- 返回
- 标题 `系统媒体`
- 筛选 / 多选
- 不加欢迎区
- 不加统计区
- 不加额外说明卡

### 内容区
- 顶部下面直接进入时间流媒体网格
- 默认时间降序
- 视觉上尽量接近系统相册

### 网格卡片
- 默认只显示缩略图
- `已发帖` 只做极轻角标
- 不显示评论
- 不显示所属帖子
- 不显示原图状态

### 已发帖标记
已定方向：
- 角落一个很轻的小标记
- 不做大角标
- 不做彩色强提示
- 不打断系统媒体浏览体验

---

## 10.4 查看态

### 风格定位
- 更像系统相册查看器
- 比 app 内容区更工具化
- 少纪念感表达

### 不显示
- 评论入口
- `加载原图`
- `所属帖子`

### 只保留重点操作
- 发成新帖子
- 加入已有帖子
- 移到系统回收站

### 删除
- 单张删除：只弹一次系统确认
- 批量删除：只弹一次系统确认
- app 不额外叠加确认层

---

# 11. 删除与回收站的 UI 风格原则（只谈风格，不谈模型）

新版 PRD 中，删除语义已经分得很清楚，因此 UI 风格上必须做到：

# **让用户一看就知道自己删的是什么层级。**

---

## 11.1 照片页删除

### 风格定位
- 最普通、最直给
- 因为照片页本来就是全局媒体视角

### 建议
- 普通确认弹窗即可
- 明确写是“全局删除该媒体”
- 不做额外语义分支

---

## 11.2 帖子内删除媒体

### 风格定位
- 比照片页删除更复杂，但仍属于“单媒体删除”

### 建议
- 先出底部操作面板
- 让用户先选：
  - 从当前帖子移除
  - 系统删除该媒体
- 再确认危险操作

这样用户会感知到：
- 这里是“关系删除”与“本体删除”的分叉

---

## 11.3 删除整个帖子

### 风格定位
- 最危险
- 最不该和删媒体长得一样

### 已定标准方案
- 走专门删除选择层 / 强提示弹层
- 不是普通操作面板

### 原因
因为它是帖子级删除，并且可能连带媒体级全局影响，所以必须在风格上更“重”一点，让用户知道：

> 这是一个更高层级的删除动作。

---

## 11.4 回收站

### 风格原则
- 只读、克制、内容优先
- 列表项本身不做成操作面板
- 删除态详情尽量保持和正常页相似，但去掉编辑与导出入口

### 风格关键词
- 清楚
- 低噪音
- 语义明确

---

# 12. 生活模块（沿用此前讨论的基础方向）

> 说明：生活模块本次没有新版 PRD 重构，因此这里只保留此前讨论中仍有效的 UI 基线。

## 12.1 模块定位
当前暂定功能：
- 纪念日
- 记账
- 聊天记录查看器

它最适合在整个 app 中承担：

# **温柔卡片中枢**

---

## 12.2 风格关键词
- 温和
- 入口感
- 陪伴感
- 轻渐变
- 不复杂

---

## 12.3 当前默认方向
- 顶部不做二级导航
- 首页直接以**三张平权入口卡**为主
- 三张卡尺寸同等
- 卡片可以承载一点轻渐变与柔和高光
- 是整个 app 里最适合放“少量情绪化设计”的地方

---

# 13. Files 模块（沿用此前讨论的基础方向）

> 说明：Files 模块当前不在新版相册 PRD 的重构范围内，因此本稿只保留之前已经达成的总体气质，不做进一步细化。

## 13.1 总方向
- 结构参考百度网盘
- 但做精简版
- 借它的结构，不借它的商业噪音

## 13.2 气质关键词
- 清楚
- 稳
- 干净
- 私人化
- 不花哨

## 13.3 和相册模块的关系
- 共享同一设计系统
- 但 Files 比相册更工具化
- 比系统媒体区更有品牌感
- 比相册 Viewer 更少氛围表达

---

# 14. 后续落地建议

## 14.1 当前最合理的实施策略
不要一开始就把每个细节做得很定制。正确顺序应是：

### 第一步：先把页面身份做对
- 照片页轻
- 相册页柔
- 帖子详情页稳
- Viewer 沉浸
- 系统媒体区降温

### 第二步：再把标准版页面做出来
- 不急着花式玻璃
- 不急着做特别梦幻的预览层
- 不急着把每个按钮做成强定制组件

### 第三步：最后再“吹毛求疵”
等真实界面跑出来后，再逐步定制：
- 光雾强度
- 玻璃感强度
- 渐变比重
- 评论预览层梦幻程度
- 选中态个性程度
- Viewer 的氛围精修

---

## 14.2 首版最该优先做好的
1. 全局主题 Token（颜色 / 形状 / 字体层级）
2. 照片模块顶部单行结构
3. 相册页帖子单元
4. 帖子详情页信息白区与评论区
5. Viewer 的边缘操作层与评论预览层
6. 系统媒体工具区的冷静工具化风格

---

# 15. 最终结论

当前阶段，映世的 UI 方向已经可以明确总结为：

# **一个以轻雾蓝为品牌基调、以极简骨架为基础、在不同页面中呈现不同温度的个人空间产品。**

它不是一个统一温度的单一界面，而是：

- **照片页**：系统相册式浏览
- **相册页**：纪念册目录式内容沉淀
- **帖子详情页**：温和的上下文信息页
- **Viewer**：夜间沉浸式查看页
- **系统媒体工具区**：主动降温的整理工作台
- **生活模块**：温柔卡片中枢
- **Files 模块**：干净克制的私人云盘

这份总稿的价值，不在于它把所有视觉细节一次性钉死，而在于：

> **它已经把“不同页面到底该是什么气质”这件事，基本说明白了。**

后续真正做 UI 时，只要不偏离这些页面身份与温度分工，就可以在此基础上再自由精修和定制。

## Stage 4.3 implementation note

- 照片模块顶部栏视觉上应更贴近系统状态栏下沿；当前页标题突出、非当前标题弱化、右侧“系统 + 铃铛”同一行的结构保持不变。
- 照片页 / 相册页双指切列和二级主页左右滑切换先做轻量流畅性修正，后续精修阶段继续处理动画、手势阈值和列表状态细节。
- 帖子详情页媒体区应更像同帖媒体浏览区域，图片下方信息行保留普通态信息结构，不做成 Viewer 浮层。
- 帖子内查看态占位需要明确区别于照片页全局 Viewer：来源为当前帖子，未来左右切换范围为同帖媒体。

## Stage 4.4 implementation note

- 帖子内 Viewer 与照片流 Viewer 共用深色沉浸式查看语言、评论预览/详情入口和边缘操作层。
- 帖子内 Viewer 专属差异是同帖媒体范围、底部分段小白条，以及返回回到帖子详情页。
- 分段小白条应轻、短、低存在感，当前及之前段高亮，缩放时弱化或隐藏，不抢媒体主体。

## Stage 5.1 implementation note

## Stage 5.2 implementation note

- Post comment input and viewer media comment input both stay lightweight: plain text field, send action, empty content disabled, and local clear-after-send.
- Viewer media comment preview still only handles preview visibility; entering comment detail still depends on tapping a specific preview comment, and the detail sheet should reflect newly added media comments immediately.
- Comment lists keep the newest 10 items in the collapsed state and expose a minimal expand / collapse affordance before any real pagination exists.
- Overlay action offsets can diverge between photo-flow viewer and in-post viewer so the in-post segmented indicator keeps a clean bottom lane.

## Stage 5.3 implementation note

- Long press on a comment item should reveal a restrained first-level action menu rather than a heavy management panel.
- Comment edit may reuse the existing lightweight input style, and delete can stay immediate with light feedback as long as list refresh is instant.
- Selection can stay at the whole-comment-text level for now; only after a comment is marked selected should the copy action appear.
- The viewer comment preview layer should feel anchored to the comment bubble, while the bottom action group remains lower and still leaves the segmented indicator visible in the in-post viewer.

## Stage 5.4 implementation note

- The long-press menu should collapse into a compact inline floating menu attached to the current comment item instead of using a large modal surface.
- The inline menu keeps only four actions: copy, select, edit, and delete; no extra management entries should appear.
- The floating menu must not occupy comment-list layout height; it should sit as an overlay near the pressed comment.
- Comment selection should lean on Compose / Android native text selection behavior where possible, default to full-text selection on entry, and show only copy as the active operation while selecting.
- Entering text selection must dismiss the inline menu first, and dismissing text selection must fully clear the selection state so the next long press reopens the inline menu.
- Copy-full-text and copy-selected-text both stay lightweight and should exit selection mode after a successful copy.

## Stage 5.5 implementation note

- Comment selection should stay visually lightweight and read-only; entering selection should not summon the software keyboard.
- Keyboard appearance remains reserved for explicit edit state and bottom input bars.
- Post comment styling and media comment styling can differ by page tone, but their state boundaries should stay aligned: latest-first lists, latest 10 in collapsed mode, stable expand/collapse, and no cross-mixing of post/media targets.
- Viewer comment preview and detail should continue to feel like one layered flow: preview stays light and anchored, detail stays deeper but safe even when the requested highlight target is missing.
- Stage 7.1 keeps trash as a lightweight three-segment page in the photo module: deleted posts, removed media, and system-deleted media.
- Gear Edit post delete, media-management delete, and photo-page multi-select delete all write into the local in-memory trash model first.
- Restore details, 24h undo, and formal delete-detail pages stay deferred to Stage 7.2 and later.

## Stage 7.2 implementation note

- Trash detail is now a standalone full-screen page without the global bottom bar or the photos module top secondary tabs.
- Deleted-state detail should still feel close to normal browsing, but it is read-only and no longer exposes export, edit, or Gear Edit style actions.
- The top-right action set is reduced to exactly two actions: `恢复` and `移出回收站 / 删除`.
- Remove-from-trash feedback stays lightweight through a short snackbar plus a dedicated `24h 可撤销 / 待清理` entry inside the trash page, rather than a persistent bottom banner.
- Missing targets or missing delete snapshots should fall back to a safe empty state instead of crashing.

## Stage 7.2 targeted fix note

- 删除态详情在 UI 上优先渲染 trash snapshot / fallback 内容，不应因为正常页面数据已经移除而闪退。
- “24h 可撤销”从全局常驻底部提示调整为回收站页内入口或分区；移出回收站后只允许短暂 snackbar 提示，不长期占底部。
- `照片 / 相册 / 回收站` 二级导航点击后应直接平滑吸附到完整页面，不允许停在半屏状态。
- Time Scrubber、列数切换和二级页切换只做轻量局部优化，优先减少重复计算、重复滚动和状态源冲突。

- 帖子评论区保持普通内容页评论气质，只显示帖子评论，不混入媒体评论。
- Viewer 的评论预览层与评论详情区都只服务当前 `mediaId` 的媒体评论，并延续深色沉浸背景下的轻量层级。
- 评论气泡负责展开 / 收起预览层；预览层中的评论条目才是进入详情区的入口，详情区需要对被点击评论做清晰高亮。
- 帖子内 Viewer 的底部分段小白条应退到底部位置指示区，评论气泡、加载原图、所属帖子等操作按钮整体略上移，避免互相重合。
## Stage 8.1 implementation note

- `系统媒体` 使用独立页面壳层：顶部只保留返回、标题、筛选、多选，不显示底部导航，也不显示照片模块二级导航。
- 权限区先做三态壳子：`已授权 / 未授权 / 部分授权`，并保留 `重新申请权限` 与 `去系统设置` 的轻量入口占位。
- 主体保持偏工具区的时间流网格气质：fake system media、轻量 `已发帖` 标记、多选上下文操作条，以及不接真实系统删除。
- 系统媒体查看态接近系统相册查看器，只保留最必要的工具动作，不显示评论、原图加载和所属帖子信息。
- 从回收站详情页或 `24h 可撤销 / 待清理` 区返回时，应回到回收站并保持原来的回收站分段 / 待清理展开状态。
## Stage 8.2 implementation note

- `系统媒体` 页面不再先显示权限引导壳子，而是默认直接进入系统媒体主列表；权限问题仅在查询失败时退回到极简错误态。
- 主体从 fake grid 切到真实本地媒体缩略图：图片 / 视频混排、时间降序、uri 直接加载，视频先以缩略图或静态占位查看。
- 多选、`已发帖` 标记和系统工具动作继续保留，但这轮只升级查询与展示，不引入真实系统删除、真实发帖或评论能力。
## Stage 8.3 implementation note

- The main photo feed now confirms delete before media is written into app trash.
- System media multi-select and single-item viewer actions share the same lightweight local fake flows: create post, add to existing post, and simulated move to system trash.
- After `create post` or `add to existing post`, the system-media card should immediately show a lightweight `已发帖` marker and the `posted / unposted` filter should update.
- Simulated system trash is a local hide-only behavior inside the system-media tool area and should clearly avoid app-trash semantics.
- Smooth scrolling and state retention for the system-media tool remain follow-up work and should stay documented as known performance risk.
## Stage 8.3 targeted adjustment note

- `加入已有帖子` in system media should use a two-step picker: first choose an album, then choose a post under that album.
- The picker can stay lightweight, but it must support returning to the previous album step and must keep safe empty states for no albums or albums without posts.
- Post items in the second step should expose a lightweight cover, title, time, and media count so the target feels confirmable before the local fake import continues.
