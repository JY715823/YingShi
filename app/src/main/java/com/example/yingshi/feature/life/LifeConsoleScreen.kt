package com.example.yingshi.feature.life

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.yingshi.data.model.RemoteLifeConsoleBowelEvent
import com.example.yingshi.data.model.RemoteLifeConsoleBowelHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleBowelUserSummary
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.AppContentMediaThumbnail
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.rememberCollaboratorDirectorySnapshot
import com.example.yingshi.feature.photos.resolveAppMediaType
import com.example.yingshi.feature.photos.toAppContentMediaSource
import com.example.yingshi.feature.photos.TrashDialogActionButton
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.components.TitleTabs
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

enum class LifeConsoleHistoryRange(val label: String, val limitDays: Int) {
    LAST_7("近7天", 7),
    LAST_30("近30天", 30),
    ALL("全部", 365),
}

@Composable
fun LifeConsoleScreen(
    modifier: Modifier = Modifier,
    initialSlotKey: String? = null,
    initialMediaId: String? = null,
    onBack: () -> Unit = {},
    onOpenLedgerAdd: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val zoneId = LIFE_CONSOLE_ZONE_ID

    val viewModel: LifeConsoleViewModel = viewModel(
        factory = LifeConsoleViewModel.factory(
            application = context.applicationContext as Application,
            zoneId = zoneId,
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        viewModel.showNotice(message, tone)
    }

    // Round 8 第十四轮: 相册上传改用 ACTION_GET_CONTENT (OpenMultiple) 而非 Photo Picker.
    // 原因: Android 13+ Photo Picker 会对返回的 Uri 做隐私 redaction — 主动剥离 EXIF GPS
    // 字节范围, 并屏蔽 MediaStore 的 latitude/longitude 列. 改用 ACTION_GET_CONTENT 能拿到
    // 完整的原始文件 (含 EXIF GPS), 与相册 app 详情页显示的"地点"一致.
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        val category = uiState.pendingUploadCategory ?: return@rememberLauncherForActivityResult
        viewModel.setPendingUploadCategory(null)
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        viewModel.onUploadResult(uris = uris, category = category, isFromCamera = false)
    }

    // Round 8 第十四轮: 拍照上传 launcher (TakePicture 一次拍一张)
    // 拍照上传会触发即时 GPS 定位, 相册上传读 EXIF GPS
    val cameraPhotoUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraPhotoUri.value
        val category = uiState.pendingUploadCategory
        viewModel.setPendingUploadCategory(null)
        cameraPhotoUri.value = null
        if (success && uri != null && category != null) {
            viewModel.onUploadResult(uris = listOf(uri), category = category, isFromCamera = true)
        }
    }

    // Round 8 第十六轮: CAMERA 运行时权限 — Android 6.0+ 必须主动请求, 否则 TakePicture 抛 SecurityException
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // 权限刚授予, uri 与 category 已在 launchCamera 中预置, 直接启动相机
            val uri = cameraPhotoUri.value
            val category = uiState.pendingUploadCategory
            if (uri != null && category != null) {
                try {
                    cameraLauncher.launch(uri)
                } catch (e: android.content.ActivityNotFoundException) {
                    viewModel.setPendingUploadCategory(null)
                    cameraPhotoUri.value = null
                    showNotice("没有可用的相机应用", YingShiNoticeTone.WARNING)
                } catch (e: Exception) {
                    viewModel.setPendingUploadCategory(null)
                    cameraPhotoUri.value = null
                    showNotice("无法启动相机: ${e.message}", YingShiNoticeTone.WARNING)
                }
            }
        } else {
            viewModel.setPendingUploadCategory(null)
            cameraPhotoUri.value = null
            showNotice("需要相机权限才能拍照", YingShiNoticeTone.WARNING)
        }
    }

    fun launchCamera(category: String) {
        val ctx = context
        // Round 8 第十六轮: 先检查 CAMERA 运行时权限
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        // Round 8 第十七轮: 将临时文件创建 + FileProvider URI 生成移入 try-catch,
        // 避免这些步骤抛异常时直接崩溃 (用户看到的是"应用停止运行"而非友好提示).
        // 同时处理 ActivityNotFoundException (无相机应用) 等具体异常.
        cameraPhotoUri.value = null
        viewModel.setPendingUploadCategory(category)
        val authority = "${ctx.packageName}.fileprovider"
        val uri = try {
            val captureDir = java.io.File(ctx.cacheDir, "life-console-capture").also { it.mkdirs() }
            val tmpFile = java.io.File.createTempFile("life_camera_${System.currentTimeMillis()}", ".jpg", captureDir)
            androidx.core.content.FileProvider.getUriForFile(ctx, authority, tmpFile)
        } catch (e: Exception) {
            viewModel.setPendingUploadCategory(null)
            showNotice("无法创建拍照文件: ${e.message}", YingShiNoticeTone.WARNING)
            return
        }
        cameraPhotoUri.value = uri
        try {
            if (!hasCameraPermission) {
                // 先请求权限, 授予后由 cameraPermissionLauncher 回调启动相机
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            } else {
                cameraLauncher.launch(uri)
            }
        } catch (e: android.content.ActivityNotFoundException) {
            viewModel.setPendingUploadCategory(null)
            cameraPhotoUri.value = null
            showNotice("没有可用的相机应用", YingShiNoticeTone.WARNING)
        } catch (e: Exception) {
            viewModel.setPendingUploadCategory(null)
            cameraPhotoUri.value = null
            showNotice("无法启动相机: ${e.message}", YingShiNoticeTone.WARNING)
        }
    }

    // Round 7 阶段 7: 位置选择页 launcher
    val locationPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val lat = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LAT, Double.NaN)
            val lng = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LNG, Double.NaN)
            val label = data.getStringExtra(LifeLocationPickerActivity.EXTRA_RESULT_LABEL)
            val target = uiState.pendingLocationUpdateTarget ?: return@rememberLauncherForActivityResult
            viewModel.setPendingLocationUpdateTarget(null)
            val safeLat = if (lat.isNaN()) null else lat
            val safeLng = if (lng.isNaN()) null else lng
            when (target) {
                is LocationUpdateTarget.Media -> viewModel.updateMediaLocation(target.mediaId, safeLat, safeLng, label)
                is LocationUpdateTarget.Bowel -> viewModel.updateBowelEventLocation(target.eventId, safeLat, safeLng, label)
            }
        }
    }

    fun launchLocationPicker(target: LocationUpdateTarget) {
        viewModel.setPendingLocationUpdateTarget(target)
        locationPickerLauncher.launch(
            LifeLocationPickerActivity.intent(
                context = context,
                initialLat = target.initialLat,
                initialLng = target.initialLng,
                initialLabel = target.initialLabel,
                title = when (target) {
                    is LocationUpdateTarget.Media -> "调整照片位置"
                    is LocationUpdateTarget.Bowel -> "调整记录位置"
                },
            )
        )
    }

    // FR-19: Request location permission at runtime so GPS data is available for uploads
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — LocationHelper checks permission each time */ }
    LaunchedEffect(Unit) {
        if (!LocationHelper.hasLocationPermission(context)) {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(uiState.showHistoryPage, uiState.historyRange) {
        if (uiState.showHistoryPage) {
            viewModel.refreshHistory()
        }
    }

    // FR-6: 错误分级展示 — 使用 YingShiNotice 展示分级错误
    LaunchedEffect(uiState.errorType, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        val tone = when (uiState.errorType) {
            LifeConsoleErrorType.NETWORK -> YingShiNoticeTone.WARNING
            LifeConsoleErrorType.TIMEOUT -> YingShiNoticeTone.WARNING
            LifeConsoleErrorType.SERVER -> YingShiNoticeTone.WARNING
            else -> YingShiNoticeTone.WARNING
        }
        showNotice(msg, tone)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Round 8 第十九轮: 持续定位 (30s 间隔 + 50m 最小距离).
    // 进入今日痕迹页启动, 离开停止. 拍照上传时优先读缓存 (0ms), 为 null 才 fallback 到一次性请求.
    // 用 ON_RESUME/ON_PAUSE 而非 onDispose, 确保页面切到后台时也停止省电.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> LocationHelper.startContinuousUpdates(context)
                Lifecycle.Event.ON_PAUSE -> LocationHelper.stopContinuousUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 兜底: 确保页面销毁时一定停止
            LocationHelper.stopContinuousUpdates()
        }
    }

    if (uiState.showHistoryPage) {
        BackHandler { viewModel.setShowHistoryPage(false) }
        LifeConsoleHistoryPage(
            history = uiState.history,
            isLoading = uiState.isHistoryLoading,
            actionMessage = uiState.actionMessage,
            selectedRange = uiState.historyRange,
            onRangeChange = { viewModel.setHistoryRange(it) },
            onBack = { viewModel.setShowHistoryPage(false) },
            onRefresh = { viewModel.refreshHistory() },
            modifier = modifier,
            onOpenMedia = { media, slotKey ->
                context.startActivity(LifeMediaQuickViewerActivity.intent(context, media, slotKey))
            },
            onLocationClick = { target -> launchLocationPicker(target) },
            onUpload = { category, isFromCamera ->
                if (isFromCamera) {
                    launchCamera(category)
                } else {
                    viewModel.setPendingUploadCategory(category)
                    pickerLauncher.launch("image/*")
                }
            },
            onDelete = { category, mediaId ->
                viewModel.setPendingDeleteTarget(mediaId, category)
            },
        )
        return
    }
    BackHandler(onBack = onBack)

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = false,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        // Round 8: 不再使用 ShellPage — 自己管理布局，让标题+返回键同一行，
        // 并给 HorizontalPager 有限高度约束，避免 verticalScroll 导致子级拿到无限高度。
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // 第1行: 返回键 + 标题同一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .yingShiClickable(shape = RoundedCornerShape(12.dp), pressedScale = 0.94f, onClick = onBack),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.sectionBackground.copy(alpha = 0.80f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.titleAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = "今日痕迹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.titleAccent,
                    modifier = Modifier.weight(1f),
                )
            }
            // 第2行: 刷新 + 历史记录 + 回收站按钮 (右对齐)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LifeConsolePillAction(
                    text = "刷新",
                    icon = Icons.Filled.Refresh,
                    onClick = {
                        viewModel.loadToday()
                        viewModel.loadHistory()
                    },
                    enabled = !uiState.isLoading && !uiState.isHistoryLoading,
                    containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                    contentColor = colors.titleAccent,
                )
                LifeConsolePillAction(
                    text = "历史记录",
                    onClick = { viewModel.setShowHistoryPage(true) },
                    enabled = !uiState.isHistoryLoading,
                    containerColor = colors.sectionBackground.copy(alpha = 0.90f),
                    contentColor = colors.titleAccent,
                )
                // P1-2: 今日痕迹回收站入口 (人物/吃饭 媒体回收 + 24h 撤回中心)
                LifeConsolePillAction(
                    text = "回收站",
                    icon = Icons.Filled.Delete,
                    onClick = { context.startActivity(LifeTrashActivity.intent(context)) },
                    containerColor = colors.sectionBackground.copy(alpha = 0.90f),
                    contentColor = colors.titleAccent,
                )
            }
            // 离线 Banner + 错误消息
            // FR-6: 带 errorType 的错误已由顶部 YingShiNotice 分级展示，
            // 此处仅展示未走 Notice 的纯文本消息（如上传/删除/排便操作错误），避免双重展示。
            if (uiState.actionMessage != null && uiState.errorType == null) {
                Text(
                    text = uiState.actionMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            AnimatedVisibility(visible = uiState.offlineMode) {
                Surface(
                    shape = RoundedCornerShape(radius.capsule),
                    color = colors.primaryContainer.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, colors.primaryAction.copy(alpha = 0.18f)),
                    modifier = Modifier.yingShiClickable(
                        onClick = {
                            viewModel.loadToday()
                            viewModel.loadHistory()
                        },
                    ),
                ) {
                    Text(
                        text = "离线数据 · 点击刷新",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.titleAccent,
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xxs + 4.dp),
                    )
                }
            }
            StaleBanner(
                module = SyncModule.LIFE_CONSOLE,
                onRefresh = {
                    viewModel.loadToday()
                    viewModel.loadHistory()
                    SyncVersionTracker.markRefreshed(SyncModule.LIFE_CONSOLE)
                },
            )
            // 主体内容: 占满剩余空间，给 HorizontalPager 有限高度
            when {
                uiState.isLoading && uiState.snapshot == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAction)
                    }
                }
                uiState.snapshot == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        LifeConsoleTodayEmptyState()
                    }
                }
                else -> {
                    val today = requireNotNull(uiState.snapshot)
                    // Round 8: 上传进度条
                    if (uiState.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50)),
                            color = colors.primaryAction,
                            trackColor = colors.dividerSoft.copy(alpha = 0.34f),
                        )
                    }
                    LifeConsoleTodayPager(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        snapshot = today,
                        isBusy = uiState.isLoading,
                        isAddingBowel = uiState.isAddingBowel,
                        pendingLocationBowelEventIds = uiState.pendingLocationBowelEventIds,
                        initialSlotKey = uiState.lastUploadedSlotKey ?: initialSlotKey,
                        initialMediaId = uiState.lastUploadedMediaId ?: initialMediaId,
                        pendingLocationMediaIds = uiState.pendingLocationMediaIds,
                        onUploadConsumed = viewModel::consumeLastUploadedMediaId,
                        onOpenMedia = { media, slotKey ->
                            context.startActivity(LifeMediaQuickViewerActivity.intent(context, media, slotKey))
                        },
                        onUpload = { category, isFromCamera ->
                            if (isFromCamera) {
                                launchCamera(category)
                            } else {
                                viewModel.setPendingUploadCategory(category)
                                pickerLauncher.launch("image/*")
                            }
                        },
                        onDelete = { category, mediaId ->
                            viewModel.setPendingDeleteTarget(mediaId, category)
                        },
                        onAddBowel = { viewModel.addBowelEvent() },
                        onRemoveBowel = { viewModel.requestBowelDelete() },
                        onLocationClick = { target -> launchLocationPicker(target) },
                    )
                }
            }
        }

        YingShiNoticeHost(
            notice = uiState.notice,
            onExpired = { nonce ->
                if (uiState.notice?.nonce == nonce) {
                    viewModel.dismissNotice(nonce)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = spacing.md),
        )
    }

    uiState.pendingDeleteTarget?.let { (category, mediaId) ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteMedia() },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "从今日痕迹移除这张媒体？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "移除后会从今天的人物或吃饭格子里消失，但不会影响已经导入照片流的内容。",
                    style = YingShiThemeTokens.typography.body,
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "继续移除",
                    emphasized = true,
                    onClick = { viewModel.confirmDeleteMedia() },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { viewModel.cancelDeleteMedia() })
            },
        )
    }

    // Round 8 第九轮: 大便删除确认对话框
    if (uiState.pendingBowelDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelBowelDelete() },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "删除最近一条大便记录？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "将删除今天最近一次记录，删除后不可恢复。",
                    style = YingShiThemeTokens.typography.body,
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "删除",
                    emphasized = true,
                    onClick = { viewModel.removeBowelEvent() },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { viewModel.cancelBowelDelete() })
            },
        )
    }
}

@Composable
private fun LifeConsoleTodayEmptyState(modifier: Modifier = Modifier) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // FR-2: 温度空态 — 圆形图标 + 温暖文案
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.memoryWash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = colors.memoryAccent.copy(alpha = 0.72f),
                )
            }
            Text(
                text = "今天还没有痕迹",
                style = YingShiThemeTokens.typography.cardTitle,
                color = colors.textPrimary.copy(alpha = 0.82f),
            )
            Text(
                text = "拍下今天的第一张照片吧",
                style = YingShiThemeTokens.typography.caption,
                color = colors.textSecondary.copy(alpha = 0.62f),
            )
        }
    }
}

@Composable
private fun LifeConsoleHistoryPage(
    history: RemoteLifeConsoleHistory?,
    isLoading: Boolean,
    actionMessage: String?,
    selectedRange: LifeConsoleHistoryRange,
    onRangeChange: (LifeConsoleHistoryRange) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
    onUpload: (String, Boolean) -> Unit = { _, _ -> },
    onDelete: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = false,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LifeConsoleBackButton(onClick = onBack)
                Text(
                    text = "历史记录",
                    modifier = Modifier.weight(1f),
                    style = YingShiThemeTokens.typography.sectionTitle,
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LifeConsolePillAction(
                    text = "刷新",
                    icon = Icons.Filled.Refresh,
                    onClick = onRefresh,
                    enabled = !isLoading,
                    containerColor = colors.primaryContainer.copy(alpha = 0.82f),
                    contentColor = colors.titleAccent,
                )
            }
            if (!actionMessage.isNullOrBlank()) {
                Text(
                    text = actionMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = YingShiThemeTokens.typography.caption,
                )
            }
            LifeConsoleHistoryPanel(
                history = history,
                isLoading = isLoading,
                selectedRange = selectedRange,
                onRangeChange = onRangeChange,
                onOpenMedia = onOpenMedia,
                onLocationClick = onLocationClick,
                onUpload = onUpload,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Round 8 问题3: 历史页查看模式
private enum class LifeConsoleHistoryViewMode(val label: String) {
    AGGREGATED("聚合"),   // 一个框左右滑动 + 时间地点
    THUMBNAIL("缩略图"),  // 两人左右排列，无时间地点
}

@Composable
private fun LifeConsoleHistoryPanel(
    history: RemoteLifeConsoleHistory?,
    isLoading: Boolean,
    selectedRange: LifeConsoleHistoryRange,
    onRangeChange: (LifeConsoleHistoryRange) -> Unit,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
    onUpload: (String, Boolean) -> Unit = { _, _ -> },
    onDelete: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )
    // Round 8 问题3: 查看模式状态
    var viewMode by remember { mutableStateOf(LifeConsoleHistoryViewMode.AGGREGATED) }
    // 响应式用户名: 优先使用 CollaboratorDirectoryStore (实时更新), 回退到服务端快照
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)
    val resolvedSelfLabel = collaboratorDirectory.currentUser?.displayName
        ?: history?.currentUser?.displayName
        ?: "我"
    val resolvedPartnerLabel = collaboratorDirectory.partner?.displayName
        ?: history?.partner?.displayName
        ?: "对方"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // FR-3: 胶囊选择器 + Tab 切换行（分两行）
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TitleTabs(
                tabs = listOf("人物", "吃饭", "大便"),
                selectedIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                onSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            )
            // Round 8 问题3: 范围选择器 + 查看模式切换 (同一行)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧: 范围选择器
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    LifeConsoleHistoryRange.entries.forEach { range ->
                        val selected = range == selectedRange
                        Surface(
                            shape = RoundedCornerShape(radius.capsule),
                            color = if (selected) colors.primaryContainer.copy(alpha = 0.72f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (selected) colors.glassStroke else colors.dividerSoft.copy(alpha = 0.62f),
                            ),
                            modifier = Modifier.yingShiClickable(
                                pressedScale = 0.96f,
                                onClick = { onRangeChange(range) },
                            ),
                        ) {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) colors.titleAccent else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs + 2.dp),
                            )
                        }
                    }
                }
                // 右侧: 查看模式切换 (仅人物/吃饭页显示)
                if (pagerState.currentPage != 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        LifeConsoleHistoryViewMode.entries.forEach { mode ->
                            val selected = mode == viewMode
                            Surface(
                                shape = RoundedCornerShape(radius.capsule),
                                color = if (selected) colors.softGreenContainer.copy(alpha = 0.72f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) colors.softGreenAction.copy(alpha = 0.62f) else colors.dividerSoft.copy(alpha = 0.62f),
                                ),
                                modifier = Modifier.yingShiClickable(
                                    pressedScale = 0.96f,
                                    onClick = { viewMode = mode },
                                ),
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) colors.titleAccent else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs + 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // FR-3: 美化统计
        if (history != null) {
            val totalDays = maxOf(history.personDays.size, history.mealDays.size, history.bowelDays.size)
            if (totalDays > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共 ",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "$totalDays",
                        style = YingShiThemeTokens.typography.statNumber,
                        color = colors.goldAccent,
                    )
                    Text(
                        text = " 天记录",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when {
                isLoading && history == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAction)
                    }
                }
                history == null -> {
                    LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                }
                page == 2 -> {
                    // FR-3: 大便历史时间轴
                    if (history.bowelDays.isEmpty()) {
                        LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            itemsIndexed(history.bowelDays) { index, day ->
                                LifeConsoleHistoryTimelineItem(
                                    isLast = index == history.bowelDays.lastIndex,
                                    dateLabel = day.displayLabel,
                                    // Round 8: 日期旁不再显示日级聚合地点, 每条 event 自己有地点即可
                                    locationLabel = null,
                                ) {
                                    LifeConsoleHistoryBowelDayCard(
                                        day = day,
                                        selfUserId = history.currentUser.userId,
                                        partnerUserId = history.partner?.userId,
                                        selfLabel = resolvedSelfLabel,
                                        partnerLabel = resolvedPartnerLabel,
                                        onLocationClick = onLocationClick,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    val days = if (page == 0) history.personDays else history.mealDays
                    // Round 8 第十五轮: 历史 day 卡片增加添加/删除入口, category 由当前 tab 推导
                    val category = if (page == 0) "PERSON" else "MEAL"
                    if (days.isEmpty()) {
                        LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        // Round 8 问题3: 根据 viewMode 渲染不同布局
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            itemsIndexed(days) { index, day ->
                                LifeConsoleHistoryTimelineItem(
                                    isLast = index == days.lastIndex,
                                    dateLabel = day.displayLabel,
                                    // Round 8: 日期旁不再显示日级聚合地点, 每个媒体自己有地点即可
                                    locationLabel = null,
                                ) {
                                    LifeConsoleHistoryDaySection(
                                        day = day,
                                        selfLabel = resolvedSelfLabel,
                                        partnerLabel = resolvedPartnerLabel,
                                        viewMode = viewMode,
                                        category = category,
                                        onOpenMedia = onOpenMedia,
                                        onLocationClick = onLocationClick,
                                        onUpload = onUpload,
                                        onDelete = onDelete,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FR-3: 时间轴条目组件
@Composable
private fun LifeConsoleHistoryTimelineItem(
    isLast: Boolean,
    dateLabel: String,
    locationLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Row(modifier = Modifier.fillMaxWidth()) {
        // 左侧时间线
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            // 日期节点圆点
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.titleAccent),
            )
            // 竖线
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(colors.dividerSoft.copy(alpha = 0.62f)),
                )
            }
        }
        Spacer(Modifier.width(spacing.xs))
        // 右侧内容
        Column(modifier = Modifier.weight(1f)) {
            // FR-20: 日期 + 位置标签（如有）放在同一行
            if (locationLabel.isNullOrBlank()) {
                Text(
                    text = dateLabel,
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.titleAccent,
                    modifier = Modifier.padding(bottom = spacing.xxs),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dateLabel,
                        style = YingShiThemeTokens.typography.statLabel,
                        color = colors.titleAccent,
                    )
                    // FR-20: 位置标签胶囊
                    Text(
                        text = "📍 $locationLabel",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(start = spacing.xs)
                            .clip(RoundedCornerShape(50))
                            .background(colors.dividerSoft.copy(alpha = 0.34f))
                            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun LifeConsoleHistoryBowelDayCard(
    day: RemoteLifeConsoleBowelHistoryDay,
    selfUserId: String,
    partnerUserId: String?,
    selfLabel: String,
    partnerLabel: String,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    YingShiMistCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        borderless = true,
        color = colors.raisedSurface,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // FR-3: 增强历史大肠卡片 — emoji + 大号统计
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "💩",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${day.users.sumOf { it.count }} 次",
                    style = YingShiThemeTokens.typography.statNumber,
                    color = colors.titleAccent,
                )
            }
            // Round 8: 逐 event 单独展示，每条 event 时间和地点各占一行（与今日页一致）。
            // 双方都展示，无地点时显示"添加地点"胶囊，可点击进入位置选择页。
            day.users.forEach { user ->
                val name = when (user.userId) {
                    selfUserId -> selfLabel
                    partnerUserId -> partnerLabel
                    else -> user.userId
                }
                // 用户名标签
                Text(
                    text = "$name · ${user.count} 次",
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.textSecondary,
                )
                // 逐条 event 展示 (每条单独一个 Column, 避免与同用户其它 event 混淆)
                user.events?.forEach { event ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.sectionBackground.copy(alpha = 0.50f))
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        // 第1行: 时间 (单独一行)
                        Text(
                            text = "🕐 ${formatFullTime(event.occurredAtMillis)}",
                            style = YingShiThemeTokens.typography.caption,
                            color = colors.textSecondary.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 第2行: 地点 (无地点时显示"添加地点"胶囊, 可点击)
                        val locationLabel = event.locationLabel
                        if (!locationLabel.isNullOrBlank()) {
                            Text(
                                text = "📍 $locationLabel",
                                style = YingShiThemeTokens.typography.caption,
                                color = colors.textSecondary.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Bowel(
                                                eventId = event.bowelEventId,
                                                initialLat = event.latitude,
                                                initialLng = event.longitude,
                                                initialLabel = event.locationLabel,
                                            )
                                        )
                                    },
                                ),
                            )
                        } else {
                            Text(
                                text = "📍 添加地点",
                                style = YingShiThemeTokens.typography.caption,
                                color = colors.textSecondary.copy(alpha = 0.50f),
                                modifier = Modifier.yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Bowel(
                                                eventId = event.bowelEventId,
                                                initialLat = event.latitude,
                                                initialLng = event.longitude,
                                                initialLabel = event.locationLabel,
                                            )
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryDaySection(
    day: RemoteLifeConsoleHistoryDay,
    selfLabel: String,
    partnerLabel: String,
    viewMode: LifeConsoleHistoryViewMode,
    category: String,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
    onUpload: (String, Boolean) -> Unit = { _, _ -> },
    onDelete: (String, String) -> Unit = { _, _ -> },
) {
    // Round 8 第十六轮: 由 category 拼接 self/partner slotKey
    val slotKeySelf = "${category.lowercase()}_self"
    val slotKeyPartner = "${category.lowercase()}_partner"
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    YingShiMistCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
        borderless = true,
        color = colors.raisedSurface,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // 统计摘要
            Text(
                text = "共 ${day.selfMedia.size + day.partnerMedia.size} 张",
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.goldAccent,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            when (viewMode) {
                LifeConsoleHistoryViewMode.AGGREGATED -> {
                    // Round 8 问题3: 聚合模式 — 我和对方各一个聚合框，上下排列
                    // 每个聚合框内左右滑动切换照片，下面显示时间和地点各一行
                    // Round 8 第十五轮: 自己的框可以添加/删除; 对方的框不可操作 (与今日页一致)
                    if (day.selfMedia.isNotEmpty()) {
                        LifeHistoryAggregatedFrame(
                            ownerLabel = selfLabel,
                            mediaItems = day.selfMedia,
                            accentColor = colors.memoryAccent,
                            canEdit = true,
                            category = category,
                            slotKey = slotKeySelf,
                            onOpenMedia = onOpenMedia,
                            onLocationClick = onLocationClick,
                            onUpload = onUpload,
                            onDelete = onDelete,
                        )
                    }
                    if (day.partnerMedia.isNotEmpty()) {
                        LifeHistoryAggregatedFrame(
                            ownerLabel = partnerLabel,
                            mediaItems = day.partnerMedia,
                            accentColor = colors.goldAccent,
                            canEdit = false,
                            category = category,
                            slotKey = slotKeyPartner,
                            onOpenMedia = onOpenMedia,
                            onLocationClick = onLocationClick,
                        )
                    }
                }
                LifeConsoleHistoryViewMode.THUMBNAIL -> {
                    // Round 8: 缩略图模式 — 双方左右排列, 每方内部一行2个, 一行共4个, 卡片做大
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        // 我 — 占半屏宽度, 内部一行2个
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.memoryAccent.copy(alpha = 0.68f)),
                                )
                                Text(
                                    text = "$selfLabel · ${day.selfMedia.size}",
                                    style = YingShiThemeTokens.typography.statLabel,
                                    color = colors.textSecondary,
                                )
                            }
                            LifeHistoryThumbnailGrid(
                                mediaItems = day.selfMedia,
                                slotKey = slotKeySelf,
                                onOpenMedia = onOpenMedia,
                            )
                        }
                        // 对方 — 占半屏宽度, 内部一行2个
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.goldAccent.copy(alpha = 0.68f)),
                                )
                                Text(
                                    text = "$partnerLabel · ${day.partnerMedia.size}",
                                    style = YingShiThemeTokens.typography.statLabel,
                                    color = colors.textSecondary,
                                )
                            }
                            LifeHistoryThumbnailGrid(
                                mediaItems = day.partnerMedia,
                                slotKey = slotKeyPartner,
                                onOpenMedia = onOpenMedia,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Round 8 问题3: 历史页聚合模式 — 单人聚合框
 * 一个框，左右滑动切换照片，下面显示当前照片的时间和地点（各一行）
 */
@Composable
private fun LifeHistoryAggregatedFrame(
    ownerLabel: String,
    mediaItems: List<RemoteMedia>,
    accentColor: Color,
    canEdit: Boolean,
    category: String,
    slotKey: String,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
    onUpload: (String, Boolean) -> Unit = { _, _ -> },
    onDelete: (String, String) -> Unit = { _, _ -> },
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mediaItems.size.coerceAtLeast(1) },
    )
    val currentMedia = mediaItems.getOrNull(
        pagerState.currentPage.coerceAtMost((mediaItems.size - 1).coerceAtLeast(0)),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.sectionBackground.copy(alpha = 0.40f), RoundedCornerShape(radius.md))
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // 用户标签 + 计数
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.68f)),
                )
                Text(
                    text = ownerLabel,
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.goldAccent,
            )
        }
        // 照片区: HorizontalPager
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(radius.md)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val media = mediaItems[page]
                LifeMediaPreview(
                    media = media,
                    onClick = { onOpenMedia(media, slotKey) },
                )
            }
        }
        // 圆点指示器
        if (mediaItems.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(mediaItems.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) colors.titleAccent
                                else colors.dividerSoft.copy(alpha = 0.72f),
                            ),
                    )
                }
            }
        }
        // 时间行
        currentMedia?.let { media ->
            Text(
                text = "🕐 ${formatFullTime(media.displayTimeMillis)}",
                style = YingShiThemeTokens.typography.caption,
                color = colors.textSecondary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 地点行
            val locationLabel = media.locationLabel
            if (!locationLabel.isNullOrBlank()) {
                Text(
                    text = "📍 $locationLabel",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            onLocationClick(
                                LocationUpdateTarget.Media(
                                    mediaId = media.mediaId,
                                    initialLat = media.latitude,
                                    initialLng = media.longitude,
                                    initialLabel = media.locationLabel,
                                )
                            )
                        },
                    ),
                )
            } else {
                Text(
                    text = "📍 添加地点",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.50f),
                    modifier = Modifier.yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            onLocationClick(
                                LocationUpdateTarget.Media(
                                    mediaId = media.mediaId,
                                    initialLat = media.latitude,
                                    initialLng = media.longitude,
                                    initialLabel = media.locationLabel,
                                )
                            )
                        },
                    ),
                )
            }
            // Round 8 第十五轮: 底部操作栏 — 仅自己的框可添加/删除; 对方的框不显示
            if (canEdit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs, alignment = Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                        style = YingShiThemeTokens.typography.statLabel,
                        color = colors.goldAccent,
                        modifier = Modifier.weight(1f),
                    )
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "删除",
                        onClick = {
                            currentMedia?.let { onDelete(category, it.mediaId) }
                        },
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
                    )
                    // Round 8 第十四轮: 双按钮 — 拍照(即时定位) + 相册(读EXIF GPS)
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.CameraAlt,
                        contentDescription = "拍照",
                        onClick = { onUpload(category, true) },
                        enabled = true,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.PhotoLibrary,
                        contentDescription = "从相册选择",
                        onClick = { onUpload(category, false) },
                        enabled = true,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                }
            }
        }
    }
}

/**
 * Round 8 问题3: 历史页缩略图模式 — 紧凑网格，无时间和地点
 */
@Composable
private fun LifeHistoryThumbnailGrid(
    mediaItems: List<RemoteMedia>,
    slotKey: String,
    onOpenMedia: (RemoteMedia, String) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    if (mediaItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(radius.sm))
                .background(colors.sectionBackground.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无",
                style = YingShiThemeTokens.typography.body,
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
        }
    } else {
        // Round 8: 一行2个网格布局, 卡片更紧凑, 双方上下各占一整行宽度
        val rows = mediaItems.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    rowItems.forEach { media ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(radius.sm)),
                        ) {
                            LifeMediaPreview(
                                media = media,
                                onClick = { onOpenMedia(media, slotKey) },
                            )
                        }
                    }
                    // 如果最后一行只有1个, 补一个空格占位保持等宽
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryMediaColumn(
    title: String,
    mediaItems: List<RemoteMedia>,
    modifier: Modifier = Modifier,
    onOpenMedia: (RemoteMedia) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // FR-3: 用户标签带 memoryAccent 圆点
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.memoryAccent.copy(alpha = 0.68f)),
            )
            Text(
                text = title,
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.textSecondary,
            )
        }
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.08f)
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.sectionBackground.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无",
                    style = YingShiThemeTokens.typography.body,
                    color = colors.textSecondary.copy(alpha = 0.72f),
                )
            }
        } else {
            // Round 7: 全宽单列 + 时间/位置信息条
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                mediaItems.forEach { media ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(radius.md))
                                .background(colors.sectionBackground.copy(alpha = 0.78f)),
                        ) {
                            LifeMediaPreview(
                                media = media,
                                onClick = { onOpenMedia(media) },
                            )
                        }
                        LifeMediaInfoStrip(
                            media = media,
                            onLocationClick = onLocationClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryEmptyState(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.textSecondary.copy(alpha = 0.46f),
            )
            Text(
                text = "还没有历史记录",
                style = YingShiThemeTokens.typography.body,
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
            Text(
                text = "上传照片后会自动出现在这里",
                style = YingShiThemeTokens.typography.caption,
                color = colors.textSecondary.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
private fun LifeConsoleBackButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.sm)
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LifeConsoleTodayPager(
    modifier: Modifier = Modifier,
    snapshot: RemoteLifeConsoleToday,
    isBusy: Boolean,
    isAddingBowel: Boolean,
    pendingLocationBowelEventIds: Set<String>,
    initialSlotKey: String?,
    initialMediaId: String?,
    pendingLocationMediaIds: Set<String>,
    onUploadConsumed: () -> Unit,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onUpload: (String, Boolean) -> Unit,
    onDelete: (String, String) -> Unit,
    onAddBowel: () -> Unit,
    onRemoveBowel: () -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

    // Round 8 问题5a: 不再根据 initialSlotKey 强制切换 pager 页 — 用户在哪个页上传就留在哪个页
    // 响应式用户名: 优先使用 CollaboratorDirectoryStore (实时更新), 回退到服务端快照
    val collaboratorDirectory = rememberCollaboratorDirectorySnapshot(fallbackToFakeProfile = false)
    val resolvedSelfLabel = collaboratorDirectory.currentUser?.displayName
        ?: snapshot.currentUser.displayName
    val resolvedPartnerLabel = collaboratorDirectory.partner?.displayName
        ?: snapshot.partner?.displayName
        ?: "对方"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TitleTabs(
            tabs = listOf("人物", "吃饭", "大便"),
            selectedIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth(),
            onSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
        )
        // Round 8: HorizontalPager 占满剩余空间，有有限高度约束
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> LifeConsoleTodayMediaPage(
                    slot1 = snapshot.personSelf,
                    slot2 = snapshot.personPartner,
                    slotKey1 = LifeConsoleSlotKeys.PERSON_SELF,
                    slotKey2 = LifeConsoleSlotKeys.PERSON_PARTNER,
                    selfLabel = resolvedSelfLabel,
                    partnerLabel = resolvedPartnerLabel,
                    accentColor = LifePersonAccent,
                    isBusy = isBusy,
                    initialMediaId = initialMediaId,
                    initialSlotKey = initialSlotKey,
                    pendingLocationMediaIds = pendingLocationMediaIds,
                    onUploadConsumed = onUploadConsumed,
                    onOpenMedia = onOpenMedia,
                    onUpload = onUpload,
                    onDelete = onDelete,
                    onLocationClick = onLocationClick,
                )
                1 -> LifeConsoleTodayMediaPage(
                    slot1 = snapshot.mealSelf,
                    slot2 = snapshot.mealPartner,
                    slotKey1 = LifeConsoleSlotKeys.MEAL_SELF,
                    slotKey2 = LifeConsoleSlotKeys.MEAL_PARTNER,
                    selfLabel = resolvedSelfLabel,
                    partnerLabel = resolvedPartnerLabel,
                    accentColor = LifeMealAccent,
                    isBusy = isBusy,
                    initialMediaId = initialMediaId,
                    initialSlotKey = initialSlotKey,
                    pendingLocationMediaIds = pendingLocationMediaIds,
                    onUploadConsumed = onUploadConsumed,
                    onOpenMedia = onOpenMedia,
                    onUpload = onUpload,
                    onDelete = onDelete,
                    onLocationClick = onLocationClick,
                )
                2 -> LifeConsoleTodayBowelPage(
                    snapshot = snapshot,
                    selfLabel = resolvedSelfLabel,
                    partnerLabel = resolvedPartnerLabel,
                    isBusy = isBusy,
                    isAddingBowel = isAddingBowel,
                    pendingLocationBowelEventIds = pendingLocationBowelEventIds,
                    onAdd = onAddBowel,
                    onRemove = onRemoveBowel,
                    onLocationClick = onLocationClick,
                )
            }
        }
    }
}

@Composable
private fun LifeConsoleTodayMediaPage(
    slot1: RemoteLifeConsoleMediaSlot,
    slot2: RemoteLifeConsoleMediaSlot,
    slotKey1: String,
    slotKey2: String,
    selfLabel: String,
    partnerLabel: String,
    accentColor: Color,
    isBusy: Boolean,
    initialMediaId: String?,
    initialSlotKey: String?,
    pendingLocationMediaIds: Set<String>,
    onUploadConsumed: () -> Unit,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onUpload: (String, Boolean) -> Unit,
    onDelete: (String, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val scrollState = rememberScrollState()

    // Round 8 第六轮: 双方各自独立框上下排列, 每方独立 HorizontalPager, 对方没数据显示空态
    val isMeal = slotKey1.contains("MEAL")
    val slotIcon = if (isMeal) Icons.Filled.Restaurant else Icons.Filled.Person
    val emptyHintSelf = if (isMeal) "记录今天的一餐" else "记录今天的身影"
    val emptyHintPartner = if (isMeal) "对方还没记录今天的一餐" else "对方还没记录今天的身影"

    // Round 8 第八轮: 修复 slot key 匹配 bug.
    // 之前: initialSlotKey = category = "PERSON" (来自服务端 enum 大写名),
    //       slotKey1 = "person_self" (内部常量小写),
    //       比较结果 "PERSON" == "person_self" 永远 false, 跳转永远不生效.
    // 修复: 用规范化匹配 — category 名 + "_self" 后缀, 大小写不敏感.
    val selfSlotMatches = initialSlotKey != null &&
        slotKey1.equals("${initialSlotKey.lowercase()}_self", ignoreCase = true)

    // Round 8 问题5c/5d: 自己的框 — 计算 initialPage, 上传后跳到新媒体
    // Round 8 第十五轮: 默认进入时定位到最新一张 (最后一张), 而不是第一张
    val selfInitialPage = remember(initialMediaId, selfSlotMatches, slot1) {
        if (initialMediaId != null && selfSlotMatches) {
            slot1.mediaItems.indexOfFirst { it.mediaId == initialMediaId }.coerceAtLeast(0)
        } else {
            (slot1.mediaItems.size - 1).coerceAtLeast(0)
        }
    }
    val selfPagerState = rememberPagerState(
        initialPage = selfInitialPage.coerceAtMost((slot1.mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { slot1.mediaItems.size.coerceAtLeast(1) },
    )
    // Round 8 第八轮: 跳转逻辑改为只依赖 initialMediaId (不依赖 slot1),
    // 避免 slot1 频繁变化 (异步定位更新触发 snapshot 刷新) 导致 effect 反复重启.
    // 用 rememberUpdatedState 拿到最新的 slot1.
    val latestSlot1 = rememberUpdatedState(slot1)
    val latestSelfSlotMatches = rememberUpdatedState(selfSlotMatches)
    LaunchedEffect(initialMediaId) {
        if (initialMediaId == null) return@LaunchedEffect
        // 等待 slot1 包含目标 mediaId (服务端 snapshot 可能稍晚才到)
        var attempts = 0
        while (attempts < 20) {
            val s1 = latestSlot1.value
            val matches = latestSelfSlotMatches.value
            if (matches && s1.mediaItems.isNotEmpty()) {
                val targetPage = s1.mediaItems.indexOfFirst { it.mediaId == initialMediaId }
                if (targetPage >= 0) {
                    if (targetPage != selfPagerState.currentPage) {
                        runCatching { selfPagerState.scrollToPage(targetPage) }
                    }
                    onUploadConsumed()
                    return@LaunchedEffect
                }
            }
            attempts++
            delay(100) // 每 100ms 重试, 最多 2 秒
        }
        // 2 秒还没找到, 放弃消费, 避免卡住
        onUploadConsumed()
    }
    // Round 8 第十五轮: 默认进入时定位到最新一张.
    // 处理数据延迟加载的场景: 进入时 slot1 可能还没数据, pager 创建为 0 页;
    // 数据来了后用 effect 跳到最后一张. 用标志位确保只跳一次, 不干扰用户后续滑动.
    val selfHasInitiallyJumped = remember { mutableStateOf(false) }
    LaunchedEffect(slot1.mediaItems.size) {
        if (initialMediaId == null && !selfHasInitiallyJumped.value && slot1.mediaItems.size > 0) {
            val latest = slot1.mediaItems.size - 1
            if (selfPagerState.currentPage != latest) {
                runCatching { selfPagerState.scrollToPage(latest) }
            }
            selfHasInitiallyJumped.value = true
        }
    }
    val selfPagerIndex = selfPagerState.currentPage.coerceAtMost((slot1.mediaItems.size - 1).coerceAtLeast(0))
    val currentSelf = slot1.mediaItems.getOrNull(selfPagerIndex)

    // 对方的框 — 独立 pager
    // Round 8 第十五轮: 对方也默认定位到最新一张
    val partnerInitialPage = remember(slot2) {
        (slot2.mediaItems.size - 1).coerceAtLeast(0)
    }
    val partnerPagerState = rememberPagerState(
        initialPage = partnerInitialPage,
        pageCount = { slot2.mediaItems.size.coerceAtLeast(1) },
    )
    val partnerHasInitiallyJumped = remember { mutableStateOf(false) }
    LaunchedEffect(slot2.mediaItems.size) {
        if (!partnerHasInitiallyJumped.value && slot2.mediaItems.size > 0) {
            val latest = slot2.mediaItems.size - 1
            if (partnerPagerState.currentPage != latest) {
                runCatching { partnerPagerState.scrollToPage(latest) }
            }
            partnerHasInitiallyJumped.value = true
        }
    }
    val partnerPagerIndex = partnerPagerState.currentPage.coerceAtMost((slot2.mediaItems.size - 1).coerceAtLeast(0))
    val currentPartner = slot2.mediaItems.getOrNull(partnerPagerIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 我
        LifeConsoleTodayMediaSlotCard(
            label = selfLabel,
            labelColor = colors.memoryAccent,
            count = slot1.mediaItems.size,
            mediaItems = slot1.mediaItems,
            pagerState = selfPagerState,
            currentMedia = currentSelf,
            accentColor = accentColor,
            slotIcon = slotIcon,
            emptyHint = emptyHintSelf,
            canUpload = slot1.editable && !isBusy,
            slotKey = slotKey1,
            category = slot1.category,
            pendingLocationMediaIds = pendingLocationMediaIds,
            onOpenMedia = onOpenMedia,
            onUpload = onUpload,
            onDelete = onDelete,
            onLocationClick = onLocationClick,
        )
        // 对方
        LifeConsoleTodayMediaSlotCard(
            label = partnerLabel,
            labelColor = colors.goldAccent,
            count = slot2.mediaItems.size,
            mediaItems = slot2.mediaItems,
            pagerState = partnerPagerState,
            currentMedia = currentPartner,
            accentColor = accentColor,
            slotIcon = slotIcon,
            emptyHint = emptyHintPartner,
            canUpload = slot2.editable && !isBusy,
            slotKey = slotKey2,
            category = slot2.category,
            pendingLocationMediaIds = pendingLocationMediaIds,
            onOpenMedia = onOpenMedia,
            onUpload = onUpload,
            onDelete = onDelete,
            onLocationClick = onLocationClick,
        )
    }
}

/**
 * Round 8 第六轮: 单个 media slot 卡片 — 自己或对方通用
 * 有数据: HorizontalPager 左右滑动 + 时间/地点各一行 + 删除/上传操作栏
 * 无数据: 空态卡片 (图标 + 提示 + 可选上传按钮)
 */
@Composable
private fun LifeConsoleTodayMediaSlotCard(
    label: String,
    labelColor: Color,
    count: Int,
    mediaItems: List<RemoteMedia>,
    pagerState: PagerState,
    currentMedia: RemoteMedia?,
    accentColor: Color,
    slotIcon: ImageVector,
    emptyHint: String,
    canUpload: Boolean,
    slotKey: String,
    category: String,
    pendingLocationMediaIds: Set<String>,
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onUpload: (String, Boolean) -> Unit,
    onDelete: (String, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.raisedSurface, RoundedCornerShape(radius.lg))
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // 第1行: 标签 + 数量
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(labelColor.copy(alpha = 0.68f)),
                )
                Text(
                    text = label,
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = "$count 张",
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
        }
        if (mediaItems.isEmpty()) {
            // 空态
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(radius.md))
                    .background(colors.sectionBackground.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = slotIcon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = accentColor.copy(alpha = 0.40f),
                    )
                    Text(
                        text = emptyHint,
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary.copy(alpha = 0.60f),
                    )
                    if (canUpload) {
                        // Round 8 第十四轮: 双按钮 — 拍照(即时定位) + 相册(读EXIF GPS)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LifeConsoleSmallIconButton(
                                icon = Icons.Filled.CameraAlt,
                                contentDescription = "拍照",
                                onClick = { onUpload(category, true) },
                                enabled = true,
                                containerColor = accentColor.copy(alpha = 0.12f),
                                contentColor = accentColor,
                            )
                            LifeConsoleSmallIconButton(
                                icon = Icons.Filled.PhotoLibrary,
                                contentDescription = "从相册选择",
                                onClick = { onUpload(category, false) },
                                enabled = true,
                                containerColor = accentColor.copy(alpha = 0.12f),
                                contentColor = accentColor,
                            )
                        }
                    }
                }
            }
        } else {
            // 有数据: HorizontalPager 展示照片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(radius.md)),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val media = mediaItems[page]
                    LifeMediaPreview(
                        media = media,
                        onClick = { onOpenMedia(media, slotKey) },
                    )
                }
            }
            // 圆点指示器
            if (mediaItems.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(mediaItems.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) colors.titleAccent
                                    else colors.dividerSoft.copy(alpha = 0.72f),
                                ),
                        )
                    }
                }
            }
            // 时间行
            currentMedia?.let { media ->
                Text(
                    text = "🕐 ${formatFullTime(media.displayTimeMillis)}",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
                // 地点行 (可点击)
                val locationLabel = media.locationLabel
                val isLocating = media.mediaId in pendingLocationMediaIds
                when {
                    !locationLabel.isNullOrBlank() -> {
                        Text(
                            text = "📍 $locationLabel",
                            style = YingShiThemeTokens.typography.caption,
                            color = colors.textSecondary.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = spacing.xs)
                                .yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Media(
                                                mediaId = media.mediaId,
                                                initialLat = media.latitude,
                                                initialLng = media.longitude,
                                                initialLabel = media.locationLabel,
                                            )
                                        )
                                    },
                                ),
                        )
                    }
                    isLocating -> {
                        // Round 8 第八轮: 刚上传的照片正在异步获取定位, 显示"正在定位中"
                        Row(
                            modifier = Modifier
                                .padding(horizontal = spacing.xs)
                                .fillMaxWidth(0.6f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.textSecondary.copy(alpha = 0.50f),
                            )
                            Text(
                                text = "正在定位中…",
                                style = YingShiThemeTokens.typography.caption,
                                color = colors.textSecondary.copy(alpha = 0.50f),
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "📍 添加地点",
                            style = YingShiThemeTokens.typography.caption,
                            color = colors.textSecondary.copy(alpha = 0.50f),
                            modifier = Modifier
                                .padding(horizontal = spacing.xs)
                                .yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Media(
                                                mediaId = media.mediaId,
                                                initialLat = media.latitude,
                                                initialLng = media.longitude,
                                                initialLabel = media.locationLabel,
                                            )
                                        )
                                    },
                                ),
                        )
                    }
                }
            }
            // 底部操作栏 (仅自己的框可上传/删除; 对方的框仅显示页码)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.goldAccent,
                    modifier = Modifier.weight(1f),
                )
                if (canUpload) {
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "删除",
                        onClick = {
                            currentMedia?.let { onDelete(category, it.mediaId) }
                        },
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
                    )
                    // Round 8 第十四轮: 双按钮 — 拍照(即时定位) + 相册(读EXIF GPS)
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.CameraAlt,
                        contentDescription = "拍照",
                        onClick = { onUpload(category, true) },
                        enabled = true,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.PhotoLibrary,
                        contentDescription = "从相册选择",
                        onClick = { onUpload(category, false) },
                        enabled = true,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleTodayBowelPage(
    snapshot: RemoteLifeConsoleToday,
    selfLabel: String,
    partnerLabel: String,
    isBusy: Boolean,
    isAddingBowel: Boolean,
    pendingLocationBowelEventIds: Set<String>,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val scrollState = rememberScrollState()

    // Round 8 第十一轮: 改为双方独立框上下排列 (与人物/吃饭页一致).
    // 服务端 bowel.users 已包含双方 (对方 count=0 也在列表里), 直接按 userId 匹配.
    val selfUserId = snapshot.currentUser.userId
    val partnerUserId = snapshot.partner?.userId
    val selfSummary = snapshot.bowel.users.firstOrNull { it.userId == selfUserId }
    val partnerSummary = snapshot.bowel.users.firstOrNull { it.userId == partnerUserId }
    val selfEvents = (selfSummary?.events ?: emptyList()).sortedByDescending { it.occurredAtMillis }
    val partnerEvents = (partnerSummary?.events ?: emptyList()).sortedByDescending { it.occurredAtMillis }

    // Round 8: HorizontalPager 已有有限高度，可以用 verticalScroll
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // 我 — 独立框 (含加减号按钮在卡片内部底部)
        LifeConsoleTodayBowelSlotCard(
            label = selfLabel,
            labelColor = colors.memoryAccent,
            events = selfEvents,
            isLocatingEventIds = pendingLocationBowelEventIds,
            emptyHint = "记录今天的一次",
            isSelf = true,
            isAddingBowel = isAddingBowel,
            isBusy = isBusy,
            onAdd = onAdd,
            onRemove = onRemove,
            onLocationClick = onLocationClick,
        )
        // 对方 — 独立框 (没数据显示空态, 无操作按钮)
        LifeConsoleTodayBowelSlotCard(
            label = partnerLabel,
            labelColor = colors.goldAccent,
            events = partnerEvents,
            isLocatingEventIds = emptySet(), // 对方的事件不归我管, 永远不会在 pending 里
            emptyHint = "对方还没记录今天的",
            isSelf = false,
            isAddingBowel = false,
            isBusy = isBusy,
            onAdd = {},
            onRemove = {},
            onLocationClick = onLocationClick,
        )
    }
}

/**
 * Round 8 第十一轮: 大便独立框卡片 (单用户). 双方各自一个, 上下排列.
 * 没事件时显示空态 (图标 + 文案).
 * Round 8 第十二轮: 自己的卡片内部底部放加减号按钮, 对方卡片无操作.
 */
@Composable
private fun LifeConsoleTodayBowelSlotCard(
    label: String,
    labelColor: Color,
    events: List<RemoteLifeConsoleBowelEvent>,
    isLocatingEventIds: Set<String>,
    emptyHint: String,
    isSelf: Boolean, // true=自己的框 (可操作), false=对方的框 (只读)
    isAddingBowel: Boolean,
    isBusy: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.raisedSurface, RoundedCornerShape(radius.lg))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // 标签行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = YingShiThemeTokens.typography.cardTitle,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (events.isNotEmpty()) {
                Text(
                    text = "${events.size} 次",
                    style = YingShiThemeTokens.typography.statLabel,
                    color = colors.textSecondary,
                )
            }
        }
        if (events.isEmpty()) {
            // 空态: 图标 + 文案
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\uD83D\uDCA9",
                    style = YingShiThemeTokens.typography.body,
                    color = colors.textSecondary.copy(alpha = 0.40f),
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = emptyHint,
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.50f),
                )
            }
        } else {
            // 逐条 event 展示
            events.forEach { event ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.sm))
                        .background(colors.sectionBackground.copy(alpha = 0.50f))
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    // 第1行: 时间 (单独一行)
                    Text(
                        text = "🕐 ${formatFullTime(event.occurredAtMillis)}",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 第2行: 地点 (单独一行, 可点击; 无地点时显示"添加地点"或"正在定位中")
                    val locationLabel = event.locationLabel
                    when {
                        !locationLabel.isNullOrBlank() -> {
                            Text(
                                text = "📍 $locationLabel",
                                style = YingShiThemeTokens.typography.caption,
                                color = colors.textSecondary.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Bowel(
                                                eventId = event.bowelEventId,
                                                initialLat = event.latitude,
                                                initialLng = event.longitude,
                                                initialLabel = event.locationLabel,
                                            )
                                        )
                                    },
                                ),
                            )
                        }
                        event.bowelEventId in isLocatingEventIds -> {
                            // 正在异步获取 GPS 定位
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = colors.textSecondary.copy(alpha = 0.50f),
                                )
                                Text(
                                    text = "正在定位中…",
                                    style = YingShiThemeTokens.typography.caption,
                                    color = colors.textSecondary.copy(alpha = 0.50f),
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "📍 添加地点",
                                style = YingShiThemeTokens.typography.caption,
                                color = colors.textSecondary.copy(alpha = 0.50f),
                                modifier = Modifier.yingShiClickable(
                                    pressedScale = 0.96f,
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onLocationClick(
                                            LocationUpdateTarget.Bowel(
                                                eventId = event.bowelEventId,
                                                initialLat = event.latitude,
                                                initialLng = event.longitude,
                                                initialLabel = event.locationLabel,
                                            )
                                        )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        // Round 8 第十二轮: 自己的卡片内部底部放加减号按钮, 对方卡片无操作
        if (isSelf) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isAddingBowel) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.titleAccent,
                        )
                        Text(
                            text = "正在添加…",
                            style = YingShiThemeTokens.typography.caption,
                            color = colors.titleAccent,
                        )
                    }
                } else {
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Remove,
                        contentDescription = "减一次",
                        onClick = onRemove,
                        enabled = !isBusy,
                        containerColor = colors.sectionBackground.copy(alpha = 0.86f),
                        contentColor = colors.titleAccent,
                    )
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "加一次",
                        onClick = onAdd,
                        enabled = !isBusy,
                        containerColor = colors.primaryAction,
                        contentColor = colors.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleTodayBowelItem(
    event: RemoteLifeConsoleBowelEvent,
    userLabel: String,
    isLocating: Boolean = false,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.raisedSurface, RoundedCornerShape(radius.lg))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // 第1行: emoji + 用户标签 (右对齐)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\uD83D\uDCA9",
                style = YingShiThemeTokens.typography.cardTitle,
            )
            Text(
                text = userLabel,
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.dividerSoft.copy(alpha = 0.34f))
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
        // 第2行: 时间 (单独一行)
        Text(
            text = "🕐 ${formatFullTime(event.occurredAtMillis)}",
            style = YingShiThemeTokens.typography.caption,
            color = colors.textSecondary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 第3行: 地点 (单独一行，可点击；无地点时显示"添加地点"或"正在定位中")
        val locationLabel = event.locationLabel
        when {
            !locationLabel.isNullOrBlank() -> {
                Text(
                    text = "📍 $locationLabel",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            onLocationClick(
                                LocationUpdateTarget.Bowel(
                                    eventId = event.bowelEventId,
                                    initialLat = event.latitude,
                                    initialLng = event.longitude,
                                    initialLabel = event.locationLabel,
                                )
                            )
                        },
                    ),
                )
            }
            isLocating -> {
                // Round 8 第十轮: 刚添加的事件正在异步获取 GPS 定位
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.textSecondary.copy(alpha = 0.50f),
                    )
                    Text(
                        text = "正在定位中…",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary.copy(alpha = 0.50f),
                    )
                }
            }
            else -> {
                Text(
                    text = "📍 添加地点",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.50f),
                    modifier = Modifier.yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            onLocationClick(
                                LocationUpdateTarget.Bowel(
                                    eventId = event.bowelEventId,
                                    initialLat = event.latitude,
                                    initialLng = event.longitude,
                                    initialLabel = event.locationLabel,
                                )
                            )
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun LifeConsoleTodayBowelEmptyState(
    isBusy: Boolean,
    onAdd: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colors.raisedSurface, RoundedCornerShape(radius.lg))
            .padding(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "\uD83D\uDCA9",
            style = YingShiThemeTokens.typography.cardTitle,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = "今天还没有大便记录",
            style = YingShiThemeTokens.typography.caption,
            color = colors.textSecondary.copy(alpha = 0.60f),
        )
        Spacer(Modifier.height(spacing.sm))
        LifeConsoleSmallIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "加一次",
            onClick = onAdd,
            enabled = !isBusy,
            containerColor = colors.primaryAction,
            contentColor = colors.onPrimaryContainer,
        )
    }
}

@Composable
private fun LifeMediaFrame(
    title: String,
    slotKey: String,
    slot: RemoteLifeConsoleMediaSlot,
    modifier: Modifier = Modifier,
    isBusy: Boolean,
    initialMediaId: String?,
    accentColor: Color = Color.Transparent,
    onUploadConsumed: () -> Unit = {},
    onOpenMedia: (RemoteMedia, String) -> Unit,
    onUpload: (String, Boolean) -> Unit,
    onDelete: (String, String) -> Unit,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val targetInitialPage = remember(slotKey, initialMediaId, slot.mediaItems) {
        if (initialMediaId != null) {
            slot.mediaItems.indexOfFirst { it.mediaId == initialMediaId }.coerceAtLeast(0)
        } else {
            (slot.mediaItems.size - 1).coerceAtLeast(0)
        }
    }
    val pagerState = rememberPagerState(
        initialPage = targetInitialPage.coerceAtMost((slot.mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { slot.mediaItems.size.coerceAtLeast(1) },
    )
    LaunchedEffect(slotKey, initialMediaId, slot.mediaItems) {
        if (initialMediaId == null || slot.mediaItems.isEmpty()) return@LaunchedEffect
        val targetPage = slot.mediaItems.indexOfFirst { it.mediaId == initialMediaId }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    // Round 7 阶段 5: 滚动到目标 mediaId 后一次性消费，清空 UiState.lastUploadedMediaId 避免重复触发
    LaunchedEffect(slotKey, initialMediaId, slot.mediaItems, pagerState.currentPage) {
        if (initialMediaId == null || slot.mediaItems.isEmpty()) return@LaunchedEffect
        val current = slot.mediaItems.getOrNull(pagerState.currentPage)
        if (current?.mediaId == initialMediaId) {
            onUploadConsumed()
        }
    }
    val currentMedia = slot.mediaItems.getOrNull(
        pagerState.currentPage.coerceAtMost((slot.mediaItems.size - 1).coerceAtLeast(0)),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.raisedSurface, RoundedCornerShape(radius.lg)),
    ) {
        // 顶部: 左竖条 + 标题 + 计数
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, top = spacing.sm, end = spacing.md, bottom = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.72f)),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = title,
                style = YingShiThemeTokens.typography.cardTitle,
                color = colors.titleAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${slot.mediaItems.size}",
                style = YingShiThemeTokens.typography.statLabel,
                color = colors.goldAccent,
            )
        }

        if (slot.mediaItems.isEmpty()) {
            // Round 7 A3: 空态尺寸统一 — 加 aspectRatio(1f) 与有数据态正方形对齐
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(radius.md))
                    .background(colors.sectionBackground.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (title.contains("吃饭")) Icons.Filled.Restaurant
                        else Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = accentColor.copy(alpha = 0.40f),
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = if (title.contains("吃饭")) "记录今天的一餐"
                        else "记录今天的身影",
                        style = YingShiThemeTokens.typography.caption,
                        color = colors.textSecondary.copy(alpha = 0.60f),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    if (slot.editable) {
                        // Round 8 第十四轮: 双按钮 — 拍照(即时定位) + 相册(读EXIF GPS)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LifeConsoleSmallIconButton(
                                icon = Icons.Filled.CameraAlt,
                                contentDescription = "拍照",
                                onClick = { onUpload(slot.category, true) },
                                enabled = !isBusy,
                                containerColor = accentColor.copy(alpha = 0.12f),
                                contentColor = accentColor,
                            )
                            LifeConsoleSmallIconButton(
                                icon = Icons.Filled.PhotoLibrary,
                                contentDescription = "从相册选择",
                                onClick = { onUpload(slot.category, false) },
                                enabled = !isBusy,
                                containerColor = accentColor.copy(alpha = 0.12f),
                                contentColor = accentColor,
                            )
                        }
                    }
                }
            }
        } else {
            // 有数据: HorizontalPager 直接展示，无包裹阴影 Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(radius.md)),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val media = slot.mediaItems[page]
                        LifeMediaPreview(
                            media = media,
                            onClick = { onOpenMedia(media, slotKey) },
                        )
                    }
                }
            }
            // 圆点指示器
            if (slot.mediaItems.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(slot.mediaItems.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(
                                    if (index == pagerState.currentPage) 8.dp else 6.dp,
                                )
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) colors.titleAccent
                                    else colors.dividerSoft.copy(alpha = 0.72f),
                                ),
                        )
                    }
                }
            }
            // 位置标签 (取最新一条带位置的媒体)
            val mediaLocationLabel = remember(slot.mediaItems) {
                slot.mediaItems
                    .filter { !it.locationLabel.isNullOrBlank() }
                    .maxByOrNull { it.displayTimeMillis }
                    ?.locationLabel
            }
            // Round 7 阶段 7: 点击胶囊调整当前展示媒体的位置
            if (!mediaLocationLabel.isNullOrBlank() && currentMedia != null) {
                Text(
                    text = "📍 $mediaLocationLabel",
                    style = YingShiThemeTokens.typography.caption,
                    color = colors.textSecondary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = spacing.sm, top = spacing.xs)
                        .yingShiClickable(
                            pressedScale = 0.96f,
                            shape = RoundedCornerShape(50),
                            onClick = {
                                onLocationClick(
                                    LocationUpdateTarget.Media(
                                        mediaId = currentMedia.mediaId,
                                        initialLat = currentMedia.latitude,
                                        initialLng = currentMedia.longitude,
                                        initialLabel = currentMedia.locationLabel,
                                    )
                                )
                            },
                        )
                        .clip(RoundedCornerShape(50))
                        .background(colors.dividerSoft.copy(alpha = 0.34f))
                        .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                )
            }
            // 底部操作栏
            if (slot.editable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Round 8 第十四轮: 双按钮 — 拍照(即时定位) + 相册(读EXIF GPS)
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.CameraAlt,
                        contentDescription = "拍照",
                        onClick = { onUpload(slot.category, true) },
                        enabled = !isBusy,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.PhotoLibrary,
                        contentDescription = "从相册选择",
                        onClick = { onUpload(slot.category, false) },
                        enabled = !isBusy,
                        containerColor = accentColor.copy(alpha = 0.12f),
                        contentColor = accentColor,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "删除",
                        onClick = {
                            currentMedia?.mediaId?.let { mediaId ->
                                onDelete(slot.category, mediaId)
                            }
                        },
                        enabled = !isBusy && currentMedia != null,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeMediaPreview(
    media: RemoteMedia,
    onClick: () -> Unit,
) {
    val mediaType = resolveAppMediaType(
        rawType = media.mediaType,
        mimeType = media.mimeType,
        thumbnailUrl = media.thumbnailUrl ?: media.previewUrl,
        mediaUrl = media.mediaUrl,
        videoUrl = media.videoUrl,
        coverUrl = media.coverUrl,
        originalUrl = media.originalUrl,
    )
    AppContentMediaThumbnail(
        mediaSource = media.toAppContentMediaSource(),
        mediaType = mediaType,
        palette = LifeFramePalette,
        modifier = Modifier
            .fillMaxSize()
            .yingShiClickable(
                pressedScale = 0.985f,
                onClick = onClick,
            ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        showVideoPlayOverlay = mediaType == AppMediaType.VIDEO,
    )
}

/**
 * Round 7: 媒体卡片下方的时间+位置信息条
 * 展示完整时间 + 位置胶囊（阶段 7 接入位置选择页点击）。
 */
@Composable
private fun LifeMediaInfoStrip(
    media: RemoteMedia,
    onLocationClick: (LocationUpdateTarget) -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatFullTime(media.displayTimeMillis),
            style = YingShiThemeTokens.typography.caption,
            color = colors.textSecondary.copy(alpha = 0.78f),
        )
        if (!media.locationLabel.isNullOrBlank()) {
            Text(
                text = "\uD83D\uDCCD ${media.locationLabel}",
                style = YingShiThemeTokens.typography.caption,
                color = colors.textSecondary.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = spacing.sm)
                    .yingShiClickable(
                        pressedScale = 0.96f,
                        shape = RoundedCornerShape(50),
                        onClick = {
                            onLocationClick(
                                LocationUpdateTarget.Media(
                                    mediaId = media.mediaId,
                                    initialLat = media.latitude,
                                    initialLng = media.longitude,
                                    initialLabel = media.locationLabel,
                                )
                            )
                        },
                    )
                    .clip(RoundedCornerShape(50))
                    .background(colors.dividerSoft.copy(alpha = 0.34f))
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
            )
        }
    }
}

@Composable
private fun LifeConsolePillAction(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = if (enabled) containerColor else colors.sectionBackground.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm + 2.dp, vertical = spacing.sm - 3.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) contentColor else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LifeConsoleSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color = YingShiThemeTokens.colors.dividerSoft.copy(alpha = 0.62f),
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.93f,
                onClick = onClick,
            ),
        shape = shape,
        color = if (enabled) containerColor else colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, if (enabled) borderColor else colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) contentColor else colors.textSecondary.copy(alpha = 0.58f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

fun RemoteLifeConsoleToday.withoutMedia(mediaId: String): RemoteLifeConsoleToday {
    fun RemoteLifeConsoleMediaSlot.withoutTarget(): RemoteLifeConsoleMediaSlot {
        return copy(mediaItems = mediaItems.filterNot { it.mediaId == mediaId })
    }
    return copy(
        personSelf = personSelf.withoutTarget(),
        personPartner = personPartner.withoutTarget(),
        mealSelf = mealSelf.withoutTarget(),
        mealPartner = mealPartner.withoutTarget(),
    )
}

fun RemoteLifeConsoleToday.withOptimisticBowelDelta(delta: Int): RemoteLifeConsoleToday? {
    val userId = currentUser.userId
    val nowMillis = System.currentTimeMillis()
    val users = bowel.users.toMutableList()
    val userIndex = users.indexOfFirst { it.userId == userId }
    val current = users.getOrNull(userIndex) ?: if (delta > 0) {
        RemoteLifeConsoleBowelUserSummary(
            userId = userId,
            count = 0,
            latestOccurredAtMillis = null,
            eventTimesMillis = emptyList(),
        )
    } else {
        return null
    }
    val nextTimes = if (delta > 0) {
        current.eventTimesMillis + nowMillis
    } else {
        if (current.eventTimesMillis.isEmpty() && current.count <= 0) return null
        current.eventTimesMillis.dropLast(1)
    }
    // Round 8 Bug 修复: 同步更新 events 列表, 否则 UI 看不到乐观新增/删除的 event,
    // 表现为"点击加号闪一下什么都没发生"。
    val nextEvents = if (delta > 0) {
        val newEvent = RemoteLifeConsoleBowelEvent(
            bowelEventId = "optimistic_${nowMillis}",
            userId = userId,
            occurredAtMillis = nowMillis,
            latitude = null,
            longitude = null,
            locationLabel = null,
        )
        listOf(newEvent) + (current.events ?: emptyList())
    } else {
        val existing = current.events ?: emptyList()
        if (existing.isEmpty()) emptyList() else existing.drop(1)
    }
    val nextUser = current.copy(
        count = (current.count + delta).coerceAtLeast(0),
        latestOccurredAtMillis = nextTimes.lastOrNull(),
        eventTimesMillis = nextTimes,
        events = nextEvents,
    )
    if (userIndex >= 0) {
        users[userIndex] = nextUser
    } else {
        users += nextUser
    }
    return copy(bowel = bowel.copy(users = users))
}

fun currentLifeConsoleDate(zoneId: String): String {
    return LocalDate.now(ZoneId.of(zoneId)).toString()
}

fun millisUntilNextLifeConsoleRefresh(zoneId: String): Long {
    val now = ZonedDateTime.now(ZoneId.of(zoneId))
    val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(1)
    return ChronoUnit.MILLIS.between(now, next).coerceAtLeast(1L)
}

private val LifeFramePalette = PhotoThumbnailPalette(
    start = Color(0xFFE8EEF7),
    end = Color(0xFFD6E0EC),
    accent = Color(0xFF526A86),
)

// FR-8: AMOLED accent gradient 调优，暗色模式下提高 alpha
@Composable
private fun lifePersonGradient(): List<Color> {
    val dark = isSystemInDarkTheme()
    val baseAlpha = if (dark) 0.10f else 0.06f
    val midAlpha = if (dark) 0.05f else 0.03f
    return listOf(
        Color(0xFF4A7CBA).copy(alpha = baseAlpha),
        Color(0xFF4A7CBA).copy(alpha = midAlpha),
        Color.Transparent,
    )
}

@Composable
private fun lifeMealGradient(): List<Color> {
    val dark = isSystemInDarkTheme()
    val baseAlpha = if (dark) 0.10f else 0.06f
    val midAlpha = if (dark) 0.05f else 0.03f
    return listOf(
        Color(0xFFC4874A).copy(alpha = baseAlpha),
        Color(0xFFC4874A).copy(alpha = midAlpha),
        Color.Transparent,
    )
}

// 竖条颜色
private val LifePersonAccent = Color(0xFF4A7CBA)
private val LifeMealAccent = Color(0xFFC4874A)

internal object LifeConsoleSlotKeys {
    internal const val PERSON_SELF = "person_self"
    internal const val PERSON_PARTNER = "person_partner"
    internal const val MEAL_SELF = "meal_self"
    internal const val MEAL_PARTNER = "meal_partner"
}
