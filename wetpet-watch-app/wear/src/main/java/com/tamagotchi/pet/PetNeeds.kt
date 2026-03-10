package com.tamagotchi.pet

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * 6-dimensional pet needs + XP system.
 * Persisted to SharedPreferences, resets daily at midnight.
 */
data class PetNeeds(
  var hunger: Float = 1.0f,
  var energy: Float = 1.0f,
  var happiness: Float = 0.5f,
  var health: Float = 1.0f,
  var social: Float = 0.5f,
  var stress: Float = 0.0f,
  var xpTotal: Int = 0,
  var level: Int = 1,
  var lastStepMilestone: Int = 0,
  var lastSavedTimestamp: Long = System.currentTimeMillis()
) {
  companion object {
    private const val PREFS_NAME = "wetpet_needs"
    private const val XP_PER_LEVEL = 100

    fun load(context: Context): PetNeeds {
      val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      val needs = PetNeeds(
        hunger = prefs.getFloat("hunger", 1.0f),
        energy = prefs.getFloat("energy", 1.0f),
        happiness = prefs.getFloat("happiness", 0.5f),
        health = prefs.getFloat("health", 1.0f),
        social = prefs.getFloat("social", 0.5f),
        stress = prefs.getFloat("stress", 0.0f),
        xpTotal = prefs.getInt("xp_total", 0),
        level = prefs.getInt("level", 1),
        lastStepMilestone = prefs.getInt("last_step_milestone", 0),
        lastSavedTimestamp = prefs.getLong("last_saved", System.currentTimeMillis())
      )

      // Check if we need a daily reset (new day)
      val lastDay = Calendar.getInstance().apply {
        timeInMillis = needs.lastSavedTimestamp
      }.get(Calendar.DAY_OF_YEAR)
      val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

      if (lastDay != today) {
        needs.hunger = 1.0f
        needs.happiness = 0.5f
        needs.social = 0.5f
        needs.stress = 0.0f
        needs.lastStepMilestone = 0
        // XP and level persist forever
      }

      return needs
    }

    fun save(context: Context, needs: PetNeeds) {
      context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putFloat("hunger", needs.hunger)
        .putFloat("energy", needs.energy)
        .putFloat("happiness", needs.happiness)
        .putFloat("health", needs.health)
        .putFloat("social", needs.social)
        .putFloat("stress", needs.stress)
        .putInt("xp_total", needs.xpTotal)
        .putInt("level", needs.level)
        .putInt("last_step_milestone", needs.lastStepMilestone)
        .putLong("last_saved", System.currentTimeMillis())
        .apply()
    }
  }

  /** Award XP and auto-level */
  fun awardXp(amount: Int) {
    xpTotal += amount
    while (xpTotal >= level * XP_PER_LEVEL) {
      xpTotal -= level * XP_PER_LEVEL
      level++
    }
  }

  /** Clamp all values to valid range */
  fun clamp() {
    hunger = hunger.coerceIn(0f, 1f)
    energy = energy.coerceIn(0f, 1f)
    happiness = happiness.coerceIn(0f, 1f)
    health = health.coerceIn(0f, 1f)
    social = social.coerceIn(0f, 1f)
    stress = stress.coerceIn(0f, 1f)
  }
}
