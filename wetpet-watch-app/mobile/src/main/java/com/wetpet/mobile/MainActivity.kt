package com.wetpet.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*

/**
 * Main activity. Handles transcription of pending notes.
 * RecordingReceiver saves the file, MainActivity transcribes it.
 */
class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

  companion object {
    private const val TAG = "WetPetMobile"
  }

  private val transcriptionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "MainActivity created")
    setContent {
      VoiceNotesApp()
    }
    startTranscriptionWorker()
  }

  override fun onResume() {
    super.onResume()
    Wearable.getDataClient(this).addListener(this)
    startTranscriptionWorker()
  }

  override fun onPause() {
    super.onPause()
    Wearable.getDataClient(this).removeListener(this)
  }

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
    dataEvents.release()
    transcriptionScope.launch {
      delay(3000)
      transcribePendingNotes()
    }
  }

  private fun startTranscriptionWorker() {
    transcriptionScope.launch {
      delay(2000)
      transcribePendingNotes()
    }
  }

  private suspend fun transcribePendingNotes() {
    val repo = NotesRepository(applicationContext)
    val notes = repo.loadNotes()
    val pending = notes.filter { it.isTranscribing }

    if (pending.isEmpty()) return

    Log.d(TAG, "${pending.size} notes pending transcription")

    val service = TranscriptionService(applicationContext)

    for (note in pending) {
      try {
        val audioFile = java.io.File(note.audioPath)
        if (!audioFile.exists()) {
          Log.e(TAG, "Audio file missing: ${note.audioPath}")
          repo.updateTranscript(note.id, "[Audio file missing]", 0)
          continue
        }

        Log.d(TAG, "Transcribing: ${audioFile.name}")
        val startTime = System.currentTimeMillis()

        val transcript = service.transcribe(audioFile)

        val processingTimeMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "Done in ${processingTimeMs}ms: ${transcript.take(60)}...")

        repo.updateTranscript(note.id, transcript, processingTimeMs)
        sendBroadcast(Intent(RecordingReceiver.ACTION_NEW_NOTE))
      } catch (e: Exception) {
        Log.e(TAG, "Transcription failed for ${note.id}: ${e.message}", e)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    transcriptionScope.cancel()
  }
}
