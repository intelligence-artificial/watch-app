package com.tamagotchi.pet

import android.os.BatteryManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
  healthDataManager: HealthDataManager,
  onNavigateToStats: () -> Unit,
  onNavigateToHrChart: () -> Unit,
  onNavigateToRecord: () -> Unit = {},
  onNavigateToChat: () -> Unit = {}
) {
  val context = LocalContext.current
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  // ── TV Animation controller ──
  val tvState = rememberTvAnimation(autoTurnOn = true)

  // Refresh complications every 2s
  LaunchedEffect(Unit) {
    while (true) {
      delay(2000)
      FaceComplicationService.requestUpdate(context)
      StepsComplicationService.requestUpdate(context)
      HeartRateComplicationService.requestUpdate(context)
    }
  }

  // Animation frame ticker — 100ms = 10fps for smooth pixel animation
  var animTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(100L)
      animTimeMs = System.currentTimeMillis()
    }
  }

  // Animator with persistent state
  val animator = remember { PixelPetAnimator() }

  // Health data refresh every 15s
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(15000)
      val prefs = context.getSharedPreferences("wetpet_state", Context.MODE_PRIVATE)
      prefs.edit()
        .putInt("heart_rate", healthDataManager.heartRate)
        .putInt("daily_steps", healthDataManager.dailySteps)
        .putInt("calories", healthDataManager.calories)
        .apply()
      tick++
    }
  }

  val steps = remember(tick) { healthDataManager.dailySteps }
  val hr = remember(tick) { healthDataManager.heartRate }
  val calories = remember(tick) { healthDataManager.calories }

  val expression = remember(tick) {
    FaceExpression.fromHealth(hr, steps, calories)
  }

  // Trigger TV glitch on expression change
  var prevExpression by remember { mutableStateOf(expression) }
  LaunchedEffect(expression) {
    if (expression != prevExpression && tvState.isFullyOn) {
      tvState.glitch()
      prevExpression = expression
    }
  }

  val faceColor = Color(
    ((expression.color shr 16) and 0xFF).toInt(),
    ((expression.color shr 8) and 0xFF).toInt(),
    (expression.color and 0xFF).toInt()
  )

  // Get current face frame from animator
  val hour = remember(tick) { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
  val currentFaceFrame = remember(animTimeMs) {
    if (tvState.showStatic) {
      PixelPetRenderer.generateStaticFrame()
    } else {
      animator.getCurrentFrame(animTimeMs, expression, hour)
    }
  }

  // Glow pulse animation
  val infiniteTransition = rememberInfiniteTransition(label = "face_pulse")
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.08f,
    targetValue = 0.20f,
    animationSpec = infiniteRepeatable(
      animation = tween(2000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
  )

  // Subtle bob animation for the CRT monitor
  val bobOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (expression) {
          FaceExpression.EXCITED -> 400
          FaceExpression.ACTIVE -> 600
          FaceExpression.SLEEPY -> 2000
          else -> 800
        },
        easing = FastOutSlowInEasing
      ),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bob"
  )
  val bobAmplitude = when (expression) {
    FaceExpression.EXCITED -> 8f
    FaceExpression.ACTIVE -> 5f
    FaceExpression.SLEEPY -> 1f
    else -> 3f
  }
  val faceYOffset = (bobOffset * bobAmplitude * 2 - bobAmplitude).dp

  // Battery
  val batteryPct = remember(tick) {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
  }

  // CRT scanline flicker
  val scanlineOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(3000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "scanline"
  )

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    autoCentering = null,
    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
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
    // ── Hero: Arc Rings + CRT Monitor Pixel Face ──
    item(key = "hero") {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(185.dp)
      ) {
        // Arc rings
        ArcRings(
          stepsProgress = (steps / 10000f).coerceIn(0f, 1f),
          batteryPct = batteryPct / 100f,
          faceColor = faceColor,
          glowAlpha = glowAlpha
        )

        // CRT Monitor pixel face (animated with TV on/off + bob)
        Box(modifier = Modifier.offset(y = faceYOffset)) {
          PixelPetCanvas(
            faceFrame = currentFaceFrame,
            faceColor = faceColor,
            sizeDp = 100,
            tvProgress = tvState.progress,
            scanlineOffset = scanlineOffset,
            showStatic = tvState.showStatic
          )
        }
      }
    }

    // ── Status label ──
    item(key = "status") {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 2.dp)
      ) {
        Text(
          text = expression.label,
          color = faceColor,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Text(
          text = expression.statusLine,
          color = faceColor.copy(alpha = 0.45f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // ── Quick stats (glassmorphic card) ──
    item(key = "stats") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.04f))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Row(
          horizontalArrangement = Arrangement.SpaceEvenly,
          modifier = Modifier.fillMaxWidth()
        ) {
          StatChip("👟", "$steps", Color(0xFF50E6FF))
          StatChip("♥", if (hr > 0) "$hr" else "--", Color(0xFFFF6464))
          StatChip("🔥", "$calories", Color(0xFFFF9F50))
        }
      }
    }

    // ── Navigation chips ──
    item(key = "stats_btn") {
      Chip(
        onClick = onNavigateToStats,
        label = {
          Text(
            "📊 Stats & Health",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFF50E6FF)
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF50E6FF).copy(alpha = 0.10f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
      )
    }

    item(key = "record_btn") {
      Chip(
        onClick = onNavigateToRecord,
        label = {
          Text(
            "🎙️ Voice Note",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFF43A047)
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF43A047).copy(alpha = 0.10f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
      )
    }

    item(key = "chat_btn") {
      Chip(
        onClick = onNavigateToChat,
        label = {
          Text(
            "💬 Chat",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFF9C27B0)
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF9C27B0).copy(alpha = 0.10f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
      )
    }
  }
}

// ========================
// CRT Monitor + Face Canvas
// ========================

@Composable
private fun PixelPetCanvas(
  faceFrame: Array<IntArray>,
  faceColor: Color,
  sizeDp: Int = 100,
  tvProgress: Float = 1f,
  scanlineOffset: Float = 0f,
  showStatic: Boolean = false
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

  val paletteMap = mapOf(
    1 to monitorDark,
    2 to monitorMid,
    3 to monitorLight,
    4 to screenBg,
    5 to monitorStand
  )

  // Color for static noise
  val staticColor = if (showStatic) {
    faceColor.copy(alpha = 0.6f)
  } else {
    faceColor
  }

  Canvas(modifier = Modifier.size(sizeDp.dp)) {
    val pixelSize = size.width / PixelPetRenderer.GRID

    // ── 1. Draw monitor frame (16×16 grid) ──
    PixelPetRenderer.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, paletteIdx ->
        if (paletteIdx == 0) return@forEachIndexed
        val color = paletteMap[paletteIdx] ?: return@forEachIndexed
        drawRect(
          color = color,
          topLeft = Offset(col * pixelSize, row * pixelSize),
          size = Size(pixelSize, pixelSize)
        )
      }
    }

    // ── 2. TV Power Effect — clip face visibility ──
    if (tvProgress < 1f && tvProgress > 0f) {
      // Draw power-on/off line effect
      val screenStartY = PixelPetRenderer.SCREEN_ROW_START * pixelSize
      val screenHeight = 9 * pixelSize  // rows 3-11
      val screenCenterY = screenStartY + screenHeight / 2f

      val visibleHeight = screenHeight * tvProgress
      val lineY = screenCenterY - visibleHeight / 2f

      // Draw bright line
      drawRect(
        color = faceColor.copy(alpha = 0.8f),
        topLeft = Offset(PixelPetRenderer.SCREEN_COL_START * pixelSize - pixelSize, lineY),
        size = Size(10 * pixelSize, visibleHeight.coerceAtLeast(pixelSize * 0.3f))
      )
    }

    // ── 3. Draw face on screen area (only when TV is mostly on) ──
    if (tvProgress > 0.5f) {
      val faceAlpha = ((tvProgress - 0.5f) * 2f).coerceIn(0f, 1f)
      val facePixelW = pixelSize
      val facePixelH = pixelSize

      val screenOffsetX = PixelPetRenderer.SCREEN_COL_START * pixelSize
      val screenOffsetY = PixelPetRenderer.SCREEN_ROW_START * pixelSize

      faceFrame.forEachIndexed { row, cols ->
        cols.forEachIndexed { col, px ->
          if (px == 0) return@forEachIndexed
          drawRect(
            color = staticColor.copy(alpha = faceAlpha),
            topLeft = Offset(
              screenOffsetX + col * facePixelW,
              screenOffsetY + row * facePixelH
            ),
            size = Size(facePixelW, facePixelH)
          )
        }
      }
    }

    // ── 4. CRT Scanlines overlay ──
    if (tvProgress > 0.3f) {
      val screenStartX = 3 * pixelSize
      val screenStartY = PixelPetRenderer.SCREEN_ROW_START * pixelSize
      val screenWidth = 10 * pixelSize
      val screenHeight = 9 * pixelSize
      val scanlineSpacing = pixelSize * 0.5f

      // Moving scanline bar (bright horizontal line that scrolls down)
      val barY = screenStartY + (scanlineOffset * screenHeight)
      val barHeight = pixelSize * 0.3f
      if (barY in screenStartY..(screenStartY + screenHeight)) {
        drawRect(
          color = faceColor.copy(alpha = 0.06f),
          topLeft = Offset(screenStartX, barY),
          size = Size(screenWidth, barHeight)
        )
      }

      // Static scanlines (alternate dim rows)
      var y = screenStartY
      while (y < screenStartY + screenHeight) {
        drawRect(
          color = Color.Black.copy(alpha = 0.12f),
          topLeft = Offset(screenStartX, y),
          size = Size(screenWidth, scanlineSpacing * 0.4f)
        )
        y += scanlineSpacing
      }
    }

    // ── 5. Screen glow (phosphor halo around screen edges) ──
    if (tvProgress > 0.5f) {
      val screenStartX = 3 * pixelSize
      val screenStartY = PixelPetRenderer.SCREEN_ROW_START * pixelSize
      val screenWidth = 10 * pixelSize
      val screenHeight = 9 * pixelSize
      val glowSize = pixelSize * 0.5f

      // Top glow
      drawRect(
        color = faceColor.copy(alpha = 0.04f * tvProgress),
        topLeft = Offset(screenStartX, screenStartY - glowSize),
        size = Size(screenWidth, glowSize)
      )
      // Bottom glow
      drawRect(
        color = faceColor.copy(alpha = 0.04f * tvProgress),
        topLeft = Offset(screenStartX, screenStartY + screenHeight),
        size = Size(screenWidth, glowSize)
      )
    }
  }
}

// ========================
// Arc Rings
// ========================

@Composable
private fun ArcRings(
  stepsProgress: Float,
  batteryPct: Float,
  faceColor: Color,
  glowAlpha: Float
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val outerSize = size.width - 8f
    val outerRect = Size(outerSize, outerSize)
    val outerOffset = Offset((size.width - outerSize) / 2, (size.height - outerSize) / 2)

    drawArc(
      color = Color(0x1250E6FF),
      startAngle = -225f, sweepAngle = 270f,
      useCenter = false, topLeft = outerOffset, size = outerRect,
      style = Stroke(width = 7f, cap = StrokeCap.Round)
    )
    drawArc(
      color = Color(0xFF50E6FF),
      startAngle = -225f, sweepAngle = 270f * stepsProgress,
      useCenter = false, topLeft = outerOffset, size = outerRect,
      style = Stroke(width = 7f, cap = StrokeCap.Round)
    )

    for (pct in listOf(0.25f, 0.5f, 0.75f)) {
      drawArc(
        color = Color(0x25FFFFFF),
        startAngle = -225f + 270f * pct - 1f, sweepAngle = 2f,
        useCenter = false, topLeft = outerOffset, size = outerRect,
        style = Stroke(width = 11f)
      )
    }

    val innerSize = outerSize - 22f
    val innerRect = Size(innerSize, innerSize)
    val innerOffset = Offset((size.width - innerSize) / 2, (size.height - innerSize) / 2)

    drawArc(
      color = Color(0x0E50FF78),
      startAngle = -135f, sweepAngle = 90f,
      useCenter = false, topLeft = innerOffset, size = innerRect,
      style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
    val batColor = if (batteryPct < 0.3f) Color(0xFFFF4646) else Color(0xFF50FF78)
    drawArc(
      color = batColor,
      startAngle = -135f, sweepAngle = 90f * batteryPct,
      useCenter = false, topLeft = innerOffset, size = innerRect,
      style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    val glowSize = innerSize - 18f
    val glowRect = Size(glowSize, glowSize)
    val glowOffset = Offset((size.width - glowSize) / 2, (size.height - glowSize) / 2)
    drawArc(
      color = faceColor.copy(alpha = glowAlpha),
      startAngle = 0f, sweepAngle = 360f,
      useCenter = false, topLeft = glowOffset, size = glowRect,
      style = Stroke(width = 2.5f)
    )
  }
}

@Composable
private fun StatChip(icon: String, value: String, color: Color) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(color.copy(alpha = 0.06f))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Text(text = icon, fontSize = 12.sp)
    Text(
      text = value,
      color = color,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}
