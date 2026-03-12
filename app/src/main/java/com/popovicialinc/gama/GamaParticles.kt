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
import androidx.compose.ui.graphics.drawscope.withTransform
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
// Particles: ParticleState, CelestialState, ParticlesOverlay
// ============================================================

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
    val screenHeightPx = remember(configuration) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    // Celestial position — recomputed at most once per second via a tick counter.
    // Previously computed inside Canvas on every draw frame (60–120× per second),
    // which allocated a Calendar object and ran trigonometry every frame needlessly
    // since the sun/moon only moves visibly once per minute.
    var celestialTickSecond by remember { mutableStateOf(0L) }
    val celestialState = remember(celestialTickSecond, timeModeEnabled) {
        if (!timeModeEnabled) null
        else calculateCelestialPosition(screenWidthPx, screenHeightPx, timeOffsetHours)
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
    // Physics inputs captured per-frame (read on main thread inside withFrameNanos)
    // Frame counter — Canvas reads this to know when to redraw each frame.
    var frameCount by remember { mutableLongStateOf(0L) }
    var lastFrameTime by remember { mutableStateOf(0L) }

    // Convert speed setting to multiplier
    val speedMultiplier = when (particleSpeed) {
        0 -> 1.5f
        1 -> 3.0f
        2 -> 4.5f
        else -> 3.0f
    }

    // Pre-compute daytime once per frame outside the draw loop (avoids Calendar
    // allocation inside Canvas which runs on the render thread every frame)
    var frameIsDaytime by remember { mutableStateOf(true) }

    LaunchedEffect(particles, enabled) {
        if (!enabled) return@LaunchedEffect

        lastFrameTime = 0L

        while (isActive) {
            // ── withFrameNanos: run on main thread, kept as short as possible ───
            // Physics runs BEFORE withFrameNanos so the updated positions are
            // ready the instant the frame callback fires — no extra round-trip.
            // On the very first iteration physics hasn't run yet (particles are
            // pre-heated at creation time), which is fine.
            withFrameNanos { time ->
                // IDEAL_FRAME = 1/120s. If the gap since the last frame is larger
                // than this (e.g. a panel just opened and recomposition ate 25ms),
                // we clamp to exactly one ideal step. Particles appear to pause for
                // one frame rather than lurching forward to "catch up".
                val IDEAL_FRAME = 0.00833f // 8.33ms = 1/120s
                val frameDelta = if (lastFrameTime > 0L) {
                    val raw = (time - lastFrameTime) / 1_000_000_000f
                    if (raw > IDEAL_FRAME * 1.5f) IDEAL_FRAME else raw  // clamp on slow frames
                } else {
                    IDEAL_FRAME
                }
                lastFrameTime = time

                val frameRotX = if (parallaxEnabled) animatedRotationX else 0f
                val frameRotY = if (parallaxEnabled) animatedRotationY else 0f
                val frameSens = if (parallaxEnabled) parallaxSensitivity else 0f
                frameIsDaytime = isDaytime(timeOffsetHours)
                // Update celestial position at most once per second (not every frame)
                val nowSec = time / 1_000_000_000L
                if (nowSec != celestialTickSecond) celestialTickSecond = nowSec
                frameCount++   // invalidates Canvas on every frame

                // ── Physics on the main thread inside the frame callback ─────────
                // Counter-intuitive but correct for 120fps: the physics work per
                // frame is tiny (150 particles × simple arithmetic ≈ 50µs) and
                // running it here means zero thread-switch overhead. The previous
                // withContext(Dispatchers.Default) forced a full thread context
                // switch AFTER every frame callback, adding ~1-2ms of scheduling
                // latency that showed up as consistent frame drops.
                particles.forEach { p ->
                    p.update(speedMultiplier, time, frameRotX, frameRotY,
                        frameDelta, frameSens, starMode, timeModeEnabled, frameIsDaytime)
                }
            }
        }
    }

    // Animated alpha for celestial objects (sun/moon) with smooth ease-in-out fade
    // When panels open: stay visible but blur instead of hiding or scaling
    val celestialAlpha by animateFloatAsState(
        targetValue = if (timeModeEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "celestial_alpha"
    )
    // Blur radius for moon/sun when a panel is open — replaces scale/fade animation
    val celestialBlurRadius by animateDpAsState(
        targetValue = if (timeModeEnabled && anyPanelOpen) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "celestial_blur"
    )

    // remember blocks MUST be called unconditionally (Compose rules), so they live
    // outside the particleAlpha > 0.01f guard below.
    // Reusable paths — allocated once, never recreated.
    val reusableStarPath  = remember { Path() }
    val reusableRayPath   = remember { Path() }
    val reusableFullDisc  = remember { android.graphics.Path() }
    val reusableBitePath  = remember { android.graphics.Path() }
    val reusableCrescent  = remember { android.graphics.Path() }

    if (particleAlpha > 0.01f) {
        // Pre-compute base RGB int (alpha stripped) so we can reconstruct any alpha
        // variant with Color((alpha shl 24) or colorArgb) inside the draw loop —
        // avoiding color.copy(alpha=…) which allocates a new Color object per call.
        // At 150 particles × 120fps this saves ~18,000 allocations/sec.
        val colorArgb = color.toArgb() and 0x00FFFFFF

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Use graphicsLayer alpha instead of Modifier.alpha() — graphicsLayer
                // composites on the GPU without an extra offscreen render pass.
                .graphicsLayer(alpha = particleAlpha)
                .pointerInput(Unit) {}
        ) {
            // Reading frameCount tells Compose this Canvas depends on it,
            // so it redraws every time the physics loop increments it.
            @Suppress("UNUSED_VARIABLE") val frame = frameCount
            val useStars = starMode || (timeModeEnabled && !frameIsDaytime)
            particles.forEach { particle ->
                if (useStars) {
                    val cx = size.width * particle.x
                    val cy = size.height * particle.y
                    val r  = particle.size * 1.2f
                    val a  = particle.alpha * particleAlpha * 0.6f

                    // Reuse path object — reset() clears without allocating
                    reusableStarPath.reset()
                    reusableStarPath.moveTo(cx,            cy - r)
                    reusableStarPath.lineTo(cx + r * 0.25f, cy - r * 0.25f)
                    reusableStarPath.lineTo(cx + r,         cy)
                    reusableStarPath.lineTo(cx + r * 0.25f, cy + r * 0.25f)
                    reusableStarPath.lineTo(cx,             cy + r)
                    reusableStarPath.lineTo(cx - r * 0.25f, cy + r * 0.25f)
                    reusableStarPath.lineTo(cx - r,         cy)
                    reusableStarPath.lineTo(cx - r * 0.25f, cy - r * 0.25f)
                    reusableStarPath.close()

                    // Use int-based Color constructor to avoid color.copy() allocation
                    val aInt  = ((a.coerceIn(0f,1f) * 255f).toInt() shl 24) or colorArgb
                    val a8Int = ((a * 0.8f).coerceIn(0f,1f) * 255f).toInt() shl 24 or colorArgb
                    drawPath(path = reusableStarPath, color = Color(aInt))
                    drawCircle(color = Color(a8Int), radius = r * 0.3f,
                        center = Offset(cx, cy))
                } else {
                    val a = particle.alpha * particleAlpha * 0.5f
                    val aInt = ((a.coerceIn(0f,1f) * 255f).toInt() shl 24) or colorArgb
                    drawCircle(
                        color = Color(aInt),
                        radius = particle.size,
                        center = Offset(size.width * particle.x, size.height * particle.y)
                    )
                }
            }

        }

        // Celestial objects (sun/moon) drawn in a separate layer so we can blur
        // them independently when a panel is open — no scale/fade, just a soft blur.
        if (timeModeEnabled && celestialAlpha > 0.01f) {
            val blurMod = if (celestialBlurRadius.value > 0f)
                Modifier.blur(radius = celestialBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            else Modifier

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod)
                    .graphicsLayer(alpha = celestialAlpha)
                    .pointerInput(Unit) {}
            ) {
                @Suppress("UNUSED_VARIABLE") val frame = frameCount
                celestialState?.let { cel ->
                    val adjustedX = if (isLandscape) cel.x * 0.5f else cel.x
                    val isSun = frameIsDaytime
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
                            // Reuse path to avoid 8 allocations per frame
                            val perpAngle = angle + (PI / 2).toFloat()
                            val baseWidth = rayWidth
                            val tipWidth = rayWidth * 0.3f
                            reusableRayPath.reset()
                            reusableRayPath.moveTo(
                                rayStart.x + cos(perpAngle) * baseWidth,
                                rayStart.y + sin(perpAngle) * baseWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x + cos(perpAngle) * tipWidth,
                                rayEnd.y + sin(perpAngle) * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x - cos(perpAngle) * tipWidth,
                                rayEnd.y - sin(perpAngle) * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayStart.x - cos(perpAngle) * baseWidth,
                                rayStart.y - sin(perpAngle) * baseWidth
                            )
                            reusableRayPath.close()

                            drawPath(
                                path = reusableRayPath,
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
                        // Draw a proper crescent moon using canvas path clipping
                        val moonCenter = Offset(adjustedX, cel.y)
                        val moonR = cel.size * 2f   // outer radius of the full disc
                        val biteR = moonR * 0.82f   // radius of the "shadow bite"
                        // The shadow circle is offset to the upper-right, carving the crescent
                        val biteCenter = Offset(moonCenter.x + moonR * 0.48f, moonCenter.y - moonR * 0.12f)

                        // 1. Soft glow halo behind the crescent
                        for (i in 5 downTo 1) {
                            drawCircle(
                                color = color.copy(alpha = effectiveAlpha * 0.05f / i),
                                radius = moonR * (1f + i * 0.28f),
                                center = moonCenter
                            )
                        }

                        // 2. Draw crescent using drawIntoCanvas with native path clipping
                        drawIntoCanvas { canvas ->
                            val nCanvas = canvas.nativeCanvas
                            nCanvas.save()

                            // Reuse pre-allocated Path objects — avoids 3 allocations per frame
                            reusableFullDisc.reset()
                            reusableFullDisc.addCircle(moonCenter.x, moonCenter.y, moonR, android.graphics.Path.Direction.CW)

                            reusableBitePath.reset()
                            reusableBitePath.addCircle(biteCenter.x, biteCenter.y, biteR, android.graphics.Path.Direction.CW)

                            reusableCrescent.reset()
                            reusableCrescent.op(reusableFullDisc, reusableBitePath, android.graphics.Path.Op.DIFFERENCE)

                            // Clip to the crescent shape
                            nCanvas.clipPath(reusableCrescent)

                            // Fill the crescent with a warm moonlight gradient
                            val gradPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                shader = android.graphics.RadialGradient(
                                    moonCenter.x - moonR * 0.2f,
                                    moonCenter.y - moonR * 0.2f,
                                    moonR * 1.1f,
                                    intArrayOf(
                                        color.copy(alpha = effectiveAlpha).toArgb(),
                                        color.copy(alpha = effectiveAlpha * 0.85f).toArgb()
                                    ),
                                    floatArrayOf(0f, 1f),
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                            }
                            nCanvas.drawPath(reusableCrescent, gradPaint)

                            // Subtle inner-edge glow along the lit side of the crescent
                            val rimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = moonR * 0.06f
                                this.color = color.copy(alpha = effectiveAlpha * 0.55f).toArgb()
                                maskFilter = android.graphics.BlurMaskFilter(
                                    moonR * 0.12f,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            nCanvas.drawPath(reusableCrescent, rimPaint)

                            nCanvas.restore()
                        }

                        // 3. Small craters on the visible crescent face (left side only)
                        val craterPositions = listOf(
                            Pair(-0.30f, -0.20f) to 0.09f,
                            Pair(-0.20f,  0.28f) to 0.07f,
                            Pair(-0.42f,  0.08f) to 0.06f
                        )
                        craterPositions.forEach { (offset, craterSize) ->
                            val craterCenter = Offset(
                                moonCenter.x + moonR * offset.first,
                                moonCenter.y + moonR * offset.second
                            )
                            drawCircle(
                                color = Color.Black.copy(alpha = effectiveAlpha * 0.18f),
                                radius = moonR * craterSize,
                                center = craterCenter
                            )
                            drawCircle(
                                color = color.copy(alpha = effectiveAlpha * 0.25f),
                                radius = moonR * craterSize * 0.7f,
                                center = Offset(craterCenter.x - moonR * craterSize * 0.15f, craterCenter.y - moonR * craterSize * 0.15f)
                            )
                        }
                    } // end else (moon)
                } // end celestialState?.let
            } // end celestial Canvas
        } // end if timeModeEnabled
    }
}

// CompositionLocals
