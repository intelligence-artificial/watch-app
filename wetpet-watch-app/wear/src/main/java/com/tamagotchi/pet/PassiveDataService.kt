package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer

/**
 * Background passive health data service.
 * Receives batched health data from Health Services even when the app
 * is not in the foreground. Writes values to SharedPreferences so that
 * HealthDataManager, ComplicationServices, and the watchface can read them.
 */
class PassiveDataService : PassiveListenerService() {

  companion object {
    private const val TAG = "PassiveDataSvc"
  }

  override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
    Log.d(TAG, "Background data received")

    val prefs = applicationContext.getSharedPreferences(
      HealthDataManager.PREFS_NAME,
      Context.MODE_PRIVATE
    )

    val editor = prefs.edit()

    // Heart rate
    try {
      val hrPoints = dataPoints.getData(
        androidx.health.services.client.data.DataType.HEART_RATE_BPM
      )
      if (hrPoints.isNotEmpty()) {
        val latest = hrPoints.last().value.toInt()
        if (latest in 30..220) {
          editor.putInt(HealthDataManager.KEY_HEART_RATE, latest)
          // Append to HR history for chart
          HrHistoryStore(applicationContext).append(latest)
          Log.d(TAG, "BG HR: $latest bpm")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "HR read failed: ${e.message}")
    }

    // Steps
    try {
      val stepPoints = dataPoints.getData(
        androidx.health.services.client.data.DataType.STEPS_DAILY
      )
      if (stepPoints.isNotEmpty()) {
        val latest = stepPoints.last().value.toInt()
        editor.putInt(HealthDataManager.KEY_DAILY_STEPS, latest)
        Log.d(TAG, "BG Steps: $latest")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Steps read failed: ${e.message}")
    }

    // Calories
    try {
      val calPoints = dataPoints.getData(
        androidx.health.services.client.data.DataType.CALORIES_DAILY
      )
      if (calPoints.isNotEmpty()) {
        val latest = calPoints.last().value.toInt()
        editor.putInt(HealthDataManager.KEY_CALORIES, latest)
        Log.d(TAG, "BG Calories: $latest")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Calories read failed: ${e.message}")
    }

    // Floors
    try {
      val floorPoints = dataPoints.getData(
        androidx.health.services.client.data.DataType.FLOORS_DAILY
      )
      if (floorPoints.isNotEmpty()) {
        val latest = floorPoints.last().value.toInt()
        editor.putInt(HealthDataManager.KEY_FLOORS, latest)
        Log.d(TAG, "BG Floors: $latest")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Floors read failed: ${e.message}")
    }

    // Distance
    try {
      val distPoints = dataPoints.getData(
        androidx.health.services.client.data.DataType.DISTANCE_DAILY
      )
      if (distPoints.isNotEmpty()) {
        val latest = distPoints.last().value.toFloat()
        editor.putFloat(HealthDataManager.KEY_DISTANCE, latest)
        Log.d(TAG, "BG Distance: ${latest}m")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Distance read failed: ${e.message}")
    }

    editor.putLong(HealthDataManager.KEY_LAST_UPDATE, System.currentTimeMillis())
    editor.apply()

    // Request complication updates so watchface gets fresh data
    try {
      FaceComplicationService.requestUpdate(applicationContext)
      HeartRateComplicationService.requestUpdate(applicationContext)
      StepsComplicationService.requestUpdate(applicationContext)
      CaloriesComplicationService.requestUpdate(applicationContext)
    } catch (e: Exception) {
      Log.w(TAG, "Complication update request failed: ${e.message}")
    }
  }
}
