package com.kompakt.calendar

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kompakt.calendar.ui.theme.KompaktCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Clear notification badge and active notifications
        clearNotifications()

        // Ensure notifications are scheduled
        NotificationScheduler.rescheduleAll(this)

        setContent {
            KompaktCalendarTheme {
                val calendarViewModel: CalendarViewModel = viewModel()
                val onboardingCompleted by calendarViewModel.onboardingCompleted.collectAsState()
                val startDestination by calendarViewModel.startDestination.collectAsState()
                val textScale by calendarViewModel.textScale.collectAsState()
                val density = LocalDensity.current

                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = textScale)
                ) {
                if (!onboardingCompleted) {
                    OnboardingScreen(
                        onFinished = { calendarViewModel.refreshPermission() }
                    )
                } else {
                    val navController = rememberNavController()
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier.padding(innerPadding),
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
                            composable("location") {
                                LocationScreen(
                                    navController = navController,
                                    viewModel = calendarViewModel
                                )
                            }
                            composable(
                                route = "event_detail/{eventId}?instanceTime={instanceTime}",
                                arguments = listOf(
                                    navArgument("eventId") { type = NavType.LongType },
                                    navArgument("instanceTime") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    }
                                )
                            ) { backStackEntry ->
                                val eventId =
                                    backStackEntry.arguments?.getLong("eventId") ?: return@composable
                                val instanceTime =
                                    backStackEntry.arguments?.getLong("instanceTime")?.let { if (it == -1L) null else it }
                                EventDetailScreen(
                                    navController = navController,
                                    eventId = eventId,
                                    instanceTime = instanceTime,
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
    }

    override fun onStart() {
        super.onStart()
        clearNotifications()
    }

    private fun clearNotifications() {
        getSystemService(NotificationManager::class.java)?.cancelAll()
    }
}
