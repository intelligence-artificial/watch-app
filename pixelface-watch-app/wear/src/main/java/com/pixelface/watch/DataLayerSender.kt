package com.pixelface.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Sends recordings and health data to the paired phone via Wear OS DataClient.
 * Ported from the note-taking-app (handles voice recordings)
 * and extended for health data sync.
 */
class DataLayerSender(private val context: Context) {

  companion object {
    private const val TAG = "DataLayerSender"
    const val RECORDING_PATH = "/voice_recording"
    const val HEALTH_SYNC_PATH = "/health_sync"
    const val KEY_AUDIO = "audio_data"
    const val KEY_FILENAME = "filename"
    const val KEY_TIMESTAMP = "timestamp"
    const val KEY_TRANSCRIPT = "transcript"
    const val KEY_HR_DATA = "hr_data"
    const val KEY_STEPS_DATA = "steps_data"
    const val KEY_CALORIES_DATA = "calories_data"
  }

  enum class SendStatus {
    SENDING, SENT, NO_PHONE, ERROR
  }

  /** Send a voice recording + transcript to the phone */
  suspend fun sendRecording(file: File, transcript: String = ""): SendStatus {
    return try {
      val nodes = Wearable.getNodeClient(context).connectedNodes.await()
      if (nodes.isEmpty()) return SendStatus.NO_PHONE

      val bytes = file.readBytes()
      val asset = Asset.createFromBytes(bytes)
      val timestamp = System.currentTimeMillis()
      val path = "$RECORDING_PATH/$timestamp"

      val dataMapRequest = PutDataMapRequest.create(path).apply {
        dataMap.putAsset(KEY_AUDIO, asset)
        dataMap.putString(KEY_FILENAME, file.name)
        dataMap.putLong(KEY_TIMESTAMP, timestamp)
        if (transcript.isNotBlank()) {
          dataMap.putString(KEY_TRANSCRIPT, transcript)
        }
      }

      Wearable.getDataClient(context).putDataItem(
        dataMapRequest.asPutDataRequest().setUrgent()
      ).await()

      Log.d(TAG, "Recording sent: ${file.name} (transcript: ${transcript.take(40)}...)")
      SendStatus.SENT
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send recording: ${e.message}", e)
      SendStatus.ERROR
    }
  }

  /** Send transcript only (no audio file) — from Google Speech Activity */
  suspend fun sendTranscriptOnly(transcript: String): SendStatus {
    return try {
      val nodes = Wearable.getNodeClient(context).connectedNodes.await()
      if (nodes.isEmpty()) return SendStatus.NO_PHONE

      val timestamp = System.currentTimeMillis()
      val path = "$RECORDING_PATH/$timestamp"

      val dataMapRequest = PutDataMapRequest.create(path).apply {
        dataMap.putString(KEY_FILENAME, "voice_note_${timestamp}.txt")
        dataMap.putLong(KEY_TIMESTAMP, timestamp)
        dataMap.putString(KEY_TRANSCRIPT, transcript)
      }

      Wearable.getDataClient(context).putDataItem(
        dataMapRequest.asPutDataRequest().setUrgent()
      ).await()

      Log.d(TAG, "Transcript sent: ${transcript.take(60)}...")
      SendStatus.SENT
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send transcript: ${e.message}", e)
      SendStatus.ERROR
    }
  }

  /** Send health history data to the phone for CSV export */
  suspend fun sendHealthData(
    hrJson: String,
    stepsJson: String,
    caloriesJson: String
  ): SendStatus {
    return try {
      val nodes = Wearable.getNodeClient(context).connectedNodes.await()
      if (nodes.isEmpty()) return SendStatus.NO_PHONE

      val timestamp = System.currentTimeMillis()
      val path = "$HEALTH_SYNC_PATH/$timestamp"

      val dataMapRequest = PutDataMapRequest.create(path).apply {
        dataMap.putString(KEY_HR_DATA, hrJson)
        dataMap.putString(KEY_STEPS_DATA, stepsJson)
        dataMap.putString(KEY_CALORIES_DATA, caloriesJson)
        dataMap.putLong(KEY_TIMESTAMP, timestamp)
      }

      Wearable.getDataClient(context).putDataItem(
        dataMapRequest.asPutDataRequest().setUrgent()
      ).await()

      Log.d(TAG, "Health data synced to phone")
      SendStatus.SENT
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send health data: ${e.message}", e)
      SendStatus.ERROR
    }
  }
}
