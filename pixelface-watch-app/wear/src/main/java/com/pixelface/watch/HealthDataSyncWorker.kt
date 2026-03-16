package com.pixelface.watch

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker that syncs health data to the phone every 4 hours.
 *
 * Battery-optimized: only runs when the device has network connectivity.
 * Uses the existing HealthDataSyncService to send data via DataLayer.
 *
 * Scheduled by HealthDataManager.start() and BootReceiver.
 */
class HealthDataSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    private const val TAG = "HealthSyncWorker"
    const val WORK_NAME = "health_data_sync"
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      val syncService = HealthDataSyncService(applicationContext)
      val success = syncService.syncToPhone()

      if (success) {
        Log.d(TAG, "✓ Health data synced to phone")
      } else {
        Log.w(TAG, "Health sync returned false (phone may not be connected)")
      }

      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Health sync failed: ${e.message}", e)
      Result.retry()
    }
  }
}
