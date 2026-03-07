package com.watchvoice.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────
//  Colors
// ─────────────────────────────────────────────

private val RecordRed = Color(0xFFE53935)
private val RecordingGreen = Color(0xFF43A047)
private val SyncBlue = Color(0xFF42A5F5)
private val DarkBg = Color(0xFF0D0D0D)
private val TextGray = Color(0xFF888888)

// Japanese watchface palette
private val InkBlack = Color(0xFF1A1A1A)         // sumi ink
private val WashiCream = Color(0xFFE8DCC8)       // warm paper tone
private val Crimson = Color(0xFFCC2936)           // torii red
private val CrimsonDim = Color(0xFF661420)        // dark red
private val SakuraPink = Color(0xFFE8A0B4)        // cherry blossom
private val InkGray = Color(0xFF555555)           // faded ink
private val InkGrayDim = Color(0xFF333333)        // very faded

// Eye palette
private val EyeballWhite = Color(0xFFF0EDE8)
private val EyeballEdge = Color(0xFFD5D0C8)
private val IrisOuter = Color(0xFF3A3A3A)
private val IrisMid = Color(0xFF555555)
private val IrisInner = Color(0xFF222222)
private val IrisRecOuter = Color(0xFF1B5E20)
private val IrisRecMid = Color(0xFF388E3C)
private val IrisRecInner = Color(0xFF0D3B0F)
private val PupilColor = Color(0xFF000000)
private val LidColor = Color(0xFF181818)
private val LashColor = Color(0xFFCCCCCC)
private val OutlineLight = Color(0xFF999999)
private val OutlineRec = Color(0xFF66BB6A)

// Stroke widths
private const val LID_STROKE = 5f
private const val LASH_STROKE = 3f
private const val OUTLINE_STROKE = 3f
private const val LASH_COUNT = 7

// Silence detection
private const val SILENCE_THRESHOLD = 500
private const val SILENCE_TIMEOUT_MS = 3000L
private const val MIN_RECORDING_MS = 2000L
private const val AMPLITUDE_POLL_MS = 300L

// Smoothing
private const val PUPIL_LERP = 0.12f

// ─────────────────────────────────────────────
//  Accelerometer-driven pupil offset
// ─────────────────────────────────────────────

@Composable
fun rememberPupilOffset(): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val offset = remember { mutableStateOf(0f to 0f) }
    val targetX = remember { mutableFloatStateOf(0f) }
    val targetY = remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                targetX.floatValue = (-e.values[0] / 4.5f).coerceIn(-1f, 1f)
                targetY.floatValue = ((e.values[1] - 2.5f) / 4.5f).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val (cx, cy) = offset.value
            offset.value = (cx + (targetX.floatValue - cx) * PUPIL_LERP) to
                           (cy + (targetY.floatValue - cy) * PUPIL_LERP)
            delay(16L)
        }
    }
    return offset
}

// ─────────────────────────────────────────────
//  Main Screen — two modes: Watchface & Recorder
// ─────────────────────────────────────────────

@Composable
fun RecordScreen(
    onNavigateToRecordings: () -> Unit,
    recorderService: AudioRecorderService,
    dataLayerSender: DataLayerSender
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ──
    var isInRecorderMode by remember { mutableStateOf(false) }
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
            context.filesDir.listFiles()?.count { f -> f.extension == "m4a" } ?: 0
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // ── Watchface clock (single millis source → no second-hand desync) ──
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(16L) // ~60fps for smooth second hand
        }
    }

    // ── Recorder mode stuff ──
    val pupilOffset by rememberPupilOffset()

    // Blink
    var blinkAmount by remember { mutableFloatStateOf(0f) }
    val blinkAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 5000))
            blinkAnim.animateTo(1f, tween(130, easing = EaseInOutCubic))
            delay(60)
            blinkAnim.animateTo(0f, tween(100, easing = FastOutSlowInEasing))
            if (Random.nextFloat() < 0.2f) {
                delay(120)
                blinkAnim.animateTo(1f, tween(110, easing = EaseInOutCubic))
                delay(50)
                blinkAnim.animateTo(0f, tween(90, easing = FastOutSlowInEasing))
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { blinkAnim.value }.collect { blinkAmount = it }
    }

    // Micro-saccades
    var saccadeX by remember { mutableFloatStateOf(0f) }
    var saccadeY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(800, 2200))
            saccadeX = (Random.nextFloat() - 0.5f) * 0.04f
            saccadeY = (Random.nextFloat() - 0.5f) * 0.04f
            delay(150)
            repeat(5) { saccadeX *= 0.6f; saccadeY *= 0.6f; delay(30) }
            saccadeX = 0f; saccadeY = 0f
        }
    }

    // Timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsedTime = 0L
            while (isRecording) { delay(1000); elapsedTime += 1 }
        }
    }

    // Auto-clear sync
    LaunchedEffect(syncStatus) {
        if (syncStatus != null && syncStatus != "Sending…") { delay(3000); syncStatus = null }
    }

    // ── Stop helper (shared) ──
    fun doStopRecording(autoStopped: Boolean = false) {
        val info = recorderService.stopRecording()
        isRecording = false
        recordingCount++
        val vib = context.getSystemService(Vibrator::class.java)
        vib?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        if (info != null) {
            syncStatus = "Sending…"
            scope.launch {
                syncStatus = when (dataLayerSender.sendRecording(info.file)) {
                    DataLayerSender.SendStatus.SENT -> if (autoStopped) "Auto-stopped ✓" else "Sent ✓"
                    DataLayerSender.SendStatus.NO_PHONE -> "Phone not found"
                    DataLayerSender.SendStatus.ERROR -> "Send failed"
                    DataLayerSender.SendStatus.SENDING -> "Sending…"
                }
            }
        } else if (autoStopped) {
            syncStatus = "Auto-stopped"
        }
        // Return to watchface after stopping
        isInRecorderMode = false
    }

    // Silence detection / auto-stop
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect
        val startMs = System.currentTimeMillis()
        var silentMs = 0L
        while (isRecording) {
            delay(AMPLITUDE_POLL_MS)
            if (System.currentTimeMillis() - startMs < MIN_RECORDING_MS) continue
            val amp = recorderService.getAmplitude()
            if (amp < SILENCE_THRESHOLD) silentMs += AMPLITUDE_POLL_MS else silentMs = 0L
            if (silentMs >= SILENCE_TIMEOUT_MS) {
                Log.d("RecordScreen", "Auto-stopping after ${silentMs}ms silence")
                doStopRecording(autoStopped = true)
                break
            }
        }
    }

    // Recording pulse & glow
    val eyeInf = rememberInfiniteTransition(label = "eye")
    val pulseScale by eyeInf.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(900, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulse"
    )
    val glowAlpha by eyeInf.animateFloat(
        0.15f, 0.55f,
        infiniteRepeatable(tween(1200, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow"
    )

    // Animated iris color
    val irisOuterAnim by animateColorAsState(if (isRecording) IrisRecOuter else IrisOuter, tween(600), label = "iO")
    val irisMidAnim by animateColorAsState(if (isRecording) IrisRecMid else IrisMid, tween(600), label = "iM")
    val irisInnerAnim by animateColorAsState(if (isRecording) IrisRecInner else IrisInner, tween(600), label = "iI")
    val outlineAnim by animateColorAsState(if (isRecording) OutlineRec else OutlineLight, tween(400), label = "ol")

    // ─────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────

    Box(
        modifier = Modifier.fillMaxSize().background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        if (!isInRecorderMode) {
            // ═══════════════════════════════════
            //  WATCHFACE MODE — Analog Clock
            // ═══════════════════════════════════
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@clickable
                        }
                        // Enter recorder mode and start recording immediately
                        isInRecorderMode = true
                        if (recorderService.startRecording() != null) {
                            isRecording = true
                            val vib = context.getSystemService(Vibrator::class.java)
                            vib?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
            ) {
                drawAnalogWatchface(
                    nowMillis = nowMillis,
                    syncStatus = syncStatus
                )
            }
        } else {
            // ═══════════════════════════════════
            //  RECORDER MODE — Eye Animation
            // ═══════════════════════════════════
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRecording) formatTime(elapsedTime) else "Listening…",
                    color = if (isRecording) RecordingGreen else TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                val s = if (isRecording) pulseScale else 1f
                val currentGlowAlpha = if (isRecording) glowAlpha else 0f
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .clickable {
                            if (isRecording) {
                                doStopRecording()
                            }
                        }
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = size.width * 0.42f * s

                    // Glow ring
                    if (currentGlowAlpha > 0.01f) {
                        drawCircle(
                            color = RecordingGreen.copy(alpha = currentGlowAlpha),
                            radius = r + 12f,
                            center = Offset(cx, cy),
                            style = Stroke(8f)
                        )
                        drawCircle(
                            color = RecordingGreen.copy(alpha = currentGlowAlpha * 0.4f),
                            radius = r + 20f,
                            center = Offset(cx, cy),
                            style = Stroke(14f)
                        )
                    }

                    drawStylizedEye(
                        cx = cx, cy = cy,
                        radius = r,
                        pupilOffX = (pupilOffset.first + saccadeX) * r * 0.32f,
                        pupilOffY = (pupilOffset.second + saccadeY) * r * 0.28f,
                        blinkProg = blinkAmount,
                        isRecording = isRecording,
                        irisColors = listOf(irisInnerAnim, irisMidAnim, irisOuterAnim, irisInnerAnim),
                        outlineColor = outlineAnim
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (syncStatus != null) {
                    Text(
                        text = syncStatus!!,
                        color = when {
                            syncStatus!!.contains("✓") -> RecordingGreen
                            syncStatus == "Sending…" -> SyncBlue
                            else -> RecordRed
                        },
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Recording count — always visible at bottom
        if (!isInRecorderMode && recordingCount > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "$recordingCount recording${if (recordingCount != 1) "s" else ""} ▸",
                    color = TextGray,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .clickable { onNavigateToRecordings() }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Analog Watchface Drawing — Japanese Style
// ─────────────────────────────────────────────

// Kanji hour numerals
private val KANJI_HOURS = arrayOf(
    "十二", "一", "二", "三", "四", "五",
    "六", "七", "八", "九", "十", "十一"
)

private fun DrawScope.drawAnalogWatchface(
    nowMillis: Long,
    syncStatus: String?
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val clockRadius = w * 0.40f

    // Derive time from single millisecond source — no desync
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val hour = cal.get(Calendar.HOUR)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val millis = cal.get(Calendar.MILLISECOND)
    val exactSecond = second + millis / 1000f  // smooth fractional seconds

    // ── Ensō-inspired outer ring (ink brush circle) ──
    drawCircle(
        color = InkGray,
        radius = clockRadius + 3f,
        center = Offset(cx, cy),
        style = Stroke(2.5f)
    )
    // Subtle inner ring
    drawCircle(
        color = InkGrayDim.copy(alpha = 0.4f),
        radius = clockRadius - 1f,
        center = Offset(cx, cy),
        style = Stroke(0.8f)
    )

    // ── Tick marks ──
    for (i in 0 until 60) {
        val angle = (i * 6f - 90f) * (PI.toFloat() / 180f)
        val isHourMark = i % 5 == 0
        val outerR = clockRadius - 2f
        val innerR = if (isHourMark) clockRadius - 16f else clockRadius - 7f
        val markColor = if (isHourMark) WashiCream else InkGrayDim
        val markWidth = if (isHourMark) 2.5f else 0.8f

        drawLine(
            color = markColor,
            start = Offset(cx + cos(angle) * innerR, cy + sin(angle) * innerR),
            end = Offset(cx + cos(angle) * outerR, cy + sin(angle) * outerR),
            strokeWidth = markWidth,
            cap = StrokeCap.Round
        )
    }

    // ── Kanji numerals ──
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        val kanjiPaint = Paint().apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = w * 0.075f
            color = 0xFFE8DCC8.toInt()  // washi cream
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val numeralR = clockRadius - 28f
        for (i in 0 until 12) {
            val angle = ((i + 1) * 30f - 90f) * (PI.toFloat() / 180f)
            // Use slightly smaller font for two-character numerals
            val isLong = KANJI_HOURS[if (i + 1 == 12) 0 else i + 1].length > 1
            kanjiPaint.textSize = if (isLong) w * 0.058f else w * 0.075f

            val nx = cx + cos(angle) * numeralR
            val ny = cy + sin(angle) * numeralR + kanjiPaint.textSize * 0.35f
            nativeCanvas.drawText(KANJI_HOURS[if (i + 1 == 12) 0 else i + 1], nx, ny, kanjiPaint)
        }

        // ── Date window at 6 o'clock position ──
        val datePaint = Paint().apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = w * 0.05f
            color = 0xFFE8DCC8.toInt()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dateY = cy + clockRadius * 0.48f

        // Date box
        val boxPaint = Paint().apply {
            color = 0xFF1A1A1A.toInt()
            style = Paint.Style.FILL
        }
        val boxStrokePaint = Paint().apply {
            color = 0xFF555555.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val boxW = w * 0.06f
        val boxH = w * 0.04f
        nativeCanvas.drawRect(cx - boxW, dateY - boxH, cx + boxW, dateY + boxH * 0.5f, boxPaint)
        nativeCanvas.drawRect(cx - boxW, dateY - boxH, cx + boxW, dateY + boxH * 0.5f, boxStrokePaint)
        nativeCanvas.drawText("$day", cx, dateY, datePaint)

        // ── Hint / status at bottom ──
        val hintY = cy + clockRadius + 20f
        if (syncStatus != null) {
            val statusPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textSize = w * 0.05f
                color = when {
                    syncStatus.contains("✓") -> 0xFF43A047.toInt()
                    syncStatus == "Sending…" -> 0xFF42A5F5.toInt()
                    else -> 0xFFE53935.toInt()
                }
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            nativeCanvas.drawText(syncStatus, cx, hintY, statusPaint)
        } else {
            val hintPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                textSize = w * 0.055f
                color = 0xFFCC2936.toInt()  // crimson
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            nativeCanvas.drawText("録音", cx, hintY, hintPaint)  // "recording"
        }

        // ── Label at top: 音声 (voice) ──
        val labelPaint = Paint().apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = w * 0.05f
            color = 0xFF555555.toInt()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        nativeCanvas.drawText("音声", cx, cy - clockRadius - 10f, labelPaint)
    }

    // ── Hour hand (ink-colored) ──
    val hourAngle = ((hour + minute / 60f) * 30f - 90f) * (PI.toFloat() / 180f)
    val hourLen = clockRadius * 0.50f
    drawLine(
        color = WashiCream,
        start = Offset(cx - cos(hourAngle) * 8f, cy - sin(hourAngle) * 8f),
        end = Offset(cx + cos(hourAngle) * hourLen, cy + sin(hourAngle) * hourLen),
        strokeWidth = 5f,
        cap = StrokeCap.Round
    )

    // ── Minute hand ──
    val minAngle = ((minute + exactSecond / 60f) * 6f - 90f) * (PI.toFloat() / 180f)
    val minLen = clockRadius * 0.72f
    drawLine(
        color = WashiCream,
        start = Offset(cx - cos(minAngle) * 10f, cy - sin(minAngle) * 10f),
        end = Offset(cx + cos(minAngle) * minLen, cy + sin(minAngle) * minLen),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )

    // ── Second hand (crimson, smooth sweep from millis) ──
    val secAngle = (exactSecond * 6f - 90f) * (PI.toFloat() / 180f)
    val secLen = clockRadius * 0.82f
    drawLine(
        color = Crimson,
        start = Offset(cx - cos(secAngle) * 16f, cy - sin(secAngle) * 16f),
        end = Offset(cx + cos(secAngle) * secLen, cy + sin(secAngle) * secLen),
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )

    // ── Center dot (crimson hanafuda-style) ──
    drawCircle(color = Crimson, radius = 5f, center = Offset(cx, cy))
    drawCircle(color = InkBlack, radius = 2f, center = Offset(cx, cy))
}

// ─────────────────────────────────────────────
//  Eye Drawing
// ─────────────────────────────────────────────

private fun DrawScope.drawStylizedEye(
    cx: Float, cy: Float,
    radius: Float,
    pupilOffX: Float, pupilOffY: Float,
    blinkProg: Float,
    isRecording: Boolean,
    irisColors: List<Color>,
    outlineColor: Color
) {
    val scleraBrush = Brush.radialGradient(
        colors = listOf(EyeballWhite, EyeballEdge, Color(0xFFAAAAAA)),
        center = Offset(cx - radius * 0.2f, cy - radius * 0.15f),
        radius = radius * 1.1f
    )
    drawCircle(brush = scleraBrush, radius = radius, center = Offset(cx, cy))
    drawCircle(color = outlineColor, radius = radius, center = Offset(cx, cy), style = Stroke(OUTLINE_STROKE))

    val openAmount = 1f - blinkProg
    val lidCurve = radius * 0.95f * openAmount

    val openingPath = Path().apply {
        moveTo(cx - radius, cy)
        cubicTo(cx - radius * 0.5f, cy - lidCurve, cx + radius * 0.5f, cy - lidCurve, cx + radius, cy)
        cubicTo(cx + radius * 0.5f, cy + lidCurve, cx - radius * 0.5f, cy + lidCurve, cx - radius, cy)
        close()
    }

    clipPath(openingPath) {
        drawCircle(brush = scleraBrush, radius = radius, center = Offset(cx, cy))

        val maxOff = radius * 0.35f
        val ox = pupilOffX.coerceIn(-maxOff, maxOff)
        val oy = pupilOffY.coerceIn(-maxOff, maxOff)
        val iCx = cx + ox
        val iCy = cy + oy
        val irisR = radius * 0.52f
        val pupilR = radius * 0.28f

        val irisBrush = Brush.radialGradient(colors = irisColors, center = Offset(iCx, iCy), radius = irisR)
        drawCircle(brush = irisBrush, radius = irisR, center = Offset(iCx, iCy))
        drawCircle(color = Color(0xFF111111), radius = irisR, center = Offset(iCx, iCy), style = Stroke(2.5f))
        drawCircle(
            color = if (isRecording) Color(0x4066BB6A) else Color(0x30777777),
            radius = irisR * 0.72f,
            center = Offset(iCx - irisR * 0.1f, iCy - irisR * 0.05f)
        )
        drawCircle(color = PupilColor, radius = pupilR, center = Offset(iCx, iCy))
        drawCircle(color = Color(0xEEFFFFFF), radius = pupilR * 0.4f, center = Offset(iCx - pupilR * 0.5f, iCy - pupilR * 0.55f))
        drawCircle(color = Color(0x77FFFFFF), radius = pupilR * 0.18f, center = Offset(iCx + pupilR * 0.6f, iCy + pupilR * 0.45f))
    }

    // Eyelids
    val topLidPath = Path().apply {
        addArc(Rect(cx - radius, cy - radius, cx + radius, cy + radius), 180f, 180f)
        lineTo(cx + radius, cy)
        cubicTo(cx + radius * 0.5f, cy - lidCurve, cx - radius * 0.5f, cy - lidCurve, cx - radius, cy)
        close()
    }
    drawPath(topLidPath, color = LidColor)
    val topEdgePath = Path().apply {
        moveTo(cx - radius, cy)
        cubicTo(cx - radius * 0.5f, cy - lidCurve, cx + radius * 0.5f, cy - lidCurve, cx + radius, cy)
    }
    drawPath(topEdgePath, color = outlineColor, style = Stroke(LID_STROKE, cap = StrokeCap.Round))

    val botLidPath = Path().apply {
        addArc(Rect(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 180f)
        lineTo(cx - radius, cy)
        cubicTo(cx - radius * 0.5f, cy + lidCurve, cx + radius * 0.5f, cy + lidCurve, cx + radius, cy)
        close()
    }
    drawPath(botLidPath, color = LidColor)
    val botEdgePath = Path().apply {
        moveTo(cx + radius, cy)
        cubicTo(cx + radius * 0.5f, cy + lidCurve, cx - radius * 0.5f, cy + lidCurve, cx - radius, cy)
    }
    drawPath(botEdgePath, color = outlineColor, style = Stroke(LID_STROKE * 0.7f, cap = StrokeCap.Round))

    // Eyelashes
    if (openAmount > 0.15f) {
        val lashLen = radius * 0.18f * openAmount
        for (i in 0 until LASH_COUNT) {
            val t = (i + 1).toFloat() / (LASH_COUNT + 1)
            val lx = cubicBezierX(cx - radius, cx - radius * 0.5f, cx + radius * 0.5f, cx + radius, t)
            val ly = cubicBezierY(cy, cy - lidCurve, cy - lidCurve, cy, t)
            val angle = (-PI * 0.5 + (t - 0.5) * PI * 0.6).toFloat()
            drawLine(
                color = LashColor,
                start = Offset(lx, ly),
                end = Offset(lx + cos(angle) * lashLen, ly + sin(angle) * lashLen),
                strokeWidth = LASH_STROKE, cap = StrokeCap.Round
            )
        }
    }

    if (isRecording) {
        drawCircle(color = RecordingGreen, radius = 5f, center = Offset(cx, cy + radius + 14f))
    }
}

// ─────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────

private fun cubicBezierX(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
}

private fun cubicBezierY(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}

private val EaseInOutCubic = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
