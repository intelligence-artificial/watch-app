package com.wetpet.mobile

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileOutputStream

/**
 * Receives voice notes from the WetPet watch app via Wear DataLayer.
 * Supports two modes:
 *   1. Audio + transcript: saves .m4a file + applies transcript
 *   2. Transcript only: creates note with just the transcribed text (from Google Speech Activity)
 */
class RecordingReceiver : WearableListenerService() {

  companion object {
    private const val TAG = "RecordingReceiver"
    private const val RECORDING_PATH_PREFIX = "/voice_recording"
    const val ACTION_NEW_NOTE = "com.wetpet.mobile.NEW_NOTE"
  }

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    Log.d(TAG, "onDataChanged: ${dataEvents.count} events")

    try {
      for (event in dataEvents) {
        if (event.type == DataEvent.TYPE_CHANGED &&
          event.dataItem.uri.path?.startsWith(RECORDING_PATH_PREFIX) == true
        ) {
          val frozenItem = event.dataItem.freeze()
          processRecordingSync(frozenItem)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in onDataChanged: ${e.message}", e)
    } finally {
      dataEvents.release()
    }
  }

  private fun processRecordingSync(dataItem: com.google.android.gms.wearable.DataItem) {
    try {
      val dataMapItem = DataMapItem.fromDataItem(dataItem)
      val dataMap = dataMapItem.dataMap

      val filename = dataMap.getString("filename")
        ?: "note_${System.currentTimeMillis()}.txt"
      val transcript = dataMap.getString("transcript") ?: ""
      val audioAsset = dataMap.getAsset("audio_data") // May be null for transcript-only

      val repo = NotesRepository(applicationContext)

      if (repo.hasNoteForFile(filename)) {
        Log.d(TAG, "Already processed: $filename")
        return
      }

      if (audioAsset != null) {
        // Mode 1: Audio + transcript
        Log.d(TAG, "Processing audio: $filename")

        val dataClient = Wearable.getDataClient(applicationContext)
        val assetResult = com.google.android.gms.tasks.Tasks.await(
          dataClient.getFdForAsset(audioAsset)
        )
        val inputStream = assetResult.inputStream ?: run {
          Log.e(TAG, "Null input stream")
          return
        }

        val audioFile = File(repo.recordingsDir, filename)
        FileOutputStream(audioFile).use { output ->
          inputStream.copyTo(output, bufferSize = 8192)
        }
        inputStream.close()

        Log.d(TAG, "Saved: ${audioFile.name} (${audioFile.length()} bytes)")

        if (transcript.isNotBlank()) {
          val note = repo.addNote(audioFile)
          repo.updateTranscript(note.id, transcript, 0)
          Log.d(TAG, "✓ Audio + transcript: \"${transcript.take(60)}\"")
        } else {
          repo.addNote(audioFile)
          Log.d(TAG, "Audio only, queued for Vosk transcription")
        }
      } else if (transcript.isNotBlank()) {
        // Mode 2: Transcript only (from Google Speech Activity on watch)
        Log.d(TAG, "Processing transcript-only note")

        // Create a note with just the transcript, no audio file
        val note = repo.addTranscriptNote(filename, transcript)
        Log.d(TAG, "✓ Transcript note: \"${transcript.take(60)}\"")
      } else {
        Log.w(TAG, "Empty note (no audio, no transcript) — skipping")
        return
      }

      sendBroadcast(Intent(ACTION_NEW_NOTE).setPackage(packageName))
    } catch (e: Exception) {
      Log.e(TAG, "Failed: ${e.message}", e)
    }
  }
}
