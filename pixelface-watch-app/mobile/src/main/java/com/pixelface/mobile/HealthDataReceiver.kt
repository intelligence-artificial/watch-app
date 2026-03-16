package com.pixelface.mobile

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * Receives health data from the watch via Wear DataLayer.
 *
 * Listens on /health_sync path. Parses JSON arrays of HR, steps,
 * and calories readings, then inserts into Room database.
 *
 * Triggered by the watch's HealthDataSyncWorker (every 4h) or
 * manual sync from the watch app.
 */
class HealthDataReceiver : WearableListenerService() {

  companion object {
    private const val TAG = "HealthDataReceiver"
    private const val HEALTH_SYNC_PREFIX = "/health_sync"
  }

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    Log.d(TAG, "onDataChanged: ${dataEvents.count} events")

    try {
      for (event in dataEvents) {
        if (event.type == DataEvent.TYPE_CHANGED &&
          event.dataItem.uri.path?.startsWith(HEALTH_SYNC_PREFIX) == true
        ) {
          val frozenItem = event.dataItem.freeze()
          processHealthSync(frozenItem)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in onDataChanged: ${e.message}", e)
    } finally {
      dataEvents.release()
    }
  }

  private fun processHealthSync(dataItem: com.google.android.gms.wearable.DataItem) {
    try {
      val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
      val db = HealthDatabase.getInstance(applicationContext)

      val hrJson = dataMap.getString("hr_data") ?: "[]"
      val stepsJson = dataMap.getString("steps_data") ?: "[]"
      val caloriesJson = dataMap.getString("calories_data") ?: "[]"
      val syncTimestamp = dataMap.getLong("timestamp")

      Log.d(TAG, "Received health sync at $syncTimestamp")

      runBlocking {
        // Parse and insert HR readings
        val hrArray = JSONArray(hrJson)
        val hrReadings = mutableListOf<HrReading>()
        for (i in 0 until hrArray.length()) {
          val obj = hrArray.getJSONObject(i)
          hrReadings.add(
            HrReading(
              bpm = obj.getInt("bpm"),
              timestampMs = obj.getLong("ts")
            )
          )
        }
        if (hrReadings.isNotEmpty()) {
          db.hrDao().insertAll(hrReadings)
          Log.d(TAG, "Inserted ${hrReadings.size} HR readings")
        }

        // Parse and insert Steps readings
        val stepsArray = JSONArray(stepsJson)
        val stepsReadings = mutableListOf<StepsReading>()
        for (i in 0 until stepsArray.length()) {
          val obj = stepsArray.getJSONObject(i)
          stepsReadings.add(
            StepsReading(
              steps = obj.getInt("steps"),
              timestampMs = obj.getLong("ts")
            )
          )
        }
        if (stepsReadings.isNotEmpty()) {
          db.stepsDao().insertAll(stepsReadings)
          Log.d(TAG, "Inserted ${stepsReadings.size} Steps readings")
        }

        // Parse and insert Calories readings
        val calArray = JSONArray(caloriesJson)
        val calReadings = mutableListOf<CaloriesReading>()
        for (i in 0 until calArray.length()) {
          val obj = calArray.getJSONObject(i)
          calReadings.add(
            CaloriesReading(
              calories = obj.getInt("cal"),
              timestampMs = obj.getLong("ts")
            )
          )
        }
        if (calReadings.isNotEmpty()) {
          db.caloriesDao().insertAll(calReadings)
          Log.d(TAG, "Inserted ${calReadings.size} Calories readings")
        }

        // Prune old data (keep 90 days)
        val cutoffMs = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        db.hrDao().deleteOlderThan(cutoffMs)
        db.stepsDao().deleteOlderThan(cutoffMs)
        db.caloriesDao().deleteOlderThan(cutoffMs)

        Log.d(TAG, "✓ Health data synced and stored")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process health sync: ${e.message}", e)
    }
  }
}
