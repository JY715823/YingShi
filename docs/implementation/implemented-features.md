# 已实现功能清单

更新时间：2026-05-25

本文档按当前代码状态整理 `YingShi Android` 已完成能力。若旧版 PRD、路线图或联调记录与当前实现不一致，以本文件和现有代码为准。

## 1. 全局壳层与导航

- 一级导航已经稳定为 `首页 / 照片 / 生活 / 我的`
- 照片模块顶部二级导航已经稳定为 `照片 / 相册 / 回收站`
- 中央 `+` 入口可进入内容创建相关流程
- Viewer、帖子详情、帖子编辑、系统媒体、回收站详情、设置、缓存管理、后端联调诊断等全屏页会自动隐藏底部栏
- 应用启动时会初始化调试配置、恢复本地会话，并在存在 token 时调用 `/api/auth/me` 校验登录态
- token 无效或后端返回 `401` 时，会清理本地会话并回到登录页
- `RepositoryProvider` 已统一管理 `FAKE / REAL` Repository 切换
- 切换 `Base URL` 会清空旧 token、递增 sessionVersion 并重建 Retrofit graph

## 2. 认证、我的与设置

- 真实认证链路已接入：
  - `POST /api/auth/login`
  - `POST /api/auth/refresh-token`
  - `GET /api/auth/me`
  - `POST /api/auth/logout`
  - `PATCH /api/auth/me/profile`
- `AuthSessionManager` 会把 access token / refresh token 持久化到 `SharedPreferences`
- 登录页支持真实账号密码登录，当前联调用种子账号是 `demo.a` / `demo.b`
- `我的` 页面已经可展示：
  - 当前用户卡片
  - 共享空间卡片
  - 搭子卡片
  - 当前模式、账号状态、后端地址
  - 退出登录入口
- 个人主页与编辑资料页已经打通昵称、简介的查看与保存
- 设置页已经提供浏览偏好、缓存管理和后端联调诊断入口
- 后端联调诊断页当前支持：
  - 查看和编辑 `Base URL`
  - 使用模拟器 `10.0.2.2` 预设
  - 使用 `127.0.0.1` 预设
  - 一键“保存并重登”默认 demo 账号
  - 清理登录缓存
  - 切换 `FAKE / REAL`
  - 执行最小 `health` 检查
  - 查看最近一次联调结果

## 3. 照片主链路

- 照片流同时支持 `FAKE / REAL` 两套数据源
- `REAL` 模式下已经通过后端 media feed 拉取 App 内容区媒体
- 照片流按媒体维度全局去重，同一媒体挂多个帖子也只显示一次
- 已支持按时间倒序分组展示
- 已支持 2~6 列密度切换
- 已支持时间滑条快速定位，且当前交互规则是“只允许拖动 thumb，不允许点击轨道跳转”
- 已支持长按进入多选、横向拖拽扩选、边缘自动滚动
- 已支持多选后：
  - 发成新帖子
  - 加入已有帖子
  - 移入回收站

## 4. Viewer

- 已支持从照片流、帖子详情、系统媒体、传输中心等入口打开 Viewer
- 已支持图片 / 视频混排查看
- 已支持左右切换当前媒体
- 图片已支持缩放、拖拽、长图适配
- 真实模式下已支持通过后端媒体文件接口加载 `preview / original / cover`
- 原图加载状态按单个 `mediaId` 独立维护，不会串到下一张
- 视频已支持封面、播放、暂停和失败兜底
- 已支持查看媒体评论
- 已支持从媒体打开所属帖子
- 已支持从 Viewer 删除真实媒体并进入回收站语义
- 已支持进入缓存管理入口

## 5. 相册、帖子、评论与编辑

- 已支持相册列表和相册下帖子列表
- 已支持帖子通用列表接口的 REAL Repository 对接：`GET /api/posts`
- 已支持帖子详情页：基础信息、媒体区域、评论区域
- 已支持帖子评论和媒体评论两条评论链路
- 已支持评论创建、编辑、删除
- 已支持发新帖：
  - 从 App 内已有媒体创建
  - 从系统媒体创建
  - 从系统媒体 Viewer 创建
- 已支持帖子编辑基础信息：
  - 标题
  - 摘要
  - 展示时间
  - 相册归属
- 已支持帖子媒体管理：
  - 设置封面
  - 调整顺序
  - 向帖子追加媒体
  - 从帖子移除媒体
  - 删除整个帖子

## 6. 系统媒体、上传与传输中心

- 已接入 `MediaStore` 图片 / 视频查询
- 已支持系统媒体筛选、按时间分组、时间滑条、多选和 Viewer
- 已支持从系统媒体执行：
  - 导入到 App
  - 发成新帖子
  - 加入已有帖子
  - 移入 Android 系统回收站
- 已支持把系统媒体操作转成传输任务并在传输中心统一展示
- 传输中心已支持展示任务来源、状态、剩余数量和结果打开
- 真实上传链路已接入：
  - `POST /api/uploads/token`
  - `POST /api/uploads/{uploadId}/file`
  - `GET /api/uploads/{uploadId}`
  - `POST /api/uploads/{uploadId}/confirm`
  - `POST /api/uploads/{uploadId}/cancel`
- 当前 `confirmUpload` 主要作为上传任务收尾 / 状态确认接口使用，不会再次创建媒体

## 7. 回收站

- 已接入三类回收站记录：
  - `postDeleted`
  - `mediaRemoved`
  - `mediaSystemDeleted`
- 已支持回收站列表、详情、恢复、移出、永久删除、撤销移出、待清理列表
- 已支持 `24h 可撤销` 的待清理语义
- 已支持真实模式回收站详情按 `sourceMediaId / relatedMediaIds` 渲染已删内容预览
- 已支持帖子删除、帖子内移除媒体、全局系统删除媒体三条入口统一汇入回收站

## 8. 生活模块

- `生活` 已不再只是空白占位页
- `纪念日` 入口已移除
- 已有本地 Room 驱动的记账模块，包含：
  - 账本
  - 交易记录
  - 分类
  - 账户 / 资产
  - 预算
  - 统计
  - 回收站
- 已有聊天记录查看器，支持导入 `QCE ZIP`
- 导入后的聊天记录会落本地 Room，支持离线浏览

## 9. 已接入真实后端的 Repository 能力

- `RealAuthRepository`
  - `login`
  - `refreshToken`
  - `logout`
  - `getCurrentUser`
  - `updateCurrentUserProfile`
- `RealAlbumRepository`
  - `getAlbums`
  - `getAlbumPosts`
- `RealMediaRepository`
  - `getMediaFeed`
  - `getMediaFeedPage`
  - `deleteMediaFromPost`
  - `systemDeleteMedia`
- `RealPostRepository`
  - `getPosts`
  - `getPostDetail`
  - `createPost`
  - `updatePostBasicInfo`
  - `setPostCover`
  - `updatePostMediaOrder`
  - `addMediaToPost`
  - `deletePost`
- `RealCommentRepository`
  - 帖子评论 / 媒体评论列表
  - 创建、编辑、删除评论
- `RealTrashRepository`
  - 列表、详情、恢复、移出、永久删除、撤销移出、待清理列表
- `RealUploadRepository`
  - 创建上传 token
  - multipart 文件上传
  - `confirmUpload`
  - `cancelUpload`
  - `getUploadTask`

## 10. 当前仍保留的占位或限制

- `首页` 仍主要是壳层入口，尚未接真实业务
- 通知中心当前仍以本地 fake 数据为主，尚未接真实服务端通知源
- 头像上传与真实头像图片展示的 Android UI 尚未实现
- refresh-token 虽已在 `RealAuthRepository` 提供，但全局自动续期与失败请求重放策略仍未完整接好
- 大规模离线同步、冲突合并、发布级错误治理仍未进入当前阶段
