package com.safeharborsecurity.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.safeharborsecurity.app.ui.calls.CallsScreen
import com.safeharborsecurity.app.ui.chat.SafeHarborChatScreen
import com.safeharborsecurity.app.ui.email.EmailSetupScreen
import com.safeharborsecurity.app.ui.home.HomeScreen
import com.safeharborsecurity.app.ui.messages.MessagesScreen
import com.safeharborsecurity.app.ui.onboarding.GuidedPermissionScreen
import com.safeharborsecurity.app.ui.onboarding.OnboardingScreen
import com.safeharborsecurity.app.ui.panic.PanicScreen
import com.safeharborsecurity.app.ui.privacy.PrivacyMonitorScreen
import com.safeharborsecurity.app.ui.privacy.PrivacyPromiseScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.safeharborsecurity.app.data.model.QrAnalysisResult
import com.safeharborsecurity.app.data.model.QrType
import com.safeharborsecurity.app.ui.safety.NfcWarningScreen
import com.safeharborsecurity.app.ui.safety.QrScannerScreen
import com.safeharborsecurity.app.ui.safety.SafetyCheckerScreen
import com.safeharborsecurity.app.ui.safety.SafetyCheckerViewModel
import com.safeharborsecurity.app.ui.safety.VoicemailScannerScreen
import com.safeharborsecurity.app.ui.security.AdditionalSecurityScreen
import com.safeharborsecurity.app.ui.settings.SettingsScreen
import com.safeharborsecurity.app.ui.wifi.WifiDetailScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object SafeHarbor : Screen("safeharbor?context={context}") {
        fun withContext(context: String = "") =
            "safeharbor?context=${java.net.URLEncoder.encode(context, "UTF-8")}"
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
    object Privacy : Screen("privacy")
    object SafetyChecker : Screen("safety_checker")
    object Panic : Screen("panic")
    object PrivacyPromise : Screen("privacy_promise")
    object EmailSetup : Screen("email_setup")
    object WifiDetail : Screen("wifi_detail")
    object AdditionalSecurity : Screen("additional_security")
    object QrScanner : Screen("qr_scanner")
    object NfcWarning : Screen("nfc_warning")
    object VoicemailScanner : Screen("voicemail_scanner")
    object PermissionWalkthrough : Screen("permission_walkthrough")
    object News : Screen("news")
    object HiddenDeviceScanner : Screen("hidden_device_scanner")
    object TrackerScanner : Screen("tracker_scanner")
    object WeeklyDigest : Screen("weekly_digest")
    object Points : Screen("points")
    object MessageDetail : Screen("message_detail/{alertId}") {
        fun createRoute(alertId: Long) = "message_detail/$alertId"
    }
    object AppChecker : Screen("app_checker")
    object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/${java.net.URLEncoder.encode(packageName, "UTF-8")}"
    }
    object WifiNetworkDetail : Screen("wifi_network_detail/{ssid}/{bssid}") {
        fun createRoute(ssid: String, bssid: String) =
            "wifi_network_detail/${java.net.URLEncoder.encode(ssid, "UTF-8")}/${java.net.URLEncoder.encode(bssid, "UTF-8")}"
    }
    object SecurityHub : Screen("security_hub")
    object PrivacyPolicy : Screen("privacy_policy_doc")
}

@Composable
fun SafeHarborNavHost(
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
                onNavigateToSafeHarbor = { navController.navigate(Screen.SafeHarbor.withContext()) },
                onNavigateToCalls = { navController.navigate(Screen.Calls.route) },
                onNavigateToMessages = { navController.navigate(Screen.Messages.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateToSafetyChecker = { navController.navigate(Screen.SafetyChecker.route) },
                onNavigateToPanic = { navController.navigate(Screen.Panic.route) },
                onNavigateToWifiDetail = { navController.navigate(Screen.WifiDetail.route) },
                onNavigateToNews = { navController.navigate(Screen.News.route) },
                onNavigateToPoints = { navController.navigate(Screen.Points.route) },
                onNavigateToAppChecker = { navController.navigate(Screen.AppChecker.route) },
                onNavigateToWeeklyDigest = { navController.navigate(Screen.WeeklyDigest.route) },
                onNavigateToMessageDetail = { alertId ->
                    navController.navigate(Screen.MessageDetail.createRoute(alertId))
                }
            )
        }

        composable(
            route = Screen.SafeHarbor.route,
            arguments = listOf(navArgument("context") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val context = backStackEntry.arguments?.getString("context") ?: ""
            SafeHarborChatScreen(
                initialContext = java.net.URLDecoder.decode(context, "UTF-8"),
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSafetyChecker = { navController.navigate(Screen.SafetyChecker.route) }
            )
        }

        composable(Screen.Calls.route) {
            CallsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSafeHarbor = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                }
            )
        }

        composable(Screen.Messages.route) {
            MessagesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSafeHarbor = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                },
                onNavigateToEmailSetup = { navController.navigate(Screen.EmailSetup.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacyPromise = { navController.navigate(Screen.PrivacyPromise.route) },
                onNavigateToEmailSetup = { navController.navigate(Screen.EmailSetup.route) },
                onNavigateToAdditionalSecurity = { navController.navigate(Screen.AdditionalSecurity.route) },
                onNavigateToPermissionWalkthrough = { navController.navigate(Screen.PermissionWalkthrough.route) },
                onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) }
            )
        }

        composable(Screen.Privacy.route) {
            PrivacyMonitorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.SafetyChecker.route) { backStackEntry ->
            val safetyVm: SafetyCheckerViewModel = hiltViewModel()

            // Observe QR result returned from QrScannerScreen
            val qrRawValue = backStackEntry.savedStateHandle.get<String>("qr_raw_value")
            val qrTypeName = backStackEntry.savedStateHandle.get<String>("qr_type_name")
            if (qrRawValue != null && qrTypeName != null) {
                LaunchedEffect(qrRawValue) {
                    val qrType = try { QrType.valueOf(qrTypeName) } catch (_: Exception) { QrType.UNKNOWN }
                    val warning = backStackEntry.savedStateHandle.get<String>("qr_instant_warning")?.ifBlank { null }
                    val needsAnalysis = backStackEntry.savedStateHandle.get<Boolean>("qr_needs_analysis") ?: false
                    safetyVm.onQrCodeDetected(QrAnalysisResult(qrRawValue, qrType, warning, needsAnalysis))
                    backStackEntry.savedStateHandle.remove<String>("qr_raw_value")
                    backStackEntry.savedStateHandle.remove<String>("qr_type_name")
                    backStackEntry.savedStateHandle.remove<String>("qr_instant_warning")
                    backStackEntry.savedStateHandle.remove<Boolean>("qr_needs_analysis")
                }
            }

            SafetyCheckerScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenSafeHarborWithContext = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                },
                onNavigateToQrScanner = { navController.navigate(Screen.QrScanner.route) },
                onNavigateToVoicemailScanner = { navController.navigate(Screen.VoicemailScanner.route) },
                onNavigateToRoomScanner = { navController.navigate(Screen.HiddenDeviceScanner.route) },
                onNavigateToAppChecker = { navController.navigate(Screen.AppChecker.route) },
                onNavigateToTrackerScanner = { navController.navigate(Screen.TrackerScanner.route) },
                onNavigateToListeningShield = { navController.navigate(Screen.Privacy.route) },
                viewModel = safetyVm
            )
        }

        composable(Screen.TrackerScanner.route) {
            com.safeharborsecurity.app.ui.safety.TrackerScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.WeeklyDigest.route) {
            com.safeharborsecurity.app.ui.digest.WeeklyDigestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.QrScanner.route) {
            QrScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onQrDetected = { qrResult ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_raw_value", qrResult.rawValue)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_type_name", qrResult.qrType.name)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_instant_warning", qrResult.instantWarning ?: "")
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("qr_needs_analysis", qrResult.needsClaudeAnalysis)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.NfcWarning.route) {
            NfcWarningScreen(
                tagAnalysis = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Panic.route) {
            PanicScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                }
            )
        }

        composable(Screen.PrivacyPromise.route) {
            PrivacyPromiseScreen(
                isOnboarding = false,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.WifiDetail.route) {
            WifiDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.EmailSetup.route) {
            EmailSetupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.VoicemailScanner.route) {
            VoicemailScannerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AdditionalSecurity.route) {
            AdditionalSecurityScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PermissionWalkthrough.route) {
            GuidedPermissionScreen(
                onComplete = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.News.route) {
            com.safeharborsecurity.app.ui.news.NewsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Points.route) {
            com.safeharborsecurity.app.ui.points.PointsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.HiddenDeviceScanner.route) {
            com.safeharborsecurity.app.ui.scanner.HiddenDeviceScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onWifiNetworkTapped = { ssid, bssid ->
                    navController.navigate(Screen.WifiNetworkDetail.createRoute(ssid, bssid))
                }
            )
        }

        composable(
            route = Screen.WifiNetworkDetail.route,
            arguments = listOf(
                navArgument("ssid") { type = NavType.StringType },
                navArgument("bssid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val ssid = backStackEntry.arguments?.getString("ssid") ?: return@composable
            val bssid = backStackEntry.arguments?.getString("bssid") ?: return@composable
            com.safeharborsecurity.app.ui.scanner.WifiNetworkDetailScreen(
                ssid = java.net.URLDecoder.decode(ssid, "UTF-8"),
                bssid = java.net.URLDecoder.decode(bssid, "UTF-8"),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SecurityHub.route) {
            com.safeharborsecurity.app.ui.security.SecurityHubScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyPolicy.route) {
            com.safeharborsecurity.app.ui.privacy.PrivacyPolicyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MessageDetail.route,
            arguments = listOf(navArgument("alertId") { type = NavType.LongType })
        ) { backStackEntry ->
            val alertId = backStackEntry.arguments?.getLong("alertId") ?: return@composable
            com.safeharborsecurity.app.ui.messages.MessageDetailScreen(
                alertId = alertId,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                }
            )
        }

        composable(Screen.AppChecker.route) {
            com.safeharborsecurity.app.ui.appchecker.AppCheckerScreen(
                onNavigateBack = { navController.popBackStack() },
                onAppSelected = { packageName ->
                    navController.navigate(Screen.AppDetail.createRoute(packageName))
                }
            )
        }

        composable(
            route = Screen.AppDetail.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString("packageName") ?: return@composable
            val decoded = java.net.URLDecoder.decode(pkg, "UTF-8")
            com.safeharborsecurity.app.ui.appchecker.AppDetailScreen(
                packageName = decoded,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { context ->
                    navController.navigate(Screen.SafeHarbor.withContext(context))
                }
            )
        }
    }
}
