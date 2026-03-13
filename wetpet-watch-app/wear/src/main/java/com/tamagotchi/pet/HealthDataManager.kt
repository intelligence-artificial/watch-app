package com.tamagotchi.pet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Expanded passive health data manager — pulls all available
 * Fitbit/Health Services data types for the WetPet emotion engine.
 *
 * Architecture:
 * - Checks device capabilities before registering
 * - Uses PassiveListenerCallback for foreground data (faster delivery)
 * - PassiveDataService handles background delivery (registered separately)
 * - Reads from SharedPreferences (written by PassiveDataService) as fallback
 * - Health Connect integration reads Fitbit-exclusive metrics (SpO2, HRV, skin temp)
 */
class HealthDataManager(private val context: Context) {

  companion object {
    private const val TAG = "HealthDataMgr"
    private const val SEDENTARY_THRESHOLD_MS = 45 * 60 * 1000L // 45 minutes
    const val PREFS_NAME = "wetpet_health_data"

    // SharedPreferences keys (shared with PassiveDataService)
    const val KEY_HEART_RATE = "heart_rate"
    const val KEY_DAILY_STEPS = "daily_steps"
    const val KEY_CALORIES = "calories"
    const val KEY_FLOORS = "floors"
    const val KEY_DISTANCE = "distance"
    const val KEY_LAST_UPDATE = "last_update_ms"
    const val KEY_SUPPORTED_TYPES = "supported_types"
    const val KEY_CAPABILITIES_CHECKED = "capabilities_checked"
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

  // ── Derived metrics ──
  @Volatile var activeMinutesZone2Plus: Int = 0
    private set
  @Volatile var isSedentary: Boolean = false
    private set
  @Volatile var lastDataUpdateMs: Long = 0L
    private set

  // ── Capability info (for debug display) ──
  @Volatile var supportedTypes: Set<String> = emptySet()
    private set
  @Volatile var capabilitiesChecked: Boolean = false
    private set

  private var lastStepCount: Int = 0
  private var lastStepChangeMs: Long = System.currentTimeMillis()

  // HR history for chart
  val hrHistoryStore = HrHistoryStore(context)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val passiveClient: PassiveMonitoringClient =
    HealthServices.getClient(context).passiveMonitoringClient
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  // Foreground callback — delivers data faster while app is active
  private val callback = object : PassiveListenerCallback {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
      processDataPoints(dataPoints)
    }
  }

  /**
   * Check device capabilities, then register for supported data types.
   * Must be called after permissions are granted.
   */
  fun start() {
    // First load any previously saved data from SharedPreferences
    loadFromPrefs()

    scope.launch {
      try {
        checkCapabilitiesAndRegister()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start health monitoring: ${e.message}", e)
        // Fallback: try registering without capability check
        registerWithDefaults()
      }
      // Force complication binding on app start
      HeartRateComplicationService.requestUpdate(context)
      PetComplicationService.requestUpdate(context)
    }
  }

  private suspend fun checkCapabilitiesAndRegister() {
    val capabilities = passiveClient.getCapabilitiesAsync().get()
    val supported = capabilities.supportedDataTypesPassiveMonitoring

    Log.d(TAG, "Device supported passive types: $supported")
    supportedTypes = supported.map { it.name }.toSet()
    capabilitiesChecked = true

    // Save capabilities info for debug display
    prefs.edit()
      .putStringSet(KEY_SUPPORTED_TYPES, supportedTypes)
      .putBoolean(KEY_CAPABILITIES_CHECKED, true)
      .apply()

    // Build data type set from only what the device supports
    val requestedTypes = mutableSetOf<DataType<*, *>>()

    val wantedTypes = listOf(
      DataType.HEART_RATE_BPM,
      DataType.STEPS_DAILY,
      DataType.CALORIES_DAILY,
      DataType.FLOORS_DAILY,
      DataType.DISTANCE_DAILY
    )

    for (type in wantedTypes) {
      if (type in supported) {
        requestedTypes.add(type)
        Log.d(TAG, "  ✓ ${type.name} supported")
      } else {
        Log.w(TAG, "  ✗ ${type.name} NOT supported on this device")
      }
    }

    if (requestedTypes.isEmpty()) {
      Log.e(TAG, "No health data types supported! Check device and permissions.")
      return
    }

    val config = PassiveListenerConfig.Builder()
      .setDataTypes(requestedTypes)
      .setShouldUserActivityInfoBeRequested(true)
      .build()

    // Register foreground callback for faster delivery while app is active
    passiveClient.setPassiveListenerCallback(config, callback)
    Log.d(TAG, "Passive health listener registered (${requestedTypes.size} types)")

    // Also register background service for data delivery when app is not active
    try {
      passiveClient.setPassiveListenerServiceAsync(
        PassiveDataService::class.java,
        config
      ).get()
      Log.d(TAG, "Background PassiveDataService registered")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to register background service: ${e.message}")
    }
  }

  private fun registerWithDefaults() {
    Log.w(TAG, "Falling back to default registration (no capability check)")
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
    Log.d(TAG, "Fallback passive listener registered (${dataTypes.size} types)")
  }

  /**
   * Process incoming data points — used by both foreground callback
   * and called when reading from SharedPreferences.
   */
  fun processDataPoints(dataPoints: DataPointContainer) {
    // Heart rate
    val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
    if (hrPoints.isNotEmpty()) {
      val latest = hrPoints.last().value.toInt()
      if (latest in 30..220) {
        heartRate = latest
        hrHistoryStore.append(latest)
        if (latest >= 100) activeMinutesZone2Plus++
        Log.d(TAG, "HR: $heartRate bpm")
        // Push update to watch face complication
        HeartRateComplicationService.requestUpdate(context)
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

    // Update timestamp and persist
    lastDataUpdateMs = System.currentTimeMillis()
    saveToPrefs()
  }

  /** Save current values to SharedPreferences for cross-component access */
  private fun saveToPrefs() {
    prefs.edit()
      .putInt(KEY_HEART_RATE, heartRate)
      .putInt(KEY_DAILY_STEPS, dailySteps)
      .putInt(KEY_CALORIES, calories)
      .putInt(KEY_FLOORS, floorsClimbed)
      .putFloat(KEY_DISTANCE, distance)
      .putLong(KEY_LAST_UPDATE, lastDataUpdateMs)
      .apply()
  }

  /** Load last-known values from SharedPreferences */
  private fun loadFromPrefs() {
    heartRate = prefs.getInt(KEY_HEART_RATE, 0)
    dailySteps = prefs.getInt(KEY_DAILY_STEPS, 0)
    calories = prefs.getInt(KEY_CALORIES, 0)
    floorsClimbed = prefs.getInt(KEY_FLOORS, 0)
    distance = prefs.getFloat(KEY_DISTANCE, 0f)
    lastDataUpdateMs = prefs.getLong(KEY_LAST_UPDATE, 0L)
    supportedTypes = prefs.getStringSet(KEY_SUPPORTED_TYPES, emptySet()) ?: emptySet()
    capabilitiesChecked = prefs.getBoolean(KEY_CAPABILITIES_CHECKED, false)
    Log.d(TAG, "Loaded from prefs: steps=$dailySteps, hr=$heartRate, cal=$calories")
  }

  fun stop() {
    passiveClient.clearPassiveListenerCallbackAsync()
    Log.d(TAG, "Passive health listener cleared")
  }

  fun snapshot(): HealthDataSnapshot = HealthDataSnapshot(
    heartRate = heartRate,
    dailySteps = dailySteps,
    calories = calories,
    floorsClimbed = floorsClimbed,
    distance = distance,
    activeMinutesZone2Plus = activeMinutesZone2Plus,
    isSedentary = isSedentary
  )
}
