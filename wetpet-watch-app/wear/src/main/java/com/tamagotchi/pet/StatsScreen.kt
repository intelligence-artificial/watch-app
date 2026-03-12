package com.tamagotchi.pet

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
  petStateManager: PetStateManager,
  healthDataManager: HealthDataManager,
  petStatusEngine: PetStatusEngine,
  onBack: () -> Unit
) {
  val listState = rememberScalingLazyListState()
  val context = LocalContext.current

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

  // Debug info
  val lastUpdate = remember(refreshTick) { healthDataManager.lastDataUpdateMs }
  val supported = remember(refreshTick) { healthDataManager.supportedTypes }
  val capChecked = remember(refreshTick) { healthDataManager.capabilitiesChecked }
  var showDebug by remember { mutableStateOf(false) }

  val emotionColor = Color(
    ((emotion.arcColor shr 16) and 0xFF).toInt(),
    ((emotion.arcColor shr 8) and 0xFF).toInt(),
    (emotion.arcColor and 0xFF).toInt()
  )

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxSize().background(Color(0xFF020206))
  ) {
    // Header
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "📊 Stats",
          color = Color(0xFF50E6FF),
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        // Mood badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(emotionColor.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
          Text(
            text = "${emotion.line1}",
            color = emotionColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Fitness card
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.04f))
          .padding(12.dp)
      ) {
        Text(
          "Fitness",
          color = Color(0xFF50E6FF),
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))
        DataRow("♥  Heart Rate", if (hr > 0) "$hr bpm" else "--", Color(0xFFFF6464))
        DataRow("👟  Steps", "$steps / 10,000", Color(0xFF50E6FF))
        DataRow("🔥  Calories", "$calories kcal", Color(0xFFFF9F50))
        DataRow("🏢  Floors", "$floors", Color(0xFF78FFA0))
        DataRow(
          "💤  Sedentary",
          if (sedentary) "Yes ⚠" else "No ✓",
          if (sedentary) Color(0xFFFF4646) else Color(0xFF50FF78)
        )
      }
    }

    // Pet needs card
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.04f))
          .padding(12.dp)
      ) {
        Text(
          "Pet Status",
          color = Color(0xFF78FFA0),
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))
        NeedRow("Hunger", needs.hunger)
        NeedRow("Happiness", needs.happiness)
        NeedRow("Energy", needs.energy)
        NeedRow("Health", needs.health)
        NeedRow("Stress", needs.stress)
        Spacer(Modifier.height(6.dp))
        DataRow("⭐  Level", "${needs.level}", Color(0xFFFFE664))
        DataRow("✦  XP", "${needs.xpTotal}", Color(0xFFFFE664))
      }
    }

    // Debug toggle
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.02f))
          .clickable { showDebug = !showDebug }
          .padding(8.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (showDebug) "▼ Debug Info" else "▶ Debug Info",
          color = Color.White.copy(alpha = 0.4f),
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // Debug card
    if (showDebug) {
      item {
        val bodySensors = ContextCompat.checkSelfPermission(
          context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        val activityRecog = ContextCompat.checkSelfPermission(
          context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        val lastUpdateText = if (lastUpdate > 0) {
          SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdate))
        } else {
          "never"
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A0A2E).copy(alpha = 0.6f))
            .padding(12.dp)
        ) {
          Text(
            "🔧 Debug",
            color = Color(0xFFB088FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Spacer(Modifier.height(6.dp))

          // Permissions
          DataRow(
            "BODY_SENSORS",
            if (bodySensors) "✓" else "✗",
            if (bodySensors) Color(0xFF50FF78) else Color(0xFFFF4646)
          )
          DataRow(
            "ACTIVITY_RECOG",
            if (activityRecog) "✓" else "✗",
            if (activityRecog) Color(0xFF50FF78) else Color(0xFFFF4646)
          )

          Spacer(Modifier.height(4.dp))

          // Capabilities
          DataRow(
            "Cap checked",
            if (capChecked) "✓" else "✗",
            if (capChecked) Color(0xFF50FF78) else Color(0xFFFFE650)
          )
          DataRow(
            "Types",
            "${supported.size}",
            Color(0xFF50E6FF)
          )

          // Supported types list
          if (supported.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            for (type in supported.take(6)) {
              Text(
                text = "  • $type",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }

          Spacer(Modifier.height(4.dp))
          DataRow("Last data", lastUpdateText, Color(0xFF50E6FF))

          // Raw values
          Spacer(Modifier.height(4.dp))
          Text(
            "Raw Values",
            color = Color(0xFFB088FF).copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          DataRow("HR raw", "$hr", Color.White.copy(alpha = 0.5f))
          DataRow("Steps raw", "$steps", Color.White.copy(alpha = 0.5f))
          DataRow("Cal raw", "$calories", Color.White.copy(alpha = 0.5f))
          DataRow("Floors raw", "$floors", Color.White.copy(alpha = 0.5f))
        }
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
    Text(
      text = label,
      color = Color.White.copy(alpha = 0.6f),
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace
    )
    Text(
      text = value,
      color = color,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
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
      Text(
        text = label,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace
      )
      Text(
        text = "${(value * 100).toInt()}%",
        color = barColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      )
    }
    Spacer(Modifier.height(1.dp))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.08f))
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
