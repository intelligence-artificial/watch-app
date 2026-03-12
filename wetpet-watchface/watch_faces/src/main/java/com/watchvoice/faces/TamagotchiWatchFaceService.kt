package com.watchvoice.faces

import android.content.Context
import android.graphics.*
import android.os.BatteryManager
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
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
 * WatchPet V2 — Scuba Dive Computer Tamagotchi watch face.
 *
 * Design rules:
 * - Concentric arc zones with background tracks, glow fills, tick boundaries
 * - 3 complication arcs: Steps (bottom-left), BPM (bottom-right), Battery (top)
 * - Seconds ticker ring (360° innermost)
 * - Pet sprite center with emotion-driven glow
 * - Proper ambient mode (dim gray, no glow, 1-min updates)
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
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.SOFTWARE,
    interactiveDrawModeUpdateDelayMillis = 500L
) {
    // ── Drawable sprite cache ──
    private val spriteCache = mutableMapOf<String, Bitmap?>()
    private val TAG = "WF_PetSync"

    private fun loadSprite(name: String): Bitmap? {
        return spriteCache.getOrPut(name) {
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            android.util.Log.d(TAG, "loadSprite: name=$name pkg=${context.packageName} resId=$resId")
            if (resId != 0) {
                val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
                android.util.Log.d(TAG, "loadSprite: decoded=$name → ${bmp?.width}x${bmp?.height}")
                bmp
            } else {
                android.util.Log.e(TAG, "loadSprite: RESOURCE NOT FOUND for '$name' in ${context.packageName}")
                null
            }
        }
    }

    /**
     * Read pet config from the WetPet wear app via ContentProvider.
     * Queries content://com.tamagotchi.pet.config/state
     * Caches result for 10 seconds to avoid querying every frame.
     */
    private var cachedConfig: Triple<String, String, String>? = null
    private var cacheTimestamp = 0L

    private fun readWearAppPrefs(): Triple<String, String, String> {
        val now = System.currentTimeMillis()
        val cached = cachedConfig
        if (cached != null && (now - cacheTimestamp) < 10_000) {
            return cached
        }

        android.util.Log.d(TAG, "readWearAppPrefs: querying ContentProvider...")

        val result = try {
            val uri = android.net.Uri.parse("content://com.tamagotchi.pet.config/state")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            android.util.Log.d(TAG, "readWearAppPrefs: cursor=${cursor != null}, count=${cursor?.count ?: -1}")

            cursor?.use {
                if (it.moveToFirst()) {
                    val petType = it.getString(it.getColumnIndexOrThrow("pet_type"))
                    val colorTheme = it.getString(it.getColumnIndexOrThrow("color_theme"))
                    val emotion = it.getString(it.getColumnIndexOrThrow("emotion"))
                    android.util.Log.d(TAG, "readWearAppPrefs: petType=$petType colorTheme=$colorTheme emotion=$emotion")

                    val petPrefix = when (petType) {
                        "BLOB" -> "pet"
                        "CAT" -> "cat"
                        "DOG" -> "dog"
                        else -> "dog"
                    }
                    Triple(petPrefix, colorTheme.lowercase(), emotion)
                } else {
                    android.util.Log.w(TAG, "readWearAppPrefs: cursor empty!")
                    Triple("dog", "green", "IDLE")
                }
            } ?: run {
                android.util.Log.e(TAG, "readWearAppPrefs: cursor is NULL — provider not found?")
                Triple("dog", "green", "IDLE")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "readWearAppPrefs: EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            Triple("dog", "green", "IDLE")
        }

        android.util.Log.d(TAG, "readWearAppPrefs: result=$result")
        cachedConfig = result
        cacheTimestamp = now
        return result
    }

    private fun getSprite(hour: Int, second: Int, isAmbient: Boolean): Bitmap? {
        val (pet, theme, _) = readWearAppPrefs()
        val spriteName = when {
            isAmbient -> "${pet}_sleep_aod"
            hour >= 23 || hour <= 5 -> "${pet}_sleep_$theme"
            second % 2 == 0 -> "${pet}_idle_1_$theme"
            else -> "${pet}_idle_2_$theme"
        }
        android.util.Log.d(TAG, "getSprite: loading '$spriteName' (pet=$pet theme=$theme)")
        return loadSprite(spriteName)
    }

    // ── Colors for arc complications ──
    private val stepsColor = Color.argb(255, 0, 230, 120)
    private val bpmColor = Color.argb(255, 255, 75, 100)
    private val batteryColor = Color.argb(255, 0, 200, 255)

    // ── Core paints ──
    private val bgPaint = Paint().apply { color = Color.parseColor("#020206") }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val clockGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 120, 255, 160)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 200, 200, 200)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 120, 255, 160); style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
    }

    // ── Health data ──
    private val healthDataManager = HealthDataManager(context)

    init {
        healthDataManager.start()
    }


    // ═══════════════════════════════════════════════════════════════
    //  SCUBA-STYLE ARC COMPLICATION RENDERER
    //  Adapted from user's design rules — always draws track, glow,
    //  tick boundaries, and labeled value at arc midpoint.
    // ═══════════════════════════════════════════════════════════════

    /**
     * Draws a scuba-style arc complication (steps, BPM, battery etc.)
     * @param canvas    Canvas to draw on
     * @param cx/cy     Center of watch face
     * @param radius    Arc ring radius from center
     * @param trackWidth Stroke width of the ring (6-10f)
     * @param startAngle Degrees from 3-o'clock
     * @param sweepTotal Total arc span in degrees
     * @param progress  0f–1f fill fraction
     * @param fillColor Active arc color (ARGB with full alpha)
     * @param label     Short text label ("BPM", "STEPS")
     * @param value     Data value string ("72", "4,231")
     * @param isAmbient Whether in ambient/AOD mode
     */
    private fun drawArcComplication(
        canvas: Canvas, cx: Float, cy: Float,
        radius: Float, trackWidth: Float,
        startAngle: Float, sweepTotal: Float,
        progress: Float,
        fillColor: Int, label: String, value: String,
        isAmbient: Boolean = false
    ) {
        val oval = RectF(
            cx - radius, cy - radius, cx + radius, cy + radius
        )

        // 1. Background track — same arc, dim
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = trackWidth
            strokeCap = Paint.Cap.ROUND
            color = if (isAmbient) {
                Color.argb(20, 150, 150, 150)
            } else {
                Color.argb(35, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
            }
        }
        canvas.drawArc(oval, startAngle, sweepTotal, false, trackPaint)

        // 2. Fill arc — progress portion with glow shadow
        if (progress > 0.01f) {
            if (!isAmbient) {
                // Glow layer (wider, transparent)
                val glowArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = trackWidth + 6f
                    strokeCap = Paint.Cap.ROUND
                    color = Color.argb(30,
                        Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
                }
                canvas.drawArc(oval, startAngle, sweepTotal * progress, false, glowArcPaint)
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = trackWidth
                strokeCap = Paint.Cap.ROUND
                color = if (isAmbient) Color.argb(140, 180, 180, 180) else fillColor
            }
            canvas.drawArc(oval, startAngle, sweepTotal * progress, false, fillPaint)
        }

        // 3. End-cap tick marks at arc boundaries
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f
            color = if (isAmbient) {
                Color.argb(30, 150, 150, 150)
            } else {
                Color.argb(60, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
            }
        }
        val a0 = Math.toRadians(startAngle.toDouble())
        val a1 = Math.toRadians((startAngle + sweepTotal).toDouble())
        val inner = radius - trackWidth / 2f - 3f
        val outer = radius + trackWidth / 2f + 3f

        canvas.drawLine(
            (cx + cos(a0) * inner).toFloat(), (cy + sin(a0) * inner).toFloat(),
            (cx + cos(a0) * outer).toFloat(), (cy + sin(a0) * outer).toFloat(), tickPaint)
        canvas.drawLine(
            (cx + cos(a1) * inner).toFloat(), (cy + sin(a1) * inner).toFloat(),
            (cx + cos(a1) * outer).toFloat(), (cy + sin(a1) * outer).toFloat(), tickPaint)

        // 4. Label + value near arc midpoint
        val midAngle = Math.toRadians((startAngle + sweepTotal * 0.5).toDouble())
        val labelR = radius - trackWidth / 2f - 14f
        val lx = (cx + cos(midAngle) * labelR).toFloat()
        val ly = (cy + sin(midAngle) * labelR).toFloat()

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 18f
            color = if (isAmbient) Color.argb(180, 200, 200, 200) else fillColor
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; textSize = 11f
            color = if (isAmbient) {
                Color.argb(100, 150, 150, 150)
            } else {
                Color.argb(150, Color.red(fillColor), Color.green(fillColor), Color.blue(fillColor))
            }
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText(value, lx, ly, valPaint)
        canvas.drawText(label, lx, ly + 14f, lblPaint)
    }

    // ═══════════════════════════════════════════════════════════════
    //  MAIN RENDER LOOP
    // ═══════════════════════════════════════════════════════════════

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {

        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = w / 2f; val cy = h / 2f
        val maxR = w * 0.46f
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

        // Background
        canvas.drawRect(bounds, bgPaint)

        val hour = zonedDateTime.hour
        val minute = zonedDateTime.minute
        val second = zonedDateTime.second

        // Health data
        val steps = healthDataManager.dailySteps
        val bpm = healthDataManager.heartRate
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

        // Read pet mood from the WetPet wear app's SharedPreferences
        val (_, _, emotionName) = readWearAppPrefs()
        val emotionColor = getEmotionColor(emotionName)

        // ──────── SECONDS TICKER RING (outermost, full 360°) ────────
        if (!isAmbient) {
            val secRadius = maxR * 0.96f
            val secRect = RectF(cx - secRadius, cy - secRadius, cx + secRadius, cy + secRadius)

            // Background track
            val secTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(10, 80, 255, 120); style = Paint.Style.STROKE
                strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(secRect, -90f, 360f, false, secTrackPaint)

            // Fill
            val secFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(55, 80, 255, 120); style = Paint.Style.STROKE
                strokeWidth = 3f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(secRect, -90f, second * 6f, false, secFillPaint)

            // 12 tick marks at each hour position
            val secTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(30, 120, 255, 160); style = Paint.Style.STROKE; strokeWidth = 1f
            }
            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30.0) - 90.0)
                val innerR = secRadius - 5f
                val outerR = secRadius + 5f
                canvas.drawLine(
                    (cx + cos(angle) * innerR).toFloat(), (cy + sin(angle) * innerR).toFloat(),
                    (cx + cos(angle) * outerR).toFloat(), (cy + sin(angle) * outerR).toFloat(),
                    secTickPaint
                )
            }
        }

        // ──────── ARC COMPLICATIONS (3 rings, same radius) ────────

        // Steps — bottom-left arc (green)
        val stepsProgress = (steps / 10000f).coerceIn(0f, 1f)
        val stepsValue = if (steps > 0) steps.toString() else "--"
        drawArcComplication(canvas, cx, cy,
            radius = maxR * 0.82f, trackWidth = 7f,
            startAngle = 150f, sweepTotal = 80f,
            progress = stepsProgress,
            fillColor = stepsColor,
            label = "STEPS", value = stepsValue,
            isAmbient = isAmbient
        )

        // BPM — bottom-right arc (red/pink)
        val bpmProgress = if (bpm > 0) ((bpm - 40f) / 160f).coerceIn(0f, 1f) else 0f
        val bpmValue = if (bpm > 0) bpm.toString() else "--"
        drawArcComplication(canvas, cx, cy,
            radius = maxR * 0.82f, trackWidth = 7f,
            startAngle = 310f, sweepTotal = 80f,
            progress = bpmProgress,
            fillColor = bpmColor,
            label = "BPM", value = bpmValue,
            isAmbient = isAmbient
        )

        // Battery — top arc (cyan), wider sweep
        val batFillColor = if (battery <= 20) Color.argb(255, 255, 70, 70) else batteryColor
        drawArcComplication(canvas, cx, cy,
            radius = maxR * 0.82f, trackWidth = 7f,
            startAngle = 200f, sweepTotal = 140f,
            progress = battery / 100f,
            fillColor = batFillColor,
            label = "BAT", value = "$battery%",
            isAmbient = isAmbient
        )

        // ──────── EMOTION GLOW RING (around pet zone) ────────
        if (!isAmbient) {
            val glowR = maxR * 0.52f
            val glowRect = RectF(cx - glowR, cy - glowR, cx + glowR, cy + glowR)
            val pulseAlpha = (20 + (sin(System.currentTimeMillis() / 1500.0) * 12).toInt()).coerceIn(8, 35)

            val emotionGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.5f
                color = Color.argb(pulseAlpha,
                    Color.red(emotionColor), Color.green(emotionColor), Color.blue(emotionColor))
            }
            canvas.drawArc(glowRect, 0f, 360f, false, emotionGlowPaint)
        }

        // ──────── PET SPRITE (center) ────────
        val sprite = getSprite(hour, second, isAmbient)

        // Bounce animation (suppressed in ambient)
        val bounceY = if (isAmbient) 0f else (sin(second.toDouble() * 0.5 * PI) * 3f).toFloat()
        val petSize = 100f
        val petX = cx - petSize / 2f
        val petY = cy - petSize / 2f - 20f + bounceY

        // Glow behind pet
        if (!isAmbient) {
            glowPaint.color = Color.argb(25,
                Color.red(emotionColor), Color.green(emotionColor), Color.blue(emotionColor))
            canvas.drawCircle(cx, cy - 20f + bounceY, petSize * 0.4f, glowPaint)
        }

        // Draw pet (nearest-neighbor scaling for pixel art)
        if (sprite != null) {
            val destRect = RectF(petX, petY, petX + petSize, petY + petSize)
            val srcPaint = Paint().apply { isFilterBitmap = false }
            canvas.drawBitmap(sprite, null, destRect, srcPaint)
        }

        // ──────── CLOCK (below pet) ────────
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val timeStr = String.format("%d:%02d", h12, minute)

        clockPaint.textSize = w * 0.11f
        clockGlowPaint.textSize = w * 0.11f
        val timeY = cy + petSize / 2f + 10f

        if (isAmbient) {
            clockPaint.color = Color.argb(200, 200, 200, 200)
        } else {
            clockPaint.color = Color.WHITE
            canvas.drawText(timeStr, cx, timeY, clockGlowPaint)
        }
        canvas.drawText(timeStr, cx, timeY, clockPaint)

        // ──────── DATE (tiny, below clock) ────────
        if (!isAmbient) {
            datePaint.textSize = 14f
            val dayNames = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
            val dow = zonedDateTime.dayOfWeek.value % 7
            val day = zonedDateTime.dayOfMonth
            val mon = zonedDateTime.monthValue
            canvas.drawText("${dayNames[dow]} $mon/$day", cx, timeY + 18f, datePaint)
        }

        // ──────── EMOTION STATUS TEXT (bottom center) ────────
        if (!isAmbient) {
            val moodLabel = getEmotionLabel(emotionName)
            val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f; textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                color = Color.argb(140,
                    Color.red(emotionColor), Color.green(emotionColor), Color.blue(emotionColor))
            }
            canvas.drawText(moodLabel, cx, timeY + 36f, moodPaint)
        }
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        canvas.drawColor(Color.argb(60, 0, 0, 0))
    }

    // ── Emotion helpers ──

    private fun getEmotionColor(emotionName: String): Int {
        return when (emotionName) {
            "CRITICAL" -> Color.parseColor("#FF4646")
            "SICK" -> Color.parseColor("#B0C0B0")
            "EXHAUSTED" -> Color.parseColor("#808080")
            "STRESSED" -> Color.parseColor("#FF8040")
            "SLEEPY" -> Color.parseColor("#6060A0")
            "HUNGRY" -> Color.parseColor("#FFAA50")
            "SAD" -> Color.parseColor("#5070A0")
            "BORED" -> Color.parseColor("#808080")
            "CONTENT" -> Color.parseColor("#78FFA0")
            "ACTIVE" -> Color.parseColor("#00FFEE")
            "EXCITED" -> Color.parseColor("#FF50FF")
            "HAPPY" -> Color.parseColor("#50E6FF")
            "ECSTATIC" -> Color.parseColor("#FFD700")
            else -> Color.parseColor("#50FF78") // IDLE
        }
    }

    private fun getEmotionLabel(emotionName: String): String {
        return when (emotionName) {
            "CRITICAL" -> "⚠ ALERT"
            "SICK" -> "feeling ill..."
            "EXHAUSTED" -> "so tired..."
            "STRESSED" -> "stressed out"
            "SLEEPY" -> "sleepy time"
            "HUNGRY" -> "feed me!"
            "SAD" -> "feeling down"
            "BORED" -> "bored..."
            "CONTENT" -> "feeling good"
            "ACTIVE" -> "in the zone!"
            "EXCITED" -> "LET'S GO!!"
            "HAPPY" -> "great day!"
            "ECSTATIC" -> "GOAL SMASHED!"
            else -> "all good"
        }
    }
}
