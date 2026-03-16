package com.pixelface.watch

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

// Steps activity zones (color-coded)
private val STEPS_LOW = Color(0xFF4488FF)      // < 2000 — Sedentary
private val STEPS_LIGHT = Color(0xFF00D68F)    // 2000–5000 — Light
private val STEPS_MODERATE = Color(0xFFFFB800) // 5000–8000 — Moderate
private val STEPS_ACTIVE = Color(0xFFFF6B35)   // 8000–10000 — Active
private val STEPS_HIGH = Color(0xFFFF3366)     // 10000+ — Very Active

private fun stepsZoneColor(steps: Int): Color = when {
  steps < 2000 -> STEPS_LOW
  steps < 5000 -> STEPS_LIGHT
  steps < 8000 -> STEPS_MODERATE
  steps < 10000 -> STEPS_ACTIVE
  else -> STEPS_HIGH
}

private fun stepsZoneLabel(steps: Int): String = when {
  steps < 2000 -> "Sedentary"
  steps < 5000 -> "Light"
  steps < 8000 -> "Moderate"
  steps < 10000 -> "Active"
  else -> "Very Active"
}

@Composable
fun StepsChartScreen(
  stepsHistoryStore: StepsHistoryStore,
  currentSteps: Int,
  onBack: () -> Unit
) {
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
  val coroutineScope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(5000)
      refreshTick++
    }
  }

  var selectedRange by remember { mutableStateOf(TimeRange.DAY) }
  val history = remember(refreshTick, selectedRange) { stepsHistoryStore.getHistoryForRange(selectedRange) }
  val zoneColor = stepsZoneColor(currentSteps)
  val zoneLabel = stepsZoneLabel(currentSteps)

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
    // ── Header: current steps ──
    item {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "👟 Steps",
          color = zoneColor,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = if (currentSteps > 0) numFmt.format(currentSteps) else "--",
            color = zoneColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Spacer(Modifier.width(4.dp))
          Text(
            text = "steps",
            color = zoneColor.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 5.dp)
          )
        }
        // Zone badge
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
        StepsSparklineChart(
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
        val stepValues = history.map { it.steps }
        val minSteps = stepValues.min()
        val maxSteps = stepValues.max()
        val avgSteps = stepValues.average().toInt()

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
            StepsStatBadge("MIN", numFmt.format(minSteps), stepsZoneColor(minSteps))
            StepsStatBadge("AVG", numFmt.format(avgSteps), stepsZoneColor(avgSteps))
            StepsStatBadge("MAX", numFmt.format(maxSteps), stepsZoneColor(maxSteps))
          }

          // Time range
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
private fun StepsStatBadge(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = label,
      color = Color.White.copy(alpha = 0.4f),
      fontSize = 8.sp,
      fontFamily = FontFamily.Monospace
    )
    Text(
      text = value,
      color = color,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}

@Composable
private fun StepsSparklineChart(
  readings: List<StepsHistoryStore.StepsReading>,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (readings.size < 2) return@Canvas

    val w = size.width
    val h = size.height

    // Y axis range
    val stepValues = readings.map { it.steps.toFloat() }
    val yMin = (stepValues.min() - 200).coerceAtLeast(0f)
    val yMax = (stepValues.max() + 200).coerceAtMost(20000f)
    val yRange = (yMax - yMin).coerceAtLeast(500f)

    // Draw zone bands
    val zones = listOf(
      0f to 2000f to STEPS_LOW,
      2000f to 5000f to STEPS_LIGHT,
      5000f to 8000f to STEPS_MODERATE,
      8000f to 10000f to STEPS_ACTIVE,
      10000f to 20000f to STEPS_HIGH
    )

    for ((range, color) in zones) {
      val (lo, hi) = range
      val bandTop = h - ((hi.coerceIn(yMin, yMax) - yMin) / yRange) * h
      val bandBot = h - ((lo.coerceIn(yMin, yMax) - yMin) / yRange) * h
      if (bandBot > bandTop) {
        drawRect(
          color = color.copy(alpha = 0.06f),
          topLeft = Offset(0f, bandTop),
          size = androidx.compose.ui.geometry.Size(w, bandBot - bandTop)
        )
      }
    }

    // Draw sparkline
    val path = Path()
    val xStep = w / (readings.size - 1).toFloat()

    for (i in readings.indices) {
      val x = i * xStep
      val y = h - ((readings[i].steps - yMin) / yRange) * h

      if (i == 0) {
        path.moveTo(x, y)
      } else {
        path.lineTo(x, y)
      }
    }

    val lineColor = stepsZoneColor(readings.last().steps)
    drawPath(
      path = path,
      color = lineColor.copy(alpha = 0.8f),
      style = Stroke(width = 2.5f, cap = StrokeCap.Round)
    )

    // Draw dots at each reading
    for (i in readings.indices) {
      val x = i * xStep
      val y = h - ((readings[i].steps - yMin) / yRange) * h
      val dotColor = stepsZoneColor(readings[i].steps)

      drawCircle(
        color = dotColor.copy(alpha = 0.5f),
        radius = 2.5f,
        center = Offset(x, y)
      )
    }

    // Highlight latest reading
    val lastX = (readings.size - 1) * xStep
    val lastY = h - ((readings.last().steps - yMin) / yRange) * h
    drawCircle(
      color = lineColor,
      radius = 4.5f,
      center = Offset(lastX, lastY)
    )
  }
}
