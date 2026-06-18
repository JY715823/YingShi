# System Media Refinement

> One module only. Closed brief for future reopening.

- Module key: `SystemMedia`
- Status: `closed`
- Last updated: `2026-06-18`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 系统媒体工具区像照片流一样顺滑、好看、可信，同时保留本机媒体工具区的直接感。
- Product intent: 打通本机媒体 -> App 媒体库 -> 小相册的真实链路，避免旧的 `已发帖 / 未发帖` 语义继续漂移。
- Success criteria: 系统媒体主状态统一为 `已导入 / 未导入`；导入、新建、加入复用已导入媒体；Viewer 和照片流/PhotoViewer 手感一致；照片模块网络提示不常驻。

## Final Scope
### Shipped
- Android 系统媒体页、系统媒体 Viewer、系统媒体上传/创建/加入链路。
- Server 媒体导入状态查询接口和合同文档。
- 照片流网络/cache notice 生命周期修复。
- 系统媒体列表视觉、密度、时间滑条、多选、删除、缓存、导入状态和 Viewer 体验精修。

### Out of scope
- 不改系统回收站协议本身。
- 不重做上传分片、转码、CDN。
- 不把系统媒体做成 App 内容评论/原图体系。

### Non-negotiables
- 删除系统媒体只影响手机本地 MediaStore，不删除 App 媒体库内容。
- `已导入 / 未导入` 是 App 媒体库状态，不再叫 `已发帖 / 未发帖`。
- 加入小相册必须跳过已经在目标小相册里的媒体。

## Final Contracts
### Server
- Added `POST /api/media/import-status`.
- Request: `{ "sourceFingerprints": ["..."] }`.
- Response: `[{ "sourceFingerprint": "...", "mediaId": "...", "smallAlbumIds": ["..."] }]`.
- Server rule: only returns active media in the current shared library and active small-album relations; missing fingerprints are omitted.
- No DB migration was required.

### Android
- `MediaRepository.getImportStatus()` and REAL DTO/API plumbing were added.
- `SystemMediaItem` carries `importedAppMediaId`, `linkedSmallAlbumIds`, and `isImportedToApp`.
- `LocalSystemMediaBridgeRepository` persists lightweight import overlay metadata (`sourceKey -> appMediaId / smallAlbumIds`) without copying local media bytes.
- `LocalSystemMediaQueryCache` persists lightweight MediaStore metadata rows so cold entry can restore quickly.

## Final Behavior
### List And Controls
- Top controls use a hamburger category menu; the visible multi-select button and horizontal category strip were removed.
- Filters use `全部 / 相机 / 截图 / 视频 / 已导入 / 未导入`.
- Multi-select shows a full-width centered `已选 x 项` row and a single-row bottom bar: `导入 / 新建 / 加入 / 删除`.
- Fresh app process defaults system media to `3列`; returning to system media in the same app process restores the last chosen density from `LocalSystemMediaPageStateStore`.
- `16列` is year-overview style, with year-only grouping and lighter per-cell composition for scroll performance.

### Import / Create / Add
- Import action skips already imported media.
- Create-small-album and add-to-existing-small-album reuse known App media ids before uploading.
- Add-to-small-album skips media already in the target small album.
- Historical local media without `sourceFingerprint` can only be matched by local overlay or future backfill; this is an accepted residual risk.

### Cache / Offline / Refresh
- System media restores from lightweight metadata cache; no duplicate media files are stored.
- Import status sync is best-effort and does not block local MediaStore display.
- Photo feed network/cache notice now auto-clears through the existing status-message lifecycle.
- Metadata cache may be briefly stale until background MediaStore refresh reconciles new/deleted local media.

### Grid / Density / Scrubber
- Grid uses local MediaStore-backed bitmap thumbnail cache with separate overview and regular memory caches.
- Thumbnails are force-fitted to requested edge before caching to avoid large OEM-returned bitmaps evicting overview thumbnails.
- `16列` actively loads thumbnails over a stable palette underpaint, disables `animateItem()`, skips nonessential frame/badge/selection-scale layers, and uses direct per-URI cache lookup.
- Thumbnail warmup is bounded, cancellable, and pauses during active time-scrubber dragging.
- Time scrubber target lookup uses a precomputed `mediaId -> header block index` map; scrub scroll jobs cancel previous jobs and are density-throttled for high-density mode.

### Viewer
- System media Viewer uses a photo-viewer-like glow background and no rounded framed canvas.
- Viewer bottom info is prefix-free: type, time, and import status.
- Viewer supports tap-to-immerse, zoom reset before exit, stable `6x`, elastic `9x`, and long-image reading/zoom behavior aligned with PhotoViewer.
- Viewer display aspect ratio uses raw MediaStore `width/height` before falling back to grid card aspect ratio.

## Validation Snapshot
### Locally validated
- Android `:app:compileDebugKotlin` passed after the final implementation pass.
- Focused `git diff --check` passed for touched SystemMedia files and this brief.
- Server compile passed earlier for the import-status implementation; final SystemMedia performance passes did not change server files.
- Android and Server media API docs include `POST /api/media/import-status`.

### Real-device validated by user feedback
- `16列` repeated flashing was reduced to acceptable level after thumbnail cache, animation, and card-composition fixes.
- System media fresh app entry defaults to `3列`, while same-process return keeps the last selected density.
- `16列` scrolling and time-scrubber dragging were accepted as “差不多了” after the final performance pass.

### Remaining risks
- OEM MediaStore trash confirmation/result behavior can vary by Android version and device.
- Historical imported media without stable `sourceFingerprint` may not be recognized by server import-status.
- Metadata cache can be briefly stale after local media changes until background refresh reconciles.
- The density transition is still not a literal extraction of the photo-feed private morph state machine; it is visually aligned enough for this module close.

## Related Modules
- Upload / transfer center: recheck mixed imported/unimported media upload reuse when changing upload queue semantics.
- Small album detail/media list: recheck duplicate skipping when changing post-media relations.
- Photo feed: recheck network/cache notice and density/thumbnail helper changes if shared later.
- PhotoViewer: recheck long-image zoom and elastic settle behavior when touching shared viewer math.
- Server media contract: keep `POST /api/media/import-status` scoped to active shared-library media and active small-album relations.

## Closeout Summary
- Shipped: System media list, import status, create/add reuse, Viewer polish, system delete flow, lightweight metadata/import overlay cache, high-density grid stability, time-scrubber performance, and photo-feed notice cleanup.
- Validated: Android compile, focused diff check, earlier server compile for the new endpoint, and real-device feedback cycles through list, Viewer, `16列`, density memory, and scrubber performance.
- Deferred or risky: OEM MediaStore differences, old media without fingerprints, brief metadata staleness after out-of-app file changes, and exact photo-feed private density morph extraction.

## Carry-forward Notes
- Do not reintroduce `已发帖 / 未发帖`; system media uses `已导入 / 未导入`.
- Do not store/copy local media files for cache; only metadata and import overlay ids are persisted.
- `16列` is performance-sensitive: avoid per-cell heavy overlays, `animateItem()`, repeated LRU snapshot scans, and unbounded thumbnail prefetch.
- Time scrubber performance depends on precomputed target indexes and cancellable scroll jobs; avoid rebuilding grid blocks during drag.
- If the team later wants exact photo-feed density morph parity, extract shared primitives instead of continuing parallel approximation inside `SystemMediaScreen`.

## Closeout Self-check
- Brief completeness: Final scope, contracts, shipped behavior, validation, related modules, and residual risks are captured in compressed form.
- Remaining risk clarity: OEM MediaStore behavior, historical fingerprint gaps, metadata staleness, and exact density morph parity are explicit.
- Carry-forward quality: Future notes are short, factual, and focused on areas likely to regress in adjacent modules.
