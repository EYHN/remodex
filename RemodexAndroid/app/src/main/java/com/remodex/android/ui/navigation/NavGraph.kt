package com.remodex.android.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import com.remodex.android.ui.about.AboutScreen
import com.remodex.android.ui.main.MainScreen
import com.remodex.android.ui.main.ContentViewModel
import com.remodex.android.ui.onboarding.OnboardingScreen
import com.remodex.android.ui.scanner.QRScannerScreen
import com.remodex.android.ui.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val SCANNER = "scanner"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ARCHIVED_CHATS = "archived_chats"
    const val ABOUT = "about"
}

@Composable
fun RemodexNavGraph() {
    val viewModel: ContentViewModel = hiltViewModel()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()
    val notificationNavigationToken by viewModel.notificationNavigationToken.collectAsState()

    val navController = rememberNavController()
    val startDestination = remember {
        if (hasSeenOnboarding) Routes.MAIN else Routes.ONBOARDING
    }

    LaunchedEffect(notificationNavigationToken) {
        if (notificationNavigationToken > 0L) {
            navController.navigate(Routes.MAIN) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.SCANNER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                    viewModel.markOnboardingSeen()
                }
            )
        }
        composable(Routes.SCANNER) {
            QRScannerScreen(
                viewModel = viewModel,
                onPaired = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SCANNER) { inclusive = true }
                    }
                },
                onBack = if (hasSeenOnboarding) {
                    { navController.popBackStack() }
                } else null
            )
        }
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToScanner = {
                    navController.navigate(Routes.SCANNER)
                }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToScanner = { navController.navigate(Routes.SCANNER) },
                onNavigateToArchivedChats = { navController.navigate(Routes.ARCHIVED_CHATS) }
            )
        }
        composable(Routes.ARCHIVED_CHATS) {
            com.remodex.android.ui.settings.ArchivedChatsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
