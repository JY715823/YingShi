# Yingshi Android

映世 Android 客户端仓库。

当前仓库处于 `Stage 0 / Bootstrap` 阶段，目标是先把工程壳层、阶段文档和 AI 协作基线搭好，再逐步推进照片、相册、帖子、Viewer 与评论等后续模块。

## 先读这些文档

按下面顺序阅读：

1. `docs/product/album-prd-v2.md`
2. `docs/design/ui-design-v2.md`
3. `docs/implementation/non-negotiables.md`
4. `docs/implementation/current-task.md`
5. `AGENTS.md`

## 当前阶段

当前只做本地优先的 UI 壳层与仓库初始化：

- 仓库协作文档
- Android 工程基础结构
- 全局底部导航占位
- 照片模块顶部二级导航占位
- 占位页面与最小主题组织

当前明确不做：

- 真实后端接入
- Room / Retrofit / MediaStore
- 真实评论系统
- 真实 Viewer 逻辑
- 真实系统媒体工具区

## 仓库结构

当前推荐关注这些目录：

- `app/src/main/java/com/example/yingshi/app`
  应用壳层与根级导航
- `app/src/main/java/com/example/yingshi/feature`
  按功能拆分的占位页面与后续 feature 入口
- `app/src/main/java/com/example/yingshi/ui/theme`
  最小主题与设计 token 基线
- `docs/product`
  产品规则与 PRD
- `docs/design`
  UI 总稿与设计方向
- `docs/implementation`
  roadmap、当前任务、实现边界

## 开发约束

- Kotlin
- Jetpack Compose
- Material 3
- 优先保留可运行壳层
- 当前阶段使用假数据和占位结构
- 避免与当前 Stage 无关的重构

## 如何运行

Windows:

```powershell
.\gradlew.bat assembleDebug
```

也可以直接用 Android Studio 打开项目并运行 `app`。

## 阶段文档

- 产品 PRD：`docs/product/album-prd-v2.md`
- UI 总稿：`docs/design/ui-design-v2.md`
- 非协商规则：`docs/implementation/non-negotiables.md`
- 路线图：`docs/implementation/roadmap.md`
- 当前任务：`docs/implementation/current-task.md`
- 仓库协作说明：`AGENTS.md`
