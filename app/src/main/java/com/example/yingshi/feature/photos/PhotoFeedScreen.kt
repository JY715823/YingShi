package com.example.yingshi.feature.photos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PhotoFeedLeadingItemCount = 0
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
        feedItems.mapIndexed { index, item -> item.mediaId to index }.toMap()
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
    val scrollAnchors = remember(blocks, density) {
        buildPhotoFeedScrollAnchors(
            blocks = blocks,
            density = density,
            leadingItemCount = PhotoFeedLeadingItemCount,
        )
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var lastRequestedAnchorIndex by remember { mutableIntStateOf(-1) }
    val currentVisibleDateLabel by remember(listState, blocks, feedItems) {
        derivedStateOf {
            resolveCurrentVisibleDateLabel(
                itemIndex = listState.firstVisibleItemIndex,
                blocks = blocks,
                fallbackItems = feedItems,
            )
        }
    }
    val currentScrollProgress by remember(listState, scrollAnchors) {
        derivedStateOf {
            calculatePhotoFeedScrollProgress(
                listState = listState,
                anchorCount = scrollAnchors.size,
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

    LaunchedEffect(listState.isScrollInProgress, scrubberInteracting, scrollAnchors.size) {
        if (scrollAnchors.size <= 1) {
            scrubberVisible = false
            return@LaunchedEffect
        }

        if (listState.isScrollInProgress || scrubberInteracting) {
            scrubberVisible = true
        } else {
            delay(700)
            if (!listState.isScrollInProgress && !scrubberInteracting) {
                scrubberVisible = false
            }
        }
    }

    LaunchedEffect(currentScrollProgress, scrubberInteracting, scrollAnchors.size) {
        if (!scrubberInteracting) {
            lastRequestedAnchorIndex = if (scrollAnchors.isEmpty()) {
                -1
            } else {
                (currentScrollProgress * scrollAnchors.lastIndex)
                    .roundToInt()
                    .coerceIn(0, scrollAnchors.lastIndex)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PhotoFeedToolbar(
            mediaCount = feedItems.size,
            selectedCount = selectionState.selectedCount,
            selectedDensity = density,
            enabled = !selectionState.isInSelectionMode,
            onDensitySelected = updateDensity,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                items(
                    items = blocks,
                    key = { it.key },
                    contentType = { block ->
                        when (block) {
                            is PhotoFeedSectionHeader -> "section"
                            is PhotoFeedDayHeader -> "day"
                            is PhotoFeedGridRow -> "grid-${density.columns}"
                        }
                    },
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
                                                sourceLabel = "photos-feed",
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

            androidx.compose.animation.AnimatedVisibility(
                visible = scrubberVisible && scrollAnchors.size > 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = spacing.xs),
            ) {
                PhotoFeedTimeScrubber(
                    modifier = Modifier.fillMaxHeight(),
                    progress = currentScrollProgress,
                    label = currentVisibleDateLabel,
                    showLabel = scrubberInteracting || listState.isScrollInProgress,
                    onSeekToProgress = { progress ->
                        if (scrollAnchors.isEmpty()) {
                            return@PhotoFeedTimeScrubber
                        }
                        val anchorIndex = (progress * scrollAnchors.lastIndex)
                            .roundToInt()
                            .coerceIn(0, scrollAnchors.lastIndex)
                        if (anchorIndex == lastRequestedAnchorIndex) {
                            return@PhotoFeedTimeScrubber
                        }
                        scrollAnchors.getOrNull(anchorIndex)?.let { anchor ->
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
                looksLikeVideoSource(target.url, target.mimeType)
            ) {
                prefetchVideoPoster(
                    context = context,
                    url = target.url,
                    accessToken = accessToken,
                )
                return@forEach
            }
            backendMediaImageRequest(
                context = context,
                url = target.url,
                accessToken = accessToken,
                memoryCacheKey = sharedPreviewMemoryCacheKey(target.url),
                size = 512,
            )?.let(imageLoader::enqueue)
        }
    }
}

private data class PrefetchTarget(
    val url: String,
    val mediaType: AppMediaType,
    val mimeType: String?,
)

private data class PhotoFeedScrollAnchor(
    val itemIndex: Int,
    val label: String,
)

@Composable
private fun PhotoFeedToolbar(
    mediaCount: Int,
    selectedCount: Int,
    selectedDensity: PhotoFeedDensity,
    enabled: Boolean,
    onDensitySelected: (PhotoFeedDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.capsule),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selectedCount > 0) {
                    "已选 $selectedCount"
                } else {
                    "$mediaCount 项"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhotoFeedDensitySwitcher(
                selectedDensity = selectedDensity,
                enabled = enabled,
                onDensitySelected = onDensitySelected,
            )
        }
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoFeedDensity.entries.forEach { density ->
            val selected = density == selectedDensity
            val backgroundColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                selected -> Color.White
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            }
            val textColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                selected -> Color.Black
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.capsule))
                    .background(backgroundColor)
                    .clickable(enabled = enabled) { onDensitySelected(density) }
                    .padding(horizontal = spacing.xs, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = density.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun PhotoFeedTimeScrubber(
    progress: Float,
    label: String,
    showLabel: Boolean,
    onSeekToProgress: (Float) -> Unit,
    onInteractingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val spacing = YingShiThemeTokens.spacing
    val thumbWidth = 6.dp
    val thumbHeight = 34.dp
    val verticalPadding = 18.dp
    val touchWidth = 22.dp
    var scrubberHeightPx by remember { mutableIntStateOf(0) }
    var labelHeightPx by remember { mutableIntStateOf(0) }
    var lastDispatchedProgress by remember { mutableStateOf(Float.NaN) }
    val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
    val thumbHeightPx = with(density) { thumbHeight.roundToPx() }
    val travelHeightPx = (scrubberHeightPx - (verticalPaddingPx * 2) - thumbHeightPx).coerceAtLeast(1)
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val thumbTopPx = verticalPaddingPx + (travelHeightPx * normalizedProgress).roundToInt()
    val labelTopPx = (thumbTopPx + (thumbHeightPx / 2) - (labelHeightPx / 2))
        .coerceIn(0, (scrubberHeightPx - labelHeightPx).coerceAtLeast(0))

    fun dispatchProgress(offsetY: Float) {
        if (scrubberHeightPx <= 0) return
        val nextProgress = ((offsetY - verticalPaddingPx - (thumbHeightPx / 2f)) / travelHeightPx.toFloat())
            .coerceIn(0f, 1f)
        if (!lastDispatchedProgress.isNaN() && abs(lastDispatchedProgress - nextProgress) < 0.01f) {
            return
        }
        lastDispatchedProgress = nextProgress
        onSeekToProgress(nextProgress)
    }

    Box(
        modifier = modifier
            .width(touchWidth)
            .onSizeChanged { scrubberHeightPx = it.height }
            .pointerInput(Unit) {
                detectTapGestures { offset: Offset ->
                    onInteractingChanged(true)
                    dispatchProgress(offset.y)
                    lastDispatchedProgress = Float.NaN
                    onInteractingChanged(false)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onInteractingChanged(true)
                        dispatchProgress(offset.y)
                    },
                    onVerticalDrag = { change, _ ->
                        dispatchProgress(change.position.y)
                    },
                    onDragEnd = {
                        lastDispatchedProgress = Float.NaN
                        onInteractingChanged(false)
                    },
                    onDragCancel = {
                        lastDispatchedProgress = Float.NaN
                        onInteractingChanged(false)
                    },
                )
            },
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel && label.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -72, y = labelTopPx) },
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.96f),
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .padding(horizontal = spacing.sm, vertical = 6.dp)
                        .onSizeChanged { labelHeightPx = it.height },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = verticalPadding)
                .offset { IntOffset(x = 0, y = thumbTopPx - verticalPaddingPx) }
                .size(width = thumbWidth, height = thumbHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.96f)),
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
            showLoadingIndicator = false,
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
                text = "v",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

private fun buildPhotoFeedScrollAnchors(
    blocks: List<PhotoFeedBlock>,
    density: PhotoFeedDensity,
    leadingItemCount: Int,
): List<PhotoFeedScrollAnchor> {
    if (blocks.isEmpty()) return emptyList()

    val anchors = blocks.mapIndexedNotNull { index, block ->
        val row = block as? PhotoFeedGridRow ?: return@mapIndexedNotNull null
        val item = row.items.firstOrNull() ?: return@mapIndexedNotNull null
        PhotoFeedScrollAnchor(
            itemIndex = leadingItemCount + index,
            label = item.toScrubberLabel(),
        )
    }

    return if (density.columns >= 16) {
        anchors.filterIndexed { index, _ -> index % 2 == 0 || index == anchors.lastIndex }
    } else {
        anchors
    }
}

private fun resolveCurrentVisibleDateLabel(
    itemIndex: Int,
    blocks: List<PhotoFeedBlock>,
    fallbackItems: List<PhotoFeedItem>,
): String {
    if (blocks.isEmpty()) {
        return fallbackItems.firstOrNull()?.toScrubberLabel().orEmpty()
    }

    val safeIndex = itemIndex.coerceIn(0, blocks.lastIndex)
    val nextRow = blocks
        .drop(safeIndex)
        .firstOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow
    val previousRow = blocks
        .take(safeIndex + 1)
        .lastOrNull { it is PhotoFeedGridRow } as? PhotoFeedGridRow

    return nextRow?.items?.firstOrNull()?.toScrubberLabel()
        ?: previousRow?.items?.firstOrNull()?.toScrubberLabel()
        ?: fallbackItems.firstOrNull()?.toScrubberLabel()
        ?: ""
}

private fun calculatePhotoFeedScrollProgress(
    listState: LazyListState,
    anchorCount: Int,
): Float {
    if (anchorCount <= 1) return 0f

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 1) return 0f

    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val offsetFraction = ((-firstVisible.offset).toFloat() / maxOf(firstVisible.size, viewportHeight).toFloat())
        .coerceIn(0f, 1f)

    return ((listState.firstVisibleItemIndex + offsetFraction) / (totalItems - 1).toFloat())
        .coerceIn(0f, 1f)
}

private fun PhotoFeedItem.toScrubberLabel(): String {
    return "%04d.%02d.%02d".format(displayYear, displayMonth, displayDay)
}

private fun sectionSpacing(density: PhotoFeedDensity): Dp {
    return when {
        density.columns <= 4 -> 10.dp
        density.columns == 8 -> 8.dp
        else -> 6.dp
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
