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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val quicksandFontFamily = try {
    FontFamily(
        Font(R.font.quicksand_bold, FontWeight.Normal),
        Font(R.font.quicksand_bold, FontWeight.Bold),
        Font(R.font.quicksand_bold, FontWeight.SemiBold),
        Font(R.font.quicksand_bold, FontWeight.ExtraBold)
    )
} catch (e: Exception) {
    // Fallback to system default if fonts are missing
    FontFamily.Default
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

        val silk = SpringConfig(0.8f, 300f)
        val smooth = SpringConfig(0.75f, 400f)
        val gentle = SpringConfig(0.7f, 500f)
        val balanced = SpringConfig(0.6f, 600f)
        val responsive = SpringConfig(0.55f, 700f)
        val playful = SpringConfig(0.5f, 250f)
        val snappy = SpringConfig(0.85f, 1000f)
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
fun calculateCelestialPosition(screenWidth: Float, screenHeight: Float, timeOffsetHours: Float = 0f): CelestialState? {
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
            val lowestY = screenHeight * 0.30f  // Lower at endpoints (horizon level)
            val highestY = screenHeight * 0.12f  // Lowered peak (was 0.05f)

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
            val y = screenHeight * 0.30f

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
            val y = screenHeight * 0.30f

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
            val lowestY = screenHeight * 0.30f
            val highestY = screenHeight * 0.12f  // Lowered peak (was 0.05f)

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
            val y = screenHeight * 0.30f

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
            val y = screenHeight * 0.30f

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

        while (isActive) {
            withFrameNanos { time ->
                // Calculate delta time in seconds
                val deltaTime = if (lastFrameTime > 0L) {
                    ((time - lastFrameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
                } else {
                    0.016f // Default to 60fps
                }
                lastFrameTime = time

                // When parallax is disabled, pass 0 for rotation and sensitivity
                val effectiveRotationX = if (parallaxEnabled) animatedRotationX else 0f
                val effectiveRotationY = if (parallaxEnabled) animatedRotationY else 0f
                val effectiveSensitivity = if (parallaxEnabled) parallaxSensitivity else 0f

                // Check if it's daytime for time mode (with offset)
                val currentDaytime = isDaytime(timeOffsetHours)

                particles.forEach { it.update(speedMultiplier, time, effectiveRotationX, effectiveRotationY, deltaTime, effectiveSensitivity, starMode, timeModeEnabled, currentDaytime) }

                trigger = time // Trigger recomposition
            }
        }
    }

    // Animated alpha for celestial objects (sun/moon) with smooth ease-in-out fade in/out
    // Hide celestials when panels are open, but keep particles visible
    val celestialAlpha by animateFloatAsState(
        targetValue = if (timeModeEnabled && !anyPanelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
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
            border = Color.Transparent, // No borders in OLED mode
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

// Grain Effect for Banding Mitigation
@Composable
fun GrainEffect(alpha: Float = 0.04f) {
    // Generate a reusable noise bitmap
    val noiseBitmap = remember {
        val width = 256
        val height = 256
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val random = java.util.Random()

        for (i in pixels.indices) {
            // Generate monochromatic noise pixels with varying alpha
            // Black pixels with random alpha create a nice subtle grain
            val a = (random.nextFloat() * 255).toInt()
            pixels[i] = android.graphics.Color.argb(a, 0, 0, 0)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    }

    val paint = remember {
        androidx.compose.ui.graphics.Paint().apply {
            asFrameworkPaint().apply {
                shader = android.graphics.BitmapShader(
                    noiseBitmap.asAndroidBitmap(),
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT
                )
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        drawIntoCanvas { canvas ->
            canvas.drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        }
    }
}

// Glide Option Selector for Theme and Animation Quality
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
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, // Force Bold
                        fontFamily = quicksandFontFamily
                    )
                }
            }
        }

        // Gesture Overlay
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
    var versionStatus by remember { mutableStateOf("Checking version...") }
    var changelogText by remember { mutableStateOf("Loading...") }
    var showWarningDialog by remember { mutableStateOf(false) }
    var pendingRendererSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRendererName by remember { mutableStateOf("") } // Store target renderer name

    // Dialog States
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successDialogMessage by remember { mutableStateOf("") }
    var showDeveloperMenu by remember { mutableStateOf(false) }
    var showVerbosePanel by remember { mutableStateOf(false) }
    var showAppSelector by remember { mutableStateOf(false) }
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
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showVisualEffects by remember { mutableStateOf(false) }
    var showEffects by remember { mutableStateOf(false) }
    var showColorCustomization by remember { mutableStateOf(false) }
    var showOLED by remember { mutableStateOf(false) }
    var showFunctionality by remember { mutableStateOf(false) }
    var showDeveloper by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }

    var shizukuRunning by remember { mutableStateOf(false) }
    var shizukuPermissionGranted by remember { mutableStateOf(false) }

    // Back handlers
    BackHandler(enabled = showSuccessDialog) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showSuccessDialog = false
    }

    BackHandler(enabled = showDeveloperMenu) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showDeveloperMenu = false
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
        // Re-open parent
        showVisualEffects = true
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

    BackHandler(enabled = showFunctionality) {
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        showFunctionality = false
    }

    BackHandler(enabled = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality) {
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
    // Default animation level to 0 (Max) instead of 2 (None)
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


    val isDarkTheme = when (themePreference) {
        1 -> true
        2 -> false
        else -> systemInDarkTheme
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

    // Animated colors with ease-in-out transitions
    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetColors.background,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "bg_anim"
    )

    val animatedCardBackground by animateColorAsState(
        targetValue = targetColors.cardBackground,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "card_bg_anim"
    )

    val animatedAccent by animateColorAsState(
        targetValue = targetColors.primaryAccent,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "accent_anim"
    )

    val animatedGradientStart by animateColorAsState(
        targetValue = targetColors.gradientStart,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "grad_start_anim"
    )

    val animatedGradientEnd by animateColorAsState(
        targetValue = targetColors.gradientEnd,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "grad_end_anim"
    )

    val animatedTextPrimary by animateColorAsState(
        targetValue = targetColors.textPrimary,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "text_primary_anim"
    )

    val animatedTextSecondary by animateColorAsState(
        targetValue = targetColors.textSecondary,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "text_secondary_anim"
    )

    val animatedBorder by animateColorAsState(
        targetValue = targetColors.border,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "border_anim"
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
        prefs.edit().apply {
            putInt("animation_level", animationLevel)
            putBoolean("gradient_enabled", gradientEnabled)
            putBoolean("particles_enabled", particlesEnabled)
            putInt("particle_speed", particleSpeed)
            putBoolean("particle_parallax_enabled", particleParallaxEnabled)
            putInt("particle_parallax_sensitivity", particleParallaxSensitivity)
            putBoolean("particle_star_mode", particleStarMode)
            putBoolean("particle_time_mode", particleTimeMode)
            putFloat("time_offset_hours", timeOffsetHours)
            putInt("particle_count", particleCount)
            putInt("particle_count_custom", particleCountCustom.toIntOrNull() ?: 150)
            putBoolean("blur_enabled", blurEnabled)
            putInt("theme_preference", themePreference)
            putBoolean("use_dynamic_color", useDynamicColor)
            putInt("custom_accent", customAccentColor.toArgb())
            putInt("custom_gradient_start", customGradientStart.toArgb())
            putInt("custom_gradient_end", customGradientEnd.toArgb())

            putInt("ui_scale", uiScale)
            putBoolean("verbose_mode", verboseMode)
            putBoolean("aggressive_mode", aggressiveMode)
            putStringSet("excluded_apps", excludedAppsList.toSet())
            putBoolean("oled_mode", oledMode)
            putInt("oled_accent_color", oledAccentColor.toArgb())
            putBoolean("use_dynamic_color_oled", useDynamicColorOLED)
            putBoolean("dismiss_on_click_outside", dismissOnClickOutside)
            apply()
        }
    }

    val animDuration = when (animationLevel) {
        0 -> 585
        1 -> 450
        else -> 0
    }

    val anyPanelOpen = showWarningDialog || showGitHubDialog || showChangelogDialog ||
            showSettings || showVisualEffects || showColorCustomization || showOLED ||
            showFunctionality || showShizukuHelp || showSuccessDialog || showDeveloperMenu ||
            showVerbosePanel || showAppSelector || showAggressiveWarning || showGPUWatchConfirm ||
            showDeveloper

    val currentVersion = "1.1"

    // Gradient "Come Alive" Animation on Startup
    val gradientStartupAlpha = remember { Animatable(0f) }

    // Stronger breathing gradient loop
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
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
            // This prevents overwriting the "Last Known" valid state with "Unknown" or error
            if (shizukuRunning && shizukuPermissionGranted) {
                val detectedRenderer = ShizukuHelper.getCurrentRenderer()
                currentRenderer = detectedRenderer
                // Save the detected renderer
                prefs.edit().putString("last_renderer", detectedRenderer).apply()
            }
            // If Shizuku isn't running, currentRenderer retains the default value retrieved from prefs
            // which handles the "Remember last applied API" requirement.
        }

        scope.launch {
            versionStatus = ShizukuHelper.checkVersion(currentVersion)
        }

        scope.launch {
            changelogText = fetchChangelog()
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

    // Provide the scaled density to the entire app
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalAnimationLevel provides animationLevel,
        LocalThemeColors provides colors,
        LocalUIScale provides uiScale,
        LocalDismissOnClickOutside provides dismissOnClickOutside,
        LocalDensity provides Density(
            density = currentDensity.density * animatedUiScale,
            fontScale = currentDensity.fontScale // fontScale includes user system pref, we multiply density so fonts scale too
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor)
                .graphicsLayer { } // Hardware acceleration for smooth animations
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

            // Grain Overlay to reduce banding (increased from 0.04 to 0.12 for stronger effect)
            GrainEffect(alpha = 0.12f)

            // Main content
            // Special case: Always blur for Aggressive Warning, otherwise respect blurEnabled setting
            val blurAmount by animateDpAsState(
                targetValue = if (showAggressiveWarning || (anyPanelOpen && blurEnabled)) 20.dp else 0.dp,
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

            // Get current hour for greeting
            val currentHour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurAmount)
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
                                AnimatedElement(visible = isVisible, staggerIndex = 0) {
                                    TitleSection(
                                        colors = colors,
                                        isSmallScreen = isSmallScreen,
                                        isLandscape = true,
                                        userName = userName,
                                        currentHour = currentHour
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
                                                { commandOutput = it },
                                                if (verboseMode) { output -> verboseOutput += output } else null
                                            )
                                        }
                                        // Prepare success message
                                        successDialogMessage =
                                            "Okay! OpenGL has been applied!\nClear your recents menu."
                                        showWarningDialog = true
                                    },
                                    onGitHubClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showGitHubDialog = true
                                    },
                                    onGPUWatchClick = {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showGPUWatchConfirm = true // Correctly show dialog first
                                    },
                                    oledMode = oledMode
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
                                AnimatedElement(visible = isVisible, staggerIndex = 0) {
                                    Text(
                                        text = if (userName.isNotEmpty()) {
                                            when (currentHour) {
                                                in 0..5 -> "Up late, $userName? 🌙"
                                                in 6..11 -> "Good morning, $userName! ☀️"
                                                in 12..16 -> "Hey there, $userName! 👋"
                                                in 17..22 -> "Good evening, $userName! 🌙"
                                                in 23..24 -> "Up late, $userName? 🌙"
                                                else -> "Hey, $userName 👋"
                                            }
                                        } else {
                                            when (currentHour) {
                                                in 0..5 -> "Up late? 🌙"
                                                in 6..11 -> "Good morning! ☀️"
                                                in 12..16 -> "Hey there! 👋"
                                                in 17..22 -> "Good evening! 🌙"
                                                in 23..24 -> "Up late? 🌙"
                                                else -> "Hey 👋"
                                            }
                                        },
                                        fontSize = if (isSmallScreen) 16.sp else 18.sp,
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // GAMA Title with bars
                                AnimatedElement(visible = isVisible, staggerIndex = 1) {
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

                                        val gamaTextSize = if (isSmallScreen) 44.sp else 58.sp  // Increased from 40/52
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
                                                .drawWithContent {
                                                    drawIntoCanvas { canvas ->
                                                        val paint = Paint().asFrameworkPaint()
                                                        paint.color = colors.textPrimary.toArgb()
                                                        paint.textSize = gamaTextSize.toPx()
                                                        paint.setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                                                        paint.textAlign = android.graphics.Paint.Align.CENTER
                                                        paint.typeface = quicksandBoldTypeface

                                                        canvas.nativeCanvas.drawText(
                                                            "GAMA",
                                                            size.width / 2,
                                                            size.height / 2 + (gamaTextSize.toPx() * 0.25f),
                                                            paint
                                                        )
                                                    }
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
                                AnimatedElement(visible = isVisible, staggerIndex = 2) {
                                    Text(
                                        text = "Standing by and awaiting your command",
                                        fontSize = if (isSmallScreen) 15.sp else 17.sp,  // Reduced from 18/21
                                        fontFamily = quicksandFontFamily,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // What's Next
                                AnimatedElement(visible = isVisible, staggerIndex = 3) {
                                    Text(
                                        text = "WHAT'S NEXT?",
                                        fontSize = if (isSmallScreen) 13.sp else 15.sp,  // Reduced from 16/19
                                        fontFamily = quicksandFontFamily,
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Renderer Card
                                AnimatedElement(visible = isVisible, staggerIndex = 4) {
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
                                        oledMode = oledMode
                                    )
                                }

                                // Vulkan/OpenGL Buttons
                                AnimatedElement(visible = isVisible, staggerIndex = 5) {
                                    // Animation states for Vulkan button
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

                                    // Animation states for OpenGL button
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

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        FlatButton(
                                            text = "Vulkan",
                                            onClick = {
                                                if (shizukuRunning && shizukuPermissionGranted) {
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
                                                            { commandOutput = it },
                                                            if (verboseMode) { output -> verboseOutput += output } else null
                                                        )
                                                    }
                                                    successDialogMessage = "Okay! Vulkan has been applied!\nClear your recents menu."
                                                    showWarningDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .graphicsLayer {
                                                    scaleX = vulkanScale
                                                    scaleY = vulkanScale
                                                }
                                                .alpha(vulkanAlpha),
                                            accent = currentRenderer == "Vulkan",
                                            enabled = shizukuRunning && shizukuPermissionGranted,
                                            colors = colors,
                                            maxLines = 1,
                                            oledMode = oledMode
                                        )
                                        FlatButton(
                                            text = "OpenGL",
                                            onClick = {
                                                if (shizukuRunning && shizukuPermissionGranted) {
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
                                                            { commandOutput = it },
                                                            if (verboseMode) { output -> verboseOutput += output } else null
                                                        )
                                                    }
                                                    successDialogMessage = "Okay! OpenGL has been applied!\nClear your recents menu."
                                                    showWarningDialog = true
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .graphicsLayer {
                                                    scaleX = openglScale
                                                    scaleY = openglScale
                                                }
                                                .alpha(openglAlpha),
                                            accent = currentRenderer == "OpenGL",
                                            enabled = shizukuRunning && shizukuPermissionGranted,
                                            colors = colors,
                                            maxLines = 1,
                                            oledMode = oledMode
                                        )
                                    }
                                }

                                // GitHub Button
                                AnimatedElement(visible = isVisible, staggerIndex = 6) {
                                    FlatButton(
                                        text = "Visit GitHub Repository",
                                        onClick = {
                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                            showGitHubDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        accent = true,
                                        enabled = true,
                                        colors = colors,
                                        maxLines = 1,
                                        oledMode = oledMode
                                    )
                                }

                                // GPUWatch Button
                                AnimatedElement(visible = isVisible, staggerIndex = 7) {
                                    FlatButton(
                                        text = "Open GPUWatch",
                                        onClick = {
                                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                            showGPUWatchConfirm = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        accent = false,
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

            } // End of blur wrapper Box for main content

            // Background Overlay - Auto brightened/darkened based on blur toggle and theme
            val overlayTargetColor = if (isDarkTheme) Color.Black else Color.White
            val overlayTargetAlpha = if (anyPanelOpen && !blurEnabled) 0.5f else 0f

            val overlayAlpha by animateFloatAsState(
                targetValue = overlayTargetAlpha,
                animationSpec = if (animationLevel == 2) snap<Float>() else tween<Float>(durationMillis = 600, easing = MotionTokens.Easing.emphasizedDecelerate),
                label = "overlay_alpha"
            )

            // Animate color to prevent "additional background" artifact when switching themes while overlay is active
            val animatedOverlayColor by animateColorAsState(
                targetValue = overlayTargetColor,
                animationSpec = tween<Color>(durationMillis = 400),
                label = "overlay_color"
            )

            if (overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedOverlayColor.copy(alpha = overlayAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures { }
                        }
                )
            }

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

                    // 2. Execute the actual switch
                    pendingRendererSwitch?.invoke()
                    pendingRendererSwitch = null

                    // 3. Verify in background and correct if needed
                    scope.launch {
                        delay(2500) // Wait for command to finish
                        if (ShizukuHelper.checkBinder()) {
                            val newRenderer = ShizukuHelper.getCurrentRenderer()
                            // Only update if it's different to avoid jitter, or if optimistic update was wrong
                            if (newRenderer != "Default" && newRenderer.isNotEmpty()) {
                                currentRenderer = newRenderer
                                prefs.edit().putString("last_renderer", newRenderer).apply()
                            }
                        }
                    }
                    // Trigger success dialog after executing command
                    showSuccessDialog = true
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
                onDismiss = {
                    performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                    showSuccessDialog = false
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
                visible = showSettings && !showVisualEffects && !showColorCustomization && !showOLED && !showFunctionality && !showEffects && !showParticles,
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
                            if (enabled && !aggressiveModeConfirmed) {
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
                            showAppSelector = true
                        },
                        onShowAggressiveWarning = {
                            performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                            showAggressiveWarning = true
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
                isTablet = isTablet
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
                oledMode = oledMode
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
                    // Changelog button (Row 5 - last delay)
                    AnimatedElement(visible = isVisible, staggerIndex = 4, modifier = Modifier.align(Alignment.BottomStart)) {
                        Box(
                            modifier = Modifier
                                .size(if (isSmallScreen) 48.dp else 52.dp)
                                .background(
                                    color = colors.primaryAccent.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = colors.border.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showChangelogDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Document icon
                            Canvas(modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp)) {
                                val path = Path().apply {
                                    moveTo(size.width * 0.25f, size.height * 0.15f)
                                    lineTo(size.width * 0.6f, size.height * 0.15f)
                                    lineTo(size.width * 0.75f, size.height * 0.3f)
                                    lineTo(size.width * 0.75f, size.height * 0.85f)
                                    lineTo(size.width * 0.25f, size.height * 0.85f)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                val lineY1 = size.height * 0.45f
                                val lineY2 = size.height * 0.60f
                                val lineY3 = size.height * 0.75f
                                drawLine(
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    start = Offset(size.width * 0.35f, lineY1),
                                    end = Offset(size.width * 0.65f, lineY1),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    start = Offset(size.width * 0.35f, lineY2),
                                    end = Offset(size.width * 0.65f, lineY2),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    start = Offset(size.width * 0.35f, lineY3),
                                    end = Offset(size.width * 0.55f, lineY3),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }

                    // Version number
                    AnimatedElement(visible = isVisible, staggerIndex = 4, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Text(
                            text = "v$currentVersion",
                            color = colors.textSecondary.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = quicksandFontFamily
                        )
                    }

                    // Settings button
                    AnimatedElement(visible = isVisible, staggerIndex = 4, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Box(
                            modifier = Modifier
                                .size(if (isSmallScreen) 48.dp else 52.dp)
                                .background(
                                    color = colors.primaryAccent.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = colors.border.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
                                        showSettings = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp)) {
                                val dotRadius = 2.5.dp.toPx()
                                val spacing = size.height / 4
                                val centerX = size.width / 2

                                repeat(3) { i ->
                                    drawCircle(
                                        color = colors.primaryAccent.copy(alpha = 0.7f),
                                        radius = dotRadius,
                                        center = Offset(centerX, spacing + (i * spacing))
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
fun TitleSection(colors: ThemeColors, isSmallScreen: Boolean, isLandscape: Boolean, userName: String = "", currentHour: Int = 12) {
    // Determine greeting based on time
    val greeting = when (currentHour) {
        in 0..5 -> "Up late? 🌙"
        in 6..11 -> "Morning ☀️"
        in 12..16 -> "Hey there 👋"
        in 17..22 -> "Evening 🌙"
        in 23..24 -> "Up late? 🌙"
        else -> "Hey 👋"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 10.dp else 14.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 16.dp) // Add padding to prevent glow clipping
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.emphasizedDecelerate))
    ) {
        // Greeting with username (Always present, expands smoothly)
        val displayText = if (userName.isNotEmpty()) "$greeting, $userName" else greeting

        // Use AnimatedContent for smooth text substitution if the greeting changes,
        // though typically it just appends the name. animateContentSize on parent handles the width change.
        Text(
            text = displayText,
            fontSize = if (isSmallScreen) 16.sp else 18.sp,
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold // Force Bold
        )

        // GAMA Title with Blue Bars (matching Settings panel style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Left Gradient Bar
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

            val gamaTextSize = if (isSmallScreen) 40.sp else if (isLandscape) 60.sp else 52.sp
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
                    .drawWithContent {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint()
                            paint.color = colors.textPrimary.toArgb()
                            paint.textSize = gamaTextSize.toPx()
                            paint.setShadowLayer(200f, 0f, 0f, colors.textPrimary.toArgb())
                            paint.textAlign = android.graphics.Paint.Align.CENTER
                            paint.typeface = quicksandBoldTypeface

                            canvas.nativeCanvas.drawText(
                                "GAMA",
                                size.width / 2,
                                size.height / 2 + (gamaTextSize.toPx() * 0.25f),
                                paint
                            )
                        }
                    }
            )

            // Right Gradient Bar
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

        // Standing by text - 50% bigger
        Text(
            text = "Standing by and awaiting your command",
            fontSize = if (isSmallScreen) 18.sp else 21.sp, // 50% bigger (was 12/14sp)
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        // WHAT'S NEXT? text in accent color - 50% bigger
        Text(
            text = "WHAT'S NEXT?",
            fontSize = if (isSmallScreen) 16.sp else 19.sp, // 50% bigger (was ~11/13sp)
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationLevel = LocalAnimationLevel.current

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (animationLevel == 2) snap<Float>() else spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f
        ),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = if (animationLevel == 2) snap<Float>() else spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.001f
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
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
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val settingsBlurAmount by animateDpAsState(
            targetValue = 0.dp,
            animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
            label = "settings_blur"
        )

        val dismissOnClickOutside = LocalDismissOnClickOutside.current



        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(settingsBlurAmount)
                .pointerInput(Unit) {
                    if (dismissOnClickOutside) {
                        detectTapGestures { onDismiss() } // Empty space acts as back button
                    } else {
                        detectTapGestures { } // Consume taps
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Adaptive Layout Container
            val modifier = Modifier
                .widthIn(max = if(isLandscape) 900.dp else 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 0.dp) // Changed from 20/30dp to 0 to fill status bar area

            if (isLandscape) {
                // Landscape Layout for Settings
                Column(
                    modifier = modifier
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block taps on content from dismissing panel */ }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(28.dp)) // Reduced top spacer for landscape
                    CleanTitle(
                        text = "Settings",
                        fontSize = if (isSmallScreen) 34.sp else 42.sp,
                        colors = colors,
                        reverseGradient = false
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    }
                }

                Spacer(modifier = Modifier.height(24.dp)) // Reduced bottom spacer for landscape
            } else {
                // Portrait Layout (Existing)
                Column(
                    modifier = modifier
                        .padding(horizontal = 24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { /* Block taps on content from dismissing panel */ }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(40.dp)) // Added top spacer for status bar
                    CleanTitle(
                        text = "SETTINGS",
                        fontSize = if (isSmallScreen) 38.sp else 48.sp,
                        colors = colors,
                        reverseGradient = false
                    )

                    Text(
                        text = "Configure how GAMA looks and behaves",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

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

                    Spacer(modifier = Modifier.height(40.dp)) // Added bottom spacer
                }
            }

            // Fixed: Back Arrow always on Bottom-Right
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
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
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "settings_nav_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.25f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "settings_nav_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (oledMode) 0.5.dp else 1.dp,
                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled) { if (enabled) onClick() }
            .alpha(alpha),
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
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
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
                        fontSize = if (isSmallScreen) 14.sp else 16.sp,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold // Force Bold
                    )
                }
            }

            // Draw a simple arrow to avoid icon dependency
            Canvas(modifier = Modifier.size(24.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.4f, size.height * 0.2f)
                    lineTo(size.width * 0.7f, size.height * 0.5f)
                    lineTo(size.width * 0.4f, size.height * 0.8f)
                }
                drawPath(
                    path = path,
                    color = colors.textSecondary.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
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
    maxLines: Int,
    oledMode: Boolean = false // Added oledMode support
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    // REMOVED manual calculation logic based on LocalButtonSize.
    // Now we rely on LocalDensity scaling provided in GamaUI.

    val baseHeight = if (isSmallScreen) 54.dp else 58.dp
    val baseFontSize = if (isSmallScreen) 18.sp else 20.sp

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = if (animLevel == 2) {
            snap<Float>()
        } else {
            spring<Float>(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium
            )
        },
        finishedListener = { isPressed = false },
        label = "button_scale"
    )

    // Animated colors with ease-in-out
    val baseButtonColor = if (oledMode) {
        Color.Black  // OLED mode always uses black background
    } else if (accent) {
        colors.primaryAccent  // Normal mode accent buttons are filled
    } else {
        colors.cardBackground  // Normal mode non-accent buttons
    }
    val animatedButtonColor by animateColorAsState(
        targetValue = if (!enabled) colors.textSecondary.copy(alpha = 0.05f) else baseButtonColor,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "button_color"
    )

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else {
        colors.textPrimary
    }
    val animatedTextColor by animateColorAsState(
        targetValue = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "text_color"
    )

    // Determine Border Color - in OLED mode, all cards get subtle accent-colored outlines
    val baseBorderColor = if (oledMode) {
        if (!enabled) {
            colors.border.copy(alpha = 0.3f)  // Disabled: very subtle gray outline
        } else if (accent) {
            colors.primaryAccent.copy(alpha = 0.3f)  // Accent buttons: subtle accent outline
        } else {
            colors.primaryAccent.copy(alpha = 0.2f)  // Non-accent buttons: very subtle accent outline
        }
    } else if (!accent) {
        if (enabled) colors.border else colors.border.copy(alpha = 0.5f)
    } else {
        if (!enabled) colors.border.copy(alpha = 0.5f) else Color.Transparent
    }

    // Animate border color for smooth OLED mode transitions
    val animatedBorderColor by animateColorAsState(
        targetValue = baseBorderColor,
        animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
        label = "border_color"
    )

    val shouldShowBorder = if (oledMode) {
        true  // Always show border in OLED mode
    } else if (!accent) {
        true  // Show border for non-accent buttons
    } else {
        !enabled  // Show border for disabled accent buttons only
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Button(
            onClick = {
                if (enabled && animLevel != 2) isPressed = true
                if (enabled) onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(baseHeight)
                .then(
                    if (shouldShowBorder) {
                        Modifier.border(
                            width = if (oledMode) 0.5.dp else 1.dp,
                            color = animatedBorderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else Modifier
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = animatedButtonColor),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            enabled = enabled
        ) {
            Text(
                text = text,
                fontSize = baseFontSize,
                fontWeight = FontWeight.Bold, // Force Bold
                color = animatedTextColor,
                fontFamily = quicksandFontFamily,
                maxLines = maxLines,
                textAlign = TextAlign.Center
            )
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
    onGitHubClick: () -> Unit,
    onGPUWatchClick: () -> Unit, // NEW CALLBACK
    oledMode: Boolean = false // Added
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
            AnimatedElement(visible = isVisible, staggerIndex = 1) {
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
                    oledMode = oledMode
                )
            }

            // Row 3: Action Buttons (Delay 300)
            AnimatedElement(visible = isVisible, staggerIndex = 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FlatButton(
                        text = "Vulkan",
                        onClick = onVulkanClick,
                        modifier = Modifier
                            .weight(1f)
                            .scale(0.95f) // Zoom out slightly
                            .alpha(if (shizukuReady) 1f else 0.65f) // Less grayed out
                            .then(
                                if (!shizukuReady) {
                                    Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                } else if (currentRenderer == "Vulkan") {
                                    Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                }
                            ),
                        accent = currentRenderer == "Vulkan",
                        enabled = shizukuReady,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode
                    )

                    FlatButton(
                        text = "OpenGL",
                        onClick = onOpenGLClick,
                        modifier = Modifier
                            .weight(1f)
                            .scale(0.95f) // Zoom out slightly
                            .alpha(if (shizukuReady) 1f else 0.65f) // Less grayed out
                            .then(
                                if (!shizukuReady) {
                                    Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                } else if (currentRenderer == "OpenGL") {
                                    Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(16.dp))
                                } else {
                                    Modifier
                                }
                            ),
                        accent = currentRenderer == "OpenGL",
                        enabled = shizukuReady,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode
                    )
                }
            }

            // Row 4: GitHub Button (Delay 450)
            AnimatedElement(visible = isVisible, staggerIndex = 3) {
                FlatButton(
                    text = "Visit GitHub Repository",
                    onClick = onGitHubClick,
                    modifier = Modifier.fillMaxWidth(),
                    accent = true,
                    enabled = true,
                    colors = colors,
                    maxLines = 1,
                    oledMode = oledMode
                )
            }

            // Row 5: Developer Options Button
            AnimatedElement(visible = isVisible, staggerIndex = 4) {
                FlatButton(
                    text = "Open GPUWatch",
                    onClick = onGPUWatchClick,
                    modifier = Modifier.fillMaxWidth(),
                    accent = false,
                    enabled = true,
                    colors = colors,
                    maxLines = 1,
                    oledMode = oledMode
                )
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
    oledMode: Boolean = false
) {
    // Only show error border if Shizuku isn't ready. Show subtle accent border in OLED mode if ready.
    val borderColor = if (!shizukuReady) {
        Color(0xFFFFC107).copy(alpha = 0.5f) // Warning color for non-intrusive warning
    } else if (oledMode) {
        colors.primaryAccent.copy(alpha = 0.3f) // Subtle accent outline in OLED mode
    } else {
        colors.border
    }

    // Subtle yellow tinted background when Shizuku is not ready
    val isDarkTheme = cardBackground.luminance() < 0.5f
    val subtleWarningBackground = if (!shizukuReady) {
        if (isDarkTheme) {
            // Dark mode: subtle yellow tint but still dark
            cardBackground.copy(
                red = (cardBackground.red + 0.1f).coerceAtMost(1f),
                green = (cardBackground.green + 0.08f).coerceAtMost(1f),
                alpha = 1f // Fully opaque
            )
        } else {
            // Light mode: subtle yellow tint but still light
            cardBackground.copy(
                red = (cardBackground.red + 0.05f).coerceAtMost(1f),
                green = (cardBackground.green + 0.04f).coerceAtMost(1f),
                alpha = 1f // Fully opaque
            )
        }
    } else {
        cardBackground
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = if (oledMode && shizukuReady) 0.5.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (!shizukuReady) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onShizukuErrorClick
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = subtleWarningBackground),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 18.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ALWAYS SHOW RENDERER (Default to OpenGL if "Default" or empty)
            Text(
                text = "CURRENT RENDERER",
                color = if (!shizukuReady) Color(0xFFFFC107) else colors.primaryAccent.copy(alpha = 0.7f),
                fontSize = if (isSmallScreen) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold, // Force Bold
                letterSpacing = 2.sp,
                fontFamily = quicksandFontFamily
            )

            val displayRenderer = if (currentRenderer == "Default" || currentRenderer.isEmpty()) "OpenGL" else currentRenderer

            Text(
                text = displayRenderer,
                color = colors.textPrimary,
                fontSize = if (isSmallScreen) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold, // Force Bold
                fontFamily = quicksandFontFamily,
                textAlign = TextAlign.Center
            )

            // If command output exists, show it (e.g. from a recent switch)
            if (commandOutput.isNotEmpty()) {
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
                    color = colors.textSecondary,
                    fontSize = if (isSmallScreen) 13.sp else 15.sp,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold, // Force Bold
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // If Shizuku is NOT ready, show a subtle warning footer (no clickable - whole card is clickable)
            if (!shizukuReady) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .wrapContentWidth()  // Wrap to fit content
                        .background(Color(0xFFFFC107).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⚠️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (!shizukuRunning) "Shizuku not running" else "Permission needed",
                        fontSize = 12.sp,
                        color = Color(0xFFFFC107), // Warning Yellow
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )
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
                    fontSize = if (isSmallScreen) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary
                )
            }

            Text(
                text = when (helpType) {
                    "not_running" -> "Shizuku needs to be running for GAMA to work.\n\n1. Open the Shizuku app\n2. Tap 'Start' to activate the service\n3. Return to GAMA\n\nIf Shizuku won't start, follow the wireless debugging instructions in the Shizuku app."
                    "permission" -> "GAMA needs permission to use Shizuku.\n\n1. Open Shizuku\n2. Tap 'Authorized application'\n3. Find GAMA and enable it\n4. Close GAMA from your recents\n5. Reopen GAMA\n\nThe status should now show \"Shizuku is running ✅\""
                    else -> "Unknown error"
                },
                fontSize = if (isSmallScreen) 15.sp else 17.sp,
                lineHeight = if (isSmallScreen) 22.sp else 25.sp,
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
                    text = "This will briefly restart System UI, alongside other processes, to apply changes. Your device will return to normal in just a moment.",
                    fontSize = if (isSmallScreen) 16.sp else 18.sp,
                    lineHeight = if (isSmallScreen) 23.sp else 26.sp,
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
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
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    BouncyDialog(
        visible = visible,
        onDismiss = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
                .widthIn(max = 500.dp)
                // CHANGED: Use primaryAccent instead of successColor to prevent green border
                .border(
                    width = 1.dp,
                    color = colors.primaryAccent.copy(alpha = 0.5f),
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
                // Checkmark Icon
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    // CHANGED: Use primaryAccent instead of successColor
                    val iconColor = colors.primaryAccent

                    drawCircle(
                        color = iconColor,
                        radius = size.minDimension / 2,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    val path = Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.5f)
                        lineTo(size.width * 0.45f, size.height * 0.7f)
                        lineTo(size.width * 0.75f, size.height * 0.35f)
                    }
                    drawPath(
                        path = path,
                        color = iconColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                Text(
                    text = message,
                    fontSize = if (isSmallScreen) 16.sp else 18.sp,
                    lineHeight = if (isSmallScreen) 23.sp else 26.sp,
                    color = colors.textPrimary.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold // Force Bold
                )

                DialogButton(
                    text = if (userName.isNotEmpty()) "Okay, $userName!" else "OK",
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
                    fontSize = if (isSmallScreen) 22.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary
                )

                Text(
                    text = "Developer tools for testing and debugging.",
                    fontSize = if (isSmallScreen) 14.sp else 16.sp,
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
                        fontSize = if (isSmallScreen) 16.sp else 18.sp,
                        lineHeight = if (isSmallScreen) 23.sp else 26.sp,
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
                    .fillMaxWidth(if (isLandscape && !isTablet) 0.75f else 0.9f)
                    .fillMaxHeight(if (isLandscape) 0.9f else 0.85f)
                    .widthIn(max = if (isLandscape) 800.dp else 650.dp)
                    .border(
                        width = if (oledMode) 0.5.dp else 1.dp,
                        brush = if (oledMode) {
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.primaryAccent.copy(alpha = 0.3f),
                                    colors.primaryAccent.copy(alpha = 0.3f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.primaryAccent.copy(alpha = 0.5f),
                                    colors.primaryAccent.copy(alpha = 0.2f),
                                    colors.primaryAccent.copy(alpha = 0.5f)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(1000f, 1000f)
                            )
                        },
                        shape = RoundedCornerShape(28.dp)
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on card itself */ }
                    },
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if (isLandscape) 24.dp else if (isSmallScreen) 32.dp else 40.dp,
                            start = if (isLandscape) 24.dp else 28.dp,
                            end = if (isLandscape) 24.dp else 28.dp,
                            bottom = 0.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Decorative top accent
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.primaryAccent,
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

                    // Header
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "CHANGELOG",
                            fontSize = if (isLandscape) 26.sp else if (isSmallScreen) 28.sp else 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = quicksandFontFamily,
                            color = colors.textPrimary,
                            letterSpacing = if (isLandscape) 2.sp else 3.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))

                    // Subtitle with version status
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 2.dp else 4.dp)
                    ) {
                        Text(
                            text = "WHAT'S NEW?",
                            fontSize = if (isLandscape) 12.sp else if (isSmallScreen) 13.sp else 15.sp,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Version status indicator
                        var versionStatus by remember { mutableStateOf("Checking version...") }
                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current

                        // Check version on mount and when changelog changes (indicates refresh)
                        LaunchedEffect(changelogText) {
                            versionStatus = "Checking version..."
                            delay(300) // Small delay for UX
                            versionStatus = ShizukuHelper.checkVersion("1.1")
                        }

                        Text(
                            text = versionStatus,
                            fontSize = 12.sp,
                            fontFamily = quicksandFontFamily,
                            color = if (versionStatus.contains("✅") || versionStatus.contains("latest")) {
                                colors.textSecondary.copy(alpha = 0.6f)
                            } else if (versionStatus.contains("available") || versionStatus.contains("update")) {
                                colors.primaryAccent
                            } else {
                                colors.textSecondary.copy(alpha = 0.5f)
                            },
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

                    // Scrollable text content with enhanced fade effects
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val scrollState = rememberScrollState()

                        // Parse and display changelog with styling
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(
                                    bottom = if (isLandscape) 16.dp else 24.dp,
                                    start = 6.dp,
                                    end = 6.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // Parse changelog text into lines
                            changelogText.lines().forEach { line ->
                                when {
                                    // Headers: lines starting with "## "
                                    line.startsWith("## ") -> {
                                        Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 16.dp))
                                        Text(
                                            text = line.removePrefix("## ").trim(),
                                            fontSize = if (isLandscape) 14.sp else if (isSmallScreen) 15.sp else 17.sp,
                                            fontFamily = quicksandFontFamily,
                                            color = colors.primaryAccent.copy(alpha = 0.9f),
                                            letterSpacing = if (isLandscape) 1.sp else 1.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(if (isLandscape) 4.dp else 8.dp))
                                    }
                                    // Bullet points: lines starting with "- "
                                    line.startsWith("- ") -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "•",
                                                color = colors.primaryAccent.copy(alpha = 0.8f),
                                                fontSize = if (isLandscape) 14.sp else if (isSmallScreen) 15.sp else 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = line.removePrefix("- ").trim(),
                                                color = colors.textPrimary.copy(alpha = 0.92f),
                                                fontSize = if (isLandscape) 13.sp else if (isSmallScreen) 15.sp else 17.sp,
                                                lineHeight = if (isLandscape) 20.sp else if (isSmallScreen) 24.sp else 28.sp,
                                                fontFamily = quicksandFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    // Regular text lines
                                    line.isNotBlank() -> {
                                        Text(
                                            text = line,
                                            color = colors.textPrimary.copy(alpha = 0.92f),
                                            fontSize = if (isLandscape) 13.sp else if (isSmallScreen) 15.sp else 17.sp,
                                            lineHeight = if (isLandscape) 20.sp else if (isSmallScreen) 24.sp else 28.sp,
                                            fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    // Empty lines (spacing)
                                    else -> {
                                        Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 4.dp))
                                    }
                                }
                            }
                        }

                        // Top fade effect
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            cardBackground.copy(alpha = 1f),
                                            cardBackground.copy(alpha = 0f)
                                        )
                                    )
                                )
                                .pointerInput(Unit) {}
                        )

                        // Bottom fade effect with stronger gradient
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            cardBackground.copy(alpha = 0f),
                                            cardBackground.copy(alpha = 0.7f),
                                            cardBackground.copy(alpha = 1f)
                                        )
                                    )
                                )
                                .pointerInput(Unit) {}
                        )
                    }

                    // Decorative bottom accent
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.primaryAccent.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Emoji and Refresh button row at bottom center
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Refresh button with custom icon
                        var isRefreshing by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()

                        val buttonSize = if (isSmallScreen) 48.dp else 54.dp
                        val iconSize = if (isSmallScreen) 22.dp else 26.dp

                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.primaryAccent.copy(alpha = 0.25f),
                                            colors.primaryAccent.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = colors.primaryAccent.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable(enabled = !isRefreshing) {
                                    scope.launch {
                                        isRefreshing = true
                                        onRefresh()
                                        delay(1000) // Give visual feedback
                                        isRefreshing = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Custom refresh icon
                            val rotation by if (isRefreshing) {
                                rememberInfiniteTransition(label = "refresh_rotation").animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "rotation"
                                )
                            } else {
                                remember { mutableStateOf(0f) }
                            }

                            Canvas(
                                modifier = Modifier
                                    .size(iconSize)
                                    .graphicsLayer { rotationZ = rotation }
                            ) {
                                val strokeWidth = 2.5.dp.toPx()
                                val radius = size.minDimension / 2.5f
                                val centerX = size.width / 2
                                val centerY = size.height / 2

                                // Draw two curved arrows forming a circle
                                // Top arrow (counterclockwise)
                                drawArc(
                                    color = colors.primaryAccent,
                                    startAngle = 90f,
                                    sweepAngle = 160f,
                                    useCenter = false,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    ),
                                    topLeft = Offset(centerX - radius, centerY - radius),
                                    size = Size(radius * 2, radius * 2)
                                )

                                // Bottom arrow (clockwise)
                                drawArc(
                                    color = colors.primaryAccent,
                                    startAngle = 270f,
                                    sweepAngle = 160f,
                                    useCenter = false,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    ),
                                    topLeft = Offset(centerX - radius, centerY - radius),
                                    size = Size(radius * 2, radius * 2)
                                )

                                // Top arrow head (pointing right at top)
                                val arrowSize = 5.dp.toPx()
                                val angle1 = Math.toRadians(250.0)
                                val arrowX1 = centerX + radius * cos(angle1).toFloat()
                                val arrowY1 = centerY + radius * sin(angle1).toFloat()

                                val arrowPath1 = Path().apply {
                                    moveTo(arrowX1, arrowY1)
                                    lineTo(arrowX1 - arrowSize * 0.7f, arrowY1 - arrowSize)
                                    lineTo(arrowX1 + arrowSize * 0.7f, arrowY1 - arrowSize * 0.3f)
                                    close()
                                }
                                drawPath(path = arrowPath1, color = colors.primaryAccent)

                                // Bottom arrow head (pointing left at bottom)
                                val angle2 = Math.toRadians(70.0)
                                val arrowX2 = centerX + radius * cos(angle2).toFloat()
                                val arrowY2 = centerY + radius * sin(angle2).toFloat()

                                val arrowPath2 = Path().apply {
                                    moveTo(arrowX2, arrowY2)
                                    lineTo(arrowX2 + arrowSize * 0.7f, arrowY2 + arrowSize)
                                    lineTo(arrowX2 - arrowSize * 0.7f, arrowY2 + arrowSize * 0.3f)
                                    close()
                                }
                                drawPath(path = arrowPath2, color = colors.primaryAccent)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Developer mode button with custom sparkle icon
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.primaryAccent.copy(alpha = 0.25f),
                                            colors.primaryAccent.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = colors.primaryAccent.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { onDeveloperModeTrigger() },
                            contentAlignment = Alignment.Center
                        ) {
                            // Custom sparkle icon
                            Canvas(modifier = Modifier.size(iconSize)) {
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val strokeWidth = 2.5.dp.toPx()
                                val mainLength = size.minDimension / 2
                                val shortLength = mainLength * 0.6f

                                // Draw 4-point sparkle
                                // Vertical line
                                drawLine(
                                    color = colors.primaryAccent,
                                    start = Offset(centerX, centerY - mainLength),
                                    end = Offset(centerX, centerY + mainLength),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                                // Horizontal line
                                drawLine(
                                    color = colors.primaryAccent,
                                    start = Offset(centerX - mainLength, centerY),
                                    end = Offset(centerX + mainLength, centerY),
                                    strokeWidth = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                                // Diagonal line 1
                                drawLine(
                                    color = colors.primaryAccent,
                                    start = Offset(centerX - shortLength, centerY - shortLength),
                                    end = Offset(centerX + shortLength, centerY + shortLength),
                                    strokeWidth = strokeWidth * 0.8f,
                                    cap = StrokeCap.Round
                                )
                                // Diagonal line 2
                                drawLine(
                                    color = colors.primaryAccent,
                                    start = Offset(centerX + shortLength, centerY - shortLength),
                                    end = Offset(centerX - shortLength, centerY + shortLength),
                                    strokeWidth = strokeWidth * 0.8f,
                                    cap = StrokeCap.Round
                                )

                                // Center dot
                                drawCircle(
                                    color = colors.primaryAccent,
                                    radius = 2.dp.toPx(),
                                    center = Offset(centerX, centerY)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Back Arrow Button
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 20.dp else 24.dp, bottom = if (isSmallScreen) 24.dp else 32.dp)
            )
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 0.dp) // Changed from 20/30dp to 0 to fill status bar area
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on content from dismissing panel */ }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp)) // Added top spacer for status bar
                CleanTitle(
                    text = "VISUALS",
                    fontSize = if (isLandscape) 32.sp else if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Adjust theme, colors, animations, and visual effects",
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // OLED Mode Toggle - always full width
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


                // Theme Selector
                val themeCardAlpha by animateFloatAsState(
                    targetValue = if (oledMode) 0.5f else 1f,
                    animationSpec = tween(durationMillis = 800, easing = MotionTokens.Easing.emphasizedDecelerate),
                    label = "theme_card_alpha"
                )

                val themeCardScale by animateFloatAsState(
                    targetValue = if (oledMode) 0.98f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "theme_card_scale"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = themeCardAlpha
                            scaleX = themeCardScale
                            scaleY = themeCardScale
                        }
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "THEME",
                                    color = colors.primaryAccent.copy(alpha = 0.7f),
                                    fontSize = if (isSmallScreen) 11.sp else 13.sp,
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
                                    fontSize = if (isSmallScreen) 14.sp else 16.sp,
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = if (isSmallScreen) 18.sp else 20.sp
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
                            enabled = !oledMode // Disable when OLED mode is on
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                        Text("ANIMATION QUALITY", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                        GlideOptionSelector(listOf("Max", "Medium", "None"), animationLevel, { performHaptic(); onAnimationLevelChange(it) }, colors)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                        Text("UI SCALE", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                        GlideOptionSelector(listOf("75%", "100%", "125%"), uiScale, { performHaptic(); onUiScaleChange(it) }, colors)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                        Text("PERSONALIZATION", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)

                        Text(
                            text = "Add your name for a personalized experience throughout the app.",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontFamily = quicksandFontFamily,
                            lineHeight = 20.sp,
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

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Fixed: Back Arrow always on Bottom-Right
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on content from dismissing panel */ }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "EFFECTS",
                    fontSize = if (isLandscape) 32.sp else if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Control blur, grain, and animation quality",
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Gradient toggle with scale animation when disabled
                val gradientScale by animateFloatAsState(
                    targetValue = if (oledMode) 0.85f else 1f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "gradient_toggle_scale"
                )

                val gradientAlpha by animateFloatAsState(
                    targetValue = if (oledMode) 0.25f else 1f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "gradient_toggle_alpha"
                )

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
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = gradientScale
                                        scaleY = gradientScale
                                        alpha = gradientAlpha
                                    }
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
                    }

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
                    // Single column for portrait
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = gradientScale
                                scaleY = gradientScale
                                alpha = gradientAlpha
                            }
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
                }


                Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))
            }

            // Fixed: Back Arrow always on Bottom-Right
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 0.dp)
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on content from dismissing panel */ }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "PARTICLES",
                    fontSize = if (isLandscape) 32.sp else if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Configure animated background particles",
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Enable/Disable Particles
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

                AnimatedVisibility(visible = particlesEnabled && !oledMode) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
                    ) {
                        // Star Mode Toggle
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
                                enabled = !timeMode // Disable if time mode is active
                            )
                        }

                        // Time-based Sun & Moon System Toggle
                        ToggleCard(
                            title = "TIME SYSTEM",
                            description = "Sun and moon follow real-time, particles adapt to day/night",
                            checked = particleTimeMode,
                            onCheckedChange = {
                                performHaptic()
                                onParticleTimeModeChange(it)
                                // When time mode is enabled, disable star mode
                                if (it && particleStarMode) {
                                    onParticleStarModeChange(false)
                                }
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            isSmallScreen = isSmallScreen,
                            oledMode = oledMode
                        )

                        // Parallax Effect Toggle
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

                        // Parallax Sensitivity (only shown when parallax is enabled)
                        AnimatedVisibility(visible = particleParallaxEnabled) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (oledMode) 0.5.dp else 1.dp,
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
                                    Text("PARALLAX SENSITIVITY", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                    GlideOptionSelector(listOf("Low", "Medium", "High"), particleParallaxSensitivity, { performHaptic(); onParticleParallaxSensitivityChange(it) }, colors)
                                }
                            }
                        }

                        // Particle Speed
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (oledMode) 0.5.dp else 1.dp,
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
                                Text("PARTICLE VELOCITY", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                GlideOptionSelector(listOf("Low", "Medium", "High"), particleSpeed, { performHaptic(); onParticleSpeedChange(it) }, colors)
                            }
                        }
                        // Particle Count
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (oledMode) 0.5.dp else 1.dp,
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
                                Text("PARTICLE COUNT", color = colors.primaryAccent.copy(alpha = 0.7f), fontSize = if (isSmallScreen) 11.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = quicksandFontFamily)
                                GlideOptionSelector(listOf("Low", "Medium", "High"), particleCount, { performHaptic(); onParticleCountChange(it) }, colors)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Fixed: Back Arrow always on Bottom-Right
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
            )
        }
    }

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
    val animLevel = LocalAnimationLevel.current
    val panelDuration = when (animLevel) {
        0 -> 585  // Max quality
        1 -> 450  // Medium quality
        else -> 0 // None
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
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 0.dp) // Changed from 20/40dp to 0 to fill status bar area
                    .padding(horizontal = if (isLandscape) 40.dp else 24.dp)
                    .padding(bottom = 80.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on content from dismissing panel */ }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Added top spacer for status bar
                CleanTitle(
                    text = "COLORS",
                    fontSize = if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                // Animated visibility for description text when OLED mode changes
                AnimatedVisibility(
                    visible = !oledMode,
                    enter = fadeIn(animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate))
                ) {
                    Text(
                        text = "Customize accent colors and gradients",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                Spacer(modifier = Modifier.height(20.dp))
            }

            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 20.dp else 28.dp, bottom = if (isSmallScreen) 24.dp else 36.dp)
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isLandscape) 40.dp else 24.dp)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 28.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                CleanTitle(
                    text = "OLED SETTINGS",
                    fontSize = if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Pure black theme optimized for OLED displays",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Main Toggle
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

                // Customization Options (only visible if enabled)
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
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = colors.primaryAccent.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        )
                    }
                }
            }

            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 20.dp else 28.dp, bottom = if (isSmallScreen) 24.dp else 36.dp)
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
    userName: String,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .padding(bottom = if (isLandscape) 60.dp else 80.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { /* Block taps on content from dismissing panel */ }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 28.dp else 40.dp))
                CleanTitle(
                    text = "FUNCTIONALITY",
                    fontSize = if (isLandscape) 32.sp else if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Configure renderer options and app behaviour",
                    fontSize = if (isLandscape) 13.sp else 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

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
                    // Single column for portrait
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
            }

            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
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
                    .widthIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 0.dp) // Changed from specific padding to 0 to fill status bar area
                    .padding(horizontal = 24.dp)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 24.dp)
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Added top spacer for status bar

                CleanTitle(
                    text = "DEVELOPER",
                    fontSize = if (isSmallScreen) 32.sp else 40.sp,
                    colors = colors,
                    reverseGradient = false
                )

                Text(
                    text = "Advanced testing and debugging tools",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // Test Notification Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontFamily = quicksandFontFamily,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send a test notification to verify permissions",
                                fontSize = 13.sp,
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

                // Test Boot Notification Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontFamily = quicksandFontFamily,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Test the Vulkan rendering notification",
                                fontSize = 13.sp,
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

                // Time Offset Controls for Time System
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (oledMode) 0.5.dp else 1.dp,
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontFamily = quicksandFontFamily,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Adjust time for testing sun/moon positions",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold
                        )

                        // Current offset display
                        Text(
                            text = "Offset: ${if (timeOffsetHours >= 0) "+" else ""}${timeOffsetHours.roundToInt()}h",
                            fontSize = 15.sp,
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
                                fontSize = 12.sp,
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
                                fontSize = 12.sp,
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
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp,
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
                                        fontSize = 14.sp,
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
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (timeOffsetHours == 0f) colors.primaryAccent else colors.textPrimary,
                                    fontFamily = quicksandFontFamily
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Back Arrow Button
            BackArrowButton(
                onClick = onDismiss,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (isSmallScreen) 16.dp else 20.dp, bottom = if (isSmallScreen) 20.dp else 30.dp)
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
    val presetColors = listOf(
        Color(0xFF4895EF), Color(0xFF2563EB), Color(0xFF7C3AED),
        Color(0xFFEC4899), Color(0xFFEF4444), Color(0xFFF59E0B),
        Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF8B5CF6),
        Color.White, Color.Black
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (!enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { /* Consume touch when disabled */ }
                    }
                } else {
                    Modifier
                }
            )
            .border(
                width = if (oledMode) 0.5.dp else 1.dp,
                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(20.dp)
    ) {
        if (isLandscape) {
            // Landscape: Keep current layout (text on left, colors on right in row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        fontSize = if (isSmallScreen) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = quicksandFontFamily
                    )
                    Text(
                        text = description,
                        color = colors.textSecondary,
                        fontSize = if (isSmallScreen) 13.sp else 14.sp,
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
                    .fillMaxWidth()
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
                        fontSize = if (isSmallScreen) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = description,
                        color = colors.textSecondary,
                        fontSize = if (isSmallScreen) 13.sp else 14.sp,
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
        }
    }
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
    reverseGradient: Boolean = false
) {
    val titleColor = colors.textPrimary
    val context = LocalContext.current
    val quicksandBoldTypeface = remember {
        try {
            android.graphics.Typeface.createFromAsset(context.assets, "fonts/quicksand_bold.ttf")
        } catch (e: Exception) {
            android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    // REFACTORED TO INCLUDE GRADIENT BARS WITH OPTIONAL REVERSE
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left Gradient Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
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
                    drawIntoCanvas { canvas ->
                        val paint = Paint().asFrameworkPaint()
                        paint.color = titleColor.toArgb()
                        paint.textSize = fontSize.toPx()
                        // Reduced from 200f to 80f to reduce banding in glow
                        paint.setShadowLayer(80f, 0f, 0f, titleColor.toArgb())
                        paint.textAlign = android.graphics.Paint.Align.CENTER
                        paint.typeface = quicksandBoldTypeface
                        // Enable dithering to reduce banding
                        paint.isDither = true

                        canvas.nativeCanvas.drawText(
                            text,
                            size.width / 2,
                            size.height / 2 + (fontSize.toPx() * 0.25f), // Simplified from .value * 0.25f.sp.toPx()
                            paint
                        )
                    }
                }
        )

        // Right Gradient Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (oledMode) 0.5.dp else 1.dp,
                color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border,
                shape = RoundedCornerShape(18.dp)
            )
            .alpha(alpha),
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    color = if (enabled) colors.primaryAccent.copy(alpha = 0.7f) else colors.textSecondary.copy(alpha = 0.25f),
                    fontSize = if (isSmallScreen) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = quicksandFontFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = if (enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.25f),
                    fontSize = if (isSmallScreen) 14.sp else 16.sp,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold // Force Bold
                )
            }

            // Oled mode switch styles with animated transitions
            val animatedCheckedTrackColor by animateColorAsState(
                targetValue = if (oledMode) Color(0xFF1A1A1A) else colors.primaryAccent, // Dark gray for OLED mode
                animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
                label = "toggle_checked_track_color"
            )

            val animatedUncheckedTrackColor by animateColorAsState(
                targetValue = if (oledMode) Color(0xFF1A1A1A) else Color.Gray, // Dark gray for OLED mode
                animationSpec = tween(durationMillis = 1200, easing = MotionTokens.Easing.emphasizedDecelerate),
                label = "toggle_unchecked_track_color"
            )

            val switchColors = SwitchDefaults.colors(
                checkedThumbColor = if (oledMode) colors.primaryAccent else Color.White,
                checkedTrackColor = animatedCheckedTrackColor,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = animatedUncheckedTrackColor,
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
        }
    }
}

@Composable
fun BackArrowButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    Box(
        modifier = modifier
            .size(if (isSmallScreen) 48.dp else 56.dp)
            .background(
                color = colors.primaryAccent.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(if (isSmallScreen) 24.dp else 28.dp)) {
            val arrowPath = Path().apply {
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
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ThemeColors,
    cardBackground: Color,
    accent: Boolean = false  // NEW: accent parameter
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = if (animLevel == 2) {
            tween<Float>(150, easing = LinearEasing)  // Even "None" animates
        } else {
            spring<Float>(
                dampingRatio = 0.7f,
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
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) colors.primaryAccent else cardBackground
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        modifier = modifier
            .scale(scale)
            .border(
                width = 1.dp,
                color = if (accent) Color.Transparent else colors.border,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Text(
            text,
            color = if (accent) {
                if (colors.primaryAccent.luminance() > 0.6f) Color.Black else Color.White
            } else {
                colors.textPrimary
            },
            fontSize = if (isSmallScreen) 16.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
            fontFamily = quicksandFontFamily,
            textAlign = TextAlign.Center
        )
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
    val animDuration = when (animLevel) {
        0 -> 585  // Max quality
        1 -> 450  // Medium quality
        else -> 0 // None
    }

    val dismissOnClickOutside = LocalDismissOnClickOutside.current

    // Barrier outside AnimatedVisibility - dismisses on tap when visible = true
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dismissOnClickOutside) {
                    if (dismissOnClickOutside) {
                        detectTapGestures {
                            // Tap empty space to dismiss
                            onDismiss()
                        }
                    } else {
                        // Consume taps to prevent interaction with background, but do NOT dismiss
                        detectTapGestures { }
                    }
                }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween<Float>(animDuration, easing = MotionTokens.Easing.silk)) +
                scaleIn(
                    initialScale = 0.7f,
                    animationSpec = if (animLevel == 2) {
                        // No bounce when animation is disabled
                        tween<Float>(0, easing = LinearEasing)
                    } else {
                        // Bouncy spring for Max/Medium
                        spring<Float>(dampingRatio = 0.35f, stiffness = 500f)
                    }
                ),
        exit = fadeOut(animationSpec = tween<Float>(animDuration, easing = MotionTokens.Easing.silk)) +
                scaleOut(
                    targetScale = 0.7f,
                    animationSpec = tween<Float>(animDuration, easing = MotionTokens.Easing.silk)
                )
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
                    Text(
                        text = "VERBOSE OUTPUT",
                        fontSize = if (isSmallScreen) 24.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.textPrimary
                    )

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
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
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
    isTablet: Boolean
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(visible) {
        if (visible) {
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
                    // Decorative top accent
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.primaryAccent)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Header
                    Text(
                        text = "APP TARGETING",
                        fontSize = if (isSmallScreen) 24.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.textPrimary,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = "Select apps to exclude from cleanup",
                        fontSize = 14.sp,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

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
                                // "Exclude All" Header Item
                                item {
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

                                items(allApps) { (packageName, appName) ->
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
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = quicksandFontFamily
                                            )
                                            Text(
                                                text = packageName,
                                                color = colors.textSecondary,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold // Force Bold
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
fun AggressiveWarningDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false // Added
) {
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
                        fontSize = if (isSmallScreen) 20.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent
                    )

                    Text(
                        text = "Using Aggressive mode is powerful, sure, but comes with some side effects that you should know about:",
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold // Force Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🛑", fontSize = 20.sp)
                            Column {
                                Text(
                                    "Resets Defaults",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "Your default browser and keyboard will be reset",
                                    fontSize = 13.sp,
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
                            Text("📵", fontSize = 20.sp)
                            Column {
                                Text(
                                    "Connectivity Issues",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "Loss of WiFi-Calling/VoLTE capability. Fix: Settings → Connections → SIM manager, toggle SIM off and back on",
                                    fontSize = 13.sp,
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
                            Text("☠️", fontSize = 20.sp)
                            Column {
                                Text(
                                    "... and other stuff we haven't yet documented",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent, // Accented color
                                    fontFamily = quicksandFontFamily
                                )
                                Text(
                                    "ARE YOU CERTAIN WHATEVER YOU'RE DOING IS WORTH IT?",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "This mode is NOT recommended. If you're just pushing buttons to see what they do, don't mess with this.",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                    fontFamily = quicksandFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FlatButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            accent = false,
                            enabled = true,
                            colors = colors,
                            maxLines = 1,
                            oledMode = oledMode
                        )
                        FlatButton(
                            text = "OK",
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
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
                    fontSize = if (isSmallScreen) 20.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "GAMA can't open GPUWatch directly. You will be taken to Developer Options where you can find and enable 'GPUWatch' yourself.",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold // Force Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlatButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        accent = false,
                        enabled = true,
                        colors = colors,
                        maxLines = 1,
                        oledMode = oledMode
                    )
                    FlatButton(
                        text = "Open",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
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