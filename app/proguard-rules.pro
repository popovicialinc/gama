# Maximum optimization
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# ── Shizuku ──────────────────────────────────────────────────
-keep class rikka.shizuku.** { *; }

# ── GAMA app classes ─────────────────────────────────────────
-keep class com.popovicialinc.gama.MainActivity { *; }
-keep class com.popovicialinc.gama.ShizukuHelper { *; }
-keep class com.popovicialinc.gama.GamaUIKt { *; }
-keep class com.popovicialinc.gama.BootReceiver { *; }
-keep class com.popovicialinc.gama.TaskerReceiver { *; }

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

-keepattributes *Annotation*
-keepattributes Signature
-dontwarn **
-ignorewarnings