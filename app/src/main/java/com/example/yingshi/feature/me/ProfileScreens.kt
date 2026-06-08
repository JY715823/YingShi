package com.example.yingshi.feature.me

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.remote.result.isUnauthorized
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.photos.BackendInlineNotice
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.components.YingShiNoticeTone
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val TITLE_PROFILE = "\u4e2a\u4eba\u4e3b\u9875"
private const val SUMMARY_PROFILE = ""
private const val TITLE_EDIT = "\u7f16\u8f91\u8d44\u6599"
private const val SUMMARY_EDIT = ""
private const val TEXT_BIO_EMPTY = "\u6682\u672a\u8bbe\u7f6e\u7b80\u4ecb\u3002"
private const val TITLE_PARTNER = "\u53e6\u4e00\u534a"
private const val SUMMARY_PARTNER = "\u4e00\u8d77\u8bb0\u5f55\u3001\u4e00\u8d77\u56de\u770b\uff0c\u8fd9\u91cc\u662f\u4f60\u4eec\u5171\u540c\u7a7a\u95f4\u91cc\u7684\u53e6\u4e00\u4f4d\u3002"
private const val TITLE_SHARED_SPACE = "\u6211\u4eec\u7684\u5c0f\u7a7a\u95f4"
private const val SUMMARY_SHARED_SPACE = "\u76ee\u524d\u770b\u5230\u7684\u7167\u7247\u3001\u76f8\u518c\u3001\u5e16\u5b50\u548c\u8bc4\u8bba\uff0c\u90fd\u9ed8\u8ba4\u5c5e\u4e8e\u4f60\u4eec\u4e24\u4e2a\u4eba\u7684\u5171\u540c\u7a7a\u95f4\u3002"
private const val LABEL_ACCOUNT = "\u8d26\u53f7"
private const val LABEL_JOINED_AT = "\u52a0\u5165\u65f6\u95f4"
private const val LABEL_PARTNER_ACCOUNT = "\u5bf9\u65b9\u8d26\u53f7"
private const val LABEL_DISPLAY_NAME = "\u6635\u79f0"
private const val LABEL_BIO = "\u7b80\u4ecb"
private const val ACTION_EDIT = "\u7f16\u8f91\u8d44\u6599"
private const val ACTION_CANCEL = "\u53d6\u6d88"
private const val ACTION_SAVE = "\u4fdd\u5b58"
private const val ACTION_SAVING = "\u4fdd\u5b58\u4e2d..."
private const val ACTION_UPDATE_AVATAR = "\u66f4\u6362\u5934\u50cf"
private const val ACTION_UPLOADING_AVATAR = "\u4e0a\u4f20\u5934\u50cf\u4e2d..."
private const val MESSAGE_SAVED = "\u8d44\u6599\u5df2\u4fdd\u5b58"
private const val MESSAGE_AVATAR_UPDATED = "\u5934\u50cf\u5df2\u66f4\u65b0"
private const val MESSAGE_AVATAR_PICK_CANCELLED = "\u5df2\u53d6\u6d88\u9009\u62e9\u5934\u50cf"
private const val MESSAGE_AVATAR_PICK_FAILED = "\u65e0\u6cd5\u8bfb\u53d6\u9009\u4e2d\u7684\u5934\u50cf"
private const val TEXT_UNFILLED = "\u672a\u586b\u5199"
private const val TEXT_UNRECORDED = "\u672a\u8bb0\u5f55"
private const val FALLBACK_AVATAR_FILE_NAME = "avatar.jpg"
private const val FALLBACK_AVATAR_MIME_TYPE = "image/jpeg"

data class PersonalProfileRoute(
    val source: String = "my-page",
)

data class EditProfileRoute(
    val source: String = "personal-profile",
)

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

    ShellPage(
        title = "",
        summary = "",
        onBack = null,
        modifier = modifier.fillMaxSize(),
        headerContent = {
            ProfileInlineHeader(
                title = TITLE_PROFILE,
                onBack = onBack,
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                    color = colors.raisedSurface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(
                                name = currentUser.displayName,
                                avatarUrl = currentUser.avatarUrl,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                Text(
                                    text = currentUser.displayName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = currentUser.account,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }

                        Text(
                            text = currentUser.bio?.takeIf { it.isNotBlank() } ?: TEXT_BIO_EMPTY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
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

                PartnerSection(
                    partner = currentUser.partner,
                )

                Button(
                    onClick = onOpenEditProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        disabledContainerColor = colors.sectionBackground,
                        disabledContentColor = colors.textSecondary,
                    ),
                    border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
                ) {
                    Text(ACTION_EDIT)
                }
            }
        },
    )
}

@Composable
private fun PartnerSection(
    partner: RemotePartnerProfile?,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    val displayName = partner?.displayName?.takeIf { it.isNotBlank() } ?: TITLE_PARTNER
    val account = partner?.account?.takeIf { it.isNotBlank() } ?: TEXT_UNFILLED
    val bio = partner?.bio?.takeIf { it.isNotBlank() } ?: SUMMARY_PARTNER

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
        color = colors.raisedSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    name = displayName,
                    avatarUrl = partner?.avatarUrl,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    Text(
                        text = TITLE_PARTNER,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.memoryAccent,
                    )
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

            ProfileInfoRow(label = LABEL_PARTNER_ACCOUNT, value = account)
            ProfileInfoRow(label = LABEL_BIO, value = bio)
        }
    }
}

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
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            onShowNotice(MESSAGE_AVATAR_PICK_CANCELLED, YingShiNoticeTone.INFO)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isUploadingAvatar = true
            errorMessage = null
            val uploadResult = runCatching {
                RepositoryProvider.authRepository.uploadCurrentUserAvatar(
                    fileName = uri.resolvePickedDisplayName(context),
                    mimeType = context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
                        ?: FALLBACK_AVATAR_MIME_TYPE,
                    fileSizeBytes = uri.resolvePickedSizeBytes(context) ?: 0L,
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

    ShellPage(
        title = "",
        summary = "",
        onBack = null,
        modifier = modifier.fillMaxSize(),
        headerContent = {
            ProfileInlineHeader(
                title = TITLE_EDIT,
                onBack = onBack,
            )
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(
                        name = displayName.ifBlank { currentUser.displayName },
                        avatarUrl = currentUser.avatarUrl,
                    )
                    OutlinedButton(
                        onClick = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        enabled = !isSaving && !isUploadingAvatar,
                    ) {
                        Text(if (isUploadingAvatar) ACTION_UPLOADING_AVATAR else ACTION_UPDATE_AVATAR)
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { newValue -> displayName = newValue },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(LABEL_DISPLAY_NAME) },
                    singleLine = true,
                    enabled = !isSaving && !isUploadingAvatar,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { newValue -> bio = newValue },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(LABEL_BIO) },
                    minLines = 4,
                    maxLines = 6,
                    enabled = !isSaving && !isUploadingAvatar,
                )

                errorMessage?.let {
                    BackendInlineNotice(
                        text = it,
                        emphasized = true,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isSaving && !isUploadingAvatar,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(ACTION_CANCEL)
                    }
                    Button(
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
                        enabled = !isSaving && !isUploadingAvatar && displayName.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                            disabledContainerColor = colors.sectionBackground,
                            disabledContentColor = colors.textSecondary,
                        ),
                        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.72f)),
                    ) {
                        Text(if (isSaving) ACTION_SAVING else ACTION_SAVE)
                    }
                }
            }
        },
    )
}

@Composable
private fun ProfileInlineHeader(
    title: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
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
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.titleAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.titleAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val colors = YingShiThemeTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        Text(
            text = value.ifBlank { TEXT_UNFILLED },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
}

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
