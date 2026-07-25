package com.example.yingshi.feature.life

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.yingshi.data.model.RemotePendingCleanup
import com.example.yingshi.data.model.RemoteTrashItem
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.formatMediaDisplayTime
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P1-2: 今日痕迹回收站。
 *
 * - 列表页分 PERSON / MEAL 两个 Tab, 3 列网格展示媒体缩略图。
 * - 24h 撤回中心: 已移出回收站但仍在 24h 撤销窗口内的项, 可撤销移出或永久删除。
 * - 查看态: 简化版 (HorizontalPager + 顶部返回/恢复/移出 + 底部日期/地点), 复刻 LifeMediaQuickViewer 的视觉风格。
 * - life 媒体的 lifeCategory 字段 (PERSON/MEAL) 在服务端区分, 客户端通过 lifeCategory 字段过滤。
 */
class LifeTrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        setContent {
            YingShiTheme {
                LifeTrashScreen(
                    initialCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: CATEGORY_PERSON,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        internal const val EXTRA_CATEGORY = "life_trash_category"
        internal const val CATEGORY_PERSON = "PERSON"
        internal const val CATEGORY_MEAL = "MEAL"

        fun intent(context: Context, category: String? = null): Intent {
            return Intent(context, LifeTrashActivity::class.java).apply {
                category?.let { putExtra(EXTRA_CATEGORY, it) }
            }
        }
    }
}

@Composable
private fun LifeTrashScreen(
    initialCategory: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    val viewModel: LifeTrashViewModel = viewModel(factory = LifeTrashViewModel.factory())
    val uiState by viewModel.uiState.collectAsState()

    // 列表页 / 撤回中心 / 查看态 三态切换
    var showPendingCleanup by remember { mutableStateOf(false) }
    var viewerState by remember { mutableStateOf<LifeTrashViewerState?>(null) }

    // 进入页面时加载列表
    LaunchedEffect(Unit) {
        viewModel.loadAll()
    }

    // 查看态打开时拦截返回键
    BackHandler(enabled = viewerState != null) {
        viewerState = null
    }
    BackHandler(enabled = showPendingCleanup && viewerState == null) {
        showPendingCleanup = false
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.appBackground)) {
        when {
            viewerState != null -> {
                LifeTrashViewer(
                    state = viewerState!!,
                    onBack = { viewerState = null },
                    onRestore = { item ->
                        viewModel.restoreItem(item.trashItemId) {
                            viewerState = null
                            viewModel.loadAll()
                        }
                    },
                    onMoveOut = { item ->
                        viewModel.moveItemOut(item.trashItemId) {
                            viewerState = null
                            viewModel.loadAll()
                        }
                    },
                )
            }
            showPendingCleanup -> {
                LifeTrashPendingCleanupPage(
                    pendingItems = uiState.pendingItems,
                    isLoading = uiState.isLoadingPending,
                    errorMessage = uiState.errorMessage,
                    onBack = { showPendingCleanup = false },
                    onRefresh = { viewModel.loadPendingCleanup() },
                    onUndo = { entry ->
                        viewModel.undoMoveOut(entry.trashItemId) {
                            viewModel.loadAll()
                        }
                    },
                    onPurge = { entry ->
                        viewModel.purgeItem(entry.trashItemId) {
                            viewModel.loadAll()
                        }
                    },
                )
            }
            else -> {
                LifeTrashListPage(
                    uiState = uiState,
                    initialCategory = initialCategory,
                    onClose = onClose,
                    onOpenItem = { item ->
                        val category = item.lifeCategory ?: return@LifeTrashListPage
                        val sameCategoryItems = uiState.items.filter { it.lifeCategory == category }
                        val initialIndex = sameCategoryItems.indexOfFirst { it.trashItemId == item.trashItemId }
                        viewerState = LifeTrashViewerState(
                            items = sameCategoryItems,
                            initialIndex = if (initialIndex >= 0) initialIndex else 0,
                        )
                    },
                    onOpenPendingCleanup = { showPendingCleanup = true },
                    onRefresh = { viewModel.loadAll() },
                )
            }
        }

        // 全局错误提示
        if (uiState.errorMessage != null && viewerState == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(radius.capsule),
                color = colors.destructiveContainer.copy(alpha = 0.92f),
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.destructive,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun LifeTrashListPage(
    uiState: LifeTrashUiState,
    initialCategory: String,
    onClose: () -> Unit,
    onOpenItem: (RemoteTrashItem) -> Unit,
    onOpenPendingCleanup: () -> Unit,
    onRefresh: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    // Tab 切换 PERSON / MEAL
    val initialTabIndex = if (initialCategory == LifeTrashActivity.CATEGORY_MEAL) 1 else 0
    var selectedTabIndex by remember { mutableIntStateOf(initialTabIndex) }
    val currentCategory = if (selectedTabIndex == 0) LifeTrashActivity.CATEGORY_PERSON else LifeTrashActivity.CATEGORY_MEAL

    val filteredItems = remember(uiState.items, currentCategory) {
        uiState.items.filter { it.lifeCategory == currentCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // 顶部: 返回键 + 标题 + 撤回中心按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .yingShiClickable(shape = RoundedCornerShape(12.dp), pressedScale = 0.94f, onClick = onClose),
                shape = RoundedCornerShape(12.dp),
                color = colors.sectionBackground.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.titleAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = "今日痕迹回收站",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.titleAccent,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm, alignment = Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LifeTrashPillAction(
                text = "刷新",
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = !uiState.isLoading,
                containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                contentColor = colors.titleAccent,
            )
            LifeTrashPillAction(
                text = "24h 撤回",
                icon = Icons.Filled.AutoAwesome,
                onClick = onOpenPendingCleanup,
                containerColor = colors.sectionBackground.copy(alpha = 0.90f),
                contentColor = colors.titleAccent,
            )
        }

        // Tab: 人物 / 吃饭
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colors.sectionBackground.copy(alpha = 0.60f),
            contentColor = colors.titleAccent,
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("人物", fontWeight = FontWeight.SemiBold) },
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("吃饭", fontWeight = FontWeight.SemiBold) },
            )
        }

        // 内容区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                uiState.isLoading && filteredItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAction)
                    }
                }
                filteredItems.isEmpty() && uiState.errorMessage == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无回收站内容",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        items(filteredItems, key = { it.trashItemId }) { item ->
                            LifeTrashGridCell(
                                item = item,
                                onClick = { onOpenItem(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeTrashGridCell(
    item: RemoteTrashItem,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val mediaId = item.sourceMediaId
    val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
    val previewUrl = if (mediaId != null) "$baseUrl/api/media/files/$mediaId?variant=preview" else null
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }
    val context = LocalContext.current

    // P1-2 复刻照片回收站: 入站日期 badge (显示 "X天", >25天红色)
    val daysInTrash = remember(item.deletedAtMillis) {
        val now = System.currentTimeMillis()
        if (item.deletedAtMillis <= 0L || now <= item.deletedAtMillis) 0L
        else java.util.concurrent.TimeUnit.MILLISECONDS.toDays(now - item.deletedAtMillis)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .yingShiClickable(shape = RoundedCornerShape(12.dp), pressedScale = 0.96f, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (previewUrl != null) {
                val request = remember(context, previewUrl, accessToken) {
                    ImageRequest.Builder(context)
                        .data(previewUrl)
                        .crossfade(true)
                        .apply {
                            if (accessToken != null) {
                                addHeader("Authorization", "Bearer $accessToken")
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            // 入站日期 badge (右上角)
            LifeTrashDaysBadge(
                days = daysInTrash,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp),
            )
        }
    }
}

/**
 * P1-2 复刻照片回收站 RealTrashDaysBadge: 显示 "X天", 超过 25 天变红色提示即将清理
 */
@Composable
private fun LifeTrashDaysBadge(
    days: Long,
    modifier: Modifier = Modifier,
) {
    val danger = days > 25
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
        color = if (danger) {
            colors.destructive.copy(alpha = 0.88f)
        } else {
            colors.viewerBackground.copy(alpha = 0.36f)
        },
        border = BorderStroke(
            1.dp,
            if (danger) {
                colors.destructive.copy(alpha = 0.24f)
            } else {
                colors.viewerAccent.copy(alpha = 0.14f)
            },
        ),
    ) {
        Text(
            text = "${days.coerceAtLeast(0)}天",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = colors.viewerText,
        )
    }
}

@Composable
private fun LifeTrashPendingCleanupPage(
    pendingItems: List<RemotePendingCleanup>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUndo: (RemotePendingCleanup) -> Unit,
    onPurge: (RemotePendingCleanup) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // 顶部: 返回键 + 标题 + 刷新
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .yingShiClickable(shape = RoundedCornerShape(12.dp), pressedScale = 0.94f, onClick = onBack),
                shape = RoundedCornerShape(12.dp),
                color = colors.sectionBackground.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.titleAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = "24h 撤回中心",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.titleAccent,
                modifier = Modifier.weight(1f),
            )
            LifeTrashPillAction(
                text = "刷新",
                icon = Icons.Filled.Refresh,
                onClick = onRefresh,
                enabled = !isLoading,
                containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                contentColor = colors.titleAccent,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                isLoading && pendingItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryAction)
                    }
                }
                pendingItems.isEmpty() && errorMessage == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无待处理项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                else -> {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(pendingItems) { entry ->
                            LifeTrashPendingCleanupRow(
                                entry = entry,
                                onUndo = { onUndo(entry) },
                                onPurge = { onPurge(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<RemotePendingCleanup>,
    itemContent: @Composable (RemotePendingCleanup) -> Unit,
) {
    items(items.size) { index ->
        itemContent(items[index])
    }
}

@Composable
private fun LifeTrashPendingCleanupRow(
    entry: RemotePendingCleanup,
    onUndo: () -> Unit,
    onPurge: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    val now = System.currentTimeMillis()
    val remainingMillis = entry.undoDeadlineMillis - now
    val remainingHours = (remainingMillis / (60L * 60L * 1000L)).coerceAtLeast(0L)
    val isExpired = remainingMillis <= 0L

    val categoryLabel = when (entry.item.lifeCategory) {
        LifeTrashActivity.CATEGORY_PERSON -> "人物"
        LifeTrashActivity.CATEGORY_MEAL -> "吃饭"
        else -> "今日痕迹"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.62f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // 缩略图
            val mediaId = entry.item.sourceMediaId
            val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
            val previewUrl = if (mediaId != null) "$baseUrl/api/media/files/$mediaId?variant=preview" else null
            val sessionVersion = AuthSessionManager.sessionVersion
            val accessToken = remember(sessionVersion) {
                AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
            }
            val context = LocalContext.current
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(10.dp),
                color = colors.sectionBackground,
            ) {
                if (previewUrl != null) {
                    val request = remember(context, previewUrl, accessToken) {
                        ImageRequest.Builder(context)
                            .data(previewUrl)
                            .crossfade(true)
                            .apply {
                                if (accessToken != null) {
                                    addHeader("Authorization", "Bearer $accessToken")
                                }
                            }
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            // 信息 + 倒计时
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(entry.item.deletedAtMillis))
                Text(
                    text = "删除于 $dateText",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = if (isExpired) "已过期" else "剩 ${remainingHours}h 可撤销",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isExpired || remainingHours < 6L) colors.destructive else colors.textSecondary,
                )
            }
            // 撤销 + 永久删除按钮
            LifeTrashPillAction(
                text = "撤销",
                icon = Icons.Filled.Restore,
                onClick = onUndo,
                enabled = !isExpired,
                containerColor = colors.primaryContainer.copy(alpha = 0.78f),
                contentColor = colors.titleAccent,
            )
            LifeTrashPillAction(
                text = "彻底删除",
                icon = Icons.Filled.Delete,
                onClick = onPurge,
                containerColor = colors.destructiveContainer.copy(alpha = 0.78f),
                contentColor = colors.destructive,
            )
        }
    }
}

@Composable
private fun LifeTrashViewer(
    state: LifeTrashViewerState,
    onBack: () -> Unit,
    onRestore: (RemoteTrashItem) -> Unit,
    onMoveOut: (RemoteTrashItem) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = state.items
    val pagerState = rememberPagerState(initialPage = state.initialIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))) { items.size }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showMoveOutConfirm by remember { mutableStateOf(false) }
    var isMutating by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val mediaId = currentItem?.sourceMediaId
    val baseUrl = BackendDebugConfig.currentBaseUrl().trimEnd('/')
    val previewUrl = if (mediaId != null) "$baseUrl/api/media/files/$mediaId?variant=preview" else null
    val originalUrl = if (mediaId != null) "$baseUrl/api/media/files/$mediaId" else null
    val sessionVersion = AuthSessionManager.sessionVersion
    val accessToken = remember(sessionVersion) {
        AuthSessionManager.peekAccessToken()?.takeIf { it.isNotBlank() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 媒体分页
        if (items.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                val pageMediaId = item.sourceMediaId
                val pagePreviewUrl = if (pageMediaId != null) "$baseUrl/api/media/files/$pageMediaId?variant=preview" else null
                val pageOriginalUrl = if (pageMediaId != null) "$baseUrl/api/media/files/$pageMediaId" else null
                val request = remember(context, pageOriginalUrl, accessToken) {
                    if (pageOriginalUrl == null) null
                    else ImageRequest.Builder(context)
                        .data(pageOriginalUrl)
                        .crossfade(true)
                        .apply {
                            if (accessToken != null) {
                                addHeader("Authorization", "Bearer $accessToken")
                            }
                        }
                        .build()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (request != null) {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }

        // 顶部: 返回 + 恢复 + 移出
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onBack),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.44f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f))
                // 恢复按钮
                Surface(
                    modifier = Modifier
                        .yingShiClickable(
                            shape = CircleShape,
                            pressedScale = 0.94f,
                            onClick = { showRestoreConfirm = true },
                        ),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.44f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = "恢复",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                    )
                }
                // 移出回收站按钮 (移入 24h 撤回中心)
                Surface(
                    modifier = Modifier
                        .yingShiClickable(
                            shape = CircleShape,
                            pressedScale = 0.94f,
                            onClick = { showMoveOutConfirm = true },
                        ),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.44f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "移出回收站",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp),
                    )
                }
            }
        }

        // 底部: 日期 + 地点
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = spacing.md, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                currentItem?.let { item ->
                    val dateText = if (item.deletedAtMillis > 0L) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(item.deletedAtMillis))
                    } else ""
                    if (dateText.isNotBlank()) {
                        Text(
                            text = "删除于 $dateText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // P1-2 复刻照片回收站: 显示已入站天数
                    val daysInTrash = remember(item.deletedAtMillis) {
                        val now = System.currentTimeMillis()
                        if (item.deletedAtMillis <= 0L || now <= item.deletedAtMillis) 0L
                        else java.util.concurrent.TimeUnit.MILLISECONDS.toDays(now - item.deletedAtMillis)
                    }
                    val isDanger = daysInTrash > 25
                    Text(
                        text = "已入站 ${daysInTrash}天" + if (isDanger) " · 建议尽快处理" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDanger) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.78f),
                    )
                    val categoryLabel = when (item.lifeCategory) {
                        LifeTrashActivity.CATEGORY_PERSON -> "人物"
                        LifeTrashActivity.CATEGORY_MEAL -> "吃饭"
                        else -> "今日痕迹"
                    }
                    Text(
                        text = "分类: $categoryLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }
        }

        if (isMutating) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
    }

    // 恢复确认
    if (showRestoreConfirm && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("恢复这张媒体？", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = { Text("恢复后会回到今日痕迹对应分类中。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        val target = currentItem
                        isMutating = true
                        onRestore(target)
                    },
                ) { Text("恢复", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            },
        )
    }

    // 移出回收站确认
    if (showMoveOutConfirm && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showMoveOutConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("移出回收站？", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = { Text("将把当前项目移到 24h 撤回中心。24 小时内可撤销，也可以在撤回中心永久删除。", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMoveOutConfirm = false
                        val target = currentItem
                        isMutating = true
                        onMoveOut(target)
                    },
                ) { Text("移出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showMoveOutConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LifeTrashPillAction(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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

private data class LifeTrashViewerState(
    val items: List<RemoteTrashItem>,
    val initialIndex: Int,
)
