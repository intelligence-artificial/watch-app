package com.tamagotchi.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

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

  // Refresh + update emotion engine every 2s
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(2000)
      petStatusEngine.update(healthDataManager.snapshot())
      refreshTick++
    }
  }

  val petName = remember(refreshTick) { petStateManager.petName }
  val theme = remember(refreshTick) { petStateManager.colorTheme }
  val emotion = remember(refreshTick) { petStatusEngine.currentEmotion }
  val needs = remember(refreshTick) { petStatusEngine.currentNeeds }
  val steps = remember(refreshTick) { healthDataManager.dailySteps }
  val hr = remember(refreshTick) { healthDataManager.heartRate }

  // Map old mood from PetStateManager (for sprite lookup)
  val mood = remember(refreshTick) { petStateManager.mood }
  val spriteRes = remember(theme, mood, refreshTick) {
    getSpriteResource(context, theme, mood, refreshTick)
  }

  val themeColor = when (theme) {
    PetColorTheme.GREEN -> Color(0xFF78FFA0)
    PetColorTheme.BLUE -> Color(0xFF64C8FF)
    PetColorTheme.PINK -> Color(0xFFFF8CC8)
    PetColorTheme.YELLOW -> Color(0xFFFFE664)
  }

  val emotionColor = Color(emotion.arcColor)

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
  ) {
    // Pet name + level
    item {
      Text(
        text = "$petName  Lv.${needs.level}",
        color = themeColor,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    }

    // Pet sprite
    item {
      if (spriteRes != 0) {
        Image(
          painter = painterResource(id = spriteRes),
          contentDescription = "Pet",
          modifier = Modifier.size(100.dp).padding(8.dp)
        )
      }
    }

    // Emotion status
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = emotion.line1,
          color = emotionColor,
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
        Text(
          text = emotion.line2,
          color = emotionColor.copy(alpha = 0.7f),
          fontSize = 12.sp,
          textAlign = TextAlign.Center
        )
      }
    }

    // Needs bars
    item {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      ) {
        NeedBar("HNG", needs.hunger, Color(0xFFFFAA50))
        NeedBar("HAP", needs.happiness, Color(0xFF50E6FF))
        NeedBar("NRG", needs.energy, Color(0xFF50FF78))
      }
    }

    // Quick stats
    item {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      ) {
        StatChip(label = "Steps", value = "$steps", color = Color(0xFF50E6FF))
        StatChip(label = "HR", value = if (hr > 0) "${hr}bpm" else "--", color = Color(0xFFFF6464))
        StatChip(label = "XP", value = "${needs.xpTotal}", color = themeColor)
      }
    }

    // Customize button
    item {
      Chip(
        onClick = onNavigateToCustomize,
        label = { Text("Customize") },
        colors = ChipDefaults.chipColors(backgroundColor = themeColor.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
      )
    }

    // Stats button
    item {
      Chip(
        onClick = onNavigateToStats,
        label = { Text("Stats & Health") },
        colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF50E6FF).copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
      )
    }
  }
}

@Composable
private fun NeedBar(label: String, value: Float, color: Color) {
  val barColor = when {
    value > 0.7f -> Color(0xFF50FF78)  // green
    value > 0.3f -> Color(0xFFFFE650)  // yellow
    else -> Color(0xFFFF4646)           // red
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.width(60.dp)
  ) {
    Text(text = label, color = color.copy(alpha = 0.7f), fontSize = 9.sp)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.1f))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(value.coerceIn(0f, 1f))
          .fillMaxHeight()
          .clip(RoundedCornerShape(3.dp))
          .background(barColor)
      )
    }
  }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.15f))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text(text = label, color = color.copy(alpha = 0.7f), fontSize = 9.sp)
  }
}

/**
 * Resolve sprite drawable resource ID based on theme, mood, and animation frame.
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
    PetMood.HAPPY -> "pet_happy_$frame$suffix"
    PetMood.CONTENT -> "pet_idle_$frame$suffix"
    PetMood.TIRED -> "pet_tired$suffix"
    PetMood.HUNGRY -> "pet_tired$suffix"
    PetMood.CELEBRATING -> "pet_celebrate_$frame$suffix"
    PetMood.SLEEPING -> "pet_sleep$suffix"
  }
  return context.resources.getIdentifier(name, "drawable", context.packageName)
}
