package com.pixelface.watch

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/**
 * WatchFaceConfigActivity — Launched when user long-presses → "Customize".
 * Shows a live preview of the watch face with the selected color theme.
 * User scrolls through themes using the crown (rotary input).
 */
class WatchFaceConfigActivity : ComponentActivity() {

  companion object {
    private const val TAG = "PixelFaceConfig"
    const val PREFS_NAME = "pixelface_config"
    const val KEY_COLOR_THEME = "color_theme"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate called")

    setContent {
      val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
      var selectedIndex by remember {
        val saved = prefs.getString(KEY_COLOR_THEME, "GREY") ?: "GREY"
        val idx = THEME_OPTIONS.indexOfFirst { it.id == saved }
        mutableIntStateOf(if (idx >= 0) idx else 0)
      }

      val focusRequester = remember { FocusRequester() }

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }

      // Save whenever selection changes
      LaunchedEffect(selectedIndex) {
        val theme = THEME_OPTIONS[selectedIndex]
        prefs.edit().putString(KEY_COLOR_THEME, theme.id).apply()
        Log.d(TAG, "Theme set to: ${theme.id}")
      }

      var scrollAccumulator by remember { mutableFloatStateOf(0f) }
      val scrollThreshold = 40f // pixels of crown rotation needed to switch

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .onRotaryScrollEvent { event ->
            scrollAccumulator += event.verticalScrollPixels
            if (scrollAccumulator > scrollThreshold) {
              selectedIndex = (selectedIndex + 1) % THEME_OPTIONS.size
              scrollAccumulator = 0f
            } else if (scrollAccumulator < -scrollThreshold) {
              selectedIndex = (selectedIndex - 1 + THEME_OPTIONS.size) % THEME_OPTIONS.size
              scrollAccumulator = 0f
            }
            true
          }
          .focusRequester(focusRequester)
          .focusable(),
        contentAlignment = Alignment.Center
      ) {
        val theme = THEME_OPTIONS[selectedIndex]

        // Draw a mini watch face preview with the theme colors
        WatchFacePreview(
          theme = theme,
          modifier = Modifier.fillMaxSize()
        )

        // Theme name at bottom
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
          verticalArrangement = Arrangement.Bottom,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = theme.name,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
          )
          Text(
            text = "↻ Scroll crown",
            color = Color(0xFF666666),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
          )
        }

        // Dot indicators at top
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.Top
        ) {
          THEME_OPTIONS.forEachIndexed { index, _ ->
            val dotColor = if (index == selectedIndex) Color.White else Color(0xFF444444)
            Canvas(modifier = Modifier.size(8.dp).padding(horizontal = 1.dp)) {
              drawCircle(color = dotColor)
            }
          }
        }
      }
    }
  }
}

data class ThemeOption(
  val id: String,
  val name: String,
  val hourColor: Color,
  val minuteColor: Color,
  val accentColor: Color
)

// Nature-inspired, muted grey-based color palettes
val THEME_OPTIONS = listOf(
  ThemeOption("GREY", "Slate", Color(0xFF5A5A5A), Color(0xFFB0B0B0), Color(0xFF808080)),
  ThemeOption("BLUE", "Ocean", Color(0xFF3D5A80), Color(0xFF8AABC4), Color(0xFF5E7D99)),
  ThemeOption("GREEN", "Forest", Color(0xFF4A6B4A), Color(0xFF8FB88F), Color(0xFF698C69)),
  ThemeOption("PINK", "Blossom", Color(0xFF8B5E6B), Color(0xFFC49DA8), Color(0xFFA07A86)),
  ThemeOption("CYAN", "Glacier", Color(0xFF4A7070), Color(0xFF8FB8B8), Color(0xFF699090)),
  ThemeOption("GOLD", "Sandstone", Color(0xFF8B7A50), Color(0xFFC4B48A), Color(0xFFA09068)),
  ThemeOption("RED", "Ember", Color(0xFF7A3B3B), Color(0xFFB88080), Color(0xFF995858)),
  ThemeOption("ORANGE", "Amber", Color(0xFF8B6040), Color(0xFFC49E7A), Color(0xFFA07858)),
  ThemeOption("VIOLET", "Twilight", Color(0xFF5E4A7A), Color(0xFF9E8AB8), Color(0xFF7A6899)),
  ThemeOption("ICE", "Frost", Color(0xFF3D5870), Color(0xFF8AA4B8), Color(0xFF587A90)),
  ThemeOption("LIME", "Moss", Color(0xFF607A3B), Color(0xFFA0B880), Color(0xFF7A9958)),
  ThemeOption("ROSE", "Terracotta", Color(0xFF8B5050), Color(0xFFC49090), Color(0xFFA06868)),
)

@Composable
fun WatchFacePreview(
  theme: ThemeOption,
  modifier: Modifier = Modifier
) {
  val now = remember { LocalTime.now() }
  val hour = now.hour
  val minute = now.minute

  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f

    val minuteFraction = minute / 60f
    val hourFraction = ((hour % 12) + minuteFraction) / 12f

    // -- Hour arc (outer — thicker) --
    val hourInset = w * 0.06f
    drawArc(
      color = theme.hourColor.copy(alpha = 0.15f),
      startAngle = -90f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = Offset(hourInset, hourInset),
      size = Size(w - hourInset * 2, h - hourInset * 2),
      style = Stroke(width = w * 0.04f, cap = StrokeCap.Round)
    )
    drawArc(
      color = theme.hourColor,
      startAngle = -90f,
      sweepAngle = hourFraction * 360f,
      useCenter = false,
      topLeft = Offset(hourInset, hourInset),
      size = Size(w - hourInset * 2, h - hourInset * 2),
      style = Stroke(width = w * 0.04f, cap = StrokeCap.Round)
    )

    // -- Minute arc (inner — thinner) --
    val minInset = w * 0.12f
    val minRadius = cx - minInset
    drawArc(
      color = theme.minuteColor.copy(alpha = 0.15f),
      startAngle = -90f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = Offset(minInset, minInset),
      size = Size(w - minInset * 2, h - minInset * 2),
      style = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
    )
    drawArc(
      color = theme.minuteColor,
      startAngle = -90f,
      sweepAngle = minuteFraction * 360f,
      useCenter = false,
      topLeft = Offset(minInset, minInset),
      size = Size(w - minInset * 2, h - minInset * 2),
      style = Stroke(width = w * 0.03f, cap = StrokeCap.Round)
    )

    // 12 hour dots on inner ring
    for (i in 0..11) {
      val angle = Math.PI * 2 * (i / 12.0) - Math.PI / 2
      val dx = cx + cos(angle).toFloat() * minRadius
      val dy = cy + sin(angle).toFloat() * minRadius
      val dotRadius = if (i <= (hour % 12)) w * 0.012f else w * 0.006f
      val dotColor = if (i <= (hour % 12)) theme.hourColor else theme.hourColor.copy(alpha = 0.3f)
      drawCircle(color = dotColor, radius = dotRadius, center = Offset(dx, dy))
    }

    // Time text in center
    val timeStr = String.format("%d:%02d", if (hour % 12 == 0) 12 else hour % 12, minute)
    val paint = android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      textSize = w * 0.15f
      textAlign = android.graphics.Paint.Align.CENTER
      typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
      isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(timeStr, cx, cy + w * 0.05f, paint)
  }
}
