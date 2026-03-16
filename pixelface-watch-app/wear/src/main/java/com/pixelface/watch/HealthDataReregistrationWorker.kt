package com.pixelface.watch

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic worker that re-registers the passive health data listener.
 *
 * Passive listener registrations can be silently dropped by the system
 * due to battery optimization, Doze mode, app updates, or other events.
 * This worker runs every 2 hours to ensure continuous data collection.
 *
 * Scheduled by HealthDataManager.start() via WorkManager.
 */
class HealthDataReregistrationWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    private const val TAG = "HealthReregWorker"
    const val WORK_NAME = "health_data_reregistration"
  }

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      val passiveClient = HealthServices
        .getClient(applicationContext)
        .passiveMonitoringClient

      // Check capabilities
      val capabilities = passiveClient.getCapabilitiesAsync().get()
      val supported = capabilities.supportedDataTypesPassiveMonitoring

      val requestedTypes = mutableSetOf<DataType<*, *>>()
      val wantedTypes = listOf(
        DataType.HEART_RATE_BPM,
        DataType.STEPS_DAILY,
        DataType.CALORIES_DAILY,
        DataType.FLOORS_DAILY,
        DataType.DISTANCE_DAILY
      )

      for (type in wantedTypes) {
        if (type in supported) requestedTypes.add(type)
      }

      if (requestedTypes.isEmpty()) {
        Log.w(TAG, "No supported types found during re-registration")
        return@withContext Result.success()
      }

      val config = PassiveListenerConfig.Builder()
        .setDataTypes(requestedTypes)
        .setShouldUserActivityInfoBeRequested(true)
        .build()

      // Re-register the background service
      passiveClient.setPassiveListenerServiceAsync(
        PassiveDataService::class.java,
        config
      ).get()

      Log.d(TAG, "✓ Passive listener re-registered (${requestedTypes.size} types)")

      // Also update capabilities info in SharedPreferences
      val prefs = applicationContext.getSharedPreferences(
        HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE
      )
      prefs.edit()
        .putStringSet(HealthDataManager.KEY_SUPPORTED_TYPES, supported.map { it.name }.toSet())
        .putBoolean(HealthDataManager.KEY_CAPABILITIES_CHECKED, true)
        .apply()

      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Re-registration failed: ${e.message}", e)
      Result.retry()
    }
  }
}
