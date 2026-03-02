package com.guardianangel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.guardianangel.app.ui.navigation.GuardianNavHost
import com.guardianangel.app.ui.navigation.Screen
import com.guardianangel.app.ui.onboarding.OnboardingViewModel
import com.guardianangel.app.ui.theme.GuardianAngelTheme
import com.guardianangel.app.ui.theme.TextSizePreference
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val onboardingVm: OnboardingViewModel = hiltViewModel()
            val isOnboardingDone by onboardingVm.isOnboardingDone.collectAsStateWithLifecycle()
            val textSizePref by onboardingVm.textSizePref.collectAsStateWithLifecycle()

            // Deep-link context from notification
            val deepLinkContext = intent?.data
                ?.getQueryParameter("context") ?: ""

            val navController = rememberNavController()

            // Map stored string to enum
            val textSizeEnum = when (textSizePref) {
                "LARGE" -> TextSizePreference.LARGE
                "EXTRA_LARGE" -> TextSizePreference.EXTRA_LARGE
                else -> TextSizePreference.NORMAL
            }

            GuardianAngelTheme(textSizePreference = textSizeEnum) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isOnboardingDone != null) {
                        val start = if (isOnboardingDone == true) Screen.Home.route
                                    else Screen.Onboarding.route
                        GuardianNavHost(
                            navController = navController,
                            startDestination = start
                        )
                    }
                }
            }
        }
    }
}
