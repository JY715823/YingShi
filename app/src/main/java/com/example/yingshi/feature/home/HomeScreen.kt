package com.example.yingshi.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.YingShiIconBubble
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit = {},
    onOpenLife: () -> Unit = {},
    onOpenMe: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors

    YingShiMistBackground(modifier = modifier, showWaves = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 24.dp, bottom = 104.dp),
        ) {
            YingShiIconBubble(
                icon = Icons.Rounded.Notifications,
                contentDescription = "通知",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp),
                onClick = onOpenNotifications,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "映世",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = "欢迎回来",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YingShiTheme {
        HomeScreen()
    }
}
