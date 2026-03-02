package com.guardianangel.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.guardianangel.app.ui.calls.CallsScreen
import com.guardianangel.app.ui.chat.GuardianChatScreen
import com.guardianangel.app.ui.home.HomeScreen
import com.guardianangel.app.ui.messages.MessagesScreen
import com.guardianangel.app.ui.onboarding.OnboardingScreen
import com.guardianangel.app.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Guardian : Screen("guardian?context={context}") {
        fun withContext(context: String = "") =
            "guardian?context=${java.net.URLEncoder.encode(context, "UTF-8")}"
    }
    object Calls : Screen("calls")
    object Messages : Screen("messages")
    object Settings : Screen("settings")
    object CallDetail : Screen("calls/{callId}") {
        fun withId(callId: Long) = "calls/$callId"
    }
    object AlertDetail : Screen("alerts/{alertId}") {
        fun withId(alertId: Long) = "alerts/$alertId"
    }
}

@Composable
fun GuardianNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToGuardian = { navController.navigate(Screen.Guardian.withContext()) },
                onNavigateToCalls = { navController.navigate(Screen.Calls.route) },
                onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Guardian.route,
            arguments = listOf(navArgument("context") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val context = backStackEntry.arguments?.getString("context") ?: ""
            GuardianChatScreen(
                initialContext = java.net.URLDecoder.decode(context, "UTF-8"),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Calls.route) {
            CallsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenGuardian = { context ->
                    navController.navigate(Screen.Guardian.withContext(context))
                }
            )
        }

        composable(Screen.Messages.route) {
            MessagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenGuardian = { context ->
                    navController.navigate(Screen.Guardian.withContext(context))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
