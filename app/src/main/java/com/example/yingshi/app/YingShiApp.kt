package com.example.yingshi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.yingshi.feature.home.HomeScreen
import com.example.yingshi.feature.life.LifeScreen
import com.example.yingshi.feature.photos.PhotosRootScreen
import com.example.yingshi.navigation.RootDestination
import com.example.yingshi.ui.theme.YingShiTheme

@Composable
fun YingShiApp() {
    var selectedDestinationName by rememberSaveable {
        mutableStateOf(RootDestination.PHOTOS.name)
    }
    val selectedDestination = RootDestination.valueOf(selectedDestinationName)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                RootDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selectedDestination,
                        onClick = { selectedDestinationName = destination.name },
                        icon = { Text(text = destination.glyph) },
                        label = { Text(text = destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedDestination) {
                RootDestination.HOME -> HomeScreen()
                RootDestination.PHOTOS -> PhotosRootScreen()
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
