# Clawd Mobile ProGuard rules

# Keep Gson serialized data classes
-keepclassmembers class com.clawd.mobile.data.model.** {
    <fields>;
}

# Keep data classes used with Gson
-keep class com.clawd.mobile.data.model.SessionData { *; }
-keep class com.clawd.mobile.data.model.SessionEvent { *; }
-keep class com.clawd.mobile.data.model.ConnectionConfig { *; }
-keep class com.clawd.mobile.data.local.ConnectionHistoryEntry { *; }

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Compose
-dontwarn androidx.compose.**
