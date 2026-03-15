package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles syncing health data from watch to phone via DataLayer.
 * The phone app receives JSON data and can export to CSV.
 *
 * Triggered on-demand from StatsScreen or periodically via WorkManager.
 */
class HealthDataSyncService(private val context: Context) {

  companion object {
    private const val TAG = "HealthSync"
  }

  private val dataLayerSender = DataLayerSender(context)

  /**
   * Sync all health data to phone.
   * Returns true if sync was successful.
   */
  suspend fun syncToPhone(): Boolean {
    return try {
      val hrStore = HrHistoryStore(context)
      val stepsStore = StepsHistoryStore(context)
      val calStore = CaloriesHistoryStore(context)

      val result = dataLayerSender.sendHealthData(
        hrJson = hrStore.toJson(),
        stepsJson = stepsStore.toJson(),
        caloriesJson = calStore.toJson()
      )

      val success = result == DataLayerSender.SendStatus.SENT
      Log.d(TAG, "Health sync result: $result")
      success
    } catch (e: Exception) {
      Log.e(TAG, "Health sync failed: ${e.message}", e)
      false
    }
  }

  /**
   * Fire-and-forget sync from a non-suspend context.
   */
  fun syncInBackground() {
    CoroutineScope(Dispatchers.IO).launch {
      syncToPhone()
    }
  }
}
