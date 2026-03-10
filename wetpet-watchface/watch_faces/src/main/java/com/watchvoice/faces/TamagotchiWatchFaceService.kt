package com.watchvoice.faces

import android.graphics.*
import android.os.BatteryManager
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
import kotlin.math.sin
import kotlin.math.cos

/**
 * WatchPet — Retro pixel-art Tamagotchi watch face.
 * Uses the same WatchFaceService + CanvasRenderer pattern as all other faces.
 */
class TamagotchiWatchFaceService : WatchFaceService() {

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
        val renderer = WatchPetRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class WatchPetRenderer(
    private val context: android.content.Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.SOFTWARE,
    interactiveDrawModeUpdateDelayMillis = 500L // Update every 500ms (2fps is fine for a pet)
) {
    // ── Sprite Data ──
    // Each sprite is a 12x12 grid. Values: 0=transparent, 1=body, 2=bodyDim, 3=eyes, 4=cheeks, 5=shadow
    private val spriteIdle1 = arrayOf(
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,0,0,0,0,1,0,0,0),
        intArrayOf(0,0,1,1,0,0,0,0,1,1,0,0)
    )
    private val spriteIdle2 = arrayOf(
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,0,1,0,0,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,0,0,1,1,0,0,0)
    )
    private val spriteSleep = arrayOf(
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,5,5,1,1,5,5,1,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,1,4,1,1,1,1,1,1,4,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,0,0,0,0,1,0,0,0),
        intArrayOf(0,0,1,1,0,0,0,0,1,1,0,0)
    )

    // ── Neon Color Palette ──
    private val bodyColor = Color.argb(255, 120, 255, 160)
    private val bodyDimColor = Color.argb(255, 60, 180, 90)
    private val eyeColor = Color.argb(255, 40, 40, 50)
    private val cheekColor = Color.argb(180, 255, 140, 160)
    private val shadowColor = Color.argb(200, 60, 60, 70)

    // ── Pre-rendered Bitmaps ──
    private var bmpIdle1: Bitmap? = null
    private var bmpIdle2: Bitmap? = null
    private var bmpSleep: Bitmap? = null

    // ── Paints ──
    private val bgPaint = Paint().apply { color = Color.parseColor("#020206") }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 48f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 200); textSize = 18f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val arcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val batteryArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#50ff78"); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val stepsArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#50e6ff"); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 120, 255, 160); style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 200, 200, 200); textSize = 12f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private fun getPixelColor(value: Int): Int = when(value) {
        1 -> bodyColor; 2 -> bodyDimColor; 3 -> eyeColor; 4 -> cheekColor; 5 -> shadowColor
        else -> Color.TRANSPARENT
    }

    private fun renderSprite(sprite: Array<IntArray>, scale: Int = 10): Bitmap {
        val rows = sprite.size; val cols = sprite[0].size
        val bmp = Bitmap.createBitmap(cols * scale, rows * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val c = getPixelColor(sprite[y][x])
                if (c != Color.TRANSPARENT) {
                    paint.color = c
                    canvas.drawRect(
                        (x * scale).toFloat(), (y * scale).toFloat(),
                        ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), paint
                    )
                }
            }
        }
        return bmp
    }

    private fun ensureBitmaps() {
        if (bmpIdle1 == null) bmpIdle1 = renderSprite(spriteIdle1)
        if (bmpIdle2 == null) bmpIdle2 = renderSprite(spriteIdle2)
        if (bmpSleep == null) bmpSleep = renderSprite(spriteSleep)
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        ensureBitmaps()

        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f

        // Background
        canvas.drawRect(bounds, bgPaint)

        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second

        // ── Battery Arc (top) ──
        val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val arcRect = RectF(20f, 20f, w - 20f, h - 20f)

        canvas.drawArc(arcRect, -135f, 90f, false, arcBgPaint)
        batteryArcPaint.color = if (battery <= 30) Color.parseColor("#ff4646") else Color.parseColor("#50ff78")
        canvas.drawArc(arcRect, -135f, battery * 0.9f, false, batteryArcPaint)

        // ── Steps Arc (bottom) ──
        canvas.drawArc(arcRect, 45f, 90f, false, arcBgPaint)
        // We don't have step data from CanvasRenderer, so show a static indicator
        canvas.drawArc(arcRect, 45f, 45f, false, stepsArcPaint)

        // ── Labels ──
        canvas.drawText("BAT", cx, 50f, labelPaint)
        canvas.drawText("${battery}%", cx, 64f, labelPaint)

        // ── Pet Sprite ──
        val sprite = when {
            hour >= 23 || hour <= 5 -> bmpSleep!!
            second % 2 == 0 -> bmpIdle1!!
            else -> bmpIdle2!!
        }

        // Subtle bounce
        val bounceY = (sin(second.toDouble() * 0.5 * PI) * 3f).toFloat()
        val petSize = 120f
        val petX = cx - petSize / 2f
        val petY = cy - petSize / 2f - 15f + bounceY

        // Glow behind pet
        canvas.drawCircle(cx, cy - 15f + bounceY, petSize * 0.45f, glowPaint)

        // Draw scaled pet
        val destRect = RectF(petX, petY, petX + petSize, petY + petSize)
        val srcPaint = Paint().apply { isFilterBitmap = false } // Nearest-neighbor for pixel art
        canvas.drawBitmap(sprite, null, destRect, srcPaint)

        // ── Clock ──
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val timeStr = String.format("%d:%02d", h12, minute)
        canvas.drawText(timeStr, cx, h - 65f, clockPaint)

        // ── Date ──
        val dayNames = arrayOf("SUN","MON","TUE","WED","THU","FRI","SAT")
        val dow = zonedDateTime.dayOfWeek.value % 7  // 0=Sunday
        val day = zonedDateTime.dayOfMonth
        val mon = zonedDateTime.monthValue
        canvas.drawText("${dayNames[dow]} $mon/$day", cx, h - 42f, datePaint)

        // ── Seconds tick arc ──
        val secArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 80, 255, 120); style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
        }
        val secRect = RectF(35f, 35f, w - 35f, h - 35f)
        canvas.drawArc(secRect, -90f, second * 6f, false, secArcPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
