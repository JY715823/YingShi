# Current Task: Stage 12.1 第二轮 - Viewer 图片产品化与沉浸背景修正

## 背景

Stage 12.1 第一轮已聚焦 REAL 媒体 URL 与缩略图显示收口。本轮继续处理 Viewer 图片查看体验。

当前需要重点修正一个真实查看态问题：原 demo 图片查看态中存在五颜六色背景。切换到真实照片后，图片按比例自适应显示时，未被图片占据的区域仍然露出原来的彩色背景。真实 Viewer 应改为统一沉浸式背景，并保证媒体在不遮挡顶部和底部操作区域的前提下居中、最大化显示。

## 本轮目标

让 REAL 模式下以下 Viewer 的真实图片查看体验稳定：

1. 照片流 Viewer
2. 帖子内 Viewer
3. 复用同一组件的系统媒体 Viewer 回归验证

## 范围

### 1. Viewer 图片显示产品化

需要支持：

- 默认显示可用预览图
- 点击加载原图后再请求 originalUrl
- 原图加载中有状态
- 原图加载失败不闪退
- 原图失败后可以回退或保留预览图
- URL 为空时显示安全占位
- 图片加载失败时显示中文提示或安全占位
- 左右切换媒体时状态不串
- 缩放 / 平移能力不被破坏

### 2. 预览图 / 原图优先级

需要明确并实现图片展示 URL 优先级。

建议：

- 默认预览显示：thumbnailUrl -> mediaUrl -> originalUrl
- 加载原图：originalUrl -> mediaUrl
- 如果当前媒体是视频，本轮不进入图片原图加载逻辑，只显示视频占位或封面

实际优先级以项目现有字段为准，但必须在代码和文档中保持一致。

### 3. Viewer 状态隔离

需要避免：

- A 图正在加载原图，切到 B 图后 B 图也显示 loading
- A 图原图加载失败，切到 B 图后 B 图也显示 error
- A 图已加载原图，切到 B 图后错误复用 A 图原图
- 左右切换后缩放、平移、加载状态互相污染

可以按 mediaId / stable key 管理状态。

### 4. 沉浸式背景修正

需要修正：

- demo 时代五颜六色背景残留
- 真实图片未占满区域露出彩色背景
- 图片区域和顶部 / 底部操作区域重叠

要求：

- Viewer 整体背景统一为沉浸式深色背景
- 图片未占据的区域也显示统一背景色
- 图片保持原始比例
- 图片在顶部和底部操作区之间的可用区域内最大化显示
- 图片水平、垂直居中
- 图片不遮挡顶部按钮、底部操作区、小白条
- 不为铺满屏幕强行裁剪真实照片的重要内容

### 5. 保持现有操作不坏

以下能力不能被破坏：

- 返回 / 关闭
- 删除
- 评论气泡
- 所属帖子入口
- 左右切换
- 缩放 / 平移
- FAKE 模式 Viewer 展示
- REAL 模式缩略图展示

## 不做内容

本轮不要做：

- 视频播放产品化
- 视频生命周期完整重构
- 上传流程重做
- OSS
- 真实缓存扫描
- 回收站规则调整
- fake/real 架构大重构
- 删除 FAKE repository
- 强制默认 REAL
- UI 大精修
- 后端大改

## 文档要求

根据实际改动更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

如 Server 未修改，需要在最终输出中说明 Server 未改。

## 验收

1. FAKE 模式照片流 Viewer 仍正常。
2. FAKE 模式帖子内 Viewer 仍正常。
3. REAL 模式照片流 Viewer 能显示真实图片。
4. REAL 模式帖子内 Viewer 能显示真实图片。
5. 默认先显示预览图。
6. 点击加载原图有 loading 状态。
7. 原图加载成功后显示原图。
8. 原图加载失败不闪退，并保留或回退预览图。
9. URL 为空时不闪退，有安全占位。
10. 图片加载失败时不闪退，有安全占位或中文提示。
11. 左右切换媒体时 loading / error / originalLoaded 状态不串。
12. 图片缩放 / 平移能力不被破坏。
13. 删除、评论、所属帖子入口不被破坏。
14. Viewer 背景不再出现 demo 五颜六色残留。
15. 图片未占满区域显示统一沉浸背景。
16. 图片不和顶部区域、底部区域、小白条重叠。
17. 图片在可用区域内保持比例、居中、尽可能放大。
18. 视频媒体本轮至少不崩溃，可显示占位。
19. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.1 second-round implementation note

- App-content Viewer now uses a preview-first image path for REAL image media: `thumbnailUrl -> mediaUrl -> originalUrl`.
- Tapping the original action switches only the current `mediaId` into original loading and requests `originalUrl -> mediaUrl`; loading, success, and failure are stored in a Viewer-local map keyed by `mediaId`.
- Original loading failure does not crash or blank the Viewer. The preview image remains visible when available, and the original action moves to the retry/failure state.
- Missing image URLs and image decode failures show a dark safe placeholder with Chinese text.
- Viewer image backgrounds now use the shared deep immersive background instead of the old demo palette gradient, so letterboxed areas around REAL photos no longer show colored demo blocks.
- The image canvas still uses the existing top and bottom Viewer padding lanes, `ContentScale.Fit`, and the existing zoom/pan gesture path so images keep aspect ratio, stay centered in the available media area, and do not overlap the top bar, bottom action row, or in-post segment indicator.
- Video Viewer remains a placeholder shell in this round; no real video playback productization was added.
- FAKE media without a REAL `mediaSource` keeps the previous demo placeholder path.
