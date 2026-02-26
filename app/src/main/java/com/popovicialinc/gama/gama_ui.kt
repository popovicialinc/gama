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


// Custom fonts
// Custom fonts - with safe fallback
// Only quicksand_bold exists in res/font. All weights map to it; the OS will
// synthesise lighter weights when needed rather than crashing on a missing resource.
private val quicksandFontFamily = try {
    FontFamily(
        Font(R.font.quicksand_bold, FontWeight.Normal),
        Font(R.font.quicksand_bold, FontWeight.SemiBold),
        Font(R.font.quicksand_bold, FontWeight.Bold),
        Font(R.font.quicksand_bold, FontWeight.ExtraBold)
    )
} catch (e: Exception) {
    FontFamily.Default
}

// ============================================================================
// ADAPTIVE TYPE SYSTEM
// ============================================================================
//
// Single source of truth for font sizing.  Every call site uses adaptiveSp()
// instead of raw hard-coded values so text scales with:
//   • Screen width / height (phones vs tablets vs foldables)
//   • System font-scale preference (accessibility)
//   • Orientation (landscape reduces available vertical space)
//   • DPI class (ldpi / mdpi / hdpi / xhdpi / …)
//
// Usage:
//   val ts = rememberAdaptiveType()
//   fontSize = ts.body           // or ts.label, ts.title, etc.
//
// The scale factor is kept in [0.82, 1.20] so nothing is ever unreadably
// small or comically large regardless of device/setting combination.

data class AdaptiveTypeScale(
    // Panel / dialog titles  (e.g. "SETTINGS", "RESOURCES")
    val displayLarge:  TextUnit,   // ~40–48 sp at normal density
    val displayMedium: TextUnit,   // ~32–40 sp
    val displaySmall:  TextUnit,   // ~26–34 sp

    // Section headings inside panels
    val headlineLarge:  TextUnit,  // ~22–28 sp
    val headlineMedium: TextUnit,  // ~20–24 sp
    val headlineSmall:  TextUnit,  // ~17–21 sp

    // Body / list content
    val bodyLarge:   TextUnit,     // ~15–18 sp
    val bodyMedium:  TextUnit,     // ~13–16 sp
    val bodySmall:   TextUnit,     // ~12–14 sp

    // Buttons
    val buttonLarge:  TextUnit,    // ~18–22 sp
    val buttonMedium: TextUnit,    // ~16–18 sp

    // Labels / captions / badges
    val labelLarge:  TextUnit,     // ~13–15 sp
    val labelMedium: TextUnit,     // ~12–13 sp
    val labelSmall:  TextUnit,     // ~11–12 sp
)

@Composable
fun rememberAdaptiveType(): AdaptiveTypeScale {
    val configuration = LocalConfiguration.current
    val density       = LocalDensity.current

    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.fontScale,
        configuration.densityDpi,
        configuration.orientation
    ) {
        val widthDp  = configuration.screenWidthDp.toFloat()
        val heightDp = configuration.screenHeightDp.toFloat()
        val fontScale = configuration.fontScale          // user accessibility pref
        val dpi       = configuration.densityDpi         // physical DPI
        val isLandscape = widthDp > heightDp
        val isTablet    = widthDp >= 600f && heightDp >= 600f

        // ── Layout scale: how much space we actually have ────────────────────
        // Normalised against a "reference" 400 dp wide portrait phone.
        // Tablets get a gentle boost; landscape phones get a mild reduction
        // because vertical space is tight and we don't want giant labels.
        val layoutScale = when {
            isTablet   -> (widthDp / 720f).coerceIn(0.92f, 1.25f)
            isLandscape -> (widthDp / 640f).coerceIn(0.80f, 1.05f)
            else        -> (widthDp / 400f).coerceIn(0.85f, 1.18f)
        }

        // ── DPI nudge: very high-DPI screens pack more pixels per dp so text
        // already looks sharp; very low-DPI screens need a touch more size ──
        val dpiNudge = when {
            dpi >= 560 -> 0.96f   // xxxhdpi — already crisp
            dpi >= 420 -> 0.98f   // xxhdpi
            dpi >= 280 -> 1.00f   // xhdpi / hdpi — reference
            dpi >= 200 -> 1.02f   // mdpi
            else       -> 1.05f   // ldpi — needs help
        }

        // ── Font scale: the user may have set a large/small system font.
        // We apply it at 0.75 weight so GAMA's layout stays stable while still
        // honouring large-font accessibility preferences more faithfully.
        // Users who set fontScale ≥ 1.5–1.8× for low-vision reasons previously
        // received text that was up to 30% smaller than their OS preference intended;
        // 0.75 weight closes roughly half of that gap without breaking the layout.
        val accessibilityWeight = 0.75f
        val accessibilityScale = 1f + (fontScale - 1f) * accessibilityWeight

        // ── Combined multiplier, clamped.
        // The ceiling is widened from 1.25 → 1.35 so that the accessibility scale
        // of a fontScale=1.8 user (accessibilityScale ≈ 1.60) can reach 1.35 rather
        // than being hard-capped at 1.25 on a reference-density phone.
        val m = (layoutScale * dpiNudge * accessibilityScale).coerceIn(0.85f, 1.35f)

        fun Float.s() = (this * m).coerceIn(8f, 72f).sp

        AdaptiveTypeScale(
            displayLarge   = 50f.s(),
            displayMedium  = 44f.s(),
            displaySmall   = 37f.s(),

            headlineLarge  = 28f.s(),
            headlineMedium = 24f.s(),
            headlineSmall  = 21f.s(),

            bodyLarge      = 18f.s(),
            bodyMedium     = 16f.s(),
            bodySmall      = 14f.s(),

            buttonLarge    = 21f.s(),
            buttonMedium   = 17f.s(),

            labelLarge     = 15f.s(),
            labelMedium    = 13f.s(),
            labelSmall     = 12f.s(),
        )
    }
}

// ============================================================================
// PREMIUM ANIMATION SYSTEM - Motion Design Tokens
// ============================================================================

object MotionTokens {
    object Duration {
        const val instant = 0
        const val flash = 150
        const val quick = 300
        const val brief = 450
        const val short = 600
        const val snappy = 750
        const val medium = 900
        const val moderate = 1050
        const val balanced = 1200
        const val leisurely = 1350
        const val long = 1500
        const val extended = 1800
        const val expansive = 2100
        const val dramatic = 2400
        const val epic = 3000
    }

    object Easing {
        val emphasized = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        val emphasizedDecelerate = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)
        val emphasizedAccelerate = CubicBezierEasing(0.4f, 0.0f, 0.6f, 0.3f)
        val silk = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)
        val butter = CubicBezierEasing(0.45f, 0.0f, 0.15f, 1.0f)
        val velvet = CubicBezierEasing(0.37f, 0.0f, 0.63f, 1.0f)
    }

    object Springs {
        data class SpringConfig(val dampingRatio: Float, val stiffness: Float)

        val silk       = SpringConfig(0.8f,  300f)
        val smooth     = SpringConfig(0.75f, 400f)
        val gentle     = SpringConfig(0.7f,  500f)
        val balanced   = SpringConfig(0.6f,  600f)
        val responsive = SpringConfig(0.55f, 700f)
        val playful    = SpringConfig(0.5f,  250f)
        val snappy     = SpringConfig(0.85f, 1000f)
        // Press-down: immediate, no bounce. Release: overshoots past rest, settles with one bounce.
        val pressDown  = SpringConfig(0.9f,  1400f)   // crisp snap to pressed state
        val pressUp    = SpringConfig(0.48f,  480f)   // overshoot back past 1.0, one clean bounce
    }

    object Scale {
        const val subtle = 0.97f
        const val mild = 0.95f
        const val moderate = 0.90f
        const val dramatic = 0.85f
    }
}

// Particle system for animated backgrounds
data class ParticleState(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val phase: Float = kotlin.random.Random.nextFloat() * 2f * kotlin.math.PI.toFloat()
) {
    var velocityX = 0f
    var velocityY = 0f

    // Smoothed rotation values
    var smoothRotationX = 0f
    var smoothRotationY = 0f

    fun update(
        speedMultiplier: Float = 1f,
        time: Long = 0L,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        deltaTime: Float = 0.016f,
        parallaxSensitivity: Float = 0.025f,
        starMode: Boolean = false,
        timeModeEnabled: Boolean = false,
        isDaytime: Boolean = true
    ) {
        // Calculate rotation delta (change from last frame)
        val rawDeltaX = rotationX - smoothRotationX
        val rawDeltaY = rotationY - smoothRotationY

        // Clamp deltas to prevent excessive speed regardless of phone orientation
        // This ensures consistent speed whether phone is horizontal, vertical, or anywhere in between
        val maxDelta = 0.1f // Maximum allowed change per frame (significantly reduced for subtlety)
        val deltaRotationX = rawDeltaX.coerceIn(-maxDelta, maxDelta)
        val deltaRotationY = rawDeltaY.coerceIn(-maxDelta, maxDelta)

        // Update smoothed rotation for next frame
        smoothRotationX = rotationX
        smoothRotationY = rotationY

        // Natural forces - ALWAYS USE NORMAL RISING BEHAVIOR
        // Particles always rise from bottom to top with no horizontal drift
        val naturalForceX = 0f // No horizontal drift - all particles move straight up
        val naturalForceY = -0.01f * speed * speedMultiplier // Negative = upward drift (multiplied by speed multiplier)

        // Parallax influence based on ROTATION CHANGE (not absolute rotation)
        // Only moves particles when device is actively rotating
        // When device stops rotating, particles just rise naturally
        // MODIFIED: Added speedMultiplier to parallax forces to make responsiveness consistent with velocity setting
        // MODIFIED: X axis sensitivity is now 7x (350f) compared to Y axis (50f)
        val parallaxInfluenceX = deltaRotationY * parallaxSensitivity * speed * speedMultiplier * 350f
        val parallaxInfluenceY = -deltaRotationX * parallaxSensitivity * speed * speedMultiplier * 50f

        // Combine all forces
        val totalForceX = naturalForceX + parallaxInfluenceX
        val totalForceY = naturalForceY + parallaxInfluenceY

        // Apply forces to velocity with acceleration
        velocityX += totalForceX * deltaTime * 60f // Normalize for frame rate
        velocityY += totalForceY * deltaTime * 60f

        // Apply damping for smooth, natural movement
        // Balanced damping on both axes for natural drift
        val dampingX = 0.92f  // Lighter damping on X for horizontal drift
        val dampingY = 0.92f  // Lighter damping on Y for smooth upward flow
        velocityX *= dampingX
        velocityY *= dampingY

        // Clamp velocity to prevent excessive speeds
        val maxVelocity = 15f * speedMultiplier
        velocityX = velocityX.coerceIn(-maxVelocity, maxVelocity)
        velocityY = velocityY.coerceIn(-maxVelocity, maxVelocity)

        // Update position
        x += velocityX * deltaTime
        y += velocityY * deltaTime

        // Wrap around screen edges - ALWAYS VERTICAL (bottom to top)
        // Normal mode behavior: wrap vertically (bottom to top)
        when {
            y < -0.1f -> {
                y = 1.1f
                x = kotlin.random.Random.nextFloat()
                velocityX *= 0.5f // Keep some momentum
                velocityY *= 0.5f
            }
            y > 1.1f -> {
                y = -0.1f
                x = kotlin.random.Random.nextFloat()
                velocityX *= 0.5f
                velocityY *= 0.5f
            }
        }

        // Also wrap horizontally to keep particles on screen
        when {
            x < -0.1f -> x = 1.1f
            x > 1.1f -> x = -0.1f
        }
    }
}

// Celestial object state for sun and moon
data class CelestialState(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float
)

// Function to calculate current celestial position based on real time
fun calculateCelestialPosition(screenWidth: Float, screenHeight: Float, timeOffsetHours: Float = 0f, isLandscape: Boolean = false): CelestialState? {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    // Convert time to decimal hours (0.0 - 24.0) and apply offset
    val baseTime = hour + (minute / 60f)
    var currentTime = (baseTime + timeOffsetHours) % 24f
    if (currentTime < 0f) currentTime += 24f

    // Define day and night periods
    val sunriseStart = 6.0f  // 6:00 AM - sun starts rising
    val sunriseEnd = 7.0f    // 7:00 AM - sun fully risen
    val sunsetStart = 18.0f  // 6:00 PM - sun starts setting
    val sunsetEnd = 19.0f    // 7:00 PM - sun fully set

    val moonriseStart = 19.0f  // 7:00 PM - moon starts rising
    val moonriseEnd = 20.0f    // 8:00 PM - moon fully risen
    val moonsetStart = 5.0f    // 5:00 AM - moon starts setting
    val moonsetEnd = 6.0f      // 6:00 AM - moon fully set

    // NEW TRAJECTORY: Arc matching the star pattern from reference image
    // Wider horizontal spread (almost edge to edge)
    // Higher peak in the center
    // Lower endpoints near the horizon

    // Calculate position and alpha based on time
    return when {
        // Daytime - Sun visible (7 AM to 6 PM)
        currentTime >= sunriseEnd && currentTime < sunsetStart -> {
            // Sun moves from left (7 AM) to center (12:30 PM) to right (6 PM)
            val dayDuration = sunsetStart - sunriseEnd // 11 hours
            val progress = (currentTime - sunriseEnd) / dayDuration

            // Wide arc matching the star pattern: endpoints closer to screen edges
            val startX = screenWidth * 0.08f  // Much closer to left edge
            val endX = screenWidth * 0.92f    // Much closer to right edge
            val lowestY = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f
            val highestY = if (isLandscape) screenHeight * 0.12f else screenHeight * 0.08f

            // Calculate X position (linear interpolation)
            val x = startX + (endX - startX) * progress

            // Calculate Y position (smooth parabolic arc)
            // Use sine for smoother arc shape matching the star pattern
            val arcProgress = sin(progress * PI.toFloat())
            val y = lowestY - (lowestY - highestY) * arcProgress

            CelestialState(
                x = x,
                y = y,
                size = 48f,  // Slightly larger sun
                alpha = 0.75f  // More visible
            )
        }

        // Sunrise transition (6 AM to 7 AM)
        currentTime >= sunriseStart && currentTime < sunriseEnd -> {
            val progress = (currentTime - sunriseStart) / (sunriseEnd - sunriseStart)
            val x = screenWidth * 0.08f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 48f,
                alpha = 0.75f * progress // Fade in
            )
        }

        // Sunset transition (6 PM to 7 PM)
        currentTime >= sunsetStart && currentTime < sunsetEnd -> {
            val progress = (currentTime - sunsetStart) / (sunsetEnd - sunsetStart)
            val x = screenWidth * 0.92f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 48f,
                alpha = 0.75f * (1f - progress) // Fade out
            )
        }

        // Nighttime - Moon visible (8 PM to 5 AM)
        currentTime >= moonriseEnd || currentTime < moonsetStart -> {
            // Normalize time to 0-9 hour range (8 PM = 0, 5 AM = 9)
            val normalizedTime = if (currentTime >= moonriseEnd) {
                currentTime - moonriseEnd
            } else {
                currentTime + (24f - moonriseEnd)
            }

            val nightDuration = (24f - moonriseEnd) + moonsetStart // 9 hours total
            val progress = normalizedTime / nightDuration

            // Moon follows same wide arc pattern as sun
            val startX = screenWidth * 0.08f
            val endX = screenWidth * 0.92f
            val lowestY = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f
            val highestY = if (isLandscape) screenHeight * 0.12f else screenHeight * 0.08f

            val x = startX + (endX - startX) * progress

            val arcProgress = sin(progress * PI.toFloat())
            val y = lowestY - (lowestY - highestY) * arcProgress

            CelestialState(
                x = x,
                y = y,
                size = 42f,
                alpha = 0.65f  // Slightly more visible
            )
        }

        // Moonrise transition (7 PM to 8 PM)
        currentTime >= moonriseStart && currentTime < moonriseEnd -> {
            val progress = (currentTime - moonriseStart) / (moonriseEnd - moonriseStart)
            val x = screenWidth * 0.08f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 42f,
                alpha = 0.65f * progress // Fade in
            )
        }

        // Moonset transition (5 AM to 6 AM)
        currentTime >= moonsetStart && currentTime < moonsetEnd -> {
            val progress = (currentTime - moonsetStart) / (moonsetEnd - moonsetStart)
            val x = screenWidth * 0.92f
            val y = if (isLandscape) screenHeight * 0.30f else screenHeight * 0.25f

            CelestialState(
                x = x,
                y = y,
                size = 42f,
                alpha = 0.65f * (1f - progress) // Fade out
            )
        }

        else -> null
    }
}

// Helper function to determine if it's daytime
fun isDaytime(timeOffsetHours: Float = 0f): Boolean {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    val baseTime = hour + (minute / 60f)
    var currentTime = (baseTime + timeOffsetHours) % 24f
    if (currentTime < 0f) currentTime += 24f
    return currentTime >= 7f && currentTime < 19f // 7 AM to 7 PM
}

// Standalone Particles Overlay Component
@Composable
fun ParticlesOverlay(
    enabled: Boolean,
    color: Color,
    particleSpeed: Int = 1, // 0=low, 1=medium, 2=high
    parallaxEnabled: Boolean = true,
    particleCount: Int = 1, // 0=low(75), 1=medium(150), 2=high(300), 3=custom
    particleCountCustom: Int = 150,
    parallaxSensitivity: Float = 0.025f, // New parameter for sensitivity (0.0 to 1.0) - reduced for subtlety
    starMode: Boolean = false, // Star mode toggle
    timeModeEnabled: Boolean = false, // Time-based sun & moon system
    timeOffsetHours: Float = 0f, // Developer: hours to add to current time
    anyPanelOpen: Boolean = false, // Hide celestials when panels are open
    isLandscape: Boolean = false // NEW: Constrain celestials to left half in landscape
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Calculate screen width in pixels for cloud updates
    val screenWidthPx = remember(configuration) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }

    // Calculate actual particle count based on setting
    val actualParticleCount = when (particleCount) {
        0 -> 75  // Low
        1 -> 150 // Medium
        2 -> 300 // High
        3 -> particleCountCustom.coerceIn(1, 500) // Custom (clamped to 1-500)
        else -> 150
    }

    val particles = remember(actualParticleCount, particleSpeed, parallaxSensitivity) {
        // Create particles distributed across the entire screen for initial display
        val particleList = List(actualParticleCount) {
            ParticleState(
                x = kotlin.random.Random.nextFloat(), // Fully random X: 0.0 to 1.0
                y = kotlin.random.Random.nextFloat(), // Fully random Y: 0.0 to 1.0
                // Bigger particles: up from 4f max to 7f max
                size = kotlin.random.Random.nextFloat() * 6f + 1f,
                // Higher base speed for visible movement
                speed = kotlin.random.Random.nextFloat() * 1.2f + 0.3f, // 0.3 to 1.5
                // Higher alpha range
                alpha = kotlin.random.Random.nextFloat() * 0.7f + 0.3f
            )
        }

        // Pre-heat particles: give them initial velocity in both directions
        // This prevents them from starting stationary
        particleList.forEach { particle ->
            // Set initial velocity to move naturally
            val preHeatSpeed = particle.speed * 1.5f // Higher initial speed

            // Initialize with both upward and horizontal movement
            particle.velocityX = (kotlin.random.Random.nextFloat() - 0.5f) * preHeatSpeed * 2.0f // Random horizontal drift
            particle.velocityY = -(preHeatSpeed * 5.0f) // Stronger upward movement
        }

        particleList
    }

    // Track device rotation for parallax
    var rotationX by remember { mutableStateOf(0f) }
    var rotationY by remember { mutableStateOf(0f) }

    // Animated rotation values that smoothly transition when parallax is disabled
    val animatedRotationX by animateFloatAsState(
        targetValue = if (parallaxEnabled) rotationX else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 80f
        ),
        label = "rotation_x"
    )
    val animatedRotationY by animateFloatAsState(
        targetValue = if (parallaxEnabled) rotationY else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 80f
        ),
        label = "rotation_y"
    )

    // Calibration state
    var calibrationComplete by remember { mutableStateOf(false) }
    var initialRotationX by remember { mutableStateOf(0f) }
    var initialRotationY by remember { mutableStateOf(0f) }
    var calibrationSamples by remember { mutableStateOf(0) }

    DisposableEffect(parallaxEnabled) {
        if (!parallaxEnabled) {
            // Reset rotation values when parallax is disabled
            rotationX = 0f
            rotationY = 0f
            // Must return an onDispose result here
            return@DisposableEffect onDispose { }
        }

        // Reset calibration when parallax is enabled/re-enabled
        calibrationComplete = false
        calibrationSamples = 0
        initialRotationX = 0f
        initialRotationY = 0f

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager

        // Try rotation vector first, fall back to accelerometer+magnetometer
        val rotationSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (rotationSensor == null) {
            android.util.Log.w("Parallax", "No rotation sensor available")
            return@DisposableEffect onDispose { }
        }

        val listener = object : android.hardware.SensorEventListener {
            // Low-pass filter for smoothing
            private val alpha = 0.8f
            private var filteredX = 0f
            private var filteredY = 0f

            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                event?.let {
                    try {
                        // Get rotation matrix from rotation vector
                        val rotationMatrix = FloatArray(9)
                        android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, it.values)

                        // Remap coordinates to account for device orientation
                        val remappedMatrix = FloatArray(9)
                        android.hardware.SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            android.hardware.SensorManager.AXIS_X,
                            android.hardware.SensorManager.AXIS_Z,
                            remappedMatrix
                        )

                        // Get orientation angles
                        val orientation = FloatArray(3)
                        android.hardware.SensorManager.getOrientation(remappedMatrix, orientation)

                        // Extract pitch (forward/back tilt) and roll (left/right tilt)
                        // Convert radians to degrees and scale
                        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat() // Forward/back
                        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()  // Left/right

                        // Apply low-pass filter to reduce jitter
                        filteredX = alpha * filteredX + (1 - alpha) * pitch
                        filteredY = alpha * filteredY + (1 - alpha) * roll

                        // Calibration: collect samples for stable initial position
                        if (!calibrationComplete) {
                            if (calibrationSamples < 10) {
                                initialRotationX += filteredX
                                initialRotationY += filteredY
                                calibrationSamples++
                            } else {
                                initialRotationX /= 10f
                                initialRotationY /= 10f
                                calibrationComplete = true
                                android.util.Log.d("Parallax", "Calibration complete: X=$initialRotationX, Y=$initialRotationY")
                            }
                        }

                        if (calibrationComplete) {
                            // Calculate delta from calibrated position
                            val deltaX = filteredX - initialRotationX
                            val deltaY = filteredY - initialRotationY

                            // Direct linear mapping with gentle scaling
                            // No special handling for different angles - consistent response throughout
                            // Scale to a smaller range for subtle effect
                            rotationX = deltaX * 0.15f  // Simple linear scaling
                            rotationY = deltaY * 0.15f  // Simple linear scaling
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Parallax", "Sensor error", e)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {
                // No-op
            }
        }

        // Register sensor with GAME delay for balance of responsiveness and battery
        sensorManager?.registerListener(
            listener,
            rotationSensor,
            android.hardware.SensorManager.SENSOR_DELAY_GAME
        )

        onDispose {
            sensorManager?.unregisterListener(listener)
            calibrationComplete = false
            calibrationSamples = 0
        }
    }


    // Animated alpha for fade in/out
    // Particles stay visible when panels are open (only fade if disabled)
    val particleAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = MotionTokens.Duration.expansive,
            easing = MotionTokens.Easing.silk
        ),
        label = "particle_fade"
    )

    // Independent Animation Loop
    var trigger by remember { mutableStateOf(0L) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    // Convert speed setting to multiplier
    // MODIFIED: 3x Velocity Multipliers across the board
    val speedMultiplier = when (particleSpeed) {
        0 -> 1.5f   // Low (was 0.5)
        1 -> 3.0f   // Medium (was 1.0)
        2 -> 4.5f   // High (was 1.5)
        else -> 3.0f
    }

    LaunchedEffect(particles, enabled) {
        if (!enabled) return@LaunchedEffect

        lastFrameTime = 0L // Reset frame time when restarting

        // `while (isActive)` is the correct cancellation guard here.
        // `withFrameNanos` suspends until the next Choreographer frame; it is not
        // itself cancellable mid-suspension, but the coroutine will see the
        // cancellation on the very next `isActive` check and exit cleanly.
        // The `withContext(Dispatchers.Default)` block is cooperative: if the
        // parent scope is cancelled it throws CancellationException and the loop
        // terminates without leaking the physics work.
        while (isActive) {
            // ── Frame callback: stays minimal on the main thread ──────────────────
            // We snapshot the current time + all inputs, then immediately return so
            // the choreographer can schedule the next frame without delay.
            var frameTime = 0L
            var frameDelta = 0.016f
            var frameRotX = 0f
            var frameRotY = 0f
            var frameSens = 0f
            var frameDaytime = true

            withFrameNanos { time ->
                frameDelta = if (lastFrameTime > 0L) {
                    ((time - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
                } else {
                    0.016f
                }
                lastFrameTime = time
                frameTime = time
                frameRotX = if (parallaxEnabled) animatedRotationX else 0f
                frameRotY = if (parallaxEnabled) animatedRotationY else 0f
                frameSens = if (parallaxEnabled) parallaxSensitivity else 0f
                frameDaytime = isDaytime(timeOffsetHours)
                trigger = time // Trigger Canvas redraw
            }

            // ── Physics: runs on a background thread so main thread stays free ───
            // This means panel opens / heavy compositions never steal frame budget.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                particles.forEach {
                    it.update(speedMultiplier, frameTime, frameRotX, frameRotY,
                        frameDelta, frameSens, starMode, timeModeEnabled, frameDaytime)
                }
            }
        }
    }

    // Animated alpha for celestial objects (sun/moon) with smooth ease-in-out fade in/out
    // Hide celestials when panels are open, but keep particles visible
    val celestialAlpha by animateFloatAsState(
        targetValue = if (timeModeEnabled && !anyPanelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
        label = "celestial_alpha"
    )

    if (particleAlpha > 0.01f) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(particleAlpha)
                // Make sure clicks pass through
                .pointerInput(Unit) {}
        ) {
            // Read trigger to ensure redraw on every frame
            val t = trigger

            particles.forEach { particle ->
                // Automatically use star mode if time mode is enabled AND it's nighttime
                val useStars = starMode || (timeModeEnabled && !isDaytime(timeOffsetHours))

                if (useStars) {
                    // Draw star
                    val starCenter = Offset(
                        size.width * particle.x,
                        size.height * particle.y
                    )
                    val starRadius = particle.size * 1.2f
                    val starAlpha = particle.alpha * 0.6f

                    // Draw 4-pointed star (simpler and cleaner)
                    val starPath = Path().apply {
                        // Top point
                        moveTo(starCenter.x, starCenter.y - starRadius)
                        lineTo(starCenter.x + starRadius * 0.25f, starCenter.y - starRadius * 0.25f)
                        // Right point
                        lineTo(starCenter.x + starRadius, starCenter.y)
                        lineTo(starCenter.x + starRadius * 0.25f, starCenter.y + starRadius * 0.25f)
                        // Bottom point
                        lineTo(starCenter.x, starCenter.y + starRadius)
                        lineTo(starCenter.x - starRadius * 0.25f, starCenter.y + starRadius * 0.25f)
                        // Left point
                        lineTo(starCenter.x - starRadius, starCenter.y)
                        lineTo(starCenter.x - starRadius * 0.25f, starCenter.y - starRadius * 0.25f)
                        close()
                    }

                    // Draw star fill
                    drawPath(
                        path = starPath,
                        color = color.copy(alpha = starAlpha)
                    )

                    // Draw center glow
                    drawCircle(
                        color = color.copy(alpha = starAlpha * 0.8f),
                        radius = starRadius * 0.3f,
                        center = starCenter
                    )
                } else {
                    // Draw normal circle particle
                    drawCircle(
                        color = color.copy(alpha = particle.alpha * 0.5f),
                        radius = particle.size,
                        center = Offset(
                            size.width * particle.x,
                            size.height * particle.y
                        )
                    )
                }
            }

            // Draw celestial objects (sun or moon) when time mode is enabled
            if (timeModeEnabled && celestialAlpha > 0.01f) {
                val celestial = calculateCelestialPosition(size.width, size.height, timeOffsetHours)
                celestial?.let { cel ->
                    // In landscape mode, constrain celestial X position to left half (0 to 0.5)
                    val adjustedX = if (isLandscape) {
                        // Map the celestial's X from full screen (0 to width) to left half (0 to width/2)
                        cel.x * 0.5f
                    } else {
                        cel.x
                    }

                    // Check if it's sun or moon time (using offset)
                    val calendar = Calendar.getInstance()
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val minute = calendar.get(Calendar.MINUTE)
                    val baseTime = hour + (minute / 60f)
                    var currentTime = (baseTime + timeOffsetHours) % 24f
                    if (currentTime < 0f) currentTime += 24f
                    val isSun = currentTime >= 6f && currentTime < 19f

                    // Apply celestial alpha to all drawing operations
                    val effectiveAlpha = cel.alpha * celestialAlpha

                    if (isSun) {
                        // Draw beautiful sun with rays
                        val sunCenter = Offset(adjustedX, cel.y)

                        // Draw sun rays (8 rays in a circle)
                        val numRays = 8
                        val rayLength = cel.size * 1.8f
                        val rayWidth = cel.size * 0.18f

                        for (i in 0 until numRays) {
                            val angle = (i * 2 * PI / numRays).toFloat()
                            val rayStart = Offset(
                                sunCenter.x + cos(angle) * cel.size * 1.1f,
                                sunCenter.y + sin(angle) * cel.size * 1.1f
                            )
                            val rayEnd = Offset(
                                sunCenter.x + cos(angle) * rayLength,
                                sunCenter.y + sin(angle) * rayLength
                            )

                            // Draw ray with gradient effect (thicker at base, thinner at tip)
                            val rayPath = Path().apply {
                                val perpAngle = angle + (PI / 2).toFloat()
                                val baseWidth = rayWidth
                                val tipWidth = rayWidth * 0.3f

                                // Create trapezoid shape for ray
                                moveTo(
                                    rayStart.x + cos(perpAngle) * baseWidth,
                                    rayStart.y + sin(perpAngle) * baseWidth
                                )
                                lineTo(
                                    rayEnd.x + cos(perpAngle) * tipWidth,
                                    rayEnd.y + sin(perpAngle) * tipWidth
                                )
                                lineTo(
                                    rayEnd.x - cos(perpAngle) * tipWidth,
                                    rayEnd.y - sin(perpAngle) * tipWidth
                                )
                                lineTo(
                                    rayStart.x - cos(perpAngle) * baseWidth,
                                    rayStart.y - sin(perpAngle) * baseWidth
                                )
                                close()
                            }

                            drawPath(
                                path = rayPath,
                                color = color.copy(alpha = effectiveAlpha * 0.5f)
                            )
                        }

                        // Draw outer glow (largest)
                        drawCircle(
                            color = color.copy(alpha = effectiveAlpha * 0.15f),
                            radius = cel.size * 2.0f,
                            center = sunCenter
                        )

                        // Draw middle glow
                        drawCircle(
                            color = color.copy(alpha = effectiveAlpha * 0.3f),
                            radius = cel.size * 1.4f,
                            center = sunCenter
                        )

                        // Draw main sun body
                        drawCircle(
                            color = color.copy(alpha = effectiveAlpha),
                            radius = cel.size,
                            center = sunCenter
                        )

                        // Draw bright core
                        drawCircle(
                            color = color.copy(alpha = effectiveAlpha * 0.9f),
                            radius = cel.size * 0.6f,
                            center = sunCenter
                        )
                    } else {
                        // Draw beautiful crescent moon with glow and texture
                        val moonCenter = Offset(adjustedX, cel.y)
                        val moonSize = cel.size * 2f

                        // Outer glow/halo effect - multiple layers for smooth gradient
                        // Only on the crescent side
                        for (i in 4 downTo 1) {
                            drawCircle(
                                color = color.copy(alpha = effectiveAlpha * 0.06f / i),
                                radius = moonSize * (1f + i * 0.25f),
                                center = moonCenter
                            )
                        }

                        // Main moon body with subtle gradient
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    color.copy(alpha = effectiveAlpha * 1.0f),
                                    color.copy(alpha = effectiveAlpha * 0.92f)
                                ),
                                center = moonCenter,
                                radius = moonSize
                            ),
                            radius = moonSize,
                            center = moonCenter
                        )

                        // Add subtle surface texture - small craters on visible part
                        val craterPositions = listOf(
                            Pair(-0.25f, -0.15f) to 0.12f,  // x, y offset + size
                            Pair(-0.15f, 0.3f) to 0.10f,
                            Pair(-0.35f, 0.1f) to 0.08f
                        )

                        craterPositions.forEach { (offset, craterSize) ->
                            val craterCenter = Offset(
                                moonCenter.x + moonSize * offset.first,
                                moonCenter.y + moonSize * offset.second
                            )
                            // Crater shadow for depth
                            drawCircle(
                                color = Color.Black.copy(alpha = effectiveAlpha * 0.2f),
                                radius = moonSize * craterSize,
                                center = craterCenter
                            )
                            // Crater rim highlight
                            drawCircle(
                                color = color.copy(alpha = effectiveAlpha * 0.3f),
                                radius = moonSize * craterSize * 0.9f,
                                center = Offset(
                                    craterCenter.x - moonSize * craterSize * 0.12f,
                                    craterCenter.y - moonSize * craterSize * 0.12f
                                )
                            )
                        }

                        // Crescent shadow to create the crescent shape
                        // Uses black with matching alpha so it fades in/out with the moon
                        val crescentOffset = Offset(
                            moonCenter.x + moonSize * 0.35f,
                            moonCenter.y - moonSize * 0.1f
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = effectiveAlpha),
                            radius = moonSize * 0.95f,
                            center = crescentOffset
                        )
                    }
                }
            }

        }
    }
}

// CompositionLocals
val LocalAnimationLevel = compositionLocalOf { 0 }
val LocalThemeColors = compositionLocalOf { ThemeColors.dark() }
val LocalUIScale = compositionLocalOf { 1 } // 0=75%, 1=100%, 2=125%
val LocalDismissOnClickOutside = compositionLocalOf { true } // New global setting for back behavior
val LocalTypeScale = compositionLocalOf { AdaptiveTypeScale(
    displayLarge = 50.sp, displayMedium = 44.sp, displaySmall = 37.sp,
    headlineLarge = 28.sp, headlineMedium = 24.sp, headlineSmall = 21.sp,
    bodyLarge = 18.sp, bodyMedium = 16.sp, bodySmall = 14.sp,
    buttonLarge = 21.sp, buttonMedium = 17.sp,
    labelLarge = 15.sp, labelMedium = 13.sp, labelSmall = 12.sp,
) }


// Theme color scheme
data class ThemeColors(
    val background: Color,
    val cardBackground: Color,
    val primaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val successColor: Color,
    val errorColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
) {
    companion object {
        fun dark(
            accent: Color = Color(0xFF4895EF),
            gradStart: Color = Color(0xFF0A2540),
            gradEnd: Color = Color(0xFF000000)
        ) = ThemeColors(
            background = Color(0xFF000000),
            cardBackground = Color(0xFF111111),  // Fully opaque
            primaryAccent = accent,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.6f),
            border = accent.copy(alpha = 0.3f),
            successColor = accent,
            errorColor = Color(0xFFF44336),
            gradientStart = gradStart,
            gradientEnd = gradEnd
        )

        fun light(
            accent: Color = Color(0xFF2563EB),
            gradStart: Color = Color(0xFFB8D4FF),
            gradEnd: Color = Color(0xFFE8F0FF)
        ) = ThemeColors(
            background = Color(0xFFE8F0FF),
            cardBackground = Color.White,
            primaryAccent = accent,
            textPrimary = Color(0xFF1A1A1A),
            textSecondary = Color(0xFF666666),
            border = accent.copy(alpha = 0.3f),
            successColor = accent,
            errorColor = Color(0xFFC62828),
            gradientStart = gradStart,
            gradientEnd = gradEnd
        )

        // OLED-Optimized Theme with customizable accent color
        fun oledMode(accentColor: Color = Color(0xFF4895EF)) = ThemeColors(
            background = Color(0xFF000000), // Pure black for OLED
            cardBackground = Color(0xFF000000), // Pure black for OLED
            primaryAccent = accentColor, // Accent color for text elements
            textPrimary = Color.White, // Perfect white for text
            textSecondary = Color.White.copy(alpha = 0.7f), // Dimmed white for secondary text
            border = accentColor.copy(alpha = 0f), // No visible borders in OLED — accent-transparent so animation fades cleanly
            successColor = accentColor,
            errorColor = accentColor,
            gradientStart = Color(0xFF000000), // Pure black
            gradientEnd = Color(0xFF000000) // Pure black
        )
    }
}

// Helper to send notification
fun sendBootNotification(
    context: Context,
    userName: String = "",
    onRequestPermission: () -> Unit = {}
) {
    // Check permission first
    if (!ShizukuHelper.hasNotificationPermission(context)) {
        // Request permission instead of silently failing
        onRequestPermission()
        return
    }

    val channelId = "gama_boot_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "GAMA Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications from GAMA"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val title = if (userName.isNotEmpty()) "Hey there, $userName! 👋" else "Hey there! 👋"
    val builder = android.app.Notification.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)  // Use app icon
        .setContentTitle(title)
        .setContentText("Is now a good time to switch to Vulkan rendering?")
        .setAutoCancel(true)

    try {
        notificationManager.notify(1001, builder.build())
    } catch (e: Exception) {
        // Handle failures gracefully
    }
}

// Send a periodic reminder when the renderer is still OpenGL
fun sendOpenGLReminderNotification(context: Context, userName: String = ""): Boolean {
    if (!ShizukuHelper.hasNotificationPermission(context)) return false

    val channelId = "gama_reminder_channel"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(
            channelId, "Renderer Reminders", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Periodic reminders to switch to Vulkan when OpenGL is active"
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    val name = if (userName.isNotEmpty()) ", $userName" else ""
    val title = if (userName.isNotEmpty()) "Hey there, $userName! 👋" else "Hey there! 👋"
    val messages = listOf(
        "Is now a good time to switch to Vulkan? ⚡",
        "Still on OpenGL$name - Vulkan is ready whenever you are!",
        "Quick heads up$name - you might get a speed boost from Vulkan!",
        "Psst$name… Vulkan is just one tap away",
        "Just a friendly nudge$name - Vulkan could make things snappier!"
    )

    return try {
        nm.notify(
            2001,
            android.app.Notification.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(messages.random())
                .setAutoCancel(true)
                .build()
        )
        true
    } catch (e: Exception) { false }
}

// GrainEffect removed — was causing main-thread stall on first composition

// Glide Option Selector for Theme and Animation Speed
// @SuppressLint rationale: maxWidth IS read from the BoxWithConstraints scope (see `val itemWidth = maxWidth / options.size`
// below). The lint rule fires because maxWidth is captured via a local `val` instead of being used directly inside
// the lambda argument — the usage is real, the warning is a false-positive.
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun GlideOptionSelector(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true // Add enabled parameter
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current

    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "glide_selector_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.25f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "glide_selector_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primaryAccent.copy(alpha = 0.07f))
            .border(1.dp, colors.border.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .alpha(alpha) // Apply alpha when disabled
    ) {
        val maxWidth = maxWidth
        val itemWidth = maxWidth / options.size

        // Animated indicator
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = if (animLevel == 2) {
                tween<Dp>(durationMillis = 150, easing = LinearEasing)
            } else {
                spring<Dp>(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                )
            },
            label = "indicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primaryAccent)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, text ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val isSelected = index == selectedIndex
                    // Use simple direct color transition or very fast animation to avoid lag
                    val textColor = if (isSelected) {
                        if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
                    } else {
                        colors.textSecondary
                    }

                    Text(
                        text = text,
                        color = textColor,
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold, // Force Bold
                        fontFamily = quicksandFontFamily
                    )
                }
            }
        }

        // Gesture Overlay
        if (!enabled) {
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                            .changes.forEach { it.consume() }
                    }
                }
            })
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) { // Only enable gestures when enabled
                    if (enabled) {
                        val itemWidthPx = size.width / options.size
                        detectTapGestures { offset ->
                            val index = (offset.x / itemWidthPx).toInt().coerceIn(0, options.size - 1)
                            if (index != currentSelectedIndex) {
                                currentOnOptionSelected(index)
                            }
                        }
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        val itemWidthPx = size.width / options.size
                        var lastIndex = -1

                        detectDragGestures(
                            onDragStart = { offset ->
                                lastIndex = (offset.x / itemWidthPx).toInt().coerceIn(0, options.size - 1)
                                if (lastIndex != currentSelectedIndex) {
                                    currentOnOptionSelected(lastIndex)
                                }
                            },
                            onDrag = { change, _ ->
                                val index = (change.position.x / itemWidthPx).toInt().coerceIn(0, options.size - 1)
                                if (index != lastIndex) {
                                    currentOnOptionSelected(index)
                                    lastIndex = index
                                }
                            }
                        )
                    } // Close if (enabled) block
                }
        )
    }
}


@Composable
fun GamaUI(
    onRequestNotificationPermission: () -> Unit = {}
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

    // Version-based preferences reset
    // Increment PREFS_VERSION when you need to clear old user preferences
    val PREFS_VERSION = 4 // Increment this to reset all preferences
    val savedPrefsVersion = prefs.getInt("prefs_version", 0)

    if (savedPrefsVersion < PREFS_VERSION) {
        // Clear old preferences
        prefs.edit().clear().apply()
        // Save new version
        prefs.edit().putInt("prefs_version", PREFS_VERSION).apply()
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
    var versionStatus by remember { mutableStateOf("Checking version...") }
    var changelogText by remember { mutableStateOf("Loading...") }
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
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVisualEffects by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showColorCustomization by remember { mutableStateOf(false) }
    var showOLED by remember { mutableStateOf(false) }
    var showFunctionality by remember { mutableStateOf(false) }
    var showIntegrations by remember { mutableStateOf(false) }
    var showDeveloper by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }

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

    BackHandler(enabled = showEffects) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showEffects = false
        // Re-open parent
        showVisualEffects = true
    }

    BackHandler(enabled = showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showNotifications = false
        showFunctionality = true
    }

    BackHandler(enabled = showFunctionality && !showNotifications) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showFunctionality = false
    }
    BackHandler(enabled = showIntegrations) {
        showIntegrations = false
    }

    BackHandler(enabled = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality && !showEffects && !showParticles && !showNotifications) {
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

    BackHandler(enabled = showChangelogDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showChangelogDialog = false
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
    // Using SnapshotStateList for instant updates
    val excludedAppsList = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("excluded_apps", setOf()) ?: emptySet()) } }
    var oledMode by remember { mutableStateOf(prefs.getBoolean("oled_mode", false)) }
    var oledAccentColor by remember { mutableStateOf(Color(prefs.getInt("oled_accent_color", 0xFF4895EF.toInt()))) }
    var useDynamicColorOLED by remember { mutableStateOf(prefs.getBoolean("use_dynamic_color_oled", false)) }

    // Dynamic colors
    var useDynamicColor by remember { mutableStateOf(prefs.getBoolean("use_dynamic_color", true)) }
    var customAccentColor by remember { mutableStateOf(Color(prefs.getInt("custom_accent", 0xFF4895EF.toInt()))) }
    var customGradientStart by remember { mutableStateOf(Color(prefs.getInt("custom_gradient_start", 0xFF0A2540.toInt()))) }
    var customGradientEnd by remember { mutableStateOf(Color(prefs.getInt("custom_gradient_end", 0xFF000000.toInt()))) }

    // Global back behavior toggle
    var dismissOnClickOutside by remember { mutableStateOf(prefs.getBoolean("dismiss_on_click_outside", true)) }

    // Aggressive mode confirmation
    var aggressiveModeConfirmed by remember { mutableStateOf(false) }
    var dontShowAggressiveWarning by remember { mutableStateOf(prefs.getBoolean("dont_show_aggressive_warning", false)) }


    val timeAwareHourForTheme = run {
        val raw = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        ((raw + timeOffsetHours.toInt()).let { h -> ((h % 24) + 24) % 24 })
    }
    val isDarkTheme = when (themePreference) {
        1 -> true
        2 -> false
        else -> systemInDarkTheme // Auto: always follow the OS dark/light mode setting
    }

    val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
        if (isDarkTheme) {
            Color(context.getColor(android.R.color.system_accent1_400))
        } else {
            Color(context.getColor(android.R.color.system_accent1_600))
        }
    } else {
        customAccentColor
    }

    // Separate dynamic color for OLED mode - always pulls from wallpaper when enabled
    val oledDynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(context.getColor(android.R.color.system_accent1_400))  // Always use dark variant for OLED
    } else {
        customAccentColor
    }

    val dynamicGradientStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
        if (isDarkTheme) {
            Color(context.getColor(android.R.color.system_accent1_700))
        } else {
            Color(context.getColor(android.R.color.system_accent1_200))
        }
    } else {
        customGradientStart
    }

    val dynamicGradientEnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && useDynamicColor) {
        if (isDarkTheme) {
            Color(0xFF000000)
        } else {
            Color(context.getColor(android.R.color.system_neutral1_50))
        }
    } else {
        customGradientEnd
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
        val snapThemePref        = themePreference
        val snapDynColor         = useDynamicColor
        val snapAccent           = customAccentColor.toArgb()
        val snapGradStart        = customGradientStart.toArgb()
        val snapGradEnd          = customGradientEnd.toArgb()
        val snapUiScale          = uiScale
        val snapVerbose          = verboseMode
        val snapAggressive       = aggressiveMode
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
                putInt("custom_accent",                 snapAccent)
                putInt("custom_gradient_start",         snapGradStart)
                putInt("custom_gradient_end",           snapGradEnd)
                putInt("ui_scale",                      snapUiScale)
                putBoolean("verbose_mode",              snapVerbose)
                putBoolean("aggressive_mode",           snapAggressive)
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

    val anyPanelOpen = showWarningDialog || showGitHubDialog || showResourcesPanel || showExternalLinkConfirm ||
            showChangelogDialog || showSettings || showVisualEffects || showColorCustomization || showOLED ||
            showFunctionality || showIntegrations || showShizukuHelp || showSuccessDialog || showDeveloperMenu ||
            showVerbosePanel || showAppSelector || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications

    // Blur is expensive — lightweight pop-up dialogs (ExternalLinkConfirmDialog) are excluded
    // so their entry animation never races against a simultaneous full-screen blur pass.
    val anyFullPanelOpen = showWarningDialog || showGitHubDialog || showResourcesPanel ||
            showChangelogDialog || showSettings || showVisualEffects || showColorCustomization || showOLED ||
            showFunctionality || showIntegrations || showShizukuHelp || showSuccessDialog || showDeveloperMenu ||
            showVerbosePanel || showAppSelector || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper || showEasterEgg || showNotifications

    val currentVersion = "1.2"

    // Gradient "Come Alive" Animation on Startup
    val gradientStartupAlpha = remember { Animatable(0f) }

    // Stronger breathing gradient loop
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )

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
            // Only attempt to check/update renderer if Shizuku is actually running and authorized
            if (shizukuRunning && shizukuPermissionGranted) {
                val detectedRenderer = ShizukuHelper.getCurrentRenderer()
                // Only overwrite when we got a real answer back. "Unknown" means the
                // command failed (reflection error, timeout, unexpected prop value) —
                // in that case keep the prefs value already shown so the card never
                // flashes "Unknown" when the renderer hasn't actually changed.
                if (detectedRenderer == "Vulkan" || detectedRenderer == "OpenGL") {
                    currentRenderer = detectedRenderer
                    prefs.edit().putString("last_renderer", detectedRenderer).apply()
                }
                // "Default", "Not Set", and error values are silently ignored —
                // the prefs value loaded at initialisation continues to show.
            }
            // If Shizuku isn't running, currentRenderer retains the value from prefs.
            rendererLoading = false // Done — stop skeleton shimmer regardless of outcome
        }

        scope.launch {
            versionStatus = ShizukuHelper.checkVersion(currentVersion)
        }

        scope.launch {
            changelogText = fetchChangelog()
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isDarkTheme) breathingAlpha else 0.6f)
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

            // Main content
            // Special case: Always blur for Aggressive Warning, otherwise respect blurEnabled setting
            val blurAmount by animateDpAsState(
                targetValue = if (showAggressiveWarning || (anyFullPanelOpen && blurEnabled)) 12.dp else 0.dp,
                animationSpec = if (animationLevel == 2) snap<Dp>() else tween<Dp>(durationMillis = 600, easing = MotionTokens.Easing.emphasizedDecelerate),
                label = "blur_amount"
            )

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

            // Wrapper Box for blurring main content AND panels together
            // Only attach the blur modifier when it's actually needed — avoids the
            // full offscreen RenderEffect texture pass on every frame when blur = 0.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurAmount > 0.dp) Modifier.blur(blurAmount) else Modifier)
            ) {

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
                                                in 0..5 -> if (userName.isNotEmpty()) listOf("Still up, $userName? 🌙","Late night session, $userName? 🌙","The world is quiet, $userName. 🌙","Just you and the GPU, $userName. 🌙","Somewhere between today and tomorrow, $userName. 🌙") else listOf("Still up? 🌙","Late night session? 🌙","The world is quiet. 🌙","Just you and the GPU. 🌙","Somewhere between today and tomorrow. 🌙")
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
                                                        val paint = Paint().asFrameworkPaint()
                                                        paint.color = android.graphics.Color.TRANSPARENT
                                                        paint.textSize = gamaTextSize.toPx()
                                                        paint.setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                        paint.textAlign = android.graphics.Paint.Align.CENTER
                                                        paint.isDither = true
                                                        canvas.nativeCanvas.drawText(
                                                            "GAMA",
                                                            size.width / 2,
                                                            size.height / 2 + (gamaTextSize.toPx() * 0.25f),
                                                            paint
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
                                                accent = true, enabled = true,
                                                colors = colors, oledMode = oledMode, iconType = "resources"
                                            )
                                            IllustratedButton(
                                                text = "Open GPUWatch",
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

            } // End of blur wrapper Box for main content



            // Shizuku Help Dialog
            BouncyDialog(
                visible = showShizukuHelp,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showShizukuHelp = false
                }
            ) {
                ShizukuHelpDialog(
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
            }

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

                    // 1. Optimistically update UI immediately
                    if (pendingRendererName.isNotEmpty()) {
                        currentRenderer = pendingRendererName
                        prefs.edit().putString("last_renderer", pendingRendererName).apply()
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

                    // Save the time this switch was confirmed
                    val switchNow = System.currentTimeMillis()
                    prefs.edit().putLong("last_switch_time", switchNow).apply()
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

            ChangelogDialog(
                visible = showChangelogDialog,
                changelogText = changelogText,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showChangelogDialog = false
                },
                onRefresh = {
                    scope.launch {
                        // Refresh changelog
                        changelogText = fetchChangelog()
                        // Version check is handled within the ChangelogDialog itself
                    }
                },
                onDeveloperModeTrigger = {
                    performHaptic(HapticFeedbackConstants.LONG_PRESS)
                    showChangelogDialog = false
                    showDeveloper = true
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = oledMode
            )

            SettingsPanel(
                visible = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality && !showIntegrations && !showEffects && !showParticles && !showNotifications,
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

            // Blur wrapper specifically for FunctionalityPanel when Aggressive Warning is shown
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val functionalityBlurAmount by animateDpAsState(
                    targetValue = if (showAggressiveWarning) 20.dp else 0.dp,
                    animationSpec = if (animationLevel == 2) snap<Dp>() else tween<Dp>(durationMillis = 600, easing = MotionTokens.Easing.emphasizedDecelerate),
                    label = "functionality_blur"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(functionalityBlurAmount)
                ) {
                    FunctionalityPanel(
                        visible = showFunctionality,
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

            IntegrationsPanel(
                visible = showIntegrations,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showIntegrations = false
                },
                isSmallScreen = isSmallScreen,
                isLandscape = isLandscape,
                isTablet = isTablet,
                colors = colors,
                cardBackground = cardBackground,
                oledMode = oledMode
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
                visible = showEffects,
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showEffects = false
                    // Re-open parent
                    showVisualEffects = true
                },
                blurEnabled = blurEnabled,
                onBlurChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    blurEnabled = value
                    savePreferences()
                },
                gradientEnabled = gradientEnabled,
                onGradientChange = { value ->
                    performHaptic(HapticFeedbackConstants.CLOCK_TICK)
                    gradientEnabled = value
                    savePreferences()
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
                    // Changelog button (bottom-start)
                    AnimatedElement(visible = isVisible, staggerIndex = 4,
                        totalItems = 7, modifier = Modifier.align(Alignment.BottomStart)) {
                        val btnSize = if (isSmallScreen) 48.dp else 52.dp
                        val iconSize = if (isSmallScreen) 24.dp else 28.dp

                        val changelogGlowTransition = rememberInfiniteTransition(label = "changelog_glow")
                        val changelogGlowAlpha by changelogGlowTransition.animateFloat(
                            initialValue = 0.18f, targetValue = 0.38f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = MotionTokens.Easing.silk),
                                repeatMode = RepeatMode.Reverse
                            ), label = "changelog_glow_a"
                        )
                        var changelogPressed by remember { mutableStateOf(false) }
                        val changelogPressScale by animateFloatAsState(
                            targetValue = if (changelogPressed) MotionTokens.Scale.subtle else 1f,
                            animationSpec = spring(
                                dampingRatio = if (changelogPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                                stiffness    = if (changelogPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                            ),
                            label = "changelog_press"
                        )
                        val changelogBorderAlpha by animateFloatAsState(
                            targetValue = if (changelogPressed) 1f else 0.4f,
                            animationSpec = tween(durationMillis = if (changelogPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick, easing = FastOutSlowInEasing),
                            label = "changelog_border_a"
                        )
                        val changelogBorderWidth by animateDpAsState(
                            targetValue = if (changelogPressed) 2.dp else 1.5.dp,
                            animationSpec = tween(durationMillis = if (changelogPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick, easing = FastOutSlowInEasing),
                            label = "changelog_border_w"
                        )
                        val glowSize = btnSize * 1.8f

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(glowSize)) {
                                // Glow blob
                                Box(
                                    modifier = Modifier
                                        .size(glowSize)
                                        .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    colors.primaryAccent.copy(alpha = changelogGlowAlpha),
                                                    colors.primaryAccent.copy(alpha = changelogGlowAlpha * 0.4f),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                // Button surface
                                Box(
                                    modifier = Modifier
                                        .size(btnSize)
                                        .graphicsLayer(scaleX = changelogPressScale, scaleY = changelogPressScale)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    colors.primaryAccent.copy(alpha = 0.22f),
                                                    colors.primaryAccent.copy(alpha = 0.08f)
                                                )
                                            )
                                        )
                                        .border(changelogBorderWidth, colors.primaryAccent.copy(alpha = changelogBorderAlpha), RoundedCornerShape(14.dp))
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    changelogPressed = true
                                                    tryAwaitRelease()
                                                    changelogPressed = false
                                                },
                                                onTap = {
                                                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    showChangelogDialog = true
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(iconSize)) {
                                        val path = Path().apply {
                                            moveTo(size.width * 0.25f, size.height * 0.15f)
                                            lineTo(size.width * 0.6f, size.height * 0.15f)
                                            lineTo(size.width * 0.75f, size.height * 0.3f)
                                            lineTo(size.width * 0.75f, size.height * 0.85f)
                                            lineTo(size.width * 0.25f, size.height * 0.85f)
                                            close()
                                        }
                                        drawPath(path = path, color = colors.primaryAccent.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx()))
                                        val lineY1 = size.height * 0.45f
                                        val lineY2 = size.height * 0.60f
                                        val lineY3 = size.height * 0.75f
                                        drawLine(colors.primaryAccent.copy(alpha = 0.9f), Offset(size.width * 0.35f, lineY1), Offset(size.width * 0.65f, lineY1), 1.5.dp.toPx())
                                        drawLine(colors.primaryAccent.copy(alpha = 0.9f), Offset(size.width * 0.35f, lineY2), Offset(size.width * 0.65f, lineY2), 1.5.dp.toPx())
                                        drawLine(colors.primaryAccent.copy(alpha = 0.9f), Offset(size.width * 0.35f, lineY3), Offset(size.width * 0.55f, lineY3), 1.5.dp.toPx())
                                    }
                                }
                                // One-time label
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showButtonLabels,
                                    enter = fadeIn(animationSpec = tween(400)),
                                    exit  = fadeOut(animationSpec = tween(600))
                                ) {
                                    Text(
                                        text = "Changelog",
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

                        val settingsGlowTransition = rememberInfiniteTransition(label = "settings_glow")
                        val settingsGlowAlpha by settingsGlowTransition.animateFloat(
                            initialValue = 0.18f, targetValue = 0.38f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = MotionTokens.Easing.silk),
                                repeatMode = RepeatMode.Reverse
                            ), label = "settings_glow_a"
                        )
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
                                // Glow blob
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
                                // Button surface
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

@Composable
fun BouncyDialog(visible: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Call the full implementation with default parameters
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = false,
        applyBlur = false,
        content = content
    )
}

// Fetch changelog from GitHub
suspend fun fetchChangelog(): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://raw.githubusercontent.com/popovicialinc/gama/refs/heads/main/GAMA%20for%20Android/changelog.txt")
            val connection = url.openConnection()
            connection.readTimeout = 10000
            connection.connectTimeout = 10000
            val content = connection.getInputStream().bufferedReader().use { it.readText() }
            // Add blank line before GitHub content and 2 blank lines after
            "\n\n\n$content"
        } catch (e: Exception) {
            "Failed to fetch changelog:\n${e.message}"
        }
    }
}

@Composable
fun TitleSection(colors: ThemeColors, isSmallScreen: Boolean, isLandscape: Boolean, userName: String = "", currentHour: Int = 12, onEasterEgg: (() -> Unit)? = null) {
    val ts = LocalTypeScale.current

    // ── Greeting pools per time period ───────────────────────────────────────
    val greetingPool: List<String> = when (currentHour) {
        in 0..5 -> if (userName.isNotEmpty()) listOf(
            "Still up, $userName? 🌙",
            "The world is quiet, $userName. 🌙",
            "Somewhere between today and tomorrow, $userName. 🌙",
            "The quiet hours, $userName. 🌙",
            "Some nights are for thinking, $userName. 🌙"
        ) else listOf(
            "Still up? 🌙",
            "The world is quiet. 🌙",
            "Somewhere between today and tomorrow. 🌙",
            "The quiet hours. 🌙",
            "Some nights are for thinking. 🌙"
        )
        in 6..11 -> if (userName.isNotEmpty()) listOf(
            "Good morning, $userName! ☀️",
            "A fresh start, $userName. ☀️",
            "The world is quiet, $userName. ☀️",
            "The world can wait, $userName. ☀️",
            "Up early, $userName? ☀️"
        ) else listOf(
            "Good morning! ☀️",
            "A fresh start. ☀️",
            "The world is quiet. ☀️",
            "The world can wait. ☀️",
            "Up early? ☀️"
        )
        in 12..16 -> if (userName.isNotEmpty()) listOf(
            "Hey, $userName! 👋",
            "Welcome back, $userName. 👋",
            "The world can wait, $userName. 👋",
            "Good afternoon, $userName! 👋",
            "There you are, $userName! 👋"
        ) else listOf(
            "Hey! 👋",
            "Welcome back. 👋",
            "The world can wait. 👋",
            "Good afternoon! 👋",
            "There you are! 👋"
        )
        in 17..22 -> if (userName.isNotEmpty()) listOf(
            "Good evening, $userName! 🌙",
            "The world can wait, $userName. 🌙",
            "Winding down, $userName? 🌙",
            "The quiet hours, $userName. 🌙",
            "Some nights are for thinking, $userName. 🌙"
        ) else listOf(
            "Good evening! 🌙",
            "The world can wait. 🌙",
            "Winding down? 🌙",
            "The quiet hours. 🌙",
            "Some nights are for thinking. 🌙"
        )
        else -> if (userName.isNotEmpty()) listOf(
            "Still up, $userName? 🌙",
            "The quiet hours, $userName. 🌙",
            "Somewhere between today and tomorrow, $userName. 🌙",
            "Some nights are for thinking, $userName. 🌙",
            "The world can wait, $userName. 🌙"
        ) else listOf(
            "Still up? 🌙",
            "The quiet hours. 🌙",
            "Somewhere between today and tomorrow. 🌙",
            "Some nights are for thinking. 🌙",
            "The world can wait. 🌙"
        )
    }
    val displayText = remember(currentHour, userName) { greetingPool.random() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 10.dp else 14.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.emphasizedDecelerate))
    ) {
        // Use AnimatedContent for smooth text substitution if the greeting changes,
        // though typically it just appends the name. animateContentSize on parent handles the width change.
        Text(
            text = displayText,
            fontSize = ts.bodyLarge,
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold // Force Bold
        )

        val gamaTextSize = if (isLandscape) (ts.displayLarge.value * 1.15f).sp else (ts.displayLarge.value * 1.10f).sp
        val context = LocalContext.current
        val quicksandBoldTypeface = remember {
            try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/quicksand_bold.ttf") }
            catch (e: Exception) { android.graphics.Typeface.DEFAULT_BOLD }
        }

        // One-shot shimmer matching CleanTitle — fires once on composition, then settles permanently
        val animationLevel = LocalAnimationLevel.current
        var shimmerTarget by remember { mutableStateOf(0f) }
        val shimmerProgress by animateFloatAsState(
            targetValue = shimmerTarget,
            animationSpec = if (animationLevel == 2) snap()
            else tween(durationMillis = 900, easing = MotionTokens.Easing.silk),
            label = "gama_title_shimmer"
        )
        LaunchedEffect(Unit) {
            if (animationLevel != 2) delay(250)
            shimmerTarget = 1f
        }

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                // Left bar — shimmer sweeps left → right (toward text)
                Box(modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .drawWithContent {
                        drawContent()
                        val bandWidth = size.width * 0.35f
                        val bandCenter = shimmerProgress * (size.width + bandWidth) - bandWidth * 0.5f
                        val shimmerBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.primaryAccent.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.75f),
                                colors.primaryAccent.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            startX = bandCenter - bandWidth * 0.5f,
                            endX   = bandCenter + bandWidth * 0.5f
                        )
                        drawRect(brush = shimmerBrush)
                    }
                    .background(Brush.horizontalGradient(listOf(colors.primaryAccent.copy(alpha=0f), colors.primaryAccent.copy(alpha=1f)))))
                Text(text = "GAMA", fontSize = gamaTextSize, fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily, color = colors.textPrimary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).pointerInput(onEasterEgg) {
                        if (onEasterEgg != null) detectTapGestures(onLongPress = { onEasterEgg() })
                    }.drawWithContent {
                        drawIntoCanvas { canvas ->
                            val gp = Paint().asFrameworkPaint().apply {
                                isAntiAlias = true; color = android.graphics.Color.TRANSPARENT
                                textSize = gamaTextSize.toPx(); textAlign = android.graphics.Paint.Align.CENTER
                                maskFilter = android.graphics.BlurMaskFilter(80f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                setShadowLayer(80f, 0f, 0f, colors.textPrimary.toArgb())
                            }
                            val cx = size.width/2; val cy = size.height/2 + gamaTextSize.toPx()*0.25f
                            canvas.nativeCanvas.drawText("GAMA", cx, cy, gp)
                        }
                        drawContent()
                    })
                // Right bar — shimmer sweeps right → left (toward text)
                Box(modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .drawWithContent {
                        drawContent()
                        val bandWidth = size.width * 0.35f
                        val bandCenter = (1f - shimmerProgress) * (size.width + bandWidth) - bandWidth * 0.5f
                        val shimmerBrush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.primaryAccent.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.75f),
                                colors.primaryAccent.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            startX = bandCenter - bandWidth * 0.5f,
                            endX   = bandCenter + bandWidth * 0.5f
                        )
                        drawRect(brush = shimmerBrush)
                    }
                    .background(Brush.horizontalGradient(listOf(colors.primaryAccent.copy(alpha=1f), colors.primaryAccent.copy(alpha=0f)))))
            }
        }

        // Standing by text - 50% bigger
        Text(
            text = "Standing by and awaiting your command",
            fontSize = ts.bodyLarge, // greeting name
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        // WHAT'S NEXT? text in accent color - 50% bigger
        Text(
            text = "WHAT'S NEXT?",
            fontSize = ts.bodyMedium, // greeting subtitle
            fontFamily = quicksandFontFamily,
            color = colors.primaryAccent.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold, // Force Bold
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedElement(
    visible: Boolean,
    staggerIndex: Int = 0,
    totalItems: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationLevel = LocalAnimationLevel.current
    val density = LocalDensity.current

    // When staggerIndex > 0 and animations are on, delay the item's own
    // entrance by (staggerIndex * 75ms) so cards cascade top-to-bottom.
    // The item starts invisible and becomes "locally visible" after the delay.
    // On exit (visible=false) all items hide immediately — no stagger out.
    var localVisible by remember { mutableStateOf(if (staggerIndex == 0) visible else !visible) }

    LaunchedEffect(visible) {
        if (visible) {
            if (staggerIndex > 0 && animationLevel != 2) {
                // Stagger gap on enter: each card waits a bit longer than the one above
                delay(staggerIndex * 95L)
            }
            localVisible = true
        } else {
            if (totalItems > 0 && staggerIndex > 0 && animationLevel != 2) {
                // Reverse stagger on exit: bottom card (highest index) exits first,
                // top card exits last — mirror image of the enter cascade.
                // Delay = how far from the bottom this card is.
                delay((totalItems - staggerIndex) * 55L)
            }
            localVisible = false
        }
    }

    // Two-phase enter animation:
    //   Phase 1 (~60% of duration) — accelerate in from below/compressed, overshoot past 1.0
    //   Phase 2 (~40% of duration) — ease-in-out back to exactly 1.0 (no snap, no wobble)
    //
    // keyframes gives us precise control over both the overshoot magnitude AND the easing
    // of the final correction, which a plain spring can't do without oscillating.
    //
    // Scale:       0.84 → 1.065 (overshoot) → 1.0  (ease-in-out settle)
    // TranslationY: 36dp below → -8dp (overshoot up) → 0dp (ease-in-out settle)
    // Alpha:        0 → 1 quick ease-out (opacity never overshoots)
    //
    // animLevel 0 = full quality: longer, more relaxed settle
    // animLevel 1 = reduced:      shorter, tighter

    // Gentle two-phase enter:
    //   Rush in slowly, nudge just barely past 1.0, then ease-in-out back to rest.
    //   Overshoot is intentionally tiny — the effect should feel like a soft breath,
    //   not a bounce. The long settle is what makes it feel organic, not robotic.
    val totalDuration = if (animationLevel == 0) 680 else 460
    val overshootAt   = (totalDuration * 0.62f).toInt()
    val gentleEase    = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)  // soft ease-in-out

    // Scale uses a two-phase keyframe: rush in, nudge just past 1.0, ease-in-out back to rest.
    // translationY uses a plain ease-out tween with NO overshoot — Y overshoot pushes cards
    // outside the scroll container's clip bounds, which is what caused the clipping. Keeping
    // the overshoot purely in scale means it expands inward (toward the card's own centre)
    // and never escapes the column's layout bounds.
    // Scale never exceeds 1.0 — going over causes X-axis clipping against the parent column.
    // The springy feel comes from the easing: fast rush to ~0.995 then a slow, lazy ease-in-out
    // into exactly 1.0, which reads as a natural settle without any layout overflow.
    val scaleSpec: AnimationSpec<Float> = if (animationLevel == 2) snap() else keyframes {
        durationMillis = totalDuration
        0.88f   at 0           using CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f)
        0.995f  at overshootAt using gentleEase
        1.0f    at totalDuration using gentleEase
    }

    val offsetYDp = if (animationLevel == 0) 22f else 16f
    val offsetYPx = with(density) { offsetYDp.dp.toPx() }

    // No Y overshoot — just a smooth ease-out slide-up so cards never escape clip bounds
    val translationYSpec: AnimationSpec<Float> = if (animationLevel == 2) snap() else
        tween(durationMillis = (totalDuration * 0.75f).toInt(), easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f))

    val alphaSpec: AnimationSpec<Float> = if (animationLevel == 2) snap() else
        tween(durationMillis = (totalDuration * 0.50f).toInt(), easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f))

    val alpha by animateFloatAsState(
        targetValue = if (localVisible) 1f else 0f,
        animationSpec = alphaSpec,
        label = "stagger_alpha_$staggerIndex"
    )

    val scale by animateFloatAsState(
        targetValue = if (localVisible) 1f else 0.88f,
        animationSpec = scaleSpec,
        label = "stagger_scale_$staggerIndex"
    )

    val translationY by animateFloatAsState(
        targetValue = if (localVisible) 0f else offsetYPx,
        animationSpec = translationYSpec,
        label = "stagger_translationY_$staggerIndex"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                this.translationY = translationY
                clip = false
            }
    ) {
        content()
    }
}



@Composable
fun SettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVisualEffectsClick: () -> Unit,
    onFunctionalityClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created once, never re-created during enter animation
    val settingsScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "settings_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() }
                    } else {
                        detectTapGestures { }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val modifier = Modifier
                .widthIn(max = if(isLandscape) 900.dp else 500.dp)
                .verticalScroll(settingsScroll)
                .padding(vertical = 0.dp)

            if (isLandscape) {
                Column(
                    modifier = modifier
                        .widthIn(max = 800.dp)
                        .padding(horizontal = 32.dp)
                        .padding(bottom = backPadding)
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(28.dp))
                    CleanTitle(text = "SETTINGS", fontSize = ts.displayMedium, colors = colors, reverseGradient = false,
                        scrollOffset = settingsScroll.value
                    )
                    Text("Configure how GAMA looks and behaves", fontSize = ts.labelMedium, color = colors.textSecondary,
                        fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    AnimatedElement(visible = visible, staggerIndex = 1,
                        totalItems = 4) {
                        SettingsNavigationCard(title = "VISUALS", description = "Customize appearance and animations",
                            onClick = onVisualEffectsClick, isSmallScreen = isSmallScreen, colors = colors,
                            cardBackground = cardBackground, enabled = true, oledMode = oledMode)
                    }
                    AnimatedElement(visible = visible, staggerIndex = 2,
                        totalItems = 5) {
                        SettingsNavigationCard(title = "FUNCTIONALITY", description = "Control renderer and execution behaviour",
                            onClick = onFunctionalityClick, isSmallScreen = isSmallScreen, colors = colors,
                            cardBackground = cardBackground, enabled = true, oledMode = oledMode)
                    }
                    AnimatedElement(visible = visible, staggerIndex = 3,
                        totalItems = 5) {
                        SettingsNavigationCard(title = "INTEGRATIONS", description = "Tasker, Quick Settings tile, home screen widget",
                            onClick = onIntegrationsClick, isSmallScreen = isSmallScreen, colors = colors,
                            cardBackground = cardBackground, enabled = true, oledMode = oledMode)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Column(
                    modifier = modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = backPadding)
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(40.dp))
                    CleanTitle(
                        text = "SETTINGS",
                        fontSize = ts.displayLarge,
                        colors = colors,
                        reverseGradient = false,
                        scrollOffset = settingsScroll.value
                    )
                    Text(
                        text = "Configure how GAMA looks and behaves",
                        fontSize = ts.labelLarge,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedElement(visible = visible, staggerIndex = 1,
                        totalItems = 4) {
                        SettingsNavigationCard(
                            title = "VISUALS",
                            description = "Customize appearance and animations",
                            onClick = onVisualEffectsClick,
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                    AnimatedElement(visible = visible, staggerIndex = 2,
                        totalItems = 5) {
                        SettingsNavigationCard(
                            title = "FUNCTIONALITY",
                            description = "Control renderer and execution behaviour",
                            onClick = onFunctionalityClick,
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                    AnimatedElement(visible = visible, staggerIndex = 3,
                        totalItems = 5) {
                        SettingsNavigationCard(
                            title = "INTEGRATIONS",
                            description = "Tasker, Quick Settings tile, home screen widget",
                            onClick = onIntegrationsClick,
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = settingsScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun SettingsNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    enabled: Boolean = true,
    oledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ts = LocalTypeScale.current
    // --- disabled-state animations (unchanged) ---
    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "settings_nav_disabled_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.25f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "settings_nav_alpha"
    )
    // --- press-state tracking (must be declared before any animation that reads isPressed) ---
    var isPressed by remember { mutableStateOf(false) }

    val animatedCardBorderColor = when {
        isPressed && enabled -> colors.primaryAccent
        oledMode             -> Color.Transparent  // borderless at rest in OLED — card shape implied by pure-black background
        else                 -> colors.border
    }

    val cardBorderWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else if (oledMode) 0.dp else 1.dp,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "nav_card_border_width"
    )

    // Card scale: crisp press-down, bouncy overshoot on release
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MotionTokens.Scale.subtle else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "settings_nav_press_scale"
    )

    // Chevron scale: pops out on press, overshoots back on release
    val chevronScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "settings_nav_chevron_scale"
    )

    // Chevron translate: nudge rightward on press, overshoots back on release
    val density = LocalDensity.current
    val chevronTranslateX by animateFloatAsState(
        targetValue = if (isPressed && enabled) with(density) { 4.dp.toPx() } else 0f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "settings_nav_chevron_translate"
    )

    // Chevron color: pulse to accent on press, fade back to muted default
    // colors.* are already globally animated — no local animateColorAsState needed
    val chevronAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "settings_nav_chevron_alpha"
    )
    val chevronColor = if (isPressed && enabled)
        colors.primaryAccent
    else
        colors.textSecondary.copy(alpha = chevronAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // combine disabled scale and press scale
            .graphicsLayer(
                scaleX = disabledScale * pressScale,
                scaleY = disabledScale * pressScale
            )
            .border(width = cardBorderWidth, color = animatedCardBorderColor, shape = RoundedCornerShape(18.dp))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = alpha)
                .then(if (!enabled) Modifier.pointerInput(enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes.forEach { it.consume() }
                        }
                    }
                } else Modifier)
                // Replace .clickable with pointerInput so we can track press/release
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            if (released) onClick()
                        }
                    )
                },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.primaryAccent.copy(alpha = 0.7f),
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold, // Force Bold
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Animated description text with crossfade
                    androidx.compose.animation.AnimatedContent(
                        targetState = description,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                        },
                        label = "description_crossfade"
                    ) { targetDescription ->
                        Text(
                            text = targetDescription,
                            color = colors.textSecondary,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold // Force Bold
                        )
                    }
                }

                // Chevron with spring scale + rightward nudge + accent color pulse on press
                Canvas(
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(
                            scaleX = chevronScale,
                            scaleY = chevronScale,
                            translationX = chevronTranslateX
                        )
                ) {
                    val path = Path().apply {
                        moveTo(size.width * 0.4f, size.height * 0.2f)
                        lineTo(size.width * 0.7f, size.height * 0.5f)
                        lineTo(size.width * 0.4f, size.height * 0.8f)
                    }
                    drawPath(
                        path = path,
                        color = chevronColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    } // end border Box
}


@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    maxLines: Int,
    oledMode: Boolean = false
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val density = LocalDensity.current

    val baseHeight = if (isSmallScreen) 54.dp else 58.dp
    val ts = LocalTypeScale.current
    val baseFontSize = ts.buttonLarge
    val shape = RoundedCornerShape(18.dp)

    // Card scale: crisp press-down, bouncy overshoot on release
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "flatbtn_scale"
    )

    // Text scale: pops inward on press, overshoots back out on release
    val textScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "flatbtn_text_scale"
    )

    // Text translate: nudges down on press, springs back with overshoot
    val textTranslateY by animateFloatAsState(
        targetValue = if (isPressed && enabled) with(density) { 1.5.dp.toPx() } else 0f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "flatbtn_text_ty"
    )

    // Border: normal weight at rest, thicker accent on press
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else if (oledMode) 0.dp else 1.dp,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "flatbtn_border_width"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "flatbtn_border_alpha"
    )

    val animatedButtonColor = if (!enabled) colors.textSecondary.copy(alpha = 0.05f)
    else if (oledMode) Color.Black
    else if (accent) colors.primaryAccent
    else colors.cardBackground

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    // On press: always show accent border. At rest: normal border logic.
    // In OLED mode borders are suppressed at rest (black-on-black = invisible) and only
    // materialise on press to give tactile feedback without permanently cluttering the UI.
    val borderColor = when {
        isPressed && enabled -> colors.primaryAccent
        oledMode             -> Color.Transparent
        !accent              -> if (enabled) colors.border.copy(alpha = borderAlpha) else colors.border.copy(alpha = 0.5f)
        else                 -> if (!enabled) colors.border.copy(alpha = 0.5f) else Color.Transparent
    }

    val shouldShowBorder = !oledMode || (isPressed && enabled) || !accent || !enabled

    // Outer Box: handles layout sizing (modifier carries weight/fill/etc.) — never scaled
    Box(
        modifier = modifier.height(baseHeight),
        contentAlignment = Alignment.Center
    ) {
        // Inner Box: scaled visually but layout is already committed above, so the
        // press highlight, background, border and clip are all perfectly coincident
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(animatedButtonColor)
                .then(
                    if (shouldShowBorder) Modifier.border(
                        width = borderWidth,
                        color = borderColor,
                        shape = shape
                    ) else Modifier
                )
                .then(
                    if (enabled) Modifier.pointerInput(enabled) {
                        detectTapGestures(
                            onPress = {
                                if (animLevel != 2) isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = { onClick() }
                        )
                    } else Modifier
                )
        ) {
            Text(
                text = text,
                fontSize = baseFontSize,
                fontWeight = FontWeight.Bold,
                color = animatedTextColor,
                fontFamily = quicksandFontFamily,
                maxLines = maxLines,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer(
                    scaleX = textScale,
                    scaleY = textScale,
                    translationY = textTranslateY
                )
            )
        }
    }
}

@Composable
fun IllustratedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    oledMode: Boolean = false,
    iconType: String   // "vulkan" | "opengl" | "resources" | "gpuwatch"
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val density = LocalDensity.current
    val ts = LocalTypeScale.current

    val baseHeight = if (isSmallScreen) 54.dp else 58.dp
    val shape = RoundedCornerShape(18.dp)

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ), label = "illbtn_scale"
    )
    val textScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ), label = "illbtn_text_scale"
    )
    val textTranslateY by animateFloatAsState(
        targetValue = if (isPressed && enabled) with(density) { 1.5.dp.toPx() } else 0f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ), label = "illbtn_text_ty"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed && enabled) 2.dp else if (oledMode) 0.dp else 1.dp,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ), label = "illbtn_border_width"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ), label = "illbtn_border_alpha"
    )

    val animatedButtonColor = if (!enabled) colors.textSecondary.copy(alpha = 0.05f)
    else if (oledMode) Color.Black
    else if (accent) colors.primaryAccent
    else colors.cardBackground

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    val borderColor = when {
        isPressed && enabled -> colors.primaryAccent
        oledMode             -> Color.Transparent
        !accent              -> if (enabled) colors.border.copy(alpha = borderAlpha) else colors.border.copy(alpha = 0.5f)
        else                 -> if (!enabled) colors.border.copy(alpha = 0.5f) else Color.Transparent
    }

    val shouldShowBorder = !oledMode || (isPressed && enabled) || !accent || !enabled

    // The icon colour: accent colour on non-accent buttons, contrasting colour on accent buttons
    val iconColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f)
    } else {
        colors.primaryAccent.copy(alpha = if (enabled) 0.85f else 0.3f)
    }

    // Screen-reader description: combine the drawn icon's meaning with the
    // button label and its current enabled state so TalkBack announces something
    // meaningful rather than "unlabelled button".
    val iconDescription = when (iconType) {
        "vulkan"   -> "Vulkan lightning bolt icon"
        "opengl"   -> "OpenGL hexagon icon"
        "resources"-> "Resources list icon"
        "gpuwatch" -> "GPU activity waveform icon"
        else       -> "Button icon"
    }
    val semanticsLabel = if (enabled) "$iconDescription, $text button"
    else         "$iconDescription, $text button, disabled"

    Box(
        modifier = modifier
            .height(baseHeight)
            .semantics(mergeDescendants = true) { contentDescription = semanticsLabel },
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(animatedButtonColor)
                .then(if (shouldShowBorder) Modifier.border(borderWidth, borderColor, shape) else Modifier)
                .then(if (enabled) Modifier.pointerInput(enabled) {
                    detectTapGestures(
                        onPress = {
                            if (animLevel != 2) isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                } else Modifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .graphicsLayer(scaleX = textScale, scaleY = textScale, translationY = textTranslateY)
                    .padding(horizontal = 8.dp)
            ) {
                // ── Custom Canvas illustration ───────────────────────────────
                val iconSizeDp = if (isSmallScreen) 18.dp else 20.dp
                Canvas(modifier = Modifier.size(iconSizeDp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val strokeThin = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    when (iconType) {
                        "vulkan" -> {
                            // Clean lightning bolt — two angled strokes forming a Z-like bolt
                            val boltPath = Path().apply {
                                moveTo(w * 0.62f, h * 0.04f)   // top-right start
                                lineTo(w * 0.28f, h * 0.50f)   // sweep down-left to midpoint
                                lineTo(w * 0.52f, h * 0.50f)   // short hop right at mid
                                lineTo(w * 0.18f, h * 0.96f)   // sweep down-left to bottom
                            }
                            drawPath(boltPath, color = iconColor, style = Stroke(
                                width = w * 0.115f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            ))
                        }
                        "opengl" -> {
                            // Concentric layered hexagon — classic GPU/graphics vibe
                            fun hexPath(cx: Float, cy: Float, r: Float): Path {
                                val pts = (0..5).map { i ->
                                    val a = Math.toRadians((60.0 * i - 30.0))
                                    Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
                                }
                                return Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                                    close()
                                }
                            }
                            drawPath(hexPath(w*0.5f, h*0.5f, w*0.46f), color = iconColor, style = stroke)
                            drawPath(hexPath(w*0.5f, h*0.5f, w*0.26f), color = iconColor.copy(alpha = 0.55f), style = strokeThin)
                            drawCircle(color = iconColor.copy(alpha = 0.8f), radius = w * 0.09f, center = Offset(w*0.5f, h*0.5f))
                        }
                        "resources" -> {
                            // Stacked horizontal bars with a small accent pip — like a resource list / library
                            val barH = h * 0.11f
                            val cap = StrokeCap.Round
                            val sw = w * 0.09f
                            // Three bars, decreasing width
                            listOf(
                                Triple(w*0.12f, w*0.88f, h*0.25f),
                                Triple(w*0.12f, w*0.72f, h*0.50f),
                                Triple(w*0.12f, w*0.56f, h*0.75f)
                            ).forEachIndexed { i, (x1, x2, y) ->
                                drawLine(
                                    color = iconColor.copy(alpha = 1f - i * 0.18f),
                                    start = Offset(x1, y), end = Offset(x2, y),
                                    strokeWidth = sw, cap = cap
                                )
                            }
                            // Small accent dot on the right
                            drawCircle(
                                color = iconColor,
                                radius = w * 0.10f,
                                center = Offset(w * 0.84f, h * 0.75f)
                            )
                        }
                        "gpuwatch" -> {
                            // Activity pulse / waveform — clean monitor metaphor
                            val pulsePath = Path().apply {
                                moveTo(w * 0.04f, h * 0.50f)   // far left, mid
                                lineTo(w * 0.28f, h * 0.50f)   // flat lead-in
                                lineTo(w * 0.40f, h * 0.18f)   // spike up
                                lineTo(w * 0.52f, h * 0.82f)   // dip down
                                lineTo(w * 0.64f, h * 0.50f)   // return to mid
                                lineTo(w * 0.96f, h * 0.50f)   // flat lead-out
                            }
                            drawPath(pulsePath, color = iconColor, style = Stroke(
                                width = w * 0.105f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            ))
                        }
                    }
                }
                Spacer(modifier = Modifier.width(if (isSmallScreen) 7.dp else 9.dp))
                Text(
                    text = text,
                    fontSize = ts.buttonLarge,
                    fontWeight = FontWeight.Bold,
                    color = animatedTextColor,
                    fontFamily = quicksandFontFamily,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MainContentCards(
    isVisible: Boolean,
    isSmallScreen: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    currentRenderer: String,
    commandOutput: String,
    shizukuStatus: String,
    shizukuRunning: Boolean,
    shizukuPermissionGranted: Boolean,
    onShizukuStatusClick: () -> Unit,
    onVulkanClick: () -> Unit,
    onOpenGLClick: () -> Unit,
    onResourcesClick: () -> Unit,
    onGPUWatchClick: () -> Unit, // NEW CALLBACK
    oledMode: Boolean = false, // Added
    rendererLoading: Boolean = false,
    lastSwitchTime: Long = 0L
) {
    // In landscape, we want the cards to fill the available space better
    // rather than being constrained too tightly if it's splitting screen with title
    val maxWidth = when {
        isTablet -> 500.dp
        else -> 600.dp // Allow wider on phones to fill 1/2 screen
    }

    val shizukuReady = shizukuRunning && shizukuPermissionGranted

    Column(
        modifier = Modifier
            .fillMaxSize(), // Fill all available space
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Center content vertically
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 14.dp else 18.dp)
        ) {
            // Row 2: Renderer Card (Delay 150)
            AnimatedElement(visible = isVisible, staggerIndex = 1,
                totalItems = 4) {
                RendererCard(
                    currentRenderer = currentRenderer,
                    commandOutput = commandOutput,
                    isSmallScreen = isSmallScreen,
                    colors = colors,
                    cardBackground = cardBackground,
                    shizukuReady = shizukuReady,
                    shizukuRunning = shizukuRunning,
                    shizukuStatus = shizukuStatus,
                    onShizukuErrorClick = onShizukuStatusClick,
                    oledMode = oledMode,
                    rendererLoading = rendererLoading,
                    lastSwitchTime = lastSwitchTime
                )
            }

            // Row 3: 2×2 Action Button Grid (Delay 300)
            AnimatedElement(visible = isVisible, staggerIndex = 2,
                totalItems = 4) {
                val settingsVulkanScale by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.85f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_vulkan_scale"
                )
                val settingsVulkanAlpha by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.25f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_vulkan_alpha"
                )
                val settingsOpenglScale by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.85f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_opengl_scale"
                )
                val settingsOpenglAlpha by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.25f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_opengl_alpha"
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top row: Vulkan | OpenGL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IllustratedButton(
                            text = "Vulkan",
                            onClick = onVulkanClick,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer(
                                    scaleX = settingsVulkanScale,
                                    scaleY = settingsVulkanScale,
                                    alpha = settingsVulkanAlpha
                                )
                                .then(
                                    when {
                                        !shizukuReady -> Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                        currentRenderer == "Vulkan" -> Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(18.dp))
                                        else -> Modifier
                                    }
                                ),
                            accent = true,
                            enabled = shizukuReady,
                            colors = colors,
                            oledMode = oledMode,
                            iconType = "vulkan"
                        )
                        IllustratedButton(
                            text = "OpenGL",
                            onClick = onOpenGLClick,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer(
                                    scaleX = settingsOpenglScale,
                                    scaleY = settingsOpenglScale,
                                    alpha = settingsOpenglAlpha
                                )
                                .then(
                                    when {
                                        !shizukuReady -> Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                        currentRenderer == "OpenGL" -> Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(18.dp))
                                        else -> Modifier
                                    }
                                ),
                            accent = true,
                            enabled = shizukuReady,
                            colors = colors,
                            oledMode = oledMode,
                            iconType = "opengl"
                        )
                    }
                    // Bottom row: Resources | Open GPUWatch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IllustratedButton(
                            text = "Resources",
                            onClick = onResourcesClick,
                            modifier = Modifier.weight(1f),
                            accent = false,
                            enabled = true,
                            colors = colors,
                            oledMode = oledMode,
                            iconType = "resources"
                        )
                        IllustratedButton(
                            text = "Open GPUWatch",
                            onClick = onGPUWatchClick,
                            modifier = Modifier.weight(1f),
                            accent = false,
                            enabled = true,
                            colors = colors,
                            oledMode = oledMode,
                            iconType = "gpuwatch"
                        )
                    }
                }
            }
        } // Close nested Column
    } // Close outer Column
}

@Composable
fun RendererCard(
    currentRenderer: String,
    commandOutput: String,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    shizukuReady: Boolean,
    shizukuRunning: Boolean,
    shizukuStatus: String,
    onShizukuErrorClick: () -> Unit,
    oledMode: Boolean = false,
    rendererLoading: Boolean = false,
    lastSwitchTime: Long = 0L  // epoch millis; 0 means never recorded
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val density = LocalDensity.current

    // ── State color: accent when ready, red/amber when not ──────────────────
    val stateColor = when {
        shizukuReady   -> colors.primaryAccent
        !shizukuRunning -> Color(0xFFFF3B30)
        else            -> Color(0xFFE8A020)
    }

    // ── Infinite transition shared by glow + border pulse ───────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "renderer_infinite")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue  = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "renderer_glow_alpha"
    )

    val warningBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue  = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warning_border_alpha"
    )

    val rendererTextAlpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue  = if (shizukuReady) 1.0f else 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "renderer_text_alpha"
    )

    // ── Press state ──────────────────────────────────────────────────────────
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "renderer_press_scale"
    )

    // Renderer name pops on press, overshoots back on release
    val nameScale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "renderer_name_scale"
    )

    // Label nudges on press, overshoots back on release
    val nameTY by animateFloatAsState(
        targetValue = if (isPressed) with(density) { -2.dp.toPx() } else 0f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "renderer_name_ty"
    )

    // ── Colors ───────────────────────────────────────────────────────────────
    val borderColor = if (isPressed) {
        colors.primaryAccent
    } else if (!shizukuReady) {
        stateColor.copy(alpha = warningBorderAlpha)
    } else if (oledMode) {
        colors.primaryAccent.copy(alpha = 0.3f)
    } else {
        colors.border
    }

    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else if (oledMode && shizukuReady) 0.75.dp else 1.dp,
        animationSpec = if (animLevel == 2) snap() else tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "renderer_border_width"
    )

    val isDarkTheme = cardBackground.luminance() < 0.5f
    val subtleWarningBackground = if (!shizukuReady) {
        if (!shizukuRunning) {
            // Red state
            if (isDarkTheme)
            // Dark: nudge the dark card toward red
                cardBackground.copy(red = (cardBackground.red + 0.12f).coerceAtMost(1f), green = (cardBackground.green + 0.01f).coerceAtMost(1f), blue = (cardBackground.blue + 0.01f).coerceAtMost(1f), alpha = 1f)
            else
            // Light: punchy rose-red — contrasty and clearly in error state
                Color(0xFFFFD0CC)
        } else {
            // Amber/yellow state
            if (isDarkTheme)
            // Dark: nudge toward amber
                cardBackground.copy(red = (cardBackground.red + 0.10f).coerceAtMost(1f), green = (cardBackground.green + 0.06f).coerceAtMost(1f), blue = (cardBackground.blue + 0.00f).coerceAtMost(1f), alpha = 1f)
            else
            // Light: soft warm amber — gentle warning tint without being too intense
                Color(0xFFFFF5D6)
        }
    } else {
        cardBackground
    }

    // ── Layout: glow blob behind + card on top ───────────────────────────────
    // The glow is larger than the card so it bleeds softly around all edges.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Glow blob — blurred radial gradient, sized generously so edges spill out
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSmallScreen) 120.dp else 140.dp)
                .blur(radius = 28.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            stateColor.copy(alpha = glowAlpha * 0.7f),
                            stateColor.copy(alpha = glowAlpha * 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Card — scaled on press, all visuals inside the graphicsLayer so clip is respected
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(subtleWarningBackground)
                    .border(
                        width = borderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .pointerInput(shizukuReady) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = {
                                if (!shizukuReady) onShizukuErrorClick()
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isSmallScreen) 18.dp else 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CURRENT RENDERER",
                        color = if (!shizukuReady) stateColor else colors.primaryAccent.copy(alpha = 0.7f),
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily
                    )

                    val displayRenderer = if (currentRenderer == "Default" || currentRenderer.isEmpty()) "OpenGL" else currentRenderer

                    // Renderer name — shimmer skeleton while loading, then the real value
                    if (rendererLoading) {
                        // Skeleton shimmer: a pill-shaped placeholder that pulses while
                        // Shizuku detects the actual renderer.  Sized to roughly match
                        // the real renderer name text so layout doesn't shift on reveal.
                        val shimmerTransition = rememberInfiniteTransition(label = "renderer_skeleton")
                        val shimmerX by shimmerTransition.animateFloat(
                            initialValue = -1f,
                            targetValue  =  1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "renderer_skeleton_x"
                        )
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(ts.headlineSmall.value.dp * 1.2f)
                                .clip(RoundedCornerShape(8.dp))
                                .drawWithContent {
                                    // Base muted fill
                                    drawRoundRect(
                                        color = colors.textSecondary.copy(alpha = 0.12f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                    )
                                    // Moving shimmer band
                                    val bandW = size.width * 0.55f
                                    val cx    = shimmerX * (size.width + bandW) * 0.5f + size.width * 0.5f
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                colors.textSecondary.copy(alpha = 0.28f),
                                                Color.Transparent
                                            ),
                                            startX = cx - bandW * 0.5f,
                                            endX   = cx + bandW * 0.5f
                                        )
                                    )
                                }
                        )
                    } else {
                        Text(
                            text = displayRenderer,
                            color = colors.textPrimary.copy(alpha = if (shizukuReady) rendererTextAlpha else 1f),
                            fontSize = ts.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer(
                                scaleX = nameScale,
                                scaleY = nameScale,
                                translationY = nameTY
                            )
                        )
                    }

                    // Last-switched timestamp
                    if (lastSwitchTime > 0L && commandOutput.isEmpty()) {
                        val relativeTime = remember(lastSwitchTime) {
                            val diff = System.currentTimeMillis() - lastSwitchTime
                            val minutes = diff / 60_000
                            val hours   = diff / 3_600_000
                            val days    = diff / 86_400_000
                            when {
                                minutes < 1   -> "Graphics API last changed just now"
                                minutes < 60  -> "Graphics API last changed ${minutes}m ago"
                                hours   < 24  -> "Graphics API last changed ${hours}h ago"
                                days    == 1L -> "Graphics API last changed yesterday"
                                else          -> "Graphics API last changed ${days}d ago"
                            }
                        }
                        Text(
                            text = relativeTime,
                            color = colors.textSecondary.copy(alpha = 0.45f),
                            fontSize = ts.labelSmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (commandOutput.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, colors.border, Color.Transparent)
                                    )
                                )
                        )
                        Text(
                            text = commandOutput,
                            color = colors.textSecondary,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (!shizukuReady) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .background(stateColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("⚠️", fontSize = ts.labelMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!shizukuRunning) "Shizuku not running" else "Permission needed",
                                fontSize = ts.labelMedium,
                                color = stateColor,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Shizuku Help Dialog
@Composable
fun ShizukuHelpDialog(
    helpType: String,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
            .widthIn(max = 500.dp)
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { /* Block taps on card from dismissing dialog */ }
            },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 24.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
        ) {
            // Title
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (helpType == "not_running") "Shizuku Not Running" else "Permission Needed",
                    fontSize = ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary
                )
            }

            Text(
                text = when (helpType) {
                    "not_running" -> "Shizuku needs to be running for GAMA to work.\n\n1. Open the Shizuku app\n2. Tap 'Start' to activate the service\n3. Return to GAMA\n\nIf Shizuku won't start, follow the wireless debugging instructions in the Shizuku app."
                    "permission" -> "GAMA needs permission to use Shizuku.\n\n1. Open Shizuku\n2. Tap 'Authorized application'\n3. Find GAMA and enable it\n4. Close GAMA from your recents\n5. Reopen GAMA\n"
                    else -> "Unknown error"
                },
                fontSize = ts.bodyLarge,
                lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                color = colors.textPrimary.copy(alpha = 0.85f),
                fontFamily = quicksandFontFamily,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold, // Force Bold
                modifier = Modifier.fillMaxWidth()
            )

            DialogButton(
                text = "OK",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                cardBackground = cardBackground
            )
        }
    }
}

// Dialogs

@Composable
fun WarningDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing dialog */ }
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Just a sec!",
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )
                }

                Text(
                    text = "This will briefly restart System UI and other processes to apply the change. Your device will be back to normal in just a moment.",
                    fontSize = ts.bodyLarge,
                    lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                    DialogButton(
                        text = "Continue",
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
            }
        }
    }
}

// NEW: Success Dialog
@Composable
fun SuccessDialog(
    visible: Boolean,
    message: String,
    userName: String,
    isSwitching: Boolean = false,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts        = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current

    // ── Spinner — only runs while isSwitching ─────────────────────────────────
    val spinnerTransition = rememberInfiniteTransition(label = "success_spinner")
    val spinnerAngle by spinnerTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rot"
    )

    // ── Checkmark draw progress (0 → 1 once switching finishes) ──────────────
    // Driven by animateFloatAsState so it eases in smoothly after the spinner stops.
    val checkmarkProgress by animateFloatAsState(
        targetValue   = if (isSwitching) 0f else 1f,
        animationSpec = if (animLevel == 2) snap()
        else tween(durationMillis = 420, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
        label         = "checkmark_draw"
    )

    BouncyDialog(
        visible   = visible,
        // Block back-tap while the command is running — there is nothing to dismiss yet
        onDismiss = { if (!isSwitching) onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing */ }
                },
            colors    = CardDefaults.cardColors(containerColor = cardBackground),
            shape     = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // ── Icon: spinner arc while running, drawing checkmark on completion ──
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val iconColor  = colors.primaryAccent
                    val strokePx   = 3.dp.toPx()
                    val strokeWide = 4.dp.toPx()
                    val radius     = size.minDimension / 2f

                    if (isSwitching) {
                        // Faint background track
                        drawCircle(
                            color  = iconColor.copy(alpha = 0.15f),
                            radius = radius,
                            style  = Stroke(width = strokePx)
                        )
                        // 270° spinning arc
                        drawArc(
                            color      = iconColor.copy(alpha = 0.85f),
                            startAngle = spinnerAngle - 90f,
                            sweepAngle = 270f,
                            useCenter  = false,
                            style      = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    } else {
                        // Full circle fades in with the checkmark draw progress
                        drawCircle(
                            color  = iconColor.copy(alpha = checkmarkProgress),
                            radius = radius,
                            style  = Stroke(width = strokePx)
                        )

                        // Animated path: draw tick stroke proportionally to checkmarkProgress.
                        // Two segments: p1→p2 (downstroke), p2→p3 (upstroke).
                        val p1 = Offset(size.width * 0.25f, size.height * 0.50f)
                        val p2 = Offset(size.width * 0.45f, size.height * 0.70f)
                        val p3 = Offset(size.width * 0.75f, size.height * 0.35f)

                        val seg1 = kotlin.math.sqrt(
                            ((p2.x - p1.x).toDouble().let { it * it } + (p2.y - p1.y).toDouble().let { it * it })
                        ).toFloat()
                        val seg2 = kotlin.math.sqrt(
                            ((p3.x - p2.x).toDouble().let { it * it } + (p3.y - p2.y).toDouble().let { it * it })
                        ).toFloat()
                        val total  = seg1 + seg2
                        val drawn  = total * checkmarkProgress

                        if (drawn > 0f) {
                            val path = Path()
                            if (drawn <= seg1) {
                                val t = drawn / seg1
                                path.moveTo(p1.x, p1.y)
                                path.lineTo(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                            } else {
                                val t = (drawn - seg1) / seg2
                                path.moveTo(p1.x, p1.y)
                                path.lineTo(p2.x, p2.y)
                                path.lineTo(p2.x + (p3.x - p2.x) * t, p2.y + (p3.y - p2.y) * t)
                            }
                            drawPath(
                                path  = path,
                                color = iconColor,
                                style = Stroke(width = strokeWide, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                // ── Status text — crossfades between "Applying changes…" and final message ──
                AnimatedContent(
                    targetState  = isSwitching,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label        = "success_msg"
                ) { switching ->
                    Text(
                        text        = if (switching) "Applying changes…" else message,
                        fontSize    = ts.bodyLarge,
                        lineHeight  = (ts.bodyLarge.value * 1.4f).sp,
                        color       = colors.textPrimary.copy(alpha = if (switching) 0.55f else 0.9f),
                        modifier    = Modifier.fillMaxWidth(),
                        fontFamily  = quicksandFontFamily,
                        textAlign   = TextAlign.Center,
                        fontWeight  = FontWeight.Bold
                    )
                }

                // ── OK button — only appears once the switch is confirmed ─────────────────
                AnimatedVisibility(
                    visible = !isSwitching,
                    enter   = fadeIn(tween(280)) + expandVertically(tween(280, easing = FastOutSlowInEasing)),
                    exit    = fadeOut(tween(150))
                ) {
                    DialogButton(
                        text           = if (userName.isNotEmpty()) "Okay, $userName!" else "OK",
                        onClick        = onDismiss,
                        modifier       = Modifier.fillMaxWidth(),
                        colors         = colors,
                        cardBackground = cardBackground,
                        accent         = true
                    )
                }
            }
        }
    }
}

// NEW: Developer Menu Dialog
@Composable
fun DeveloperMenuDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTestNotification: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent,
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing dialog */ }
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                Text(
                    text = "Developer Mode",
                    fontSize = ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary
                )

                Text(
                    text = "Developer tools for testing and debugging.",
                    fontSize = ts.bodyMedium,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold // Force Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlatButton(
                    text = "Send Test Notification",
                    onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                    enabled = true,
                    colors = colors,
                    maxLines = 1
                )

                DialogButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    cardBackground = cardBackground,
                    accent = true
                )
            }
        }
    }
}

@Composable
fun GitHubDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVisitGitHub: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .border(
                    width = 1.dp,
                    color = colors.border,
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Block taps on card from dismissing dialog */ }
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // MODIFIED: Minimalist Circle Icon with Arrow
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val sizePx = size.minDimension
                    val stroke = 3.dp.toPx()
                    val color = colors.primaryAccent

                    // 1. Minimal circle
                    drawCircle(
                        color = color,
                        radius = sizePx / 2,
                        style = Stroke(width = stroke)
                    )

                    // 2. Simple arrow pointing top-right (External Link style)
                    val arrowPath = Path().apply {
                        // Start bottom-left-ish
                        moveTo(sizePx * 0.35f, sizePx * 0.65f)
                        // Line to top-right
                        lineTo(sizePx * 0.65f, sizePx * 0.35f)
                    }

                    // Arrowhead
                    val arrowHeadPath = Path().apply {
                        moveTo(sizePx * 0.45f, sizePx * 0.35f) // left of top-right
                        lineTo(sizePx * 0.65f, sizePx * 0.35f) // point
                        lineTo(sizePx * 0.65f, sizePx * 0.55f) // down from top-right
                    }

                    drawPath(
                        path = arrowPath,
                        color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )

                    drawPath(
                        path = arrowHeadPath,
                        color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Would you like to visit the GAMA repository on GitHub?",
                        fontSize = ts.bodyLarge,
                        lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth(),
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold // Force Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                    DialogButton(
                        text = "Sure!",
                        onClick = onVisitGitHub,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
            }
        }
    }
}

@Composable
fun ResourcesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (url: String, label: String, description: String) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    isBlurred: Boolean = false,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val resourcesScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "resources_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val animLevel = LocalAnimationLevel.current
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        val blurAmount by animateDpAsState(
            targetValue = if (isBlurred) 20.dp else 0.dp,
            animationSpec = if (animLevel == 2) snap<Dp>() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "resources_blur"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurAmount > 0.dp) Modifier.blur(blurAmount) else Modifier)
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                    else detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (isLandscape) 16.dp else backPadding)
                    .verticalScroll(resourcesScroll)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else if (isSmallScreen) 40.dp else 56.dp))
                CleanTitle(
                    text = "RESOURCES",
                    fontSize = ts.displayLarge,
                    colors = colors
                    ,
                    scrollOffset = resourcesScroll.value
                )
                Text(
                    text = "Links, community & more",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 4) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsNavigationCard(
                                title = "GITHUB REPOSITORY",
                                description = "Source code, releases & issue tracker",
                                onClick = {
                                    onLinkSelected(
                                        "https://github.com/popovicialinc/gama",
                                        "GitHub Repository",
                                        "GAMA is open source. The repository has the full source code, release history, and an issue tracker where bugs get filed and discussed. If something is broken or you want to see how it works under the hood, this is the place."
                                    )
                                },
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )
                            SettingsNavigationCard(
                                title = "SUGGESTION BOX",
                                description = "Have an idea? Tell us what you think",
                                onClick = {
                                    onLinkSelected(
                                        "https://docs.google.com/forms/d/e/1FAIpQLSdQIm49RciDKQ0ese2wWonMBZpOEFD_f3Ki0hNrprZjjGSdKQ/viewform",
                                        "Suggestion Box",
                                        "Got a feature idea, something that feels off, or just an opinion about how GAMA works? This is a short form and every response actually gets read. No account needed."
                                    )
                                },
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        SettingsNavigationCard(
                            title = "GITHUB REPOSITORY",
                            description = "Source code, releases & issue tracker",
                            onClick = {
                                onLinkSelected(
                                    "https://github.com/popovicialinc/gama",
                                    "GitHub Repository",
                                    "GAMA is open source. The repository has the full source code, release history, and an issue tracker where bugs get filed and discussed. If something is broken or you want to see how it works under the hood, this is the place."
                                )
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                }
                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 4) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingsNavigationCard(
                                title = "VULKAN COMPATIBILITY",
                                description = "Does Vulkan work on my device? 📱",
                                onClick = {
                                    onLinkSelected(
                                        "https://docs.google.com/spreadsheets/d/1X_UuSJBWc9O2Q9nW0x-V_WC0uY-yKDfNRkxgko8i6AA/edit?gid=242381638#gid=242381638",
                                        "Vulkan Compatibility List",
                                        "Vulkan doesn't behave the same on every Android device — some phones run it flawlessly, others see graphical glitches, crashes, or no improvement at all. This list documents known Vulkan behaviour across real devices so you know what to expect before switching renderers."
                                    )
                                },
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )
                            SettingsNavigationCard(
                                title = "MY DEVICE REPORT 📋",
                                description = "Something feels off? Tell us about it!",
                                onClick = {
                                    onLinkSelected(
                                        "https://docs.google.com/forms/d/e/1FAIpQLSf4rtIaJZCHFsgfiJDptRgG4wJAk6CReM85GXqHG_GyEKa5aA/viewform",
                                        "Device Report Form",
                                        "If Vulkan is crashing, causing visual glitches, or doing something unexpected on your device, reporting it adds your hardware to the known behaviour list. It takes two minutes and helps narrow down what actually works across different phones."
                                    )
                                },
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        SettingsNavigationCard(
                            title = "SUGGESTION BOX",
                            description = "Have an idea? Tell us what you think",
                            onClick = {
                                onLinkSelected(
                                    "https://docs.google.com/forms/d/e/1FAIpQLSdQIm49RciDKQ0ese2wWonMBZpOEFD_f3Ki0hNrprZjjGSdKQ/viewform",
                                    "Suggestion Box",
                                    "Got a feature idea, something that feels off, or just an opinion about how GAMA works? This is a short form and every response actually gets read. No account needed."
                                )
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                }
                AnimatedElement(visible = visible, staggerIndex = 3,
                    totalItems = 4) {
                    if (!isLandscape) {
                        SettingsNavigationCard(
                            title = "VULKAN COMPATIBILITY",
                            description = "Does Vulkan work on my device? 📱",
                            onClick = {
                                onLinkSelected(
                                    "https://docs.google.com/spreadsheets/d/1X_UuSJBWc9O2Q9nW0x-V_WC0uY-yKDfNRkxgko8i6AA/edit?gid=242381638#gid=242381638",
                                    "Vulkan Compatibility List",
                                    "Vulkan doesn't behave the same on every Android device — some phones run it flawlessly, others see graphical glitches, crashes, or no improvement at all. This list documents known Vulkan behaviour across real devices so you know what to expect before switching renderers."
                                )
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                }
                AnimatedElement(visible = visible, staggerIndex = 4,
                    totalItems = 4) {
                    if (!isLandscape) {
                        SettingsNavigationCard(
                            title = "MY DEVICE REPORT 📋",
                            description = "Something feels off? Tell us about it!",
                            onClick = {
                                onLinkSelected(
                                    "https://docs.google.com/forms/d/e/1FAIpQLSf4rtIaJZCHFsgfiJDptRgG4wJAk6CReM85GXqHG_GyEKa5aA/viewform",
                                    "Device Report Form",
                                    "If Vulkan is crashing, causing visual glitches, or doing something unexpected on your device, reporting it adds your hardware to the known behaviour list. It takes two minutes and helps narrow down what actually works across different phones."
                                )
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = resourcesScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun ExternalLinkConfirmDialog(
    visible: Boolean,
    label: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
            ) {
                // Title — matches ShizukuHelpDialog style exactly
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You're leaving GAMA",
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )
                }

                Text(
                    text = description,
                    fontSize = ts.bodyLarge,
                    lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                    DialogButton(
                        text = "Open",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true
                    )
                }
            }
        }
    }
}

@Composable
fun ChangelogDialog(
    visible: Boolean,
    changelogText: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDeveloperModeTrigger: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        // ── Shared state ──────────────────────────────────────────────────────
        var versionStatus by remember { mutableStateOf("Checking version...") }
        val scope = rememberCoroutineScope()
        LaunchedEffect(changelogText) {
            versionStatus = "Checking version..."
            delay(300)
            versionStatus = ShizukuHelper.checkVersion("1.2")
        }
        var isRefreshing by remember { mutableStateOf(false) }

        // Breathing glow behind card header
        val glowPulse = rememberInfiniteTransition(label = "cl_glow")
        val glowAlpha by glowPulse.animateFloat(
            initialValue = 0.10f, targetValue = 0.22f,
            animationSpec = infiniteRepeatable(
                tween(2000, easing = MotionTokens.Easing.silk), RepeatMode.Reverse
            ), label = "cl_glow_a"
        )

        // ── Version pill colors ───────────────────────────────────────────────
        val isUpdateAvailable = versionStatus.contains("available") || versionStatus.contains("update")
        val isUpToDate = versionStatus.contains("✅") || versionStatus.contains("latest")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                    else detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {
            // ── Outer glow halo behind the card ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape && !isTablet) 0.84f else 0.93f)
                    .fillMaxHeight(if (isLandscape) 0.89f else 0.87f)
                    .widthIn(max = if (isLandscape) 860.dp else 560.dp)
                    .blur(40.dp, BlurredEdgeTreatment.Unbounded)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.9f),
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.25f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // ── Card ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape && !isTablet) 0.84f else 0.93f)
                    .fillMaxHeight(if (isLandscape) 0.89f else 0.87f)
                    .widthIn(max = if (isLandscape) 860.dp else 560.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(cardBackground)
            ) {
                if (isLandscape) {
                    // ── LANDSCAPE: left sidebar + right scroll ────────────────
                    Row(modifier = Modifier.fillMaxSize()) {

                        // Left sidebar — tinted with a soft accent wash
                        Box(
                            modifier = Modifier
                                .width(if (isSmallScreen) 188.dp else 216.dp)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            colors.primaryAccent.copy(alpha = if (oledMode) 0.05f else 0.07f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 22.dp, end = 18.dp, top = 28.dp, bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // ── Title block ───────────────────────────────
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    // Accent pip above title
                                    Box(
                                        modifier = Modifier
                                            .width(28.dp).height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        colors.primaryAccent.copy(alpha = 0.4f),
                                                        colors.primaryAccent,
                                                        colors.primaryAccent.copy(alpha = 0.4f)
                                                    )
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "CHANGELOG",
                                        fontSize = if (isSmallScreen) ts.headlineMedium else ts.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textPrimary,
                                        letterSpacing = 2.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        "WHAT'S NEW?",
                                        fontSize = ts.labelSmall,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.primaryAccent.copy(alpha = 0.75f),
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    // Version pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isUpdateAvailable) colors.primaryAccent.copy(alpha = 0.16f)
                                                else colors.textSecondary.copy(alpha = 0.07f)
                                            )
                                            .border(
                                                0.75.dp,
                                                if (isUpdateAvailable) colors.primaryAccent.copy(alpha = 0.45f)
                                                else colors.border.copy(alpha = 0.25f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            versionStatus,
                                            fontSize = ts.labelSmall,
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            color = when {
                                                isUpToDate -> colors.textSecondary.copy(alpha = 0.55f)
                                                isUpdateAvailable -> colors.primaryAccent
                                                else -> colors.textSecondary.copy(alpha = 0.45f)
                                            }
                                        )
                                    }
                                }

                                // ── Bottom buttons ────────────────────────────
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Hairline divider
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.55f).height(1.dp)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        colors.primaryAccent.copy(alpha = 0.3f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ChangelogIconButton(
                                                isRefreshing = isRefreshing, isDeveloper = false,
                                                onClick = { scope.launch { isRefreshing = true; onRefresh(); delay(1000); isRefreshing = false } },
                                                colors = colors, isSmallScreen = isSmallScreen
                                            )
                                            Text(
                                                if (isRefreshing) "CHECKING..." else "CHECK FOR\nUPDATES",
                                                fontSize = 8.sp,
                                                fontFamily = quicksandFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp,
                                                color = colors.primaryAccent.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ChangelogIconButton(
                                                isRefreshing = false, isDeveloper = true,
                                                onClick = { onDeveloperModeTrigger() },
                                                colors = colors, isSmallScreen = isSmallScreen
                                            )
                                            Text(
                                                "DEVELOPER",
                                                fontSize = 8.sp,
                                                fontFamily = quicksandFontFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.8.sp,
                                                color = colors.textSecondary.copy(alpha = 0.38f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Hairline vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.84f)
                                .align(Alignment.CenterVertically)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.primaryAccent.copy(alpha = 0.22f),
                                            colors.primaryAccent.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Right — scrollable changelog with fade overlays painted at draw time
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            listOf(cardBackground, cardBackground.copy(alpha = 0f)),
                                            startY = 0f, endY = 40.dp.toPx()
                                        )
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            listOf(cardBackground.copy(alpha = 0f), cardBackground),
                                            startY = size.height - 52.dp.toPx(), endY = size.height
                                        )
                                    )
                                }
                        ) {
                            val isLoading = changelogText == "Loading..."
                            if (isLoading) {
                                ChangelogShimmer(colors = colors, isSmallScreen = isSmallScreen)
                            } else {
                                val lines = remember(changelogText) { changelogText.lines() }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 30.dp, end = 30.dp,
                                        top = 8.dp, bottom = 48.dp
                                    )
                                ) {
                                    items(lines) { line ->
                                        ChangelogLineItem(
                                            line = line, ts = ts, colors = colors,
                                            isSmallScreen = isSmallScreen, compact = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // ── PORTRAIT ─────────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize()) {

                        // ── Accent top bar ────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.primaryAccent.copy(alpha = 0.7f),
                                            colors.primaryAccent,
                                            colors.primaryAccent.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // ── Header ────────────────────────────────────────────
                        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = if (isSmallScreen) 24.dp else 32.dp,
                                        start = 32.dp, end = 32.dp,
                                        bottom = if (isSmallScreen) 18.dp else 24.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "CHANGELOG",
                                    fontSize = if (isSmallScreen) ts.displaySmall else ts.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = quicksandFontFamily,
                                    color = colors.textPrimary,
                                    letterSpacing = 4.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "WHAT'S NEW IN GAMA?",
                                    fontSize = ts.labelSmall,
                                    fontFamily = quicksandFontFamily,
                                    color = colors.primaryAccent.copy(alpha = 0.72f),
                                    letterSpacing = 2.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Version status pill with border
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isUpdateAvailable) colors.primaryAccent.copy(alpha = 0.15f)
                                            else colors.textSecondary.copy(alpha = 0.07f)
                                        )
                                        .border(
                                            0.75.dp,
                                            if (isUpdateAvailable) colors.primaryAccent.copy(alpha = 0.4f)
                                            else colors.border.copy(alpha = 0.25f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        versionStatus,
                                        fontSize = ts.labelSmall,
                                        fontFamily = quicksandFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        color = when {
                                            isUpToDate -> colors.textSecondary.copy(alpha = 0.55f)
                                            isUpdateAvailable -> colors.primaryAccent
                                            else -> colors.textSecondary.copy(alpha = 0.45f)
                                        }
                                    )
                                }
                            }
                        }

                        // Accent divider under header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.primaryAccent.copy(alpha = 0.35f),
                                            colors.primaryAccent.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // ── Scrollable body ───────────────────────────────────
                        AnimatedElement(
                            visible = visible, staggerIndex = 2, totalItems = 3,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                listOf(cardBackground, cardBackground.copy(alpha = 0f)),
                                                startY = 0f, endY = 40.dp.toPx()
                                            )
                                        )
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                listOf(cardBackground.copy(alpha = 0f), cardBackground),
                                                startY = size.height - 68.dp.toPx(), endY = size.height
                                            )
                                        )
                                    }
                            ) {
                                val isLoading = changelogText == "Loading..."
                                if (isLoading) {
                                    ChangelogShimmer(colors = colors, isSmallScreen = isSmallScreen)
                                } else {
                                    val lines = remember(changelogText) { changelogText.lines() }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 32.dp, end = 32.dp,
                                            top = 8.dp, bottom = 56.dp
                                        )
                                    ) {
                                        items(lines) { line ->
                                            ChangelogLineItem(
                                                line = line, ts = ts, colors = colors,
                                                isSmallScreen = isSmallScreen, compact = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Bottom action bar ─────────────────────────────────
                        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 3) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Accent divider above buttons
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .height(1.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    colors.primaryAccent.copy(alpha = 0.30f),
                                                    colors.primaryAccent.copy(alpha = 0.30f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        ChangelogIconButton(
                                            isRefreshing = isRefreshing, isDeveloper = false,
                                            onClick = { scope.launch { isRefreshing = true; onRefresh(); delay(1000); isRefreshing = false } },
                                            colors = colors, isSmallScreen = isSmallScreen
                                        )
                                        Text(
                                            if (isRefreshing) "CHECKING..." else "CHECK FOR\nUPDATES",
                                            fontSize = if (isSmallScreen) 8.sp else 9.sp,
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            color = colors.primaryAccent.copy(alpha = 0.72f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        ChangelogIconButton(
                                            isRefreshing = false, isDeveloper = true,
                                            onClick = { onDeveloperModeTrigger() },
                                            colors = colors, isSmallScreen = isSmallScreen
                                        )
                                        Text(
                                            "DEVELOPER",
                                            fontSize = if (isSmallScreen) 8.sp else 9.sp,
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp,
                                            color = colors.textSecondary.copy(alpha = 0.38f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(if (isSmallScreen) 14.dp else 18.dp))
                            }
                        }
                    }
                }
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

// Renders a single parsed markdown line from the changelog.
// Used by both portrait and landscape LazyColumns so formatting is consistent.
@Composable
private fun ChangelogLineItem(
    line: String,
    ts: AdaptiveTypeScale,
    colors: ThemeColors,
    isSmallScreen: Boolean,
    compact: Boolean           // true = landscape (smaller sizes)
) {
    val headerSize   = if (compact) ts.labelLarge  else if (isSmallScreen) ts.bodyMedium else ts.bodyLarge
    val bodySize     = if (compact) ts.labelMedium else if (isSmallScreen) ts.bodyMedium else ts.bodyLarge
    val lineHeightSp = (bodySize.value * 1.45f).sp
    val topSpacing   = if (compact) 20.dp          else 28.dp

    when {
        line.startsWith("## ") -> {
            Spacer(modifier = Modifier.height(topSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Accent left bar beside section headers
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(headerSize.value.times(1.15f).dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(colors.primaryAccent)
                )
                Text(
                    line.removePrefix("## ").trim(),
                    fontSize = headerSize,
                    fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent.copy(alpha = 0.95f),
                    letterSpacing = if (compact) 0.6.sp else 1.2.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        line.startsWith("- ") -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 5.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Round filled bullet
                Box(
                    modifier = Modifier
                        .padding(top = bodySize.value.times(0.38f).dp)
                        .size(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.55f))
                )
                Text(
                    line.removePrefix("- ").trim(),
                    fontSize = bodySize,
                    lineHeight = lineHeightSp,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        line.isNotBlank() -> {
            Text(
                line,
                fontSize = bodySize,
                lineHeight = lineHeightSp,
                fontFamily = quicksandFontFamily,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }
        else -> Spacer(modifier = Modifier.height(6.dp))
    }
}


// Shimmer placeholder for changelog content while fetching from GitHub.
// Uses the same left-to-right sweeping band as the renderer-name skeleton,
// giving a consistent loading visual language throughout the app.
@Composable
fun ChangelogShimmer(colors: ThemeColors, isSmallScreen: Boolean) {
    val shimmerTransition = rememberInfiniteTransition(label = "changelog_shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue = -1f,
        targetValue  =  1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "changelog_shimmer_x"
    )

    // Row widths: vary to look like real text lines
    val rowWidths = remember {
        listOf(0.55f, 0.90f, 0.82f, 0.75f, 0.68f, 0.88f, 0.60f, 0.78f, 0.50f, 0.85f, 0.70f, 0.65f)
    }
    val rowHeight = if (isSmallScreen) 12.dp else 14.dp
    val headerHeight = if (isSmallScreen) 16.dp else 18.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fake section header
        Box(
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(headerHeight)
                .clip(RoundedCornerShape(6.dp))
                .drawWithContent {
                    drawRoundRect(color = colors.primaryAccent.copy(alpha = 0.14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    val bandW = size.width * 0.6f
                    val cx = shimmerX * (size.width + bandW) * 0.5f + size.width * 0.5f
                    drawRect(brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, colors.primaryAccent.copy(alpha = 0.30f), Color.Transparent),
                        startX = cx - bandW * 0.5f, endX = cx + bandW * 0.5f))
                }
        )
        Spacer(modifier = Modifier.height(4.dp))
        rowWidths.forEachIndexed { index, fraction ->
            // Insert a section-header-sized placeholder every 5 rows
            if (index == 6) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(headerHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .drawWithContent {
                            drawRoundRect(color = colors.primaryAccent.copy(alpha = 0.14f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                            val bandW = size.width * 0.6f
                            val cx = shimmerX * (size.width + bandW) * 0.5f + size.width * 0.5f
                            drawRect(brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, colors.primaryAccent.copy(alpha = 0.30f), Color.Transparent),
                                startX = cx - bandW * 0.5f, endX = cx + bandW * 0.5f))
                        }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(rowHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        drawRoundRect(color = colors.textSecondary.copy(alpha = 0.10f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                        val bandW = size.width * 0.55f
                        val cx = shimmerX * (size.width + bandW) * 0.5f + size.width * 0.5f
                        drawRect(brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, colors.textSecondary.copy(alpha = 0.22f), Color.Transparent),
                            startX = cx - bandW * 0.5f, endX = cx + bandW * 0.5f))
                    }
            )
        }
    }
}


@Composable
fun ChangelogIconButton(
    isRefreshing: Boolean,
    isDeveloper: Boolean,
    onClick: () -> Unit,
    colors: ThemeColors,
    isSmallScreen: Boolean
) {
    val buttonSize = if (isSmallScreen) 48.dp else 54.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Rotation: spins continuously and smoothly while refreshing
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_infinite")
    val spinningRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = CubicBezierEasing(0.4f, 0.0f, 0.6f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinning_rot"
    )

    // Idle breathing scale for the refresh icon when not spinning
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (!isDeveloper && !isRefreshing) 1.06f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath"
    )

    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = if (!isDeveloper) 0.38f else 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Smooth animated rotation: snaps to spinning value when refreshing, snaps back to 0 when done
    val rotation by animateFloatAsState(
        targetValue = if (!isDeveloper && isRefreshing) spinningRotation else 0f,
        animationSpec = if (isRefreshing) tween(0) else spring(
            dampingRatio = 0.5f,
            stiffness = 200f
        ),
        label = "refresh_rot"
    )

    // Button press scale
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else if (!isDeveloper && !isRefreshing) idleBreath else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "press_scale"
    )

    val glowSize = buttonSize * 1.8f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(glowSize)
    ) {
        // Animated glow blob behind button
        Box(
            modifier = Modifier
                .size(glowSize)
                .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = glowAlpha),
                            colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Button surface
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(1.5.dp, colors.primaryAccent.copy(alpha = 0.4f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (isDeveloper || !isRefreshing) {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            }
                        },
                        onTap = {
                            if (isDeveloper || !isRefreshing) onClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer { rotationZ = if (!isDeveloper) rotation else 0f }
            ) {
                val sw = 2.4.dp.toPx()
                val cx = size.width / 2
                val cy = size.height / 2

                if (!isDeveloper) {
                    // Clean refresh icon:
                    // A single ~300° arc (leaving a gap at top-right where the arrowhead sits),
                    // with one crisp filled arrowhead at the arc's end — simple and sharp.

                    val r = size.minDimension * 0.36f

                    // Arc: starts just past the arrowhead gap and sweeps ~295° clockwise
                    // Gap is at ~-55° (top-right area). Arc runs from -55° + a tiny tail buffer.
                    val arcStart = -50f
                    val arcSweep = 295f

                    drawArc(
                        color = colors.primaryAccent,
                        startAngle = arcStart,
                        sweepAngle = arcSweep,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2, r * 2)
                    )

                    // Arrowhead at the arc's end (arcStart + arcSweep = 245°)
                    run {
                        val endAngleDeg = (arcStart + arcSweep).toDouble()
                        val endRad = Math.toRadians(endAngleDeg)
                        // Point on circle where arc ends
                        val ax = (cx + r * cos(endRad)).toFloat()
                        val ay = (cy + r * sin(endRad)).toFloat()
                        // Clockwise tangent direction at that point = endAngle + 90°
                        val tRad = Math.toRadians(endAngleDeg + 90.0)
                        val tx = cos(tRad).toFloat()
                        val ty = sin(tRad).toFloat()
                        // Inward normal (perpendicular, toward center)
                        val nx = -ty
                        val ny = tx
                        val al = 3.6.dp.toPx()  // arrowhead arm length
                        val aw = al * 0.6f       // arrowhead half-width
                        // Tip slightly forward along tangent, base pulled back
                        val arrowPath = Path().apply {
                            moveTo(ax + tx * al * 0.6f, ay + ty * al * 0.6f)           // tip
                            lineTo(ax - tx * al * 0.4f + nx * aw, ay - ty * al * 0.4f + ny * aw) // base left
                            lineTo(ax - tx * al * 0.4f - nx * aw, ay - ty * al * 0.4f - ny * aw) // base right
                            close()
                        }
                        drawPath(arrowPath, colors.primaryAccent)
                    }

                } else {
                    // Sparkle / developer icon (unchanged)
                    val ml = size.minDimension / 2; val sl = ml * 0.6f
                    drawLine(colors.primaryAccent, Offset(cx, cy - ml), Offset(cx, cy + ml), sw, StrokeCap.Round)
                    drawLine(colors.primaryAccent, Offset(cx - ml, cy), Offset(cx + ml, cy), sw, StrokeCap.Round)
                    drawLine(colors.primaryAccent, Offset(cx - sl, cy - sl), Offset(cx + sl, cy + sl), sw * 0.8f, StrokeCap.Round)
                    drawLine(colors.primaryAccent, Offset(cx + sl, cy - sl), Offset(cx - sl, cy + sl), sw * 0.8f, StrokeCap.Round)
                    drawCircle(colors.primaryAccent, 2.dp.toPx(), Offset(cx, cy))
                }
            }
        }
    }
}

// New Advanced Settings Panel
@Composable
fun VisualEffectsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    themePreference: Int,
    onThemeChange: (Int) -> Unit,
    animationLevel: Int,
    onAnimationLevelChange: (Int) -> Unit,
    uiScale: Int,
    onUiScaleChange: (Int) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
    oledMode: Boolean,
    onOledModeChange: (Boolean) -> Unit,
    onColorCustomizationClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onParticlesClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val visualsScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "visuals_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(visualsScroll)
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp)) // Added top spacer for status bar
                CleanTitle(
                    text = "VISUALS",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false,
                    scrollOffset = visualsScroll.value
                )

                Text(
                    text = "Adjust theme, colors, animations, and visual effects",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // OLED Mode Toggle - always full width
                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 6) {
                    ToggleCard(
                        title = "OLED MODE",
                        description = "Pure black backgrounds for OLED screens",
                        checked = oledMode,
                        onCheckedChange = { enabled ->
                            performHaptic()
                            onOledModeChange(enabled)
                        },
                        colors = colors,
                        cardBackground = cardBackground,
                        isSmallScreen = isSmallScreen,
                        oledMode = oledMode
                    )
                }

                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 6) {
                    if (isLandscape) {
                        // Two-column layout for navigation cards in landscape
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left column
                            SettingsNavigationCard(
                                title = "COLORS",
                                description = if (oledMode) "Customize accent color" else "Customize accents & gradients",
                                onClick = onColorCustomizationClick,
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )

                            // Right column
                            SettingsNavigationCard(
                                title = "EFFECTS",
                                description = "Blur, gradients & particles",
                                onClick = onEffectsClick,
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // Single column for portrait
                        Column(verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)) {
                            SettingsNavigationCard(
                                title = "COLORS",
                                description = if (oledMode) "Customize accent color" else "Customize accents & gradients",
                                onClick = onColorCustomizationClick,
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode
                            )

                            SettingsNavigationCard(
                                title = "EFFECTS",
                                description = "Blur, gradients & particles",
                                onClick = onEffectsClick,
                                isSmallScreen = isSmallScreen,
                                colors = colors,
                                cardBackground = cardBackground,
                                enabled = true,
                                oledMode = oledMode
                            )
                        }
                    }
                }


                // Theme Selector
                AnimatedElement(visible = visible, staggerIndex = 3,
                    totalItems = 6) {
                    val themeCardAlpha by animateFloatAsState(
                        targetValue = if (oledMode) 0.25f else 1f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "theme_card_alpha"
                    )

                    val themeCardScale by animateFloatAsState(
                        targetValue = if (oledMode) 0.85f else 1f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "theme_card_scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(
                                scaleX = themeCardScale,
                                scaleY = themeCardScale,
                                alpha  = themeCardAlpha   // whole card fades like Vulkan button
                            )
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .then(if (oledMode) Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                    }
                                }
                            } else Modifier)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "THEME",
                                            color = colors.primaryAccent.copy(alpha = 0.7f),
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            fontFamily = quicksandFontFamily
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (oledMode) {
                                                "OLED mode forces dark theme for pure black backgrounds"
                                            } else {
                                                "Choose between light, dark, or system theme"
                                            },
                                            fontSize = ts.bodyMedium,
                                            color = colors.textSecondary.copy(alpha = 0.7f),
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = (ts.bodyMedium.value * 1.4f).sp
                                        )
                                    }
                                }
                                GlideOptionSelector(
                                    options = listOf("Auto", "Dark", "Light"),
                                    selectedIndex = themePreference,
                                    onOptionSelected = {
                                        performHaptic()
                                        onThemeChange(it)
                                    },
                                    colors = colors,
                                    enabled = true
                                )
                            }
                        }
                    } // end border Box
                } // end AnimatedElement staggerIndex=3

                AnimatedElement(visible = visible, staggerIndex = 4,
                    totalItems = 6) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("ANIMATION SPEED", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                            GlideOptionSelector(listOf("Normal", "Fast", "Disabled"), animationLevel, { performHaptic(); onAnimationLevelChange(it) }, colors)
                        }
                    }
                } // end AnimatedElement staggerIndex=4

                AnimatedElement(visible = visible, staggerIndex = 5,
                    totalItems = 6) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("UI SCALE", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                            GlideOptionSelector(listOf("75%", "100%", "125%"), uiScale, { performHaptic(); onUiScaleChange(it) }, colors)
                        }
                    }
                } // end AnimatedElement staggerIndex=5

                AnimatedElement(visible = visible, staggerIndex = 6,
                    totalItems = 6) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("PERSONALIZATION", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)

                            Text(
                                text = "Add your name for a personalized experience throughout the app.",
                                color = colors.textSecondary,
                                fontSize = ts.labelLarge,
                                fontFamily = quicksandFontFamily,
                                lineHeight = (ts.labelLarge.value * 1.4f).sp,
                                fontWeight = FontWeight.Bold // Force Bold
                            )

                            OutlinedTextField(
                                value = userName,
                                onValueChange = onUserNameChange,
                                label = { Text("Your Name", fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedBorderColor = colors.primaryAccent,
                                    unfocusedBorderColor = colors.border,
                                    cursorColor = colors.primaryAccent,
                                    focusedLabelColor = colors.primaryAccent,
                                    unfocusedLabelColor = colors.textSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } // end AnimatedElement staggerIndex=6

            } // end AnimatedElement staggerIndex=3

            Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = visualsScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

// New Effects Panel
@Composable
fun EffectsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    onParticlesClick: () -> Unit,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val effectsScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "effects_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(effectsScroll)
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "EFFECTS",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false
                    ,
                    scrollOffset = effectsScroll.value
                )

                Text(
                    text = "Control blur, grain, and animation quality",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )



                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 3) {
                    if (isLandscape) {
                        // Two-column layout for landscape
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ToggleCard(
                                    title = "BLUR",
                                    description = "Background blur effect",
                                    checked = blurEnabled,
                                    onCheckedChange = { enabled ->
                                        performHaptic()
                                        onBlurChange(enabled)
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ToggleCard(
                                    title = "GRADIENT",
                                    description = if (oledMode) "Disabled in OLED mode" else "Breathing gradient background",
                                    checked = gradientEnabled,
                                    onCheckedChange = { enabled ->
                                        performHaptic()
                                        onGradientChange(enabled)
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode,
                                    enabled = !oledMode
                                )
                            }
                        }
                    } else {
                        // Single column for portrait — Blur is slot 1
                        ToggleCard(
                            title = "BLUR",
                            description = "Background blur effect",
                            checked = blurEnabled,
                            onCheckedChange = { enabled ->
                                performHaptic()
                                onBlurChange(enabled)
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )
                    }
                } // end AnimatedElement staggerIndex=1

                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 3) {
                    if (isLandscape) {
                        // Particles nav card full width below
                        SettingsNavigationCard(
                            title = "PARTICLES",
                            description = if (oledMode) "Disabled in OLED mode" else "Configure particle system",
                            onClick = {
                                performHaptic()
                                onParticlesClick()
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = !oledMode,
                            oledMode = oledMode
                        )
                    } else {
                        ToggleCard(
                            title = "GRADIENT",
                            description = if (oledMode) "Disabled in OLED mode" else "Breathing gradient background",
                            checked = gradientEnabled,
                            onCheckedChange = { enabled ->
                                performHaptic()
                                onGradientChange(enabled)
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode,
                            enabled = !oledMode
                        )
                    }
                } // end AnimatedElement staggerIndex=2

                if (!isLandscape) {
                    AnimatedElement(visible = visible, staggerIndex = 3,
                        totalItems = 3) {
                        SettingsNavigationCard(
                            title = "PARTICLES",
                            description = if (oledMode) "Disabled in OLED mode" else "Configure particle system",
                            onClick = {
                                performHaptic()
                                onParticlesClick()
                            },
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = !oledMode,
                            oledMode = oledMode
                        )
                    } // end AnimatedElement staggerIndex=3
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = effectsScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun ParticlesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    onParticlesChange: (Boolean) -> Unit,
    particleSpeed: Int,
    onParticleSpeedChange: (Int) -> Unit,
    particleParallaxEnabled: Boolean,
    onParticleParallaxChange: (Boolean) -> Unit,
    particleParallaxSensitivity: Int,
    onParticleParallaxSensitivityChange: (Int) -> Unit,
    particleCount: Int,
    onParticleCountChange: (Int) -> Unit,
    particleStarMode: Boolean,
    onParticleStarModeChange: (Boolean) -> Unit,
    particleTimeMode: Boolean,
    onParticleTimeModeChange: (Boolean) -> Unit,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val particlesScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "particles_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(particlesScroll)
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "PARTICLES",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false
                    ,
                    scrollOffset = particlesScroll.value
                )

                Text(
                    text = "Configure animated background particles",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Enable/Disable Particles
                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 8) {
                    ToggleCard(
                        title = "PARTICLES",
                        description = if (oledMode) "Disabled in OLED mode" else "Show animated particles in background",
                        checked = particlesEnabled && !oledMode,
                        onCheckedChange = {
                            if (!oledMode) {
                                performHaptic()
                                onParticlesChange(it)
                            }
                        },
                        colors = colors,
                        cardBackground = cardBackground,
                        isSmallScreen = isSmallScreen,
                        oledMode = oledMode,
                        enabled = !oledMode
                    )
                } // end AnimatedElement staggerIndex=1

                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 8) {
                    AnimatedVisibility(visible = particlesEnabled && !oledMode) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                        ) {
                            // Star Mode Toggle
                            AnimatedElement(visible = visible, staggerIndex = 3,
                                totalItems = 8) {
                                AnimatedContent(
                                    targetState = particleTimeMode,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) togetherWith
                                                fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing))
                                    },
                                    label = "star_mode_description"
                                ) { timeMode ->
                                    ToggleCard(
                                        title = "STAR MODE",
                                        description = if (timeMode) "Night sky mode (linked to Time System)" else "Drift like stars in the night sky",
                                        checked = particleStarMode,
                                        onCheckedChange = {
                                            performHaptic()
                                            onParticleStarModeChange(it)
                                        },
                                        colors = colors,
                                        cardBackground = cardBackground,
                                        isSmallScreen = isSmallScreen,
                                        oledMode = oledMode,
                                        enabled = !timeMode
                                    )
                                }
                            }

                            // Time-based Sun & Moon System Toggle
                            AnimatedElement(visible = visible, staggerIndex = 4,
                                totalItems = 8) {
                                ToggleCard(
                                    title = "TIME SYSTEM",
                                    description = "Sun and moon follow real-time, particles adapt to day/night",
                                    checked = particleTimeMode,
                                    onCheckedChange = {
                                        performHaptic()
                                        onParticleTimeModeChange(it)
                                        if (it && particleStarMode) {
                                            onParticleStarModeChange(false)
                                        }
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )
                            }

                            // Parallax Effect Toggle
                            AnimatedElement(visible = visible, staggerIndex = 5,
                                totalItems = 8) {
                                ToggleCard(
                                    title = "PARALLAX EFFECT",
                                    description = "Particles respond to device rotation",
                                    checked = particleParallaxEnabled,
                                    onCheckedChange = {
                                        performHaptic()
                                        onParticleParallaxChange(it)
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )
                            }

                            // Parallax Sensitivity (only shown when parallax is enabled)
                            AnimatedVisibility(visible = particleParallaxEnabled) {
                                AnimatedElement(visible = visible, staggerIndex = 6,
                                    totalItems = 8) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = if (oledMode) 0.75.dp else 1.dp,
                                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                                shape = RoundedCornerShape(18.dp)
                                            ),
                                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text("PARALLAX SENSITIVITY", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                            GlideOptionSelector(listOf("Low", "Medium", "High"), particleParallaxSensitivity, { performHaptic(); onParticleParallaxSensitivityChange(it) }, colors)
                                        }
                                    }
                                }
                            }

                            // Particle Speed
                            AnimatedElement(visible = visible, staggerIndex = 7,
                                totalItems = 8) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (oledMode) 0.75.dp else 1.dp,
                                            color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                            shape = RoundedCornerShape(18.dp)
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("PARTICLE VELOCITY", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                        GlideOptionSelector(listOf("Low", "Medium", "High"), particleSpeed, { performHaptic(); onParticleSpeedChange(it) }, colors)
                                    }
                                }
                            }

                            // Particle Count
                            AnimatedElement(visible = visible, staggerIndex = 8,
                                totalItems = 8) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (oledMode) 0.75.dp else 1.dp,
                                            color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                            shape = RoundedCornerShape(18.dp)
                                        ),
                                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("PARTICLE COUNT", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = ts.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                        GlideOptionSelector(listOf("Low", "Medium", "High"), particleCount, { performHaptic(); onParticleCountChange(it) }, colors)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    } // end AnimatedVisibility
                } // end AnimatedElement staggerIndex=2

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
            } // end Column

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = particlesScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        } // end Box
    } // end BouncyDialog
}

// REMOVED EffectsPanel as it is now merged into VisualEffectsPanel

@Composable
fun ColorCustomizationPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    customAccentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    customGradientStart: Color,
    onGradientStartChange: (Color) -> Unit,
    customGradientEnd: Color,
    onGradientEndChange: (Color) -> Unit,
    isDarkTheme: Boolean,
    performHaptic: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val panelDuration = when (animLevel) {
        0 -> 585  // Max quality
        1 -> 450  // Medium quality
        else -> 0 // None
    }
    // Hoisted above BouncyDialog — created before enter animation starts
    val colorScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "colors_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 700.dp else 500.dp)
                    .verticalScroll(colorScroll)
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 40.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Added top spacer for status bar
                CleanTitle(
                    text = "COLORS",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false
                    ,
                    scrollOffset = colorScroll.value
                )

                // Animated visibility for description text when OLED mode changes
                AnimatedVisibility(
                    visible = !oledMode,
                    enter = fadeIn(animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate))
                ) {
                    Text(
                        text = "Customize accent colors and gradients",
                        fontSize = ts.labelLarge,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AnimatedElement(visible = visible, staggerIndex = 1,
                        totalItems = 2) {
                        ToggleCard(
                            title = "DYNAMIC COLORS",
                            description = "Match your wallpaper colors",
                            checked = useDynamicColor,
                            onCheckedChange = onDynamicColorChange,
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )
                    }
                }

                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 2) {
                    AnimatedVisibility(
                        visible = !useDynamicColor,
                        enter = fadeIn(animationSpec = tween(panelDuration, easing = MotionTokens.Easing.silk)) + expandVertically(animationSpec = tween(panelDuration, easing = MotionTokens.Easing.silk)),
                        exit = fadeOut(animationSpec = tween(panelDuration, easing = MotionTokens.Easing.silk)) + shrinkVertically(animationSpec = tween(panelDuration, easing = MotionTokens.Easing.silk))
                    ) {
                        // Optimize layout for landscape mode
                        if (isLandscape && !oledMode) {
                            // Landscape mode with gradients: Use 2-column grid layout
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                            ) {
                                // Accent color takes full width
                                CompactColorPickerCard(
                                    title = "ACCENT COLOR",
                                    description = "Buttons and highlights",
                                    currentColor = customAccentColor,
                                    onColorChange = onAccentColorChange,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    isLandscape = isLandscape,
                                    enabled = true,
                                    oledMode = oledMode
                                )

                                // Gradient pickers in a row (side by side)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
                                ) {
                                    CompactColorPickerCard(
                                        title = "GRADIENT START",
                                        description = "Top gradient color",
                                        currentColor = customGradientStart,
                                        onColorChange = onGradientStartChange,
                                        colors = colors,
                                        cardBackground = cardBackground,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = isLandscape,
                                        enabled = true,
                                        oledMode = oledMode,
                                        modifier = Modifier.weight(1f)
                                    )

                                    CompactColorPickerCard(
                                        title = "GRADIENT END",
                                        description = "Bottom gradient color",
                                        currentColor = customGradientEnd,
                                        onColorChange = onGradientEndChange,
                                        colors = colors,
                                        cardBackground = cardBackground,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = isLandscape,
                                        enabled = true,
                                        oledMode = oledMode,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            // Portrait mode or OLED mode: Use vertical stack layout
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                            ) {
                                CompactColorPickerCard(
                                    title = "ACCENT COLOR",
                                    description = "Buttons and highlights",
                                    currentColor = customAccentColor,
                                    onColorChange = onAccentColorChange,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    isLandscape = isLandscape,
                                    enabled = true,
                                    oledMode = oledMode
                                )

                                // Only show gradient pickers when NOT in OLED mode
                                if (!oledMode) {
                                    CompactColorPickerCard(
                                        title = "GRADIENT START",
                                        description = "Top gradient color",
                                        currentColor = customGradientStart,
                                        onColorChange = onGradientStartChange,
                                        colors = colors,
                                        cardBackground = cardBackground,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = isLandscape,
                                        enabled = true,
                                        oledMode = oledMode
                                    )

                                    CompactColorPickerCard(
                                        title = "GRADIENT END",
                                        description = "Bottom gradient color",
                                        currentColor = customGradientEnd,
                                        onColorChange = onGradientEndChange,
                                        colors = colors,
                                        cardBackground = cardBackground,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = isLandscape,
                                        enabled = true,
                                        oledMode = oledMode
                                    )
                                }
                            }
                        }
                    }
                } // end AnimatedElement staggerIndex=2

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = colorScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun OLEDPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    oledMode: Boolean,
    onOledModeChange: (Boolean) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val oledScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "oled_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() }
                    } else {
                        detectTapGestures { }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 700.dp else 500.dp)
                    .verticalScroll(oledScroll)
                    .padding(horizontal = if (isLandscape) 40.dp else 24.dp)
                    .padding(bottom = backPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                CleanTitle(
                    text = "OLED SETTINGS",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false
                    ,
                    scrollOffset = oledScroll.value
                )

                Text(
                    text = "Pure black theme optimized for OLED displays",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Main Toggle
                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 2) {
                    ToggleCard(
                        title = "OLED MODE",
                        description = "Enable pure black background",
                        checked = oledMode,
                        onCheckedChange = onOledModeChange,
                        colors = colors,
                        cardBackground = cardBackground,
                        isSmallScreen = isSmallScreen,
                        oledMode = oledMode
                    )
                } // end AnimatedElement staggerIndex=1

                // Customization Options (only visible if enabled)
                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 2) {
                    AnimatedVisibility(
                        visible = oledMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
                        ) {
                            Text(
                                text = "OLED mode uses your regular accent color. To customize it, go to Visual Effects → Color Customization.",
                                fontSize = ts.labelLarge,
                                color = colors.textSecondary,
                                fontFamily = quicksandFontFamily,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = colors.primaryAccent.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                                    .padding(16.dp)
                            )
                        }
                    }
                } // end AnimatedElement staggerIndex=2

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
            } // end Column

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = oledScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun FunctionalityPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    aggressiveMode: Boolean,
    onAggressiveModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    onAppSelectorClick: () -> Unit,
    onShowAggressiveWarning: () -> Unit,
    onNotificationsClick: () -> Unit,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val funcScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "functionality_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dismissOnClickOutside) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() }
                    } else {
                        detectTapGestures { }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(funcScroll)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "FUNCTIONALITY",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false,
                    scrollOffset = funcScroll.value
                )

                Text(
                    text = "Configure renderer options and app behaviour",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 3) {
                    if (isLandscape) {
                        // Two-column layout for landscape
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ToggleCard(
                                    title = "TAP TO DISMISS",
                                    description = "Tap empty space to close panels",
                                    checked = dismissOnClickOutside,
                                    onCheckedChange = onDismissOnClickOutsideChange,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )

                                SettingsNavigationCard(
                                    title = "NOTIFICATIONS",
                                    description = "OpenGL reminders, frequency and permissions",
                                    onClick = onNotificationsClick,
                                    isSmallScreen = isSmallScreen,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    enabled = true,
                                    oledMode = oledMode
                                )

                                ToggleCard(
                                    title = "AGGRESSIVE MODE",
                                    description = "Force stop background apps",
                                    checked = aggressiveMode,
                                    onCheckedChange = onAggressiveModeChange,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ToggleCard(
                                    title = "VERBOSE MODE",
                                    description = "Show detailed command output logs",
                                    checked = verboseMode,
                                    onCheckedChange = onVerboseModeChange,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    isSmallScreen = isSmallScreen,
                                    oledMode = oledMode
                                )
                            }
                        }
                    } else {
                        // Single column for portrait — TAP TO DISMISS is slot 1
                        ToggleCard(
                            title = "TAP TO DISMISS",
                            description = "Tap empty space to close panels",
                            checked = dismissOnClickOutside,
                            onCheckedChange = onDismissOnClickOutsideChange,
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )
                    }
                } // end AnimatedElement staggerIndex=1

                if (!isLandscape) {
                    AnimatedElement(visible = visible, staggerIndex = 2,
                        totalItems = 3) {
                        SettingsNavigationCard(
                            title = "NOTIFICATIONS",
                            description = "OpenGL reminders, frequency and permissions",
                            onClick = onNotificationsClick,
                            isSmallScreen = isSmallScreen,
                            colors = colors,
                            cardBackground = cardBackground,
                            enabled = true,
                            oledMode = oledMode
                        )
                    }

                    AnimatedElement(visible = visible, staggerIndex = 3,
                        totalItems = 3) {
                        ToggleCard(
                            title = "VERBOSE MODE",
                            description = "Show detailed command output logs",
                            checked = verboseMode,
                            onCheckedChange = onVerboseModeChange,
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )
                    }

                    AnimatedElement(visible = visible, staggerIndex = 4,
                        totalItems = 3) {
                        ToggleCard(
                            title = "AGGRESSIVE MODE",
                            description = "Force stop background apps",
                            checked = aggressiveMode,
                            onCheckedChange = onAggressiveModeChange,
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )
                    }
                } // end !isLandscape block

                AnimatedElement(visible = visible, staggerIndex = if (isLandscape) 2 else 5) {
                    AnimatedVisibility(visible = aggressiveMode) {
                        FlatButton(
                            text = "APP TARGETING",
                            onClick = onAppSelectorClick,
                            modifier = Modifier.fillMaxWidth(),
                            accent = false,
                            enabled = true,
                            colors = colors,
                            maxLines = 1,
                            oledMode = oledMode
                        )
                    }
                } // end AnimatedElement APP TARGETING

                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = funcScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NotificationsPanel — sub-panel of Functionality
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun NotificationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notifIntervalIndex: Int,
    onNotifIntervalChange: (Int) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTestNotification: () -> Unit,
    currentRenderer: String,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val dismissOnClickOutside = LocalDismissOnClickOutside.current
    // Hoisted above BouncyDialog so these exist before enter animation starts
    val scroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(500, easing = MotionTokens.Easing.emphasized),
        label = "notif_back_padding"
    )

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dismissOnClickOutside) {
                    if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                    else detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {
            val hPad = if (isLandscape) 32.dp else 24.dp
            val intervalLabels = listOf("2h", "4h", "6h", "12h", "24h")
            val intervalDescriptions = listOf(
                "Every 2 hours", "Every 4 hours", "Every 6 hours",
                "Every 12 hours", "Once a day"
            )

            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(scroll)
                    .padding(horizontal = hPad)
                    .padding(bottom = backPadding)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(Modifier.height(if (isLandscape) 28.dp else 40.dp))

                CleanTitle(
                    text = "NOTIFICATIONS",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false,
                    scrollOffset = scroll.value
                )

                AnimatedElement(visible = visible, staggerIndex = 0, totalItems = 5) {
                    Text(
                        text = "Get reminded when OpenGL is still active",
                        fontSize = ts.labelLarge,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ── Permission status card ────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
                    val permGranted = hasPermission
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Accent edge bar
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(
                                            color = if (permGranted)
                                                colors.primaryAccent
                                            else
                                                colors.primaryAccent.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = if (isSmallScreen) 20.dp else 24.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "PERMISSION STATUS",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = colors.primaryAccent.copy(alpha = if (permGranted) 0.7f else 0.9f),
                                            fontFamily = quicksandFontFamily
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (permGranted)
                                                "Notifications are allowed ✅"
                                            else
                                                "Permission not granted — tap to request",
                                            fontSize = ts.bodyMedium,
                                            color = colors.textSecondary,
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (!permGranted) {
                                        Spacer(Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.primaryAccent.copy(alpha = 0.15f))
                                                .border(1.dp, colors.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .pointerInput(Unit) {
                                                    detectTapGestures { onRequestPermission() }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "ALLOW",
                                                fontSize = ts.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = colors.primaryAccent,
                                                fontFamily = quicksandFontFamily
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Enable / disable toggle ───────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
                    ToggleCard(
                        title = "ENABLE NOTIFICATIONS",
                        description = "Remind me when OpenGL is still active",
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange,
                        colors = colors,
                        cardBackground = cardBackground,
                        isSmallScreen = isSmallScreen,
                        oledMode = oledMode,
                        enabled = hasPermission
                    )
                }

                // ── Reminder frequency — wrapped in a card matching THEME style ──
                AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
                    val selectorAlpha by animateFloatAsState(
                        targetValue = if (notificationsEnabled && hasPermission) 1f else 0.38f,
                        animationSpec = tween(300),
                        label = "interval_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer(alpha = selectorAlpha)
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isSmallScreen) 20.dp else 24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "REMINDER FREQUENCY",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            color = colors.primaryAccent.copy(alpha = 0.7f),
                                            fontFamily = quicksandFontFamily
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "How often to nudge you when OpenGL is active",
                                            fontSize = ts.bodyMedium,
                                            color = colors.textSecondary.copy(alpha = 0.7f),
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = (ts.bodyMedium.value * 1.4f).sp
                                        )
                                    }
                                }
                                GlideOptionSelector(
                                    options = intervalLabels,
                                    selectedIndex = notifIntervalIndex,
                                    onOptionSelected = onNotifIntervalChange,
                                    colors = colors,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = notificationsEnabled && hasPermission
                                )
                                Text(
                                    text = intervalDescriptions[notifIntervalIndex],
                                    fontSize = ts.labelSmall,
                                    color = colors.textSecondary.copy(alpha = 0.6f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // ── Test notification button ──────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = if (isSmallScreen) 20.dp else 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SEND TEST NOTIFICATION 🔔",
                                        fontSize = ts.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        fontFamily = quicksandFontFamily
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "See exactly what the reminder looks like",
                                        fontSize = ts.bodyMedium,
                                        color = colors.textSecondary,
                                        fontFamily = quicksandFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (hasPermission) colors.primaryAccent.copy(alpha = 0.15f)
                                            else colors.textSecondary.copy(alpha = 0.07f)
                                        )
                                        .border(
                                            1.dp,
                                            if (hasPermission) colors.primaryAccent.copy(alpha = 0.5f)
                                            else colors.border.copy(alpha = 0.3f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .then(
                                            if (hasPermission) Modifier.pointerInput(Unit) {
                                                detectTapGestures { onTestNotification() }
                                            } else Modifier
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "SEND",
                                        fontSize = ts.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = if (hasPermission) colors.primaryAccent
                                        else colors.textSecondary.copy(alpha = 0.3f),
                                        fontFamily = quicksandFontFamily
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (isLandscape) 28.dp else 40.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = scroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

// Developer Panel
@Composable
fun DeveloperPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onTestNotification: () -> Unit,
    onTestBootNotification: () -> Unit,
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit,
    timeOffsetHours: Float,
    onTimeOffsetChange: (Float) -> Unit
) {
    val ts = LocalTypeScale.current
    // Hoisted above BouncyDialog — created before enter animation starts
    val devScroll = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "developer_back_padding"
    )
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(devScroll)
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Added top spacer for status bar

                CleanTitle(
                    text = "DEVELOPER",
                    fontSize = ts.displayMedium,
                    colors = colors,
                    reverseGradient = false
                    ,
                    scrollOffset = devScroll.value
                )

                AnimatedElement(visible = visible, staggerIndex = 0,
                    totalItems = 3) {
                    Text(
                        text = "Advanced testing and debugging tools",
                        fontSize = ts.labelLarge,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Test Notification Button
                AnimatedElement(visible = visible, staggerIndex = 1,
                    totalItems = 3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                performHaptic()
                                onTestNotification()
                            },
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TEST NOTIFICATIONS",
                                    fontSize = ts.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Send a test notification to verify permissions",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Bell Icon
                            Canvas(modifier = Modifier.size(32.dp)) {
                                val strokeWidth = 2.5.dp.toPx()

                                // Bell body
                                val bellPath = Path().apply {
                                    moveTo(size.width * 0.3f, size.height * 0.35f)
                                    cubicTo(
                                        size.width * 0.3f, size.height * 0.25f,
                                        size.width * 0.7f, size.height * 0.25f,
                                        size.width * 0.7f, size.height * 0.35f
                                    )
                                    lineTo(size.width * 0.75f, size.height * 0.65f)
                                    lineTo(size.width * 0.25f, size.height * 0.65f)
                                    close()
                                }

                                drawPath(
                                    path = bellPath,
                                    color = colors.primaryAccent,
                                    style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                                )

                                // Bell bottom
                                drawLine(
                                    color = colors.primaryAccent,
                                    start = Offset(size.width * 0.2f, size.height * 0.65f),
                                    end = Offset(size.width * 0.8f, size.height * 0.65f),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )

                                // Bell clapper
                                drawCircle(
                                    color = colors.primaryAccent,
                                    radius = 3.dp.toPx(),
                                    center = Offset(size.width * 0.5f, size.height * 0.75f)
                                )
                            }
                        }
                    }

                } // end AnimatedElement staggerIndex=1

                // Test Boot Notification Button
                AnimatedElement(visible = visible, staggerIndex = 2,
                    totalItems = 3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                performHaptic()
                                onTestBootNotification()
                            },
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TEST BOOT NOTIFICATION",
                                    fontSize = ts.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Test the Vulkan rendering notification",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Rocket Icon
                            Canvas(modifier = Modifier.size(32.dp)) {
                                val strokeWidth = 2.5.dp.toPx()

                                // Rocket body
                                val rocketPath = Path().apply {
                                    moveTo(size.width * 0.5f, size.height * 0.2f)
                                    lineTo(size.width * 0.35f, size.height * 0.5f)
                                    lineTo(size.width * 0.35f, size.height * 0.65f)
                                    lineTo(size.width * 0.65f, size.height * 0.65f)
                                    lineTo(size.width * 0.65f, size.height * 0.5f)
                                    close()
                                }

                                drawPath(
                                    path = rocketPath,
                                    color = colors.primaryAccent,
                                    style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                                )

                                // Rocket flames
                                val flameLeft = Path().apply {
                                    moveTo(size.width * 0.4f, size.height * 0.65f)
                                    lineTo(size.width * 0.35f, size.height * 0.8f)
                                    lineTo(size.width * 0.45f, size.height * 0.7f)
                                }
                                val flameRight = Path().apply {
                                    moveTo(size.width * 0.6f, size.height * 0.65f)
                                    lineTo(size.width * 0.65f, size.height * 0.8f)
                                    lineTo(size.width * 0.55f, size.height * 0.7f)
                                }

                                drawPath(
                                    path = flameLeft,
                                    color = colors.primaryAccent,
                                    style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                                )
                                drawPath(
                                    path = flameRight,
                                    color = colors.primaryAccent,
                                    style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                                )

                                // Window
                                drawCircle(
                                    color = colors.primaryAccent,
                                    radius = 4.dp.toPx(),
                                    center = Offset(size.width * 0.5f, size.height * 0.38f)
                                )
                            }
                        }
                    }

                } // end AnimatedElement staggerIndex=2

                // Time Offset Controls for Time System
                AnimatedElement(visible = visible, staggerIndex = 3,
                    totalItems = 3) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (oledMode) 0.75.dp else 1.dp,
                                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                                shape = RoundedCornerShape(18.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(if (isSmallScreen) 20.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "TIME SYSTEM OFFSET",
                                fontSize = ts.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontFamily = quicksandFontFamily,
                                letterSpacing = 1.sp
                            )

                            Text(
                                text = "Adjust time for testing sun/moon positions",
                                fontSize = ts.labelMedium,
                                color = colors.textSecondary,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold
                            )

                            // Current offset display
                            Text(
                                text = "Offset: ${if (timeOffsetHours >= 0) "+" else ""}${timeOffsetHours.roundToInt()}h",
                                fontSize = ts.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent,
                                fontFamily = quicksandFontFamily
                            )

                            // Slider for continuous adjustment (0 to 12 hours)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Slider: 0h to +12h",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )

                                Slider(
                                    value = timeOffsetHours.coerceIn(0f, 12f),
                                    onValueChange = {
                                        performHaptic()
                                        onTimeOffsetChange(it)
                                    },
                                    valueRange = 0f..12f,
                                    steps = 11, // 0, 1, 2, ..., 12
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.primaryAccent,
                                        activeTrackColor = colors.primaryAccent,
                                        inactiveTrackColor = colors.border
                                    )
                                )
                            }

                            // Quick buttons for +2 hour increments
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Quick Buttons: +2h increments",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // +2h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 2f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 2f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(2f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+2h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 2f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }

                                    // +4h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 4f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 4f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(4f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+4h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 4f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }

                                    // +6h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 6f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 6f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(6f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+6h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 6f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // +8h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 8f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 8f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(8f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+8h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 8f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }

                                    // +10h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 10f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 10f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(10f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+10h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 10f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }

                                    // +12h button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(
                                                color = if (timeOffsetHours == 12f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (timeOffsetHours == 12f) colors.primaryAccent else colors.border,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                performHaptic()
                                                onTimeOffsetChange(12f)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+12h",
                                            fontSize = ts.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (timeOffsetHours == 12f) colors.primaryAccent else colors.textPrimary,
                                            fontFamily = quicksandFontFamily
                                        )
                                    }
                                }

                                // Reset button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .background(
                                            color = if (timeOffsetHours == 0f) colors.primaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (timeOffsetHours == 0f) colors.primaryAccent else colors.border,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            performHaptic()
                                            onTimeOffsetChange(0f)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Reset (0h - Current Time)",
                                        fontSize = ts.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (timeOffsetHours == 0f) colors.primaryAccent else colors.textPrimary,
                                        fontFamily = quicksandFontFamily
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                }
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = devScroll,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}

@Composable
fun CompactColorPickerCard(
    title: String,
    description: String,
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    enabled: Boolean = true,
    oledMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ts = LocalTypeScale.current
    val presetColors = listOf(
        Color(0xFF4895EF), Color(0xFF2563EB), Color(0xFF7C3AED),
        Color(0xFFEC4899), Color(0xFFEF4444), Color(0xFFF59E0B),
        Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF8B5CF6),
        Color.White, Color.Black
    )

    // Border color comes directly from the already-animated colors.* — no local tween needed
    val cardBorderColor = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border

    val colorCardScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "color_card_scale"
    )
    val colorCardAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.25f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "color_card_alpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = colorCardScale, scaleY = colorCardScale)
            .border(width = if (oledMode) 0.75.dp else 1.dp, color = cardBorderColor, shape = RoundedCornerShape(20.dp))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = colorCardAlpha)
                .then(if (!enabled) Modifier.pointerInput(enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                                .changes.forEach { it.consume() }
                        }
                    }
                } else Modifier),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(20.dp)
        ) {
            // Left accent bar + content side-by-side using IntrinsicSize.Min
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
            ) {
                // Left accent bar — always shown for active color picker cards
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            color = colors.primaryAccent,
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                )
                // Content — takes remaining width
                if (isLandscape) {
                    // Landscape: Keep current layout (text on left, colors on right in row)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(if (isSmallScreen) 20.dp else 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                fontSize = ts.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                fontFamily = quicksandFontFamily,
                                softWrap = true
                            )
                            Text(
                                text = description,
                                color = colors.textSecondary,
                                fontSize = ts.labelLarge,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold // Force Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetColors.take(6).forEach { color ->
                                    CompactColorBox(
                                        color = color,
                                        isSelected = color == currentColor,
                                        onClick = { onColorChange(color) },
                                        colors = colors,
                                        isLandscape = isLandscape
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                presetColors.drop(6).forEach { color ->
                                    CompactColorBox(
                                        color = color,
                                        isSelected = color == currentColor,
                                        onClick = { onColorChange(color) },
                                        colors = colors,
                                        isLandscape = isLandscape
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Portrait: Revert to old layout (text on top, colors below in column)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(if (isSmallScreen) 20.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                fontSize = ts.labelLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                fontFamily = quicksandFontFamily,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = description,
                                color = colors.textSecondary,
                                fontSize = ts.labelLarge,
                                fontFamily = quicksandFontFamily,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold // Force Bold
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presetColors.take(6).forEach { color ->
                                    CompactColorBox(
                                        color = color,
                                        isSelected = color == currentColor,
                                        onClick = { onColorChange(color) },
                                        colors = colors,
                                        isLandscape = isLandscape
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                presetColors.drop(6).forEach { color ->
                                    CompactColorBox(
                                        color = color,
                                        isSelected = color == currentColor,
                                        onClick = { onColorChange(color) },
                                        colors = colors,
                                        isLandscape = isLandscape
                                    )
                                }
                            }
                        }
                    }
                } // end landscape/portrait content
            } // end accent bar Row
        }
    } // end border Box
}

@Composable
fun CompactColorBox(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: ThemeColors,
    isLandscape: Boolean
) {
    val size = if (isLandscape) 32.dp else 36.dp

    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) colors.primaryAccent else colors.border.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Canvas(modifier = Modifier.size(size * 0.5f)) {
                val strokeWidth = 2.dp.toPx()
                val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White

                drawPath(
                    path = Path().apply {
                        moveTo(this@Canvas.size.width * 0.2f, this@Canvas.size.height * 0.5f)
                        lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.7f)
                        lineTo(this@Canvas.size.width * 0.8f, this@Canvas.size.height * 0.3f)
                    },
                    color = checkColor,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun CleanTitle(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    colors: ThemeColors,
    reverseGradient: Boolean = false,
    scrollOffset: Int = 0
) {
    val titleColor = colors.textPrimary
    val animationLevel = LocalAnimationLevel.current
    val context = LocalContext.current
    val quicksandBoldTypeface = remember {
        try {
            android.graphics.Typeface.createFromAsset(context.assets, "fonts/quicksand_bold.ttf")
        } catch (e: Exception) {
            android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    // One-shot shimmer: sweeps once on the very first app launch, then never again.
    // "title_shimmer_played" is written to SharedPreferences after the animation
    // completes, so re-entering the composable (panel close/reopen, recomposition)
    // finds the flag already set and skips the effect entirely.
    val prefs = remember { context.getSharedPreferences("gama_prefs", android.content.Context.MODE_PRIVATE) }
    val shimmerAlreadyPlayed = remember { prefs.getBoolean("title_shimmer_played", false) }
    // Start at 1f immediately if we should skip, so the bars render in their settled state
    var shimmerTarget by remember { mutableStateOf(if (shimmerAlreadyPlayed) 1f else 0f) }
    val shimmerProgress by animateFloatAsState(
        targetValue = shimmerTarget,
        animationSpec = if (shimmerAlreadyPlayed || animationLevel == 2) snap()
        else tween(durationMillis = 900, easing = MotionTokens.Easing.silk),
        label = "title_shimmer"
    )
    // Kick off after a short delay so the panel entrance has started and the
    // shimmer reads as a follow-on flourish rather than fighting the open animation.
    // Skipped entirely on every run after the first.
    LaunchedEffect(Unit) {
        if (!shimmerAlreadyPlayed) {
            if (animationLevel != 2) delay(250)
            shimmerTarget = 1f
            // Persist immediately after triggering — we don't wait for the animation
            // to finish because we want the flag written even if the user navigates away
            prefs.edit().putBoolean("title_shimmer_played", true).apply()
        }
    }

    // REFACTORED TO INCLUDE GRADIENT BARS WITH OPTIONAL REVERSE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Parallax: title drifts upward at 40% of scroll speed, creating depth
                translationY = -scrollOffset * 0.4f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left Gradient Bar — shimmer travels right-to-left (toward the text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .drawWithContent {
                    drawContent()
                    // Shimmer highlight: a narrow bright band sweeping inward
                    // Progress 0→1 maps the band from the far-left edge to the text edge
                    val bandWidth = size.width * 0.35f
                    // Left bar: band moves from left (0) toward right (size.width)
                    val bandCenter = shimmerProgress * (size.width + bandWidth) - bandWidth * 0.5f
                    val shimmerBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.primaryAccent.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.75f),
                            colors.primaryAccent.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        startX = bandCenter - bandWidth * 0.5f,
                        endX   = bandCenter + bandWidth * 0.5f
                    )
                    drawRect(brush = shimmerBrush)
                }
                .background(
                    brush = if (reverseGradient) {
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = 0f),
                                colors.primaryAccent.copy(alpha = 1f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = 0f),
                                colors.primaryAccent.copy(alpha = 1f)
                            )
                        )
                    }
                )
        )

        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = quicksandFontFamily,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .drawWithContent {
                    // Draw glow shadow underneath via canvas
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint()
                        paint.color = android.graphics.Color.TRANSPARENT
                        paint.textSize = fontSize.toPx()
                        paint.setShadowLayer(80f, 0f, 0f, colors.primaryAccent.toArgb())
                        paint.textAlign = android.graphics.Paint.Align.CENTER
                        paint.isDither = true
                        canvas.nativeCanvas.drawText(
                            text,
                            size.width / 2,
                            size.height / 2 + (fontSize.toPx() * 0.25f),
                            paint
                        )
                    }
                    // Draw actual Compose text on top (uses quicksandFontFamily)
                    drawContent()
                }
        )

        // Right Gradient Bar — shimmer sweeps right-to-left (toward the text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .drawWithContent {
                    drawContent()
                    // Right bar: band moves from right edge toward left (text edge)
                    val bandWidth = size.width * 0.35f
                    val bandCenter = (1f - shimmerProgress) * (size.width + bandWidth) - bandWidth * 0.5f
                    val shimmerBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.primaryAccent.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.75f),
                            colors.primaryAccent.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        startX = bandCenter - bandWidth * 0.5f,
                        endX   = bandCenter + bandWidth * 0.5f
                    )
                    drawRect(brush = shimmerBrush)
                }
                .background(
                    brush = if (reverseGradient) {
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = 1f),
                                colors.primaryAccent.copy(alpha = 0f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = 1f),
                                colors.primaryAccent.copy(alpha = 0f)
                            )
                        )
                    }
                )
        )
    }
}

@Composable
fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    oledMode: Boolean = false,
    enabled: Boolean = true
) {
    val ts = LocalTypeScale.current
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "toggle_card_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.25f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "toggle_card_alpha"
    )
    // --- checked-state colour expressions ---
    // Only animate the alpha/opacity of the accent — the color itself comes from the
    // global animated `colors.*`, so no local color animation is needed. Adding a
    // second animateColorAsState on top of an already-animating color causes it to
    // perpetually chase a moving target, producing the "delayed" appearance.

    // Border alpha: instant when checked/enabled change, color follows global animation
    val borderAlpha = when {
        !enabled            -> 0.15f                 // disabled: barely visible
        checked && oledMode -> 0.55f                 // on + oled: prominent
        checked             -> 0.55f                 // on: prominent accent ring
        oledMode            -> 0.2f                  // off + oled: subtle
        else                -> 0.2f                  // off: subtle, recedes
    }
    val cardBorderColor = colors.primaryAccent.copy(alpha = borderAlpha)

    // Left accent edge alpha — only the alpha transitions (boolean state change)
    val accentEdgeAlpha by animateFloatAsState(
        targetValue = if (checked && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "toggle_accent_edge_alpha"
    )

    // Card background wash — alpha-only animation, color follows global
    val bgWashAlpha by animateFloatAsState(
        targetValue = if (checked && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "toggle_card_bg_alpha"
    )
    val cardBg = if (bgWashAlpha > 0f)
        cardBackground.copy(
            red   = (cardBackground.red   + colors.primaryAccent.red   * 0.06f * bgWashAlpha).coerceAtMost(1f),
            green = (cardBackground.green + colors.primaryAccent.green * 0.06f * bgWashAlpha).coerceAtMost(1f),
            blue  = (cardBackground.blue  + colors.primaryAccent.blue  * 0.06f * bgWashAlpha).coerceAtMost(1f)
        )
    else cardBackground

    // Title alpha — only the opacity transitions, color follows global
    val titleAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.25f
            checked  -> 1.0f
            else     -> 0.7f
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "toggle_title_alpha"
    )
    val titleColor = colors.primaryAccent.copy(
        alpha = if (!enabled) colors.textSecondary.alpha * 0.25f else titleAlpha
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .border(
                width = if (oledMode) 0.75.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .then(if (!enabled) Modifier.pointerInput(enabled) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                            .changes.forEach { it.consume() }
                    }
                }
            } else Modifier)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = alpha),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent edge — 3 dp wide, full card height, clipped to card shape
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            color = colors.primaryAccent.copy(alpha = accentEdgeAlpha),
                            shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                        )
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            top    = if (isSmallScreen) 20.dp else 24.dp,
                            bottom = if (isSmallScreen) 20.dp else 24.dp,
                            start  = if (isSmallScreen) 17.dp else 21.dp, // 20/24 minus 3dp edge
                            end    = if (isSmallScreen) 20.dp else 24.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            text = title,
                            color = titleColor,
                            fontSize = ts.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = quicksandFontFamily
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            color = if (enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.25f),
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold // Force Bold
                        )
                    }

                    // Switch colors use colors.* directly — those values are already
                    // globally animated. A local animateColorAsState on top causes double-animation
                    // (chasing a moving target), making colors appear delayed.
                    // Only oledMode switching (boolean→ static Color) still gets a tween.
                    val oledTrackAlpha by animateFloatAsState(
                        targetValue = if (oledMode) 1f else 0f,
                        animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.emphasized),
                        label = "oled_track_alpha"
                    )
                    val checkedTrackColor = lerp(colors.primaryAccent, Color(0xFF1A1A1A), oledTrackAlpha)
                    val uncheckedTrackColor = lerp(Color.Gray, Color(0xFF1A1A1A), oledTrackAlpha)
                    val checkedThumbColor = lerp(Color.White, colors.primaryAccent, oledTrackAlpha)

                    val switchColors = SwitchDefaults.colors(
                        checkedThumbColor = checkedThumbColor,
                        checkedTrackColor = checkedTrackColor,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = uncheckedTrackColor,
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                        disabledCheckedThumbColor = colors.textSecondary.copy(alpha = 0.4f),
                        disabledCheckedTrackColor = Color.Gray.copy(alpha = 0.3f),
                        disabledUncheckedThumbColor = colors.textSecondary.copy(alpha = 0.3f),
                        disabledUncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
                    )

                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        colors = switchColors,
                        enabled = enabled
                    )
                } // end inner content Row
            } // end outer Row (edge + content)
        }
    } // end border Box
}

@Composable
fun BackArrowButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val buttonSize = if (isSmallScreen) 48.dp else 56.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Glow pulse matching ChangelogIconButton style
    val glowTransition = rememberInfiniteTransition(label = "back_glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "back_glow_alpha"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "back_press_scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 1.5.dp,
        animationSpec = tween(durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick, easing = FastOutSlowInEasing),
        label = "back_border_width"
    )
    val borderColor = if (isPressed) colors.primaryAccent
    else colors.primaryAccent.copy(alpha = 0.35f)

    val glowSize = buttonSize * 1.7f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(glowSize)
    ) {
        // Blurred glow behind button
        Box(
            modifier = Modifier
                .size(glowSize)
                .blur(radius = 18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = glowAlpha),
                            colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Circular button surface
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.18f),
                            colors.primaryAccent.copy(alpha = 0.07f)
                        )
                    )
                )
                .border(borderWidth, borderColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val arrowPath = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.28f)
                    lineTo(size.width * 0.32f, size.height * 0.50f)
                    lineTo(size.width * 0.62f, size.height * 0.72f)
                }
                drawPath(
                    path = arrowPath,
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

// Panel back button — visually identical to the Settings/Changelog icon buttons on the
// main screen (square with rounded corners, breathing glow blob, left-chevron inside).
// Positioned at BottomEnd by callers — same location as the Settings gear — so every
// panel has a consistent, familiar back affordance.
//
// When scrollState is provided, the button only appears while the content actually
// overflows its container (maxValue > 0). When everything fits on one screen the
// button would sit on top of the last card; callers are expected to show an inline
// fallback (e.g. a bottom Spacer + this button inside the scrolling Column) in that
// case. Pass null to always show the floating button regardless (legacy behaviour).
@Composable
fun PanelBackButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    scrollState: ScrollState? = null,
    modifier: Modifier = Modifier
) {
    // scrollState is kept as a parameter for future use (e.g. auto-scroll-to-top on tap),
    // but the back button is always rendered regardless of whether the content scrolls.
    // Hiding it when content fits on screen caused panels to have no visible back button
    // on larger displays or when panel content was short.

    val animLevel = LocalAnimationLevel.current
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Breathing glow — same parameters as Settings/Changelog buttons
    val glowTransition = rememberInfiniteTransition(label = "back_btn_glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.18f,
        targetValue  = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = MotionTokens.Easing.silk),
            repeatMode = RepeatMode.Reverse
        ),
        label = "back_btn_glow_a"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "back_btn_press"
    )

    // Chevron nudges left on press, overshoots back on release
    val density = LocalDensity.current
    val chevronTX by animateFloatAsState(
        targetValue = if (isPressed) with(density) { -3.dp.toPx() } else 0f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "back_btn_chevron_tx"
    )

    // Border: rest → 2dp accent on press
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 1.5.dp,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "back_btn_border_w"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.4f,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "back_btn_border_a"
    )

    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow blob
        Box(
            modifier = Modifier
                .size(glowSize)
                .blur(radius = 20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = glowAlpha),
                            colors.primaryAccent.copy(alpha = glowAlpha * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        // Button surface — square with rounded corners, same as Settings button
        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    borderWidth,
                    colors.primaryAccent.copy(alpha = borderAlpha),
                    RoundedCornerShape(14.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (animLevel != 2) isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationX = chevronTX)
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.25f)
                    lineTo(size.width * 0.32f, size.height * 0.50f)
                    lineTo(size.width * 0.62f, size.height * 0.75f)
                }
                drawPath(
                    path = path,
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ThemeColors,
    cardBackground: Color,
    accent: Boolean = false,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val density = LocalDensity.current
    val shape = RoundedCornerShape(14.dp)

    // Card scale: crisp press-down, bouncy overshoot on release
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "dlgbtn_scale"
    )

    // Text scale: pops inward on press (mirrors the chevron pop feel)
    val textScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = MotionTokens.Springs.playful.dampingRatio,
            stiffness = MotionTokens.Springs.playful.stiffness
        ),
        label = "dlgbtn_text_scale"
    )

    // Text translate: subtle downward nudge on press
    val textTranslateY by animateFloatAsState(
        targetValue = if (isPressed) with(density) { 1.5.dp.toPx() } else 0f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = MotionTokens.Springs.playful.dampingRatio,
            stiffness = MotionTokens.Springs.playful.stiffness
        ),
        label = "dlgbtn_text_ty"
    )

    // Border: normal at rest, thicker accent on press
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 1.dp,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "dlgbtn_border_width"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = FastOutSlowInEasing
        ),
        label = "dlgbtn_border_alpha"
    )

    val containerColor = if (accent) colors.primaryAccent else cardBackground
    // OLED: suppress rest-state border entirely; flare accent on press only
    val borderColor = when {
        isPressed            -> colors.primaryAccent
        oledMode             -> Color.Transparent
        accent               -> Color.Transparent
        else                 -> colors.border.copy(alpha = borderAlpha)
    }

    // Outer Box: takes the caller's modifier (weight/fill) — never scaled, so layout is stable
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Inner Box: visually scaled on press, but layout is already committed by outer Box,
        // so background, border, clip and highlight are always perfectly coincident
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(containerColor)
                .border(width = borderWidth, color = borderColor, shape = shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (animLevel != 2) isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onClick() }
                    )
                }
                .padding(horizontal = 16.dp, vertical = if (isSmallScreen) 12.dp else 14.dp)
        ) {
            Text(
                text,
                color = if (accent) {
                    if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
                } else {
                    colors.textPrimary
                },
                fontSize = ts.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = textScale,
                        scaleY = textScale,
                        translationY = textTranslateY
                    )
            )
        }
    }
}

@Composable
fun BouncyDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    fullScreen: Boolean = false,
    applyBlur: Boolean = false,
    content: @Composable () -> Unit
) {
    val animLevel = LocalAnimationLevel.current
    val dismissOnClickOutside = LocalDismissOnClickOutside.current
    val density = LocalDensity.current

    // ── Single-source animation: pure graphicsLayer, no AnimatedVisibility ─────
    //
    // AnimatedVisibility causes flicker because its own enter/exit specs fight
    // with any graphicsLayer transforms we apply on top.  Instead we drive every
    // frame ourselves with two Animatables and a simple `if (renderContent)` gate.
    //
    //  ENTER  — scale 0.80 → 1.0 (spring, one gentle overshoot) + fade 0 → 1
    //  EXIT   — scale 1.0 → 1.08 (micro-swell, 80 ms) → 0.72 (shrink, 260 ms)
    //           + fade 1 → 0 in parallel over the full exit duration
    //
    //  SWIPE  — only for fullScreen panels.  Dragging down moves the card
    //           proportionally and dims a translucent scrim behind it.
    //           Release above threshold → spring back.
    //           Release past threshold OR fast fling → slide off + onDismiss().
    //
    // `renderContent` is the mount gate:
    //   • true  immediately on open  (before enter animation starts)
    //   • false only after the exit alpha reaches 0

    var renderContent by remember { mutableStateOf(visible) }
    val animScale     = remember { Animatable(if (visible) 1f else 0.80f) }
    val animAlpha     = remember { Animatable(if (visible) 1f else 0f) }
    // dragOffsetY: 0 = resting, positive = dragged downward (px)
    val dragOffsetY   = remember { Animatable(0f) }
    val scope         = rememberCoroutineScope()

    // Screen height in px — used to compute dismiss threshold and slide-out target
    val screenHeightPx = remember(density) {
        with(density) { 900.dp.toPx() }  // conservative fallback; actual measured below
    }
    var measuredHeightPx by remember { mutableStateOf(screenHeightPx) }

    LaunchedEffect(visible) {
        if (visible) {
            // Always snap drag back to 0 on open — guards against re-open with stale offset
            dragOffsetY.snapTo(0f)
            renderContent = true

            if (animLevel == 2) {
                animScale.snapTo(1f)
                animAlpha.snapTo(1f)
                return@LaunchedEffect
            }

            // Start from compressed/invisible state
            animScale.snapTo(0.80f)
            animAlpha.snapTo(0f)

            // Spring enter: scale overshoots past 1.0 then snaps back — that springy pop.
            val enterSpring = when (animLevel) {
                0    -> spring<Float>(dampingRatio = 0.38f, stiffness = 160f)
                else -> spring<Float>(dampingRatio = 0.55f, stiffness = 260f)
            }
            val alphaEnterDuration = when (animLevel) { 0 -> 280; else -> 200 }

            // Scale (spring) and alpha (tween) run in parallel
            scope.launch {
                animScale.animateTo(
                    targetValue = 1f,
                    animationSpec = enterSpring
                )
            }
            animAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = alphaEnterDuration, easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f))
            )

        } else {
            if (animLevel == 2) {
                renderContent = false
                animScale.snapTo(0.80f)
                animAlpha.snapTo(0f)
                dragOffsetY.snapTo(0f)
                return@LaunchedEffect
            }

            val exitDuration = when (animLevel) { 0 -> 340; else -> 240 }
            val exitEasing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)

            scope.launch {
                animScale.animateTo(
                    targetValue = 0.72f,
                    animationSpec = tween(durationMillis = exitDuration, easing = exitEasing)
                )
            }

            animAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = exitDuration, easing = exitEasing)
            )

            renderContent = false
            animScale.snapTo(0.80f)
            animAlpha.snapTo(0f)
            dragOffsetY.snapTo(0f)
        }
    }

    if (!renderContent) return

    // Outside-tap barrier — measures screen height for the swipe threshold at the same time
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { measuredHeightPx = it.height.toFloat() }
            .pointerInput(dismissOnClickOutside) {
                if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                else detectTapGestures { }
            }
    )

    // Swipe-to-dismiss scrim removed — no overlay between barrier and content.

    // Content — scale, alpha, and (for fullScreen) drag translationY applied via graphicsLayer
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = animScale.value
                scaleY = animScale.value
                alpha  = animAlpha.value
                // Drag translation sits on top of the bouncy entrance/exit so the
                // card physically follows the finger without fighting the spring.
                translationY = if (fullScreen) dragOffsetY.value else 0f
            }
            // Swipe-to-dismiss gesture — only wired for fullScreen panels and
            // when animations are enabled.  Uses raw pointer events so we can
            // read velocity and decide whether to commit or spring back.
            .then(
                if (fullScreen && animLevel != 2) Modifier.pointerInput(onDismiss) {
                    // Dismiss if dragged past 30% of screen height OR flung at ≥ 800 px/s
                    val dismissOffsetThreshold  = measuredHeightPx * 0.30f
                    val dismissVelocityThreshold = 800f  // px/s

                    awaitPointerEventScope {
                        while (true) {
                            // Wait for the first finger-down event
                            val down = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.changedToDown() } ?: continue

                            val velocityTracker = VelocityTracker()
                            var totalDragY = 0f

                            // Track the drag — addPosition each frame for accurate velocity
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var isDragging = false

                            // Consume all move events for this pointer until it lifts
                            var pointer = down
                            while (pointer.pressed) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break

                                val dy = change.positionChange().y

                                // Only start dragging if the initial movement is predominantly downward
                                // and there has been enough movement to distinguish from a tap.
                                if (!isDragging) {
                                    if (kotlin.math.abs(dy) > with(density) { 8.dp.toPx() }) {
                                        isDragging = dy > 0f  // Only allow downward drag
                                    }
                                }

                                if (isDragging && dy > 0f) {
                                    totalDragY += dy
                                    // Rubber-band: full resistance at 0, softening after threshold
                                    val rubberband = if (totalDragY < dismissOffsetThreshold) {
                                        totalDragY
                                    } else {
                                        dismissOffsetThreshold + (totalDragY - dismissOffsetThreshold) * 0.4f
                                    }
                                    scope.launch { dragOffsetY.snapTo(rubberband) }
                                    change.consume()
                                }

                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                pointer = change
                            }

                            // Finger lifted — decide: commit dismiss or spring back
                            if (isDragging) {
                                val velocityY = velocityTracker.calculateVelocity().y
                                val shouldDismiss = totalDragY >= dismissOffsetThreshold ||
                                        velocityY >= dismissVelocityThreshold

                                if (shouldDismiss) {
                                    // Slide the card off the bottom of the screen, then dismiss
                                    scope.launch {
                                        dragOffsetY.animateTo(
                                            targetValue = measuredHeightPx,
                                            animationSpec = tween(
                                                durationMillis = 280,
                                                easing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)
                                            )
                                        )
                                        onDismiss()
                                    }
                                } else {
                                    // Spring the card back to rest — same feel as button release
                                    scope.launch {
                                        dragOffsetY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = MotionTokens.Springs.pressUp.dampingRatio,
                                                stiffness    = MotionTokens.Springs.pressUp.stiffness
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = if (fullScreen) Alignment.TopStart else Alignment.Center
    ) {
        content()
    }
}

@Composable
fun VerbosePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseOutput: String,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    blurEnabled: Boolean,
    oledMode: Boolean = false // Added
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isSmallScreen) 20.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CleanTitle(
                        text = "VERBOSE OUTPUT",
                        fontSize = ts.headlineLarge,
                        colors = colors,
                        reverseGradient = false
                    )

                    val verboseScrollState = rememberScrollState()
                    val verboseScope = rememberCoroutineScope()

                    // Auto-scroll to bottom whenever new output is appended
                    LaunchedEffect(verboseOutput) {
                        verboseScrollState.animateScrollTo(verboseScrollState.maxValue)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                color = colors.background.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = verboseOutput.ifEmpty { "Waiting for commands..." },
                            color = colors.textPrimary.copy(alpha = 0.9f),
                            fontSize = ts.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(verboseScrollState),
                            fontWeight = FontWeight.Bold // Force Bold
                        )
                    }

                    FlatButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        accent = true,
                        enabled = true,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode
                    )
                }
            }
        }
    }
}

@Composable
fun AppSelectorPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    excludedApps: MutableList<String>,
    onExcludedAppsChange: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    blurEnabled: Boolean,
    performHaptic: () -> Unit,
    oledMode: Boolean = false,
    isLandscape: Boolean,
    isTablet: Boolean,
    // Pre-loaded from parent so IO doesn't fire during enter animation
    preloadedApps: List<Pair<String, String>> = emptyList(),
    isPreloading: Boolean = false
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current
    // Use preloaded list if available, otherwise fall back to local load
    var allApps by remember { mutableStateOf(preloadedApps) }
    var isLoading by remember { mutableStateOf(preloadedApps.isEmpty()) }

    // Search query — empty string means show all apps
    var searchQuery by remember { mutableStateOf("") }

    // Memoised filtered list: only recomputed when allApps or searchQuery actually changes,
    // not on every unrelated recomposition triggered by checkbox state or haptic callbacks.
    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { (pkg, name) ->
            name.contains(searchQuery, ignoreCase = true) ||
                    pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    // Keep allApps in sync if preloaded list arrives after panel opens
    LaunchedEffect(preloadedApps) {
        if (preloadedApps.isNotEmpty()) {
            allApps = preloadedApps
            isLoading = false
        }
    }

    // Fallback: only load internally if no preloaded data arrived at all
    LaunchedEffect(visible) {
        if (visible && preloadedApps.isEmpty() && !isPreloading) {
            isLoading = true
            withContext(Dispatchers.IO) {
                allApps = getAllInstalledPackages(context)
            }
            isLoading = false
        }
    }

    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.primaryAccent.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() }
                    } else {
                        detectTapGestures { }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape && !isTablet) 0.7f else 0.95f)
                    .fillMaxHeight(0.85f)
                    .widthIn(max = 650.dp)
                    .border(
                        width = 1.dp,
                        color = colors.border,
                        shape = RoundedCornerShape(28.dp)
                    )
                ,
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = if (isSmallScreen) 32.dp else 40.dp, start = 28.dp, end = 28.dp, bottom = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CleanTitle(
                        text = "APP TARGETING",
                        fontSize = ts.displaySmall,
                        colors = colors,
                        reverseGradient = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Select apps to exclude from cleanup",
                        fontSize = ts.labelLarge,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Search field ─────────────────────────────────────────
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search apps…",
                                color = colors.textSecondary.copy(alpha = 0.5f),
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = ts.bodyMedium
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = colors.textPrimary,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = ts.bodyMedium
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = colors.primaryAccent,
                            unfocusedBorderColor = colors.border,
                            cursorColor          = colors.primaryAccent,
                            focusedContainerColor   = colors.background.copy(alpha = 0.3f),
                            unfocusedContainerColor = colors.background.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "✕",
                                    color = colors.textSecondary.copy(alpha = 0.6f),
                                    fontSize = ts.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { searchQuery = "" }
                                        .padding(8.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // List Content
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colors.primaryAccent)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(
                                    color = colors.background.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // "Exclude All / Include All" header — hidden while a search
                                // is active because bulk-toggling filtered results is confusing.
                                if (searchQuery.isBlank()) {
                                    item(key = "__header__") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    performHaptic()
                                                    if (excludedApps.isEmpty()) {
                                                        excludedApps.addAll(allApps.map { it.first })
                                                    } else {
                                                        excludedApps.clear()
                                                    }
                                                    onExcludedAppsChange()
                                                }
                                                .background(colors.primaryAccent.copy(alpha = 0.1f))
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (excludedApps.isEmpty()) "Exclude All Apps" else "Include All Apps",
                                                color = colors.primaryAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = quicksandFontFamily
                                            )
                                        }
                                        HorizontalDivider(color = colors.border.copy(alpha = 0.3f))
                                    }
                                }

                                // App rows — keyed by package name so Compose reuses the
                                // correct composable when the list reorders after a search.
                                if (filteredApps.isEmpty()) {
                                    item(key = "__empty__") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No apps match \"$searchQuery\"",
                                                color = colors.textSecondary.copy(alpha = 0.6f),
                                                fontSize = ts.bodyMedium,
                                                fontFamily = quicksandFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    items(
                                        items = filteredApps,
                                        key   = { (packageName, _) -> packageName }
                                    ) { (packageName, appName) ->
                                        val isExcluded = excludedApps.contains(packageName)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    performHaptic()
                                                    if (isExcluded) {
                                                        excludedApps.remove(packageName)
                                                    } else {
                                                        excludedApps.add(packageName)
                                                    }
                                                    onExcludedAppsChange()
                                                }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = appName,
                                                    color = colors.textPrimary,
                                                    fontSize = ts.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = quicksandFontFamily
                                                )
                                                Text(
                                                    text = packageName,
                                                    color = colors.textSecondary,
                                                    fontSize = ts.labelMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Checkbox(
                                                checked = !isExcluded,
                                                onCheckedChange = {
                                                    performHaptic()
                                                    if (it) {
                                                        excludedApps.remove(packageName)
                                                    } else {
                                                        excludedApps.add(packageName)
                                                    }
                                                    onExcludedAppsChange()
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = colors.primaryAccent,
                                                    uncheckedColor = colors.textSecondary
                                                )
                                            )
                                        }
                                        HorizontalDivider(color = colors.border.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FlatButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        accent = true,
                        enabled = true,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun EasterEggDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean
) {
    val ts = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    BouncyDialog(visible = visible, onDismiss = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isSmallScreen) 0.90f else 0.84f)
                    .widthIn(max = 460.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    colors.primaryAccent.copy(alpha = 0.6f),
                                    colors.primaryAccent.copy(alpha = 0.15f),
                                    colors.primaryAccent.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    // ── Ambient glow blob at the top ──────────────────────────
                    val glowPulse = rememberInfiniteTransition(label = "egg_glow")
                    val glowAlpha by glowPulse.animateFloat(
                        initialValue = 0.28f, targetValue = 0.48f,
                        animationSpec = infiniteRepeatable(
                            tween(2200, easing = MotionTokens.Easing.silk),
                            RepeatMode.Reverse
                        ), label = "egg_glow_a"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .blur(60.dp, BlurredEdgeTreatment.Unbounded)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.primaryAccent.copy(alpha = glowAlpha),
                                        colors.primaryAccent.copy(alpha = glowAlpha * 0.3f),
                                        Color.Transparent
                                    ),
                                    radius = 400f
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = if (isSmallScreen) 40.dp else 52.dp,
                                bottom = if (isSmallScreen) 32.dp else 40.dp,
                                start = if (isSmallScreen) 28.dp else 36.dp,
                                end = if (isSmallScreen) 28.dp else 36.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // ── GAMA wordmark with glow ───────────────────────────
                        val gamaSize = if (isSmallScreen) (ts.displayLarge.value * 1.4f).sp
                        else (ts.displayLarge.value * 1.7f).sp
                        val context = LocalContext.current
                        Text(
                            text = "GAMA",
                            fontSize = gamaSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.drawWithContent {
                                drawIntoCanvas { canvas ->
                                    val gp = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        color = android.graphics.Color.TRANSPARENT
                                        maskFilter = android.graphics.BlurMaskFilter(
                                            100f, android.graphics.BlurMaskFilter.Blur.NORMAL
                                        )
                                        setShadowLayer(100f, 0f, 0f, colors.primaryAccent.copy(alpha = 0.7f).toArgb())
                                    }
                                    canvas.nativeCanvas.drawText(
                                        "GAMA",
                                        size.width / 2f,
                                        size.height / 2f + gamaSize.toPx() * 0.28f,
                                        gp
                                    )
                                }
                                drawContent()
                            }
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 6.dp else 8.dp))

                        // ── Thin accent divider ───────────────────────────────
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            colors.primaryAccent.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 20.dp else 28.dp))

                        // ── Main copy ─────────────────────────────────────────
                        Text(
                            text = "Graphics API Manager\nfor Android",
                            fontSize = ts.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                            lineHeight = (ts.headlineSmall.value * 1.45f).sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 16.dp else 22.dp))

                        Text(
                            text = "Built with obsessive attention to detail,\nlate nights, and too much hot cocoa.",
                            fontSize = ts.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                            lineHeight = (ts.bodyMedium.value * 1.6f).sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 8.dp else 10.dp))

                        Text(
                            text = "Thanks for using it.",
                            fontSize = ts.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 6.dp else 8.dp))

                        Text(
                            text = "— @popovicialinc",
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textSecondary.copy(alpha = 0.38f),
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(Modifier.height(if (isSmallScreen) 28.dp else 36.dp))

                        // ── Dismiss button ────────────────────────────────────
                        DialogButton(
                            text = "❤️  Nice",
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AggressiveWarningDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false, // Added
    dontShowAgain: Boolean = false,
    onDontShowAgainChange: (Boolean) -> Unit = {}
) {
    val ts = LocalTypeScale.current
    // Dialog Content - relies on main blur system (showAggressiveWarning is in anyPanelOpen)
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 500.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .pointerInput(Unit) { /* Consume taps */ },
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isSmallScreen) 24.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Aggressive Mode Warning️ ⚠️",
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )

                    Text(
                        text = "Using Aggressive mode is powerful, sure, but comes with some side effects that you should know about:",
                        fontSize = ts.bodyMedium,
                        color = colors.textPrimary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold // Force Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🛑", fontSize = ts.buttonLarge)
                            Column {
                                Text(
                                    "Resets Defaults",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "Your default browser and keyboard will be reset",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("📵", fontSize = ts.buttonLarge)
                            Column {
                                Text(
                                    "Connectivity Issues",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "Loss of WiFi-Calling/VoLTE capability. Fix: Settings → Connections → SIM manager, toggle SIM off and back on",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Added third warning row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("☠️", fontSize = ts.buttonLarge)
                            Column {
                                Text(
                                    "... and other stuff we haven't yet documented",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent, // Accented color
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "ARE YOU CERTAIN WHATEVER YOU'RE DOING IS WORTH IT?",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "This mode is NOT recommended. If you're just pushing buttons to see what they do, don't mess with this.",
                                    fontSize = ts.labelMedium,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                    }

                    // "Don't show again" checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDontShowAgainChange(!dontShowAgain) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { onDontShowAgainChange(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primaryAccent,
                                uncheckedColor = colors.textSecondary
                            )
                        )
                        Text(
                            text = "Don't show this warning again",
                            fontSize = ts.bodyMedium,
                            color = colors.textSecondary,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DialogButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = false,
                            oledMode = oledMode
                        )
                        DialogButton(
                            text = "OK",
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = colors,
                            cardBackground = cardBackground,
                            accent = true,
                            oledMode = oledMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GPUWatchConfirmDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false // Added
) {
    val ts = LocalTypeScale.current
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 450.dp)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 24.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Modified: Terminal/Code Icon instead of Gear
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    val cornerRadius = 12.dp.toPx()

                    // Draw outer window rectangle
                    drawRoundRect(
                        color = colors.primaryAccent,
                        topLeft = Offset(size.width * 0.15f, size.height * 0.2f),
                        size = Size(size.width * 0.7f, size.height * 0.6f),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = strokeWidth)
                    )

                    // Draw window header line
                    drawLine(
                        color = colors.primaryAccent,
                        start = Offset(size.width * 0.15f, size.height * 0.35f),
                        end = Offset(size.width * 0.85f, size.height * 0.35f),
                        strokeWidth = strokeWidth
                    )

                    // Draw ">" prompt
                    val promptPath = Path().apply {
                        moveTo(size.width * 0.3f, size.height * 0.45f)
                        lineTo(size.width * 0.4f, size.height * 0.55f)
                        lineTo(size.width * 0.3f, size.height * 0.65f)
                    }
                    drawPath(
                        path = promptPath,
                        color = colors.primaryAccent,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw "_" cursor
                    drawLine(
                        color = colors.primaryAccent,
                        start = Offset(size.width * 0.45f, size.height * 0.65f),
                        end = Offset(size.width * 0.55f, size.height * 0.65f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                Text(
                    text = "Open Developer Options",
                    fontSize = ts.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "GAMA can't open GPUWatch directly. You will be taken to Developer Options where you can find and enable 'GPUWatch' yourself.",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold // Force Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DialogButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = false,
                        oledMode = oledMode
                    )
                    DialogButton(
                        text = "Open",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        cardBackground = cardBackground,
                        accent = true,
                        oledMode = oledMode
                    )
                }
            }
        }
    }
}

// Private helper to get all installed applications
private fun getAllInstalledPackages(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    // Get ALL installed packages, not just launcher activities
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return packages.map {
        val packageName = it.packageName
        val label = it.loadLabel(pm).toString()
        packageName to label
    }.sortedBy { it.second }
}

// ═════════════════════════════════════════════════════════════════════════════
// SecondaryIconButton — compact pill button used for Resources and GPUWatch
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SecondaryIconButton(
    label: String,
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Dp) -> Unit
) {
    val ts       = LocalTypeScale.current
    val animLevel = LocalAnimationLevel.current
    val density  = LocalDensity.current
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "sec_btn_scale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.45f,
        animationSpec = tween(durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick),
        label = "sec_btn_border_alpha"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed) 2.dp else if (oledMode) 0.dp else 1.dp,
        animationSpec = tween(durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick),
        label = "sec_btn_border_width"
    )
    // OLED: no border at rest; flash accent on press for tactile feedback
    val borderColor = if (isPressed) colors.primaryAccent
    else if (oledMode) Color.Transparent
    else colors.border.copy(alpha = borderAlpha)

    val bgColor = if (oledMode) Color.Black else colors.cardBackground
    val iconSizeDp = 18.dp

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon(iconSizeDp)
            Text(
                text = label,
                fontSize = ts.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily,
                color = colors.textPrimary,
                maxLines = 1
            )
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════════
// IntegrationsPanel — Settings sub-panel covering Tasker, QS tile, widget
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun IntegrationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false
) {
    val ts    = LocalTypeScale.current
    val context = LocalContext.current
    // Hoisted above BouncyDialog so they exist before enter animation starts
    val scrollState = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "integrations_back_padding"
    )

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                    else detectTapGestures { }
                },
            contentAlignment = Alignment.Center
        ) {

            Column(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = backPadding)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))

                CleanTitle(
                    text = "INTEGRATIONS",
                    fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                    colors = colors,
                    reverseGradient = false,
                    scrollOffset = scrollState.value
                )

                Text(
                    text = "Connect GAMA to other apps and system features",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // ── Tasker ──────────────────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
                    IntegrationInfoCard(
                        title = "TASKER",
                        description = "Automate renderer switching based on time, WiFi, app launch, and more using broadcast intents.",
                        statusLabel = "Available",
                        statusOk = true,
                        actionLabel = "Open Guide",
                        onAction = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/popovicialinc/gama/blob/main/GAMA%20for%20Android/GAMA_Tasker_Guide.pdf")
                            )
                            context.startActivity(intent)
                        },
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode,
                        isSmallScreen = isSmallScreen
                    )
                }

                // ── Quick Settings Tile ─────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
                    val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    IntegrationInfoCard(
                        title = "QUICK SETTINGS TILE",
                        description = "Toggle Vulkan / OpenGL from your notification shade without opening the app.",
                        statusLabel = if (tileAvailable) "Available" else "Requires Android 7+",
                        statusOk = tileAvailable,
                        actionLabel = if (tileAvailable) "How to add" else null,
                        onAction = if (tileAvailable) ({
                            android.widget.Toast.makeText(
                                context,
                                "Pull down your notification shade → tap Edit → drag GAMA Renderer into your tiles",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }) else null,
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode,
                        isSmallScreen = isSmallScreen
                    )
                }

                // ── Home Screen Widget ──────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
                    IntegrationInfoCard(
                        title = "HOME SCREEN WIDGET",
                        description = "Add a one-tap Vulkan / OpenGL switch directly to your home screen.",
                        statusLabel = "Available",
                        statusOk = true,
                        actionLabel = "How to add",
                        onAction = {
                            android.widget.Toast.makeText(
                                context,
                                "Long-press your home screen → Widgets → find GAMA → drag to place",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode,
                        isSmallScreen = isSmallScreen
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            PanelBackButton(
                onClick = onDismiss,
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// IntegrationInfoCard — individual card inside IntegrationsPanel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntegrationInfoCard(
    title: String,
    description: String,
    statusLabel: String,
    statusOk: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    isSmallScreen: Boolean
) {
    val ts = LocalTypeScale.current
    var isPressed by remember { mutableStateOf(false) }

    // In OLED mode the card background is pure black — borders at rest create unwanted
    // visual noise against a black canvas.  Show them only on press for tactile feedback.
    val borderColor = when {
        isPressed && onAction != null -> colors.primaryAccent
        oledMode                      -> Color.Transparent
        else                          -> colors.border
    }
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed && onAction != null) 2.dp else if (oledMode) 0.dp else 1.dp,
        animationSpec = tween(durationMillis = MotionTokens.Duration.quick),
        label = "intcard_border"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onAction != null) MotionTokens.Scale.subtle else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "intcard_scale"
    )
    val statusColor = if (statusOk) colors.successColor else Color(0xFFF59E0B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onAction != null) Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { isPressed = true; tryAwaitRelease(); isPressed = false },
                            onTap = { onAction() }
                        )
                    } else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 18.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = colors.primaryAccent.copy(alpha = 0.7f),
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily
                    )
                    // Status pill
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = ts.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily
                        )
                    }
                }

                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = ts.bodyMedium,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (ts.bodyMedium.value * 1.4f).sp
                )

                if (actionLabel != null && onAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = actionLabel + "  →",
                            color = colors.primaryAccent,
                            fontSize = ts.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily
                        )
                    }
                }
            }
        }
    }
}