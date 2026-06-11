package com.example.yingshi.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.ledger.LedgerBookPickerSheet
import com.example.yingshi.feature.photos.AppContentMediaThumbnail
import com.example.yingshi.feature.photos.AppMediaType
import com.example.yingshi.feature.photos.CollaboratorIdentityUiModel
import com.example.yingshi.feature.photos.toggleCollaboratorSelection
import com.example.yingshi.ui.components.YingShiAuroraBackdrop
import com.example.yingshi.ui.components.YingShiBackdropVariant
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.components.yingShiHapticClickable
import com.example.yingshi.ui.components.yingShiSoftReveal
import com.example.yingshi.ui.theme.YingShiTheme
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenPhotos: () -> Unit = {},
    onOpenLedger: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val motionEnabled = rememberYingShiMotionEnabled()
    var requestedPhotoOwnerIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var requestedLedgerBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showLedgerBookPicker by rememberSaveable { mutableStateOf(false) }
    val uiState = rememberHomeUiState(
        selectedPhotoOwnerIds = requestedPhotoOwnerIds.toSet(),
        requestedLedgerBookId = requestedLedgerBookId,
    )
    val allPhotoOwnerIds = remember(uiState.photoCollaborators) {
        uiState.photoCollaborators.mapTo(linkedSetOf()) { it.userId }
    }

    YingShiAuroraBackdrop(
        modifier = modifier.fillMaxSize(),
        variant = YingShiBackdropVariant.HOME,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "映世",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                    modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                )
                HomeNotificationBellButton(
                    unreadCount = uiState.unreadNotificationCount,
                    onClick = onOpenNotifications,
                )
            }

            if (uiState.isReadOnly) {
                HomeStatusPill(
                    text = "缓存只读",
                    modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                )
            }

            HomePhotoCard(
                summary = uiState.recentPhotos,
                collaborators = uiState.photoCollaborators,
                selectedOwnerIds = uiState.selectedPhotoOwnerIds,
                modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                onClick = onOpenPhotos,
                onToggleCollaborator = { userId ->
                    val nextSelection = toggleCollaboratorSelection(
                        currentSelection = uiState.selectedPhotoOwnerIds,
                        toggledUserId = userId,
                        allUserIds = allPhotoOwnerIds,
                    )
                    requestedPhotoOwnerIds = nextSelection.toList()
                },
            )

            HomeLedgerCard(
                uiState = uiState,
                modifier = Modifier.yingShiSoftReveal(motionEnabled = motionEnabled),
                onClick = onOpenLedger,
                onOpenBookPicker = {
                    if (uiState.ledgerBooks.isNotEmpty()) {
                        showLedgerBookPicker = true
                    }
                },
            )

            Spacer(modifier = Modifier.size(4.dp))
        }

        if (
            showLedgerBookPicker &&
            uiState.ledgerBooks.isNotEmpty() &&
            !uiState.selectedLedgerBookId.isNullOrBlank()
        ) {
            LedgerBookPickerSheet(
                books = uiState.ledgerBooks,
                selectedBookId = uiState.selectedLedgerBookId,
                defaultBookId = uiState.defaultLedgerBookId,
                onDismiss = { showLedgerBookPicker = false },
                onSelectBook = { bookId ->
                    requestedLedgerBookId = bookId
                    showLedgerBookPicker = false
                },
            )
        }
    }
}

@Composable
private fun HomeStatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.raisedSurface.copy(alpha = 0.74f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.70f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.memoryAccent,
        )
    }
}

@Composable
private fun HomePhotoCard(
    summary: HomeRecentPhotosSummary,
    collaborators: List<CollaboratorIdentityUiModel>,
    selectedOwnerIds: Set<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggleCollaborator: (String) -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(32.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.96f)
            .yingShiHapticClickable(shape = shape, pressedScale = 0.986f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.56f)),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.raisedSurface.copy(alpha = 0.20f),
                            colors.glowWash.copy(alpha = 0.18f),
                            colors.memoryWash.copy(alpha = 0.12f),
                        ),
                    ),
                ),
        ) {
            HomePhotoCollage(
                summary = summary,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent,
                                Color(0xCC5E708A).copy(alpha = 0.42f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最近照片",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface,
                    )
                    if (collaborators.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            collaborators.take(2).forEach { identity ->
                                HomeCollaboratorDot(
                                    label = if (identity.isCurrentUser) "我" else "TA",
                                    selected = identity.userId in selectedOwnerIds,
                                    onClick = { onToggleCollaborator(identity.userId) },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (summary.hasPhotos) {
                        Text(
                            text = formatHomeRelativeTime(summary.latestPhotoAtMillis),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.raisedSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HomeMetaPill(text = "${summary.totalCount} 张")
                            HomeMetaPill(text = formatHomeTimeStamp(summary.latestPhotoAtMillis))
                        }
                    } else {
                        Text(
                            text = "0 张",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.raisedSurface,
                        )
                        HomeMetaPill(text = "未缓存")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePhotoCollage(
    summary: HomeRecentPhotosSummary,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val tiles = summary.tiles
    Row(
        modifier = modifier.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.14f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomePhotoPane(
                tile = tiles.getOrNull(0),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFFB9D6F5).copy(alpha = 0.92f),
                        Color(0xFFEAF5FF).copy(alpha = 0.96f),
                    ),
                ),
                modifier = Modifier.weight(1.28f),
            )
            HomePhotoPane(
                tile = tiles.getOrNull(1),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFE8D9).copy(alpha = 0.92f),
                        colors.raisedSurface.copy(alpha = 0.94f),
                    ),
                ),
                modifier = Modifier.weight(0.82f),
            )
        }
        Column(
            modifier = Modifier.weight(0.86f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomePhotoPane(
                tile = tiles.getOrNull(2),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFFE9F6D8).copy(alpha = 0.90f),
                        colors.sectionBackground.copy(alpha = 0.94f),
                    ),
                ),
                modifier = Modifier.weight(0.78f),
            )
            HomePhotoPane(
                tile = tiles.getOrNull(3),
                fallbackBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFFF1E4FF).copy(alpha = 0.88f),
                        Color(0xFFD8E8FF).copy(alpha = 0.90f),
                    ),
                ),
                modifier = Modifier.weight(1.22f),
            )
        }
    }
}

@Composable
private fun HomePhotoPane(
    tile: HomePhotoTile?,
    fallbackBrush: Brush,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.32f)),
    ) {
        if (tile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackBrush)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            center = Offset(120f, 120f),
                            radius = 520f,
                        ),
                    ),
            )
        } else {
            AppContentMediaThumbnail(
                mediaSource = tile.mediaSource,
                mediaType = tile.mediaType,
                palette = tile.palette,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestSize = 720,
                showLoadingIndicator = false,
                showStatusBadge = false,
                showVideoPlayOverlay = tile.mediaType != AppMediaType.IMAGE,
            )
        }
    }
}

@Composable
private fun HomeCollaboratorDot(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        modifier = Modifier
            .size(30.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.95f, onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            colors.primaryContainer.copy(alpha = 0.96f)
        } else {
            colors.raisedSurface.copy(alpha = 0.78f)
        },
        border = BorderStroke(1.dp, colors.glassStroke.copy(alpha = 0.86f)),
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) colors.titleAccent else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HomeMetaPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}

@Composable
private fun HomeLedgerCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOpenBookPicker: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val shape = RoundedCornerShape(30.dp)
    val latestHeadline = uiState.ledger.latestTransactionLabel ?: "--"
    val latestSupporting = uiState.ledger.latestTransactionAmountText ?: "--"
    val latestTime = formatHomeTimeStamp(uiState.ledger.latestTransactionAtMillis)
    val monthExpense = uiState.ledger.monthExpenseText ?: "¥0.00"
    val bookLabel = uiState.ledger.bookName ?: "默认账本"
    val selectedBook = remember(uiState.ledgerBooks, uiState.selectedLedgerBookId) {
        uiState.ledgerBooks.firstOrNull { it.id == uiState.selectedLedgerBookId }
    }
    val accentColor = selectedBook?.let { Color(it.coverColor) } ?: colors.primaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .yingShiHapticClickable(shape = shape, pressedScale = 0.988f, onClick = onClick),
        shape = shape,
        color = colors.raisedSurface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.74f)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.raisedSurface.copy(alpha = 0.96f),
                            Color(0xFFEAF7F3).copy(alpha = 0.94f),
                            Color(0xFFFFF2E8).copy(alpha = 0.88f),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "账本信号",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                HomeBookFilterChip(
                    label = bookLabel,
                    accentColor = accentColor,
                    enabled = uiState.ledgerBooks.size > 1,
                    onClick = onOpenBookPicker,
                )
            }

            HomeLedgerHeroSignal(
                amount = monthExpense,
                bookLabel = bookLabel,
                accentColor = accentColor,
            )

            HomeLedgerRecentSignal(
                hasTransaction = uiState.ledger.hasTransaction,
                hasBook = uiState.ledger.hasBook,
                latestHeadline = latestHeadline,
                latestSupporting = latestSupporting,
                latestTime = latestTime,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun HomeBookFilterChip(
    label: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = Modifier.yingShiClickable(
            enabled = enabled,
            shape = shape,
            pressedScale = 0.96f,
            onClick = onClick,
        ),
        shape = shape,
        color = colors.sectionBackground.copy(alpha = if (enabled) 0.90f else 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.88f),
            ) {}
            Text(
                text = if (enabled) "$label ▾" else label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.titleAccent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeLedgerHeroSignal(
    amount: String,
    bookLabel: String,
    accentColor: Color,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.60f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.56f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.70f),
                            Color(0xFFFFF5ED).copy(alpha = 0.64f),
                        ),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "本月支出",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                        )
                        Text(
                            text = bookLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = null,
                                tint = colors.titleAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Text(
                    text = amount,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
                Text(
                    text = "当前自然月",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary.copy(alpha = 0.84f),
                )
            }
        }
    }
}

@Composable
private fun HomeLedgerRecentSignal(
    hasTransaction: Boolean,
    hasBook: Boolean,
    latestHeadline: String,
    latestSupporting: String,
    latestTime: String,
    accentColor: Color,
) {
    val colors = YingShiThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.raisedSurface.copy(alpha = 0.70f),
        border = BorderStroke(1.dp, colors.dividerSoft.copy(alpha = 0.58f)),
    ) {
        if (hasTransaction) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "近",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.titleAccent,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "最近一笔",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                    )
                    Text(
                        text = latestHeadline,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = latestTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary.copy(alpha = 0.82f),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
                ) {
                    Text(
                        text = latestSupporting,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.titleAccent,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "最近一笔",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
                Text(
                    text = if (hasBook) "暂无记录" else "进入记账",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.titleAccent,
                )
            }
        }
    }
}

@Composable
private fun HomeNotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val colors = YingShiThemeTokens.colors
    val bellIcon = if (unreadCount > 0) {
        Icons.Rounded.NotificationsActive
    } else {
        Icons.Rounded.Notifications
    }

    Surface(
        modifier = Modifier
            .size(44.dp)
            .yingShiClickable(shape = CircleShape, pressedScale = 0.94f, onClick = onClick),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.68f),
        border = BorderStroke(
            width = 1.dp,
            color = colors.dividerSoft.copy(alpha = 0.72f),
        ),
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = if (unreadCount > 0) {
                                listOf(
                                    colors.memoryWash.copy(alpha = 0.36f),
                                    colors.glowWash.copy(alpha = 0.20f),
                                    Color.Transparent,
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    colors.glowWash.copy(alpha = 0.14f),
                                    Color.Transparent,
                                )
                            },
                            radius = 60f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = bellIcon,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (unreadCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = colors.memoryAccent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.raisedSurface,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    YingShiTheme {
        HomeScreen()
    }
}
