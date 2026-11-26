# R8 / ProGuard hardening for SecureGPS Camera
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'o'
-flattenpackagehierarchy 'o'

# Strip verbose debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep Kotlin metadata minimally needed for Compose / Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep our data models serialized manually if needed
-keep class com.securegps.camera.data.VaultPhotoMeta { *; }
