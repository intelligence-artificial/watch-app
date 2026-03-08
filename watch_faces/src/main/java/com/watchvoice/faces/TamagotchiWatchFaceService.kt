package com.watchvoice.faces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.BatteryManager
import android.util.Log
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
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tamagotchi Watch Face — A pixel art virtual pet that reacts to your
 * real health data. OLED dark mode with neon pixel rendering.
 *
 * Pet states:
 *  - Sleeping (nighttime 11pm-6am)
 *  - Idle (normal, gentle bounce)
 *  - Happy (good step count)
 *  - Celebrating (step goal reached)
 *  - Tired (low battery or very few steps)
 *  - Exercising (elevated heart rate / lots of movement)
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
        val healthManager = HealthDataManager(applicationContext)
        healthManager.start()
        Log.d("TamagotchiWF", "Health data manager started")

        val renderer = TamagotchiCanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            healthDataManager = healthManager
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

private class TamagotchiCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val healthDataManager: HealthDataManager
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.SOFTWARE,
    interactiveDrawModeUpdateDelayMillis = 50L // ~20fps for smooth pixel animation
) {
    // ── Color Palette ──
    private val neonGreen = Color.argb(255, 80, 255, 120)
    private val neonCyan = Color.argb(255, 80, 230, 255)
    private val neonPink = Color.argb(255, 255, 120, 180)
    private val neonYellow = Color.argb(255, 255, 230, 60)
    private val neonRed = Color.argb(255, 255, 70, 70)
    private val dimGreen = Color.argb(40, 80, 255, 120)
    private val dimCyan = Color.argb(30, 80, 230, 255)
    private val dimPink = Color.argb(30, 255, 120, 180)
    private val petBody = Color.argb(255, 120, 255, 160)
    private val petBodyDim = Color.argb(255, 60, 180, 90)
    private val petEyes = Color.argb(255, 40, 40, 50)
    private val petCheeks = Color.argb(180, 255, 140, 160)
    private val bgColor = Color.argb(255, 2, 2, 6)

    // ── Paints ──
    private val pixelPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonGreen; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val timeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 80, 255, 120); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(12f, 0f, 0f, Color.argb(60, 80, 255, 120))
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 80, 230, 255); textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = neonCyan; textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val barBgPaint = Paint().apply {
        color = Color.argb(25, 255, 255, 255); style = Paint.Style.FILL
    }
    private val barFillPaint = Paint().apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // ── Pet Sprite Data (12x12 pixel art) ──
    // 0 = empty, 1 = body, 2 = body dark, 3 = eyes, 4 = cheeks, 5 = mouth/nose
    private val spriteIdle1 = arrayOf(
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,1,2,0,0,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
    )

    private val spriteIdle2 = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,1,3,1,1,1,1,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
    )

    // Happy: eyes are arched (^_^), little bounce
    private val spriteHappy1 = arrayOf(
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,0,1,1,1,1,0,1,1,0),
        intArrayOf(0,1,1,3,0,1,1,0,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,1,2,0,0,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
    )

    private val spriteHappy2 = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,0,1,1,1,1,0,1,1,0),
        intArrayOf(0,1,1,3,0,1,1,0,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
    )

    // Sleeping: eyes closed as lines, zzZ
    private val spriteSleep = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,5,5,1,1,5,5,1,1,0),
        intArrayOf(0,1,4,1,1,1,1,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
    )

    // Tired/sad: droopy eyes, slumped
    private val spriteTired = arrayOf(
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
        intArrayOf(0,0,0,0,1,1,1,1,0,0,0,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,3,3,1,1,3,3,1,0,0),
        intArrayOf(0,0,1,1,3,1,1,3,1,1,0,0),
        intArrayOf(0,0,4,1,1,5,5,1,1,4,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
    )

    // Celebrating: arms up! (wider sprite)
    private val spriteCelebrate1 = arrayOf(
        intArrayOf(0,1,0,0,0,0,0,0,0,0,1,0),
        intArrayOf(0,1,0,0,1,1,1,1,0,0,1,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,0,1,1,1,1,0,1,1,0),
        intArrayOf(0,1,1,3,0,1,1,0,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,1,1,1,1,1,1,1,1,1,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,1,2,0,0,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
    )

    private val spriteCelebrate2 = arrayOf(
        intArrayOf(1,0,0,0,0,0,0,0,0,0,0,1),
        intArrayOf(0,1,0,0,1,1,1,1,0,0,1,0),
        intArrayOf(0,0,0,1,1,1,1,1,1,0,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,1,1,0,1,1,1,1,0,1,1,0),
        intArrayOf(0,1,1,3,0,1,1,0,3,1,1,0),
        intArrayOf(0,1,4,1,1,5,5,1,1,4,1,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,1,1,1,1,1,1,1,1,0,0),
        intArrayOf(0,0,0,1,2,1,1,2,1,0,0,0),
        intArrayOf(0,0,0,2,2,0,0,2,2,0,0,0),
        intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0),
    )

    // ── Particle System ──
    private data class Particle(var x: Float, var y: Float, var vy: Float, var life: Float,
                                var color: Int, var type: Int) // type: 0=heart, 1=star, 2=zzz, 3=sweat, 4=confetti

    private val particles = mutableListOf<Particle>()
    private var lastParticleTime = 0f

    // ── State ──
    private enum class PetMood { IDLE, HAPPY, CELEBRATING, TIRED, SLEEPING }
    private var currentMood = PetMood.IDLE

    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun determineMood(hour: Int, battery: Int, steps: Int, heartRate: Int): PetMood {
        // Night time = sleeping
        if (hour >= 23 || hour < 6) return PetMood.SLEEPING

        // Low battery = tired
        if (battery < 20) return PetMood.TIRED

        // Mood driven by real step count
        return when {
            steps >= 10000 -> PetMood.CELEBRATING  // Goal reached!
            steps >= 7000  -> PetMood.HAPPY         // Getting there
            steps >= 2000  -> PetMood.IDLE           // Chillin'
            else           -> PetMood.TIRED          // Barely moved
        }
    }

    private fun getSprite(mood: PetMood, frame: Int): Array<IntArray> {
        return when (mood) {
            PetMood.IDLE -> if (frame == 0) spriteIdle1 else spriteIdle2
            PetMood.HAPPY -> if (frame == 0) spriteHappy1 else spriteHappy2
            PetMood.CELEBRATING -> if (frame == 0) spriteCelebrate1 else spriteCelebrate2
            PetMood.TIRED -> spriteTired
            PetMood.SLEEPING -> spriteSleep
        }
    }

    private fun getPixelColor(value: Int): Int {
        return when (value) {
            1 -> petBody
            2 -> petBodyDim
            3 -> petEyes
            4 -> petCheeks
            5 -> Color.argb(200, 60, 60, 70)
            else -> 0
        }
    }

    private fun drawSprite(canvas: Canvas, sprite: Array<IntArray>, cx: Float, cy: Float,
                           pixSize: Float, yOffset: Float) {
        val rows = sprite.size
        val cols = sprite[0].size
        val startX = cx - (cols * pixSize) / 2f
        val startY = cy - (rows * pixSize) / 2f + yOffset

        // Glow under pet
        glowPaint.color = Color.argb(15, 80, 255, 120)
        canvas.drawCircle(cx, cy + yOffset + rows * pixSize * 0.3f, cols * pixSize * 0.4f, glowPaint)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val v = sprite[row][col]
                if (v == 0) continue
                val color = getPixelColor(v)
                pixelPaint.color = color
                val px = startX + col * pixSize
                val py = startY + row * pixSize
                canvas.drawRect(px, py, px + pixSize - 1f, py + pixSize - 1f, pixelPaint)
            }
        }
    }

    private fun spawnParticles(mood: PetMood, cx: Float, cy: Float, totalSeconds: Float) {
        if (totalSeconds - lastParticleTime < 0.4f) return
        lastParticleTime = totalSeconds

        val seed = (totalSeconds * 1000).toLong()
        when (mood) {
            PetMood.HAPPY -> {
                // Float hearts upward
                particles.add(Particle(
                    x = cx + ((seed % 60) - 30),
                    y = cy - 30f,
                    vy = -0.8f,
                    life = 1.5f,
                    color = neonPink,
                    type = 0
                ))
            }
            PetMood.CELEBRATING -> {
                // Confetti burst
                for (i in 0..1) {
                    val colors = intArrayOf(neonGreen, neonPink, neonCyan, neonYellow)
                    particles.add(Particle(
                        x = cx + ((seed + i * 137) % 80) - 40,
                        y = cy - 50f,
                        vy = -1.2f + (((seed + i * 71) % 10) / 10f) * 0.6f,
                        life = 2f,
                        color = colors[((seed + i) % 4).toInt()],
                        type = 4
                    ))
                }
            }
            PetMood.SLEEPING -> {
                // Float Z's
                particles.add(Particle(
                    x = cx + 40f,
                    y = cy - 20f,
                    vy = -0.5f,
                    life = 2f,
                    color = Color.argb(120, 80, 230, 255),
                    type = 2
                ))
            }
            PetMood.TIRED -> {
                // Sweat drops
                if (seed % 3 == 0L) {
                    particles.add(Particle(
                        x = cx + 35f, y = cy - 10f, vy = 0.6f,
                        life = 1f, color = neonCyan, type = 3
                    ))
                }
            }
            else -> {}
        }
    }

    private fun updateAndDrawParticles(canvas: Canvas, dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            if (p.life <= 0f) { iter.remove(); continue }
            p.y += p.vy
            p.x += sin(p.life * 3f) * 0.3f // gentle sway

            val alpha = (p.life.coerceIn(0f, 1f) * 255).toInt()

            when (p.type) {
                0 -> { // Heart ♥
                    statusPaint.color = Color.argb(alpha, 255, 120, 180)
                    statusPaint.textSize = 14f
                    statusPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("♥", p.x, p.y, statusPaint)
                }
                1 -> { // Star ★
                    statusPaint.color = Color.argb(alpha, 255, 230, 60)
                    statusPaint.textSize = 12f
                    statusPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("★", p.x, p.y, statusPaint)
                }
                2 -> { // Zzz
                    statusPaint.color = Color.argb(alpha, 80, 230, 255)
                    statusPaint.textSize = 10f + (2f - p.life) * 4f
                    statusPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText("z", p.x, p.y, statusPaint)
                }
                3 -> { // Sweat drop
                    pixelPaint.color = Color.argb(alpha, 80, 230, 255)
                    canvas.drawCircle(p.x, p.y, 2f, pixelPaint)
                }
                4 -> { // Confetti
                    pixelPaint.color = Color.argb(alpha,
                        Color.red(p.color), Color.green(p.color), Color.blue(p.color))
                    canvas.drawRect(p.x - 2f, p.y - 2f, p.x + 2f, p.y + 2f, pixelPaint)
                }
            }
        }
    }

    private fun drawStatusBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
                               fill: Float, color: Int, label: String, value: String) {
        // Label
        labelPaint.textSize = h * 1.4f
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, y - 3f, labelPaint)

        // Value
        statusPaint.textSize = h * 1.4f
        statusPaint.textAlign = Paint.Align.RIGHT
        statusPaint.color = color
        canvas.drawText(value, x + w, y - 3f, statusPaint)

        // Bar background
        canvas.drawRect(x, y, x + w, y + h, barBgPaint)

        // Bar fill
        barFillPaint.color = color
        barFillPaint.alpha = 180
        canvas.drawRect(x, y, x + w * fill.coerceIn(0f, 1f), y + h, barFillPaint)

        // Bar glow
        barFillPaint.alpha = 30
        canvas.drawRect(x, y - 1f, x + w * fill.coerceIn(0f, 1f), y + h + 1f, barFillPaint)
    }

    private fun drawScanLines(canvas: Canvas, w: Float, h: Float, totalSeconds: Float) {
        val scanPaint = Paint().apply {
            color = Color.argb(6, 255, 255, 255); style = Paint.Style.FILL
        }
        var y = (totalSeconds * 8f) % 4f
        while (y < h) {
            canvas.drawRect(0f, y, w, y + 1f, scanPaint)
            y += 4f
        }
    }

    private fun drawPixelBorder(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, radius: Float) {
        val borderPaint = Paint().apply {
            color = Color.argb(20, 80, 255, 120); style = Paint.Style.STROKE; strokeWidth = 1f
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Corner brackets
        val m = 20f; val cl = 18f
        val cPaint = Paint().apply {
            color = Color.argb(35, 80, 255, 120); style = Paint.Style.STROKE
            strokeWidth = 1.5f; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(m, m + cl, m, m, cPaint); canvas.drawLine(m, m, m + cl, m, cPaint)
        canvas.drawLine(w - m - cl, m, w - m, m, cPaint); canvas.drawLine(w - m, m, w - m, m + cl, cPaint)
        canvas.drawLine(m, h - m - cl, m, h - m, cPaint); canvas.drawLine(m, h - m, m + cl, h - m, cPaint)
        canvas.drawLine(w - m - cl, h - m, w - m, h - m, cPaint); canvas.drawLine(w - m, h - m - cl, w - m, h - m, cPaint)
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f

        canvas.drawColor(bgColor)

        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second
        val nanos = zonedDateTime.nano
        val totalSeconds = hour * 3600f + minute * 60f + second + nanos / 1_000_000_000f
        val dt = 0.05f // approximate frame delta

        val battery = getBatteryLevel()
        val realSteps = healthDataManager.dailySteps
        val realHR = healthDataManager.heartRate
        currentMood = determineMood(hour, battery, realSteps, realHR)

        // Scan lines
        drawScanLines(canvas, w, h, totalSeconds)

        // Pixel border
        drawPixelBorder(canvas, w, h, cx, cy, w * 0.46f)

        // ── TIME ──
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val timeStr = String.format("%d:%02d", h12, minute)
        timePaint.textSize = w * 0.16f
        timeGlowPaint.textSize = w * 0.16f

        val timeY = cy - w * 0.24f
        canvas.drawText(timeStr, cx, timeY, timeGlowPaint)
        canvas.drawText(timeStr, cx, timeY, timePaint)

        // AM/PM
        val ampm = if (hour < 12) "AM" else "PM"
        labelPaint.textSize = w * 0.04f
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = Color.argb(80, 80, 255, 120)
        canvas.drawText(ampm, cx + w * 0.14f, timeY - w * 0.06f, labelPaint)

        // Date
        labelPaint.textSize = w * 0.04f
        labelPaint.color = Color.argb(60, 80, 230, 255)
        val dayNames = arrayOf("MON","TUE","WED","THU","FRI","SAT","SUN")
        val dow = dayNames[(zonedDateTime.dayOfWeek.value - 1).coerceIn(0, 6)]
        val dateStr = String.format("%s %02d.%02d", dow, zonedDateTime.monthValue, zonedDateTime.dayOfMonth)
        canvas.drawText(dateStr, cx, timeY + w * 0.05f, labelPaint)

        // ── MOOD LABEL ──
        val moodText = when (currentMood) {
            PetMood.IDLE -> "chillin'"
            PetMood.HAPPY -> "feeling great!"
            PetMood.CELEBRATING -> "★ GOAL! ★"
            PetMood.TIRED -> "need charge..."
            PetMood.SLEEPING -> "zzZzz..."
        }
        val moodColor = when (currentMood) {
            PetMood.IDLE -> Color.argb(80, 80, 255, 120)
            PetMood.HAPPY -> Color.argb(120, 255, 230, 60)
            PetMood.CELEBRATING -> Color.argb(200, 255, 230, 60)
            PetMood.TIRED -> Color.argb(100, 255, 70, 70)
            PetMood.SLEEPING -> Color.argb(60, 80, 230, 255)
        }
        labelPaint.textSize = w * 0.04f
        labelPaint.color = moodColor
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(moodText, cx, cy - w * 0.105f, labelPaint)

        // ── PET ──
        val animFrame = ((totalSeconds * 2f).toInt() % 2)
        val sprite = getSprite(currentMood, animFrame)
        val pixSize = w * 0.028f
        val bounceY = when (currentMood) {
            PetMood.IDLE -> sin(totalSeconds * 2.5f) * 3f
            PetMood.HAPPY -> sin(totalSeconds * 4f) * 5f
            PetMood.CELEBRATING -> abs(sin(totalSeconds * 5f)) * -8f
            PetMood.TIRED -> 4f
            PetMood.SLEEPING -> sin(totalSeconds * 0.8f) * 1.5f
        }
        drawSprite(canvas, sprite, cx, cy + w * 0.02f, pixSize, bounceY)

        // ── PARTICLES ──
        spawnParticles(currentMood, cx, cy, totalSeconds)
        updateAndDrawParticles(canvas, dt)

        // ── STATUS BARS ──
        val barW = w * 0.5f
        val barH = 3f
        val barX = cx - barW / 2f
        val barStartY = cy + w * 0.22f

        // Steps bar (real data from Health Services)
        val stepGoal = 10000f
        val stepsDisplay = if (realSteps > 0) String.format("%,d", realSteps) else "--"
        drawStatusBar(canvas, barX, barStartY, barW, barH,
            realSteps / stepGoal, neonCyan, "STEPS", stepsDisplay)

        // Heart rate (real data from Health Services)
        val hrDisplay = if (realHR > 0) realHR.toString() else "--"
        drawStatusBar(canvas, barX, barStartY + 18f, barW, barH,
            realHR / 180f, neonPink, "♥ BPM", hrDisplay)

        // Battery
        drawStatusBar(canvas, barX, barStartY + 36f, barW, barH,
            battery / 100f,
            if (battery > 30) neonGreen else neonRed,
            "PWR", "$battery%")

        // ── Second dots (bottom) ──
        val dotRadius = 1.5f
        val dotArcR = w * 0.40f
        for (i in 0 until 60) {
            val angle = (i * 6f - 90f) * (Math.PI.toFloat() / 180f)
            val lit = i <= second
            pixelPaint.color = if (lit) Color.argb(120, 80, 255, 120) else Color.argb(15, 80, 255, 120)
            canvas.drawCircle(
                cx + kotlin.math.cos(angle) * dotArcR,
                cy + kotlin.math.sin(angle) * dotArcR,
                dotRadius, pixelPaint
            )
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }
}
