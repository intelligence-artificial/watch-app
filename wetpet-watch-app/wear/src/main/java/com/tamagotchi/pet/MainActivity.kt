package com.tamagotchi.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

  private lateinit var healthDataManager: HealthDataManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    healthDataManager = HealthDataManager(this)
    healthDataManager.start()

    setContent {
      WetPetWearApp(healthDataManager)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    healthDataManager.stop()
  }
}
