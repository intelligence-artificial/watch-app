package com.tamagotchi.pet

/**
 * Shared constants for DataLayer communication between watch and phone.
 */
object DataLayerPaths {
  // Watch → Phone: fitness snapshot
  const val FITNESS_UPDATE_PATH = "/wetpet/fitness_update"
  const val KEY_STEPS = "steps"
  const val KEY_HEART_RATE = "heart_rate"
  const val KEY_CALORIES = "calories"
  const val KEY_FLOORS = "floors"
  const val KEY_DISTANCE = "distance"
  const val KEY_TIMESTAMP = "timestamp"

  // Watch → Phone: pet state sync
  const val PET_STATE_PATH = "/wetpet/pet_state"
  const val KEY_PET_NAME = "pet_name"
  const val KEY_COLOR_THEME = "color_theme"
  const val KEY_HUNGER = "hunger"
  const val KEY_ENERGY = "energy"
  const val KEY_HAPPINESS = "happiness"
  const val KEY_HEALTH = "health"
  const val KEY_STRESS = "stress"
  const val KEY_XP = "xp"
  const val KEY_LEVEL = "level"
  const val KEY_MOOD = "mood"
  const val KEY_EMOTION = "emotion"

  // Phone → Watch: pet effects results
  const val PET_EFFECTS_PATH = "/wetpet/pet_effects"
  const val KEY_XP_GAINED = "xp_gained"
  const val KEY_HUNGER_CHANGE = "hunger_change"
  const val KEY_ENERGY_CHANGE = "energy_change"
  const val KEY_HAPPINESS_CHANGE = "happiness_change"

  // SharedPreferences name
  const val PREFS_NAME = "wetpet_state"
}

enum class PetMood {
  HAPPY, CONTENT, TIRED, HUNGRY, CELEBRATING, SLEEPING
}

enum class PetColorTheme {
  GREEN, BLUE, PINK, YELLOW
}

enum class PetType {
  BLOB, CAT, DOG
}
