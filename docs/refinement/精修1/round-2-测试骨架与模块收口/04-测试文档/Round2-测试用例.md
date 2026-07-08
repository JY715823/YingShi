# Round 2 测试文档

> 覆盖 R2-M1 ~ R2-M4 全部模块

---

## R2-M1: 服务端测试覆盖

### LiveServerIntegrationTest（10 个测试方法）

| TC | 名称 | 端点 | 预期 |
|----|------|------|------|
| LS-01 | healthEndpoint | GET /api/health | 200 + UP + X-Request-Id |
| LS-02 | loginChallengeFlow | POST /api/auth/login/challenge | 200 + challengeId |
| LS-03 | protectedEndpointRejectsUnauthenticated | GET /api/auth/me | 401 |
| LS-04 | trashEndpointsRequireAuth | GET /api/trash/items | 401 |
| LS-05 | uploadEndpointsRequireAuth | GET /api/uploads | 401 |
| LS-06 | ledgerSyncEndpointExists | POST /api/ledger/sync | 401 或 404 |
| LS-07 | syncVersionsEndpoint | GET /api/sync/versions | 401 |
| LS-08 | pushDiagnosticsRequiresAuth | GET /api/push/diagnostics | 401 |
| LS-09 | openApiDocsAccessible | GET /v3/api-docs | 200 或 404 |
| LS-10 | actuatorOrApiHealthAccessible | /actuator/health 或 /api/health | 200 + UP |

### AuthIntegrationTest（8 个 Testcontainers 测试方法）

| TC | 名称 | 说明 |
|----|------|------|
| AUTH-01 | loginChallengeVerifyAndMe | 完整挑战→验证→获取用户 |
| AUTH-02 | refreshTokenRotation | 刷新 token + 旧 token 失效 |
| AUTH-03 | logoutRevokesToken | 登出后 token 失效 |
| AUTH-04 | wrongPasswordRejects | 错误密码 → 401 |
| AUTH-05 | wrongCodeRejects | 错误验证码 → 401 |
| AUTH-06 | protectedEndpointRejectsMissingToken | 无 token → 401 |
| AUTH-07 | crossUserAccessBlocked | 跨用户访问隔离 |
| AUTH-08 | profileUpdateAndReadBack | 更新个人资料并回读 |

### UploadIntegrationTest（5 个）

| TC | 名称 | 说明 |
|----|------|------|
| UP-01 | threePhaseUploadTokenFileConfirm | token → file → confirm 完整流 |
| UP-02 | cancelUpload | 创建 token → 取消 |
| UP-03 | uploadHistoryListing | 创建多个 → 列出历史 |
| UP-04 | dismissUpload | 取消 → dismiss |
| UP-05 | duplicateFingerprintDetected | 相同指纹第二次上传检测重复 |

### TrashIntegrationTest（6 个）

| TC | 名称 | 说明 |
|----|------|------|
| TR-01 | listTrashInitiallyEmpty | 初始空列表 |
| TR-02 | pendingCleanupInitiallyEmpty | 初始空待清理 |
| TR-03 | systemDeleteCreatesTrashItemAndRestoreWorks | 系统删除→恢复 |
| TR-04 | moveToPendingCleanupAndPurge | 删除→移出→purge |
| TR-05 | undoRemoveReturnsToInTrash | 撤销移出→回到 IN_TRASH |
| TR-06 | purgeDirectlyFromInTrash | 从 IN_TRASH 直接 purge |

### LedgerSyncIntegrationTest（4 个）

| TC | 名称 | 说明 |
|----|------|------|
| LG-01 | emptySyncReturnsEmptyChanges | 空同步 |
| LG-02 | upsertBookAndSyncBack | 写入 book → 同步回读 |
| LG-03 | incrementalSyncOnlyReturnsChangesSinceVersion | 增量同步 |
| LG-04 | crossUserSyncSharesData | 跨用户共享数据 |

---

## R2-M2/M3/M4: 模块关闭验证

### 端点可达性测试（curl）

| TC | 模块 | 端点 | 预期 |
|----|------|------|------|
| EP-01 | bin | GET /api/trash/items | 401 |
| EP-02 | bin | GET /api/trash/pending-cleanup | 401 |
| EP-03 | noticeCenter | GET /api/notifications | 401 |
| EP-04 | noticeCenter | GET /api/push/preferences | 401 |
| EP-05 | noticeCenter | GET /api/push/diagnostics | 401 |
| EP-06 | noticeCenter | GET /api/sync/versions | 401 |
| EP-07 | chatShower | GET /api/chat/imported/snapshot | 401 |
| EP-08 | 通用 | GET /api/albums | 401 |
| EP-09 | 通用 | GET /api/media/feed | 401 |
