package com.watchvoice.faces

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Lightweight manager for passive health data (heart rate + daily steps).
 * Uses Health Services PassiveMonitoringClient for background-efficient reads.
 * Falls back gracefully to 0 if the watch doesn't support a sensor.
 */
class HealthDataManager(context: Context) {

    companion object {
        private const val TAG = "HealthDataMgr"
    }

    // Latest values – read from the renderer on every frame
    @Volatile var heartRate: Int = 0
        private set
    @Volatile var dailySteps: Int = 0
        private set

    private val passiveClient: PassiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    private val callback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            // Heart rate – grab most recent sample
            val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
            if (hrPoints.isNotEmpty()) {
                val latest = hrPoints.last().value.toInt()
                if (latest in 30..220) { // sanity check
                    heartRate = latest
                    Log.d(TAG, "HR updated: $heartRate bpm")
                }
            }

            // Daily steps – cumulative since midnight
            val stepPoints = dataPoints.getData(DataType.STEPS_DAILY)
            if (stepPoints.isNotEmpty()) {
                val latest = stepPoints.last().value
                dailySteps = latest.toInt()
                Log.d(TAG, "Steps updated: $dailySteps")
            }
        }
    }

    fun start() {
        val config = PassiveListenerConfig.Builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
            .build()

        passiveClient.setPassiveListenerCallback(config, callback)
        Log.d(TAG, "Passive health listener registered")
    }

    fun stop() {
        passiveClient.clearPassiveListenerCallbackAsync()
        Log.d(TAG, "Passive health listener cleared")
    }
}
