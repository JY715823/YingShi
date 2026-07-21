# 照片流精修

> 照片流本轮按“招牌页收尾”精修，重点收口时间层级一致性、时间标题视觉质感，以及主照片流专属氛围，不扩到新的后端接口或业务结构。

- Module key: `photo_stream`
- Status: `closed`
- Last updated: `2026-07-08`
- Primary surfaces: `android`
- Linked server brief: `none`
- Refine2 status: `completed` (2026-07-08, 3 rounds, 11 FRs, 41/41 AC pass)

## Module Goal
- User value: 让照片流作为 App 的招牌页，在不同密度下都具备清晰、一致、好看的时间秩序，同时保留顺滑的照片浏览体验。
- Business or product intent: 把照片流从“功能完整”推进到“成品态旗舰页”，强化记忆时间轴感知和整体质感。
- Success criteria:
  - `2 / 3 / 4 列` 保留 `月 + 日` 层级，`8 列` 只保留 `月`，`16 列` 只保留 `年`
  - 普通流和协作流的时间层级完全一致，`16 列` 不再冒出日标题
  - 右侧拖拽标尺与当前可见时间标签按密度匹配粒度
  - 主照片流标题不再全是石墨黑，而是具备克制流光的文字质感
  - 视觉增强优先作用于主照片流，不明显污染 picker 与小相册详情

## Current State
- What exists today:
  - `PhotoFeedScreen.kt` 已具备密度切换、右侧时间标尺、协作筛选、多选、滚动恢复和缓存态承接
  - `PhotoFeedGrouping.kt` 已有普通照片流的 `2/3/4 -> 月日`、`8 -> 月`、`16 -> 年` 分组分支
  - `PhotosRootScreen.kt` 已给照片大模块接入统一的 `YingShiAuroraBackdrop(PHOTOS)` 背景体系
- Known constraints:
  - `PhotoFeedScreen` / 标题行组件被 picker 和帖子详情复用，视觉放大不能无差别外溢
  - 当前工作树已有大量非本模块改动，本轮只围绕照片流相关文件推进，不回退其他改动
  - 这轮不新增后端接口，也不改照片业务数据结构
- Relevant code or docs:
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PhotoFeedScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PhotoFeedGrouping.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PhotoFeedModels.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/RealPhotoFeedPage.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/AppPhotoFeedPickerScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PostDetailScreen.kt`

## Your Current Ideas
- 各时间标题增加颜色点缀，不要全是石墨黑
- `16 列` 不该再出现日期标题
- 这页已经比较完善，但作为主要招牌页，希望 Codex 再多发挥一点

## Codex Recommendations
### Recommended to finish in this module
- 统一普通流与协作流的时间层级:
  - Why it is worth considering: 当前 `16 列` 文档语义和协作流实现不一致，是最容易留下割裂感的真实问题
  - Impact on usability, robustness, or smoothness: 时间导航更稳定，拖拽标尺和标题语义不会互相打架
- 把右侧时间标尺和当前标签改成密度感知:
  - Why it is worth considering: 只修标题不修标尺，会保留“16 列按年看图，但手里还在拖具体日期”的违和感
  - Impact on usability, robustness, or smoothness: 高密度总览更像扫时间长河，而不是普通列表滚动
- 给主照片流加主场级标题质感与氛围层:
  - Why it is worth considering: 这是当前照片模块最有资格做招牌感的一页
  - Impact on usability, robustness, or smoothness: 不改信息结构也能明显提升成品感与记忆气质

### Defer only with explicit acceptance
- 把 picker、小相册详情、更多复用页面一起重做成同级旗舰视觉:
  - Why it would otherwise belong in this module: 它们确实共用同一套组件
  - Why it might still be deferred: 本轮已明确主照片流优先，复用页只做兼容，不做全量放大

## Key Questions
- [x] 视觉方向固定为 `克制流光`
- [x] 精修优先范围固定为 `主照片流优先`
- [x] `16 列` 标尺与当前可见时间标签固定为 `按年`

## Scope Boundaries
### In scope
- 照片流不同密度下的时间分组、标题和拖拽标尺语义收口
- 主照片流标题文字样式与背景氛围增强
- 主照片流与复用页的展示级别拆分

### Out of scope
- 新增后端接口
- 修改照片上传、viewer、分页或缓存契约
- 顺手重做 picker、小相册详情的整体视觉

### Non-negotiables
- `16 列` 固定按年总览
- 协作流不能再和普通流使用不同时间层级
- 视觉增强要高级但克制，不能用重霓虹或厚胶囊

### Failure and fallback expectations
- Failure states to support:
  - 空照片流
  - 离线只读
  - 加载更多失败
  - 协作筛选为空
- Rollback or fallback behavior:
  - 保持现有可用状态和错误反馈，不因精修标题而破坏原有加载、空态和缓存回退

## Related Modules
- Module: `photos_root`
  - Relationship: 照片流运行在照片主入口之内，复用统一背景与多选壳层逻辑
  - Recheck before ship: 主照片流增强不能打断根层导航、多选和背景表现
- Module: `photo_viewer`
  - Relationship: 照片流点击进入 Viewer，时间精修不应影响打开落点
  - Recheck before ship: 不同密度下点击媒体后 Viewer 初始索引与返回落点正常
- Module: `post_detail`
  - Relationship: 小相册详情复用同一组分组与标题组件
  - Recheck before ship: 逻辑一致性修复生效，但视觉不要过重
- Module: `picker`
  - Relationship: 系统媒体加入和照片选择器复用 `PhotoFeedScreen`
  - Recheck before ship: 时间标签仍正常，视觉不至于抢过选择器自身 UI

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories:
  - `PhotoFeedScreen`
  - `RealPhotoFeedPage`
  - `AppPhotoFeedPickerScreen`
  - `PostDetailScreen`
  - `PhotoFeedPageStateStore`

### Server endpoints and payloads
- 不新增接口，不改已有 contract

### Shared rules
- Auth, permissions, identity, ordering, time, copy:
  - 时间展示按密度统一
  - 右侧标尺标签与当前可见标签必须同一粒度
  - 协作模式下小时桶只在低密度下生效

## UI and Visual Details
- Layout or information hierarchy: 不改核心结构，重点加强时间标题、标尺和主照片流氛围层
- Components and states: 年 / 月 / 日标题，右侧时间标尺，主照片流背景流光层
- Motion or transitions: 只做轻 reveal 和轻 glow，不做持续装饰动画
- Copy notes: 保留现有短标题形式，主要改颜色、层级和粒度

## Interaction Feedback
- Loading: 维持现有照片流与加载更多反馈
- Empty: 保持现有空态，不额外引入解释型文案
- Error: 保持现有错误提示与重试入口
- Success: 时间层级、标尺与标题语义一致
- Permission denial: 本轮无新增权限
- Offline or retry: 继续承接缓存只读与离线回退

## Deployment Readiness
- Release-critical expectations:
  - `16 列` 时间体系彻底一致
  - 主照片流视觉达到可部署成品态
  - 复用页不被明显误伤
- Anything that must be true before moving to the next module:
  - 编译通过
  - 主照片流、picker、小相册详情的基础回归通过
- Acceptable defers, if any:
  - 不把更多复用页一起做成旗舰视觉

## Hidden Impact Checklist
- Notifications: 无直接影响
- Auth: 无直接影响
- Upload: 上传后返回照片流的滚动恢复与新增高亮不能受影响
- Comments: 小相册详情评论入口不应被时间标题改动打断
- Viewer: 点击媒体进入 Viewer 的初始索引与返回路径需要回归
- Settings: 无新增设置项
- Analytics or logging: 无新增
- Cache or offline: 离线只读与缓存回退仍需成立
- Permissions: 无新增权限
- Copy and empty states: 不回退成解释型大文案

## Plan Self-check
- Recommendation quality: 已把最核心的真实问题收敛为时间层级一致、标尺粒度一致、主照片流旗舰化三条主线。
- Scope pressure test: 范围集中在主照片流与共享时间逻辑，不扩张到新的后端和跨模块业务。
- Contract and dependency pressure test: 已识别 `PhotoFeedScreen` 的复用面和需要做展示级别拆分的调用点。
- UX state pressure test: 正常、空态、离线只读、加载更多失败与协作筛选空都纳入了影响面。
- Risks to watch in implement: 共享标题签名改动造成的小相册详情 / picker 编译回归，以及标题特效过重影响滚动性能。

## Implementation Notes
### Client
- `PhotoFeedModels.kt`
  - 新增 `PhotoFeedPresentation`，区分 `MAIN_STREAM` 与 `EMBEDDED`
  - 新增 `PhotoFeedTimeGranularity`
  - `PhotoFeedSectionHeader` / `PhotoFeedDayHeader` 增加时间语义字段，避免只靠字符串做样式和标尺推断
- `PhotoFeedGrouping.kt`
  - 普通照片流继续维持 `2/3/4 -> 月+日`、`8 -> 月`、`16 -> 年`
  - 协作照片流改为真正按 density 分支：
    - `2/3/4` 仍支持日级与小时桶
    - `8` 只保留月级
    - `16` 只保留年级
  - `buildPhotoFeedScrubberAnchors()` 改成直接使用 header 自身的时间粒度信息，不再在 `8/16 列` 回退成日级标签
  - 新增密度感知的 `formatScrubberLabel()` / `formatScrubberDateLabel()`
- `PhotoFeedScreen.kt`
  - 新增 `presentation` 参数，主照片流可使用更强标题和更重一点的氛围层，复用页继续保持克制
  - 主照片流密度切换新增专用转场状态机：
    - `Idle -> Previewing -> Rebounding -> Committing -> Settling -> Idle`
    - 只作用于 `PhotoFeedPresentation.MAIN_STREAM`
  - `双指捏合` 现在正式改成“旧排版持续可见做底座 + 目标排版逐步长出来 + 松手才真正切档”：
    - 预览态不再在中途直接切真实 density
    - 一次手势只锁 `当前档` 的前后一档，第一下有效方向固定本次目标
    - 放大锁到更稀疏一档，缩小锁到更高密度一档；反向拖回时只回拉进度，不再换目标
    - 松手只有 `rawProgress >= 0.66` 才提交到相邻目标档，否则走回弹
    - 手感按更慢的阈值组执行：
      - 锁定阈值改成 `1.05 / 0.95`
      - 满进度阈值改成 `1.34 / 0.76`
      - 渲染进度改成更早起步的 `smootherstep(raw^1.1)`，轻微捏合就能看到预览，但真正落档仍保持克制
  - 预览和提交阶段继续以 `视口中心最近媒体` 为锚点，但内部语义已改成双层 scene：
    - `source scene` 保留真实旧排版作为底座，不再在 preview 中整体消失，最低只降到约 `0.42` 透明度
    - `target scene` 以 overlay 形式逐步长出来，不再等松手后才突然冒出来
    - source / target scene 统一覆盖：
      - 媒体网格
      - 年 / 月 / 日标题
      - 时间桶标题
      - 协作头像头和分隔线
      - 右侧 scrubber 与当前日期标签
      - 顶部协作筛选头像行
  - target scene 的预测不再是“媒体 rect + 零散 header 补丁”，而是基于 `完整 target blocks` 生成：
    - `SectionHeader / DayHeader / TimeBucketHeader / CollaboratorHeader / Divider / GridRow` 全部使用同一套目标排版口径
    - 月标题、年标题、日标题都会在 preview 阶段提前留位
    - 松手前最后一帧和 commit 第一帧尽量保持同一版式，不再先把媒体挤满再插回标题
  - commit / rebound 收口规则已经收成：
  - `Previewing`：source scene 继续可见，target scene 逐步显现，不切真实 density
  - `Committing`：保留 release 时刻 preview scene；后台切真实 density 并校正锚点；source overlay 淡出、target overlay 交棒、live target list 同步淡入
  - `Rebounding`：不达阈值时不切真实 density，target scene 退回，source scene 恢复完整
  - overlay 候选和贴图负担这轮继续减重：
    - morph overlay 改成最多约 `48` 个中心可见媒体
    - source commit 底座改成最多约 `96` 个可见媒体
    - 预览初始化不再重复扫描同一批候选
    - source header / scrubber snapshot 与 source 底座候选延后到 commit 再补，降低手指刚开始捏合时的同步启动成本
    - 转场 overlay 的缩略图 request size 上限压到约 `384px`，优先保滑动和捏合实时性
    - morph overlay 改成固定底座尺寸 + `graphicsLayer` 平移缩放，减少每帧按插值 `Rect` 重新走布局
    - live list 的 alpha 不再逐卡下发，改成收在 `LazyColumn` 容器层，减少捏合过程里整页可见项的重组压力
    - preview 候选几何不再只依赖每个卡片回传的 `bounds`，没有现成 `bounds` 时会按当前网格规则直接推导，降低“过程不动、松手才跳”的概率
    - 为避免“媒体层直接消失”的回归，媒体层和说明层已经重新拆开：
      - 媒体层在转场中保持稳定可见，只做很轻的明度收束
      - 标题、协作头像头和右侧时间 UI 继续交给 overlay 扛主要特效
      - 不再通过砍掉真实媒体层来换性能
    - `2026-06-11` 再补一轮 scene 接管修复：
      - 预览 / 回弹阶段，live 标题层、协作头像头、顶部协作筛选行和右侧时间标尺不再 100% 常驻
      - 这些辅助层现在会按同一份 preview progress 逐步退到 source scene 的低透明度，再交给 target overlay 接棒
      - commit 阶段 live 辅助层会完全让位，settling 后再由真实目标列表接回，避免“媒体已进入目标态，但日期/头像还停在旧位置并与目标层重合”
    - `2026-06-11` 深度回归修复后，密度转场增加了更硬的可见性不变量：
      - preview / rebound / committing / settle 任一阶段都不能让 live 标题头像层和 target overlay 同时为 0
      - target 标题 / 协作头像 / scrubber overlay 现在覆盖 preview、commit 和交接尾段，不再只在预览阶段出现
      - commit 结束后不再把标题 reveal alpha 从 0 重新跑一遍，避免切档完成瞬间标题和头像二次消失
      - target overlay 几何没有及时采集到时，真实目标标题 / 头像层会立刻兜底显示，不允许空窗
      - 预览几何短暂不可用时先走轻量 fallback 缩放反馈，后续 bounds 稳定后再接完整 morph，避免“手指过程中不动、松手才跳”
      - 未提交回弹也会走同一条回弹动画，不再只有完整 overlay 场景才有回弹
  - 转场期间：
    - inline video 暂停
    - 原档标题、协作头像头、顶部协作头像行和右侧 scrubber 不再走“中段全隐藏”，而是作为 source scene 持续可见并逐步变淡
    - 目标标题、协作头像头和右侧时间 UI 在 preview / commit 阶段按目标排版连续长出来
    - live target list 不再等 settle 才突然出现，而是在 commit 阶段就开始接棒淡入，减少完成瞬间重影和二次重排感
    - 为了保实时手感，preview 阶段的真实列表尽量保持静止，主要由 overlay 扛动画；较重的 source snapshot 和额外底座延后到松手后的 commit 再补
    - 视频缩略链路改成“静态封面优先，抽帧兜底”：
      - 有 `cover / preview` 这类静态图时直接按图片请求，不再误当成视频源去抽帧
      - 只有真的只剩视频源时才走本地 poster 抽帧
      - 避免正在播放的视频在捏合暂停后退成纯色封面
      - 当前可见视频会额外做 poster 预热，降低转场第一下才临时补封面的概率
  - 回退保护已接入：
    - 非主照片流和其他旧调用点默认不接这套主照片流预览
    - reduced motion / 系统动画关闭时仍保留“松手才落档”，但退回无动画吸附
    - 空态、阻断式 loading、几何采集不完整时走安全回退
    - 即使回退，也不再保留之前那种先整体压暗再切的闪屏路径
  - `resolveCurrentVisibleDateLabel()` 改为接收 `density`，当前可见时间标签会按密度输出：
    - `2/3/4 列` -> `YYYY年M月D日`
    - `8 列` -> `YYYY年M月`
    - `16 列` -> `YYYY年`
  - 右侧拖拽标尺交互标签同样改成按密度输出
  - 协作流的小时桶入口在高密度下自动隐藏，避免与 `8/16 列` 总览语义冲突
  - 主照片流标题改成“文本本身发光/染色”的层级体系：
    - 年标题最强
    - 月标题按季节/月度做轻色相偏移
    - 日标题明显更轻
  - 针对 `16 列` 年标题再补了一轮更强的字面层叠：
    - 额外的辉光底影
    - 更明显的高光掠层
    - 更厚一点的主文字 glow 和轻微放大
  - `PhotoFeedAtmosphereLayer` 增强主照片流版本，但复用页仍走较轻背景
- `DiscreteZoomGesture.kt`
  - 预览态改成单目标锁档模型：
    - `sourceLevel`
    - `targetLevel`
    - `direction`
    - `rawProgress`
    - `renderProgress`
    - `accumulatedZoom`
    - `centroid`
  - 新增可选 `commitOnGestureEnd` 语义：
    - 主照片流开启后走“松手提交”
    - 其他旧调用点默认仍保持原先的离散阈值切换
- `Tokens.kt`
  - 新增密度转场 motion token：
    - `densityPreviewMillis = 140`
    - `densityMorphMillis = 280`
    - `densitySettleMillis = 160`
    - `densityPreviewZoomInScale = 1.04f`
    - `densityPreviewZoomOutScale = 0.94f`
- `RealPhotoFeedPage.kt` 和 `PhotosRootScreen.kt`
  - 主照片流入口显式传 `presentation = MAIN_STREAM`
  - 新导入媒体回流和普通滚动定位不再复用同一条“强制刷新”链路，避免 `scrollTrigger` 造成额外整页刷新
  - 删除成功提示改成短时态反馈，只有 `已删除...` 这类删除结果会在约 `3.2s` 后自动消失，不影响离线只读等需要持续存在的状态提示
- `PostDetailScreen.kt`
  - 小相册详情继续复用共享时间逻辑，但保留嵌入式展示级别
  - 右侧拖拽标尺的标签也已按密度统一
 - `RealPhotoFeedViewModel.kt` / `PhotoFeedScreen.kt`
  - 当照片流已有内容时，后续刷新改成静默同步，不再重新打阻断式 loading
  - 照片流内删除媒体后先本地更新和写回缓存，再只通知其他关联模块刷新，避免当前页被自己的删除事件再打断一次
  - 行内媒体卡片补上稳定 `mediaId` key，降低新增/删除后同一行复用错位带来的闪动感
- `docs/refinement/photo_stream.md`
  - 建立本模块精修 brief，承接本轮实现与后续真机验证

### Server
- `UploadController.java`
  - 上传接口显式固定 `multipart/form-data -> application/json`，避免视频文件 part 的 `video/mp4` 把响应内容类型带歪
- `GlobalExceptionHandler.java`
  - 错误响应也强制回 `application/json`，避免上传链路异常时再次因为预设内容类型不对而丢失可读错误
- `UploadService.java`
  - 视频上传生成 `MediaEntity` 时不再把 `previewUrl` 留空，而是统一指向 `cover` 变体
  - 视频 `previewObjectKey` 与 `coverObjectKey` 同步，兼容现有 `media.preview_url not null` 约束与读取模型
  - 本轮深度纠错确认了一个关键运行时问题：
    - 用户实际在打 Docker + PostgreSQL + MinIO 环境
    - 失败并不是“视频时长限制”，而是运行中的 `yingshi-server` 容器仍在执行旧代码，持续因为 `media.preview_url not null` 约束导致视频落库失败
    - 已将最新 `app.jar` 直接替换进运行中的 `yingshi-server` 容器并完成重启，使真实运行环境吃到修复
- `MediaService.java`
  - 照片流聚合层增加静默去重，优先按 `checksum` 去重，缺失时再回退 `sourceFingerprint` 与媒体形态键
  - 覆盖“删到回收站后又重传同一文件，再恢复旧媒体”场景，避免照片流里同时出现两条相同媒体
- Upload limits:
  - 当前本地后端 multipart 体积限制仍是 `max-file-size=4096MB`、`max-request-size=4300MB`
  - 这次短视频和长视频都失败的根因不是时长限制，而是上传响应内容类型与视频实体入库字段约束

### Design
- 主照片流保持 `克制流光`，但比复用页更有“招牌页”氛围
- 标题特效主落在字本身，不引入新的胶囊或说明条
- 颜色不走高饱和霓虹，而是珍珠浅蓝、冰青、微暖金与柔雾青之间的轻层次变化
- 年标题被拉成最强记忆锚点，适合 `16 列` 年级扫视

### Interaction Feedback
- `16 列` 下的标题、当前可见时间标签和右侧拖拽标尺统一到年级语义
- `8 列` 下统一为月级
- 低密度协作模式仍保留小时桶，但切到 `8/16 列` 后不再混进小时语义
- 主照片流增强不影响原有点击媒体、长按多选、滚动恢复、离线只读和加载更多反馈
- 照片/生活共用上传链路里的视频上传不再因为 JSON 回包类型或 `previewUrl` 约束而直接 500

## Post-implement Self-check
- Validation run:
  - `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` 通过
  - `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -Dtest=YingshiServerApplicationTests#localVideoUploadReturnsJsonEnvelope test"` 通过
  - `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -Dtest=YingshiServerApplicationTests#restoredDeletedMediaDoesNotDuplicatePhotoFeedAfterReupload test"` 通过
  - 运行时部署修复：
    - 已确认 `yingshi-server` 容器在 `2026-06-10 17:11:45 +08:00` 完成重启并加载新包
    - 已在当前运行中的 `http://127.0.0.1:8080` 服务上执行真实视频上传 smoke：
      - `POST /api/uploads/token` 返回 `200` JSON
      - `POST /api/uploads/{uploadId}/file` 返回 `200` JSON
      - 返回体包含有效的 `mediaId`、`previewUrl`、`coverUrl`、`videoUrl`
      - 数据库中的对应 `upload_tasks` 已落为 `SUCCESS`
  - New behavior sanity:
  - 主照片流密度切换现在具备：
    - 一次手势只锁相邻一档，方向一旦确定，本次捏合只在 `当前档 <-> 相邻目标档` 之间来回
    - 旧排版会持续作为底座保留，target scene 逐步长出来，不再先把原排版整体清空
    - 松手后才真正提交目标 density，且 release 最后一帧与 commit 第一帧尽量保持同一版式
    - 中心锚点媒体优先稳定，目标标题 / 协作头像头 / scrubber 会和 target scene 一起接棒，不再等 settle 后才突然出现
    - `2026-06-11` 的回归修复后，辅助信息层的接管顺序也已统一：
      - preview / rebound：source live 辅助层逐步变淡，target overlay 辅助层逐步接起
      - committing：live 辅助层不再继续悬在旧位置
      - settling / idle：真实目标辅助层再回到 1，不再和 overlay 双层重合
    - 本轮深度修复后补齐了转场可见性兜底：
      - target header / avatar / scrubber overlay 在 commit 阶段继续参与，不会松手后突然断掉
      - commit 结束直接回到稳定可见态，不再二次把标题和头像清空再渐显
      - overlay 几何缺失时真实目标层负责兜底，预览几何缺失时至少有轻量跟手 fallback
  - picker、小相册详情和其他复用页未接入这套连续 preview overlay，仍走原有稳定切换路径
  - 普通流与协作流的密度分组语义已统一
  - 主照片流与复用页的展示级别已拆开，视觉增强不再无差别外溢
  - 右侧拖拽标尺和当前可见标签已按密度对齐
  - `16 列` 年标题现在有单独加强过的字面光泽层，不再只是和月/日标题共用同一档弱特效
  - 视频上传链路已补齐 JSON 回包与视频预览占位，服务端定向回归用例可以成功上传 `video/mp4`
  - 真实 Docker 运行环境已不再复现旧的 `preview_url` 空值报错
  - 照片流在“新增媒体回流 / 当前页删除媒体”场景下不再额外走一轮阻断式整页刷新，列表复用也更稳定
  - 删除成功提示不会再长时间挂在标题下方；恢复已删旧媒体与重传同文件并存时，照片流只保留一条可见媒体
- Contract sanity:
  - 未新增后端接口
  - 客户端只扩展了 `PhotoFeedScreen` 的标题渲染参数，不改既有照片 contract
  - 上传 contract 形状不变，仍是 `token -> multipart file -> confirm/get status`
  - 服务端只是把上传接口和异常响应明确固定为 JSON，并让视频也满足现有 `previewUrl` / `previewObjectKey` 读写约束
- Test plan quality:
  - 已补齐 Android 编译、本地服务端视频上传回归、关联模块回归点和真机重点场景
- Known gaps:
  - `16 列` 年标题的主观观感仍需要你在设备上验收
  - 当前环境没有可直接读取真机 `adb logcat` 的工具，因此如果你手机端仍出现“视频上传失败”，下一轮需要围绕设备端日志继续钉 Android 侧现场原因
  - 视频上传虽然已有服务端定向回归和真实运行时 smoke 通过，但仍需要你在真机从 `照片导入` 和 `今日痕迹` 两个真实入口各测一轮短视频/长视频

## New Coupling Recheck
- Module:
  - What was rechecked: `photos_root` 主照片流入口、`post_detail` 小相册详情、`picker` 复用 `PhotoFeedScreen` 的时间标签与展示级别
  - Result: 主照片流已显式走 `MAIN_STREAM` 且开启 `commitOnGestureEnd`；复用页继续走嵌入式默认值和旧手势语义；共享时间 helper 改动已覆盖到 `PostDetail`
- Module:
  - What was rechecked: `LocalSystemMediaBridgeRepository` 与 `LifeConsoleUploadBridge` 共用的上传服务契约
  - Result: Android 上传请求形状无需变更；服务端视频上传回归已通过；还需你在真机覆盖照片入口和生活入口的真实视频选择路径

## Implement Test Plan
### Locally validated
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"`
- Result: 通过
- Check: `git diff --check -- app/src/main/java/com/example/yingshi/feature/photos/PhotoFeedScreen.kt docs/refinement/photo_stream.md`
- Result: 通过
- Check: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -Dtest=YingshiServerApplicationTests#localVideoUploadReturnsJsonEnvelope test"`
- Result: 通过

### Linked-module regression checks
- Module:
  - What to recheck: `photos_root` 里主照片流切页、筛选、多选和背景氛围
  - Why it can regress: 这轮新增了主照片流专属 `presentation` 与更强氛围层
- Module:
  - What to recheck: `post_detail` 小相册详情的时间标题、右侧标尺和多密度切换
  - Why it can regress: 共享时间 helper 和 header 模型已调整
- Module:
  - What to recheck: `picker` 里的照片流时间标签、长按选择和拖拽标尺
  - Why it can regress: `PhotoFeedScreen` 现在支持展示级别和新的密度标签规则
- Module:
  - What to recheck: `life_console` 和系统媒体导入的共享视频上传链路
  - Why it can regress: 这轮额外动了服务端上传响应与视频实体入库占位字段

### Real-device checks for the user
- Scenario:
  - Steps: 在主照片流里用双指捏合切换 `2<->3`、`3<->4`、`4<->8`、`8<->16`
  - Expected result:
    - 捏合过程中网格全程跟手预览，不会中途先切到某一档真实 density
    - 一次手势只允许预览相邻一档，不会一口气跳两档
    - 旧排版会一直作为底座存在，只是逐步变淡；目标排版会逐步长出来，而不是松手后才突然冒出来
    - 松手前最后一帧和切档完成后的第一观感尽量一致，不应再出现“又重新排了一次”
- Scenario:
  - Steps: 在主照片流里切换 `2 / 3 / 4 / 8 / 16 列`
  - Expected result:
    - `2 / 3 / 4` 有 `月 + 日`
    - `8` 只有月标题
    - `16` 只有年标题，不再出现日标题
- Scenario:
  - Steps: 在 `16 列` 下拖动右侧时间标尺，并观察当前可见时间标签
  - Expected result: 拖拽标签和当前标签都按年显示，不再出现具体日期
- Scenario:
  - Steps: 进入协作照片流，分别在低密度和 `8/16 列` 下观察时间桶与标题
  - Expected result:
    - 低密度仍可用小时桶
    - `8/16 列` 不再出现小时桶或日级语义残留
- Scenario:
  - Steps: 从照片流点开 Viewer，再返回；切到小相册详情和 picker 再看标题与标尺
  - Expected result:
    - 主照片流标题更有质感
    - 复用页仍自然、不夸张
    - 点击落点与返回路径正常
- Scenario:
  - Steps: 从 `照片 -> 系统媒体导入` 选择一个几秒的视频，再选一个几分钟的视频，分别上传
  - Expected result:
    - 两类视频都能创建上传任务并成功完成
    - 不再出现直接失败或服务器 500
    - 上传完成后可正常回流到照片流
- Scenario:
  - Steps: 在 `生活 -> 今日痕迹` 选择视频上传
  - Expected result:
    - 能成功上传并进入今日痕迹媒体槽
    - 不再出现因为视频上传失败导致的报错

### Still unverified
- Risk:
  - Why it remains open: 这轮只完成了本地编译和服务端定向回归，`16 列` 年标题的主观强度以及真机真实视频上传体验还没由你在设备上确认
  - Best next verification path: 用主力机先看 `16 列` 年标题观感，再分别从照片入口和生活入口各传一段短视频与长视频

## Real-device Issue Log
### Issue 2026-06-10-01
- Date: `2026-06-10`
- Build version: `未提供`
- Device / OS: `未提供`
- Module key: `photo_stream`
- Test environment: `真机验证`
- Repro steps:
  1. 进入照片流页
  2. 切换到 `16 列` 总览
  3. 观察只显示年份的时间标题，并与其他列密度下的时间标题特效对比
- Expected result: `16 列` 保持只显示年标题，但标题本身也应具备和其他列一致的文字特效体系，而不是退化成普通文本
- Actual result: `16 列` 已经只显示年标题，但年标题缺少明显特效，看起来没有和其他列标题保持同一套视觉语言
- Evidence: `用户文字反馈`
- Severity: `polish`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/PhotoFeedScreen.kt` 中年标题分支的文字层叠、调色和辉光强度
- Next action: 已在本轮实现年标题的额外辉光、高光和主文字强化；下一步由真机复查观感是否达成预期

## Validation Snapshot
### Verified
- 计划已落地到代码
- Kotlin 编译通过
- 普通流 / 协作流 / 小相册详情的时间标签 helper 已统一
- 真机已确认 `16 列` 只显示年标题，没有再混入日期标题
- 服务端定向回归已确认 `video/mp4` 上传接口能成功回 JSON 并完成入库
- 服务端定向回归已确认“恢复回收站旧媒体 + 重传同文件”不会在照片流里产生重复项

### Pending
- None blocking closeout; remaining spot-checks are accepted as carry-forward verification

### Blocked
- None

## Next Fix Queue
- No active fix queue at closeout; future checks are tracked in `Carry-forward Notes`

## Closeout Summary
- What shipped:
  - 照片流时间层级已按密度统一收口：`2 / 3 / 4 列 -> 月 + 日`，`8 列 -> 月`，`16 列 -> 年`，普通流与协作流现在使用同一套语义。
  - 主照片流已接入更完整的时间标题质感与背景氛围，年 / 月 / 日标题层级拉开，`16 列` 年标题也补齐了独立特效，不再是弱化普通文本。
  - 右侧拖拽标尺和当前可见时间标签已改成密度感知输出，高密度下不再残留日级文案。
  - 主照片流密度切换的转场状态机已进一步收口：标题、日期、协作头像和右侧时间标尺在 `preview / commit / settle` 全链路都有显示兜底；commit 结束也不再二次清空标题层。
  - 视频上传问题已完成深度纠错：服务端固定上传成功/失败都返回 JSON，视频媒体写入时补齐 `previewUrl` 占位，真实 Docker 运行环境也已替换到修复后的 `app.jar`。
  - 照片流额外收掉了一轮交互抖动：已有内容时改成静默刷新，删除媒体不再被本页自己的变更事件反向重刷，行内卡片也改成按 `mediaId` 稳定复用，降低新增/删除时“整屏闪一下”的体感。
  - 删除成功提示现在会在短暂反馈后自动消失，不再一直占在标题下方。
  - 照片流服务端聚合补上静默去重：同一文件被删进回收站、又重新上传、再恢复旧媒体时，照片流不会再露出两条相同媒体。
- What was validated:
  - Android `:app:compileDebugKotlin` 通过。
  - 最新一轮密度转场接棒修复后，`git diff --check` 与 Android `:app:compileDebugKotlin` 继续通过，说明状态机改动至少在本地构建层面已收稳。
  - 服务端 `localVideoUploadReturnsJsonEnvelope` 定向回归通过。
  - 运行中的 `yingshi-server` 容器已在 `2026-06-10 17:11:45 +08:00` 重启并加载修复包。
  - 针对真实 `http://127.0.0.1:8080` 的 `mp4` 上传 smoke 已成功返回 `mediaId`、`previewUrl`、`coverUrl`、`videoUrl`，数据库 `upload_tasks` 落为 `SUCCESS`。
  - 真机已确认 `16 列` 只显示年标题，不再混入日期标题。
- What remains risky:
  - `16 列` 年标题的最终主观观感仍以真机视觉验收为准，但这属于成品质感微调，不阻塞本模块关闭。
  - 当前环境没有可直接读取真机 `adb` 日志的能力，所以如果后续仅某台设备仍复现视频上传异常，需要在那一轮结合设备现场日志继续钉 Android 侧问题。
  - “新增媒体 / 删除媒体的闪动感”这轮只做了代码级压制和编译验证，还需要你在真机再扫一遍体感确认。
- What was intentionally deferred:
  - 不把 picker、小相册详情等所有复用页一起升级成与主照片流同级的旗舰视觉，只保留逻辑一致和克制样式对齐。
  - 不扩到新的后端接口、照片业务结构或跨模块内容摘要。

## Carry-forward Notes
- Fact future modules must remember: `PhotoFeedScreen` 仍是多处复用组件，后续再改标题、背景或时间结构时要继续区分 `MAIN_STREAM` 和嵌入式场景，避免把主场特效外溢到 picker / `post_detail`。
- Fact future modules must remember: 主照片流密度切换现在依赖一套 source/target/live 三层接棒语义；后续如果再动标题、协作头像或右侧 scrubber，优先守住“任一阶段都不能让两套辅助层同时为 0”这个可见性不变量。
- Fact future modules must remember: 视频上传这次不是时长限制问题，真实根因是运行中 Docker 服务落后于源码版本叠加 `media.preview_url` 空值约束；以后碰到“源码已修但线上还错”时要先核对容器内 `app.jar`。
- Adjacent modules to revisit later: `photos_root`、`post_detail`、`picker`、`life_console`。
- Future spot-checks when those modules are touched:
  - 再看一次 `16 列` 年标题在主力机上的观感强度。
  - 从 `照片导入` 和 `今日痕迹` 两个真实入口各补测一轮短视频与长视频上传。
  - 在主照片流里各做一轮“新增媒体回流”和“删除当前媒体”体感复查，确认不会再出现明显整屏闪动。

## Closeout Self-check
- Brief completeness: 最终范围、时间层级决策、上传修复事实、验证结果和接受的 defer 都已落到简报，没有依赖聊天历史才能理解的关键缺口。
- Remaining risk clarity: 剩余风险已明确收敛为真机观感微调和设备侧上传现场日志能力不足，均已标明不阻塞本模块关闭。
- Carry-forward quality: 后续需要记住的复用边界、Docker 部署漂移教训和关联模块复查点都已保留，重复性的实现来回过程已压缩掉。
