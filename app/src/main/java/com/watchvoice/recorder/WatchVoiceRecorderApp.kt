package com.watchvoice.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

@Composable
fun WatchVoiceRecorderApp() {
    val context = LocalContext.current
    val recordingsDir = remember { context.filesDir }
    val recorderService = remember { AudioRecorderService(context, recordingsDir) }
    val playerService = remember { AudioPlayerService() }
    val navController = rememberSwipeDismissableNavController()

    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "record"
        ) {
            composable("record") {
                RecordScreen(
                    onNavigateToRecordings = {
                        navController.navigate("recordings")
                    },
                    recorderService = recorderService
                )
            }

            composable("recordings") {
                RecordingsScreen(
                    recordingsDir = recordingsDir,
                    playerService = playerService,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
