package com.pixelface.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

/**
 * Root Composable for the PixelFace Wear app.
 * 3 chart routes with deep-link support from complication taps.
 */
@Composable
fun PixelFaceWearApp(
  healthDataManager: HealthDataManager,
  pendingNavTarget: MutableState<String?>,
  navRequestId: MutableIntState
) {
  val navController = rememberSwipeDismissableNavController()

  // React to deep-link requests — counter ensures re-trigger for same destination
  LaunchedEffect(navRequestId.intValue) {
    val target = pendingNavTarget.value
    if (target != null) {
      Log.d("PixelFaceNav", "Deep-link navigate to: $target (requestId=${navRequestId.intValue})")

      // Pop back to start, then navigate to the target
      navController.popBackStack("hr_chart", inclusive = false)
      when (target) {
        "hr_chart" -> { /* Already at start, just pop was enough */ }
        "steps_chart" -> navController.navigate("steps_chart")
        "cal_chart" -> navController.navigate("cal_chart")
      }
      pendingNavTarget.value = null
    }
  }

  MaterialTheme {
    SwipeDismissableNavHost(
      navController = navController,
      startDestination = "hr_chart"
    ) {
      composable("hr_chart") {
        HrChartScreen(
          hrHistoryStore = healthDataManager.hrHistoryStore,
          currentBpm = healthDataManager.heartRate,
          onBack = {},
          onNavigateToSteps = { navController.navigate("steps_chart") },
          onNavigateToCal = { navController.navigate("cal_chart") }
        )
      }

      composable("steps_chart") {
        StepsChartScreen(
          stepsHistoryStore = healthDataManager.stepsHistoryStore,
          currentSteps = healthDataManager.dailySteps,
          onBack = { navController.popBackStack() },
          onNavigateToHr = { navController.navigate("hr_chart") { popUpTo("hr_chart") { inclusive = true } } },
          onNavigateToCal = { navController.navigate("cal_chart") }
        )
      }

      composable("cal_chart") {
        CaloriesChartScreen(
          caloriesHistoryStore = healthDataManager.caloriesHistoryStore,
          currentCalories = healthDataManager.calories,
          onBack = { navController.popBackStack() },
          onNavigateToHr = { navController.navigate("hr_chart") { popUpTo("hr_chart") { inclusive = true } } },
          onNavigateToSteps = { navController.navigate("steps_chart") }
        )
      }
    }
  }
}
