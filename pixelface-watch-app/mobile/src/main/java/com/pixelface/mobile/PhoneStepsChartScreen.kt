package com.pixelface.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.Alignment

private val STEPS_LOW = Color(0xFF4488FF)
private val STEPS_LIGHT = Color(0xFF00D68F)
private val STEPS_MODERATE = Color(0xFFFFB800)
private val STEPS_ACTIVE = Color(0xFFFF6B35)
private val STEPS_HIGH = Color(0xFFFF3366)

private fun stepsColor(steps: Int): Color = when {
  steps < 2000 -> STEPS_LOW
  steps < 5000 -> STEPS_LIGHT
  steps < 8000 -> STEPS_MODERATE
  steps < 10000 -> STEPS_ACTIVE
  else -> STEPS_HIGH
}

private fun stepsZone(steps: Int): String = when {
  steps < 2000 -> "Sedentary"
  steps < 5000 -> "Light"
  steps < 8000 -> "Moderate"
  steps < 10000 -> "Active"
  else -> "Very Active"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneStepsChartScreen(
  db: HealthDatabase,
  onBack: () -> Unit
) {
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
  var selectedRange by remember { mutableStateOf(PhoneTimeRange.DAY) }
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(30_000); refreshTick++ }
  }

  var readings by remember { mutableStateOf<List<StepsReading>>(emptyList()) }
  var latest by remember { mutableStateOf<StepsReading?>(null) }

  LaunchedEffect(selectedRange, refreshTick) {
    val cutoff = System.currentTimeMillis() - selectedRange.ms
    readings = db.stepsDao().getAfter(cutoff)
    latest = db.stepsDao().getLatest()
  }

  val currentSteps = latest?.steps ?: 0
  val color = stepsColor(currentSteps)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("👟 Steps", fontWeight = FontWeight.Bold) },
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
      // ── Current Steps ──
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
                if (currentSteps > 0) numFmt.format(currentSteps) else "--",
                color = color,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "steps",
                color = color.copy(alpha = 0.6f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
              )
            }
            Text(
              stepsZone(currentSteps),
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
            values = readings.map { it.steps.toFloat() },
            timestamps = readings.map { it.timestampMs },
            lineColor = color,
            zoneBands = listOf(
              0f to 2000f to STEPS_LOW,
              2000f to 5000f to STEPS_LIGHT,
              5000f to 8000f to STEPS_MODERATE,
              8000f to 10000f to STEPS_ACTIVE,
              10000f to 20000f to STEPS_HIGH
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF1A1A1A))
              .padding(16.dp)
          )
        } else {
          EmptyChartPlaceholder("Collecting steps data...\n${readings.size} readings")
        }
      }

      // ── Stats ──
      item {
        if (readings.isNotEmpty()) {
          val vals = readings.map { it.steps }
          PhoneStatsSummary(
            min = vals.min(),
            avg = vals.average().toInt(),
            max = vals.max(),
            count = readings.size,
            rangeLabel = selectedRange.label,
            colorFn = { stepsColor(it) }
          )
        }
      }
    }
  }
}
