# Add project specific ProGuard rules here.
-keep class com.guardianangel.app.data.remote.model.** { *; }
-keep class com.guardianangel.app.data.local.entity.** { *; }

# Strip all Android log calls in release builds — prevents API keys / user data from
# appearing in logcat on rooted devices or ADB-connected developer machines.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(java.lang.String, java.lang.String);
    public static int v(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int d(java.lang.String, java.lang.String);
    public static int d(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int i(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int w(java.lang.String, java.lang.String);
    public static int w(java.lang.String, java.lang.String, java.lang.Throwable);
    public static int e(java.lang.String, java.lang.String);
    public static int e(java.lang.String, java.lang.String, java.lang.Throwable);
}

# Preserve Hilt / Dagger generated classes
-keep class dagger.hilt.** { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Preserve Picovoice Porcupine native layer
-keep class ai.picovoice.** { *; }
