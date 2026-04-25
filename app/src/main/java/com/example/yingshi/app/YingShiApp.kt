package com.example.yingshi.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.photos.GearEditRoute
import com.example.yingshi.feature.photos.GearEditScreen
import com.example.yingshi.feature.photos.MediaManagementRoute
import com.example.yingshi.feature.photos.MediaManagementScreen
import com.example.yingshi.feature.photos.PhotoViewerRoute
import com.example.yingshi.feature.photos.PhotoViewerScreen
import com.example.yingshi.feature.photos.PhotosRootScreen
import com.example.yingshi.feature.photos.PostDetailPlaceholderRoute
import com.example.yingshi.feature.photos.PostDetailScreen
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.components.AppShellScaffold
import com.example.yingshi.ui.theme.YingShiTheme

@Composable
fun YingShiApp() {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(RootDestination.PHOTOS.name)
    }
    var photoViewerRoute by remember {
        mutableStateOf<PhotoViewerRoute?>(null)
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
    val selectedDestination = RootDestination.valueOf(selectedDestinationName)

    if (photoViewerRoute != null) {
        BackHandler {
            photoViewerRoute = null
        }
    }
    if (postDetailRoute != null) {
        BackHandler(enabled = gearEditRoute == null && mediaManagementRoute == null) {
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

    AppShellScaffold(
        selectedDestination = selectedDestination,
        onDestinationSelected = { selectedDestinationName = it.name },
        showBottomBar = photoViewerRoute == null &&
            postDetailRoute == null &&
            gearEditRoute == null &&
            mediaManagementRoute == null,
    ) {
        when {
            photoViewerRoute != null -> {
                photoViewerRoute?.let { route ->
                    PhotoViewerScreen(
                        route = route,
                        onBack = { photoViewerRoute = null },
                    )
                }
            }

            else -> {
                when (selectedDestination) {
                    RootDestination.HOME -> HomeScreen()
                    RootDestination.PHOTOS -> PhotosRootScreen(
                        onOpenViewer = { photoViewerRoute = it },
                        onOpenPostDetail = { postDetailRoute = it },
                    )
                    RootDestination.LIFE -> LifeScreen()
                }

                postDetailRoute?.let { route ->
                    PostDetailScreen(
                        route = route,
                        onBack = { postDetailRoute = null },
                        onOpenGearEdit = { gearEditRoute = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                gearEditRoute?.let { route ->
                    GearEditScreen(
                        route = route,
                        onBack = { gearEditRoute = null },
                        onOpenMediaManagement = { mediaManagementRoute = it },
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
