package com.tamagotchi.pet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TamagotchiPhoneTheme {
                TamagotchiPhoneApp()
            }
        }
    }
}

@Composable
fun TamagotchiPhoneTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF78FFA0),
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        secondary = androidx.compose.ui.graphics.Color(0xFF50E6FF),
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        tertiary = androidx.compose.ui.graphics.Color(0xFFFF8CC8),
        background = androidx.compose.ui.graphics.Color(0xFF0A0A12),
        surface = androidx.compose.ui.graphics.Color(0xFF12121E),
        onBackground = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color.White,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0B0C0)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
