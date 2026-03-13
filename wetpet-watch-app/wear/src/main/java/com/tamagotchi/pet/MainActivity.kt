package com.tamagotchi.pet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/**
 * Single Activity for the WetPet Wear app.
 *
 * NAVIGATION ARCHITECTURE:
 * ========================
 * - The nav graph ALWAYS starts at "home" (root).
 * - Deep-links from complications pass a "navigate_to" extra.
 * - The extra is stored in [pendingNavTarget] (a Compose state).
 * - WetPetWearApp reads it ONCE via LaunchedEffect, navigates forward,
 *   then clears it. This keeps "home" in the backstack so swipe-back works.
 *
 * INTENT ROUTING:
 * ===============
 * - navigate_to = "home"     → stay on home (default)
 * - navigate_to = "stats"    → home → stats
 * - navigate_to = "hr_chart" → home → stats → hr_chart
 * - (no extra / null)        → WFF Launch fallback → stats
 *
 * setContent is called EXACTLY ONCE in onCreate.
 * onNewIntent updates [pendingNavTarget] which triggers recomposition.
 */
class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(
      Manifest.permission.BODY_SENSORS,
      Manifest.permission.ACTIVITY_RECOGNITION
    )
  }

  private lateinit var healthDataManager: HealthDataManager

  /** Compose-observable state: the screen to navigate to (consumed once). */
  val pendingNavTarget = mutableStateOf<String?>(null)

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val allGranted = grants.values.all { it }
    Log.d(TAG, "Permissions result: allGranted=$allGranted")
    if (allGranted) {
      healthDataManager.start()
    } else {
      Log.w(TAG, "Health permissions denied — data will not be collected")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    healthDataManager = HealthDataManager(this)
    requestPermissionsAndStart()

    // Read initial deep-link target
    pendingNavTarget.value = resolveNavTarget(intent)
    Log.d(TAG, "onCreate: pendingNavTarget=${pendingNavTarget.value}")

    // setContent is called EXACTLY ONCE — never again
    setContent {
      WetPetWearApp(healthDataManager, pendingNavTarget)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val target = resolveNavTarget(intent)
    Log.d(TAG, "onNewIntent: navigate_to=${intent.getStringExtra("navigate_to")} → target=$target")
    // Update the observable state — triggers LaunchedEffect in WetPetWearApp
    pendingNavTarget.value = target
  }

  /**
   * Resolve intent extras to a nav target.
   *
   * When opened from:
   * - HR complication tapAction   → navigate_to="hr_chart" → "hr_chart"
   * - Pet complication tapAction  → navigate_to="home"     → "home"
   * - WFF Launch (no extras)      → navigate_to=null       → "hr_chart"
   */
  private fun resolveNavTarget(intent: Intent?): String? {
    return when (intent?.getStringExtra("navigate_to")) {
      "home" -> "home"
      "stats" -> "stats"
      "hr_chart" -> "hr_chart"
      "steps_chart" -> "steps_chart"
      "cal_chart" -> "cal_chart"
      else -> "home"       // WFF Launch fallback (BPM tap): go to HR chart
    }
  }

  private fun requestPermissionsAndStart() {
    val needed = REQUIRED_PERMISSIONS.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (needed.isEmpty()) {
      Log.d(TAG, "All health permissions already granted")
      healthDataManager.start()
    } else {
      Log.d(TAG, "Requesting permissions: $needed")
      permissionLauncher.launch(needed.toTypedArray())
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    healthDataManager.stop()
  }
}
