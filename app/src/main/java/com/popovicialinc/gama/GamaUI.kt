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
    onImportBackup: (callback: (String) -> Unit) -> Unit = {}
) {
    val ts = LocalTypeScale.current
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

    fun performHaptic(type: Int = HapticFeedbackConstants.LONG_PRESS) {
        view.performHapticFeedback(type)
    }

    // Enhanced haptic pattern for success
    fun performSuccessHaptic() {
        scope.launch {
            // Success pattern: quick double tap followed by a long confirmation
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            delay(80)
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            delay(120)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    // Enhanced haptic pattern for errors/warnings
    fun performErrorHaptic() {
        scope.launch {
            // Error pattern: three short pulses
            repeat(3) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                delay(100)
            }
        }
    }

    // Enhanced haptic for interactions (more pronounced than simple click)
    fun performInteractionHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
    var successDialogMessage by remember { mutableStateOf("") }
    // True from the moment the user taps Continue until commandOutput arrives,
    // which is the real completion signal from ShizukuHelper. During this window
    // SuccessDialog shows a spinner rather than the final confirmation message.
    var rendererSwitching by remember { mutableStateOf(false) }
    var showDeveloperMenu by remember { mutableStateOf(false) }
    var showVerbosePanel by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
    // Pre-cached app list — loaded on IO thread the moment showAppSelector becomes true,
    // before BouncyDialog's enter animation even starts, so the list is ready instantly.
    var cachedAppList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var appListLoading by remember { mutableStateOf(false) }
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
    var showVisualEffects by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showBlurSettings by remember { mutableStateOf(false) }
    var showColorCustomization by remember { mutableStateOf(false) }
    var showOLED by remember { mutableStateOf(false) }
    var showFunctionality by remember { mutableStateOf(false) }
    var showIntegrations by remember { mutableStateOf(false) }
    var showIntegrationLinkConfirm  by remember { mutableStateOf(false) }
    var pendingIntegrationLink      by remember { mutableStateOf("") }
    var pendingIntegrationLinkLabel by remember { mutableStateOf("") }
    var pendingIntegrationLinkDesc  by remember { mutableStateOf("") }
    var showIntegrationInfoDialog   by remember { mutableStateOf(false) }
    var integrationInfoTitle        by remember { mutableStateOf("") }
    var integrationInfoBody         by remember { mutableStateOf("") }
    var showDeveloper by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }

    // ── Notifications panel ───────────────────────────────────────────────────
    var showNotifications by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }
    // 0=2 h, 1=4 h, 2=6 h, 3=12 h, 4=24 h
    var notifIntervalIndex by remember { mutableStateOf(prefs.getInt("notif_interval_idx", 2)) }
    var lastNotifSentTime by remember { mutableStateOf(prefs.getLong("notif_last_sent", 0L)) }
    // True once we've asked for the OS permission at least once
    var notifPermissionRequested by remember { mutableStateOf(prefs.getBoolean("notif_perm_requested", false)) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuPermissionGranted by remember { mutableStateOf(false) }

    // Back handlers
    BackHandler(enabled = showSuccessDialog) {
        if (!rendererSwitching) {
            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
            showSuccessDialog = false
        }
    }

    BackHandler(enabled = showDeveloperMenu) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showDeveloperMenu = false
    }

    BackHandler(enabled = showDeveloper) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showDeveloper = false
    }

    BackHandler(enabled = showShizukuHelp) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showShizukuHelp = false
    }

    BackHandler(enabled = showColorCustomization) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showColorCustomization = false
        // Re-open parent
        showVisualEffects = true
    }

    BackHandler(enabled = showParticles) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showParticles = false
        // Re-open parent (Effects panel, not Visual Effects)
        showEffects = true
    }

    BackHandler(enabled = showVisualEffects && !showColorCustomization && !showOLED && !showParticles) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showVisualEffects = false
    }

    BackHandler(enabled = showOLED) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showOLED = false
    }

    BackHandler(enabled = showEffects && !showBlurSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEffects = false
        // Re-open parent
        showVisualEffects = true
    }

    BackHandler(enabled = showBlurSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showBlurSettings = false
        showEffects = true
    }

    BackHandler(enabled = showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showNotifications = false
        showFunctionality = true
    }

    BackHandler(enabled = showBackup) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showBackup = false
        showFunctionality = true
    }

    BackHandler(enabled = showCrashLog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showCrashLog = false
        showFunctionality = true
    }

    BackHandler(enabled = showFunctionality && !showNotifications && !showBackup && !showCrashLog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showFunctionality = false
    }
    BackHandler(enabled = showIntegrations) {
        showIntegrations = false
    }

    BackHandler(enabled = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality && !showEffects && !showBlurSettings && !showParticles && !showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettings = false
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

    BackHandler(enabled = showAppSelector) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showAppSelector = false
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
    // true = optimised (blur at full strength, alpha-fade the overlay) — default and recommended.
    // false = real (animate the blur radius itself, more expensive).
    var blurOptimised by remember { mutableStateOf(prefs.getBoolean("blur_optimised", true)) }
    var particlesEnabled by remember { mutableStateOf(prefs.getBoolean("particles_enabled", true)) }
    var particleSpeed by remember { mutableStateOf(prefs.getInt("particle_speed", 0)) } // 0=low, 1=medium, 2=high (default: low)
    var particleParallaxEnabled by remember { mutableStateOf(prefs.getBoolean("particle_parallax_enabled", true)) }
    var particleParallaxSensitivity by remember { mutableStateOf(prefs.getInt("particle_parallax_sensitivity", 0)) } // 0=low(0.15), 1=medium(0.3), 2=high(0.5)
    var particleStarMode by remember { mutableStateOf(prefs.getBoolean("particle_star_mode", false)) } // New: star mode toggle
    var particleTimeMode by remember { mutableStateOf(prefs.getBoolean("particle_time_mode", true)) } // Time-based sun & moon system
    var timeOffsetHours by remember { mutableStateOf(prefs.getFloat("time_offset_hours", 0f)) } // Developer: time offset
    var particleCount by remember { mutableStateOf(prefs.getInt("particle_count", 0)) } // 0=low(75), 1=medium(150), 2=high(300), 3=custom (default: low)
    var particleCountCustom by remember { mutableStateOf(prefs.getInt("particle_count_custom", 150).toString()) }
    var themePreference by remember { mutableStateOf(prefs.getInt("theme_preference", 0)) }

    // New settings
    // Replaced separate buttonSize with shared uiScale
    var uiScale by remember { mutableStateOf(prefs.getInt("ui_scale", 1)) } // 0=75%, 1=100%, 2=125%
    var verboseMode by remember { mutableStateOf(prefs.getBoolean("verbose_mode", false)) }
    var aggressiveMode by remember { mutableStateOf(prefs.getBoolean("aggressive_mode", false)) }
    var dozeMode by remember { mutableStateOf(prefs.getBoolean("doze_mode", false)) }
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

    // Separate dynamic color for OLED mode - always pulls from wallpaper when enabled
    val oledDynamicAccent = remember(customAccentColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(context.getColor(android.R.color.system_accent1_400))  // Always use dark variant for OLED
        } else {
            customAccentColor
        }
    }

    val dynamicGradientStart = remember(useDynamicColor, isDarkTheme, customGradientStart) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(context.getColor(android.R.color.system_accent1_700))
            } else {
                Color(context.getColor(android.R.color.system_accent1_200))
            }
        } else {
            customGradientStart
        }
    }

    val dynamicGradientEnd = remember(useDynamicColor, isDarkTheme, customGradientEnd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
            if (isDarkTheme) {
                Color(0xFF000000)
            } else {
                Color(context.getColor(android.R.color.system_neutral1_50))
            }
        } else {
            customGradientEnd
        }
    }

    // Determine target colors (Normal or Success/Pastel Green or OLED)
    val baseColors = when {
        oledMode -> ThemeColors.oledMode(dynamicAccent)  // Use normal accent for OLED mode
        isDarkTheme -> ThemeColors.dark(dynamicAccent, dynamicGradientStart, dynamicGradientEnd)
        else -> ThemeColors.light(dynamicAccent, dynamicGradientStart, dynamicGradientEnd)
    }

    // Use base colors directly - no color changing for success dialog
    val targetColors = baseColors

    // Synchronized color animations — all channels transition together so the UI
    // updates in perfect sync on every theme/accent/oled/dynamic-color change.
    // The accent color picker produces a natural wave feel just from the way
    // Compose recomposes (accent-derived values like border update one frame later
    // naturally), without needing artificial delays that desync fast toggles.
    val animatedAccent by animateColorAsState(
        targetValue = targetColors.primaryAccent,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "accent_anim"
    )
    val animatedBorder by animateColorAsState(
        targetValue = targetColors.border,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "border_anim"
    )
    val animatedTextPrimary by animateColorAsState(
        targetValue = targetColors.textPrimary,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "text_primary_anim"
    )
    val animatedTextSecondary by animateColorAsState(
        targetValue = targetColors.textSecondary,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "text_secondary_anim"
    )
    val animatedCardBackground by animateColorAsState(
        targetValue = targetColors.cardBackground,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "card_bg_anim"
    )
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetColors.background,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "bg_anim"
    )
    val animatedGradientStart by animateColorAsState(
        targetValue = targetColors.gradientStart,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "grad_start_anim"
    )
    val animatedGradientEnd by animateColorAsState(
        targetValue = targetColors.gradientEnd,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
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

    val cardBackground = animatedCardBackground

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
        val snapBlurOptimised    = blurOptimised
        val snapThemePref        = themePreference
        val snapDynColor         = useDynamicColor
        val snapAdvColorPicker   = advancedColorPicker
        val snapAccent           = customAccentColor.toArgb()
        val snapGradStart        = customGradientStart.toArgb()
        val snapGradEnd          = customGradientEnd.toArgb()
        val snapUiScale          = uiScale
        val snapVerbose          = verboseMode
        val snapAggressive       = aggressiveMode
        val snapDoze             = dozeMode
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
                putBoolean("blur_optimised",            snapBlurOptimised)
                putInt("theme_preference",              snapThemePref)
                putBoolean("use_dynamic_color",         snapDynColor)
                putBoolean("advanced_color_picker",     snapAdvColorPicker)
                putInt("custom_accent",                 snapAccent)
                putInt("custom_gradient_start",         snapGradStart)
                putInt("custom_gradient_end",           snapGradEnd)
                putInt("ui_scale",                      snapUiScale)
                putBoolean("verbose_mode",              snapVerbose)
                putBoolean("aggressive_mode",           snapAggressive)
                putBoolean("doze_mode",                 snapDoze)
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
            showSettings || showVisualEffects || showColorCustomization || showOLED ||
            showFunctionality || showIntegrations || showShizukuHelp || showSuccessDialog || showDeveloperMenu ||
            showVerbosePanel || showAppSelector || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
            showEffects || showBlurSettings || showParticles
        }
    }

    // Blur is expensive — lightweight pop-up dialogs (ExternalLinkConfirmDialog) are excluded
    // so their entry animation never races against a simultaneous full-screen blur pass.
    val anyFullPanelOpen by remember {
        derivedStateOf {
            showWarningDialog || showGitHubDialog || showResourcesPanel ||
            showSettings || showVisualEffects || showColorCustomization || showOLED ||
            showFunctionality || showIntegrations || showShizukuHelp || showSuccessDialog || showDeveloperMenu ||
            showVerbosePanel || showAppSelector || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications || showBackup || showCrashLog ||
            showEffects || showBlurSettings || showParticles
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

        // Gradient ramps up slowly (0 -> 1 over 2000ms)
        gradientStartupAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
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
                if (userName.isNotEmpty()) "You're all set, $userName! ✅" else "Shizuku is running ✅"
            } else {
                "Permission needed ⚠️"
            }
        } else {
            "Shizuku isn't running ❌"
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
                targetValue = if (gradientEnabled) 1f else 0f,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(1000, easing = MotionTokens.Easing.emphasizedDecelerate),
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
                                alpha = if (isDarkTheme) breathingAlpha else 0.6f
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

            // Independent Particles Overlay (sun/moon/clouds)
            // OUTSIDE blur box so particles stay sharp when panels open
            // Disabled when OLED mode is enabled
            ParticlesOverlay(
                enabled = particlesEnabled && !oledMode,
                color = colors.primaryAccent,
                particleSpeed = particleSpeed,
                parallaxEnabled = particleParallaxEnabled,
                particleCount = particleCount,
                particleCountCustom = particleCountCustom.toIntOrNull() ?: 150,
                parallaxSensitivity = animatedParallaxSensitivity,
                starMode = particleStarMode,
                timeModeEnabled = particleTimeMode,
                timeOffsetHours = timeOffsetHours,
                anyPanelOpen = anyPanelOpen,
                isLandscape = isLandscape
            )

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
                        // Landscape Layout - Split screen with title on left, cards on right
                        Row(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            // Left half: Title section (fills 50% width, 100% height, centered content)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Row 1: Title (Delay 0)
                                AnimatedElement(visible = isVisible, staggerIndex = 0,
                                    totalItems = 7) {
                                    TitleSection(
                                        colors = colors,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = true,
                                        userName = userName,
                                        currentHour = currentHour,
                                        onEasterEgg = {
                                            performHaptic(HapticFeedbackConstants.LONG_PRESS)
                                            showEasterEgg = true
                                        }
                                    )
                                }
                            }

                            // Right half: Action cards (fills 50% width, 100% height, centered content)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .padding(end = 24.dp), // Right padding to balance
                                contentAlignment = Alignment.Center
                            ) {
                                MainContentCards(
                                    isVisible = isVisible,
                                    isSmallScreen = isSmallScreen,
                                    isTablet = isTablet,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    currentRenderer = currentRenderer,
                                    commandOutput = commandOutput,
                                    shizukuStatus = shizukuStatus,
                                    shizukuRunning = shizukuRunning,
                                    shizukuPermissionGranted = shizukuPermissionGranted,
                                    onShizukuStatusClick = {
                                        if (shizukuStatus.contains("❌") || shizukuStatus.contains("⚠️")) {
                                            performHaptic()
                                            shizukuHelpType = when {
                                                shizukuStatus.contains("❌") -> "not_running"
                                                shizukuStatus.contains("⚠️") -> "permission"
                                                else -> ""
                                            }
                                            showShizukuHelp = true
                                        }
                                    },
                                    onVulkanClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        pendingRendererName = "Vulkan"
                                        pendingRendererSwitch = {
                                            verboseOutput = ""
                                            if (verboseMode) showVerbosePanel = true
                                            ShizukuHelper.runVulkan(
                                                context,
                                                scope,
                                                aggressiveMode,
                                                excludedAppsList.toSet(),
                                                emptySet(),
                                                { commandOutput = it },
                                                if (verboseMode) { output -> verboseOutput += output } else null
                                            )
                                        }
                                        // Prepare success message
                                        successDialogMessage =
                                            "Okay! Vulkan has been applied!\nClear your recents menu."
                                        showWarningDialog = true
                                    },
                                    onOpenGLClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        pendingRendererName = "OpenGL"
                                        pendingRendererSwitch = {
                                            verboseOutput = ""
                                            if (verboseMode) showVerbosePanel = true
                                            ShizukuHelper.runOpenGL(
                                                context,
                                                scope,
                                                aggressiveMode,
                                                excludedAppsList.toSet(),
                                                emptySet(),
                                                { commandOutput = it },
                                                if (verboseMode) { output -> verboseOutput += output } else null
                                            )
                                        }
                                        // Prepare success message
                                        successDialogMessage =
                                            "Okay! OpenGL has been applied!\nClear your recents menu."
                                        showWarningDialog = true
                                    },
                                    onResourcesClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showResourcesPanel = true
                                    },
                                    onGPUWatchClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showGPUWatchConfirm = true // Correctly show dialog first
                                    },
                                    oledMode = oledMode,
                                    rendererLoading = rendererLoading,
                                    lastSwitchTime = lastSwitchTime
                                )
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
                                AnimatedElement(visible = isVisible, staggerIndex = 0,
                                    totalItems = 7) {
                                    Text(
                                        text = remember(currentHour, userName) {
                                            val pool = when (currentHour) {
                                                in 0..5 -> if (userName.isNotEmpty()) listOf("Still up, $userName? 🌙","Late night session, $userName? 🌙","The world is quiet, $userName. 🌙","Somewhere between today and tomorrow, $userName. 🌙") else listOf("Still up? 🌙","Late night session? 🌙","The world is quiet. 🌙","Somewhere between today and tomorrow. 🌙")
                                                in 6..11 -> if (userName.isNotEmpty()) listOf("Good morning, $userName! ☀️","Morning, $userName! ☀️","Rise and shine, $userName! ☀️","A fresh start, $userName. ☀️","Up early, $userName? ☀️") else listOf("Good morning! ☀️","Morning! ☀️","Rise and shine! ☀️","A fresh start. ☀️","Up early? ☀️")
                                                in 12..16 -> if (userName.isNotEmpty()) listOf("Hey, $userName! 👋","Good afternoon, $userName! 👋","Welcome back, $userName. 👋","There you are, $userName! 👋","Good to see you, $userName. 👋") else listOf("Hey! 👋","Good afternoon! 👋","Welcome back. 👋","There you are! 👋","Good to see you. 👋")
                                                in 17..22 -> if (userName.isNotEmpty()) listOf("Good evening, $userName! 🌙","Evening, $userName. 🌙","Winding down, $userName? 🌙","End of the day, $userName. 🌙","Hope it was a good one, $userName. 🌙") else listOf("Good evening! 🌙","Evening. 🌙","Winding down? 🌙","End of the day. 🌙","Hope it was a good one. 🌙")
                                                else -> if (userName.isNotEmpty()) listOf("Still at it, $userName? 🌙","The quiet hours, $userName. 🌙","Almost tomorrow, $userName. 🌙","Some nights are for thinking, $userName. 🌙","The world can wait, $userName. 🌙") else listOf("Still at it? 🌙","The quiet hours. 🌙","Almost tomorrow. 🌙","Some nights are for thinking. 🌙","The world can wait. 🌙")
                                            }
                                            pool.random()
                                        },
                                        fontSize = ts.bodyLarge,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // GAMA Title with bars
                                AnimatedElement(visible = isVisible, staggerIndex = 1,
                                    totalItems = 7) {
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
                                    totalItems = 7) {
                                    Text(
                                        text = "Standing by and awaiting your command",
                                        fontSize = ts.bodyLarge,  // Reduced from 18/21
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // What's Next
                                AnimatedElement(visible = isVisible, staggerIndex = 3,
                                    totalItems = 7) {
                                    Text(
                                        text = "WHAT'S NEXT?",
                                        fontSize = ts.bodyMedium,  // Reduced from 16/19
                                        fontFamily = quicksandFontFamily,
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Renderer Card
                                AnimatedElement(visible = isVisible, staggerIndex = 4,
                                    totalItems = 7) {
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
                                                showShizukuHelp = true
                                            }
                                        },
                                        oledMode = oledMode,
                                        rendererLoading = rendererLoading,
                                        lastSwitchTime = lastSwitchTime
                                    )
                                }

                                // 2×2 Button Grid
                                AnimatedElement(visible = isVisible, staggerIndex = 5,
                                    totalItems = 7) {
                                    val vulkanScale by animateFloatAsState(
                                        targetValue = if (shizukuRunning && shizukuPermissionGranted) 1f else 0.85f,
                                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                        label = "vulkan_scale"
                                    )
                                    val vulkanAlpha by animateFloatAsState(
                                        targetValue = if (shizukuRunning && shizukuPermissionGranted) 1f else 0.25f,
                                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                        label = "vulkan_alpha"
                                    )
                                    val openglScale by animateFloatAsState(
                                        targetValue = if (shizukuRunning && shizukuPermissionGranted) 1f else 0.85f,
                                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                        label = "opengl_scale"
                                    )
                                    val openglAlpha by animateFloatAsState(
                                        targetValue = if (shizukuRunning && shizukuPermissionGranted) 1f else 0.25f,
                                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                        label = "opengl_alpha"
                                    )
                                    val shizukuReady = shizukuRunning && shizukuPermissionGranted
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            IllustratedButton(
                                                text = "Vulkan",
                                                onClick = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    pendingRendererName = "Vulkan"
                                                    pendingRendererSwitch = {
                                                        verboseOutput = ""
                                                        if (verboseMode) showVerbosePanel = true
                                                        ShizukuHelper.runVulkan(
                                                            context, scope, aggressiveMode,
                                                            excludedAppsList.toSet(), emptySet(),
                                                            { commandOutput = it },
                                                            if (verboseMode) { output -> verboseOutput += output } else null
                                                        )
                                                    }
                                                    successDialogMessage = "Okay! Vulkan has been applied!\nClear your recents menu."
                                                    showWarningDialog = true
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .graphicsLayer(scaleX = vulkanScale, scaleY = vulkanScale, alpha = vulkanAlpha),
                                                accent = false, enabled = shizukuReady,
                                                colors = colors, oledMode = oledMode, iconType = "vulkan"
                                            )
                                            IllustratedButton(
                                                text = "OpenGL",
                                                onClick = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    pendingRendererName = "OpenGL"
                                                    pendingRendererSwitch = {
                                                        verboseOutput = ""
                                                        if (verboseMode) showVerbosePanel = true
                                                        ShizukuHelper.runOpenGL(
                                                            context, scope, aggressiveMode,
                                                            excludedAppsList.toSet(), emptySet(),
                                                            { commandOutput = it },
                                                            if (verboseMode) { output -> verboseOutput += output } else null
                                                        )
                                                    }
                                                    successDialogMessage = "Okay! OpenGL has been applied!\nClear your recents menu."
                                                    showWarningDialog = true
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .graphicsLayer(scaleX = openglScale, scaleY = openglScale, alpha = openglAlpha),
                                                accent = false, enabled = shizukuReady,
                                                colors = colors, oledMode = oledMode, iconType = "opengl"
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            IllustratedButton(
                                                text = "Resources",
                                                onClick = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    showResourcesPanel = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                accent = false, enabled = true,
                                                colors = colors, oledMode = oledMode, iconType = "resources"
                                            )
                                            IllustratedButton(
                                                text = "GPUWatch",
                                                onClick = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    showGPUWatchConfirm = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                accent = false, enabled = true,
                                                colors = colors, oledMode = oledMode, iconType = "gpuwatch"
                                            )
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
            // Single render, always. Modifier.blur() is applied in the draw phase
            // via graphicsLayer — zero recomposition overhead on any frame.
            //
            // API gate: Modifier.blur() requires API 31 (Android 12 / RenderEffect).
            // On older devices the panel open is indicated by the panel sliding in
            // alone — no blur needed and none applied. Zero GPU cost on old chipsets.
            //
            // The blurEnabled user setting is still respected: if the user has
            // turned blur off, bgBlurApply is false and no blur runs at all.
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val bgBlurApply = blurShouldApply && animationLevel != 2 && canBlur
            val bgBlurRadius by animateDpAsState(
                targetValue = if (bgBlurApply) 40.dp else 0.dp,
                animationSpec = tween(
                    durationMillis = 380,
                    easing = MotionTokens.Easing.emphasized
                ),
                label = "bg_blur_radius"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgBlurRadius > 0.dp)
                            Modifier.blur(bgBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
            ) { mainContent() }


            // Shizuku Help Dialog
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
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // NEW: Developer Menu Dialog
            DeveloperMenuDialog(
                visible = showDeveloperMenu,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showDeveloperMenu = false
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)

                    // Check if permission is granted
                    if (!ShizukuHelper.hasNotificationPermission(context)) {
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
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

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
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                isBlurred = showExternalLinkConfirm,
                oledMode = oledMode
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
                visible = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality && !showIntegrations && !showEffects && !showParticles && !showNotifications && !showBackup && !showCrashLog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettings = false
                },
                onVisualEffectsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = true
                },
                onFunctionalityClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showFunctionality = true
                },
                onIntegrationsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrations = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic() },
                oledMode = oledMode
            )

            OLEDPanel(
                visible = showOLED,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showOLED = false
                },
                oledMode = oledMode,
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    oledMode = enabled
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
            )

            // Blur wrapper for FunctionalityPanel when Aggressive Warning is shown.
            // API-gated: blur requires API 31; on older devices the panel is simply
            // visible unblurred behind the warning dialog — functionally identical.
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
                    FunctionalityPanel(
                        visible = showFunctionality && !showBackup && !showCrashLog && !showAppSelector,
                        onDismiss = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showFunctionality = false
                        },
                        verboseMode = verboseMode,
                        onVerboseModeChange = { enabled ->
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            verboseMode = enabled
                            savePreferences()
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
                                if (!enabled) {
                                    aggressiveModeConfirmed = false
                                }
                            }
                        },
                        dismissOnClickOutside = dismissOnClickOutside,
                        onDismissOnClickOutsideChange = { enabled ->
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            dismissOnClickOutside = enabled
                            savePreferences()
                        },
                        dozeMode = dozeMode,
                        onDozeModeChange = { enabled ->
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            dozeMode = enabled
                            scope.launch {
                                try {
                                    val cmd = if (enabled)
                                        "dumpsys deviceidle force-idle deep"
                                    else
                                        "dumpsys deviceidle unforce"
                                    ShizukuHelper.runCommand(cmd)
                                } catch (_: Exception) {}
                            }
                            savePreferences()
                        },
                        onAppSelectorClick = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            // Start loading apps on IO immediately — before the panel opens —
                            // so the list is ready by the time the enter animation finishes.
                            if (cachedAppList.isEmpty() && !appListLoading) {
                                appListLoading = true
                                scope.launch {
                                    cachedAppList = withContext(Dispatchers.IO) {
                                        getAllInstalledPackages(context)
                                    }
                                    appListLoading = false
                                }
                            }
                            showAppSelector = true
                        },
                        onShowAggressiveWarning = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showAggressiveWarning = true
                        },
                        onNotificationsClick = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showFunctionality = false
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
                        userName = userName,
                        isSmallScreen = isSmallScreen,
                        isLandscape = isLandscape,
                        isTablet = isTablet,
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode,
                        performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) }
                    )
                }
            } // End of FunctionalityPanel blur wrapper

            // Notifications panel (sub-panel of Functionality)
            NotificationsPanel(
                visible = showNotifications,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showNotifications = false
                    showFunctionality = true
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
                hasPermission = ShizukuHelper.hasNotificationPermission(context),
                onRequestPermission = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    onRequestNotificationPermission()
                },
                onTestNotification = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (!ShizukuHelper.hasNotificationPermission(context)) {
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
                oledMode = oledMode
            )

            // ── Backup & Restore panel ────────────────────────────────────────
            BackupPanel(
                visible = showBackup,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBackup = false
                    showFunctionality = true
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
                                blurOptimised        = prefs.getBoolean("blur_optimised", true)
                                particlesEnabled     = prefs.getBoolean("particles_enabled", true)
                                particleSpeed        = prefs.getInt("particle_speed", 0)
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
                                oledMode             = prefs.getBoolean("oled_mode", false)
                                oledAccentColor      = Color(prefs.getInt("oled_accent_color", 0xFF4895EF.toInt()))
                                useDynamicColorOLED  = prefs.getBoolean("use_dynamic_color_oled", false)
                                useDynamicColor      = prefs.getBoolean("use_dynamic_color", true)
                                customAccentColor    = Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt()))
                                customGradientStart  = Color(prefs.getInt("custom_gradient_start", 0xFF0A2540.toInt()))
                                customGradientEnd    = Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))
                                dismissOnClickOutside = prefs.getBoolean("dismiss_on_click_outside", true)
                                notificationsEnabled = prefs.getBoolean("notif_enabled", true)
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
                oledMode = oledMode
            )

            // ── Crash Log panel ───────────────────────────────────────────────
            CrashLogPanel(
                visible = showCrashLog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showCrashLog = false
                    showFunctionality = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = oledMode
            )

            IntegrationsPanel(
                visible = showIntegrations,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrations = false
                },
                onLinkSelected = { url, label, desc ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    pendingIntegrationLink      = url
                    pendingIntegrationLinkLabel = label
                    pendingIntegrationLinkDesc  = desc
                    showIntegrationLinkConfirm  = true
                },
                onInfoRequested = { title, body ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    integrationInfoTitle      = title
                    integrationInfoBody       = body
                    showIntegrationInfoDialog = true
                },
                isBlurred = showIntegrationLinkConfirm || showIntegrationInfoDialog,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = oledMode
            )

            // ── Integrations: external link confirm ───────────────────────────
            ExternalLinkConfirmDialog(
                visible = showIntegrationLinkConfirm,
                label = pendingIntegrationLinkLabel,
                description = pendingIntegrationLinkDesc,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrationLinkConfirm = false
                },
                onConfirm = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingIntegrationLink))
                    context.startActivity(intent)
                    showIntegrationLinkConfirm = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground
            )

            // ── Integrations: info-only dialog (QS Tiles / Widget) ────────────
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

                    // Check if permission is granted
                    if (!ShizukuHelper.hasNotificationPermission(context)) {
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
                oledMode = oledMode,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                timeOffsetHours = timeOffsetHours,
                onTimeOffsetChange = { value ->
                    timeOffsetHours = value
                    savePreferences()
                }
            )


            VisualEffectsPanel(
                visible = showVisualEffects,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = false
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
                oledMode = oledMode,
                onOledModeChange = { enabled ->
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    oledMode = enabled
                    savePreferences()
                },
                onColorCustomizationClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = false
                    showColorCustomization = true
                },
                onEffectsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = false
                    showEffects = true
                },
                onParticlesClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = false
                    showParticles = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) }
            )

            EffectsPanel(
                visible = showEffects && !showBlurSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = false
                    // Re-open parent
                    showVisualEffects = true
                },
                gradientEnabled = gradientEnabled,
                onGradientChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    gradientEnabled = value
                    savePreferences()
                },
                onBlurSettingsClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBlurSettings = true
                },
                onParticlesClick = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = false
                    showParticles = true
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = oledMode
            )

            BlurPanel(
                visible = showBlurSettings,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showBlurSettings = false
                    showEffects = true
                },
                blurEnabled = blurEnabled,
                onBlurChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    blurEnabled = value
                    savePreferences()
                },
                blurOptimised = blurOptimised,
                onBlurOptimisedChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    blurOptimised = value
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = oledMode
            )

            ParticlesPanel(
                visible = showParticles,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showParticles = false
                    showEffects = true
                },
                particlesEnabled = particlesEnabled,
                onParticlesChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    particlesEnabled = value
                    savePreferences()
                },
                particleSpeed = particleSpeed,
                onParticleSpeedChange = { value ->
                    particleSpeed = value
                    savePreferences()
                },
                particleParallaxEnabled = particleParallaxEnabled,
                onParticleParallaxChange = { value ->
                    particleParallaxEnabled = value
                    savePreferences()
                },
                particleParallaxSensitivity = particleParallaxSensitivity,
                onParticleParallaxSensitivityChange = { value ->
                    particleParallaxSensitivity = value
                    savePreferences()
                },
                particleCount = particleCount,
                onParticleCountChange = { value ->
                    particleCount = value
                    savePreferences()
                },
                particleStarMode = particleStarMode,
                onParticleStarModeChange = { value ->
                    particleStarMode = value
                    savePreferences()
                },
                particleTimeMode = particleTimeMode,
                onParticleTimeModeChange = { value ->
                    particleTimeMode = value
                    if (value && particleStarMode) {
                        particleStarMode = false // Disable star mode when time mode is enabled
                    }
                    savePreferences()
                },
                userName = userName,
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                performHaptic = { performHaptic(HapticFeedbackConstants.CLOCK_TICK) },
                oledMode = oledMode
            )

            ColorCustomizationPanel(
                visible = showColorCustomization,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showColorCustomization = false
                    showVisualEffects = true
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
                    savePreferences()
                },
                customGradientStart = customGradientStart,
                onGradientStartChange = { color ->
                    customGradientStart = color
                    savePreferences()
                },
                customGradientEnd = customGradientEnd,
                onGradientEndChange = { color ->
                    customGradientEnd = color
                    savePreferences()
                },
                isDarkTheme = isDarkTheme,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = oledMode
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
                oledMode = oledMode
            )

            AppSelectorPanel(
                visible = showAppSelector,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showAppSelector = false
                },
                excludedApps = excludedAppsList,
                onExcludedAppsChange = {
                    savePreferences()
                },
                isSmallScreen = isSmallScreen,
                colors = colors,
                cardBackground = cardBackground,
                blurEnabled = blurEnabled,
                performHaptic = { performHaptic(HapticFeedbackConstants.CONTEXT_CLICK) },
                oledMode = oledMode,
                isLandscape = isLandscape,
                isTablet = isTablet,
                preloadedApps = cachedAppList,
                isPreloading = appListLoading
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
                oledMode = oledMode,
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
                oledMode = oledMode
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
                        totalItems = 7, modifier = Modifier.align(Alignment.BottomCenter)) {
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
                        totalItems = 7, modifier = Modifier.align(Alignment.BottomEnd)) {
                        val btnSize = if (isSmallScreen) 48.dp else 52.dp
                        val iconSize = if (isSmallScreen) 24.dp else 28.dp


                        // Glow blob uses a fixed alpha — no infinite transition needed.
                        // The blur already diffuses the shape; animating alpha here
                        // caused a recomposition every frame on the idle main screen.
                        val settingsGlowAlpha = 0.25f
                        var settingsPressed by remember { mutableStateOf(false) }
                        val settingsPressScale by animateFloatAsState(
                            targetValue = if (settingsPressed) MotionTokens.Scale.subtle else 1f,
                            animationSpec = spring(
                                dampingRatio = if (settingsPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                                stiffness    = if (settingsPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                            ),
                            label = "settings_press"
                        )
                        val settingsBorderAlpha by animateFloatAsState(
                            targetValue = if (settingsPressed) 1f else 0.4f,
                            animationSpec = tween(durationMillis = if (settingsPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick, easing = FastOutSlowInEasing),
                            label = "settings_border_a"
                        )
                        val settingsBorderWidth by animateDpAsState(
                            targetValue = if (settingsPressed) 2.dp else 1.5.dp,
                            animationSpec = tween(durationMillis = if (settingsPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick, easing = FastOutSlowInEasing),
                            label = "settings_border_w"
                        )
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
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    colors.primaryAccent.copy(alpha = 0.22f),
                                                    colors.primaryAccent.copy(alpha = 0.08f)
                                                )
                                            )
                                        )
                                        .border(settingsBorderWidth, colors.primaryAccent.copy(alpha = settingsBorderAlpha), RoundedCornerShape(14.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    settingsPressed = true
                                                    tryAwaitRelease()
                                                    settingsPressed = false
                                                },
                                                onTap = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    showSettings = true
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
                            enter = fadeIn(animationSpec = tween(400)),
                            exit  = fadeOut(animationSpec = tween(600))
                        ) {
                            Text(
                                text = "Settings",
                                fontSize = ts.labelSmall,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } // end Column
                }
            }
        }
    }
}