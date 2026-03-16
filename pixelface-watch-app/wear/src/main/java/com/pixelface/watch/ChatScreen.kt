package com.pixelface.watch

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ChatPurple = Color(0xFF9C27B0)
private val ChatPurpleDim = Color(0xFF4A148C)

data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())

/**
 * Chat screen — speak to the CRT face bot via voice input.
 *
 * Architecture:
 * - Voice input via Android's built-in speech recognizer
 * - Gemini API responses relayed through phone (when available)
 * - Face mouth animates while "speaking" response text
 * - Falls back to local echo/canned responses when phone is not connected
 */
@Composable
fun ChatScreen(
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
  val focusRequester = remember { FocusRequester() }
  val scope = rememberCoroutineScope()
  val talkingAnimator = remember { TalkingAnimator() }

  var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
  var isBotTalking by remember { mutableStateOf(false) }
  var displayedResponse by remember { mutableStateOf("") }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  // Animation ticker
  var animTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(100L)
      animTimeMs = System.currentTimeMillis()
    }
  }

  // Face frame — talking when bot is responding
  val faceFrame = remember(animTimeMs) {
    talkingAnimator.getCurrentFrame(animTimeMs, isBotTalking)
  }

  // Speech recognizer launcher
  val speechLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val spoken = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()

      if (!spoken.isNullOrEmpty()) {
        messages = messages + ChatMessage(spoken, isUser = true)

        // Simulate bot response (in production, this goes through Gemini via phone)
        scope.launch {
          delay(500)
          val response = generateLocalResponse(spoken)
          isBotTalking = true
          displayedResponse = ""

          // Typewriter effect — character by character
          for (i in response.indices) {
            displayedResponse = response.substring(0, i + 1)
            delay(30)
          }

          isBotTalking = false
          messages = messages + ChatMessage(response, isUser = false)
          displayedResponse = ""
        }
      }
    }
  }

  ScalingLazyColumn(
    state = listState,
    horizontalAlignment = Alignment.CenterHorizontally,
    autoCentering = null,
    contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF020206))
      .onRotaryScrollEvent { event ->
        scope.launch { listState.scroll(MutatePriority.UserInput) { scrollBy(event.verticalScrollPixels) } }
        true
      }
      .focusRequester(focusRequester)
      .focusable()
  ) {
    // ── CRT Face at top ──
    item(key = "face") {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MiniCrtCanvas(
          faceFrame = faceFrame,
          faceColor = ChatPurple,
          sizeDp = 70
        )
        if (isBotTalking && displayedResponse.isNotEmpty()) {
          Spacer(Modifier.height(4.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(ChatPurple.copy(alpha = 0.10f))
              .padding(8.dp)
          ) {
            Text(
              text = displayedResponse,
              color = ChatPurple,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Start
            )
          }
        }
      }
    }

    // ── Chat history ──
    items(messages) { msg ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = if (msg.isUser) 32.dp else 12.dp,
            end = if (msg.isUser) 12.dp else 32.dp,
            top = 2.dp, bottom = 2.dp
          )
          .clip(RoundedCornerShape(10.dp))
          .background(
            if (msg.isUser) Color(0xFF50E6FF).copy(alpha = 0.10f)
            else ChatPurple.copy(alpha = 0.08f)
          )
          .padding(8.dp)
      ) {
        Text(
          text = msg.text,
          color = if (msg.isUser) Color(0xFF50E6FF) else ChatPurple.copy(alpha = 0.8f),
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          textAlign = if (msg.isUser) TextAlign.End else TextAlign.Start,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    // ── Speak button ──
    item(key = "speak_btn") {
      Spacer(Modifier.height(4.dp))
      Chip(
        onClick = {
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")
          }
          speechLauncher.launch(intent)
        },
        label = {
          Text(
            "🎤 Speak",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ChatPurple
          )
        },
        colors = ChipDefaults.chipColors(backgroundColor = ChatPurple.copy(alpha = 0.12f)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
      )
    }
  }
}

/**
 * Local response generator — used as fallback when Gemini is not available.
 * In production, this would be replaced by Gemini API through phone relay.
 */
private fun generateLocalResponse(input: String): String {
  val lower = input.lowercase()
  return when {
    lower.contains("hello") || lower.contains("hi") ->
      "Hey! I'm your pixel buddy. How's your day going?"
    lower.contains("time") ->
      "Check the big clock on your wrist! That's what I'm here for 😄"
    lower.contains("health") || lower.contains("heart") || lower.contains("steps") ->
      "Head to Stats & Health from the home screen to see your data!"
    lower.contains("weather") ->
      "I can see you, but I can't see outside! Check your phone for weather."
    lower.contains("joke") ->
      "Why do pixels never get lost? Because they always stay in line! 🤣"
    lower.contains("how are you") ->
      "I'm running at 10fps and feeling great! Thanks for asking."
    lower.contains("name") ->
      "I'm PixelFace — your tiny CRT companion on your wrist!"
    else ->
      "Interesting! I heard: \"$input\". Connect me to Gemini for smarter chats!"
  }
}
