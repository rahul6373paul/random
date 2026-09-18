# Retrofit interfaces are referenced reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.blocktime.data.remote.** {
    public static ** Companion;
}
-keepclassmembers class com.blocktime.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio platform-specific classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
