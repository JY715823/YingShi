# 映世交互动效插件与工具使用指南 v1

Updated: 2026-06-05

这份文档记录当前本机与 Codex 环境里和交互、动效、视觉推演相关的工具。结论先放前面：映世是 Jetpack Compose Android App，生产代码不应该直接引入 GSAP。GSAP 这组 skill 的价值主要在两个方向：一是做 Web/HTML 动效原型，二是把它的动效方法论转译成 Compose motion token、`Animatable`、`AnimatedVisibility`、`graphicsLayer`、`alpha` 和状态驱动的 UI helper。

## 1. 当前环境结论

### 1.1 已确认存在的 GSAP skill

本机 `C:\Users\10850\.codex\skills` 下有 8 个 GSAP skill：

| Skill | 本地路径 | 当前用途 |
| --- | --- | --- |
| `gsap-core` | `~/.codex/skills/gsap-core/SKILL.md` | GSAP 基础 tween、easing、stagger、reduced motion |
| `gsap-timeline` | `~/.codex/skills/gsap-timeline/SKILL.md` | 多步骤动效编排、label、播放控制 |
| `gsap-plugins` | `~/.codex/skills/gsap-plugins/SKILL.md` | Flip、Draggable、Observer、SplitText、SVG、CustomEase 等插件 |
| `gsap-scrolltrigger` | `~/.codex/skills/gsap-scrolltrigger/SKILL.md` | Web 滚动驱动、pin、scrub、batch |
| `gsap-performance` | `~/.codex/skills/gsap-performance/SKILL.md` | 动效性能、transform/opacity、避免 layout thrash |
| `gsap-utils` | `~/.codex/skills/gsap-utils/SKILL.md` | clamp、mapRange、normalize、interpolate、snap、wrap |
| `gsap-react` | `~/.codex/skills/gsap-react/SKILL.md` | React/Next 中的 `useGSAP`、ref、cleanup |
| `gsap-frameworks` | `~/.codex/skills/gsap-frameworks/SKILL.md` | Vue/Svelte/Nuxt 生命周期、作用域和清理 |

注意：当前 Codex session 的可触发 skill 列表没有暴露这 8 个 GSAP skill。它们的文件已经安装，但未必会自动按名字触发。实用做法是新开线程或重启 Codex 后再确认；在当前线程里，也可以明确说“参考 `~/.codex/skills/gsap-core/SKILL.md`”。

### 1.2 已确认的 5 个设计/交互推演 skill

`AI-Design-Tools/configs/tool-matrix.json` 里记录的 5 个主力设计 skill 是：

| Skill | 当前状态 | 适合做什么 |
| --- | --- | --- |
| `brainstorming` | 位于 `~/.codex/.tmp/plugins/plugins/superpowers/skills/brainstorming`，但不在当前 session 可触发 skill 列表中 | 大功能开工前做需求、范围、方案、设计文档 |
| `ui-ux-pro-max` | 当前 session 可触发 | 查风格、配色、字体、移动端 UX 规则、设计系统候选 |
| `design-taste-frontend` / `taste-skill` | 当前 session 可触发 | 审美校准，主要偏 Web/landing/redesign，避免 AI 默认模板味 |
| `impeccable` | 当前 session 可触发 | 产品 UI 审核、动效、布局、可读性、像素级 polish，最适合映世这种 App UI |
| `prototype` | 当前 session 可触发 | 做一次性原型，验证布局、状态机、交互路径，不直接当生产代码 |

`brainstorming` 当前不是稳定启用状态。`~/.codex/config.toml` 的 `[plugins]` 中没有启用 `superpowers@openai-curated`，所以它更像“插件缓存里有，但当前环境没有正式暴露”。如果后面要长期使用它，应在 Codex 插件面板或 CLI 里启用 superpowers，或者把该 skill 正式安装到 `~/.codex/skills/brainstorming`。

### 1.3 映世项目自己的现状

映世已经不是空白设计阶段，项目内已有较明确的产品与设计系统：

- `PRODUCT.md` 已定义产品是双人私密相册与生活记录 App，默认 register 是 `product`。
- `docs/design/ui/03-color-system-v1.1.md` 已锁定 `珍珠玉蓝 · 温暖记忆点缀版`，Viewer 使用独立深色 token。
- `app/src/main/java/com/example/yingshi/ui/theme/Tokens.kt` 已有 `YingShiMotion`，包括 tap、state、floating、viewer notice、comment preview、memory glow、media fade 等参数。
- `YingShiSharedTransitions.kt` 已有 `rememberYingShiMotionEnabled()`、`yingShiMediaEnterMotion()`、`yingShiSoftReveal()`、`yingShiMemoryGlow()`。
- `PhotoFeedScreen.kt` 已有 `PhotoFeedAtmosphereLayer`、新导入/恢复/定位高亮、扫光、多选态缩放和暗层。
- `PhotoViewerScreen.kt` 已有 `ViewerNotice`、`ViewerNoticeHost`、评论预览浮层、Viewer 深色背景与 overlay 显隐动画。

所以当前重点不是“找一个动画库重做”，而是把已有 Compose 动效系统继续收口、补齐、统一。

## 2. GSAP 8 个 skill 怎么用

### 2.1 `gsap-core`

核心能力：

- `gsap.to()`、`gsap.from()`、`gsap.fromTo()`、`gsap.set()`。
- duration、delay、ease、stagger、repeat、yoyo、overwrite。
- transform alias：`x`、`y`、`scale`、`rotation`、`xPercent`、`yPercent`。
- `autoAlpha` 同时处理 opacity 和 visibility。
- `gsap.matchMedia()` 支持响应式和 `prefers-reduced-motion`。

在映世里怎么用：

- 用于做 HTML/React 动效草稿，比如 Viewer 轻提示、评论浮层、照片打开 fallback motion。
- 不直接进入 Android 生产代码。
- 转译到 Compose 时，对应 `animateFloatAsState`、`Animatable`、`tween`、`FastOutSlowInEasing`、`graphicsLayer`、`alpha`。

推荐请求：

```text
参考 gsap-core，帮我做一个 Viewer 轻提示 Web 动效原型，只验证 alpha、scale、y 位移和 reduced motion，不改 Android 代码。
```

### 2.2 `gsap-timeline`

核心能力：

- `gsap.timeline()` 组织多个 tween。
- position parameter：`"<"`、`">"`、`"+=0.2"`、label。
- timeline defaults、暂停、反转、seek、嵌套 timeline。

在映世里怎么用：

- 用于推演“Viewer 入场：背景先建立、图片显现、顶栏和底栏延迟浮现”这种顺序感。
- Compose 生产实现不需要 timeline 库，可以用同一个 `visible` 状态配合不同 duration/delay，或用 `LaunchedEffect + Animatable` 明确编排。
- 避免在 App 里做过长 page-load choreography。产品 UI 常规过渡应控制在 150-320ms。

推荐请求：

```text
用 gsap-timeline 给我做 3 套 Viewer 入场节奏原型，目标 320ms 内，输出时间轴参数，方便我转成 Compose token。
```

### 2.3 `gsap-plugins`

核心能力：

- ScrollToPlugin、ScrollSmoother。
- Flip：列表/卡片位置变化的 FLIP 动效。
- Draggable、Inertia、Observer。
- SplitText、ScrambleText。
- DrawSVG、MorphSVG、MotionPath。
- CustomEase、EasePack、CustomWiggle、CustomBounce。
- Physics2D、PhysicsProps、GSDevTools。

在映世里怎么用：

- 最有参考价值的是 Flip、Observer、CustomEase。
- Flip 可用于 Web 原型里验证“缩略图到 Viewer”的感觉，但生产上本轮已明确不再使用 Compose shared transition，也不建议把 JS FLIP 思路硬塞进 native。
- Observer/Draggable 适合验证手势原型，但 Android 生产应使用 Compose 手势系统。
- SplitText、ScrambleText、DrawSVG、MorphSVG 对映世主 App 价值低，不建议用于照片流/Viewer。
- Bounce、Elastic、Wiggle 和物理插件不符合“轻雾蓝、安静、克制”的方向，除非是非常局部的 playful 实验。

推荐请求：

```text
参考 gsap-plugins 里的 Flip，只做一个 Web 原型验证缩略图打开 Viewer 的节奏。不要把方案要求落实到 Android 生产代码。
```

### 2.4 `gsap-scrolltrigger`

核心能力：

- scroll-linked animation、trigger start/end、toggleActions。
- `ScrollTrigger.batch()`。
- scrub、pin、markers、timeline + ScrollTrigger。
- scrollerProxy、自定义滚动容器。

在映世里怎么用：

- 主要用于 Web/HTML 长页原型，不适合 Android 照片流生产代码。
- Compose 的 Lazy grid/list 不应引入复杂 item placement 动画；中端安卓优先稳定滚动。
- 对映世照片流可借鉴的是“按可见范围、批量、低成本”的思想，而不是 ScrollTrigger 本身。

推荐请求：

```text
用 gsap-scrolltrigger 做一个设计说明页的滚动演示，不用于映世 Android 照片流生产代码。
```

### 2.5 `gsap-performance`

核心能力：

- 优先动画 transform 和 opacity。
- 谨慎使用 `will-change`。
- 避免读写混杂造成 layout thrash。
- 大量元素使用 stagger/batch，减少同时工作量。
- scroll 动效注意 refresh、batch、可见范围。

在映世里怎么用：

- 这是 8 个 GSAP skill 里最能直接转译到 Compose 的。
- 对应映世原则：照片流大列表只做 `alpha / graphicsLayer / background overlay / AnimatedVisibility`，不改 item 布局尺寸，不加持续无限动画。
- Viewer 内浮层动效可使用 alpha、scale、轻微 y 位移，但不要触发布局重算。

推荐请求：

```text
参考 gsap-performance，审核 PhotoFeedScreen 的动效是否只动 alpha/graphicsLayer/background overlay，并列出会影响 Lazy grid 滚动的风险。
```

### 2.6 `gsap-utils`

核心能力：

- `clamp()`、`mapRange()`、`normalize()`、`interpolate()`。
- `random()`、`snap()`、`shuffle()`、`distribute()`。
- `getUnit()`、`unitize()`、`splitColor()`。
- `toArray()`、`selector()`、`pipe()`、`wrap()`。

在映世里怎么用：

- 可转译成 Kotlin 小工具，用于手势、滚动进度、缩放、拖拽、scrubber、评论浮层位移。
- 例如 Viewer scrubber 拖动进度可以使用 `coerceIn`、线性映射、snap 到页面索引。
- 不建议把随机动效用于真实照片流，映世需要稳定、可预期。

推荐请求：

```text
参考 gsap-utils，把 Viewer 底部 scrubber 的拖动 progress 映射规则整理成 Kotlin helper 方案，要求可测试、无随机。
```

### 2.7 `gsap-react`

核心能力：

- React/Next 里使用 `@gsap/react` 的 `useGSAP()`。
- ref scope、依赖更新、cleanup、contextSafe。
- SSR 安全。

在映世里怎么用：

- 只用于 `AI-Design-Tools` 里的 Web 原型、screenshot-to-code 草稿或 OpenUI sample。
- 不适用于 Compose 生产代码。
- 如果做 Viewer/照片流动效原型，优先使用 React + GSAP，再把节奏参数转译到 Compose。

推荐请求：

```text
用 gsap-react 做一个只读 Viewer 动效原型页，三种 variant 可切换，最后输出 Compose 可用的 duration/easing/alpha/scale 参数。
```

### 2.8 `gsap-frameworks`

核心能力：

- Vue、Nuxt、Svelte、SvelteKit 里的 mounted/unmounted 生命周期。
- `gsap.context()` 作用域选择器和 cleanup。
- lazy load plugin。

在映世里怎么用：

- 当前映世主线几乎用不到，除非后续做 Vue/Svelte 设计原型站。
- 可作为“组件卸载必须清理动画”的通用提醒。

推荐请求：

```text
如果我要用 Vue/Svelte 做一个设计原型，请参考 gsap-frameworks 写生命周期和清理方式。
```

## 3. 5 个设计/交互 skill 怎么用

### 3.1 `brainstorming`

核心能力：

- 先查项目上下文，再逐步问清目标、约束、成功标准。
- 提供 2-3 个方案和取舍。
- 写设计文档到 `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`。
- 再交给 implementation plan。

当前状态：

- 文件存在于 superpowers 插件缓存：`~/.codex/.tmp/plugins/plugins/superpowers/skills/brainstorming/SKILL.md`。
- 当前 session 没有把它列为可触发 skill。

在映世里怎么用：

- 适合“大功能”前置，比如重新设计 Life、聊天记录 Viewer、账本流程、相册重构。
- 不适合每个小 polish 都强制走一遍，否则会拖慢收尾。
- 如果要恢复稳定使用，优先启用 superpowers 插件或把 skill 安装到顶层 `~/.codex/skills/brainstorming`。

推荐请求：

```text
用 brainstorming 帮我收敛“聊天记录 Viewer 的交互与动效”，先写设计规格，不要直接改代码。
```

### 3.2 `ui-ux-pro-max`

核心能力：

- 内置 `colors.csv`、`styles.csv`、`typography.csv`、`ux-guidelines.csv`、`app-interface.csv`、`products.csv` 等资料。
- 可以查移动 App 模式、风格候选、UX 准则、图表和字体建议。
- 有脚本可搜索本地 CSV。

在映世里怎么用：

- 用来做“参考库检索”，不是直接改映世颜色系统。
- 映世颜色必须以 `03-color-system-v1.1.md` 为准。
- 对动效有用的方式是查移动 App、图片 Viewer、底部操作、评论、状态反馈、空状态等规则。

推荐请求：

```text
用 ui-ux-pro-max 查移动端照片 Viewer、评论预览、轻提示的 UX 规则。只输出映世可用建议，不改颜色 token。
```

### 3.3 `design-taste-frontend` / `taste-skill`

核心能力：

- 反模板、反 AI 默认审美。
- 强调设计读题、颜色克制、布局节奏、字体纪律、按钮对比、移动端适配。
- 更偏 landing、portfolio、redesign、Web 前端。

在映世里怎么用：

- 对 Android 产品 UI 只能部分参考，不是主力。
- 可用于审核 HTML/React 原型是否有 AI 味、是否过度玻璃拟态、是否重复卡片、是否颜色跑偏。
- 映世是 product UI，不能套它偏营销页面的 hero、bento、Awwwards 逻辑。

推荐请求：

```text
用 design-taste-frontend 审一下这个 Viewer Web 原型有没有 AI 味和过度装饰，只给审美风险，不改 Android。
```

### 3.4 `impeccable`

核心能力：

- 产品 UI 审核、动效、polish、布局、可读性、颜色、组件状态。
- 对 product register 有明确规则：动效服务状态，不做装饰；150-250ms 常规过渡；一致组件词汇；可读性优先。
- 支持 `animate`、`polish`、`audit`、`layout`、`colorize`、`harden` 等方向。

在映世里怎么用：

- 这是当前最适合映世的 skill。
- 可用于审查 `PhotoViewerScreen`、`PhotoFeedScreen`、`AppShell` 等实际 Compose 界面。
- 对动效的输出应落到 `YingShiMotion`、`YingShiInteractions`、`ViewerNoticeHost`、`PhotoFeedAtmosphereLayer` 等已有结构。

推荐请求：

```text
用 impeccable animate 审核 PhotoViewerScreen 和 PhotoFeedScreen，只聚焦现有 Compose 动效、可访问性和中端安卓性能。
```

### 3.5 `prototype`

核心能力：

- 做一次性原型，用来回答一个具体问题。
- UI 原型默认 3 个 variant，可用 `?variant=` 切换。
- 原型必须可删除或吸收到生产代码，不应长期留在 repo。

在映世里怎么用：

- 适合在 `AI-Design-Tools/outputs` 或独立 Web sandbox 中做 Viewer/照片流动效 variant。
- 也适合做逻辑原型，比如多选状态、导入后定位、高亮消失节奏。
- 不建议在 Android app 生产路由里长期保留 prototype switcher。

推荐请求：

```text
用 prototype 做 3 个 Viewer 评论预览层动效 variant：A 轻上浮、B 左下贴边、C 底部抽屉。只做可删除 Web 原型。
```

## 4. 相关工具链怎么配合

虽然本问题重点是 GSAP 和 5 个 skill，但 `AI-Design-Tools` 里还有一条对映世设计很有用的链路：

| 工具 | 角色 | 映世适用方式 |
| --- | --- | --- |
| MeiGen | 同风格生图 | 生成 Viewer、照片流、登录页等视觉方向图 |
| screenshot-to-code | 截图/原型图转 HTML/React 草稿 | 把 MeiGen 图或手绘稿变成可交互 Web 草稿 |
| Chrome DevTools MCP | 浏览器检查 | 看 HTML 原型真实响应式、控制台、截图 |
| html.to.design | Web 回 Figma | 把网页草稿导回 Figma 做图层整理 |
| Tokens Studio | Figma token 管理 | 把映世颜色、间距、圆角整理成 Figma 变量 |
| Material Theme Builder | Material 3 色彩辅助 | 仅用于参考 M3 role，不覆盖映世 v1.1 token |
| Material 3 Design Kit | Android 组件参考 | 对齐 Compose 控件结构、触控尺寸、状态 |
| Material Symbols | 图标参考 | 统一 Android 图标语言 |

推荐流程：

1. 用 `impeccable` 或 `brainstorming` 收敛目标。
2. 用 `ui-ux-pro-max` 查规则和候选方向。
3. 用 MeiGen 出 2-4 张氛围方向图。
4. 用 screenshot-to-code 或 prototype 做 Web 交互草稿。
5. 如果是动效草稿，可以在 Web 里用 GSAP。
6. 用 Chrome DevTools MCP 截图检查。
7. 需要设计稿时用 html.to.design 回 Figma。
8. 最后只把已验证的 motion 参数和交互逻辑转成 Compose。

## 5. 映世里能用到什么

### 5.1 可以直接落地到 Android Compose 的东西

这些不是“使用 GSAP 代码”，而是把工具产出的结论转成 Compose：

- 统一 motion token：把所有点击、状态、浮层、Viewer notice、comment preview、memory glow 都从 `YingShiMotion` 读取。
- 轻提示系统：把 Viewer 内 Toast 继续迁到 `ViewerNoticeHost`，并按 `mediaId` 绑定。
- 照片流反馈：多选、导入、恢复、定位高亮保持 `graphicsLayer`、`alpha`、局部 overlay。
- 低频氛围层：沿用 `PhotoFeedAtmosphereLayer`，不做高成本 blur，不做持续无限动画。
- reduced motion：继续使用 `rememberYingShiMotionEnabled()`，关闭位移和扫光，只保留必要 alpha 或直接显示。
- 性能规则：大列表不做 layout size 变化，不在每个 item 上跑无限动画，不在滚动时启动复杂路径/粒子/blur。

### 5.2 适合先做 Web/HTML 原型再转译的东西

- Viewer 入场节奏。
- 评论预览层位置、透明度、边缘光强度。
- 新导入/恢复扫光节奏。
- 照片流密度切换的视觉节奏。
- 多选编号浮现和取消选择回落。
- 原图加载轻提示的文案、时长、强调态。

### 5.3 不建议做的东西

- 不把 GSAP、React、Vue、Svelte 引进 Android App 生产代码。
- 不恢复本轮已经放弃的 `SharedTransitionLayout/sharedElement/sharedBounds`。
- 不做 ScrollTrigger 式原生照片流滚动动效。
- 不做 Viewer 里 SplitText/ScrambleText 之类文字炫技。
- 不做 bounce、elastic、wiggle、physics 类夸张动效。
- 不在照片卡片上铺彩色大底，不用记忆色做普通卡片装饰。
- 不在 Lazy grid item 上做持续无限动画或高成本 blur。

## 6. 对当前代码的具体判断

### 6.1 已经做对的部分

- `YingShiMotion` 已经有完整的基础 token，不需要另起一套。
- `rememberYingShiMotionEnabled()` 已经尊重系统动画开关。
- `PhotoFeedAtmosphereLayer` 已经是低强度背景氛围，没有抢照片主体。
- `yingShiMemoryGlow()` 和 `MemoryStatusSweepOverlay` 符合“状态驱动、一次性、高亮结束后不持续运行”的方向。
- Viewer 内 `ViewerNoticeHost` 已经实现了按 `currentMediaId` 过滤，能避免快速左右滑时串提示。
- 评论预览层已经使用雾夜蓝半透明层、细边和弱 `viewerAccent` 光感。

### 6.2 当前值得继续推进的缺口

- `YingShiInteractions.kt` 仍使用固定 `YingShiTapMillis = 170`，可以改为读取 `YingShiMotion.tapMillis` 和 `pressedScale`。
- `PhotoViewerScreen.kt` 还有若干 Viewer 内 Toast：删除、删除失败、时间修改、评论操作提交等，应逐步迁到 Viewer notice 或对应 sheet 内提示。
- `PhotoFeedScreen.kt` 还有定位/导入相关 Toast，可做照片流局部 notice 或顶部轻提示。
- `ViewerNotice` 当前是 `private data class`，只服务 Viewer。这个范围是对的，但后续可提炼成同文件内更清晰的 message type，避免到处塞裸字符串。
- `ViewerCommentPreviewLayer` 高度固定为 `172.dp`，后续需要在小屏和横屏检查是否挡住主体。
- 现在的 motion token 没有细分 delay。若后续继续打磨 Viewer 顶栏/底栏/评论预览节奏，可以增加 `overlayDelayMillis`、`noticeExitMillis` 等内部 token。
- `PhotoFeedAtmosphereLayer` 是常态背景层，强度应维持低频，不要继续叠多个背景光效。

## 7. 建议的下一阶段计划

### P0：当前照片流与 Viewer 稳定精修

- 统一 `YingShiInteractions` 到 `YingShiMotion`。
- 把 Viewer 内剩余 Toast 迁到 `ViewerNoticeHost` 或 sheet 内轻提示。
- 把照片流导入/恢复/定位结果 Toast 迁到照片页局部 notice。
- 复查 `showCommentPreview`、`viewerNotice`、original load state 是否全部按 `mediaId` 绑定。
- 做 reduced motion 手动检查：关闭系统动画后，扫光、位移、缩放应降级。

### P1：做一次动效原型，不直接改生产代码

- 用 `prototype + gsap-core + gsap-timeline` 做 Viewer 入场和评论预览 3 个 variant。
- 输出一张 motion 参数表：duration、delay、alpha、scale、y offset、easing。
- 只采纳参数和状态逻辑，删除原型代码。

### P2：建立映世 motion 文档

- 新增 `docs/design/ui/04-motion-system-v1.md`。
- 固化 token、使用场景、禁止项、reduced motion、测试清单。
- 把 `03-color-system-v1.1.md` 的 token 与 motion 场景关联起来：Viewer 用 `viewerAccent`，新导入/恢复/未读用 `memoryAccent/memoryContainer`。

### P3：扩展到其他页面

- 等照片流和 Viewer 稳定后，再处理系统媒体 Viewer、相册详情、生活页、聊天记录 Viewer。
- 每个页面只补状态动效，不做全局大改。

## 8. 日常使用话术

### 要做设计收敛

```text
用 impeccable + ui-ux-pro-max 帮我审一下映世照片流的交互体验，必须遵守 docs/design/ui/03-color-system-v1.1.md，不要改代码，先给问题清单和优先级。
```

### 要做动效原型

```text
用 prototype 和 GSAP 做一个 Viewer 评论预览动效原型，3 个 variant，可切换，目标是提取 Compose 可用的 motion 参数，不写进 Android 生产代码。
```

### 要检查性能

```text
参考 gsap-performance 审核 PhotoFeedScreen 的动效性能风险，重点看 Lazy grid、graphicsLayer、alpha、background overlay、reduced motion。
```

### 要真正落地 Compose

```text
按 docs/design/motion-plugin-tooling-guide-v1.md 的 P0 计划推进，只改 PhotoViewerScreen、PhotoFeedScreen、YingShiInteractions 和必要 token，不引入 GSAP，不恢复共享转场。
```

### 要启用 brainstorming

```text
请先检查 brainstorming skill 是否在当前 session 可触发；如果不可触发，说明如何启用 superpowers 或把 skill 安装到 ~/.codex/skills/brainstorming。
```

## 9. 最终原则

映世的动效目标不是“看起来用了很多动画插件”，而是让用户感觉：

- 点击有反馈。
- 照片打开不突兀。
- 评论浮层出现有位置感。
- 原图加载状态不打断沉浸。
- 新导入、恢复、未读、评论新增这些“记忆状态”能被温和看见。
- 关闭动画或在中端安卓上使用时，体验仍稳定。

GSAP 可以帮助我们把动效想清楚、做原型、提炼参数；Compose 才是映世生产实现的主场。
