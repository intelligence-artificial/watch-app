package com.watchvoice.faces

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 桜回路 (Sakura Circuit) Watch Face
 * Zen meets technology — circuit board traces that bloom like cherry blossoms.
 * Traces progressively illuminate with seconds. Floating petal particles.
 */
class SakuraCircuitWatchFaceService : WatchFaceService() {

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
        val renderer = SakuraCircuitCanvasRenderer(
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class SakuraCircuitCanvasRenderer(
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
    private val sakuraPink = Color.argb(255, 255, 143, 171)
    private val softWhite = Color.argb(255, 232, 224, 240)
    private val electricViolet = Color.argb(255, 155, 48, 255)
    private val bgColor = Color.argb(255, 13, 0, 16)

    // Circuit trace paint — dim (unlit)
    private val traceDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(20, 155, 48, 255)
        style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }
    // Circuit trace — lit
    private val traceLitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sakuraPink
        style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val traceGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 143, 171)
        style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }

    // Node dot paints
    private val nodeDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 155, 48, 255); style = Paint.Style.FILL
    }
    private val nodeLitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = sakuraPink; style = Paint.Style.FILL
    }
    private val nodeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 143, 171); style = Paint.Style.FILL
    }

    // Time paint
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = softWhite
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val timeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 143, 171)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(15f, 0f, 0f, Color.argb(80, 255, 143, 171))
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 155, 48, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    // Petal particles — fixed positions for determinism
    private data class Petal(val x: Float, val speed: Float, val size: Float, val drift: Float, val alpha: Int)
    private val petals = (0 until 15).map { i ->
        val seed = (i * 6271L + 3137L)
        Petal(
            x = (seed % 1000) / 1000f,
            speed = 0.02f + (seed % 300) / 10000f,
            size = 2f + (seed % 30) / 10f,
            drift = (seed % 500) / 500f * 0.3f,
            alpha = 30 + (seed % 50).toInt()
        )
    }
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Circuit ring tick paint
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(15, 155, 48, 255)
        style = Paint.Style.STROKE; strokeWidth = 1f
    }

    // Circuit trace layout — 60 segments (one per second) as radial branches
    private data class TraceSegment(
        val startR: Float, val endR: Float,
        val angle: Float,
        val hasNode: Boolean,
        val branchAngle: Float, val branchR: Float
    )

    private fun buildTraceSegments(): List<TraceSegment> {
        return (0 until 60).map { i ->
            val angle = (i * 6f - 90f) * (PI.toFloat() / 180f)
            val seed = (i * 4813L + 2909L)
            val innerR = 0.25f + (seed % 100) / 1000f
            val outerR = 0.38f + (seed % 80) / 1000f
            val hasNode = i % 5 == 0
            val branchAngle = angle + (if (seed % 2 == 0L) 0.08f else -0.08f)
            val branchR = outerR + 0.04f
            TraceSegment(innerR, outerR, angle, hasNode, branchAngle, branchR)
        }
    }

    private val segments = buildTraceSegments()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f
        val maxR = w * 0.46f

        canvas.drawColor(bgColor)

        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second
        val nanos = zonedDateTime.nano
        val exactSecond = second + nanos / 1_000_000_000f
        val totalSeconds = hour * 3600f + minute * 60f + second + nanos / 1_000_000_000f

        // Outer ring - subtle
        canvas.drawCircle(cx, cy, maxR, outerRingPaint)
        canvas.drawCircle(cx, cy, maxR * 0.68f, outerRingPaint)

        // Draw circuit traces — 60 segments
        for ((i, seg) in segments.withIndex()) {
            val lit = i < second || (i == second && nanos > 0)
            val justLit = i == second

            val innerPx = seg.startR * w
            val outerPx = seg.endR * w

            val x1 = cx + cos(seg.angle) * innerPx
            val y1 = cy + sin(seg.angle) * innerPx
            val x2 = cx + cos(seg.angle) * outerPx
            val y2 = cy + sin(seg.angle) * outerPx

            if (lit) {
                // Glow
                if (justLit) {
                    traceGlowPaint.alpha = (60 * (1f - nanos / 1_000_000_000f)).toInt() + 20
                    canvas.drawLine(x1, y1, x2, y2, traceGlowPaint)
                }
                traceLitPaint.alpha = if (justLit) 255 else (120 + (i * 2)).coerceAtMost(220)
                canvas.drawLine(x1, y1, x2, y2, traceLitPaint)

                // Node at end
                if (seg.hasNode) {
                    canvas.drawCircle(x2, y2, 4f, nodeGlowPaint)
                    canvas.drawCircle(x2, y2, 2f, nodeLitPaint)
                }

                // Branch
                val bx = cx + cos(seg.branchAngle) * seg.branchR * w
                val by = cy + sin(seg.branchAngle) * seg.branchR * w
                traceLitPaint.alpha = 80
                canvas.drawLine(x2, y2, bx, by, traceLitPaint)
            } else {
                canvas.drawLine(x1, y1, x2, y2, traceDimPaint)
                if (seg.hasNode) {
                    canvas.drawCircle(x2, y2, 2.5f, nodeDimPaint)
                }
                // Dim branch
                val bx = cx + cos(seg.branchAngle) * seg.branchR * w
                val by = cy + sin(seg.branchAngle) * seg.branchR * w
                traceDimPaint.alpha = 12
                canvas.drawLine(x2, y2, bx, by, traceDimPaint)
                traceDimPaint.alpha = 20
            }
        }

        // Sakura petals floating upward
        for (petal in petals) {
            val px = petal.x * w + sin(totalSeconds * petal.drift * 3f) * 15f
            val py = h - ((totalSeconds * petal.speed * 40f + petal.x * 500f) % (h + 40f)) + 20f

            petalPaint.color = Color.argb(petal.alpha, 255, 180, 200)
            drawPetal(canvas, px, py, petal.size, totalSeconds * 0.5f + petal.x * 10f)
        }

        // Inner glow ring — progress indicator
        val innerR = maxR * 0.22f
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = sakuraPink; style = Paint.Style.STROKE; strokeWidth = 1.5f
            strokeCap = Paint.Cap.ROUND
        }
        val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(15, 255, 143, 171); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        val innerRect = android.graphics.RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        canvas.drawArc(innerRect, -90f, 360f, false, progressBgPaint)
        progressPaint.alpha = 160
        canvas.drawArc(innerRect, -90f, (minute / 60f) * 360f, false, progressPaint)

        // Time display — clean digital in center
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val timeStr = String.format("%d:%02d", h12, minute)

        timePaint.textSize = w * 0.14f
        timeGlowPaint.textSize = w * 0.14f
        val timeY = cy + timePaint.textSize * 0.12f

        canvas.drawText(timeStr, cx, timeY, timeGlowPaint)
        canvas.drawText(timeStr, cx, timeY, timePaint)

        // Seconds below
        val secStr = String.format("%02d", second)
        datePaint.textSize = w * 0.06f
        datePaint.color = Color.argb(100, 255, 143, 171)
        canvas.drawText(secStr, cx, cy + timePaint.textSize * 0.55f, datePaint)

        // Date at bottom
        datePaint.textSize = w * 0.04f
        datePaint.color = Color.argb(80, 155, 48, 255)
        val dateStr = String.format("%02d.%02d.%04d", zonedDateTime.dayOfMonth, zonedDateTime.monthValue, zonedDateTime.year)
        canvas.drawText(dateStr, cx, cy + maxR * 0.55f, datePaint)

        // AM/PM tiny
        val ampmStr = if (hour < 12) "AM" else "PM"
        datePaint.color = Color.argb(60, 232, 224, 240)
        canvas.drawText(ampmStr, cx, cy - timePaint.textSize * 0.45f, datePaint)
    }

    private fun drawPetal(canvas: Canvas, x: Float, y: Float, size: Float, rotation: Float) {
        val path = Path()
        // Simple petal shape — two overlapping ellipses
        val cosR = cos(rotation)
        val sinR = sin(rotation)

        val points = arrayOf(
            floatArrayOf(0f, -size),
            floatArrayOf(size * 0.5f, 0f),
            floatArrayOf(0f, size * 0.6f),
            floatArrayOf(-size * 0.5f, 0f)
        )

        path.moveTo(
            x + points[0][0] * cosR - points[0][1] * sinR,
            y + points[0][0] * sinR + points[0][1] * cosR
        )
        for (i in 1 until points.size) {
            path.lineTo(
                x + points[i][0] * cosR - points[i][1] * sinR,
                y + points[i][0] * sinR + points[i][1] * cosR
            )
        }
        path.close()
        canvas.drawPath(path, petalPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
