package com.tamagotchi.pet

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
