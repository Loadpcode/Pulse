# Add project specific ProGuard rules here.
# Native Go / JNI bridges (libv2ray & hev-socks5-tunnel)
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-dontwarn libv2ray.**

-keep class go.** { *; }
-keep interface go.** { *; }
-dontwarn go.**

-keep class org.amnezia.awg.hevtunnel.** { *; }
-dontwarn org.amnezia.awg.hevtunnel.**

# Preserve kotlinx.serialization @Serializable routes and models
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.xraypulse.app.ui.navigation.** { *; }
-keep,includedescriptorclasses class com.xraypulse.app.model.** { *; }

# Room database & entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.xraypulse.app.data.local.** { *; }

# Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# OkHttp optional TLS platform providers (not bundled on Android)
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**


