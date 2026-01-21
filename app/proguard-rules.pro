# EmisferIA Proactive ProGuard Rules

# Keep API models
-keep class com.emisferia.proactive.api.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**

# Keep Speech Recognition
-keep class android.speech.** { *; }

# Porcupine / Picovoice Wake Word
-keep class ai.picovoice.** { *; }
-keep class ai.picovoice.porcupine.** { *; }
-keepclassmembers class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
