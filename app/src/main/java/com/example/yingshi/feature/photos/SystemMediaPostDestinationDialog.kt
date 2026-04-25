package com.example.yingshi.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SystemMediaPostDestinationDialog(
    albums: List<AlbumSummaryUiModel>,
    posts: List<AlbumPostCardUiModel>,
    onDismiss: () -> Unit,
    onPostSelected: (String) -> Unit,
) {
    var selectedAlbumId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedAlbum = albums.firstOrNull { it.id == selectedAlbumId }
    val albumCards = albums.map { album ->
        SystemMediaAlbumChoice(
            album = album,
            postCount = posts.count { post -> post.albumIds.contains(album.id) },
        )
    }
    val postsInSelectedAlbum = selectedAlbum?.let { album ->
        posts.filter { post -> post.albumIds.contains(album.id) }
            .sortedByDescending { it.postDisplayTimeMillis }
    }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (selectedAlbum == null) "选择相册" else "选择帖子",
                )
                Text(
                    text = selectedAlbum?.title ?: "先选择一个相册，再选择帖子",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedAlbum == null) {
                    if (albumCards.isEmpty()) {
                        SystemMediaPickerEmptyState(
                            text = "当前没有可选相册",
                        )
                    } else {
                        albumCards.forEach { choice ->
                            Surface(
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                                onClick = { selectedAlbumId = choice.album.id },
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = choice.album.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "${choice.postCount} 个帖子",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else if (postsInSelectedAlbum.isEmpty()) {
                    SystemMediaPickerEmptyState(
                        text = "该相册下还没有帖子",
                    )
                } else {
                    postsInSelectedAlbum.forEach { post ->
                        Surface(
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                            onClick = { onPostSelected(post.id) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            brush = Brush.linearGradient(
                                                listOf(post.coverPalette.start, post.coverPalette.end),
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                        ),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = post.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = formatSystemMediaPickerTime(post.postDisplayTimeMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${post.mediaCount} 项媒体",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedAlbum != null) {
                TextButton(onClick = { selectedAlbumId = null }) {
                    Text(text = "返回上一级")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun SystemMediaPickerEmptyState(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class SystemMediaAlbumChoice(
    val album: AlbumSummaryUiModel,
    val postCount: Int,
)

private fun formatSystemMediaPickerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
