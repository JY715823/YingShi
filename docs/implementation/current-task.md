# Current Task - Stage 0 Bootstrap

## Goal

完成映世 Android 仓库的 Stage 0 初始化检查与补全，让仓库具备继续分阶段 AI 协作开发的基础条件。

## In scope

这次只做：

- repository docs setup
- AGENTS.md baseline
- base README
- roadmap / current-task 补全
- app shell structure
- global bottom navigation placeholder
- photo module top navigation placeholder
- placeholder screens
- minimal theme organization

## Do not do

这次明确不做：

- no real backend
- no Room
- no Retrofit
- no MediaStore
- no comment system
- no real Viewer logic
- no upload/download
- no notifications implementation
- no real system media implementation

## Structural target

如果 Android 工程已存在，本阶段只做不破坏运行的基础整理：

- 保留现有 `app` 工程可编译
- 用 feature-oriented 方式拆出最小包结构
- 用占位页面替换模板页
- 不引入复杂架构和真实业务流

## Done when

完成标准：

- project builds if the local environment allows it
- bottom navigation exists: `主页 / 照片 / 生活`
- photo section top nav exists: `照片 / 相册 / 回收站`
- screens have placeholder content
- repository docs are in place
- AGENTS.md is in place
- Stage 0 scope and non-goals are explicit

## Notes for the next stage

- Stage 1 开始再继续加强主题 token、通用壳层与组件约定
- Stage 2 再进入照片页假数据媒体流
- 进入功能阶段前，始终先核对 `non-negotiables.md`
