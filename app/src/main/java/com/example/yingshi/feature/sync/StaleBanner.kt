package com.example.yingshi.feature.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yingshi.ui.components.yingShiClickable
import com.example.yingshi.ui.theme.YingShiThemeTokens

@Composable
fun StaleBanner(
    module: SyncModule,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val staleState by SyncVersionTracker.staleState.collectAsState()
    val isStale = staleState.isStale(module)
    val motion = YingShiThemeTokens.motion
    val colors = YingShiThemeTokens.colors

    AnimatedVisibility(
        visible = isStale,
        enter = fadeIn(animationSpec = tween(motion.noticeMillis, easing = motion.easing)) +
            slideInVertically(animationSpec = tween(motion.noticeMillis, easing = motion.easing)) {
                -it / 2
            },
        exit = fadeOut(animationSpec = tween(motion.stateMillis, easing = motion.easing)) +
            slideOutVertically(animationSpec = tween(motion.stateMillis, easing = motion.easing)) {
                -it / 2
            },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .yingShiClickable(onClick = onRefresh),
            shape = RoundedCornerShape(YingShiThemeTokens.radius.capsule),
            color = colors.primaryContainer.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, colors.primaryAction.copy(alpha = 0.18f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = colors.titleAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "有新内容，点击刷新",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.titleAccent,
                )
            }
        }
    }
}
