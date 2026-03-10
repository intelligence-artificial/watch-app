package com.tamagotchi.pet

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Sends fitness snapshots (steps, heart rate) to the phone via DataLayer.
 * Adapted from the note-taking-app DataLayerSender pattern.
 */
class FitnessDataSender(private val context: Context) {

    companion object {
        private const val TAG = "FitnessDataSender"
    }

    enum class SendStatus {
        SENDING, SENT, NO_PHONE, ERROR
    }

    /**
     * Send a fitness snapshot to the connected phone.
     */
    suspend fun sendFitnessUpdate(steps: Int, heartRate: Int): SendStatus {
        return try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found")
                return SendStatus.NO_PHONE
            }

            val timestamp = System.currentTimeMillis()
            val path = "${DataLayerPaths.FITNESS_UPDATE_PATH}/$timestamp"

            val dataMapRequest = PutDataMapRequest.create(path).apply {
                dataMap.putInt(DataLayerPaths.KEY_STEPS, steps)
                dataMap.putInt(DataLayerPaths.KEY_HEART_RATE, heartRate)
                dataMap.putLong(DataLayerPaths.KEY_TIMESTAMP, timestamp)
            }

            val putDataReq = dataMapRequest.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(putDataReq).await()
            Log.d(TAG, "Fitness update sent: steps=$steps, hr=$heartRate")

            SendStatus.SENT
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send fitness update: ${e.message}", e)
            SendStatus.ERROR
        }
    }
}
