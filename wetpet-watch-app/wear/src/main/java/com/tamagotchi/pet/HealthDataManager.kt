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
 * Passive health data manager for the wear companion app.
 */
class HealthDataManager(context: Context) {

    companion object {
        private const val TAG = "HealthDataMgr"
    }

    @Volatile var heartRate: Int = 0
        private set
    @Volatile var dailySteps: Int = 0
        private set

    private val passiveClient: PassiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    private val callback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
            if (hrPoints.isNotEmpty()) {
                val latest = hrPoints.last().value.toInt()
                if (latest in 30..220) {
                    heartRate = latest
                    Log.d(TAG, "HR updated: $heartRate bpm")
                }
            }

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
