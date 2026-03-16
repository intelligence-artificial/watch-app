package com.pixelface.watch

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
 * Root Composable for the PixelFace Wear app.
 *
 * SCREEN MAP:
 * ===========
 *   "home"        → HomeScreen             (pixel face + health summary)
 *   "stats"       → StatsScreen            (health data overview)
 *   "hr_chart"    → HrChartScreen          (heart rate chart)
 *   "steps_chart" → StepsChartScreen       (steps chart)
 *   "cal_chart"   → CaloriesChartScreen    (calories chart)
 *   "record"      → VoiceNoteScreen        (voice recording)
 *   "recordings"  → RecordingsListScreen   (recordings list)
 *   "chat"        → ChatScreen             (chat with pixel face)
 */
@Composable
fun PixelFaceWearApp(
  healthDataManager: HealthDataManager,
  pendingNavTarget: MutableState<String?>
) {
  val context = LocalContext.current
  val navController = rememberSwipeDismissableNavController()

  // Services for voice recording
  val recordingsDir = remember { context.filesDir }
  val recorderService = remember { AudioRecorderService(context, recordingsDir) }
  val playerService = remember { AudioPlayerService() }
  val dataLayerSender = remember { DataLayerSender(context) }

  // React to deep-link navigation requests
  LaunchedEffect(Unit) {
    snapshotFlow { pendingNavTarget.value }
      .collect { target ->
        if (target != null) {
          Log.d("PixelFaceNav", "Deep-link navigate to: $target")

          navController.popBackStack("home", inclusive = false)

          when (target) {
            "home" -> { /* Already at home */ }
            "stats" -> navController.navigate("stats")
            "hr_chart" -> {
              navController.navigate("stats")
              navController.navigate("hr_chart")
            }
            "steps_chart" -> {
              navController.navigate("stats")
              navController.navigate("steps_chart")
            }
            "cal_chart" -> {
              navController.navigate("stats")
              navController.navigate("cal_chart")
            }
            "record" -> navController.navigate("record")
            "chat" -> navController.navigate("chat")
          }

          pendingNavTarget.value = null
        }
      }
  }

  MaterialTheme {
    SwipeDismissableNavHost(
      navController = navController,
      startDestination = "home"
    ) {
      // ── Home ──
      composable("home") {
        HomeScreen(
          healthDataManager = healthDataManager,
          onNavigateToStats = { navController.navigate("stats") },
          onNavigateToHrChart = { navController.navigate("hr_chart") },
          onNavigateToRecord = { navController.navigate("record") },
          onNavigateToChat = { navController.navigate("chat") }
        )
      }

      // ── Stats & Health ──
      composable("stats") {
        StatsScreen(
          healthDataManager = healthDataManager,
          onBack = { navController.popBackStack() },
          onNavigateToHrChart = { navController.navigate("hr_chart") },
          onNavigateToStepsChart = { navController.navigate("steps_chart") },
          onNavigateToCalChart = { navController.navigate("cal_chart") }
        )
      }

      // ── HR Chart ──
      composable("hr_chart") {
        HrChartScreen(
          hrHistoryStore = healthDataManager.hrHistoryStore,
          currentBpm = healthDataManager.heartRate,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Steps Chart ──
      composable("steps_chart") {
        StepsChartScreen(
          stepsHistoryStore = healthDataManager.stepsHistoryStore,
          currentSteps = healthDataManager.dailySteps,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Calories Chart ──
      composable("cal_chart") {
        CaloriesChartScreen(
          caloriesHistoryStore = healthDataManager.caloriesHistoryStore,
          currentCalories = healthDataManager.calories,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Voice Recording ──
      composable("record") {
        VoiceNoteScreen(
          recorderService = recorderService,
          dataLayerSender = dataLayerSender,
          onNavigateToRecordings = { navController.navigate("recordings") },
          onBack = { navController.popBackStack() }
        )
      }

      // ── Recordings List ──
      composable("recordings") {
        RecordingsListScreen(
          playerService = playerService,
          onBack = { navController.popBackStack() }
        )
      }

      // ── Chat ──
      composable("chat") {
        ChatScreen(
          onBack = { navController.popBackStack() }
        )
      }
    }
  }
}
