package com.watchvoice.recorder

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
 * Receives recordings from watch. Only saves the file and creates the note.
 * Does NOT transcribe — transcription is handled by TranscriptionWorker
 * to avoid service lifecycle cancellation.
 */
class RecordingReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "RecordingReceiver"
        private const val RECORDING_PATH_PREFIX = "/voice_recording"
        const val ACTION_NEW_NOTE = "com.watchvoice.recorder.NEW_NOTE"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")

        try {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path?.startsWith(RECORDING_PATH_PREFIX) == true
                ) {
                    val frozenItem = event.dataItem.freeze()
                    // Process synchronously in this callback (no coroutine)
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

            val filename = dataMap.getString("filename") ?: "recording_${System.currentTimeMillis()}.m4a"
            val audioAsset = dataMap.getAsset("audio_data") ?: run {
                Log.e(TAG, "No audio asset")
                return
            }

            val repo = NotesRepository(applicationContext)

            if (repo.hasNoteForFile(filename)) {
                Log.d(TAG, "Already processed: $filename")
                return
            }

            Log.d(TAG, "Processing: $filename")

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

            // Add note with isTranscribing=true, transcription will happen in MainActivity
            repo.addNote(audioFile)
            sendBroadcast(Intent(ACTION_NEW_NOTE))

            Log.d(TAG, "Note created, transcription will start via MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message}", e)
        }
    }
}
