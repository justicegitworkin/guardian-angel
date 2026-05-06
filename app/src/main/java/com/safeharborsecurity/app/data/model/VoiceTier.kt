package com.safeharborsecurity.app.data.model

enum class VoiceTier(val displayName: String, val description: String) {
    ELEVEN_LABS("ElevenLabs", "Human-quality voices"),
    GOOGLE_NEURAL("Google Neural", "Natural, warm voices"),
    ANDROID_TTS("Device Voice", "Built-in voice (works offline)")
}

enum class VoiceMode(val displayName: String) {
    AUTO("Auto (best available)"),
    BEST_QUALITY("Best Quality"),
    SAVE_COST("Save Cost"),
    OFFLINE_ONLY("Offline Only")
}
