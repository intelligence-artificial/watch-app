package com.tamagotchi.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    petStateManager: PetStateManager,
    healthDataManager: HealthDataManager,
    onNavigateToCustomize: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()

    // Refresh pet data periodically
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            refreshTick++
        }
    }

    // Read current state
    val petName = remember(refreshTick) { petStateManager.petName }
    val mood = remember(refreshTick) { petStateManager.mood }
    val level = remember(refreshTick) { petStateManager.level }
    val steps = remember(refreshTick) { healthDataManager.dailySteps }
    val theme = remember(refreshTick) { petStateManager.colorTheme }

    // Get sprite resource for current theme and mood
    val spriteRes = remember(theme, mood, refreshTick) {
        getSpriteResource(context, theme, mood, refreshTick)
    }

    val themeColor = when (theme) {
        PetColorTheme.GREEN -> Color(0xFF78FFA0)
        PetColorTheme.BLUE -> Color(0xFF64C8FF)
        PetColorTheme.PINK -> Color(0xFFFF8CC8)
        PetColorTheme.YELLOW -> Color(0xFFFFE664)
    }

    val moodEmoji = when (mood) {
        PetMood.HAPPY -> "♥ Happy"
        PetMood.CONTENT -> "● Content"
        PetMood.TIRED -> "◑ Tired"
        PetMood.HUNGRY -> "○ Hungry"
        PetMood.CELEBRATING -> "★ Celebrating!"
        PetMood.SLEEPING -> "☾ Sleeping"
    }

    ScalingLazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020206))
    ) {
        // Pet name
        item {
            Text(
                text = petName,
                color = themeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // Pet sprite
        item {
            if (spriteRes != 0) {
                Image(
                    painter = painterResource(id = spriteRes),
                    contentDescription = "Pet",
                    modifier = Modifier
                        .size(100.dp)
                        .padding(8.dp)
                )
            }
        }

        // Mood
        item {
            Text(
                text = moodEmoji,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // Quick stats
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                StatChip(label = "Lv", value = "$level", color = themeColor)
                StatChip(label = "Steps", value = "$steps", color = Color(0xFF50E6FF))
            }
        }

        // Customize button
        item {
            Chip(
                onClick = onNavigateToCustomize,
                label = { Text("Customize") },
                colors = ChipDefaults.chipColors(
                    backgroundColor = themeColor.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // Stats button
        item {
            Chip(
                onClick = onNavigateToStats,
                label = { Text("Stats & Health") },
                colors = ChipDefaults.chipColors(
                    backgroundColor = Color(0xFF50E6FF).copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

/**
 * Resolve sprite drawable resource ID based on theme, mood, and animation frame.
 */
private fun getSpriteResource(context: android.content.Context, theme: PetColorTheme, mood: PetMood, tick: Int): Int {
    val suffix = "_${theme.name.lowercase()}"
    val frame = if (tick % 2 == 0) "1" else "2"
    val name = when (mood) {
        PetMood.HAPPY -> "pet_happy_$frame$suffix"
        PetMood.CONTENT -> "pet_idle_$frame$suffix"
        PetMood.TIRED -> "pet_tired$suffix"
        PetMood.HUNGRY -> "pet_tired$suffix"
        PetMood.CELEBRATING -> "pet_celebrate_$frame$suffix"
        PetMood.SLEEPING -> "pet_sleep$suffix"
    }
    return context.resources.getIdentifier(name, "drawable", context.packageName)
}
