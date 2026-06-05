package com.example.yingshi.feature.life

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.io.File

class WidgetMediaEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        val category = intent.getStringExtra(LifeConsoleWidgetProvider.EXTRA_CATEGORY)
            ?: LifeConsoleWidgetProvider.CATEGORY_PERSON
        setContent {
            YingShiTheme {
                WidgetMediaEntryScreen(
                    category = category,
                    onFinish = {
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    fun createCaptureUri(prefix: String, extension: String): Uri {
        val dir = File(cacheDir, "life-console-capture").also { it.mkdirs() }
        val file = File.createTempFile(prefix, extension, dir)
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun WidgetMediaEntryScreen(
    category: String,
    onFinish: () -> Unit,
) {
    val activity = LocalContext.current as WidgetMediaEntryActivity
    val colors = YingShiThemeTokens.colors
    var captureImageUriValue by rememberSaveable { mutableStateOf<String?>(null) }
    var captureVideoUriValue by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraAction by rememberSaveable { mutableStateOf<CameraAction?>(null) }
    val captureImageUri = captureImageUriValue?.let(Uri::parse)
    val captureVideoUri = captureVideoUriValue?.let(Uri::parse)

    BackHandler(onBack = onFinish)

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        LifeConsoleUploadRuntime.enqueueWidgetUpload(
            context = activity.applicationContext,
            category = category,
            uris = uris,
        )
        onFinish()
    }

    fun showLaunchFailure(message: String = "无法打开系统入口，请稍后再试") {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris -> upload(uris) }
    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) captureImageUri?.let { upload(listOf(it)) }
    }
    val captureVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success ->
        if (success) captureVideoUri?.let { upload(listOf(it)) }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null
        if (!granted || action == null) {
            Toast.makeText(activity, "需要相机权限才能拍摄", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        when (action) {
            CameraAction.Photo -> {
                val uri = runCatching { activity.createCaptureUri("life-photo-", ".jpg") }
                    .onFailure { showLaunchFailure("无法创建拍照文件") }
                    .getOrNull() ?: return@rememberLauncherForActivityResult
                captureImageUriValue = uri.toString()
                runCatching { takePicture.launch(uri) }
                    .onFailure { error ->
                        if (error is ActivityNotFoundException) {
                            showLaunchFailure("没有可用的相机应用")
                        } else {
                            showLaunchFailure()
                        }
                    }
            }
            CameraAction.Video -> {
                val uri = runCatching { activity.createCaptureUri("life-video-", ".mp4") }
                    .onFailure { showLaunchFailure("无法创建视频文件") }
                    .getOrNull() ?: return@rememberLauncherForActivityResult
                captureVideoUriValue = uri.toString()
                runCatching { captureVideo.launch(uri) }
                    .onFailure { error ->
                        if (error is ActivityNotFoundException) {
                            showLaunchFailure("没有可用的相机应用")
                        } else {
                            showLaunchFailure()
                        }
                    }
            }
        }
    }

    fun launchCamera(action: CameraAction) {
        if (!activity.hasCameraPermission()) {
            pendingCameraAction = action
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        when (action) {
            CameraAction.Photo -> {
                val uri = runCatching { activity.createCaptureUri("life-photo-", ".jpg") }
                    .onFailure { showLaunchFailure("无法创建拍照文件") }
                    .getOrNull() ?: return
                captureImageUriValue = uri.toString()
                runCatching { takePicture.launch(uri) }
                    .onFailure { error ->
                        if (error is ActivityNotFoundException) {
                            showLaunchFailure("没有可用的相机应用")
                        } else {
                            showLaunchFailure()
                        }
                    }
            }
            CameraAction.Video -> {
                val uri = runCatching { activity.createCaptureUri("life-video-", ".mp4") }
                    .onFailure { showLaunchFailure("无法创建视频文件") }
                    .getOrNull() ?: return
                captureVideoUriValue = uri.toString()
                runCatching { captureVideo.launch(uri) }
                    .onFailure { error ->
                        if (error is ActivityNotFoundException) {
                            showLaunchFailure("没有可用的相机应用")
                        } else {
                            showLaunchFailure()
                        }
                    }
            }
        }
    }

    YingShiMistBackground(showWaves = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 26.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (category == LifeConsoleWidgetProvider.CATEGORY_MEAL) "吃饭记录" else "人物记录",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = "选择来源，完成后自动回到桌面。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            WidgetMediaActionButton(
                icon = Icons.Filled.PhotoLibrary,
                text = "从相册选择",
                onClick = {
                    runCatching {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }.onFailure { showLaunchFailure("无法打开系统相册") }
                },
                enabled = true,
                emphasized = true,
            )
            WidgetMediaActionButton(
                icon = Icons.Filled.CameraAlt,
                text = "拍照",
                onClick = { launchCamera(CameraAction.Photo) },
                enabled = true,
                emphasized = false,
            )
            WidgetMediaActionButton(
                icon = Icons.Filled.Videocam,
                text = "拍视频",
                onClick = { launchCamera(CameraAction.Video) },
                enabled = true,
                emphasized = false,
            )
            WidgetMediaActionButton(
                text = "取消",
                onClick = onFinish,
                enabled = true,
                emphasized = false,
            )
        }
    }
}

@Composable
private fun WidgetMediaActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.96f,
                onClick = onClick,
            ),
        shape = shape,
        color = when {
            !enabled -> colors.sectionBackground.copy(alpha = 0.64f)
            emphasized -> colors.primaryContainer.copy(alpha = 0.88f)
            else -> colors.raisedSurface.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !enabled -> colors.dividerSoft.copy(alpha = 0.52f)
                emphasized -> colors.glassStroke.copy(alpha = 0.76f)
                else -> colors.dividerSoft.copy(alpha = 0.68f)
            },
        ),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) colors.titleAccent else colors.textSecondary,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.titleAccent else colors.textSecondary,
            )
        }
    }
}

private enum class CameraAction {
    Photo,
    Video,
}
