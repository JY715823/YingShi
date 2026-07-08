# Round 3 测试文档

> 覆盖 R3-M1 ~ R3-M4 全部模块

---

## R3-M1: YingShiApp.kt 拆分

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| SPLIT-01 | Helpers 提取完整性 | 检查 YingShiAppHelpers.kt | 包含 9 个 internal 函数/Composable/常量 |
| SPLIT-02 | YingShiApp.kt 无重复定义 | grep 私有函数名 | 无重复的 AuthCheckingScreen 等 |
| SPLIT-03 | 行数减少 | wc -l | < 2000 行 |
| SPLIT-04 | 编译通过 | compileDebugKotlin | 零错误 |

## R3-M2: AGENTS.md 更新

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| AGENTS-01 | 无 Stage 0 描述 | grep "Stage 0" | 零匹配 |
| AGENTS-02 | 包含构建命令 | grep gradlew/mvnw/docker | 全部存在 |
| AGENTS-03 | 当前阶段正确 | 检查 "Current phase" | "Stage 12+" |

## R3-M3: 服务端并发修复

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| CONC-01 | TTL 常量存在 | grep OPERATION_KEY_TTL | 24h = 86400000ms |
| CONC-02 | cleanupExpiredKeys 方法存在 | grep cleanupExpiredKeys | 方法定义 + removeIf |
| CONC-03 | 虚拟线程执行器 | grep newVirtualThreadPerTaskExecutor | 存在 |
| CONC-04 | 无 CompletableFuture | grep CompletableFuture | 零匹配 |

## R3-M4: Deprecated API

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| DEPR-01 | AutoMirrored ArrowBack import | grep automirrored | 存在 |
| DEPR-02 | 无 Icons.Default.ArrowBack | grep "Icons.Default.ArrowBack" | 零匹配 |
| DEPR-03 | 编译通过 | compileDebugKotlin | 零错误 |
