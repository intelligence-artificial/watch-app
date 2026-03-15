package com.tamagotchi.pet

import androidx.compose.animation.core.*
import androidx.compose.runtime.*

/**
 * CRT TV power on/off animation controller.
 *
 * Power-ON sequence (400ms total):
 *   1. Black screen → white horizontal line appears at center
 *   2. Line expands vertically → full screen visible
 *   3. Brief static noise → face appears
 *
 * Power-OFF sequence (300ms total):
 *   1. Face → collapses to horizontal line
 *   2. Line shrinks to bright dot
 *   3. Dot fades → black
 *
 * Expression change: TV-OFF (200ms) → TV-ON (300ms)
 */

enum class TvPowerState {
  OFF,
  TURNING_ON,
  ON,
  TURNING_OFF
}

/**
 * Composable state holder for the TV animation.
 *
 * @param progress 0f = fully off (black), 1f = fully on (face visible)
 * @param state Current power state
 * @param showStatic Whether to show static noise (during transitions)
 * @param lineHeight For the CRT line effect: 0f = invisible, 1f = full height
 */
class TvAnimationState {
  var state by mutableStateOf(TvPowerState.OFF)
    internal set
  var progress by mutableFloatStateOf(0f)
    internal set
  var showStatic by mutableStateOf(false)
    internal set
  var lineWidth by mutableFloatStateOf(0f)
    internal set

  val isFullyOn: Boolean get() = state == TvPowerState.ON && progress >= 1f
  val isFullyOff: Boolean get() = state == TvPowerState.OFF && progress <= 0f
}

@Composable
fun rememberTvAnimation(
  autoTurnOn: Boolean = true
): TvAnimationState {
  val tvState = remember { TvAnimationState() }

  // Auto turn on when composable appears
  if (autoTurnOn) {
    LaunchedEffect(Unit) {
      tvState.turnOn()
    }
  }

  return tvState
}

/**
 * Trigger power-on animation.
 * Call from a coroutine scope.
 */
suspend fun TvAnimationState.turnOn() {
  if (state == TvPowerState.ON) return
  state = TvPowerState.TURNING_ON

  // Phase 1: Line appears (0ms → 100ms)
  lineWidth = 0f
  val lineAnim = Animatable(0f)
  lineAnim.animateTo(
    1f,
    animationSpec = tween(100, easing = FastOutSlowInEasing)
  ) { lineWidth = value }

  // Phase 2: Static noise flash (100ms → 200ms)
  showStatic = true
  progress = 0.3f
  kotlinx.coroutines.delay(80)
  showStatic = false

  // Phase 3: Expand to full (200ms → 400ms)
  val expandAnim = Animatable(0.3f)
  expandAnim.animateTo(
    1f,
    animationSpec = tween(200, easing = FastOutSlowInEasing)
  ) { progress = value }

  lineWidth = 1f
  state = TvPowerState.ON
}

/**
 * Trigger power-off animation.
 * Call from a coroutine scope.
 */
suspend fun TvAnimationState.turnOff() {
  if (state == TvPowerState.OFF) return
  state = TvPowerState.TURNING_OFF

  // Phase 1: Collapse to line (0ms → 150ms)
  val collapseAnim = Animatable(progress)
  collapseAnim.animateTo(
    0.05f,
    animationSpec = tween(150, easing = FastOutSlowInEasing)
  ) { progress = value }

  // Phase 2: Line shrinks to dot (150ms → 250ms)
  val dotAnim = Animatable(lineWidth)
  dotAnim.animateTo(
    0f,
    animationSpec = tween(100, easing = FastOutSlowInEasing)
  ) { lineWidth = value }

  // Phase 3: Dot fades
  progress = 0f
  state = TvPowerState.OFF
}

/**
 * Quick TV-off → TV-on for expression changes.
 * Much faster than full power cycle.
 */
suspend fun TvAnimationState.glitch() {
  if (state != TvPowerState.ON) return

  // Quick off
  state = TvPowerState.TURNING_OFF
  showStatic = true
  val offAnim = Animatable(1f)
  offAnim.animateTo(0.1f, tween(80, easing = LinearEasing)) { progress = value }
  kotlinx.coroutines.delay(60)

  // Quick on
  state = TvPowerState.TURNING_ON
  val onAnim = Animatable(0.1f)
  onAnim.animateTo(1f, tween(120, easing = FastOutSlowInEasing)) { progress = value }
  showStatic = false
  state = TvPowerState.ON
}
