package com.tamagotchi.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(fitnessRepo: FitnessRepository) {
    val petState = remember { fitnessRepo.loadPetState() }
    val todaySteps = remember { fitnessRepo.getTodaySteps() }
    val latestHR = remember { fitnessRepo.getLatestHeartRate() }

    val themeColor = when (petState.colorTheme) {
        "BLUE" -> Color(0xFF64C8FF)
        "PINK" -> Color(0xFFFF8CC8)
        "YELLOW" -> Color(0xFFFFE664)
        else -> Color(0xFF78FFA0)
    }

    val moodEmoji = when (petState.mood) {
        "HAPPY" -> "♥"
        "TIRED" -> "◑"
        "HUNGRY" -> "○"
        "CELEBRATING" -> "★"
        "SLEEPING" -> "☾"
        else -> "●"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pet Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Pet emoji/mood indicator (large)
                Text(
                    text = moodEmoji,
                    fontSize = 64.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pet name
                Text(
                    text = petState.name,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )

                // Level + mood
                Text(
                    text = "Level ${petState.level} · ${petState.mood.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // XP Progress bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("XP", fontSize = 12.sp, color = themeColor.copy(alpha = 0.7f))
                        Text(
                            "${petState.xp} / ${petState.level * 100}",
                            fontSize = 12.sp,
                            color = themeColor
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (petState.xp.toFloat() / (petState.level * 100)).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = themeColor,
                        trackColor = themeColor.copy(alpha = 0.15f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                label = "Steps Today",
                value = "$todaySteps",
                color = Color(0xFF50E6FF),
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                label = "Heart Rate",
                value = if (latestHR > 0) "$latestHR bpm" else "-- bpm",
                color = Color(0xFFFF5050),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pet Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStatCard(
                label = "Hunger",
                value = "${petState.hunger}%",
                color = Color(0xFFFF8C50),
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                label = "Energy",
                value = "${petState.energy}%",
                color = Color(0xFFFFE650),
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                label = "Happy",
                value = "${petState.happiness}%",
                color = Color(0xFFFF78B4),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Watch Connection",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (todaySteps > 0 || latestHR > 0) "● Synced" else "○ Waiting",
                    fontSize = 14.sp,
                    color = if (todaySteps > 0 || latestHR > 0) Color(0xFF50FF78) else Color(0xFFFF8C50)
                )
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
