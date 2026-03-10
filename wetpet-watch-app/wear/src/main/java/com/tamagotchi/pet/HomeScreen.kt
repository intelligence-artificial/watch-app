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
  onNavigateToStats: () -> Unit
) {
  val context = LocalContext.current
  val listState = rememberScalingLazyListState()
  val scope = rememberCoroutineScope()

  // Update engine every 2s
  var tick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(2000)
      petStatusEngine.update(healthDataManager.snapshot())
      petStateManager.saveEmotion(petStatusEngine.currentEmotion)
      tick++
      if (tick % 15 == 0) {
        scope.launch {
          petStateManager.syncToPhone(
            petStatusEngine.currentEmotion,
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
  val spriteRes = remember(theme, mood, tick) {
    getSpriteResource(context, theme, mood, tick)
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
    // ── Scuba Arc Rings + Pet ──
    item(key = "hero") {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
      ) {
        // Draw scuba-style arc rings
        ScubaArcRings(
          stepsProgress = (steps / 10000f).coerceIn(0f, 1f),
          batteryPct = batteryPct / 100f,
          themeColor = themeColor,
          emotionColor = emotionColor
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
              modifier = Modifier.size(72.dp)
            )
          } else {
            Text("🐾", fontSize = 40.sp)
          }
        }
      }
    }

    // ── Pet name + level ──
    item(key = "header") {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = petName,
          color = themeColor,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Text(
          text = "LV.${needs.level}  ·  ${needs.xpTotal}XP",
          color = themeColor.copy(alpha = 0.5f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // ── Emotion status ──
    item(key = "emotion") {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 2.dp)
      ) {
        Text(
          text = emotion.line1,
          color = emotionColor,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Text(
          text = emotion.line2,
          color = emotionColor.copy(alpha = 0.5f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
      }
    }

    // ── Needs bars ──
    item(key = "needs") {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
      ) {
        NeedBar("HNG", needs.hunger, Color(0xFFFFAA50))
        NeedBar("HAP", needs.happiness, Color(0xFF50E6FF))
        NeedBar("NRG", needs.energy, Color(0xFF50FF78))
      }
    }

    // ── Quick stats ──
    item(key = "stats") {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
      ) {
        StatChip("👟", "$steps", Color(0xFF50E6FF))
        StatChip("♥", if (hr > 0) "$hr" else "--", Color(0xFFFF6464))
        StatChip("🔥", "$calories", Color(0xFFFF9F50))
      }
    }

    // ── Navigation ──
    item(key = "customize_btn") {
      Chip(
        onClick = onNavigateToCustomize,
        label = { Text("Customize", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        colors = ChipDefaults.chipColors(backgroundColor = themeColor.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)
      )
    }
    item(key = "stats_btn") {
      Chip(
        onClick = onNavigateToStats,
        label = { Text("Stats & Health", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF50E6FF).copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp)
      )
    }
  }
}

/**
 * Scuba-style concentric arc rings drawn with Canvas.
 */
@Composable
private fun ScubaArcRings(
  stepsProgress: Float,
  batteryPct: Float,
  themeColor: Color,
  emotionColor: Color
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
      color = Color(0x1550E6FF),
      startAngle = -225f,
      sweepAngle = 270f,
      useCenter = false,
      topLeft = outerOffset,
      size = outerRect,
      style = Stroke(width = 6f, cap = StrokeCap.Round)
    )
    // Fill
    drawArc(
      color = Color(0xFF50E6FF),
      startAngle = -225f,
      sweepAngle = 270f * stepsProgress,
      useCenter = false,
      topLeft = outerOffset,
      size = outerRect,
      style = Stroke(width = 6f, cap = StrokeCap.Round)
    )

    // Tick marks at 25%, 50%, 75%
    for (pct in listOf(0.25f, 0.5f, 0.75f)) {
      val angle = -225f + 270f * pct
      drawArc(
        color = Color(0x30FFFFFF),
        startAngle = angle - 1f,
        sweepAngle = 2f,
        useCenter = false,
        topLeft = outerOffset,
        size = outerRect,
        style = Stroke(width = 10f)
      )
    }

    // ── Inner ring: Battery (top 90°) ──
    val innerSize = outerSize - 20f
    val innerRect = Size(innerSize, innerSize)
    val innerOffset = Offset((size.width - innerSize) / 2, (size.height - innerSize) / 2)

    // Background track
    drawArc(
      color = Color(0x1050FF78),
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

    // ── Emotion glow ring (subtle) ──
    val glowSize = innerSize - 16f
    val glowRect = Size(glowSize, glowSize)
    val glowOffset = Offset((size.width - glowSize) / 2, (size.height - glowSize) / 2)

    drawArc(
      color = emotionColor.copy(alpha = 0.15f),
      startAngle = 0f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = glowOffset,
      size = glowRect,
      style = Stroke(width = 2f)
    )
  }
}

@Composable
private fun NeedBar(label: String, value: Float, color: Color) {
  val barColor = when {
    value > 0.7f -> Color(0xFF50FF78)
    value > 0.3f -> Color(0xFFFFE650)
    else -> Color(0xFFFF4646)
  }
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.width(55.dp)
  ) {
    Text(
      text = label,
      color = color.copy(alpha = 0.5f),
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(Color.White.copy(alpha = 0.06f))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(value.coerceIn(0f, 1f))
          .fillMaxHeight()
          .clip(RoundedCornerShape(2.dp))
          .background(barColor)
      )
    }
  }
}

@Composable
private fun StatChip(icon: String, value: String, color: Color) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.08f))
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
    Text(text = icon, fontSize = 11.sp)
    Text(
      text = value,
      color = color,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}

/**
 * Resolve sprite drawable resource ID.
 */
private fun getSpriteResource(
  context: android.content.Context,
  theme: PetColorTheme,
  mood: PetMood,
  tick: Int
): Int {
  val suffix = "_${theme.name.lowercase()}"
  val frame = if (tick % 2 == 0) "1" else "2"
  val name = when (mood) {
    PetMood.HAPPY -> "pet_idle_$frame$suffix"
    PetMood.CONTENT -> "pet_idle_$frame$suffix"
    PetMood.TIRED -> "pet_sleep$suffix"
    PetMood.HUNGRY -> "pet_sleep$suffix"
    PetMood.CELEBRATING -> "pet_idle_$frame$suffix"
    PetMood.SLEEPING -> "pet_sleep$suffix"
  }
  return context.resources.getIdentifier(name, "drawable", context.packageName)
}
