package com.pixelface.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.wear.compose.material.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// BPM zone colors
private val ZONE_BLUE = Color(0xFF4488FF)
private val ZONE_GREEN = Color(0xFF00D68F)
private val ZONE_AMBER = Color(0xFFFFB800)
private val ZONE_RED = Color(0xFFFF3366)

private fun bpmZoneColor(bpm: Int): Color = when {
  bpm < 70 -> ZONE_BLUE
  bpm < 90 -> ZONE_GREEN
  bpm < 120 -> ZONE_AMBER
  else -> ZONE_RED
}

private fun bpmZoneLabel(bpm: Int): String = when {
  bpm < 70 -> "Resting"
  bpm < 90 -> "Normal"
  bpm < 120 -> "Elevated"
  else -> "High"
}

@Composable
fun HrChartScreen(
  hrHistoryStore: HrHistoryStore,
  currentBpm: Int,
  onBack: () -> Unit,
  onNavigateToSteps: () -> Unit = {},
  onNavigateToCal: () -> Unit = {}
) {
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(5000); refreshTick++ }
  }

  var selectedRange by remember { mutableStateOf(TimeRange.DAY) }
  val history = remember(refreshTick, selectedRange) {
    hrHistoryStore.getHistoryForRange(selectedRange)
  }
  val zoneColor = bpmZoneColor(currentBpm)
  val zoneLabel = bpmZoneLabel(currentBpm)

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    autoCentering = null,
    contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
      .padding(horizontal = 8.dp)
      .onRotaryScrollEvent { event ->
        coroutineScope.launch { listState.scroll(MutatePriority.UserInput) { scrollBy(event.verticalScrollPixels) } }
        true
      }
      .focusRequester(focusRequester)
      .focusable()
  ) {
    // ── Chart Tab Bar (emoji-only) ──
    item {
      ChartTabBar(activeTab = "hr", onNavigateToHr = {}, onNavigateToSteps = onNavigateToSteps, onNavigateToCal = onNavigateToCal)
    }

    // ── Header ──
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("♥ Heart Rate", color = zoneColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(if (currentBpm > 0) "$currentBpm" else "--", color = zoneColor, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
          Spacer(Modifier.width(4.dp))
          Text("bpm", color = zoneColor.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 6.dp))
        }
        Box(
          modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(zoneColor.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
          Text(zoneLabel, color = zoneColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
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
        HrSparklineChart(
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
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(60.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.03f)),
          contentAlignment = Alignment.Center
        ) {
          Text("Collecting data...\n${history.size} readings", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
      }
    }

    // ── Stats summary ──
    item {
      if (history.isNotEmpty()) {
        val bpmValues = history.map { it.bpm }
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.04f)).padding(10.dp)
        ) {
          Text("${selectedRange.label} · ${history.size} readings", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
          Spacer(Modifier.height(4.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatBadge("MIN", bpmValues.min(), bpmZoneColor(bpmValues.min()))
            StatBadge("AVG", bpmValues.average().toInt(), bpmZoneColor(bpmValues.average().toInt()))
            StatBadge("MAX", bpmValues.max(), bpmZoneColor(bpmValues.max()))
          }
          if (history.size >= 2) {
            val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
            Spacer(Modifier.height(4.dp))
            Text(
              "${fmt.format(Date(history.first().timestampMs))} — ${fmt.format(Date(history.last().timestampMs))}",
              color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp, fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

// ========================
// Shared Time Range Tab Selector
// ========================

@Composable
fun TimeRangeTabs(
  selected: TimeRange,
  onSelect: (TimeRange) -> Unit,
  accentColor: Color
) {
  Row(
    horizontalArrangement = Arrangement.SpaceEvenly,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
  ) {
    TimeRange.entries.forEach { range ->
      val isSelected = range == selected
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(
            if (isSelected) accentColor.copy(alpha = 0.18f)
            else Color.White.copy(alpha = 0.04f)
          )
          .clickable { onSelect(range) }
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          text = range.shortLabel,
          color = if (isSelected) accentColor else Color.White.copy(alpha = 0.4f),
          fontSize = 12.sp,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}



@Composable
private fun StatBadge(label: String, value: Int, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    Text("$value", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
  }
}

@Composable
private fun HrSparklineChart(
  readings: List<HrHistoryStore.HrReading>,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (readings.size < 2) return@Canvas
    val w = size.width; val h = size.height
    val bpmValues = readings.map { it.bpm }
    val yMin = (bpmValues.min() - 5).coerceAtLeast(40).toFloat()
    val yMax = (bpmValues.max() + 5).coerceAtMost(200).toFloat()
    val yRange = (yMax - yMin).coerceAtLeast(10f)

    val zones = listOf(40f to 70f to ZONE_BLUE, 70f to 90f to ZONE_GREEN, 90f to 120f to ZONE_AMBER, 120f to 200f to ZONE_RED)
    for ((range, color) in zones) {
      val (lo, hi) = range
      val bandTop = h - ((hi.coerceIn(yMin, yMax) - yMin) / yRange) * h
      val bandBot = h - ((lo.coerceIn(yMin, yMax) - yMin) / yRange) * h
      if (bandBot > bandTop) {
        drawRect(color.copy(alpha = 0.06f), Offset(0f, bandTop), androidx.compose.ui.geometry.Size(w, bandBot - bandTop))
      }
    }

    val path = Path()
    val xStep = w / (readings.size - 1).toFloat()
    for (i in readings.indices) {
      val x = i * xStep; val y = h - ((readings[i].bpm - yMin) / yRange) * h
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, bpmZoneColor(readings.last().bpm).copy(alpha = 0.8f), style = Stroke(2.5f, cap = StrokeCap.Round))

    for (i in readings.indices) {
      val x = i * xStep; val y = h - ((readings[i].bpm - yMin) / yRange) * h
      drawCircle(bpmZoneColor(readings[i].bpm).copy(alpha = 0.5f), 2.5f, Offset(x, y))
    }
    val lastX = (readings.size - 1) * xStep; val lastY = h - ((readings.last().bpm - yMin) / yRange) * h
    drawCircle(bpmZoneColor(readings.last().bpm), 4.5f, Offset(lastX, lastY))
  }
}
