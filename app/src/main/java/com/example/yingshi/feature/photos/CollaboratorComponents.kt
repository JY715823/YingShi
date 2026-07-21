package com.example.yingshi.feature.photos

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.yingshi.feature.me.ProfileAvatar
import com.example.yingshi.ui.components.rememberYingShiMotionEnabled
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Immutable
data class CollaboratorFilterOption(
    val identity: CollaboratorIdentityUiModel,
    val selected: Boolean,
)

@Composable
fun rememberCollaboratorDirectorySnapshot(
    fallbackToFakeProfile: Boolean = true,
): CollaboratorDirectorySnapshot {
    val currentUser = CollaboratorDirectoryStore.currentUser
    return remember(currentUser, fallbackToFakeProfile) {
        CollaboratorDirectoryStore.snapshot(fallbackToFakeProfile = fallbackToFakeProfile)
    }
}

@Composable
fun CollaboratorAvatar(
    identity: CollaboratorIdentityUiModel,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    ProfileAvatar(
        name = identity.displayName,
        avatarUrl = identity.avatarUrl,
        modifier = modifier,
        size = size,
    )
}

@Composable
fun CollaboratorFilterChip(
    identity: CollaboratorIdentityUiModel,
    selected: Boolean,
    onClick: () -> Unit,
    labelText: String = identity.displayName,
    modifier: Modifier = Modifier,
) {
    val colors = YingShiThemeTokens.colors
    val spacing = YingShiThemeTokens.spacing
    val radius = YingShiThemeTokens.radius
    val shape = RoundedCornerShape(radius.capsule)
    val motionEnabled = rememberYingShiMotionEnabled()
    val animDuration = if (motionEnabled) 200 else 0

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer.copy(alpha = 0.88f)
        else colors.raisedSurface.copy(alpha = 0.92f),
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.glassStroke.copy(alpha = 0.86f)
        else colors.dividerSoft.copy(alpha = 0.66f),
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.titleAccent else colors.textSecondary,
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
    )
    val chipElevation by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
    )

    Surface(
        modifier = modifier.yingShiClickable(
            shape = shape,
            pressedScale = 0.97f,
            onClick = onClick,
        ),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = chipElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.xs, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            CollaboratorAvatar(identity = identity, size = 24.dp)
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
            )
        }
    }
}

@Composable
fun CollaboratorFilterChipRow(
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    onToggleCollaborator: (String) -> Unit,
    labelForIdentity: (CollaboratorIdentityUiModel) -> String = { it.displayName },
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(YingShiThemeTokens.spacing.xs),
) {
    if (directory.all.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        directory.all.forEach { identity ->
            CollaboratorFilterChip(
                identity = identity,
                selected = identity.userId in selectedUserIds,
                onClick = { onToggleCollaborator(identity.userId) },
                labelText = labelForIdentity(identity),
            )
        }
    }
}

@Composable
fun CollaboratorAvatarStack(
    identities: List<CollaboratorIdentityUiModel>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 28.dp,
) {
    val visibleIdentities = identities.take(2)
    if (visibleIdentities.isEmpty()) return
    val overlap = 16.dp
    val stackWidth = avatarSize + overlap * (visibleIdentities.size - 1).coerceAtLeast(0)
    Box(
        modifier = modifier
            .width(stackWidth)
            .height(avatarSize),
    ) {
        visibleIdentities.reversed().forEachIndexed { index, identity ->
            val offsetX = (visibleIdentities.size - 1 - index) * 16
            Box(
                modifier = Modifier.offset(x = offsetX.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(2.dp, YingShiThemeTokens.colors.appBackground),
                    color = YingShiThemeTokens.colors.appBackground,
                ) {
                    CollaboratorAvatar(identity = identity, size = avatarSize)
                }
            }
        }
    }
}

@Composable
fun CollaboratorMarkerBadge(
    identity: CollaboratorIdentityUiModel,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        border = BorderStroke(1.dp, YingShiThemeTokens.colors.glassStroke.copy(alpha = 0.82f)),
        color = YingShiThemeTokens.colors.raisedSurface.copy(alpha = 0.96f),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(YingShiThemeTokens.colors.raisedSurface.copy(alpha = 0.96f))
                .padding(1.dp),
            contentAlignment = Alignment.Center,
        ) {
            CollaboratorAvatar(identity = identity, size = size)
        }
    }
}
