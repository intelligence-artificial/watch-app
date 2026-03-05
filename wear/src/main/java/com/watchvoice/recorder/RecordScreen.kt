package com.watchvoice.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Colors
private val RecordRed = Color(0xFFE53935)
private val RecordingGreen = Color(0xFF43A047)
private val SyncBlue = Color(0xFF42A5F5)
private val DarkBg = Color(0xFF121212)
private val TextWhite = Color(0xFFEEEEEE)
private val TextGray = Color(0xFF888888)

@Composable
fun RecordScreen(
    onNavigateToRecordings: () -> Unit,
    recorderService: AudioRecorderService,
    dataLayerSender: DataLayerSender
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var recordingCount by remember {
        mutableIntStateOf(
            recorderService.let {
                val dir = context.filesDir
                dir.listFiles()?.count { f -> f.extension == "m4a" } ?: 0
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Elapsed time ticker
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedTime = 0L
            while (isRecording) {
                delay(1000)
                elapsedTime += 1
            }
        }
    }

    // Auto-clear sync status after 3 seconds
    LaunchedEffect(syncStatus) {
        if (syncStatus != null && syncStatus != "Sending…") {
            delay(3000)
            syncStatus = null
        }
    }

    // Pulse animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status text
            Text(
                text = if (isRecording) formatTime(elapsedTime) else "Tap to Record",
                color = if (isRecording) RecordingGreen else TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Big record button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .then(
                        if (isRecording) Modifier.scale(scale) else Modifier
                    )
                    .clip(CircleShape)
                    .background(if (isRecording) RecordingGreen else RecordRed)
                    .clickable {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@clickable
                        }

                        val vibrator = context.getSystemService(Vibrator::class.java)
                        if (isRecording) {
                            // Stop recording
                            val info = recorderService.stopRecording()
                            isRecording = false
                            recordingCount++
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                            )

                            // Auto-send to phone
                            if (info != null) {
                                syncStatus = "Sending…"
                                scope.launch {
                                    val status = dataLayerSender.sendRecording(info.file)
                                    syncStatus = when (status) {
                                        DataLayerSender.SendStatus.SENT -> "Sent ✓"
                                        DataLayerSender.SendStatus.NO_PHONE -> "Phone not found"
                                        DataLayerSender.SendStatus.ERROR -> "Send failed"
                                        DataLayerSender.SendStatus.SENDING -> "Sending…"
                                    }
                                }
                            }
                        } else {
                            // Start recording
                            val file = recorderService.startRecording()
                            if (file != null) {
                                isRecording = true
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    // Stop icon (square)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White)
                    )
                } else {
                    // Mic circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sync status
            if (syncStatus != null) {
                Text(
                    text = syncStatus!!,
                    color = when {
                        syncStatus == "Sent ✓" -> RecordingGreen
                        syncStatus == "Sending…" -> SyncBlue
                        else -> RecordRed
                    },
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Recordings button
            if (!isRecording && recordingCount > 0) {
                Text(
                    text = "$recordingCount recording${if (recordingCount != 1) "s" else ""} ▸",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onNavigateToRecordings() }
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

private val EaseInOutCubic = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
