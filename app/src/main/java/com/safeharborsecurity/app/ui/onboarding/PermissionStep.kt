package com.safeharborsecurity.app.ui.onboarding

import androidx.compose.ui.graphics.Color

/**
 * Represents a single permission step in the guided walkthrough.
 */
data class PermissionStep(
    val id: String,
    val icon: String,          // Emoji icon shown large
    val iconColor: Color,
    val title: String,
    val explanation: String,
    val reassurance: String,
    val buttonText: String,
    val actionType: PermissionActionType,
    val manifestPermission: String? = null,
    val settingsAction: String? = null
)

enum class PermissionActionType {
    RUNTIME,              // Standard runtime permission dialog
    SETTINGS_DEEP_LINK,   // Opens a Settings page (notification listener, accessibility)
    BATTERY_OPTIMIZATION  // Battery optimization exemption
}
