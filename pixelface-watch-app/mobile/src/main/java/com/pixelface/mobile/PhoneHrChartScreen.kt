package com.pixelface.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HR_RESTING = Color(0xFF4488FF)
private val HR_NORMAL = Color(0xFF00D68F)
private val HR_ELEVATED = Color(0xFFFFB800)
private val HR_HIGH = Color(0xFFFF3366)

private fun hrColor(bpm: Int): Color = when {
  bpm < 70 -> HR_RESTING
  bpm < 90 -> HR_NORMAL
  bpm < 120 -> HR_ELEVATED
  else -> HR_HIGH
}

private fun hrZone(bpm: Int): String = when {
  bpm < 70 -> "Resting"
  bpm < 90 -> "Normal"
  bpm < 120 -> "Elevated"
  else -> "High"
}

enum class PhoneTimeRange(val label: String, val ms: Long) {
  DAY("Day", 24L * 60 * 60 * 1000),
  WEEK("Week", 7L * 24 * 60 * 60 * 1000),
  MONTH("Month", 30L * 24 * 60 * 60 * 1000),
  YEAR("Year", 365L * 24 * 60 * 60 * 1000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHrChartScreen(
  db: HealthDatabase,
  onBack: () -> Unit
) {
  var selectedRange by remember { mutableStateOf(PhoneTimeRange.DAY) }
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(30_000); refreshTick++ }
  }

  var readings by remember { mutableStateOf<List<HrReading>>(emptyList()) }
  var latest by remember { mutableStateOf<HrReading?>(null) }

  LaunchedEffect(selectedRange, refreshTick) {
    val cutoff = System.currentTimeMillis() - selectedRange.ms
    readings = db.hrDao().getAfter(cutoff)
    latest = db.hrDao().getLatest()
  }

  val currentBpm = latest?.bpm ?: 0
  val color = hrColor(currentBpm)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("♥ Heart Rate", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0A0A0A),
          titleContentColor = color
        )
      )
    },
    containerColor = Color(0xFF0A0A0A)
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // ── Current BPM ──
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                if (currentBpm > 0) "$currentBpm" else "--",
                color = color,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "bpm",
                color = color.copy(alpha = 0.6f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
              )
            }
            Text(
              hrZone(currentBpm),
              color = color,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // ── Range Tabs ──
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          PhoneTimeRange.entries.forEach { range ->
            FilterChip(
              selected = selectedRange == range,
              onClick = { selectedRange = range },
              label = { Text(range.label, fontSize = 12.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.2f),
                selectedLabelColor = color
              )
            )
          }
        }
      }

      // ── Chart ──
      item {
        if (readings.size >= 2) {
          PhoneSparklineChart(
            values = readings.map { it.bpm.toFloat() },
            timestamps = readings.map { it.timestampMs },
            lineColor = color,
            zoneBands = listOf(
              0f to 70f to HR_RESTING,
              70f to 90f to HR_NORMAL,
              90f to 120f to HR_ELEVATED,
              120f to 200f to HR_HIGH
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF1A1A1A))
              .padding(16.dp)
          )
        } else {
          EmptyChartPlaceholder("Collecting HR data...\n${readings.size} readings")
        }
      }

      // ── Stats ──
      item {
        if (readings.isNotEmpty()) {
          val bpms = readings.map { it.bpm }
          PhoneStatsSummary(
            min = bpms.min(),
            avg = bpms.average().toInt(),
            max = bpms.max(),
            count = readings.size,
            rangeLabel = selectedRange.label,
            colorFn = { hrColor(it) }
          )
        }
      }
    }
  }
}

@Composable
fun PhoneSparklineChart(
  values: List<Float>,
  timestamps: List<Long>,
  lineColor: Color,
  zoneBands: List<Pair<Pair<Float, Float>, Color>>,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (values.size < 2) return@Canvas
    val w = size.width
    val h = size.height

    val yMin = (values.min() - 10f).coerceAtLeast(0f)
    val yMax = values.max() + 10f
    val yRange = (yMax - yMin).coerceAtLeast(20f)

    // Zone bands
    for ((range, color) in zoneBands) {
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

    // Sparkline
    val path = Path()
    val xStep = w / (values.size - 1).toFloat()
    for (i in values.indices) {
      val x = i * xStep
      val y = h - ((values[i] - yMin) / yRange) * h
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, lineColor.copy(alpha = 0.8f), style = Stroke(3f, cap = StrokeCap.Round))

    // Dots
    for (i in values.indices) {
      val x = i * xStep
      val y = h - ((values[i] - yMin) / yRange) * h
      drawCircle(lineColor.copy(alpha = 0.4f), 2.5f, Offset(x, y))
    }

    // Latest dot
    val lastX = (values.size - 1) * xStep
    val lastY = h - ((values.last() - yMin) / yRange) * h
    drawCircle(lineColor, 5f, Offset(lastX, lastY))
  }
}

@Composable
fun EmptyChartPlaceholder(text: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(150.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFF1A1A1A)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color(0xFF555555),
      fontSize = 14.sp,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
fun PhoneStatsSummary(
  min: Int,
  avg: Int,
  max: Int,
  count: Int,
  rangeLabel: String,
  colorFn: (Int) -> Color
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        "$rangeLabel · $count readings",
        color = Color(0xFF888888),
        fontSize = 12.sp
      )
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
      ) {
        PhoneStatBadge("MIN", "$min", colorFn(min))
        PhoneStatBadge("AVG", "$avg", colorFn(avg))
        PhoneStatBadge("MAX", "$max", colorFn(max))
      }
    }
  }
}

@Composable
fun PhoneStatBadge(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Color(0xFF666666), fontSize = 10.sp)
    Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
  }
}
