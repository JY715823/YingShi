package com.example.yingshi.feature.life

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.OriginalLoadState
import com.example.yingshi.feature.photos.RealOriginalLoadRepository
import com.example.yingshi.feature.photos.RealOriginalMediaTarget
import com.example.yingshi.feature.photos.ViewerAtmosphereLayer
import com.example.yingshi.feature.photos.ViewerBottomScrim
import com.example.yingshi.feature.photos.ViewerLayoutTuning
import com.example.yingshi.feature.photos.ViewerNotice
import com.example.yingshi.feature.photos.ViewerNoticeHost
import com.example.yingshi.feature.photos.ViewerTopScrim
import com.example.yingshi.feature.photos.ViewerTimeEditorSheet
import com.example.yingshi.feature.photos.actionLabel
import com.example.yingshi.feature.photos.applyViewerStatusBarVisibility
import com.example.yingshi.feature.photos.formatMediaDisplayTime
import com.example.yingshi.feature.photos.hasMeaningfulViewerOriginal
import com.example.yingshi.feature.photos.resolveAppMediaType
import com.example.yingshi.feature.photos.toAppContentMediaSource
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import com.example.yingshi.ui.theme.YingShiViewerAccent
import com.example.yingshi.ui.theme.YingShiViewerOverlayBorder
import com.example.yingshi.ui.theme.YingShiViewerSurface
import com.example.yingshi.ui.theme.YingShiViewerText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Round 7 阶段 6: 轻量快速查看态。
 *
 * - 复用 PhotoViewerScreen 的交互范式（分页 + 双指缩放 + 视频播放 + 单击切换 chrome + 垃圾桶删除），
 *   去掉评论、加载原图、Hero 转场等耦合 Post/Album 上下文的能力。
 * - 今日页/历史页点击媒体进入此查看态（携带 slotKey，直接用当前 slot 的 mediaItems 分页）。
 * - Widget/通知点击只携带 mediaId，进入后通过 today API 反查所属 slot 并提取同 slot 全部媒体。
 * - Widget/通知来源用 finishAndRemoveTask 直接退出回桌面；app 内来源用 finish 返回上一页。
 */
class LifeMediaQuickViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val payload = LifeMediaQuickViewerPayload.fromIntent(intent)
        val launchedFromWidget = intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_WIDGET, false)
        val launchedFromPush = intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_PUSH, false)
        val isolatedFinish = launchedFromWidget || launchedFromPush

        setContent {
            YingShiTheme {
                LifeMediaQuickViewerScreen(
                    payload = payload,
                    onClose = {
                        if (isolatedFinish) {
                            finishAndRemoveTask()
                        } else {
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        internal const val EXTRA_MEDIA_ID = "life_media_viewer_media_id"
        internal const val EXTRA_MEDIA_TYPE = "life_media_viewer_media_type"
        internal const val EXTRA_MIME_TYPE = "life_media_viewer_mime_type"
        internal const val EXTRA_PREVIEW_URL = "life_media_viewer_preview_url"
        internal const val EXTRA_THUMBNAIL_URL = "life_media_viewer_thumbnail_url"
        internal const val EXTRA_ORIGINAL_URL = "life_media_viewer_original_url"
        internal const val EXTRA_MEDIA_URL = "life_media_viewer_media_url"
        internal const val EXTRA_COVER_URL = "life_media_viewer_cover_url"
        internal const val EXTRA_VIDEO_URL = "life_media_viewer_video_url"
        internal const val EXTRA_LAUNCHED_FROM_WIDGET = "life_media_viewer_from_widget"
        internal const val EXTRA_LAUNCHED_FROM_PUSH = LifePushDispatchActivity.EXTRA_LAUNCHED_FROM_PUSH
        // Round 7 阶段 6: 携带 slotKey 便于直接定位 slot 的全部媒体
        internal const val EXTRA_SLOT_KEY = "life_media_viewer_slot_key"
        // Round 8 第十六轮: 携带 displayTimeMillis + 地点信息, 让首帧就能显示日期/地点
        // (历史页媒体不在 today slot 中时, today API 反查会失败, 但首帧占位仍需正确元数据)
        internal const val EXTRA_DISPLAY_TIME_MILLIS = "life_media_viewer_display_time_millis"
        internal const val EXTRA_LOCATION_LABEL = "life_media_viewer_location_label"
        internal const val EXTRA_LOCATION_LAT = "life_media_viewer_location_lat"
        internal const val EXTRA_LOCATION_LNG = "life_media_viewer_location_lng"

        fun intent(context: Context, media: RemoteMedia): Intent {
            return Intent(context, LifeMediaQuickViewerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_ID, media.mediaId)
                putExtra(EXTRA_MEDIA_TYPE, media.mediaType)
                putExtra(EXTRA_MIME_TYPE, media.mimeType)
                putExtra(EXTRA_PREVIEW_URL, media.previewUrl)
                putExtra(EXTRA_THUMBNAIL_URL, media.thumbnailUrl)
                putExtra(EXTRA_ORIGINAL_URL, media.originalUrl)
                putExtra(EXTRA_MEDIA_URL, media.mediaUrl)
                putExtra(EXTRA_COVER_URL, media.coverUrl)
                putExtra(EXTRA_VIDEO_URL, media.videoUrl)
                putExtra(EXTRA_LAUNCHED_FROM_WIDGET, false)
            }
        }

        /**
         * Round 7 阶段 6: 今日页/历史页点击媒体进入查看态，携带 slotKey 以便直接取同 slot 全部媒体分页。
         * Round 8 第十六轮: 同时携带 displayTimeMillis + 地点信息, 让首帧占位能正确显示元数据。
         */
        fun intent(context: Context, media: RemoteMedia, slotKey: String): Intent {
            return intent(context, media).apply {
                putExtra(EXTRA_SLOT_KEY, slotKey)
                putExtra(EXTRA_DISPLAY_TIME_MILLIS, media.displayTimeMillis)
                media.locationLabel?.let { putExtra(EXTRA_LOCATION_LABEL, it) }
                media.latitude?.let { putExtra(EXTRA_LOCATION_LAT, it) }
                media.longitude?.let { putExtra(EXTRA_LOCATION_LNG, it) }
            }
        }

        fun widgetIntent(context: Context, media: RemoteMedia): Intent {
            return intent(context, media).apply {
                putExtra(EXTRA_LAUNCHED_FROM_WIDGET, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
        }
    }
}

/**
 * 用于跨进程传递的初始 payload。如果带了 slotKey 则直接用 payload 自身字段渲染首帧，
 * 同时异步加载 today API 拉取同 slot 全部媒体；如果未带 slotKey (widget/push) 则
 * 仅靠 mediaId 反查 slot。
 */
internal data class LifeMediaQuickViewerPayload(
    val mediaId: String?,
    val mediaType: String?,
    val mimeType: String?,
    val previewUrl: String?,
    val thumbnailUrl: String?,
    val originalUrl: String?,
    val mediaUrl: String?,
    val coverUrl: String?,
    val videoUrl: String?,
    val slotKey: String?,
    val displayTimeMillis: Long?,
    val locationLabel: String?,
    val locationLat: Double?,
    val locationLng: Double?,
) {
    val isVideo: Boolean
        get() = mediaType.equals("video", ignoreCase = true) ||
            mimeType?.startsWith("video/", ignoreCase = true) == true ||
            !videoUrl.isNullOrBlank()

    companion object {
        fun fromIntent(intent: Intent): LifeMediaQuickViewerPayload {
            // Round 8 第十六轮: displayTimeMillis 用 Long.MIN_VALUE 作为缺失标记, 取出时转 null
            val rawDisplayTime = intent.getLongExtra(LifeMediaQuickViewerActivity.EXTRA_DISPLAY_TIME_MILLIS, Long.MIN_VALUE)
            val rawLat = intent.getDoubleExtra(LifeMediaQuickViewerActivity.EXTRA_LOCATION_LAT, Double.NaN)
            val rawLng = intent.getDoubleExtra(LifeMediaQuickViewerActivity.EXTRA_LOCATION_LNG, Double.NaN)
            return LifeMediaQuickViewerPayload(
                mediaId = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_ID),
                mediaType = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_TYPE),
                mimeType = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MIME_TYPE),
                previewUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_PREVIEW_URL),
                thumbnailUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_THUMBNAIL_URL),
                originalUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_ORIGINAL_URL),
                mediaUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_MEDIA_URL),
                coverUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_COVER_URL),
                videoUrl = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_VIDEO_URL),
                slotKey = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_SLOT_KEY),
                displayTimeMillis = rawDisplayTime.takeIf { it != Long.MIN_VALUE },
                locationLabel = intent.getStringExtra(LifeMediaQuickViewerActivity.EXTRA_LOCATION_LABEL),
                locationLat = rawLat.takeIf { !it.isNaN() },
                locationLng = rawLng.takeIf { !it.isNaN() },
            )
        }
    }
}

@Composable
private fun LifeMediaQuickViewerScreen(
    payload: LifeMediaQuickViewerPayload,
    onClose: () -> Unit,
) {
    // viewerItems 是当前 slot 的全部媒体；初始用 payload 单条占位，加载完成后替换为同 slot 全部媒体
    val viewerItems = remember { androidx.compose.runtime.mutableStateListOf<RemoteMedia>() }
    var initialIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 初始占位：用 payload 构造一条 RemoteMedia，保证首帧立即可见
    LaunchedEffect(payload.mediaId) {
        val placeholder = payload.toRemoteMedia()
        if (placeholder != null) {
            viewerItems.clear()
            viewerItems.add(placeholder)
            initialIndex = 0
        }
    }

    // 异步加载 today API，反查 slot 全部媒体替换 viewerItems
    LaunchedEffect(payload.mediaId, payload.slotKey) {
        try {
            isLoading = true
            loadError = null
            val today = withContext(Dispatchers.IO) {
                when (val result = RepositoryProvider.lifeConsoleRepository.getToday()) {
                    is ApiResult.Success -> result.data
                    else -> null
                }
            } ?: run {
                loadError = "加载失败"
                isLoading = false
                return@LaunchedEffect
            }
            val (_, items) = resolveSlot(today, payload.mediaId, payload.slotKey)
            if (items.isEmpty()) {
                // Round 7 阶段 6: 反查失败（如历史页媒体不在今日 slot 中）— 保留占位首帧
                isLoading = false
                return@LaunchedEffect
            }
            val targetIndex = items.indexOfFirst { it.mediaId == payload.mediaId }
            if (targetIndex < 0) {
                // media 不在 slot 中 — 保留占位首帧
                isLoading = false
                return@LaunchedEffect
            }
            viewerItems.clear()
            viewerItems.addAll(items)
            initialIndex = targetIndex
            isLoading = false
        } catch (e: Exception) {
            loadError = "加载失败: ${e.message ?: ""}"
            isLoading = false
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (viewerItems.isNotEmpty()) {
            LifeMediaQuickViewerPager(
                items = viewerItems,
                initialIndex = initialIndex,
                slotKey = payload.slotKey,
                onClose = onClose,
            )
        }

        if (isLoading && viewerItems.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp).align(Alignment.Center),
                color = Color.White.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (loadError != null && viewerItems.isEmpty()) {
            Text(
                text = loadError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.76f),
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }
    }
}

/**
 * 把 payload 转回 RemoteMedia，作为加载完成前的占位首帧。
 */
private fun LifeMediaQuickViewerPayload.toRemoteMedia(): RemoteMedia? {
    val id = mediaId ?: return null
    return RemoteMedia(
        mediaId = id,
        mediaType = mediaType ?: "image",
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        videoUrl = videoUrl,
        width = null,
        height = null,
        aspectRatio = null,
        thumbnailUrl = thumbnailUrl,
        mediaUrl = mediaUrl,
        coverUrl = coverUrl,
        mimeType = mimeType,
        // Round 8 第十六轮: 用 intent 传入的 displayTimeMillis 而非 0L, 让首帧占位就能显示正确日期
        displayTimeMillis = displayTimeMillis ?: 0L,
        commentCount = 0,
        smallAlbumIds = emptyList(),
        locationLabel = locationLabel,
        latitude = locationLat,
        longitude = locationLng,
    )
}

/**
 * 反查 slot：若 payload.slotKey 存在则直接用该 slot；否则遍历 4 个 slot 查找含 mediaId 的 slot。
 * 返回 (slotKeyResolved, slot.mediaItems)。slotKeyResolved 可能为 null（未找到时）。
 */
private fun resolveSlot(
    today: RemoteLifeConsoleToday,
    mediaId: String?,
    slotKey: String?,
): Pair<String?, List<RemoteMedia>> {
    val slots = listOf(
        LifeConsoleSlotKeys.PERSON_SELF to today.personSelf,
        LifeConsoleSlotKeys.PERSON_PARTNER to today.personPartner,
        LifeConsoleSlotKeys.MEAL_SELF to today.mealSelf,
        LifeConsoleSlotKeys.MEAL_PARTNER to today.mealPartner,
    )
    val resolved = slotKey?.let { k ->
        slots.firstOrNull { it.first == k }
    } ?: mediaId?.let { id ->
        slots.firstOrNull { (_, s) -> s.mediaItems.any { it.mediaId == id } }
    }
    return resolved?.first to (resolved?.second?.mediaItems ?: emptyList())
}

@OptIn(UnstableApi::class)
@Composable
private fun LifeMediaQuickViewerPager(
    items: SnapshotStateList<RemoteMedia>,
    initialIndex: Int,
    slotKey: String?,
    onClose: () -> Unit,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val view = LocalView.current
    val pageCount = items.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, pageCount - 1), pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    // Round 8 第十七轮: 复刻照片流 Viewer 的 zoom/immersive/forceShowOverlays 三态 chrome.
    // - zoomState 提升到 pager 层, 让父级能感知当前页缩放状态以驱动 overlay 可见性.
    // - 非缩放: 单击 toggle isImmersive (隐藏状态栏 + 所有 chrome).
    // - 缩放: 单击 toggle forceShowOverlays (临时呼出 chrome), 与照片流 Viewer 行为一致.
    val zoomState = remember { LifeQuickViewerZoomState() }
    var isImmersive by remember { mutableStateOf(false) }
    var forceShowOverlays by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Round 8 第十六轮: 时间选择器 (复刻照片流 Viewer, 点击日期胶囊弹出)
    var showTimeEditorSheet by remember { mutableStateOf(false) }
    val overlaysVisible = !zoomState.isZoomed || forceShowOverlays
    val appOverlaysVisible = overlaysVisible && !isImmersive
    val chromeVisible = appOverlaysVisible
    fun toggleChrome() {
        if (zoomState.isZoomed) {
            forceShowOverlays = !forceShowOverlays
            if (isImmersive) {
                applyViewerStatusBarVisibility(view, false)
                isImmersive = false
            }
            if (!forceShowOverlays) {
                showTimeEditorSheet = false
            }
            return
        }
        val nextImmersive = !isImmersive
        applyViewerStatusBarVisibility(view, nextImmersive)
        isImmersive = nextImmersive
        if (nextImmersive) {
            showTimeEditorSheet = false
        }
    }

    // Round 8 第十六轮: currentMedia 必须在 showViewerNotice 之前声明, 否则编译错误
    val currentMedia = items.getOrNull(pagerState.currentPage)
    // Round 8 第十六轮: ViewerNotice toast (复刻照片流 Viewer)
    var viewerNotice by remember { mutableStateOf<ViewerNotice?>(null) }
    var viewerNoticeNonce by remember { mutableIntStateOf(0) }
    fun showViewerNotice(message: String, emphasized: Boolean = false) {
        val mediaId = currentMedia?.mediaId.orEmpty()
        viewerNoticeNonce += 1
        viewerNotice = ViewerNotice(
            mediaId = mediaId,
            message = message,
            emphasized = emphasized,
            nonce = viewerNoticeNonce,
        )
    }

    // Round 8 第十六轮: 地点胶囊点击跳地图 (复用 LifeLocationPickerActivity), 返回后调服务端更新
    val locationPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data ?: return@rememberLauncherForActivityResult
        val lat = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LAT, Double.NaN)
        val lng = data.getDoubleExtra(LifeLocationPickerActivity.EXTRA_RESULT_LNG, Double.NaN)
        val label = data.getStringExtra(LifeLocationPickerActivity.EXTRA_RESULT_LABEL)
        val target = currentMedia ?: return@rememberLauncherForActivityResult
        val safeLat = if (lat.isNaN()) null else lat
        val safeLng = if (lng.isNaN()) null else lng
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                RepositoryProvider.lifeConsoleRepository.updateMediaLocation(
                    mediaId = target.mediaId,
                    latitude = safeLat,
                    longitude = safeLng,
                    locationLabel = label,
                )
            }
            when (res) {
                is ApiResult.Success -> showViewerNotice("位置已更新", emphasized = true)
                else -> showViewerNotice("位置更新失败")
            }
            // 本地立即更新 items, 触发 UI 重绘
            val targetId = target.mediaId
            val targetIndex = items.indexOfFirst { it.mediaId == targetId }
            if (targetIndex >= 0) {
                items[targetIndex] = items[targetIndex].copy(
                    locationLabel = label,
                    latitude = safeLat,
                    longitude = safeLng,
                )
            }
        }
    }
    fun openLocationPicker() {
        val media = currentMedia ?: return
        locationPickerLauncher.launch(
            LifeLocationPickerActivity.intent(
                context = context,
                initialLat = media.latitude,
                initialLng = media.longitude,
                initialLabel = media.locationLabel,
                title = "调整照片位置",
            )
        )
    }

    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val networkState by NetworkConnectivityMonitor.state.collectAsState()

    // Round 8 第十五轮: 加载原图 (与照片流同样的 RealOriginalLoadRepository 存储策略)
    val currentOriginalTarget = remember(currentMedia) {
        if (currentMedia == null) null
        else RealOriginalMediaTarget(
            mediaId = currentMedia.mediaId,
            mediaType = resolveAppMediaType(
                rawType = currentMedia.mediaType,
                mimeType = currentMedia.mimeType,
                thumbnailUrl = currentMedia.thumbnailUrl,
                mediaUrl = currentMedia.mediaUrl,
                videoUrl = currentMedia.videoUrl,
                coverUrl = currentMedia.coverUrl,
                originalUrl = currentMedia.originalUrl,
            ),
            mediaSource = currentMedia.toAppContentMediaSource(),
        )
    }
    val currentAppMediaType = remember(currentMedia) {
        if (currentMedia == null) AppMediaType.IMAGE
        else resolveAppMediaType(
            rawType = currentMedia.mediaType,
            mimeType = currentMedia.mimeType,
            thumbnailUrl = currentMedia.thumbnailUrl,
            mediaUrl = currentMedia.mediaUrl,
            videoUrl = currentMedia.videoUrl,
            coverUrl = currentMedia.coverUrl,
            originalUrl = currentMedia.originalUrl,
        )
    }
    val currentOriginalState = if (
        RepositoryProvider.currentMode == RepositoryMode.REAL &&
        currentOriginalTarget != null &&
        currentAppMediaType == AppMediaType.IMAGE
    ) {
        RealOriginalLoadRepository.getState(currentOriginalTarget)
    } else {
        OriginalLoadState.NotLoaded
    }
    val canOpenOriginal = currentAppMediaType == AppMediaType.IMAGE &&
        currentOriginalTarget?.mediaSource.hasMeaningfulViewerOriginal(currentAppMediaType)
    val originalActionLabel = if (
        RepositoryProvider.currentMode == RepositoryMode.REAL &&
        currentOriginalState == OriginalLoadState.Loading &&
        !networkState.isConnected
    ) {
        "等待网络恢复"
    } else {
        currentOriginalState.actionLabel()
    }

    // Round 8 第十五轮: 时间/地点信息 (与照片流 Viewer 同样的 formatMediaDisplayTime)
    val timeLabel = remember(currentMedia) {
        if (currentMedia == null || currentMedia.displayTimeMillis <= 0L) ""
        else formatMediaDisplayTime(currentMedia.displayTimeMillis)
    }
    val locationLabel = currentMedia?.locationLabel

    // Round 8 第十七轮: 切页时重置缩放 + forceShowOverlays, 并恢复 chrome 可见 (复刻照片流 Viewer).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect {
                zoomState.reset()
                forceShowOverlays = false
                if (isImmersive) {
                    applyViewerStatusBarVisibility(view, false)
                    isImmersive = false
                }
            }
    }

    // Round 8 第十七轮: 缩放状态下返回键先退出缩放, 而不是直接关闭查看态 (复刻照片流 Viewer).
    BackHandler(enabled = zoomState.isZoomed) {
        zoomState.reset()
        forceShowOverlays = false
    }

    // Round 8 第十五轮: 修复查看态位置不一致 bug.
    // 根因: rememberPagerState 的 initialPage 只在首次创建时生效, 异步加载完成前
    //   viewerItems 只有 placeholder 一条, pagerState 被锁定为 page 0;
    //   加载完成后 initialIndex 变成用户点击的 targetIndex, 但 pagerState 不会自动跳转,
    //   导致"我明明在最后一张, 查看态却是第一张".
    // 修复: 监听 initialIndex 和 items.size, 加载完成后主动 scrollToPage 到 targetIndex.
    LaunchedEffect(initialIndex, items.size) {
        if (items.isNotEmpty() && initialIndex in 0 until items.size) {
            if (pagerState.currentPage != initialIndex) {
                runCatching { pagerState.scrollToPage(initialIndex) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Round 8 第十六轮: 大气层 (复刻照片流 Viewer)
        ViewerAtmosphereLayer(
            modifier = Modifier
                .matchParentSize()
                .alpha(if (isImmersive) 0.42f else 1f),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Round 8 第十七轮: 缩放时禁用横向滑动, 避免双指缩放手势被 pager 拦截 (复刻照片流 Viewer).
            userScrollEnabled = items.size > 1 && !zoomState.isZoomed,
        ) { page ->
            val media = items.getOrNull(page) ?: return@HorizontalPager
            LifeMediaQuickViewerPage(
                media = media,
                chromeVisible = chromeVisible,
                // Round 8 第十七轮: 当前页共享 pager 级 zoomState, 非当前页传 null 让 page 自建临时实例.
                zoomState = if (page == pagerState.currentPage) zoomState else null,
                // Round 8 第十九轮: 当前页用 currentOriginalState (驱动原图加载),
                // 非当前页用 NotLoaded (避免预加载原图浪费流量).
                originalLoadState = if (page == pagerState.currentPage) currentOriginalState else OriginalLoadState.NotLoaded,
                onToggleChrome = { toggleChrome() },
            )
        }

        // Round 8 第十六轮: 顶部 Scrim (复刻照片流 Viewer)
        if (chromeVisible) {
            ViewerTopScrim(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(124.dp),
            )
        }

        // Round 8 第十六轮: 底部 Scrim (复刻照片流 Viewer)
        if (chromeVisible) {
            ViewerBottomScrim(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(188.dp),
            )
        }

        // Round 8 第十六轮: 顶部 chrome — 返回键 (左) + 垃圾桶 (右), 第二行: 地点胶囊 (右对齐, 点击跳地图)
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            LifeQuickViewerTopBar(
                onClose = onClose,
                onDelete = { showDeleteConfirm = true },
                deleteEnabled = !deleting && currentMedia != null && !slotKey.isNullOrBlank(),
                locationLabel = locationLabel,
                onOpenLocation = { openLocationPicker() },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = ViewerLayoutTuning.topBarStartInset,
                        end = ViewerLayoutTuning.topBarEndInset,
                        top = ViewerLayoutTuning.topBarTopInset,
                    ),
            )
        }

        // Round 8 第十六轮: 底部信息栏 — 两行布局 (日期点击弹时间选择器 + 加载原图), 地点已移到顶部
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            LifeQuickViewerEdgeActions(
                timeLabel = timeLabel,
                canOpenOriginal = canOpenOriginal,
                originalActionLabel = originalActionLabel,
                originalLoadState = currentOriginalState,
                onEditTime = { showTimeEditorSheet = true },
                onOpenOriginal = {
                    val target = currentOriginalTarget ?: return@LifeQuickViewerEdgeActions
                    if (RepositoryProvider.currentMode != RepositoryMode.REAL) return@LifeQuickViewerEdgeActions
                    when {
                        currentAppMediaType != AppMediaType.IMAGE ||
                            !currentOriginalTarget.mediaSource.hasMeaningfulViewerOriginal(currentAppMediaType) -> {
                            showViewerNotice("当前媒体没有独立原图")
                        }
                        currentOriginalState == OriginalLoadState.Loading -> {
                            showViewerNotice(if (networkState.isConnected) "原图加载中" else "网络已断开，恢复后继续加载原图")
                        }
                        currentOriginalState == OriginalLoadState.Loaded -> {
                            showViewerNotice("已加载原图", emphasized = true)
                        }
                        else -> {
                            if (RealOriginalLoadRepository.requestOriginal(context, target, accessToken)) {
                                showViewerNotice("开始加载原图")
                            } else {
                                showViewerNotice("当前媒体没有独立原图")
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            )
        }

        // Round 8 第十六轮: ViewerNoticeHost (复刻照片流 Viewer toast)
        ViewerNoticeHost(
            notice = viewerNotice,
            currentMediaId = currentMedia?.mediaId.orEmpty(),
            onExpired = { nonce ->
                if (viewerNotice?.nonce == nonce) {
                    viewerNotice = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp),
        )

        if (deleting) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp).align(Alignment.Center),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
    }

    if (showDeleteConfirm && currentMedia != null && !slotKey.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("从今日痕迹移除这张媒体？", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = { Text("移除后会从今天的人物或吃饭格子里消失。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        val mediaToDelete = currentMedia
                        val category = slotKey
                        deleting = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                RepositoryProvider.lifeConsoleRepository.deleteMedia(
                                    category = category,
                                    mediaId = mediaToDelete.mediaId,
                                )
                            }
                            deleting = false
                            if (result is ApiResult.Success) {
                                val removedIndex = items.indexOfFirst { it.mediaId == mediaToDelete.mediaId }
                                if (removedIndex >= 0) {
                                    items.removeAt(removedIndex)
                                }
                                if (items.isEmpty()) {
                                    onClose()
                                } else {
                                    val target = removedIndex.coerceAtMost(items.size - 1)
                                    pagerState.scrollToPage(target)
                                }
                            }
                        }
                    },
                ) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    // Round 8 第十六轮: 时间选择器 (复刻照片流 Viewer, 点击日期胶囊弹出).
    // 注意: 服务端 life-console 媒体暂未提供修改时间的 endpoint, 这里先做本地预览 + 通知,
    // 让用户在当前查看态看到新时间; 重新进入会还原. 后续可加服务端 endpoint 持久化.
    if (showTimeEditorSheet && currentMedia != null) {
        ViewerTimeEditorSheet(
            initialTimeMillis = currentMedia.displayTimeMillis.takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            onDismiss = { showTimeEditorSheet = false },
            onConfirm = { nextTimeMillis ->
                showTimeEditorSheet = false
                val targetId = currentMedia.mediaId
                val targetIndex = items.indexOfFirst { it.mediaId == targetId }
                if (targetIndex >= 0) {
                    items[targetIndex] = items[targetIndex].copy(displayTimeMillis = nextTimeMillis)
                    // 按时间倒序重排, 跟随照片流 Viewer 行为
                    val sorted = items.sortedByDescending { it.displayTimeMillis }
                    items.clear()
                    items.addAll(sorted)
                    val newIndex = sorted.indexOfFirst { it.mediaId == targetId }.takeIf { it >= 0 }
                        ?: targetIndex.coerceIn(0, sorted.lastIndex)
                    scope.launch { pagerState.scrollToPage(newIndex) }
                }
                showViewerNotice("时间已修改（本地预览）", emphasized = true)
            },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LifeMediaQuickViewerPage(
    media: RemoteMedia,
    chromeVisible: Boolean,
    // Round 8 第十七轮: 接收 pager 级 zoomState; null 时 (非当前页) 自建临时实例仅作占位.
    zoomState: LifeQuickViewerZoomState?,
    // Round 8 第十九轮: 接收 originalLoadState, 透传给 LifeQuickViewerImage 做双 painter 切换.
    // 非当前页传 NotLoaded, 避免预加载原图.
    originalLoadState: OriginalLoadState,
    onToggleChrome: () -> Unit,
) {
    val isVideo = media.isVideo()
    val effectiveZoomState = zoomState ?: remember(media.mediaId) { LifeQuickViewerZoomState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(media.mediaId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    effectiveZoomState.transform(zoomChange = zoom, panChange = pan, containerSize = size)
                }
            }
            .pointerInput(media.mediaId, isVideo) {
                // Round 8 第十九轮: 加 onDoubleTap 双击放大/恢复, 复刻照片流 Viewer.
                // 图片: 双击切换放大 (2.5x) / 恢复 (1f), 放大时以双击点为中心.
                // 视频: 不处理双击 (视频用 ExoPlayer 默认双击行为).
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = if (!isVideo) { tapPosition ->
                        effectiveZoomState.toggleDoubleTap(tapPosition, size)
                    } else null,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo) {
            LifeQuickViewerVideoPlayer(
                media = media,
                chromeVisible = chromeVisible,
            )
        } else {
            LifeQuickViewerImage(
                media = media,
                zoomState = effectiveZoomState,
                originalLoadState = originalLoadState,
            )
        }
    }
}

@Composable
private fun LifeQuickViewerImage(
    media: RemoteMedia,
    zoomState: LifeQuickViewerZoomState,
    // Round 8 第十九轮: 接收 originalLoadState, 实现 preview/original 双 painter 切换.
    originalLoadState: OriginalLoadState,
) {
    val context = LocalContext.current
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    // Round 8 第十九轮: preview (小图秒开) + original (原图按需加载) 双 URL.
    val previewUrl = remember(media.mediaId, media.thumbnailUrl, media.mediaUrl, media.previewUrl, media.coverUrl) {
        resolveLifeMediaPreviewUrl(media)
    }
    val originalUrl = remember(media.mediaId, media.originalUrl, media.mediaUrl) {
        resolveLifeMediaOriginalUrl(media)
    }
    val shouldRequestOriginal = originalLoadState == OriginalLoadState.Loaded

    val previewRequest = remember(context, previewUrl, accessToken) {
        if (previewUrl.isNullOrBlank()) null
        else ImageRequest.Builder(context)
            .data(previewUrl)
            .apply {
                if (isBackendLifeMediaUrl(previewUrl) && !accessToken.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $accessToken")
                }
            }
            .build()
    }
    // 仅当 Loaded 时才构造 originalRequest, 避免 Loading 期间也加载原图浪费流量.
    val originalRequest = remember(context, originalUrl, shouldRequestOriginal, accessToken) {
        if (!shouldRequestOriginal || originalUrl.isNullOrBlank()) null
        else ImageRequest.Builder(context)
            .data(originalUrl)
            .apply {
                if (isBackendLifeMediaUrl(originalUrl) && !accessToken.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $accessToken")
                }
            }
            .build()
    }
    val previewPainter = rememberAsyncImagePainter(model = previewRequest)
    val originalPainter = rememberAsyncImagePainter(model = originalRequest)
    val previewState = previewPainter.state
    val originalState = originalPainter.state

    // 原图加载成功后才切换显示, 否则继续显示 preview (避免原图加载期间白屏).
    val showOriginal = shouldRequestOriginal && originalState is AsyncImagePainter.State.Success
    val showPreview = previewRequest != null &&
        previewState !is AsyncImagePainter.State.Error &&
        !showOriginal
    val isPreviewLoading = previewState is AsyncImagePainter.State.Loading && !showOriginal && !showPreview

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // graphicsLayer 同时应用到 preview 和 original, 保证缩放/平移一致.
        val imageModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }

        if (showPreview) {
            Image(
                painter = previewPainter,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        }

        if (showOriginal) {
            Image(
                painter = originalPainter,
                contentDescription = null,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        }

        // preview 加载中 (首次进入, Coil 缓存未命中) 才显示转圈.
        // 原图加载中由底部胶囊按钮的 Loading 态表达, 不在这里转圈.
        if (isPreviewLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.92f),
                strokeWidth = 2.dp,
            )
        }

        if (previewRequest == null && originalRequest == null) {
            Text(
                text = "图片加载失败",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.76f),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LifeQuickViewerVideoPlayer(
    media: RemoteMedia,
    chromeVisible: Boolean,
) {
    val context = LocalContext.current
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val resolvedVideoUrl = remember(media.videoUrl, media.mediaUrl) {
        resolveLifeMediaVideoUrl(media)
    }

    var player by remember(media.mediaId) { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember(media.mediaId) { mutableStateOf(false) }
    var hasError by remember(media.mediaId) { mutableStateOf(false) }

    // 创建 ExoPlayer 并注入 Authorization header
    DisposableEffect(media.mediaId, resolvedVideoUrl) {
        if (resolvedVideoUrl.isNullOrBlank()) {
            onDispose { }
        } else {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(
                    buildMap {
                        if (isBackendLifeMediaUrl(resolvedVideoUrl) && !accessToken.isNullOrBlank()) {
                            put("Authorization", "Bearer $accessToken")
                        }
                    },
                )
            val mediaSource: MediaSource = DefaultMediaSourceFactory(httpFactory as DataSource.Factory)
                .createMediaSource(MediaItem.fromUri(resolvedVideoUrl))
            val exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory as DataSource.Factory))
                .build()
                .apply {
                    setMediaSource(mediaSource)
                    prepare()
                    playWhenReady = false
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            hasError = true
                        }
                    })
                }
            player = exoPlayer
            onDispose {
                exoPlayer.release()
                player = null
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val currentPlayer = player
        if (currentPlayer != null && !hasError) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = currentPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 中心播放/暂停按钮（仅在暂停或 chrome 可见时显示）
            if (!isPlaying || chromeVisible) {
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.42f),
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentPlayer) {
                                detectTapGestures {
                                    if (currentPlayer.isPlaying) {
                                        currentPlayer.pause()
                                    } else {
                                        currentPlayer.play()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        } else {
            // 无视频 URL 或加载失败 — 显示封面占位 (用 preview 小图, 避免加载原图)
            val coverUrl = media.coverUrl ?: media.thumbnailUrl ?: media.previewUrl
            if (!coverUrl.isNullOrBlank()) {
                val resolvedCoverUrl = remember(media.mediaId, coverUrl) {
                    resolveLifeBackendUrl(coverUrl)
                }
                val request = remember(context, resolvedCoverUrl, accessToken) {
                    if (resolvedCoverUrl.isNullOrBlank()) null
                    else ImageRequest.Builder(context)
                        .data(resolvedCoverUrl)
                        .apply {
                            if (isBackendLifeMediaUrl(resolvedCoverUrl) && !accessToken.isNullOrBlank()) {
                                addHeader("Authorization", "Bearer $accessToken")
                            }
                        }
                        .build()
                }
                if (request != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = request),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (hasError) {
                Text(
                    text = "视频加载失败",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.76f),
                )
            }
        }
    }
}

// ---- 工具函数 ----

/**
 * 解析媒体图片 URL.
 *
 * Round 8 第十九轮: 拆分为 preview (首帧秒开) + original (按需加载) 两套 URL,
 * 复刻照片流 ViewerImageCanvas 的双 painter 切换策略.
 *
 * - previewUrl: 优先 thumbnailUrl (最小, 几十KB), 回退 mediaUrl/previewUrl/coverUrl.
 *   首帧用这个, Coil 内存缓存命中时 0ms 显示.
 * - originalUrl: 优先 originalUrl (原图, 几MB), 回退 mediaUrl.
 *   仅当用户点"加载原图"且 OriginalLoadState.Loaded 时才加载.
 *
 * 之前直接用 originalUrl 优先导致首帧加载几 MB 原图, 转圈几秒.
 */
private fun resolveLifeMediaPreviewUrl(media: RemoteMedia): String? {
    val candidates = listOf(media.thumbnailUrl, media.mediaUrl, media.previewUrl, media.coverUrl)
    val first = candidates.firstOrNull { url ->
        url != null && url.isNotBlank() && !looksLikeVideoUrl(url)
    }?.trim()
    return resolveLifeBackendUrl(first)
}

private fun resolveLifeMediaOriginalUrl(media: RemoteMedia): String? {
    val candidates = listOf(media.originalUrl, media.mediaUrl)
    val first = candidates.firstOrNull { url ->
        url != null && url.isNotBlank() && !looksLikeVideoUrl(url)
    }?.trim()
    return resolveLifeBackendUrl(first)
}

private fun resolveLifeMediaVideoUrl(media: RemoteMedia): String? {
    val candidates = listOf(media.videoUrl, media.mediaUrl)
    val first = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
    return resolveLifeBackendUrl(first)
}

private fun resolveLifeBackendUrl(rawUrl: String?): String? {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    return runCatching {
        val parsed = Uri.parse(normalized)
        if (!parsed.scheme.isNullOrBlank()) {
            normalized
        } else {
            val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
            val relativePath = normalized.trimStart('/')
            "$baseUrl/$relativePath"
        }
    }.getOrNull()
}

private fun isBackendLifeMediaUrl(url: String): Boolean {
    val normalizedUrl = url.trim().lowercase()
    if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) return false
    val baseUrl = BackendDebugConfig.currentBaseUrl().trim().lowercase()
    return normalizedUrl.startsWith(baseUrl)
}

private class LifeQuickViewerZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    // Round 8 第十七轮: 复刻照片流 ViewerZoomState, isZoomed 用于驱动 overlay 可见性.
    val isZoomed: Boolean
        get() = kotlin.math.abs(scale - 1f) > 0.02f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun transform(
        zoomChange: Float,
        panChange: Offset,
        containerSize: IntSize,
    ) {
        val nextScale = (scale * zoomChange).coerceIn(1f, 5.5f)
        scale = nextScale
        offset = if (nextScale <= 1.02f) {
            Offset.Zero
        } else {
            val maxX = containerSize.width * (nextScale - 1f) / 2f
            val maxY = containerSize.height * (nextScale - 1f) / 2f
            Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
    }

    // Round 8 第十九轮: 双击切换放大/恢复, 复刻照片流 ViewerZoomState.toggleDoubleTap.
    // 已缩放则 reset 回 1f; 未缩放则放大到 DOUBLE_TAP_SCALE (2.5x), 并定位到双击点.
    fun toggleDoubleTap(
        tapPosition: Offset,
        containerSize: IntSize,
    ) {
        if (isZoomed) {
            reset()
            return
        }
        val targetScale = DOUBLE_TAP_SCALE.coerceIn(1f, 5.5f)
        scale = targetScale
        // 以双击点为中心放大: offset = tapPosition - tapPosition * targetScale
        // 这样 tapPosition 这个点在缩放后保持不动, 视觉效果是"放大到双击的位置".
        val maxX = containerSize.width * (targetScale - 1f) / 2f
        val maxY = containerSize.height * (targetScale - 1f) / 2f
        offset = Offset(
            x = (tapPosition.x - containerSize.width / 2f - (tapPosition.x - containerSize.width / 2f) * targetScale)
                .coerceIn(-maxX, maxX),
            y = (tapPosition.y - containerSize.height / 2f - (tapPosition.y - containerSize.height / 2f) * targetScale)
                .coerceIn(-maxY, maxY),
        )
    }

    companion object {
        private const val DOUBLE_TAP_SCALE = 2.5f
    }
}

/**
 * Round 8 第十八轮 u6: 今日痕迹查看态底部信息栏 — 直接复刻照片流 Viewer 单行布局.
 *   [时间居中]  [加载原图靠右]  两端对齐 (无评论按钮)
 * 地点在顶部 TopBar 第二行, 评论/所属小相册不再展示.
 */
@Composable
private fun LifeQuickViewerEdgeActions(
    timeLabel: String,
    canOpenOriginal: Boolean,
    originalActionLabel: String,
    originalLoadState: OriginalLoadState,
    onEditTime: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Round 8 第十八轮 u6: 单行 Box 三端对齐, 复刻 PhotoViewerEdgeActions.
    // 时间居中不挤占加载原图按钮.
    Box(modifier = modifier.fillMaxWidth()) {
        if (timeLabel.isNotBlank()) {
            LifeQuickViewerTimeBadge(
                text = timeLabel,
                onClick = onEditTime,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (canOpenOriginal) {
            LifeQuickViewerCapsule(
                text = originalActionLabel,
                emphasized = originalLoadState == OriginalLoadState.Loaded,
                enabled = originalLoadState != OriginalLoadState.Loading,
                onClick = onOpenOriginal,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * Round 8 第十八轮 u6: 顶部 chrome — 直接复刻照片流 Viewer PhotoViewerTopBar.
 * 第一行: 返回键 (左) + 垃圾桶图标 (右)
 * 第二行: 地点胶囊 (右对齐, 最大宽度 220dp, 省略, 点击跳地图)
 *         无地点时也显示"添加地点"占位, 点击跳地图页选择.
 */
@Composable
private fun LifeQuickViewerTopBar(
    onClose: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    locationLabel: String?,
    onOpenLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            LifeQuickViewerIconCircle(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart),
            )
            LifeQuickViewerIconCircle(
                icon = Icons.Filled.Delete,
                contentDescription = "删除",
                onClick = onDelete,
                enabled = deleteEnabled,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        // Round 8 第十八轮 u6: 第二行地点胶囊永远显示, 无值时为"添加地点"占位.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            LifeQuickViewerLocationCapsule(
                text = locationLabel?.takeIf { it.isNotBlank() } ?: "添加地点",
                onClick = onOpenLocation,
                placeholder = locationLabel.isNullOrBlank(),
            )
        }
    }
}

@Composable
private fun LifeQuickViewerIconCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = CircleShape
    // Round 8 第十八轮 u6: 去掉 drawBehind { drawRect(...) } (矩形背景超出 CircleShape 边界形成难看矩形框),
    // 改用 Surface color 直接设置圆形背景, 复刻 ViewerIconCircle.
    // 图标 tint 用 YingShiViewerText (高对比浅色) 而非 YingShiViewerSurface (深色, 看不清).
    Surface(
        modifier = modifier
            .size(46.dp)
            .then(
                if (enabled) {
                    Modifier.yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = YingShiViewerSurface.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, YingShiViewerOverlayBorder),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = YingShiViewerText.copy(alpha = if (enabled) 0.90f else 0.38f),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun LifeQuickViewerLocationCapsule(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    // Round 8 第十九轮: 复刻照片流 ViewerLocationCapsule 加长加高.
    // widthIn 220→280, vertical padding xs(8dp)→10dp, 图标 14→16dp.
    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick),
        shape = shape,
        color = YingShiViewerSurface.copy(alpha = if (placeholder) 0.40f else 0.56f),
        border = BorderStroke(1.dp, YingShiViewerOverlayBorder.copy(alpha = if (placeholder) 0.50f else 1f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Icon(
                imageVector = if (placeholder) Icons.Filled.Add else Icons.Filled.LocationOn,
                contentDescription = null,
                tint = YingShiViewerAccent.copy(alpha = if (placeholder) 0.88f else 0.94f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (placeholder) FontWeight.Normal else FontWeight.Medium,
                ),
                color = YingShiViewerText.copy(alpha = if (placeholder) 0.72f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LifeQuickViewerTimeBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    // Round 8 第十八轮 u6: 复刻 ViewerTimeBadge, 文字颜色用 YingShiViewerText (高对比浅色).
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
            } else {
                Modifier
            },
        )
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            YingShiViewerSurface.copy(alpha = 0.82f),
                            YingShiViewerSurface.copy(alpha = 0.55f),
                        ),
                    ),
                )
            },
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, YingShiViewerOverlayBorder),
        shadowElevation = 4.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = YingShiViewerText.copy(alpha = 0.96f),
        )
    }
}

@Composable
private fun LifeQuickViewerCapsule(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    surfaceAlpha: Float = if (emphasized) 0.14f else 0.10f,
    contentAlpha: Float = 0.94f,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    // Round 8 第十八轮 u6: 复刻 ViewerCapsule, 文字颜色用 YingShiViewerText (高对比浅色).

    Surface(
        modifier = modifier.then(
            if (onClick != null && enabled) {
                Modifier.yingShiClickable(shape = shape, pressedScale = 0.96f, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = shape,
        color = YingShiViewerSurface.copy(alpha = if (enabled) surfaceAlpha + 0.26f else 0.22f),
        border = BorderStroke(
            width = 1.dp,
            color = YingShiViewerOverlayBorder.copy(alpha = if (enabled) surfaceAlpha + 0.10f else 0.08f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = if (emphasized) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = YingShiViewerText.copy(alpha = if (enabled) contentAlpha else 0.58f),
        )
    }
}
