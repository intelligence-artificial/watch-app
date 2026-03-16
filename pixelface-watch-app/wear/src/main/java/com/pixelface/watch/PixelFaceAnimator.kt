package com.pixelface.watch

/**
 * Animation controller for the pixel pet face.
 *
 * Picks which face frames to show based on the current expression,
 * controls timing with ms-based frame durations, and handles blink cycles.
 *
 * Usage: call getCurrentFrame() every render tick — it returns the
 * 8×8 pixel array to draw inside the CRT monitor screen area.
 */
class PixelFaceAnimator {

  /** A single animation frame: pixel data + how long to display it */
  data class Frame(val pixels: Array<IntArray>, val durationMs: Long)

  private var frames: List<Frame> = emptyList()
  private var currentFrameIndex = 0
  private var lastFrameTimeMs = 0L
  private var currentExpression: FaceExpression? = null

  /**
   * Returns the current face frame pixels to draw.
   *
   * @param nowMs Current time in milliseconds (System.currentTimeMillis())
   * @param expression Current face expression (derived from health data)
   * @param hour Current hour of day (0-23) — used for sleep override
   * @return 8×8 IntArray grid of pixel values
   */
  fun getCurrentFrame(nowMs: Long, expression: FaceExpression, hour: Int): Array<IntArray> {
    // Rebuild frame sequence when expression changes
    if (expression != currentExpression) {
      currentExpression = expression
      frames = buildSequence(expression, hour)
      currentFrameIndex = 0
      lastFrameTimeMs = nowMs
    }

    if (frames.isEmpty()) return PixelFaceRenderer.FACE_IDLE

    val current = frames[currentFrameIndex]
    if (nowMs - lastFrameTimeMs >= current.durationMs) {
      currentFrameIndex = (currentFrameIndex + 1) % frames.size
      lastFrameTimeMs = nowMs
    }
    return frames[currentFrameIndex].pixels
  }

  /**
   * Build the animation frame sequence for a given expression.
   * Each expression has a different timing/rhythm.
   */
  private fun buildSequence(expression: FaceExpression, hour: Int): List<Frame> {
    // Always show sleep face at night
    if (hour >= 23 || hour <= 5) {
      return listOf(Frame(PixelFaceRenderer.FACE_SLEEP, 2000L))
    }

    return when (expression) {
      FaceExpression.NEUTRAL -> listOf(
        Frame(PixelFaceRenderer.FACE_IDLE,  2800L),  // open eyes
        Frame(PixelFaceRenderer.FACE_IDLE,  2800L),  // still open
        Frame(PixelFaceRenderer.FACE_BLINK,  120L),  // blink — very fast
        Frame(PixelFaceRenderer.FACE_IDLE,  2800L),  // open again
      )
      FaceExpression.HAPPY -> listOf(
        Frame(PixelFaceRenderer.FACE_HAPPY,  2000L),
        Frame(PixelFaceRenderer.FACE_EXCITED, 600L),
        Frame(PixelFaceRenderer.FACE_HAPPY,  2000L),
        Frame(PixelFaceRenderer.FACE_BLINK,   120L),
      )
      FaceExpression.CHILL -> listOf(
        Frame(PixelFaceRenderer.FACE_CHILL,  2500L),  // relaxed face
        Frame(PixelFaceRenderer.FACE_HAPPY,   800L),  // occasional happy
        Frame(PixelFaceRenderer.FACE_CHILL,  2500L),
        Frame(PixelFaceRenderer.FACE_BLINK,   120L),
      )
      FaceExpression.EXCITED -> listOf(
        Frame(PixelFaceRenderer.FACE_EXCITED, 200L),  // rapid switching
        Frame(PixelFaceRenderer.FACE_HAPPY,   200L),
      )
      FaceExpression.ACTIVE -> listOf(
        Frame(PixelFaceRenderer.FACE_HAPPY,   500L),
        Frame(PixelFaceRenderer.FACE_EXCITED, 300L),
        Frame(PixelFaceRenderer.FACE_HAPPY,   500L),
        Frame(PixelFaceRenderer.FACE_BLINK,   100L),
      )
      FaceExpression.SLEEPY -> listOf(
        Frame(PixelFaceRenderer.FACE_SLEEP,  2000L),
      )
      FaceExpression.ALERT -> listOf(
        Frame(PixelFaceRenderer.FACE_ALERT,  1000L),
        Frame(PixelFaceRenderer.FACE_BLINK,   120L),
        Frame(PixelFaceRenderer.FACE_ALERT,  1000L),
      )
    }
  }
}
