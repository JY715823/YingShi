package com.example.yingshi.feature.me

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.result.isUnauthorized
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.BackendInlineNotice
import com.example.yingshi.ui.components.YingShiMistBackground
import com.example.yingshi.ui.components.YingShiMistCard
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.components.YingShiPrimaryMistButton
import com.example.yingshi.ui.components.YingShiTextField
import com.example.yingshi.ui.components.yingShiRouteReveal
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val TITLE_PROFILE = "个人主页"
private const val TITLE_EDIT = "编辑资料"
private const val TEXT_BIO_EMPTY = "暂未设置简介。"
private const val TITLE_PARTNER = "另一半"
private const val SUMMARY_PARTNER = "一起记录、一起回看，这里是你们共同空间里的另一位。"
private const val LABEL_DISPLAY_NAME = "昵称"
private const val LABEL_BIO = "简介"
private const val ACTION_EDIT = "编辑资料"
private const val ACTION_CANCEL = "取消"
private const val ACTION_SAVE = "保存"
private const val ACTION_SAVING = "保存中..."
private const val ACTION_UPDATE_AVATAR = "更换头像"
private const val ACTION_UPLOADING_AVATAR = "上传头像中..."
private const val MESSAGE_SAVED = "资料已保存"
private const val MESSAGE_AVATAR_UPDATED = "头像已更新"
private const val MESSAGE_AVATAR_PICK_CANCELLED = "已取消选择头像"
private const val MESSAGE_AVATAR_PICK_FAILED = "无法读取选中的头像"
private const val MESSAGE_AVATAR_TOO_LARGE = "头像文件不能超过 10MB"
private const val TEXT_UNFILLED = "未填写"
private const val TEXT_UNRECORDED = "未记录"
private const val FALLBACK_AVATAR_FILE_NAME = "avatar.jpg"
private const val FALLBACK_AVATAR_MIME_TYPE = "image/jpeg"
private const val MAX_AVATAR_FILE_SIZE_BYTES = 10L * 1024 * 1024

data class PersonalProfileRoute(
    val source: String = "my-page",
)

data class EditProfileRoute(
    val source: String = "personal-profile",
)

// ─────────────────────────────────────────────────────────────
//  PersonalProfileScreen — 个人主页
// ─────────────────────────────────────────────────────────────

@Composable
fun PersonalProfileScreen(
    currentUser: RemoteCurrentUser,
    isOfflineReadOnly: Boolean,
    isRefreshing: Boolean,
    refreshErrorMessage: String?,
    onBack: () -> Unit,
    onOpenEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val scrollState = rememberScrollState()

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = com.example.yingshi.ui.components.YingShiBackdropVariant.ME,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // ── Header: back + title ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.sectionBackground.copy(alpha = 0.80f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.titleAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = TITLE_PROFILE,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Profile card ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .yingShiRouteReveal(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            ) {
                Column {
                    // Cover gradient bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.primaryContainer.copy(alpha = 0.55f),
                                        colors.memoryContainer.copy(alpha = 0.40f),
                                        colors.glowWash.copy(alpha = 0.50f),
                                    ),
                                ),
                            ),
                    )

                    // Avatar overlapping cover
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-44).dp)
                            .padding(horizontal = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        GradientRingAvatar(
                            name = currentUser.displayName,
                            avatarUrl = currentUser.avatarUrl,
                            avatarSize = 100.dp,
                        )
                    }

                    // Name + account + bio
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-32).dp)
                            .padding(horizontal = spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        Text(
                            text = currentUser.displayName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                        )
                        Text(
                            text = currentUser.account,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                        if (!currentUser.bio.isNullOrBlank()) {
                            Text(
                                text = currentUser.bio,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = spacing.sm),
                            )
                        } else {
                            Text(
                                text = TEXT_BIO_EMPTY,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                    }

                    // Status hints
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-24).dp)
                            .padding(horizontal = spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                    ) {
                        if (isRefreshing) {
                            Text(
                                text = "正在同步最新资料...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.titleAccent,
                            )
                        } else if (isOfflineReadOnly && refreshErrorMessage.isNullOrBlank()) {
                            Text(
                                text = "当前显示的是缓存资料，恢复连接后会自动刷新。",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        } else if (!refreshErrorMessage.isNullOrBlank()) {
                            Text(
                                text = refreshErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // ── Partner section ──
            PartnerSection(
                partner = currentUser.partner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .yingShiSoftReveal(),
            )

            // ── Edit button ──
            YingShiPrimaryMistButton(
                text = ACTION_EDIT,
                onClick = onOpenEditProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .padding(top = spacing.sm, bottom = spacing.xl),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PartnerSection — 暖色差异化
// ─────────────────────────────────────────────────────────────

@Composable
private fun PartnerSection(
    partner: RemotePartnerProfile?,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val displayName = partner?.displayName?.takeIf { it.isNotBlank() } ?: TITLE_PARTNER
    val account = partner?.account?.takeIf { it.isNotBlank() } ?: TEXT_UNFILLED
    val bio = partner?.bio?.takeIf { it.isNotBlank() } ?: SUMMARY_PARTNER

    YingShiMistCard(
        modifier = modifier,
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.memoryWash.copy(alpha = 0.55f),
        borderColor = colors.memoryAccent.copy(alpha = 0.18f),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Header with heart icon
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = colors.memoryAccent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = TITLE_PARTNER,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.memoryAccent,
                )
            }

            // Partner avatar + info
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientRingAvatar(
                    name = displayName,
                    avatarUrl = partner?.avatarUrl,
                    avatarSize = 64.dp,
                    ringWidth = 3.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = account,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            // Bio
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary.copy(alpha = 0.85f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  EditProfileScreen — 编辑资料
// ─────────────────────────────────────────────────────────────

@Composable
fun EditProfileScreen(
    currentUser: RemoteCurrentUser,
    onBack: () -> Unit,
    onProfileSaved: (RemoteCurrentUser) -> Unit,
    onSessionExpired: (String) -> Unit,
    onShowNotice: (String, YingShiNoticeTone) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var displayName by rememberSaveable(currentUser.userId) { mutableStateOf(currentUser.displayName) }
    var bio by rememberSaveable(currentUser.userId) { mutableStateOf(currentUser.bio.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            onShowNotice(MESSAGE_AVATAR_PICK_CANCELLED, YingShiNoticeTone.INFO)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val fileSize = uri.resolvePickedSizeBytes(context) ?: 0L
            if (fileSize > MAX_AVATAR_FILE_SIZE_BYTES) {
                onShowNotice(MESSAGE_AVATAR_TOO_LARGE, YingShiNoticeTone.WARNING)
                return@launch
            }
            isUploadingAvatar = true
            errorMessage = null
            val uploadResult = runCatching {
                RepositoryProvider.authRepository.uploadCurrentUserAvatar(
                    fileName = uri.resolvePickedDisplayName(context),
                    mimeType = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
                        ?: FALLBACK_AVATAR_MIME_TYPE,
                    fileSizeBytes = fileSize,
                    openInputStream = {
                        context.contentResolver.openInputStream(uri) ?: error(MESSAGE_AVATAR_PICK_FAILED)
                    },
                )
            }.getOrElse {
                ApiResult.Error(
                    code = "AUTH_AVATAR_PICK_FAILED",
                    message = MESSAGE_AVATAR_PICK_FAILED,
                    throwable = it,
                )
            }
            when (uploadResult) {
                is ApiResult.Success -> {
                    onProfileSaved(uploadResult.data)
                    onShowNotice(MESSAGE_AVATAR_UPDATED, YingShiNoticeTone.SUCCESS)
                }
                is ApiResult.Error -> {
                    if (uploadResult.isUnauthorized()) {
                        onSessionExpired(uploadResult.message)
                    } else {
                        errorMessage = uploadResult.message
                    }
                }
                ApiResult.Loading -> Unit
            }
            isUploadingAvatar = false
        }
    }

    YingShiMistBackground(
        modifier = modifier.fillMaxSize(),
        showWaves = true,
        variant = com.example.yingshi.ui.components.YingShiBackdropVariant.ME,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // ── Header: back + title ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.sectionBackground.copy(alpha = 0.80f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.titleAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = TITLE_EDIT,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.titleAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Form card ──
            YingShiMistCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .yingShiRouteReveal(),
                shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    // Cover gradient bar (decorative top)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colors.primaryContainer.copy(alpha = 0.45f),
                                        colors.memoryContainer.copy(alpha = 0.35f),
                                        colors.glowWash.copy(alpha = 0.40f),
                                    ),
                                ),
                            ),
                    )

                    // Centered avatar with gradient ring + tap to change
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-28).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.clickable(
                                enabled = !isSaving && !isUploadingAvatar,
                                onClick = {
                                    avatarPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            ),
                        ) {
                            GradientRingAvatar(
                                name = displayName.ifBlank { currentUser.displayName },
                                avatarUrl = currentUser.avatarUrl,
                                avatarSize = 96.dp,
                            )
                        }
                    }

                    // "更换头像" hint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isUploadingAvatar) ACTION_UPLOADING_AVATAR else ACTION_UPDATE_AVATAR,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.titleAccent,
                        )
                    }

                    // Name field
                    YingShiTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "输入昵称",
                        icon = Icons.Rounded.Person,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs),
                        enabled = !isSaving && !isUploadingAvatar,
                        singleLine = true,
                    )

                    // Bio field (multi-line, custom styled)
                    EditBioField(
                        value = bio,
                        onValueChange = { bio = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving && !isUploadingAvatar,
                    )

                    // Error message
                    errorMessage?.let {
                        BackendInlineNotice(
                            text = it,
                            emphasized = true,
                        )
                    }

                    // Button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    enabled = !isSaving && !isUploadingAvatar,
                                    onClick = onBack,
                                ),
                            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                            color = colors.sectionBackground.copy(alpha = 0.82f),
                            border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
                        ) {
                            Box(
                                modifier = Modifier.padding(
                                    horizontal = spacing.lg,
                                    vertical = 14.dp,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ACTION_CANCEL,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        YingShiPrimaryMistButton(
                            text = if (isSaving) ACTION_SAVING else ACTION_SAVE,
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    errorMessage = null
                                    when (
                                        val result = RepositoryProvider.authRepository.updateCurrentUserProfile(
                                            UpdateProfileRequestDto(
                                                displayName = displayName.trim(),
                                                bio = bio.trim().takeIf { it.isNotBlank() },
                                            ),
                                        )
                                    ) {
                                        is ApiResult.Success -> {
                                            onProfileSaved(result.data)
                                            onShowNotice(MESSAGE_SAVED, YingShiNoticeTone.SUCCESS)
                                            onBack()
                                        }
                                        is ApiResult.Error -> {
                                            if (result.isUnauthorized()) {
                                                onSessionExpired(result.message)
                                            } else {
                                                errorMessage = result.message
                                            }
                                        }
                                        ApiResult.Loading -> Unit
                                    }
                                    isSaving = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = displayName.trim().isNotBlank(),
                            loading = isSaving,
                        )
                    }
                }
            }

            // Bottom spacer
            Box(modifier = Modifier.padding(bottom = spacing.xl))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  EditBioField — multi-line styled text field
// ─────────────────────────────────────────────────────────────

@Composable
private fun EditBioField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = YingShiThemeTokens.colors
    val radius = YingShiThemeTokens.radius
    val spacing = YingShiThemeTokens.spacing
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.lg),
        color = colors.raisedSurface.copy(alpha = 0.82f),
        border = BorderStroke(
            width = 1.dp,
            color = if (isFocused) colors.glassStroke.copy(alpha = 0.92f) else colors.dividerSoft.copy(alpha = 0.82f),
        ),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = LABEL_BIO,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.dividerSoft.copy(alpha = 0.50f)),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                enabled = enabled,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isBlank()) {
                            Text(
                                text = "写点什么介绍自己...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary.copy(alpha = 0.60f),
                            )
                        }
                        innerTextField()
                    }
                },
                minLines = 3,
                maxLines = 6,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────

private fun formatEpochMillis(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) {
        return TEXT_UNRECORDED
    }
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }.getOrDefault(TEXT_UNRECORDED)
}

private fun Uri.resolvePickedDisplayName(context: Context): String {
    return context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0) cursor.getString(columnIndex) else null
        } else {
            null
        }
    }.orEmpty().ifBlank { FALLBACK_AVATAR_FILE_NAME }
}

private fun Uri.resolvePickedSizeBytes(context: Context): Long? {
    return context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (columnIndex >= 0 && !cursor.isNull(columnIndex)) cursor.getLong(columnIndex) else null
        } else {
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PersonalProfileScreenPreview() {
    YingShiTheme {
        PersonalProfileScreen(
            currentUser = RemoteCurrentUser(
                userId = "user_demo_a",
                account = "demo.a@yingshi.local",
                displayName = "映世小屋",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "我们的小空间",
                bio = "一起把平常日子慢慢收进这座小小相册。",
                partner = RemotePartnerProfile(
                    userId = "user_demo_b",
                    account = "demo.b@yingshi.local",
                    displayName = "另一半",
                    avatarUrl = null,
                    bio = "把生活里的闪光片段，也把安静和想念一起留下来。",
                ),
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            isOfflineReadOnly = false,
            isRefreshing = false,
            refreshErrorMessage = null,
            onBack = {},
            onOpenEditProfile = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditProfileScreenPreview() {
    YingShiTheme {
        EditProfileScreen(
            currentUser = RemoteCurrentUser(
                userId = "user_demo_a",
                account = "demo.a@yingshi.local",
                displayName = "映世小屋",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "我们的小空间",
                bio = "一起把平常日子慢慢收进这座小小相册。",
                partner = RemotePartnerProfile(
                    userId = "user_demo_b",
                    account = "demo.b@yingshi.local",
                    displayName = "另一半",
                    avatarUrl = null,
                    bio = "把生活里的闪光片段，也把安静和想念一起留下来。",
                ),
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            onBack = {},
            onProfileSaved = {},
            onSessionExpired = {},
        )
    }
}
