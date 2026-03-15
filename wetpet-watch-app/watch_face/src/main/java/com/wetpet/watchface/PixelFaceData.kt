package com.wetpet.watchface

/**
 * Pixel art data — identical to PixelPetRenderer in the wear module.
 * Duplicated here because watch_face is a separate APK/module.
 *
 * Defines the 16×16 CRT monitor frame and 8×8 face expression frames.
 */
object PixelFaceData {

  const val GRID = 16
  const val FACE_GRID = 8
  private const val T = 0

  // Screen area offset for centering 8x8 face inside 16x16 monitor
  const val SCREEN_ROW_START = 3
  const val SCREEN_COL_START = 4

  val MONITOR_FRAME = arrayOf(
    intArrayOf(T,T,1,1,1,1,1,1,1,1,1,1,1,1,T,T),
    intArrayOf(T,1,2,2,2,2,2,2,2,2,2,2,2,2,1,T),
    intArrayOf(1,2,3,3,3,3,3,3,3,3,3,3,3,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,4,4,4,4,4,4,4,4,4,4,3,2,1),
    intArrayOf(1,2,3,3,3,3,3,3,3,3,3,3,3,3,2,1),
    intArrayOf(T,1,1,5,5,5,5,5,5,5,5,5,5,1,1,T),
    intArrayOf(T,T,1,5,5,1,T,T,T,T,1,5,5,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T,T,T,T,T,T,T,T,T),
  )

  // ── FACE FRAMES ──

  val FACE_IDLE = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  val FACE_BLINK = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  val FACE_HAPPY = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,1,1,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  val FACE_SLEEP = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  val FACE_ALERT = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  // ── Expression logic (same as FaceExpression in wear module) ──

  enum class Expression(val faceColor: Int, val label: String) {
    NEUTRAL(0xFF50E6FF.toInt(), "Neutral"),
    HAPPY(0xFF50FF78.toInt(), "Happy"),
    ACTIVE(0xFFFFB800.toInt(), "Active"),
    ALERT(0xFFFF3366.toInt(), "Alert"),
    SLEEPY(0xFF7B68EE.toInt(), "Sleepy"),
    EXCITED(0xFFFF9F50.toInt(), "Excited");

    companion object {
      fun fromHealth(hr: Int, steps: Int, calories: Int): Expression = when {
        hr > 130 -> ALERT
        hr > 100 && steps > 5000 -> ACTIVE
        steps > 10000 || calories > 2000 -> EXCITED
        steps > 5000 -> HAPPY
        hr in 1..55 -> SLEEPY
        else -> NEUTRAL
      }
    }
  }

  /** Get face frame for expression + blink timing */
  fun getFrame(expression: Expression, timeMs: Long, hour: Int): Array<IntArray> {
    // Sleep face at night (11pm–6am)
    if (hour in 23..23 || hour in 0..5) {
      return FACE_SLEEP
    }

    // Blink every ~3 seconds for 150ms
    val cyclePosition = timeMs % 3000L
    if (cyclePosition in 2850..3000) {
      return FACE_BLINK
    }

    return when (expression) {
      Expression.HAPPY -> FACE_HAPPY
      Expression.ALERT -> FACE_ALERT
      Expression.SLEEPY -> FACE_SLEEP
      Expression.ACTIVE -> FACE_HAPPY
      Expression.EXCITED -> FACE_HAPPY
      Expression.NEUTRAL -> FACE_IDLE
    }
  }
}
