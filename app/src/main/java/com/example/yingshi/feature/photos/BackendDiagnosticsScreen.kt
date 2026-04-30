package com.example.yingshi.feature.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.data.model.AuthTokens
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory
import com.example.yingshi.data.remote.dto.CreateCommentRequestDto
import com.example.yingshi.data.remote.dto.CreateUploadTokenRequestDto
import com.example.yingshi.data.remote.dto.LoginRequestDto
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class BackendDiagnosticsRoute(
    val source: String = "settings",
)

@Composable
fun BackendDiagnosticsScreen(
    route: BackendDiagnosticsRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = YingShiThemeTokens.spacing
    val settings = BackendDebugConfig.settings
    val scope = rememberCoroutineScope()
    var baseUrlInput by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var accountInput by rememberSaveable { mutableStateOf("demo.a@yingshi.local") }
    var passwordInput by rememberSaveable { mutableStateOf("demo123456") }
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("No diagnostics run yet.") }

    LaunchedEffect(settings.baseUrl) {
        if (baseUrlInput != settings.baseUrl) {
            baseUrlInput = settings.baseUrl
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        DiagnosticsTopBar(
            source = route.source,
            onBack = onBack,
        )

        DiagnosticsSection(
            title = "Connection",
            subtitle = "Debug defaults to 10.0.2.2:8080. For device testing, switch to 127.0.0.1 or your computer LAN IP.",
        ) {
            OutlinedTextField(
                value = baseUrlInput,
                onValueChange = { baseUrlInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
            )
            DiagnosticsActionRow(
                title = "Save Base URL",
                subtitle = "Saving rebuilds Retrofit immediately, and newly created real repositories will follow it.",
                actionLabel = if (isRunning) "Busy" else "Save",
                enabled = !isRunning,
                onClick = {
                    BackendDebugConfig.updateBaseUrl(baseUrlInput)
                    lastResult = "Saved base URL: ${BackendDebugConfig.currentBaseUrl()}"
                },
            )
            DiagnosticsDualActionRow(
                primaryLabel = "Emulator 10.0.2.2",
                onPrimaryClick = {
                    baseUrlInput = RemoteConfig.DEBUG_EMULATOR_BASE_URL
                    BackendDebugConfig.updateBaseUrl(baseUrlInput)
                    lastResult = "Switched to emulator preset: ${BackendDebugConfig.currentBaseUrl()}"
                },
                secondaryLabel = "Device 127.0.0.1",
                onSecondaryClick = {
                    baseUrlInput = RemoteConfig.DEBUG_DEVICE_LOOPBACK_BASE_URL
                    BackendDebugConfig.updateBaseUrl(baseUrlInput)
                    lastResult = "Switched to device preset: ${BackendDebugConfig.currentBaseUrl()}"
                },
            )
            DiagnosticsInfoRow(
                title = "Active base URL",
                value = RemoteServiceFactory.currentBaseUrl(),
                subtitle = "For same-Wi-Fi device testing, replace this with your computer LAN IP.",
            )
        }

        DiagnosticsSection(
            title = "Repository Mode",
            subtitle = "Keep fake as the default app path. Switch to real only when you want fresh repositories to hit the backend.",
        ) {
            RepositoryModeRow(
                selectedMode = settings.repositoryMode,
                onSelected = { BackendDebugConfig.updateRepositoryMode(it) },
            )
            DiagnosticsInfoRow(
                title = "Current mode",
                value = settings.repositoryMode.name,
                subtitle = "This diagnostics page calls the backend directly, so it is not blocked by fake data.",
            )
        }

        DiagnosticsSection(
            title = "Credentials",
            subtitle = "The default values use the dev seed account. Login stores the token in the current app session.",
        ) {
            OutlinedTextField(
                value = accountInput,
                onValueChange = { accountInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Account") },
                singleLine = true,
            )
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
            )
            DiagnosticsActionRow(
                title = "Login and verify /me",
                subtitle = "Runs login first, then calls /api/auth/me to verify the session.",
                actionLabel = if (isRunning) "Busy" else "Login",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("login") {
                            val loginResponse = loginWithCredentials(accountInput, passwordInput)
                            val currentUser = RemoteServiceFactory.authApi.getCurrentUser().data
                            "displayName=${loginResponse.displayName}, spaceId=${currentUser.spaceId}"
                        }
                        isRunning = false
                    }
                },
            )
            DiagnosticsInfoRow(
                title = "Token state",
                value = if (AuthSessionManager.isLoggedIn) "Logged in" else "Logged out",
                subtitle = "If this stays logged out, check whether the backend is running and whether the base URL is correct.",
            )
        }

        DiagnosticsSection(
            title = "Requests",
            subtitle = "Trash runs a delete-then-restore flow so the seeded data is left as intact as possible.",
        ) {
            DiagnosticsActionRow(
                title = "Health",
                subtitle = "Calls /api/health to verify service reachability and cleartext handling.",
                actionLabel = if (isRunning) "Busy" else "Run",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("health") {
                            healthSummary()
                        }
                        isRunning = false
                    }
                },
            )
            DiagnosticsActionRow(
                title = "Albums and post detail",
                subtitle = "Reads albums, album posts, and post detail.",
                actionLabel = if (isRunning) "Busy" else "Run",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("albums/post") {
                            loginIfNeeded(accountInput, passwordInput)
                            val albums = RemoteServiceFactory.albumApi.getAlbums().data
                            val albumId = albums.firstOrNull()?.albumId ?: "album_001"
                            val posts = RemoteServiceFactory.albumApi.getAlbumPosts(albumId).data
                            val postId = posts.firstOrNull()?.postId ?: "post_001"
                            val detail = RemoteServiceFactory.postApi.getPostDetail(postId).data
                            "albums=${albums.size}, albumPosts=${posts.size}, post=${detail.postId}"
                        }
                        isRunning = false
                    }
                },
            )
            DiagnosticsActionRow(
                title = "Media and comments",
                subtitle = "Reads media feed, post comments, media comments, and creates one temporary media comment.",
                actionLabel = if (isRunning) "Busy" else "Run",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("media/comments") {
                            loginIfNeeded(accountInput, passwordInput)
                            val mediaFeed = RemoteServiceFactory.mediaApi.getMediaFeed().data
                            val mediaId = mediaFeed.firstOrNull()?.mediaId ?: "media_001"
                            val postComments = RemoteServiceFactory.commentApi.getPostComments("post_001", 1, 20).data
                            val mediaComments = RemoteServiceFactory.commentApi.getMediaComments(mediaId, 1, 20).data
                            val createdComment = RemoteServiceFactory.commentApi.createMediaComment(
                                mediaId = mediaId,
                                request = CreateCommentRequestDto(content = "Android diagnostics comment"),
                            ).data
                            RemoteServiceFactory.commentApi.deleteComment(createdComment.commentId)
                            "media=${mediaFeed.size}, postComments=${postComments.comments.size}, mediaComments=${mediaComments.comments.size}"
                        }
                        isRunning = false
                    }
                },
            )
            DiagnosticsActionRow(
                title = "Trash",
                subtitle = "Performs directory delete on post_001/media_002, then list, detail, and restore.",
                actionLabel = if (isRunning) "Busy" else "Run",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("trash") {
                            loginIfNeeded(accountInput, passwordInput)
                            trashSmokeSummary()
                        }
                        isRunning = false
                    }
                },
            )
            DiagnosticsActionRow(
                title = "Run all smoke actions",
                subtitle = "Runs health, login, albums, media, comments, upload, and trash for a quick device acceptance pass.",
                actionLabel = if (isRunning) "Busy" else "Run",
                enabled = !isRunning,
                onClick = {
                    scope.launch {
                        isRunning = true
                        lastResult = runStep("smoke") {
                            loginIfNeeded(accountInput, passwordInput)
                            val health = healthSummary()
                            val albums = RemoteServiceFactory.albumApi.getAlbums().data.size
                            val media = RemoteServiceFactory.mediaApi.getMediaFeed().data.size
                            val postComments = RemoteServiceFactory.commentApi.getPostComments("post_001", 1, 20).data.comments.size
                            val mediaComments = RemoteServiceFactory.commentApi.getMediaComments("media_001", 1, 20).data.comments.size
                            val upload = uploadSmokeSummary()
                            val trash = trashSmokeSummary()
                            "$health; albums=$albums; media=$media; postComments=$postComments; mediaComments=$mediaComments; $upload; $trash"
                        }
                        isRunning = false
                    }
                },
            )
        }

        DiagnosticsSection(
            title = "Last result",
            subtitle = "Each action writes its latest success or failure message here for device acceptance checks.",
        ) {
            DiagnosticsResultBlock(lastResult)
        }
    }
}

private suspend fun loginWithCredentials(account: String, password: String) = RemoteServiceFactory.authApi.login(
    LoginRequestDto(
        account = account.trim(),
        password = password,
    ),
).data.also { response ->
    AuthSessionManager.saveTokens(
        AuthTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            accessTokenExpireAtMillis = response.accessTokenExpireAtMillis,
            refreshTokenExpireAtMillis = response.refreshTokenExpireAtMillis,
        ),
    )
}

private suspend fun loginIfNeeded(account: String, password: String) {
    if (!AuthSessionManager.isLoggedIn) {
        loginWithCredentials(account, password)
    }
}

private suspend fun healthSummary(): String {
    val health = RemoteServiceFactory.healthApi.getHealth().data
    return "health=${health.status}, app=${health.application}"
}

private suspend fun uploadSmokeSummary(): String {
    val fileBytes = "android-upload".toByteArray()
    val tokenResponse = RemoteServiceFactory.uploadApi.createUploadToken(
        request = CreateUploadTokenRequestDto(
            fileName = "android-diagnostics.jpg",
            mimeType = "image/jpeg",
            fileSizeBytes = fileBytes.size.toLong(),
            mediaType = "image",
            width = 64,
            height = 64,
            durationMillis = null,
            displayTimeMillis = System.currentTimeMillis(),
        ),
    ).data
    val multipart = MultipartBody.Part.createFormData(
        "file",
        "android-diagnostics.jpg",
        fileBytes.toRequestBody("image/jpeg".toMediaType()),
    )
    val uploadResponse = RemoteServiceFactory.uploadApi.uploadFile(
        uploadId = tokenResponse.uploadId,
        file = multipart,
    ).data
    return "upload=${uploadResponse.state}/${uploadResponse.media.mediaId}"
}

private suspend fun trashSmokeSummary(): String {
    var trashItemId: String? = null
    return try {
        trashItemId = RemoteServiceFactory.mediaApi.deleteMediaFromPost(
            postId = "post_001",
            mediaId = "media_002",
            deleteMode = "directory",
        ).data.trashItemId
        val trashList = RemoteServiceFactory.trashApi.getTrashItems(itemType = "mediaRemoved").data
        val detail = RemoteServiceFactory.trashApi.getTrashDetail(trashItemId).data
        RemoteServiceFactory.trashApi.restoreTrashItem(trashItemId)
        "trash=${trashList.items.size}/$trashItemId/${detail.canRestore}"
    } finally {
        if (!trashItemId.isNullOrBlank()) {
            runCatching { RemoteServiceFactory.trashApi.restoreTrashItem(trashItemId) }
        }
    }
}

private suspend fun runStep(stepName: String, block: suspend () -> String): String {
    return runCatching { block() }.fold(
        onSuccess = { "[$stepName] success: $it" },
        onFailure = { "[$stepName] failed: ${it.message ?: it::class.java.simpleName}" },
    )
}

@Composable
private fun DiagnosticsTopBar(
    source: String,
    onBack: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DiagnosticsCircleButton(text = "<", onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Backend Diagnostics",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Source: $source",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.xl),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun DiagnosticsInfoRow(
    title: String,
    value: String,
    subtitle: String,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DiagnosticsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.lg))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.30f else 0.18f),
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsDualActionRow(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        DiagnosticsSmallAction(
            label = primaryLabel,
            modifier = Modifier.weight(1f),
            onClick = onPrimaryClick,
        )
        DiagnosticsSmallAction(
            label = secondaryLabel,
            modifier = Modifier.weight(1f),
            onClick = onSecondaryClick,
        )
    }
}

@Composable
private fun DiagnosticsSmallAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RepositoryModeRow(
    selectedMode: RepositoryMode,
    onSelected: (RepositoryMode) -> Unit,
) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        RepositoryMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radius.lg))
                    .clickable { onSelected(mode) },
                shape = RoundedCornerShape(radius.lg),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                ),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsResultBlock(text: String) {
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius

    Surface(
        shape = RoundedCornerShape(radius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DiagnosticsCircleButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BackendDiagnosticsScreenPreview() {
    YingShiTheme {
        BackendDiagnosticsScreen(
            route = BackendDiagnosticsRoute(),
            onBack = { },
        )
    }
}
