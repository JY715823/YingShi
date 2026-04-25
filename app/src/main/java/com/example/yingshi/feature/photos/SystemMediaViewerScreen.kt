package com.example.yingshi.feature.photos

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SystemMediaViewerScreen(
    route: SystemMediaViewerRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = route.initialIndex.coerceIn(0, (route.mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { route.mediaItems.size.coerceAtLeast(1) },
    )
    val currentItem = route.mediaItems.getOrNull(
        pagerState.currentPage.coerceIn(0, (route.mediaItems.size - 1).coerceAtLeast(0)),
    )
    var showAddToPostDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showMoveToTrashDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val albums = FakeAlbumRepository.getAlbums()
    val posts = FakeAlbumRepository.getPosts()

    if (showAddToPostDialog && currentItem != null) {
        SystemMediaPostDestinationDialog(
            albums = albums,
            posts = posts,
            onDismiss = { showAddToPostDialog = false },
            onPostSelected = { postId ->
                val addedCount = LocalSystemMediaBridgeRepository.addSystemMediaToExistingPost(
                    postId = postId,
                    mediaItems = listOf(currentItem),
                )
                showAddToPostDialog = false
                Toast.makeText(
                    context,
                    if (addedCount > 0) {
                        "已加入已有帖子，并同步进入照片页"
                    } else {
                        "该媒体已在目标帖子里"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (showMoveToTrashDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showMoveToTrashDialog = false },
            title = {
                Text(text = "确认移到系统回收站？")
            },
            text = {
                Text(text = "当前仅做本地模拟：会从系统媒体工具区隐藏，不会进入 app 回收站，也不会执行真实系统删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LocalSystemMediaBridgeRepository.moveToSimulatedSystemTrash(
                            listOf(currentItem.id),
                        )
                        showMoveToTrashDialog = false
                        Toast.makeText(context, "已从系统媒体工具区本地隐藏", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                ) {
                    Text(text = "移到系统回收站")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveToTrashDialog = false }) {
                    Text(text = "取消")
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E131A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SystemMediaViewerCircleButton(
                    text = "<",
                    onClick = onBack,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "系统媒体",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} / ${route.mediaItems.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            if (route.mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前没有可查看的系统媒体",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                    key = { page -> route.mediaItems[page].id },
                ) { page ->
                    val item = route.mediaItems[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (item.type == SystemMediaType.VIDEO) {
                            Surface(
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                                color = Color.Black.copy(alpha = 0.42f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            ) {
                                Text(
                                    text = "视频预览占位",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        currentItem?.let { item ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = YingShiThemeTokens.spacing.lg, vertical = YingShiThemeTokens.spacing.md),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        text = "${item.type.label} · ${formatSystemMediaViewerTime(item.displayTimeMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
                    ) {
                        SystemMediaViewerActionChip(
                            text = "发成新帖子",
                            onClick = {
                                val post = LocalSystemMediaBridgeRepository.createPostFromSystemMedia(
                                    listOf(item),
                                )
                                Toast.makeText(
                                    context,
                                    if (post != null) {
                                        "已发成新帖子，并同步进入照片页和相册页"
                                    } else {
                                        "当前媒体无法处理"
                                    },
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        SystemMediaViewerActionChip(
                            text = "加入已有帖子",
                            onClick = {
                                showAddToPostDialog = true
                            },
                        )
                        SystemMediaViewerActionChip(
                            text = "移到系统回收站",
                            onClick = {
                                showMoveToTrashDialog = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemMediaViewerCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerActionChip(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun SystemMediaViewerPostPickerDialog(
    posts: List<AlbumPostCardUiModel>,
    onDismiss: () -> Unit,
    onPostSelected: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "加入已有帖子")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                posts.forEach { post ->
                    Surface(
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.lg),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
                        onClick = { onPostSelected(post.id) },
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

private fun formatSystemMediaViewerTime(timeMillis: Long): String {
    return SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timeMillis))
}

@Preview(showBackground = true)
@Composable
private fun SystemMediaViewerScreenPreview() {
    YingShiTheme {
        SystemMediaViewerScreen(
            route = SystemMediaViewerRoute(
                mediaItems = emptyList(),
                initialIndex = 0,
            ),
            onBack = {},
        )
    }
}
