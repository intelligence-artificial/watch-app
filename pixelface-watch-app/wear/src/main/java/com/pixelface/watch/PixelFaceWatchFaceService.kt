package com.pixelface.watch

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val watchFace = WatchFace(WatchFaceType.DIGITAL, renderer)
    watchFace.setTapListener(object : WatchFace.TapListener {
      override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        if (tapType == TapType.UP) {
          val x = tapEvent.xPos.toFloat()
          val y = tapEvent.yPos.toFloat()
          
          // Face tap → launch voice note
          if (renderer.isMonitorTapped(x, y)) {
            try {
              val intent = Intent(applicationContext, VoiceNoteActivity::class.java)
              intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              applicationContext.startActivity(intent)
            } catch (e: Exception) {
              Log.e(TAG, "Failed to launch voice note", e)
            }
            return
          }
          
          // Ring tap → launch PixelFace app chart
          val tappedRing = renderer.getTappedRing(x, y)
          if (tappedRing != null) {
            try {
              val intent = applicationContext.packageManager.getLaunchIntentForPackage("com.pixelface.watch")
              if (intent != null) {
                intent.putExtra("navigate_to", tappedRing)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(intent)
              }
            } catch (e: Exception) {
              Log.e(TAG, "Failed to launch app", e)
            }
          }
        }
      }
    })
    return watchFace
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

  // ── Hybrid Step Counter ──
  // Health Services STEPS_DAILY = authoritative baseline (same as Fitbit)
  // Hardware TYPE_STEP_COUNTER = live delta between batches (responsiveness)
  private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
  private val stepSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
  private var sensorBaselineRaw = -1        // raw sensor value when we last synced with Health Services
  private var healthServicesBaseline = -1   // last STEPS_DAILY value from Health Services

  private val stepListener = object : android.hardware.SensorEventListener {
    override fun onSensorChanged(event: android.hardware.SensorEvent) {
      if (event.values.isEmpty()) return
      val rawSteps = event.values[0].toInt()
      val prefs = context.getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)
      val hsSteps = prefs.getInt(HealthDataManager.KEY_DAILY_STEPS, 0)

      // If Health Services delivered a new batch, snap to it as the new baseline
      if (hsSteps != healthServicesBaseline) {
        healthServicesBaseline = hsSteps
        sensorBaselineRaw = rawSteps
        return // accept the HS value as-is this tick
      }

      // First reading — just set baseline, don't override
      if (sensorBaselineRaw == -1) {
        sensorBaselineRaw = rawSteps
        healthServicesBaseline = hsSteps
        return
      }

      // Calculate delta from hardware sensor since last HS batch
      val sensorDelta = (rawSteps - sensorBaselineRaw).coerceAtLeast(0)
      val liveSteps = healthServicesBaseline + sensorDelta

      // Only write if we're adding steps (never decrease)
      if (liveSteps > hsSteps) {
        prefs.edit().putInt(HealthDataManager.KEY_DAILY_STEPS, liveSteps).apply()
        StepsComplicationService.requestUpdate(context)
      }
    }
    override fun onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) {}
  }

  init {
    CoroutineScope(Dispatchers.Main).launch {
      watchState.isVisible.collect { visible: Boolean? ->
        if (visible == true && stepSensor != null) {
          // Reset baselines on screen-on so we re-sync with Health Services
          sensorBaselineRaw = -1
          healthServicesBaseline = -1
          sensorManager.registerListener(stepListener, stepSensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        } else if (stepSensor != null) {
          sensorManager.unregisterListener(stepListener)
        }
      }
    }
  }

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

  // ── Complication ring tap positions ──
  private var stepsRingCx = 0f; private var stepsRingCy = 0f; private var stepsRingRadius = 0f
  private var bpmRingCx = 0f; private var bpmRingCy = 0f; private var bpmRingRadius = 0f
  private var kcalRingCx = 0f; private var kcalRingCy = 0f; private var kcalRingRadius = 0f

  // ── Monitor tap position (for voice note) ──
  private var monitorCx = 0f; private var monitorCy = 0f; private var monitorHalfSize = 0f

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

  /** Check if tap coordinates hit any complication ring */
  fun getTappedRing(x: Float, y: Float): String? {
    val hitMultiplier = 1.5f
    fun hitTest(cx: Float, cy: Float, r: Float): Boolean {
      val dx = x - cx; val dy = y - cy
      return dx * dx + dy * dy <= (r * hitMultiplier) * (r * hitMultiplier)
    }
    if (hitTest(stepsRingCx, stepsRingCy, stepsRingRadius)) return "steps_chart"
    if (hitTest(bpmRingCx, bpmRingCy, bpmRingRadius)) return "hr_chart"
    if (hitTest(kcalRingCx, kcalRingCy, kcalRingRadius)) return "cal_chart"
    return null
  }

  /** Check if tap coordinates hit the monitor/face area */
  fun isMonitorTapped(x: Float, y: Float): Boolean {
    return x >= monitorCx - monitorHalfSize && x <= monitorCx + monitorHalfSize &&
           y >= monitorCy - monitorHalfSize && y <= monitorCy + monitorHalfSize
  }

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

    // ── Read health data directly from HealthDataManager's prefs ──
    // (Previously read from "pixelface_state" which only updates when HomeScreen is open)
    val healthPrefs = context.getSharedPreferences(HealthDataManager.PREFS_NAME, Context.MODE_PRIVATE)
    val hr = healthPrefs.getInt(HealthDataManager.KEY_HEART_RATE, 0)
    val steps = healthPrefs.getInt(HealthDataManager.KEY_DAILY_STEPS, 0)
    val calories = healthPrefs.getInt(HealthDataManager.KEY_CALORIES, 0)
    val hour = zonedDateTime.hour

    // Use the SAME FaceExpression logic as the app
    val expression = FaceExpression.fromHealth(hr, steps, calories)
    val faceThemePrefs = context.getSharedPreferences(
      WatchFaceConfigActivity.PREFS_NAME, Context.MODE_PRIVATE
    )
    val faceThemeName = faceThemePrefs.getString(WatchFaceConfigActivity.KEY_COLOR_THEME, "GREY") ?: "GREY"
    val faceColor = when (faceThemeName) {
        "BLUE" -> Color.rgb(138, 171, 196)   // Ocean light
        "GREEN" -> Color.rgb(143, 184, 143)  // Forest light
        "PINK" -> Color.rgb(196, 157, 168)   // Blossom light
        "CYAN" -> Color.rgb(143, 184, 184)   // Glacier light
        "GOLD" -> Color.rgb(196, 180, 138)   // Sandstone light
        "RED" -> Color.rgb(184, 128, 128)    // Ember light
        "ORANGE" -> Color.rgb(196, 158, 122) // Amber light
        "VIOLET" -> Color.rgb(158, 138, 184) // Twilight light
        "ICE" -> Color.rgb(138, 164, 184)    // Frost light
        "LIME" -> Color.rgb(160, 184, 128)   // Moss light
        "ROSE" -> Color.rgb(196, 144, 144)   // Terracotta light
        else -> Color.rgb(176, 176, 176)     // Slate light
    }

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

    // ── CRT MONITOR + FACE — smaller, shifted up ──
    val monitorSize = w * 0.40f
    val monitorCenterY = cy - h * 0.10f + bobOffset
    monitorCx = cx
    monitorCy = monitorCenterY
    monitorHalfSize = monitorSize / 2f

    // ── EXPRESSION LABEL — above the monitor (bold) ──
    textPaint.textSize = w * 0.035f
    textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    textPaint.color = Color.argb(140, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    canvas.drawText(expression.label, cx, monitorCenterY - monitorSize / 2f - h * 0.015f, textPaint)
    textPaint.typeface = Typeface.MONOSPACE

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

    // ── DATE — below the monitor (where expression label used to be, bold) ──
    val dateLabelY = monitorCenterY + monitorSize / 2f + h * 0.03f
    textPaint.textSize = w * 0.035f
    textPaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    textPaint.color = Color.argb(120, Color.red(faceColor), Color.green(faceColor), Color.blue(faceColor))
    val dateFmt = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
    canvas.drawText(dateFmt.format(java.util.Date(nowMs)), cx, dateLabelY, textPaint)
    textPaint.typeface = Typeface.MONOSPACE

    // ── Read current theme colors for ring styling (2-tone match) ──
    val themePrefs = context.getSharedPreferences(
      WatchFaceConfigActivity.PREFS_NAME, Context.MODE_PRIVATE
    )
    val themeName = themePrefs.getString(WatchFaceConfigActivity.KEY_COLOR_THEME, "GREY") ?: "GREY"
    val ringDarkColor = when (themeName) {  // darker tone = ring arc
        "BLUE" -> Color.rgb(61, 90, 128)     // Ocean
        "GREEN" -> Color.rgb(74, 107, 74)    // Forest
        "PINK" -> Color.rgb(139, 94, 107)    // Blossom
        "CYAN" -> Color.rgb(74, 112, 112)    // Glacier
        "GOLD" -> Color.rgb(139, 122, 80)    // Sandstone
        "RED" -> Color.rgb(122, 59, 59)      // Ember
        "ORANGE" -> Color.rgb(139, 96, 64)   // Amber
        "VIOLET" -> Color.rgb(94, 74, 122)   // Twilight
        "ICE" -> Color.rgb(61, 88, 112)      // Frost
        "LIME" -> Color.rgb(96, 122, 59)     // Moss
        "ROSE" -> Color.rgb(139, 80, 80)     // Terracotta
        else -> Color.rgb(90, 90, 90)        // Slate
    }
    val ringLightColor = when (themeName) { // lighter tone = data text
        "BLUE" -> Color.rgb(138, 171, 196)   // Ocean
        "GREEN" -> Color.rgb(143, 184, 143)  // Forest
        "PINK" -> Color.rgb(196, 157, 168)   // Blossom
        "CYAN" -> Color.rgb(143, 184, 184)   // Glacier
        "GOLD" -> Color.rgb(196, 180, 138)   // Sandstone
        "RED" -> Color.rgb(184, 128, 128)    // Ember
        "ORANGE" -> Color.rgb(196, 158, 122) // Amber
        "VIOLET" -> Color.rgb(158, 138, 184) // Twilight
        "ICE" -> Color.rgb(138, 164, 184)    // Frost
        "LIME" -> Color.rgb(160, 184, 128)   // Moss
        "ROSE" -> Color.rgb(196, 144, 144)   // Terracotta
        else -> Color.rgb(176, 176, 176)     // Slate
    }
    val ringBgColor = Color.argb(40,
      Color.red(ringDarkColor), Color.green(ringDarkColor), Color.blue(ringDarkColor))

    // ── COMPLICATION RINGS — triangle formation below the pixel face ──
    // Monitor bottom ≈ h*0.62, clock arc bottom ≈ h*0.93
    // Triangle: Steps + Kcal on top row, BPM at bottom vertex
    val ringRadius = w * 0.075f
    val ringStroke = w * 0.012f

    // Steps — triangle top-left
    stepsRingCx = w * 0.28f
    stepsRingCy = h * 0.70f
    stepsRingRadius = ringRadius
    val stepsGoal = 10000
    val stepsFraction = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
    drawComplicationRing(canvas, stepsRingCx, stepsRingCy, ringRadius, ringStroke,
      "$steps", "STEPS", stepsFraction,
      ringDarkColor, ringBgColor, ringLightColor)

    // Kcal — triangle top-right (symmetrical with steps)
    kcalRingCx = w * 0.72f
    kcalRingCy = h * 0.70f
    kcalRingRadius = ringRadius
    val kcalGoal = 2500
    val kcalFraction = (calories.toFloat() / kcalGoal).coerceIn(0f, 1f)
    drawComplicationRing(canvas, kcalRingCx, kcalRingCy, ringRadius, ringStroke,
      "$calories", "FLAME", kcalFraction,
      ringDarkColor, ringBgColor, ringLightColor)

    // BPM — triangle bottom vertex (centered)
    bpmRingCx = cx
    bpmRingCy = h * 0.79f
    bpmRingRadius = ringRadius
    val bpmFraction = if (hr > 0) (hr.toFloat() / 180f).coerceIn(0f, 1f) else 0f
    drawComplicationRing(canvas, bpmRingCx, bpmRingCy, ringRadius, ringStroke,
      if (hr > 0) "$hr" else "--", "HEART", bpmFraction,
      ringDarkColor, ringBgColor, ringLightColor)
  }

  private fun drawComplicationRing(
    canvas: Canvas, cx: Float, cy: Float, radius: Float, strokeWidth: Float,
    value: String, iconType: String, fraction: Float,
    activeColor: Int, bgColor: Int, textColor: Int = activeColor
  ) {
    val ringRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    
    // Arc with gap at bottom for icon (gap = 50°)
    val gapDegrees = 50f
    val arcStart = 90f + gapDegrees / 2f
    val arcSweep = 360f - gapDegrees
    
    // Background arc (dim)
    arcPaint.strokeWidth = strokeWidth * 1.5f
    arcPaint.color = bgColor
    arcPaint.style = Paint.Style.STROKE
    arcPaint.strokeCap = Paint.Cap.ROUND
    canvas.drawArc(ringRect, arcStart, arcSweep, false, arcPaint)
    
    // Active progress arc
    if (fraction > 0f) {
      arcPaint.color = activeColor
      canvas.drawArc(ringRect, arcStart, fraction * arcSweep, false, arcPaint)
    }
    
    // Small filled circle at bottom of ring for icon
    val iconCircleRadius = radius * 0.35f
    val iconCx = cx
    val iconCy = cy + radius
    
    val circlePaint = Paint().apply {
      isAntiAlias = true
      style = Paint.Style.FILL
      color = activeColor
    }
    canvas.drawCircle(iconCx, iconCy, iconCircleRadius, circlePaint)
    
    // Draw vector icon inside the circle
    val iconPaint = Paint().apply {
      isAntiAlias = true
      style = Paint.Style.FILL
      color = Color.BLACK
    }
    val iconSize = iconCircleRadius * 0.85f
    
    when (iconType) {
      "HEART" -> drawHeartIcon(canvas, iconCx, iconCy, iconSize, iconPaint)
      "FLAME" -> drawFlameIcon(canvas, iconCx, iconCy, iconSize, iconPaint)
      "STEPS" -> drawStepsIcon(canvas, iconCx, iconCy, iconSize, iconPaint)
    }
    
    // Value text (large, centered in ring)
    val valuePaint = Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
      textSize = radius * 0.68f
      color = textColor
    }
    canvas.drawText(value, cx, cy + radius * 0.08f, valuePaint)
  }

  private fun drawHeartIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
    val path = android.graphics.Path()
    val s = size
    path.moveTo(cx, cy + s * 0.35f)                         // bottom point
    path.cubicTo(cx - s * 1.2f, cy - s * 0.2f,              // left curve
                 cx - s * 0.5f, cy - s * 0.9f,
                 cx, cy - s * 0.3f)
    path.cubicTo(cx + s * 0.5f, cy - s * 0.9f,              // right curve
                 cx + s * 1.2f, cy - s * 0.2f,
                 cx, cy + s * 0.35f)
    path.close()
    canvas.drawPath(path, paint)
  }

  private fun drawFlameIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
    val path = android.graphics.Path()
    val s = size
    path.moveTo(cx, cy - s * 0.6f)                          // top tip
    path.cubicTo(cx + s * 0.1f, cy - s * 0.3f,
                 cx + s * 0.55f, cy - s * 0.1f,
                 cx + s * 0.4f, cy + s * 0.25f)
    path.cubicTo(cx + s * 0.35f, cy + s * 0.5f,
                 cx + s * 0.1f, cy + s * 0.6f,
                 cx, cy + s * 0.55f)
    path.cubicTo(cx - s * 0.1f, cy + s * 0.6f,
                 cx - s * 0.35f, cy + s * 0.5f,
                 cx - s * 0.4f, cy + s * 0.25f)
    path.cubicTo(cx - s * 0.55f, cy - s * 0.1f,
                 cx - s * 0.1f, cy - s * 0.3f,
                 cx, cy - s * 0.6f)
    path.close()
    canvas.drawPath(path, paint)
  }

  private fun drawStepsIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
    // Simple walking person silhouette
    val s = size
    paint.strokeWidth = s * 0.22f
    paint.strokeCap = Paint.Cap.ROUND
    val origStyle = paint.style
    
    // Head
    paint.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy - s * 0.45f, s * 0.18f, paint)
    
    // Body line
    paint.style = Paint.Style.STROKE
    canvas.drawLine(cx, cy - s * 0.25f, cx, cy + s * 0.15f, paint)
    
    // Arms (slightly angled, walking pose)
    canvas.drawLine(cx - s * 0.3f, cy - s * 0.05f, cx + s * 0.3f, cy - s * 0.2f, paint)
    
    // Legs (walking stance)
    canvas.drawLine(cx, cy + s * 0.15f, cx - s * 0.25f, cy + s * 0.55f, paint)
    canvas.drawLine(cx, cy + s * 0.15f, cx + s * 0.25f, cy + s * 0.55f, paint)
    
    paint.style = origStyle
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
        "BLUE" -> Color.rgb(61, 90, 128)     // Ocean
        "GREEN" -> Color.rgb(74, 107, 74)    // Forest
        "PINK" -> Color.rgb(139, 94, 107)    // Blossom
        "CYAN" -> Color.rgb(74, 112, 112)    // Glacier
        "GOLD" -> Color.rgb(139, 122, 80)    // Sandstone
        "RED" -> Color.rgb(122, 59, 59)      // Ember
        "ORANGE" -> Color.rgb(139, 96, 64)   // Amber
        "VIOLET" -> Color.rgb(94, 74, 122)   // Twilight
        "ICE" -> Color.rgb(61, 88, 112)      // Frost
        "LIME" -> Color.rgb(96, 122, 59)     // Moss
        "ROSE" -> Color.rgb(139, 80, 80)     // Terracotta
        else -> Color.rgb(90, 90, 90)        // Slate
    }
    
    val activeMinuteColor = when (themeName) {
        "BLUE" -> Color.rgb(138, 171, 196)   // Ocean
        "GREEN" -> Color.rgb(143, 184, 143)  // Forest
        "PINK" -> Color.rgb(196, 157, 168)   // Blossom
        "CYAN" -> Color.rgb(143, 184, 184)   // Glacier
        "GOLD" -> Color.rgb(196, 180, 138)   // Sandstone
        "RED" -> Color.rgb(184, 128, 128)    // Ember
        "ORANGE" -> Color.rgb(196, 158, 122) // Amber
        "VIOLET" -> Color.rgb(158, 138, 184) // Twilight
        "ICE" -> Color.rgb(138, 164, 184)    // Frost
        "LIME" -> Color.rgb(160, 184, 128)   // Moss
        "ROSE" -> Color.rgb(196, 144, 144)   // Terracotta
        else -> Color.rgb(176, 176, 176)     // Slate
    }

    val minuteFraction = minute / 60f
    val hourFraction = ((hour % 12) + minuteFraction) / 12f

    // 1. Hours Arc (Outer — thicker)
    val hourInset = w * 0.035f
    val hourArcRect = RectF(hourInset, hourInset, w - hourInset, h - hourInset)
    
    // 2. Minutes Arc (Inner — thinner)
    val minInset = w * 0.07f
    val minArcRect = RectF(minInset, minInset, w - minInset, h - minInset)

    val cx = w / 2f
    val cy = h / 2f
    val minRadius = cx - minInset

    // Draw 12 dots for hours on inner ring
    for (i in 0..11) {
        val angle = Math.PI * 2 * (i / 12f) - Math.PI / 2
        val dx = cx + Math.cos(angle).toFloat() * minRadius
        val dy = cy + Math.sin(angle).toFloat() * minRadius
        
        dotDimPaint.color = if (isAmbient) Color.rgb(80, 80, 80) else Color.argb(80, Color.red(activeHourColor), Color.green(activeHourColor), Color.blue(activeHourColor))
        canvas.drawCircle(dx, dy, w * 0.008f, dotDimPaint)

        // Draw active dot if this hour is filled
        if (hourFraction >= (i / 12f) && !isAmbient) {
             dotActivePaint.color = activeHourColor
             canvas.drawCircle(dx, dy, w * 0.008f, dotActivePaint)
        }
    }

    arcPaint.strokeCap = Paint.Cap.ROUND
    
    // --- Hours sweep (Outer — thicker) ---
    arcPaint.strokeWidth = w * 0.028f

    if (!isAmbient) {
      // Ghost hour ring
      arcPaint.color = Color.argb(40, Color.red(activeHourColor), Color.green(activeHourColor), Color.blue(activeHourColor))
      canvas.drawArc(hourArcRect, -90f, 360f, false, arcPaint)
      
      // Active hour sweep
      arcPaint.color = activeHourColor
    } else {
      arcPaint.color = Color.rgb(100, 100, 100)
    }
    canvas.drawArc(hourArcRect, -90f, 360f * hourFraction, false, arcPaint)

    // --- Minutes sweep (Inner) ---
    arcPaint.strokeWidth = w * 0.020f

    if (!isAmbient) {
      // Ghost minute ring
      arcPaint.color = Color.argb(40, Color.red(activeMinuteColor), Color.green(activeMinuteColor), Color.blue(activeMinuteColor))
      canvas.drawArc(minArcRect, -90f, 360f, false, arcPaint)

      // Active minute sweep
      arcPaint.color = activeMinuteColor
    } else {
      arcPaint.color = Color.rgb(150, 150, 150)
    }
    canvas.drawArc(minArcRect, -90f, 360f * minuteFraction, false, arcPaint)
  }
}
