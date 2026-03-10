package com.tamagotchi.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.launch

@Composable
fun CustomizeScreen(
    petStateManager: PetStateManager,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

    var selectedTheme by remember { mutableStateOf(petStateManager.colorTheme) }
    var petName by remember { mutableStateOf(petStateManager.petName) }

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
                text = "Customize",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Color theme picker
        item {
            Text(
                text = "Pet Color",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ColorCircle(
                    color = Color(0xFF78FFA0),
                    label = "Green",
                    isSelected = selectedTheme == PetColorTheme.GREEN,
                    onClick = {
                        selectedTheme = PetColorTheme.GREEN
                        petStateManager.colorTheme = PetColorTheme.GREEN
                        petStateManager.saveMood()
                    }
                )
                ColorCircle(
                    color = Color(0xFF64C8FF),
                    label = "Blue",
                    isSelected = selectedTheme == PetColorTheme.BLUE,
                    onClick = {
                        selectedTheme = PetColorTheme.BLUE
                        petStateManager.colorTheme = PetColorTheme.BLUE
                        petStateManager.saveMood()
                    }
                )
                ColorCircle(
                    color = Color(0xFFFF8CC8),
                    label = "Pink",
                    isSelected = selectedTheme == PetColorTheme.PINK,
                    onClick = {
                        selectedTheme = PetColorTheme.PINK
                        petStateManager.colorTheme = PetColorTheme.PINK
                        petStateManager.saveMood()
                    }
                )
                ColorCircle(
                    color = Color(0xFFFFE664),
                    label = "Yellow",
                    isSelected = selectedTheme == PetColorTheme.YELLOW,
                    onClick = {
                        selectedTheme = PetColorTheme.YELLOW
                        petStateManager.colorTheme = PetColorTheme.YELLOW
                        petStateManager.saveMood()
                    }
                )
            }
        }

        // Pet name
        item {
            Text(
                text = "Pet Name",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Name preset chips
        item {
            val presetNames = listOf("Tamago", "Pixel", "Mochi", "Nori", "Tofu", "Dango")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                presetNames.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        row.forEach { name ->
                            val isSelected = petName == name
                            val themeColor = when (selectedTheme) {
                                PetColorTheme.GREEN -> Color(0xFF78FFA0)
                                PetColorTheme.BLUE -> Color(0xFF64C8FF)
                                PetColorTheme.PINK -> Color(0xFFFF8CC8)
                                PetColorTheme.YELLOW -> Color(0xFFFFE664)
                            }
                            CompactChip(
                                onClick = {
                                    petName = name
                                    petStateManager.petName = name
                                },
                                label = {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = if (isSelected) themeColor else themeColor.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Sync button
        item {
            Chip(
                onClick = {
                    scope.launch {
                        petStateManager.syncToPhone()
                    }
                    onBack()
                },
                label = { Text("Save & Sync") },
                colors = ChipDefaults.chipColors(
                    backgroundColor = Color(0xFF50FF78).copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
                .clickable { onClick() }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) color else Color.White.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}
