package com.example.yingshi.app

import android.app.Activity
import androidx.core.app.NotificationManagerCompat
import com.example.yingshi.feature.life.push.SseConnectionManager
import com.example.yingshi.feature.photos.MediaCacheRepository
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import android.widget.Toast
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.cache.OfflineReadOnlyDefaultMessage
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.result.isUnauthorized
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.auth.LoginScreen
import com.example.yingshi.feature.chat.ImportedChatScreen
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeConsoleScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.life.LifeSubRoute
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.feature.ledger.LedgerScreen
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.feature.me.EditProfileRoute
import com.example.yingshi.feature.me.EditProfileScreen
import com.example.yingshi.feature.me.MyScreen
import com.example.yingshi.feature.me.PersonalProfileRoute
import com.example.yingshi.feature.me.PersonalProfileScreen
import com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository
import com.example.yingshi.feature.photos.AlbumPageStateStore
import com.example.yingshi.feature.photos.CacheManagementRoute
import com.example.yingshi.feature.photos.CreatePostRoute
import com.example.yingshi.feature.photos.CreatePostScreen
import com.example.yingshi.feature.photos.CacheManagementScreen
import com.example.yingshi.feature.photos.BackendDiagnosticsRoute
import com.example.yingshi.feature.photos.BackendDiagnosticsScreen
import com.example.yingshi.feature.photos.CollaboratorDirectoryStore
import com.example.yingshi.feature.photos.GearEditRoute
import com.example.yingshi.feature.photos.GearEditScreen
import com.example.yingshi.feature.photos.MediaManagementRoute
import com.example.yingshi.feature.photos.MediaManagementScreen
import com.example.yingshi.feature.photos.NotificationCenterRoute
import com.example.yingshi.feature.photos.NotificationCenterScreen
import com.example.yingshi.feature.photos.NotificationDetailRoute
import com.example.yingshi.feature.photos.NotificationDetailScreen
import com.example.yingshi.feature.photos.NotificationCenterItemType
import com.example.yingshi.feature.photos.NotificationCenterItemUiModel
import com.example.yingshi.feature.photos.offlineReadOnlyMessage
import com.example.yingshi.feature.photos.isLifeChatTarget
import com.example.yingshi.feature.photos.isLifeConsoleTarget
import com.example.yingshi.feature.photos.isLifeLedgerTarget
import com.example.yingshi.feature.photos.PhotoSelectionBottomBar
import com.example.yingshi.feature.photos.PhotoSelectionShellAction
import com.example.yingshi.feature.photos.PhotoViewerRoute
import com.example.yingshi.feature.photos.PhotoViewerScreen
import com.example.yingshi.feature.photos.GlobalPhotoFeedPageStateStore
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.PhotosRootScreen
import com.example.yingshi.feature.photos.PhotosRootSelectionUiState
import com.example.yingshi.feature.photos.PhotosRootTrashParams
import com.example.yingshi.feature.photos.PhotosRootSelectionParams
import com.example.yingshi.feature.photos.PostDetailPlaceholderRoute
import com.example.yingshi.feature.photos.PostDetailScreen
import com.example.yingshi.feature.photos.SettingsRoute
import com.example.yingshi.feature.photos.SettingsScreen
import com.example.yingshi.feature.photos.SystemMediaRoute
import com.example.yingshi.feature.photos.SystemMediaScreen
import com.example.yingshi.feature.photos.SystemMediaImportPreview
import com.example.yingshi.feature.photos.SystemMediaImportPreviewDialog
import com.example.yingshi.feature.photos.SettingsRepository
import com.example.yingshi.feature.photos.TransferCenterRoute
import com.example.yingshi.feature.photos.TransferCenterScreen
import com.example.yingshi.feature.photos.shouldFallbackToReadCache
import com.example.yingshi.feature.photos.SystemMediaViewerRoute
import com.example.yingshi.feature.photos.SystemMediaViewerScreen
import com.example.yingshi.feature.photos.SystemMediaUploadTaskUiModel
import com.example.yingshi.feature.photos.TrashDetailRoute
import com.example.yingshi.feature.photos.TrashDetailScreen
import com.example.yingshi.feature.photos.TrashEntryType
import com.example.yingshi.feature.photos.TrashPageScreen
import com.example.yingshi.feature.photos.toPostDetailPlaceholderRoute
import com.example.yingshi.ui.theme.YingShiThemeTokens
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.components.AppShellScaffold
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.theme.YingShiTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YingShiApp() {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(RootDestination.HOME.name)
    }
    var photosTopDestinationName by rememberSaveable {
        mutableStateOf(PhotosTopDestination.PHOTOS.name)
    }
    var trashSelectedTypeName by rememberSaveable {
        mutableStateOf(TrashEntryType.MEDIA_SYSTEM_DELETED.name)
    }
    var trashShowPendingCleanup by rememberSaveable {
        mutableStateOf(false)
    }
    var trashSelectionModeState by rememberSaveable {
        mutableStateOf(false)
    }
    var trashSelectedEntryIdsState by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var lifeSubRoute by remember {
        mutableStateOf<LifeSubRoute>(LifeSubRoute.Life)
    }
    var photoViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
    }
    var latestPhotoViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
    }
    var photoFeedScrollTrigger by remember { mutableIntStateOf(0) }
    var photoSelectionClearTrigger by remember { mutableIntStateOf(0) }
    var photoSelectionShellState by remember {
        mutableStateOf(PhotosRootSelectionUiState())
    }
    var photoSelectionAction by remember {
        mutableStateOf<PhotoSelectionShellAction?>(null)
    }
    var photoSelectionActionNonce by rememberSaveable {
        mutableIntStateOf(0)
    }
    var systemMediaScrollTrigger by remember { mutableIntStateOf(0) }
    var systemMediaRoute by remember {
        mutableStateOf<SystemMediaRoute?>(null)
    }
    var systemMediaViewerRoute by remember {
        mutableStateOf<SystemMediaViewerRoute?>(null)
    }
    var createPostRoute by remember {
        mutableStateOf<CreatePostRoute?>(null)
    }
    var postDetailRoute by remember {
        mutableStateOf<PostDetailPlaceholderRoute?>(null)
    }
    var postDetailReturnViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
    }
    var pendingPostListUpdatedPostId by remember {
        mutableStateOf<String?>(null)
    }
    var pendingPostListUpdatedAlbumId by remember {
        mutableStateOf<String?>(null)
    }
    var postDetailFeedbackNonce by rememberSaveable {
        mutableIntStateOf(0)
    }
    var gearEditRoute by remember {
        mutableStateOf<GearEditRoute?>(null)
    }
    var mediaManagementRoute by remember {
        mutableStateOf<MediaManagementRoute?>(null)
    }
    var notificationCenterRoute by remember {
        mutableStateOf<NotificationCenterRoute?>(null)
    }
    var notificationCenterSnapshotRoute by remember {
        mutableStateOf<NotificationCenterRoute?>(null)
    }
    var notificationTargetReturnRoute by remember {
        mutableStateOf<NotificationCenterRoute?>(null)
    }
    var transferCenterRoute by remember {
        mutableStateOf<TransferCenterRoute?>(null)
    }
    var settingsRoute by remember {
        mutableStateOf<SettingsRoute?>(null)
    }
    var notificationDetailRoute by remember {
        mutableStateOf<NotificationDetailRoute?>(null)
    }
    var cacheManagementRoute by remember {
        mutableStateOf<CacheManagementRoute?>(null)
    }
    var backendDiagnosticsRoute by remember {
        mutableStateOf<BackendDiagnosticsRoute?>(null)
    }
    var personalProfileRoute by remember {
        mutableStateOf<PersonalProfileRoute?>(null)
    }
    var editProfileRoute by remember {
        mutableStateOf<EditProfileRoute?>(null)
    }
    var trashDetailRoute by remember {
        mutableStateOf<TrashDetailRoute?>(null)
    }
    val operationResults = LocalSystemMediaBridgeRepository.operationResults
    val selectedDestination = RootDestination.valueOf(selectedDestinationName)
    val motion = YingShiThemeTokens.motion
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val authSessionVersion = AuthSessionManager.sessionVersion
    val backendSettings = BackendDebugConfig.settings
    val offlineAccessState = OfflineAccessManager.state
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    val openLifeConsoleNonce = AppNavigationRequests.openLifeConsoleNonce
    val openLedgerRequestNonce = AppNavigationRequests.openLedgerNonce
    val openLedgerAddRequestNonce = AppNavigationRequests.openLedgerAddNonce
    val openPhotoFeedRequestNonce = AppNavigationRequests.openPhotoFeedNonce
    val openSmallAlbumRequestNonce = AppNavigationRequests.openSmallAlbumNonce
    val initialCurrentUserSnapshot = remember(appContext, backendSettings.baseUrl) {
        AuthSessionManager.getCurrentUserSnapshot()
    }
    var currentUser by remember { mutableStateOf(initialCurrentUserSnapshot) }
    var isCheckingAuth by remember {
        mutableStateOf(initialCurrentUserSnapshot == null && AuthSessionManager.isLoggedIn)
    }
    var isLoggingOut by remember { mutableStateOf(false) }
    var authNoticeMessage by remember { mutableStateOf<String?>(null) }
    var profileRefreshMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshingProfile by remember { mutableStateOf(false) }
    var appNotice by remember { mutableStateOf<YingShiNotice?>(null) }
    var appNoticeNonce by rememberSaveable { mutableIntStateOf(0) }
    var lastRootBackPressedAt by rememberSaveable { mutableStateOf(0L) }

    fun showAppNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        appNoticeNonce += 1
        appNotice = YingShiNotice(
            message = message,
            tone = tone,
            nonce = appNoticeNonce,
        )
    }

    fun sessionExpiredReadOnlyMessage(): String {
        return "登录状态已失效，当前显示缓存内容，重新登录后可恢复实时同步。"
    }

    fun sessionExpiredNoticeMessage(): String {
        return "登录状态已失效，请重新登录。"
    }

    fun enableOfflineReadOnly(message: String = OfflineReadOnlyDefaultMessage) {
        val wasReadOnly = OfflineAccessManager.state.isReadOnly
        OfflineAccessManager.enterReadOnly(message)
        if (!wasReadOnly) {
            showAppNotice(message, YingShiNoticeTone.WARNING)
        }
    }

    LaunchedEffect(currentUser) {
        CollaboratorDirectoryStore.update(currentUser)
        currentUser?.let(AuthSessionManager::saveCurrentUserSnapshot)
    }

    LaunchedEffect(currentUser?.userId) {
        SyncVersionTracker.reset()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                SyncVersionTracker.setAppInForeground(true)
                SyncVersionTracker.startPolling()
            } else if (event == Lifecycle.Event.ON_STOP) {
                SyncVersionTracker.setAppInForeground(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            SyncVersionTracker.setAppInForeground(true)
            SyncVersionTracker.startPolling()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SyncVersionTracker.setAppInForeground(false)
        }
    }

    fun resetAccountRoutes() {
        personalProfileRoute = null
        editProfileRoute = null
    }

    fun clearProtectedUiRoutes() {
        lifeSubRoute = LifeSubRoute.Life
        photoViewerRoute = null
        systemMediaRoute = null
        systemMediaViewerRoute = null
        createPostRoute = null
        postDetailRoute = null
        postDetailReturnViewerRoute = null
        gearEditRoute = null
        mediaManagementRoute = null
        notificationCenterRoute = null
        notificationCenterSnapshotRoute = null
        notificationTargetReturnRoute = null
        transferCenterRoute = null
        notificationDetailRoute = null
        settingsRoute = null
        backendDiagnosticsRoute = null
        cacheManagementRoute = null
        resetAccountRoutes()
    }

    fun captureNotificationReturnRoute(): NotificationCenterRoute {
        val snapshot = notificationCenterRoute
            ?: notificationCenterSnapshotRoute
            ?: NotificationCenterRoute(source = "notification-center")
        notificationCenterSnapshotRoute = snapshot
        notificationTargetReturnRoute = snapshot
        return snapshot
    }

    fun restoreNotificationCenter(): Boolean {
        val restoreRoute = notificationTargetReturnRoute ?: return false
        notificationDetailRoute = null
        notificationCenterRoute = restoreRoute
        notificationCenterSnapshotRoute = restoreRoute
        notificationTargetReturnRoute = null
        return true
    }

    fun handleUnauthorized(message: String?) {
        val cachedUser = AuthSessionManager.getCurrentUserSnapshot() ?: currentUser
        AuthSessionManager.clearTokensPreservingReadCache()
        isCheckingAuth = false
        isRefreshingProfile = false
        isLoggingOut = false
        clearProtectedUiRoutes()
        selectedDestinationName = RootDestination.HOME.name
        if (cachedUser != null) {
            currentUser = cachedUser
            authNoticeMessage = message ?: sessionExpiredNoticeMessage()
            profileRefreshMessage = sessionExpiredReadOnlyMessage()
            enableOfflineReadOnly(sessionExpiredReadOnlyMessage())
        } else {
            OfflineAccessManager.clear()
            currentUser = null
            profileRefreshMessage = null
            authNoticeMessage = message ?: sessionExpiredNoticeMessage()
        }
    }

    fun performLogout() {
        scope.launch {
            isLoggingOut = true
            runCatching {
                RepositoryProvider.authRepository.logout()
            }
            AuthSessionManager.clearTokens()

            // R3-FR-2: Comprehensive logout cleanup
            // 1. Clear media disk cache (prevent cross-account data leakage)
            runCatching { MediaCacheRepository.clearAllMediaCaches(context) }
            // 2. Clear all notifications (remove stale push notifications from notification bar)
            runCatching { NotificationManagerCompat.from(context).cancelAll() }
            // 3. Stop SSE connection (prevent idle polling after logout)
            runCatching { SseConnectionManager.stop() }

            currentUser = null
            authNoticeMessage = null
            profileRefreshMessage = null
            isRefreshingProfile = false
            clearProtectedUiRoutes()
            selectedDestinationName = RootDestination.HOME.name
            isLoggingOut = false
        }
    }

    LaunchedEffect(authSessionVersion, backendSettings.baseUrl) {
        val storedCachedUser = AuthSessionManager.getCurrentUserSnapshot()
        val cachedUser = storedCachedUser ?: currentUser
        if (!AuthSessionManager.isLoggedIn) {
            if (storedCachedUser != null) {
                currentUser = storedCachedUser
                authNoticeMessage = sessionExpiredNoticeMessage()
                isCheckingAuth = false
                isRefreshingProfile = false
                profileRefreshMessage = sessionExpiredReadOnlyMessage()
                enableOfflineReadOnly(sessionExpiredReadOnlyMessage())
                return@LaunchedEffect
            }
            OfflineAccessManager.clear()
            currentUser = null
            isCheckingAuth = false
            isRefreshingProfile = false
            profileRefreshMessage = null
            return@LaunchedEffect
        }
        if (currentUser == null && cachedUser != null) {
            currentUser = cachedUser
        }
        val hasVisibleUser = cachedUser != null
        isCheckingAuth = !hasVisibleUser
        when (val result = RepositoryProvider.authRepository.getCurrentUser()) {
            is ApiResult.Success -> {
                currentUser = result.data
                PushTokenRegistrar.registerCurrentTokenIfPossible(context)
                authNoticeMessage = null
                profileRefreshMessage = null
                isCheckingAuth = false
                OfflineAccessManager.clear()
            }
            is ApiResult.Error -> {
                if (result.isUnauthorized()) {
                    handleUnauthorized(result.message)
                } else {
                    if (cachedUser != null && result.shouldFallbackToReadCache()) {
                        currentUser = cachedUser
                        authNoticeMessage = null
                        enableOfflineReadOnly(result.offlineReadOnlyMessage())
                    } else if (!hasVisibleUser) {
                        authNoticeMessage = result.message
                    } else {
                        authNoticeMessage = null
                    }
                    isCheckingAuth = false
                }
            }
            ApiResult.Loading -> Unit
        }
    }

    LaunchedEffect(personalProfileRoute?.source, currentUser?.userId, backendSettings.baseUrl, offlineAccessState.isReadOnly) {
        if (personalProfileRoute == null || currentUser == null) {
            isRefreshingProfile = false
            profileRefreshMessage = null
            return@LaunchedEffect
        }
        if (offlineAccessState.isReadOnly && !AuthSessionManager.isLoggedIn) {
            isRefreshingProfile = false
            profileRefreshMessage = offlineAccessState.message ?: sessionExpiredReadOnlyMessage()
            return@LaunchedEffect
        }
        isRefreshingProfile = true
        profileRefreshMessage = null
        when (val result = RepositoryProvider.authRepository.getCurrentUser()) {
            is ApiResult.Success -> {
                currentUser = result.data
                profileRefreshMessage = null
                OfflineAccessManager.clear()
            }
            is ApiResult.Error -> {
                if (result.isUnauthorized()) {
                    handleUnauthorized(result.message)
                    return@LaunchedEffect
                }
                if (result.shouldFallbackToReadCache() && currentUser != null) {
                    val message = result.offlineReadOnlyMessage("当前显示的是缓存资料，恢复连接后会自动刷新。")
                    profileRefreshMessage = message
                    enableOfflineReadOnly(message)
                } else {
                    profileRefreshMessage = result.message
                }
            }
            ApiResult.Loading -> Unit
        }
        isRefreshingProfile = false
    }

    LaunchedEffect(networkState.changeVersion, networkState.isConnected, currentUser?.userId, personalProfileRoute?.source) {
        if (networkState.changeVersion <= 0 || networkState.isConnected || isLoggingOut) {
            return@LaunchedEffect
        }
        isCheckingAuth = false
        isRefreshingProfile = false
        val cachedUser = AuthSessionManager.getCurrentUserSnapshot() ?: currentUser ?: return@LaunchedEffect
        currentUser = cachedUser
        authNoticeMessage = null
        val message = "网络已断开，当前显示缓存内容，恢复连接后会自动刷新。"
        if (personalProfileRoute != null) {
            profileRefreshMessage = message
        }
        enableOfflineReadOnly(message)
    }

    LaunchedEffect(networkState.changeVersion) {
        if (networkState.changeVersion <= 0 || !networkState.isConnected || isLoggingOut) {
            return@LaunchedEffect
        }
        val cachedUser = AuthSessionManager.getCurrentUserSnapshot() ?: currentUser
        val shouldRecover = offlineAccessState.isReadOnly ||
            !authNoticeMessage.isNullOrBlank() ||
            (currentUser == null && (cachedUser != null || AuthSessionManager.isLoggedIn))
        if (!shouldRecover) {
            return@LaunchedEffect
        }
        if (!AuthSessionManager.isLoggedIn) {
            if (cachedUser != null) {
                currentUser = cachedUser
                authNoticeMessage = sessionExpiredNoticeMessage()
                profileRefreshMessage = sessionExpiredReadOnlyMessage()
                enableOfflineReadOnly(sessionExpiredReadOnlyMessage())
            }
            return@LaunchedEffect
        }
        val latestCachedUser = AuthSessionManager.getCurrentUserSnapshot() ?: currentUser ?: cachedUser
        if (latestCachedUser != null) {
            currentUser = latestCachedUser
            profileRefreshMessage = if (personalProfileRoute != null) {
                "网络已恢复，正在刷新资料..."
            } else {
                profileRefreshMessage
            }
        } else {
            isCheckingAuth = true
        }
        when (val result = RepositoryProvider.authRepository.getCurrentUser()) {
            is ApiResult.Success -> {
                currentUser = result.data
                PushTokenRegistrar.registerCurrentTokenIfPossible(context)
                SyncVersionTracker.requestImmediatePoll()
                authNoticeMessage = null
                profileRefreshMessage = null
                isCheckingAuth = false
                isRefreshingProfile = false
                OfflineAccessManager.clear()
            }
            is ApiResult.Error -> {
                if (result.isUnauthorized()) {
                    handleUnauthorized(result.message)
                } else {
                    if (latestCachedUser != null && result.shouldFallbackToReadCache()) {
                        currentUser = latestCachedUser
                        val message = result.offlineReadOnlyMessage("网络已恢复，但服务器暂时仍不可用，继续显示缓存内容。")
                        authNoticeMessage = null
                        profileRefreshMessage = message
                        enableOfflineReadOnly(message)
                    } else if (latestCachedUser == null) {
                        authNoticeMessage = result.message
                    } else {
                        profileRefreshMessage = result.message
                    }
                    isCheckingAuth = false
                    isRefreshingProfile = false
                }
            }
            ApiResult.Loading -> Unit
        }
    }

    if (isCheckingAuth) {
        AuthCheckingScreen()
        return
    }

    if (currentUser == null) {
        val loginScreenRestoreKey = "$authSessionVersion:${AuthSessionManager.getLastSignedInAccount().orEmpty()}"
        key(loginScreenRestoreKey) {
            LoginScreen(
                sessionMessage = authNoticeMessage,
                onLoginSuccess = { user ->
                    currentUser = user
                    AuthSessionManager.saveCurrentUserSnapshot(user)
                    OfflineAccessManager.clear()
                    authNoticeMessage = null
                    profileRefreshMessage = null
                    isCheckingAuth = false
                    selectedDestinationName = RootDestination.HOME.name
                },
            )
        }
        return
    }

    if (editProfileRoute != null) {
        BackHandler { editProfileRoute = null }
    } else if (personalProfileRoute != null) {
        BackHandler { personalProfileRoute = null }
    }

    LaunchedEffect(openLifeConsoleNonce) {
        if (openLifeConsoleNonce <= 0) return@LaunchedEffect
        selectedDestinationName = RootDestination.LIFE.name
        lifeSubRoute = LifeSubRoute.Console
    }

    LaunchedEffect(openLedgerRequestNonce) {
        if (openLedgerRequestNonce <= 0) return@LaunchedEffect
        selectedDestinationName = RootDestination.LIFE.name
        lifeSubRoute = LifeSubRoute.Ledger
    }

    LaunchedEffect(openLedgerAddRequestNonce) {
        if (openLedgerAddRequestNonce <= 0) return@LaunchedEffect
        selectedDestinationName = RootDestination.LIFE.name
        lifeSubRoute = LifeSubRoute.LedgerAdd
    }

    LaunchedEffect(openPhotoFeedRequestNonce) {
        if (openPhotoFeedRequestNonce <= 0) return@LaunchedEffect
        val mediaId = AppNavigationRequests.photoFeedMediaId
        val autoOpenViewer = AppNavigationRequests.photoFeedAutoOpenViewer
        val autoOpenComment = AppNavigationRequests.photoFeedAutoOpenComment
        if (!mediaId.isNullOrBlank()) {
            GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = mediaId
            GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
            GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
            GlobalPhotoFeedPageStateStore.pendingNotificationMediaIds = setOf(mediaId)
            GlobalPhotoFeedPageStateStore.pendingNotificationNonce += 1
            GlobalPhotoFeedPageStateStore.pendingAutoOpenViewer = autoOpenViewer
            GlobalPhotoFeedPageStateStore.pendingAutoOpenComment = autoOpenComment
            GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = "已定位到通知里的媒体"
            GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = "照片流还在刷新定位"
        }
        photoViewerRoute = null
        systemMediaViewerRoute = null
        notificationCenterRoute = null
        notificationDetailRoute = null
        postDetailRoute = null
        transferCenterRoute = null
        selectedDestinationName = RootDestination.PHOTOS.name
        photosTopDestinationName = PhotosTopDestination.PHOTOS.name
        photoFeedScrollTrigger++
    }

    val isProfileFlowActive = personalProfileRoute != null || editProfileRoute != null

    val markPostListUpdated: (String, String?) -> Unit = { postId, albumId ->
        if (postId.isNotBlank()) {
            AlbumPageStateStore.pendingUpdatedPostId = postId
            pendingPostListUpdatedPostId = postId
        }
        if (!albumId.isNullOrBlank()) {
            AlbumPageStateStore.pendingSelectedAlbumId = albumId
            pendingPostListUpdatedAlbumId = albumId
        }
    }
    val closePostDetail: () -> Unit = {
        pendingPostListUpdatedAlbumId?.let { albumId ->
            AlbumPageStateStore.pendingSelectedAlbumId = albumId
        }
        pendingPostListUpdatedPostId?.let { postId ->
            AlbumPageStateStore.pendingUpdatedPostId = postId
        }
        pendingPostListUpdatedPostId = null
        pendingPostListUpdatedAlbumId = null
        postDetailRoute = null
        postDetailReturnViewerRoute?.let { viewerRoute ->
            postDetailReturnViewerRoute = null
            photoViewerRoute = viewerRoute
        } ?: restoreNotificationCenter()
    }
    val postDetailRouteWithNotice: (PostDetailPlaceholderRoute, String) -> PostDetailPlaceholderRoute = { route, notice ->
        postDetailFeedbackNonce += 1
        route.copy(
            entryNotice = notice,
            feedbackNonce = postDetailFeedbackNonce,
        )
    }
    val openPostDetailAfterAdd: (PostDetailPlaceholderRoute) -> Unit = { route ->
        markPostListUpdated(route.postId, route.albumId)
        photoViewerRoute = null
        systemMediaViewerRoute = null
        systemMediaRoute = null
        createPostRoute = null
        transferCenterRoute = null
        selectedDestinationName = RootDestination.PHOTOS.name
        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
        postDetailRoute = postDetailRouteWithNotice(route, route.entryNotice ?: "已加入小相册")
    }
    LaunchedEffect(openSmallAlbumRequestNonce) {
        if (openSmallAlbumRequestNonce <= 0) return@LaunchedEffect
        val postId = AppNavigationRequests.smallAlbumId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val autoOpenComment = AppNavigationRequests.smallAlbumAutoOpenComment
        photoViewerRoute = null
        systemMediaViewerRoute = null
        systemMediaRoute = null
        createPostRoute = null
        transferCenterRoute = null
        notificationCenterRoute = null
        notificationDetailRoute = null
        selectedDestinationName = RootDestination.PHOTOS.name
        photosTopDestinationName = PhotosTopDestination.ALBUMS.name

        val resolvedRoute = when (val result = RepositoryProvider.postRepository.getPostDetail(postId)) {
            is ApiResult.Success -> result.data.toPostDetailPlaceholderRoute()
            is ApiResult.Error -> {
                showAppNotice("已进入小相册，详情还在同步。", YingShiNoticeTone.WARNING)
                pushSmallAlbumFallbackRoute(postId)
            }
            ApiResult.Loading -> pushSmallAlbumFallbackRoute(postId)
        }
        val route = resolvedRoute.copy(
            entryNotice = "从推送进入",
            autoOpenComment = autoOpenComment,
        )
        markPostListUpdated(route.postId, route.albumId)
        postDetailRoute = postDetailRouteWithNotice(route, "从推送进入")
    }
    val requestPhotoFeedRefresh: (List<String>, Boolean) -> Unit = { resultMediaIds, hasRetryableItems ->
        val validResultMediaIds = resultMediaIds.filter { it.isNotBlank() }.distinct()
        val targetMediaId = validResultMediaIds.firstOrNull()
        if (targetMediaId != null) {
            GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = targetMediaId
            GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
            GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
            GlobalPhotoFeedPageStateStore.pendingNewImportedMediaIds = validResultMediaIds.toSet()
            GlobalPhotoFeedPageStateStore.pendingNewImportedNonce += 1
            GlobalPhotoFeedPageStateStore.pendingImportHasRetryableItems = hasRetryableItems
            val extraCount = (validResultMediaIds.size - 1).coerceAtLeast(0)
            GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = if (extraCount > 0) {
                "已定位到刚导入媒体，另有 $extraCount 项已导入"
            } else {
                "已定位到刚导入媒体"
            }
            GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = if (extraCount > 0) {
                "已导入 ${validResultMediaIds.size} 项媒体，照片流还在刷新定位"
            } else {
                "已导入媒体，照片流还在刷新定位"
            }
            if (hasRetryableItems) {
                GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage =
                    "已定位到刚导入媒体，未完成项可在传输中心查看并重试。"
                GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage =
                    "成功项已导入，照片流还在刷新定位；未完成项可在传输中心查看并重试。"
            }
        }
        photoViewerRoute = null
        systemMediaViewerRoute = null
        systemMediaRoute = null
        createPostRoute = null
        postDetailRoute = null
        transferCenterRoute = null
        selectedDestinationName = RootDestination.PHOTOS.name
        photosTopDestinationName = PhotosTopDestination.PHOTOS.name
        photoFeedScrollTrigger++
    }
    val openPhotoFeedMediaFromTransferCenter: (String, List<String>, Boolean) -> Unit = { selectedMediaId, resultMediaIds, hasRetryableItems ->
        val targetMediaId = selectedMediaId.takeIf { it.isNotBlank() }
        if (targetMediaId != null) {
            val validResultMediaIds = (listOf(targetMediaId) + resultMediaIds)
                .filter { it.isNotBlank() }
                .distinct()
            GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = targetMediaId
            GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
            GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
            GlobalPhotoFeedPageStateStore.pendingNewImportedMediaIds = validResultMediaIds.toSet()
            GlobalPhotoFeedPageStateStore.pendingNewImportedNonce += 1
            GlobalPhotoFeedPageStateStore.pendingImportHasRetryableItems = hasRetryableItems
            GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = "已定位到所选媒体"
            GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = "媒体已导入，照片流还在刷新定位"
            photoViewerRoute = null
            systemMediaViewerRoute = null
            systemMediaRoute = null
            createPostRoute = null
            postDetailRoute = null
            transferCenterRoute = null
            selectedDestinationName = RootDestination.PHOTOS.name
            photosTopDestinationName = PhotosTopDestination.PHOTOS.name
            photoFeedScrollTrigger++
        }
    }
    val requestPhotoFeedRestoreLocate: (List<String>) -> Unit = { resultMediaIds ->
        val validResultMediaIds = resultMediaIds.filter { it.isNotBlank() }.distinct()
        val targetMediaId = validResultMediaIds.firstOrNull()
        if (targetMediaId != null) {
            GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = targetMediaId
            GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
            GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
            GlobalPhotoFeedPageStateStore.pendingNewImportedMediaIds = emptySet()
            GlobalPhotoFeedPageStateStore.pendingRestoredMediaIds = validResultMediaIds.toSet()
            GlobalPhotoFeedPageStateStore.pendingRestoredNonce += 1
            GlobalPhotoFeedPageStateStore.pendingImportHasRetryableItems = false
            val extraCount = (validResultMediaIds.size - 1).coerceAtLeast(0)
            GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = if (extraCount > 0) {
                "已定位到恢复媒体，另有 $extraCount 项已恢复"
            } else {
                "已定位到恢复媒体"
            }
            GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = if (extraCount > 0) {
                "已恢复 ${validResultMediaIds.size} 项媒体，照片流还在刷新定位"
            } else {
                "已恢复媒体，照片流还在刷新定位"
            }
        }
        photoViewerRoute = null
        systemMediaViewerRoute = null
        systemMediaRoute = null
        createPostRoute = null
        postDetailRoute = null
        transferCenterRoute = null
        selectedDestinationName = RootDestination.PHOTOS.name
        photosTopDestinationName = PhotosTopDestination.PHOTOS.name
        photoFeedScrollTrigger++
    }
    LaunchedEffect(operationResults.size) {
        if (operationResults.isEmpty()) return@LaunchedEffect
        val pendingEvents = operationResults.toList()
        pendingEvents.forEach { event ->
            transferToastMessage(event).takeIf { it.isNotBlank() }?.let { message ->
                showAppNotice(
                    message,
                    if (event.failureCount > 0 || event.cancelledCount > 0) {
                        YingShiNoticeTone.WARNING
                    } else {
                        YingShiNoticeTone.SUCCESS
                    },
                )
            }
            if (event.operationType == LocalSystemMediaBridgeRepository.OperationType.CREATE_POST &&
                event.postRoute != null &&
                event.successCount > 0
            ) {
                openPostDetailAfterAdd(event.postRoute.copy(entryNotice = "小相册创建完成"))
            } else if (
                event.operationType == LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST &&
                event.postRoute != null &&
                event.successCount > 0
            ) {
                openPostDetailAfterAdd(event.postRoute)
            } else if (
                event.operationType == LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP &&
                event.shouldAutoOpenResult &&
                event.successCount > 0 &&
                event.resultMediaIds.isNotEmpty()
            ) {
                val validResultMediaIds = event.resultMediaIds.filter { it.isNotBlank() }.distinct()
                val targetMediaId = validResultMediaIds.firstOrNull()
                if (targetMediaId != null) {
                    GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = targetMediaId
                    GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
                    GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
                    GlobalPhotoFeedPageStateStore.pendingNewImportedMediaIds = validResultMediaIds.toSet()
                    GlobalPhotoFeedPageStateStore.pendingNewImportedNonce += 1
                    GlobalPhotoFeedPageStateStore.pendingImportHasRetryableItems =
                        event.failureCount > 0 || event.cancelledCount > 0
                    GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = "已定位到刚导入媒体"
                    GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = "已导入媒体，照片流还在刷新定位"
                    if (
                        selectedDestinationName == RootDestination.PHOTOS.name &&
                        photosTopDestinationName == PhotosTopDestination.PHOTOS.name
                    ) {
                        photoFeedScrollTrigger++
                    }
                }
            }
            if (event.successCount > 0) {
                if (event.operationType == LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP) {
                    SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
                    SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                } else if (
                    event.operationType == LocalSystemMediaBridgeRepository.OperationType.CREATE_POST ||
                    event.operationType == LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST
                ) {
                    SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                    SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                }
            }
            LocalSystemMediaBridgeRepository.dismissOperationResult(event.eventId)
        }
    }

    if (photoViewerRoute != null) {
        BackHandler {
            photoViewerRoute = null
        }
    }
    if (lifeSubRoute != LifeSubRoute.Life) {
        BackHandler {
            when (lifeSubRoute) {
                is LifeSubRoute.LedgerAdd -> lifeSubRoute = LifeSubRoute.Ledger
                else -> lifeSubRoute = LifeSubRoute.Life
            }
        }
    }
    if (systemMediaViewerRoute != null) {
        BackHandler {
            systemMediaViewerRoute = null
        }
    }
    if (systemMediaRoute != null && systemMediaViewerRoute == null && createPostRoute == null) {
        BackHandler {
            systemMediaRoute = null
        }
    }
    if (createPostRoute != null) {
        BackHandler {
            createPostRoute = null
        }
    }
    if (trashDetailRoute != null) {
        BackHandler {
            trashDetailRoute = null
        }
    }
    if (postDetailRoute != null) {
        BackHandler(enabled = trashDetailRoute == null && gearEditRoute == null && mediaManagementRoute == null) {
            closePostDetail()
        }
    }
    if (gearEditRoute != null) {
        BackHandler(enabled = mediaManagementRoute == null) {
            gearEditRoute = null
        }
    }
    if (mediaManagementRoute != null) {
        BackHandler {
            mediaManagementRoute = null
        }
    }
    if (notificationDetailRoute != null) {
        BackHandler {
            notificationDetailRoute = null
        }
    }
    if (transferCenterRoute != null) {
        BackHandler {
            transferCenterRoute = null
            restoreNotificationCenter()
        }
    }
    if (
        notificationCenterRoute != null &&
        notificationDetailRoute == null &&
        settingsRoute == null &&
        cacheManagementRoute == null
    ) {
        BackHandler {
            notificationCenterRoute = null
        }
    }
    if (settingsRoute != null && cacheManagementRoute == null) {
        BackHandler {
            settingsRoute = null
        }
    }
    if (backendDiagnosticsRoute != null) {
        BackHandler {
            backendDiagnosticsRoute = null
        }
    }
    if (cacheManagementRoute != null) {
        BackHandler {
            cacheManagementRoute = null
            restoreNotificationCenter()
        }
    }
    if (
        notificationTargetReturnRoute != null &&
        notificationCenterRoute == null &&
        notificationDetailRoute == null &&
        transferCenterRoute == null &&
        postDetailRoute == null &&
        trashDetailRoute == null &&
        cacheManagementRoute == null &&
        settingsRoute == null &&
        backendDiagnosticsRoute == null &&
        photoViewerRoute == null &&
        systemMediaRoute == null &&
        systemMediaViewerRoute == null &&
        createPostRoute == null &&
        lifeSubRoute == LifeSubRoute.Life &&
        !isProfileFlowActive
    ) {
        BackHandler {
            restoreNotificationCenter()
        }
    }
    val activity = context.findActivity()
    val isRootDestination = selectedDestination in setOf(
        RootDestination.HOME,
        RootDestination.PHOTOS,
        RootDestination.LIFE,
        RootDestination.ME,
    )
    val rootExitEligible =
        isRootDestination &&
            notificationCenterRoute == null &&
            notificationDetailRoute == null &&
            transferCenterRoute == null &&
            settingsRoute == null &&
            backendDiagnosticsRoute == null &&
            cacheManagementRoute == null &&
            photoViewerRoute == null &&
            systemMediaRoute == null &&
            systemMediaViewerRoute == null &&
            createPostRoute == null &&
            trashDetailRoute == null &&
            postDetailRoute == null &&
            gearEditRoute == null &&
            mediaManagementRoute == null &&
            lifeSubRoute == LifeSubRoute.Life &&
            !isProfileFlowActive &&
            !photoSelectionShellState.isActive
    if (rootExitEligible) {
        BackHandler {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRootBackPressedAt <= 2000L) {
                activity?.finish()
            } else {
                lastRootBackPressedAt = now
                Toast.makeText(context, "再按一次退出 App", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val photosOverlayActive = photoViewerRoute != null ||
        systemMediaRoute != null ||
        systemMediaViewerRoute != null ||
        createPostRoute != null ||
        trashDetailRoute != null ||
        postDetailRoute != null ||
        gearEditRoute != null ||
        mediaManagementRoute != null
    val showRootBottomBar = photoViewerRoute == null &&
        systemMediaRoute == null &&
        createPostRoute == null &&
        trashDetailRoute == null &&
        postDetailRoute == null &&
        gearEditRoute == null &&
        mediaManagementRoute == null &&
        notificationCenterRoute == null &&
        transferCenterRoute == null &&
        notificationDetailRoute == null &&
        settingsRoute == null &&
        backendDiagnosticsRoute == null &&
        cacheManagementRoute == null &&
        !isProfileFlowActive &&
        (selectedDestination != RootDestination.LIFE || lifeSubRoute == LifeSubRoute.Life)
    val showPhotoSelectionBottomBar = showRootBottomBar &&
        selectedDestination == RootDestination.PHOTOS &&
        photosTopDestinationName == PhotosTopDestination.PHOTOS.name &&
        photoSelectionShellState.isActive

    if (showPhotoSelectionBottomBar) {
        BackHandler {
            photoSelectionClearTrigger += 1
        }
    }

    fun dispatchPhotoSelectionAction(action: PhotoSelectionShellAction) {
        photoSelectionAction = action
        photoSelectionActionNonce += 1
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppShellScaffold(
            selectedDestination = selectedDestination,
            onDestinationSelected = { selectedDestinationName = it.name },
            showBottomBar = showRootBottomBar,
            bottomBarOverride = if (showPhotoSelectionBottomBar) {
                {
                    val motionEnabled = rememberYingShiMotionEnabled()
                    val animDuration = if (motionEnabled) 250 else 0
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(durationMillis = animDuration),
                        ) + fadeIn(animationSpec = tween(durationMillis = animDuration)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(durationMillis = 200),
                        ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                    ) {
                        PhotoSelectionBottomBar(
                            selectedCount = photoSelectionShellState.selectedCount,
                            writeEnabled = photoSelectionShellState.writeEnabled,
                            deleteInFlight = photoSelectionShellState.isDeleting,
                            onAction = ::dispatchPhotoSelectionAction,
                        )
                    }
                }
            } else {
                null
            },
        ) {
            val openNotificationTarget: (NotificationCenterItemUiModel) -> Unit = { item ->
                captureNotificationReturnRoute()
                when {
                    // 1. 缓存管理入口
                    item.id == "notice-cache-1" -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        cacheManagementRoute = CacheManagementRoute(source = "notification-center")
                    }

                    // 2. 生活模块（记账 / 今日痕迹 / 聊天导入）
                    item.isLifeLedgerTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        lifeSubRoute = LifeSubRoute.Ledger
                    }

                    item.isLifeConsoleTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        lifeSubRoute = LifeSubRoute.Console
                    }

                    item.isLifeChatTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        lifeSubRoute = LifeSubRoute.Chat
                    }

                    // 3. 回收站 — 有具体条目跳详情，否则跳回收站列表
                    !item.trashItemId.isNullOrBlank() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.TRASH.name
                        trashDetailRoute = TrashDetailRoute(
                            entryId = item.trashItemId,
                            entryType = item.targetType.toTrashEntryTypeOrNull(),
                            sourcePostId = item.postId,
                            sourceMediaId = item.mediaId,
                        )
                    }

                    item.type == NotificationCenterItemType.DELETE_RESTORE -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.TRASH.name
                    }

                    // 4. 导入完成 — 跳转照片流定位到导入的媒体
                    item.targetType.equals("UPLOAD", ignoreCase = true) -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        openPhotoFeedMediaFromTransferCenter(
                            item.mediaId.orEmpty(),
                            item.mediaItems.map { it.mediaId },
                            false,
                        )
                    }

                    // 5. 小相册（新建 / 编辑 / 评论）- 进入相册详情，高亮对应媒体
                    !item.postId.isNullOrBlank() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                        postDetailRoute = item.toNotificationPostRoute()
                    }

                    // 6. 内容更新（无具体相册时回退到相册列表）
                    item.type == NotificationCenterItemType.CONTENT_UPDATE -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                    }

                    // 7. 单媒体（无相册归属）- 在照片流中定位，评论类型自动打开查看器
                    item.targetRoute?.startsWith("photos:media:", ignoreCase = true) == true ||
                        (!item.mediaId.isNullOrBlank() && item.mediaItems.isNotEmpty()) ||
                        (item.type == NotificationCenterItemType.COMMENT && !item.mediaId.isNullOrBlank()) -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        if (item.type == NotificationCenterItemType.COMMENT) {
                            GlobalPhotoFeedPageStateStore.pendingAutoOpenViewer = true
                            GlobalPhotoFeedPageStateStore.pendingAutoOpenComment = true
                        }
                        openPhotoFeedMediaFromTransferCenter(
                            item.mediaId.orEmpty(),
                            item.mediaItems.map { it.mediaId },
                            false,
                        )
                    }

                    // 8. 兜底 — 打开通知详情页
                    else -> {
                        notificationDetailRoute = NotificationDetailRoute(
                            notificationId = item.id,
                            source = "notification-center",
                        )
                    }
                }
            }
            when {
            backendDiagnosticsRoute != null -> {
                backendDiagnosticsRoute?.let { route ->
                    BackendDiagnosticsScreen(
                        route = route,
                        onBack = { backendDiagnosticsRoute = null },
                    )
                }
            }

            cacheManagementRoute != null -> {
                cacheManagementRoute?.let { route ->
                    CacheManagementScreen(
                        route = route,
                        onBack = { cacheManagementRoute = null },
                    )
                }
            }

            settingsRoute != null -> {
                settingsRoute?.let { route ->
                    SettingsScreen(
                        route = route,
                        onBack = { settingsRoute = null },
                        onOpenBackendDiagnostics = { backendDiagnosticsRoute = it },
                        onOpenCacheManagement = { cacheManagementRoute = it },
                        onLogout = { performLogout() },
                    )
                }
            }

            notificationDetailRoute != null -> {
                notificationDetailRoute?.let { route ->
                    NotificationDetailScreen(
                        route = route,
                        onBack = { notificationDetailRoute = null },
                        onOpenTarget = openNotificationTarget,
                    )
                }
            }

            notificationCenterRoute != null -> {
                notificationCenterRoute?.let { route ->
                    NotificationCenterScreen(
                        route = route,
                        onBack = { notificationCenterRoute = null },
                        onOpenNotificationDetail = { notificationDetailRoute = it },
                        onOpenNotificationTarget = openNotificationTarget,
                        onRouteSnapshotChange = { notificationCenterSnapshotRoute = it },
                    )
                }
            }

            transferCenterRoute != null -> {
                transferCenterRoute?.let { route ->
                    TransferCenterScreen(
                        route = route,
                        onBack = { transferCenterRoute = null },
                        onOpenTaskMedia = { task ->
                            val selectedMediaId = task.resultMediaId?.takeIf { it.isNotBlank() }
                            when {
                                selectedMediaId != null -> {
                                    val operationMediaIds = task.successfulResultMediaIdsInOperation()
                                    openPhotoFeedMediaFromTransferCenter(
                                        selectedMediaId,
                                        operationMediaIds,
                                        task.operationFailureCount > 0 || task.operationCancelledCount > 0,
                                    )
                                }

                                task.resultPostRoute != null -> {
                                    transferCenterRoute = null
                                    openPostDetailAfterAdd(task.resultPostRoute)
                                }

                                else -> {
                                    showAppNotice(
                                        "这个任务还没有可查看的结果。",
                                        YingShiNoticeTone.WARNING,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            systemMediaRoute != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    SystemMediaScreen(
                        modifier = Modifier.fillMaxSize(),
                        onBack = {
                            systemMediaViewerRoute = null
                            systemMediaRoute = null
                        },
                        onOpenViewer = { systemMediaViewerRoute = it },
                        onOpenPostDetail = { route ->
                            systemMediaViewerRoute = null
                            systemMediaRoute = null
                            postDetailRoute = route
                        },
                        onOpenCreatePost = { createPostRoute = it },
                        scrollTrigger = systemMediaScrollTrigger,
                        inlineVideoAutoPlayEnabled = systemMediaViewerRoute == null,
                    )

                    systemMediaViewerRoute?.let { route ->
                        SystemMediaViewerScreen(
                            route = route,
                            onBack = {
                                systemMediaViewerRoute = null
                                systemMediaScrollTrigger++
                            },
                            onOpenCreatePost = { createPostRoute = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    createPostRoute?.let { route ->
                        CreatePostScreen(
                            route = route,
                            onBack = { createPostRoute = null },
                            onCreated = { createdRoute ->
                                markPostListUpdated(createdRoute.postId, createdRoute.albumId)
                                createPostRoute = null
                                systemMediaViewerRoute = null
                                systemMediaRoute = null
                                selectedDestinationName = RootDestination.PHOTOS.name
                                photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                                photoSelectionClearTrigger++
                                postDetailRoute = postDetailRouteWithNotice(createdRoute, "已发布")
                            },
                            onSubmittedToBackground = { createPostRoute = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            trashDetailRoute != null -> {
                trashDetailRoute?.let { route ->
                    TrashDetailScreen(
                        route = route,
                        onBack = { trashDetailRoute = null },
                        onEntryRemoved = { trashDetailRoute = null },
                        onEntryRestored = { mediaIds ->
                            SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
                            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                            SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
                            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                            SyncVersionTracker.markLocalMutation(SyncModule.SYSTEM_MEDIA)
                            trashDetailRoute = null
                            // 恢复后留在回收站列表，不跳转到照片页
                            // 小相册恢复只恢复引用关系，不会删除里面的照片
                        },
                        onShowNotice = ::showAppNotice,
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
	                    targetState = selectedDestination,
	                    transitionSpec = {
	                        fadeIn(animationSpec = tween(motion.routeMillis, easing = motion.easing)) togetherWith
	                            fadeOut(animationSpec = tween(motion.stateMillis, easing = motion.easing))
	                    },
                    label = "rootDestinationTransition",
                ) { destination ->
                when (destination) {
                    RootDestination.HOME -> HomeScreen(
                        onOpenPhotos = {
                            selectedDestinationName = RootDestination.PHOTOS.name
                            photosTopDestinationName = PhotosTopDestination.PHOTOS.name
                        },
                        onOpenLedger = {
                            selectedDestinationName = RootDestination.LIFE.name
                            lifeSubRoute = LifeSubRoute.Ledger
                        },
                        onOpenNotifications = {
                            notificationCenterRoute = NotificationCenterRoute(source = "home-bell")
                        },
                    )
                    RootDestination.PHOTOS -> PhotosRootScreen(
                        selectedTopDestinationName = photosTopDestinationName,
                        hasTransferFailure = false,
                        runningTransferCount = 0,
                        onSystemMediaClick = {
                            if (!photosOverlayActive) {
                                systemMediaRoute = SystemMediaRoute()
                            }
                        },
                        onTransferClick = {
                            if (!photosOverlayActive) {
                                transferCenterRoute = TransferCenterRoute(source = "photos-top-bar")
                            }
                        },
                        selectedSectionInitial = null,
                        trashParams = PhotosRootTrashParams(
                            selectedTypeName = trashSelectedTypeName,
                        ),
                        selectionParams = PhotosRootSelectionParams(
                            onAddedMediaToPost = { route: Any? -> (route as? PostDetailPlaceholderRoute)?.let(openPostDetailAfterAdd) },
                            photoFeedScrollTrigger = photoFeedScrollTrigger.toLong(),
                            photoSelectionClearTrigger = photoSelectionClearTrigger.toLong(),
                        ),
                        onOpenViewer = { route ->
                            if (!photosOverlayActive) {
                                photoViewerRoute = route
                            }
                        },
                        onOpenPostDetail = { route ->
                            if (!photosOverlayActive) {
                                postDetailRoute = route
                            }
                        },
                        onOpenTrashDetail = { route ->
                            if (!photosOverlayActive) {
                                photosTopDestinationName = PhotosTopDestination.TRASH.name
                                trashDetailRoute = route
                            }
                        },
                        onOpenCreatePost = { route ->
                            if (!photosOverlayActive) {
                                createPostRoute = route
                            }
                        },
                        onAddedMediaToPost = { route ->
                            openPostDetailAfterAdd(route)
                        },
                        onSelectedTopDestinationChange = { newName ->
                            photosTopDestinationName = newName
                        },
                        onPhotoSelectionShellStateChange = { photoSelectionShellState = it },
                    )
                    RootDestination.LIFE -> {
                        when (lifeSubRoute) {
                            is LifeSubRoute.Ledger -> LedgerScreen(
                                onCloseLedger = { lifeSubRoute = LifeSubRoute.Life },
                                modifier = Modifier.fillMaxSize(),
                            )
                            is LifeSubRoute.LedgerAdd -> LedgerScreen(
                                openAddNonce = LifeSubRoute.LedgerAdd.hashCode(),
                                onCloseLedger = { lifeSubRoute = LifeSubRoute.Ledger },
                                modifier = Modifier.fillMaxSize(),
                            )
                            is LifeSubRoute.Console -> LifeConsoleScreen(
                                modifier = Modifier.fillMaxSize(),
                                initialSlotKey = AppNavigationRequests.lifeConsoleSlotKey,
                                initialMediaId = AppNavigationRequests.lifeConsoleMediaId,
                                onBack = { lifeSubRoute = LifeSubRoute.Life },
                                onOpenLedgerAdd = {
                                    lifeSubRoute = LifeSubRoute.LedgerAdd
                                },
                            )
                            is LifeSubRoute.Chat -> ImportedChatScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { lifeSubRoute = LifeSubRoute.Life },
                            )
                            is LifeSubRoute.Life -> LifeScreen(
                                onNavigate = { lifeSubRoute = it },
                            )
                        }
                    }
                    RootDestination.ME -> run {
                        val user = requireNotNull(currentUser)
                        when {
                        editProfileRoute != null -> EditProfileScreen(
                            currentUser = user,
                            onBack = { editProfileRoute = null },
                            onProfileSaved = { updatedUser ->
                                // 同步更新 CollaboratorDirectoryStore，避免 LaunchedEffect 异步时序导致资产管理等页面读到旧值
                                CollaboratorDirectoryStore.update(updatedUser)
                                AuthSessionManager.saveCurrentUserSnapshot(updatedUser)
                                currentUser = updatedUser
                                profileRefreshMessage = null
                            },
                            onSessionExpired = { message -> handleUnauthorized(message) },
                            onShowNotice = ::showAppNotice,
                            modifier = Modifier.fillMaxSize(),
                        )

                        personalProfileRoute != null -> PersonalProfileScreen(
                            currentUser = user,
                            isOfflineReadOnly = offlineAccessState.isReadOnly,
                            isRefreshing = isRefreshingProfile,
                            refreshErrorMessage = profileRefreshMessage,
                            onBack = { personalProfileRoute = null },
                            onOpenEditProfile = {
                                editProfileRoute = EditProfileRoute(source = "personal-profile")
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> MyScreen(
                            currentUser = user,
                            isOfflineReadOnly = offlineAccessState.isReadOnly,
                            isLoggingOut = isLoggingOut,
                            onOpenProfile = {
                                personalProfileRoute = PersonalProfileRoute(source = "my-page")
                            },
                            onLogout = { performLogout() },
                            onOpenSettings = { settingsRoute = SettingsRoute(source = "my-page") },
                            onOpenCacheManagement = {
                                cacheManagementRoute = CacheManagementRoute(source = "my-page")
                            },
                        )
                        }
                    }
                }
                }

                createPostRoute?.let { route ->
                    CreatePostScreen(
                        route = route,
                        onBack = { createPostRoute = null },
                        onCreated = { createdRoute ->
                            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                            markPostListUpdated(createdRoute.postId, createdRoute.albumId)
                            createPostRoute = null
                            selectedDestinationName = RootDestination.PHOTOS.name
                            photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                            photoSelectionClearTrigger++
                            postDetailRoute = postDetailRouteWithNotice(createdRoute, "已发布")
                        },
                        onSubmittedToBackground = { createPostRoute = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                postDetailRoute?.let { route ->
                    PostDetailScreen(
                        route = route,
                        onBack = closePostDetail,
                        onOpenGearEdit = { gearEditRoute = it },
                        onOpenPostDetail = { postDetailRoute = it },
                        onOpenCacheManagement = { cacheManagementRoute = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                gearEditRoute?.let { route ->
                    GearEditScreen(
                        route = route,
                        onBack = { gearEditRoute = null },
                        onPostUpdated = { postId, albumId ->
                            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                            markPostListUpdated(postId, albumId)
                            postDetailRoute = postDetailRoute?.let { currentRoute ->
                                if (currentRoute.postId == postId) {
                                    postDetailRouteWithNotice(
                                        currentRoute.copy(
                                            albumId = albumId ?: currentRoute.albumId,
                                            albumIds = albumId?.let { listOf(it) } ?: currentRoute.albumIds,
                                            highlightMediaIds = emptyList(),
                                            focusMediaId = null,
                                        ),
                                        "小相册已更新",
                                    )
                                } else {
                                    currentRoute
                                }
                            }
                        },
                        onDeleteCurrentPost = { postId, deleteMediaSystemWide ->
                            if (deleteMediaSystemWide) {
                                SyncVersionTracker.markLocalMutation(SyncModule.PHOTO_FEED)
                            }
                            SyncVersionTracker.markLocalMutation(SyncModule.ALBUMS)
                            SyncVersionTracker.markLocalMutation(SyncModule.TRASH)
                            SyncVersionTracker.markLocalMutation(SyncModule.NOTIFICATIONS)
                            markPostListUpdated(postId, postDetailRoute?.albumId)
                            gearEditRoute = null
                            postDetailRoute = null
                            selectedDestinationName = RootDestination.PHOTOS.name
                            photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                mediaManagementRoute?.let { route ->
                    MediaManagementScreen(
                        route = route,
                        onBack = { mediaManagementRoute = null },
                        onPostUpdated = { postId ->
                            markPostListUpdated(postId, postDetailRoute?.albumId)
                            postDetailRoute = postDetailRoute?.let { currentRoute ->
                                if (currentRoute.postId == postId) {
                                    postDetailRouteWithNotice(
                                        currentRoute.copy(
                                            highlightMediaIds = emptyList(),
                                            focusMediaId = null,
                                        ),
                                        "媒体已更新",
                                    )
                                } else {
                                    currentRoute
                                }
                            }
                        },
                        onCurrentPostDeleted = {
                            mediaManagementRoute = null
                            gearEditRoute = null
                            postDetailRoute = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

	                photoViewerRoute?.let { route ->
	                    PhotoViewerScreen(
                        route = route,
                        onBack = {
                            val snapshot = latestPhotoViewerRoute ?: route
                            val targetItem = if (snapshot.mediaItems.isNotEmpty()) {
                                snapshot.mediaItems.getOrNull(
                                    snapshot.initialIndex.coerceIn(0, snapshot.mediaItems.lastIndex),
                                )
                            } else {
                                null
                            }
                            if (targetItem != null && !snapshot.showPostSegments) {
                                GlobalPhotoFeedPageStateStore.pendingScrollTargetMediaId = targetItem.mediaId
                                GlobalPhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
                                GlobalPhotoFeedPageStateStore.pendingHighlightNonce += 1
                                GlobalPhotoFeedPageStateStore.pendingLocateSuccessMessage = null
                                GlobalPhotoFeedPageStateStore.pendingLocateFailureMessage = null
                            }
                            photoViewerRoute = null
                            latestPhotoViewerRoute = null
                            photoFeedScrollTrigger++
                        },
                        onOpenPostDetail = {
                            postDetailReturnViewerRoute = latestPhotoViewerRoute ?: route
                            photoViewerRoute = null
                            postDetailRoute = it
                        },
                        onOpenCreatePost = { route ->
                            photoViewerRoute = null
                            latestPhotoViewerRoute = null
                            createPostRoute = route
                        },
                        onOpenCacheManagement = { cacheManagementRoute = it },
                        onRouteSnapshotChange = { latestPhotoViewerRoute = it },
	                    )
	                }
                YingShiNoticeHost(
                    notice = appNotice,
                    onExpired = { nonce ->
                        if (appNotice?.nonce == nonce) {
                            appNotice = null
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = YingShiThemeTokens.spacing.md),
                )
	            } // closes Box
	            }
	        }
    }

}
}
