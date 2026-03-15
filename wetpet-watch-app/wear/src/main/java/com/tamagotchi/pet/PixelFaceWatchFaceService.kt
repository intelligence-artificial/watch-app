package com.tamagotchi.pet

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
 * PixelFace Watch Face — Canvas-based, lives in the SAME APK as the app.
 *
 * Uses FaceExpression + PixelPetRenderer + PixelPetAnimator directly
 * (no duplicate data). Reads SharedPreferences written by HomeScreen
 * since it's the same package.
 *
 * Animation strategy:
 * - INTERACTIVE: 10fps, all effects (bob, TV-on, glitch, scanlines, glow)
 * - AMBIENT: static sleep face, grayscale, zero animation
 */
class PixelFaceWatchFaceService : WatchFaceService() {

  override fun createUserStyleSchema(): UserStyleSchema = UserStyleSchema(emptyList())

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

  // Use the SAME animator as the app
  private val animator = PixelPetAnimator()

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
  
  // ── Analog Hand Paints ──
  private val hourHandPaint = Paint().apply {
    style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
    strokeWidth = 16f
  }
  private val minuteHandPaint = Paint().apply {
    style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND
    strokeWidth = 10f
  }
  private val centerDotPaint = Paint().apply {
    style = Paint.Style.FILL; isAntiAlias = true
    color = Color.WHITE
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
    val prefs = context.getSharedPreferences("wetpet_state", Context.MODE_PRIVATE)
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
      drawMonitor(canvas, cx, cy - h * 0.06f, w * 0.50f, PixelPetRenderer.FACE_SLEEP, Color.rgb(100, 100, 100), 1f)
      
      // Draw analog hands in ambient mode (dimmer)
      hourHandPaint.color = Color.rgb(150, 150, 150)
      minuteHandPaint.color = Color.rgb(200, 200, 200)
      drawAnalogHands(canvas, cx, cy, w, zonedDateTime)
      
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

    // ── PULSING GLOW ──
    val glowAlpha = (0.08f + 0.12f * Math.sin((nowMs % 2000L) / 2000.0 * Math.PI * 2).toFloat()).coerceIn(0.04f, 0.22f)

    // ── FACE FRAME — use the SAME animator as the app ──
    val faceFrame = if (showStatic) {
      PixelPetRenderer.generateStaticFrame()
    } else {
      animator.getCurrentFrame(nowMs, expression, hour)
    }

    // ── ARC RINGS ──
    val stepsProgress = (steps / 10000f).coerceIn(0f, 1f)
    val arcInset = 12f
    val arcRect = RectF(arcInset, arcInset, w - arcInset, h - arcInset)

    arcPaint.strokeWidth = 6f
    arcPaint.color = Color.argb(18, 80, 230, 255)
    canvas.drawArc(arcRect, -225f, 270f, false, arcPaint)
    arcPaint.color = Color.rgb(80, 230, 255)
    canvas.drawArc(arcRect, -225f, 270f * stepsProgress, false, arcPaint)

    arcPaint.strokeWidth = 10f
    arcPaint.color = Color.argb(37, 255, 255, 255)
    for (pct in listOf(0.25f, 0.5f, 0.75f)) {
      canvas.drawArc(arcRect, -225f + 270f * pct - 1f, 2f, false, arcPaint)
    }

    // Battery arc
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

    // Glow ring
    val glowInset = innerInset + 12f
    val glowRect = RectF(glowInset, glowInset, w - glowInset, h - glowInset)
    arcPaint.strokeWidth = 2.5f
    arcPaint.color = Color.argb(
      (glowAlpha * 255).toInt().coerceIn(10, 55),
      Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor)
    )
    canvas.drawArc(glowRect, 0f, 360f, false, arcPaint)

    // ── CRT MONITOR + FACE ──
    val monitorSize = w * 0.50f
    val monitorCenterY = cy - h * 0.06f + bobOffset
    drawMonitor(canvas, cx, monitorCenterY, monitorSize, faceFrame, faceColor, effectiveTvProgress)

    // ── CRT SCANLINES ──
    if (effectiveTvProgress > 0.3f) {
      val pxSz = monitorSize / PixelPetRenderer.GRID
      val mLeft = cx - monitorSize / 2f
      val mTop = monitorCenterY - monitorSize / 2f
      val sX = mLeft + 3 * pxSz
      val sY = mTop + PixelPetRenderer.SCREEN_ROW_START * pxSz
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

    // ── ANALOG HANDS ──
    // Draw hands over the monitor
    hourHandPaint.color = Color.argb(200, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    minuteHandPaint.color = Color.WHITE
    drawAnalogHands(canvas, cx, cy, w, zonedDateTime)

    // ── EXPRESSION LABEL ──
    val labelY = monitorCenterY + monitorSize / 2f + h * 0.02f
    textPaint.textSize = w * 0.035f
    textPaint.color = Color.argb(115, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    canvas.drawText(expression.label, cx, labelY, textPaint)

    // ── DIGITAL TIME ──
    val timeY = labelY + h * 0.06f
    textPaint.textSize = w * 0.11f
    textPaint.color = faceColor
    val hourStr = String.format("%02d", if (hour % 12 == 0) 12 else hour % 12)
    val minStr = String.format("%02d", zonedDateTime.minute)
    val colon = if (nowMs % 1000 < 500) ":" else " "
    canvas.drawText("$hourStr$colon$minStr", cx, timeY, textPaint)

    textPaint.textSize = w * 0.03f
    textPaint.color = Color.argb(100, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    canvas.drawText(if (hour < 12) "AM" else "PM", cx + w * 0.15f, timeY, textPaint)

    // ── HEALTH STATS ──
    val statsY = timeY + h * 0.05f
    textPaint.textSize = w * 0.028f

    textPaint.color = Color.rgb(80, 230, 255)
    canvas.drawText("👟 $steps", cx - w * 0.18f, statsY, textPaint)
    textPaint.color = Color.rgb(255, 100, 100)
    canvas.drawText("♥ ${if (hr > 0) hr else "--"}", cx, statsY, textPaint)
    textPaint.color = Color.rgb(255, 159, 80)
    canvas.drawText("🔥 $calories", cx + w * 0.18f, statsY, textPaint)

    // ── DATE ──
    textPaint.textSize = w * 0.035f
    textPaint.color = Color.argb(80, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
    canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, h - h * 0.06f, textPaint)
  }

  private fun drawMonitor(
    canvas: Canvas, centerX: Float, centerY: Float, size: Float,
    faceFrame: Array<IntArray>, faceColor: Int, tvProgress: Float
  ) {
    val pxSz = size / PixelPetRenderer.GRID
    val left = centerX - size / 2f
    val top = centerY - size / 2f

    // Monitor frame
    PixelPetRenderer.MONITOR_FRAME.forEachIndexed { row, cols ->
      cols.forEachIndexed { col, idx ->
        if (idx == 0) return@forEachIndexed
        val paint = if (idx == 4) screenBgPaint else (monitorPaints[idx] ?: return@forEachIndexed)
        canvas.drawRect(left + col * pxSz, top + row * pxSz, left + (col + 1) * pxSz, top + (row + 1) * pxSz, paint)
      }
    }

    // TV power line
    if (tvProgress in 0.001f..0.999f) {
      val sY = top + PixelPetRenderer.SCREEN_ROW_START * pxSz
      val sH = 9 * pxSz
      val cY = sY + sH / 2f
      val visH = sH * tvProgress
      pixelPaint.color = Color.argb(
        (204 * tvProgress).toInt().coerceIn(50, 204),
        Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor)
      )
      canvas.drawRect(
        left + (PixelPetRenderer.SCREEN_COL_START - 1) * pxSz, cY - visH / 2f,
        left + (PixelPetRenderer.SCREEN_COL_START + PixelPetRenderer.FACE_GRID + 1) * pxSz,
        cY - visH / 2f + visH.coerceAtLeast(pxSz * 0.3f),
        pixelPaint
      )
    }

    // Face pixels
    if (tvProgress > 0.5f) {
      val alpha = ((tvProgress - 0.5f) * 2f * 255).toInt().coerceIn(0, 255)
      val fX = left + PixelPetRenderer.SCREEN_COL_START * pxSz
      val fY = top + PixelPetRenderer.SCREEN_ROW_START * pxSz
      pixelPaint.color = Color.argb(alpha, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))

      faceFrame.forEachIndexed { row, cols ->
        cols.forEachIndexed { col, px ->
          if (px == 0) return@forEachIndexed
          canvas.drawRect(fX + col * pxSz, fY + row * pxSz, fX + (col + 1) * pxSz, fY + (row + 1) * pxSz, pixelPaint)
        }
      }
    }
  }
  
  private fun drawAnalogHands(canvas: Canvas, cx: Float, cy: Float, w: Float, zonedDateTime: ZonedDateTime) {
    val hour = zonedDateTime.hour % 12
    val minute = zonedDateTime.minute
    val second = zonedDateTime.second
    
    // Calculate angles (in radians)
    // -90 degrees because 0 starts at 3 o'clock in standard math
    val minRotation = minute + second / 60f
    val hourRotation = hour + minRotation / 60f
    
    val minAngle = Math.toRadians((minRotation * 6f) - 90.0)
    val hourAngle = Math.toRadians((hourRotation * 30f) - 90.0)
    
    // Hand lengths based on screen width
    val hourHandLength = w * 0.22f
    val minHandLength = w * 0.35f
    
    // Calculate endpoints
    val hourX = (cx + Math.cos(hourAngle) * hourHandLength).toFloat()
    val hourY = (cy + Math.sin(hourAngle) * hourHandLength).toFloat()
    
    val minX = (cx + Math.cos(minAngle) * minHandLength).toFloat()
    val minY = (cy + Math.sin(minAngle) * minHandLength).toFloat()
    
    // Draw hands
    canvas.drawLine(cx, cy, hourX, hourY, hourHandPaint)
    canvas.drawLine(cx, cy, minX, minY, minuteHandPaint)
    
    // Center dot
    canvas.drawCircle(cx, cy, 6f, centerDotPaint)
  }
}
