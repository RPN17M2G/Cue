package io.github.rpn17m2g.cue.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.rpn17m2g.cue.CueApplication
import io.github.rpn17m2g.cue.di.AppContainer
import io.github.rpn17m2g.cue.ui.screens.ErrorScreen
import io.github.rpn17m2g.cue.ui.screens.HomeScreen
import io.github.rpn17m2g.cue.ui.screens.ProcessingScreen
import io.github.rpn17m2g.cue.ui.screens.ResultScreen
import io.github.rpn17m2g.cue.ui.screens.ReviewScreen
import io.github.rpn17m2g.cue.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val RESULT = "result"
    const val ERROR = "error"
    const val SETTINGS = "settings"

    const val REVIEW_PATTERN = "review/{inputId}"
    const val PROCESSING_PATTERN = "processing/{inputId}"

    fun review(inputId: String): String = "review/$inputId"

    fun processing(inputId: String): String = "processing/$inputId"
}

fun Context.appContainer(): AppContainer =
    (applicationContext as CueApplication).container

@Composable
fun CueNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val container = LocalContext.current.appContainer()
    val request by container.navigationRequests.request.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val target = request ?: return@LaunchedEffect
        container.navigationRequests.consume()
        navController.navigate(target.route) {
            launchSingleTop = true
        }
    }
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartReview = { id -> navController.navigate(Routes.review(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenError = { navController.navigate(Routes.ERROR) },
            )
        }
        composable(
            route = Routes.REVIEW_PATTERN,
            arguments = listOf(navArgument("inputId") { type = NavType.StringType }),
        ) { entry ->
            val inputId = entry.arguments?.getString("inputId").orEmpty()
            ReviewScreen(
                inputId = inputId,
                onBack = { navController.popBackStack() },
                onProcessing = { navController.navigate(Routes.processing(inputId)) },
            )
        }
        composable(
            route = Routes.PROCESSING_PATTERN,
            arguments = listOf(navArgument("inputId") { type = NavType.StringType }),
        ) {
            ProcessingScreen(
                onFinished = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onError = {
                    navController.navigate(Routes.ERROR) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onCancelled = { navController.popBackStack() },
            )
        }
        composable(Routes.RESULT) {
            ResultScreen(
                onProcessAnother = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ERROR) {
            ErrorScreen(
                onRetryStarted = { inputId ->
                    navController.navigate(Routes.processing(inputId)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBack = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
