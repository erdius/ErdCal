package com.example.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.helloworld.ui.theme.KompaktCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            KompaktCalendarTheme {
                val navController = rememberNavController()
                val calendarViewModel: CalendarViewModel = viewModel()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "calendar",
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None }
                    ) {
                        composable("calendar") {
                            CalendarScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                        composable("agenda") {
                            AgendaScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                        composable("day_view") {
                            DayViewScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                        composable(
                            route = "add_event?fromCalendar={fromCalendar}",
                            arguments = listOf(
                                navArgument("fromCalendar") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val fromCalendar =
                                backStackEntry.arguments?.getBoolean("fromCalendar") ?: false
                            AddEventScreen(
                                navController = navController,
                                viewModel = calendarViewModel,
                                useToday = fromCalendar
                            )
                        }
                        composable("notes") {
                            NotesScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                        composable(
                            route = "event_detail/{eventId}",
                            arguments = listOf(
                                navArgument("eventId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val eventId =
                                backStackEntry.arguments?.getLong("eventId") ?: return@composable
                            EventDetailScreen(
                                navController = navController,
                                eventId = eventId,
                                viewModel = calendarViewModel
                            )
                        }
                        composable("event_search") {
                            EventSearchScreen(
                                navController = navController,
                                viewModel = calendarViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
