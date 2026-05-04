package com.jucha.acometidasapp.core.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jucha.acometidasapp.workers.SyncPrediosWorker
import java.util.concurrent.TimeUnit

object SyncManager {

    private const val SYNC_PREDIOS_WORK_NAME = "sync_predios_work"
    private const val SYNC_PREDIOS_IMMEDIATE_WORK = "sync_predios_immediate"
    private const val SYNC_INTERVAL_MINUTES = 15L  // Para testing, usar 15 min; en prod usar valores mayores

    fun enqueueSyncPredios(context: Context) {
        Log.d("SyncManager", "enqueueSyncPredios: Encolando sincronización periódica")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncPrediosWorker>(
            SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_PREDIOS_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Si ya existe, mantenerla
            syncRequest
        )
        Log.d("SyncManager", "enqueueSyncPredios: Sincronización periódica encolada exitosamente")
    }

    fun executeSyncNow(context: Context) {
        Log.d("SyncManager", "executeSyncNow: Ejecutando sincronización ahora mismo")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncPrediosWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_PREDIOS_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d("SyncManager", "executeSyncNow: Sincronización inmediata encolada")
    }

    fun cancelSyncPredios(context: Context) {
        Log.d("SyncManager", "cancelSyncPredios: Cancelando sincronización")
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_PREDIOS_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_PREDIOS_IMMEDIATE_WORK)
    }

    fun getSyncWorkName(): String = SYNC_PREDIOS_WORK_NAME
}
