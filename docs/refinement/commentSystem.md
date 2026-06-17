# Comment System Refinement

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `commentSystem`
- Status: `closed`
- Last updated: `2026-06-17`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: 评论新增、编辑、删除、查看都稳定可信，不出现删除空壳、误删、混流或常驻提示。
- Product intent: 把评论系统收口成两类可靠评论：小相册评论按 `smallAlbumId`，媒体评论按 `mediaId`。
- Success criteria: 普通评论列表隐藏软删除评论；删除仍走软删除审计链路；小相册评论使用底部弹窗；评论反馈短暂自动消失；小相册和媒体评论交互一致。

## Final Scope
### In scope
- Android 小相册评论、媒体评论详情、评论预览入口、Viewer 评论入口、回收站/通知相关只读映射。
- Server 评论列表 active-only 查询、软删除合同、评论相关测试和文档。
- 评论编辑、删除、选择、复制、键盘贴底、空态、成功提示、定位提示和视觉层级精修。

### Out of scope
- 不改成作者独占编辑/删除权限；共享空间成员仍均可编辑和删除评论。
- 不引入多级回复、点赞、评论未读楼层、分页加载更多或评论权限重构。

## Final Contracts
- `GET /api/small-albums/{smallAlbumId}/comments` 和 `GET /api/media/{mediaId}/comments` 只返回 active comments，即 `deletedAt IS NULL`。
- `DELETE /api/comments/{commentId}` 继续返回 soft-deleted `CommentDto`，其中 `isDeleted=true`、`content=null`，用于通知、审计和清理链路。
- 编辑已删除评论仍返回错误；普通列表不展示“已删除”墓碑。
- 客户端在 DTO 映射、线程状态、详情页、Viewer、回收站只读查看中都防御性过滤 `isDeleted=true`。
- 评论目标不混流：小相册评论和媒体评论分别按各自目标拉取和提交。

## Shipped Behavior
### Client
- 小相册详情页不再内嵌完整评论流，改为轻量入口卡；点击后打开底部评论弹窗。
- 小相册评论弹窗与媒体评论详情使用一致的评论列表、输入、编辑、复制、选择、删除确认和展开/收起交互。
- 删除评论先进入“确认删除”危险态；删除成功后立即乐观移除并刷新兜底，不再留下“有名称和时间但没内容”的空壳。
- 编辑状态使用 `TextFieldValue`，进入编辑时光标落在末尾；保存、取消、删除后清理编辑、选择和菜单状态。
- 评论成功/删除/定位等提示改为短暂反馈，不再长期驻留；媒体评论从预览点入详情时，“已定位到这条评论”约 2.4 秒后消失，目标评论仍高亮。
- 小相册评论 sheet 外层不再直接吃 `imePadding`，键盘适配收敛到底部输入栏，目标是避免弹窗被压小或顶到上方。
- 小相册和媒体评论的“选择”恢复系统文本选择控件，点击“选择”后应直接可拖动选择句柄，不需要二次长按。
- 评论输入栏固定在底部，长列表独立滚动；空态文案统一为私人记忆语气：“还没有留言，给这段记忆留一句。”

### Server
- `CommentRepository` 增加 active-only 小相册评论和媒体评论查询。
- 删除接口保持软删除返回 DTO；列表查询不再返回软删除评论。
- 新增 Postgres partial indexes，覆盖 active 小相册评论和 active 媒体评论查询维度，避免 `deletedAt IS NULL` 过滤带来性能退化。
- `comment-api.md` 已同步 active-list 与 soft-delete response 合同。

### Design
- 媒体评论详情保持深色 Viewer 沉浸风格，增强标题、副信息、蓝色光感点缀、输入栏质感和评论内容层级。
- 小相册评论使用浅色记忆暖光，并将主要强调色统一到颜色系统 `goldAccent`：弹窗边框、竖向点缀、浅色评论作者名、elevated 输入栏边框。
- 小相册评论弹窗高度收敛到更轻的比例；详情页只保留评论入口与摘要，避免主信息区臃肿。

## Validation Snapshot
### Verified
- Android `:app:compileDebugKotlin` passed on 2026-06-17；最新一次只剩既有 `PhotoViewerScreen` fullscreen/status-bar deprecation warnings。
- Server `cmd.exe /c mvnw.cmd -q -DskipTests compile` passed。
- Server targeted test `YingshiServerApplicationTests#commentApisWorkAndPostMediaFlowsStaySeparated` passed：删除后列表不再出现该评论，编辑已删除评论返回 `VALIDATION_ERROR`。
- Server targeted test `YingshiServerApplicationTests#notificationsIncludeCommentCreateEditDeleteVariants` passed：`comment_delete` 通知链路仍可生成。
- 用户已确认前一轮核心评论问题“差不多了”，进入收尾；最后补充的媒体评论定位提示已编译通过。

### Accepted Remaining Risk
- 真机 IME 动画、不同 Android 版本文本选择句柄、不同屏幕高度下 bottom sheet 体感仍需要后续整体回归时顺手确认。
- 小相册评论香槟金观感、媒体评论深色质感属于真机视觉体感风险，代码和编译已闭环，未再阻塞本模块收尾。
- 工作树存在大量非本模块改动，本 brief 只记录 `commentSystem` 范围，不代表其他模块已冻结。

### Blocked
- None.

## Real-device Findings Closed
- 媒体评论详情编辑误触删除、删除后空壳、常驻“评论已删除”：已通过删除确认、乐观移除、active-only list 和短暂 notice 收口。
- 小相册评论编辑光标在开头、删除后空壳：已通过 `TextFieldValue` 末尾 selection 和删除后状态清理修复。
- 小相册评论展示不够满意：已改为底部弹窗，详情页仅保留入口摘要。
- 小相册评论输入栏键盘收起/弹出位置问题：已改为列表独立滚动 + 输入栏固定底部，并调整 IME padding 层级。
- 评论“选择”需要二次长按：已恢复系统文本选择控件，点击“选择”后直接进入可拖选目标状态。
- 媒体评论预览点入详情后“已定位到这条评论”常驻：已改为短暂显示。

## Carry-forward Notes
- 后续改 Viewer、回收站、通知入口时，要继续确保评论映射过滤 `isDeleted=true`，但删除通知仍可使用 soft-deleted DTO。
- 后续改评论权限前，先明确共享空间协作语义；本轮保留“双方都可编辑/删除评论”。
- 后续改 bottom sheet 或输入栏时，需要重点复测小相册评论长列表、键盘弹出/收起、导航栏手势和输入栏贴底。
- 后续改评论选择/复制时，不要再次禁用系统 `TextToolbar`，否则会回到点击“选择”后仍需长按的问题。
- 后续改颜色系统时，小相册评论浅色点缀依赖 `goldAccent`；媒体评论仍使用 Viewer 深色 accent。

## Closeout Summary
- What shipped: Android 小相册评论弹窗化、媒体评论详情精修、评论编辑/删除/选择/复制/键盘/定位提示收口；Server active-only list、软删除合同和 partial index 同步；API 文档同步。
- What was validated: Android 编译通过；Server compile 和评论/通知定向测试通过；用户设备反馈驱动的问题均已实现修复或标为后续整体回归确认项。
- Remaining risk: 仅剩真机体感类风险，主要是 IME 动画、系统选择句柄和视觉观感，不再阻塞模块关闭。
- Adjacent modules affected: Viewer、Small Album Detail、Trash read-only viewer、Notifications。

## Closeout Self-check
- Brief completeness: Final scope, contracts, shipped behavior, validation, accepted risk, and adjacent-module notes are captured; implementation notes no longer depend on chat history.
- Remaining risk clarity: Remaining items are explicit真机体感/视觉风险，没有隐藏 server contract 或 data-shape blocker。
- Carry-forward quality: Future-critical notes are short and actionable: deleted filtering, soft-delete notification DTO, shared edit/delete permission, bottom sheet IME handling, system text toolbar, and color-token dependency.
