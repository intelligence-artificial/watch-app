package com.wetpet.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
  repo: NotesRepository,
  noteId: String,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val note = remember { repo.loadNotes().find { it.id == noteId } }
  var isPlaying by remember { mutableStateOf(false) }
  var player by remember { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      player?.release()
    }
  }

  if (note == null) {
    onBack()
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            note.displayTime,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          )
        },
        navigationIcon = {
          TextButton(onClick = onBack) {
            Text("← Back", color = MaterialTheme.colorScheme.primary)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      // Playback controls
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              "Audio Recording",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            if (note.durationMs > 0) {
              Text(
                note.durationDisplay,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
              )
            }
          }

          Button(
            onClick = {
              if (isPlaying) {
                player?.stop()
                player?.release()
                player = null
                isPlaying = false
              } else {
                val audioFile = File(note.audioPath)
                if (audioFile.exists()) {
                  player = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    prepare()
                    setOnCompletionListener {
                      isPlaying = false
                      it.release()
                      player = null
                    }
                    start()
                  }
                  isPlaying = true
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (isPlaying)
                MaterialTheme.colorScheme.error
              else
                MaterialTheme.colorScheme.primary
            ),
            shape = CircleShape
          ) {
            Text(if (isPlaying) "⏹ Stop" else "▶ Play")
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Transcript
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "Transcript",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = note.transcript ?: if (note.isTranscribing) "Transcribing…" else "No transcript available",
            color = MaterialTheme.colorScheme.onSurface.copy(
              alpha = if (note.transcript != null) 0.95f else 0.5f
            ),
            fontSize = 16.sp,
            lineHeight = 26.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Action buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
              ClipData.newPlainText("Voice Note", note.transcript ?: "")
            )
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          enabled = !note.transcript.isNullOrBlank()
        ) {
          Text("📋 Copy")
        }

        OutlinedButton(
          onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_TEXT, note.transcript ?: "")
            }
            context.startActivity(Intent.createChooser(intent, "Share Voice Note"))
          },
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(12.dp),
          enabled = !note.transcript.isNullOrBlank()
        ) {
          Text("📤 Share")
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = {
          repo.deleteNote(noteId)
          onBack()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        )
      ) {
        Text("🗑 Delete Note")
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}
