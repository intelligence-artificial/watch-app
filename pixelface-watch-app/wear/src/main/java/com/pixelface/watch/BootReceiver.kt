package com.pixelface.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.runBlocking

/**
 * Re-registers passive health listener after device reboot.
 * Passive data registrations don't survive reboots, so this ensures
 * continuous health data collection.
 */
class BootReceiver : BroadcastReceiver() {

  companion object {
    private const val TAG = "BootReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    Log.d(TAG, "Boot completed — scheduling passive listener re-registration")
    WorkManager.getInstance(context).enqueue(
      OneTimeWorkRequestBuilder<PassiveListenerRegistrationWorker>().build()
    )

    // Also schedule periodic re-registration to survive Doze/battery optimization
    val reregWork = androidx.work.PeriodicWorkRequestBuilder<HealthDataReregistrationWorker>(
      2, java.util.concurrent.TimeUnit.HOURS,
      30, java.util.concurrent.TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      HealthDataReregistrationWorker.WORK_NAME,
      androidx.work.ExistingPeriodicWorkPolicy.KEEP,
      reregWork
    )
    Log.d(TAG, "Scheduled periodic re-registration (every 2h)")

    // Schedule periodic health data sync to phone (every 4h, battery-optimized)
    val constraints = androidx.work.Constraints.Builder()
      .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
      .build()
    val syncWork = androidx.work.PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
      4, java.util.concurrent.TimeUnit.HOURS,
      30, java.util.concurrent.TimeUnit.MINUTES
    ).setConstraints(constraints).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      HealthDataSyncWorker.WORK_NAME,
      androidx.work.ExistingPeriodicWorkPolicy.KEEP,
      syncWork
    )
    Log.d(TAG, "Scheduled periodic health sync (every 4h)")
  }
}

/**
 * Worker that re-registers the PassiveDataService with Health Services.
 * Uses WorkManager because Health Services may take 10+ seconds to
 * acknowledge during boot, which exceeds BroadcastReceiver limits.
 */
class PassiveListenerRegistrationWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

  companion object {
    private const val TAG = "PassiveRegWorker"
  }

  override fun doWork(): Result {
    return try {
      runBlocking {
        val passiveClient = HealthServices
          .getClient(applicationContext)
          .passiveMonitoringClient

        // Check capabilities first
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
          Log.w(TAG, "No supported types found after boot")
          return@runBlocking
        }

        val config = PassiveListenerConfig.Builder()
          .setDataTypes(requestedTypes)
          .setShouldUserActivityInfoBeRequested(true)
          .build()

        passiveClient.setPassiveListenerServiceAsync(
          PassiveDataService::class.java,
          config
        ).get()

        Log.d(TAG, "PassiveDataService re-registered after boot (${requestedTypes.size} types)")
      }
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to re-register after boot: ${e.message}", e)
      Result.retry()
    }
  }
}
