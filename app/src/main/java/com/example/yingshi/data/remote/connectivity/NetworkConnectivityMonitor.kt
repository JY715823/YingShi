package com.example.yingshi.data.remote.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkConnectivityState(
    val isConnected: Boolean = false,
    val changeVersion: Int = 0,
)

object NetworkConnectivityMonitor {
    private val _state = MutableStateFlow(NetworkConnectivityState())
    val state: StateFlow<NetworkConnectivityState> = _state.asStateFlow()
    val currentState: NetworkConnectivityState
        get() = _state.value

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
                ?: return
            _state.value = NetworkConnectivityState(
                isConnected = connectivityManager.isConnectedNow(),
                changeVersion = 0,
            )
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        updateConnectionState(
                            isConnected = connectivityManager.isConnectedNow(),
                            forceVersionBump = true,
                        )
                    }

                    override fun onLost(network: Network) {
                        updateConnectionState(
                            isConnected = connectivityManager.isConnectedNow(),
                            forceVersionBump = true,
                        )
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        updateConnectionState(connectivityManager.isConnectedNow())
                    }

                    override fun onUnavailable() {
                        updateConnectionState(
                            isConnected = false,
                            forceVersionBump = true,
                        )
                    }
                },
            )
            initialized = true
        }
    }

    private fun updateConnectionState(
        isConnected: Boolean,
        forceVersionBump: Boolean = false,
    ) {
        val current = _state.value
        if (current.isConnected == isConnected && !forceVersionBump) return
        _state.value = NetworkConnectivityState(
            isConnected = isConnected,
            changeVersion = current.changeVersion + 1,
        )
    }
}

private fun ConnectivityManager.isConnectedNow(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
