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
        val naturalForceY = -0.018f * speed * speedMultiplier // Negative = upward drift — raised from 0.01 for snappier movement

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

        // Apply forces to velocity.
        // Forces are per-tick impulse magnitudes calibrated to a 60Hz reference.
        // "* deltaTime * 60f" normalises them to any tick rate:
        //   at 60Hz: deltaTime=1/60, so factor = 1.0x (identical to original)
        //   at 120Hz: deltaTime=1/120, applied 2x more often → same per-second effect
        //   at 30Hz: deltaTime=1/30, applied half as often → same per-second effect
        velocityX += totalForceX * deltaTime * 60f
        velocityY += totalForceY * deltaTime * 60f

        // Frame-rate-independent damping, normalised to the 60Hz reference.
        // Fixed damping (e.g. *= 0.94 every tick) is wrong at non-60Hz rates:
        //   at 120Hz: 0.94^120/s = 0.00076 → particles stop almost instantly
        //   at 30Hz:  0.94^30/s  = 0.161   → particles barely damp at all
        // pow(0.94, deltaTime * 60) gives 0.94^1 at 60Hz (identical to original),
        // 0.94^0.5 at 120Hz, 0.94^2 at 30Hz — all decaying at the same per-second rate.
        // Raised from 0.92 → 0.94 so the stronger naturalForce above translates to
        // visibly snappier movement rather than being absorbed before particles build speed.
        val frameDamping = Math.pow(0.94, (deltaTime * 60f).toDouble()).toFloat()
        velocityX *= frameDamping
        velocityY *= frameDamping

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

// ─────────────────────────────────────────────────────────────────────────────
// PhysicsInputs — volatile snapshot shared between the Compose main thread
// (writes) and the physics background thread (reads).
//
// Why @Volatile instead of AtomicXxx:
//   • Float/Boolean reads/writes are effectively atomic on 64-bit ARM (all
//     modern Android).  @Volatile adds the happens-before memory barrier so
//     the background thread always sees the latest value — no stale reads.
//   • Zero allocation, zero lock contention, zero GC pressure.
//   • Worst case of a racy read: one particle appears at its previous position
//     for one 16ms frame — completely invisible to the human eye.
// ─────────────────────────────────────────────────────────────────────────────
private class PhysicsInputs {
    @Volatile var rotX:     Float   = 0f
    @Volatile var rotY:     Float   = 0f
    @Volatile var sens:     Float   = 0f
    @Volatile var speed:    Float   = 3f
    @Volatile var star:     Boolean = false
    @Volatile var timeMode: Boolean = false
    @Volatile var daytime:  Boolean = true
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
    isLandscape: Boolean = false, // NEW: Constrain celestials to left half in landscape
    nativeRefreshRate: Boolean = false, // true = render every vsync; false = skip every other (default, saves battery)
    quarterRefreshRate: Boolean = false  // true = render at 1/4 native rate; only applies when nativeRefreshRate is false
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

    // ── Device capability cap ─────────────────────────────────────────────────
    // On constrained devices (low-RAM or single/dual-core) the user's selected
    // particle count is silently capped so the physics thread never competes badly
    // with the main thread.  Thresholds are conservative:
    //   ≤ 1 GB total RAM  → max 50  (entry-level: Unisoc, old MediaTek)
    //   ≤ 2 GB total RAM  → max 100 (mid-range: SD450, Helio P22)
    //   ≤ 3 GB total RAM  → max 150 (lower-mid: SD625, SD660)
    //   > 3 GB            → no cap  (SD700+, Dimensity 900+, etc.)
    // We use totalMem from ActivityManager — the only reliable cross-API source.
    val deviceParticleCap = remember {
        try {
            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            when {
                totalGb <= 1.0 -> 50
                totalGb <= 2.0 -> 100
                totalGb <= 3.0 -> 150
                else           -> Int.MAX_VALUE
            }
        } catch (_: Exception) { Int.MAX_VALUE } // fail open — don't cap if detection fails
    }

    // Calculate actual particle count based on setting, then apply device cap
    val actualParticleCount = (when (particleCount) {
        0 -> 75  // Low
        1 -> 150 // Medium
        2 -> 300 // High
        3 -> particleCountCustom.coerceIn(1, 500) // Custom (clamped to 1-500)
        else -> 150
    }).coerceAtMost(deviceParticleCap)

    val particles = remember(actualParticleCount, particleSpeed, parallaxSensitivity) {
        // Particles are created with positions spread across the screen and
        // modest initial velocities — no giant pre-heat spike that would cause
        // the physics engine to thrash on the first few frames.
        // The background physics loop warms them naturally over ~0.5 s before
        // particleAlpha reaches 1f (the fade-in takes 2.1 s), so there is
        // nothing visible to stutter over.
        List(actualParticleCount) {
            val spd = kotlin.random.Random.nextFloat() * 1.2f + 0.3f   // 0.3–1.5
            ParticleState(
                x     = kotlin.random.Random.nextFloat(),
                y     = kotlin.random.Random.nextFloat(),
                size  = kotlin.random.Random.nextFloat() * 6f + 1f,
                speed = spd,
                alpha = kotlin.random.Random.nextFloat() * 0.7f + 0.3f
            ).also { p ->
                // Give each particle a gentle initial upward nudge so they start
                // moving immediately, but keep velocities well within normal
                // operating range — no clamping, no physics thrash.
                p.velocityX = (kotlin.random.Random.nextFloat() - 0.5f) * spd * 0.4f
                p.velocityY = -spd * 0.8f
            }
        }
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
                    } catch (_: Exception) {
                        // Sensor read failed — skip this event silently
                    }
                }
            }

            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {
                // No-op
            }
        }

        // Register sensor with UI delay (~15 Hz) — cuts sensor wake-ups by ~70%
        // compared to SENSOR_DELAY_GAME (~50 Hz) with no visible difference for
        // a subtle parallax effect that only reacts to slow, deliberate tilts.
        sensorManager?.registerListener(
            listener,
            rotationSensor,
            android.hardware.SensorManager.SENSOR_DELAY_UI
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

    // Speed multiplier — converted from the 0/1/2 setting once here so the
    // physics thread and inputs-sync can both reference the same value.
    val speedMultiplier = when (particleSpeed) {
        0 -> 1.5f
        1 -> 3.0f
        2 -> 4.5f
        else -> 3.0f
    }

    // frameCount — incremented by the render-trigger LaunchedEffect on the main
    // thread each time we want the Canvas to redraw.  Canvas reads this value to
    // establish a Compose snapshot dependency; it redraws every time it changes.
    var frameCount by remember { mutableLongStateOf(0L) }

    // frameIsDaytime — updated by the render-trigger at most once per second.
    // Kept as Compose state so Canvas reads it correctly via snapshot.
    var frameIsDaytime by remember { mutableStateOf(true) }

    // ── Thread-safe input snapshot ────────────────────────────────────────────
    // Written on the Compose main thread; read on the physics background thread.
    val physicsInputs = remember { PhysicsInputs() }

    // ── Inputs sync — main thread ─────────────────────────────────────────────
    // Runs whenever any physics-relevant setting changes.  Cheap: just copies
    // a handful of scalars into the volatile fields.  The physics thread reads
    // these fields independently — no lock, no coordination needed.
    LaunchedEffect(
        animatedRotationX, animatedRotationY, parallaxEnabled,
        parallaxSensitivity, speedMultiplier, starMode, timeModeEnabled, timeOffsetHours
    ) {
        physicsInputs.rotX     = if (parallaxEnabled) animatedRotationX else 0f
        physicsInputs.rotY     = if (parallaxEnabled) animatedRotationY else 0f
        physicsInputs.sens     = if (parallaxEnabled) parallaxSensitivity else 0f
        physicsInputs.speed    = speedMultiplier
        physicsInputs.star     = starMode
        physicsInputs.timeMode = timeModeEnabled
        // daytime is updated by the render trigger (once/sec) — no Calendar alloc here
    }

    // ── Actual display refresh rate ───────────────────────────────────────────
    //
    // Queried once and shared by both the physics loop and the render trigger.
    // Works for any Hz: 50, 60, 90, 120, 144, or anything adaptive — nothing
    // below is hardcoded to a specific display rate.
    val displayHz: Float = remember(context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE)
                            as android.view.WindowManager).defaultDisplay.refreshRate
            }
        } catch (_: Exception) { 60f }
    }.coerceAtLeast(1f)

    // ── Vsync divisor ─────────────────────────────────────────────────────────
    //
    // Single number that controls how many display vsyncs are skipped between
    // physics ticks and canvas redraws.  Everything flows from this:
    //
    //   nativeRefreshRate = true            → 1  (tick every vsync — full rate)
    //   quarterRefreshRate = true           → 4  (tick every 4th vsync — 1/4 rate)
    //   capable/mid device, half rate       → 2  (tick every other vsync)
    //   very weak device (≤2 GB), half      → 3  (tick every third vsync)
    //
    // quarterRefreshRate is ignored when nativeRefreshRate is true.
    //
    // Example results at any display Hz:
    //   120Hz display, native   → 120Hz physics, 8.3ms  render interval
    //   120Hz display, half     → 60Hz  physics, 16.7ms render interval
    //   120Hz display, quarter  → 30Hz  physics, 33.3ms render interval
    //   90Hz  display, native   → 90Hz  physics, 11.1ms render interval
    //   90Hz  display, half     → 45Hz  physics, 22.2ms render interval
    //   90Hz  display, quarter  → 22Hz  physics, 44.4ms render interval
    //   60Hz  display, quarter  → 15Hz  physics, 66.7ms render interval
    val vsyncDivisor: Int = when {
        nativeRefreshRate        -> 1
        quarterRefreshRate       -> 4   // 1/4 native rate — lightest option
        deviceParticleCap <= 100 -> 3   // very weak: 1/3 native rate
        else                     -> 2   // standard: half rate
    }

    // Physics target Hz: display rate divided by divisor.
    // Clamped so extreme displays (24Hz film, 240Hz gaming) stay sane.
    val physicsHz: Long = (displayHz / vsyncDivisor).toLong().coerceIn(8L, 144L)

    // Render interval in nanoseconds.
    // At 90Hz + divisor 2: 2 * 1e9 / 90 = 22.2ms. Always correct, any Hz.
    val renderIntervalNs: Long = (vsyncDivisor * 1_000_000_000L / displayHz).toLong()

    // ── Physics loop — Dispatchers.Default (background thread) ───────────────
    //
    // Ticks at physicsHz derived from the actual display rate above.
    // MAX_DELTA clamp prevents position jumps if the thread wakes late.
    LaunchedEffect(particles, enabled, physicsHz) {
        if (!enabled) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            val TARGET_NS = 1_000_000_000L / physicsHz
            val MAX_DELTA = 1f / physicsHz.toFloat()
            var lastPhysicsNs = 0L

            while (isActive) {
                val now     = System.nanoTime()
                val elapsed = if (lastPhysicsNs == 0L) TARGET_NS else now - lastPhysicsNs

                if (elapsed >= TARGET_NS) {
                    val delta = (elapsed / 1_000_000_000f).coerceAtMost(MAX_DELTA)
                    lastPhysicsNs = now

                    // Read the volatile snapshot once (one memory barrier for the batch)
                    val inp = physicsInputs
                    val rotX     = inp.rotX
                    val rotY     = inp.rotY
                    val sens     = inp.sens
                    val spd      = inp.speed
                    val star     = inp.star
                    val timeMode = inp.timeMode
                    val day      = inp.daytime

                    particles.forEach { p ->
                        p.update(spd, now, rotX, rotY, delta, sens, star, timeMode, day)
                    }
                }

                // Sleep until the next physics tick
                val sleepMs = maxOf(1L, (TARGET_NS - (System.nanoTime() - lastPhysicsNs)) / 1_000_000L)
                delay(sleepMs)
            }
        }
    }

    // ── Render trigger — main thread ──────────────────────────────────────────
    //
    // withFrameNanos fires at the display's native refresh rate. We compare
    // elapsed time against renderIntervalNs to skip vsyncs we don't need,
    // matching canvas redraws to the physics tick rate exactly.
    // Because renderIntervalNs flows from displayHz, this works correctly
    // for any refresh rate without any special-casing.
    LaunchedEffect(enabled, renderIntervalNs, timeOffsetHours) {
        if (!enabled) return@LaunchedEffect

        val TARGET_RENDER_NS = renderIntervalNs
        var lastRenderNs     = 0L

        while (isActive) {
            withFrameNanos { time ->
                if (lastRenderNs > 0L && time - lastRenderNs < TARGET_RENDER_NS) {
                    return@withFrameNanos
                }
                lastRenderNs = time

                // Update once-per-second state (celestial position + daytime flag).
                // isDaytime() calls Calendar.getInstance() + trig — run it at most
                // once per second inside this gate instead of every render frame.
                val nowSec = time / 1_000_000_000L
                if (nowSec != celestialTickSecond) {
                    celestialTickSecond = nowSec
                    frameIsDaytime = isDaytime(timeOffsetHours)
                    physicsInputs.daytime = frameIsDaytime
                }

                // Invalidate Canvas — particles have moved since the last render
                frameCount++
            }
        }
    }

    // Animated alpha for celestial objects (sun/moon) with smooth ease-in-out fade
    val celestialAlpha by animateFloatAsState(
        targetValue = if (timeModeEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "celestial_alpha"
    )

    // Celestial blur: crossfade between sharp and blurred copies — mirrors the
    // technique used for the main content blur in GamaUI.
    //
    // celestialBlurAlpha animates 0→1 when a panel opens, 1→0 when it closes.
    // Two Canvas layers are composited: the sharp one fades OUT (1−alpha), the
    // blurred one fades IN (alpha).  The GPU holds a fixed RenderEffect the whole
    // time; only the layer alpha changes, which is essentially free.
    val celestialBlurTarget = timeModeEnabled && anyPanelOpen
    val celestialBlurAlpha by animateFloatAsState(
        targetValue = if (celestialBlurTarget) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = MotionTokens.Easing.emphasized),
        label = "celestial_blur_alpha"
    )

    // remember blocks MUST be called unconditionally (Compose rules), so they live
    // outside the particleAlpha > 0.01f guard below.
    // Reusable paths and Paint objects — allocated once, never recreated.
    val reusableStarPath  = remember { Path() }
    val reusableRayPath   = remember { Path() }
    val reusableFullDisc  = remember { android.graphics.Path() }
    val reusableBitePath  = remember { android.graphics.Path() }
    val reusableCrescent  = remember { android.graphics.Path() }

    // Reusable Paint objects for the moon crescent — previously allocated fresh
    // inside drawIntoCanvas on every draw frame (60-120x/sec), each constructing
    // a new Paint + RadialGradient shader.  Cached here; the shader is rebuilt
    // inside the draw block only when moonCenter/moonR/color/alpha actually change.
    val reusableGradPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val reusableRimPaint  = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
    }
    // Cache the last BlurMaskFilter radius so we only reconstruct it when moonR
    // actually changes (i.e. never at runtime — moonR is constant per celestial state).
    // BlurMaskFilter allocates a native object; rebuilding it every draw frame at
    // 30fps was the single biggest GC source on older JIT-based devices.
    var cachedBlurRadius  = remember { -1f }
    var cachedBlurFilter  = remember<android.graphics.BlurMaskFilter?> { null }

    // Crater data: constant fractions of moonR — list allocated once, not per frame.
    val craterData = remember {
        listOf(
            Triple(-0.30f, -0.20f, 0.09f),
            Triple(-0.20f,  0.28f, 0.07f),
            Triple(-0.42f,  0.08f, 0.06f)
        )
    }

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

        // Celestial objects (sun/moon) — drawn in two layers so blur can cross-
        // fade smoothly when panels open/close without a jarring snap.
        //
        //  Layer 1 (sharp):  graphicsLayer alpha = celestialAlpha * (1 − blurAlpha)
        //  Layer 2 (blurred): graphicsLayer alpha = celestialAlpha * blurAlpha
        //
        // Both layers share identical draw code via the celestialDrawContent lambda.
        if (timeModeEnabled && celestialAlpha > 0.01f) {

            // Shared draw logic — called once per active layer
            val drawCelestial: DrawScope.() -> Unit = drawLambda@{
                @Suppress("UNUSED_VARIABLE") val frame = frameCount
                val cel = celestialState ?: return@drawLambda
                val adjustedX = if (isLandscape) cel.x * 0.5f else cel.x
                val isSun = frameIsDaytime
                val effectiveAlpha = cel.alpha * celestialAlpha

                // Pre-compute base RGB int (alpha stripped) — same technique as the
                // particle draw loop.  Avoids color.copy(alpha=…) which allocates a
                // new Color object per call.  At 30fps on old 60hz devices these 13
                // calls would otherwise generate ~390 Color allocations/sec.
                val colorRgb = color.toArgb() and 0x00FFFFFF
                // Helper: assemble a Color from a pre-stripped RGB int + float alpha [0,1]
                fun colorWithAlpha(alpha: Float): Color =
                    Color((((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or colorRgb))

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
                                color = colorWithAlpha(effectiveAlpha * 0.5f)
                            )
                        }

                        // Draw outer glow (largest)
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha * 0.15f),
                            radius = cel.size * 2.0f,
                            center = sunCenter
                        )

                        // Draw middle glow
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha * 0.3f),
                            radius = cel.size * 1.4f,
                            center = sunCenter
                        )

                        // Draw main sun body
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha),
                            radius = cel.size,
                            center = sunCenter
                        )

                        // Draw bright core
                        drawCircle(
                            color = colorWithAlpha(effectiveAlpha * 0.9f),
                            radius = cel.size * 0.6f,
                            center = sunCenter
                        )
                    } else {
                        // Draw a proper crescent moon using canvas path clipping
                        val moonCenter = Offset(adjustedX, cel.y)
                        val moonR = cel.size * 2f
                        val biteR = moonR * 0.82f
                        val biteCenter = Offset(moonCenter.x + moonR * 0.48f, moonCenter.y - moonR * 0.12f)

                        // 1. Soft glow halo behind the crescent
                        for (i in 5 downTo 1) {
                            drawCircle(
                                color = colorWithAlpha(effectiveAlpha * 0.05f / i),
                                radius = moonR * (1f + i * 0.28f),
                                center = moonCenter
                            )
                        }

                        // 2. Draw crescent using drawIntoCanvas with native path clipping
                        drawIntoCanvas { canvas ->
                            val nCanvas = canvas.nativeCanvas
                            nCanvas.save()

                            reusableFullDisc.reset()
                            reusableFullDisc.addCircle(moonCenter.x, moonCenter.y, moonR, android.graphics.Path.Direction.CW)

                            reusableBitePath.reset()
                            reusableBitePath.addCircle(biteCenter.x, biteCenter.y, biteR, android.graphics.Path.Direction.CW)

                            reusableCrescent.reset()
                            reusableCrescent.op(reusableFullDisc, reusableBitePath, android.graphics.Path.Op.DIFFERENCE)

                            nCanvas.clipPath(reusableCrescent)

                            // Fill the crescent — reuse cached Paint, rebuild shader with current values.
                            // The RadialGradient must be rebuilt each frame because effectiveAlpha changes.
                            // Use int-math to avoid color.copy() allocations inside toArgb().
                            reusableGradPaint.shader = android.graphics.RadialGradient(
                                moonCenter.x - moonR * 0.2f,
                                moonCenter.y - moonR * 0.2f,
                                moonR * 1.1f,
                                intArrayOf(
                                    colorWithAlpha(effectiveAlpha).toArgb(),
                                    colorWithAlpha(effectiveAlpha * 0.85f).toArgb()
                                ),
                                floatArrayOf(0f, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            nCanvas.drawPath(reusableCrescent, reusableGradPaint)

                            // Rim glow — reuse cached Paint, update stroke/color for this frame.
                            // BlurMaskFilter is only rebuilt when moonR changes (never at runtime).
                            reusableRimPaint.strokeWidth = moonR * 0.06f
                            reusableRimPaint.color = colorWithAlpha(effectiveAlpha * 0.55f).toArgb()
                            val blurRadiusPx = moonR * 0.12f
                            if (blurRadiusPx != cachedBlurRadius) {
                                cachedBlurRadius = blurRadiusPx
                                cachedBlurFilter = android.graphics.BlurMaskFilter(
                                    blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            reusableRimPaint.maskFilter = cachedBlurFilter
                            nCanvas.drawPath(reusableCrescent, reusableRimPaint)

                            nCanvas.restore()
                        }

                        // 3. Small craters
                        craterData.forEach { (offsetX, offsetY, craterSize) ->
                            val craterCenter = Offset(
                                moonCenter.x + moonR * offsetX,
                                moonCenter.y + moonR * offsetY
                            )
                            drawCircle(
                                color = Color(((effectiveAlpha * 0.18f * 255f + 0.5f).toInt().coerceIn(0,255) shl 24) or 0x000000),
                                radius = moonR * craterSize,
                                center = craterCenter
                            )
                            drawCircle(
                                color = colorWithAlpha(effectiveAlpha * 0.25f),
                                radius = moonR * craterSize * 0.7f,
                                center = Offset(craterCenter.x - moonR * craterSize * 0.15f, craterCenter.y - moonR * craterSize * 0.15f)
                            )
                        }
                    } // end else (moon)
            } // end drawCelestial lambda

            // Single Canvas render with API-gated blur.
            //
            // Previously: drawCelestial() was called in two separate Canvas nodes
            // (one sharp, one blurred) that crossfaded — executing the entire draw
            // lambda twice per frame for the full transition duration.
            //
            // Now: one Canvas, always.  When a panel is open:
            //   API 31+: graphicsLayer renderEffect blurs in the draw phase only.
            //             Zero recomposition, zero extra draw-lambda execution.
            //   API < 31: no blur — celestial simply dims with the panel alpha.
            //             Old devices never had a GPU capable of blur at 30fps anyway.
            val celestialBlurDp = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                celestialBlurAlpha > 0.001f
            ) {
                (celestialBlurAlpha * 18f).dp
            } else {
                0.dp
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = celestialAlpha)
                    .then(
                        if (celestialBlurDp > 0.dp)
                            Modifier.blur(celestialBlurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        else Modifier
                    )
                    .pointerInput(Unit) {}
            ) { drawCelestial() }
        } // end if timeModeEnabled
    }
}

// CompositionLocals
