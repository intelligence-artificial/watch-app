package com.wetpet.watchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import java.time.ZonedDateTime

/**
 * PixelFace Watch Face — Canvas-based with FULL animation parity to the app.
 *
 * Effects ported from HomeScreen.kt:
 * 1. Bob animation (sinusoidal vertical bounce, speed varies by expression)
 * 2. TV power-on when wrist raised (ambient → interactive transition)
 * 3. CRT scanlines (moving bright bar + static dim lines)
 * 4. Pulsing phosphor glow ring
 * 5. Blink cycle (every ~3s for 150ms)
 * 6. Expression glitch on health data change (brief static noise)
 * 7. Steps arc ring with progress
 * 8. Same color palette and face frames
 *
 * Battery strategy:
 * - INTERACTIVE: 10fps, all effects
 * - AMBIENT: static sleep face, no effects, grayscale
 */
class PixelFaceWatchFaceService : WatchFaceService() {

  override fun createUserStyleSchema(): UserStyleSchema = UserStyleSchema(emptyList())

  override suspend fun createWatchFace(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository
  ): WatchFace {
    val renderer = PixelFaceRenderer(
      context = applicationContext,
      surfaceHolder = surfaceHolder,
      watchState = watchState,
      currentUserStyleRepository = currentUserStyleRepository
    )
    return WatchFace(WatchFaceType.DIGITAL, renderer)
  }
}

class PixelFaceRenderer(
  private val context: Context,
  surfaceHolder: SurfaceHolder,
  watchState: WatchState,
  currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<Renderer.SharedAssets>(
  surfaceHolder = surfaceHolder,
  currentUserStyleRepository = currentUserStyleRepository,
  watchState = watchState,
  canvasType = CanvasType.HARDWARE,
  interactiveDrawModeUpdateDelayMillis = 100L,  // 10fps
  clearWithBackgroundTintBeforeRenderingHighlightLayer = true
) {

  class Assets : SharedAssets { override fun onDestroy() {} }
  override suspend fun createSharedAssets(): SharedAssets = Assets()

  // ── TV Power-On State ──
  private var tvProgress = 0f          // 0 = off, 1 = fully on
  private var tvTurnOnStartMs = 0L     // when power-on started
  private var wasAmbient = true        // track ambient → interactive transition
  private val TV_ON_DURATION_MS = 400f // 400ms power-on sequence

  // ── Expression Glitch State ──
  private var lastExpression: PixelFaceData.Expression? = null
  private var glitchStartMs = 0L
  private var isGlitching = false
  private val GLITCH_DURATION_MS = 200f

  // ── Reusable Paints ──
  private val bgPaint = Paint().apply { color = Color.rgb(2, 2, 6); style = Paint.Style.FILL }
  private val pixelPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
  private val screenBgPaint = Paint().apply { color = Color.rgb(8, 8, 14); style = Paint.Style.FILL; isAntiAlias = false }
  private val monitorPaints = mapOf(
    1 to Paint().apply { color = Color.rgb(30, 30, 40); style = Paint.Style.FILL; isAntiAlias = false },
    2 to Paint().apply { color = Color.rgb(60, 60, 80); style = Paint.Style.FILL; isAntiAlias = false },
    3 to Paint().apply { color = Color.rgb(100, 100, 130); style = Paint.Style.FILL; isAntiAlias = false },
    5 to Paint().apply { color = Color.rgb(50, 50, 65); style = Paint.Style.FILL; isAntiAlias = false }
  )
  private val textPaint = Paint().apply {
    isAntiAlias = true; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
  }
  private val arcPaint = Paint().apply {
    style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
  }

  override fun renderHighlightLayer(
    canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets
  ) {}

  override fun render(
    canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: SharedAssets
  ) {
    val w = bounds.width().toFloat()
    val h = bounds.height().toFloat()
    val cx = w / 2f
    val cy = h / 2f
    val nowMs = System.currentTimeMillis()
    val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

    // ── Background ──
    canvas.drawRect(0f, 0f, w, h, bgPaint)

    // ── Read health data ──
    val prefs = context.getSharedPreferences("wetpet_state", Context.MODE_PRIVATE)
    val hr = prefs.getInt("heart_rate", 0)
    val steps = prefs.getInt("daily_steps", 0)
    val calories = prefs.getInt("calories", 0)
    val hour = zonedDateTime.hour

    val expression = PixelFaceData.Expression.fromHealth(hr, steps, calories)
    val faceColor = if (isAmbient) Color.rgb(100, 100, 100) else expression.faceColor

    // ══════════════════════════════════════
    // AMBIENT MODE — static, no animation
    // ══════════════════════════════════════
    if (isAmbient) {
      wasAmbient = true
      tvProgress = 0f
      drawMonitor(canvas, cx, cy - h * 0.06f, w * 0.50f, PixelFaceData.FACE_SLEEP, Color.rgb(100, 100, 100), 1f)
      // Time only
      textPaint.textSize = w * 0.11f
      textPaint.color = Color.WHITE
      val timeY = cy + w * 0.25f + h * 0.04f
      val timeStr = String.format("%02d:%02d", if (hour % 12 == 0) 12 else hour % 12, zonedDateTime.minute)
      canvas.drawText(timeStr, cx, timeY, textPaint)
      // Date
      textPaint.textSize = w * 0.035f
      textPaint.color = Color.rgb(60, 60, 60)
      val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
      canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, h - h * 0.08f, textPaint)
      return
    }

    // ══════════════════════════════════════
    // INTERACTIVE MODE — full animation
    // ══════════════════════════════════════

    // ── 1. TV POWER-ON (ambient → interactive transition) ──
    if (wasAmbient) {
      wasAmbient = false
      tvTurnOnStartMs = nowMs
      tvProgress = 0f
    }

    if (tvProgress < 1f) {
      val elapsed = (nowMs - tvTurnOnStartMs).toFloat()
      tvProgress = (elapsed / TV_ON_DURATION_MS).coerceIn(0f, 1f)
    }

    // ── 2. EXPRESSION GLITCH (on health data change) ──
    if (lastExpression != null && lastExpression != expression && !isGlitching) {
      isGlitching = true
      glitchStartMs = nowMs
    }
    lastExpression = expression

    var showStatic = false
    var effectiveTvProgress = tvProgress

    if (isGlitching) {
      val glitchElapsed = (nowMs - glitchStartMs).toFloat()
      if (glitchElapsed >= GLITCH_DURATION_MS) {
        isGlitching = false
      } else {
        val glitchPhase = glitchElapsed / GLITCH_DURATION_MS
        if (glitchPhase < 0.4f) {
          // Collapse
          effectiveTvProgress = 1f - (glitchPhase / 0.4f) * 0.9f
          showStatic = true
        } else {
          // Expand back
          effectiveTvProgress = 0.1f + ((glitchPhase - 0.4f) / 0.6f) * 0.9f
          showStatic = glitchPhase < 0.6f
        }
      }
    }

    // ── 3. BOB ANIMATION ──
    val bobPeriodMs = when (expression) {
      PixelFaceData.Expression.EXCITED -> 400.0
      PixelFaceData.Expression.ACTIVE -> 600.0
      PixelFaceData.Expression.SLEEPY -> 2000.0
      else -> 800.0
    }
    val bobAmplitude = when (expression) {
      PixelFaceData.Expression.EXCITED -> 8f
      PixelFaceData.Expression.ACTIVE -> 5f
      PixelFaceData.Expression.SLEEPY -> 1f
      else -> 3f
    }
    val bobPhase = (nowMs % bobPeriodMs.toLong()) / bobPeriodMs
    val bobOffset = (Math.sin(bobPhase * Math.PI * 2) * bobAmplitude).toFloat()

    // ── 4. PULSING GLOW ──
    val glowPhase = (nowMs % 2000L) / 2000.0
    val glowAlpha = (0.08f + 0.12f * Math.sin(glowPhase * Math.PI * 2).toFloat()).coerceIn(0.04f, 0.22f)

    // ── 5. FACE FRAME (blink + expression) ──
    val faceFrame = if (showStatic) {
      // Random static noise during glitch
      Array(PixelFaceData.FACE_GRID) { IntArray(PixelFaceData.FACE_GRID) { if (Math.random() > 0.5) 1 else 0 } }
    } else {
      PixelFaceData.getFrame(expression, nowMs, hour)
    }

    // ── 6. STEPS ARC RING ──
    val stepsProgress = (steps / 10000f).coerceIn(0f, 1f)
    val arcInset = 12f
    val arcRect = RectF(arcInset, arcInset, w - arcInset, h - arcInset)

    // Background arc
    arcPaint.strokeWidth = 6f
    arcPaint.color = Color.argb(18, 80, 230, 255)
    canvas.drawArc(arcRect, -225f, 270f, false, arcPaint)

    // Progress arc
    arcPaint.color = Color.rgb(80, 230, 255)
    canvas.drawArc(arcRect, -225f, 270f * stepsProgress, false, arcPaint)

    // Quarter marks
    arcPaint.strokeWidth = 10f
    arcPaint.color = Color.argb(37, 255, 255, 255)
    for (pct in listOf(0.25f, 0.5f, 0.75f)) {
      canvas.drawArc(arcRect, -225f + 270f * pct - 1f, 2f, false, arcPaint)
    }

    // Battery arc (inner)
    val batteryPct = try {
      val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
      (bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50) / 100f
    } catch (_: Exception) { 0.5f }

    val innerInset = arcInset + 16f
    val innerRect = RectF(innerInset, innerInset, w - innerInset, h - innerInset)
    arcPaint.strokeWidth = 3.5f
    arcPaint.color = Color.argb(14, 80, 255, 120)
    canvas.drawArc(innerRect, -135f, 90f, false, arcPaint)
    arcPaint.color = if (batteryPct < 0.3f) Color.rgb(255, 70, 70) else Color.rgb(80, 255, 120)
    canvas.drawArc(innerRect, -135f, 90f * batteryPct, false, arcPaint)

    // ── 7. PULSING GLOW RING ──
    val glowInset = innerInset + 12f
    val glowRect = RectF(glowInset, glowInset, w - glowInset, h - glowInset)
    arcPaint.strokeWidth = 2.5f
    arcPaint.color = Color.argb(
      (glowAlpha * 255).toInt().coerceIn(10, 55),
      (faceColor shr 16) and 0xFF,
      (faceColor shr 8) and 0xFF,
      faceColor and 0xFF
    )
    canvas.drawArc(glowRect, 0f, 360f, false, arcPaint)

    // ── 8. DRAW CRT MONITOR + FACE (with bob + TV progress) ──
    val monitorSize = w * 0.50f
    val monitorCenterY = cy - h * 0.06f + bobOffset

    drawMonitor(canvas, cx, monitorCenterY, monitorSize, faceFrame, faceColor, effectiveTvProgress)

    // ── 9. CRT SCANLINES ──
    if (effectiveTvProgress > 0.3f) {
      val pixelSize = monitorSize / PixelFaceData.GRID
      val monitorLeft = cx - monitorSize / 2f
      val monitorTop = monitorCenterY - monitorSize / 2f
      val screenStartX = monitorLeft + 3 * pixelSize
      val screenStartY = monitorTop + PixelFaceData.SCREEN_ROW_START * pixelSize
      val screenWidth = 10 * pixelSize
      val screenHeight = 9 * pixelSize

      // Moving bright bar
      val scanProgress = (nowMs % 3000L) / 3000f
      val barY = screenStartY + scanProgress * screenHeight
      pixelPaint.color = Color.argb(15, (faceColor shr 16) and 0xFF, (faceColor shr 8) and 0xFF, faceColor and 0xFF)
      canvas.drawRect(screenStartX, barY, screenStartX + screenWidth, barY + pixelSize * 0.3f, pixelPaint)

      // Static dim scanlines
      pixelPaint.color = Color.argb(30, 0, 0, 0)
      var y = screenStartY
      while (y < screenStartY + screenHeight) {
        canvas.drawRect(screenStartX, y, screenStartX + screenWidth, y + pixelSize * 0.15f, pixelPaint)
        y += pixelSize * 0.5f
      }

      // Phosphor glow edges
      val glowStrength = (10 * effectiveTvProgress).toInt().coerceIn(4, 12)
      pixelPaint.color = Color.argb(glowStrength, (faceColor shr 16) and 0xFF, (faceColor shr 8) and 0xFF, faceColor and 0xFF)
      // Top glow
      canvas.drawRect(screenStartX, screenStartY - pixelSize * 0.5f, screenStartX + screenWidth, screenStartY, pixelPaint)
      // Bottom glow
      canvas.drawRect(screenStartX, screenStartY + screenHeight, screenStartX + screenWidth, screenStartY + screenHeight + pixelSize * 0.5f, pixelPaint)
    }

    // ── 10. EXPRESSION LABEL ──
    val labelY = monitorCenterY + monitorSize / 2f + h * 0.02f
    textPaint.textSize = w * 0.035f
    textPaint.color = Color.argb(115, (faceColor shr 16) and 0xFF, (faceColor shr 8) and 0xFF, faceColor and 0xFF)
    canvas.drawText(expression.label, cx, labelY, textPaint)

    // ── 11. DIGITAL TIME ──
    val timeY = labelY + h * 0.06f
    textPaint.textSize = w * 0.11f
    textPaint.color = faceColor
    val hourStr = String.format("%02d", if (hour % 12 == 0) 12 else hour % 12)
    val minStr = String.format("%02d", zonedDateTime.minute)
    val colonBlink = if (nowMs % 1000 < 500) ":" else " "
    canvas.drawText("$hourStr$colonBlink$minStr", cx, timeY, textPaint)

    // AM/PM
    textPaint.textSize = w * 0.03f
    textPaint.color = Color.argb(100, (faceColor shr 16) and 0xFF, (faceColor shr 8) and 0xFF, faceColor and 0xFF)
    canvas.drawText(if (hour < 12) "AM" else "PM", cx + w * 0.15f, timeY, textPaint)

    // ── 12. HEALTH STATS ──
    val statsY = timeY + h * 0.05f
    textPaint.textSize = w * 0.028f

    textPaint.color = Color.rgb(80, 230, 255)
    canvas.drawText("👟 $steps", cx - w * 0.18f, statsY, textPaint)

    textPaint.color = Color.rgb(255, 100, 100)
    canvas.drawText("♥ ${if (hr > 0) hr else "--"}", cx, statsY, textPaint)

    textPaint.color = Color.rgb(255, 159, 80)
    canvas.drawText("🔥 $calories", cx + w * 0.18f, statsY, textPaint)

    // ── 13. DATE ──
    textPaint.textSize = w * 0.035f
    textPaint.color = Color.argb(80, (faceColor shr 16) and 0xFF, (faceColor shr 8) and 0xFF, faceColor and 0xFF)
    val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
    canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, h - h * 0.06f, textPaint)
  }

  /**
   * Draw the full CRT monitor with face, handling TV power-on/off progress.
   */
  private fun drawMonitor(
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    size: Float,
    faceFrame: Array<IntArray>,
    faceColor: Int,
    tvProgress: Float
  ) {
    val pixelSize = size / PixelFaceData.GRID
    val left = centerX - size / 2f
    val top = centerY - size / 2f

    // ── Monitor frame (always visible) ──
    PixelFaceData.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, idx ->
        if (idx == 0) return@forEachIndexed
        val paint = if (idx == 4) screenBgPaint else (monitorPaints[idx] ?: return@forEachIndexed)
        canvas.drawRect(
          left + col * pixelSize,
          top + row * pixelSize,
          left + (col + 1) * pixelSize,
          top + (row + 1) * pixelSize,
          paint
        )
      }
    }

    // ── TV power line effect (during power-on, progress < 1) ──
    if (tvProgress < 1f && tvProgress > 0f) {
      val screenStartY = top + PixelFaceData.SCREEN_ROW_START * pixelSize
      val screenHeight = 9 * pixelSize
      val screenCenterY = screenStartY + screenHeight / 2f
      val visibleHeight = screenHeight * tvProgress
      val lineY = screenCenterY - visibleHeight / 2f
      val screenStartX = left + PixelFaceData.SCREEN_COL_START * pixelSize - pixelSize

      pixelPaint.color = Color.argb(
        (204 * tvProgress).toInt().coerceIn(50, 204),
        (faceColor shr 16) and 0xFF,
        (faceColor shr 8) and 0xFF,
        faceColor and 0xFF
      )
      canvas.drawRect(
        screenStartX, lineY,
        screenStartX + 10 * pixelSize,
        lineY + visibleHeight.coerceAtLeast(pixelSize * 0.3f),
        pixelPaint
      )
    }

    // ── Face (only when TV is mostly on) ──
    if (tvProgress > 0.5f) {
      val faceAlpha = ((tvProgress - 0.5f) * 2f * 255).toInt().coerceIn(0, 255)
      val faceOffsetX = left + PixelFaceData.SCREEN_COL_START * pixelSize
      val faceOffsetY = top + PixelFaceData.SCREEN_ROW_START * pixelSize

      pixelPaint.color = Color.argb(
        faceAlpha,
        (faceColor shr 16) and 0xFF,
        (faceColor shr 8) and 0xFF,
        faceColor and 0xFF
      )

      faceFrame.forEachIndexed { row, cols ->
        cols.forEachIndexed { col, px ->
          if (px == 0) return@forEachIndexed
          canvas.drawRect(
            faceOffsetX + col * pixelSize,
            faceOffsetY + row * pixelSize,
            faceOffsetX + (col + 1) * pixelSize,
            faceOffsetY + (row + 1) * pixelSize,
            pixelPaint
          )
        }
      }
    }
  }
}
