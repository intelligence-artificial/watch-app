package com.tamagotchi.pet

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * Transparent trampoline Activity used by the HR complication's
 * WFF <Launch> overlay. Opens MainActivity with navigate_to=stats.
 *
 * This allows WFF <Launch target="com.wetpet.watch"> to go directly
 * to the stats screen, since WFF Launch cannot pass intent extras.
 */
class StatsLaunchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("StatsLaunch", "Trampolining to stats screen")
    startActivity(Intent(this, MainActivity::class.java).apply {
      putExtra("navigate_to", "stats")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    })
    finish()
  }
}
