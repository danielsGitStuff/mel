package de.mel.android.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class NetworkChangeLollipop(val androidService: AndroidService) : NetworkChangeListener {
    private var networkCallback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            androidService.melAuthService.powerManager.onCommunicationsDisabled()

        }

        override fun onUnavailable() {
            androidService.melAuthService.powerManager.onCommunicationsDisabled()
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {

        }

        override fun onAvailable(network: Network) {
            androidService.melAuthService.powerManager.onCommunicationsEnabled()
        }
    }

    init {
        val connectivityManager = androidService.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onDestroy() {
        val connectivityManager = androidService.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}