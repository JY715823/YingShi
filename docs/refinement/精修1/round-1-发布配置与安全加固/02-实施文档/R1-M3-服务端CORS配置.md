# R1-M3: 服务端 CORS 生产配置 实施文档

## 一、目标

确保生产环境下 CORS 不允许通配符来源，避免凭据泄露风险。在 `ProductionSafetyStartupCheck` 中添加 CORS 通配符检测告警。

## 二、涉及文件清单

| 文件 | 端 | 改动类型 | 说明 |
|------|-----|---------|------|
| `ProductionSafetyStartupCheck.java` | Server | 修改 | 添加 CORS 通配符检测 |
| `.env.example` | Server | 修改 | 添加 CORS 环境变量说明 |

**不修改**的文件：
- `WebMvcConfig.java` — 逻辑已正确（有配 origin 就用指定值，无配则用通配符）
- `application.yml` — 默认空值已正确
- `application-docker.yml` — 继承默认值，通过 `.env` 覆盖即可

## 三、配置级实施方案

### 3.1 ProductionSafetyStartupCheck.java — 添加 CORS 检查

在 `run()` 方法中，在现有检查之后添加：

```java
// CORS wildcard check
String corsOrigins = environment.getProperty("app.cors.allowed-origins", "");
if (corsOrigins == null || corsOrigins.isBlank()) {
    log.warn("⚠ CORS WARNING: app.cors.allowed-origins is empty. "
            + "All origins are allowed with credentials=true. "
            + "Set APP_CORS_ALLOWED_ORIGINS for production.");
}
```

需要在类中添加 Logger：

```java
private static final Logger log = LoggerFactory.getLogger(ProductionSafetyStartupCheck.class);
```

添加 import：

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

**设计决策**：
- 使用 `log.warn` 而非 `throw`——CORS 通配符不阻断启动（避免 dev 环境突然无法使用），但会在日志中显著警告
- 检查条件与 `WebMvcConfig` 一致：`allowedOrigins` 为空时走通配符

### 3.2 .env.example — 添加 CORS 变量

在文件末尾（或 `APP_PRODUCTION_SAFETY_ENABLED` 附近）添加：

```properties
# CORS: Comma-separated list of allowed origins for production.
# Leave empty to allow all origins (WARNING: not safe for production with credentials).
# Example: APP_CORS_ALLOWED_ORIGINS=https://yingshi.example.com,https://admin.example.com
APP_CORS_ALLOWED_ORIGINS=
```

## 四、验收标准

- [ ] docker/prod profile 下未配 CORS 时启动日志有明确 WARNING 信息
- [ ] 配置 `APP_CORS_ALLOWED_ORIGINS=https://example.com` 后，WARNING 消失
- [ ] dev profile 下 localhost 访问不受影响（dev profile 不启用 productionSafety）
- [ ] 非允许来源的请求返回 CORS 错误
