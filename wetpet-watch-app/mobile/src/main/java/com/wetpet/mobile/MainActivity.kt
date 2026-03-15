package com.wetpet.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable

/**
 * Main activity. Displays voice notes received from the watch.
 * Transcription is handled by Google Speech on the watch — no Vosk needed.
 */
class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

  companion object {
    private const val TAG = "WetPetMobile"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "MainActivity created")
    setContent {
      VoiceNotesApp()
    }
  }

  override fun onResume() {
    super.onResume()
    Wearable.getDataClient(this).addListener(this)
  }

  override fun onPause() {
    super.onPause()
    Wearable.getDataClient(this).removeListener(this)
  }

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
    dataEvents.release()
  }
}
