package com.pixelface.watch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import java.time.ZonedDateTime

/**
 * PixelFace Watch Face — Canvas-based, lives in the SAME APK as the app.
 *
 * Uses FaceExpression + PixelFaceRenderer + PixelFaceAnimator directly
 * (no duplicate data). Reads SharedPreferences written by HomeScreen
 * since it's the same package.
 *
 * Animation strategy:
 * - INTERACTIVE: 10fps, all effects (bob, TV-on, glitch, scanlines, glow)
 * - AMBIENT: static sleep face, grayscale, zero animation
 */
class PixelFaceWatchFaceService : WatchFaceService() {

  companion object {
    private const val TAG = "PixelFaceWF"
  }

  override fun createUserStyleSchema(): UserStyleSchema {
    // Empty schema — customization is handled by WatchFaceConfigActivity
    // using SharedPreferences instead of the system's broken editor UI
    return UserStyleSchema(emptyList())
  }

  override suspend fun createWatchFace(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository
  ): WatchFace {
    val renderer = PixelFaceCanvasRenderer(
      context = applicationContext,
      surfaceHolder = surfaceHolder,
      watchState = watchState,
      currentUserStyleRepository = currentUserStyleRepository
    )
    return WatchFace(WatchFaceType.DIGITAL, renderer)
  }
}

class PixelFaceCanvasRenderer(
  private val context: Context,
  surfaceHolder: SurfaceHolder,
  watchState: WatchState,
  private val currentUserStyleRepository: CurrentUserStyleRepository
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

  // Use the SAME animator as the app
  private val animator = PixelFaceAnimator()

  // ── TV Power-On State ──
  private var tvProgress = 0f
  private var tvTurnOnStartMs = 0L
  private var wasAmbient = true
  private val TV_ON_DURATION_MS = 400f

  // ── Expression Glitch State ──
  private var lastExpression: FaceExpression? = null
  private var glitchStartMs = 0L
  private var isGlitching = false
  private val GLITCH_DURATION_MS = 200f

  // ── Paints ──
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
  
  // ── Analog Hand Paints (Now Dot Ring Paints) ──
  private val dotActivePaint = Paint().apply {
    style = Paint.Style.FILL; isAntiAlias = true
  }
  private val dotDimPaint = Paint().apply {
    style = Paint.Style.FILL; isAntiAlias = true
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

    canvas.drawRect(0f, 0f, w, h, bgPaint)

    // ── Read health data from SHARED SharedPreferences ──
    val prefs = context.getSharedPreferences("pixelface_state", Context.MODE_PRIVATE)
    val hr = prefs.getInt("heart_rate", 0)
    val steps = prefs.getInt("daily_steps", 0)
    val calories = prefs.getInt("calories", 0)
    val hour = zonedDateTime.hour

    // Use the SAME FaceExpression logic as the app
    val expression = FaceExpression.fromHealth(hr, steps, calories)
    val faceColorLong = expression.color
    val faceColor = Color.rgb(
      ((faceColorLong shr 16) and 0xFF).toInt(),
      ((faceColorLong shr 8) and 0xFF).toInt(),
      (faceColorLong and 0xFF).toInt()
    )

    // ══════════════════════════════════════
    // AMBIENT — static, no animation
    // ══════════════════════════════════════
    if (isAmbient) {
      wasAmbient = true
      tvProgress = 0f
      // Draw dim clock ring in ambient mode
      drawDualArcClock(canvas, w, h, hour, zonedDateTime.minute, true)
      
      textPaint.textSize = w * 0.11f
      textPaint.color = Color.WHITE
      val timeStr = String.format("%02d:%02d", if (hour % 12 == 0) 12 else hour % 12, zonedDateTime.minute)
      canvas.drawText(timeStr, cx, cy + w * 0.25f + h * 0.04f, textPaint)
      textPaint.textSize = w * 0.035f
      textPaint.color = Color.rgb(60, 60, 60)
      val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
      canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, h - h * 0.08f, textPaint)
      return
    }

    // ══════════════════════════════════════
    // INTERACTIVE — full animation
    // ══════════════════════════════════════

    // ── TV POWER-ON ──
    if (wasAmbient) {
      wasAmbient = false
      tvTurnOnStartMs = nowMs
      tvProgress = 0f
    }
    if (tvProgress < 1f) {
      tvProgress = ((nowMs - tvTurnOnStartMs) / TV_ON_DURATION_MS).coerceIn(0f, 1f)
    }

    // ── EXPRESSION GLITCH ──
    if (lastExpression != null && lastExpression != expression && !isGlitching) {
      isGlitching = true
      glitchStartMs = nowMs
    }
    lastExpression = expression

    var showStatic = false
    var effectiveTvProgress = tvProgress

    if (isGlitching) {
      val elapsed = (nowMs - glitchStartMs) / GLITCH_DURATION_MS
      if (elapsed >= 1f) {
        isGlitching = false
      } else if (elapsed < 0.4f) {
        effectiveTvProgress = 1f - (elapsed / 0.4f) * 0.9f
        showStatic = true
      } else {
        effectiveTvProgress = 0.1f + ((elapsed - 0.4f) / 0.6f) * 0.9f
        showStatic = elapsed < 0.6f
      }
    }

    // ── BOB ANIMATION ──
    val bobPeriod = when (expression) {
      FaceExpression.EXCITED -> 400.0
      FaceExpression.ACTIVE -> 600.0
      FaceExpression.SLEEPY -> 2000.0
      else -> 800.0
    }
    val bobAmp = when (expression) {
      FaceExpression.EXCITED -> 8f
      FaceExpression.ACTIVE -> 5f
      FaceExpression.SLEEPY -> 1f
      else -> 3f
    }
    val bobOffset = (Math.sin((nowMs % bobPeriod.toLong()) / bobPeriod * Math.PI * 2) * bobAmp).toFloat()

    // ── FACE FRAME — use the SAME animator as the app ──
    val faceFrame = if (showStatic) {
      PixelFaceRenderer.generateStaticFrame()
    } else {
      animator.getCurrentFrame(nowMs, expression, hour)
    }

    // ── CLOCK RINGS (Minutes & Hours) ──
    drawDualArcClock(canvas, w, h, hour, zonedDateTime.minute, false)

    // ── CRT MONITOR + FACE ──
    val monitorSize = w * 0.50f
    val monitorCenterY = cy - h * 0.06f + bobOffset
    drawMonitor(canvas, cx, monitorCenterY, monitorSize, faceFrame, faceColor, effectiveTvProgress)

    // ── CRT SCANLINES ──
    if (effectiveTvProgress > 0.3f) {
      val pxSz = monitorSize / PixelFaceRenderer.GRID
      val mLeft = cx - monitorSize / 2f
      val mTop = monitorCenterY - monitorSize / 2f
      val sX = mLeft + 3 * pxSz
      val sY = mTop + PixelFaceRenderer.SCREEN_ROW_START * pxSz
      val sW = 10 * pxSz
      val sH = 9 * pxSz

      // Moving bar
      val barY = sY + ((nowMs % 3000L) / 3000f) * sH
      pixelPaint.color = Color.argb(15, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
      canvas.drawRect(sX, barY, sX + sW, barY + pxSz * 0.3f, pixelPaint)

      // Static lines
      pixelPaint.color = Color.argb(30, 0, 0, 0)
      var y = sY
      while (y < sY + sH) {
        canvas.drawRect(sX, y, sX + sW, y + pxSz * 0.15f, pixelPaint)
        y += pxSz * 0.5f
      }

      // Phosphor glow
      val gs = (10 * effectiveTvProgress).toInt().coerceIn(4, 12)
      pixelPaint.color = Color.argb(gs, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
      canvas.drawRect(sX, sY - pxSz * 0.5f, sX + sW, sY, pixelPaint)
      canvas.drawRect(sX, sY + sH, sX + sW, sY + sH + pxSz * 0.5f, pixelPaint)
    }



    // ── EXPRESSION LABEL ──
    val labelY = monitorCenterY + monitorSize / 2f + h * 0.02f
    textPaint.textSize = w * 0.035f
    textPaint.color = Color.argb(115, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    canvas.drawText(expression.label, cx, labelY, textPaint)

    // ── HEALTH STATS — Pill Row ──
    // Draw 3 rounded-rect "chips" side by side at the bottom third
    val chipY = cy + h * 0.28f           // position below the face, but above date
    val chipH = h * 0.07f
    val chipW = w * 0.22f
    val chipSpacing = w * 0.25f
    val chipTop = chipY - chipH / 2f
    val chipBottom = chipY + chipH / 2f
    val cornerRadius = chipH / 2f

    val chipPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    val statLabelPaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = w * 0.036f           // larger, legible
    }

    // Steps chip
    chipPaint.color = Color.argb(50, 80, 230, 255)
    canvas.drawRoundRect(cx - chipSpacing - chipW/2f, chipTop, cx - chipSpacing + chipW/2f, chipBottom, cornerRadius, cornerRadius, chipPaint)
    statLabelPaint.color = Color.rgb(80, 230, 255)
    canvas.drawText("$steps", cx - chipSpacing, chipY + chipH * 0.18f, statLabelPaint)

    // Heart rate chip
    chipPaint.color = Color.argb(50, 255, 100, 100)
    canvas.drawRoundRect(cx - chipW/2f, chipTop, cx + chipW/2f, chipBottom, cornerRadius, cornerRadius, chipPaint)
    statLabelPaint.color = Color.rgb(255, 100, 100)
    canvas.drawText(if (hr > 0) "$hr" else "--", cx, chipY + chipH * 0.18f, statLabelPaint)

    // Calories chip
    chipPaint.color = Color.argb(50, 255, 159, 80)
    canvas.drawRoundRect(cx + chipSpacing - chipW/2f, chipTop, cx + chipSpacing + chipW/2f, chipBottom, cornerRadius, cornerRadius, chipPaint)
    statLabelPaint.color = Color.rgb(255, 159, 80)
    canvas.drawText("$calories", cx + chipSpacing, chipY + chipH * 0.18f, statLabelPaint)

    // Add small icon ABOVE each chip (shift textPaint up)
    statLabelPaint.textSize = w * 0.022f
    statLabelPaint.color = Color.argb(140, 255, 255, 255)
    canvas.drawText("STEPS", cx - chipSpacing, chipTop - chipH * 0.15f, statLabelPaint)
    canvas.drawText("BPM", cx, chipTop - chipH * 0.15f, statLabelPaint)
    canvas.drawText("KCAL", cx + chipSpacing, chipTop - chipH * 0.15f, statLabelPaint)

    // ── DATE ──
    textPaint.textSize = w * 0.038f // Slightly larger date for readability
    textPaint.color = Color.argb(120, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
    canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, h - h * 0.04f, textPaint)
  }

  private fun drawMonitor(
    canvas: Canvas, centerX: Float, centerY: Float, size: Float,
    faceFrame: Array<IntArray>, faceColor: Int, tvProgress: Float
  ) {
    val pxSz = size / PixelFaceRenderer.GRID
    val left = centerX - size / 2f
    val top = centerY - size / 2f

    // Monitor frame
    PixelFaceRenderer.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, idx ->
        if (idx == 0) return@forEachIndexed
        val paint = if (idx == 4) screenBgPaint else (monitorPaints[idx] ?: return@forEachIndexed)
        canvas.drawRect(left + col * pxSz, top + row * pxSz, left + (col + 1) * pxSz, top + (row + 1) * pxSz, paint)
      }
    }

    // TV power line
    if (tvProgress in 0.001f..0.999f) {
      val sY = top + PixelFaceRenderer.SCREEN_ROW_START * pxSz
      val sH = 9 * pxSz
      val cY = sY + sH / 2f
      val visH = sH * tvProgress
      pixelPaint.color = Color.argb(
        (204 * tvProgress).toInt().coerceIn(50, 204),
        Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor)
      )
      canvas.drawRect(
        left + (PixelFaceRenderer.SCREEN_COL_START - 1) * pxSz, cY - visH / 2f,
        left + (PixelFaceRenderer.SCREEN_COL_START + PixelFaceRenderer.FACE_GRID + 1) * pxSz,
        cY - visH / 2f + visH.coerceAtLeast(pxSz * 0.3f),
        pixelPaint
      )
    }

    // Face pixels
    if (tvProgress > 0.5f) {
      val alpha = ((tvProgress - 0.5f) * 2f * 255).toInt().coerceIn(0, 255)
      val fX = left + PixelFaceRenderer.SCREEN_COL_START * pxSz
      val fY = top + PixelFaceRenderer.SCREEN_ROW_START * pxSz
      pixelPaint.color = Color.argb(alpha, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))

      faceFrame.forEachIndexed { row, cols ->
        cols.forEachIndexed { col, px ->
          if (px == 0) return@forEachIndexed
          canvas.drawRect(fX + col * pxSz, fY + row * pxSz, fX + (col + 1) * pxSz, fY + (row + 1) * pxSz, pixelPaint)
        }
      }
    }
  }
  
  private fun drawDualArcClock(canvas: Canvas, w: Float, h: Float, hour: Int, minute: Int, isAmbient: Boolean) {
    // Read color theme from SharedPreferences (set by WatchFaceConfigActivity)
    val prefs = context.getSharedPreferences(
      WatchFaceConfigActivity.PREFS_NAME, Context.MODE_PRIVATE
    )
    val themeName = prefs.getString(WatchFaceConfigActivity.KEY_COLOR_THEME, "GREY") ?: "GREY"

    val activeHourColor = when (themeName) {
        "BLUE" -> Color.rgb(80, 80, 200)
        "GREEN" -> Color.rgb(80, 200, 80)
        "PINK" -> Color.rgb(200, 80, 200)
        "CYAN" -> Color.rgb(80, 200, 200)
        "GOLD" -> Color.rgb(200, 160, 80)
        "RED" -> Color.rgb(200, 48, 48)
        "ORANGE" -> Color.rgb(200, 112, 48)
        "VIOLET" -> Color.rgb(128, 64, 200)
        "ICE" -> Color.rgb(48, 128, 200)
        "LIME" -> Color.rgb(144, 200, 48)
        "ROSE" -> Color.rgb(200, 80, 112)
        else -> Color.rgb(80, 80, 80) // GREY
    }
    
    val activeMinuteColor = when (themeName) {
        "BLUE" -> Color.rgb(150, 150, 255)
        "GREEN" -> Color.rgb(150, 255, 150)
        "PINK" -> Color.rgb(255, 150, 255)
        "CYAN" -> Color.rgb(150, 255, 255)
        "GOLD" -> Color.rgb(255, 220, 150)
        "RED" -> Color.rgb(255, 112, 112)
        "ORANGE" -> Color.rgb(255, 176, 112)
        "VIOLET" -> Color.rgb(184, 128, 255)
        "ICE" -> Color.rgb(112, 184, 255)
        "LIME" -> Color.rgb(200, 255, 112)
        "ROSE" -> Color.rgb(255, 144, 176)
        else -> Color.rgb(200, 200, 200) // GREY
    }

    val minuteFraction = minute / 60f
    val hourFraction = ((hour % 12) + minuteFraction) / 12f

    // 1. Minutes Arc (Outer)
    val minInset = w * 0.035f
    val minArcRect = RectF(minInset, minInset, w - minInset, h - minInset)
    
    // 2. Hours Arc (Inner)
    val hourInset = w * 0.07f
    val hourArcRect = RectF(hourInset, hourInset, w - hourInset, h - hourInset)
    val cx = w / 2f
    val cy = h / 2f
    val hourRadius = cx - hourInset

    // Draw 12 dots for hours
    for (i in 0..11) {
        val angle = Math.PI * 2 * (i / 12f) - Math.PI / 2
        val dx = cx + Math.cos(angle).toFloat() * hourRadius
        val dy = cy + Math.sin(angle).toFloat() * hourRadius
        
        dotDimPaint.color = if (isAmbient) Color.rgb(80, 80, 80) else Color.argb(80, Color.red(activeHourColor), Color.green(activeHourColor), Color.blue(activeHourColor))
        canvas.drawCircle(dx, dy, w * 0.008f, dotDimPaint)

        // Draw active dot if this hour is filled
        if (hourFraction >= (i / 12f) && !isAmbient) {
             dotActivePaint.color = activeHourColor
             canvas.drawCircle(dx, dy, w * 0.008f, dotActivePaint)
        }
    }

    arcPaint.strokeCap = Paint.Cap.ROUND
    
    // --- Minutes sweep ---
    arcPaint.strokeWidth = w * 0.022f

    if (!isAmbient) {
      // Ghost minute ring
      arcPaint.color = Color.argb(40, Color.red(activeMinuteColor), Color.green(activeMinuteColor), Color.blue(activeMinuteColor))
      canvas.drawArc(minArcRect, -90f, 360f, false, arcPaint)
      
      // Active minute sweep
      arcPaint.color = activeMinuteColor
    } else {
      // Ambient minute sweep
      arcPaint.color = Color.rgb(150, 150, 150) // Dimmer gray  
    }
    canvas.drawArc(minArcRect, -90f, 360f * minuteFraction, false, arcPaint)

    // --- Hours sweep ---
    arcPaint.strokeWidth = w * 0.014f

    if (!isAmbient) {
      // Ghost hour ring
      arcPaint.color = Color.argb(40, Color.red(activeHourColor), Color.green(activeHourColor), Color.blue(activeHourColor))
      canvas.drawArc(hourArcRect, -90f, 360f, false, arcPaint)

      // Active hour sweep
      arcPaint.color = activeHourColor
    } else {
      // Ambient hour sweep
      arcPaint.color = Color.rgb(100, 100, 100) // Dimmer gray
    }
    canvas.drawArc(hourArcRect, -90f, 360f * hourFraction, false, arcPaint)
  }
}
