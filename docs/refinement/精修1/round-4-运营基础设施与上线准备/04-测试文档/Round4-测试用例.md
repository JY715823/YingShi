# Round 4 测试文档

> 覆盖 R4-M1 ~ R4-M5 全部模块

---

## R4-M1: CI/CD 流水线

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| CI-01 | Workflow 文件存在 | 检查 .github/workflows/ | android-ci.yml |
| CI-02 | JDK 21 配置 | grep setup-java | temurin, java-version: '21' |
| CI-03 | Android SDK | grep setup-android | android-actions/setup-android@v3 |
| CI-04 | Gradle 缓存 | grep actions/cache | gradle key hash |
| CI-05 | 编译步骤 | grep compileDebugKotlin | 存在 |
| CI-06 | 打包步骤 | grep assembleDebug | 存在 |
| CI-07 | 产物上传 | grep upload-artifact | APK path |
| CI-08 | 触发条件 | 检查 on: | push + pull_request on main/master |

## R4-M2: 崩溃上报

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| CR-01 | Crashlytics 版本 | libs.versions.toml | crashlytics = "3.0.4" |
| CR-02 | Crashlytics 库 | libs.versions.toml | firebase-crashlytics |
| CR-03 | Crashlytics 插件 | libs.versions.toml + root build.gradle.kts | plugin declared apply false |
| CR-04 | 条件化 apply | app/build.gradle.kts | if (google-services.json) apply crashlytics |
| CR-05 | 依赖添加 | app/build.gradle.kts | implementation(libs.firebase.crashlytics) |
| CR-06 | AppLogger 存在 | util/AppLogger.kt | e/w/i/d/v + recordException |
| CR-07 | 静默降级 | AppLogger.kt | try-catch around FirebaseCrashlytics |
| CR-08 | 编译通过 | compileDebugKotlin | 零错误 |

## R4-M3: 真机回归测试

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| TR-01 | 15 模块覆盖 | 检查清单 | 全部模块有检查项 |
| TR-02 | 双设备覆盖 | 检查清单 | 主力机 + 窄屏机 |
| TR-03 | 跨模块场景 | 检查清单 | ≥3 个端到端场景 |

## R4-M4: 隐私权限说明

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| PM-01 | CAMERA 说明 | strings.xml | permission_camera_rationale |
| PM-02 | READ_MEDIA_IMAGES | strings.xml | permission_read_media_images_rationale |
| PM-03 | READ_MEDIA_VIDEO | strings.xml | permission_read_media_video_rationale |
| PM-04 | POST_NOTIFICATIONS | strings.xml | permission_post_notifications_rationale |
| PM-05 | READ_EXTERNAL_STORAGE | strings.xml | permission_read_external_storage_rationale |

## R4-M5: 上线前终审

| TC | 名称 | 方法 | 预期 |
|----|------|------|------|
| PR-01 | P0 全部修复 | 终审报告 | 8/8 PASS |
| PR-02 | P1 大部分修复 | 终审报告 | ≥9/13 |
| PR-03 | 剩余行动项 | 终审报告 | 清单完整 |
