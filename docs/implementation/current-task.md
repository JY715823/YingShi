# Current Task: Stage 12.4 - 架构减重与 FAKE / REAL 整理

## 背景

Stage 12.1-12.3 已完成 REAL 媒体展示、Viewer 图片/视频、主链路状态一致性、上传/删除/恢复/撤销等复杂链路压测修复。

现在进入 Stage 12.4。本阶段不新增大功能，重点是整理前几轮留下的重复逻辑、临时状态、页面内手写刷新和 FAKE / REAL 分叉混乱点。

## 目标

1. 媒体 URL 归一化逻辑收口。
2. 图片 / 视频类型判断收口。
3. Viewer 图片 / 视频状态模型收口。
4. mutation / refresh 策略收口。
5. FAKE / REAL repository 边界清晰。
6. DTO 不直接进入 Compose UI。
7. loading / empty / error / retry 组件和文案尽量复用。
8. 清理无用临时代码、过期注释、重复 mapper。
9. 保持 Stage 12.1-12.3 功能不回退。

## 检查范围

### 1. 媒体 URL 与类型判断

检查：

- 是否还有页面内手写 baseUrl 拼接
- 是否还有重复 URL helper
- thumbnailUrl / mediaUrl / originalUrl / videoUrl 优先级是否一致
- mimeType / type / 后缀兜底是否集中
- 视频是否还被误当图片加载

### 2. Viewer 状态

检查：

- 图片原图 loading / error / loaded 状态是否重复
- 视频 playing / progress / error / loading 状态是否重复
- 图片 / 视频切换状态是否仍有页面局部 hack
- 系统媒体 Viewer 是否误复用帖子 Viewer 专属状态
- 控制层是否独立于缩放层

### 3. mutation / refresh

检查：

- 编辑、上传、删除、恢复、撤销、评论是否使用清晰刷新策略
- 是否存在多个 ViewModel 各自手写相同刷新逻辑
- 是否有 dirty flag / event / invalidation key 可统一
- 失败后是否有回滚或重新拉取
- FAKE / REAL 是否使用不同缓存 key 或不同数据源

### 4. FAKE / REAL 边界

检查：

- FAKE repository 不被 REAL 状态污染
- REAL repository 不依赖 fake-only 字段
- UI model 兼容两种模式
- DTO 不直接进入 Compose UI
- mapper 层职责清晰

### 5. 通用状态组件

整理：

- loading
- empty
- error
- retry
- URL missing
- upload failed
- network failed
- permission denied

## 不做内容

- 不新增大功能
- 不删除 FAKE repository
- 不强制默认 REAL
- 不重构整个架构
- 不做 OSS
- 不做 UI 大精修
- 不改业务规则
- 不大改 Server

## 文档要求

更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

## 验收

1. 没有页面内重复手写媒体 URL 拼接。
2. 媒体类型判断入口清晰。
3. Viewer 图片 / 视频状态没有明显重复 hack。
4. 编辑、上传、删除、恢复、撤销、评论刷新策略清晰。
5. FAKE / REAL 数据源边界不混。
6. DTO 不直接进入 Compose UI。
7. 通用 loading / empty / error / retry 能复用。
8. Stage 12.1-12.3 主要功能不回退。
9. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.4 Implementation Notes

- Android media URL normalization, cache keys, auth headers, and image request construction are now expected to go through the shared media support layer instead of page-local helpers.
- Image and video source detection is now centralized around the shared media support functions so feed thumbnails, Viewer preview, and video poster fallback use the same decision path.
- Viewer image/video state cleanup now prefers shared Viewer state helpers for failure copy, playback labels, and retry reset behavior instead of duplicating those strings per page.
- REAL mutation refresh now prefers the shared content/post/comment helper entrypoints instead of each page rebuilding the same scope set inline.
- REAL loading / empty / error / retry cards are being consolidated into reusable backend state card components; new REAL pages should reuse them instead of creating new one-off variants.
