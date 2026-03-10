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

    val steps = remember(refreshTick) { healthDataManager.dailySteps }
    val hr = remember(refreshTick) { healthDataManager.heartRate }
    val hunger = remember(refreshTick) { petStateManager.hunger }
    val energy = remember(refreshTick) { petStateManager.energy }
    val happiness = remember(refreshTick) { petStateManager.happiness }
    val xp = remember(refreshTick) { petStateManager.xp }
    val level = remember(refreshTick) { petStateManager.level }
    val xpForNextLevel = level * 100

    val themeColor = when (petStateManager.colorTheme) {
        PetColorTheme.GREEN -> Color(0xFF78FFA0)
        PetColorTheme.BLUE -> Color(0xFF64C8FF)
        PetColorTheme.PINK -> Color(0xFFFF8CC8)
        PetColorTheme.YELLOW -> Color(0xFFFFE664)
    }

    ScalingLazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020206))
    ) {
        // Title
        item {
            Text(
                text = "Stats",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Fitness section
        item {
            Text(
                text = "FITNESS",
                color = Color(0xFF50E6FF).copy(alpha = 0.7f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            StatBar(
                label = "Steps",
                value = steps,
                maxValue = 10000,
                displayText = "$steps / 10,000",
                color = Color(0xFF50E6FF)
            )
        }

        item {
            StatBar(
                label = "Heart Rate",
                value = hr,
                maxValue = 200,
                displayText = if (hr > 0) "$hr bpm" else "-- bpm",
                color = Color(0xFFFF5050)
            )
        }

        // Pet section
        item {
            Text(
                text = "PET STATUS",
                color = themeColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            StatBar(
                label = "Level $level XP",
                value = xp,
                maxValue = xpForNextLevel,
                displayText = "$xp / $xpForNextLevel XP",
                color = themeColor
            )
        }

        item {
            StatBar(
                label = "Hunger",
                value = hunger,
                maxValue = 100,
                displayText = "$hunger%",
                color = Color(0xFFFF8C50)
            )
        }

        item {
            StatBar(
                label = "Energy",
                value = energy,
                maxValue = 100,
                displayText = "$energy%",
                color = Color(0xFFFFE650)
            )
        }

        item {
            StatBar(
                label = "Happiness",
                value = happiness,
                maxValue = 100,
                displayText = "$happiness%",
                color = Color(0xFFFF78B4)
            )
        }
    }
}

@Composable
private fun StatBar(
    label: String,
    value: Int,
    maxValue: Int,
    displayText: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Text(text = displayText, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
