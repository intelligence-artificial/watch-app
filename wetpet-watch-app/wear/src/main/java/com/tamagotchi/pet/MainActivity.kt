package com.tamagotchi.pet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

  companion object {
    private const val TAG = "MainActivity"
    private val REQUIRED_PERMISSIONS = arrayOf(
      Manifest.permission.BODY_SENSORS,
      Manifest.permission.ACTIVITY_RECOGNITION
    )
  }

  private lateinit var healthDataManager: HealthDataManager

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val allGranted = grants.values.all { it }
    Log.d(TAG, "Permissions result: allGranted=$allGranted, details=$grants")
    if (allGranted) {
      healthDataManager.start()
    } else {
      Log.w(TAG, "Health permissions denied — data will not be collected")
      // Still start to show the UI, but data will remain at defaults
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    healthDataManager = HealthDataManager(this)
    requestPermissionsAndStart()

    setContent {
      WetPetWearApp(healthDataManager)
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
