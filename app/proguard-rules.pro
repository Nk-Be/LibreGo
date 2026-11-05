# Project specific ProGuard rules for LibreGo

-keep class com.baidu.** { *; }
-keep class vi.com.** { *; }
-keep class com.baidu.vi.** { *; }
-dontwarn com.baidu.**

# Keep Shizuku API
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Keep app classes and models
-keep class com.zcshou.** { *; }
-keep class top.nkbe.** { *; }

# Keep reflection methods
-keepclassmembers class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
