# Current Task: Photo Feed Paging Step 1

## Background

上传/导入链路已经具备进度、失败保留、重试、取消、基础去重和成功后刷新定位能力。媒体数量继续增长后，照片流不能长期依赖一次性加载全量数据。

## Goals

1. REAL 模式照片流首屏按页加载媒体。
2. 滚动到底部时自动加载下一页。
3. 已加载媒体按 `mediaId` 去重后继续使用现有时间分组、密度切换、多选和视频预览逻辑。
4. FAKE 模式保持可用，不因 REAL 分页改造被破坏。
5. 时间滑条跳转日期时，优先滚到日期/月份/年份标题，使标题在顶部可见。

## Scope

- Android REAL 照片流 ViewModel 分页状态。
- Android 照片流 UI 触底加载更多回调。
- Android 时间滑条锚点定位。
- Server `/api/media/feed` 兼容式分页参数与 page envelope。

## Non Goals

- 不做跨页目标媒体定位。
- 不做刷新不丢滚动位置的大优化。
- 不做加载状态视觉大改。
- 不改 Viewer、上传中心、回收站、帖子详情等无关模块。

## Acceptance

1. REAL 模式照片流首屏正常加载。
2. 滚动到底部继续加载下一页。
3. 加载更多后时间分组、去重、密度切换、多选、视频预览保持正常。
4. FAKE 模式照片流保持可用。
5. 时间滑条跳转日期时，对应标题不会被顶出屏幕。
6. Android `assembleDebug` 通过，Server 测试通过。
