# Current Task: Stage 12.1 第三轮 - Viewer 视频播放稳定化与系统媒体控制条修正

## 背景

Stage 12.1 前两轮已处理 REAL 媒体 URL、缩略图、图片 Viewer、原图状态与沉浸式背景。本轮进入视频查看态收口。

当前发现一个系统媒体查看态问题：视频暂停 / 播放按钮会跟随视频缩放而缩放、改变位置。视频画面可以缩放 / 平移，但播放控制层不应该参与缩放变换。控制条应固定在媒体画布内部，形成标准视频播放样式。

## 本轮目标

让以下 Viewer 的视频查看态稳定可用：

1. 照片流 Viewer
2. 帖子内 Viewer
3. 系统媒体 Viewer
4. 复用的视频查看组件

## 范围

### 1. 视频基础播放稳定化

需要支持：

- 视频基础播放
- 暂停
- 重新播放
- 加载中状态
- 播放失败提示
- URL 为空安全占位
- 网络失败安全提示
- 视频和图片左右切换不崩溃
- 切离视频时暂停或释放
- 退出 Viewer 时暂停或释放

### 2. 视频内容层与控制层分离

必须修正：

- 播放 / 暂停按钮跟随缩放
- 播放 / 暂停按钮跟随平移
- 进度条跟随视频画面位移
- 控制层被视频 transform 污染

要求：

- 只有视频画面参与缩放 / 平移
- 播放控制条不参与缩放 / 平移
- 控制条作为 overlay 独立绘制
- 控制条固定在媒体画布内部左下角
- 控制条随 Viewer 布局稳定，而不是随视频内容变换

### 3. 标准视频控制条样式

系统媒体 Viewer、照片流 Viewer、帖子内 Viewer 尽量统一。

控制条要求：

- 左下角放播放 / 暂停按钮
- 进度条放在按钮右侧
- 可以显示当前时间 / 总时长
- 可以显示缓冲 / loading 状态
- 控制条位于媒体画布内部
- 控制条不和顶部区域重合
- 控制条不和底部信息区、小白条、评论气泡、底部操作区重合
- 控制条背景可使用半透明沉浸式底
- 控制条按钮点击区域足够稳定

### 4. 照片流 Viewer 视频

需要保证：

- 视频能播放 / 暂停
- 控制条在媒体画布内部
- 不压评论气泡
- 不压底部操作区
- 不压加载原图 / 所属帖子等按钮
- 切到图片时视频停止
- 切到另一个视频时旧播放器释放或重置
- 删除、评论、所属帖子、关闭等操作不被破坏

### 5. 帖子内 Viewer 视频

需要保证：

- 视频能播放 / 暂停
- 左右只切当前帖子媒体
- 控制条不压底部分段小白条
- 控制条不压帖子内 Viewer 底部操作区
- 切换媒体时播放状态不串
- 返回帖子详情时释放播放器

### 6. 系统媒体 Viewer 视频

需要重点保证：

- 系统媒体视频能基础查看 / 播放
- 播放按钮不再跟着视频缩放
- 进度条不再跟着视频缩放
- 控制条固定在媒体画布内部左下角
- 视频画面仍支持缩放 / 平移
- 默认信息区只显示媒体名称、类型、日期
- 不出现评论、加载原图、所属帖子
- 右上角菜单和底部操作弹窗不被破坏
- 移到系统回收站流程不被破坏

### 7. 图片 / 视频混合切换状态隔离

需要避免：

- A 视频播放中，切到 B 图片后仍有声音
- A 视频 loading，切到 B 图后 B 图显示 loading
- A 视频 error，切到 B 视频后 error 残留
- A 视频播放进度串到 B 视频
- 视频播放器对象泄漏
- 返回页面后后台继续播放

可以按 mediaId / stable key 管理播放状态，也可以在切换时明确 reset / release。

### 8. 错误态统一

需要补齐：

- loading
- empty
- error
- retry
- media URL missing
- video load failed
- unsupported video
- network failed

要求：

- 中文提示
- 不闪退
- 可重试时提供重试入口
- 不影响图片 Viewer 已完成逻辑

## 不做内容

本轮不要做：

- 上传流程重做
- OSS
- 复杂转码
- 真实缓存扫描
- fake/real 架构大重构
- 删除 FAKE repository
- 强制默认 REAL
- UI 大精修
- 后端大改
- 评论系统改造
- 回收站业务规则调整

## 文档要求

根据实际改动更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

如 Server 未修改，最终输出需要明确说明 Server 未修改。

## 验收

1. FAKE 模式照片流 Viewer 视频仍可打开。
2. REAL 模式照片流 Viewer 视频可播放 / 暂停。
3. 照片流 Viewer 视频控制条在媒体画布内部。
4. 照片流 Viewer 视频切到图片后不继续播放。
5. FAKE 模式帖子内 Viewer 视频仍可打开。
6. REAL 模式帖子内 Viewer 视频可播放 / 暂停。
7. 帖子内 Viewer 左右只切当前帖子媒体。
8. 帖子内 Viewer 视频控制条不压底部分段小白条。
9. 系统媒体 Viewer 视频可播放 / 暂停。
10. 系统媒体 Viewer 视频画面可缩放 / 平移。
11. 系统媒体 Viewer 播放 / 暂停按钮不跟随缩放。
12. 系统媒体 Viewer 进度条不跟随缩放。
13. 系统媒体 Viewer 控制条固定在媒体画布内部左下角。
14. 系统媒体 Viewer 默认信息区只显示名称、类型、日期。
15. 系统媒体 Viewer 不出现评论、加载原图、所属帖子。
16. 切换视频 / 图片时 loading、error、progress、playing 状态不串。
17. 退出 Viewer 后视频停止播放。
18. 视频 URL 为空时不闪退，有中文提示。
19. 视频加载失败时不闪退，有中文提示或重试。
20. 图片 Viewer 前两轮功能不被破坏。
21. 删除、评论、所属帖子、系统媒体菜单、系统回收站入口不被破坏。
22. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.1 third-round implementation note

- App-content Viewer video now resolves playable URLs with `videoUrl -> mediaUrl -> originalUrl`, while poster display still stays preview-first and tolerant of missing thumbnails.
- Photo-flow Viewer and in-post Viewer keep video playback state isolated by `mediaId`; switching media pauses the previous player and prevents loading, error, progress, and playing state from leaking across items.
- System-media Viewer now separates the transformed media layer from the fixed control layer: only the video canvas zooms or pans, while the playback control bar stays anchored inside the media canvas at the lower-left area.
- Video loading, URL-missing, and playback-failure states now use Chinese fallback copy and safe retry entry points instead of crashing the Viewer.
- This round stays inside the Stage 12.1 stabilization scope and does not introduce upload rework, OSS, transcoding, or server-side productization.
