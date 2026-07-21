package com.example.yingshi.feature.photos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

internal sealed interface SystemMediaGridBlock {
    val key: String

    data class MonthHeader(
        override val key: String,
        val title: String,
    ) : SystemMediaGridBlock

    data class DayHeader(
        override val key: String,
        val title: String,
    ) : SystemMediaGridBlock

    data class Media(
        override val key: String,
        val item: SystemMediaItem,
    ) : SystemMediaGridBlock
}

internal data class SystemMediaDensityGhost(
    val density: PhotoFeedDensity,
    val blocks: List<SystemMediaGridBlock>,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val nonce: Long,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SystemMediaCard(
    item: SystemMediaItem,
    modifier: Modifier = Modifier,
    density: PhotoFeedDensity,
    thumbnailRequestSize: Int,
    selectionMode: Boolean,
    selected: Boolean,
    selectionFlash: SelectionNumberFlash?,
    inlineVideoAutoPlayEnabled: Boolean,
    isInlineVideoPlaying: Boolean,
    isInlineVideoActive: Boolean,
    isInlineVideoPaused: Boolean,
    inlineVideoProgress: InlineVideoPlaybackProgress?,
    onClick: () -> Unit,
    onOpenMedia: () -> Unit,
    onLongPress: () -> Unit,
    onToggleInlineVideo: () -> Unit,
    onInlineVideoProgressChange: (InlineVideoPlaybackProgress) -> Unit,
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val motion = YingShiThemeTokens.motion
    val motionEnabled = rememberYingShiMotionEnabled()
    val isOverview16 = density == PhotoFeedDensity.OVERVIEW_16
    val shape = RoundedCornerShape(0.dp)
    val videoThumbnail = if (item.type == SystemMediaType.VIDEO && density.columns <= 4) {
        rememberSystemVideoThumbnail(context, item.uri)
    } else {
        null
    }
    val mediaThumbnail = if (videoThumbnail == null) {
        rememberSystemMediaThumbnail(
            context = context,
            uri = item.uri,
            targetSizePx = thumbnailRequestSize,
        )
    } else {
        null
    }
    val selectionHotspotOnly = selectionMode && density.columns in 2..4
    val supportsInlineVideo = inlineVideoAutoPlayEnabled &&
        !selectionMode &&
        item.type == SystemMediaType.VIDEO &&
        density.columns <= 4
    val showSelectionVideoMarker = selectionMode &&
        item.type == SystemMediaType.VIDEO &&
        density.columns <= 4
    val itemScale by animateFloatAsState(
        targetValue = if (selected && !isOverview16) motion.selectedMediaScale else 1f,
        animationSpec = tween(if (motionEnabled && !isOverview16) motion.stateMillis else 0, easing = motion.easing),
        label = "systemMediaCardSelectionScale",
    )
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (videoThumbnail != null || mediaThumbnail != null) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled && !isOverview16) 90 else 0,
            easing = motion.easing,
        ),
        label = "systemMediaThumbnailAlpha",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(shape)
            .background(systemMediaCardBackground(item = item, density = density))
            .combinedClickable(
                onClick = if (selectionHotspotOnly) onOpenMedia else onClick,
                onLongClick = onLongPress,
            ),
    ) {
        if (videoThumbnail != null) {
            Image(
                bitmap = videoThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = thumbnailAlpha },
                contentScale = ContentScale.Crop,
            )
        } else if (mediaThumbnail != null) {
            Image(
                bitmap = mediaThumbnail.toComposeBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = thumbnailAlpha },
                contentScale = ContentScale.Crop,
            )
        }

        if (!isOverview16) {
            YingShiMediaFrame(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                selected = selected,
                topScrimAlpha = if (item.type == SystemMediaType.VIDEO) 0.22f else 0.14f,
                bottomGlowAlpha = if (selected) 0.30f else 0.14f,
                selectedBorderColor = colors.viewerAccent.copy(alpha = 0.58f),
            )
        }

        if (supportsInlineVideo && isInlineVideoPlaying) {
            SystemMediaInlineVideoPlayer(
                item = item,
                playWhenReady = true,
                modifier = Modifier.fillMaxSize(),
                onPlaybackProgressChange = onInlineVideoProgressChange,
            )
        }

        if (supportsInlineVideo) {
            InlineVideoPlaybackButton(
                isPlaying = isInlineVideoActive && !isInlineVideoPaused,
                onClick = onToggleInlineVideo,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }

        if (showSelectionVideoMarker) {
            InlineVideoPlaybackButton(
                isPlaying = false,
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }

        if (item.type == SystemMediaType.VIDEO && !isOverview16) {
            VideoDurationBadge(
                durationMillis = item.gridVideoBadgeDurationMillis(inlineVideoProgress),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp),
            )
        }

        if (item.isImportedToApp && !isOverview16) {
            SystemMediaBadge(
                text = "已导入",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp, start = 6.dp),
            )
        }

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.viewerBackground.copy(alpha = if (selected) 0.18f else 0.06f)),
            )
            if (selectionHotspotOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(46.dp)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    SystemMediaSelectionBadge(
                        selected = selected,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    )
                }
            } else {
                SystemMediaSelectionBadge(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp),
                )
            }
        }
        if (selectionMode && !isOverview16) {
            SystemMediaSelectionNumberFlashOverlay(
                flash = selectionFlash,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private fun SystemMediaItem.gridVideoBadgeDurationMillis(
    progress: InlineVideoPlaybackProgress?,
): Long? {
    val totalMillis = progress?.durationMillis ?: videoDurationMillis
    if (totalMillis == null || totalMillis <= 0L) return null
    val positionMillis = progress?.positionMillis ?: 0L
    return (totalMillis - positionMillis).coerceIn(0L, totalMillis)
}

@Composable
internal fun SystemMediaBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = colors.memoryContainer.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colors.memoryAccent.copy(alpha = 0.28f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onMemoryContainer,
        )
    }
}

internal fun systemMediaCardBackground(
    item: SystemMediaItem,
    density: PhotoFeedDensity,
): Brush {
    val alpha = if (density == PhotoFeedDensity.OVERVIEW_16) 0.82f else 0.62f
    return Brush.linearGradient(
        colors = listOf(
            item.palette.start.copy(alpha = alpha),
            item.palette.end.copy(alpha = (alpha + 0.08f).coerceAtMost(0.92f)),
        ),
    )
}

@Composable
internal fun SystemMediaMonthHeaderRow(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = YingShiThemeTokens.colors.titleAccent,
    )
}

@Composable
internal fun SystemMediaDayHeaderRow(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 6.dp, bottom = 5.dp),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = YingShiThemeTokens.colors.textPrimary.copy(alpha = 0.88f),
    )
}

internal fun systemMediaGridEdgePadding(density: PhotoFeedDensity) = rowSpacing(density)

internal fun systemMediaThumbnailRequestSize(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 512
        PhotoFeedDensity.COMFORT_3 -> 384
        PhotoFeedDensity.DENSE_4 -> 288
        PhotoFeedDensity.OVERVIEW_8 -> 144
        PhotoFeedDensity.OVERVIEW_16 -> 96
    }
}

internal fun systemMediaThumbnailWarmWindow(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 18
        PhotoFeedDensity.COMFORT_3 -> 24
        PhotoFeedDensity.DENSE_4 -> 36
        PhotoFeedDensity.OVERVIEW_8 -> 120
        PhotoFeedDensity.OVERVIEW_16 -> 240
    }
}

internal fun systemMediaThumbnailWarmBatchSize(density: PhotoFeedDensity): Int {
    return when (density) {
        PhotoFeedDensity.COMFORT_2 -> 6
        PhotoFeedDensity.COMFORT_3 -> 8
        PhotoFeedDensity.DENSE_4 -> 10
        PhotoFeedDensity.OVERVIEW_8 -> 14
        PhotoFeedDensity.OVERVIEW_16 -> 10
    }
}

internal fun buildSystemMediaGridBlocks(
    items: List<SystemMediaItem>,
    density: PhotoFeedDensity,
): List<SystemMediaGridBlock> {
    if (items.isEmpty()) return emptyList()
    val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)
    val yearFormat = SimpleDateFormat("yyyy年", Locale.CHINA)
    val yearKeyFormat = SimpleDateFormat("yyyy", Locale.CHINA)
    val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    val blocks = mutableListOf<SystemMediaGridBlock>()
    var lastMonthKey: String? = null
    var lastYearKey: String? = null
    var lastDayKey: String? = null

    items.forEach { item ->
        val date = Date(item.displayTimeMillis)
        if (density.columns >= 16) {
            val yearKey = yearKeyFormat.format(date)
            if (yearKey != lastYearKey) {
                blocks += SystemMediaGridBlock.MonthHeader(
                    key = "system-year-$yearKey",
                    title = yearFormat.format(date),
                )
                lastYearKey = yearKey
            }
            blocks += SystemMediaGridBlock.Media(
                key = "system-media-${item.id}",
                item = item,
            )
            return@forEach
        }
        val monthKey = monthKeyFormat.format(date)
        if (monthKey != lastMonthKey) {
            blocks += SystemMediaGridBlock.MonthHeader(
                key = "system-month-$monthKey",
                title = monthFormat.format(date),
            )
            lastMonthKey = monthKey
            lastDayKey = null
        }
        val dayKey = dayKeyFormat.format(date)
        if (dayKey != lastDayKey) {
            blocks += SystemMediaGridBlock.DayHeader(
                key = "system-day-$dayKey",
                title = formatSystemMediaDayHeader(item.displayTimeMillis),
            )
            lastDayKey = dayKey
        }
        blocks += SystemMediaGridBlock.Media(
            key = "system-media-${item.id}",
            item = item,
        )
    }
    return blocks
}

internal fun formatSystemMediaDayHeader(timeMillis: Long): String {
    val target = Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance(Locale.CHINA).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return when (target.timeInMillis) {
        today.timeInMillis -> "今天"
        yesterday.timeInMillis -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(timeMillis))
    }
}

@Composable
internal fun SystemMediaDensityGhostOverlay(
    ghost: SystemMediaDensityGhost,
    gridEdgePadding: androidx.compose.ui.unit.Dp,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = YingShiThemeTokens.motion
    key(ghost.nonce) {
        val alpha = remember { Animatable(1f) }
        val scale = remember { Animatable(1f) }
        val ghostState = rememberLazyGridState(
            initialFirstVisibleItemIndex = ghost.firstVisibleItemIndex.coerceAtLeast(0),
            initialFirstVisibleItemScrollOffset = ghost.firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
        val thumbnailRequestSize = remember(ghost.density) {
            systemMediaThumbnailRequestSize(ghost.density)
        }
        LaunchedEffect(ghost.nonce) {
            coroutineScope {
                val alphaJob = async {
                    alpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = motion.densityMorphMillis,
                            easing = motion.easing,
                        ),
                    )
                }
                val scaleJob = async {
                    scale.animateTo(
                        targetValue = 0.96f,
                        animationSpec = tween(
                            durationMillis = motion.densityMorphMillis,
                            easing = motion.easing,
                        ),
                    )
                }
                awaitAll(alphaJob, scaleJob)
            }
            onFinished()
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(ghost.density.columns),
            state = ghostState,
            userScrollEnabled = false,
            modifier = modifier
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(
                start = gridEdgePadding,
                end = gridEdgePadding,
                top = 2.dp,
                bottom = 112.dp,
            ),
        ) {
            items(
                items = ghost.blocks,
                key = { "ghost-${ghost.nonce}-${it.key}" },
                span = { block ->
                    when (block) {
                        is SystemMediaGridBlock.Media -> GridItemSpan(1)
                        is SystemMediaGridBlock.MonthHeader,
                        is SystemMediaGridBlock.DayHeader,
                        -> GridItemSpan(maxLineSpan)
                    }
                },
                contentType = { block ->
                    when (block) {
                        is SystemMediaGridBlock.MonthHeader -> "ghost-system-month"
                        is SystemMediaGridBlock.DayHeader -> "ghost-system-day"
                        is SystemMediaGridBlock.Media -> "ghost-${block.item.type}-${ghost.density.columns}"
                    }
                },
            ) { block ->
                when (block) {
                    is SystemMediaGridBlock.MonthHeader -> SystemMediaMonthHeaderRow(block.title)
                    is SystemMediaGridBlock.DayHeader -> SystemMediaDayHeaderRow(block.title)
                    is SystemMediaGridBlock.Media -> SystemMediaGhostCard(
                        item = block.item,
                        density = ghost.density,
                        thumbnailRequestSize = thumbnailRequestSize,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SystemMediaGhostCard(
    item: SystemMediaItem,
    density: PhotoFeedDensity,
    thumbnailRequestSize: Int,
) {
    val context = LocalContext.current
    val thumbnail = rememberSystemMediaThumbnail(
        context = context,
        uri = item.uri,
        targetSizePx = thumbnailRequestSize,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio.coerceIn(0.56f, 1.8f))
            .clip(RoundedCornerShape(if (density.columns <= 4) 14.dp else 7.dp))
            .background(item.palette.start.copy(alpha = 0.58f)),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.toComposeBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun SystemMediaThumbnailWarmer(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
    visibleItems: List<SystemMediaItem>,
    thumbnailRequestSize: Int,
    warmWindow: Int,
    warmBatchSize: Int,
    scrubberInteracting: Boolean,
    visibleItemIndexById: Map<String, Int>,
) {
    val context = LocalContext.current
    LaunchedEffect(gridState, gridBlocks, visibleItems, thumbnailRequestSize, warmWindow, scrubberInteracting) {
        if (visibleItems.isEmpty() || warmWindow <= 0 || scrubberInteracting) return@LaunchedEffect
        snapshotFlow {
            val mediaIndices = gridState.layoutInfo.visibleItemsInfo.mapNotNull { visibleInfo ->
                val block = gridBlocks.getOrNull(visibleInfo.index) as? SystemMediaGridBlock.Media
                block?.item?.id?.let(visibleItemIndexById::get)
            }
            if (mediaIndices.isEmpty()) {
                null
            } else {
                mediaIndices.minOrNull()!! to mediaIndices.maxOrNull()!!
            }
        }.collectLatest { range ->
            val (firstVisible, lastVisible) = range ?: return@collectLatest
            val start = (firstVisible - warmWindow).coerceAtLeast(0)
            val end = (lastVisible + warmWindow).coerceAtMost(visibleItems.lastIndex)
            visibleItems
                .subList(start, end + 1)
                .chunked(warmBatchSize)
                .forEach { batch ->
                    coroutineScope {
                        batch.map { item ->
                            async {
                                prefetchSystemMediaThumbnail(
                                    context = context,
                                    uri = item.uri,
                                    targetSizePx = thumbnailRequestSize,
                                )
                            }
                        }.awaitAll()
                    }
                }
        }
    }
}

internal class SystemMediaInlineVideoController(
    val activeInlineVideoId: String?,
    val playingInlineVideoId: String?,
    val pausedInlineVideoIds: Set<String>,
    val inlineVideoProgressById: Map<String, InlineVideoPlaybackProgress>,
    val onToggleInlineVideo: (SystemMediaItem) -> Unit,
    val onInlineVideoProgressChange: (String, InlineVideoPlaybackProgress) -> Unit,
)

@Composable
internal fun rememberSystemMediaInlineVideoController(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
    inlineVideoAutoPlayAllowed: Boolean,
): SystemMediaInlineVideoController {
    var manualInlineVideoId by remember { mutableStateOf<String?>(null) }
    var pausedInlineVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inlineVideoProgressById by remember { mutableStateOf<Map<String, InlineVideoPlaybackProgress>>(emptyMap()) }
    val visibleInlineVideoIds by remember(gridState, gridBlocks) {
        derivedStateOf {
            visibleSystemMediaVideoIds(
                gridState = gridState,
                gridBlocks = gridBlocks,
            )
        }
    }
    val centeredInlineVideoId by remember(gridState, gridBlocks) {
        derivedStateOf {
            centeredSystemMediaVideoId(
                gridState = gridState,
                gridBlocks = gridBlocks,
            )
        }
    }
    LaunchedEffect(inlineVideoAutoPlayAllowed) {
        if (!inlineVideoAutoPlayAllowed) {
            manualInlineVideoId = null
        }
    }
    LaunchedEffect(visibleInlineVideoIds) {
        val manualId = manualInlineVideoId
        if (manualId != null && manualId !in visibleInlineVideoIds) {
            manualInlineVideoId = null
        }
    }
    val activeInlineVideoId = if (inlineVideoAutoPlayAllowed) {
        manualInlineVideoId?.takeIf { it in visibleInlineVideoIds } ?: centeredInlineVideoId
    } else {
        null
    }
    val playingInlineVideoId = activeInlineVideoId?.takeUnless { it in pausedInlineVideoIds }
    val onToggleInlineVideo = remember(inlineVideoAutoPlayAllowed, activeInlineVideoId, pausedInlineVideoIds) {
        toggle@{ item: SystemMediaItem ->
            if (!inlineVideoAutoPlayAllowed || item.type != SystemMediaType.VIDEO) {
                return@toggle
            }
            if (activeInlineVideoId == item.id) {
                pausedInlineVideoIds = if (item.id in pausedInlineVideoIds) {
                    pausedInlineVideoIds - item.id
                } else {
                    pausedInlineVideoIds + item.id
                }
            } else {
                manualInlineVideoId = item.id
                pausedInlineVideoIds = pausedInlineVideoIds - item.id
            }
        }
    }
    return SystemMediaInlineVideoController(
        activeInlineVideoId = activeInlineVideoId,
        playingInlineVideoId = playingInlineVideoId,
        pausedInlineVideoIds = pausedInlineVideoIds,
        inlineVideoProgressById = inlineVideoProgressById,
        onToggleInlineVideo = onToggleInlineVideo,
        onInlineVideoProgressChange = { itemId, progress ->
            inlineVideoProgressById = inlineVideoProgressById + (itemId to progress)
        },
    )
}

private fun visibleSystemMediaVideoIds(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
): Set<String> {
    return gridState.layoutInfo.visibleItemsInfo
        .mapNotNull { visible ->
            val block = gridBlocks.getOrNull(visible.index) as? SystemMediaGridBlock.Media
            block?.item?.takeIf { it.type == SystemMediaType.VIDEO }?.id
        }
        .toSet()
}

private fun centeredSystemMediaVideoId(
    gridState: LazyGridState,
    gridBlocks: List<SystemMediaGridBlock>,
): String? {
    val layoutInfo = gridState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return null
    val viewportCenterX = layoutInfo.viewportSize.width / 2f
    val viewportCenterY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f

    return layoutInfo.visibleItemsInfo
        .mapNotNull { visible ->
            val block = gridBlocks.getOrNull(visible.index) as? SystemMediaGridBlock.Media
                ?: return@mapNotNull null
            val item = block.item.takeIf { it.type == SystemMediaType.VIDEO } ?: return@mapNotNull null
            val centerX = visible.offset.x + visible.size.width / 2f
            val centerY = visible.offset.y + visible.size.height / 2f
            val score = abs(centerX - viewportCenterX) + abs(centerY - viewportCenterY)
            item.id to score
        }
        .minByOrNull { it.second }
        ?.first
}

@Composable
internal fun SystemMediaPendingScrollHandler(
    scrollTrigger: Int,
    filteredItems: List<SystemMediaItem>,
    density: PhotoFeedDensity,
    visibleItems: List<SystemMediaItem>,
    gridBlocks: List<SystemMediaGridBlock>,
    gridState: LazyGridState,
    renderPageSize: Int,
    renderedCount: Int,
    onRenderedCountChange: (Int) -> Unit,
) {
    var pendingTargetMediaIdSnapshot by remember { mutableStateOf<String?>(null) }
    var pendingTargetRenderedCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(
        scrollTrigger,
        filteredItems,
        renderedCount,
        density.columns,
    ) {
        val mediaId = LocalSystemMediaPageStateStore.pendingScrollTargetMediaId ?: return@LaunchedEffect
        val targetIndex = filteredItems.indexOfFirst { it.id == mediaId }
        if (mediaId != pendingTargetMediaIdSnapshot) {
            pendingTargetMediaIdSnapshot = mediaId
            pendingTargetRenderedCount = -1
        }
        if (targetIndex < 0) {
            pendingTargetMediaIdSnapshot = null
            pendingTargetRenderedCount = -1
            LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
            LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
            return@LaunchedEffect
        }
        if (density != PhotoFeedDensity.OVERVIEW_16 &&
            targetIndex >= visibleItems.size &&
            targetIndex < filteredItems.size
        ) {
            val nextRenderedCount = (targetIndex + renderPageSize)
                .coerceAtMost(filteredItems.size)
            if (nextRenderedCount > renderedCount && pendingTargetRenderedCount != nextRenderedCount) {
                pendingTargetRenderedCount = nextRenderedCount
                onRenderedCountChange(nextRenderedCount)
            }
            return@LaunchedEffect
        }
        val targetBlockIndex = gridBlocks.indexOfFirst { block ->
            block is SystemMediaGridBlock.Media && block.item.id == mediaId
        }.takeIf { it >= 0 } ?: targetIndex
        if (targetBlockIndex in gridState.layoutInfo.visibleItemsInfo.map { it.index }) {
            pendingTargetMediaIdSnapshot = null
            pendingTargetRenderedCount = -1
            LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
            LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
            return@LaunchedEffect
        }
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() }
            .first { it }
        gridState.scrollToItem(targetBlockIndex)
        pendingTargetMediaIdSnapshot = null
        pendingTargetRenderedCount = -1
        LocalSystemMediaPageStateStore.pendingScrollTargetMediaId = null
        LocalSystemMediaPageStateStore.pendingScrollAnchorOriginalIndex = -1
    }
}
