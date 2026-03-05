package com.watchvoice.recorder

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF42A5F5),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFF43A047),
    tertiary = Color(0xFFAB47BC),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF252525),
    onBackground = Color(0xFFEEEEEE),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF999999),
    error = Color(0xFFE53935),
    outline = Color(0xFF333333)
)

@Composable
fun VoiceNotesApp() {
    val context = LocalContext.current
    val repo = remember { NotesRepository(context) }
    val navController = rememberNavController()

    MaterialTheme(
        colorScheme = DarkColorScheme
    ) {
        NavHost(
            navController = navController,
            startDestination = "notes_list"
        ) {
            composable("notes_list") {
                NotesListScreen(
                    repo = repo,
                    onNoteClick = { noteId ->
                        navController.navigate("note_detail/$noteId")
                    }
                )
            }

            composable("note_detail/{noteId}") { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                NoteDetailScreen(
                    repo = repo,
                    noteId = noteId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
