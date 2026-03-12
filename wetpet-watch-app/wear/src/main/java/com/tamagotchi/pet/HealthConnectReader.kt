package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Reads Fitbit-exclusive health metrics from Health Connect.
 *
 * The Fitbit app on Pixel Watch 2 writes SpO2, HRV, and skin temperature
 * to Health Connect, but these are NOT available through the Wear OS
 * Health Services passive monitoring API. We read them from Health Connect
 * as a secondary data source.
 *
 * Requirements:
 * - Health Connect must be available on the device (Wear OS 4+)
 * - App must have Health Connect read permissions granted
 */
class HealthConnectReader(private val context: Context) {

  companion object {
    private const val TAG = "HealthConnectRdr"
  }

  private var client: HealthConnectClient? = null

  fun initialize(): Boolean {
    return try {
      client = HealthConnectClient.getOrCreate(context)
      Log.d(TAG, "Health Connect client initialized")
      true
    } catch (e: Exception) {
      Log.w(TAG, "Health Connect not available: ${e.message}")
      false
    }
  }

  /**
   * Read the most recent HRV and SpO2 values from the last 24 hours.
   * Returns a FitbitMetrics object or null if unavailable.
   */
  suspend fun readLatestMetrics(): FitbitMetrics? {
    val hcClient = client ?: return null

    val now = Instant.now()
    val dayAgo = now.minus(24, ChronoUnit.HOURS)
    val timeRange = TimeRangeFilter.between(dayAgo, now)

    var hrv = 0f
    var spO2 = 0
    // Note: Skin temperature is not yet in Health Connect's standard record types
    // It may become available in future API versions
    var skinTemp = 0f

    // Read HRV (RMSSD)
    try {
      val hrvResponse = hcClient.readRecords(
        ReadRecordsRequest(
          recordType = HeartRateVariabilityRmssdRecord::class,
          timeRangeFilter = timeRange
        )
      )
      if (hrvResponse.records.isNotEmpty()) {
        hrv = hrvResponse.records.last().heartRateVariabilityMillis.toFloat()
        Log.d(TAG, "HRV from Health Connect: $hrv ms")
      }
    } catch (e: Exception) {
      Log.w(TAG, "HRV read failed: ${e.message}")
    }

    // Read SpO2
    try {
      val spo2Response = hcClient.readRecords(
        ReadRecordsRequest(
          recordType = OxygenSaturationRecord::class,
          timeRangeFilter = timeRange
        )
      )
      if (spo2Response.records.isNotEmpty()) {
        val record = spo2Response.records.last()
        spO2 = record.percentage.value.toInt()
        Log.d(TAG, "SpO2 from Health Connect: $spO2%")
      }
    } catch (e: Exception) {
      Log.w(TAG, "SpO2 read failed: ${e.message}")
    }

    return if (hrv > 0f || spO2 > 0) {
      FitbitMetrics(hrv = hrv, spO2 = spO2, skinTemperature = skinTemp)
    } else {
      null
    }
  }
}

data class FitbitMetrics(
  val hrv: Float = 0f,
  val spO2: Int = 0,
  val skinTemperature: Float = 0f
)
