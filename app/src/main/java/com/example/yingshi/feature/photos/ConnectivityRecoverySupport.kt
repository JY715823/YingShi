package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.yingshi.data.remote.connectivity.NetworkConnectivityMonitor

@Composable
internal fun ReconnectRefreshEffect(
    shouldRefresh: Boolean,
    onReconnect: () -> Unit,
    onDisconnect: (() -> Unit)? = null,
) {
    val networkState by NetworkConnectivityMonitor.state.collectAsState()
    var lastConnectionState by remember { mutableStateOf(networkState.isConnected) }
    LaunchedEffect(shouldRefresh, networkState.changeVersion, networkState.isConnected) {
        val wasConnected = lastConnectionState
        lastConnectionState = networkState.isConnected
        if (networkState.changeVersion <= 0) return@LaunchedEffect
        if (!networkState.isConnected) {
            if (wasConnected) {
                onDisconnect?.invoke()
            }
            return@LaunchedEffect
        }
        if (shouldRefresh) {
            onReconnect()
        }
    }
}
