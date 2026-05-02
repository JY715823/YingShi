package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PhotoFeedLeadingItemCount = 2
private const val PhotoFeedPrefetchCount = 36

@Composable
fun PhotoFeedScreen(
    feedItems: List<PhotoFeedItem> = FakePhotoFeedRepository.getPhotoFeed(),
    modifier: Modifier = Modifier,
    selectionState: PhotoFeedSelectionState = PhotoFeedSelectionState(),
    bottomOverlayPadding: Dp = 0.dp,
    onSelectionStateChange: (PhotoFeedSelectionState) -> Unit = { },
    onOpenViewer: (PhotoViewerRoute) -> Unit = { },
) {
    val spacing = YingShiThemeTokens.spacing
    val settingsState = FakeSettingsRepository.getSettingsState()
    PrefetchPhotoFeedThumbnails(feedItems)
    val mediaPositionLookup = remember(feedItems) {
        feedItems.mapIndexed { index, item ->
            item.mediaId to index
        }.toMap()
    }
    var densityName by rememberSaveable { mutableStateOf<String?>(null) }
    var scrubberVisible by remember { mutableStateOf(false) }
    var scrubberInteracting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (densityName == null) {
            densityName = PhotoFeedDensity.DENSE_4.name
        }
    }
    val density = PhotoFeedDensity.valueOf(
        densityName ?: settingsState.defaultPhotoFeedDensity.name,
    )
    val blocks = remember(feedItems, density) {
        buildPhotoFeedBlocks(
            items = feedItems,
            density = density,
        )
    }
    val scrubberAnchors = remember(blocks, density) {
        buildPhotoFeedScrubberAnchors(
            blocks = blocks,
            density = density,
            leadingItemCount = PhotoFeedLeadingItemCount,
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var lastRequestedAnchorIndex by remember { mutableIntStateOf(-1) }
    val currentScrubberAnchorIndex by remember(listState, scrubberAnchors) {
        derivedStateOf {
            resolveCurrentScrubberAnchorIndex(
                itemIndex = listState.firstVisibleItemIndex,
                anchors = scrubberAnchors,
            )
        }
    }
    val updateDensity = remember(density) {
        { nextDensity: PhotoFeedDensity ->
            if (nextDensity != density) {
                densityName = nextDensity.name
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, scrubberInteracting, scrubberAnchors.isNotEmpty()) {
        if (scrubberAnchors.isEmpty()) {
            scrubberVisible = false
            return@LaunchedEffect
        }

        if (listState.isScrollInProgress || scrubberInteracting) {
            scrubberVisible = true
        } else {
            delay(900)
            if (!listState.isScrollInProgress && !scrubberInteracting) {
                scrubberVisible = false
            }
        }
    }

    LaunchedEffect(currentScrubberAnchorIndex, scrubberInteracting) {
        if (!scrubberInteracting) {
            lastRequestedAnchorIndex = currentScrubberAnchorIndex
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .discreteZoomLevelGesture(
                    enabled = !selectionState.isInSelectionMode,
                    levels = PhotoFeedDensity.entries.toList(),
                    currentLevel = density,
                    onLevelChange = updateDensity,
                ),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing(density)),
            contentPadding = PaddingValues(
                top = spacing.xs,
                bottom = spacing.xxl + bottomOverlayPadding,
            ),
        ) {
            item(key = "feed-summary") {
                PhotoFeedSummary(
                    mediaCount = feedItems.size,
                    selectedCount = selectionState.selectedCount,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "density-switcher") {
                PhotoFeedDensitySwitcher(
                    selectedDensity = density,
                    enabled = !selectionState.isInSelectionMode,
                    onDensitySelected = updateDensity,
                )
            }

            items(
                items = blocks,
                key = { it.key },
            ) { block ->
                when (block) {
                    is PhotoFeedSectionHeader -> PhotoFeedSectionHeaderRow(title = block.title)
                    is PhotoFeedDayHeader -> PhotoFeedDayHeaderRow(title = block.title)
                    is PhotoFeedGridRow -> PhotoFeedGridRowContent(
                        row = block,
                        density = density,
                        selectionState = selectionState,
                        onMediaClick = { item ->
                            onSelectionStateChange(
                                if (selectionState.isInSelectionMode) {
                                    selectionState.toggle(item.mediaId)
                                } else {
                                    onOpenViewer(
                                        PhotoViewerRoute(
                                            mediaItems = feedItems,
                                            initialIndex = mediaPositionLookup[item.mediaId] ?: 0,
                                            sourceLabel = "照片页全局媒体流",
                                            showPostSegments = false,
                                        ),
                                    )
                                    selectionState
                                },
                            )
                        },
                        onMediaLongPress = { item ->
                            onSelectionStateChange(
                                if (selectionState.isInSelectionMode) {
                                    selectionState.toggle(item.mediaId)
                                } else {
                                    selectionState.enterWith(item.mediaId)
                                },
                            )
                        },
                    )
                }
            }
        }
        }

        AnimatedVisibility(
            visible = scrubberVisible && scrubberAnchors.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = spacing.xs),
        ) {
            PhotoFeedTimeScrubber(
                modifier = Modifier.fillMaxHeight(),
                anchors = scrubberAnchors,
                currentAnchorIndex = currentScrubberAnchorIndex,
                onSeekToAnchor = { anchorIndex ->
                    if (anchorIndex == lastRequestedAnchorIndex) {
                        return@PhotoFeedTimeScrubber
                    }
                    scrubberAnchors.getOrNull(anchorIndex)?.let { anchor ->
                        lastRequestedAnchorIndex = anchorIndex
                        coroutineScope.launch {
                            listState.scrollToItem(anchor.itemIndex)
                        }
                    }
                },
                onInteractingChanged = { scrubberInteracting = it },
            )
        }
    }
}

@Composable
private fun PrefetchPhotoFeedThumbnails(feedItems: List<PhotoFeedItem>) {
    if (RepositoryProvider.currentMode != RepositoryMode.REAL) return

    val context = LocalContext.current
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.getAccessToken()?.takeIf { it.isNotBlank() }
    }
    val prefetchTargets = remember(feedItems) {
        feedItems
            .take(PhotoFeedPrefetchCount)
            .mapNotNull { item ->
                val url = item.mediaSource.thumbnailModelUrl(item.mediaType) ?: return@mapNotNull null
                PrefetchTarget(
                    url = url,
                    mediaType = item.mediaType,
                    mimeType = item.mediaSource?.mimeType,
                )
            }
            .distinctBy { "${it.mediaType}:${it.url}" }
    }

    LaunchedEffect(context, prefetchTargets, accessToken) {
        val imageLoader = context.imageLoader
        prefetchTargets.forEach { target ->
            if (target.mediaType == AppMediaType.VIDEO &&
                (target.mimeType?.startsWith("video/", ignoreCase = true) == true || target.url.isLikelyVideoUrl())
            ) {
                prefetchVideoPoster(
                    context = context,
                    url = target.url,
                    accessToken = accessToken,
                )
                return@forEach
            }
            imageLoader.enqueue(
                ImageRequest.Builder(context).apply {
                    data(target.url)
                    size(512)
                    precision(Precision.INEXACT)
                    crossfade(false)
                    memoryCacheKey(sharedPreviewMemoryCacheKey(target.url))
                    diskCacheKey("media:${target.url}")
                    networkCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    accessToken
                        ?.takeIf { target.url.startsWith("http", ignoreCase = true) }
                        ?.let { token -> addHeader("Authorization", "${RemoteConfig.AUTH_SCHEME} $token") }
                }.build(),
            )
        }
    }
}

private data class PrefetchTarget(
    val url: String,
    val mediaType: AppMediaType,
    val mimeType: String?,
)

private fun String.isLikelyVideoUrl(): Boolean {
    val normalized = substringBefore('?').substringBefore('#').lowercase()
    return normalized.endsWith(".mp4") ||
        normalized.endsWith(".mov") ||
        normalized.endsWith(".m4v") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".3gp") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".avi")
}

@Composable
private fun PhotoFeedSummary(
    mediaCount: Int,
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryTag(text = "全局媒体流")
        SummaryTag(text = "最新优先")
        Text(
            text = if (selectedCount > 0) {
                "多选中 $selectedCount 项"
            } else {
                "已去重 $mediaCount 项媒体"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryTag(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhotoFeedDensitySwitcher(
    selectedDensity: PhotoFeedDensity,
    enabled: Boolean,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
        tonalElevation = if (enabled) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            PhotoFeedDensity.entries.forEach { density ->
                val selected = density == selectedDensity
                val containerColor = when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }

                TextButton(
                    onClick = { onDensitySelected(density) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.capsule))
                            .background(containerColor)
                            .padding(horizontal = spacing.xs, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = density.label,
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (selected && enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedTimeScrubber(
    anchors: List<PhotoFeedScrubberAnchor>,
    currentAnchorIndex: Int,
    onSeekToAnchor: (Int) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val density = LocalDensity.current
    val railWidth = 28.dp
    val thumbSize = 18.dp
    val verticalPadding = 18.dp
    var scrubberHeightPx by remember { mutableIntStateOf(0) }
    var bubbleHeightPx by remember { mutableIntStateOf(0) }
    var lastDispatchedAnchorIndex by remember(anchors) { mutableIntStateOf(-1) }
    val safeAnchorIndex = currentAnchorIndex.coerceIn(0, (anchors.size - 1).coerceAtLeast(0))
    val currentLabel = anchors.getOrNull(safeAnchorIndex)?.label.orEmpty()
    val thumbFraction = if (anchors.size <= 1) {
        0f
    } else {
        safeAnchorIndex.toFloat() / anchors.lastIndex.toFloat()
    }
    val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
    val thumbSizePx = with(density) { thumbSize.roundToPx() }
    val travelHeightPx = (scrubberHeightPx - (verticalPaddingPx * 2) - thumbSizePx).coerceAtLeast(1)
    val thumbTopPx = verticalPaddingPx + (travelHeightPx * thumbFraction).roundToInt()
    val bubbleTopPx = (thumbTopPx + (thumbSizePx / 2) - (bubbleHeightPx / 2))
        .coerceIn(0, (scrubberHeightPx - bubbleHeightPx).coerceAtLeast(0))

    fun seekByOffset(offsetY: Float) {
        if (anchors.isEmpty() || scrubberHeightPx <= 0) return

        val fraction = ((offsetY - verticalPaddingPx - (thumbSizePx / 2f)) / travelHeightPx.toFloat())
            .coerceIn(0f, 1f)
        val anchorIndex = (fraction * anchors.lastIndex).roundToInt().coerceIn(0, anchors.lastIndex)
        if (anchorIndex == lastDispatchedAnchorIndex) return
        lastDispatchedAnchorIndex = anchorIndex
        onSeekToAnchor(anchorIndex)
    }

    Box(
        modifier = modifier
            .width(104.dp)
            .onSizeChanged { scrubberHeightPx = it.height }
            .pointerInput(anchors) {
                detectTapGestures { offset: Offset ->
                    onInteractingChanged(true)
                    seekByOffset(offset.y)
                    lastDispatchedAnchorIndex = -1
                    onInteractingChanged(false)
                }
            }
            .pointerInput(anchors) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onInteractingChanged(true)
                        seekByOffset(offset.y)
                    },
                    onVerticalDrag = { change, _ ->
                        seekByOffset(change.position.y)
                    },
                    onDragEnd = {
                        lastDispatchedAnchorIndex = -1
                        onInteractingChanged(false)
                    },
                    onDragCancel = {
                        lastDispatchedAnchorIndex = -1
                        onInteractingChanged(false)
                    },
                )
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(x = 0, y = bubbleTopPx) }
                .onSizeChanged { bubbleHeightPx = it.height },
            shape = RoundedCornerShape(radius.capsule),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
            ),
        ) {
            Text(
                text = currentLabel,
                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = verticalPadding, bottom = verticalPadding, end = spacing.xxs)
                .width(railWidth)
                .clip(RoundedCornerShape(radius.capsule))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                .border(
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                    ),
                    shape = RoundedCornerShape(radius.capsule),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(2.dp)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = verticalPadding, end = spacing.xxs + 5.dp)
                .offset { IntOffset(x = 0, y = thumbTopPx - verticalPaddingPx) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)),
        )
    }
}

@Composable
private fun PhotoFeedSectionHeaderRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun PhotoFeedDayHeaderRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PhotoFeedGridRowContent(
    row: PhotoFeedGridRow,
    density: PhotoFeedDensity,
    selectionState: PhotoFeedSelectionState,
    onMediaClick: (PhotoFeedItem) -> Unit,
    onMediaLongPress: (PhotoFeedItem) -> Unit,
) {
    val spacing = rowSpacing(density)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        row.items.forEach { item ->
            PhotoFeedCard(
                item = item,
                density = density,
                isInSelectionMode = selectionState.isInSelectionMode,
                isSelected = selectionState.contains(item.mediaId),
                modifier = Modifier.weight(1f),
                onClick = { onMediaClick(item) },
                onLongPress = { onMediaLongPress(item) },
            )
        }

        repeat(density.columns - row.items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoFeedCard(
    item: PhotoFeedItem,
    density: PhotoFeedDensity,
    isInSelectionMode: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        AppContentMediaThumbnail(
            mediaSource = item.mediaSource,
            mediaType = item.mediaType,
            palette = item.palette,
            modifier = Modifier.matchParentSize(),
            contentDescription = item.mediaId,
        )

        if (isInSelectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            Color.Black.copy(alpha = 0.12f)
                        },
                ),
            )
        }

        if (isInSelectionMode) {
            SelectionBadge(
                selected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.55f)
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

private fun sectionSpacing(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 4 -> 14.dp
        density.columns == 8 -> 12.dp
        else -> 10.dp
    }
}

private fun rowSpacing(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 4 -> 1.dp
        density.columns <= 8 -> 1.dp
        else -> 1.dp
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoFeedScreenPreview() {
    YingShiTheme {
        PhotoFeedScreen(
            selectionState = PhotoFeedSelectionState(),
        )
    }
}
