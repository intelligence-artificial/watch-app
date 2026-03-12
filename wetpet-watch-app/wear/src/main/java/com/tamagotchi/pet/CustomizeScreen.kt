package com.tamagotchi.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
  var selectedPetType by remember { mutableStateOf(petStateManager.petType) }

  val themeColor = when (selectedTheme) {
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
        text = "✦ Customize",
        color = themeColor,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 6.dp)
      )
    }

    // Pet type section
    item {
      Text(
        text = "PET TYPE",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    }

    item {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp, horizontal = 16.dp)
      ) {
        val petTypes = listOf(
          Triple(PetType.BLOB, "🐾", "Blob"),
          Triple(PetType.CAT, "🐱", "Cat"),
          Triple(PetType.DOG, "🐶", "Dog")
        )
        petTypes.forEach { (type, emoji, label) ->
          val isSelected = selectedPetType == type
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(
                if (isSelected) themeColor.copy(alpha = 0.20f)
                else Color.White.copy(alpha = 0.04f)
              )
              .then(
                if (isSelected) Modifier.border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                else Modifier
              )
              .clickable {
                selectedPetType = type
                petStateManager.petType = type
              }
              .padding(horizontal = 14.dp, vertical = 8.dp)
          ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(2.dp))
            Text(
              label,
              color = if (isSelected) themeColor else Color.White.copy(alpha = 0.45f),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          }
        }
      }
    }

    // Color theme section
    item {
      Text(
        text = "THEME COLOR",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp)
      )
    }

    item {
      Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp, horizontal = 16.dp)
      ) {
        ColorCircle(
          color = Color(0xFF78FFA0),
          label = "GRN",
          isSelected = selectedTheme == PetColorTheme.GREEN,
          onClick = {
            selectedTheme = PetColorTheme.GREEN
            petStateManager.colorTheme = PetColorTheme.GREEN
          }
        )
        ColorCircle(
          color = Color(0xFF64C8FF),
          label = "BLU",
          isSelected = selectedTheme == PetColorTheme.BLUE,
          onClick = {
            selectedTheme = PetColorTheme.BLUE
            petStateManager.colorTheme = PetColorTheme.BLUE
          }
        )
        ColorCircle(
          color = Color(0xFFFF8CC8),
          label = "PNK",
          isSelected = selectedTheme == PetColorTheme.PINK,
          onClick = {
            selectedTheme = PetColorTheme.PINK
            petStateManager.colorTheme = PetColorTheme.PINK
          }
        )
        ColorCircle(
          color = Color(0xFFFFE664),
          label = "YLW",
          isSelected = selectedTheme == PetColorTheme.YELLOW,
          onClick = {
            selectedTheme = PetColorTheme.YELLOW
            petStateManager.colorTheme = PetColorTheme.YELLOW
          }
        )
      }
    }

    // Pet name section
    item {
      Text(
        text = "PET NAME",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 4.dp)
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
              .padding(vertical = 2.dp, horizontal = 12.dp)
          ) {
            row.forEach { name ->
              val isSelected = petName == name
              CompactChip(
                onClick = {
                  petName = name
                  petStateManager.petName = name
                },
                label = {
                  Text(
                    text = name,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.Black else Color.White
                  )
                },
                colors = ChipDefaults.chipColors(
                  backgroundColor = if (isSelected) themeColor else themeColor.copy(alpha = 0.12f)
                )
              )
            }
          }
        }
      }
    }

    // Save button
    item {
      Chip(
        onClick = {
          scope.launch {
            petStateManager.requestComplicationUpdates()
            petStateManager.syncToPhone()
          }
          onBack()
        },
        label = {
          Text(
            "✓ Save & Sync",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
          )
        },
        colors = ChipDefaults.chipColors(
          backgroundColor = themeColor.copy(alpha = 0.85f)
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
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
        .size(34.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = if (isSelected) 1f else 0.6f))
        .then(
          if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
          else Modifier.border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
        )
        .clickable { onClick() }
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
      text = label,
      color = if (isSelected) color else Color.White.copy(alpha = 0.4f),
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      textAlign = TextAlign.Center
    )
  }
}
