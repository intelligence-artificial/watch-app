package com.pixelface.watch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/**
 * Single Activity for the PixelFace Wear app (Charts Only).
 *
 * Deep-link navigation uses a counter to ensure the LaunchedEffect
 * re-fires even when the same destination is requested twice.
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

  /** Target chart to navigate to ("hr_chart", "steps_chart", "cal_chart") */
  val pendingNavTarget = mutableStateOf<String?>(null)

  /** Incremented each time a new nav request arrives — ensures re-trigger */
  val navRequestId = mutableIntStateOf(0)

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val allGranted = grants.values.all { it }
    Log.d(TAG, "Permissions result: allGranted=$allGranted")
    if (allGranted) healthDataManager.start()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    healthDataManager = HealthDataManager(this)
    requestPermissionsAndStart()

    // Read initial deep-link target
    val target = resolveNavTarget(intent)
    if (target != null) {
      pendingNavTarget.value = target
      navRequestId.intValue++
    }
    Log.d(TAG, "onCreate: target=$target")

    setContent {
      PixelFaceWearApp(healthDataManager, pendingNavTarget, navRequestId)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val target = resolveNavTarget(intent)
    Log.d(TAG, "onNewIntent: navigate_to=${intent.getStringExtra("navigate_to")} → $target")
    if (target != null) {
      pendingNavTarget.value = target
      navRequestId.intValue++
    }
  }

  private fun resolveNavTarget(intent: Intent?): String? {
    return when (intent?.getStringExtra("navigate_to")) {
      "hr_chart" -> "hr_chart"
      "steps_chart" -> "steps_chart"
      "cal_chart" -> "cal_chart"
      else -> null
    }
  }

  private fun requestPermissionsAndStart() {
    val needed = REQUIRED_PERMISSIONS.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    if (needed.isEmpty()) {
      healthDataManager.start()
    } else {
      permissionLauncher.launch(needed.toTypedArray())
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    healthDataManager.stop()
  }
}
