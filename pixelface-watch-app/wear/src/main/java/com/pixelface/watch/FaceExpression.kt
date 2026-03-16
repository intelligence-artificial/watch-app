package com.pixelface.watch

import java.util.Calendar

/**
 * Pixel face expressions — driven by health data.
 * Bot reacts to how healthy/active the user is being today.
 */
enum class FaceExpression(
  val label: String,
  val statusLine: String,
  val color: Long
) {
  // Positive states
  HAPPY("great day!", "keep moving!", 0xFF50FF78),
  EXCITED("LET'S GO!!", "max effort!", 0xFFFF50FF),
  ACTIVE("in the zone", "cardio active", 0xFF00FFEE),
  CHILL("feeling good", "keep it up", 0xFF78FFA0),
  NEUTRAL("all good", "monitoring...", 0xFF50E6FF),

  // Negative / concern states
  SAD("get moving!", "low activity", 0xFF6080A0),
  WORRIED("check health", "hr concern", 0xFFA06060),
  TIRED("need rest", "overdoing it", 0xFF8070A0),

  // Time-based
  SLEEPY("zzz...", "rest mode", 0xFF6060A0),

  // Critical
  ALERT("⚠ heads up", "check stats", 0xFFFF4646);

  companion object {
    /**
     * Resolve expression from raw health data.
     * Positive health = happy face, poor metrics = unhappy face.
     */
    fun fromHealth(
      heartRate: Int,
      dailySteps: Int,
      calories: Int
    ): FaceExpression {
      val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

      // ── CRITICAL: dangerously high HR at rest ──
      if (heartRate > 180 && dailySteps < 10) return ALERT

      // ── SLEEP HOURS (10pm - 5am) ──
      if (hour in 22..23 || hour in 0..5) return SLEEPY

      // ── HEART RATE CONCERNS ──
      // Very high resting HR (not exercising)
      if (heartRate > 120 && dailySteps < 500) return WORRIED
      // Very low HR (potential concern)
      if (heartRate in 1..44) return WORRIED

      // ── ACTIVE WORKOUT ──
      if (heartRate >= 140) return EXCITED
      if (heartRate in 100..139) return ACTIVE

      // ── STEP & CALORIE BASED (time-of-day aware) ──
      val stepsRatio = dailySteps / 10000f
      val calRatio = calories / 2000f

      // Great day — smashed goals
      if (stepsRatio > 0.85f && calRatio > 0.6f) return HAPPY
      if (stepsRatio > 0.85f) return HAPPY

      // Good progress — on track
      if (stepsRatio > 0.5f || calRatio > 0.5f) return CHILL

      // ── BEHIND SCHEDULE (afternoon/evening checks) ──
      if (hour >= 18) {
        // Evening and barely moved = sad
        if (stepsRatio < 0.3f) return SAD
        if (calRatio < 0.3f) return SAD
      }

      if (hour >= 14) {
        // Afternoon and very low activity = worried
        if (stepsRatio < 0.2f && calRatio < 0.2f) return WORRIED
        // Afternoon with some activity but behind
        if (stepsRatio < 0.35f) return TIRED
      }

      if (hour >= 10) {
        // Late morning, barely started
        if (stepsRatio < 0.1f && calRatio < 0.1f) return TIRED
      }

      return NEUTRAL
    }
  }
}
