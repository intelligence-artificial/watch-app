package com.pixelface.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Calorie zones
private val CAL_LOW = Color(0xFF4488FF)       // < 500 — Low
private val CAL_LIGHT = Color(0xFF00D68F)     // 500–1000 — Light
private val CAL_MODERATE = Color(0xFFFFB800)  // 1000–1800 — Moderate
private val CAL_ACTIVE = Color(0xFFFF6B35)    // 1800–2500 — Active
private val CAL_HIGH = Color(0xFFFF3366)      // 2500+ — High Burn

private fun calZoneColor(cal: Int): Color = when {
  cal < 500 -> CAL_LOW
  cal < 1000 -> CAL_LIGHT
  cal < 1800 -> CAL_MODERATE
  cal < 2500 -> CAL_ACTIVE
  else -> CAL_HIGH
}

private fun calZoneLabel(cal: Int): String = when {
  cal < 500 -> "Low"
  cal < 1000 -> "Light"
  cal < 1800 -> "Moderate"
  cal < 2500 -> "Active"
  else -> "High Burn"
}

@Composable
fun CaloriesChartScreen(
  caloriesHistoryStore: CaloriesHistoryStore,
  currentCalories: Int,
  onBack: () -> Unit,
  onNavigateToHr: () -> Unit = {},
  onNavigateToSteps: () -> Unit = {}
) {
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(5000); refreshTick++ }
  }

  var selectedRange by remember { mutableStateOf(TimeRange.DAY) }
  val history = remember(refreshTick, selectedRange) { caloriesHistoryStore.getHistoryForRange(selectedRange) }
  val zoneColor = calZoneColor(currentCalories)
  val zoneLabel = calZoneLabel(currentCalories)

  ScalingLazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .onRotaryScrollEvent {
        coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }
        true
      }
      .focusRequester(focusRequester)
      .focusable(),
    state = listState,
    autoCentering = null
  ) {
    // ── Chart Tab Bar (emoji-only) ──
    item {
      ChartTabBar(activeTab = "cal", onNavigateToHr = onNavigateToHr, onNavigateToSteps = onNavigateToSteps, onNavigateToCal = {})
    }

    // ── Header: current calories ──
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = if (currentCalories > 0) numFmt.format(currentCalories) else "--",
            color = zoneColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "kcal",
            color = zoneColor.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 5.dp)
          )
        }
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(zoneColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
          Text(
            text = zoneLabel,
            color = zoneColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // ── Time Range Tabs (D/W/M/Y) ──
    item {
      TimeRangeTabs(selected = selectedRange, onSelect = { selectedRange = it }, accentColor = zoneColor)
    }

    // ── Chart ──
    item {
      if (history.size >= 2) {
        CalSparklineChart(
          readings = history,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(75.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(8.dp)
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Collecting data...\n${history.size} readings",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
          )
        }
      }
    }

    // ── Stats summary ──
    item {
      if (history.isNotEmpty()) {
        val calValues = history.map { it.calories }
        val minCal = calValues.min()
        val maxCal = calValues.max()
        val avgCal = calValues.average().toInt()

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(10.dp)
        ) {
          Text(
            "${selectedRange.label} · ${history.size} readings",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Spacer(Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            CalStatBadge("MIN", numFmt.format(minCal), calZoneColor(minCal))
            CalStatBadge("AVG", numFmt.format(avgCal), calZoneColor(avgCal))
            CalStatBadge("MAX", numFmt.format(maxCal), calZoneColor(maxCal))
          }
          if (history.size >= 2) {
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val first = fmt.format(Date(history.first().timestampMs))
            val last = fmt.format(Date(history.last().timestampMs))
            Spacer(Modifier.height(4.dp))
            Text(
              text = "$first — $last",
              color = Color.White.copy(alpha = 0.3f),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CalStatBadge(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun CalSparklineChart(
  readings: List<CaloriesHistoryStore.CalReading>,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (readings.size < 2) return@Canvas
    val w = size.width
    val h = size.height
    val calValues = readings.map { it.calories.toFloat() }
    val yMin = (calValues.min() - 50).coerceAtLeast(0f)
    val yMax = (calValues.max() + 50).coerceAtMost(5000f)
    val yRange = (yMax - yMin).coerceAtLeast(100f)

    val zones = listOf(
      0f to 500f to CAL_LOW,
      500f to 1000f to CAL_LIGHT,
      1000f to 1800f to CAL_MODERATE,
      1800f to 2500f to CAL_ACTIVE,
      2500f to 5000f to CAL_HIGH
    )
    for ((range, color) in zones) {
      val (lo, hi) = range
      val bandTop = h - ((hi.coerceIn(yMin, yMax) - yMin) / yRange) * h
      val bandBot = h - ((lo.coerceIn(yMin, yMax) - yMin) / yRange) * h
      if (bandBot > bandTop) {
        drawRect(color = color.copy(alpha = 0.06f), topLeft = Offset(0f, bandTop),
          size = androidx.compose.ui.geometry.Size(w, bandBot - bandTop))
      }
    }

    val path = Path()
    val xStep = w / (readings.size - 1).toFloat()
    for (i in readings.indices) {
      val x = i * xStep
      val y = h - ((readings[i].calories - yMin) / yRange) * h
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val lineColor = calZoneColor(readings.last().calories)
    drawPath(path, lineColor.copy(alpha = 0.8f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    for (i in readings.indices) {
      val x = i * xStep
      val y = h - ((readings[i].calories - yMin) / yRange) * h
      drawCircle(calZoneColor(readings[i].calories).copy(alpha = 0.5f), 2.5f, Offset(x, y))
    }
    val lastX = (readings.size - 1) * xStep
    val lastY = h - ((readings.last().calories - yMin) / yRange) * h
    drawCircle(lineColor, 4.5f, Offset(lastX, lastY))
  }
}
