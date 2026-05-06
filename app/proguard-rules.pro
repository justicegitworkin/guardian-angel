# ═══════════════════════════════════════════════════════════════════
# Safe Companion — R8/ProGuard Rules
# Part B5 — comprehensive obfuscation + log stripping
# ═══════════════════════════════════════════════════════════════════

# ── Aggressive obfuscation ──
-optimizationpasses 5
-repackageclasses ''
-allowaccessmodification

# ── Keep Room entities, model DTOs (reflection access) ──
-keep class com.safeharborsecurity.app.data.remote.model.** { *; }
-keep class com.safeharborsecurity.app.data.local.entity.** { *; }
-keep class com.safeharborsecurity.app.data.model.** { *; }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ── Retrofit / OkHttp ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.safeharborsecurity.app.data.remote.ClaudeApiService { *; }

# ── Gson serialization ──
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Hilt / Dagger ──
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel

# ── Kotlin ──
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**

# ── ML Kit ──
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── AndroidX Navigation Compose ──
-keep class androidx.navigation.** { *; }

# ── Compose ──
-dontwarn androidx.compose.**

# ── ONNX Runtime (in case re-added later) ──
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ── Strip all android.util.Log calls in release ──
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}

# ── Strip SecureLog in release builds (BuildConfig.DEBUG=false makes them no-ops anyway) ──
-assumenosideeffects class com.safeharborsecurity.app.util.SecureLog {
    public static void d(...);
    public static void w(...);
    public static void i(...);
}

# ── Suppress harmless reflection warnings ──
-dontwarn javax.lang.model.element.Modifier

# ── Strip filenames/line numbers from stack traces in release (optional but nice) ──
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
