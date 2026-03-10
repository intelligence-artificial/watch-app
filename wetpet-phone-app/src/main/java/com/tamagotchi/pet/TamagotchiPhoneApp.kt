package com.tamagotchi.pet

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TamagotchiPhoneApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val fitnessRepo = remember { FitnessRepository(context) }
    val petEffectsEngine = remember { PetEffectsEngine() }

    val navItems = listOf(
        Triple("dashboard", "Dashboard", Icons.Default.Dashboard),
        Triple("fitness", "Fitness", Icons.Default.FitnessCenter),
        Triple("effects", "Pet Effects", Icons.Default.FavoriteBorder)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Tamagotchi Pet") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                navItems.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("dashboard") {
                DashboardScreen(fitnessRepo = fitnessRepo)
            }
            composable("fitness") {
                FitnessScreen(fitnessRepo = fitnessRepo)
            }
            composable("effects") {
                PetEffectsScreen(
                    fitnessRepo = fitnessRepo,
                    petEffectsEngine = petEffectsEngine
                )
            }
        }
    }
}
