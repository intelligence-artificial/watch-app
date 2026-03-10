package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends fitness data snapshots to the phone via DataLayer API.
 * Now integrated with the expanded health data.
 */
class FitnessDataSender(private val context: Context) {

  companion object {
    private const val TAG = "FitnessSender"
  }

  suspend fun sendFitnessSnapshot(health: HealthDataManager) {
    try {
      val timestamp = System.currentTimeMillis()
      val path = "${DataLayerPaths.FITNESS_UPDATE_PATH}/$timestamp"

      val dataMapRequest = PutDataMapRequest.create(path).apply {
        dataMap.putInt(DataLayerPaths.KEY_STEPS, health.dailySteps)
        dataMap.putInt(DataLayerPaths.KEY_HEART_RATE, health.heartRate)
        dataMap.putInt(DataLayerPaths.KEY_CALORIES, health.calories)
        dataMap.putInt(DataLayerPaths.KEY_FLOORS, health.floorsClimbed)
        dataMap.putFloat(DataLayerPaths.KEY_DISTANCE, health.distance)
        dataMap.putLong(DataLayerPaths.KEY_TIMESTAMP, timestamp)
      }

      val putDataReq = dataMapRequest.asPutDataRequest().setUrgent()
      Wearable.getDataClient(context).putDataItem(putDataReq).await()
      Log.d(TAG, "Fitness snapshot sent: steps=${health.dailySteps}, hr=${health.heartRate}, cal=${health.calories}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send fitness snapshot: ${e.message}", e)
    }
  }
}
