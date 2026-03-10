package com.tamagotchi.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(
  petStateManager: PetStateManager,
  healthDataManager: HealthDataManager,
  petStatusEngine: PetStatusEngine,
  onBack: () -> Unit
) {
  val listState = rememberScalingLazyListState()

  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(3000)
      refreshTick++
    }
  }

  val needs = remember(refreshTick) { petStatusEngine.currentNeeds }
  val emotion = remember(refreshTick) { petStatusEngine.currentEmotion }
  val hr = remember(refreshTick) { healthDataManager.heartRate }
  val steps = remember(refreshTick) { healthDataManager.dailySteps }
  val calories = remember(refreshTick) { healthDataManager.calories }
  val floors = remember(refreshTick) { healthDataManager.floorsClimbed }
  val sedentary = remember(refreshTick) { healthDataManager.isSedentary }

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxSize().background(Color(0xFF020206))
  ) {
    // Header
    item {
      Text(
        text = "WetPet Stats",
        color = Color(0xFF50FF78),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    }

    // Current emotion
    item {
      Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(4.dp)
      ) {
        Text(
          text = "Mood: ${emotion.name}",
          color = Color(emotion.arcColor),
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // Fitness data
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.White.copy(alpha = 0.05f))
          .padding(12.dp)
      ) {
        Text("Fitness", color = Color(0xFF50E6FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        DataRow("♥ Heart Rate", if (hr > 0) "$hr bpm" else "--", Color(0xFFFF6464))
        DataRow("👟 Steps", "$steps / 10,000", Color(0xFF50E6FF))
        DataRow("🔥 Calories", "$calories kcal", Color(0xFFFF9F50))
        DataRow("🏢 Floors", "$floors", Color(0xFF78FFA0))
        DataRow("💤 Sedentary", if (sedentary) "Yes ⚠" else "No ✓",
          if (sedentary) Color(0xFFFF4646) else Color(0xFF50FF78))
      }
    }

    // Pet needs
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color.White.copy(alpha = 0.05f))
          .padding(12.dp)
      ) {
        Text("Pet Status", color = Color(0xFF78FFA0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        NeedRow("Hunger", needs.hunger)
        NeedRow("Happiness", needs.happiness)
        NeedRow("Energy", needs.energy)
        NeedRow("Health", needs.health)
        NeedRow("Stress", needs.stress)
        Spacer(Modifier.height(4.dp))
        DataRow("Level", "${needs.level}", Color(0xFFFFE664))
        DataRow("XP", "${needs.xpTotal}", Color(0xFFFFE664))
      }
    }
  }
}

@Composable
private fun DataRow(label: String, value: String, color: Color) {
  Row(
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
  ) {
    Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun NeedRow(label: String, value: Float) {
  val barColor = when {
    value > 0.7f -> Color(0xFF50FF78)
    value > 0.3f -> Color(0xFFFFE650)
    else -> Color(0xFFFF4646)
  }

  Column(modifier = Modifier.padding(vertical = 2.dp)) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
      Text(
        text = "${(value * 100).toInt()}%",
        color = barColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
      )
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(Color.White.copy(alpha = 0.1f))
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
