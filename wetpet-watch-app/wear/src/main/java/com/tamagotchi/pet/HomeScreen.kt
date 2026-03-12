package com.tamagotchi.pet

import android.os.BatteryManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlin.math.sin

@Composable
fun HomeScreen(
  petStateManager: PetStateManager,
  healthDataManager: HealthDataManager,
  petStatusEngine: PetStatusEngine,
  onNavigateToCustomize: () -> Unit,
  onNavigateToStats: () -> Unit,
  onNavigateToHrChart: () -> Unit
) {
  val context = LocalContext.current
  val listState = rememberScalingLazyListState()
  val scope = rememberCoroutineScope()

  // Track previous emotion to detect changes
  var prevEmotion by remember { mutableStateOf(petStatusEngine.currentEmotion) }

  // (Health Connect reader removed to decouple from Fitbit)

  // Update engine every 2s
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(2000)
      petStatusEngine.update(healthDataManager.snapshot())
      val newEmotion = petStatusEngine.currentEmotion
      petStateManager.saveEmotion(newEmotion)
      tick++

      // Trigger complication updates when emotion changes or every 30s
      if (newEmotion != prevEmotion || tick % 15 == 0) {
        prevEmotion = newEmotion
        petStateManager.saveNeedsForComplications(petStatusEngine.currentNeeds)
        petStateManager.requestComplicationUpdates()
        scope.launch {
          petStateManager.syncToPhone(
            newEmotion,
            petStatusEngine.currentNeeds,
            healthDataManager
          )
        }
      }
    }
  }

  val petName = remember(tick) { petStateManager.petName }
  val theme = remember(tick) { petStateManager.colorTheme }
  val emotion = remember(tick) { petStatusEngine.currentEmotion }
  val needs = remember(tick) { petStatusEngine.currentNeeds }
  val steps = remember(tick) { healthDataManager.dailySteps }
  val hr = remember(tick) { healthDataManager.heartRate }
  val calories = remember(tick) { healthDataManager.calories }

  val mood = remember(tick) { petStateManager.emotionToMood(emotion) }
  val petType = remember(tick) { petStateManager.petType }
  val spriteRes = remember(theme, mood, petType, tick) {
    getSpriteResource(context, petType, theme, mood, tick)
  }

  val themeColor = when (theme) {
    PetColorTheme.GREEN -> Color(0xFF78FFA0)
    PetColorTheme.BLUE -> Color(0xFF64C8FF)
    PetColorTheme.PINK -> Color(0xFFFF8CC8)
    PetColorTheme.YELLOW -> Color(0xFFFFE664)
  }

  val emotionColor = Color(
    ((emotion.arcColor shr 16) and 0xFF).toInt(),
    ((emotion.arcColor shr 8) and 0xFF).toInt(),
    (emotion.arcColor and 0xFF).toInt()
  )

  // ── Bouncing animation ──
  val infiniteTransition = rememberInfiniteTransition(label = "pet_bounce")
  val bounceOffset by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(
        durationMillis = when (emotion) {
          PetEmotion.EXCITED, PetEmotion.ECSTATIC -> 400
          PetEmotion.HAPPY, PetEmotion.ACTIVE -> 600
          PetEmotion.SLEEPY, PetEmotion.EXHAUSTED -> 2000
          PetEmotion.SAD, PetEmotion.BORED -> 1500
          else -> 800
        },
        easing = FastOutSlowInEasing
      ),
      repeatMode = RepeatMode.Reverse
    ),
    label = "bounce"
  )
  val bounceAmplitude = when (emotion) {
    PetEmotion.EXCITED, PetEmotion.ECSTATIC -> 10f
    PetEmotion.HAPPY, PetEmotion.ACTIVE -> 6f
    PetEmotion.SLEEPY, PetEmotion.EXHAUSTED -> 1f
    PetEmotion.SAD, PetEmotion.BORED -> 2f
    PetEmotion.IDLE, PetEmotion.CONTENT -> 4f
    else -> 3f
  }
  val petYOffset = (bounceOffset * bounceAmplitude * 2 - bounceAmplitude).dp

  // Glow pulse animation
  val glowAlpha by infiniteTransition.animateFloat(
    initialValue = 0.08f,
    targetValue = 0.20f,
    animationSpec = infiniteRepeatable(
      animation = tween(2000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
  )

  // Battery
  val batteryPct = remember(tick) {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
  }

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
  ) {
    // ── Hero: Arc Rings + Pet ──
    item(key = "hero") {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(185.dp)
      ) {
        // Draw scuba-style arc rings with glow
        ScubaArcRings(
          stepsProgress = (steps / 10000f).coerceIn(0f, 1f),
          batteryPct = batteryPct / 100f,
          themeColor = themeColor,
          emotionColor = emotionColor,
          glowAlpha = glowAlpha
        )

        // Pet sprite (animated)
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.offset(y = petYOffset)
        ) {
          if (spriteRes != 0) {
            Image(
              painter = painterResource(id = spriteRes),
              contentDescription = "WetPet",
              modifier = Modifier.size(82.dp)
            )
          } else {
            Text("🐾", fontSize = 44.sp)
          }
        }
      }
    }

    // ── Pet name + level ──
    item(key = "header") {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 2.dp)
      ) {
        Text(
          text = petName,
          color = themeColor,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Text(
          text = "LV.${needs.level}  ·  ${needs.xpTotal}XP",
          color = themeColor.copy(alpha = 0.45f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // ── Emotion status card ──
    item(key = "emotion") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(emotionColor.copy(alpha = 0.08f))
          .padding(vertical = 6.dp, horizontal = 12.dp)
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = emotion.line1,
            color = emotionColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
          )
          Text(
            text = emotion.line2,
            color = emotionColor.copy(alpha = 0.50f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    // ── Needs bars (glassmorphic card, vertical layout) ──
    item(key = "needs") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 28.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.04f))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          CompactNeedRow("HNG", needs.hunger, Color(0xFFFFAA50))
          CompactNeedRow("HAP", needs.happiness, Color(0xFF50E6FF))
          CompactNeedRow("NRG", needs.energy, Color(0xFF50FF78))
        }
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

    // ── Navigation ──
    item(key = "customize_btn") {
      Chip(
        onClick = onNavigateToCustomize,
        label = {
          Text(
            "✦ Customize",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = themeColor
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = themeColor.copy(alpha = 0.10f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
      )
    }
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
    item(key = "hr_chart_btn") {
      Chip(
        onClick = onNavigateToHrChart,
        label = {
          Text(
            "♥ HR Chart",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Color(0xFFFF6464)
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFFFF6464).copy(alpha = 0.10f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 2.dp)
      )
    }
  }
}

/**
 * Scuba-style concentric arc rings drawn with Canvas.
 * Includes subtle glow pulse on the emotion ring.
 */
@Composable
private fun ScubaArcRings(
  stepsProgress: Float,
  batteryPct: Float,
  themeColor: Color,
  emotionColor: Color,
  glowAlpha: Float
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val cx = size.width / 2
    val cy = size.height / 2

    // ── Outer ring: Steps (270° sweep) ──
    val outerSize = size.width - 8f
    val outerRect = Size(outerSize, outerSize)
    val outerOffset = Offset((size.width - outerSize) / 2, (size.height - outerSize) / 2)

    // Background track
    drawArc(
      color = Color(0x1250E6FF),
      startAngle = -225f,
      sweepAngle = 270f,
      useCenter = false,
      topLeft = outerOffset,
      size = outerRect,
      style = Stroke(width = 7f, cap = StrokeCap.Round)
    )
    // Fill
    drawArc(
      color = Color(0xFF50E6FF),
      startAngle = -225f,
      sweepAngle = 270f * stepsProgress,
      useCenter = false,
      topLeft = outerOffset,
      size = outerRect,
      style = Stroke(width = 7f, cap = StrokeCap.Round)
    )

    // Tick marks at 25%, 50%, 75%
    for (pct in listOf(0.25f, 0.5f, 0.75f)) {
      val angle = -225f + 270f * pct
      drawArc(
        color = Color(0x25FFFFFF),
        startAngle = angle - 1f,
        sweepAngle = 2f,
        useCenter = false,
        topLeft = outerOffset,
        size = outerRect,
        style = Stroke(width = 11f)
      )
    }

    // ── Inner ring: Battery (top 90°) ──
    val innerSize = outerSize - 22f
    val innerRect = Size(innerSize, innerSize)
    val innerOffset = Offset((size.width - innerSize) / 2, (size.height - innerSize) / 2)

    // Background track
    drawArc(
      color = Color(0x0E50FF78),
      startAngle = -135f,
      sweepAngle = 90f,
      useCenter = false,
      topLeft = innerOffset,
      size = innerRect,
      style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
    // Fill
    val batColor = if (batteryPct < 0.3f) Color(0xFFFF4646) else Color(0xFF50FF78)
    drawArc(
      color = batColor,
      startAngle = -135f,
      sweepAngle = 90f * batteryPct,
      useCenter = false,
      topLeft = innerOffset,
      size = innerRect,
      style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // ── Emotion glow ring (pulsing) ──
    val glowSize = innerSize - 18f
    val glowRect = Size(glowSize, glowSize)
    val glowOffset = Offset((size.width - glowSize) / 2, (size.height - glowSize) / 2)

    drawArc(
      color = emotionColor.copy(alpha = glowAlpha),
      startAngle = 0f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = glowOffset,
      size = glowRect,
      style = Stroke(width = 2.5f)
    )
  }
}

@Composable
private fun CompactNeedRow(label: String, value: Float, color: Color) {
  val barColor = when {
    value > 0.7f -> Color(0xFF50FF78)
    value > 0.3f -> Color(0xFFFFE650)
    else -> Color(0xFFFF4646)
  }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth()
  ) {
    Text(
      text = label,
      color = color.copy(alpha = 0.60f),
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.width(32.dp)
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.06f))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(value.coerceIn(0f, 1f))
          .fillMaxHeight()
          .clip(RoundedCornerShape(3.dp))
          .background(barColor)
      )
    }
    Spacer(Modifier.width(6.dp))
    Text(
      text = "${(value * 100).toInt()}%",
      color = barColor.copy(alpha = 0.8f),
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.width(30.dp)
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

/**
 * Resolve sprite drawable resource ID based on pet type, theme, mood, and frame.
 */
private fun getSpriteResource(
  context: android.content.Context,
  petType: PetType,
  theme: PetColorTheme,
  mood: PetMood,
  tick: Int
): Int {
  val prefix = when (petType) {
    PetType.BLOB -> "pet"
    PetType.CAT -> "cat"
    PetType.DOG -> "dog"
  }
  val suffix = "_${theme.name.lowercase()}"
  val frame = if (tick % 2 == 0) "1" else "2"
  val name = when (mood) {
    PetMood.HAPPY, PetMood.CONTENT, PetMood.CELEBRATING -> "${prefix}_idle_$frame$suffix"
    PetMood.TIRED, PetMood.HUNGRY -> "${prefix}_sleep$suffix"
    PetMood.SLEEPING -> "${prefix}_sleep$suffix"
  }
  return context.resources.getIdentifier(name, "drawable", context.packageName)
}
