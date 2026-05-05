# Current Task: Stage 12.7-Hotfix - 上传媒体 / 系统媒体导入真实修复

## 背景

当前原图加载已暂时认为修好。现在开始专注上传媒体和系统媒体导入链路。

真机问题：

1. 底部导航加号按钮中的“上传媒体”选择媒体并确认后，提示“正在后台加载，加载好会出现在照片流里”，但媒体实际没有出现在照片流。
2. 该流程没有明确失败提示，疑似静默失败。
3. 系统媒体区选中图片后点击“导入到 App”，提示上传失败。
4. 上传 / 导入后的媒体需要和原图加载、评论、帖子、照片流、Viewer、Gear Edit、删除 / 回收站接轨。

## 目标

1. 底部加号上传媒体真实可用。
2. 系统媒体导入到 App 真实可用。
3. 上传成功后媒体出现在照片流。
4. 上传失败有明确中文提示和 debug 日志。
5. 新上传媒体能进入 Viewer。
6. 新上传媒体的原图加载逻辑正确。
7. 新上传媒体和评论、帖子归属、删除 / 恢复逻辑不冲突。
8. FAKE / REAL 上传链路隔离。

## 检查范围

- 底部导航加号上传媒体入口
- 系统媒体区导入到 App
- 系统媒体发成新帖子
- 系统媒体加入已有帖子
- Gear Edit 添加媒体
- Android contentUri / MediaStore / Photo Picker 读取
- Multipart 上传 repository
- upload ViewModel / task state
- media repository / mapper
- 照片流刷新
- Viewer 媒体列表
- 原图加载状态机
- 评论入口
- 帖子归属 relatedPosts / postIds
- 删除 / 回收站

## 关键要求

### 1. 业务语义

底部加号“上传媒体”和系统媒体“导入到 App”都应该把选中的系统媒体导入到 App 媒体库。

导入成功后：

- 媒体出现在照片流
- 可以打开 Viewer
- 可以删除 / 恢复
- 可以后续加入帖子或发成新帖子
- 如果没有帖子归属，所属帖子区域显示为空或不显示
- 评论入口按当前业务规则安全展示

### 2. Android URI 上传

需要正确处理：

- content:// URI
- file:// URI
- mimeType
- displayName
- file size
- inputStream
- multipart body
- Android 13/14 Photo Picker / 权限模型
- URI 失效
- 用户取消选择

禁止：

- 把 contentUri 当 file path 直接用
- URI 失效后静默失败
- 上传失败后插入假媒体
- 成功提示和真实状态不一致

### 3. Server 上传契约

上传成功后 Media DTO 需要包含：

- mediaId
- type
- mimeType
- thumbnailUrl
- mediaUrl
- originalUrl
- videoUrl
- width
- height
- duration
- createdAt
- postIds / relatedPosts

如无独立 originalUrl，Android 不显示加载原图按钮。

### 4. 上传任务状态

每个上传任务需要有：

- pending
- uploading
- success
- failed
- canceled
- retry

要求：

- 多文件不串状态
- 成功才刷新照片流
- 失败有中文提示
- 失败可重试或重新选择
- 取消后清理任务
- 不再只显示“后台加载”然后无结果

### 5. 新媒体接轨

新上传媒体必须接轨：

- 照片流
- Viewer
- 原图加载
- 评论系统
- 帖子归属
- 加入已有帖子
- 发成新帖子
- Gear Edit
- 删除 / 回收站

### 6. 静默失败治理

debug 日志至少包含：

- entry source
- uri
- mimeType
- displayName
- size
- endpoint
- http status
- server error body
- returned mediaId
- returned urls
- refresh result

UI 至少显示中文兜底错误，例如：

- 无法读取所选媒体
- 没有媒体访问权限
- 上传失败，请重试
- 文件过大
- 网络不可用
- 服务器没有返回媒体信息

## 不做内容

- 不做 OSS
- 不做云端存储
- 不重构 fake/real 总架构
- 不删除 FAKE
- 不强制默认 REAL
- 不做 UI 大精修
- 不改回收站业务规则

## 验收

1. 底部加号上传图片成功后，照片流出现该图片。
2. 底部加号上传视频成功后，照片流出现该视频或稳定占位。
3. 系统媒体区导入到 App 成功后，照片流出现该媒体。
4. 上传失败时不插入假媒体。
5. 上传失败时有中文提示。
6. 上传失败时 debug 日志能看到真实失败原因。
7. 多个媒体连续上传状态不串。
8. 上传取消后不产生脏数据。
9. 新上传媒体能打开 Viewer。
10. 新上传媒体原图按钮逻辑正确。
11. 新上传媒体评论入口不崩。
12. 新上传媒体无帖子归属时所属帖子区域不崩。
13. 新上传媒体加入已有帖子后，帖子详情 / Gear Edit 刷新。
14. 新上传媒体发成新帖子后，新帖子详情可打开。
15. 新上传媒体删除 / 恢复后照片流刷新。
16. FAKE 模式主流程正常。
17. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
## Stage 12.7 Hotfix Update - 2026-05-05

- Bottom `+ -> 上传媒体` and System Media `导入到 App` now share the same REAL upload path through `LocalSystemMediaBridgeRepository`.
- Android reads selected `content://` media through `ContentResolver`, resolves display name / mime type / dimensions, uploads bytes as multipart field `file`, and never treats a `contentUri` as a filesystem path.
- Upload tasks now have visible per-file states at the app root: waiting, uploading, success, failure, cancelled, retry.
- Upload requests are bounded by explicit local-read, token, and multipart-upload timeouts. Failures publish Chinese UI messages and DEBUG logs under `SystemMediaUpload`.
- Upload success immediately notifies the REAL backend mutation bus with returned `mediaId`, so photo feed / viewer data refresh from the server instead of waiting for a fake placeholder.
- Import-to-App is an orphan-media import: it does not require `postId`; later add-to-post and create-post flows use returned uploaded media ids.
- Android `assembleDebug` passed after this update.
