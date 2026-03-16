package com.pixelface.watch

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RecordingGreen = Color(0xFF43A047)
private val RecordingRed = Color(0xFFE53935)

/** Safe vibration helper — won't crash if permission missing */
private fun safeVibrate(context: android.content.Context, durationMs: Long) {
  try {
    val vib = context.getSystemService(Vibrator::class.java)
    vib?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
  } catch (e: Exception) {
    Log.w("VoiceNote", "Vibrate failed: ${e.message}")
  }
}

@Composable
fun VoiceNoteScreen(
  recorderService: AudioRecorderService,
  dataLayerSender: DataLayerSender,
  onNavigateToRecordings: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val talkingAnimator = remember { TalkingAnimator() }

  var syncStatus by remember { mutableStateOf<String?>(null) }
  var recordingCount by remember {
    mutableIntStateOf(
      context.filesDir.listFiles()?.count { it.extension == "m4a" } ?: 0
    )
  }
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted -> hasPermission = granted }

  // Google Speech Activity launcher — same approach as ChatScreen (PROVEN WORKING)
  val speechLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val transcript = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull() ?: ""

      if (transcript.isNotBlank()) {
        Log.d("VoiceNote", "Got transcript: \"${transcript.take(60)}\"")
        safeVibrate(context, 100)
        syncStatus = "Sending…"

        scope.launch {
          try {
            val sendResult = dataLayerSender.sendTranscriptOnly(transcript)
            syncStatus = when (sendResult) {
              DataLayerSender.SendStatus.SENT -> "Sent to phone ✓"
              DataLayerSender.SendStatus.NO_PHONE -> "Saved locally"
              else -> "Send error"
            }
          } catch (e: Exception) {
            syncStatus = "Send error"
            Log.e("VoiceNote", "Send failed: ${e.message}")
          }
        }
      } else {
        syncStatus = "No speech detected"
      }
    } else {
      syncStatus = "Cancelled"
      Log.d("VoiceNote", "Speech cancelled (code: ${result.resultCode})")
    }
  }

  // Animation ticker (10fps)
  var animTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) { delay(100L); animTimeMs = System.currentTimeMillis() }
  }

  // Auto-clear sync status
  LaunchedEffect(syncStatus) {
    if (syncStatus != null && syncStatus != "Sending…") { delay(3000); syncStatus = null }
  }

  fun doStartRecording() {
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }
    // Launch Google's speech recognition Activity
    // Same as ChatScreen — this is the PROVEN WORKING approach
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your voice note...")
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }
    speechLauncher.launch(intent)
  }

  // Face frame
  val faceFrame = remember(animTimeMs) {
    talkingAnimator.getCurrentFrame(animTimeMs, false)
  }
  val faceColor = Color(0xFF50E6FF)

  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xFF020206)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Status text
      Text(
        text = when {
          syncStatus != null -> syncStatus!!
          else -> "Tap to Record"
        },
        color = when {
          syncStatus?.contains("✓") == true -> RecordingGreen
          syncStatus != null -> Color(0xFF42A5F5)
          else -> Color(0xFF888888)
        },
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(4.dp))

      // CRT Face (tap = launch Google speech)
      Box(
        modifier = Modifier
          .size(90.dp)
          .clickable { doStartRecording() }
      ) {
        MiniCrtCanvas(faceFrame = faceFrame, faceColor = faceColor, sizeDp = 90)
      }

      Spacer(Modifier.height(6.dp))

      // Recordings button
      if (recordingCount > 0) {
        Chip(
          onClick = { onNavigateToRecordings() },
          label = {
            Text(
              "$recordingCount recording${if (recordingCount != 1) "s" else ""} ▸",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = Color(0xFF888888)
            )
          },
          colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.04f)),
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    }
  }
}

/** Mini CRT canvas for sub-screens */
@Composable
fun MiniCrtCanvas(
  faceFrame: Array<IntArray>,
  faceColor: Color,
  sizeDp: Int = 80
) {
  val monitorDark = Color(0xFF1E1E28)
  val monitorMid = Color(0xFF3C3C50)
  val monitorLight = Color(0xFF646482)
  val monitorStand = Color(0xFF323241)
  val screenBg = Color(
    red = (faceColor.red * 0.08f),
    green = (faceColor.green * 0.08f),
    blue = (faceColor.blue * 0.08f),
    alpha = 1f
  )
  val paletteMap = mapOf(1 to monitorDark, 2 to monitorMid, 3 to monitorLight, 4 to screenBg, 5 to monitorStand)

  Canvas(modifier = Modifier.size(sizeDp.dp)) {
    val pixelSize = size.width / PixelFaceRenderer.GRID

    PixelFaceRenderer.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, idx ->
        if (idx == 0) return@forEachIndexed
        val c = paletteMap[idx] ?: return@forEachIndexed
        drawRect(c, Offset(col * pixelSize, row * pixelSize), Size(pixelSize, pixelSize))
      }
    }

    val offX = PixelFaceRenderer.SCREEN_COL_START * pixelSize
    val offY = PixelFaceRenderer.SCREEN_ROW_START * pixelSize
    faceFrame.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, px ->
        if (px == 0) return@forEachIndexed
        drawRect(faceColor, Offset(offX + col * pixelSize, offY + row * pixelSize), Size(pixelSize, pixelSize))
      }
    }
  }
}

private fun formatRecTime(seconds: Long): String {
  val m = seconds / 60
  val s = seconds % 60
  return String.format("%d:%02d", m, s)
}
