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
    const val ABOUT = "about"
}

@Composable
fun RemodexNavGraph() {
    val viewModel: ContentViewModel = hiltViewModel()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()

    val navController = rememberNavController()

    val startDestination = if (hasSeenOnboarding) Routes.MAIN else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    viewModel.markOnboardingSeen()
                    navController.navigate(Routes.SCANNER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
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
                onNavigateToScanner = { navController.navigate(Routes.SCANNER) }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
