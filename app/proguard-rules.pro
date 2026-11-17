# ==============================================================================
# Neo Child Clinic - Release ProGuard / R8 Full Mode Optimization & Keep Rules
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Diagnostic Logging Suppression
# ------------------------------------------------------------------------------
# Strip debug and verbose logs in release builds to eliminate diagnostic data leakage:
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# ------------------------------------------------------------------------------
# 2. Kotlinx Serialization
# ------------------------------------------------------------------------------
# Keep serialization attributes
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Keep @Serializable classes and their synthetic constructors
-keep @kotlinx.serialization.Serializable class * {
    <init>(...);
    *;
}
-keep @kotlinx.serialization.Polymorphic class * { *; }

# Keep companion objects that provide serializer()
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    public static final ** Companion;
}

# Keep serializer() lookup method on companion objects
-keepclassmembers class * {
    public static final ** serializer(...);
    public final ** serializer(...);
}

# Keep generated $serializer classes and their singleton INSTANCE
-keep class *$$serializer {
    public static final ** INSTANCE;
}
-keepclassmembers class *$$serializer {
    public static final ** INSTANCE;
    *** INSTANCE;
}

# Suppress harmless serialization library notes
-dontnote kotlinx.serialization.SerializationKt

# ------------------------------------------------------------------------------
# 3. AndroidX Hilt & WorkManager (@HiltWorker)
# ------------------------------------------------------------------------------
# Keep ListenableWorker and CoroutineWorker implementations
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep @HiltWorker annotated workers and assisted constructors
-keep @androidx.hilt.work.HiltWorker class * extends androidx.work.ListenableWorker {
    public <init>(...);
    <init>(...);
}

# Keep Hilt assisted injection factories
-keep class * implements androidx.hilt.work.WorkerAssistedFactory { *; }
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
-keep class **_AssistedFactory { *; }

# ------------------------------------------------------------------------------
# 4. SQLCipher JNI Bindings & Native Components
# ------------------------------------------------------------------------------
# Preserve SQLCipher packages (both net.zetetic and legacy net.sqlcipher)
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Preserve native JNI method bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 5. AndroidX Room Database & SQLite
# ------------------------------------------------------------------------------
# Keep RoomDatabase and generated _Impl classes
-keep class * extends androidx.room.RoomDatabase
-keep class **_Impl extends androidx.room.RoomDatabase { *; }
-keep class **_Impl implements androidx.room.RoomDatabase { *; }

# Keep Room internal adapters and helpers
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter { *; }
-keep class * extends androidx.room.EntityInsertionAdapter { *; }
-keep class * extends androidx.room.SharedSQLiteStatement { *; }
-dontwarn androidx.room.paging.**
