# Photo Viewer Refinement

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `photoViewer`
- Status: `closed`
- Last updated: `2026-06-18`
- Primary surfaces: `android | shared`
- Linked server brief: `none`

## Module Goal
- User value: Viewer 看图/看视频更像主场体验，长图识别准确、缩放够用、信息清楚、返回位置可靠，断网时小相册详情仍能读缓存。
- Product intent: 把照片流、小相册详情、Viewer、缓存只读串成一条稳定观看链路。
- Success criteria: 普通手机截图不误入长图；图片可双击/双指顺滑缩放；Viewer 菜单/时间/视频控制更高级；所属小相册中文标题和返回锚点稳定；已缓存小相册详情离线可显示。

## Scope Boundaries
### In scope
- Viewer 图片长图阈值、最大缩放、单击沉浸、双击缩放和 Back 缩放复位。
- Viewer 底部时间、三横线菜单、所属小相册 sheet、视频控制条。
- Viewer -> 所属小相册 -> Viewer -> 照片流的返回位置。
- 小相册详情 per-post read cache 和离线只读展示。

### Out of scope
- 不改 Server 合同。
- 不重做上传、评论、回收站协议。
- 不新增 Viewer 设置项。

## Frontend and Backend Contracts
### Client state and entry points
- Main Viewer: `PhotoViewerScreen.kt`, `PhotoViewerRoute`, `YingShiApp.kt` overlay routing.
- Small album detail: `PostDetailRealViewModel`, `PostDetailScreen.kt`, `AppReadCacheStore`.
- Related modules: `PhotoFeedScreen`, `GlobalPhotoFeedPageStateStore`, settings viewer preferences, real/fake repositories.

### Server endpoints and payloads
- Reuse existing `GET /api/small-albums` and `GET /api/small-albums/{smallAlbumId}`.
- No Server DTO or endpoint changes.

### Shared rules
- Long image threshold: `height / width >= 3.0`.
- Max zoom: stable `6x`, elastic in-gesture `9x`; double-tap target `2.5x`, any zoomed double tap resets to `1x`.
- Back while image is zoomed resets zoom before exiting Viewer, including immersive state.
- Viewer menu keeps top share button but removes menu share item.
- Cached small-album detail is read-only while offline and only exists after that detail has been opened online once.

## Shipped Behavior
### Viewer gestures
- 普通图片稳定最大缩放为 `6x`，双指继续拉可临时到 `9x`，松手回落到 `6x`。
- 双指缩放按当前手势中心做焦点锚定，不再固定从画面中心放大。
- 双击在原图状态进入 `2.5x` 快速放大；任意已放大状态双击恢复 `1x`。
- 单击沉浸/唤出改为快速自定义识别，不再为了双击等待系统长延迟。
- 放大状态下 Back 优先恢复原图大小，再按一次才退出 Viewer。

### Viewer chrome and media info
- 长图识别阈值调整为 `height / width >= 3.0`，减少普通手机截图误判成长图。
- 三横线菜单移除“分享”，保留顶部分享入口。
- 时间胶囊回到底部中间区域，略向左偏；只展示时间本身，不显示“拍摄时间”前缀，也不挤压评论和原图按钮。
- Viewer notice 维持短暂自动消失。

### Related small albums and return anchors
- 所属小相册标题解析顺序为当前 route、远端 summary、缓存大相册目录，最终兜底 `未命名小相册`，避免展示英文 id。
- 所属小相册 sheet 每行补所属大相册 chip，优先使用远端/缓存大相册标题，Fake 数据走 `FakeAlbumRepository` 标题。
- `PhotoViewerScreen` 向 App 层回传当前 route 快照；从 Viewer 打开所属小相册再返回时保留原 Viewer 当前页。
- Viewer 关闭回照片流时通过当前媒体 id 写入滚动锚点，避免回到照片流第一张。

### Video viewer
- 视频控件补齐重播、错误重试、自动隐藏、进度拖动与“剩余时间”显示。
- 拖动 seek 不再重建播放器；松手后用目标时间钉住控件和播放状态，避免先闪到开头再跳到目标位置。

### Offline small-album detail cache
- `AppReadCacheStore` 增加 per-post `RemotePostDetail` 读缓存。
- `PostDetailRealViewModel` 会先用缓存渲染小相册详情，再尝试网络刷新；登录/网络失败时不再把已缓存详情清空。
- 缓存按当前用户和 baseUrl 作用域保存，不改变写入权限或 Server 合同。

## Validation Snapshot
### Verified
- Android Kotlin compile passed on 2026-06-17 with `:app:compileDebugKotlin`; only existing status-bar deprecation warnings remained.
- Real-device feedback confirmed stable `6x` zoom direction was acceptable before the later elastic/focal-point pass.
- Real-device issue rounds drove and were folded into the final shipped behavior: elastic `9x`, bottom time left offset, fast single tap, double-tap reset, Back zoom reset, related big-album chip, and video seek no-flash behavior.

### Pending / risk to recheck
- Fast single tap, double tap, Pager horizontal swipe, pinch zoom, and read-only Viewer taps should be regression-checked together on a real device because they share pointer input.
- Elastic `9x` focal-point zoom should be judged by feel on real fingers, especially release back to stable `6x`.
- Back first resetting zoom before exiting Viewer should be rechecked in both immersive and non-immersive states.
- Video seek should be rechecked with longer videos and weak network media URLs.
- Related small-album chip titles should be rechecked with real/cached data combinations.
- Offline detail cache only works after the small album detail was opened online once; first-ever offline open still cannot fabricate data.

### Blocked
- None.

## Adjacent Module Impact
- Photo feed: Viewer close writes `GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId` and triggers the photo-feed scroll anchor.
- Trash read-only viewer: shared tap gesture helper remains compatible; needs light regression when trash viewer is touched again.
- Small album detail: cached detail pre-render changes offline behavior but stays read-only and uses existing DTOs.
- Settings: existing `hideOverlaysWhenZoomed` and `autoPauseVideoOnMediaSwitch` semantics remain intact.
- Server: untouched; if future small-album DTOs change, recheck cached detail mapping and related album title resolution.

## Closeout Summary
- Shipped: Viewer long-image threshold, stable/elastic zoom, focal-point pinch, double-tap reset, Back zoom reset, faster single tap, bottom time capsule, overflow menu cleanup, related small-album Chinese titles and big-album chips, return anchors, video seek stability, and small-album detail read cache.
- Validated: Android compile passed; user real-device feedback was processed through multiple fix passes and final behavior has been documented as the expected contract.
- Remaining risk: The module is closed with tactile gesture/video/offline behaviors explicitly marked for future regression checks, not as active blockers.
- Deferred: No new settings, no Server contract work, no upload/comment/recycle-bin protocol changes.

## Carry-forward Notes
- Treat `height / width >= 3.0` and stable `6x` / elastic `9x` as the current Viewer contract unless product direction changes.
- Any future work touching `PhotoViewerScreen.kt` pointer input must retest single tap, double tap, horizontal paging, pinch zoom, Back reset, and read-only Viewer together.
- Any future work touching small-album DTOs, cache keys, or title mapping must retest offline small-album detail and related small-album sheet titles/chips.
- Cache behavior is “last opened online can be shown offline,” not full offline sync.
- Server remains unchanged for this module.

## Closeout Self-check
- Brief completeness: Final scope, shipped behavior, validation, contracts, and adjacent impacts are captured; old issue history was compressed into durable outcomes.
- Remaining risk clarity: Gesture conflicts, video seek feel, related-title data combinations, and cache-hit limits are explicit.
- Carry-forward quality: Future recheck notes are short, concrete, and tied to the files/modules most likely to drift.
