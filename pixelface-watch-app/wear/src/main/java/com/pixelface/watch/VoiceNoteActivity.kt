package com.pixelface.watch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * VoiceNoteActivity — launched when user taps the robot face on the watch face.
 * Uses Android's built-in speech recognizer to capture a voice note,
 * then sends it to the phone app via Wearable Data Layer.
 */
class VoiceNoteActivity : Activity() {

  companion object {
    private const val TAG = "VoiceNote"
    private const val SPEECH_REQUEST_CODE = 100
    private const val RECORDING_PATH = "/voice_recording"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "Starting voice note capture")
    launchSpeechRecognizer()
  }

  private fun launchSpeechRecognizer() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your note...")
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    try {
      startActivityForResult(intent, SPEECH_REQUEST_CODE)
    } catch (e: Exception) {
      Log.e(TAG, "Speech recognizer not available", e)
      Toast.makeText(this, "Speech not available", Toast.LENGTH_SHORT).show()
      finish()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == SPEECH_REQUEST_CODE) {
      if (resultCode == RESULT_OK && data != null) {
        val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spokenText = results?.firstOrNull() ?: ""

        if (spokenText.isNotBlank()) {
          sendNoteToPhone(spokenText)
          Toast.makeText(this, "Note sent ✓", Toast.LENGTH_SHORT).show()
          Log.d(TAG, "Sending note to phone: $spokenText")
        }
      } else {
        Log.d(TAG, "Speech cancelled or failed")
      }
      finish()
    }
  }

  /**
   * Send the transcript to the phone via Wearable Data Layer.
   * The phone's RecordingReceiver will pick it up and save it as a note.
   */
  private fun sendNoteToPhone(text: String) {
    val timestamp = System.currentTimeMillis()
    val filename = "watch_note_${timestamp}.txt"

    val putDataMapRequest = PutDataMapRequest.create(
      "$RECORDING_PATH/$timestamp"
    ).apply {
      dataMap.putString("filename", filename)
      dataMap.putString("transcript", text)
      dataMap.putLong("timestamp", timestamp)
    }

    val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()

    Wearable.getDataClient(this).putDataItem(putDataRequest)
      .addOnSuccessListener {
        Log.d(TAG, "✓ Note sent to phone: \"${text.take(60)}\"")
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Failed to send note to phone", e)
        Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
      }
  }
}
