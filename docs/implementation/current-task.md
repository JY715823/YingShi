# Current Task: Stage 12.5 - Viewer 产品化收口

## 背景

Stage 12.1-12.4 已完成 REAL 媒体接入、缩略图、Viewer 基础可用、主链路状态一致性、媒体管理压测和架构减重。

当前项目主链路已成，但 Viewer 还不是最终产品态。原图、所属帖子、长图体验、视频封面和混合媒体切换仍需要收口。

## 目标

本阶段只聚焦 Viewer 产品化：

1. 原图产品化
2. 所属帖子产品化
3. 长图和图片画布体验
4. 视频封面和首开体验
5. 图片 / 视频混合媒体切换状态隔离
6. 系统媒体 Viewer 和帖子 Viewer 边界清晰

## 范围

### 1. 原图产品化

需要保证：

- thumbnailUrl / mediaUrl / originalUrl 优先级清晰
- 没有 originalUrl 时不显示无意义的加载原图按钮
- 原图加载中有状态
- 原图加载失败有中文提示和 retry
- 原图失败后保留预览图
- 每个 mediaId 的原图状态独立
- FAKE / REAL 都不被破坏

### 2. 所属帖子产品化

需要保证：

- Viewer 内能稳定显示所属帖子入口
- 单帖子归属显示清楚
- 多帖子归属有合理展示
- 点击后能跳转对应帖子
- 返回后列表和详情刷新不乱
- 系统媒体 Viewer 不显示帖子专属功能

### 3. 长图和图片画布

需要保证：

- 普通图片保持比例、居中、最大化
- 长图支持合理缩放 / 平移
- 图片不压顶部区域
- 图片不压底部操作区
- 图片外区域是统一沉浸背景
- 帖子详情媒体区矩形画布比例稳定
- 长图在帖子详情媒体区不显示得很怪

### 4. 视频封面和首开体验

需要保证：

- 视频缩略图 / 封面稳定
- 无封面时显示统一视频占位
- Viewer 首开视频不黑屏
- 视频 loading / error / retry 状态完整
- 图片 / 视频混合切换不串状态
- 播放控制条保持独立，不跟随缩放层

### 5. Viewer 边界

需要区分：

- 照片流 Viewer
- 帖子内 Viewer
- 系统媒体 Viewer

系统媒体 Viewer 不应出现：

- 评论
- 加载原图
- 所属帖子
- 帖子跳转
- 帖子专属底部操作

## 不做内容

- 不做完整新增媒体体系
- 不做完整新增帖子体系
- 不做 OSS
- 不做正式云端存储
- 不重构 fake/real 总架构
- 不删除 FAKE repository
- 不强制默认 REAL
- 不做 UI 大精修
- 不做评论系统重构
- 不改回收站业务规则

## 文档要求

更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

## 验收

1. REAL 图片 Viewer 默认显示预览图。
2. 有 originalUrl 时加载原图按钮可用。
3. 无 originalUrl 时不显示无意义原图按钮。
4. 原图加载失败保留预览图。
5. 左右切换媒体时原图状态不串。
6. Viewer 所属帖子入口稳定。
7. 多帖子归属展示合理。
8. 点击所属帖子跳转稳定。
9. 系统媒体 Viewer 不显示帖子专属功能。
10. 长图可合理缩放 / 平移。
11. 图片不和顶部 / 底部操作区重叠。
12. 视频封面或占位稳定。
13. Viewer 首开视频不黑屏。
14. 图片 / 视频混合切换状态不串。
15. 帖子详情媒体区画布比例稳定。
16. FAKE 模式主流程正常。
17. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.5 Implementation Notes

- Viewer 预览图优先级明确为 `thumbnailUrl -> mediaUrl -> originalUrl`。
- Viewer 原图按钮只在 `originalUrl` 存在且与当前预览图不是同一地址时显示；没有独立原图资源时不再假装支持“加载原图”。
- 原图加载状态继续按 `mediaId` 隔离，`loading / loaded / failed` 不会串到其他媒体。
- Viewer 所属帖子入口已从占位文案改为可跳转的帖子 route；单帖子直接跳，多帖子通过底部 sheet 选择。
- 帖子内 Viewer 现在会把当前帖子 route 透传给共享 Viewer，保证“所属帖子”能回到正确帖子详情。
- 视频首开时优先显示稳定封面；没有封面时显示统一视频占位，不直接黑屏等待播放器。
