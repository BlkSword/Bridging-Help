package com.bridginghelp.core.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.annotation.SuppressLint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络状态
 */
sealed class ConnectivityStatus {
    data object Available : ConnectivityStatus()
    data object Unavailable : ConnectivityStatus()
    data object Losing : ConnectivityStatus()
    data object Lost : ConnectivityStatus()
}

/**
 * 网络类型
 */
enum class NetworkType {
    NONE,
    MOBILE,
    WIFI,
    ETHERNET,
    VPN,
    OTHER
}

/**
 * 网络信息
 */
data class NetworkInfo(
    val status: ConnectivityStatus,
    val type: NetworkType = NetworkType.NONE,
    val isMetered: Boolean = false,
    val bandwidth: Long = 0L
)

/**
 * 网络状态观察器
 * 使用ConnectivityManager.NetworkCallback监听网络变化
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * 监听网络状态变化
     */
    @SuppressLint("MissingPermission")
    fun observe(): Flow<NetworkInfo> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkInfo(ConnectivityStatus.Available, getNetworkType(network)))
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkInfo(ConnectivityStatus.Losing, getNetworkType(network)))
            }

            override fun onLost(network: Network) {
                trySend(NetworkInfo(ConnectivityStatus.Lost))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val type = getNetworkType(network)
                val isMetered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                val bandwidth = networkCapabilities.linkDownstreamBandwidthKbps

                trySend(NetworkInfo(
                    status = ConnectivityStatus.Available,
                    type = type,
                    isMetered = isMetered,
                    bandwidth = bandwidth * 1024L // 转换为bps
                ))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()

    /**
     * 获取当前网络状态
     */
    @SuppressLint("MissingPermission")
    fun getCurrentNetworkInfo(): NetworkInfo {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            return NetworkInfo(ConnectivityStatus.Unavailable)
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            return NetworkInfo(ConnectivityStatus.Unavailable)
        }

        return NetworkInfo(
            status = ConnectivityStatus.Available,
            type = getNetworkType(activeNetwork),
            isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            bandwidth = capabilities.linkDownstreamBandwidthKbps * 1024L
        )
    }

    /**
     * 检查是否有网络连接
     */
    @SuppressLint("MissingPermission")
    fun isOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @SuppressLint("MissingPermission")
    private fun getNetworkType(network: Network): NetworkType {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.OTHER
        }
    }
}
