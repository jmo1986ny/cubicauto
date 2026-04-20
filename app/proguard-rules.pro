# ── Spotify App Remote SDK ────────────────────────────────────────────────────
-keep class com.spotify.** { *; }
-keep interface com.spotify.** { *; }
-dontwarn com.spotify.**

# ── MediaSession / MediaBrowser ───────────────────────────────────────────────
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }
-keep class androidx.media3.** { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
