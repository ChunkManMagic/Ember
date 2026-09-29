package com.ember.companion.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reports how (and whether) the device's traffic is being tunnelled.
 *
 * Ember does not implement a VPN. WebView uses the platform's default network, so
 * when the OS has a VPN up, Ember's traffic is already inside it. This class
 * surfaces that state so the UI can tell the user the truth instead of implying
 * the app is doing something it isn't.
 */
class VpnMonitor(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    data class State(
        val hasActiveNetwork: Boolean = false,
        val vpnActive: Boolean = false,
        val vpnInterface: String? = null,
        val vpnDnsServers: List<String> = emptyList(),
        val activeTransport: String? = null,
        val validated: Boolean = false,
    )

    fun current(): State {
        val manager = cm ?: return State()
        val network: Network? = manager.activeNetwork
            ?: return State()
        val caps = manager.getNetworkCapabilities(network) ?: return State()
        val link: LinkProperties? = manager.getLinkProperties(network)
        val vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return State(
            hasActiveNetwork = true,
            vpnActive = vpnActive,
            vpnInterface = if (vpnActive) link?.interfaceName else null,
            vpnDnsServers = if (vpnActive) {
                link?.dnsServers?.map { it.hostAddress }.orEmpty()
            } else {
                emptyList()
            },
            activeTransport = firstTransport(caps),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }

    fun observe(): Flow<State> = callbackFlow {
        val manager = cm
        if (manager == null) {
            trySend(current())
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(current())
            }

            override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) {
                trySend(current())
            }
        }
        trySend(current())
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }

    private fun firstTransport(caps: NetworkCapabilities): String? = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> null
    }
}
