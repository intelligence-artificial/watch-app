package com.tamagotchi.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

@Composable
fun TamagotchiWearApp() {
    val context = LocalContext.current
    val petStateManager = remember { PetStateManager(context) }
    val healthDataManager = remember { HealthDataManager(context).also { it.start() } }
    val fitnessDataSender = remember { FitnessDataSender(context) }
    val navController = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    petStateManager = petStateManager,
                    healthDataManager = healthDataManager,
                    onNavigateToCustomize = { navController.navigate("customize") },
                    onNavigateToStats = { navController.navigate("stats") }
                )
            }

            composable("customize") {
                CustomizeScreen(
                    petStateManager = petStateManager,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("stats") {
                StatsScreen(
                    petStateManager = petStateManager,
                    healthDataManager = healthDataManager,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
