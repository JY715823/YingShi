# Round 1 测试用例

> 覆盖 R1-M1 ~ R1-M5 全部模块

---

## R1-M1: Android 发布配置

### TC-001: build.gradle.kts 语法正确性
- **测试方法**: `gradlew.bat assembleDebug` 编译通过
- **预期**: 零错误，可能有 deprecation 警告

### TC-002: Release buildType 配置验证
- **测试方法**: 检查 build.gradle.kts 中 release 块
- **预期**: `isMinifyEnabled = true`、`isShrinkResources = true`、`signingConfig` 条件设置

### TC-003: ProGuard 规则完整性
- **测试方法**: 检查 proguard-rules.pro 包含所有关键库的 keep 规则
- **预期**: Retrofit、Gson、Room、Firebase、Media3、Compose、SilkDecoder 均有 -keep 或 -dontwarn

### TC-004: allowBackup 关闭
- **测试方法**: 检查 AndroidManifest.xml
- **预期**: `android:allowBackup="false"`

### TC-005: data_extraction_rules.xml 格式正确
- **测试方法**: 检查 XML 文件存在且格式合法
- **预期**: 文件存在，包含空的 cloud-backup 和 device-transfer 规则

### TC-006: keystore.properties.example 存在
- **测试方法**: 检查文件存在且包含 4 个必要字段
- **预期**: storeFile、storePassword、keyAlias、keyPassword

### TC-007: .gitignore 排除 keystore.properties
- **测试方法**: 检查 .gitignore 包含 `keystore.properties`
- **预期**: 文件中存在该规则

## R1-M2: 调试常量清理

### TC-008: BackendAutoLoginManager.kt 无硬编码密码
- **测试方法**: grep 文件内容
- **预期**: 无 `"123456"` 字面量（BuildConfig 赋值除外）

### TC-009: BackendAutoLoginManager.kt 无硬编码邮箱
- **测试方法**: grep 文件内容
- **预期**: 无 `"1085060329@qq.com"` 或 `"2926315047@qq.com"` 字面量

### TC-010: LoginScreen.kt 引用 BuildConfig
- **测试方法**: 检查 LoginScreen.kt 的 import 和引用
- **预期**: 无 `DEFAULT_PRIMARY_ACCOUNT` 等裸引用，全部使用 `BuildConfig.XXX`

### TC-011: LoginScreen.kt 编译通过
- **测试方法**: `gradlew.bat assembleDebug`
- **预期**: 编译零错误

### TC-012: buildConfigField 声明完整
- **测试方法**: 检查 build.gradle.kts
- **预期**: defaultConfig 有 PRIMARY/SECONDARY_ACCOUNT，debug 有 TEMP_PASSWORD="123456"，release 有 TEMP_PASSWORD=""

## R1-M3: 服务端 CORS 配置

### TC-013: ProductionSafetyStartupCheck 包含 CORS 检查
- **测试方法**: 检查 Java 源文件
- **预期**: run() 方法中有 `app.cors.allowed-origins` 检查和 `log.warn`

### TC-014: ProductionSafetyStartupCheck 编译通过
- **测试方法**: `mvnw.cmd compile`
- **预期**: 编译零错误

### TC-015: .env.example 包含 CORS 变量
- **测试方法**: 检查文件内容
- **预期**: 包含 `APP_CORS_ALLOWED_ORIGINS=` 和注释说明

## R1-M4: HTTPS 部署加固

### TC-016: nginx-https.conf 包含安全头
- **测试方法**: 检查文件内容
- **预期**: 包含 HSTS、X-Frame-Options、X-Content-Type-Options、X-XSS-Protection、Referrer-Policy

### TC-017: nginx-https.conf X-Forwarded-For 使用 $remote_addr
- **测试方法**: 检查文件内容
- **预期**: `proxy_set_header X-Forwarded-For $remote_addr`（非 $proxy_add_x_forwarded_for）

### TC-018: docker-compose.prod.yml 结构正确
- **测试方法**: 检查文件包含 nginx + extends 配置
- **预期**: 5 个服务（nginx + 4 extends），server 端口绑 127.0.0.1

## R1-M5: V24 孤儿数据审计

### TC-019: audit-orphans.sql 覆盖全部 FK 关系
- **测试方法**: 检查 SQL 文件覆盖 V24 中的表对
- **预期**: 至少 25 个 SELECT COUNT 查询

### TC-020: fix-orphans.sql 软修复可直接执行
- **测试方法**: 检查 BEGIN/COMMIT 包裹，UPDATE 语句语法正确
- **预期**: 软修复部分无语法错误，硬修复部分默认注释
