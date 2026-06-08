package com.example.yingshi.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.yingshi.data.remote.auth.BackendAutoLoginManager
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.result.isUnauthorized
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.auth.LoginScreen
import com.example.yingshi.feature.chat.ImportedChatScreen
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeConsoleScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.feature.ledger.LedgerScreen
import com.example.yingshi.feature.me.EditProfileRoute
import com.example.yingshi.feature.me.EditProfileScreen
import com.example.yingshi.feature.me.MyScreen
import com.example.yingshi.feature.me.PersonalProfileRoute
import com.example.yingshi.feature.me.PersonalProfileScreen
import com.example.yingshi.feature.photos.FakeAlbumRepository
import com.example.yingshi.feature.photos.FakeTrashRepository
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
import com.example.yingshi.feature.photos.PhotoViewerRoute
import com.example.yingshi.feature.photos.PhotoViewerScreen
import com.example.yingshi.feature.photos.GlobalPhotoFeedPageStateStore
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.PhotosRootScreen
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
import com.example.yingshi.ui.theme.YingShiThemeTokens
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.components.AppShellScaffold
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
    var showQuickAddSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var pendingQuickAddImportPreview by remember {
        mutableStateOf<SystemMediaImportPreview?>(null)
    }
    var ledgerRouteActive by rememberSaveable {
        mutableStateOf(false)
    }
    var chatViewerRouteActive by rememberSaveable {
        mutableStateOf(false)
    }
    var lifeConsoleRouteActive by rememberSaveable {
        mutableStateOf(false)
    }
    var ledgerOpenAddNonce by rememberSaveable {
        mutableIntStateOf(0)
    }
    var ledgerOpenHomeNonce by rememberSaveable {
        mutableIntStateOf(0)
    }
    var photoViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
    }
    var photoFeedScrollTrigger by remember { mutableIntStateOf(0) }
    var photoSelectionClearTrigger by remember { mutableIntStateOf(0) }
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

    fun enableOfflineReadOnly(message: String = OfflineReadOnlyDefaultMessage) {
        val wasReadOnly = OfflineAccessManager.state.isReadOnly
        OfflineAccessManager.enterReadOnly(message)
        if (!wasReadOnly) {
            showAppNotice(message, YingShiNoticeTone.WARNING)
        }
    }

    LaunchedEffect(currentUser?.userId, currentUser?.updatedAtMillis, currentUser?.partner?.userId) {
        CollaboratorDirectoryStore.update(currentUser)
        currentUser?.let(AuthSessionManager::saveCurrentUserSnapshot)
    }

    LaunchedEffect(offlineAccessState.isReadOnly) {
        if (offlineAccessState.isReadOnly) {
            showQuickAddSheet = false
        }
    }

    fun resetAccountRoutes() {
        personalProfileRoute = null
        editProfileRoute = null
    }

    fun clearProtectedUiRoutes() {
        ledgerRouteActive = false
        chatViewerRouteActive = false
        lifeConsoleRouteActive = false
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
        AuthSessionManager.clearTokens()
        currentUser = null
        isCheckingAuth = false
        isRefreshingProfile = false
        isLoggingOut = false
        profileRefreshMessage = null
        authNoticeMessage = message ?: "登录状态已失效，请重新登录。"
        clearProtectedUiRoutes()
        selectedDestinationName = RootDestination.HOME.name
    }

    fun performLogout() {
        scope.launch {
            isLoggingOut = true
            runCatching {
                RepositoryProvider.authRepository.logout()
            }
            AuthSessionManager.clearTokens()
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
                authNoticeMessage = null
                isCheckingAuth = false
                isRefreshingProfile = false
                profileRefreshMessage = OfflineReadOnlyDefaultMessage
                enableOfflineReadOnly("当前正在显示上次缓存内容，恢复连接后会自动刷新。")
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
            profileRefreshMessage = offlineAccessState.message ?: "当前显示的是缓存资料，恢复连接后会自动刷新。"
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
            if (cachedUser == null) return@LaunchedEffect
            val loginOutcome = BackendAutoLoginManager.loginDefault(
                force = false,
                reason = "shell_reconnect_recover",
            )
            if (!loginOutcome.success) {
                return@LaunchedEffect
            }
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
        ledgerRouteActive = false
        chatViewerRouteActive = false
        lifeConsoleRouteActive = true
    }

    LaunchedEffect(openLedgerRequestNonce) {
        if (openLedgerRequestNonce <= 0) return@LaunchedEffect
        selectedDestinationName = RootDestination.LIFE.name
        lifeConsoleRouteActive = false
        chatViewerRouteActive = false
        ledgerOpenAddNonce = 0
        ledgerOpenHomeNonce += 1
        ledgerRouteActive = true
    }

    LaunchedEffect(openLedgerAddRequestNonce) {
        if (openLedgerAddRequestNonce <= 0) return@LaunchedEffect
        selectedDestinationName = RootDestination.LIFE.name
        lifeConsoleRouteActive = false
        chatViewerRouteActive = false
        ledgerRouteActive = true
        ledgerOpenAddNonce += 1
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
    val quickAddPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
    ) { uris ->
        if (uris.isEmpty()) {
            showAppNotice("已取消导入媒体")
            return@rememberLauncherForActivityResult
        }
        showQuickAddSheet = false
        runCatching {
            com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository
                .buildImportPickedMediaPreview(
                    context = context,
                    mediaUris = uris,
                )
        }.onSuccess { preview ->
            if (preview.requestedCount > 0) {
                pendingQuickAddImportPreview = preview
            } else {
                showAppNotice(
                    "没有找到可导入的图片或视频。",
                    YingShiNoticeTone.WARNING,
                )
            }
        }.onFailure {
            showAppNotice(
                "导入媒体失败，请稍后重试。",
                YingShiNoticeTone.WARNING,
            )
        }
    }

    pendingQuickAddImportPreview?.let { preview ->
        SystemMediaImportPreviewDialog(
            preview = preview,
            timePreferenceLabel = SettingsRepository.getSettingsState().mediaTimePreference.label,
            onDismiss = { pendingQuickAddImportPreview = null },
            onConfirmImport = {
                val previewSnapshot = pendingQuickAddImportPreview ?: return@SystemMediaImportPreviewDialog
                if (!previewSnapshot.hasImportableItems) {
                    pendingQuickAddImportPreview = null
                    return@SystemMediaImportPreviewDialog
                }
                val importedCount = LocalSystemMediaBridgeRepository.enqueueImportToAppUpload(
                    context = context,
                    mediaItems = previewSnapshot.importableItems,
                )
                pendingQuickAddImportPreview = null
                if (importedCount > 0) {
                    showAppNotice(
                        "已加入导入队列，完成后会出现在照片流。",
                        YingShiNoticeTone.SUCCESS,
                    )
                } else {
                    showAppNotice(
                        "这些媒体已经在导入队列里，或没有可导入的媒体。",
                        YingShiNoticeTone.WARNING,
                    )
                }
            },
        )
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
                requestPhotoFeedRefresh(
                    event.resultMediaIds,
                    event.failureCount > 0 || event.cancelledCount > 0,
                )
            }
            LocalSystemMediaBridgeRepository.dismissOperationResult(event.eventId)
        }
    }

    if (photoViewerRoute != null) {
        BackHandler {
            photoViewerRoute = null
        }
    }
    if (lifeConsoleRouteActive) {
        BackHandler {
            lifeConsoleRouteActive = false
        }
    }
    if (chatViewerRouteActive) {
        BackHandler {
            chatViewerRouteActive = false
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
            restoreNotificationCenter()
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
        !ledgerRouteActive &&
        !chatViewerRouteActive &&
        !lifeConsoleRouteActive &&
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
            !ledgerRouteActive &&
            !chatViewerRouteActive &&
            !lifeConsoleRouteActive &&
            !isProfileFlowActive &&
            !showQuickAddSheet
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

    Box(modifier = Modifier.fillMaxSize()) {
        AppShellScaffold(
            selectedDestination = selectedDestination,
            onDestinationSelected = { selectedDestinationName = it.name },
            centerActionEnabled = !offlineAccessState.isReadOnly,
            onCenterAction = {
                if (offlineAccessState.isReadOnly) {
                    showAppNotice("当前为缓存只读，恢复连接后才能继续新建或导入。", YingShiNoticeTone.WARNING)
                } else {
                    showQuickAddSheet = true
                }
            },
            showBottomBar = photoViewerRoute == null &&
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
                !ledgerRouteActive &&
                !chatViewerRouteActive &&
                !lifeConsoleRouteActive,
        ) {
            val openNotificationTarget: (NotificationCenterItemUiModel) -> Unit = { item ->
                captureNotificationReturnRoute()
                when {
                    item.id == "notice-cache-1" -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        cacheManagementRoute = CacheManagementRoute(source = "notification-center")
                    }

                    !item.postId.isNullOrBlank() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                        postDetailRoute = if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                            item.toNotificationPostRoute()
                        } else {
                            FakeAlbumRepository.getPost(item.postId)
                                ?.let(FakeAlbumRepository::toPostDetailRoute)
                                ?.copy(
                                    entryNotice = "从通知进入",
                                    highlightMediaIds = item.mediaId?.let(::listOf).orEmpty(),
                                    focusMediaId = item.mediaId,
                                )
                                ?: item.toNotificationPostRoute()
                        }
                    }

                    !item.trashItemId.isNullOrBlank() && RepositoryProvider.currentMode == RepositoryMode.REAL -> {
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

                    item.isLifeLedgerTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        lifeConsoleRouteActive = false
                        chatViewerRouteActive = false
                        ledgerRouteActive = true
                    }

                    item.isLifeConsoleTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        ledgerRouteActive = false
                        chatViewerRouteActive = false
                        lifeConsoleRouteActive = true
                    }

                    item.isLifeChatTarget() -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.LIFE.name
                        ledgerRouteActive = false
                        lifeConsoleRouteActive = false
                        chatViewerRouteActive = true
                    }

                    item.type == NotificationCenterItemType.DELETE_RESTORE -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.TRASH.name
                    }

                    item.targetType.equals("UPLOAD", ignoreCase = true) -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        transferCenterRoute = TransferCenterRoute(source = "notification-center")
                    }

                    item.type == NotificationCenterItemType.CONTENT_UPDATE -> {
                        notificationCenterRoute = null
                        notificationDetailRoute = null
                        selectedDestinationName = RootDestination.PHOTOS.name
                        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                    }

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
                            when {
                                task.resultPostRoute != null -> {
                                    transferCenterRoute = null
                                    openPostDetailAfterAdd(task.resultPostRoute)
                                }

                                !task.resultMediaId.isNullOrBlank() -> {
                                    val mediaIds = task.successfulResultMediaIdsInOperation()
	                                    if (mediaIds.isNotEmpty()) {
	                                        requestPhotoFeedRefresh(
	                                            mediaIds,
	                                            task.operationFailureCount > 0 || task.operationCancelledCount > 0,
	                                        )
	                                    } else {
	                                        showAppNotice(
	                                            "没有成功导入的媒体，可在传输中心查看失败原因并重试。",
	                                            YingShiNoticeTone.WARNING,
	                                        )
	                                    }
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
                            trashDetailRoute = null
                            if (mediaIds.isNotEmpty()) {
                                requestPhotoFeedRestoreLocate(mediaIds)
                            } else {
                                selectedDestinationName = RootDestination.PHOTOS.name
                                photosTopDestinationName = PhotosTopDestination.PHOTOS.name
                            }
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
                        onOpenLife = {
                            selectedDestinationName = RootDestination.LIFE.name
                            ledgerRouteActive = false
                            chatViewerRouteActive = false
                        },
                        onOpenMe = {
                            selectedDestinationName = RootDestination.ME.name
                        },
                        onOpenNotifications = {
                            notificationCenterRoute = NotificationCenterRoute(source = "home-bell")
                        },
                    )
                    RootDestination.PHOTOS -> PhotosRootScreen(
                        selectedTopDestinationName = photosTopDestinationName,
                        onSelectedTopDestinationChange = { photosTopDestinationName = it },
                        trashSelectedTypeName = trashSelectedTypeName,
                        onTrashSelectedTypeNameChange = { trashSelectedTypeName = it },
                        trashShowPendingCleanup = trashShowPendingCleanup,
                        onTrashShowPendingCleanupChange = {
                            trashShowPendingCleanup = it
                        },
                        trashSelectionMode = trashSelectionModeState,
                        onTrashSelectionModeChange = { trashSelectionModeState = it },
                        trashSelectedEntryIds = trashSelectedEntryIdsState,
                        onTrashSelectedEntryIdsChange = { trashSelectedEntryIdsState = it },
                        onOpenViewer = { if (!photosOverlayActive) photoViewerRoute = it },
                        onOpenPostDetail = { if (!photosOverlayActive) postDetailRoute = it },
                        onOpenTrashDetail = { if (!photosOverlayActive) trashDetailRoute = it },
                        onTrashRestoreTargetMediaIds = { mediaIds ->
                            if (mediaIds.isNotEmpty()) {
                                requestPhotoFeedRestoreLocate(mediaIds)
                            }
                        },
                        onOpenSystemMedia = {
                            if (!photosOverlayActive) {
                                systemMediaRoute = SystemMediaRoute()
                            }
                        },
                        onOpenTransferCenter = {
                            if (!photosOverlayActive) {
                                transferCenterRoute = TransferCenterRoute(source = "photos-top-bar")
                            }
                        },
                        onOpenCreatePost = {
                            if (!photosOverlayActive) {
                                createPostRoute = it
                            }
                        },
                        onAddedMediaToPost = openPostDetailAfterAdd,
                        photoFeedScrollTrigger = photoFeedScrollTrigger,
                        photoSelectionClearTrigger = photoSelectionClearTrigger,
                        inlineVideoAutoPlayEnabled = photoViewerRoute == null,
                    )
                    RootDestination.LIFE -> {
                        if (ledgerRouteActive) {
                            LedgerScreen(
                                openHomeNonce = ledgerOpenHomeNonce,
                                openAddNonce = ledgerOpenAddNonce,
                                onCloseLedger = { ledgerRouteActive = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (lifeConsoleRouteActive) {
                            LifeConsoleScreen(
                                modifier = Modifier.fillMaxSize(),
                                initialSlotKey = AppNavigationRequests.lifeConsoleSlotKey,
                                initialMediaId = AppNavigationRequests.lifeConsoleMediaId,
                                onBack = { lifeConsoleRouteActive = false },
                                onOpenLedgerAdd = {
                                    lifeConsoleRouteActive = false
                                    chatViewerRouteActive = false
                                    ledgerRouteActive = true
                                    ledgerOpenAddNonce += 1
                                },
                            )
                        } else if (chatViewerRouteActive) {
                            ImportedChatScreen(
                                modifier = Modifier.fillMaxSize(),
                                onBack = { chatViewerRouteActive = false },
                            )
                        } else {
                            LifeScreen(
                                onOpenLifeConsole = {
                                    ledgerRouteActive = false
                                    chatViewerRouteActive = false
                                    lifeConsoleRouteActive = true
                                },
                                onOpenLedger = {
                                    ledgerOpenAddNonce = 0
                                    ledgerOpenHomeNonce += 1
                                    chatViewerRouteActive = false
                                    lifeConsoleRouteActive = false
                                    ledgerRouteActive = true
                                },
                                onOpenChatViewer = {
                                    ledgerRouteActive = false
                                    lifeConsoleRouteActive = false
                                    chatViewerRouteActive = true
                                },
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
                            if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                                markPostListUpdated(postId, postDetailRoute?.albumId)
                                gearEditRoute = null
                                postDetailRoute = null
                                selectedDestinationName = RootDestination.PHOTOS.name
                                photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                                return@GearEditScreen
                            }
                            val postSnapshot = FakeAlbumRepository.snapshotPost(postId)
                            if (postSnapshot == null) {
                                gearEditRoute = null
                                postDetailRoute = null
                                return@GearEditScreen
                            }

                            if (deleteMediaSystemWide) {
                                val mediaIds = postSnapshot.mediaSnapshots.map { it.mediaId }.toSet()
                                val relationSnapshotsByMediaId = FakeAlbumRepository.snapshotMediaRelations(mediaIds)
                                val outcome = FakeAlbumRepository.previewGlobalMediaDelete(mediaIds)
                                val deletedPostSnapshots = outcome.deletedPostIds.mapNotNull(
                                    FakeAlbumRepository::snapshotPost,
                                )
                                FakeTrashRepository.recordDeletedPost(postSnapshot)
                                FakeTrashRepository.recordSystemDeletedMedia(
                                    mediaSnapshots = postSnapshot.mediaSnapshots,
                                    relationSnapshotsByMediaId = relationSnapshotsByMediaId,
                                )
                                deletedPostSnapshots.forEach { snapshot ->
                                    FakeTrashRepository.recordDeletedPost(snapshot)
                                }
                                val appliedOutcome = FakeAlbumRepository.applyGlobalMediaDelete(mediaIds)
                                FakeAlbumRepository.deletePostsLocally(appliedOutcome.deletedPostIds + postId)
                            } else {
                                FakeTrashRepository.recordDeletedPost(postSnapshot)
                                FakeAlbumRepository.deletePostsLocally(listOf(postId))
                            }
                            gearEditRoute = null
                            postDetailRoute = null
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
                            photoViewerRoute = null
                            photoFeedScrollTrigger++
                        },
                        onOpenPostDetail = {
                            postDetailReturnViewerRoute = route
                            photoViewerRoute = null
                            postDetailRoute = it
                        },
                        onOpenCreatePost = { route ->
                            photoViewerRoute = null
                            createPostRoute = route
                        },
                        onOpenCacheManagement = { cacheManagementRoute = it },
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

    if (showQuickAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAddSheet = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs)) {
                    Text(
                        text = "照片",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = {
                            showQuickAddSheet = false
                            runCatching {
                                quickAddPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        mediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                    ),
                                )
	                            }.onFailure {
	                                showAppNotice(
	                                    "无法打开系统照片选择器，请稍后重试。",
	                                    YingShiNoticeTone.WARNING,
	                                )
	                            }
                        },
                    ) {
                        Text(text = "导入媒体")
                    }
                    TextButton(
                        onClick = {
                            showQuickAddSheet = false
                            selectedDestinationName = RootDestination.PHOTOS.name
                            createPostRoute = CreatePostRoute(
                                source = "bottom-quick-add",
                                initialAppMediaIds = emptyList(),
                                initialAppMediaItems = emptyList(),
                            )
                        },
                    ) {
                        Text(text = "新建小相册")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs)) {
                    Text(
                        text = "记账",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = {
                            showQuickAddSheet = false
                            selectedDestinationName = RootDestination.LIFE.name
                            ledgerRouteActive = true
                            ledgerOpenAddNonce += 1
                        },
                    ) {
                        Text(text = "记一笔")
                    }
                }
            }
        }
    }
}

}

@Composable
private fun AuthCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(YingShiThemeTokens.spacing.lg),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在校验登录状态...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun transferToastMessage(event: LocalSystemMediaBridgeRepository.OperationResultEvent): String {
    val shouldAutoOpenResult = event.shouldAutoOpenResult
    val totalCount = event.totalCount
    val successCount = event.successCount
    val failureCount = event.failureCount
    val cancelledCount = event.cancelledCount
    val succeeded = event.succeeded
    val operationType = event.operationType
    if (shouldAutoOpenResult && totalCount > successCount) {
        return ""
    }
    val hasUnfinishedItems = failureCount > 0 || cancelledCount > 0
    val isPartial = successCount > 0 && hasUnfinishedItems
    return when (operationType) {
        LocalSystemMediaBridgeRepository.OperationType.IMPORT_TO_APP -> when {
            isPartial -> "部分导入完成"
            succeeded && successCount > 0 -> "导入完成"
            cancelledCount > 0 && failureCount == 0 -> "导入已取消"
            else -> "导入失败，可重试"
        }
        LocalSystemMediaBridgeRepository.OperationType.CREATE_POST -> when {
            isPartial -> "小相册部分创建完成"
            succeeded && successCount > 0 -> "小相册创建完成"
            cancelledCount > 0 && failureCount == 0 -> "小相册创建已取消"
            else -> "小相册创建失败，可重试"
        }
        LocalSystemMediaBridgeRepository.OperationType.ADD_TO_EXISTING_POST -> when {
            isPartial -> "部分加入成功"
            succeeded && successCount > 0 -> "已加入小相册"
            cancelledCount > 0 && failureCount == 0 -> "加入已取消"
            else -> "加入失败，可重试"
        }
    }
}

private fun SystemMediaUploadTaskUiModel.successfulResultMediaIdsInOperation(): List<String> {
    val taskOperationId = operationId
    val ids = LocalSystemMediaBridgeRepository.uploadTasks
        .filter { task -> task.operationId == taskOperationId }
        .mapNotNull { task -> task.resultMediaId?.takeIf { it.isNotBlank() } }
        .distinct()
    return ids.ifEmpty { resultMediaId?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty() }
}

private fun NotificationCenterItemUiModel.toNotificationPostRoute(): PostDetailPlaceholderRoute {
    val resolvedPostId = requireNotNull(postId)
    val syntheticAlbumId = "notification-entry"
    return PostDetailPlaceholderRoute(
        postId = resolvedPostId,
        albumId = syntheticAlbumId,
        albumIds = listOf(syntheticAlbumId),
        title = targetSummary.ifBlank { title },
        summary = body,
        postDisplayTimeMillis = createdAtMillis,
        mediaCount = if (mediaId.isNullOrBlank()) 0 else 1,
        coverPalette = NotificationPlaceholderPalette,
        entryNotice = "从通知进入",
        highlightMediaIds = mediaId?.let(::listOf).orEmpty(),
        focusMediaId = mediaId,
    )
}

private fun String?.toTrashEntryTypeOrNull(): TrashEntryType? {
    return com.example.yingshi.feature.photos.parseTrashEntryTypeOrNull(this)
}

private val NotificationPlaceholderPalette = PhotoThumbnailPalette(
    start = Color(0xFFE9E2D8),
    end = Color(0xFFD7C6BB),
    accent = Color(0xFF8C6C59),
)

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Preview(showBackground = true)
@Composable
private fun YingShiAppPreview() {
    YingShiTheme {
        YingShiApp()
    }
}
