# Current Task: Stage 12.2 - 主链路状态一致性收口

## 背景

Stage 12.1 已完成 REAL 媒体 URL、缩略图、图片 Viewer、视频 Viewer、沉浸背景和基础播放控制条收口。接下来进入 Stage 12.2，重点不再是 Viewer，而是已有主链路动作后的数据一致性。

当前需要确保用户在编辑、上传、删除、恢复、评论变更之后，相关页面能够及时刷新，不继续显示旧数据，也不出现 FAKE / REAL 状态互相污染。

## 开始前 git 要求

开始本阶段前，必须完成：

1. Stage 12.1 Android 成果提交。
2. Stage 12.1 Android 分支合并到 main。
3. Stage 12.1 Server 成果或文档提交。
4. Stage 12.1 Server 分支合并到 main。
5. Android 从 main 新建 feature/stage-12-2-state-consistency。
6. Server 从 main 新建 feature/stage-12-2-state-consistency-support。

如果 Server 没有任何改动，需要在最终输出说明 Server 无改动、main 已确认最新。

## 本阶段目标

收口以下动作后的刷新与状态一致性：

1. 编辑帖子后刷新
2. 上传或添加媒体后刷新
3. 删除媒体后刷新
4. 删除帖子后刷新
5. 移入回收站后刷新
6. 从回收站恢复后刷新
7. 评论新增后刷新
8. 评论编辑后刷新
9. 评论删除后刷新
10. 系统媒体删除 / 恢复后刷新
11. FAKE / REAL 模式切换隔离

## 范围

### 1. Gear Edit 编辑后刷新

需要检查：

- 修改帖子标题后，相册卡片是否更新
- 修改帖子描述后，帖子详情是否更新
- 修改时间 / 地点 / 可见字段后，相关 UI 是否更新
- 从 Gear Edit 返回后是否仍显示旧值
- REAL 模式是否重新拉取或更新本地状态
- FAKE 模式是否仍保持原逻辑

### 2. 上传或添加媒体后刷新

需要检查：

- 上传成功后，照片流是否出现新媒体
- 帖子详情媒体区是否出现新媒体
- Gear Edit 媒体管理是否出现新媒体
- Viewer 入口是否使用最新媒体列表
- 上传失败不污染列表
- 重试后状态正确

### 3. 删除媒体后刷新

需要检查：

- 删除媒体后，照片流不继续显示该媒体
- 帖子详情媒体区不继续显示该媒体
- Gear Edit 媒体管理不继续显示该媒体
- 如果 Viewer 正在查看被删除媒体，需要安全退出、切到下一项或显示已删除提示
- 删除失败时 UI 不误删
- FAKE / REAL 模式互不影响

### 4. 删除帖子 / 移入回收站后刷新

需要检查：

- 删除帖子后，相册列表不继续显示旧卡片
- 照片流中相关媒体处理符合现有业务规则
- 从帖子详情删除后，不返回已不存在详情
- 删除失败时 UI 不误删
- 回收站列表更新

### 5. 回收站恢复后刷新

需要检查：

- 恢复帖子后，相册列表重新出现
- 恢复媒体后，照片流重新出现
- 恢复后帖子详情可打开
- 回收站列表移除已恢复项
- 恢复失败时 UI 不误恢复

### 6. 评论状态一致性

需要检查：

- 新增评论后，评论列表刷新
- 新增评论后，评论气泡 / 评论计数刷新
- 编辑评论后，列表内容刷新
- 删除评论后，列表和计数刷新
- 评论失败时保留原状态或显示错误
- Viewer、帖子详情、照片流之间评论状态不串

### 7. 系统媒体区状态一致性

需要检查：

- 删除系统媒体后，系统媒体列表刷新
- 移到系统回收站后，列表刷新
- 恢复系统媒体后，列表刷新
- 查看态中操作完成后，返回列表不显示旧状态
- 右上角菜单和底部确认弹窗不被破坏

### 8. FAKE / REAL 状态隔离

需要检查：

- REAL 刷新不污染 FAKE 数据
- FAKE 修改不污染 REAL 数据
- 切换模式后清理或重建必要状态
- 不共享错误的缓存 key
- 不共享错误的 mutation 状态
- 不把 DTO 直接塞到 Compose UI

### 9. 刷新策略收口

需要尽量避免每个页面各自乱刷。

可以考虑：

- repository 层统一 refresh
- ViewModel 层统一 reload
- mutation event / mutation bus
- shared invalidation key
- 页面返回时按 dirty flag 刷新
- 操作成功后精确更新本地 state

具体方案以现有项目架构为准，但必须清晰、可维护。

## 不做内容

本阶段不要做：

- 新功能大扩展
- 上传流程重做
- OSS
- 复杂离线缓存
- WebSocket / 推送
- 多端实时同步
- fake/real 架构大重构
- 删除 FAKE repository
- 强制默认 REAL
- UI 大精修
- 后端权限体系大改
- 回收站业务规则调整
- Viewer 大改

## 文档要求

根据实际改动更新：

- docs/implementation/current-task.md
- docs/contracts/*
- docs/integration/frontend-backend-testing-guide.md

如果 Server 有契约变化，需要同步更新对应文档。

## 验收

1. Stage 12.1 Android 分支已合并 main。
2. Stage 12.1 Server 分支已合并 main，或明确无改动。
3. Android 已从 main 创建 Stage 12.2 分支。
4. Server 已从 main 创建 Stage 12.2 分支。
5. Gear Edit 修改帖子后，相册卡片刷新。
6. Gear Edit 修改帖子后，帖子详情刷新。
7. 上传或添加媒体后，照片流刷新。
8. 上传或添加媒体后，帖子详情媒体区刷新。
9. 上传或添加媒体后，Gear Edit 媒体管理刷新。
10. 删除媒体后，照片流不显示旧媒体。
11. 删除媒体后，帖子详情不显示旧媒体。
12. 删除媒体后，Viewer 不继续卡在已删除媒体。
13. 删除帖子后，相册列表不继续显示旧卡片。
14. 回收站恢复后，相关列表重新出现。
15. 回收站列表自身刷新正确。
16. 新增评论后，评论列表和计数刷新。
17. 编辑评论后，评论列表刷新。
18. 删除评论后，评论列表和计数刷新。
19. 系统媒体删除 / 恢复后列表刷新。
20. FAKE / REAL 切换不互相污染。
21. 网络失败、刷新失败不闪退，有中文提示或重试入口。
22. assembleDebug 通过。

## 构建命令

```powershell
cd D:\Projects\Yingshi\yingshi-android
.\gradlew.bat --no-daemon assembleDebug
```

## Stage 12.2 收口记录

### 本轮刷新策略

- REAL 主链路改为 `RealBackendMutationBus` 统一发出带 `scope + postIds + mediaIds` 的 mutation event。
- 照片流、相册页、帖子详情、Gear Edit 媒体管理、回收站、系统媒体导入目标列表按事件作用域决定是否刷新，不再对所有后端变更无差别全量重拉。
- 帖子详情页内的帖子评论继续走局部线程刷新；媒体评论在局部刷新之外，再向外发出刷新事件，用于同步照片流计数、帖子详情气泡和相关媒体管理页。
- `realBackendSessionKey(...)` 继续承担 FAKE / REAL、baseUrl、登录态切换隔离，mutation event 只在 REAL 模式下工作，不污染 FAKE。

### 系统媒体区补充策略

- `LocalSystemMediaBridgeRepository` 现在区分两类变更：
  - `OVERLAY_ONLY`：只更新已发帖关联、隐藏态等本地覆盖层。
  - `MEDIA_STORE_CHANGED`：系统删除后强制重新查询 MediaStore。
- 系统媒体列表页收到桥接变更后，按变更类型选择“只重算可见状态”或“重新查询系统媒体”。
- 系统媒体 Viewer 在删除后会同步过滤已被移除的媒体，避免继续停留在旧媒体上。
