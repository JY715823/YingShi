plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val defaultDebugApiBaseUrl = providers.gradleProperty("YINGSHI_DEBUG_API_BASE_URL")
    .orElse(providers.environmentVariable("YINGSHI_DEBUG_API_BASE_URL"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "http://10.106.3.193:8080/"

val configuredReleaseApiBaseUrl = providers.gradleProperty("YINGSHI_RELEASE_API_BASE_URL")
    .orElse(providers.environmentVariable("YINGSHI_RELEASE_API_BASE_URL"))
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

fun normalizeApiBaseUrl(rawUrl: String): String {
    require(rawUrl.startsWith("https://")) {
        "YINGSHI_RELEASE_API_BASE_URL must be an HTTPS URL."
    }
    return if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
}

fun normalizeDebugApiBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "YINGSHI_DEBUG_API_BASE_URL must be an HTTP or HTTPS URL."
    }
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

val releaseApiBaseUrl = configuredReleaseApiBaseUrl
    ?.let(::normalizeApiBaseUrl)
    ?: "https://release-api-url-not-configured.invalid/"

gradle.taskGraph.whenReady {
    val releaseTaskRequested = allTasks.any { task ->
        task.name.contains("Release")
    }
    if (releaseTaskRequested && configuredReleaseApiBaseUrl == null) {
        throw GradleException(
            "Release builds require YINGSHI_RELEASE_API_BASE_URL=https://your-api-domain/ " +
                "via Gradle property or environment variable.",
        )
    }
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.yingshi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.yingshi"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"${normalizeDebugApiBaseUrl(defaultDebugApiBaseUrl)}\"")
        }
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release", "debug")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"${normalizeDebugApiBaseUrl(defaultDebugApiBaseUrl)}\"")
        }
        create("optimizedDebug") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release", "debug")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"${normalizeDebugApiBaseUrl(defaultDebugApiBaseUrl)}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$releaseApiBaseUrl\"")
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.ui)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation("com.github.xxinPro:SilkDecoder:1.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
