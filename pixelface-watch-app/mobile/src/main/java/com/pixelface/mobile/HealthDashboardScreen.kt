package com.pixelface.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Health Dashboard — overview of all health metrics from the watch.
 *
 * Shows latest values for HR, Steps, Calories with tappable cards
 * that navigate to full chart screens. Reads from Room DB.
 */
@Composable
fun HealthDashboardScreen(
  db: HealthDatabase,
  onNavigateToHrChart: () -> Unit,
  onNavigateToStepsChart: () -> Unit,
  onNavigateToCalChart: () -> Unit
) {
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }

  // Auto-refresh every 30s
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(30_000)
      refreshTick++
    }
  }

  var latestHr by remember { mutableStateOf<HrReading?>(null) }
  var latestSteps by remember { mutableStateOf<StepsReading?>(null) }
  var latestCal by remember { mutableStateOf<CaloriesReading?>(null) }
  var hrCount by remember { mutableIntStateOf(0) }
  var stepsCount by remember { mutableIntStateOf(0) }
  var calCount by remember { mutableIntStateOf(0) }

  LaunchedEffect(refreshTick) {
    latestHr = db.hrDao().getLatest()
    latestSteps = db.stepsDao().getLatest()
    latestCal = db.caloriesDao().getLatest()
    hrCount = db.hrDao().count()
    stepsCount = db.stepsDao().count()
    calCount = db.caloriesDao().count()
  }

  val dateFmt = remember { SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()) }
  val lastSync = listOfNotNull(
    latestHr?.timestampMs,
    latestSteps?.timestampMs,
    latestCal?.timestampMs
  ).maxOrNull()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0A0A0A)),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // ── Header ──
    item {
      Column {
        Text(
          "Health Dashboard",
          color = Color.White,
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = if (lastSync != null) "Last sync: ${dateFmt.format(Date(lastSync))}"
          else "No data yet — waiting for watch sync",
          color = Color(0xFF888888),
          fontSize = 13.sp
        )
      }
    }

    // ── Heart Rate Card ──
    item {
      HealthMetricCard(
        emoji = "♥",
        title = "Heart Rate",
        value = latestHr?.bpm?.toString() ?: "--",
        unit = "bpm",
        subtext = "$hrCount readings",
        color = hrZoneColor(latestHr?.bpm ?: 0),
        onClick = onNavigateToHrChart
      )
    }

    // ── Steps Card ──
    item {
      HealthMetricCard(
        emoji = "👟",
        title = "Steps",
        value = if ((latestSteps?.steps ?: 0) > 0) numFmt.format(latestSteps?.steps) else "--",
        unit = "today",
        subtext = "$stepsCount readings",
        color = stepsZoneColor(latestSteps?.steps ?: 0),
        onClick = onNavigateToStepsChart
      )
    }

    // ── Calories Card ──
    item {
      HealthMetricCard(
        emoji = "🔥",
        title = "Calories",
        value = if ((latestCal?.calories ?: 0) > 0) numFmt.format(latestCal?.calories) else "--",
        unit = "kcal",
        subtext = "$calCount readings",
        color = Color(0xFFFF6B35),
        onClick = onNavigateToCalChart
      )
    }

    // ── Data Summary ──
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            "📊 Data Summary",
            color = Color(0xFF888888),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.height(8.dp))
          val totalReadings = hrCount + stepsCount + calCount
          Text(
            "$totalReadings total readings stored",
            color = Color(0xFF666666),
            fontSize = 13.sp
          )
          Text(
            "Auto-sync: every 4 hours",
            color = Color(0xFF666666),
            fontSize = 13.sp
          )
          Text(
            "Data retention: 90 days",
            color = Color(0xFF666666),
            fontSize = 13.sp
          )
        }
      }
    }

    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun HealthMetricCard(
  emoji: String,
  title: String,
  value: String,
  unit: String,
  subtext: String,
  color: Color,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(emoji, fontSize = 18.sp)
          Spacer(Modifier.width(8.dp))
          Text(
            title,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            value,
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.width(6.dp))
          Text(
            unit,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 6.dp)
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          subtext,
          color = Color(0xFF666666),
          fontSize = 12.sp
        )
      }

      Icon(
        Icons.Default.ArrowForward,
        contentDescription = "View chart",
        tint = color.copy(alpha = 0.5f),
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

// Color helpers matching the watch app's zones
private fun hrZoneColor(bpm: Int): Color = when {
  bpm < 70 -> Color(0xFF4488FF)
  bpm < 90 -> Color(0xFF00D68F)
  bpm < 120 -> Color(0xFFFFB800)
  else -> Color(0xFFFF3366)
}

private fun stepsZoneColor(steps: Int): Color = when {
  steps < 2000 -> Color(0xFF4488FF)
  steps < 5000 -> Color(0xFF00D68F)
  steps < 8000 -> Color(0xFFFFB800)
  steps < 10000 -> Color(0xFFFF6B35)
  else -> Color(0xFFFF3366)
}
