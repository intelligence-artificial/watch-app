package com.pixelface.watch

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingsListScreen(
  playerService: AudioPlayerService,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val focusRequester = remember { FocusRequester() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  // Mutable recordings list (supports deletion)
  var recordings by remember {
    mutableStateOf(
      context.filesDir.listFiles()
        ?.filter { it.extension == "m4a" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
    )
  }

  var playingFile by remember { mutableStateOf<File?>(null) }
  var confirmDeleteFile by remember { mutableStateOf<File?>(null) }

  fun deleteRecording(file: File) {
    try {
      if (playingFile == file) {
        playerService.stop()
        playingFile = null
      }
      file.delete()
      recordings = recordings.filter { it != file }
      Log.d("Recordings", "Deleted ${file.name}")
    } catch (e: Exception) {
      Log.e("Recordings", "Delete failed: ${e.message}")
    }
    confirmDeleteFile = null
  }

  fun deleteAll() {
    playerService.stop()
    playingFile = null
    recordings.forEach { it.delete() }
    recordings = emptyList()
    confirmDeleteFile = null
  }

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    autoCentering = null,
    contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
      .onRotaryScrollEvent { event ->
        scope.launch { listState.scroll(MutatePriority.UserInput) { scrollBy(event.verticalScrollPixels) } }
        true
      }
      .focusRequester(focusRequester)
      .focusable()
  ) {
    // Header
    item {
      Text(
        "🎙️ Recordings",
        color = Color(0xFF43A047),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
      )
    }

    if (recordings.isEmpty()) {
      item {
        Text(
          "No recordings",
          color = Color(0xFF888888),
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    } else {
      // Count + delete all
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            "${recordings.size} file${if (recordings.size != 1) "s" else ""}",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            "🗑 Delete All",
            color = Color(0xFFFF4646).copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable { deleteAll() }
          )
        }
      }

      items(recordings, key = { it.absolutePath }) { file ->
        val isPlaying = playingFile == file
        val isConfirmingDelete = confirmDeleteFile == file
        val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
          .format(Date(file.lastModified()))
        val sizeKb = file.length() / 1024

        if (isConfirmingDelete) {
          // Delete confirmation row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 2.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFFF4646).copy(alpha = 0.15f))
              .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Delete?",
              color = Color(0xFFFF4646),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "✓ Yes",
              color = Color(0xFFFF4646),
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFF4646).copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { deleteRecording(file) }
            )
            Text(
              "✕ No",
              color = Color(0xFF888888),
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { confirmDeleteFile = null }
            )
          }
        } else {
          // Normal recording row: tap = play, long-press = delete
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 2.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (isPlaying) Color(0xFF43A047).copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.04f)
              )
              .combinedClickable(
                onClick = {
                  if (isPlaying) {
                    playerService.stop()
                    playingFile = null
                  } else {
                    try {
                      playerService.play(file) { playingFile = null }
                      playingFile = file
                    } catch (e: Exception) {
                      Log.e("Recordings", "Play failed: ${e.message}")
                    }
                  }
                },
                onLongClick = {
                  confirmDeleteFile = file
                }
              )
              .padding(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  dateStr,
                  color = if (isPlaying) Color(0xFF43A047) else Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
                Text(
                  "${sizeKb}KB",
                  color = Color.White.copy(alpha = 0.4f),
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
              Text(
                when {
                  isPlaying -> "▶"
                  else -> "🗑"
                },
                fontSize = 14.sp,
                color = if (isPlaying) Color(0xFF43A047) else Color.White.copy(alpha = 0.2f)
              )
            }
          }
        }
      }
    }
  }
}
