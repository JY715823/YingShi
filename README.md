# YingShi Android

映时 Android 客户端。当前项目已经从早期壳层推进到“照片核心链路可用、真实后端可切换、系统媒体导入与回收站闭环可联调”的阶段。

## 当前状态

- 默认仓库模式：`REAL`
- 默认后端地址：`http://10.106.3.193:8080/`
- UI 技术栈：Kotlin、Jetpack Compose、Material 3
- 媒体能力：Coil 图片/视频缩略图、Android Photo Picker、MediaStore 查询与系统回收站请求
- 网络能力：Retrofit + Gson、JWT 会话、FAKE / REAL Repository 切换

项目仍保留 FAKE 数据链路，用于离线调试、交互验证和避免后端不可用时阻塞 UI 精修。

## 已实现功能

完整清单见 [已实现功能清单](docs/implementation/implemented-features.md)。

摘要如下：

- 全局 App 壳层：主页、照片、生活、我的四个一级入口，中央新增按钮，全屏业务页自动隐藏底部栏。
- 照片模块：照片流、相册/帖子目录、回收站三个二级入口。
- 照片流：全局媒体去重、按时间分区、密度切换、时间滑条、多选、发成新帖子、加入已有帖子、移入回收站。
- Viewer：图片/视频查看、左右切换、缩放、长图适配、原图加载、评论入口、打开所属帖子、删除/回收站链路。
- 相册与帖子：相册列表、帖子列表、帖子详情、帖子评论、帖子编辑、媒体管理、封面与排序。
- 新建帖子：从底部新增、照片流选择、系统媒体选择、系统媒体 Viewer 进入，支持标题、摘要、相册、封面和后台上传。
- 系统媒体：MediaStore 图片/视频列表、筛选、时间分区、时间滑条、多选、导入 App、发新帖、加入已有帖、移入系统回收站。
- 传输中心：上传任务列表、剩余任务 badge、状态汇总、失败清理/重试入口、任务结果打开媒体。
- 回收站：帖子删除、帖子内媒体移除、媒体系统删三类记录，支持详情、恢复、移出、24h 可撤销分类。
- 通知与设置：通知中心、通知详情、通知目标跳转、设置页、缓存管理、后端联调诊断。
- 后端接入：auth、album、post、media、comment、trash、upload、health 契约与真实 Repository。

## 文档入口

建议按下面顺序阅读：

1. [已实现功能清单](docs/implementation/implemented-features.md)
2. [当前任务](docs/implementation/current-task.md)
3. [路线图](docs/implementation/roadmap.md)
4. [产品 PRD](docs/product/album-prd-v2.md)
5. [UI 设计说明](docs/design/ui-design-v2.md)
6. [前后端联调指南](docs/integration/frontend-backend-testing-guide.md)
7. [API 契约总览](docs/contracts/api-overview.md)
8. [协作说明](AGENTS.md)

## 仓库结构

- `app/src/main/java/com/example/yingshi/app`
  应用状态、全局路由、全屏覆盖页与底部导航控制。
- `app/src/main/java/com/example/yingshi/navigation`
  一级导航和照片模块二级导航定义。
- `app/src/main/java/com/example/yingshi/feature/photos`
  照片、相册、帖子、Viewer、系统媒体、上传、回收站、通知、设置等核心功能。
- `app/src/main/java/com/example/yingshi/data/remote`
  Retrofit API、DTO、Mapper、后端配置和认证拦截。
- `app/src/main/java/com/example/yingshi/data/repository`
  FAKE / REAL Repository 契约与实现。
- `app/src/main/java/com/example/yingshi/ui`
  全局主题、设计 token、壳层组件。
- `docs`
  产品、设计、实现、契约和联调文档。

## 运行

Windows:

```powershell
cd E:\Study\Android\YingShiApp\YingShi
.\gradlew.bat assembleDebug
```

只做 Kotlin 编译检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

也可以直接用 Android Studio 打开 `YingShi` 并运行 `app`。

## 后端联调

1. 启动 `YingShi-Server`。
2. 在 App 中进入 `我的 -> 设置 -> 后端联调诊断`。
3. 确认 `baseUrl`、FAKE / REAL 模式、登录状态。
4. 依次执行 health、login、albums、media、comments、trash、upload 等 smoke check。

如果后端不可用，可以在诊断页切回 `FAKE` 模式继续调 UI。
