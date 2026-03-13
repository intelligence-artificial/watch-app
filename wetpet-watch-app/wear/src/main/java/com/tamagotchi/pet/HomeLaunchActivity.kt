package com.tamagotchi.pet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * Transparent trampoline for the Pet complication tap.
 *
 * WFF <Launch> can only specify a package name, so both Pet and BPM
 * Launch overlays would send identical intents to MainActivity.
 * This activity is registered as MAIN/LAUNCHER with a separate
 * taskAffinity so WFF can target it via the package name.
 *
 * It immediately forwards to MainActivity with navigate_to=home.
 */
class HomeLaunchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("HomeLaunch", "Trampolining to home screen")
    startActivity(Intent(this, MainActivity::class.java).apply {
      putExtra("navigate_to", "home")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    })
    finish()
  }
}
