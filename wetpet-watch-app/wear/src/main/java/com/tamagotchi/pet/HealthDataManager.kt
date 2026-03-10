package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Expanded passive health data manager — pulls all available
 * Fitbit/Health Services data types for the WetPet emotion engine.
 */
class HealthDataManager(context: Context) {

  companion object {
    private const val TAG = "HealthDataMgr"
    private const val SEDENTARY_THRESHOLD_MS = 45 * 60 * 1000L // 45 minutes
  }

  // ── Core metrics ──
  @Volatile var heartRate: Int = 0
    private set
  @Volatile var dailySteps: Int = 0
    private set

  // ── Expanded metrics ──
  @Volatile var calories: Int = 0
    private set
  @Volatile var floorsClimbed: Int = 0
    private set
  @Volatile var distance: Float = 0f
    private set
  @Volatile var heartRateVariability: Float = 0f
    private set
  @Volatile var skinTemperature: Float = 0f
    private set
  @Volatile var spo2: Int = 0
    private set
  @Volatile var activeMinutesZone2Plus: Int = 0
    private set

  // ── Sedentary tracking ──
  @Volatile var isSedentary: Boolean = false
    private set
  private var lastStepCount: Int = 0
  private var lastStepChangeMs: Long = System.currentTimeMillis()

  private val passiveClient: PassiveMonitoringClient =
    HealthServices.getClient(context).passiveMonitoringClient

  private val callback = object : PassiveListenerCallback {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
      // Heart rate
      val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
      if (hrPoints.isNotEmpty()) {
        val latest = hrPoints.last().value.toInt()
        if (latest in 30..220) {
          heartRate = latest
          // Track active minutes
          if (latest >= 100) activeMinutesZone2Plus++
          Log.d(TAG, "HR: $heartRate bpm")
        }
      }

      // Steps
      val stepPoints = dataPoints.getData(DataType.STEPS_DAILY)
      if (stepPoints.isNotEmpty()) {
        val latest = stepPoints.last().value.toInt()
        if (latest != lastStepCount) {
          lastStepCount = latest
          lastStepChangeMs = System.currentTimeMillis()
          isSedentary = false
        }
        dailySteps = latest
        Log.d(TAG, "Steps: $dailySteps")
      }

      // Calories
      try {
        val calPoints = dataPoints.getData(DataType.CALORIES_DAILY)
        if (calPoints.isNotEmpty()) {
          calories = calPoints.last().value.toInt()
          Log.d(TAG, "Calories: $calories")
        }
      } catch (e: Exception) { Log.w(TAG, "Calories not available") }

      // Floors
      try {
        val floorPoints = dataPoints.getData(DataType.FLOORS_DAILY)
        if (floorPoints.isNotEmpty()) {
          floorsClimbed = floorPoints.last().value.toInt()
          Log.d(TAG, "Floors: $floorsClimbed")
        }
      } catch (e: Exception) { Log.w(TAG, "Floors not available") }

      // Distance
      try {
        val distPoints = dataPoints.getData(DataType.DISTANCE_DAILY)
        if (distPoints.isNotEmpty()) {
          distance = distPoints.last().value.toFloat()
          Log.d(TAG, "Distance: ${distance}m")
        }
      } catch (e: Exception) { Log.w(TAG, "Distance not available") }

      // Update sedentary flag
      val elapsed = System.currentTimeMillis() - lastStepChangeMs
      if (elapsed > SEDENTARY_THRESHOLD_MS) {
        isSedentary = true
      }
    }
  }

  fun start() {
    val dataTypes = mutableSetOf(
      DataType.HEART_RATE_BPM,
      DataType.STEPS_DAILY,
      DataType.CALORIES_DAILY,
      DataType.FLOORS_DAILY,
      DataType.DISTANCE_DAILY
    )

    val config = PassiveListenerConfig.Builder()
      .setDataTypes(dataTypes)
      .build()

    passiveClient.setPassiveListenerCallback(config, callback)
    Log.d(TAG, "Passive health listener registered (${dataTypes.size} types)")
  }

  fun stop() {
    passiveClient.clearPassiveListenerCallbackAsync()
    Log.d(TAG, "Passive health listener cleared")
  }

  /** Create a snapshot for the PetStatusEngine */
  fun snapshot(): HealthDataSnapshot = HealthDataSnapshot(
    heartRate = heartRate,
    dailySteps = dailySteps,
    calories = calories,
    floorsClimbed = floorsClimbed,
    distance = distance,
    heartRateVariability = heartRateVariability,
    skinTemperature = skinTemperature,
    spo2 = spo2,
    activeMinutesZone2Plus = activeMinutesZone2Plus,
    isSedentary = isSedentary
  )
}
