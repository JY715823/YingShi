package com.example.yingshi.feature.photos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.cache.OfflineAccessManager
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.YingShiEntryCard
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiStateLayer
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch

data class SettingsRoute(
    val source: String = "my-page",
)

private enum class SettingsCategory(val label: String) {
    SPACE("空间"),
    BROWSE("浏览"),
    SHARE("分享"),
    SYSTEM("系统"),
}

@Composable
fun SettingsScreen(
    route: SettingsRoute,
    onBack: () -> Unit,
    onOpenBackendDiagnostics: (BackendDiagnosticsRoute) -> Unit,
    onOpenCacheManagement: (CacheManagementRoute) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val settingsState = SettingsRepository.getSettingsState()
    val viewerPreferences = settingsState.viewerPreferences
    val sharePreferences = settingsState.sharePreferences
    val interactionPreferences = settingsState.interactionPreferences
    val pushPreferences = settingsState.pushPreferences
    val pushDiagnostics = settingsState.pushDiagnostics
    val pushTokenDiagnostic by PushTokenRegistrar.diagnosticState.collectAsState()
    val offlineAccessState = OfflineAccessManager.state
    val currentUser = CollaboratorDirectoryStore.currentUser ?: AuthSessionManager.getCurrentUserSnapshot()
    var notificationPermissionRefresh by remember { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.SPACE) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationPermissionRefresh += 1
    }
    val loginStatusValue = when {
        offlineAccessState.isReadOnly -> "缓存只读"
        AuthSessionManager.isLoggedIn -> "已连接"
        else -> "未连接"
    }
    val sharedSpaceValue = resolveSharedSpaceStatus(currentUser)
    val systemMediaAccessValue = if (hasSystemMediaReadAccess(context)) {
        "已授权"
    } else {
        "未授权"
    }
    val notificationPermissionValue = notificationPermissionRefresh.let {
        resolveNotificationPermissionStatus(context)
    }
    val cacheSummary = MediaCacheRepository.getSummary(context)
    LaunchedEffect(AuthSessionManager.isLoggedIn) {
        if (AuthSessionManager.isLoggedIn) {
            SettingsRepository.refreshPushPreferencesFromRemote()
        }
    }

    fun updatePushPreference(
        module: String,
        category: String,
        enabled: Boolean,
        transform: (PushPreferenceState) -> PushPreferenceState,
    ) {
        coroutineScope.launch {
            SettingsRepository.updatePushPreference(
                module = module,
                category = category,
                enabled = enabled,
                transform = transform,
            )
        }
    }

    fun requestOrOpenNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    YingShiMistBackground(
        modifier = modifier,
        showWaves = true,
        variant = YingShiBackdropVariant.SHELL,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            SettingsTopBar(
                source = route.source,
                onBack = onBack,
            )

            // ── Chip category navigation ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                SettingsCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(YingShiThemeTokens.radius.capsule))
                            .yingShiHapticClickable(
                                shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                                pressedScale = 0.96f,
                            ) { selectedCategory = category },
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                        color = if (isSelected) {
                            colors.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            colors.sectionBackground.copy(alpha = 0.50f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) colors.glassStroke.copy(alpha = 0.68f)
                            else colors.dividerSoft.copy(alpha = 0.48f),
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isSelected) Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                colors.raisedSurface.copy(alpha = 0.38f),
                                                colors.glowWash.copy(alpha = 0.22f),
                                            ),
                                        ),
                                    ) else Modifier,
                                )
                                .padding(horizontal = spacing.md, vertical = spacing.xs),
                        ) {
                            Text(
                                text = category.label,
                                style = if (isSelected) {
                                    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                                color = if (isSelected) colors.titleAccent else colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // ── Category-filtered content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                AnimatedContent(
                    targetState = selectedCategory,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180))) togetherWith
                            (fadeOut(animationSpec = tween(120)))
                    },
                    label = "settingsCategoryTransition",
                ) { category ->
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        when (category) {
                            // ════════════════════════════════════════════
                            //  SPACE — 账号与空间 · 缓存与存储
                            // ════════════════════════════════════════════
                            SettingsCategory.SPACE -> {
                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "账号与空间",
                                            subtitle = "管理当前账号和两个人的共享空间。",
                                        )
                                        SettingsInfoRow(
                                            title = "共享空间",
                                            subtitle = "映世双人空间",
                                            value = sharedSpaceValue,
                                        )
                                        SettingsInfoRow(
                                            title = "登录状态",
                                            subtitle = "用于同步相册、通知和生活记录。",
                                            value = loginStatusValue,
                                        )
                                        SettingsInfoRow(
                                            title = "离线兜底",
                                            subtitle = "服务器不可用时，核心入口会优先展示上次缓存内容。",
                                            value = if (offlineAccessState.isReadOnly) "当前已启用" else "按需启用",
                                        )
                                        YingShiEntryCard(
                                            title = "退出登录",
                                            subtitle = "清除当前账号会话",
                                            icon = Icons.Rounded.Login,
                                            backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f),
                                            onClick = onLogout,
                                        )
                                    }
                                }

                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "缓存与存储",
                                            subtitle = "查看当前媒体缓存占用，按需清理，不做激进自动删除。",
                                        )
                                        SettingsInfoRow(
                                            title = "媒体缓存占用",
                                            subtitle = "含预览图、封面图、原图和视频缓存。",
                                            value = cacheSummary.totalSizeLabel,
                                        )
                                        SettingsInfoRow(
                                            title = "缓存统计",
                                            subtitle = "已登记 ${cacheSummary.registeredMediaCount} 项媒体，预览 ${cacheSummary.registeredPreviewCount}，原图 ${cacheSummary.registeredOriginalCount}。",
                                            value = "${cacheSummary.registeredVideoCount} 段视频",
                                        )
                                        YingShiEntryCard(
                                            title = "打开缓存管理",
                                            subtitle = "查看明细并手动清理缩略图、原图和视频缓存。",
                                            icon = Icons.Rounded.Storage,
                                            onClick = { onOpenCacheManagement(CacheManagementRoute(source = "settings")) },
                                        )
                                    }
                                }
                            }

                            // ════════════════════════════════════════════
                            //  BROWSE — 浏览密度 · 查看器偏好 · 时间排序
                            // ════════════════════════════════════════════
                            SettingsCategory.BROWSE -> {
                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "浏览密度",
                                            subtitle = "重新进入页面时使用这些默认排布。",
                                        )
                                        SettingsChoiceRow(
                                            title = "照片页默认网格密度",
                                            subtitle = "支持 2 / 3 / 4 / 8 / 16 列。",
                                            options = PhotoFeedDensity.entries,
                                            selectedOption = settingsState.defaultPhotoFeedDensity,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateDefaultPhotoFeedDensity(it) },
                                        )
                                        SettingsChoiceRow(
                                            title = "相册页默认列数",
                                            subtitle = "支持 2 / 3 / 4 列。",
                                            options = AlbumGridDensity.entries,
                                            selectedOption = settingsState.defaultAlbumGridDensity,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateDefaultAlbumGridDensity(it) },
                                        )
                                        SettingsChoiceRow(
                                            title = "系统媒体默认网格密度",
                                            subtitle = "默认跟照片流接近，也允许你单独记住更密或更松的浏览方式。",
                                            options = PhotoFeedDensity.entries,
                                            selectedOption = settingsState.defaultSystemMediaDensity,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateDefaultSystemMediaDensity(it) },
                                        )
                                    }
                                }

                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "查看器偏好",
                                            subtitle = "控制全屏看图、长图阅读和视频切换时的行为。",
                                        )
                                        SettingsSwitchRow(
                                            title = "长图自动阅读模式",
                                            subtitle = "检测到长图后默认按屏幕宽度阅读，并支持上下滚动。",
                                            checked = viewerPreferences.autoLongImageReading,
                                            onCheckedChange = { SettingsRepository.updateAutoLongImageReading(it) },
                                        )
                                        SettingsSwitchRow(
                                            title = "缩放时弱化操作层",
                                            subtitle = "缩放后优先查看内容，恢复到适配屏幕后再把操作层完整显示回来。",
                                            checked = viewerPreferences.hideOverlaysWhenZoomed,
                                            onCheckedChange = { SettingsRepository.updateHideViewerOverlaysWhenZoomed(it) },
                                        )
                                        SettingsSwitchRow(
                                            title = "切换媒体时自动暂停视频",
                                            subtitle = "避免视频播放状态串到下一张媒体上。",
                                            checked = viewerPreferences.autoPauseVideoOnMediaSwitch,
                                            onCheckedChange = { SettingsRepository.updateAutoPauseVideoOnMediaSwitch(it) },
                                        )
                                        SettingsInfoRow(
                                            title = "原图加载方式",
                                            subtitle = "当前保持按需加载，避免看图时默默占满缓存。",
                                            value = "按需加载",
                                        )
                                    }
                                }

                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "时间排序",
                                            subtitle = "决定系统媒体导入和上传到照片流后默认按什么时间排。",
                                        )
                                        SettingsChoiceRow(
                                            title = "媒体显示时间策略",
                                            subtitle = "拍摄优先会先用拍摄时间，缺失时再退回文件时间和导入时间。",
                                            options = MediaTimePreference.entries,
                                            selectedOption = settingsState.mediaTimePreference,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateMediaTimePreference(it) },
                                        )
                                        SettingsInfoRow(
                                            title = "当前兜底链路",
                                            subtitle = "避免相机上周拍的照片，今天导出后全挤到今天。",
                                            value = "拍摄 → 文件 → 导入",
                                        )
                                    }
                                }
                            }

                            // ════════════════════════════════════════════
                            //  SHARE — 分享
                            // ════════════════════════════════════════════
                            SettingsCategory.SHARE -> {
                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "分享",
                                            subtitle = "决定多项媒体默认怎么分享，优先发真实文件，不做相册链接。",
                                        )
                                        SettingsChoiceRow(
                                            title = "多项分享方式",
                                            subtitle = "智能默认是 1-9 项直接分享，10 项起打包 ZIP。",
                                            options = ShareDeliveryPreference.entries,
                                            selectedOption = sharePreferences.deliveryPreference,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateShareDeliveryPreference(it) },
                                        )
                                        SettingsChoiceRow(
                                            title = "图片默认画质",
                                            subtitle = "原图更适合正式分享，缩略图更适合快速转发。",
                                            options = ShareImageQualityPreference.entries,
                                            selectedOption = sharePreferences.imageQualityPreference,
                                            optionLabel = { it.label },
                                            onOptionSelected = { SettingsRepository.updateShareImageQualityPreference(it) },
                                        )
                                        SettingsChoiceRow(
                                            title = "智能打包阈值",
                                            subtitle = "仅在「智能默认」下生效。",
                                            options = listOf(10, 20, 50),
                                            selectedOption = sharePreferences.zipThreshold,
                                            optionLabel = { "${it}项" },
                                            onOptionSelected = { SettingsRepository.updateShareZipThreshold(it) },
                                        )
                                        SettingsSwitchRow(
                                            title = "分享时包含视频",
                                            subtitle = "关闭后会只分享图片，视频会被跳过。",
                                            checked = sharePreferences.includeVideos,
                                            onCheckedChange = { SettingsRepository.updateShareIncludeVideos(it) },
                                        )
                                    }
                                }
                            }

                            // ════════════════════════════════════════════
                            //  SYSTEM — 权限与通知 · 交互体验 · 连接与诊断
                            // ════════════════════════════════════════════
                            SettingsCategory.SYSTEM -> {
                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "权限与通知",
                                            subtitle = "查看照片、通知和系统媒体相关状态。",
                                        )
                                        SettingsInfoRow(
                                            title = "系统媒体访问",
                                            subtitle = "系统媒体工具区会根据权限结果显示空态、错误态或授权提示。",
                                            value = systemMediaAccessValue,
                                        )
                                        YingShiStateLayer(
                                            title = "通知权限",
                                            body = if (notificationPermissionValue == "已开启") {
                                                "系统通知已开启，点这里可进入系统通知设置。"
                                            } else {
                                                "点击向系统申请通知权限；如果曾经拒绝，可进入系统设置重新开启。"
                                            },
                                            tone = if (notificationPermissionValue == "已开启") YingShiNoticeTone.SUCCESS else YingShiNoticeTone.WARNING,
                                            actionLabel = notificationPermissionValue,
                                            onAction = ::requestOrOpenNotificationPermission,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        YingShiStateLayer(
                                            title = "推送设备注册",
                                            body = pushTokenDiagnostic.detail,
                                            tone = when (pushTokenDiagnostic.status) {
                                                "已注册" -> YingShiNoticeTone.SUCCESS
                                                "注册中" -> YingShiNoticeTone.INFO
                                                else -> YingShiNoticeTone.WARNING
                                            },
                                            actionLabel = pushTokenDiagnostic.status,
                                            onAction = { PushTokenRegistrar.registerCurrentTokenIfPossible(context, forceRetry = true) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        YingShiStateLayer(
                                            title = "最近推送诊断",
                                            body = pushDiagnostics.detail,
                                            tone = when (pushDiagnostics.status) {
                                                "正常" -> YingShiNoticeTone.SUCCESS
                                                "无数据" -> YingShiNoticeTone.INFO
                                                else -> YingShiNoticeTone.WARNING
                                            },
                                            actionLabel = pushDiagnostics.status,
                                            onAction = {
                                                coroutineScope.launch {
                                                    SettingsRepository.refreshPushDiagnosticsFromRemote()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        SettingsSwitchRow(
                                            title = "照片内容更新推送",
                                            subtitle = "默认开启，提醒对方新增或调整了照片内容。",
                                            checked = pushPreferences.photosContentUpdate,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("photos", "content_update", checked) {
                                                    it.copy(photosContentUpdate = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "照片评论推送",
                                            subtitle = "默认开启，评论会同时进入通知中心。",
                                            checked = pushPreferences.photosComment,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("photos", "comment", checked) {
                                                    it.copy(photosComment = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "照片删除推送",
                                            subtitle = "默认关闭，回收站变动仍会保留在通知中心。",
                                            checked = pushPreferences.photosDelete,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("photos", "delete", checked) {
                                                    it.copy(photosDelete = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "照片系统推送",
                                            subtitle = "默认关闭，包含传输和维护类提醒。",
                                            checked = pushPreferences.photosSystem,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("photos", "system", checked) {
                                                    it.copy(photosSystem = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "今日痕迹推送",
                                            subtitle = "默认开启，点击会回到生活模块的今日痕迹。",
                                            checked = pushPreferences.lifeTrace,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("life", "trace", checked) {
                                                    it.copy(lifeTrace = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "记账推送",
                                            subtitle = "默认关闭，记账历史仍可在生活模块查看。",
                                            checked = pushPreferences.lifeLedger,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("life", "ledger", checked) {
                                                    it.copy(lifeLedger = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "聊天导入推送",
                                            subtitle = "默认关闭，导入结果仍会留在通知中心。",
                                            checked = pushPreferences.lifeChat,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("life", "chat", checked) {
                                                    it.copy(lifeChat = checked)
                                                }
                                            },
                                        )
                                        SettingsSwitchRow(
                                            title = "生活系统推送",
                                            subtitle = "默认关闭，包含同步和维护类提醒。",
                                            checked = pushPreferences.lifeSystem,
                                            onCheckedChange = { checked ->
                                                updatePushPreference("life", "system", checked) {
                                                    it.copy(lifeSystem = checked)
                                                }
                                            },
                                        )
                                    }
                                }

                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "交互体验",
                                            subtitle = "把触感和动效控制在有用的范围里，不打断浏览。",
                                        )
                                        SettingsSwitchRow(
                                            title = "触感反馈",
                                            subtitle = "保留关键点击反馈，不做每一步都震动。",
                                            checked = interactionPreferences.hapticEnabled,
                                            onCheckedChange = { SettingsRepository.updateHapticEnabled(it) },
                                        )
                                        SettingsSwitchRow(
                                            title = "动效跟随系统减少动画",
                                            subtitle = "开启后遵循系统动画开关，关闭后始终保留轻量动效。",
                                            checked = interactionPreferences.followSystemReducedMotion,
                                            onCheckedChange = { SettingsRepository.updateFollowSystemReducedMotion(it) },
                                        )
                                    }
                                }

                                YingShiMistCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .yingShiRouteReveal(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                                    ) {
                                        SettingsSectionHeader(
                                            title = "连接与诊断",
                                            subtitle = "查看服务地址、连接状态和缓存只读兜底情况。",
                                        )
                                        YingShiEntryCard(
                                            title = "连接设置",
                                            subtitle = "查看服务地址、重新连接和当前兜底状态。",
                                            icon = Icons.Rounded.Cloud,
                                            onClick = { onOpenBackendDiagnostics(BackendDiagnosticsRoute(source = "settings")) },
                                        )
                                        YingShiEntryCard(
                                            title = "退出登录",
                                            subtitle = "清除当前账号会话",
                                            icon = Icons.Rounded.Login,
                                            backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f),
                                            onClick = onLogout,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Helper functions ──

private fun resolveSharedSpaceStatus(currentUser: RemoteCurrentUser?): String {
    return when {
        !AuthSessionManager.isLoggedIn && !OfflineAccessManager.state.isReadOnly -> "未登录"
        currentUser == null -> if (OfflineAccessManager.state.isReadOnly) "缓存资料" else "读取中"
        currentUser.partner != null -> currentUser.libraryDisplayName?.takeIf { it.isNotBlank() } ?: "双人空间"
        else -> "仅当前账号"
    }
}

private fun resolveNotificationPermissionStatus(context: Context): String {
    val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    return when {
        !notificationPermissionGranted -> "未授权"
        notificationsEnabled -> "已开启"
        else -> "已关闭"
    }
}

// ── Private composables ──

@Composable
private fun SettingsTopBar(
    source: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .yingShiSoftReveal(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsCircleButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.titleAccent,
            )
            Text(
                text = source.toSettingsSourceLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    subtitle: String,
) {
    val colors = YingShiThemeTokens.colors

    Column(verticalArrangement = Arrangement.spacedBy(YingShiThemeTokens.spacing.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.titleAccent,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    subtitle: String,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            options.forEach { option ->
                val selected = option == selectedOption
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.capsule))
                        .yingShiHapticClickable(shape = RoundedCornerShape(radius.capsule), pressedScale = 0.96f) {
                            onOptionSelected(option)
                        },
                    shape = RoundedCornerShape(radius.capsule),
                    color = if (selected) {
                        colors.primaryContainer.copy(alpha = 0.68f)
                    } else {
                        colors.sectionBackground.copy(alpha = 0.50f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            colors.glassStroke.copy(alpha = 0.58f)
                        } else {
                            colors.dividerSoft.copy(alpha = 0.56f)
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (selected) Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            colors.raisedSurface.copy(alpha = 0.34f),
                                            colors.glowWash.copy(alpha = 0.18f),
                                        ),
                                    ),
                                ) else Modifier,
                            )
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                    ) {
                        Text(
                            text = optionLabel(option),
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = if (selected) {
                                colors.titleAccent
                            } else {
                                colors.textSecondary
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.raisedSurface,
                    checkedTrackColor = colors.primaryContainer.copy(alpha = 0.96f),
                    checkedBorderColor = colors.glassStroke.copy(alpha = 0.82f),
                    uncheckedThumbColor = colors.raisedSurface,
                    uncheckedTrackColor = colors.dividerSoft.copy(alpha = 0.72f),
                    uncheckedBorderColor = colors.dividerSoft,
                ),
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    value: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val colors = YingShiThemeTokens.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = colors.sectionBackground.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.44f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.softGreenAction,
            )
        }
    }
}

@Composable
private fun SettingsCircleButton(
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier.yingShiHapticClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.66f)),
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = colors.titleAccent,
            )
        }
    }
}

private fun String.toSettingsSourceLabel(): String {
    return when (this) {
        "notification-center" -> "通知中心"
        "notification-center-topbar" -> "通知中心顶部"
        "photos-home" -> "照片页"
        "my-page" -> "我的页"
        else -> this
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    YingShiTheme {
        SettingsScreen(
            route = SettingsRoute(),
            onBack = { },
            onOpenBackendDiagnostics = { },
            onOpenCacheManagement = { },
            onLogout = { },
        )
    }
}
