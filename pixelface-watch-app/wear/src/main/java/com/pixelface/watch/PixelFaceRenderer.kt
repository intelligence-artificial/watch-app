package com.pixelface.watch

/**
 * Pixel art renderer — defines the CRT monitor frame (16×16) and
 * all face expression frames (8×8) as integer arrays.
 *
 * Architecture:
 * - MONITOR_FRAME is a 16×16 grid using palette indices (0-4)
 * - Face frames are 8×8 grids drawn inside the monitor screen area
 * - At render time, palette indices → actual colors from the theme
 * - Zero PNGs, zero file I/O — just canvas.drawRect() calls
 */
object PixelFaceRenderer {

  /** Logical grid size — all frames defined in 16×16 pixel space */
  const val GRID = 16

  /** Face grid size — faces are 8×8, drawn inside the screen area */
  const val FACE_GRID = 8

  /** Transparent pixel constant */
  private const val T = 0

  // ── MONITOR FRAME ──
  // Palette indices:
  //   0 = transparent
  //   1 = DARK (monitor body shadow/outline)
  //   2 = MID (monitor body)
  //   3 = LIGHT (monitor bezel highlight)
  //   4 = SCREEN (screen background — changes per theme)
  //   5 = STAND (monitor stand)
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
    intArrayOf(T,1,1,5,5,5,5,5,5,5,5,5,5,1,1,T),  // stand neck
    intArrayOf(T,T,1,5,5,1,T,T,T,T,1,5,5,1,T,T),  // stand feet
    intArrayOf(T,T,T,T,T,T,T,T,T,T,T,T,T,T,T,T),
  )

  // Screen area: rows 3-11, cols 3-12 (10×9 area)
  // Face is drawn as an 8×8 grid centered in the screen, offset at (col 4, row 3)
  const val SCREEN_ROW_START = 3
  const val SCREEN_COL_START = 4  // center the 8-wide face in the 10-wide screen

  // ── FACE FRAMES (8×8 grids) ──
  // 0 = transparent, 1 = face pixel (drawn in theme accent color)

  /** Neutral idle face — eyes open, small smile */
  val FACE_IDLE = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // eyes open
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),  // small smile
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Blink frame — eyes closed, same smile */
  val FACE_BLINK = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),  // closed eyes (lines)
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Happy face — squint eyes, big smile */
  val FACE_HAPPY = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),  // ^ ^ happy squint
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // big smile curve
    intArrayOf(T,T,T,1,1,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Hungry/sad face — open eyes, frown */
  val FACE_HUNGRY = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,1,1,T,T,T),  // sad frown
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Sleep face — closed eyes, flat mouth (for AOD / nighttime) */
  val FACE_SLEEP = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),  // - - closed eyes
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),  // flat mouth
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Excited face — wide bright eyes, huge open grin */
  val FACE_EXCITED = arrayOf(
    intArrayOf(T,T,1,T,T,1,T,T),  // eyebrows raised
    intArrayOf(T,1,1,T,T,1,1,T),  // wide open eyes
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,T,T,T,T,1,T),  // big open grin
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,1,1,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Chill face — relaxed happy squint, gentle smile */
  val FACE_CHILL = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),  // relaxed squint eyes
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // gentle curve smile
    intArrayOf(T,T,T,1,1,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Alert face — wide eyes, O mouth */
  val FACE_ALERT = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,1,1,T,T,1,1,T),  // wide eyes
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),  // O mouth
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  // ── TALKING FRAMES ──
  // Used during chat response / voice recording — alternates open/closed

  /** Talk open — mouth wide open */
  val FACE_TALK_OPEN = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // eyes open
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),  // open mouth top
    intArrayOf(T,T,1,T,T,1,T,T),  // open mouth sides
    intArrayOf(T,T,1,1,1,1,T,T),  // open mouth bottom
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Talk closed — mouth shut */
  val FACE_TALK_CLOSED = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // eyes open
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),  // closed mouth line
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  // ── UNHAPPY / HEALTH-AWARE FRAMES ──

  /** Worried face — angled eyebrows, small frown */
  val FACE_WORRIED = arrayOf(
    intArrayOf(T,1,T,T,T,T,1,T),  // worried eyebrows /  \
    intArrayOf(T,T,1,T,T,1,T,T),  // eyes
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,1,1,T,T,T),  // small frown
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Tired face — droopy half-closed eyes, flat mouth */
  val FACE_TIRED = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // half-closed droopy eyes
    intArrayOf(T,1,1,T,T,1,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,1,1,1,T,T),  // flat tired mouth
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  /** Sad face — sad eyes, deep frown */
  val FACE_SAD = arrayOf(
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // sad eyes
    intArrayOf(T,T,1,T,T,1,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,T,T,T,T,T,T),
    intArrayOf(T,T,1,T,T,1,T,T),  // deep frown
    intArrayOf(T,1,T,T,T,T,1,T),
    intArrayOf(T,T,T,T,T,T,T,T),
  )

  // ── TV STATIC FRAME ──
  // Shown during TV on/off transitions — random noise pattern

  /** Random static noise for TV transitions */
  fun generateStaticFrame(): Array<IntArray> {
    return Array(FACE_GRID) { IntArray(FACE_GRID) { if (Math.random() > 0.5) 1 else 0 } }
  }
}
