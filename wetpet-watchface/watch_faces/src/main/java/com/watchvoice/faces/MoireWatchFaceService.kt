package com.watchvoice.faces

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
 * Moiré Watch Face — Overlapping concentric circles creating
 * optical interference patterns. Hypnotic op-art inspired.
 */
class MoireWatchFaceService : WatchFaceService() {

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
        val renderer = MoireCanvasRenderer(
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.ANALOG, renderer)
    }
}

private class MoireCanvasRenderer(
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.SOFTWARE,
    interactiveDrawModeUpdateDelayMillis = 16L
) {
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    private val dimCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 0.8f
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val thinHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val secHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 220, 50, 50); style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 220, 50, 50); style = Paint.Style.FILL
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f
        val maxR = w * 0.46f

        canvas.drawColor(Color.BLACK)

        val hour = zonedDateTime.hour % 12
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second
        val nanos = zonedDateTime.nano
        val exactSecond = second + nanos / 1_000_000_000f

        // Moiré: two sets of concentric circles, slightly offset
        val ringSpacing = w * 0.035f
        val ringCount = (maxR / ringSpacing).toInt()

        // Set 1: centered
        for (i in 1..ringCount) {
            val r = i * ringSpacing
            canvas.drawCircle(cx, cy, r, if (i % 3 == 0) circlePaint else dimCirclePaint)
        }

        // Set 2: offset drifts with minute hand
        val offsetAngle = (minute + second / 60f) * 2f * PI.toFloat() / 60f
        val offsetDist = w * 0.04f
        val ox = cx + cos(offsetAngle) * offsetDist
        val oy = cy + sin(offsetAngle) * offsetDist
        for (i in 1..ringCount) {
            canvas.drawCircle(ox, oy, i * ringSpacing, dimCirclePaint)
        }

        // Hour ticks
        for (i in 0 until 12) {
            val angle = (i * 30f - 90f) * (PI.toFloat() / 180f)
            canvas.drawLine(
                cx + cos(angle) * (maxR - 12f), cy + sin(angle) * (maxR - 12f),
                cx + cos(angle) * (maxR - 2f), cy + sin(angle) * (maxR - 2f), tickPaint
            )
        }

        // Hour hand
        val ha = ((hour + minute / 60f) * 30f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ha) * maxR * 0.48f, cy + sin(ha) * maxR * 0.48f, handPaint)

        // Minute hand
        val ma = ((minute + exactSecond / 60f) * 6f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ma) * maxR * 0.72f, cy + sin(ma) * maxR * 0.72f, thinHandPaint)

        // Second hand
        val sa = (exactSecond * 6f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(
            cx - cos(sa) * 14f, cy - sin(sa) * 14f,
            cx + cos(sa) * maxR * 0.84f, cy + sin(sa) * maxR * 0.84f, secHandPaint
        )

        // Center dot
        canvas.drawCircle(cx, cy, 4f, centerPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
