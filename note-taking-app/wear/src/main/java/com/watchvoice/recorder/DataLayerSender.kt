package com.watchvoice.recorder

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Sends recordings to the paired phone via Wear OS DataClient with Assets.
 * Uses Asset.createFromBytes() which is the documented reliable approach.
 */
class DataLayerSender(private val context: Context) {

    companion object {
        private const val TAG = "DataLayerSender"
        const val RECORDING_PATH = "/voice_recording"
        const val KEY_AUDIO = "audio_data"
        const val KEY_FILENAME = "filename"
        const val KEY_TIMESTAMP = "timestamp"
    }

    enum class SendStatus {
        SENDING, SENT, NO_PHONE, ERROR
    }

    /**
     * Send a recording file to the connected phone via DataClient.
     */
    suspend fun sendRecording(file: File): SendStatus {
        return try {
            // Check for connected nodes first
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            Log.d(TAG, "Connected nodes: ${nodes.size}")
            nodes.forEach { node ->
                Log.d(TAG, "  Node: ${node.displayName} (${node.id}), nearby=${node.isNearby}")
            }

            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes found")
                return SendStatus.NO_PHONE
            }

            Log.d(TAG, "Reading file ${file.name} (${file.length()} bytes)")

            // Read file into byte array and create Asset from bytes
            // This is the documented reliable approach vs createFromUri
            val bytes = file.readBytes()
            val asset = Asset.createFromBytes(bytes)

            Log.d(TAG, "Created asset from ${bytes.size} bytes")

            // Build data item with unique path (timestamp ensures each recording is unique)
            val timestamp = System.currentTimeMillis()
            val path = "$RECORDING_PATH/$timestamp"

            val dataMapRequest = PutDataMapRequest.create(path).apply {
                dataMap.putAsset(KEY_AUDIO, asset)
                dataMap.putString(KEY_FILENAME, file.name)
                dataMap.putLong(KEY_TIMESTAMP, timestamp)
            }

            val putDataReq = dataMapRequest.asPutDataRequest().setUrgent()

            Log.d(TAG, "Sending DataItem to path: $path")

            // Send via DataClient
            val result = Wearable.getDataClient(context).putDataItem(putDataReq).await()
            Log.d(TAG, "DataItem sent successfully: ${result.uri}")

            SendStatus.SENT
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send recording: ${e.message}", e)
            SendStatus.ERROR
        }
    }
}
