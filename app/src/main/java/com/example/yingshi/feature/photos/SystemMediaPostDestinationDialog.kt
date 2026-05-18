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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onPostChosen: (AlbumPostCardUiModel) -> Unit = { post -> onPostSelected(post.id) },
    isLoading: Boolean = false,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    pendingPostId: String? = null,
) {
    var selectedAlbumId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val selectedAlbum = albums.firstOrNull { it.id == selectedAlbumId }
    val albumTitleById = albums.associate { it.id to it.title }
    val recentPostIds = SystemMediaRecentPostStore.ids
    val recentPosts = posts
        .sortedWith(
            compareByDescending<AlbumPostCardUiModel> { post ->
                val recentIndex = recentPostIds.indexOf(post.id)
                if (recentIndex >= 0) recentPostIds.size - recentIndex else 0
            }.thenByDescending { post -> post.postDisplayTimeMillis },
        )
        .take(6)
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
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = if (selectedAlbum == null) "选择相册" else "选择帖子")
                Text(
                    text = selectedAlbum?.title ?: "先选择一个相册，再选择目标帖子",
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
                errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    SystemMediaPickerInlineNotice(text = message)
                }

                if (isLoading) {
                    SystemMediaPickerLoadingState()
                } else if (selectedAlbum == null) {
                    SystemMediaRecentPostsSection(
                        posts = recentPosts,
                        albumTitleById = albumTitleById,
                        isSubmitting = isSubmitting,
                        pendingPostId = pendingPostId,
                        onPostChosen = { post ->
                            SystemMediaRecentPostStore.markUsed(post.id)
                            onPostChosen(post)
                        },
                    )
                    if (albumCards.isEmpty()) {
                        SystemMediaPickerEmptyState(text = "当前没有可选相册。")
                    } else {
                        Text(
                            text = "按相册选择",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        albumCards.forEach { choice ->
                            Surface(
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                                onClick = { selectedAlbumId = choice.album.id },
                                enabled = !isSubmitting,
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
                    SystemMediaPickerEmptyState(text = "该相册下还没有帖子。")
                } else {
                    postsInSelectedAlbum.forEach { post ->
                        SystemMediaPostChoiceCard(
                            post = post,
                            albumTitleById = albumTitleById,
                            isSubmitting = isSubmitting && pendingPostId == post.id,
                            enabled = !isSubmitting,
                            onClick = {
                                SystemMediaRecentPostStore.markUsed(post.id)
                                onPostChosen(post)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedAlbum != null) {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = { selectedAlbumId = null },
                ) {
                    Text(text = "返回上一级")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss,
            ) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun SystemMediaRecentPostsSection(
    posts: List<AlbumPostCardUiModel>,
    albumTitleById: Map<String, String>,
    isSubmitting: Boolean,
    pendingPostId: String?,
    onPostChosen: (AlbumPostCardUiModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "最近帖子",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (posts.isEmpty()) {
            SystemMediaPickerEmptyState(text = "还没有最近帖子，可从下方相册选择。")
        } else {
            posts.forEach { post ->
                SystemMediaPostChoiceCard(
                    post = post,
                    albumTitleById = albumTitleById,
                    isSubmitting = isSubmitting && pendingPostId == post.id,
                    enabled = !isSubmitting,
                    onClick = { onPostChosen(post) },
                )
            }
        }
    }
}

@Composable
private fun SystemMediaPostChoiceCard(
    post: AlbumPostCardUiModel,
    albumTitleById: Map<String, String>,
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val albumLabel = post.albumIds
        .mapNotNull { albumTitleById[it] }
        .distinct()
        .take(2)
        .joinToString(" / ")
        .ifBlank { "未归档相册" }
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        onClick = onClick,
        enabled = enabled,
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
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(post.coverPalette.start, post.coverPalette.end),
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = if (post.coverMediaType == AppMediaType.VIDEO) "视频" else "封面",
                    modifier = Modifier
                        .padding(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.summary.ifBlank { "还没有简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$albumLabel · ${post.mediaCount} 项媒体 · ${formatSystemMediaPickerTime(post.postDisplayTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun SystemMediaPickerLoadingState() {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "正在加载相册和帖子...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemMediaPickerInlineNotice(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
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

private object SystemMediaRecentPostStore {
    private const val MaxRecentPosts = 8
    val ids = mutableStateListOf<String>()

    fun markUsed(postId: String) {
        if (postId.isBlank()) return
        ids.remove(postId)
        ids.add(0, postId)
        while (ids.size > MaxRecentPosts) {
            ids.removeAt(ids.lastIndex)
        }
    }
}

private fun formatSystemMediaPickerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}
