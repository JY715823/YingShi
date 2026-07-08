# Ledger Module Refinement Brief

> Status: **closed**
> Module key: `ledger`
> Created: 2026-07-01
> Closed: 2026-07-02
> Total batches: 10 (Bug fixes + Sync architecture + UI overhaul)

---

## Module Goal

记账（Ledger）是映时App的核心功能模块，提供多账本管理、收支记录、资产管理、预算控制、统计分析、周期账单、日历视图、数据导入导出、云同步。

精修目标：**修复全部已知bug + 同步层升级为PostgreSQL关系表+增量同步 + UI质量提升到专业记账App水准**，不新增功能。

---

## Architecture (Post-Refinement)

- **Android端**: 24个源文件，单Activity + Compose UI，Modal-within-screen导航
- **数据层**: Room DB v4，9张业务表 + 1张 `ledger_sync_changelog` 表，Repository模式
- **Server端**: Spring Boot + PostgreSQL，9张关系表镜像Room schema（Flyway V21）
- **同步协议**: `POST /api/ledger/sync` 增量同步（changelog驱动，行级 last-write-wins）
- **旧API**: `GET/PUT /api/ledger/snapshot` 标记 `@Deprecated`，过渡保留

---

## Decisions

| 决策项 | 结论 |
|--------|------|
| UI目标 | 方案C：Bug优先 + UI对标专业记账App自然提升 |
| 搜索策略 | 切到SQL搜索，移除客户端全量过滤 |
| 同步架构 | PostgreSQL关系表 + 增量同步，行级 updated_at 比较 |
| 圆角体系 | 3种标准值：24dp(大卡片)、18dp(中面板)、12dp(小组件)，999dp(药丸) |
| 字体层级 | headlineSmall(24sp) > titleLarge(20sp) > titleMedium > labelMedium |
| 设计Token | LedgerSubtleText(#3D5260) 深于 LedgerMuted(#556B75) |

---

## Sync Architecture Design

### 核心思路

1. Server端9张PostgreSQL关系表，镜像Room的9张实体表，加 `library_id` 作用域
2. 客户端新增 `ledger_sync_changelog` Room表，记录每次本地变更
3. 同步时只传变更行（增量），双向：push本地变更 → pull远端变更

### Server端 PostgreSQL Schema（Flyway V21）

```
ledger_books:              id, library_id, name, creator_user_id, template, currency_code,
                           currency_symbol, cover_color, sort_order, created_at, updated_at, is_deleted
ledger_categories:         id, library_id, book_id, name, icon_key, color, type, sort_order, hidden, created_at, updated_at
ledger_accounts:           id, library_id, book_id, name, type, icon_key, color,
                           initial_balance_cents, balance_cents, credit_limit_cents,
                           include_in_total, hidden, note, sort_order, created_at, updated_at
ledger_transactions:       id, library_id, book_id, category_id, account_id, to_account_id,
                           amount_cents, type, occurred_at, remark, method,
                           created_at, updated_at, deleted_at
ledger_budgets:            id, library_id, book_id, period, start_at, end_at,
                           total_amount_cents, created_at, updated_at
ledger_category_budgets:   id, library_id, budget_id, category_id, amount_cents, created_at, updated_at
ledger_deleted_items:      id, library_id, book_id, item_id, type, title,
                           amount_cents, deleted_at, expires_at
ledger_recurring_rules:    id, library_id, book_id, type, category_id, account_id,
                           to_account_id, amount_cents, remark, frequency,
                           start_at, end_at, next_occurrence_at, enabled, created_at, updated_at
ledger_recurring_occurrences: id, library_id, rule_id, transaction_id, occurrence_at, created_at
```

### 客户端 changelog 表

```sql
CREATE TABLE ledger_sync_changelog (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name TEXT NOT NULL,
    row_id TEXT NOT NULL,
    changed_at_millis LONG NOT NULL,
    is_delete INTEGER NOT NULL
)
```

### API合约

`POST /api/ledger/sync` — Request: `{ lastSyncVersionMillis, changes: { books, categories, accounts, transactions, budgets, categoryBudgets, deletedItems, recurringRules, recurringOccurrences, deletedRowIds } }`
Response: `{ versionMillis, changes: { ... } }`

### 同步流程

客户端记录 `lastSyncVersionMillis` → 每次mutation写changelog → 同步时收集changelog构建请求 → Server upsert+删除+返回远端变更 → 客户端apply → 清理changelog → 更新version

### 数据迁移

首次部署：Server从 `ledger_snapshots` JSON blob 解析写入9张关系表（V21迁移脚本 + `LedgerDataMigrationService`）

---

## Scope Boundaries

### In Scope

- 全部18个已知bug修复
- 同步层从全量快照升级为PostgreSQL关系表+增量同步
- UI质量提升（10个Batch，覆盖键盘/字体/圆角/动画/空状态/图表等）
- 全链路功能正确性验证

### Out of Scope

- 新增功能（账单分享、导出PDF、多币种等）
- 架构重构（不迁移NavController，不改MVVM）
- 非记账模块的任何改动

---

## Related Modules

| 模块 | 关联点 | 影响 |
|------|--------|------|
| 主页/导航 | 记账页入口 | 无改动 |
| 用户认证 | `@AuthRequired` | 同步依赖认证 |
| 推送通知/相册/帖子 | 无直接关联 | 无改动 |

---

## Contracts

- **API**: `POST /api/ledger/sync`（增量同步），旧 `GET/PUT /api/ledger/snapshot` 标记 deprecated
- **认证**: Header token（`@AuthRequired`）
- **数据**: Room v3→v4（新增changelog表 + TypeConverters修复），PostgreSQL Flyway V21
- **金额**: 全部 Long cents 存储

---

## Bug Inventory（全部已修复）

### P0 — 数据正确性（Batch 1）

| # | Bug | 修复 |
|---|-----|------|
| 1 | `permanentlyDeleteTransaction` 不删 `ledger_transactions` | 同时删除两表 |
| 2 | `saveTransactions` 批量导入绕过余额追踪 | 纳入余额计算 |
| 3 | `insertTransactions` 非原子操作 | 单个 `@Transaction` |
| 4 | `reorderAccounts` 缺少同步调用 | 补 `afterMutation` |

### P1 — 功能缺陷（Batch 3）

| # | Bug | 修复 |
|---|-----|------|
| 5 | 统计标签硬编码"月" | 根据周期动态显示 |
| 6 | 分类统计金额始终负数 | 区分收支颜色 |
| 7 | 甜甜圈图中心硬编码 | `constraints.maxWidth / 2f` |
| 8 | 键盘硬编码WECHAT图标 | 读取实际账户类型图标 |
| 9 | 搜索做客户端过滤 | 切到DAO SQL搜索 + combine三流 |
| 10 | 周期账单删除无确认 | AlertDialog确认 |
| 11 | 设置占位项无onClick | Toast"即将上线" |

### P2 — 健壮性（Batch 5）

| # | Bug | 修复 |
|---|-----|------|
| 12 | DAO死代码重复 | 已清理 |
| 13 | 同步错误静默吞掉 | SharedFlow错误流 + 1次重试 |
| 14 | hydrated非@Volatile | 已修复 |
| 15 | TypeConverters crash | 安全枚举转换（Batch 2一并完成） |
| 16 | 286dp魔法数字 | 命名常量 `BottomPanelOverlayHeight` |
| 17-18 | Server端问题 | 新架构下自然解决 |

---

## Implementation Summary (10 Batches)

### Batch 1-5: Bug修复 + 同步架构 + 基础UI

- **Batch 1**: P0数据正确性4项修复
- **Batch 2**: 同步层全面升级 — Server 9张PostgreSQL表 + 增量同步Service；Android Room v4 + RemoteLedgerSyncBridge重写 + changelog驱动
- **Batch 3**: P1功能缺陷7项修复（统计标签/分类金额/甜甜圈图/键盘图标/SQL搜索/删除确认/设置占位）
- **Batch 4**: UI基础提升（图标40dp/分类网格4列/空状态/日分组颜色/摘要颜色）
- **Batch 5**: P2健壮性（死代码清理/同步错误处理+重试/TypeConverters/魔法数字消除）

### Batch 6-10: 全面UI升级（37项改动）

- **Batch 6**: 键盘重写 — 按键可视化(RoundedCornerShape+clip+padding)、金额32sp、分类选中高亮+动画border、日期/账户chip化、键盘面板阴影圆角、退格Backspace图标、÷键补全(5列布局)
- **Batch 7**: 视觉体系 — 圆角9→3种(30处替换)、top bar统一(10dp/8dp/20dp)、字体层级(headlineSmall/titleLarge/labelMedium)、LedgerSubtleText加深、图标尺寸统一(20/22dp)
- **Batch 8**: 首页体验 — header clip防溢出、行间Divider(0.5dp)、FAB深色(LedgerHeaderGreen)、空状态重做(AccountBalanceWallet+CTA)、侧边栏选中态、快捷操作色加深、货币符号¥
- **Batch 9**: 统计/资产/预算 — 甜甜圈标签防重叠(多轮碰撞检测)、分类统计右对齐、圆角进度条(Canvas+clip)、回收站空状态、净资产headlineSmall、统计padding统一
- **Batch 10**: 动效 — tab indicator弹簧动画、分类选中animateColor/animateDp、交易行yingShiClickable(ripple)、FAB pressScale 0.90、收支箭头图标

---

## Self-Check Findings (All Fixed)

### Post-Implement Self-Check

- **Bug A**: `getOrElse` 非局部return导致同步错误静默吞掉 → 改为 `null` 让循环自然结束
- **Bug B**: `observe()` 直接赋值覆盖 syncError message → 改为 `update {}` 原子操作

### Verify Pass (Batch 6-10 深度复核)

覆盖6个文件约7000行代码，发现并修复5个bug：

| # | 严重度 | 描述 | 修复 |
|---|--------|------|------|
| V1 | Medium | 收入金额缺少`+`前缀 | 添加`+`前缀 |
| V2 | Low | 日分组header缺少货币符号 | 添加`currencySymbol` |
| V3 | Medium | 甜甜圈标签碰撞检测单次遍历 | 多轮迭代(最多8轮) |
| V4 | Low | 回收站空状态图标语义错误 | ArrowBack→Delete |
| V5 | Low | 进度条未clamp progress | `coerceIn(0f, 1f)` |

### Closeout Self-Check (2026-07-02)

- [x] 编译通过: `assembleDebug` BUILD SUCCESSFUL
- [x] 全部10个Batch实现确认
- [x] 7个自检bug全部修复
- [x] 关键代码抽查通过（键盘/分类选中/FAB/空状态/同步桥接/设计Token）
- [x] 无跨模块耦合引入
- [x] 已知偏差记录（LedgerUiSupport.kt共享组件保留22dp/16dp非标准圆角）

---

## Deployment Readiness

- Room DB v3→v4 迁移（新增changelog表 + TypeConverters修复）
- Server Flyway V21 迁移（9张新表）+ 数据迁移脚本
- 同步协议变更需双端同时部署
- 旧snapshot API保留过渡期

---

## Carry-Forward Notes

供后续模块精修参考：

1. **同步架构可复用**: `RemoteLedgerSyncBridge` 模式（changelog + SharedFlow错误流 + 重试）可作为其他模块同步的参考模板
2. **设计Token体系**: Ledger的颜色token系统（HeaderGreen/ExpenseRed/IncomeGreen等）可作为其他模块的配色参考
3. **圆角/字体标准**: 24/18/12/999dp圆角体系 + headlineSmall/titleLarge/titleMedium/labelMedium字体层级，建议其他模块统一采用
4. **Compose动画模式**: `yingShiClickable` 替代 `.clickable`、`Crossfade` 用于内容切换、`animateColorAsState`/`animateDpAsState` 用于状态过渡
5. **Windows环境注意**: ripgrep不可用(rg.exe ENOENT)，需用Python或PowerShell Select-String替代
6. **待真机验证项**: 双向增量同步端到端、搜索性能、分类网格小屏适配、同步重试Toast、永久删除数据清除、批量导入余额

---

## Known Risks (Remaining)

- Server端数据迁移（JSON snapshot → 关系表）需部署时验证
- 大数据量下SQL搜索性能需实际观察
- 分类网格4列在极小屏幕(<5英寸)上的表现未验证
- 双向增量同步的端到端测试需真机+Server环境
