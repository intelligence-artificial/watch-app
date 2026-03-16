package com.pixelface.watch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Runs Google SpeechRecognizer on the Pixel Watch 2 concurrently with MediaRecorder.
 * Uses createOnDeviceSpeechRecognizer (API 31+) for reliability on Wear OS.
 * Must be started with a delay after MediaRecorder to avoid mic contention.
 */
class SpeechRecognizerHelper(private val context: Context) {

  companion object {
    private const val TAG = "SpeechRecHelper"
  }

  private var recognizer: SpeechRecognizer? = null
  private val segments = mutableListOf<String>()
  private var isListening = false
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * Start listening — call with a delay AFTER MediaRecorder starts.
   * Creates recognizer + starts listening in one shot on the main thread.
   */
  fun startListening() {
    mainHandler.post {
      if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Log.w(TAG, "Speech recognition not available")
        return@post
      }

      segments.clear()
      isListening = true

      try {
        // createOnDeviceSpeechRecognizer is NOT available on Pixel Watch 2
        // Use standard createSpeechRecognizer (Google's remote service)
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d(TAG, "Created SpeechRecognizer (remote)")

        recognizer?.setRecognitionListener(createListener())
        startIntent()
        Log.d(TAG, "Listening started")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start: ${e.message}", e)
      }
    }
  }

  private fun createListener(): RecognitionListener = object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) {
      Log.d(TAG, "✓ Ready for speech (mic acquired)")
    }

    override fun onBeginningOfSpeech() {
      Log.d(TAG, "✓ User speaking")
    }

    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
      Log.d(TAG, "End of speech detected")
    }

    override fun onError(error: Int) {
      val msg = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "No mic permission"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Unknown error $error"
      }
      Log.w(TAG, "Error: $msg ($error)")

      // Restart on recoverable errors while still recording
      if (isListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
        && error != SpeechRecognizer.ERROR_CLIENT
      ) {
        Log.d(TAG, "Restarting after error...")
        mainHandler.postDelayed({ if (isListening) startIntent() }, 300)
      }
    }

    override fun onResults(results: Bundle?) {
      val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
      if (!matches.isNullOrEmpty()) {
        val text = matches[0]
        Log.d(TAG, "✓ Segment: \"$text\"")
        segments.add(text)
      }
      // Restart for continuous recognition while recording
      if (isListening) {
        mainHandler.postDelayed({ if (isListening) startIntent() }, 200)
      }
    }

    override fun onPartialResults(partial: Bundle?) {
      val matches = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
      if (!matches.isNullOrEmpty()) {
        Log.d(TAG, "Partial: \"${matches[0]}\"")
      }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
  }

  private fun startIntent() {
    try {
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Long silence timeouts for voice notes
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000L)
      }
      recognizer?.startListening(intent)
    } catch (e: Exception) {
      Log.e(TAG, "startListening failed: ${e.message}")
    }
  }

  /** Stop listening and return joined transcript */
  fun stopListening(): String {
    isListening = false
    try {
      recognizer?.stopListening()
      recognizer?.destroy()
      recognizer = null
    } catch (e: Exception) {
      Log.w(TAG, "Stop error: ${e.message}")
    }

    val transcript = segments.joinToString(" ").trim()
    Log.d(TAG, "Final transcript (${segments.size} segments): \"$transcript\"")
    return transcript
  }

  fun destroy() {
    isListening = false
    try {
      recognizer?.cancel()
      recognizer?.destroy()
      recognizer = null
    } catch (_: Exception) {}
  }
}
