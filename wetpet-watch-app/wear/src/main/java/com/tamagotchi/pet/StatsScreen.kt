package com.tamagotchi.pet

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
  petStateManager: PetStateManager,
  healthDataManager: HealthDataManager,
  petStatusEngine: PetStatusEngine,
  onBack: () -> Unit,
  onNavigateToHrChart: () -> Unit = {},
  onNavigateToStepsChart: () -> Unit = {},
  onNavigateToCalChart: () -> Unit = {},
) {
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val context = LocalContext.current
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  // Refresh every 15s to reduce recomposition jank
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(15000)
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

  val emotionColor = Color(
    ((emotion.arcColor shr 16) and 0xFF).toInt(),
    ((emotion.arcColor shr 8) and 0xFF).toInt(),
    (emotion.arcColor and 0xFF).toInt()
  )

  // HR zone color
  val hrColor = when {
    hr < 70 -> Color(0xFF4488FF)
    hr < 90 -> Color(0xFF00D68F)
    hr < 120 -> Color(0xFFFFB800)
    else -> Color(0xFFFF3366)
  }

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    autoCentering = null,
    contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
      .onRotaryScrollEvent { event ->
        coroutineScope.launch { listState.scroll(MutatePriority.UserInput) { scrollBy(event.verticalScrollPixels) } }
        true
      }
      .focusRequester(focusRequester)
      .focusable()
  ) {
    // Header
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "📊 Stats & Health",
          color = Color(0xFF50E6FF),
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(emotionColor.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
          Text(
            text = emotion.line1,
            color = emotionColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Heart Rate card (tappable → chart) ──
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(hrColor.copy(alpha = 0.06f))
          .clickable { onNavigateToHrChart() }
          .padding(12.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "♥  Heart Rate",
              color = hrColor,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "▸",
              color = hrColor.copy(alpha = 0.5f),
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Spacer(Modifier.height(6.dp))
          Row(
            verticalAlignment = Alignment.Bottom
          ) {
            Text(
              text = if (hr > 0) "$hr" else "--",
              color = Color.White,
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "bpm",
              color = Color.White.copy(alpha = 0.4f),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(bottom = 4.dp)
            )
          }
          Text(
            text = "Tap for chart  →",
            color = hrColor.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Steps card (tappable → chart) ──
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFF00D68F).copy(alpha = 0.06f))
          .clickable { onNavigateToStepsChart() }
          .padding(12.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "👟  Steps",
              color = Color(0xFF00D68F),
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "▸",
              color = Color(0xFF00D68F).copy(alpha = 0.5f),
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = if (steps > 0) numFmt.format(steps) else "--",
              color = Color.White,
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "/ 10,000",
              color = Color.White.copy(alpha = 0.4f),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(bottom = 4.dp)
            )
          }
          Text(
            text = "Tap for chart  →",
            color = Color(0xFF00D68F).copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Calories card (tappable → chart) ──
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color(0xFFFF6B35).copy(alpha = 0.06f))
          .clickable { onNavigateToCalChart() }
          .padding(12.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "🔥  Calories",
              color = Color(0xFFFF6B35),
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "▸",
              color = Color(0xFFFF6B35).copy(alpha = 0.5f),
              fontSize = 14.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(
              text = if (calories > 0) numFmt.format(calories) else "--",
              color = Color.White,
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = "kcal",
              color = Color.White.copy(alpha = 0.4f),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(bottom = 4.dp)
            )
          }
          Text(
            text = "Tap for chart  →",
            color = Color(0xFFFF6B35).copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Other activity stats ──
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White.copy(alpha = 0.04f))
          .padding(12.dp)
      ) {
        StatRow("🏢", "Floors", "$floors", "", Color(0xFF78FFA0))
        Spacer(Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💤", fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
              "Sedentary",
              color = Color.White.copy(alpha = 0.6f),
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace
            )
          }
          Text(
            text = if (sedentary) "Yes ⚠" else "No ✓",
            color = if (sedentary) Color(0xFFFF4646) else Color(0xFF50FF78),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Pet needs card ──
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
        Spacer(Modifier.height(8.dp))
        NeedRow("Hunger", needs.hunger)
        NeedRow("Happiness", needs.happiness)
        NeedRow("Energy", needs.energy)
        NeedRow("Health", needs.health)
        NeedRow("Stress", needs.stress)
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              "LV",
              color = Color(0xFFFFE664).copy(alpha = 0.5f),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "${needs.level}",
              color = Color(0xFFFFE664),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              "XP",
              color = Color(0xFFFFE664).copy(alpha = 0.5f),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace
            )
            Text(
              "${needs.xpTotal}",
              color = Color(0xFFFFE664),
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatRow(
  icon: String,
  label: String,
  value: String,
  unit: String,
  color: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(icon, fontSize = 12.sp)
      Spacer(Modifier.width(6.dp))
      Text(
        text = label,
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace
      )
    }
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        text = value,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
      )
      if (unit.isNotEmpty()) {
        Spacer(Modifier.width(3.dp))
        Text(
          text = unit,
          color = color.copy(alpha = 0.45f),
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(bottom = 1.dp)
        )
      }
    }
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
