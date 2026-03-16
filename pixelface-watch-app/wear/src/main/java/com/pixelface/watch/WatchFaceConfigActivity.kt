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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
          THEME_OPTIONS.forEachIndexed { index, opt ->
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

val THEME_OPTIONS = listOf(
  ThemeOption("GREY", "Grey", Color(0xFF505050), Color(0xFFC8C8C8), Color(0xFF787878)),
  ThemeOption("BLUE", "Matrix Blue", Color(0xFF5050C8), Color(0xFF9696FF), Color(0xFF7070E0)),
  ThemeOption("GREEN", "Matrix Green", Color(0xFF50C850), Color(0xFF96FF96), Color(0xFF70E070)),
  ThemeOption("PINK", "Neon Pink", Color(0xFFC850C8), Color(0xFFFF96FF), Color(0xFFE070E0)),
  ThemeOption("CYAN", "Cyan", Color(0xFF50C8C8), Color(0xFF96FFFF), Color(0xFF70E0E0)),
  ThemeOption("GOLD", "Gold", Color(0xFFC8A050), Color(0xFFFFDC96), Color(0xFFE0B870)),
  ThemeOption("RED", "Crimson", Color(0xFFC83030), Color(0xFFFF7070), Color(0xFFE05050)),
  ThemeOption("ORANGE", "Sunset", Color(0xFFC87030), Color(0xFFFFB070), Color(0xFFE09050)),
  ThemeOption("VIOLET", "Violet", Color(0xFF8040C8), Color(0xFFB880FF), Color(0xFFA060E0)),
  ThemeOption("ICE", "Ice", Color(0xFF3080C8), Color(0xFF70B8FF), Color(0xFF5098E0)),
  ThemeOption("LIME", "Lime", Color(0xFF90C830), Color(0xFFC8FF70), Color(0xFFB0E050)),
  ThemeOption("ROSE", "Rosé", Color(0xFFC85070), Color(0xFFFF90B0), Color(0xFFE07090)),
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

    // -- Minute arc (outer) --
    val minInset = w * 0.06f
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

    // -- Hour arc (inner) --
    val hourInset = w * 0.12f
    val hourRadius = cx - hourInset
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

    // 12 hour dots
    for (i in 0..11) {
      val angle = Math.PI * 2 * (i / 12.0) - Math.PI / 2
      val dx = cx + cos(angle).toFloat() * hourRadius
      val dy = cy + sin(angle).toFloat() * hourRadius
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
