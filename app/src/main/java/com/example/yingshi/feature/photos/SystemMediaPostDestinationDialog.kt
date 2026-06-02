package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
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
            postCount = posts.count { post -> post.albumId == album.id },
        )
    }
    val postsInSelectedAlbum = selectedAlbum?.let { album ->
        posts.filter { post -> post.albumId == album.id }
            .sortedByDescending { it.postDisplayTimeMillis }
    }.orEmpty()
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius

    AlertDialog(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        containerColor = colors.raisedSurface,
        titleContentColor = colors.titleAccent,
        textContentColor = colors.textSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = if (selectedAlbum == null) "选择大相册" else "选择小相册")
                Text(
                    text = selectedAlbum?.title ?: "先选择一个大相册，再选择目标小相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
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
                            text = "按大相册选择",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                        albumCards.forEach { choice ->
                            Surface(
                                shape = RoundedCornerShape(radius.lg),
                                color = colors.sectionBackground.copy(alpha = 0.68f),
                                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
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
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = "${choice.postCount} 个小相册",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                } else if (postsInSelectedAlbum.isEmpty()) {
                    SystemMediaPickerEmptyState(text = "该大相册下还没有小相册。")
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
                TrashDialogActionButton(
                    text = "返回上一级",
                    enabled = !isSubmitting,
                    onClick = { selectedAlbumId = null },
                )
            }
        },
        dismissButton = {
            TrashDialogActionButton(
                text = "取消",
                enabled = !isSubmitting,
                onClick = onDismiss,
            )
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
    val colors = YingShiThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "最近小相册",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        if (posts.isEmpty()) {
            SystemMediaPickerEmptyState(text = "还没有最近小相册，可从下方大相册选择。")
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
    val colors = YingShiThemeTokens.colors
    val albumLabel = albumTitleById[post.albumId].orEmpty()
        .ifBlank { "未归档相册" }
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.68f)),
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
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.summary.ifBlank { "还没有简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$albumLabel · ${post.mediaCount} 项媒体 · ${formatSystemMediaPickerTime(post.postDisplayTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
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
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
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
                color = colors.titleAccent,
                trackColor = colors.sectionBackground,
            )
            Text(
                text = "正在加载相册…",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SystemMediaPickerInlineNotice(
    text: String,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.md),
        color = colors.memoryContainer.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.18f)),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onMemoryContainer,
        )
    }
}

@Composable
private fun SystemMediaPickerEmptyState(
    text: String,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
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
                color = colors.textSecondary,
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
