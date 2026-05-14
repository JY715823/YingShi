package com.example.yingshi.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import android.widget.Toast
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.me.MyScreen
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
import com.example.yingshi.feature.photos.GearEditRoute
import com.example.yingshi.feature.photos.GearEditScreen
import com.example.yingshi.feature.photos.MediaManagementRoute
import com.example.yingshi.feature.photos.MediaManagementScreen
import com.example.yingshi.feature.photos.NotificationCenterRoute
import com.example.yingshi.feature.photos.NotificationCenterScreen
import com.example.yingshi.feature.photos.NotificationDetailRoute
import com.example.yingshi.feature.photos.NotificationDetailScreen
import com.example.yingshi.feature.photos.PhotoViewerRoute
import com.example.yingshi.feature.photos.PhotoViewerScreen
import com.example.yingshi.feature.photos.PhotoFeedPageStateStore
import com.example.yingshi.feature.photos.PhotosRootScreen
import com.example.yingshi.feature.photos.PostDetailPlaceholderRoute
import com.example.yingshi.feature.photos.PostDetailScreen
import com.example.yingshi.feature.photos.SettingsRoute
import com.example.yingshi.feature.photos.SettingsScreen
import com.example.yingshi.feature.photos.SystemMediaRoute
import com.example.yingshi.feature.photos.SystemMediaScreen
import com.example.yingshi.feature.photos.TransferCenterRoute
import com.example.yingshi.feature.photos.TransferCenterScreen
import com.example.yingshi.feature.photos.SystemMediaViewerRoute
import com.example.yingshi.feature.photos.SystemMediaViewerScreen
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
import com.example.yingshi.ui.theme.YingShiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YingShiApp() {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(RootDestination.PHOTOS.name)
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
    var showQuickAddSheet by rememberSaveable {
        mutableStateOf(false)
    }
    var photoViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
    }
    var photoFeedScrollTrigger by remember { mutableIntStateOf(0) }
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
    var gearEditRoute by remember {
        mutableStateOf<GearEditRoute?>(null)
    }
    var mediaManagementRoute by remember {
        mutableStateOf<MediaManagementRoute?>(null)
    }
    var notificationCenterRoute by remember {
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
    var trashDetailRoute by remember {
        mutableStateOf<TrashDetailRoute?>(null)
    }
    val operationResults = LocalSystemMediaBridgeRepository.operationResults
    val selectedDestination = RootDestination.valueOf(selectedDestinationName)
    val context = LocalContext.current
    val requestPhotoFeedRefresh: (List<String>) -> Unit = { resultMediaIds ->
        val targetMediaId = resultMediaIds.firstOrNull { it.isNotBlank() }
        if (targetMediaId != null) {
            PhotoFeedPageStateStore.pendingScrollTargetMediaId = targetMediaId
            PhotoFeedPageStateStore.pendingScrollAnchorOriginalIndex = -1
            PhotoFeedPageStateStore.pendingHighlightNonce += 1
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
            Toast.makeText(context, "已取消导入媒体。", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        showQuickAddSheet = false
        runCatching {
            com.example.yingshi.feature.photos.LocalSystemMediaBridgeRepository
                .enqueueImportPickedMediaToAppUpload(
                    context = context,
                    mediaUris = uris,
                )
        }.onSuccess { importedCount ->
            if (importedCount > 0) {
                Toast.makeText(
                    context,
                    "已加入导入队列，完成后会出现在照片流。",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "没有找到可导入的图片或视频。",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }.onFailure {
            Toast.makeText(
                context,
                "导入媒体失败，请稍后重试。",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(operationResults.size) {
        if (operationResults.isEmpty()) return@LaunchedEffect
        val pendingEvents = operationResults.toList()
        pendingEvents.forEach { event ->
            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            if (event.operationType == LocalSystemMediaBridgeRepository.OperationType.CREATE_POST &&
                event.postRoute != null &&
                event.successCount > 0
            ) {
                AlbumPageStateStore.pendingSelectedAlbumId = event.postRoute.albumId
                photoViewerRoute = null
                systemMediaViewerRoute = null
                systemMediaRoute = null
                createPostRoute = null
                transferCenterRoute = null
                selectedDestinationName = RootDestination.PHOTOS.name
                photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                postDetailRoute = event.postRoute
            } else if (
                event.operationType != LocalSystemMediaBridgeRepository.OperationType.CREATE_POST &&
                event.shouldAutoOpenResult &&
                event.successCount > 0 &&
                event.resultMediaIds.isNotEmpty()
            ) {
                requestPhotoFeedRefresh(event.resultMediaIds)
            }
            LocalSystemMediaBridgeRepository.dismissOperationResult(event.eventId)
        }
    }

    if (photoViewerRoute != null) {
        BackHandler {
            photoViewerRoute = null
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
            postDetailRoute = null
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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppShellScaffold(
            selectedDestination = selectedDestination,
            onDestinationSelected = { selectedDestinationName = it.name },
            onCenterAction = {
                showQuickAddSheet = true
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
                cacheManagementRoute == null,
        ) {
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
                    )
                }
            }

            notificationDetailRoute != null -> {
                notificationDetailRoute?.let { route ->
                    NotificationDetailScreen(
                        route = route,
                        onBack = { notificationDetailRoute = null },
                    )
                }
            }

            notificationCenterRoute != null -> {
                notificationCenterRoute?.let { route ->
                    NotificationCenterScreen(
                        route = route,
                        onBack = { notificationCenterRoute = null },
                        onOpenNotificationDetail = { notificationDetailRoute = it },
                        onOpenNotificationTarget = { item ->
                            when (item.id) {
                                "notice-comment-1",
                                "notice-comment-2",
                                "notice-post-update-1" -> {
                                    notificationCenterRoute = null
                                    notificationDetailRoute = null
                                    if (RepositoryProvider.currentMode == RepositoryMode.REAL) {
                                        selectedDestinationName = RootDestination.PHOTOS.name
                                        photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                                    } else {
                                        FakeAlbumRepository.getPosts().firstOrNull()?.let { post ->
                                            postDetailRoute = FakeAlbumRepository.toPostDetailRoute(post)
                                        }
                                    }
                                }
                                "notice-album-update-1" -> {
                                    notificationCenterRoute = null
                                    notificationDetailRoute = null
                                    selectedDestinationName = RootDestination.PHOTOS.name
                                    photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                                }
                                "notice-trash-1" -> {
                                    notificationCenterRoute = null
                                    notificationDetailRoute = null
                                    selectedDestinationName = RootDestination.PHOTOS.name
                                    photosTopDestinationName = PhotosTopDestination.TRASH.name
                                }
                                "notice-restore-1" -> {
                                    notificationCenterRoute = null
                                    notificationDetailRoute = null
                                    selectedDestinationName = RootDestination.PHOTOS.name
                                    photosTopDestinationName = PhotosTopDestination.TRASH.name
                                }
                                "notice-cache-1" -> {
                                    notificationCenterRoute = null
                                    notificationDetailRoute = null
                                    cacheManagementRoute = CacheManagementRoute(source = "notification-center")
                                }
                                else -> {
                                    notificationDetailRoute = NotificationDetailRoute(
                                        notificationId = item.id,
                                        source = "notification-center",
                                    )
                                }
                            }
                        },
                    )
                }
            }

            transferCenterRoute != null -> {
                transferCenterRoute?.let { route ->
                    TransferCenterScreen(
                        route = route,
                        onBack = { transferCenterRoute = null },
                        onOpenTaskMedia = { task ->
                            val mediaId = task.resultMediaId?.takeIf { it.isNotBlank() }
                            if (mediaId != null) {
                                requestPhotoFeedRefresh(listOf(mediaId))
                            } else {
                                Toast.makeText(
                                    context,
                                    "该任务没有可定位的目标媒体。",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                                AlbumPageStateStore.pendingSelectedAlbumId = createdRoute.albumId
                                createPostRoute = null
                                systemMediaViewerRoute = null
                                systemMediaRoute = null
                                selectedDestinationName = RootDestination.PHOTOS.name
                                photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                                postDetailRoute = createdRoute
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
                                requestPhotoFeedRefresh(mediaIds)
                            } else {
                                selectedDestinationName = RootDestination.PHOTOS.name
                                photosTopDestinationName = PhotosTopDestination.PHOTOS.name
                            }
                        },
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                when (selectedDestination) {
                    RootDestination.HOME -> HomeScreen()
                    RootDestination.PHOTOS -> PhotosRootScreen(
                        selectedTopDestinationName = photosTopDestinationName,
                        onSelectedTopDestinationChange = { photosTopDestinationName = it },
                        trashSelectedTypeName = trashSelectedTypeName,
                        onTrashSelectedTypeNameChange = { trashSelectedTypeName = it },
                        trashShowPendingCleanup = trashShowPendingCleanup,
                        onTrashShowPendingCleanupChange = {
                            trashShowPendingCleanup = it
                        },
                        onOpenViewer = { photoViewerRoute = it },
                        onOpenPostDetail = { postDetailRoute = it },
                        onOpenTrashDetail = { trashDetailRoute = it },
                        onTrashRestoreTargetMediaIds = { mediaIds ->
                            if (mediaIds.isNotEmpty()) {
                                requestPhotoFeedRefresh(mediaIds)
                            }
                        },
                        onOpenSystemMedia = { systemMediaRoute = SystemMediaRoute() },
                        onOpenTransferCenter = { transferCenterRoute = TransferCenterRoute(source = "photos-top-bar") },
                        onOpenCreatePost = { createPostRoute = it },
                        onOpenNotifications = {
                            notificationCenterRoute = NotificationCenterRoute(source = "photos-bell")
                        },
                        photoFeedScrollTrigger = photoFeedScrollTrigger,
                        inlineVideoAutoPlayEnabled = photoViewerRoute == null,
                    )
                    RootDestination.LIFE -> LifeScreen()
                    RootDestination.ME -> MyScreen(
                        onOpenSettings = { settingsRoute = SettingsRoute(source = "my-page") },
                        onOpenCacheManagement = {
                            cacheManagementRoute = CacheManagementRoute(source = "my-page")
                        },
                    )
                }

                createPostRoute?.let { route ->
                    CreatePostScreen(
                        route = route,
                        onBack = { createPostRoute = null },
                        onCreated = { createdRoute ->
                            AlbumPageStateStore.pendingSelectedAlbumId = createdRoute.albumId
                            createPostRoute = null
                            selectedDestinationName = RootDestination.PHOTOS.name
                            photosTopDestinationName = PhotosTopDestination.ALBUMS.name
                            postDetailRoute = createdRoute
                        },
                        onSubmittedToBackground = { createPostRoute = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                postDetailRoute?.let { route ->
                    PostDetailScreen(
                        route = route,
                        onBack = { postDetailRoute = null },
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
                        onDeleteCurrentPost = { postId, deleteMediaSystemWide ->
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
                verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
            ) {
                Text(
                    text = "添加内容",
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
                            Toast.makeText(
                                context,
                                "无法打开系统照片选择器，请稍后重试。",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(text = "导入媒体")
                }
            }
        }
    }
}

}

@Preview(showBackground = true)
@Composable
private fun YingShiAppPreview() {
    YingShiTheme {
        YingShiApp()
    }
}
