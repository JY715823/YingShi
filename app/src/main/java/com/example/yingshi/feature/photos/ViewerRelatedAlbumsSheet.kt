package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.cache.AppReadCacheStore
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerRelatedPostsSheet(
    posts: List<ViewerRelatedPostUiModel>,
    onSelectPost: (ViewerRelatedPostUiModel) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ViewerNightTop,
        contentColor = ViewerSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ViewerLayoutTuning.relatedPostsSheetHeightFraction)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "所属小相册",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ViewerSurface.copy(alpha = 0.94f),
            )
            if (posts.isEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
            } else {
                posts.forEach { post ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.lg))
                            .background(ViewerSurface.copy(alpha = 0.08f))
                            .clickable { onSelectPost(post) }
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = post.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = ViewerSurface.copy(alpha = 0.88f),
                            )
                            if (post.subtitle.isNotBlank()) {
                                ViewerRelatedAlbumTag(text = post.subtitle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerRelatedAlbumTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = ViewerAccent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, ViewerAccent.copy(alpha = 0.22f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ViewerSurface.copy(alpha = 0.86f),
            maxLines = 1,
        )
    }
}

internal fun buildViewerRelatedPosts(
    media: PhotoFeedItem,
    sourcePostRoute: PostDetailPlaceholderRoute?,
    routeOverrides: Map<String, PostDetailPlaceholderRoute> = emptyMap(),
    albumTitleById: Map<String, String> = emptyMap(),
): List<ViewerRelatedPostUiModel> {
    return media.postIds.distinct().map { postId ->
        val route = routeOverrides[postId] ?: buildViewerRelatedPostRoute(
            media = media,
            postId = postId,
            sourcePostRoute = sourcePostRoute,
        )
        ViewerRelatedPostUiModel(
            id = postId,
            title = route.title,
            subtitle = route.viewerRelatedAlbumLabel(albumTitleById),
            route = route,
        )
    }
}

internal fun SmallAlbumDetailRoute.viewerRelatedAlbumLabel(
    albumTitleById: Map<String, String>,
): String {
    val title = albumIds
        .asSequence()
        .mapNotNull { albumId ->
            albumTitleById[albumId]
                ?: FakeAlbumRepository.getAlbum(albumId)?.title
        }
        .firstOrNull()
    return title?.let { "大相册 $it" }.orEmpty()
}

internal fun buildCachedViewerRelatedPostRoutes(
    postIds: List<String>,
    sourcePostRoute: PostDetailPlaceholderRoute?,
): Map<String, PostDetailPlaceholderRoute> {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return emptyMap()
    val cachedDirectory = AppReadCacheStore.readAlbumDirectory(userId)?.payload ?: return emptyMap()
    val summariesById = cachedDirectory.postsByAlbumId.values
        .flatten()
        .associateBy { it.postId }
    return postIds.mapNotNull { postId ->
        if (sourcePostRoute?.postId == postId) {
            postId to sourcePostRoute
        } else {
            val summary = summariesById[postId] ?: return@mapNotNull null
            postId to summary.toPostDetailPlaceholderRoute(
                selectedAlbumId = summary.albumIds.firstOrNull().orEmpty().ifBlank {
                    summary.albumId
                },
            )
        }
    }.toMap()
}

internal fun buildCachedViewerAlbumTitleMap(): Map<String, String> {
    val userId = AuthSessionManager.getCurrentUserSnapshot()?.userId ?: return emptyMap()
    return AppReadCacheStore.readAlbumDirectory(userId)
        ?.payload
        ?.albums
        .orEmpty()
        .associate { it.albumId to it.title }
}

internal fun buildViewerRelatedPostRoute(
    media: PhotoFeedItem,
    postId: String,
    sourcePostRoute: PostDetailPlaceholderRoute?,
): PostDetailPlaceholderRoute {
    if (sourcePostRoute?.postId == postId) {
        return sourcePostRoute
    }
    if (RepositoryProvider.currentMode == RepositoryMode.FAKE) {
        FakeAlbumRepository.getPost(postId)?.let { post ->
            return FakeAlbumRepository.toPostDetailRoute(post)
        }
    }
    val fallbackTitle = sourcePostRoute
        ?.takeIf { it.postId == postId }
        ?.title
        ?: "未命名小相册"
    val fallbackSummary = sourcePostRoute
        ?.takeIf { it.postId == postId }
        ?.summary
        ?: ""
    return PostDetailPlaceholderRoute(
        postId = postId,
        albumId = sourcePostRoute?.albumId ?: "viewer-related",
        albumIds = sourcePostRoute?.albumIds ?: listOf("viewer-related"),
        title = fallbackTitle,
        summary = fallbackSummary,
        postDisplayTimeMillis = media.mediaDisplayTimeMillis,
        mediaCount = 0,
        coverPalette = media.palette,
        coverMediaType = media.mediaType,
        coverAspectRatio = media.aspectRatio,
    )
}
