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
 * Void Mesh Watch Face — Glowing tri-color wireframe sphere
 * with animated breathing and rotation. Vibrant on OLED.
 */
class VoidMeshWatchFaceService : WatchFaceService() {

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
        val renderer = VoidMeshCanvasRenderer(
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.ANALOG, renderer)
    }
}

private class VoidMeshCanvasRenderer(
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
    // Tri-color palette: cyan, magenta, amber
    private val coreColors = intArrayOf(
        Color.argb(200, 0, 230, 255),    // cyan
        Color.argb(200, 255, 50, 180),   // magenta
        Color.argb(200, 255, 180, 0)     // amber
    )
    private val glowColors = intArrayOf(
        Color.argb(60, 0, 230, 255),
        Color.argb(60, 255, 50, 180),
        Color.argb(60, 255, 180, 0)
    )
    private val dotColors = intArrayOf(
        Color.argb(200, 0, 230, 255),
        Color.argb(200, 255, 50, 180),
        Color.argb(200, 255, 180, 0)
    )

    private val meshGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val meshCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
    }
    private val thinHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
    }
    private val secDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 60, 60); style = Paint.Style.FILL
    }
    private val secGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 60, 60); style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND
    }
    private val hourTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
    }

    private val latSteps = 10
    private val lonSteps = 14

    private fun projectSphere(
        theta: Float, phi: Float, radius: Float,
        cx: Float, cy: Float, rotY: Float, rotX: Float
    ): Triple<Float, Float, Float>? {
        var x = radius * sin(theta) * cos(phi)
        var y = radius * sin(theta) * sin(phi)
        var z = radius * cos(theta)

        // Rotate Y
        val cosRY = cos(rotY); val sinRY = sin(rotY)
        val nx = x * cosRY + z * sinRY; val nz = -x * sinRY + z * cosRY
        x = nx; z = nz

        // Rotate X
        val cosRX = cos(rotX); val sinRX = sin(rotX)
        val ny = y * cosRX - z * sinRX; val nz2 = y * sinRX + z * cosRX
        y = ny; z = nz2

        val perspective = 2.8f * radius
        val scale = perspective / (perspective + z)
        if (scale < 0.15f) return null
        return Triple(cx + x * scale, cy + y * scale, z)
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
        val totalSeconds = hour * 3600f + minute * 60f + second + nanos / 1_000_000_000f

        // Sphere with breathing
        val sphereR = w * 0.28f * (1f + 0.03f * sin(totalSeconds * 0.5f))
        val rotY = totalSeconds * 0.08f
        val rotX = totalSeconds * 0.03f

        // Latitude lines
        for (lat in 1 until latSteps) {
            val theta = lat * PI.toFloat() / latSteps
            val ci = lat % 3
            var prev: Triple<Float, Float, Float>? = null
            for (lon in 0..lonSteps) {
                val phi = lon * 2f * PI.toFloat() / lonSteps
                val pt = projectSphere(theta, phi, sphereR, cx, cy, rotY, rotX)
                if (pt == null) { prev = null } else {
                    if (prev != null) {
                        val depthA = ((1f - pt.third / sphereR) * 0.5f + 0.5f).coerceIn(0.2f, 1f)
                        meshGlowPaint.color = glowColors[ci]; meshGlowPaint.alpha = (60 * depthA).toInt()
                        meshCorePaint.color = coreColors[ci]; meshCorePaint.alpha = (180 * depthA).toInt()
                        canvas.drawLine(prev!!.first, prev!!.second, pt.first, pt.second, meshGlowPaint)
                        canvas.drawLine(prev!!.first, prev!!.second, pt.first, pt.second, meshCorePaint)
                    }
                    prev = pt
                }
            }
        }

        // Longitude lines
        for (lon in 0 until lonSteps) {
            val phi = lon * 2f * PI.toFloat() / lonSteps
            val ci = lon % 3
            var prev: Triple<Float, Float, Float>? = null
            for (lat in 0..latSteps) {
                val theta = lat * PI.toFloat() / latSteps
                val pt = projectSphere(theta, phi, sphereR, cx, cy, rotY, rotX)
                if (pt == null) { prev = null } else {
                    if (prev != null) {
                        val depthA = ((1f - pt.third / sphereR) * 0.5f + 0.5f).coerceIn(0.2f, 1f)
                        meshGlowPaint.color = glowColors[ci]; meshGlowPaint.alpha = (50 * depthA).toInt()
                        meshCorePaint.color = coreColors[ci]; meshCorePaint.alpha = (160 * depthA).toInt()
                        canvas.drawLine(prev!!.first, prev!!.second, pt.first, pt.second, meshGlowPaint)
                        canvas.drawLine(prev!!.first, prev!!.second, pt.first, pt.second, meshCorePaint)
                    }
                    prev = pt
                }
            }
        }

        // Glowing dots at intersections
        for (lat in 1 until latSteps) {
            val theta = lat * PI.toFloat() / latSteps
            for (lon in 0 until lonSteps) {
                val phi = lon * 2f * PI.toFloat() / lonSteps
                val pt = projectSphere(theta, phi, sphereR, cx, cy, rotY, rotX) ?: continue
                val depthA = ((1f - pt.third / sphereR) * 0.5f + 0.5f).coerceIn(0.2f, 1f)
                val ci = (lat + lon) % 3
                // Glow dot
                dotPaint.color = glowColors[ci]; dotPaint.alpha = (80 * depthA).toInt()
                canvas.drawCircle(pt.first, pt.second, 3f, dotPaint)
                // Core dot
                dotPaint.color = dotColors[ci]; dotPaint.alpha = (200 * depthA).toInt()
                canvas.drawCircle(pt.first, pt.second, 1.5f, dotPaint)
            }
        }

        // Ticks
        for (i in 0 until 60) {
            val angle = (i * 6f - 90f) * (PI.toFloat() / 180f)
            val isHour = i % 5 == 0
            val inner = maxR - if (isHour) 10f else 4f
            canvas.drawLine(
                cx + cos(angle) * inner, cy + sin(angle) * inner,
                cx + cos(angle) * (maxR - 1f), cy + sin(angle) * (maxR - 1f),
                if (isHour) hourTickPaint else tickPaint
            )
        }

        // Hour hand
        val ha = ((hour + minute / 60f) * 30f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ha) * maxR * 0.48f, cy + sin(ha) * maxR * 0.48f, handPaint)

        // Minute hand
        val ma = ((minute + exactSecond / 60f) * 6f - 90f) * (PI.toFloat() / 180f)
        canvas.drawLine(cx, cy, cx + cos(ma) * maxR * 0.72f, cy + sin(ma) * maxR * 0.72f, thinHandPaint)

        // Second dot with glow
        val sa = (exactSecond * 6f - 90f) * (PI.toFloat() / 180f)
        val sx = cx + cos(sa) * maxR * 0.84f; val sy = cy + sin(sa) * maxR * 0.84f
        canvas.drawCircle(sx, sy, 7f, secGlowPaint)
        canvas.drawCircle(sx, sy, 3.5f, secDotPaint)

        // Center
        canvas.drawCircle(cx, cy, 3f, centerPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
