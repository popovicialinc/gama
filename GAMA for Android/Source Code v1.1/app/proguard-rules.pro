# Maximum optimization
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively
-mergeinterfacesaggressively

# Keep only essential classes
-keep class rikka.shizuku.** { *; }
-keep class com.popovicialinc.gama.MainActivity { *; }
-keep class com.popovicialinc.gama.ShizukuHelper { *; }
-keep class com.popovicialinc.gama.GamaUIKt { *; }

# Aggressively strip everything else
-dontwarn **
-ignorewarnings

# Remove all logging and debug code
-assumenosideeffects class android.util.Log {
    public static *** *(...);
}

-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
}

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# Remove Kotlin assertions and checks
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void check*(...);
    static void throw*(...);
}

# Remove annotations
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Metadata

# Optimize attributes
-keepattributes *Annotation*
-keepattributes Signature

# Remove unused Compose code
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    void sourceInformation(...);
    void sourceInformationMarkerStart(...);
    void sourceInformationMarkerEnd(...);
}