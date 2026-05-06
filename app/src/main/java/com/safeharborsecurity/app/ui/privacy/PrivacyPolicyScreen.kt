package com.safeharborsecurity.app.ui.privacy

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.safeharborsecurity.app.ui.theme.NavyBlue

/**
 * In-app privacy policy. Loads the bundled assets/privacy_policy.html so the
 * exact same text is available to reviewers and to users — no external server
 * dependency, works offline.
 *
 * Note: WebView is configured with no JavaScript and no file/network access —
 * it is just a styled text renderer for the static HTML.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Hardened: no JS, no DOM/file/network access — just static HTML rendering.
                    @Suppress("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, url: String?
                        ): Boolean = true  // block any link navigation
                    }
                    loadUrl("file:///android_asset/privacy_policy.html")
                }
            }
        )
    }
}
