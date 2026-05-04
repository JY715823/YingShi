# Current Task: Stage 12.7 - 全局流畅度专项

## 背景

Stage 12.1-12.6 已完成 REAL 媒体主链路、Viewer 产品化、状态一致性、媒体管理、新增媒体和新增帖子闭环。现在进入 Stage 12.7，目标不是新增功能，而是提升全局使用流畅度。

当前重点是减少列表滚动卡顿、缩略图闪烁、Viewer 打开慢、系统媒体页加载慢、帖子详情首屏慢、重复请求和不必要的全量刷新。

## 目标

1. 照片流滚动更稳定。
2. 帖子详情首屏更快。
3. Viewer 打开、切换、返回更顺。
4. 系统媒体页缩略图和分页更稳。
5. 相册 / 帖子列表滚动更稳。
6. Gear Edit 媒体管理操作后不明显卡顿。
7. mutation 后尽量局部刷新。
8. 减少重复请求和重复 recomposition。
9. FAKE / REAL 模式都保持稳定。

## 范围

### 1. 照片流

检查：

- LazyGrid key 是否稳定
- 缩略图是否使用合理尺寸
- 是否误用原图加载缩略图
- 滚动中是否频繁重组
- 切换列数 / 密度是否状态稳定
- 删除 / 恢复后是否局部更新

### 2. 帖子详情和媒体区

检查：

- 首屏是否不必要等待全部媒体加载
- 媒体区是否使用缩略图
- 长图 / 视频封面是否稳定
- 评论刷新是否不导致整个详情重刷
- 删除 / 添加媒体后是否只刷新必要区域

### 3. Viewer

检查：

- 打开时是否复用列表已有预览图
- 原图是否按需加载
- 视频是否避免首开黑屏
- 左右切换是否避免重复请求
- 返回列表是否不触发无意义全量刷新
- overlay / 控制条是否造成明显重组

### 4. 系统媒体页

检查：

- 是否分页或分批加载
- 是否有加载中 / 空态 / 权限失败
- 缩略图加载是否稳定
- 移到系统回收站取消时不触发多余刷新
- 导入到帖子后状态刷新是否局部化

### 5. 相册 / 帖子卡片列表

检查：

- key 是否稳定
- 卡片封面是否使用缩略图
- 编辑标题 / 封面后是否只更新对应 item
- 删除 / 恢复后列表状态是否稳定

### 6. mutation 后局部刷新

重点优化：

- 上传成功
- 上传失败
- 删除媒体
- 删除帖子
- 恢复媒体
- 恢复帖子
- 评论新增 / 编辑 / 删除
- 加入已有帖子
- 发成新帖子

要求：

- 能局部更新就不要全量刷新
- 必须全量刷新时避免重复触发
- 失败时不产生脏状态

### 7. 重复请求 / recomposition

检查：

- 进入页面是否重复请求同一接口
- LaunchedEffect key 是否过宽
- collectAsState 是否导致大范围重组
- 列表 item 是否缺少 stable key
- UI state 是否过大导致局部变化触发整页重组
- 视频控制条进度更新是否导致整个 Viewer 重组

## 不做内容

- 不新增大功能
- 不做 OSS
- 不做正式云端存储
- 不做复杂离线缓存
- 不重构 fake/real 总架构
- 不删除 FAKE repository
- 不强制默认 REAL
- 不做 UI 大精修
- 不改业务规则

## 文档要求

更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

## 验收

1. 照片流滚动无明显缩略图错位或频繁闪烁。
2. 照片流不使用原图作为列表缩略图。
3. 帖子详情首屏不被全部媒体阻塞。
4. 帖子详情媒体区缩略图稳定。
5. Viewer 打开时优先显示已有预览。
6. Viewer 切换图片 / 视频不明显卡顿。
7. Viewer 返回列表不触发不必要全量刷新。
8. 系统媒体页加载中、空态、权限失败兜底稳定。
9. 系统媒体缩略图滚动稳定。
10. 相册 / 帖子卡片列表 key 稳定。
11. 上传 / 删除 / 恢复 / 评论后尽量局部刷新。
12. 没有明显重复请求同一接口。
13. 视频控制条进度更新不导致整页明显卡顿。
14. FAKE / REAL 主流程都正常。
15. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.7 Progress Update

- System media first-screen load was reduced to a single effective boot path. Duplicate `ensureLoaded()` triggers on enter/resume are now guarded in the ViewModel.
- System media grid now renders in batches on the UI side instead of composing the full filtered list at once; manual refresh and MediaStore-change refresh remain supported.
- Feed / album / media-management thumbnails now request smaller preview sizes for list cards so list scrolling no longer competes with full-size image decode work.
- Album page no longer blocks post-list first paint on one `getPostDetail()` call per post. It renders summary cards first and only backfills a small number of cover previews in the background.
- Comment mutation refresh was narrowed: comment changes no longer force photo-feed refresh, and post detail now handles comment-only mutation separately from full post-detail reload.
- Viewer now prefetches the current item plus adjacent items so open / swipe / return paths reuse preview cache more often.

## Post-12.7 Product Flow Adjustment

- Bottom navigation now reserves the center slot for `添加`; the old always-visible photo-page `+` row is removed.
- Photo feed no longer shows the permanent count / column switcher row. Density stays gesture-driven for now, and row / column spacing are aligned to the same thin gap.
- Photo feed time scrubber is right-edge aligned, proportional to current list progress, and only shows the date label while the scrubber itself is being dragged.
- Album page removes the extra album title / intro / manage header above chips so the chips and post grid become the first meaningful content.
- Trash `24h可撤销` moved into the trash type row as an entry button; normal trash rows still open deleted-state detail.
- Notification center removes the explanatory summary card; tapping known local notifications now routes to post detail, album, trash, pending cleanup, or cache management instead of only opening a placeholder detail page.
- System media grid is visually closer to the app photo feed: fixed square cells, thin equal gaps, and import actions aligned with app media semantics.
- System media now has `导入app`, separate from `发成新帖子` and `加入已有帖子`; post creation / add-to-post still imports the media into app content as part of the same flow.
- Viewer original action is exposed for image and video media when a usable original / media / video URL exists. Images still request the original image; videos probe the original media URL and keep the viewer stable on failure.
- Video poster state now checks the disk poster cache before entering loading state, reducing the flash after returning from Viewer.

## Post-12.7 Targeted Fix Follow-up

- App photo feed and system media both use a right-edge white proportional time scrubber. The scrubber has no vertical rail, shows the date only during direct scrub interaction, and maps top / bottom to the current list's start / end.
- System media now shares the app photo-feed density levels `2 / 3 / 4 / 8 / 16` through the same pinch-zoom gesture, while keeping square cells and thin equal gaps.
- App photo-feed multi-select now uses one bottom `操作` entry that opens a bottom sheet; system media keeps four actions in two rows. Selection badges use a clearer white-circle / blue-check style.
- REAL trash folds `24h 可撤销` into the trash category row. Trash detail renders original media previews from `sourceMediaId` / `relatedMediaIds` instead of only showing metadata fields.
- Notification taps avoid opening fake post ids in REAL mode. Known REAL notifications route to stable top-level destinations until backend notification target ids are formalized.
- Viewer original loading now treats only image `originalUrl -> mediaUrl` as original candidates. Video media uses normal playback source fallback and does not show `加载原图`.
- Video poster state has a small in-memory bitmap cache on top of the disk poster cache to reduce thumbnail flashing after returning from Viewer.

## Stage 12.7-Hotfix Original Loading

- App-content original loading is image-only in this hotfix. Video media no longer shows the `加载原图` action and never enters the image original state machine.
- Image preview display uses preview-sized sources first: `thumbnailUrl -> mediaUrl`. `originalUrl` is not used as the normal Viewer preview fallback unless a later contract explicitly permits it.
- The original action is shown only when `originalUrl -> mediaUrl` yields a non-empty image URL distinct from the current preview URL. Missing, identical, or unusable original candidates hide the action instead of showing a fake retry path.
- REAL single-image original loading is render-driven: tapping the action only moves that `mediaId` to `Loading`; the Viewer canvas or post-detail media card must successfully render the original `ImageRequest` before the state changes to `Loaded` and before the success toast is shown.
- Original image requests use dedicated original memory/disk cache keys and `Size.ORIGINAL`, so preview cache entries cannot satisfy an original render.
- Post detail `加载全帖原图` only processes loadable images, skips videos and images without meaningful originals, and reports exact loaded / skipped / failed counts.
