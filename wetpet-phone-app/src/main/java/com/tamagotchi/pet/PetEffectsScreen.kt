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
fun PetEffectsScreen(
    fitnessRepo: FitnessRepository,
    petEffectsEngine: PetEffectsEngine
) {
    val todaySteps = remember { fitnessRepo.getTodaySteps() }
    val latestHR = remember { fitnessRepo.getLatestHeartRate() }
    val petState = remember { fitnessRepo.loadPetState() }

    val effects = remember { petEffectsEngine.calculateEffects(todaySteps, latestHR) }
    val summary = remember { petEffectsEngine.summarizeEffects(effects) }

    val themeColor = when (petState.colorTheme) {
        "BLUE" -> Color(0xFF64C8FF)
        "PINK" -> Color(0xFFFF8CC8)
        "YELLOW" -> Color(0xFFFFE664)
        else -> Color(0xFF78FFA0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header: How your fitness affects your pet
        Text(
            text = "How Fitness Affects ${petState.name}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = themeColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = themeColor.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Today's Impact",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ImpactStat(
                        label = "XP",
                        value = "+${summary.xpGained}",
                        color = themeColor
                    )
                    ImpactStat(
                        label = "Hunger",
                        value = "${summary.hungerChange}",
                        color = Color(0xFFFF8C50)
                    )
                    ImpactStat(
                        label = "Energy",
                        value = "${if (summary.energyChange >= 0) "+" else ""}${summary.energyChange}",
                        color = Color(0xFFFFE650)
                    )
                    ImpactStat(
                        label = "Happy",
                        value = "${if (summary.happinessChange >= 0) "+" else ""}${summary.happinessChange}",
                        color = Color(0xFFFF78B4)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Effect cards
        Text(
            text = "Activity Log",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        effects.forEach { effect ->
            EffectCard(effect = effect, themeColor = themeColor)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (effects.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "No activity data yet. Wear your watch and start moving!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fitness tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "How It Works",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                val tips = listOf(
                    "🚶 Walking → Reduces hunger, gains XP",
                    "💓 Elevated HR → Boosts energy",
                    "🏆 10k steps → Pet celebrates!",
                    "😴 No activity → Hunger increases, mood drops"
                )

                tips.forEach { tip ->
                    Text(
                        text = tip,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImpactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = color.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EffectCard(effect: PetEffectsEngine.EffectResult, themeColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = effect.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (effect.xpGained != 0) {
                    EffectChip(
                        text = "+${effect.xpGained} XP",
                        color = themeColor
                    )
                }
                if (effect.hungerChange != 0) {
                    EffectChip(
                        text = "${effect.hungerChange} Hunger",
                        color = Color(0xFFFF8C50)
                    )
                }
                if (effect.energyChange != 0) {
                    EffectChip(
                        text = "${if (effect.energyChange > 0) "+" else ""}${effect.energyChange} Energy",
                        color = Color(0xFFFFE650)
                    )
                }
                if (effect.happinessChange != 0) {
                    EffectChip(
                        text = "${if (effect.happinessChange > 0) "+" else ""}${effect.happinessChange} Happy",
                        color = Color(0xFFFF78B4)
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
