# YingShi Android

映时 Android 客户端。当前代码已经从早期壳层推进到“认证可用、照片主链路可用、系统媒体导入可用、回收站闭环可联调、我的/生活模块已有真实功能”的阶段。

## 当前状态

- `debug` / `profile` / `optimizedDebug` 默认仓库模式都是 `REAL`
- `debug` 默认后端地址是 `http://10.106.3.193:8080/`
- 运行时仍支持在 `我的 -> 设置 -> 后端联调诊断` 中切换 `FAKE / REAL`
- 登录会话会持久化到 `SharedPreferences`，应用启动会自动校验 `/api/auth/me`
- 保留 `FAKE` 数据链路，便于离线调 UI、回归交互和隔离后端问题

## 最新进度概览

- App 壳层已经稳定为四个一级入口：`首页 / 照片 / 生活 / 我的`
- 照片模块已经覆盖 `照片 / 相册 / 回收站` 三个二级入口，以及 Viewer、帖子详情、帖子编辑、评论、删除与恢复链路
- 系统媒体链路已经接入 `MediaStore`、系统回收站请求、导入 App、发新帖、加入已有帖子和传输中心
- 认证链路已经接入真实后端：登录、refresh-token、登出、当前用户、编辑个人资料、共享空间/搭子信息展示
- `我的` 页面、设置、缓存管理、后端联调诊断都已经可用
- `生活` 当前保留记账模块和聊天记录查看器，`纪念日` 入口已经移除
- REAL Repository 已对齐后端新增接口：`GET /api/posts`、`POST /api/auth/refresh-token`、上传任务 `status / confirm / cancel`

完整清单见 [已实现功能清单](E:/Study/App/YingShi/docs/implementation/implemented-features.md)。

## 当前前后端对齐情况

已经对齐的真实能力：

- `auth`：`login / refresh-token / me / logout / me/profile`
- `albums`：相册列表、相册帖子列表
- `posts`：列表、详情、创建、更新、封面、排序、加媒体、删除
- `media`：照片流、媒体文件、系统删除、帖子内移除
- `comments`：帖子评论和媒体评论的增删改查
- `trash`：列表、详情、恢复、移出、永久删除、撤销移出、待清理列表
- `upload`：上传 token、multipart 文件上传、上传任务状态、confirm、cancel

后端已提供、但 Android UI 还没有完全消费的能力：

- 头像上传 / 头像读取：`POST /api/auth/me/avatar`、`GET /api/auth/avatar/{userId}`
- 通知接口：`GET /api/notifications`、详情、已读、全部已读

当前仍有明确缺口：

- 通知中心当前仍以本地 fake 数据为主，尚未接真实服务端通知源
- 头像上传与真实头像图片展示的 Android UI 仍未接入
- refresh-token 虽已在 `RealAuthRepository` 接通，但全局自动续期与失败请求重放策略仍未完整接好
- 远程内容离线同步仍未完成

## 文档入口

建议按下面顺序阅读：

1. [已实现功能清单](E:/Study/App/YingShi/docs/implementation/implemented-features.md)
2. [当前任务](E:/Study/App/YingShi/docs/implementation/current-task.md)
3. [路线图](E:/Study/App/YingShi/docs/implementation/roadmap.md)
4. [前后端联调指南](E:/Study/App/YingShi/docs/integration/frontend-backend-testing-guide.md)
5. [API 契约总览](E:/Study/App/YingShi/docs/contracts/api-overview.md)
6. [Auth 契约](E:/Study/App/YingShi/docs/contracts/auth-api.md)
7. [Upload 契约](E:/Study/App/YingShi/docs/contracts/upload-api.md)
8. [Notification 契约](E:/Study/App/YingShi/docs/contracts/notification-api.md)
9. [Trash 契约](E:/Study/App/YingShi/docs/contracts/trash-api.md)
10. [协作说明](E:/Study/App/YingShi/AGENTS.md)

## 仓库结构

- `app/src/main/java/com/example/yingshi/app`
  应用状态、全局路由、全屏覆盖页、启动鉴权和壳层控制。
- `app/src/main/java/com/example/yingshi/navigation`
  一级导航与照片模块二级导航定义。
- `app/src/main/java/com/example/yingshi/feature/photos`
  照片流、相册、帖子、Viewer、系统媒体、上传、回收站、设置、诊断等核心功能。
- `app/src/main/java/com/example/yingshi/feature/me`
  我的、个人主页、编辑资料、共享空间与搭子展示。
- `app/src/main/java/com/example/yingshi/feature/life`
  生活入口、记账模块、聊天记录查看器。
- `app/src/main/java/com/example/yingshi/data/remote`
  Retrofit API、DTO、Mapper、后端配置、鉴权拦截和会话管理。
- `app/src/main/java/com/example/yingshi/data/repository`
  `FAKE / REAL` Repository 契约与实现。
- `docs`
  产品、设计、实现进度、契约和联调文档。

## 运行

Windows:

```powershell
cd E:\Study\App\YingShi
.\gradlew.bat assembleDebug
```

只做 Kotlin 编译检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

也可以直接用 Android Studio 打开 `E:\Study\App\YingShi` 并运行 `app`。

## 后端联调

1. 启动 `E:\Study\App\YingShi-Server`
2. 在 App 中进入 `我的 -> 设置 -> 后端联调诊断`
3. 确认 `Base URL` 指向当前后端
4. 点击 `保存并重登`，确认默认 demo 账号自动登录成功
5. 点击 `检查健康`
6. 将模式切到 `REAL`，再重新打开需要验证的页面
7. 在真实页面里验证照片流、相册、帖子详情、回收站、上传和“我的”资料链路

如果后端不可用，可以在诊断页切回 `FAKE` 模式继续调 UI。
