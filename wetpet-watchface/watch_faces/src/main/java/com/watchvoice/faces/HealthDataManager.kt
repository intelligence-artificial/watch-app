package com.watchvoice.faces

import android.content.Context
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
 * Health data manager for watch faces.
 * Pulls heart rate, daily steps, and calories via passive Health Services.
 * Falls back gracefully to 0 if a sensor isn't supported.
 */
class HealthDataManager(context: Context) {

    companion object {
        private const val TAG = "WF_HealthDataMgr"
    }

    @Volatile var heartRate: Int = 0
        private set
    @Volatile var dailySteps: Int = 0
        private set
    @Volatile var calories: Int = 0
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val passiveClient: PassiveMonitoringClient =
        HealthServices.getClient(context).passiveMonitoringClient

    private val callback = object : PassiveListenerCallback {
        override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
            // Heart rate
            try {
                val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
                if (hrPoints.isNotEmpty()) {
                    val latest = hrPoints.last().value.toInt()
                    if (latest in 30..220) {
                        heartRate = latest
                        Log.d(TAG, "HR: $heartRate bpm")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HR read failed: ${e.message}")
            }

            // Steps
            try {
                val stepPoints = dataPoints.getData(DataType.STEPS_DAILY)
                if (stepPoints.isNotEmpty()) {
                    dailySteps = stepPoints.last().value.toInt()
                    Log.d(TAG, "Steps: $dailySteps")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Steps read failed: ${e.message}")
            }

            // Calories
            try {
                val calPoints = dataPoints.getData(DataType.CALORIES_DAILY)
                if (calPoints.isNotEmpty()) {
                    calories = calPoints.last().value.toInt()
                    Log.d(TAG, "Calories: $calories")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Calories read failed: ${e.message}")
            }
        }
    }

    fun start() {
        scope.launch {
            try {
                // Check capabilities before registering
                val capabilities = passiveClient.getCapabilitiesAsync().get()
                val supported = capabilities.supportedDataTypesPassiveMonitoring

                val requestedTypes = mutableSetOf<DataType<*, *>>()
                val wantedTypes = listOf(
                    DataType.HEART_RATE_BPM,
                    DataType.STEPS_DAILY,
                    DataType.CALORIES_DAILY
                )

                for (type in wantedTypes) {
                    if (type in supported) {
                        requestedTypes.add(type)
                        Log.d(TAG, "✓ ${type.name} supported")
                    } else {
                        Log.w(TAG, "✗ ${type.name} not supported")
                    }
                }

                if (requestedTypes.isEmpty()) {
                    Log.e(TAG, "No health types supported!")
                    return@launch
                }

                val config = PassiveListenerConfig.Builder()
                    .setDataTypes(requestedTypes)
                    .build()

                passiveClient.setPassiveListenerCallback(config, callback)
                Log.d(TAG, "Passive listener registered (${requestedTypes.size} types)")
            } catch (e: Exception) {
                Log.e(TAG, "Capability check failed, using defaults: ${e.message}")
                // Fallback
                val config = PassiveListenerConfig.Builder()
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.STEPS_DAILY))
                    .build()
                passiveClient.setPassiveListenerCallback(config, callback)
            }
        }
    }

    fun stop() {
        passiveClient.clearPassiveListenerCallbackAsync()
        Log.d(TAG, "Passive listener cleared")
    }
}
