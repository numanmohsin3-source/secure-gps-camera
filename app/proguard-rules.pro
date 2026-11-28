# Standard ProGuard / R8 rules for SecureGPS Camera
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-dontwarn androidx.**
-dontwarn com.google.**

# Keep data models
-keep class com.securegps.camera.data.** { *; }
