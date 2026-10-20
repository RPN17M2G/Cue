package io.github.rpn17m2g.cue.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.rpn17m2g.cue.ui.screens.ErrorScreen
import io.github.rpn17m2g.cue.ui.screens.HomeScreen
import io.github.rpn17m2g.cue.ui.screens.ProcessingScreen
import io.github.rpn17m2g.cue.ui.screens.ResultScreen
import io.github.rpn17m2g.cue.ui.screens.ReviewScreen
import io.github.rpn17m2g.cue.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val REVIEW = "review"
    const val PROCESSING = "processing"
    const val RESULT = "result"
    const val ERROR = "error"
    const val SETTINGS = "settings"
}

@Composable
fun CueNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddFile = { navController.navigate(Routes.REVIEW) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                onBack = { navController.popBackStack() },
                onProcess = { navController.navigate(Routes.PROCESSING) },
            )
        }
        composable(Routes.PROCESSING) {
            ProcessingScreen(
                onFinished = { navController.navigate(Routes.RESULT) },
                onError = { navController.navigate(Routes.ERROR) },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onProcessAnother = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onEditTags = { navController.navigate(Routes.REVIEW) },
            )
        }
        composable(Routes.ERROR) {
            ErrorScreen(
                onRetry = { navController.popBackStack() },
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
