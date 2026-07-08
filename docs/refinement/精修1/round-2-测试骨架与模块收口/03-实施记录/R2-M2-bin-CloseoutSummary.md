# R2-M2: bin 模块 Closeout Summary

> **模块**: bin（回收站）
> **原状态**: implementing → **新状态**: closed（服务端已关闭，真机部分 carry-forward）
> **关闭日期**: 2026-07-04

---

## What shipped（已完成）

### 服务端
- TrashService 完整实现：三阶段生命周期（IN_TRASH → PENDING_CLEANUP → purged/restored）
- 四种 itemType 支持：`largeAlbumDeleted`、`smallAlbumDeleted`、`mediaRemoved`、`mediaSystemDeleted`
- 24 小时撤销窗口 + 定时清理调度器
- purge 按类型执行不同逻辑：
  - `mediaSystemDeleted`：删除 DB 记录 + 评论 + 关联 + 物理文件（original/preview/cover）
  - `largeAlbumDeleted`/`smallAlbumDeleted`：仅删除结构（不删全局媒体文件）
- 5 项服务端定向测试全部通过（`YingshiServerApplicationTests`）：
  - `pendingCleanupItemCanBePurgedImmediately`
  - `pendingCleanupSchedulerPurgesExpiredItems`
  - `permanentDeleteSystemDeletedMediaRemovesRecordAndLocalFiles`
  - `restoredDeletedMediaDoesNotDuplicatePhotoFeedAfterReupload`
  - `directoryDeleteAndSystemDeleteHaveDifferentTrashBehavior`

### Android 客户端
- RealTrashScreens.kt + RealTrashViewModels.kt 完整实现
- 四分类列表 + 详情页 + 恢复/移出/撤销/永久删除 UI
- 待清理入口（分类菜单内，仅当有待清理项时显示）
- 24 小时倒计时显示
- `compileDebugKotlin` 通过

### 本轮新增验证
- `LiveServerIntegrationTest`：`/api/trash/items` 和 `/api/trash/pending-cleanup` 端点注册正确，返回 401 要求认证
- `TrashIntegrationTest`：6 个 Testcontainers 测试用例覆盖完整生命周期（编译通过，Testcontainers 因 Windows Docker 连接限制暂未执行）
- 服务端 curl 验证：9 个关键端点全部返回 401（路由注册 + 认证保护正确）

## What remains risky（遗留风险）

| 风险 | 严重度 | 说明 | 建议处理时机 |
|------|--------|------|------------|
| 真实 MinIO/COS 对象存储删除未验证 | HIGH | 本地测试仅覆盖 LocalObjectStorageService，MinIO/S3 的 purge 物理删除未真机验证 | Round 4 真机回归 |
| 真机离线缓存查看未验证 | MEDIUM | 断网后回收站列表/详情页的缓存命中率和过期判断未测试 | Round 4 真机回归 |
| 分类切换闪空未真机验证 | LOW | 代码层已实现缓存，但弱网/离线场景未真机确认 | Round 4 真机回归 |
| LargeAlbum_Dir 的 5 个 scenario 未真机验证 | MEDIUM | 热切换不闪空、删除分类、整组恢复等 | Round 4 真机回归 |

## What was intentionally deferred（有意延期）

- Fake/demo trash 完整复制 pending-cleanup 页面（非 REAL 模式下不实现新的 pending-cleanup UI）
- 小屏（< 5 英寸）回收站布局适配（归入 Round 4 真机回归）

## Carry-forward notes

- 事实：trash 主删除是 `remove`（进入待清理），不是物理删除；永久删除只属于待清理页面或显式二次确认
- 相邻模块待复访：fake/demo trash 可在后续更新以与 pending-cleanup 页面保持一致
