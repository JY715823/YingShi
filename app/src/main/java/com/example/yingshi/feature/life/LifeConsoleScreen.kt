package com.example.yingshi.feature.life

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.yingshi.data.model.RemoteLifeConsoleBowelHistoryDay
import com.example.yingshi.data.model.RemoteLifeConsoleBowelUserSummary
import com.example.yingshi.data.model.RemoteLifeConsoleHistory
import com.example.yingshi.data.model.RemoteLifeConsoleHistoryDay
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
import com.example.yingshi.feature.photos.TrashDialogActionButton
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.components.TitleTabs
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiNotice
import com.example.yingshi.ui.components.YingShiNoticeHost
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import com.example.yingshi.feature.sync.StaleBanner
import com.example.yingshi.feature.sync.SyncModule
import com.example.yingshi.feature.sync.SyncVersionTracker
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

private enum class LifeConsoleHistoryRange(val label: String, val limitDays: Int) {
    LAST_7("近7天", 7),
    LAST_30("近30天", 30),
    ALL("全部", 365),
}

@Composable
fun LifeConsoleScreen(
    modifier: Modifier = Modifier,
    initialSlotKey: String? = null,
    initialMediaId: String? = null,
    onBack: () -> Unit = {},
    onOpenLedgerAdd: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val scope = rememberCoroutineScope()
    val zoneId = "Asia/Shanghai"
    var snapshot by remember { mutableStateOf<RemoteLifeConsoleToday?>(null) }
    var history by remember { mutableStateOf<RemoteLifeConsoleHistory?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isHistoryLoading by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingUploadCategory by remember { mutableStateOf<String?>(null) }
    var historyRange by remember { mutableStateOf(LifeConsoleHistoryRange.ALL) }
    var showHistoryPage by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<YingShiNotice?>(null) }
    var noticeNonce by remember { mutableStateOf(0) }
    var pendingDeleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    val currentHistoryRange by rememberUpdatedState(historyRange)

    fun showNotice(
        message: String,
        tone: YingShiNoticeTone = YingShiNoticeTone.INFO,
    ) {
        noticeNonce += 1
        notice = YingShiNotice(message = message, tone = tone, nonce = noticeNonce)
    }

    fun loadToday() {
        val requestedDate = currentLifeConsoleDate(zoneId)
        if (snapshot?.date != requestedDate) {
            snapshot = null
        }
        scope.launch {
            isLoading = true
            when (val result = RepositoryProvider.lifeConsoleRepository.getToday(date = requestedDate, zoneId = zoneId)) {
                is ApiResult.Success -> {
                    val today = result.data
                    snapshot = today
                    actionMessage = null
                    LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, today)
                    contentVisible = true
                }
                is ApiResult.Error -> actionMessage = result.message
                ApiResult.Loading -> Unit
            }
            isLoading = false
        }
    }

    fun loadHistory(limitDays: Int = currentHistoryRange.limitDays) {
        val todayDate = currentLifeConsoleDate(zoneId)
        scope.launch {
            isHistoryLoading = true
            when (val result = RepositoryProvider.lifeConsoleRepository.getHistory(zoneId = zoneId, limitDays = limitDays)) {
                is ApiResult.Success -> {
                    history = result.data.withoutDate(todayDate)
                    actionMessage = null
                }
                is ApiResult.Error -> actionMessage = result.message
                ApiResult.Loading -> Unit
            }
            isHistoryLoading = false
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
                    actionMessage = null
                    showNotice("已上传到今日痕迹", YingShiNoticeTone.SUCCESS)
                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                    loadHistory()
                }
                is ApiResult.Error -> {
                    actionMessage = result.message
                }
                ApiResult.Loading -> Unit
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadToday()
        loadHistory(historyRange.limitDays)
    }
    val syncStaleState by SyncVersionTracker.staleState.collectAsState()
    LaunchedEffect(Unit) {
        snapshotFlow { syncStaleState.lifeConsoleStale }
            .collect { currentlyStale ->
                if (currentlyStale) {
                    loadToday()
                    loadHistory(historyRange.limitDays)
                    SyncVersionTracker.markRefreshed(SyncModule.LIFE_CONSOLE)
                }
            }
    }
    LaunchedEffect(zoneId) {
        while (true) {
            kotlinx.coroutines.delay(millisUntilNextLifeConsoleRefresh(zoneId))
            loadToday()
            loadHistory(currentHistoryRange.limitDays)
        }
    }
    LaunchedEffect(showHistoryPage, historyRange) {
        if (showHistoryPage) {
            loadHistory(historyRange.limitDays)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadToday()
                loadHistory(historyRange.limitDays)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showHistoryPage) {
        BackHandler { showHistoryPage = false }
        LifeConsoleHistoryPage(
            history = history,
            isLoading = isHistoryLoading,
            actionMessage = actionMessage,
            selectedRange = historyRange,
            onRangeChange = { historyRange = it },
            onBack = { showHistoryPage = false },
            onRefresh = { loadHistory(historyRange.limitDays) },
            modifier = modifier,
            onOpenMedia = { media ->
                context.startActivity(LifeMediaQuickViewerActivity.intent(context, media))
            },
        )
        return
    }
    BackHandler(onBack = onBack)

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        ShellPage(
            title = "今日痕迹",
            summary = "",
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
            headerContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LifeConsolePillAction(
                        text = "刷新",
                        icon = Icons.Filled.Refresh,
                        onClick = {
                            loadToday()
                            loadHistory(historyRange.limitDays)
                        },
                        enabled = !isLoading && !isHistoryLoading,
                        containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                        contentColor = colors.titleAccent,
                    )
                    LifeConsolePillAction(
                        text = "历史记录",
                        onClick = { showHistoryPage = true },
                        enabled = !isHistoryLoading,
                        containerColor = colors.sectionBackground.copy(alpha = 0.90f),
                        contentColor = colors.titleAccent,
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
            StaleBanner(
                module = SyncModule.LIFE_CONSOLE,
                onRefresh = {
                    loadToday()
                    loadHistory(historyRange.limitDays)
                    SyncVersionTracker.markRefreshed(SyncModule.LIFE_CONSOLE)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
                    LifeConsoleTodayEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                    )
                }
                else -> {
                    val today = requireNotNull(snapshot)
                    LifeConsoleGrid(
                        snapshot = today,
                        isBusy = isLoading,
                        initialSlotKey = initialSlotKey,
                        initialMediaId = initialMediaId,
                        contentVisible = contentVisible,
                        onOpenMedia = { media ->
                            context.startActivity(LifeMediaQuickViewerActivity.intent(context, media))
                        },
                        onUpload = { category ->
                            pendingUploadCategory = category
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        onDelete = { category, mediaId ->
                            pendingDeleteTarget = category to mediaId
                        },
                    )
                    BowelCard(
                        snapshot = today,
                        isBusy = isLoading,
                        contentVisible = contentVisible,
                        onAdd = {
                            val restored = snapshot ?: return@BowelCard
                            val optimistic = restored.withOptimisticBowelDelta(delta = 1) ?: return@BowelCard
                            snapshot = optimistic
                            LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, optimistic)
                            scope.launch {
                                when (val result = RepositoryProvider.lifeConsoleRepository.addBowelEvent()) {
                                    is ApiResult.Success -> {
                                        val current = snapshot
                                        if (current != null) {
                                            val next = current.copy(bowel = result.data.bowel)
                                            snapshot = next
                                            LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                        }
                                        SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                                        loadHistory(historyRange.limitDays)
                                    }
                                    is ApiResult.Error -> {
                                        snapshot = restored
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, restored)
                                        actionMessage = result.message
                                    }
                                    ApiResult.Loading -> Unit
                                }
                            }
                        },
                        onRemove = {
                            val restored = snapshot ?: return@BowelCard
                            val optimistic = restored.withOptimisticBowelDelta(delta = -1) ?: return@BowelCard
                            snapshot = optimistic
                            LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, optimistic)
                            scope.launch {
                                when (val result = RepositoryProvider.lifeConsoleRepository.deleteLatestBowelEvent()) {
                                    is ApiResult.Success -> {
                                        val current = snapshot
                                        if (current != null) {
                                            val next = current.copy(bowel = result.data.bowel)
                                            snapshot = next
                                            LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                        }
                                        SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                                        loadHistory(historyRange.limitDays)
                                    }
                                    is ApiResult.Error -> {
                                        snapshot = restored
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, restored)
                                        actionMessage = result.message
                                    }
                                    ApiResult.Loading -> Unit
                                }
                            }
                        },
                    )
                }
            }
        }

        YingShiNoticeHost(
            notice = notice,
            onExpired = { nonce ->
                if (notice?.nonce == nonce) {
                    notice = null
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = spacing.md),
        )
    }

    pendingDeleteTarget?.let { (category, mediaId) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            containerColor = colors.raisedSurface,
            titleContentColor = colors.titleAccent,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = "从今日痕迹移除这张媒体？",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            text = {
                Text(
                    text = "移除后会从今天的人物或吃饭格子里消失，但不会影响已经导入照片流的内容。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TrashDialogActionButton(
                    text = "继续移除",
                    emphasized = true,
                    onClick = {
                        pendingDeleteTarget = null
                        scope.launch {
                            isLoading = true
                            when (val result = RepositoryProvider.lifeConsoleRepository.deleteMedia(category, mediaId)) {
                                is ApiResult.Success -> {
                                    actionMessage = null
                                    snapshot?.withoutMedia(mediaId)?.let { next ->
                                        snapshot = next
                                        LifeConsoleWidgetProvider.applySnapshot(context.applicationContext, next)
                                    }
                                    showNotice("已从今日痕迹移除", YingShiNoticeTone.SUCCESS)
                                    SyncVersionTracker.markLocalMutation(SyncModule.LIFE_CONSOLE)
                                    loadToday()
                                    loadHistory(historyRange.limitDays)
                                }
                                is ApiResult.Error -> actionMessage = result.message
                                ApiResult.Loading -> Unit
                            }
                            isLoading = false
                        }
                    },
                )
            },
            dismissButton = {
                TrashDialogActionButton(text = "取消", onClick = { pendingDeleteTarget = null })
            },
        )
    }
}

@Composable
private fun LifeConsoleTodayEmptyState(modifier: Modifier = Modifier) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = colors.textSecondary.copy(alpha = 0.50f),
            )
            Text(
                text = "今天还没有痕迹",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
            Text(
                text = "拍下今天的第一张照片吧",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary.copy(alpha = 0.56f),
            )
        }
    }
}

@Composable
private fun LifeConsoleHistoryPage(
    history: RemoteLifeConsoleHistory?,
    isLoading: Boolean,
    actionMessage: String?,
    selectedRange: LifeConsoleHistoryRange,
    onRangeChange: (LifeConsoleHistoryRange) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMedia: (RemoteMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = YingShiBackdropVariant.LIFE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LifeConsoleBackButton(onClick = onBack)
                Text(
                    text = "历史记录",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LifeConsolePillAction(
                    text = "刷新",
                    icon = Icons.Filled.Refresh,
                    onClick = onRefresh,
                    enabled = !isLoading,
                    containerColor = colors.primaryContainer.copy(alpha = 0.82f),
                    contentColor = colors.titleAccent,
                )
            }
            if (!actionMessage.isNullOrBlank()) {
                Text(
                    text = actionMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LifeConsoleHistoryPanel(
                history = history,
                isLoading = isLoading,
                selectedRange = selectedRange,
                onRangeChange = onRangeChange,
                onOpenMedia = onOpenMedia,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LifeConsoleHistoryPanel(
    history: RemoteLifeConsoleHistory?,
    isLoading: Boolean,
    selectedRange: LifeConsoleHistoryRange,
    onRangeChange: (LifeConsoleHistoryRange) -> Unit,
    onOpenMedia: (RemoteMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 },
    )
    var filterExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleTabs(
                tabs = listOf("人物", "吃饭", "大便"),
                selectedIndex = pagerState.currentPage,
                modifier = Modifier.weight(1f),
                onSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            )
            Box {
                LifeConsolePillAction(
                    text = selectedRange.label,
                    onClick = { filterExpanded = true },
                    containerColor = colors.sectionBackground.copy(alpha = 0.88f),
                    contentColor = colors.titleAccent,
                )
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false },
                ) {
                    LifeConsoleHistoryRange.entries.forEach { range ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                filterExpanded = false
                                onRangeChange(range)
                            },
                        )
                    }
                }
            }
        }
        // History stats summary
        if (history != null) {
            val totalDays = maxOf(history.personDays.size, history.mealDays.size, history.bowelDays.size)
            if (totalDays > 0) {
                Text(
                    text = "共 $totalDays 天记录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.xxs),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when {
                isLoading && history == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAction)
                    }
                }

                history == null -> {
                    LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                }

                page == 2 -> {
                    if (history.bowelDays.isEmpty()) {
                        LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            history.bowelDays.forEach { day ->
                                LifeConsoleHistoryBowelDayCard(
                                    day = day,
                                    currentUser = history.currentUser,
                                    partner = history.partner,
                                )
                            }
                        }
                    }
                }

                else -> {
                    val days = if (page == 0) history.personDays else history.mealDays
                    if (days.isEmpty()) {
                        LifeConsoleHistoryEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(spacing.md),
                        ) {
                            days.forEach { day ->
                                LifeConsoleHistoryDaySection(
                                    day = day,
                                    selfLabel = history.currentUser.displayName,
                                    partnerLabel = history.partner?.displayName ?: "对方",
                                    onOpenMedia = onOpenMedia,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryBowelDayCard(
    day: RemoteLifeConsoleBowelHistoryDay,
    currentUser: com.example.yingshi.data.model.RemoteLifeConsoleUser,
    partner: com.example.yingshi.data.model.RemoteLifeConsoleUser?,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    YingShiMistCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = day.displayLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.titleAccent,
            )
            day.users.forEach { user ->
                val name = when (user.userId) {
                    currentUser.userId -> currentUser.displayName
                    partner?.userId -> partner.displayName
                    else -> user.userId
                }
                Text(
                    text = buildString {
                        append(name)
                        append(" · ")
                        append(user.count)
                        append(" 次")
                        if (user.eventTimesMillis.isNotEmpty()) {
                            append(" · ")
                            append(
                                user.eventTimesMillis
                                    .takeLast(4)
                                    .joinToString(" / ") { formatTime(it) },
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryDaySection(
    day: RemoteLifeConsoleHistoryDay,
    selfLabel: String,
    partnerLabel: String,
    onOpenMedia: (RemoteMedia) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    YingShiMistCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.md),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = day.displayLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.titleAccent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                LifeConsoleHistoryMediaColumn(
                    title = selfLabel,
                    mediaItems = day.selfMedia,
                    modifier = Modifier.weight(1f),
                    onOpenMedia = onOpenMedia,
                )
                LifeConsoleHistoryMediaColumn(
                    title = partnerLabel,
                    mediaItems = day.partnerMedia,
                    modifier = Modifier.weight(1f),
                    onOpenMedia = onOpenMedia,
                )
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryMediaColumn(
    title: String,
    mediaItems: List<RemoteMedia>,
    modifier: Modifier = Modifier,
    onOpenMedia: (RemoteMedia) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
        )
        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.08f)
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.sectionBackground.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary.copy(alpha = 0.72f),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp)) {
                mediaItems.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp)) {
                        rowItems.forEach { media ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(radius.sm))
                                    .background(colors.sectionBackground.copy(alpha = 0.78f)),
                            ) {
                                LifeMediaPreview(
                                    media = media,
                                    onClick = { onOpenMedia(media) },
                                )
                            }
                        }
                        repeat(2 - rowItems.size) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeConsoleHistoryEmptyState(
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.textSecondary.copy(alpha = 0.46f),
            )
            Text(
                text = "还没有历史记录",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary.copy(alpha = 0.72f),
            )
            Text(
                text = "上传照片后会自动出现在这里",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary.copy(alpha = 0.52f),
            )
        }
    }
}

@Composable
private fun LifeConsoleBackButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.sm)
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = shape, pressedScale = 0.94f, onClick = onClick),
        shape = shape,
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.titleAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LifeConsoleGrid(
    snapshot: RemoteLifeConsoleToday,
    isBusy: Boolean,
    initialSlotKey: String?,
    initialMediaId: String?,
    contentVisible: Boolean,
    onOpenMedia: (RemoteMedia) -> Unit,
    onUpload: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            LifeMediaFrame(
                title = "人物 · 我",
                slotKey = LifeConsoleSlotKeys.PERSON_SELF,
                slot = snapshot.personSelf,
                modifier = Modifier
                    .weight(1f)
                    .yingShiRouteReveal(visible = contentVisible),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.PERSON_SELF },
                accentGradient = LifePersonGradient,
                onOpenMedia = onOpenMedia,
                onUpload = onUpload,
                onDelete = onDelete,
            )
            LifeMediaFrame(
                title = "人物 · 对方",
                slotKey = LifeConsoleSlotKeys.PERSON_PARTNER,
                slot = snapshot.personPartner,
                modifier = Modifier
                    .weight(1f)
                    .yingShiRouteReveal(visible = contentVisible),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.PERSON_PARTNER },
                accentGradient = LifePersonGradient,
                onOpenMedia = onOpenMedia,
                onUpload = onUpload,
                onDelete = onDelete,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            LifeMediaFrame(
                title = "吃饭 · 我",
                slotKey = LifeConsoleSlotKeys.MEAL_SELF,
                slot = snapshot.mealSelf,
                modifier = Modifier
                    .weight(1f)
                    .yingShiRouteReveal(visible = contentVisible),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.MEAL_SELF },
                accentGradient = LifeMealGradient,
                onOpenMedia = onOpenMedia,
                onUpload = onUpload,
                onDelete = onDelete,
            )
            LifeMediaFrame(
                title = "吃饭 · 对方",
                slotKey = LifeConsoleSlotKeys.MEAL_PARTNER,
                slot = snapshot.mealPartner,
                modifier = Modifier
                    .weight(1f)
                    .yingShiRouteReveal(visible = contentVisible),
                isBusy = isBusy,
                initialMediaId = initialMediaId.takeIf { initialSlotKey == LifeConsoleSlotKeys.MEAL_PARTNER },
                accentGradient = LifeMealGradient,
                onOpenMedia = onOpenMedia,
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
    accentGradient: List<Color>? = null,
    onOpenMedia: (RemoteMedia) -> Unit,
    onUpload: (String) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors
    val targetInitialPage = remember(slotKey, initialMediaId, slot.mediaItems) {
        if (initialMediaId != null) {
            slot.mediaItems.indexOfFirst { it.mediaId == initialMediaId }.coerceAtLeast(0)
        } else {
            // Default to the latest (last) media item
            (slot.mediaItems.size - 1).coerceAtLeast(0)
        }
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
    YingShiMistCard(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
    ) {
        Box {
            // Accent gradient overlay
            if (accentGradient != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = accentGradient,
                                center = Offset(0f, 0f),
                                radius = 400f,
                            ),
                        )
                        .clip(RoundedCornerShape(radius.lg)),
                )
            }
            Column(
                modifier = Modifier.padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
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
                        .clip(RoundedCornerShape(radius.sm))
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
                            LifeMediaPreview(
                                media = media,
                                onClick = { onOpenMedia(media) },
                            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp)) {
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
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.20f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeMediaPreview(
    media: RemoteMedia,
    onClick: () -> Unit,
) {
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
        modifier = Modifier
            .fillMaxSize()
            .yingShiClickable(
                pressedScale = 0.985f,
                onClick = onClick,
            ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        showVideoPlayOverlay = mediaType == AppMediaType.VIDEO,
    )
}

@Composable
private fun EmptyFrame(title: String) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp),
        ) {
            Icon(
                imageVector = if (title.contains("吃饭")) Icons.Filled.Restaurant else Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colors.textSecondary.copy(alpha = 0.54f),
            )
            Text(
                text = when {
                    title.contains("吃饭") -> "记录今天的一餐"
                    else -> "拍下今天的瞬间"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun BowelCard(
    snapshot: RemoteLifeConsoleToday,
    isBusy: Boolean,
    contentVisible: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    YingShiMistCard(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiRouteReveal(visible = contentVisible),
        shape = RoundedCornerShape(radius.lg),
        color = colors.softGreenContainer.copy(alpha = 0.48f),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp)) {
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
                        contentColor = colors.onPrimaryContainer,
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
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
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
            modifier = Modifier.padding(horizontal = spacing.sm + 2.dp, vertical = spacing.sm - 3.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs + 2.dp),
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
    borderColor: Color = YingShiThemeTokens.colors.dividerSoft.copy(alpha = 0.62f),
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(
                enabled = enabled,
                shape = shape,
                pressedScale = 0.93f,
                onClick = onClick,
            ),
        shape = shape,
        color = if (enabled) containerColor else colors.sectionBackground.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, if (enabled) borderColor else colors.dividerSoft.copy(alpha = 0.62f)),
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

private fun RemoteLifeConsoleToday.withOptimisticBowelDelta(delta: Int): RemoteLifeConsoleToday? {
    val userId = currentUser.userId
    val nowMillis = System.currentTimeMillis()
    val users = bowel.users.toMutableList()
    val userIndex = users.indexOfFirst { it.userId == userId }
    val current = users.getOrNull(userIndex) ?: if (delta > 0) {
        RemoteLifeConsoleBowelUserSummary(
            userId = userId,
            count = 0,
            latestOccurredAtMillis = null,
            eventTimesMillis = emptyList(),
        )
    } else {
        return null
    }
    val nextTimes = if (delta > 0) {
        current.eventTimesMillis + nowMillis
    } else {
        if (current.eventTimesMillis.isEmpty() && current.count <= 0) return null
        current.eventTimesMillis.dropLast(1)
    }
    val nextUser = current.copy(
        count = (current.count + delta).coerceAtLeast(0),
        latestOccurredAtMillis = nextTimes.lastOrNull(),
        eventTimesMillis = nextTimes,
    )
    if (userIndex >= 0) {
        users[userIndex] = nextUser
    } else {
        users += nextUser
    }
    return copy(bowel = bowel.copy(users = users))
}

private fun RemoteLifeConsoleHistory.withoutDate(date: String): RemoteLifeConsoleHistory {
    return copy(
        personDays = personDays.filterNot { it.date == date },
        mealDays = mealDays.filterNot { it.date == date },
        bowelDays = bowelDays.filterNot { it.date == date },
    )
}

private fun currentLifeConsoleDate(zoneId: String): String {
    return LocalDate.now(ZoneId.of(zoneId)).toString()
}

private fun millisUntilNextLifeConsoleRefresh(zoneId: String): Long {
    val now = ZonedDateTime.now(ZoneId.of(zoneId))
    val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(1)
    return ChronoUnit.MILLIS.between(now, next).coerceAtLeast(1L)
}

private fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
}

private val LifeFramePalette = PhotoThumbnailPalette(
    start = Color(0xFFE8EEF7),
    end = Color(0xFFD6E0EC),
    accent = Color(0xFF526A86),
)

private val LifePersonGradient = listOf(
    Color(0xFF4A7CBA).copy(alpha = 0.06f),
    Color(0xFF4A7CBA).copy(alpha = 0.03f),
    Color.Transparent,
)

private val LifeMealGradient = listOf(
    Color(0xFFC4874A).copy(alpha = 0.06f),
    Color(0xFFC4874A).copy(alpha = 0.03f),
    Color.Transparent,
)

private object LifeConsoleSlotKeys {
    const val PERSON_SELF = "person_self"
    const val PERSON_PARTNER = "person_partner"
    const val MEAL_SELF = "meal_self"
    const val MEAL_PARTNER = "meal_partner"
}
