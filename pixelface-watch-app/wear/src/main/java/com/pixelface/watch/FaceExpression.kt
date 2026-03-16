package com.pixelface.watch

import java.util.Calendar

/**
 * Pixel face expressions — driven by health data instead of pet emotions.
 * Each expression defines how the pixel eyes and mouth should render.
 */
enum class FaceExpression(
  val label: String,
  val statusLine: String,
  val color: Long
) {
  NEUTRAL("all good", "monitoring...", 0xFF50E6FF),
  HAPPY("great day!", "keep moving!", 0xFF50FF78),
  EXCITED("LET'S GO!!", "max effort!", 0xFFFF50FF),
  SLEEPY("zzz...", "rest mode", 0xFF6060A0),
  ALERT("⚠ heads up", "check stats", 0xFFFF4646),
  ACTIVE("in the zone", "cardio active", 0xFF00FFEE),
  CHILL("feeling good", "keep it up", 0xFF78FFA0);

  companion object {
    /**
     * Resolve expression from raw health data — no pet needs, just direct mapping.
     */
    fun fromHealth(
      heartRate: Int,
      dailySteps: Int,
      calories: Int
    ): FaceExpression {
      val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

      // Critical: high HR at rest
      if (heartRate > 180 && dailySteps < 10) return ALERT

      // Sleep hours
      if (hour in 22..23 || hour in 0..5) return SLEEPY

      // Active workout
      if (heartRate >= 140) return EXCITED
      if (heartRate in 100..139) return ACTIVE

      // Achievement: good step count
      val stepsRatio = dailySteps / 10000f
      if (stepsRatio > 0.85f) return HAPPY

      // Moderate activity
      if (stepsRatio > 0.5f || calories > 1000) return CHILL

      return NEUTRAL
    }
  }
}
