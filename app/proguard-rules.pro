# EmisferIA Proactive ProGuard Rules

# Keep API models (all fields, constructors, generic signatures)
-keep class com.emisferia.proactive.api.** { *; }
-keepclassmembers class com.emisferia.proactive.api.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Retrofit + R8 + Coroutines (critical for suspend functions returning generics)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson - preserve generic type information
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Gson TypeToken (critical - R8 strips generic signatures without this)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

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
