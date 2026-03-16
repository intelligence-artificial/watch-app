package com.pixelface.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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

/**
 * Root Composable for the PixelFace phone app.
 *
 * Two tabs:
 *   - Health: Dashboard + charts from Room DB
 *   - Notes: Voice notes received from watch
 *
 * Plus chart detail routes for HR, Steps, Calories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelFaceApp() {
  val context = LocalContext.current
  val repo = remember { NotesRepository(context) }
  val db = remember { HealthDatabase.getInstance(context) }
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  MaterialTheme(
    colorScheme = DarkColorScheme
  ) {
    Scaffold(
      bottomBar = {
        NavigationBar(
          containerColor = Color(0xFF111111),
          contentColor = Color(0xFFEEEEEE)
        ) {
          NavigationBarItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Health") },
            label = { Text("Health") },
            selected = currentRoute in listOf("health_dashboard", "hr_chart", "steps_chart", "cal_chart"),
            onClick = {
              if (currentRoute != "health_dashboard") {
                navController.navigate("health_dashboard") {
                  popUpTo("health_dashboard") { inclusive = true }
                }
              }
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color(0xFFFF3366),
              selectedTextColor = Color(0xFFFF3366),
              indicatorColor = Color(0xFFFF3366).copy(alpha = 0.12f)
            )
          )
          NavigationBarItem(
            icon = { Icon(Icons.Default.Mic, contentDescription = "Notes") },
            label = { Text("Notes") },
            selected = currentRoute in listOf("notes_list", "note_detail"),
            onClick = {
              if (currentRoute != "notes_list") {
                navController.navigate("notes_list") {
                  popUpTo("notes_list") { inclusive = true }
                }
              }
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color(0xFF42A5F5),
              selectedTextColor = Color(0xFF42A5F5),
              indicatorColor = Color(0xFF42A5F5).copy(alpha = 0.12f)
            )
          )
        }
      },
      containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
      NavHost(
        navController = navController,
        startDestination = "health_dashboard",
        modifier = Modifier.padding(padding)
      ) {
        // ── Health Tab ──
        composable("health_dashboard") {
          HealthDashboardScreen(
            db = db,
            onNavigateToHrChart = { navController.navigate("hr_chart") },
            onNavigateToStepsChart = { navController.navigate("steps_chart") },
            onNavigateToCalChart = { navController.navigate("cal_chart") }
          )
        }
        composable("hr_chart") {
          PhoneHrChartScreen(
            db = db,
            onBack = { navController.popBackStack() }
          )
        }
        composable("steps_chart") {
          PhoneStepsChartScreen(
            db = db,
            onBack = { navController.popBackStack() }
          )
        }
        composable("cal_chart") {
          PhoneCaloriesChartScreen(
            db = db,
            onBack = { navController.popBackStack() }
          )
        }

        // ── Notes Tab ──
        composable("notes_list") {
          NotesListScreen(
            repo = repo,
            onNoteClick = { noteId ->
              navController.navigate("note_detail/$noteId")
            }
          )
        }
        composable("note_detail/{noteId}") { backStackEntry2 ->
          val noteId = backStackEntry2.arguments?.getString("noteId") ?: return@composable
          NoteDetailScreen(
            repo = repo,
            noteId = noteId,
            onBack = { navController.popBackStack() }
          )
        }
      }
    }
  }
}
