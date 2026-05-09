# Current Task: App 远程视频播放流畅度优化

## 背景

照片流和 Viewer 已经支持视频媒体，但 App 内容区的视频来自后端远程文件，链路不同于系统媒体本地 `Uri`。旧实现中 App Viewer 使用 `VideoView`，照片流远程预览使用 `MediaPlayer`，对网络视频的首帧、缓冲、seek 和 Range 读取支持都不够稳定。

## 目标

1. App 照片流远程视频预览改用更适合网络播放的 Media3 / ExoPlayer。
2. App 视频 Viewer 改用 Media3 / ExoPlayer，保留现有播放/暂停、进度条、加载、失败重试、重播和控制层逻辑。
3. 系统媒体本地视频播放链路保持不变，避免误伤本地 `Uri` 播放。
4. 列表视频预览继续静音、不请求音频焦点、只播放主要可见视频。
5. 配合后端 Range / partial content，优化拖动进度条后的按需读取。

## 范围

- `InlineVideoAutoPlay.kt`
- `PhotoViewerScreen.kt`
- Gradle Media3 依赖
- 本文档

## 不做内容

- 不改评论入口、所属帖子、删除、加载原图/原文件等 Viewer 业务菜单。
- 不改帖子详情、回收站、上传中心等无关模块。
- 不引入转码、HLS、OSS 或后台预处理大改。
- 不改变系统媒体 Viewer 的本地视频播放实现。

## 验收

1. REAL 模式下 App 照片流视频首次播放更快。
2. App 视频 Viewer 拖动进度条后等待明显缩短，不长时间黑屏或卡死。
3. 系统媒体视频播放体验不被破坏。
4. 列表视频预览仍静音，不暂停手机后台音乐。
5. Viewer 播放/暂停、进度条、自动隐藏控制层正常。
6. Android `assembleDebug` 通过。
