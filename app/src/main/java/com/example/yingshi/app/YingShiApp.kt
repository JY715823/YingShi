package com.example.yingshi.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.photos.PhotoViewerPlaceholderRoute
import com.example.yingshi.feature.photos.PhotoViewerPlaceholderScreen
import com.example.yingshi.feature.photos.PhotosRootScreen
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.components.AppShellScaffold
import com.example.yingshi.ui.theme.YingShiTheme

@Composable
fun YingShiApp() {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(RootDestination.PHOTOS.name)
    }
    var photoViewerRoute by remember {
        mutableStateOf<PhotoViewerPlaceholderRoute?>(null)
    }
    val selectedDestination = RootDestination.valueOf(selectedDestinationName)

    if (photoViewerRoute != null) {
        BackHandler {
            photoViewerRoute = null
        }
    }

    AppShellScaffold(
        selectedDestination = selectedDestination,
        onDestinationSelected = { selectedDestinationName = it.name },
        showBottomBar = photoViewerRoute == null,
    ) {
        if (photoViewerRoute != null) {
            photoViewerRoute?.let { route ->
                PhotoViewerPlaceholderScreen(
                    route = route,
                    onBack = { photoViewerRoute = null },
                )
            }
        } else {
            when (selectedDestination) {
                RootDestination.HOME -> HomeScreen()
                RootDestination.PHOTOS -> PhotosRootScreen(
                    onOpenViewer = { photoViewerRoute = it },
                )
                RootDestination.LIFE -> LifeScreen()
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
