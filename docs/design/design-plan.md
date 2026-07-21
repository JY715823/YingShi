设计计划
为映世App进行全面的UI深度精修，覆盖App的全部主要界面——包括首页、照片模块（照片流/相册/帖子详情/全屏查看器）、生活模块（生活入口/控制台/聊天列表/聊天详情）、记账模块（账本首页/添加交易/统计页）、我的模块（个人主页/资料编辑/设置页）、登录页，以及全局底部导航栏和通用组件系统（弹窗/输入框/按钮/状态提示）。在保持珍珠玉蓝色系不大改的前提下，通过三个维度提升品质：材质光影（磨砂玻璃、金属边线、丝绸渐变光泽）、视觉丰富度（更精致的装饰元素、更清晰的层次分隔、更有温度的色彩表达）、排版设计感（更有个性的字体组合、更大胆的标题对比）。最终效果应该是：安静但有华彩、克制但有温度、精致但不炫技——每一个页面都像精心布置的私密小窝的一角，让人想反复进入。请重点审核：1）整体视觉方向是否符合'高级但不张扬'的预期；2）颜色系统是否在保持珍珠玉蓝基调的同时足够丰富；3）全部页面是否风格统一、组件一致。

.design.json
契约
产物
app-ui
平台
mobile
输出
react-vite
组件库
shadcn
保真度
high-fidelity
风格
Custom luxury direction: quiet-luxury meets intimate-warmth. Keeping the 珍珠玉蓝 color palette but elevating every surface with material textures (frosted glass, metallic borders, silk gradients), richer visual layering, and bolder typographic contrast. Every screen should feel like a page from a beautifully designed personal journal.
产物
01
react-vite/src/App.jsx
创建
映世App全界面UI精修主入口，包含所有主要页面和模块的完整实现，支持Tab切换和页面间导航
实现全部主要页面模块（见structure.screens），每个页面都是高保真设计
底部导航栏全新设计：磨砂玻璃质感、精致边线光效、重新设计的选中态和中间按钮
页面间支持点击导航：从首页进入照片/账本、从生活页进入记账/聊天/痕迹、从我的页进入设置/资料等
覆盖层页面（overlay screens）使用滑入动画，带返回按钮
底部弹窗（bottom sheets）使用上滑动画，带遮罩层
使用Google Fonts加载有设计感的中英文字体组合
所有卡片和组件使用多层阴影、微妙渐变、精致边线实现高级质感
页面背景加入微妙的极光/水光纹理效果
使用CSS变量实现颜色系统，方便后续调整
移动端优先，390x844视口
02
react-vite/src/styles.css
创建
全局样式：颜色系统CSS变量、字体定义、材质光影效果、动画系统、所有通用组件样式
定义完整的珍珠玉蓝颜色系统CSS变量（包括所有语义token和Viewer深色token）
定义字体系统：标题用有设计感的serif或display字体，正文用精致的sans-serif
定义材质效果：磨砂玻璃(backdrop-filter)、金属边线(渐变border)、丝绸光泽(gradient)、水光纹理
定义多层阴影系统：卡片阴影、浮层阴影、按钮阴影、overlay阴影
定义动画系统：页面切换滑入、卡片悬浮、按钮按压、渐入效果、弹窗出入
定义通用组件样式：卡片、按钮（primary/secondary/ghost/danger）、Badge、Pill、输入框、分割线、Avatar、Icon Bubble、Tag
03
react-vite/canvas-design.html
创建
Canvas预览入口HTML
引入React/Vite构建产物
设置viewport为mobile尺寸
包含设计元数据
资源选择
技能
ID: mobile-app
理由: mode matches prototype; trigger: android app; platform: mobile
风格参考
主参考: figma
灵感参考: linear-app, stripe
必读资料
skill / mobile-app / Selected Canvas skill checklist.
design-system / figma / Primary style reference.
craft / anti-ai-slop / Baseline AI-slop guardrail.
craft / typography / Typography quality rule.
craft / color / Color quality rule.
protocol / design-mode-runtime / Runtime contract.
protocol / skill-protocol / Skill protocol.
结构
首页 Home: 聚合入口页。顶部品牌标题+通知铃铛，中间是照片回忆大卡（照片拼贴+数据覆盖层），下方是账本信号卡（月度支出+最近一笔），底部留白。背景是珍珠浅蓝极光纹理。
照片流 Photo Feed: 照片模块主页面。顶部有子Tab（照片流/相册/回收站），主体是按日期分组的全量媒体流，每个日期组有日期标题和媒体网格。支持点击媒体进入全屏查看器。
相册 Albums: 相册目录页。大相册横滑卡+小相册网格，每个相册有封面、标题、媒体数量。点击相册进入帖子详情。
照片查看器 Photo Viewer: 沉浸式全屏媒体查看器。全屏幕照片/视频展示，顶部和底部覆盖层控制栏，支持缩放手势和下滑关闭。覆盖层使用Viewer独立深色系统。
帖子详情 Post Detail: 小相册（帖子）详情页。展示相册信息（标题、描述、时间范围）、媒体网格、评论区、操作按钮。
生活页 Life: 生活模块入口。顶部时间问候+日期，中间三个入口卡（记账/聊天记录/今日痕迹），每个卡有独立的色彩主题和图标。背景是浅玉绿 mist 纹理。
生活控制台 Life Console: 今日生活总结页。顶部是今日概览（天气、日期、心情），中间是今天的生活片段网格（照片、视频、笔记），底部是历史时间线。
聊天记录列表 Chat List: 导入的聊天对话列表。每个聊天有摘要卡、类型标签、消息数量。
聊天详情 Chat Detail: 单个聊天对话视图。消息时间线，按天分组，支持边缘手势导航。
账本首页 Ledger Home: 记账模块主页面。顶部是月度摘要头（收入/支出/结余），中间是按日分组的交易列表，支持月份切换和快速操作。
添加记账 Add Transaction: 添加/编辑交易表单。金额输入、分类选择、账户选择、日期时间、备注，带计算器输入。
账本统计 Ledger Stats: 统计页面。图表展示收入/支出分类、时间趋势。
我的页 Me: 个人空间页。顶部品牌+标题，中间是身份卡（头像+昵称+简介）、伴侣卡、工具列表（设置/缓存管理）、账号状态卡。背景是mist纹理。
个人主页 Profile: 个人资料详情页。展示完整个人信息（头像、昵称、简介、账号等），支持编辑。
设置页 Settings: 应用设置页面。分类：空间、账号、调试。链接到缓存管理、后端诊断、退出登录。
登录页 Login: 登录/注册页面。用户名、密码输入框，服务器设置，登录按钮。品牌展示区域。
底部导航栏 Bottom Navigation: 全新设计的底部导航栏。4个tab（首页/照片/生活/我的）+中间添加按钮。
通用组件系统 Shared Components: 贯穿全App的通用组件设计系统：底部弹窗样式、对话框样式、表单输入样式、加载状态、空状态、Toast通知等。
交互
用户打开App → 登录页（如未登录） → 首页照片回忆和账本摘要 → 底部导航切换：照片模块（照片流/相册/回收站 → 帖子详情 → 照片查看器） / 生活模块（生活控制台/聊天列表 → 聊天详情/记账 → 添加记账/统计） / 我的模块（个人主页 → 设置/资料编辑）
Tab选中/未选中态
卡片按压态（微缩+阴影变化）
按钮hover/active态
页面切换过渡动画（overlay滑入/滑出）
底部弹窗出入动画（上滑/下滑+遮罩淡入/淡出）
照片查看器覆盖层显隐动画
列表项渐入动画（staggered）
空状态/加载态切换
质量
Generated artifact is responsive and has no obvious text overlap or clipped primary controls.
Primary actions, input controls, empty/loading/error states, and key content are visible and grounded in the brief.
Design quality check has P0=0 and all P1 issues are fixed or explicitly waived.
React/Vite output is runnable through the Canvas design runtime and keeps editable source markers.
珍珠玉蓝色系主体不变，所有核心颜色token保持可识别
字体从Material默认升级为有设计感的字体组合
底部导航栏有全新的视觉设计
所有卡片和组件有明显的质感提升（多层阴影、微妙渐变、精致边线）
所有页面背景有材质纹理效果（极光/水光/mist）
整体视觉比现有设计更丰富、更有层次、更有高级感
覆盖全部主要页面模块：首页、照片（流/相册/查看器/帖子）、生活（入口/控制台/聊天）、记账（首页/添加/统计）、我的（主页/资料/设置）、登录
页面间导航可点击跳转，overlay和bottom sheet有动画效果
通用组件系统一致：所有弹窗、输入框、按钮、badge风格统一