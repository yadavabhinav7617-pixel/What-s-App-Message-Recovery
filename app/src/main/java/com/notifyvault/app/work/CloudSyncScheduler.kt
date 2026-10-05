package com.notifyvault.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.notifyvault.app.data.CloudSyncSettings
import java.util.concurrent.TimeUnit

object CloudSyncScheduler {
    private const val UNIQUE_SYNC_WORK_NAME = "notifyvault_cloud_sync"

    fun enqueueNow(context: Context) {
        if (!CloudSyncSettings.isEnabled(context)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
