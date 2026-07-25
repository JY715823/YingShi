package com.example.yingshi.app

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.yingshi.feature.photos.PhotosRootSelectionUiState
import com.example.yingshi.feature.photos.PhotoSelectionShellAction
import com.example.yingshi.navigation.PhotosTopDestination
import com.example.yingshi.navigation.RootDestination

/**
 * Extracted back handler stack from YingShiApp.
 *
 * Manages 18 conditional BackHandler blocks for overlay/route dismissal,
 * plus the root double-press-to-exit handler and photo selection clear.
 */
@Composable
internal fun YingShiBackHandlers(
    // Route states
    photoViewerRoute: Any?,
    lifeConsoleRouteActive: Boolean,
    chatViewerRouteActive: Boolean,
    systemMediaViewerRoute: Any?,
    systemMediaRoute: Any?,
    createPostRoute: Any?,
    trashDetailRoute: Any?,
    postDetailRoute: Any?,
    gearEditRoute: Any?,
    mediaManagementRoute: Any?,
    notificationDetailRoute: Any?,
    transferCenterRoute: Any?,
    notificationCenterRoute: Any?,
    settingsRoute: Any?,
    backendDiagnosticsRoute: Any?,
    cacheManagementRoute: Any?,
    personalProfileRoute: Any?,
    editProfileRoute: Any?,
    notificationTargetReturnRoute: Any?,
    ledgerRouteActive: Boolean,
    // Root state
    selectedDestination: RootDestination,
    photoSelectionShellState: PhotosRootSelectionUiState,
    photosTopDestinationName: String,
    // Route setters
    setPhotoViewerRoute: (Any?) -> Unit,
    setLifeConsoleRouteActive: (Boolean) -> Unit,
    setChatViewerRouteActive: (Boolean) -> Unit,
    setSystemMediaViewerRoute: (Any?) -> Unit,
    setSystemMediaRoute: (Any?) -> Unit,
    setCreatePostRoute: (Any?) -> Unit,
    setTrashDetailRoute: (Any?) -> Unit,
    setGearEditRoute: (Any?) -> Unit,
    setMediaManagementRoute: (Any?) -> Unit,
    setNotificationDetailRoute: (Any?) -> Unit,
    setTransferCenterRoute: (Any?) -> Unit,
    setNotificationCenterRoute: (Any?) -> Unit,
    setSettingsRoute: (Any?) -> Unit,
    setBackendDiagnosticsRoute: (Any?) -> Unit,
    setCacheManagementRoute: (Any?) -> Unit,
    setPersonalProfileRoute: (Any?) -> Unit,
    setEditProfileRoute: (Any?) -> Unit,
    // Actions
    closePostDetail: () -> Unit,
    restoreNotificationCenter: () -> Unit,
    onPhotoSelectionClear: () -> Unit,
    lastRootBackPressedAt: Long,
    setLastRootBackPressedAt: (Long) -> Unit,
) {
    val context = LocalContext.current
    val isProfileFlowActive = personalProfileRoute != null || editProfileRoute != null

    // ---- Overlay back handlers (priority order) ----

    if (photoViewerRoute != null) {
        BackHandler { setPhotoViewerRoute(null) }
    }
    if (lifeConsoleRouteActive) {
        BackHandler { setLifeConsoleRouteActive(false) }
    }
    if (chatViewerRouteActive) {
        BackHandler { setChatViewerRouteActive(false) }
    }
    if (systemMediaViewerRoute != null) {
        BackHandler { setSystemMediaViewerRoute(null) }
    }
    if (systemMediaRoute != null && systemMediaViewerRoute == null && createPostRoute == null) {
        BackHandler { setSystemMediaRoute(null) }
    }
    if (createPostRoute != null) {
        BackHandler { setCreatePostRoute(null) }
    }
    if (trashDetailRoute != null) {
        BackHandler {
            setTrashDetailRoute(null)
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
            setGearEditRoute(null)
        }
    }
    if (mediaManagementRoute != null) {
        BackHandler { setMediaManagementRoute(null) }
    }
    if (notificationDetailRoute != null) {
        BackHandler { setNotificationDetailRoute(null) }
    }
    if (transferCenterRoute != null) {
        BackHandler {
            setTransferCenterRoute(null)
            restoreNotificationCenter()
        }
    }
    if (
        notificationCenterRoute != null &&
        notificationDetailRoute == null &&
        settingsRoute == null &&
        cacheManagementRoute == null
    ) {
        BackHandler { setNotificationCenterRoute(null) }
    }
    if (settingsRoute != null && cacheManagementRoute == null) {
        BackHandler { setSettingsRoute(null) }
    }
    if (backendDiagnosticsRoute != null) {
        BackHandler { setBackendDiagnosticsRoute(null) }
    }
    if (cacheManagementRoute != null) {
        BackHandler {
            setCacheManagementRoute(null)
            restoreNotificationCenter()
        }
    }

    // ---- Profile flow back ----

    if (editProfileRoute != null) {
        BackHandler { setEditProfileRoute(null) }
    } else if (personalProfileRoute != null) {
        BackHandler { setPersonalProfileRoute(null) }
    }

    // ---- Notification target return ----

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

    // ---- Root exit handler (double-press) ----

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
            !photoSelectionShellState.isActive

    if (rootExitEligible) {
        BackHandler {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRootBackPressedAt <= 2000L) {
                activity?.finish()
            } else {
                setLastRootBackPressedAt(now)
                Toast.makeText(context, "再按一次退出 App", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Photo selection clear ----

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
        !ledgerRouteActive &&
        !chatViewerRouteActive &&
        !lifeConsoleRouteActive

    val showPhotoSelectionBottomBar = showRootBottomBar &&
        selectedDestination == RootDestination.PHOTOS &&
        photosTopDestinationName == PhotosTopDestination.PHOTOS.name &&
        photoSelectionShellState.isActive

    if (showPhotoSelectionBottomBar) {
        BackHandler {
            onPhotoSelectionClear()
        }
    }
}
