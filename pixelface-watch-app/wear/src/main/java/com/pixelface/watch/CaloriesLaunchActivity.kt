package com.pixelface.watch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * Trampoline for the Calories complication tap.
 * Opens MainActivity with navigate_to=cal_chart.
 */
class CaloriesLaunchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("CalLaunch", "Trampolining to calories chart")
    startActivity(Intent(this, MainActivity::class.java).apply {
      putExtra("navigate_to", "cal_chart")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    })
    finish()
  }
}
