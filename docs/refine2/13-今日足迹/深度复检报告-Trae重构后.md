# 今日足迹 (LifeConsole) 深度复检报告（QoderWork review · Trae 大规模重构后）

> 复检时间: 2026-07-19
> 实施方式: Trae 大规模重构（Android 85 文件 / Server 53 文件）→ QoderWork 深度审查 + 修复
> 变更规模: Android 13614+/28055- 行，Server 4064+/2634- 行

---

## 一、历史修复保留验证（7 项）

| # | 修复项 | 状态 | 证据 |
|---|--------|------|------|
| 1 | 历史实时显示（withoutDate 移除） | **PASS** | ViewModel loadHistory 为 `history = result.data`，无过滤调用 |
| 2 | 定位权限运行时请求 | **PASS** | LifeConsoleScreen L302-309 RequestPermission + LaunchedEffect |
| 3 | addBowelEvent 不传 null body | **PASS** | RealRepositories L1516-1528 始终创建 DTO |
| 4 | 上传传 domain | **PASS** | RealRepositories L911 `domain = payload.domain` |
| 5 | 卡片视觉（无绿框/无阴影） | **PASS** | LifeMediaFrame/BowelCard 用 raisedSurface 纯白背景，accentGradient 零匹配 |
| 6 | 大便卡片标题 | **PASS** | "今日健康日志" 全项目零匹配，演进为按人分卡 |
| 7 | Tab 不截断 | **PASS** | AppShell L231 选中字号 titleMedium(16sp) |

**结论: 7/7 历史修复全部保留。**

---

## 二、新变更审查

### Android 端

| 检查项 | 状态 | 备注 |
|--------|------|------|
| LifeConsoleScreen.kt 括号配平 | PASS | 457/457 大括号，1337/1337 圆括号 |
| LifeConsoleApi 端点与后端一致 | PASS | 8 个端点逐条比对（含新增 2 个 PATCH location） |
| LifeConsoleDtos location 字段完整 | PASS | Request/Response/历史日级/用户摘要均有 |
| TODO/FIXME 遗留 | PASS | feature/life/ 零匹配 |

### Server 端

| 检查项 | 状态 | 备注 |
|--------|------|------|
| Domain 隔离 | PASS | 三 Entity 有 domain 字段，ensure 方法 domain-aware |
| 唯一约束迁移 V30 | PASS | 重建含 domain 的唯一索引 |
| 大便软删除 | PASS | deletedAt 字段 + 查询过滤 IS NULL |
| 位置追踪 | PASS | 三表位置字段 + GeocodingService 三件套 + V29 迁移 |
| 历史包含今天 | PASS | `isAfter(today)` 仅排除未来 |
| DTO 包迁移 | PASS | RegisterPushToken 在 dto/push/ |
| Flyway 迁移链 | PASS | V1-V30 连续无跳号（新增 V26-V30 共 5 个） |
| Controller 端点 | PASS | 8 个端点（原 6 + 新增 2 个 PATCH location） |

---

## 三、发现并修复的 Bug（4 个）

### BUG-1: addMedia 返回快照时区硬编码（中）

**问题**: `LifeConsoleService.addMedia` L213 返回今日快照时硬编码 `DEFAULT_ZONE_ID`（Asia/Shanghai），但写入时用调用方时区。非东八区客户端的"今日页"日期边界可能错一天。

**根因**: 写入路径用 `zone`（调用方时区），读取路径用 `DEFAULT_ZONE_ID`，两者不一致。

**修复**: 改为 `getToday(today.toString(), zone.getId(), currentUser)`，统一用调用方时区。

**验证**: Server 编译通过，测试通过。

---

### BUG-2: 只读事务内复活写入不落库（中）

**问题**: `buildMediaSlot`（getToday 调用）和 `buildHistoryDays`（getHistory 调用）都在 `@Transactional(readOnly = true)` 事务内执行 `albumRepository.save(softDeleted)` 复活软删 album。Hibernate 只读事务 FlushMode=MANUAL，该写入**不会落库**，复活逻辑静默失效。

**根因**: 在只读事务中执行了写操作。

**修复**:
- `buildMediaSlot`: 移除复活写入，软删时直接走 `buildFallbackMediaSlot`
- `buildHistoryDays`: 改用 `.or(() -> findByLibraryIdAndSystemKeyAndDomain(...))` 链式查询，软删 album 也用于**读取**（其下 posts/media 关联仍可读），不执行写入
- 复活逻辑统一由 `ensureSystemAlbum`（addMedia 写事务）处理

**验证**: Server 编译通过，测试通过。

---

### BUG-3: withoutDate 死代码残留（低）

**问题**: `LifeConsoleScreen.kt` L3187-3193 的 `withoutDate` 扩展函数已无调用点（历史实时显示修复后），但定义残留。

**修复**: 删除该函数。

**验证**: Android 编译通过。

---

### BUG-4: addBowelEvent API body 可空（低 · 回归风险）

**问题**: `LifeConsoleApi.addBowelEvent` 的 `@Body body: LifeConsoleBowelEventRequestDto? = null` 为可空+默认值。虽然当前 Repository 调用路径安全（始终创建 DTO），但若有人绕过 Repository 直调 API 可不传 body，触发 Retrofit 发送 Content-Length:0 被服务端拒绝。

**修复**: 收紧为非空 `@Body body: LifeConsoleBowelEventRequestDto`。

**验证**: Android 编译通过（调用方 RealRepositories 始终传非空值）。

---

## 四、编译与测试验证

| 验证项 | 结果 |
|--------|------|
| Android gradlew.bat assembleDebug | **PASS** (BUILD SUCCESSFUL) |
| Server mvnw.cmd compile | **PASS** (EXIT: 0) |
| Server LifeConsoleServiceTest | **PASS** (29/29) |
| Server LifeConsoleControllerTest | **PASS** (24/24) |
| Server AmapGeocodingServiceTest | **PASS** (4/4) |
| **测试总计** | **57/57** |

---

## 五、非阻塞发现

| # | 严重度 | 描述 | 位置 | 处理建议 |
|---|--------|------|------|---------|
| NB-1 | 低 | 历史页媒体缩略图残留 1.dp 阴影 | LifeConsoleScreen L1629 | 若"无阴影"是全局约定可移除 |
| NB-2 | 低 | POST /media 未传可选 zoneId | LifeConsoleApi addMedia | 服务端有 fallback，跨时区场景建议补传 |
| NB-3 | 低 | updateBowelEventLocation Controller 不接受 zoneId | LifeConsoleController L123 | 内部固定东八区 |
| NB-4 | 低 | findLatestUpdatedAtByLibraryId 未过滤 deletedAt | BowelEventRepository L29 | 同步版本号语义可接受 |
| NB-5 | 提示 | 74 个文件未提交（含 V26-V30 全部新迁移） | git status | 建议提交 |

---

## 六、总评

**Trae 本轮大规模重构质量较高**：7 项历史修复全部保留，新增位置追踪 PATCH 端点、按人分卡的大便页、月度归属算法（按 displayTimeMillis 分组到对应月份）等功能完整。QoderWork 审查发现并修复了 4 个 Bug（2 个中级：时区硬编码 + 只读事务写入；2 个低级：死代码 + API 可空 body），全部为隐蔽的逻辑缺陷而非表面问题。修复后双端编译通过，57 个测试全部通过。

**核心风险提示**: BUG-2（只读事务写入）是最隐蔽的问题——代码"看起来"在复活软删 album，实际写入永不落库，会导致用户历史数据在 album 被软删后永久不可见（直到下次 addMedia 触发 ensureSystemAlbum）。已修复为纯读取路径。
