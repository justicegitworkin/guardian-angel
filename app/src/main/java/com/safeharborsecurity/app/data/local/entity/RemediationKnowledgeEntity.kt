package com.safeharborsecurity.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remediation_knowledge")
data class RemediationKnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_name_pattern") val packageNamePattern: String,
    @ColumnInfo(name = "app_display_name") val appDisplayName: String,
    @ColumnInfo(name = "android_min_version") val androidMinVersion: Int = 26,
    @ColumnInfo(name = "android_max_version") val androidMaxVersion: Int = Int.MAX_VALUE,
    @ColumnInfo(name = "can_toggle_directly") val canToggleDirectly: Boolean = false,
    @ColumnInfo(name = "settings_intent_action") val settingsIntentAction: String? = null,
    @ColumnInfo(name = "settings_intent_package") val settingsIntentPackage: String? = null,
    @ColumnInfo(name = "how_to_instructions") val howToInstructions: String,
    @ColumnInfo(name = "learn_more_url") val learnMoreUrl: String? = null,
    @ColumnInfo(name = "last_verified") val lastVerified: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source_version") val sourceVersion: Int = 1
)
