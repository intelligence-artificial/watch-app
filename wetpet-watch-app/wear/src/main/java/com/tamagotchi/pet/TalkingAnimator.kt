package com.tamagotchi.pet

/**
 * Alternates between FACE_TALK_OPEN and FACE_TALK_CLOSED frames
 * to simulate a talking mouth. Used during:
 * - Gemini chat response display (text appearing character by character)
 * - Voice recording (mouth moves with audio amplitude)
 */
class TalkingAnimator {

  private var lastToggleMs = 0L
  private var mouthOpen = false

  /** Frame duration when talking — ~8fps mouth animation */
  private val frameDurationMs = 125L

  /**
   * Returns the current talking face frame.
   * Call every render tick while talking is active.
   *
   * @param nowMs Current time in ms
   * @param isTalking Whether the face should be animating its mouth
   * @param baseFrame The expression frame to use for eyes (mouth is overridden)
   * @return The face frame to draw
   */
  fun getCurrentFrame(nowMs: Long, isTalking: Boolean): Array<IntArray> {
    if (!isTalking) {
      mouthOpen = false
      return PixelPetRenderer.FACE_TALK_CLOSED
    }

    if (nowMs - lastToggleMs >= frameDurationMs) {
      mouthOpen = !mouthOpen
      lastToggleMs = nowMs
    }

    return if (mouthOpen) {
      PixelPetRenderer.FACE_TALK_OPEN
    } else {
      PixelPetRenderer.FACE_TALK_CLOSED
    }
  }

  /** Reset talking state */
  fun reset() {
    mouthOpen = false
    lastToggleMs = 0L
  }
}
