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
 * 漢字ネオン (Kanji Neon) Watch Face
 * Cyberpunk digital clock with Japanese kanji numerals,
 * scan lines, hexagonal grid, and neon glow effects.
 */
class KanjiNeonWatchFaceService : WatchFaceService() {

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
        val renderer = KanjiNeonCanvasRenderer(
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class KanjiNeonCanvasRenderer(
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
    // Kanji digits: 〇一二三四五六七八九
    private val kanjiDigits = charArrayOf('〇', '一', '二', '三', '四', '五', '六', '七', '八', '九')

    // Japanese months
    private val kanjiMonths = arrayOf(
        "一月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "十一月", "十二月"
    )

    // Colors
    private val neonCyan = Color.argb(255, 0, 240, 255)
    private val neonPink = Color.argb(255, 255, 45, 124)
    private val dimCyan = Color.argb(40, 0, 240, 255)
    private val dimPink = Color.argb(25, 255, 45, 124)

    // Main time paint — large kanji digits
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonCyan
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Glow behind time
    private val timeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 240, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(20f, 0f, 0f, Color.argb(120, 0, 240, 255))
    }

    // Colon/separator
    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonPink
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Date paint
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 45, 124)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    // Scan line paint
    private val scanPaint = Paint().apply {
        color = Color.argb(12, 0, 240, 255)
        style = Paint.Style.FILL
    }

    // Hex grid paint
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(15, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
    }

    // Second progress arc
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    private val arcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Corner decoration
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // Tick marks
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
        strokeCap = Paint.Cap.ROUND
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 240, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeCap = Paint.Cap.ROUND
    }

    private fun toKanji(n: Int): String {
        if (n < 10) return kanjiDigits[n].toString()
        val tens = n / 10
        val ones = n % 10
        return "${kanjiDigits[tens]}${kanjiDigits[ones]}"
    }

    private fun toKanjiDay(day: Int): String {
        return when {
            day < 10 -> "${kanjiDigits[day]}日"
            day == 10 -> "十日"
            day < 20 -> "十${kanjiDigits[day - 10]}日"
            day == 20 -> "二十日"
            day < 30 -> "二十${kanjiDigits[day - 20]}日"
            day == 30 -> "三十日"
            else -> "三十${kanjiDigits[day - 30]}日"
        }
    }

    private fun drawHexGrid(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float) {
        val hexSize = 18f
        val h = hexSize * 1.732f // sqrt(3)
        val cols = (radius * 2 / (hexSize * 1.5f)).toInt() + 2
        val rows = (radius * 2 / h).toInt() + 2
        val startX = cx - cols * hexSize * 0.75f
        val startY = cy - rows * h * 0.5f

        for (row in 0..rows) {
            for (col in 0..cols) {
                val offsetY = if (col % 2 == 1) h * 0.5f else 0f
                val hx = startX + col * hexSize * 1.5f
                val hy = startY + row * h + offsetY

                val dist = Math.sqrt(((hx - cx) * (hx - cx) + (hy - cy) * (hy - cy)).toDouble()).toFloat()
                if (dist > radius) continue

                // Pulse effect: hexagons near a sweep line glow brighter
                val sweepAngle = (time * 0.3f) % (2f * PI.toFloat())
                val hexAngle = Math.atan2((hy - cy).toDouble(), (hx - cx).toDouble()).toFloat()
                val angleDiff = Math.abs(((hexAngle - sweepAngle + PI) % (2 * PI) - PI).toFloat())
                val pulse = if (angleDiff < 0.3f) 35 else 15
                hexPaint.alpha = pulse

                drawHex(canvas, hx, hy, hexSize * 0.4f)
            }
        }
    }

    private fun drawHex(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        var prevX = cx + r
        var prevY = cy
        for (i in 1..6) {
            val angle = i * PI.toFloat() / 3f
            val nx = cx + r * cos(angle)
            val ny = cy + r * sin(angle)
            canvas.drawLine(prevX, prevY, nx, ny, hexPaint)
            prevX = nx; prevY = ny
        }
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f
        val radius = w * 0.46f

        canvas.drawColor(Color.argb(255, 4, 4, 12))

        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second
        val nanos = zonedDateTime.nano
        val totalSeconds = hour * 3600f + minute * 60f + second + nanos / 1_000_000_000f
        val exactSecond = second + nanos / 1_000_000_000f

        // Hex grid background
        drawHexGrid(canvas, cx, cy, radius, totalSeconds)

        // Scan lines
        val scanSpacing = 3f
        val scanOffset = (totalSeconds * 15f) % h
        var scanY = scanOffset % scanSpacing
        while (scanY < h) {
            canvas.drawRect(0f, scanY, w, scanY + 1f, scanPaint)
            scanY += scanSpacing
        }

        // Outer tick marks
        for (i in 0 until 60) {
            val angle = (i * 6f - 90f) * (PI.toFloat() / 180f)
            val isHour = i % 5 == 0
            val inner = radius - if (isHour) 10f else 5f
            val outer = radius - 1f
            canvas.drawLine(
                cx + cos(angle) * inner, cy + sin(angle) * inner,
                cx + cos(angle) * outer, cy + sin(angle) * outer,
                if (isHour) majorTickPaint else tickPaint
            )
        }

        // Second progress arc
        val arcRect = android.graphics.RectF(cx - radius + 4f, cy - radius + 4f, cx + radius - 4f, cy + radius - 4f)
        canvas.drawArc(arcRect, -90f, 360f, false, arcBgPaint)
        arcPaint.shader = LinearGradient(
            cx - radius, cy, cx + radius, cy,
            neonCyan, neonPink, Shader.TileMode.CLAMP
        )
        canvas.drawArc(arcRect, -90f, exactSecond * 6f, false, arcPaint)

        // Second indicator dot
        val secAngle = (exactSecond * 6f - 90f) * (PI.toFloat() / 180f)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = neonPink; style = Paint.Style.FILL
        }
        canvas.drawCircle(
            cx + cos(secAngle) * (radius - 4f),
            cy + sin(secAngle) * (radius - 4f),
            3f, dotPaint
        )

        // Time display — kanji
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val hourStr = toKanji(h12)
        val minStr = toKanji(minute)

        val timeSize = w * 0.18f
        timePaint.textSize = timeSize
        timeGlowPaint.textSize = timeSize
        colonPaint.textSize = timeSize * 0.8f

        // Blink colon
        val colonAlpha = if (second % 2 == 0) 255 else 120
        colonPaint.alpha = colonAlpha

        val timeY = cy + timeSize * 0.15f

        // Draw glow layer first
        canvas.drawText(hourStr, cx - timeSize * 0.6f, timeY, timeGlowPaint)
        canvas.drawText(minStr, cx + timeSize * 0.6f, timeY, timeGlowPaint)

        // Draw crisp layer
        canvas.drawText(hourStr, cx - timeSize * 0.6f, timeY, timePaint)
        canvas.drawText(":", cx, timeY - timeSize * 0.05f, colonPaint)
        canvas.drawText(minStr, cx + timeSize * 0.6f, timeY, timePaint)

        // AM/PM indicator
        val ampmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 0, 240, 255)
            textSize = w * 0.04f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        val ampm = if (hour < 12) "午前" else "午後"
        canvas.drawText(ampm, cx, cy - timeSize * 0.55f, ampmPaint)

        // Date line — Japanese format
        val month = kanjiMonths[zonedDateTime.monthValue - 1]
        val day = toKanjiDay(zonedDateTime.dayOfMonth)
        val dateStr = "$month $day"

        datePaint.textSize = w * 0.06f
        canvas.drawText(dateStr, cx, cy + timeSize * 0.7f, datePaint)

        // Seconds display — small digital
        val secStr = String.format("%02d", second)
        val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 45, 124)
            textSize = w * 0.04f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText(secStr, cx, cy + timeSize * 1.05f, secPaint)

        // Corner decorations — small bracket lines
        val m = 14f
        val cl = 20f
        cornerPaint.color = Color.argb(40, 0, 240, 255)
        // Top-left
        canvas.drawLine(m, m + cl, m, m, cornerPaint)
        canvas.drawLine(m, m, m + cl, m, cornerPaint)
        // Top-right
        canvas.drawLine(w - m - cl, m, w - m, m, cornerPaint)
        canvas.drawLine(w - m, m, w - m, m + cl, cornerPaint)
        // Bottom-left
        canvas.drawLine(m, h - m - cl, m, h - m, cornerPaint)
        canvas.drawLine(m, h - m, m + cl, h - m, cornerPaint)
        // Bottom-right
        canvas.drawLine(w - m - cl, h - m, w - m, h - m, cornerPaint)
        canvas.drawLine(w - m, h - m - cl, w - m, h - m, cornerPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
