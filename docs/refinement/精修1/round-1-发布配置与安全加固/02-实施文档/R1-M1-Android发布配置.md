# R1-M1: Android 发布配置 实施文档

## 一、目标

为 release 构建配置签名、代码混淆（R8）和资源压缩，确保可生成可发布的 APK。同时关闭 allowBackup 防止数据被 adb 提取。

## 二、涉及文件清单

| 文件 | 端 | 改动类型 | 说明 |
|------|-----|---------|------|
| `app/build.gradle.kts` | Android | 修改 | 添加 signingConfigs + release minify/shrink |
| `app/proguard-rules.pro` | Android | 重写 | 完整的 R8 keep 规则 |
| `app/src/main/AndroidManifest.xml` | Android | 修改 | allowBackup=false + dataExtractionRules |
| `app/src/main/res/xml/data_extraction_rules.xml` | Android | 新增 | Android 12+ 数据提取规则 |
| `keystore.properties.example` | Android | 新增 | 签名配置模板 |
| `.gitignore` | Android | 修改 | 添加 keystore.properties 排除 |

## 三、配置级实施方案

### 3.1 build.gradle.kts — 添加 signingConfigs

在 `android {}` 块内、`buildTypes {}` 之前添加：

```kotlin
// 加载签名配置
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = if (keystorePropertiesFile.exists()) {
    java.util.Properties().apply {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
} else null

signingConfigs {
    create("release") {
        if (keystoreProperties != null) {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
}
```

### 3.2 build.gradle.kts — 修改 release buildType

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    if (keystoreProperties != null) {
        signingConfig = signingConfigs.getByName("release")
    }
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
    buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$releaseApiBaseUrl\"")
}
```

**注意**：`signingConfig` 仅在 `keystore.properties` 存在时才设置。不存在时 release 构建仍可编译（但 APK 未签名，无法安装）。这避免了 CI 环境缺少密钥文件时构建失败。

### 3.3 proguard-rules.pro — 完整规则

```proguard
# ===== General =====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# ===== Kotlin =====
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== Retrofit + OkHttp + Gson =====
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# ===== Firebase =====
-keep class com.google.firebase.** { *; }

# ===== Media3 ExoPlayer =====
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ===== Compose =====
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===== SilkDecoder (JNI) =====
-keep class com.github.xxinPro.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== App data classes (DTOs for Gson serialization) =====
-keep class com.example.yingshi.data.model.** { *; }
-keep class com.example.yingshi.data.remote.**Dto* { *; }

# ===== Coil =====
-dontwarn coil.**
```

### 3.4 AndroidManifest.xml — allowBackup

将 `android:allowBackup="true"` 改为 `android:allowBackup="false"`。

移除 `android:dataExtractionRules` 引用（因为 allowBackup=false 已足够）。保留 `android:fullBackupContent` 指向 backup_rules.xml（文件已存在）。

### 3.5 data_extraction_rules.xml — 新建

创建空规则文件，声明排除所有数据：

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
    </cloud-backup>
    <device-transfer>
    </device-transfer>
</data-extraction-rules>
```

### 3.6 keystore.properties.example

```properties
# YingShi Release Signing Configuration
# Copy this file to keystore.properties and fill in your values.
# NEVER commit keystore.properties to version control.

storeFile=path/to/your/keystore.jks
storePassword=your-store-password
keyAlias=yingshi-release
keyPassword=your-key-password
```

### 3.7 .gitignore 添加

```
# Signing configuration
keystore.properties
signing/
```

## 四、验收标准

- [ ] `gradlew.bat assembleRelease` 在配置 keystore.properties 后生成已签名 APK
- [ ] 未配置 keystore.properties 时 `assembleRelease` 仍可编译（APK 未签名但构建不失败）
- [ ] release APK 体积比 debug 显著减小（minify + shrinkResources 生效）
- [ ] 混淆后 APK 功能正常（Retrofit、Room、FCM、Compose、SilkDecoder）
- [ ] `.gitignore` 排除 `keystore.properties`
- [ ] `adb backup` 无法提取应用数据
