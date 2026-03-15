package com.wetpet.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
  repo: NotesRepository,
  onNoteClick: (String) -> Unit
) {
  val context = LocalContext.current
  var notes by remember { mutableStateOf(repo.loadNotes()) }

  // Poll for new notes every 2 seconds
  LaunchedEffect(Unit) {
    while (true) {
      kotlinx.coroutines.delay(2000)
      notes = repo.loadNotes()
    }
  }

  // Also listen for broadcast (immediate refresh)
  DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        notes = repo.loadNotes()
      }
    }
    val filter = IntentFilter(RecordingReceiver.ACTION_NEW_NOTE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter)
    }
    onDispose { context.unregisterReceiver(receiver) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              "Voice Notes",
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp
            )
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
    if (notes.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("🎙", fontSize = 48.sp)
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            "No voice notes yet",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "Record on your watch to get started",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 14.sp
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Text(
            "${notes.size} note${if (notes.size != 1) "s" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
          )
        }

        items(notes, key = { it.id }) { note ->
          NoteCard(note = note, onClick = { onNoteClick(note.id) })
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
      }
    }
  }
}

@Composable
private fun NoteCard(
  note: NotesRepository.VoiceNote,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = note.displayTime,
          color = MaterialTheme.colorScheme.primary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        )
        if (note.isTranscribing) {
          Text(
            text = "⏳ Transcribing",
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 11.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = note.preview,
        color = MaterialTheme.colorScheme.onSurface.copy(
          alpha = if (note.transcript.isNullOrBlank()) 0.5f else 0.9f
        ),
        fontSize = 15.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 22.sp
      )

      if (note.durationMs > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "🔊 ${note.durationDisplay}",
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          fontSize = 12.sp
        )
      }

      if (note.processingTimeMs > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "⚡ Processed in ${note.processingTimeDisplay}",
          color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
          fontSize = 11.sp
        )
      }
    }
  }
}
