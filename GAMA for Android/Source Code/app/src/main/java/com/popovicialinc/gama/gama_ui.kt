package com.popovicialinc.gama

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Custom fonts
private val quicksandFontFamily = FontFamily(
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_bold, FontWeight.Bold),
    Font(R.font.quicksand_bold, FontWeight.SemiBold)
)

// CompositionLocals
val LocalAnimationLevel = compositionLocalOf { 0 }
val LocalThemeColors = compositionLocalOf { ThemeColors.dark() }

// Theme color scheme
data class ThemeColors(
    val background: Color,
    val cardBackground: Color,
    val primaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val successColor: Color,
    val errorColor: Color
) {
    companion object {
        fun dark() = ThemeColors(
            background = Color(0xFF000000),
            cardBackground = Color(0xFF111111),
            primaryAccent = Color(0xFF4895EF),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.6f),
            border = Color(0xFF4895EF).copy(alpha = 0.3f),
            successColor = Color(0xFF4CAF50),
            errorColor = Color(0xFFF44336)
        )

        fun light() = ThemeColors(
            background = Color(0xFFE8F0FF),
            cardBackground = Color.White,
            primaryAccent = Color(0xFF2563EB),
            textPrimary = Color(0xFF1A1A1A),
            textSecondary = Color(0xFF666666),
            border = Color(0xFF2563EB).copy(alpha = 0.3f),
            successColor = Color(0xFF2E7D32),
            errorColor = Color(0xFFC62828)
        )
    }
}

@Composable
fun GamaUI() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenWidth < 360.dp || screenHeight < 640.dp
    val isLargeScreen = screenWidth > 600.dp
    val isLandscape = screenWidth > screenHeight
    val isTablet = screenWidth >= 600.dp && screenHeight >= 600.dp

    fun performHaptic(type: Int = HapticFeedbackConstants.LONG_PRESS) {
        view.performHapticFeedback(type)
    }

    val prefs = remember { context.getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE) }

    var shizukuStatus by remember { mutableStateOf("Checking...") }
    var commandOutput by remember { mutableStateOf("") }
    var currentRenderer by remember { mutableStateOf("Detecting...") }
    var versionStatus by remember { mutableStateOf("Checking version...") }
    var showWarningDialog by remember { mutableStateOf(false) }
    var pendingRendererSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isVisible by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVisualEffects by remember { mutableStateOf(false) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuPermissionGranted by remember { mutableStateOf(false) }

    BackHandler(enabled = showVisualEffects) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showVisualEffects = false
    }

    BackHandler(enabled = showSettings && !showVisualEffects) {
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

    BackHandler(enabled = showSettings) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSettings = false
    }

    val systemInDarkTheme = isSystemInDarkTheme()
    var animationLevel by remember { mutableStateOf(prefs.getInt("animation_level", 0)) }
    var gradientEnabled by remember { mutableStateOf(prefs.getBoolean("gradient_enabled", true)) }
    var blurEnabled by remember { mutableStateOf(prefs.getBoolean("blur_enabled", true)) }
    var themePreference by remember { mutableStateOf(prefs.getInt("theme_preference", 0)) } // 0=Auto, 1=Dark, 2=Light

    val isDarkTheme = when (themePreference) {
        1 -> true  // Dark
        2 -> false // Light
        else -> systemInDarkTheme // Auto
    }

    val colors = if (isDarkTheme) ThemeColors.dark() else ThemeColors.light()

    val animatedBackgroundColor by animateColorAsState(
        targetValue = colors.background,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bg_anim"
    )

    val cardBackground by animateColorAsState(
        targetValue = colors.cardBackground,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "card_bg_anim"
    )

    fun savePreferences() {
        prefs.edit().apply {
            putInt("animation_level", animationLevel)
            putBoolean("gradient_enabled", gradientEnabled)
            putBoolean("blur_enabled", blurEnabled)
            putInt("theme_preference", themePreference)
            apply()
        }
    }

    val animDuration = when (animationLevel) {
        0 -> 600 // Max
        1 -> 300 // Medium
        else -> 0 // None
    }

    // Check if any panel is open
    val anyPanelOpen = showWarningDialog || showGitHubDialog || showSettings || showVisualEffects

    // Blur amount
    val blurAmount by animateDpAsState(
        targetValue = if (anyPanelOpen && blurEnabled) 12.dp else 0.dp,
        animationSpec = if (animationLevel == 2) snap() else tween(animDuration, easing = FastOutSlowInEasing),
        label = "blur"
    )

    // Dark overlay when blur is disabled
    val overlayAlpha by animateFloatAsState(
        targetValue = if (anyPanelOpen && !blurEnabled) { if (isDarkTheme) 0.8f else 0.4f } else 0f,
        animationSpec = if (animationLevel == 2) snap() else tween(animDuration, easing = FastOutSlowInEasing),
        label = "overlay"
    )

    val mainMenuScale by animateFloatAsState(
        targetValue = if (anyPanelOpen) 0.82f else 1f,
        animationSpec = if (animationLevel == 2) {
            snap()
        } else {
            spring(
                dampingRatio = 0.3f, // Much higher damping for premium feel
                stiffness = Spring.StiffnessLow
            )
        },
        label = "main_menu_scale"
    )

    // Settings button alpha
    val settingsButtonAlpha by animateFloatAsState(
        targetValue = if (!isVisible) 0f else 1f,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = animDuration,
            easing = FastOutSlowInEasing
        ),
        label = "settings_button_alpha"
    )

    val currentVersion = "1.0.0"

    // Infinite transition for breathing gradient
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, // Dimmer start
        targetValue = 1f,  // Brighter peak
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true

        shizukuRunning = ShizukuHelper.checkBinder()
        shizukuPermissionGranted = ShizukuHelper.checkPermission()

        shizukuStatus = if (shizukuRunning) {
            if (shizukuPermissionGranted) {
                "Shizuku is running ✅"
            } else {
                "Permission needed ⚠️"
            }
        } else {
            "Shizuku not running ❌"
        }

        scope.launch {
            currentRenderer = ShizukuHelper.getCurrentRenderer()
        }

        scope.launch {
            versionStatus = ShizukuHelper.checkVersion(currentVersion)
        }
    }

    CompositionLocalProvider(
        LocalAnimationLevel provides animationLevel,
        LocalThemeColors provides colors
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor) // Apply smooth background transition
        ) {
            // Smooth Gradient Overlay - for both themes
            if (gradientEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isDarkTheme) breathingAlpha else 0.3f) // Subtle breathing in dark, static in light
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        Color(0xFF0A1525), // Deeper midnight blue for dark
                                        animatedBackgroundColor
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFD6E8FF), // Lighter blue for light theme
                                        animatedBackgroundColor
                                    )
                                },
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY * 0.6f
                            )
                        )
                )
            }

            // Main content with blur
            if (isLandscape && !isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurAmount)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 40.dp)
                        .scale(mainMenuScale)
                        .then(
                            if (anyPanelOpen) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures
                                }
                            } else Modifier
                        ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT COLUMN - Title and Info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // GAMA Title 
                        AnimatedElement(
                            visible = isVisible,
                            delay = 0
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "GAMA",
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = quicksandFontFamily,
                                    color = colors.textPrimary,
                                    modifier = Modifier.drawWithContent {
                                        drawIntoCanvas { canvas ->
                                            val paint = Paint().asFrameworkPaint()
                                            paint.color = colors.textPrimary.toArgb()
                                            paint.textSize = 54.sp.toPx()
                                            paint.setShadowLayer(120f, 0f, 0f, colors.textPrimary.copy(alpha = 0.9f).toArgb())
                                            paint.textAlign = android.graphics.Paint.Align.CENTER

                                            canvas.nativeCanvas.drawText(
                                                "GAMA",
                                                size.width / 2,
                                                size.height / 2 + 14.sp.toPx(),
                                                paint
                                            )
                                        }
                                    }
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Standing by and awaiting your command.",
                                        color = colors.textSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = quicksandFontFamily,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Text(
                                        text = "WHAT'S NEXT?",
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 2.sp,
                                        fontFamily = quicksandFontFamily
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedElement(
                            visible = isVisible,
                            delay = 750
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Version $currentVersion",
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    text = versionStatus,
                                    color = if (versionStatus.contains("latest")) colors.successColor else colors.errorColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Current Renderer Card
                        AnimatedElement(
                            visible = isVisible,
                            delay = 150
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = colors.border,
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = cardBackground),
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "CURRENT RENDERER",
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 2.sp,
                                        fontFamily = quicksandFontFamily
                                    )
                                    Text(
                                        text = currentRenderer,
                                        color = colors.textPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = quicksandFontFamily,
                                        textAlign = TextAlign.Center
                                    )

                                    AnimatedVisibility(
                                        visible = commandOutput.isNotEmpty(),
                                        enter = fadeIn(animationSpec = tween(400)) +
                                                expandVertically(animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )),
                                        exit = fadeOut(animationSpec = tween(300)) +
                                                shrinkVertically(animationSpec = tween(300))
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                colors.border,
                                                                Color.Transparent
                                                            )
                                                        )
                                                    )
                                            )

                                            Text(
                                                text = commandOutput,
                                                color = colors.textPrimary.copy(alpha = 0.8f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = quicksandFontFamily,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AnimatedElement(
                            visible = isVisible,
                            delay = 300
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val buttonsEnabled = shizukuRunning && shizukuPermissionGranted

                                FlatButton(
                                    text = "Vulkan",
                                    onClick = {
                                        if (buttonsEnabled) {
                                            performHaptic()
                                            pendingRendererSwitch = {
                                                ShizukuHelper.runVulkan(context, scope) { status ->
                                                    commandOutput = status
                                                    scope.launch {
                                                        delay(3000)
                                                        commandOutput = ""
                                                        currentRenderer = ShizukuHelper.getCurrentRenderer()
                                                    }
                                                }
                                            }
                                            showWarningDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 80.dp),
                                    enabled = buttonsEnabled && !anyPanelOpen,
                                    colors = colors,
                                    maxLines = 1
                                )

                                FlatButton(
                                    text = "OpenGL",
                                    onClick = {
                                        if (buttonsEnabled) {
                                            performHaptic()
                                            pendingRendererSwitch = {
                                                ShizukuHelper.runOpenGL(context, scope) { status ->
                                                    commandOutput = status
                                                    scope.launch {
                                                        delay(3000)
                                                        commandOutput = ""
                                                        currentRenderer = ShizukuHelper.getCurrentRenderer()
                                                    }
                                                }
                                            }
                                            showWarningDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 80.dp),
                                    enabled = buttonsEnabled && !anyPanelOpen,
                                    colors = colors,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shizuku Status Card
                        AnimatedElement(
                            visible = isVisible,
                            delay = 450
                        ) {
                            val borderColor = if (shizukuRunning && shizukuPermissionGranted) {
                                colors.successColor
                            } else {
                                colors.errorColor
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = borderColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = cardBackground),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shizukuStatus,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = quicksandFontFamily,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // GitHub Button
                        AnimatedElement(
                            visible = isVisible,
                            delay = 600
                        ) {
                            FlatButton(
                                text = "View on GitHub",
                                maxLines = 1,
                                onClick = {
                                    if (!anyPanelOpen) {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showGitHubDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                accent = true,
                                enabled = !anyPanelOpen,
                                colors = colors
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(blurAmount)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (isSmallScreen) 16.dp else 20.dp)
                        .padding(bottom = 80.dp)
                        .scale(mainMenuScale)
                        .then(
                            if (anyPanelOpen) {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures
                                }
                            } else Modifier
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 40.dp else 60.dp))

                    // GAMA Title
                    AnimatedElement(
                        visible = isVisible,
                        delay = 0
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 12.dp else 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
                            ) {
                                Text(
                                    text = "GAMA",
                                    fontSize = if (isSmallScreen) 56.sp else if (isLargeScreen) 86.sp else 76.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = quicksandFontFamily,
                                    color = colors.textPrimary,
                                    modifier = Modifier.drawWithContent {
                                        drawIntoCanvas { canvas ->
                                            val paint = Paint().asFrameworkPaint()
                                            paint.color = colors.textPrimary.toArgb()
                                            paint.textSize = (if (isSmallScreen) 56.sp else if (isLargeScreen) 86.sp else 76.sp).toPx()
                                            paint.setShadowLayer(120f, 0f, 0f, colors.textPrimary.copy(alpha = 0.9f).toArgb())
                                            paint.textAlign = android.graphics.Paint.Align.CENTER

                                            canvas.nativeCanvas.drawText(
                                                "GAMA",
                                                size.width / 2,
                                                size.height / 2 + (if (isSmallScreen) 14.sp else 20.sp).toPx(),
                                                paint
                                            )
                                        }
                                    }
                                )

                            }

                            // Subtitle
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
                            ) {
                                Text(
                                    text = "GAMA is standing by and awaiting your command.",
                                    color = colors.textSecondary,
                                    fontSize = if (isSmallScreen) 15.sp else 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Text(
                                    text = "WHAT'S NEXT?",
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 2.sp,
                                    fontFamily = quicksandFontFamily
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 30.dp else 40.dp))

                    // Center box content
                    AnimatedElement(
                        visible = isVisible,
                        delay = 150
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = colors.border,
                                    shape = RoundedCornerShape(18.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isSmallScreen) 20.dp else 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 10.dp else 12.dp)
                            ) {
                                Text(
                                    text = "CURRENT RENDERER",
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 2.sp,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    text = currentRenderer,
                                    color = colors.textPrimary,
                                    fontSize = if (isSmallScreen) 18.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center
                                )

                                // Command output
                                AnimatedVisibility(
                                    visible = commandOutput.isNotEmpty(),
                                    enter = fadeIn(animationSpec = tween(400)) +
                                            expandVertically(animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )),
                                    exit = fadeOut(animationSpec = tween(300)) +
                                            shrinkVertically(animationSpec = tween(300))
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            colors.border,
                                                            Color.Transparent
                                                        )
                                                    )
                                                )
                                        )

                                        Text(
                                            text = commandOutput,
                                            color = colors.textPrimary.copy(alpha = 0.8f),
                                            fontSize = if (isSmallScreen) 14.sp else 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = quicksandFontFamily,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 16.dp else 20.dp))

                    // Action buttons
                    AnimatedElement(
                        visible = isVisible,
                        delay = 300
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val buttonsEnabled = shizukuRunning && shizukuPermissionGranted

                            FlatButton(
                                text = "Vulkan",
                                onClick = {
                                    if (buttonsEnabled) {
                                        performHaptic()
                                        pendingRendererSwitch = {
                                            ShizukuHelper.runVulkan(context, scope) { status ->
                                                commandOutput = status
                                                scope.launch {
                                                    delay(3000)
                                                    commandOutput = ""
                                                    currentRenderer = ShizukuHelper.getCurrentRenderer()
                                                }
                                            }
                                        }
                                        showWarningDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                enabled = buttonsEnabled && !anyPanelOpen,
                                colors = colors,
                                maxLines = 1
                            )

                            FlatButton(
                                text = "OpenGL",
                                onClick = {
                                    if (buttonsEnabled) {
                                        performHaptic()
                                        pendingRendererSwitch = {
                                            ShizukuHelper.runOpenGL(context, scope) { status ->
                                                commandOutput = status
                                                scope.launch {
                                                    delay(3000)
                                                    commandOutput = ""
                                                    currentRenderer = ShizukuHelper.getCurrentRenderer()
                                                }
                                            }
                                        }
                                        showWarningDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                enabled = buttonsEnabled && !anyPanelOpen,
                                colors = colors,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 16.dp else 20.dp))

                    // Shizuku Status Card
                    AnimatedElement(
                        visible = isVisible,
                        delay = 450
                    ) {
                        val borderColor = if (shizukuRunning && shizukuPermissionGranted) {
                            colors.successColor
                        } else {
                            colors.errorColor
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = borderColor.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(18.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(if (isSmallScreen) 20.dp else 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shizukuStatus,
                                    color = colors.textPrimary,
                                    fontSize = if (isSmallScreen) 16.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 16.dp else 20.dp))

                    // GitHub Button
                    AnimatedElement(
                        visible = isVisible,
                        delay = 600
                    ) {
                        FlatButton(
                            text = "View on GitHub",
                            maxLines = 1,
                            onClick = {
                                if (!anyPanelOpen) {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showGitHubDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            accent = true,
                            enabled = !anyPanelOpen,
                            colors = colors
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isSmallScreen) 30.dp else 40.dp))

                    // Version info
                    AnimatedElement(
                        visible = isVisible,
                        delay = 750
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Version $currentVersion",
                                color = colors.textSecondary.copy(alpha = 0.7f),
                                fontSize = if (isSmallScreen) 14.sp else 16.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = quicksandFontFamily
                            )
                            Text(
                                text = versionStatus,
                                color = colors.primaryAccent.copy(alpha = 0.6f),
                                fontSize = if (isSmallScreen) 13.sp else 15.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = quicksandFontFamily,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Dark overlay (when blur disabled)
            AnimatedVisibility(
                visible = overlayAlpha > 0f,
                enter = fadeIn(animationSpec = if (animationLevel == 2) snap() else tween(animDuration, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = if (animationLevel == 2) snap() else tween(animDuration, easing = FastOutSlowInEasing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isDarkTheme) {
                                Color.Black.copy(alpha = overlayAlpha)
                            } else {
                                Color.White.copy(alpha = overlayAlpha)
                            }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block interactions */ }
                        }
                )
            }

            // Warning Dialog
            BouncyDialog(
                visible = showWarningDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWarningDialog = false
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                        .widthIn(max = if (isLandscape && !isTablet) 600.dp else 500.dp)
                        .border(
                            width = 1.dp,
                            color = colors.border,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isSmallScreen) 24.dp else 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
                    ) {
                        // Info icon
                        Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                            drawCircle(
                                color = colors.primaryAccent,
                                radius = size.minDimension / 2,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            drawCircle(
                                color = colors.primaryAccent,
                                radius = 3.dp.toPx(),
                                center = Offset(size.width / 2, size.height * 0.35f)
                            )

                            drawLine(
                                color = colors.primaryAccent,
                                start = Offset(size.width / 2, size.height * 0.45f),
                                end = Offset(size.width / 2, size.height * 0.70f),
                                strokeWidth = 4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        Text(
                            text = "This will briefly restart your system UI to apply the changes. Everything will be back to normal in just a moment.",
                            maxLines = 3,
                            fontSize = if (isSmallScreen) 17.sp else 19.sp,
                            lineHeight = if (isSmallScreen) 24.sp else 28.sp,
                            color = colors.textPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DialogButton(
                                text = "Cancel",
                                onClick = {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showWarningDialog = false
                                    pendingRendererSwitch = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                colors = colors,
                                cardBackground = cardBackground
                            )

                            DialogButton(
                                text = "Continue",
                                onClick = {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showWarningDialog = false
                                    pendingRendererSwitch?.invoke()
                                    pendingRendererSwitch = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                colors = colors,
                                cardBackground = cardBackground
                            )
                        }
                    }
                }
            }

            // GitHub Dialog
            BouncyDialog(
                visible = showGitHubDialog,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showGitHubDialog = false
                }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                        .widthIn(max = if (isLandscape && !isTablet) 600.dp else 500.dp)
                        .border(
                            width = 1.dp,
                            color = colors.border,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (isSmallScreen) 24.dp else 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
                    ) {
                        // GitHub icon
                        Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radius = size.minDimension / 2.5f

                            drawCircle(
                                color = colors.primaryAccent,
                                radius = radius,
                                center = center,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            drawLine(
                                color = colors.primaryAccent,
                                start = Offset(size.width / 2, center.y - radius),
                                end = Offset(size.width / 2, center.y + radius),
                                strokeWidth = 2.dp.toPx()
                            )

                            drawLine(
                                color = colors.primaryAccent,
                                start = Offset(center.x - radius, size.height / 2),
                                end = Offset(center.x + radius, size.height / 2),
                                strokeWidth = 2.dp.toPx()
                            )

                            drawArc(
                                color = colors.primaryAccent,
                                startAngle = -90f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius * 0.5f, center.y - radius),
                                size = Size(radius, radius * 2),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawArc(
                                color = colors.primaryAccent,
                                startAngle = 90f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius * 0.5f, center.y - radius),
                                size = Size(radius, radius * 2),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        Text(
                            text = "Would you like to check out the GAMA repository on GitHub?",
                            maxLines = 2,
                            fontSize = if (isSmallScreen) 17.sp else 19.sp,
                            lineHeight = if (isSmallScreen) 24.sp else 28.sp,
                            color = colors.textPrimary.copy(alpha = 0.9f),
                            modifier = Modifier.fillMaxWidth(),
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DialogButton(
                                text = "Not Now",
                                onClick = {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showGitHubDialog = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                colors = colors,
                                cardBackground = cardBackground
                            )

                            DialogButton(
                                text = "Open GitHub",
                                onClick = {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/popovicialinc/gama"))
                                    context.startActivity(intent)
                                    showGitHubDialog = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 80.dp),
                                colors = colors,
                                cardBackground = cardBackground
                            )
                        }
                    }
                }
            }

            // Settings button (bottom-left) - zoom/fade out when panels active
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
            ) {
                val settingsButtonVisible = !anyPanelOpen

                val settingsScale by animateFloatAsState(
                    targetValue = if (settingsButtonVisible) 1f else 0.7f,
                    animationSpec = if (animationLevel == 2) snap() else spring(
                        dampingRatio = 0.3f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "settings_scale"
                )

                val settingsAlpha by animateFloatAsState(
                    targetValue = if (settingsButtonVisible) 1f else 0f,
                    animationSpec = if (animationLevel == 2) snap() else tween(300),
                    label = "settings_alpha"
                )

                if (settingsAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .size(if (isSmallScreen) 52.dp else 56.dp)
                            .scale(settingsScale)
                            .alpha(settingsAlpha)
                            .background(
                                color = colors.primaryAccent.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = colors.border,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showSettings = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(if (isSmallScreen) 28.dp else 32.dp)) {
                            val dotRadius = 3.dp.toPx()
                            val spacing = size.height / 4
                            val centerX = size.width / 2

                            drawCircle(
                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                radius = dotRadius,
                                center = Offset(centerX, spacing)
                            )
                            drawCircle(
                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                radius = dotRadius,
                                center = Offset(centerX, size.height / 2)
                            )
                            drawCircle(
                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                radius = dotRadius,
                                center = Offset(centerX, size.height - spacing)
                            )
                        }
                    }
                }
            }

            // Settings Menu
            BouncyDialog(
                visible = showSettings && !showVisualEffects,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSettings = false
                },
                fullScreen = true
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block taps */ }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape && !isTablet) 0.7f else 0.9f)
                            .widthIn(max = if (isLandscape && !isTablet) 600.dp else 500.dp)
                            .fillMaxHeight(if (isLandscape && !isTablet) 0.85f else 0.9f)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isSmallScreen) 20.dp else if (isLandscape && !isTablet) 16.dp else 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else if (isLandscape && !isTablet) 16.dp else 30.dp)
                        ) {
                            // Settings title
                            CleanTitle(
                                text = "SETTINGS",
                                fontSize = if (isSmallScreen) 44.sp else 56.sp,
                                colors = colors
                            )

                            // Visual Effects Button
                            Card(
                                onClick = {
                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                    showVisualEffects = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = colors.border,
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                colors = CardDefaults.cardColors(containerColor = cardBackground),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(if (isSmallScreen) 20.dp else 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "VISUAL EFFECTS",
                                            color = colors.primaryAccent.copy(alpha = 0.7f),
                                            fontSize = if (isSmallScreen) 12.sp else 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 2.sp,
                                            fontFamily = quicksandFontFamily
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Configure visual effects",
                                            color = colors.textSecondary,
                                            fontSize = if (isSmallScreen) 14.sp else 16.sp,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }

                                    // Arrow
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        val arrowPath = Path().apply {
                                            moveTo(size.width * 0.3f, size.height * 0.3f)
                                            lineTo(size.width * 0.7f, size.height * 0.5f)
                                            lineTo(size.width * 0.3f, size.height * 0.7f)
                                        }
                                        drawPath(
                                            path = arrowPath,
                                            color = colors.primaryAccent.copy(alpha = 0.7f),
                                            style = Stroke(
                                                width = 2.dp.toPx(),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                            }

                            // Theme Selector (Updated with sliding logic)
                            ThemeSelector(
                                currentTheme = themePreference,
                                onThemeChange = { newTheme ->
                                    performHaptic()
                                    themePreference = newTheme
                                    savePreferences()
                                },
                                colors = colors,
                                cardBackground = cardBackground,
                                isSmallScreen = isSmallScreen,
                                performHaptic = { performHaptic() }
                            )

                            // Animation Quality (Updated with sliding logic)
                            AnimationQualitySelector(
                                currentLevel = animationLevel,
                                onLevelChange = { newLevel ->
                                    performHaptic()
                                    animationLevel = newLevel
                                    savePreferences()
                                },
                                colors = colors,
                                cardBackground = cardBackground,
                                isSmallScreen = isSmallScreen,
                                performHaptic = { performHaptic() }
                            )

                            Spacer(modifier = Modifier.height(if (isSmallScreen) 10.dp else 20.dp))
                        }
                    } // End centering Box

                    // Back arrow button (bottom-left)
                    BackArrowButton(
                        onClick = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showSettings = false
                        },
                        colors = colors,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
                    )
                }
            }

            // Visual Effects Submenu
            BouncyDialog(
                visible = showVisualEffects,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVisualEffects = false
                },
                fullScreen = true
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block taps */ }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape && !isTablet) 0.7f else 0.9f)
                            .widthIn(max = if (isLandscape && !isTablet) 600.dp else 500.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else if (isLandscape && !isTablet) 16.dp else 30.dp)
                    ) {
                        CleanTitle(
                            text = "VISUAL EFFECTS",
                            fontSize = if (isSmallScreen) 36.sp else 44.sp,
                            colors = colors
                        )

                        // Blur Toggle
                        ToggleCard(
                            title = "BLUR",
                            description = "Background blur effect",
                            checked = blurEnabled,
                            onCheckedChange = {
                                performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                blurEnabled = it
                                savePreferences()
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen
                        )

                        // Gradient Toggle
                        ToggleCard(
                            title = "GRADIENT",
                            description = "Breathing gradient background",
                            checked = gradientEnabled,
                            onCheckedChange = {
                                performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                gradientEnabled = it
                                savePreferences()
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen
                        )

                        Spacer(modifier = Modifier.height(if (isSmallScreen) 10.dp else 20.dp))
                    }

                    // Back arrow
                    BackArrowButton(
                        onClick = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showVisualEffects = false
                        },
                        colors = colors,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanTitle(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    colors: ThemeColors
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        fontFamily = quicksandFontFamily,
        color = colors.textPrimary,
        modifier = Modifier.drawWithContent {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint()
                paint.color = colors.textPrimary.toArgb()
                paint.textSize = fontSize.toPx()
                paint.setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                paint.textAlign = android.graphics.Paint.Align.CENTER

                canvas.nativeCanvas.drawText(
                    text,
                    size.width / 2,
                    size.height / 2 + (fontSize.value * 0.25f).sp.toPx(),
                    paint
                )
            }
        }
    )
}

@Composable
private fun ThemeSelector(
    currentTheme: Int,
    onThemeChange: (Int) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    performHaptic: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(18.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 20.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
        ) {
            Text(
                text = "THEME",
                color = colors.primaryAccent.copy(alpha = 0.7f),
                fontSize = if (isSmallScreen) 12.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                fontFamily = quicksandFontFamily
            )

            // 3 INDIVIDUAL BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeButton(
                    text = "Auto",
                    isSelected = currentTheme == 0,
                    onClick = {
                        performHaptic()
                        onThemeChange(0)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
                ThemeButton(
                    text = "Dark",
                    isSelected = currentTheme == 1,
                    onClick = {
                        performHaptic()
                        onThemeChange(1)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
                ThemeButton(
                    text = "Light",
                    isSelected = currentTheme == 2,
                    onClick = {
                        performHaptic()
                        onThemeChange(2)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimationQualitySelector(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    performHaptic: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(18.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 20.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
        ) {
            Text(
                text = "ANIMATION QUALITY",
                color = colors.primaryAccent.copy(alpha = 0.7f),
                fontSize = if (isSmallScreen) 12.sp else 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                fontFamily = quicksandFontFamily
            )

            // 3 INDIVIDUAL BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeButton(
                    text = "None",
                    isSelected = currentLevel == 2,
                    onClick = {
                        performHaptic()
                        onLevelChange(2)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
                ThemeButton(
                    text = "Medium",
                    isSelected = currentLevel == 1,
                    onClick = {
                        performHaptic()
                        onLevelChange(1)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
                ThemeButton(
                    text = "Max",
                    isSelected = currentLevel == 0,
                    onClick = {
                        performHaptic()
                        onLevelChange(0)
                    },
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 80.dp)
                )
            }
        }
    }
}



@Composable
private fun ThemeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = if (animLevel == 2) {
            snap()
        } else {
            spring(
                dampingRatio = 0.3f,
                stiffness = Spring.StiffnessMedium
            )
        },
        finishedListener = { isPressed = false },
        label = "theme_button_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.primaryAccent else colors.textSecondary.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "theme_button_bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
        } else {
            colors.textPrimary.copy(alpha = 0.6f)
        },
        animationSpec = tween(200),
        label = "theme_button_text"
    )

    Button(
        onClick = {
            if (animLevel != 2) isPressed = true
            onClick()
        },
        modifier = modifier
            .height(if (isSmallScreen) 48.dp else 52.dp)
            .scale(scale)
            .then(
                if (!isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.border,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (isSmallScreen) 14.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = quicksandFontFamily
        )
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(18.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 20.dp else 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    color = colors.primaryAccent.copy(alpha = 0.7f),
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    fontFamily = quicksandFontFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = if (isSmallScreen) 14.sp else 16.sp,
                    fontFamily = quicksandFontFamily
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.primaryAccent,
                    uncheckedThumbColor = colors.textPrimary.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.Gray
                )
            )
        }
    }
}

@Composable
private fun BackArrowButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    Box(
        modifier = modifier
            .size(if (isSmallScreen) 52.dp else 56.dp)
            .background(
                color = colors.primaryAccent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        // Back arrow icon
        Canvas(modifier = Modifier.size(if (isSmallScreen) 28.dp else 32.dp)) {
            val arrowPath = Path().apply {
                // Arrow pointing left
                moveTo(size.width * 0.6f, size.height * 0.3f)
                lineTo(size.width * 0.3f, size.height * 0.5f)
                lineTo(size.width * 0.6f, size.height * 0.7f)
            }
            drawPath(
                path = arrowPath,
                color = colors.primaryAccent.copy(alpha = 0.8f),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ThemeColors,
    cardBackground: Color
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    // Adjusted for less bounce
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f, // Subtle press
        animationSpec = if (animLevel == 2) {
            snap()
        } else {
            spring(
                dampingRatio = 0.3f,
                stiffness = Spring.StiffnessMedium
            )
        },
        finishedListener = { isPressed = false },
        label = "dialog_button_scale"
    )

    Button(
        onClick = {
            if (animLevel != 2) isPressed = true
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cardBackground),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        modifier = modifier
            .scale(scale)
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Text(
            text,
            color = colors.textPrimary,
            fontSize = if (isSmallScreen) 17.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
            fontFamily = quicksandFontFamily
        )
    }
}

@Composable
private fun BouncyDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    fullScreen: Boolean = false,
    content: @Composable () -> Unit
) {
    val animLevel = LocalAnimationLevel.current
    val duration = when (animLevel) {
        0 -> 600
        1 -> 300
        else -> 0
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (animLevel == 2) {
            fadeIn(animationSpec = snap()) + scaleIn(initialScale = 1f, animationSpec = snap())
        } else {
            fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = if (fullScreen) 0.95f else 0.85f, // Subtle scale in
                        animationSpec = spring(
                            dampingRatio = 0.3f, // High damping = no wobble
                            stiffness = Spring.StiffnessLow
                        )
                    )
        },
        exit = if (animLevel == 2) {
            fadeOut(animationSpec = snap()) + scaleOut(targetScale = 1f, animationSpec = snap())
        } else {
            fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = if (fullScreen) 0.95f else 0.85f,
                        animationSpec = tween(duration, easing = FastOutSlowInEasing)
                    )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (fullScreen) Alignment.TopStart else Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun AnimatedElement(
    visible: Boolean,
    delay: Int,
    content: @Composable () -> Unit
) {
    val animationLevel = LocalAnimationLevel.current
    val duration = when (animationLevel) {
        0 -> 1000
        1 -> 500
        else -> 0
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animationLevel == 2) snap() else tween(
            durationMillis = duration,
            delayMillis = 0,
            easing = FastOutSlowInEasing
        ),
        label = "alpha_$delay"
    )

    // Tighter spring for elements too
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else if (animationLevel == 2) 1f else 0.8f,
        animationSpec = if (animationLevel == 2) snap() else spring(
            dampingRatio = 0.3f,
            stiffness = Spring.StiffnessLow,
            visibilityThreshold = 0.001f
        ),
        label = "scale_$delay"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .scale(scale)
    ) {
        content()
    }
}

@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    maxLines: Int
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    // Adjusted for less bounce
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f, // Subtle press
        animationSpec = if (animLevel == 2) {
            snap()
        } else {
            spring(
                dampingRatio = 0.3f,
                stiffness = Spring.StiffnessMedium
            )
        },
        finishedListener = { isPressed = false },
        label = "button_scale"
    )

    val buttonColor = when {
        accent -> colors.primaryAccent
        !enabled -> colors.textSecondary.copy(alpha = 0.2f)
        else -> colors.cardBackground
    }

    val textColor = when {
        accent && colors == ThemeColors.dark() -> Color.Black
        accent && colors == ThemeColors.light() -> Color.White
        !enabled -> colors.textSecondary.copy(alpha = 0.3f)
        else -> colors.textPrimary
    }

    Button(
        onClick = {
            if (enabled && animLevel != 2) isPressed = true
            if (enabled) onClick()
        },
        modifier = modifier
            .height(if (isSmallScreen) 54.dp else 58.dp)
            .scale(scale)
            .then(
                if (!accent) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (enabled) colors.border else colors.textSecondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        enabled = enabled
    ) {
        Text(
            text = text,
            fontSize = if (isSmallScreen) 18.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontFamily = quicksandFontFamily,
            maxLines = maxLines
        )
    }
}
