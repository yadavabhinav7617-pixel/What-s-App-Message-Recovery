package com.notifyvault.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.notifyvault.app.work.CloudSyncScheduler

class NotifyVaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupAutoReconnectionSync()
    }

    private fun setupAutoReconnectionSync() {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                networkRequest,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        // Trigger immediate WorkManager sync when internet becomes available
                        CloudSyncScheduler.enqueueNow(applicationContext)
                    }
                }
            )
        } catch (_: Exception) {
        }
    }
}
