package com.example.yingshi.feature.me

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.remote.dto.UpdateProfileRequestDto
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.ui.components.ShellPage
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val TITLE_PROFILE = "\u4e2a\u4eba\u4e3b\u9875"
private const val SUMMARY_PROFILE =
    "\u8fd9\u91cc\u662f\u4f60\u4eec\u5171\u4eab\u7a7a\u95f4\u91cc\u7684\u4e2a\u4eba\u5165\u53e3\uff0c\u53ea\u653e\u663e\u79f0\u3001\u7b80\u4ecb\u548c\u8f7b\u91cf\u8d26\u53f7\u4fe1\u606f\u3002"
private const val TITLE_EDIT = "\u7f16\u8f91\u8d44\u6599"
private const val SUMMARY_EDIT =
    "\u8fd9\u91cc\u53ea\u4fee\u6539\u6635\u79f0\u548c\u7b80\u4ecb\uff0c\u5934\u50cf\u7ee7\u7eed\u4f7f\u7528\u9ed8\u8ba4\u5360\u4f4d\u3002"
private const val TEXT_BIO_EMPTY = "\u6682\u672a\u8bbe\u7f6e\u7b80\u4ecb\u3002"
private const val LABEL_ACCOUNT = "\u8d26\u53f7"
private const val LABEL_JOINED_AT = "\u52a0\u5165\u65f6\u95f4"
private const val LABEL_ENV = "\u5f53\u524d\u73af\u5883"
private const val LABEL_DISPLAY_NAME = "\u6635\u79f0"
private const val LABEL_BIO = "\u7b80\u4ecb"
private const val ACTION_EDIT = "\u7f16\u8f91\u8d44\u6599"
private const val ACTION_CANCEL = "\u53d6\u6d88"
private const val ACTION_SAVE = "\u4fdd\u5b58"
private const val ACTION_SAVING = "\u4fdd\u5b58\u4e2d..."
private const val MESSAGE_SAVED = "\u8d44\u6599\u5df2\u4fdd\u5b58"
private const val TEXT_UNFILLED = "\u672a\u586b\u5199"
private const val TEXT_UNRECORDED = "\u672a\u8bb0\u5f55"

data class PersonalProfileRoute(
    val source: String = "my-page",
)

data class EditProfileRoute(
    val source: String = "personal-profile",
)

@Composable
fun PersonalProfileScreen(
    currentUser: RemoteCurrentUser,
    repositoryMode: RepositoryMode,
    baseUrl: String,
    onBack: () -> Unit,
    onOpenEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing

    ShellPage(
        title = TITLE_PROFILE,
        summary = SUMMARY_PROFILE,
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProfileAvatar(name = currentUser.displayName)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                            ) {
                                Text(
                                    text = currentUser.displayName,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = currentUser.account,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            text = currentUser.bio?.takeIf { it.isNotBlank() } ?: TEXT_BIO_EMPTY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YingShiThemeTokens.radius.xl),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        ProfileInfoRow(label = LABEL_ACCOUNT, value = currentUser.account)
                        ProfileInfoRow(label = LABEL_JOINED_AT, value = formatEpochMillis(currentUser.createdAtMillis))
                        ProfileInfoRow(label = LABEL_ENV, value = repositoryMode.name)
                        ProfileInfoRow(label = "baseUrl", value = baseUrl)
                    }
                }

                Button(
                    onClick = onOpenEditProfile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(ACTION_EDIT)
                }
            }
        },
    )
}

@Composable
fun EditProfileScreen(
    currentUser: RemoteCurrentUser,
    onBack: () -> Unit,
    onProfileSaved: (RemoteCurrentUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var displayName by rememberSaveable(currentUser.userId) { mutableStateOf(currentUser.displayName) }
    var bio by rememberSaveable(currentUser.userId) { mutableStateOf(currentUser.bio.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ShellPage(
        title = TITLE_EDIT,
        summary = SUMMARY_EDIT,
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { newValue -> displayName = newValue },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(LABEL_DISPLAY_NAME) },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { newValue -> bio = newValue },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(LABEL_BIO) },
                    minLines = 4,
                    maxLines = 6,
                    enabled = !isSaving,
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !isSaving,
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
                                        Toast.makeText(context, MESSAGE_SAVED, Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                    is ApiResult.Error -> {
                                        errorMessage = result.message
                                    }
                                    ApiResult.Loading -> Unit
                                }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving && displayName.trim().isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isSaving) ACTION_SAVING else ACTION_SAVE)
                    }
                }
            }
        },
    )
}

@Composable
private fun ProfileAvatar(
    name: String,
) {
    val avatarLabel = name.firstOrNull()?.uppercaseChar()?.toString() ?: "Y"
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Text(
            text = avatarLabel,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
) {
    val spacing = YingShiThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { TEXT_UNFILLED },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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

@Preview(showBackground = true)
@Composable
private fun PersonalProfileScreenPreview() {
    YingShiTheme {
        PersonalProfileScreen(
            currentUser = RemoteCurrentUser(
                userId = "user_demo_a",
                account = "demo.a@yingshi.local",
                displayName = "Demo A",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "YingShi Shared Library",
                bio = "\u6e29\u67d4\u8bb0\u5f55\u65e5\u5e38\uff0c\u548c\u53e6\u4e00\u534a\u5171\u4eab\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002",
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            repositoryMode = RepositoryMode.REAL,
            baseUrl = "http://10.0.2.2:8080/",
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
                displayName = "Demo A",
                avatarUrl = null,
                libraryId = "library_shared",
                libraryDisplayName = "YingShi Shared Library",
                bio = "\u6e29\u67d4\u8bb0\u5f55\u65e5\u5e38\uff0c\u548c\u53e6\u4e00\u534a\u5171\u4eab\u8fd9\u5ea7\u5c0f\u5c0f\u76f8\u518c\u3002",
                createdAtMillis = 1760000000000L,
                updatedAtMillis = 1760000000000L,
            ),
            onBack = {},
            onProfileSaved = {},
        )
    }
}
