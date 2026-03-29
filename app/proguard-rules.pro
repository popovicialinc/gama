-dontobfuscate

# Maximum optimization
-optimizationpasses 5
-allowaccessmodification

# ── Shizuku ──────────────────────────────────────────────────
-keep class rikka.shizuku.** { *; }
-keep class rikka.shizuku.shared.** { *; }
-keep interface rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-keepnames class rikka.shizuku.ShizukuProvider

# ── GAMA ─────────────────────────────────────────────────────
-keep class com.popovicialinc.gama.** { *; }

# ── WorkManager (reflection-based instantiation) ─────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.WorkerParameters { *; }

# ── Room (used internally by WorkManager) ────────────────────
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static <fields>;
    static <fields>;
}
-keep @androidx.room.Database class * { *; }

# ── Logging strip ────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** *(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void check*(...);
    static void throw*(...);
}
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(...);
    void sourceInformationMarkerStart(...);
    void sourceInformationMarkerEnd(...);
}

# ── Shizuku newProcess reflection ────────────────────────────
-keepclassmembers class rikka.shizuku.Shizuku {
    public static *** newProcess(...);
    public static *** pingBinder(...);
    public static *** checkSelfPermission(...);
    public static *** requestPermission(...);
    public static *** isPreV11(...);
    public static *** addBinderReceivedListenerSticky(...);
    public static *** addBinderDeadListener(...);
    public static *** addRequestPermissionResultListener(...);
    public static *** removeBinderReceivedListener(...);
    public static *** removeBinderDeadListener(...);
    public static *** removeRequestPermissionResultListener(...);
}

-keepattributes *Annotation*
-keepattributes Signature
-dontwarn **
-ignorewarnings