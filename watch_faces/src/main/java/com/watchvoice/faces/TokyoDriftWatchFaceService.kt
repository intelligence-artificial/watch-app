package com.watchvoice.faces

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 東京ドリフト (Tokyo Drift) Watch Face
 * Minimalist analog with animated rain streaks, neon arc segments,
 * and Japanese day-of-week display. Inspired by rainy Tokyo nights.
 */
class TokyoDriftWatchFaceService : WatchFaceService() {

    override fun createUserStyleSchema(): UserStyleSchema = UserStyleSchema(emptyList())

    override fun createComplicationSlotsManager(
        currentUserStyleRepository: CurrentUserStyleRepository
    ): ComplicationSlotsManager = ComplicationSlotsManager(emptyList(), currentUserStyleRepository)

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = TokyoDriftCanvasRenderer(
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.ANALOG, renderer)
    }
}

private class TokyoDriftCanvasRenderer(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.SOFTWARE,
    interactiveDrawModeUpdateDelayMillis = 33L
) {
    // Colors
    private val neonRed = Color.argb(255, 255, 48, 64)
    private val electricBlue = Color.argb(255, 64, 96, 255)
    private val warmYellow = Color.argb(255, 255, 208, 48)
    private val bgColor = Color.argb(255, 10, 10, 31)

    // Japanese day names: 日月火水木金土
    private val dayKanji = charArrayOf('日', '月', '火', '水', '木', '金', '土')
    private val dayColors = intArrayOf(
        Color.argb(255, 255, 80, 80),    // Sunday - red
        Color.argb(255, 200, 200, 220),  // Monday - silver
        Color.argb(255, 255, 100, 60),   // Tuesday - orange
        Color.argb(255, 80, 180, 255),   // Wednesday - blue
        Color.argb(255, 100, 220, 100),  // Thursday - green
        Color.argb(255, 255, 200, 60),   // Friday - gold
        Color.argb(255, 140, 140, 200),  // Saturday - purple-grey
    )

    // Rain drops - seeded positions for determinism
    private data class RainDrop(val x: Float, val speed: Float, val length: Float, val alpha: Int)
    private val rainDrops = (0 until 40).map { i ->
        val seed = (i * 7919L + 1013L) // prime-based pseudo-random
        RainDrop(
            x = (seed % 1000) / 1000f,
            speed = 0.3f + (seed % 500) / 1000f,
            length = 8f + (seed % 200) / 10f,
            alpha = 15 + (seed % 30).toInt()
        )
    }

    private val rainPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
    }

    // Neon arc paints
    private val arcPaintRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonRed; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val arcGlowRed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 48, 64); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
    }
    private val arcPaintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = electricBlue; style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND
    }
    private val arcGlowBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 64, 96, 255); style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND
    }
    private val arcPaintYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmYellow; style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val arcGlowYellow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 255, 208, 48); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }

    // Hand paints
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonRed; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val hourGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 48, 64); style = Paint.Style.STROKE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = warmYellow; style = Paint.Style.STROKE; strokeWidth = 1.2f; strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonRed; style = Paint.Style.FILL
    }
    private val centerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 48, 64); style = Paint.Style.FILL
    }

    // Tick paint
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }

    // Day label paint
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Digital seconds
    private val digitalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 208, 48)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f
        val maxR = w * 0.46f

        canvas.drawColor(bgColor)

        val hour = zonedDateTime.hour % 12
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second
        val nanos = zonedDateTime.nano
        val exactSecond = second + nanos / 1_000_000_000f
        val totalSeconds = hour * 3600f + minute * 60f + second + nanos / 1_000_000_000f

        // Animated rain
        for (drop in rainDrops) {
            val dropX = drop.x * w
            val cyclePos = ((totalSeconds * drop.speed * 60f) % (h + drop.length * 2)) - drop.length
            rainPaint.color = Color.argb(drop.alpha, 100, 140, 255)
            canvas.drawLine(dropX, cyclePos, dropX, cyclePos + drop.length, rainPaint)
        }

        // Neon arc segments at cardinal positions
        val arcR = maxR - 8f
        val arcRect = android.graphics.RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        val pulse = (0.6f + 0.4f * sin(totalSeconds * 2f).toFloat())

        // 12 o'clock — red
        arcGlowRed.alpha = (40 * pulse).toInt()
        canvas.drawArc(arcRect, -100f, 20f, false, arcGlowRed)
        arcPaintRed.alpha = (200 * pulse).toInt() + 55
        canvas.drawArc(arcRect, -100f, 20f, false, arcPaintRed)

        // 3 o'clock — blue
        arcGlowBlue.alpha = (30 * pulse).toInt()
        canvas.drawArc(arcRect, -10f, 20f, false, arcGlowBlue)
        arcPaintBlue.alpha = (180 * pulse).toInt() + 55
        canvas.drawArc(arcRect, -10f, 20f, false, arcPaintBlue)

        // 6 o'clock — yellow
        arcGlowYellow.alpha = (25 * pulse).toInt()
        canvas.drawArc(arcRect, 80f, 20f, false, arcGlowYellow)
        arcPaintYellow.alpha = (170 * pulse).toInt() + 55
        canvas.drawArc(arcRect, 80f, 20f, false, arcPaintYellow)

        // 9 o'clock — red
        arcGlowRed.alpha = (35 * pulse).toInt()
        canvas.drawArc(arcRect, 170f, 20f, false, arcGlowRed)
        arcPaintRed.alpha = (190 * pulse).toInt() + 55
        canvas.drawArc(arcRect, 170f, 20f, false, arcPaintRed)

        // Tick marks
        for (i in 0 until 60) {
            val angle = (i * 6f - 90f) * (PI.toFloat() / 180f)
            val isHour = i % 5 == 0
            // Skip ticks near arc segments
            if (i in 28..32 || i in 58..59 || i in 0..2 || i in 13..17 || i in 43..47) continue
            val inner = maxR - if (isHour) 12f else 5f
            val outer = maxR - 1.5f
            canvas.drawLine(
                cx + cos(angle) * inner, cy + sin(angle) * inner,
                cx + cos(angle) * outer, cy + sin(angle) * outer,
                if (isHour) majorTickPaint else tickPaint
            )
        }

        // Japanese day of week — positioned at 3 o'clock area
        val dowIndex = zonedDateTime.dayOfWeek.value % 7 // Monday=1 -> Sunday=0
        val dowKanji = dayKanji[dowIndex]
        dayPaint.textSize = w * 0.07f
        dayPaint.color = dayColors[dowIndex]
        canvas.drawText(dowKanji.toString(), cx + maxR * 0.42f, cy + dayPaint.textSize * 0.35f, dayPaint)

        // Small bracket around day
        val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, dayColors[dowIndex] shr 16 and 0xFF,
                dayColors[dowIndex] shr 8 and 0xFF, dayColors[dowIndex] and 0xFF)
            style = Paint.Style.STROKE; strokeWidth = 0.8f
        }
        val bx = cx + maxR * 0.42f
        val by = cy
        val bs = w * 0.05f
        canvas.drawLine(bx - bs, by - bs, bx - bs, by + bs, bracketPaint)
        canvas.drawLine(bx + bs, by - bs, bx + bs, by + bs, bracketPaint)

        // Hour hand with glow
        val ha = ((hour + minute / 60f) * 30f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ha) * maxR * 0.46f, cy + sin(ha) * maxR * 0.46f, hourGlowPaint)
        canvas.drawLine(cx, cy, cx + cos(ha) * maxR * 0.46f, cy + sin(ha) * maxR * 0.46f, hourPaint)

        // Minute hand
        val ma = ((minute + exactSecond / 60f) * 6f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ma) * maxR * 0.70f, cy + sin(ma) * maxR * 0.70f, minutePaint)

        // Second hand (thin, extends past center)
        val sa = (exactSecond * 6f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(
            cx - cos(sa) * 16f, cy - sin(sa) * 16f,
            cx + cos(sa) * maxR * 0.82f, cy + sin(sa) * maxR * 0.82f,
            secondPaint
        )

        // Center
        canvas.drawCircle(cx, cy, 6f, centerRingPaint)
        canvas.drawCircle(cx, cy, 3f, centerPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
