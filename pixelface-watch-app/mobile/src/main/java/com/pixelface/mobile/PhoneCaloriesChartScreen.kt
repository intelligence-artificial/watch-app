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

private val CAL_ORANGE = Color(0xFFFF6B35)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneCaloriesChartScreen(
  db: HealthDatabase,
  onBack: () -> Unit
) {
  val numFmt = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
  var selectedRange by remember { mutableStateOf(PhoneTimeRange.DAY) }
  var refreshTick by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(30_000); refreshTick++ }
  }

  var readings by remember { mutableStateOf<List<CaloriesReading>>(emptyList()) }
  var latest by remember { mutableStateOf<CaloriesReading?>(null) }

  LaunchedEffect(selectedRange, refreshTick) {
    val cutoff = System.currentTimeMillis() - selectedRange.ms
    readings = db.caloriesDao().getAfter(cutoff)
    latest = db.caloriesDao().getLatest()
  }

  val currentCal = latest?.calories ?: 0

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("🔥 Calories", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0A0A0A),
          titleContentColor = CAL_ORANGE
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
      // ── Current Calories ──
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
                if (currentCal > 0) numFmt.format(currentCal) else "--",
                color = CAL_ORANGE,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(Modifier.width(6.dp))
              Text(
                "kcal",
                color = CAL_ORANGE.copy(alpha = 0.6f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
              )
            }
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
                selectedContainerColor = CAL_ORANGE.copy(alpha = 0.2f),
                selectedLabelColor = CAL_ORANGE
              )
            )
          }
        }
      }

      // ── Chart ──
      item {
        if (readings.size >= 2) {
          PhoneSparklineChart(
            values = readings.map { it.calories.toFloat() },
            timestamps = readings.map { it.timestampMs },
            lineColor = CAL_ORANGE,
            zoneBands = emptyList(),
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF1A1A1A))
              .padding(16.dp)
          )
        } else {
          EmptyChartPlaceholder("Collecting calories data...\n${readings.size} readings")
        }
      }

      // ── Stats ──
      item {
        if (readings.isNotEmpty()) {
          val vals = readings.map { it.calories }
          PhoneStatsSummary(
            min = vals.min(),
            avg = vals.average().toInt(),
            max = vals.max(),
            count = readings.size,
            rangeLabel = selectedRange.label,
            colorFn = { CAL_ORANGE }
          )
        }
      }
    }
  }
}
