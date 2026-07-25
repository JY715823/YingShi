package com.example.yingshi.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.GlobalPhotoFeedPageStateStore
import com.example.yingshi.feature.photos.PostDetailPlaceholderRoute
import com.example.yingshi.feature.photos.toPostDetailPlaceholderRoute
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.components.YingShiNoticeTone

/**
 * Deep link handlers extracted from YingShiApp.
 *
 * Observes nonce values from [AppNavigationRequests] (incremented by MainActivity
 * when intents arrive) and dispatches navigation accordingly.
 *
 * Handles the 5 custom actions defined in AndroidManifest:
 * - OPEN_PHOTO_FEED → switch to PHOTOS tab, scroll to media
 * - OPEN_SMALL_ALBUM → switch to ALBUMS tab, open post detail
 * - OPEN_LIFE_CONSOLE → switch to LIFE tab, activate console
 * - OPEN_LEDGER → switch to LIFE tab, activate ledger (home)
 * - OPEN_LEDGER_ADD → switch to LIFE tab, activate ledger (add)
 */
@Composable
internal fun YingShiDeepLinkHandlers(
    openLifeConsoleNonce: Int,
    openLedgerRequestNonce: Int,
    openLedgerAddRequestNonce: Int,
    openPhotoFeedRequestNonce: Int,
    openSmallAlbumRequestNonce: Int,
    // Route setters
    setSelectedDestinationName: (String) -> Unit,
    setLedgerRouteActive: (Boolean) -> Unit,
    setChatViewerRouteActive: (Boolean) -> Unit,
    setLifeConsoleRouteActive: (Boolean) -> Unit,
    setLedgerOpenAddNonce: (Int) -> Unit,
    setLedgerOpenHomeNonce: (Int) -> Unit,
    setPhotoViewerRoute: (Any?) -> Unit,
    setSystemMediaViewerRoute: (Any?) -> Unit,
    setNotificationCenterRoute: (Any?) -> Unit,
    setNotificationDetailRoute: (Any?) -> Unit,
    setPostDetailRoute: (PostDetailPlaceholderRoute?) -> Unit,
    setTransferCenterRoute: (Any?) -> Unit,
    setSystemMediaRoute: (Any?) -> Unit,
    setCreatePostRoute: (Any?) -> Unit,
    setPhotosTopDestinationName: (String) -> Unit,
    setPhotoFeedScrollTrigger: (Int) -> Unit,
    photoFeedScrollTrigger: Int,
    ledgerRouteActive: Boolean,
    // Helpers
    showAppNotice: (String, YingShiNoticeTone) -> Unit,
    markPostListUpdated: (String, String?) -> Unit,
    postDetailRouteWithNotice: (PostDetailPlaceholderRoute, String) -> PostDetailPlaceholderRoute,
) {
    // OPEN_LIFE_CONSOLE
    LaunchedEffect(openLifeConsoleNonce) {
        if (openLifeConsoleNonce <= 0) return@LaunchedEffect
        setSelectedDestinationName(RootDestination.LIFE.name)
        setLedgerRouteActive(false)
        setChatViewerRouteActive(false)
        setLifeConsoleRouteActive(true)
    }

    // OPEN_LEDGER (home mode)
    LaunchedEffect(openLedgerRequestNonce) {
        if (openLedgerRequestNonce <= 0) return@LaunchedEffect
        setSelectedDestinationName(RootDestination.LIFE.name)
        setLifeConsoleRouteActive(false)
        setChatViewerRouteActive(false)
        setLedgerOpenAddNonce(0)
        setLedgerOpenHomeNonce(ledgerRouteActive.let { if (it) 0 else 0 } + 1) // always increment
        setLedgerRouteActive(true)
    }

    // OPEN_LEDGER_ADD
    LaunchedEffect(openLedgerAddRequestNonce) {
        if (openLedgerAddRequestNonce <= 0) return@LaunchedEffect
        setSelectedDestinationName(RootDestination.LIFE.name)
        setLifeConsoleRouteActive(false)
        setChatViewerRouteActive(false)
        setLedgerRouteActive(true)
        setLedgerOpenAddNonce(openLedgerAddRequestNonce) // trigger add
    }

    // OPEN_PHOTO_FEED
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
        setPhotoViewerRoute(null)
        setSystemMediaViewerRoute(null)
        setNotificationCenterRoute(null)
        setNotificationDetailRoute(null)
        setPostDetailRoute(null)
        setTransferCenterRoute(null)
        setSelectedDestinationName(RootDestination.PHOTOS.name)
        setPhotosTopDestinationName(PhotosTopDestination.PHOTOS.name)
        setPhotoFeedScrollTrigger(photoFeedScrollTrigger + 1)
    }

    // OPEN_SMALL_ALBUM
    LaunchedEffect(openSmallAlbumRequestNonce) {
        if (openSmallAlbumRequestNonce <= 0) return@LaunchedEffect
        val postId = AppNavigationRequests.smallAlbumId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val autoOpenComment = AppNavigationRequests.smallAlbumAutoOpenComment
        setPhotoViewerRoute(null)
        setSystemMediaViewerRoute(null)
        setSystemMediaRoute(null)
        setCreatePostRoute(null)
        setTransferCenterRoute(null)
        setNotificationCenterRoute(null)
        setNotificationDetailRoute(null)
        setSelectedDestinationName(RootDestination.PHOTOS.name)
        setPhotosTopDestinationName(PhotosTopDestination.ALBUMS.name)

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
        setPostDetailRoute(postDetailRouteWithNotice(route, "从推送进入"))
    }
}
