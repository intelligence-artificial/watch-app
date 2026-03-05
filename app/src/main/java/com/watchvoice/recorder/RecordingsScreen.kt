package com.watchvoice.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF121212)
private val CardBg = Color(0xFF1E1E1E)
private val AccentBlue = Color(0xFF42A5F5)
private val DeleteRed = Color(0xFFE53935)
private val TextWhite = Color(0xFFEEEEEE)
private val TextGray = Color(0xFF888888)

@Composable
fun RecordingsScreen(
    recordingsDir: File,
    playerService: AudioPlayerService,
    onBack: () -> Unit
) {
    var recordings by remember { mutableStateOf(loadRecordings(recordingsDir)) }
    var playingFile by remember { mutableStateOf<File?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings yet",
                    color = TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 32.dp,
                    bottom = 32.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Text(
                        text = "Recordings",
                        color = TextWhite,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(recordings, key = { it.absolutePath }) { file ->
                    RecordingItem(
                        file = file,
                        isPlaying = playingFile == file,
                        onPlay = {
                            if (playingFile == file) {
                                playerService.stop()
                                playingFile = null
                            } else {
                                playerService.play(file) {
                                    playingFile = null
                                }
                                playingFile = file
                            }
                        },
                        onDelete = {
                            playerService.stop()
                            playingFile = null
                            file.delete()
                            recordings = loadRecordings(recordingsDir)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingItem(
    file: File,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = try {
        val parts = file.nameWithoutExtension.removePrefix("voice_")
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val date = sdf.parse(parts)
        val displaySdf = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        if (date != null) displaySdf.format(date) else file.nameWithoutExtension
    } catch (_: Exception) {
        file.nameWithoutExtension
    }

    val sizeKb = file.length() / 1024

    Chip(
        onClick = onPlay,
        label = {
            Text(
                text = dateStr,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        },
        secondaryLabel = {
            Text(
                text = "${sizeKb}KB" + if (isPlaying) " ▶ Playing" else "",
                fontSize = 10.sp,
                color = if (isPlaying) AccentBlue else TextGray
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = CardBg,
            contentColor = TextWhite
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun loadRecordings(dir: File): List<File> {
    return dir.listFiles()
        ?.filter { it.extension == "m4a" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
}
