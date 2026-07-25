package com.example.yingshi.data.remote.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransportType { WIFI, CELLULAR, ETHERNET, OTHER }

data class NetworkConnectivityState(
    val isConnected: Boolean = false,
    val changeVersion: Int = 0,
    val transportType: TransportType = TransportType.OTHER,
)

object NetworkConnectivityMonitor {
    private val _state = MutableStateFlow(NetworkConnectivityState())
    val state: StateFlow<NetworkConnectivityState> = _state.asStateFlow()
    val currentState: NetworkConnectivityState
        get() = _state.value

    @Volatile
    private var initialized = false

    @Volatile
    private var lastTransportType: TransportType = TransportType.OTHER

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
                ?: return
            val initialCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            val initialTransport = initialCapabilities?.transportType() ?: TransportType.OTHER
            lastTransportType = initialTransport
            _state.value = NetworkConnectivityState(
                isConnected = connectivityManager.isConnectedNow(),
                changeVersion = 0,
                transportType = initialTransport,
            )
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val newTransport = connectivityManager.getNetworkCapabilities(network)?.transportType()
                            ?: TransportType.OTHER
                        updateConnectionState(
                            isConnected = connectivityManager.isConnectedNow(),
                            forceVersionBump = true,
                            transportType = newTransport,
                        )
                    }

                    override fun onLost(network: Network) {
                        updateConnectionState(
                            isConnected = connectivityManager.isConnectedNow(),
                            forceVersionBump = true,
                            transportType = TransportType.OTHER,
                        )
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        val newTransport = networkCapabilities.transportType()
                        updateConnectionState(
                            isConnected = connectivityManager.isConnectedNow(),
                            transportType = newTransport,
                        )
                    }

                    override fun onUnavailable() {
                        updateConnectionState(
                            isConnected = false,
                            forceVersionBump = true,
                            transportType = TransportType.OTHER,
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
        transportType: TransportType = lastTransportType,
    ) {
        val current = _state.value
        val transportChanged = transportType != lastTransportType
        if (transportChanged) {
            lastTransportType = transportType
        }
        val shouldBump = forceVersionBump || transportChanged
        if (current.isConnected == isConnected && current.transportType == transportType && !shouldBump) return
        _state.value = NetworkConnectivityState(
            isConnected = isConnected,
            changeVersion = current.changeVersion + 1,
            transportType = transportType,
        )
    }
}

private fun ConnectivityManager.isConnectedNow(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun NetworkCapabilities.transportType(): TransportType {
    return when {
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TransportType.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TransportType.CELLULAR
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TransportType.ETHERNET
        else -> TransportType.OTHER
    }
}
