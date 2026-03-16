package com.pixelface.watch

/**
 * Shared constants for DataLayer communication between watch and phone.
 */
object DataLayerPaths {
  // Watch → Phone: fitness snapshot
  const val FITNESS_UPDATE_PATH = "/pixelface/fitness_update"
  const val KEY_STEPS = "steps"
  const val KEY_HEART_RATE = "heart_rate"
  const val KEY_CALORIES = "calories"
  const val KEY_FLOORS = "floors"
  const val KEY_DISTANCE = "distance"
  const val KEY_TIMESTAMP = "timestamp"

  // SharedPreferences name
  const val PREFS_NAME = "pixelface_state"
}
