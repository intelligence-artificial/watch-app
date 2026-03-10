package com.tamagotchi.pet

/**
 * All 14 pet emotion states, ordered by priority (1 = highest).
 * Higher-priority emotions override lower ones when multiple conditions are true.
 */
enum class PetEmotion(
  val priority: Int,
  val bypassSmoother: Boolean,
  val line1: String,
  val line2: String,
  val arcColor: Long,
  val bgTint: Long
) {
  CRITICAL(1, true, "⚠ ALERT", "CHECK HEALTH", 0xFFFF4646, 0xFF1A0000),
  SICK(2, false, "feeling ill...", "rest up", 0xFFB0C0B0, 0xFF0A0F0A),
  EXHAUSTED(3, false, "so tired...", "HRV low", 0xFF808080, 0xFF0A0A0A),
  STRESSED(4, false, "stressed out", "take a breath", 0xFFFF8040, 0xFF120800),
  SLEEPY(5, false, "sleepy time", "Zzz...", 0xFF6060A0, 0xFF05050F),
  HUNGRY(6, false, "feed me!", "low calories", 0xFFFFAA50, 0xFF100800),
  SAD(7, false, "feeling down", "move around!", 0xFF5070A0, 0xFF050810),
  BORED(8, false, "bored...", "go for a walk", 0xFF808080, 0xFF080808),
  IDLE(9, false, "all good", "let's move!", 0xFF50FF78, 0xFF020206),
  CONTENT(10, false, "feeling good", "keep it up", 0xFF78FFA0, 0xFF020206),
  ACTIVE(11, true, "in the zone!", "cardio active", 0xFF00FFEE, 0xFF00080A),
  EXCITED(12, true, "LET'S GO!!", "max effort!", 0xFFFF50FF, 0xFF0A000A),
  HAPPY(13, false, "great day!", "keep moving!", 0xFF50E6FF, 0xFF020A0A),
  ECSTATIC(14, false, "GOAL SMASHED!", "you're amazing", 0xFFFFD700, 0xFF000A0A);

  companion object {
    fun fromOrdinal(index: Int): PetEmotion =
      entries.getOrElse(index) { IDLE }
  }
}
