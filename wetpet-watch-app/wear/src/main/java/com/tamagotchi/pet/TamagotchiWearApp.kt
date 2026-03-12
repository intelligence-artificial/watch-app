package com.tamagotchi.pet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

@Composable
fun WetPetWearApp(
  healthDataManager: HealthDataManager,
  startDestination: String = "home"
) {
  val context = LocalContext.current
  val petStateManager = remember { PetStateManager(context) }
  val petStatusEngine = remember { PetStatusEngine(context) }
  val navController = rememberSwipeDismissableNavController()

  MaterialTheme {
    SwipeDismissableNavHost(
      navController = navController,
      startDestination = startDestination
    ) {
      composable("home") {
        HomeScreen(
          petStateManager = petStateManager,
          healthDataManager = healthDataManager,
          petStatusEngine = petStatusEngine,
          onNavigateToCustomize = { navController.navigate("customize") },
          onNavigateToStats = { navController.navigate("stats") },
          onNavigateToHrChart = { navController.navigate("hr_chart") }
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
          petStatusEngine = petStatusEngine,
          onBack = { navController.popBackStack() }
        )
      }

      composable("hr_chart") {
        HrChartScreen(
          hrHistoryStore = healthDataManager.hrHistoryStore,
          currentBpm = healthDataManager.heartRate,
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}
