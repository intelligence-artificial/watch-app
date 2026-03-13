package com.tamagotchi.pet

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

/**
 * Root Composable for the WetPet Wear app.
 *
 * SCREEN MAP:
 * ===========
 *   "home"      → HomeScreen      (pet display, main menu)
 *   "customize" → CustomizeScreen (pet appearance options)
 *   "stats"     → StatsScreen     (health data overview)
 *   "hr_chart"  → HrChartScreen   (heart rate chart detail)
 *
 * NAVIGATION RULES:
 * =================
 *   1. startDestination is ALWAYS "home" — it's the root
 *   2. Deep-link targets are pushed on top of "home" via navigate()
 *   3. Swiping back from any screen returns to its parent
 *   4. pendingNavTarget is consumed once, then set to null
 *
 * DEEP-LINK FLOW:
 * ===============
 *   pendingNavTarget="stats"    →  home (root) → stats
 *   pendingNavTarget="hr_chart" →  home (root) → stats → hr_chart
 *   pendingNavTarget=null       →  home (root) — stay put
 */
@Composable
fun WetPetWearApp(
  healthDataManager: HealthDataManager,
  pendingNavTarget: MutableState<String?>
) {
  val context = LocalContext.current
  val petStateManager = remember { PetStateManager(context) }
  val petStatusEngine = remember { PetStatusEngine(context) }
  val navController = rememberSwipeDismissableNavController()

  // React to deep-link navigation requests
  LaunchedEffect(Unit) {
    snapshotFlow { pendingNavTarget.value }
      .collect { target ->
        if (target != null) {
          Log.d("WetPetNav", "Deep-link navigate to: $target")

          // Clear backstack to home first (avoid stacking duplicates)
          navController.popBackStack("home", inclusive = false)

          when (target) {
            "home" -> {
              // Already at home after popBackStack — nothing more to do
            }
            "stats" -> {
              navController.navigate("stats")
            }
            "hr_chart" -> {
              navController.navigate("stats")
              navController.navigate("hr_chart")
            }
            "steps_chart" -> {
              navController.navigate("stats")
              navController.navigate("steps_chart")
            }
          }

          // Consume the event so it doesn't fire again
          pendingNavTarget.value = null
        }
      }
  }

  MaterialTheme {
    SwipeDismissableNavHost(
      navController = navController,
      startDestination = "home"  // ALWAYS "home" — never change this
    ) {
      // ── SCREEN: Home (root) ──
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

      // ── SCREEN: Customize ──
      composable("customize") {
        CustomizeScreen(
          petStateManager = petStateManager,
          onBack = { navController.popBackStack() }
        )
      }

      // ── SCREEN: Stats & Health ──
      composable("stats") {
        StatsScreen(
          petStateManager = petStateManager,
          healthDataManager = healthDataManager,
          petStatusEngine = petStatusEngine,
          onBack = { navController.popBackStack() },
          onNavigateToHrChart = { navController.navigate("hr_chart") }
        )
      }

      // ── SCREEN: HR Chart Detail ──
      composable("hr_chart") {
        HrChartScreen(
          hrHistoryStore = healthDataManager.hrHistoryStore,
          currentBpm = healthDataManager.heartRate,
          onBack = { navController.popBackStack() }
        )
      }

      // ── SCREEN: Steps Chart Detail ──
      composable("steps_chart") {
        StepsChartScreen(
          stepsHistoryStore = healthDataManager.stepsHistoryStore,
          currentSteps = healthDataManager.dailySteps,
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}
