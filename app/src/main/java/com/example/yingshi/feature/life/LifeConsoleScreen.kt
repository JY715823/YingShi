package com.example.yingshi.feature.life

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteLifeConsoleMediaSlot
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.AppContentMediaThumbnail
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.PhotoThumbnailPalette
import com.example.yingshi.feature.photos.resolveAppMediaType
import com.example.yingshi.feature.photos.toAppContentMediaSource
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LifeConsoleScreen(
    modifier: Modifier = Modifier,
    initialSlotKey: String? = null,
    initialMediaId: String? = null,
    onBack: () -> Unit = {},
    onOpenLedgerAdd: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<RemoteLifeConsoleToday?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingUploadCategory by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    fun loadToday() {
        scope.launch {
            isLoading = true
            when (val result = RepositoryProvider.lifeConsoleRepository.getToday()) {
                is ApiResult.Success -> {
                    val today = result.data
                    snapshot = today
                    actionMessage = null
                    LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, today)
                }
                is ApiResult.Error -> actionMessage = result.message
                ApiResult.Loading -> Unit
            }
            isLoading = false
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        val category = pendingUploadCategory ?: return@rememberLauncherForActivityResult
        pendingUploadCategory = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            when (val result = LifeConsoleUploadBridge.uploadMedia(context, category, uris)) {
                is ApiResult.Success -> {
                    snapshot = result.data
                    LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, result.data)
                    Toast.makeText(context, "已上传到今日痕迹", Toast.LENGTH_SHORT).show()
                }
                is ApiResult.Error -> {
                    actionMessage = result.message
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                ApiResult.Loading -> Unit
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadToday()
    }

    ShellPage(
        title = "今日痕迹",
        summary = "",
        onBack = onBack,
        modifier = modifier,
        headerContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LifeConsolePillAction(
                    text = "刷新",
                    icon = Icons.Filled.Refresh,
                    onClick = { loadToday() },
                    enabled = !isLoading,
                    containerColor = YingShiThemeTokens.colors.primaryContainer.copy(alpha = 0.78f),
                    contentColor = YingShiThemeTokens.colors.titleAccent,
                )
                LifeConsolePillAction(
                    text = "快捷记账",
                    onClick = onOpenLedgerAdd,
                    containerColor = YingShiThemeTokens.colors.softGreenContainer.copy(alpha = 0.92f),
                    contentColor = YingShiThemeTokens.colors.softGreenAction,
                )
            }
            if (actionMessage != null) {
                Text(
                    text = actionMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    ) {
        when {
            isLoading && snapshot == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryAction)
                }
            }
            snapshot == null -> {
                Text(
                    text = "今天还没有记录。",
                    color = colors.textSecondary,
                )
            }
            else -> {
                val today = requireNotNull(snapshot)
                LifeConsoleGrid(
                    snapshot = today,
                    isBusy = isLoading,
                    initialSlotKey = initialSlotKey,
                    initialMediaId = initialMediaId,
                    onUpload = { category ->
                        pendingUploadCategory = category
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                    onDelete = { category, mediaId ->
                        scope.launch {
                            isLoading = true
                            when (val result = RepositoryProvider.lifeConsoleRepository.deleteMedia(category, mediaId)) {
                                is ApiResult.Success -> {
                                    snapshot?.withoutMedia(mediaId)?.let { next ->
                                        snapshot = next
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                    }
                                    loadToday()
                                }
                                is ApiResult.Error -> actionMessage = result.message
                                ApiResult.Loading -> Unit
                            }
                            isLoading = false
                        }
                    },
                )
                BowelCard(
                    snapshot = today,
                    isBusy = isLoading,
                    onAdd = {
                        scope.launch {
                            isLoading = true
                            when (val result = RepositoryProvider.lifeConsoleRepository.addBowelEvent()) {
                                is ApiResult.Success -> {
                                    val current = snapshot
                                    if (current != null) {
                                        val next = current.copy(bowel = result.data.bowel)
                                        snapshot = next
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                    }
                                }
                                is ApiResult.Error -> actionMessage = result.message
                                ApiResult.Loading -> Unit
                            }
                            isLoading = false
                        }
                    },
                    onRemove = {
                        scope.launch {
                            isLoading = true
                            when (val result = RepositoryProvider.lifeConsoleRepository.deleteLatestBowelEvent()) {
                                is ApiResult.Success -> {
                                    val current = snapshot
                                    if (current != null) {
                                        val next = current.copy(bowel = result.data.bowel)
                                        snapshot = next
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                    }
                                }
                                is ApiResult.Error -> actionMessage = result.message
                                ApiResult.Loading -> Unit
                            }
                            isLoading = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LifeConsoleGrid(
    snapshot: RemoteLifeConsoleToday,
    isBusy: Boolean,
    initialSlotKey: String?,
    initialMediaId: String?,
    onUpload: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md)) {
            LifeMediaFrame(
                title = "人物 · 我",
                slotKey = LifeConsoleSlotKeys.PERSON_SELF,
                slot = snapshot.personSelf,
                modifier = Modifier.weight(1f),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.PERSON_SELF },
                onUpload = onUpload,
                onDelete = onDelete,
            )
            LifeMediaFrame(
                title = "人物 · 对方",
                slotKey = LifeConsoleSlotKeys.PERSON_PARTNER,
                slot = snapshot.personPartner,
                modifier = Modifier.weight(1f),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.PERSON_PARTNER },
                onUpload = onUpload,
                onDelete = onDelete,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.md)) {
            LifeMediaFrame(
                title = "吃饭 · 我",
                slotKey = LifeConsoleSlotKeys.MEAL_SELF,
                slot = snapshot.mealSelf,
                modifier = Modifier.weight(1f),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.MEAL_SELF },
                onUpload = onUpload,
                onDelete = onDelete,
            )
            LifeMediaFrame(
                title = "吃饭 · 对方",
                slotKey = LifeConsoleSlotKeys.MEAL_PARTNER,
                slot = snapshot.mealPartner,
                modifier = Modifier.weight(1f),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.MEAL_PARTNER },
                onUpload = onUpload,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun LifeMediaFrame(
    title: String,
    slotKey: String,
    slot: RemoteLifeConsoleMediaSlot,
    modifier: Modifier = Modifier,
    isBusy: Boolean,
    initialMediaId: String?,
    onUpload: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = YingShiThemeTokens.colors
    val targetInitialPage = remember(slotKey, initialMediaId, slot.mediaItems) {
        slot.mediaItems.indexOfFirst { it.mediaId == initialMediaId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = targetInitialPage.coerceAtMost((slot.mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { slot.mediaItems.size.coerceAtLeast(1) },
    )
    LaunchedEffect(slotKey, initialMediaId, slot.mediaItems) {
        if (initialMediaId == null || slot.mediaItems.isEmpty()) return@LaunchedEffect
        val targetPage = slot.mediaItems.indexOfFirst { it.mediaId == initialMediaId }
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    val currentMedia = slot.mediaItems.getOrNull(pagerState.currentPage.coerceAtMost((slot.mediaItems.size - 1).coerceAtLeast(0)))
    Surface(
        modifier = modifier,
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.64f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${slot.mediaItems.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.sectionBackground.copy(alpha = 0.82f)),
            ) {
                if (slot.mediaItems.isEmpty()) {
                    EmptyFrame(title = title)
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val media = slot.mediaItems[page]
                        LifeMediaPreview(media = media)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (slot.mediaItems.isEmpty()) "今天还没有" else "${pagerState.currentPage + 1}/${slot.mediaItems.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (slot.editable) {
                        LifeConsoleSmallIconButton(
                            icon = Icons.Filled.Upload,
                            contentDescription = "上传",
                            onClick = { onUpload(slot.category) },
                            enabled = !isBusy,
                            containerColor = colors.softGreenContainer.copy(alpha = 0.86f),
                            contentColor = colors.softGreenAction,
                        )
                        LifeConsoleSmallIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "删除",
                            onClick = {
                                currentMedia?.mediaId?.let { mediaId ->
                                    onDelete(slot.category, mediaId)
                                }
                            },
                            enabled = !isBusy && currentMedia != null,
                            containerColor = colors.memoryContainer.copy(alpha = 0.82f),
                            contentColor = colors.memoryAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeMediaPreview(media: RemoteMedia) {
    val mediaType = resolveAppMediaType(
        rawType = media.mediaType,
        mimeType = media.mimeType,
        thumbnailUrl = media.thumbnailUrl ?: media.previewUrl,
        mediaUrl = media.mediaUrl,
        videoUrl = media.videoUrl,
        coverUrl = media.coverUrl,
        originalUrl = media.originalUrl,
    )
    AppContentMediaThumbnail(
        mediaSource = media.toAppContentMediaSource(),
        mediaType = mediaType,
        palette = LifeFramePalette,
        modifier = Modifier.fillMaxSize(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        showVideoPlayOverlay = mediaType == AppMediaType.VIDEO,
    )
}

@Composable
private fun EmptyFrame(title: String) {
    val colors = YingShiThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (title.contains("吃饭")) Icons.Filled.Restaurant else Icons.Filled.Add,
                contentDescription = null,
                tint = colors.textSecondary.copy(alpha = 0.70f),
            )
            Text(
                text = "今天还没有",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun BowelCard(
    snapshot: RemoteLifeConsoleToday,
    isBusy: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.softGreenContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(YingShiThemeTokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "大便记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.titleAccent,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Remove,
                        contentDescription = "减一次",
                        onClick = onRemove,
                        enabled = !isBusy,
                        containerColor = colors.sectionBackground.copy(alpha = 0.86f),
                        contentColor = colors.titleAccent,
                    )
                    LifeConsoleSmallIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = "加一次",
                        onClick = onAdd,
                        enabled = !isBusy,
                        containerColor = colors.primaryAction,
                        contentColor = colors.raisedSurface,
                    )
                }
            }
            snapshot.bowel.users.forEach { user ->
                val name = when (user.userId) {
                    snapshot.currentUser.userId -> snapshot.currentUser.displayName
                    snapshot.partner?.userId -> snapshot.partner.displayName
                    else -> user.userId
                }
                Text(
                    text = "$name：${user.count} 次${user.latestOccurredAtMillis?.let { "，最近 ${formatTime(it)}" }.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LifeConsolePillAction(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = if (enabled) containerColor else colors.sectionBackground.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) contentColor else colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) contentColor else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LifeConsoleSmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule)
    Surface(
        modifier = Modifier
            .size(34.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.93f,
                onClick = onClick,
            ),
        shape = shape,
        color = if (enabled) containerColor else colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) contentColor else colors.textSecondary.copy(alpha = 0.58f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun RemoteLifeConsoleToday.withoutMedia(mediaId: String): RemoteLifeConsoleToday {
    fun RemoteLifeConsoleMediaSlot.withoutTarget(): RemoteLifeConsoleMediaSlot {
        return copy(mediaItems = mediaItems.filterNot { it.mediaId == mediaId })
    }
    return copy(
        personSelf = personSelf.withoutTarget(),
        personPartner = personPartner.withoutTarget(),
        mealSelf = mealSelf.withoutTarget(),
        mealPartner = mealPartner.withoutTarget(),
    )
}

private fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private val LifeFramePalette = PhotoThumbnailPalette(
    start = Color(0xFFE8EEF7),
    end = Color(0xFFD6E0EC),
    accent = Color(0xFF526A86),
)

private object LifeConsoleSlotKeys {
    const val PERSON_SELF = "person_self"
    const val PERSON_PARTNER = "person_partner"
    const val MEAL_SELF = "meal_self"
    const val MEAL_PARTNER = "meal_partner"
}
