package com.popovicialinc.gama

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt


// ============================================================
// GamaUI: main composable — state, navigation, layout
// ============================================================

@Composable
fun GamaUI(
    onRequestNotificationPermission: () -> Unit = {},
    onExportBackup: (jsonContent: String, fileName: String) -> Unit = { _, _ -> },
    onImportBackup: (callback: (String) -> Unit) -> Unit = {},
    onExportCrashLog: (content: String, fileName: String) -> Unit = { _, _ -> }
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    val languageCode = LocalLanguageCode.current.value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenWidth < 360.dp || screenHeight < 640.dp
    val isLargeScreen = screenWidth > 600.dp
    val isLandscape = screenWidth > screenHeight
    val isTablet = screenWidth >= 600.dp && screenHeight >= 600.dp

    fun performHaptic(type: Int = HapticFeedbackConstants.CONTEXT_CLICK) {
        GamaHaptics.feedback(context, view, type)
    }

    fun performSuccessHaptic() {
        GamaHaptics.success(context, view)
    }

    fun performErrorHaptic() {
        GamaHaptics.error(context, view)
    }

    fun performInteractionHaptic() {
        GamaHaptics.selection(context, view)
    }

    fun performRendererHaptic() {
        GamaHaptics.rendererSelection(context, view)
    }

    val prefs = remember { context.getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE) }

    // Show button labels exactly once — fades after first launch
    var showButtonLabels by remember {
        mutableStateOf(!prefs.getBoolean("button_labels_shown", false))
    }

    // Version-based preferences reset — wrapped in SideEffect so it runs exactly
    // once per composition (not on every recomposition) and never on the hot path.
    // We preserve personal data so a PREFS_VERSION bump doesn't silently nuke the
    // user's name, excluded apps list, or first-launch flags.
    val PREFS_VERSION = 4
    val savedPrefsVersion = prefs.getInt("prefs_version", 0)

    if (savedPrefsVersion < PREFS_VERSION) {
        SideEffect {
            val savedUserName       = prefs.getString("user_name", "") ?: ""
            val savedExcludedApps   = prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet()
            val savedLabelsShown    = prefs.getBoolean("button_labels_shown", false)
            val savedNotifPermReq   = prefs.getBoolean("notif_perm_requested", false)

            prefs.edit().clear()
                .putInt("prefs_version",           PREFS_VERSION)
                .putString("user_name",            savedUserName)
                .putStringSet("excluded_apps",     savedExcludedApps)
                .putBoolean("button_labels_shown", savedLabelsShown)
                .putBoolean("notif_perm_requested", savedNotifPermReq)
                .apply()
        }
    }

    var shizukuStatus by remember { mutableStateOf("Checking...") }
    var showShizukuHelp by remember { mutableStateOf(false) }
    var shizukuHelpType by remember { mutableStateOf("") }
    var commandOutput by remember { mutableStateOf("") }
    // Default to OpenGL if no preference is saved
    var currentRenderer by remember { mutableStateOf(prefs.getString("last_renderer", "OpenGL") ?: "OpenGL") }
    // True from launch until Shizuku either confirms the renderer or we know Shizuku isn't ready
    var rendererLoading by remember { mutableStateOf(true) }
    var lastSwitchTime by remember { mutableStateOf(prefs.getLong("last_switch_time", 0L)) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var pendingRendererSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRendererName by remember { mutableStateOf("") } // Store target renderer name

    // Dialog States
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showClearRecentsDialog by remember { mutableStateOf(false) }
    var successDialogMessage by remember { mutableStateOf("") }
    // True from the moment the user taps Continue until commandOutput arrives,
    // which is the real completion signal from ShizukuHelper. During this window
    // SuccessDialog shows a spinner rather than the final confirmation message.
    var rendererSwitching by remember { mutableStateOf(false) }
    // showDeveloperMenu removed — DeveloperMenuDialog was never reachable (showDeveloperMenu was
    // never set to true anywhere). DeveloperPanel (showDeveloper) is the live path.
    var showVerbosePanel by remember { mutableStateOf(false) }
    var verboseOutput by remember { mutableStateOf("") }
    var showAggressiveWarning by remember { mutableStateOf(false) }
    var showGPUWatchConfirm by remember { mutableStateOf(false) }

    // User name
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }

    // STARTUP VISIBILITY STATE
    // isVisible controls the staggered entrance of main menu items
    // It should only be true when setup is done
    var isVisible by remember { mutableStateOf(false) }

    var showGitHubDialog by remember { mutableStateOf(false) }
    var showEasterEgg by remember { mutableStateOf(false) }
    var showResourcesPanel by remember { mutableStateOf(false) }
    var showExternalLinkConfirm by remember { mutableStateOf(false) }
    var pendingExternalLink by remember { mutableStateOf("") }
    var pendingExternalLinkLabel by remember { mutableStateOf("") }
    var pendingExternalLinkDescription by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSettingsSearch by remember { mutableStateOf(false) }
    var showHapticsPanel by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showColorCustomization by remember { mutableStateOf(false) }
    var showGradient by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(false) }
    var showRendererPanel by remember { mutableStateOf(false) }
    var showIntegrationInfoDialog   by remember { mutableStateOf(false) }
    var integrationInfoTitle        by remember { mutableStateOf("") }
    var integrationInfoBody         by remember { mutableStateOf("") }
    var showDeveloper by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showParticlesAppearance by remember { mutableStateOf(false) }
    var showParticlesMotion by remember { mutableStateOf(false) }
    var showParticlesPerformance by remember { mutableStateOf(false) }
    var showMatrixSettings   by remember { mutableStateOf(false) }
    var showMatrixAppearance by remember { mutableStateOf(false) }
    var showMatrixMotion     by remember { mutableStateOf(false) }
    var showParticlesSettings by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }

    // ── Notifications panel ───────────────────────────────────────────────────
    var showNotifications by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", false)) }
    // 0=2 h, 1=4 h, 2=6 h, 3=12 h, 4=24 h
    var notifIntervalIndex by remember { mutableStateOf(prefs.getInt("notif_interval_idx", 2)) }
    var lastNotifSentTime by remember { mutableStateOf(prefs.getLong("notif_last_sent", 0L)) }
    // True once we've asked for the OS permission at least once
    var notifPermissionRequested by remember { mutableStateOf(prefs.getBoolean("notif_perm_requested", false)) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuPermissionGranted by remember { mutableStateOf(false) }

    // Notification permission — re-evaluated once on every ON_RESUME (e.g. returning
    // from the OS permission dialog), not on every recomposition.
    // Previously ShizukuHelper.hasNotificationPermission(context) was called inline in
    // two composition call-sites, meaning every recomposition of GamaUI triggered a
    // JNI checkSelfPermission call. DisposableEffect + LifecycleEventObserver is the
    // idiomatic way to run a side-effect tied to a specific lifecycle event.
    val hasNotifPermission = remember { mutableStateOf(ShizukuHelper.hasNotificationPermission(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotifPermission.value = ShizukuHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back handlers
    BackHandler(enabled = showSuccessDialog) {
        if (!rendererSwitching) {
            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
            showSuccessDialog = false
        }
    }

    BackHandler(enabled = showClearRecentsDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showClearRecentsDialog = false
    }

    // (DeveloperMenuDialog BackHandler removed — showDeveloperMenu was dead state)

    BackHandler(enabled = showDeveloper) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showDeveloper = false
    }

    BackHandler(enabled = showShizukuHelp) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showShizukuHelp = false
    }

    // ── Appearance sub-panels ─────────────────────────────────────────────────

    BackHandler(enabled = showColorCustomization && !showGradient) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showColorCustomization = false
    }

    BackHandler(enabled = showGradient) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGradient = false
    }

    BackHandler(enabled = showParticlesAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesAppearance = false
        showParticlesSettings = true
    }

    BackHandler(enabled = showParticlesMotion) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesMotion = false
        showParticlesSettings = true
    }

    BackHandler(enabled = showParticlesPerformance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesPerformance = false
        showParticlesSettings = true
    }
    BackHandler(enabled = showMatrixAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixAppearance = false
    }
    BackHandler(enabled = showMatrixMotion) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixMotion = false
    }
    BackHandler(enabled = showMatrixSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showMatrixSettings = false
    }
    BackHandler(enabled = showParticlesSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticlesSettings = false
    }

    BackHandler(enabled = showParticles && !showParticlesAppearance && !showParticlesMotion && !showParticlesPerformance && !showMatrixSettings && !showMatrixAppearance && !showMatrixMotion && !showParticlesSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticles = false
    }

    BackHandler(enabled = showEffects && !showParticles) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEffects = false
    }

    BackHandler(enabled = showAppearance) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showAppearance = false
    }

    // ── System sub-panels ─────────────────────────────────────────────────────

    BackHandler(enabled = showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showNotifications = false
    }

    BackHandler(enabled = showBackup) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showBackup = false
    }

    BackHandler(enabled = showCrashLog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showCrashLog = false
    }

    BackHandler(enabled = showLanguage) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showLanguage = false
    }

    BackHandler(enabled = showSystem && !showNotifications && !showBackup && !showCrashLog && !showLanguage) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSystem = false
    }

    // ── Renderer ──────────────────────────────────────────────────────────────

    BackHandler(enabled = showRendererPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showRendererPanel = false
    }

    // ── Top-level Settings ────────────────────────────────────────────────────

    BackHandler(enabled = showSettingsSearch) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettingsSearch = false
    }

    BackHandler(enabled = showHapticsPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showHapticsPanel = false
    }

    BackHandler(enabled = showSettings && !showSettingsSearch && !showHapticsPanel && !showAppearance && !showColorCustomization && !showGradient && !showEffects && !showParticles && !showSystem && !showNotifications && !showBackup && !showCrashLog && !showRendererPanel && !showLanguage && !showHapticsPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettings = false
        showSettingsSearch = false
    }

    BackHandler(enabled = showWarningDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showWarningDialog = false
        pendingRendererSwitch = null
    }

    BackHandler(enabled = showGitHubDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGitHubDialog = false
    }

    BackHandler(enabled = showEasterEgg) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEasterEgg = false
    }

    BackHandler(enabled = showResourcesPanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showWarningDialog = false
        showResourcesPanel = false
    }

    BackHandler(enabled = showExternalLinkConfirm) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showExternalLinkConfirm = false
    }

    BackHandler(enabled = showVerbosePanel) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showVerbosePanel = false
    }

    BackHandler(enabled = showAggressiveWarning) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showAggressiveWarning = false
    }

    BackHandler(enabled = showGPUWatchConfirm) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showGPUWatchConfirm = false
    }

    val systemInDarkTheme = isSystemInDarkTheme()
    // Default animation level to 0 (Normal) — full quality animations
    var animationLevel by remember { mutableStateOf(prefs.getInt("animation_level", 0)) }
    var gradientEnabled by remember { mutableStateOf(prefs.getBoolean("gradient_enabled", true)) }
    var blurEnabled by remember { mutableStateOf(prefs.getBoolean("blur_enabled", true)) }
    var particlesEnabled by remember { mutableStateOf(prefs.getBoolean("particles_enabled", true)) }
    var particleSpeed by remember { mutableStateOf(prefs.getInt("particle_speed", 1)) } // 0=low, 1=medium, 2=high (default: medium)
    var particleParallaxEnabled by remember { mutableStateOf(prefs.getBoolean("particle_parallax_enabled", true)) }
    var particleParallaxSensitivity by remember { mutableStateOf(prefs.getInt("particle_parallax_sensitivity", 0)) } // 0=low(0.15), 1=medium(0.3), 2=high(0.5)
    var particleStarMode by remember { mutableStateOf(prefs.getBoolean("particle_star_mode", false)) } // New: star mode toggle
    var particleTimeMode by remember { mutableStateOf(prefs.getBoolean("particle_time_mode", true)) } // Time-based sun & moon system
    var timeOffsetHours by remember { mutableStateOf(prefs.getFloat("time_offset_hours", 0f)) } // Developer: time offset
    var particleCount by remember { mutableStateOf(prefs.getInt("particle_count", 0)) } // 0=low(75), 1=medium(150), 2=high(300), 3=custom (default: low)
    var particleCountCustom by remember { mutableStateOf(prefs.getInt("particle_count_custom", 150).toString()) }
    var matrixMode          by remember { mutableStateOf(prefs.getBoolean("matrix_mode", true)) }
    var matrixSpeed         by remember { mutableStateOf(prefs.getInt("matrix_speed", 1)) }          // 0=slow 1=medium 2=fast
    var matrixDensity       by remember { mutableStateOf(prefs.getInt("matrix_density", 1)) }        // 0=sparse 1=medium 2=dense
    var matrixFontSize      by remember { mutableStateOf(prefs.getInt("matrix_font_size", 1)) }      // 0=small 1=medium 2=large
    var matrixFadeLength    by remember { mutableStateOf(prefs.getInt("matrix_fade_length", 1)) }    // 0=short 1=medium 2=full
    var matrixBgAlpha       by remember { mutableStateOf(prefs.getFloat("matrix_bg_alpha", 0f)) }    // 0=transparent 1=black
    var themePreference by remember { mutableStateOf(prefs.getInt("theme_preference", 0)) }

    // New settings
    // Replaced separate buttonSize with shared uiScale
    var uiScale by remember { mutableStateOf(prefs.getInt("ui_scale", 1)) } // 0=75%, 1=100%, 2=125%
    var verboseMode by remember { mutableStateOf(prefs.getBoolean("verbose_mode", false)) }
    var aggressiveMode by remember { mutableStateOf(prefs.getBoolean("aggressive_mode", false)) }
    var killLauncher by remember { mutableStateOf(prefs.getBoolean("kill_launcher", false)) }
    var killKeyboard by remember { mutableStateOf(prefs.getBoolean("kill_keyboard", false)) }
    var dozeMode by remember { mutableStateOf(prefs.getBoolean("doze_mode", false)) }
    var showGpuWatchButton by remember { mutableStateOf(prefs.getBoolean("show_gpuwatch_button", false)) }
    var staggerEnabled by remember { mutableStateOf(prefs.getBoolean("stagger_enabled", true)) }
    var backButtonAvoidanceEnabled by remember { mutableStateOf(prefs.getBoolean("back_button_avoidance_enabled", true)) }
    var shadowsEnabled by remember { mutableStateOf(prefs.getBoolean("shadows_enabled", true)) }
    var hapticsEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_ENABLED, GamaHaptics.DEFAULT_ENABLED)) }
    var hapticsRegularEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_REGULAR_ENABLED, GamaHaptics.DEFAULT_REGULAR_ENABLED)) }
    var hapticsHoldEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_HOLD_ENABLED, GamaHaptics.DEFAULT_HOLD_ENABLED)) }
    var hapticsRendererEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_RENDERER_ENABLED, GamaHaptics.DEFAULT_RENDERER_ENABLED)) }
    var hapticsLanguageEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_LANGUAGE_ENABLED, GamaHaptics.DEFAULT_LANGUAGE_ENABLED)) }
    var hapticsBounceEnabled by remember { mutableStateOf(prefs.getBoolean(GamaHaptics.PREF_BOUNCE_ENABLED, GamaHaptics.DEFAULT_BOUNCE_ENABLED)) }
    var hapticsRegularStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_REGULAR_STRENGTH, GamaHaptics.DEFAULT_REGULAR_STRENGTH)) }
    var hapticsHoldStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_HOLD_STRENGTH, GamaHaptics.DEFAULT_HOLD_STRENGTH)) }
    var hapticsRendererStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_RENDERER_STRENGTH, GamaHaptics.DEFAULT_RENDERER_STRENGTH)) }
    var hapticsLanguageStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_LANGUAGE_STRENGTH, GamaHaptics.DEFAULT_LANGUAGE_STRENGTH)) }
    var hapticsBounceStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_BOUNCE_STRENGTH, GamaHaptics.DEFAULT_BOUNCE_STRENGTH)) }
    var hapticsBounceReturnStrength by remember { mutableStateOf(prefs.getInt(GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH, GamaHaptics.DEFAULT_BOUNCE_RETURN_STRENGTH)) }
    var particleNativeRefreshRate  by remember { mutableStateOf(prefs.getBoolean("particle_native_refresh_rate", false)) }
    var particleQuarterRefreshRate by remember { mutableStateOf(prefs.getBoolean("particle_quarter_refresh_rate", false)) }
    // Using SnapshotStateList for instant updates
    val excludedAppsList = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("excluded_apps", setOf()) ?: emptySet()) } }
    var oledMode by remember { mutableStateOf(prefs.getBoolean("oled_mode", false)) }
    var oledAccentColor by remember { mutableStateOf(Color(prefs.getInt("oled_accent_color", 0xFF4895EF.toInt()))) }
    var useDynamicColorOLED by remember { mutableStateOf(prefs.getBoolean("use_dynamic_color_oled", false)) }

    // Dynamic colors
    var useDynamicColor by remember { mutableStateOf(prefs.getBoolean("use_dynamic_color", true)) }
    var advancedColorPicker by remember { mutableStateOf(prefs.getBoolean("advanced_color_picker", false)) }
    var customAccentColor by remember { mutableStateOf(Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt()))) }
    var customGradientStart by remember { mutableStateOf(Color(prefs.getInt("custom_gradient_start", 0xFF0A2540.toInt()))) }
    var customGradientEnd by remember { mutableStateOf(Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))) }

    // Global back behavior toggle
    var dismissOnClickOutside by remember { mutableStateOf(prefs.getBoolean("dismiss_on_click_outside", true)) }

    // Aggressive mode confirmation
    var aggressiveModeConfirmed by remember { mutableStateOf(false) }
    var dontShowAggressiveWarning by remember { mutableStateOf(prefs.getBoolean("dont_show_aggressive_warning", false)) }


    // remember(timeOffsetHours): Calendar.getInstance() is called once per offset change,
    // not on every recomposition (which previously happened dozens of times per second).
    val timeAwareHourForTheme = remember(timeOffsetHours) {
        val raw = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        ((raw + timeOffsetHours.toInt()).let { h -> ((h % 24) + 24) % 24 })
    }
    val isDarkTheme = when (themePreference) {
        1 -> true
        2 -> false
        else -> systemInDarkTheme // Auto: always follow the OS dark/light mode setting
    }

    // In GAMA, dark mode is OLED mode. There is no separate dark-grey theme anymore.
    // This also makes Auto follow the system: if the phone is in dark mode, GAMA uses pure black.
    val effectiveOledMode = isDarkTheme || oledMode

    // Manual light/dark switching used to feel laggy because every major theme color
    // animated for hundreds of milliseconds, forcing the whole UI to redraw every frame.
    // During an actual mode flip we use a very short transition instead: still not a
    // harsh snap, but cheap enough to feel instant even with blur + matrix enabled.
    var themeModeSwitchInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveOledMode) {
        themeModeSwitchInProgress = true
        delay(140)
        themeModeSwitchInProgress = false
    }

    // remember(…): context.getColor() is a JNI call — memoize to avoid per-recomposition overhead.
    val dynamicAccent = remember(useDynamicColor, isDarkTheme, customAccentColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(context.getColor(android.R.color.system_accent1_400))
            } else {
                Color(context.getColor(android.R.color.system_accent1_600))
            }
        } else {
            customAccentColor
        }
    }

    // Single accent source for the whole app.
    // Important: since GAMA now treats dark mode as OLED mode, the old separate
    // oledAccentColor path made dark/OLED visuals ignore the main ACCENT COLOR.
    // This keeps cards, particles, Matrix rain, and the background gradient synced.
    val appAccent = dynamicAccent

    // Absolute visual accent used by background effects.
    // When Dynamic Color is OFF, this is the manual ACCENT COLOR picker value.
    // We pass this directly to Stars/Particles/Matrix instead of routing through
    // animated theme colors, so the background effects cannot stay stuck on blue/green.
    val effectsAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
        dynamicAccent
    } else {
        customAccentColor
    }

    val dynamicGradientStart = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(context.getColor(android.R.color.system_accent1_700))
            } else {
                Color(context.getColor(android.R.color.system_accent1_400))
            }
        } else {
            // Dynamic Color OFF: derive the gradient from ACCENT COLOR instead of
            // using stale saved gradient colors that can drift away from the accent.
            if (isDarkTheme) lerp(effectsAccent, Color.Black, 0.68f)
            else lerp(effectsAccent, Color.White, 0.22f)
        }
    }

    val dynamicGradientEnd = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(0xFF000000)
            } else {
                Color(context.getColor(android.R.color.system_neutral1_50))
            }
        } else {
            // Dynamic Color OFF: keep the lower gradient tied to the same accent,
            // just much softer so the UI stays readable.
            if (isDarkTheme) Color.Black
            else lerp(effectsAccent, Color.White, 0.90f)
        }
    }

    // ── Matrix/stars visual colors — driven by ACCENT COLOR / Dynamic Color ──
    // Important: these are animated colors, NOT keys. Changing ACCENT COLOR should
    // smoothly repaint existing Matrix/Stars particles instead of disposing and
    // respawning the overlays.
    val targetEffectsAccent = effectsAccent

    val targetMatrixHeadColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            Color(context.getColor(android.R.color.system_accent1_100))
        } else {
            lerp(effectsAccent, Color.White, 0.72f)
        }
    }
    val targetMatrixRainColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) Color(context.getColor(android.R.color.system_accent1_300))
            else Color(context.getColor(android.R.color.system_accent1_600))
        } else {
            effectsAccent
        }
    }
    val targetMatrixTrailColor = remember(useDynamicColor, isDarkTheme, effectsAccent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            Color(context.getColor(android.R.color.system_accent1_900))
        } else {
            lerp(effectsAccent, Color.Black, 0.70f)
        }
    }

    val animatedEffectsAccent by animateColorAsState(
        targetValue = targetEffectsAccent,
        animationSpec = if (animationLevel == 2) snap() else tween(durationMillis = if (themeModeSwitchInProgress) 110 else 240, easing = FastOutSlowInEasing),
        label = "effects_accent_anim"
    )
    val matrixHeadColor by animateColorAsState(
        targetValue = targetMatrixHeadColor,
        animationSpec = if (animationLevel == 2) snap() else tween(durationMillis = if (themeModeSwitchInProgress) 110 else 240, easing = FastOutSlowInEasing),
        label = "matrix_head_color_anim"
    )
    val matrixRainColor by animateColorAsState(
        targetValue = targetMatrixRainColor,
        animationSpec = if (animationLevel == 2) snap() else tween(durationMillis = if (themeModeSwitchInProgress) 110 else 240, easing = FastOutSlowInEasing),
        label = "matrix_rain_color_anim"
    )
    val matrixTrailColor by animateColorAsState(
        targetValue = targetMatrixTrailColor,
        animationSpec = if (animationLevel == 2) snap() else tween(durationMillis = if (themeModeSwitchInProgress) 110 else 240, easing = FastOutSlowInEasing),
        label = "matrix_trail_color_anim"
    )
    // Determine target colors.
    // Dark mode is OLED mode now: no dark-grey card theme, only pure black.
    val baseColors = if (effectiveOledMode) {
        ThemeColors.oledMode(appAccent)
    } else {
        ThemeColors.light(appAccent, dynamicGradientStart, dynamicGradientEnd)
    }

    // Use base colors directly - no color changing for success dialog
    val targetColors = baseColors

    // Synchronized color animations — all channels transition together so the UI
    // updates in perfect sync on every theme/accent/oled/dynamic-color change.
    // The accent color picker produces a natural wave feel just from the way
    // Compose recomposes (accent-derived values like border update one frame later
    // naturally), without needing artificial delays that desync fast toggles.
    val themeColorAnimSpec: AnimationSpec<Color> = when {
        animationLevel == 2 -> snap()
        themeModeSwitchInProgress -> tween(durationMillis = 110, easing = MotionTokens.Easing.emphasized)
        animationLevel == 1 -> tween(durationMillis = 140, easing = MotionTokens.Easing.emphasized)
        else -> tween(durationMillis = 220, easing = MotionTokens.Easing.emphasized)
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetColors.primaryAccent,
        animationSpec = themeColorAnimSpec,
        label = "accent_anim"
    )
    val animatedBorder by animateColorAsState(
        targetValue = targetColors.border,
        animationSpec = themeColorAnimSpec,
        label = "border_anim"
    )
    val animatedTextPrimary by animateColorAsState(
        targetValue = targetColors.textPrimary,
        animationSpec = themeColorAnimSpec,
        label = "text_primary_anim"
    )
    val animatedTextSecondary by animateColorAsState(
        targetValue = targetColors.textSecondary,
        animationSpec = themeColorAnimSpec,
        label = "text_secondary_anim"
    )
    val animatedCardBackground by animateColorAsState(
        targetValue = targetColors.cardBackground,
        animationSpec = themeColorAnimSpec,
        label = "card_bg_anim"
    )
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetColors.background,
        animationSpec = themeColorAnimSpec,
        label = "bg_anim"
    )
    val animatedGradientStart by animateColorAsState(
        targetValue = targetColors.gradientStart,
        animationSpec = themeColorAnimSpec,
        label = "grad_start_anim"
    )
    val animatedGradientEnd by animateColorAsState(
        targetValue = targetColors.gradientEnd,
        animationSpec = themeColorAnimSpec,
        label = "grad_end_anim"
    )

    val colors = targetColors.copy(
        background = animatedBackgroundColor,
        cardBackground = animatedCardBackground,
        primaryAccent = animatedAccent,
        gradientStart = animatedGradientStart,
        gradientEnd = animatedGradientEnd,
        textPrimary = animatedTextPrimary,
        textSecondary = animatedTextSecondary,
        border = animatedBorder
    )

    // Keep every card synced to the active theme colors.
    val cardBackground = colors.cardBackground

    fun savePreferences() {
        // Capture the excluded-apps list as an immutable snapshot *before* handing
        // off to the IO thread.  SnapshotStateList is not thread-safe: reading it
        // from a background thread while the UI thread mutates it can silently drop
        // or duplicate entries.  Calling toSet() here, on the main thread, produces
        // a stable copy that is safe to hand across the thread boundary.
        val excludedAppsSnapshot = excludedAppsList.toSet()

        // Snapshot every other Compose state value for the same reason: these objects
        // must only be read from the main thread, so we capture primitives/stable
        // values here before the coroutine crosses the thread boundary.
        val snapAnimLevel        = animationLevel
        val snapGradient         = gradientEnabled
        val snapParticles        = particlesEnabled
        val snapParticleSpeed    = particleSpeed
        val snapParallax         = particleParallaxEnabled
        val snapParallaxSens     = particleParallaxSensitivity
        val snapStarMode         = particleStarMode
        val snapTimeMode         = particleTimeMode
        val snapTimeOffset       = timeOffsetHours
        val snapParticleCount    = particleCount
        val snapParticleCustom   = particleCountCustom.toIntOrNull() ?: 150
        val snapBlur             = blurEnabled
        val snapThemePref        = themePreference
        val snapDynColor         = useDynamicColor
        val snapAdvColorPicker   = advancedColorPicker
        val snapAccent           = customAccentColor.toArgb()
        val snapGradStart        = customGradientStart.toArgb()
        val snapGradEnd          = customGradientEnd.toArgb()
        val snapUiScale          = uiScale
        val snapVerbose          = verboseMode
        val snapAggressive       = aggressiveMode
        val snapKillLauncher     = killLauncher
        val snapKillKeyboard     = killKeyboard
        val snapDoze             = dozeMode
        val snapShowGpuWatch     = showGpuWatchButton
        val snapStagger          = staggerEnabled
        val snapBackButtonAvoidance = backButtonAvoidanceEnabled
        val snapShadows          = shadowsEnabled
        val snapHapticsEnabled   = hapticsEnabled
        val snapHapticsRegularEnabled = hapticsRegularEnabled
        val snapHapticsHoldEnabled    = hapticsHoldEnabled
        val snapHapticsRendererEnabled = hapticsRendererEnabled
        val snapHapticsLanguageEnabled = hapticsLanguageEnabled
        val snapHapticsBounceEnabled   = hapticsBounceEnabled
        val snapHapticsRegular   = hapticsRegularStrength
        val snapHapticsHold      = hapticsHoldStrength
        val snapHapticsRenderer  = hapticsRendererStrength
        val snapHapticsLanguage  = hapticsLanguageStrength
        val snapHapticsBounce    = hapticsBounceStrength
        val snapHapticsBounceReturn = hapticsBounceReturnStrength
        val snapParticleNativeRefresh  = particleNativeRefreshRate
        val snapParticleQuarterRefresh = particleQuarterRefreshRate
        val snapMatrixMode       = matrixMode
        val snapMatrixSpeed      = matrixSpeed
        val snapMatrixDensity    = matrixDensity
        val snapMatrixFontSize   = matrixFontSize
        val snapMatrixFadeLength = matrixFadeLength
        val snapMatrixBgAlpha    = matrixBgAlpha
        val snapOled             = oledMode
        val snapOledAccent       = oledAccentColor.toArgb()
        val snapDynColorOled     = useDynamicColorOLED
        val snapDismissOutside   = dismissOnClickOutside
        val snapNotifEnabled     = notificationsEnabled
        val snapNotifInterval    = notifIntervalIndex
        val snapNotifLastSent    = lastNotifSentTime
        val snapNotifPermReq     = notifPermissionRequested

        scope.launch(Dispatchers.IO) {
            prefs.edit().apply {
                putInt("animation_level",               snapAnimLevel)
                putBoolean("gradient_enabled",          snapGradient)
                putBoolean("particles_enabled",         snapParticles)
                putInt("particle_speed",                snapParticleSpeed)
                putBoolean("particle_parallax_enabled", snapParallax)
                putInt("particle_parallax_sensitivity", snapParallaxSens)
                putBoolean("particle_star_mode",        snapStarMode)
                putBoolean("particle_time_mode",        snapTimeMode)
                putFloat("time_offset_hours",           snapTimeOffset)
                putInt("particle_count",                snapParticleCount)
                putInt("particle_count_custom",         snapParticleCustom)
                putBoolean("blur_enabled",              snapBlur)
                putInt("theme_preference",              snapThemePref)
                putBoolean("use_dynamic_color",         snapDynColor)
                putBoolean("advanced_color_picker",     snapAdvColorPicker)
                putInt("custom_accent",                 snapAccent)
                putInt("custom_gradient_start",         snapGradStart)
                putInt("custom_gradient_end",           snapGradEnd)
                putInt("ui_scale",                      snapUiScale)
                putBoolean("verbose_mode",              snapVerbose)
                putBoolean("aggressive_mode",           snapAggressive)
                putBoolean("kill_launcher",             snapKillLauncher)
                putBoolean("kill_keyboard",             snapKillKeyboard)
                putBoolean("doze_mode",                 snapDoze)
                putBoolean("show_gpuwatch_button",      snapShowGpuWatch)
                putBoolean("stagger_enabled",           snapStagger)
                putBoolean("back_button_avoidance_enabled", snapBackButtonAvoidance)
                putBoolean("shadows_enabled",           snapShadows)
                putBoolean(GamaHaptics.PREF_ENABLED,     snapHapticsEnabled)
                putBoolean(GamaHaptics.PREF_REGULAR_ENABLED,  snapHapticsRegularEnabled)
                putBoolean(GamaHaptics.PREF_HOLD_ENABLED,     snapHapticsHoldEnabled)
                putBoolean(GamaHaptics.PREF_RENDERER_ENABLED, snapHapticsRendererEnabled)
                putBoolean(GamaHaptics.PREF_LANGUAGE_ENABLED, snapHapticsLanguageEnabled)
                putBoolean(GamaHaptics.PREF_BOUNCE_ENABLED,   snapHapticsBounceEnabled)
                putInt(GamaHaptics.PREF_REGULAR_STRENGTH,  snapHapticsRegular)
                putInt(GamaHaptics.PREF_HOLD_STRENGTH,     snapHapticsHold)
                putInt(GamaHaptics.PREF_RENDERER_STRENGTH, snapHapticsRenderer)
                putInt(GamaHaptics.PREF_LANGUAGE_STRENGTH, snapHapticsLanguage)
                putInt(GamaHaptics.PREF_BOUNCE_STRENGTH,   snapHapticsBounce)
                putInt(GamaHaptics.PREF_BOUNCE_RETURN_STRENGTH, snapHapticsBounceReturn)
                putBoolean("particle_native_refresh_rate",  snapParticleNativeRefresh)
                putBoolean("particle_quarter_refresh_rate", snapParticleQuarterRefresh)
                putBoolean("matrix_native_refresh_rate",    false)
                putBoolean("matrix_quarter_refresh_rate",   false)
                putBoolean("matrix_mode",          snapMatrixMode)
                putInt("matrix_speed",             snapMatrixSpeed)
                putInt("matrix_density",           snapMatrixDensity)
                putInt("matrix_font_size",         snapMatrixFontSize)
                putInt("matrix_fade_length",       snapMatrixFadeLength)
                putFloat("matrix_bg_alpha",        snapMatrixBgAlpha)
                putStringSet("excluded_apps",           excludedAppsSnapshot)
                putBoolean("oled_mode",                 snapOled)
                putInt("oled_accent_color",             snapOledAccent)
                putBoolean("use_dynamic_color_oled",    snapDynColorOled)
                putBoolean("dismiss_on_click_outside",  snapDismissOutside)
                putBoolean("notif_enabled",             snapNotifEnabled)
                putInt("notif_interval_idx",            snapNotifInterval)
                putLong("notif_last_sent",              snapNotifLastSent)
                putBoolean("notif_perm_requested",      snapNotifPermReq)
                apply()
            }
        }
    }

    // ── Debounced save — for sliders that fire on every frame ────────────────
    // Toggles call savePreferences() directly (one-shot, cheap).
    // Sliders cancel any pending job and reschedule — only the final value
    // after the finger lifts is actually written to SharedPreferences.
    var pendingSaveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun savePreferencesDebounced(delayMs: Long = 300L) {
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(delayMs)
            savePreferences()
        }
    }

    fun closeAllPanelsForMainAction() {
        showWarningDialog = false
        showShizukuHelp = false
        showResourcesPanel = false
        showExternalLinkConfirm = false
        showGitHubDialog = false
        showIntegrationInfoDialog = false
        showSettings = false
        showSettingsSearch = false
        showHapticsPanel = false
        showAppearance = false
        showEffects = false
        showColorCustomization = false
        showGradient = false
        showSystem = false
        showRendererPanel = false
        showDeveloper = false
        showParticles = false
        showParticlesAppearance = false
        showParticlesMotion = false
        showParticlesPerformance = false
        showParticlesSettings = false
        showMatrixSettings = false
        showMatrixAppearance = false
        showMatrixMotion = false
        showNotifications = false
        showBackup = false
        showCrashLog = false
        showLanguage = false
        showEasterEgg = false
        showVerbosePanel = false
        showAggressiveWarning = false
        showGPUWatchConfirm = false
    }

    fun openMainPanelExclusive(open: () -> Unit) {
        closeAllPanelsForMainAction()
        open()
    }

    val animDuration = when (animationLevel) {
        0 -> 585
        1 -> 450
        else -> 0
    }

    // derivedStateOf ensures downstream recompositions only trigger when the boolean
    // *changes* (false→true or true→false), not on every recomposition that happens
    // to read one of the ~20 constituent state variables.
    val anyPanelOpen by remember {
        derivedStateOf {
            showWarningDialog || showGitHubDialog || showResourcesPanel || showExternalLinkConfirm ||
            showSettings || showSettingsSearch || showHapticsPanel || showAppearance || showColorCustomization || showGradient ||
            showSystem || showRendererPanel ||
            showShizukuHelp || showSuccessDialog || showClearRecentsDialog ||
            showVerbosePanel || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
            showEffects || showParticles || showParticlesAppearance ||
            showParticlesMotion || showParticlesPerformance || showMatrixSettings || showMatrixAppearance ||
            showMatrixMotion || showParticlesSettings || showIntegrationInfoDialog
        }
    }

    val anyFullPanelOpen by remember {
        derivedStateOf {
            showWarningDialog || showGitHubDialog || showResourcesPanel ||
            showSettings || showSettingsSearch || showHapticsPanel || showAppearance || showColorCustomization || showGradient ||
            showSystem || showRendererPanel ||
            showShizukuHelp || showSuccessDialog || showClearRecentsDialog ||
            showVerbosePanel || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
            showEffects || showParticles || showParticlesAppearance ||
            showParticlesMotion || showParticlesPerformance || showMatrixSettings || showMatrixAppearance ||
            showMatrixMotion || showParticlesSettings
        }
    }

    val currentVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) { "—" }
    }

    // Gradient "Come Alive" Animation on Startup
    val gradientStartupAlpha = remember { Animatable(0f) }

    // Stronger breathing gradient loop.
    // The InfiniteTransition is only created (and only ticks) when the gradient
    // is actually visible — when gradientEnabled is false the slot returns a
    // static 1f so the transition object is never allocated and zero frames are
    // spent animating an invisible layer.
    val breathingAlpha by if (gradientEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = MotionTokens.Easing.silk),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_alpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    LaunchedEffect(Unit) {
        // Startup Sequence: Elements appear with staggering
        // Show main UI immediately
        isVisible = true

        // Gradient ramps up slowly (0 -> 1 over 2000ms) — emphasizedDecelerate means
        // it rushes in quickly then gently settles, giving the UI a confident first impression.
        gradientStartupAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1800, easing = MotionTokens.Easing.emphasizedDecelerate)
        )
    }

    LaunchedEffect(Unit) {
        shizukuRunning = ShizukuHelper.checkBinder()
        shizukuPermissionGranted = ShizukuHelper.checkPermission()

        // ── Request notification permission once on first launch ─────────────
        if (!notifPermissionRequested && !ShizukuHelper.hasNotificationPermission(context)) {
            notifPermissionRequested = true
            prefs.edit().putBoolean("notif_perm_requested", true).apply()
            delay(1500) // Let the UI settle before the OS dialog appears
            onRequestNotificationPermission()
        }

        // Dismiss one-time button labels after 3 seconds
        if (showButtonLabels) {
            delay(3000)
            showButtonLabels = false
            prefs.edit().putBoolean("button_labels_shown", true).apply()
        }

        shizukuStatus = if (shizukuRunning) {
            if (shizukuPermissionGranted) {
                if (userName.isNotEmpty()) strings["main.shizuku_ready_named"].replace("%s", userName).ifEmpty { "You're all set, $userName! ✅" } else strings["main.shizuku_ready"].ifEmpty { "Shizuku is running ✅" }
            } else {
                strings["main.shizuku_permission_needed"].ifEmpty { "Permission needed ⚠️" }
            }
        } else {
            strings["main.shizuku_not_running"].ifEmpty { "Shizuku isn't running ❌" }
        }

        scope.launch {
            // ── Renderer detection ────────────────────────────────────────────
            // Priority order:
            //   1. If Shizuku is available, ask it directly — most authoritative.
            //   2. Otherwise use reboot detection to make a reliable offline guess.
            //
            // Reboot detection uses last_switch_uptime (elapsedRealtime at switch
            // time) rather than last_switch_time (wall-clock). elapsedRealtime
            // resets to ~0 on every boot, so if the stored uptime is greater than
            // the current elapsedRealtime the device has definitely rebooted and
            // the runtime prop has been cleared. Wall-clock comparison was broken
            // for any switch older than the current uptime (e.g. switched 16 days
            // ago — bootTimeMs is always > lastSwitchMs even with no reboot).
            if (shizukuRunning && shizukuPermissionGranted) {
                // Shizuku is live — get the ground truth directly from the system.
                val detectedRenderer = ShizukuHelper.getCurrentRenderer()
                when (detectedRenderer) {
                    "Vulkan", "OpenGL" -> {
                        currentRenderer = detectedRenderer
                        prefs.edit().putString("last_renderer", detectedRenderer).apply()
                    }
                    "Default", "Not Set" -> {
                        // Prop is empty — post-reboot state means OpenGL default
                        currentRenderer = "OpenGL"
                        prefs.edit().putString("last_renderer", "OpenGL").apply()
                    }
                    // "Unknown" / error: Shizuku command failed — keep existing value.
                }
            } else {
                // Shizuku unavailable — use offline reboot detection.
                // Only correct to OpenGL (and write prefs) if we are certain a
                // reboot occurred; otherwise trust last_renderer as-is.
                val lastSwitchUptime = prefs.getLong("last_switch_uptime", 0L)
                val rebootDetected = if (lastSwitchUptime > 0L) {
                    // Reliable path: uptime stamp present
                    android.os.SystemClock.elapsedRealtime() < lastSwitchUptime
                } else {
                    // Legacy fallback for installs that predate the uptime key.
                    // Only trust the wall-clock comparison when the switch was
                    // recent (< 12 hours ago) to avoid false positives on old switches.
                    val lastSwitchMs = prefs.getLong("last_switch_time", 0L)
                    val bootTimeMs   = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
                    lastSwitchMs > 0L &&
                        bootTimeMs > lastSwitchMs &&
                        (System.currentTimeMillis() - lastSwitchMs) < 12 * 60 * 60 * 1000L
                }
                if (rebootDetected && prefs.getString("last_renderer", "OpenGL") == "Vulkan") {
                    currentRenderer = "OpenGL"
                    prefs.edit().putString("last_renderer", "OpenGL").apply()
                }
                // No reboot detected → leave currentRenderer unchanged (already
                // initialised from last_renderer pref at the top of GamaUI).
            }
            rendererLoading = false // Done — stop skeleton shimmer regardless of outcome
        }

    }

    // ── OpenGL reminder notification loop ─────────────────────────────────────
    // Runs while the app is in the foreground. Interval options (hours):
    // index: 0=2h, 1=4h, 2=6h, 3=12h, 4=24h
    LaunchedEffect(notificationsEnabled, notifIntervalIndex) {
        val intervalMs: Long = when (notifIntervalIndex) {
            0 -> 2L * 3_600_000L
            1 -> 4L * 3_600_000L
            3 -> 12L * 3_600_000L
            4 -> 24L * 3_600_000L
            else -> 6L * 3_600_000L // default = 6 h
        }
        // Poll every 15 minutes, fire when the interval has elapsed
        while (isActive) {
            // No point waking up at all if the renderer is already Vulkan
            if (currentRenderer != "OpenGL") {
                delay(15 * 60_000L)
                continue
            }
            delay(15 * 60_000L) // check every 15 min
            if (!notificationsEnabled) continue
            if (!ShizukuHelper.hasNotificationPermission(context)) continue
            if (currentRenderer != "OpenGL") continue
            val now = System.currentTimeMillis()
            if (now - lastNotifSentTime >= intervalMs) {
                val sent = sendOpenGLReminderNotification(context, userName)
                if (sent) {
                    lastNotifSentTime = now
                    prefs.edit().putLong("notif_last_sent", now).apply()
                }
            }
        }
    }

    // When a renderer switch is in progress, commandOutput being set is the real
    // completion signal from ShizukuHelper — the shell command has finished.
    // Flip rendererSwitching off so SuccessDialog transitions from its spinner
    // to the final confirmation message.
    LaunchedEffect(commandOutput) {
        if (rendererSwitching && commandOutput.isNotEmpty()) {
            rendererSwitching = false
        }
    }

    // UI Scale Multiplier Calculation (removed 50% for better UX)
    val uiScaleMultiplier = when (uiScale) {
        0 -> 0.75f // 75%
        2 -> 1.25f // 125%
        else -> 1f // 100%
    }

    // Instant UI scale change (no animation to prevent lag)
    // Using remember to prevent recomposition lag
    val animatedUiScale = remember(uiScale) { uiScaleMultiplier }

    // Adaptive type scale — computed once here, available everywhere via LocalTypeScale
    val typeScale = rememberAdaptiveType()

    // Provide the scaled density to the entire app
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalAnimationLevel provides animationLevel,
        LocalThemeColors provides colors,
        LocalUIScale provides uiScale,
        LocalDismissOnClickOutside provides dismissOnClickOutside,
        LocalStaggerEnabled provides staggerEnabled,
        LocalBackButtonAvoidanceEnabled provides backButtonAvoidanceEnabled,
        LocalShadowsEnabled provides (shadowsEnabled && !effectiveOledMode),
        LocalTypeScale provides typeScale,
        LocalDensity provides Density(
            density = currentDensity.density * animatedUiScale,
            fontScale = currentDensity.fontScale // fontScale includes user system pref, we multiply density so fonts scale too
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor)
                .graphicsLayer { clip = false } // Hardware acceleration for smooth animations
        ) {
            // Gradient Overlay
            val gradientAlpha by animateFloatAsState(
                targetValue = if (gradientEnabled && !effectiveOledMode) 1f else 0f,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(durationMillis = if (themeModeSwitchInProgress) 90 else 260, easing = MotionTokens.Easing.emphasizedDecelerate),
                label = "gradient_visibility"
            )

            // Combined alpha: Enabled Toggle * Startup Ramp Up
            val finalGradientAlpha = gradientAlpha * gradientStartupAlpha.value

            if (finalGradientAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(finalGradientAlpha)
                ) {
                    // Use graphicsLayer for breathingAlpha so the animation only triggers
                    // a draw-phase update, not a full recomposition on every frame.
                    // This is the single biggest idle-CPU saving: previously the entire
                    // content tree recomposed every ~16ms just because of this one value.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = breathingAlpha * if (isDarkTheme) 1f else 0.65f
                            }
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        animatedGradientStart,
                                        animatedGradientEnd
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY * 0.5f
                                )
                            )
                    )
                }
            }

            // (Grain overlay removed)

            // ── Blur ────────────────────────────────────────────────────────────────────
            // Single render of mainContent — derivedStateOf ensures recomposition
            // only fires when blur state CHANGES (panel open/close), NOT every frame.
            // Previously: mainContent() rendered TWICE (sharp + blurred copies) with
            // three animated floats that caused per-frame recompositions the entire
            // time any panel was open, AND doubled GPU rendering work throughout.
            // Now: ONE render, Modifier.blur(40.dp) toggled on/off.  The panel’s own
            // BouncyDialog entrance animation (scale + fade) provides all visual
            // smoothness — no animated blur-radius transition is needed.
            val blurShouldApply by remember {
                derivedStateOf {
                    (anyFullPanelOpen && blurEnabled) || showAggressiveWarning
                }
            }


            // Animated parallax sensitivity with smooth transition
            val targetParallaxSensitivity = when (particleParallaxSensitivity) {
                0 -> 0.003125f  // Low (0.5x current: 0.00625 * 0.5)
                1 -> 0.01875f    // Medium (0.75x current: 0.025 * 0.75)
                2 -> 0.05f     // High (0.5x less: 0.10 * 0.5)
                else -> 0.01875f
            }
            val animatedParallaxSensitivity by animateFloatAsState(
                targetValue = targetParallaxSensitivity,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing  // Ease-in-out
                ),
                label = "parallax_sensitivity"
            )

            // Get current hour for greeting — respects developer time offset
            val currentHour = remember(timeOffsetHours) {
                val raw = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                ((raw + timeOffsetHours.toInt()).let { h -> ((h % 24) + 24) % 24 })
            }

            // ── Main content ──────────────────────────────────────────────────
            // The UI tree is captured once as a lambda and called in two places:
            // mainContent captures the entire main UI tree as a lambda so it can
            // be rendered in the sharp layer and the blurred layer independently.
            val mainContent: @Composable () -> Unit = {
                // Main UI Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 0.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isLandscape) {
                        // Landscape Layout — portrait style, split left/right, perfectly fitted
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val lsAvailH  = maxHeight
                            val lsAvailW  = maxWidth
                            // All spacing derived from available height so nothing ever overflows
                            val lsItemSp  = (lsAvailH * 0.036f).coerceIn(6.dp,  14.dp)
                            val lsVPad    = (lsAvailH * 0.07f ).coerceIn(10.dp, 28.dp)
                            val lsHPad    = (lsAvailW * 0.025f).coerceIn(16.dp, 32.dp)

                            Row(modifier = Modifier.fillMaxSize()) {

                                // ── LEFT HALF ────────────────────────────────────────────────
                                // Greeting · GAMA title+bars · Standing by · Choose your path
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                        .padding(horizontal = lsHPad, vertical = lsVPad),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement  = Arrangement.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement  = Arrangement.spacedBy(lsItemSp)
                                    ) {
                                        // Greeting — same compact pools as portrait
                                        val lsGreeting = remember(currentHour, userName, strings) {
                                            val pool: Array<String> = when (currentHour) {
                                                in 0..5   -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_late_named_1"].replace("%s",userName).ifEmpty{"Still up, $userName? 🌙"},strings["greetings.compact_late_named_2"].replace("%s",userName).ifEmpty{"Late night session, $userName? 🌙"},strings["greetings.compact_late_named_3"].replace("%s",userName).ifEmpty{"The world is quiet, $userName. 🌙"},strings["greetings.compact_late_named_4"].replace("%s",userName).ifEmpty{"Somewhere between today and tomorrow, $userName. 🌙"}) else arrayOf(strings["greetings.compact_late_1"].ifEmpty{"Still up? 🌙"},strings["greetings.compact_late_2"].ifEmpty{"Late night session? 🌙"},strings["greetings.compact_late_3"].ifEmpty{"The world is quiet. 🌙"},strings["greetings.compact_late_4"].ifEmpty{"Somewhere between today and tomorrow. 🌙"})
                                                in 6..11  -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_morning_named_1"].replace("%s",userName).ifEmpty{"Good morning, $userName! ☀️"},strings["greetings.compact_morning_named_2"].replace("%s",userName).ifEmpty{"Morning, $userName! ☀️"},strings["greetings.compact_morning_named_3"].replace("%s",userName).ifEmpty{"Rise and shine, $userName! ☀️"},strings["greetings.compact_morning_named_4"].replace("%s",userName).ifEmpty{"A fresh start, $userName. ☀️"},strings["greetings.compact_morning_named_5"].replace("%s",userName).ifEmpty{"Up early, $userName? ☀️"}) else arrayOf(strings["greetings.compact_morning_1"].ifEmpty{"Good morning! ☀️"},strings["greetings.compact_morning_2"].ifEmpty{"Morning! ☀️"},strings["greetings.compact_morning_3"].ifEmpty{"Rise and shine! ☀️"},strings["greetings.compact_morning_4"].ifEmpty{"A fresh start. ☀️"},strings["greetings.compact_morning_5"].ifEmpty{"Up early? ☀️"})
                                                in 12..16 -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_afternoon_named_1"].replace("%s",userName).ifEmpty{"Hey, $userName! 👋"},strings["greetings.compact_afternoon_named_2"].replace("%s",userName).ifEmpty{"Good afternoon, $userName! 👋"},strings["greetings.compact_afternoon_named_3"].replace("%s",userName).ifEmpty{"Welcome back, $userName. 👋"},strings["greetings.compact_afternoon_named_4"].replace("%s",userName).ifEmpty{"There you are, $userName! 👋"},strings["greetings.compact_afternoon_named_5"].replace("%s",userName).ifEmpty{"Good to see you, $userName. 👋"}) else arrayOf(strings["greetings.compact_afternoon_1"].ifEmpty{"Hey! 👋"},strings["greetings.compact_afternoon_2"].ifEmpty{"Good afternoon! 👋"},strings["greetings.compact_afternoon_3"].ifEmpty{"Welcome back. 👋"},strings["greetings.compact_afternoon_4"].ifEmpty{"There you are! 👋"},strings["greetings.compact_afternoon_5"].ifEmpty{"Good to see you. 👋"})
                                                in 17..22 -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_evening_named_1"].replace("%s",userName).ifEmpty{"Good evening, $userName! 🌙"},strings["greetings.compact_evening_named_2"].replace("%s",userName).ifEmpty{"Evening, $userName. 🌙"},strings["greetings.compact_evening_named_3"].replace("%s",userName).ifEmpty{"Winding down, $userName? 🌙"},strings["greetings.compact_evening_named_4"].replace("%s",userName).ifEmpty{"End of the day, $userName. 🌙"},strings["greetings.compact_evening_named_5"].replace("%s",userName).ifEmpty{"Hope it was a good one, $userName. 🌙"}) else arrayOf(strings["greetings.compact_evening_1"].ifEmpty{"Good evening! 🌙"},strings["greetings.compact_evening_2"].ifEmpty{"Evening. 🌙"},strings["greetings.compact_evening_3"].ifEmpty{"Winding down? 🌙"},strings["greetings.compact_evening_4"].ifEmpty{"End of the day. 🌙"},strings["greetings.compact_evening_5"].ifEmpty{"Hope it was a good one. 🌙"})
                                                else      -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_midnight_named_1"].replace("%s",userName).ifEmpty{"Still at it, $userName? 🌙"},strings["greetings.compact_midnight_named_2"].replace("%s",userName).ifEmpty{"The quiet hours, $userName. 🌙"},strings["greetings.compact_midnight_named_3"].replace("%s",userName).ifEmpty{"Almost tomorrow, $userName. 🌙"},strings["greetings.compact_midnight_named_4"].replace("%s",userName).ifEmpty{"Some nights are for thinking, $userName. 🌙"},strings["greetings.compact_midnight_named_5"].replace("%s",userName).ifEmpty{"The world can wait, $userName. 🌙"}) else arrayOf(strings["greetings.compact_midnight_1"].ifEmpty{"Still at it? 🌙"},strings["greetings.compact_midnight_2"].ifEmpty{"The quiet hours. 🌙"},strings["greetings.compact_midnight_3"].ifEmpty{"Almost tomorrow. 🌙"},strings["greetings.compact_midnight_4"].ifEmpty{"Some nights are for thinking. 🌙"},strings["greetings.compact_midnight_5"].ifEmpty{"The world can wait. 🌙"})
                                            }
                                            pool.random()
                                        }
                                        AnimatedElement(visible = isVisible, staggerIndex = 0, totalItems = 8) {
                                            Text(
                                                text = lsGreeting,
                                                fontSize = ts.bodyLarge,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.textSecondary.copy(alpha = 0.8f),
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // GAMA title with decorative bars — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 1, totalItems = 8) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(4.dp)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    colors.primaryAccent.copy(alpha = 0f),
                                                                    colors.primaryAccent.copy(alpha = 1f)
                                                                )
                                                            )
                                                        )
                                                )
                                                val lsGamaTextSize = (ts.displayLarge.value * 1.22f).sp
                                                val lsGamaGlowPaint = remember(colors.textPrimary, lsGamaTextSize) {
                                                    android.graphics.Paint().apply {
                                                        isAntiAlias = true
                                                        color = android.graphics.Color.TRANSPARENT
                                                        setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                        textAlign = android.graphics.Paint.Align.CENTER
                                                        isDither = true
                                                    }
                                                }
                                                Text(
                                                    text = "GAMA",
                                                    fontSize = lsGamaTextSize,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = quicksandFontFamily,
                                                    color = colors.textPrimary,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier
                                                        .padding(horizontal = 24.dp)
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onLongPress = {
                                                                    performHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                                    showEasterEgg = true
                                                                }
                                                            )
                                                        }
                                                        .drawWithContent {
                                                            drawIntoCanvas { canvas ->
                                                                lsGamaGlowPaint.textSize = lsGamaTextSize.toPx()
                                                                canvas.nativeCanvas.drawText(
                                                                    "GAMA",
                                                                    size.width / 2,
                                                                    size.height / 2 + (lsGamaTextSize.toPx() * 0.25f),
                                                                    lsGamaGlowPaint
                                                                )
                                                            }
                                                            drawContent()
                                                        }
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(4.dp)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    colors.primaryAccent.copy(alpha = 1f),
                                                                    colors.primaryAccent.copy(alpha = 0f)
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }

                                        // Standing by — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 2, totalItems = 8) {
                                            Text(
                                                text = strings["main.standing_by"].ifEmpty { "Standing by and awaiting your command" },
                                                fontSize = ts.bodyLarge,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.textSecondary,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Choose your path — identical to portrait
                                        AnimatedElement(visible = isVisible, staggerIndex = 3, totalItems = 8) {
                                            Text(
                                                text = strings["main.whats_next"].ifEmpty { "CHOOSE YOUR PATH." },
                                                fontSize = ts.headlineSmall,
                                                fontFamily = quicksandFontFamily,
                                                color = colors.primaryAccent.copy(alpha = 0.88f),
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // ── RIGHT HALF ───────────────────────────────────────────────
                                // Unified frosted-glass box — identical to portrait's single card
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f)
                                        .padding(start = 0.dp, top = lsVPad, end = lsHPad, bottom = lsVPad),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedElement(
                                        visible = isVisible, staggerIndex = 4, cardShadow = false,
                                        totalItems = 8, enabled = shizukuRunning && shizukuPermissionGranted
                                    ) {
                                        val lsShizukuReady = shizukuRunning && shizukuPermissionGranted

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(44.dp))
                                        ) {
                                            // Transparent card — no blur, no frosted backdrop.
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(Color.Transparent)
                                                    .border(
                                                        width = 1.dp,
                                                        brush = Brush.linearGradient(
                                                            colors = listOf(
                                                                colors.primaryAccent.copy(alpha = 0.45f),
                                                                colors.border.copy(alpha = 0.12f),
                                                                colors.primaryAccent.copy(alpha = 0.28f)
                                                            )
                                                        ),
                                                        shape = RoundedCornerShape(44.dp)
                                                    )
                                            )
                                            // Sharp content layer
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                RendererCard(
                                                    currentRenderer = currentRenderer,
                                                    commandOutput = commandOutput,
                                                    isSmallScreen = isSmallScreen,
                                                    colors = colors,
                                                    cardBackground = cardBackground,
                                                    shizukuReady = lsShizukuReady,
                                                    shizukuRunning = shizukuRunning,
                                                    shizukuStatus = shizukuStatus,
                                                    onShizukuErrorClick = {
                                                        if (shizukuStatus.contains("❌") || shizukuStatus.contains("⚠️")) {
                                                            performHaptic()
                                                            shizukuHelpType = when {
                                                                shizukuStatus.contains("❌") -> "not_running"
                                                                shizukuStatus.contains("⚠️") -> "permission"
                                                                else -> ""
                                                            }
                                                            openMainPanelExclusive { showShizukuHelp = true }
                                                        }
                                                    },
                                                    oledMode = effectiveOledMode,
                                                    rendererLoading = rendererLoading,
                                                    lastSwitchTime = lastSwitchTime
                                                )
                                                // Vulkan | OpenGL
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateContentSize(
                                                            animationSpec = spring(
                                                                dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                stiffness = MotionTokens.Springs.smooth.stiffness
                                                            )
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    BigRendererButton(
                                                        text = strings["renderer.vulkan"].ifEmpty { "Vulkan" },
                                                        onClick = {
                                                            performRendererHaptic()
                                                            if (!shizukuRunning || !shizukuPermissionGranted) {
                                                                shizukuHelpType = if (!shizukuRunning) "not_running" else "permission"
                                                                openMainPanelExclusive { showShizukuHelp = true }
                                                                return@BigRendererButton
                                                            }
                                                            pendingRendererName = "Vulkan"
                                                            pendingRendererSwitch = {
                                                                verboseOutput = ""
                                                                if (verboseMode) showVerbosePanel = true
                                                                ShizukuHelper.runVulkan(
                                                                    context, scope, aggressiveMode,
                                                                    killLauncher,
                                                                    killKeyboard,
                                                                    excludedAppsList.toSet(), emptySet(),
                                                                    { commandOutput = it },
                                                                    if (verboseMode) { output -> verboseOutput += output } else null
                                                                )
                                                            }
                                                            successDialogMessage = strings["main.vulkan_applied"].ifEmpty { "Vulkan has been applied!" }
                                                            openMainPanelExclusive { showWarningDialog = true }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        isSelected = currentRenderer == "Vulkan",
                                                        forceHighlight = true,
                                                        enabled = lsShizukuReady,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "vulkan"
                                                    )
                                                    BigRendererButton(
                                                        text = strings["renderer.opengl"].ifEmpty { "OpenGL" },
                                                        onClick = {
                                                            performRendererHaptic()
                                                            if (!shizukuRunning || !shizukuPermissionGranted) {
                                                                shizukuHelpType = if (!shizukuRunning) "not_running" else "permission"
                                                                openMainPanelExclusive { showShizukuHelp = true }
                                                                return@BigRendererButton
                                                            }
                                                            pendingRendererName = "OpenGL"
                                                            pendingRendererSwitch = {
                                                                verboseOutput = ""
                                                                if (verboseMode) showVerbosePanel = true
                                                                ShizukuHelper.runOpenGL(
                                                                    context, scope, aggressiveMode,
                                                                    killLauncher,
                                                                    killKeyboard,
                                                                    excludedAppsList.toSet(), emptySet(),
                                                                    { commandOutput = it },
                                                                    if (verboseMode) { output -> verboseOutput += output } else null
                                                                )
                                                            }
                                                            successDialogMessage = strings["main.opengl_applied"].ifEmpty { "OpenGL has been applied!" }
                                                            openMainPanelExclusive { showWarningDialog = true }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        isSelected = false,
                                                        enabled = lsShizukuReady,
                                                        colors = colors,
                                                        oledMode = effectiveOledMode,
                                                        iconType = "opengl"
                                                    )
                                                }
                                                // Resources | GPUWatch
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .animateContentSize(
                                                            animationSpec = spring(
                                                                dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                stiffness = MotionTokens.Springs.smooth.stiffness
                                                            )
                                                        ),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    IllustratedButton(
                                                        text = strings["integrations.widget_action"].ifEmpty { "Library" },
                                                        onClick = {
                                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                            openMainPanelExclusive { showResourcesPanel = true }
                                                        },
                                                        modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                                        accent = false, enabled = true,
                                                        colors = colors, oledMode = effectiveOledMode, iconType = "resources"
                                                    )
                                                    AnimatedVisibility(
                                                        modifier = Modifier.weight(1f),
                                                        visible = showGpuWatchButton,
                                                        enter = fadeIn(animationSpec = tween(220, easing = MotionTokens.Easing.enter)) +
                                                                expandHorizontally(
                                                                    animationSpec = spring(
                                                                        dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                        stiffness = MotionTokens.Springs.smooth.stiffness
                                                                    ),
                                                                    expandFrom = Alignment.Start
                                                                ) +
                                                                scaleIn(
                                                                    initialScale = 0.88f,
                                                                    animationSpec = spring(
                                                                        dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                                                                        stiffness = MotionTokens.Springs.gentle.stiffness
                                                                    )
                                                                ),
                                                        exit = fadeOut(animationSpec = tween(160, easing = MotionTokens.Easing.exit)) +
                                                                shrinkHorizontally(
                                                                    animationSpec = tween(180, easing = MotionTokens.Easing.exit),
                                                                    shrinkTowards = Alignment.Start
                                                                ) +
                                                                scaleOut(
                                                                    targetScale = 0.88f,
                                                                    animationSpec = tween(160, easing = MotionTokens.Easing.exit)
                                                                )
                                                    ) {
                                                        IllustratedButton(
                                                            text = strings["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
                                                            onClick = {
                                                                performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                                openMainPanelExclusive { showGPUWatchConfirm = true }
                                                            },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            accent = false, enabled = true,
                                                            colors = colors, oledMode = effectiveOledMode, iconType = "gpuwatch"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Portrait Layout - Single column using 100% vertical space
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center // Changed back to Center to bring groups together
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 12.dp else 16.dp) // Single spacing for everything
                            ) {
                                // Greeting
                                // Greeting — selected once per hour/name change, stable during the entrance animation.
                                // pool.random() is called inside remember so it only runs when keys change,
                                // not on every recomposition that happens during the 585 ms fade-in.
                                val greeting = remember(currentHour, userName, strings) {
                                    val pool: Array<String> = when (currentHour) {
                                        in 0..5   -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_late_named_1"].replace("%s",userName).ifEmpty{"Still up, $userName? 🌙"},strings["greetings.compact_late_named_2"].replace("%s",userName).ifEmpty{"Late night session, $userName? 🌙"},strings["greetings.compact_late_named_3"].replace("%s",userName).ifEmpty{"The world is quiet, $userName. 🌙"},strings["greetings.compact_late_named_4"].replace("%s",userName).ifEmpty{"Somewhere between today and tomorrow, $userName. 🌙"}) else arrayOf(strings["greetings.compact_late_1"].ifEmpty{"Still up? 🌙"},strings["greetings.compact_late_2"].ifEmpty{"Late night session? 🌙"},strings["greetings.compact_late_3"].ifEmpty{"The world is quiet. 🌙"},strings["greetings.compact_late_4"].ifEmpty{"Somewhere between today and tomorrow. 🌙"})
                                        in 6..11  -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_morning_named_1"].replace("%s",userName).ifEmpty{"Good morning, $userName! ☀️"},strings["greetings.compact_morning_named_2"].replace("%s",userName).ifEmpty{"Morning, $userName! ☀️"},strings["greetings.compact_morning_named_3"].replace("%s",userName).ifEmpty{"Rise and shine, $userName! ☀️"},strings["greetings.compact_morning_named_4"].replace("%s",userName).ifEmpty{"A fresh start, $userName. ☀️"},strings["greetings.compact_morning_named_5"].replace("%s",userName).ifEmpty{"Up early, $userName? ☀️"}) else arrayOf(strings["greetings.compact_morning_1"].ifEmpty{"Good morning! ☀️"},strings["greetings.compact_morning_2"].ifEmpty{"Morning! ☀️"},strings["greetings.compact_morning_3"].ifEmpty{"Rise and shine! ☀️"},strings["greetings.compact_morning_4"].ifEmpty{"A fresh start. ☀️"},strings["greetings.compact_morning_5"].ifEmpty{"Up early? ☀️"})
                                        in 12..16 -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_afternoon_named_1"].replace("%s",userName).ifEmpty{"Hey, $userName! 👋"},strings["greetings.compact_afternoon_named_2"].replace("%s",userName).ifEmpty{"Good afternoon, $userName! 👋"},strings["greetings.compact_afternoon_named_3"].replace("%s",userName).ifEmpty{"Welcome back, $userName. 👋"},strings["greetings.compact_afternoon_named_4"].replace("%s",userName).ifEmpty{"There you are, $userName! 👋"},strings["greetings.compact_afternoon_named_5"].replace("%s",userName).ifEmpty{"Good to see you, $userName. 👋"}) else arrayOf(strings["greetings.compact_afternoon_1"].ifEmpty{"Hey! 👋"},strings["greetings.compact_afternoon_2"].ifEmpty{"Good afternoon! 👋"},strings["greetings.compact_afternoon_3"].ifEmpty{"Welcome back. 👋"},strings["greetings.compact_afternoon_4"].ifEmpty{"There you are! 👋"},strings["greetings.compact_afternoon_5"].ifEmpty{"Good to see you. 👋"})
                                        in 17..22 -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_evening_named_1"].replace("%s",userName).ifEmpty{"Good evening, $userName! 🌙"},strings["greetings.compact_evening_named_2"].replace("%s",userName).ifEmpty{"Evening, $userName. 🌙"},strings["greetings.compact_evening_named_3"].replace("%s",userName).ifEmpty{"Winding down, $userName? 🌙"},strings["greetings.compact_evening_named_4"].replace("%s",userName).ifEmpty{"End of the day, $userName. 🌙"},strings["greetings.compact_evening_named_5"].replace("%s",userName).ifEmpty{"Hope it was a good one, $userName. 🌙"}) else arrayOf(strings["greetings.compact_evening_1"].ifEmpty{"Good evening! 🌙"},strings["greetings.compact_evening_2"].ifEmpty{"Evening. 🌙"},strings["greetings.compact_evening_3"].ifEmpty{"Winding down? 🌙"},strings["greetings.compact_evening_4"].ifEmpty{"End of the day. 🌙"},strings["greetings.compact_evening_5"].ifEmpty{"Hope it was a good one. 🌙"})
                                        else      -> if (userName.isNotEmpty()) arrayOf(strings["greetings.compact_midnight_named_1"].replace("%s",userName).ifEmpty{"Still at it, $userName? 🌙"},strings["greetings.compact_midnight_named_2"].replace("%s",userName).ifEmpty{"The quiet hours, $userName. 🌙"},strings["greetings.compact_midnight_named_3"].replace("%s",userName).ifEmpty{"Almost tomorrow, $userName. 🌙"},strings["greetings.compact_midnight_named_4"].replace("%s",userName).ifEmpty{"Some nights are for thinking, $userName. 🌙"},strings["greetings.compact_midnight_named_5"].replace("%s",userName).ifEmpty{"The world can wait, $userName. 🌙"}) else arrayOf(strings["greetings.compact_midnight_1"].ifEmpty{"Still at it? 🌙"},strings["greetings.compact_midnight_2"].ifEmpty{"The quiet hours. 🌙"},strings["greetings.compact_midnight_3"].ifEmpty{"Almost tomorrow. 🌙"},strings["greetings.compact_midnight_4"].ifEmpty{"Some nights are for thinking. 🌙"},strings["greetings.compact_midnight_5"].ifEmpty{"The world can wait. 🌙"})
                                    }
                                    pool.random()
                                }
                                AnimatedElement(visible = isVisible, staggerIndex = 0,
                                    totalItems = 8) {
                                    Text(
                                        text = greeting,
                                        fontSize = ts.bodyLarge,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // GAMA Title with bars
                                AnimatedElement(visible = isVisible, staggerIndex = 1,
                                    totalItems = 8) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 0f),
                                                            colors.primaryAccent.copy(alpha = 1f)
                                                        )
                                                    )
                                                )
                                        )

                                        val gamaTextSize = (ts.displayLarge.value * 1.22f).sp  // Hero GAMA text
                                        val context = LocalContext.current
                                        val quicksandBoldTypeface = remember {
                                            try {
                                                android.graphics.Typeface.createFromAsset(context.assets, "fonts/quicksand_bold.ttf")
                                            } catch (e: Exception) {
                                                android.graphics.Typeface.DEFAULT_BOLD
                                            }
                                        }
                                        // Cache Paint outside drawWithContent — same fix as TitleSection.
                                        // Allocating Paint+setShadowLayer inside the draw lambda runs every
                                        // frame and generates GC pressure on JIT-based older devices.
                                        val gamaGlowPaint = remember(colors.textPrimary, gamaTextSize) {
                                            android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                color = android.graphics.Color.TRANSPARENT
                                                setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                textAlign = android.graphics.Paint.Align.CENTER
                                                isDither = true
                                            }
                                        }

                                        Text(
                                            text = "GAMA",
                                            fontSize = gamaTextSize,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = quicksandFontFamily,
                                            color = colors.textPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .padding(horizontal = 24.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            performHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                            showEasterEgg = true
                                                        }
                                                    )
                                                }
                                                .drawWithContent {
                                                    drawIntoCanvas { canvas ->
                                                        gamaGlowPaint.textSize = gamaTextSize.toPx()
                                                        canvas.nativeCanvas.drawText(
                                                            "GAMA",
                                                            size.width / 2,
                                                            size.height / 2 + (gamaTextSize.toPx() * 0.25f),
                                                            gamaGlowPaint
                                                        )
                                                    }
                                                    drawContent()
                                                }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 1f),
                                                            colors.primaryAccent.copy(alpha = 0f)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                }

                                // Standing by text
                                AnimatedElement(visible = isVisible, staggerIndex = 2,
                                    totalItems = 8) {
                                    Text(
                                        text = strings["main.standing_by"].ifEmpty { "Standing by and awaiting your command" },
                                        fontSize = ts.bodyLarge,  // Reduced from 18/21
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // What's Next
                                AnimatedElement(visible = isVisible, staggerIndex = 3,
                                    totalItems = 8) {
                                    Text(
                                        text = strings["main.whats_next"].ifEmpty { "CHOOSE YOUR PATH." },
                                        fontSize = ts.headlineSmall,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.primaryAccent.copy(alpha = 0.88f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Unified frosted glass box: RendererCard + Vulkan | OpenGL + Resources | GPUWatch
                                AnimatedElement(visible = isVisible, staggerIndex = 4, cardShadow = false,
                                    totalItems = 8, enabled = shizukuRunning && shizukuPermissionGranted) {
                                    val shizukuReady = shizukuRunning && shizukuPermissionGranted

                                    // Transparent card — no blur, no frosted backdrop.
                                    // Removing the blurred background layer eliminates the
                                    // per-frame RenderEffect pass for a direct GPU performance win.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(44.dp))
                                    ) {
                                        // Accent tint + border, fully opaque, on top of transparent bg
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            colors.primaryAccent.copy(alpha = 0.45f),
                                                            colors.border.copy(alpha = 0.12f),
                                                            colors.primaryAccent.copy(alpha = 0.28f)
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(44.dp)
                                                )
                                        )

                                        // Sharp content on top
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // RendererCard at the top of the box
                                            RendererCard(
                                                currentRenderer = currentRenderer,
                                                commandOutput = commandOutput,
                                                isSmallScreen = isSmallScreen,
                                                colors = colors,
                                                cardBackground = cardBackground,
                                                shizukuReady = shizukuRunning && shizukuPermissionGranted,
                                                shizukuRunning = shizukuRunning,
                                                shizukuStatus = shizukuStatus,
                                                onShizukuErrorClick = {
                                                    if (shizukuStatus.contains("❌") || shizukuStatus.contains("⚠️")) {
                                                        performHaptic()
                                                        shizukuHelpType = when {
                                                            shizukuStatus.contains("❌") -> "not_running"
                                                            shizukuStatus.contains("⚠️") -> "permission"
                                                            else -> ""
                                                        }
                                                        openMainPanelExclusive { showShizukuHelp = true }
                                                    }
                                                },
                                                oledMode = effectiveOledMode,
                                                rendererLoading = rendererLoading,
                                                lastSwitchTime = lastSwitchTime
                                            )

                                            // Row 1: Vulkan | OpenGL — big square cards
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                            stiffness = MotionTokens.Springs.smooth.stiffness
                                                        )
                                                    ),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                BigRendererButton(
                                                    text = strings["renderer.vulkan"].ifEmpty { "Vulkan" },
                                                    onClick = {
                                                        performRendererHaptic()
                                                        if (!shizukuRunning || !shizukuPermissionGranted) {
                                                            shizukuHelpType = if (!shizukuRunning) "not_running" else "permission"
                                                            openMainPanelExclusive { showShizukuHelp = true }
                                                            return@BigRendererButton
                                                        }
                                                        pendingRendererName = "Vulkan"
                                                        pendingRendererSwitch = {
                                                            verboseOutput = ""
                                                            if (verboseMode) showVerbosePanel = true
                                                            ShizukuHelper.runVulkan(
                                                                context, scope, aggressiveMode,
                                                                killLauncher,
                                                                killKeyboard,
                                                                excludedAppsList.toSet(), emptySet(),
                                                                { commandOutput = it },
                                                                if (verboseMode) { output -> verboseOutput += output } else null
                                                            )
                                                        }
                                                        successDialogMessage = strings["main.vulkan_applied"].ifEmpty { "Vulkan has been applied!" }
                                                        openMainPanelExclusive { showWarningDialog = true }
                                                    },
                                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                                    isSelected = currentRenderer == "Vulkan",
                                                    forceHighlight = true,
                                                    enabled = shizukuReady,
                                                    colors = colors,
                                                    oledMode = effectiveOledMode,
                                                    iconType = "vulkan"
                                                )
                                                BigRendererButton(
                                                    text = strings["renderer.opengl"].ifEmpty { "OpenGL" },
                                                    onClick = {
                                                        performRendererHaptic()
                                                        if (!shizukuRunning || !shizukuPermissionGranted) {
                                                            shizukuHelpType = if (!shizukuRunning) "not_running" else "permission"
                                                            openMainPanelExclusive { showShizukuHelp = true }
                                                            return@BigRendererButton
                                                        }
                                                        pendingRendererName = "OpenGL"
                                                        pendingRendererSwitch = {
                                                            verboseOutput = ""
                                                            if (verboseMode) showVerbosePanel = true
                                                            ShizukuHelper.runOpenGL(
                                                                context, scope, aggressiveMode,
                                                                killLauncher,
                                                                killKeyboard,
                                                                excludedAppsList.toSet(), emptySet(),
                                                                { commandOutput = it },
                                                                if (verboseMode) { output -> verboseOutput += output } else null
                                                            )
                                                        }
                                                        successDialogMessage = strings["main.opengl_applied"].ifEmpty { "OpenGL has been applied!" }
                                                        openMainPanelExclusive { showWarningDialog = true }
                                                    },
                                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                                    isSelected = false,
                                                    enabled = shizukuReady,
                                                    colors = colors,
                                                    oledMode = effectiveOledMode,
                                                    iconType = "opengl"
                                                )
                                            }

                                            // Row 2: Resources | GPUWatch
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateContentSize(
                                                        animationSpec = spring(
                                                            dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                            stiffness = MotionTokens.Springs.smooth.stiffness
                                                        )
                                                    ),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                IllustratedButton(
                                                    text = strings["integrations.widget_action"].ifEmpty { "Library" },
                                                    onClick = {
                                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                        openMainPanelExclusive { showResourcesPanel = true }
                                                    },
                                                    modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                                    accent = false, enabled = true,
                                                    colors = colors, oledMode = effectiveOledMode, iconType = "resources"
                                                )
                                                AnimatedVisibility(
                                                    modifier = Modifier.weight(1f),
                                                    visible = showGpuWatchButton,
                                                    enter = fadeIn(animationSpec = tween(220, easing = MotionTokens.Easing.enter)) +
                                                            expandHorizontally(
                                                                animationSpec = spring(
                                                                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                                                                    stiffness = MotionTokens.Springs.smooth.stiffness
                                                                ),
                                                                expandFrom = Alignment.Start
                                                            ) +
                                                            scaleIn(
                                                                initialScale = 0.88f,
                                                                animationSpec = spring(
                                                                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                                                                    stiffness = MotionTokens.Springs.gentle.stiffness
                                                                )
                                                            ),
                                                    exit = fadeOut(animationSpec = tween(160, easing = MotionTokens.Easing.exit)) +
                                                            shrinkHorizontally(
                                                                animationSpec = tween(180, easing = MotionTokens.Easing.exit),
                                                                shrinkTowards = Alignment.Start
                                                            ) +
                                                            scaleOut(
                                                                targetScale = 0.88f,
                                                                animationSpec = tween(160, easing = MotionTokens.Easing.exit)
                                                            )
                                                ) {
                                                    IllustratedButton(
                                                        text = strings["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
                                                        onClick = {
                                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                            openMainPanelExclusive { showGPUWatchConfirm = true }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        accent = false, enabled = true,
                                                        colors = colors, oledMode = effectiveOledMode, iconType = "gpuwatch"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // ── Background blur when panels are open ─────────────────────────────
            //
            // EFFICIENT FAKE BLUR — two permanently-composed layers, zero stutter:
            //
            // Both the sharp and blurred copies of mainContent() are ALWAYS in the
            // composition tree — their modifier chains never change structurally, so
            // Compose never re-measures or re-lays-out the subtree when blur toggles.
            //
            // The blurred layer uses Modifier.blur() which, on API 31+, compiles to a
            // RenderEffect Gaussian kernel. Crucially, this kernel is built ONCE on the
            // first frame the layer is composed — it is never rebuilt mid-transition
            // because the blur radius is a compile-time constant (40.dp, never animated).
            //
            // The ONLY per-frame work during open/close is a single animateFloatAsState
            // driving two graphicsLayer alpha scalars — a trivially cheap GPU op.
            // When both panels are closed the blurred layer sits at alpha=0: the GPU
            // skips drawing it entirely (hardware layer optimization), so there is zero
            // rendering cost at rest.
            //
            // Particles are rendered AFTER this block so they always sit above the blur
            // overlay and scrim in z-order — their brightness never changes on toggle.
            //
            // Scrim is theme-aware: dark/OLED = darkening, light mode = brightening.

            // Theme-aware scrim: darken in dark/OLED, brighten in light mode.
            val scrimColor = if (effectiveOledMode)
                Color.Black.copy(alpha = 0.32f)
            else
                Color.White.copy(alpha = 0.45f)

            // Main-screen background transition.
            // When blur is enabled we crossfade into the blurred copy.
            // When blur is disabled we keep the sharp copy and only fade in the fallback scrim.
            val blurAlpha by animateFloatAsState(
                targetValue = if (blurShouldApply) 1f else 0f,
                animationSpec = if (animationLevel != 2)
                    tween(
                        durationMillis = if (blurShouldApply) 340 else 440,
                        easing = if (blurShouldApply) MotionTokens.Easing.emphasizedDecelerate else MotionTokens.Easing.emphasized
                    )
                else snap(),
                label = "bg_blur_alpha"
            )
            val fallbackScrimAlpha by animateFloatAsState(
                targetValue = if ((anyFullPanelOpen && !blurEnabled) || showAggressiveWarning) 1f else 0f,
                animationSpec = if (animationLevel != 2)
                    tween(
                        durationMillis = if ((anyFullPanelOpen && !blurEnabled) || showAggressiveWarning) 300 else 220,
                        easing = if ((anyFullPanelOpen && !blurEnabled) || showAggressiveWarning) MotionTokens.Easing.emphasizedDecelerate else MotionTokens.Easing.emphasized
                    )
                else snap(),
                label = "fallback_scrim_alpha"
            )

            // ── Particles — rendered BEFORE mainContent so they are BEHIND the UI ──
            // Z-order in a Box = last child is on top. Placing particles here means:
            //   • They draw behind the card/buttons — visually correct ✓
            //   • They cannot intercept touch events that belong to the UI ✓
            //   • When a panel opens the blur+scrim (below) covers particles too,
            //     which is intentional: the whole background dims together ✓
            if (matrixMode) {
                // Do NOT key this by accent color. The Matrix columns must stay alive
                // while the color eases to the new ACCENT COLOR. Keying this block by
                // color disposes/recreates the overlay, which makes the rain respawn.
                MatrixRainOverlay(
                    enabled          = particlesEnabled,
                    headColor        = targetMatrixHeadColor,
                    rainColor        = targetMatrixRainColor,
                    trailColor       = targetMatrixTrailColor,
                    backgroundColor  = Color.Black,
                    backgroundAlpha  = matrixBgAlpha,
                    speedLevel       = matrixSpeed,
                    densityLevel     = matrixDensity,
                    fontSizeLevel    = matrixFontSize,
                    fadeLength       = matrixFadeLength
                )
            }
            // Always composed so the sun/moon can fade out smoothly via its internal
            // celestialAlpha animation (600 ms tween) even when switching modes.
            // Use the raw ACCENT COLOR directly, not the animated theme accent, so stars
            // update immediately when Dynamic Color is OFF.
            ParticlesOverlay(
                enabled          = particlesEnabled && !matrixMode,
                color            = effectsAccent,
                particleSpeed    = particleSpeed,
                parallaxEnabled  = particleParallaxEnabled,
                particleCount    = particleCount,
                particleCountCustom = particleCountCustom.toIntOrNull() ?: 150,
                parallaxSensitivity = animatedParallaxSensitivity,
                starMode         = particleStarMode,
                timeModeEnabled  = particleTimeMode,
                timeOffsetHours  = timeOffsetHours,
                anyPanelOpen     = anyPanelOpen,
                isLandscape      = isLandscape,
                nativeRefreshRate  = particleNativeRefreshRate,
                quarterRefreshRate = particleQuarterRefreshRate
            )

            // ── Main content layers ───────────────────────────────────────────
            // Sharp layer fades OUT, blurred layer fades IN when a panel opens.
            // Both layers sit ABOVE particles so buttons are always hittable.
            // Modifier.blur() only affects this layer's own pixels — it never
            // reaches back to blur the particles drawn underneath.

            // Sharp (unblurred) — always visible when no panel is open.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 1f - blurAlpha)
            ) { mainContent() }

            // Blurred — fades in when a panel opens. Fixed radius = kernel built once.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = blurAlpha)
                        .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                ) { mainContent() }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = blurAlpha)
                ) { mainContent() }
            }

            // Scrim — dims everything (including particles) behind open panels.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = fallbackScrimAlpha)
                    .background(scrimColor)
            )


            ShizukuHelpDialog(
                visible = showShizukuHelp,
                helpType = shizukuHelpType,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showShizukuHelp = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // Dialogs
            WarningDialog(
                visible = showWarningDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWarningDialog = false
                    pendingRendererSwitch = null
                    pendingRendererName = ""
                },
                onContinue = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWarningDialog = false

                    // 1. Optimistically update UI immediately.
                    // Use commit() (synchronous) not apply() (async) — the renderer
                    // switch crashes SystemUI which can also kill this process before
                    // apply()'s background thread flushes. commit() guarantees the
                    // new value is on disk before we invoke the shell command.
                    if (pendingRendererName.isNotEmpty()) {
                        currentRenderer = pendingRendererName
                        prefs.edit().putString("last_renderer", pendingRendererName).commit()
                    }

                    // 2. Reset commandOutput so LaunchedEffect below can detect its arrival,
                    //    then open the dialog in the in-progress (spinner) state.
                    commandOutput = ""
                    rendererSwitching = true
                    showSuccessDialog = true

                    // 3. Execute the actual switch — commandOutput will be set by ShizukuHelper
                    //    when the shell command finishes, which flips rendererSwitching off.
                    pendingRendererSwitch?.invoke()
                    pendingRendererSwitch = null

                    // Save the time this switch was confirmed.
                    // last_switch_time  = wall-clock ms  (used for "X ago" display)
                    // last_switch_uptime = elapsedRealtime ms (used for reboot detection —
                    //   immune to the user changing the system clock)
                    // commit() again — must survive potential process death from the crash.
                    val switchNow = System.currentTimeMillis()
                    prefs.edit()
                        .putLong("last_switch_time", switchNow)
                        .putLong("last_switch_uptime", android.os.SystemClock.elapsedRealtime())
                        .commit()
                    lastSwitchTime = switchNow

                    // 4. Verify in background and correct if needed
                    scope.launch {
                        delay(2500) // Wait for command to finish
                        if (ShizukuHelper.checkBinder()) {
                            val newRenderer = ShizukuHelper.getCurrentRenderer()
                            if (newRenderer == "Vulkan" || newRenderer == "OpenGL") {
                                currentRenderer = newRenderer
                                prefs.edit().putString("last_renderer", newRenderer).apply()
                            }
                            // Anything else (Unknown, Default, error) — keep the optimistic
                            // value already set when the user confirmed the switch.
                        }
                    }

                    pendingRendererName = ""
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // NEW: Success Dialog
            SuccessDialog(
                visible = showSuccessDialog,
                message = successDialogMessage,
                userName = userName,
                isSwitching = rendererSwitching,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSuccessDialog = false
                    rendererSwitching = false
                    // Ask about clearing recents now that the switch is done and GAMA is alive
                    showClearRecentsDialog = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            ClearRecentsDialog(
                visible = showClearRecentsDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showClearRecentsDialog = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showClearRecentsDialog = false
                    scope.launch {
                        val result = ShizukuHelper.clearBackgroundApps()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (result.startsWith("Error", ignoreCase = true)) "Could not clear background apps" else "Background apps cleared",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // DeveloperMenuDialog removed — it was never reachable (showDeveloperMenu was never
            // set to true). All developer tooling lives in DeveloperPanel (showDeveloper).

            GitHubDialog(
                visible = showGitHubDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGitHubDialog = false
                },
                onVisitGitHub = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/popovicialinc/gama"))
                    context.startActivity(intent)
                    showGitHubDialog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            ResourcesPanel(
                visible = showResourcesPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showResourcesPanel = false
                },
                onLinkSelected = { url, label, description ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    pendingExternalLink = url
                    pendingExternalLinkLabel = label
                    pendingExternalLinkDescription = description
                    showExternalLinkConfirm = true
                },
                onInfoRequested = { title, body ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    integrationInfoTitle = title
                    integrationInfoBody  = body
                    showIntegrationInfoDialog = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                isBlurred = showExternalLinkConfirm || showIntegrationInfoDialog,
                oledMode = effectiveOledMode
            )

            ExternalLinkConfirmDialog(
                visible = showExternalLinkConfirm,
                label = pendingExternalLinkLabel,
                description = pendingExternalLinkDescription,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showExternalLinkConfirm = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingExternalLink))
                    context.startActivity(intent)
                    showExternalLinkConfirm = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )



            SettingsPanel(
                visible = showSettings && !showSettingsSearch && !showHapticsPanel && !showAppearance && !showColorCustomization && !showGradient && !showSystem && !showEffects && !showParticles && !showNotifications && !showBackup && !showCrashLog && !showRendererPanel && !showLanguage && !showHapticsPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettings = false
                },
                onSearchClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettingsSearch = true
                },
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAppearance = true
                },
                onRendererClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showRendererPanel = true
                },
                onSystemClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSystem = true
                },
                onHapticsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showHapticsPanel = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic() },
                oledMode = effectiveOledMode
            )

            SettingsSearchPanel(
                visible = showSettingsSearch,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettingsSearch = false
                },
                onAppearanceClick = { showAppearance = true },
                onColorCustomizationClick = { showColorCustomization = true },
                onGradientClick = { showColorCustomization = true; showGradient = true },
                onEffectsClick = { showEffects = true },
                onParticlesClick = { showParticles = true },
                onRendererClick = { showRendererPanel = true },
                onSystemClick = { showSystem = true },
                onNotificationsClick = { showSystem = true; showNotifications = true },
                onBackupClick = { showSystem = true; showBackup = true },
                onCrashLogClick = { showSystem = true; showCrashLog = true },
                onLanguageClick = { showSystem = true; showLanguage = true },
                onHapticsClick = { showHapticsPanel = true },
                // Visuals / Appearance
                themePreference = themePreference,
                onThemeChange = { themePreference = it; savePreferences() },
                animationLevel = animationLevel,
                onAnimationLevelChange = { animationLevel = it; savePreferences() },
                uiScale = uiScale,
                onUiScaleChange = { uiScale = it; savePreferences() },
                staggerEnabled = staggerEnabled,
                onStaggerEnabledChange = { staggerEnabled = it; savePreferences() },
                backButtonAvoidanceEnabled = backButtonAvoidanceEnabled,
                onBackButtonAvoidanceEnabledChange = { backButtonAvoidanceEnabled = it; savePreferences() },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { shadowsEnabled = it; savePreferences() },
                // Colors
                oledMode = effectiveOledMode,
                darkModeActive = effectiveOledMode,
                onOledModeChange = { oledMode = it; savePreferences() },
                useDynamicColor = useDynamicColor,
                onDynamicColorChange = { useDynamicColor = it; savePreferences() },
                advancedColorPicker = advancedColorPicker,
                onAdvancedColorPickerChange = { advancedColorPicker = it; savePreferences() },
                gradientEnabled = gradientEnabled,
                onGradientChange = { gradientEnabled = it; savePreferences() },
                customAccentColor = customAccentColor,
                onAccentColorChange = { color -> customAccentColor = color; savePreferencesDebounced() },
                customGradientStart = customGradientStart,
                onGradientStartChange = { color -> customGradientStart = color; savePreferencesDebounced() },
                customGradientEnd = customGradientEnd,
                onGradientEndChange = { color -> customGradientEnd = color; savePreferencesDebounced() },
                // Effects
                blurEnabled = blurEnabled,
                onBlurChange = { blurEnabled = it; savePreferences() },
                // Particles
                particlesEnabled = particlesEnabled,
                onParticlesChange = { particlesEnabled = it; savePreferences() },
                matrixMode = matrixMode,
                onMatrixModeChange = { matrixMode = it; savePreferences() },
                particleStarMode = particleStarMode,
                onParticleStarModeChange = { particleStarMode = it; savePreferences() },
                particleTimeMode = particleTimeMode,
                onParticleTimeModeChange = { particleTimeMode = it; savePreferences() },
                particleParallaxEnabled = particleParallaxEnabled,
                onParticleParallaxEnabledChange = { particleParallaxEnabled = it; savePreferences() },
                particleParallaxSensitivity = particleParallaxSensitivity,
                onParticleParallaxSensitivityChange = { particleParallaxSensitivity = it; savePreferences() },
                particleCount = particleCount,
                onParticleCountChange = { particleCount = it; savePreferences() },
                particleSpeed = particleSpeed,
                onParticleSpeedChange = { particleSpeed = it; savePreferences() },
                nativeRefreshRate = particleNativeRefreshRate,
                onNativeRefreshRateChange = { particleNativeRefreshRate = it; savePreferences() },
                quarterRefreshRate = particleQuarterRefreshRate,
                onQuarterRefreshRateChange = { particleQuarterRefreshRate = it; savePreferences() },
                matrixSpeed = matrixSpeed,
                onMatrixSpeedChange = { matrixSpeed = it; savePreferences() },
                matrixDensity = matrixDensity,
                onMatrixDensityChange = { matrixDensity = it; savePreferences() },
                matrixFontSize = matrixFontSize,
                onMatrixFontSizeChange = { matrixFontSize = it; savePreferences() },
                matrixFadeLength = matrixFadeLength,
                onMatrixFadeLengthChange = { matrixFadeLength = it; savePreferences() },
                // Renderer
                aggressiveMode = aggressiveMode,
                onAggressiveModeChange = { aggressiveMode = it; savePreferences() },
                killLauncher = killLauncher,
                onKillLauncherChange = { killLauncher = it; savePreferences() },
                killKeyboard = killKeyboard,
                onKillKeyboardChange = { killKeyboard = it; savePreferences() },
                dozeMode = dozeMode,
                onDozeModeChange = { dozeMode = it; savePreferences() },
                showGpuWatchButton = showGpuWatchButton,
                onShowGpuWatchButtonChange = { showGpuWatchButton = it; savePreferences() },
                // System / App
                verboseMode = verboseMode,
                onVerboseModeChange = { verboseMode = it; savePreferences() },
                dismissOnClickOutside = dismissOnClickOutside,
                onDismissOnClickOutsideChange = { dismissOnClickOutside = it; savePreferences() },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { notificationsEnabled = it; savePreferences() },
                notifIntervalIndex = notifIntervalIndex,
                onNotifIntervalChange = { notifIntervalIndex = it; savePreferences() },
                // Common
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )



            HapticsPanel(
                visible = showHapticsPanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showHapticsPanel = false
                },
                hapticsEnabled = hapticsEnabled,
                onHapticsEnabledChange = { hapticsEnabled = it; savePreferences() },
                regularEnabled = hapticsRegularEnabled,
                onRegularEnabledChange = { hapticsRegularEnabled = it; savePreferences() },
                holdEnabled = hapticsHoldEnabled,
                onHoldEnabledChange = { hapticsHoldEnabled = it; savePreferences() },
                rendererEnabled = hapticsRendererEnabled,
                onRendererEnabledChange = { hapticsRendererEnabled = it; savePreferences() },
                languageEnabled = hapticsLanguageEnabled,
                onLanguageEnabledChange = { hapticsLanguageEnabled = it; savePreferences() },
                bounceEnabled = hapticsBounceEnabled,
                onBounceEnabledChange = { hapticsBounceEnabled = it; savePreferences() },
                regularStrength = hapticsRegularStrength,
                onRegularStrengthChange = { hapticsRegularStrength = it; savePreferencesDebounced() },
                holdStrength = hapticsHoldStrength,
                onHoldStrengthChange = { hapticsHoldStrength = it; savePreferencesDebounced() },
                rendererStrength = hapticsRendererStrength,
                onRendererStrengthChange = { hapticsRendererStrength = it; savePreferencesDebounced() },
                languageStrength = hapticsLanguageStrength,
                onLanguageStrengthChange = { hapticsLanguageStrength = it; savePreferencesDebounced() },
                bounceStrength = hapticsBounceStrength,
                onBounceStrengthChange = { hapticsBounceStrength = it; savePreferencesDebounced() },
                bounceReturnStrength = hapticsBounceReturnStrength,
                onBounceReturnStrengthChange = { hapticsBounceReturnStrength = it; savePreferencesDebounced() },
                onResetHaptics = {
                    GamaHaptics.resetToDefaults(context)
                    hapticsEnabled = GamaHaptics.DEFAULT_ENABLED
                    hapticsRegularEnabled = GamaHaptics.DEFAULT_REGULAR_ENABLED
                    hapticsHoldEnabled = GamaHaptics.DEFAULT_HOLD_ENABLED
                    hapticsRendererEnabled = GamaHaptics.DEFAULT_RENDERER_ENABLED
                    hapticsLanguageEnabled = GamaHaptics.DEFAULT_LANGUAGE_ENABLED
                    hapticsBounceEnabled = GamaHaptics.DEFAULT_BOUNCE_ENABLED
                    hapticsRegularStrength = GamaHaptics.DEFAULT_REGULAR_STRENGTH
                    hapticsHoldStrength = GamaHaptics.DEFAULT_HOLD_STRENGTH
                    hapticsRendererStrength = GamaHaptics.DEFAULT_RENDERER_STRENGTH
                    hapticsLanguageStrength = GamaHaptics.DEFAULT_LANGUAGE_STRENGTH
                    hapticsBounceStrength = GamaHaptics.DEFAULT_BOUNCE_STRENGTH
                    hapticsBounceReturnStrength = GamaHaptics.DEFAULT_BOUNCE_RETURN_STRENGTH
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
            )

            // Blur wrapper for RendererPanel when Aggressive Warning is shown.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (showAggressiveWarning && animationLevel != 2 &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Modifier.blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
            ) {
                RendererPanel(
                    visible = showRendererPanel,
                    onDismiss = {
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        showRendererPanel = false
                    },
                    aggressiveMode = aggressiveMode,
                    onAggressiveModeChange = { enabled ->
                        if (enabled && !aggressiveModeConfirmed && !dontShowAggressiveWarning) {
                            aggressiveMode = true
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showAggressiveWarning = true
                        } else {
                            aggressiveMode = enabled
                            savePreferences()
                            if (!enabled) aggressiveModeConfirmed = false
                        }
                    },
                    killLauncher = killLauncher,
                    onKillLauncherChange = { enabled ->
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        killLauncher = enabled
                        savePreferences()
                    },
                    killKeyboard = killKeyboard,
                    onKillKeyboardChange = { enabled ->
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        killKeyboard = enabled
                        savePreferences()
                    },
                    dozeMode = dozeMode,
                    onDozeModeChange = { enabled ->
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        dozeMode = enabled
                        scope.launch {
                            try {
                                if (enabled) {
                                    // Enter doze: unplug battery reporting first, THEN force-idle.
                                    // The unplug step is required on Samsung / Android 15+ —
                                    // without it, force-idle silently does nothing.
                                    // Mirrors the exact sequence used by DozeTileService.
                                    ShizukuHelper.runCommand("dumpsys battery unplug")
                                    ShizukuHelper.runCommand("dumpsys deviceidle force-idle")
                                } else {
                                    // Exit doze: unforce first, then reset battery reporting.
                                    ShizukuHelper.runCommand("dumpsys deviceidle unforce")
                                    ShizukuHelper.runCommand("dumpsys battery reset")
                                }
                            } catch (_: Exception) {}
                        }
                        savePreferences()
                    },
                    showGpuWatchButton = showGpuWatchButton,
                    onShowGpuWatchButtonChange = { showGpuWatchButton = it; savePreferences() },
                    onShowAggressiveWarning = {
                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                        showAggressiveWarning = true
                    },
                    isSmallScreen = isSmallScreen,
                    isLandscape = isLandscape,
                    isTablet = isTablet,
                    colors = colors,
                    cardBackground = cardBackground,
                    oledMode = effectiveOledMode,
                    performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
                )
            } // End of blur wrapper
            // ── Panels live inside the outer Box so they share the blur/clip layer ──
            //    (previously they were placed after the Box's closing brace, which meant
            //    Modifier.blur never covered their backgrounds and z-order was fragile)

            SystemPanel(
                visible = showSystem && !showNotifications && !showBackup && !showCrashLog && !showLanguage,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSystem = false
                },
                verboseMode = verboseMode,
                onVerboseModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    verboseMode = enabled
                    savePreferences()
                },
                dismissOnClickOutside = dismissOnClickOutside,
                onDismissOnClickOutsideChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    dismissOnClickOutside = enabled
                    savePreferences()
                },
                onNotificationsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showNotifications = true
                },
                onBackupClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBackup = true
                },
                onCrashLogClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showCrashLog = true
                },
                onLanguageClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showLanguage = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
            )

            // Notifications panel (sub-panel of Functionality)
            NotificationsPanel(
                visible = showNotifications,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showNotifications = false
                },
                notificationsEnabled = notificationsEnabled,
                onNotificationsEnabledChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    notificationsEnabled = enabled
                    savePreferences()
                },
                notifIntervalIndex = notifIntervalIndex,
                onNotifIntervalChange = { idx ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    notifIntervalIndex = idx
                    savePreferences()
                },
                hasPermission = hasNotifPermission.value,
                onRequestPermission = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    onRequestNotificationPermission()
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (!hasNotifPermission.value) {
                        onRequestNotificationPermission()
                        android.widget.Toast.makeText(
                            context,
                            "Grant notification permission first",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val sent = sendOpenGLReminderNotification(context, userName)
                        android.widget.Toast.makeText(
                            context,
                            if (sent) "Test notification sent! ✅" else "Failed to send notification",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                currentRenderer = currentRenderer,
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // ── Backup & Restore panel ────────────────────────────────────────
            BackupPanel(
                visible = showBackup,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBackup = false
                },
                onExport = {
                    scope.launch {
                        val json = try { BackupHelper.export(prefs) } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        onExportBackup(json, BackupHelper.buildFileName())
                        android.widget.Toast.makeText(context, "Backup saved ✅", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onImport = {
                    onImportBackup { json ->
                        scope.launch {
                            try {
                                val msg = BackupHelper.import(prefs, json)
                                // Reload all prefs-backed state from updated SharedPreferences
                                animationLevel       = prefs.getInt("animation_level", 0)
                                gradientEnabled      = prefs.getBoolean("gradient_enabled", true)
                                blurEnabled          = prefs.getBoolean("blur_enabled", true)
                                particlesEnabled     = prefs.getBoolean("particles_enabled", true)
                                particleSpeed        = prefs.getInt("particle_speed", 1)
                                particleParallaxEnabled = prefs.getBoolean("particle_parallax_enabled", true)
                                particleParallaxSensitivity = prefs.getInt("particle_parallax_sensitivity", 0)
                                particleStarMode     = prefs.getBoolean("particle_star_mode", false)
                                particleTimeMode     = prefs.getBoolean("particle_time_mode", true)
                                timeOffsetHours      = prefs.getFloat("time_offset_hours", 0f)
                                particleCount        = prefs.getInt("particle_count", 0)
                                particleCountCustom  = prefs.getInt("particle_count_custom", 150).toString()
                                themePreference      = prefs.getInt("theme_preference", 0)
                                uiScale              = prefs.getInt("ui_scale", 1)
                                verboseMode          = prefs.getBoolean("verbose_mode", false)
                                aggressiveMode       = prefs.getBoolean("aggressive_mode", false)
                                killLauncher         = prefs.getBoolean("kill_launcher", false)
                                staggerEnabled       = prefs.getBoolean("stagger_enabled", true)
                                backButtonAvoidanceEnabled = prefs.getBoolean("back_button_avoidance_enabled", true)
                                particleNativeRefreshRate  = prefs.getBoolean("particle_native_refresh_rate", false)
                                particleQuarterRefreshRate = prefs.getBoolean("particle_quarter_refresh_rate", false)
                                matrixMode       = prefs.getBoolean("matrix_mode", true)
                                matrixSpeed      = prefs.getInt("matrix_speed", 1)
                                matrixDensity    = prefs.getInt("matrix_density", 1)
                                matrixFontSize   = prefs.getInt("matrix_font_size", 1)
                                matrixFadeLength = prefs.getInt("matrix_fade_length", 1)
                                matrixBgAlpha    = prefs.getFloat("matrix_bg_alpha", 0f)
                                oledMode             = prefs.getBoolean("oled_mode", false)
                                oledAccentColor      = Color(prefs.getInt("oled_accent_color", 0xFF4895EF.toInt()))
                                useDynamicColorOLED  = prefs.getBoolean("use_dynamic_color_oled", false)
                                useDynamicColor      = prefs.getBoolean("use_dynamic_color", true)
                                customAccentColor    = Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt()))
                                customGradientStart  = Color(prefs.getInt("custom_gradient_start", 0xFF0A2540.toInt()))
                                customGradientEnd    = Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))
                                dismissOnClickOutside = prefs.getBoolean("dismiss_on_click_outside", true)
                                notificationsEnabled = prefs.getBoolean("notif_enabled", false)
                                notifIntervalIndex   = prefs.getInt("notif_interval_idx", 2)
                                userName             = prefs.getString("user_name", "") ?: ""
                                excludedAppsList.clear()
                                excludedAppsList.addAll(prefs.getStringSet("excluded_apps", emptySet()) ?: emptySet())
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // ── Crash Log panel ───────────────────────────────────────────────
            CrashLogPanel(
                visible = showCrashLog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showCrashLog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                onExportCrashLog = onExportCrashLog
            )

            // ── Language panel ─────────────────────────────────────────────
            LanguagePanel(
                visible = showLanguage,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showLanguage = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic() },
                oledMode = effectiveOledMode
            )

            // ── Info dialog for QS Tiles / Widget (shown from Resources panel) ──
            IntegrationInfoDialog(
                visible = showIntegrationInfoDialog,
                title = integrationInfoTitle,
                body = integrationInfoBody,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrationInfoDialog = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            DeveloperPanel(
                visible = showDeveloper,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showDeveloper = false
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)

                    // Check if permission is granted using the lifecycle-aware state
                    if (!hasNotifPermission.value) {
                        // Request permission
                        onRequestNotificationPermission()
                        android.widget.Toast.makeText(
                            context,
                            "Please grant notification permission to test notifications",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Send test notification
                        val success = ShizukuHelper.sendTestNotification(context, userName)
                        if (success) {
                            android.widget.Toast.makeText(context, "Test notification sent!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to send notification", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onTestBootNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    sendBootNotification(
                        context = context,
                        userName = userName,
                        onRequestPermission = {
                            onRequestNotificationPermission()
                            android.widget.Toast.makeText(
                                context,
                                "Please grant notification permission to test notifications",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                    android.widget.Toast.makeText(context, "Boot notification sent!", android.widget.Toast.LENGTH_SHORT).show()
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                timeOffsetHours = timeOffsetHours,
                onTimeOffsetChange = { value ->
                    timeOffsetHours = value
                    savePreferencesDebounced()
                }
            )


            VisualEffectsPanel(
                visible = showAppearance && !showEffects && !showColorCustomization && !showGradient && !showParticles,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAppearance = false
                },
                themePreference = themePreference,
                onThemeChange = { newTheme ->
                    themePreference = newTheme
                    savePreferences()
                },
                animationLevel = animationLevel,
                onAnimationLevelChange = { newLevel ->
                    animationLevel = newLevel
                    savePreferences()
                },
                uiScale = uiScale,
                onUiScaleChange = { newSize ->
                    uiScale = newSize
                    savePreferences()
                },
                userName = userName,
                onUserNameChange = { newName ->
                    userName = newName
                    prefs.edit().putString("user_name", newName).apply()
                },
                oledMode = effectiveOledMode,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                staggerEnabled = staggerEnabled,
                onStaggerEnabledChange = { staggerEnabled = it; savePreferences() },
                backButtonAvoidanceEnabled = backButtonAvoidanceEnabled,
                onBackButtonAvoidanceEnabledChange = { backButtonAvoidanceEnabled = it; savePreferences() },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { shadowsEnabled = it; savePreferences() },
                onEffectsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = true
                },
                onColorsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showColorCustomization = true
                },
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    oledMode = enabled
                    if (enabled) {
                        themePreference = 1 // Force Dark
                        prefs.edit().putInt("theme_preference", 1).apply()
                    }
                    savePreferences()
                },
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )

            EffectsPanel(
                visible = showEffects && !showParticles && !showParticlesAppearance && !showParticlesMotion && !showParticlesPerformance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = false
                },
                blurEnabled = blurEnabled,
                onBlurChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    blurEnabled = value
                    savePreferences()
                },
                shadowsEnabled = shadowsEnabled,
                onShadowsEnabledChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    shadowsEnabled = value
                    savePreferences()
                },
                onParticlesClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticles = true
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles hub — visible when no sub-panel is open ─────────────
            ParticlesPanel(
                visible = showParticles && !showParticlesAppearance && !showParticlesMotion
                        && !showParticlesPerformance && !showMatrixSettings && !showMatrixAppearance
                        && !showMatrixMotion && !showParticlesSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticles = false
                },
                particlesEnabled = particlesEnabled,
                onParticlesChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    particlesEnabled = value
                    savePreferences()
                },
                matrixMode = matrixMode,
                onMatrixModeChange = { value ->
                    matrixMode = value
                    savePreferences()
                },
                onParticlesSettingsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = true
                },
                onMatrixSettingsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixSettings = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape   = isLandscape,
                isTablet      = isTablet,
                colors        = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode      = effectiveOledMode
            )

            // ── Particles Settings sub-panel ──────────────────────────────────
            ParticlesSettingsPanel(
                visible = showParticlesSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                },
                particlesEnabled = particlesEnabled,
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesAppearance = true
                },
                onMotionClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesMotion = true
                },
                onPerformanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesSettings = false
                    showParticlesPerformance = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape   = isLandscape,
                colors        = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode      = effectiveOledMode
            )

            // ── Particles › Appearance sub-panel ─────────────────────────────
            ParticlesAppearancePanel(
                visible = showParticlesAppearance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesAppearance = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleStarMode = particleStarMode,
                onParticleStarModeChange = { value ->
                    particleStarMode = value
                    savePreferences()
                },
                particleTimeMode = particleTimeMode,
                onParticleTimeModeChange = { value ->
                    particleTimeMode = value
                    if (value && particleStarMode) particleStarMode = false
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles › Motion sub-panel ──────────────────────────────────
            ParticlesMotionPanel(
                visible = showParticlesMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesMotion = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleSpeed = particleSpeed,
                onParticleSpeedChange = { value ->
                    particleSpeed = value
                    savePreferencesDebounced()
                },
                particleParallaxEnabled = particleParallaxEnabled,
                onParticleParallaxChange = { value ->
                    particleParallaxEnabled = value
                    savePreferences()
                },
                particleParallaxSensitivity = particleParallaxSensitivity,
                onParticleParallaxSensitivityChange = { value ->
                    particleParallaxSensitivity = value
                    savePreferencesDebounced()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Particles › Performance sub-panel ────────────────────────────
            ParticlesPerformancePanel(
                visible = showParticlesPerformance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticlesPerformance = false
                    showParticlesSettings = true
                },
                particlesEnabled = particlesEnabled,
                particleCount = particleCount,
                onParticleCountChange = { value ->
                    particleCount = value
                    savePreferencesDebounced()
                },
                nativeRefreshRate = particleNativeRefreshRate,
                onNativeRefreshRateChange = { value ->
                    particleNativeRefreshRate = value
                    savePreferences()
                },
                quarterRefreshRate = particleQuarterRefreshRate,
                onQuarterRefreshRateChange = { value ->
                    particleQuarterRefreshRate = value
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = effectiveOledMode
            )

            // ── Matrix rain settings panel ────────────────────────────────────
            MatrixSettingsPanel(
                visible           = showMatrixSettings && !showMatrixAppearance && !showMatrixMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixSettings = false
                },
                particlesEnabled  = particlesEnabled,
                onAppearanceClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixAppearance = true
                },
                onMotionClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixMotion = true
                },
                isSmallScreen     = isSmallScreen,
                isLandscape       = isLandscape,
                colors            = colors,
                cardBackground    = cardBackground,
                performHaptic     = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode          = effectiveOledMode
            )

            MatrixAppearancePanel(
                visible                    = showMatrixAppearance,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixAppearance = false
                },
                particlesEnabled              = particlesEnabled,
                matrixFontSize                = matrixFontSize,
                onMatrixFontSizeChange        = { matrixFontSize = it; savePreferences() },
                isSmallScreen                 = isSmallScreen,
                isLandscape                   = isLandscape,
                colors                        = colors,
                cardBackground                = cardBackground,
                performHaptic                 = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode                      = effectiveOledMode
            )

            MatrixMotionPanel(
                visible                  = showMatrixMotion,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showMatrixMotion = false
                },
                particlesEnabled         = particlesEnabled,
                matrixSpeed              = matrixSpeed,
                onMatrixSpeedChange      = { matrixSpeed = it;      savePreferences() },
                matrixDensity            = matrixDensity,
                onMatrixDensityChange    = { matrixDensity = it;    savePreferences() },
                matrixFadeLength         = matrixFadeLength,
                onMatrixFadeLengthChange = { matrixFadeLength = it; savePreferences() },
                isSmallScreen            = isSmallScreen,
                isLandscape              = isLandscape,
                colors                   = colors,
                cardBackground           = cardBackground,
                performHaptic            = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode                 = effectiveOledMode
            )


            ColorCustomizationPanel(
                visible = showColorCustomization && !showGradient,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showColorCustomization = false
                },
                oledMode = effectiveOledMode,
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    oledMode = enabled
                    savePreferences()
                },
                useDynamicColor = useDynamicColor,
                onDynamicColorChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    useDynamicColor = value
                    savePreferences()
                },
                advancedColorPicker = advancedColorPicker,
                onAdvancedColorPickerChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    advancedColorPicker = value
                    savePreferences()
                },
                customAccentColor = customAccentColor,
                onAccentColorChange = { color ->
                    customAccentColor = color
                    savePreferencesDebounced()
                },
                onGradientClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGradient = true
                },
                isDarkTheme = isDarkTheme,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            GradientPanel(
                visible = showGradient,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGradient = false
                },
                gradientEnabled = gradientEnabled,
                onGradientChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    gradientEnabled = value
                    savePreferences()
                },
                customGradientStart = customGradientStart,
                onGradientStartChange = { color ->
                    customGradientStart = color
                    savePreferencesDebounced()
                },
                customGradientEnd = customGradientEnd,
                onGradientEndChange = { color ->
                    customGradientEnd = color
                    savePreferencesDebounced()
                },
                useDynamicColor = useDynamicColor,
                advancedColorPicker = advancedColorPicker,
                oledMode = effectiveOledMode,
                darkModeActive = effectiveOledMode,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )

            VerbosePanel(
                visible = showVerbosePanel,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVerbosePanel = false
                },
                verboseOutput = verboseOutput,
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                blurEnabled = blurEnabled,
                oledMode = effectiveOledMode
            )

            AggressiveWarningDialog(
                visible = showAggressiveWarning,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAggressiveWarning = false
                    // Reset aggressive mode if not confirmed
                    if (!aggressiveModeConfirmed) {
                        aggressiveMode = false
                        savePreferences()
                    }
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAggressiveWarning = false
                    aggressiveModeConfirmed = true
                },
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode,
                dontShowAgain = dontShowAggressiveWarning,
                onDontShowAgainChange = { checked ->
                    dontShowAggressiveWarning = checked
                    prefs.edit().putBoolean("dont_show_aggressive_warning", checked).apply()
                }
            )

            GPUWatchConfirmDialog(
                visible = showGPUWatchConfirm,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGPUWatchConfirm = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGPUWatchConfirm = false
                    try {
                        val intent = Intent("com.android.settings.SHOW_REGULATORY_INFO")
                        intent.setClassName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            // Fallback failed
                        }
                    }
                },
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = effectiveOledMode
            )

            // Easter egg — triggered by long-pressing the GAMA title
            EasterEggDialog(
                visible = showEasterEgg,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEasterEgg = false
                },
                colors = colors,
                cardBackground = cardBackground,
                isSmallScreen = isSmallScreen
            )

            // Bottom UI Elements
            // Ensure these are NOT visible during the setup flow
            val controlsVisible = !anyPanelOpen
            val controlsAlpha by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(animDuration),
                label = "controls_alpha"
            )

            if (controlsAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (isSmallScreen) 20.dp else 30.dp, start = 20.dp, end = 20.dp)
                        .alpha(controlsAlpha)
                ) {
                    // Version number
                    AnimatedElement(visible = isVisible, staggerIndex = 4,
                        totalItems = 8, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Text(
                            text = "v$currentVersion",
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.4f)
                        )
                    }

                    // Settings button (bottom-end)
                    AnimatedElement(visible = isVisible, staggerIndex = 4,
                        totalItems = 8, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (isSmallScreen) 18.dp else 24.dp).offset(x = 20.dp, y = if (isSmallScreen) 20.dp else 30.dp)) {
                        val btnSize = if (isSmallScreen) 48.dp else 52.dp
                        val iconSize = if (isSmallScreen) 24.dp else 28.dp


                        // Glow blob uses a fixed alpha — no infinite transition needed.
                        // The blur already diffuses the shape; animating alpha here
                        // caused a recomposition every frame on the idle main screen.
                        val settingsGlowAlpha = 0.25f
                        var settingsPressed by remember { mutableStateOf(false) }
                        // ── Single Animatable replaces 3 separate animators ───
                        val settingsPressProgress = remember { Animatable(0f) }
                        LaunchedEffect(settingsPressed) {
                            settingsPressProgress.animateTo(
                                targetValue   = if (settingsPressed) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = if (settingsPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                                    stiffness    = if (settingsPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                                )
                            )
                        }
                        val spp = settingsPressProgress.value
                        val settingsAppearScale by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0.82f,
                            animationSpec = spring(
                                dampingRatio = 0.58f,
                                stiffness = 360f
                            ),
                            label = "settings_appear_scale"
                        )
                        val settingsPressScale  = (1f - spp * (1f - MotionTokens.Scale.subtle)) * settingsAppearScale
                        val settingsBorderAlpha = 0.4f + spp * 0.6f
                        val settingsBorderWidth = (1.5f + spp * 0.5f).dp
                        val glowSize = btnSize * 1.8f

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(glowSize)) {
                                // Static glow blob — blur radius is fixed so GPU sets RenderEffect once.
                                // On API < 31 (no RenderEffect support) just draw a soft circle with alpha.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Box(
                                        modifier = Modifier
                                            .size(glowSize)
                                            .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha),
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha * 0.4f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(glowSize)
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        colors.primaryAccent.copy(alpha = settingsGlowAlpha * 0.4f),
                                                        Color.Transparent
                                                    )
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .graphicsLayer(scaleX = settingsPressScale, scaleY = settingsPressScale)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    colors.primaryAccent.copy(alpha = 0.22f),
                                                    colors.primaryAccent.copy(alpha = 0.08f)
                                                )
                                            )
                                        )
                                        .border(settingsBorderWidth, colors.primaryAccent.copy(alpha = settingsBorderAlpha), RoundedCornerShape(28.dp))
                                        .semantics { contentDescription = "Open Settings" }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    val hapticStartedAt = GamaHaptics.pressStart(context, view)
                                                    settingsPressed = true
                                                    val released = tryAwaitRelease()
                                                    settingsPressed = false
                                                    GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                                                    if (released) {
                                                        openMainPanelExclusive { showSettings = true }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(iconSize)) {
                                        val dotRadius = 2.5.dp.toPx()
                                        val spacing = size.height / 4
                                        val centerX = size.width / 2
                                        repeat(3) { i ->
                                            drawCircle(
                                                color = colors.primaryAccent.copy(alpha = 0.9f),
                                                radius = dotRadius,
                                                center = Offset(centerX, spacing + (i * spacing))
                                            )
                                        }
                                    }
                                }
                            }
                        // One-time label
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showButtonLabels,
                            enter = fadeIn(animationSpec = tween(260, easing = MotionTokens.Easing.enter)) +
                                    slideInVertically(animationSpec = tween(260, easing = MotionTokens.Easing.emphasizedDecelerate)) { it / 3 },
                            exit  = fadeOut(animationSpec = tween(180, easing = MotionTokens.Easing.exit)) +
                                    slideOutVertically(animationSpec = tween(180, easing = MotionTokens.Easing.exit)) { it / 4 }
                        ) {
                            Text(
                                text = strings["settings.title"].replace("S", "S").let { it.ifEmpty { "Settings" }.lowercase().replaceFirstChar { c -> c.uppercase() } },
                                fontSize = ts.labelSmall,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
