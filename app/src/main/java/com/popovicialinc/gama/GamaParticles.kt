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
        isDaytime: Boolean = true,
        // Pre-computed damping factor for this tick — avoids one Math.pow() per particle
        // Caller computes: Math.pow(0.94, (deltaTime * 60.0)).toFloat() once per tick
        frameDamping: Float = Math.pow(0.94, (deltaTime * 60.0)).toFloat()
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
        val naturalForceY = -0.007f * speed * speedMultiplier // Negative = upward drift — reduced for much slower movement

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
        // frameDamping is pre-computed by the caller once per tick — no Math.pow here
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

private const val STAR_ALPHA_BUCKETS = 8
private const val TRAIL_LUT_SIZE = 64

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
        0 -> 0.12f   // Slow  — 1× base speed (very gentle drift)
        1 -> 0.36f   // Medium — 3× Slow
        2 -> 0.72f   // Fast  — 6× Slow
        else -> 0.22f
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
    //
    // IMPORTANT — why we use supportedModes.maxOf { refreshRate } instead of
    // display.refreshRate:
    //
    //   On LTPO / adaptive-sync panels (Galaxy S23 Ultra, Pixel 8 Pro, etc.)
    //   `Display.getRefreshRate()` returns the *current live* rate, which the
    //   OS adaptive governor can idle down to 1–60 Hz when content appears still.
    //   If the physics and render loops are calibrated to that idle rate they will
    //   target 16.7ms intervals instead of 8.3ms — meaning the overlay renders at
    //   60fps even when the panel is actually running at 120Hz, producing judder.
    //
    //   `Display.getSupportedModes()` always exposes the hardware ceiling, so
    //   maxOf { refreshRate } gives the true panel maximum regardless of whatever
    //   rate the governor has currently chosen.  The physics + render intervals
    //   then stay correctly calibrated to the panel's native cadence.
    val displayHz: Float = remember(context) {
        try {
            val display =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE)
                                as android.view.WindowManager).defaultDisplay
                }
            // Prefer the maximum mode rate; fall back to live rate if modes unavailable.
            display?.supportedModes?.maxOfOrNull { it.refreshRate }
                ?: display?.refreshRate
                ?: 60f
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

                    // Pre-compute damping ONCE per tick instead of inside every particle.
                    // Math.pow is a JNI transcendental — at 300 particles × 60Hz this saves
                    // ~18,000 Math.pow() calls per second with zero visual difference.
                    val tickDamping = Math.pow(0.94, (delta * 60.0)).toFloat()

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
                        p.update(spd, now, rotX, rotY, delta, sens, star, timeMode, day, tickDamping)
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
    //
    // ── Star batching paths ───────────────────────────────────────────────────
    // Previously: ONE reusableStarPath was reset() + rebuilt with 8 lineTo calls
    // per particle per frame, then drawPath() was called 150× per frame.
    // GPU has to tessellate each star polygon separately — 150 draw calls/frame.
    //
    // Now: TWO batch paths accumulate ALL star geometry for all alpha buckets,
    // then drawPath() is called ONCE per alpha bucket (2 calls total).
    // GPU tessellates one combined path — massively cheaper.
    //
    // Alpha bucketing: stars vary in alpha per particle. We can't batch them all
    // into one drawPath call with a single color. The fix: quantize alpha into
    // N buckets and draw one batched path per bucket. 8 buckets covers the full
    // [0,1] range with steps of 0.125 — visually indistinguishable from per-particle
    // alpha, but reduces draw calls from 150 to ≤ 8.
    // ── Star line-draw buffers ────────────────────────────────────────────────
    // Stars are no longer drawn as filled concave polygons via Compose drawPath.
    // Instead each star is 4 line segments (H + V + 2 diagonals) with ROUND caps,
    // batched into a FloatArray per alpha bucket and dispatched as a single
    // nativeCanvas.drawLines() call — a direct GPU primitive with zero tessellation.
    //
    // Why this matters:
    //   OLD: Compose drawPath(concave polygon) → CPU ear-clip tessellation per frame
    //        = 3000+ path ops + 16 draw calls + full tessellation overhead
    //   NEW: nativeCanvas.drawLines(FloatArray) → GPU line primitive, no tessellation
    //        = ≤8 native draw calls regardless of particle count
    //
    // Each star needs 4 lines × 4 floats (x1,y1,x2,y2) = 16 floats.
    // Pre-allocate worst-case: actualParticleCount × 16 floats per bucket.
    val starLineBuffers = remember(actualParticleCount) {
        Array(STAR_ALPHA_BUCKETS) { FloatArray(actualParticleCount * 16) }
    }
    val starLineCounts  = remember { IntArray(STAR_ALPHA_BUCKETS) }
    val nativeStarPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style      = android.graphics.Paint.Style.STROKE
            strokeCap  = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = 2.5f   // fixed arm thickness; star SIZE is encoded in arm length (r)
        }
    }
    // circleBatchPaths still used for circle (non-star) particle mode
    val circleBatchPaths = remember { Array(STAR_ALPHA_BUCKETS) { Path() } }
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
    // Cached alpha for the moon's RadialGradient — only rebuild shader when alpha changes.
    // effectiveAlpha is stable most of the time (only changes during fade-in/out transitions),
    // so this skips the shader allocation entirely during steady-state rendering.
    var cachedMoonGradAlphaInt = remember { -1 }

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
            if (useStars) {
                // ── Native drawLines star rendering ──────────────────────────
                // Each star = 4 line segments (H, V, diagonal \, diagonal /)
                // with ROUND stroke caps. The intersecting lines with rounded
                // endpoints naturally form a ✦ sparkle shape.
                //
                // All lines for a given alpha bucket are packed into a single
                // pre-allocated FloatArray, then dispatched as ONE native
                // drawLines() call per bucket — a direct GPU line primitive
                // with ZERO path tessellation, ZERO Compose DrawScope overhead.
                //
                // floats layout per star: [x1,y1,x2,y2] × 4 lines = 16 floats

                // Zero counts (no allocation — just int writes)
                for (i in 0 until STAR_ALPHA_BUCKETS) starLineCounts[i] = 0

                // Pass 1: write line coords into per-bucket FloatArrays
                particles.forEach { particle ->
                    val cx  = size.width  * particle.x
                    val cy  = size.height * particle.y
                    val r   = particle.size * 1.2f
                    val rd  = r * 0.68f   // diagonal arms slightly shorter for clean ✦ look
                    val a   = particle.alpha * particleAlpha * 0.6f

                    val bucket = ((a.coerceIn(0f, 1f)) * (STAR_ALPHA_BUCKETS - 1) + 0.5f).toInt()
                        .coerceIn(0, STAR_ALPHA_BUCKETS - 1)

                    val buf = starLineBuffers[bucket]
                    var idx = starLineCounts[bucket]

                    // Horizontal arm ─
                    buf[idx] = cx - r;  buf[idx+1] = cy;       buf[idx+2] = cx + r;  buf[idx+3] = cy
                    // Vertical arm │
                    buf[idx+4] = cx;    buf[idx+5] = cy - r;   buf[idx+6] = cx;      buf[idx+7] = cy + r
                    // Diagonal arm ╲
                    buf[idx+8] = cx - rd; buf[idx+9] = cy - rd; buf[idx+10] = cx + rd; buf[idx+11] = cy + rd
                    // Diagonal arm ╱
                    buf[idx+12] = cx - rd; buf[idx+13] = cy + rd; buf[idx+14] = cx + rd; buf[idx+15] = cy - rd

                    starLineCounts[bucket] = idx + 16
                }

                // Pass 2: one nativeCanvas.drawLines() per non-empty bucket
                // ≤ STAR_ALPHA_BUCKETS total GPU draw calls, regardless of particle count.
                drawIntoCanvas { composeCanvas ->
                    val nc = composeCanvas.nativeCanvas
                    nativeStarPaint.color = colorArgb  // RGB set once; alpha overridden per bucket
                    for (bucket in 0 until STAR_ALPHA_BUCKETS) {
                        val count = starLineCounts[bucket]
                        if (count == 0) continue
                        val bucketAlpha = bucket.toFloat() / (STAR_ALPHA_BUCKETS - 1)
                        nativeStarPaint.alpha = (bucketAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                        nc.drawLines(starLineBuffers[bucket], 0, count, nativeStarPaint)
                    }
                }
            } else {
                // Circle mode — batch into alpha buckets exactly like star mode.
                // Reduces N draw calls (one per particle) to ≤ STAR_ALPHA_BUCKETS GPU draw calls.
                // Reuse circleBatchPaths — the star arrays are unused in this branch.
                for (i in 0 until STAR_ALPHA_BUCKETS) circleBatchPaths[i].reset()

                particles.forEach { particle ->
                    val a = particle.alpha * particleAlpha * 0.5f
                    val bucket = ((a.coerceIn(0f, 1f)) * (STAR_ALPHA_BUCKETS - 1) + 0.5f).toInt()
                        .coerceIn(0, STAR_ALPHA_BUCKETS - 1)
                    val cx = size.width  * particle.x
                    val cy = size.height * particle.y
                    val r  = particle.size
                    circleBatchPaths[bucket].addOval(
                        androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)
                    )
                }

                for (bucket in 0 until STAR_ALPHA_BUCKETS) {
                    if (circleBatchPaths[bucket].isEmpty) continue
                    val bucketAlpha = bucket.toFloat() / (STAR_ALPHA_BUCKETS - 1)
                    val aInt = ((bucketAlpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or colorArgb
                    drawPath(path = circleBatchPaths[bucket], color = Color(aInt))
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

                        // Draw sun rays — all 8 accumulated into reusableRayPath, then ONE drawPath call.
                        // Previously: 8 separate drawPath calls (reset + draw per ray).
                        val numRays = 8
                        val rayLength = cel.size * 1.8f
                        val rayWidth = cel.size * 0.18f

                        reusableRayPath.reset()  // single reset before all 8 rays
                        for (i in 0 until numRays) {
                            val angle = (i * 2 * PI / numRays).toFloat()
                            val cosA = cos(angle)
                            val sinA = sin(angle)
                            val rayStart = Offset(
                                sunCenter.x + cosA * cel.size * 1.1f,
                                sunCenter.y + sinA * cel.size * 1.1f
                            )
                            val rayEnd = Offset(
                                sunCenter.x + cosA * rayLength,
                                sunCenter.y + sinA * rayLength
                            )

                            val perpAngle = angle + (PI / 2).toFloat()
                            val cosPa = cos(perpAngle)
                            val sinPa = sin(perpAngle)
                            val baseWidth = rayWidth
                            val tipWidth = rayWidth * 0.3f
                            reusableRayPath.moveTo(
                                rayStart.x + cosPa * baseWidth,
                                rayStart.y + sinPa * baseWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x + cosPa * tipWidth,
                                rayEnd.y + sinPa * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayEnd.x - cosPa * tipWidth,
                                rayEnd.y - sinPa * tipWidth
                            )
                            reusableRayPath.lineTo(
                                rayStart.x - cosPa * baseWidth,
                                rayStart.y - sinPa * baseWidth
                            )
                            reusableRayPath.close()
                        }
                        // Single draw call for all 8 rays
                        drawPath(
                            path = reusableRayPath,
                            color = colorWithAlpha(effectiveAlpha * 0.5f)
                        )

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

                        // 1. Soft glow halo — single radial gradient drawCircle instead of 5 layered calls.
                        // The gradient replicates the falloff of the original 5 concentric circles
                        // but costs exactly 1 GPU draw call instead of 5.
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f   to colorWithAlpha(effectiveAlpha * 0.05f),
                                0.35f to colorWithAlpha(effectiveAlpha * 0.03f),
                                0.65f to colorWithAlpha(effectiveAlpha * 0.02f),
                                1f   to colorWithAlpha(0f),
                                center = moonCenter,
                                radius = moonR * (1f + 5 * 0.28f)
                            ),
                            radius = moonR * (1f + 5 * 0.28f),
                            center = moonCenter
                        )

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

                            // Fill the crescent — rebuild RadialGradient only when effectiveAlpha
                            // changes meaningfully. At steady state alpha is stable → zero rebuilds.
                            // Previously allocated a new native shader object every frame.
                            val alphaInt255 = (effectiveAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                            if (alphaInt255 != cachedMoonGradAlphaInt) {
                                cachedMoonGradAlphaInt = alphaInt255
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
                            }
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


// ═══════════════════════════════════════════════════════════════════════════════
// MatrixRainOverlay — HIGH-PERFORMANCE MATRIX DIGITAL RAIN
//
// HOW TO INTEGRATE:
//   Open GamaParticles.kt and paste this entire block just BEFORE the
//   final "// CompositionLocals" comment at the very bottom of the file.
//   No other changes are needed in GamaParticles.kt.
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// Internal data model — per-column mutable state.
// Written by the physics background thread; read by the Canvas main thread.
// No synchronisation needed: each column is only ever written by the physics
// loop and read by the Canvas in the same tick window; the worst that can
// happen is a one-frame-old read, which is invisible at 30-120 Hz.
// ─────────────────────────────────────────────────────────────────────────────
private class MatrixColumn(
    val x: Float,            // horizontal centre of this column, px
    @Volatile var headRow: Int,  // head position in discrete row units (row * fontSizePx = Y)
    val ticksPerStep: Int,   // how many physics ticks between each 1-row downward step
    val chars: CharArray,    // ring of characters shown in the trail
    val trailSlots: Int,     // how many character rows the trail spans
    @Volatile var charAge: Int = 0,
    val shuffleEvery: Int,   // physics ticks between random character swaps
    @Volatile var tickAccum: Int = 0,  // counts ticks until next row step
    // ── 3-D depth ────────────────────────────────────────────────────────────
    // 0.0 = far away (small, slow, dim)  |  1.0 = close (large, fast, bright)
    val depth: Float = 1.0f
)

// ─────────────────────────────────────────────────────────────────────────────
// Character palette — classic Matrix mix: katakana + digits + latin + symbols
// ─────────────────────────────────────────────────────────────────────────────
private val MATRIX_CHARS: CharArray = (
    "\u30A1\u30A2\u30A3\u30A4\u30A5\u30A6\u30A7\u30A8\u30A9\u30AA" + // ア-コ
    "\u30AB\u30AC\u30AD\u30AE\u30AF\u30B0\u30B1\u30B2\u30B3\u30B4" + // カ-ゴ
    "\u30B5\u30B6\u30B7\u30B8\u30B9\u30BA\u30BB\u30BC\u30BD\u30BE" + // サ-ゾ
    "\u30BF\u30C0\u30C1\u30C2\u30C3\u30C4\u30C5\u30C6\u30C7\u30C8" + // タ-ド
    "\u30C9\u30CA\u30CB\u30CC\u30CD\u30CE\u30CF\u30D0\u30D1\u30D2" + // ト-ヒ
    "\u30D3\u30D4\u30D5\u30D6\u30D7\u30D8\u30D9\u30DA\u30DB\u30DC" + // ビ-ボ
    "0123456789" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
    "abcdefghijklmnopqrstuvwxyz" +
    "@#\$%^&*-+=<>?!~|:;"
).toCharArray()

private fun randomMatrixChar(): Char =
    MATRIX_CHARS[kotlin.random.Random.nextInt(MATRIX_CHARS.size)]

// ─────────────────────────────────────────────────────────────────────────────
// MatrixRainOverlay — public composable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MatrixRainOverlay(
    enabled: Boolean,

    // ── Visual ────────────────────────────────────────────────────────────────
    /** Colour of the leading (head) character — default bright white */
    headColor: Color = Color(0xFFFFFFFF),
    /** Colour of the characters just below the head — the "hot" part of the trail */
    rainColor: Color = Color(0xFF00FF41),
    /** Colour blended into the deep tail — the "cool" / dim end */
    trailColor: Color = Color(0xFF003B00),
    /** Solid background painted under every column.
     *  Use Color.Black + backgroundAlpha=1 for the pure cinema look,
     *  or Color.Transparent + backgroundAlpha=0 (default) to overlay on top of
     *  the existing app background. */
    backgroundColor: Color = Color.Black,
    /** 0 = fully transparent (composites over app), 1 = fully opaque black canvas */
    backgroundAlpha: Float = 0.0f,

    // ── Motion ────────────────────────────────────────────────────────────────
    /** 0 = slow, 1 = medium (default), 2 = fast */
    speedLevel: Int = 1,

    // ── Appearance ────────────────────────────────────────────────────────────
    /** 0 = sparse columns, 1 = medium (default), 2 = dense */
    densityLevel: Int = 1,
    /** 0 = small glyphs, 1 = medium (default), 2 = large */
    fontSizeLevel: Int = 1,
    /** 0 = short trail, 1 = medium (default), 2 = full-screen-length trail */
    fadeLength: Int = 1,

    // ── Performance ───────────────────────────────────────────────────────────
    // No refresh-rate knob needed: the rain steps one row at a time, so the
    // tick rate is derived entirely from speedLevel. The render loop fires
    // only when a step actually occurs — no wasted vsync budget.
) {
    val context     = LocalContext.current
    val density     = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Screen size in physical pixels — recomputed only on rotation/resize
    val screenWidthPx = remember(configuration) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(configuration) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }

    // ── Derive numeric parameters from the 0/1/2 levels ──────────────────────
    val fontSizePx: Float = remember(fontSizeLevel, density) {
        with(density) {
            when (fontSizeLevel) {
                0    -> 11.sp.toPx()
                2    -> 20.sp.toPx()
                else -> 15.sp.toPx()
            }
        }
    }

    // Column width keeps cells roughly square; density just packs them tighter
    val columnWidth: Float = remember(fontSizePx, densityLevel) {
        when (densityLevel) {
            0    -> fontSizePx * 2.0f   // sparse  — wide gaps
            2    -> fontSizePx * 1.1f   // dense   — nearly touching
            else -> fontSizePx * 1.5f   // medium
        }
    }

    // Number of visible character slots per column (trail + head)
    val trailLength: Int = remember(fadeLength, screenHeightPx, fontSizePx) {
        val fullScreen = (screenHeightPx / fontSizePx).toInt() + 4
        when (fadeLength) {
            0    -> (fullScreen * 0.22f).toInt().coerceAtLeast(6)
            2    -> fullScreen + 3
            else -> (fullScreen * 0.55f).toInt().coerceAtLeast(10)
        }
    }

    // ── Discrete step timing ──────────────────────────────────────────────────
    // The physics loop runs at ~60 Hz. Each column steps exactly one character
    // row downward every N ticks — an instant jump with no sub-pixel movement.
    // This is the authentic Matrix look: no smooth scrolling, just discrete steps.
    //   0 (slow)   → step every 8 ticks  → ~7.5 rows/sec at 60 Hz
    //   1 (medium) → step every 4 ticks  → ~15 rows/sec at 60 Hz
    //   2 (fast)   → step every 2 ticks  → ~30 rows/sec at 60 Hz
    val baseTicksPerStep: Int = remember(speedLevel) {
        when (speedLevel) {
            0    -> 8
            2    -> 2
            else -> 4
        }
    }

    // ── Build column array ────────────────────────────────────────────────────
    // headRow is in discrete row units; Y = headRow * fontSizePx.
    // Columns independently vary their ticks-per-step by ±1 so they step at
    // slightly different rates, preserving the independent-column feel.
    val columns: Array<MatrixColumn> = remember(
        screenWidthPx, screenHeightPx, columnWidth, trailLength, baseTicksPerStep, fontSizePx
    ) {
        val rng = kotlin.random.Random
        val count = ((screenWidthPx / columnWidth).toInt() + 1).coerceAtLeast(1)
        val rowsOnScreen = (screenHeightPx / fontSizePx).toInt() + 2
        Array(count) { i ->
            val xPos  = i * columnWidth + columnWidth * 0.5f
            // Stagger initial heads above screen so rain fills in gradually
            val startRow = -(rng.nextFloat() * rowsOnScreen * 1.5f).toInt()
            val slots  = trailLength + rng.nextInt((trailLength / 4).coerceAtLeast(1))
            // ── 3-D depth ─────────────────────────────────────────────────────
            // Distribute columns across three depth layers with weighted probability:
            //   far (0.15–0.40)  ~30% of columns — small, slow, dim
            //   mid (0.45–0.70)  ~40% of columns — medium
            //   near (0.75–1.00) ~30% of columns — large, fast, bright
            val depth: Float = when (rng.nextInt(10)) {
                in 0..2  -> 0.15f + rng.nextFloat() * 0.25f  // far
                in 3..6  -> 0.45f + rng.nextFloat() * 0.25f  // mid
                else     -> 0.75f + rng.nextFloat() * 0.25f  // near
            }
            // Far columns step less often (slower); near columns step more often (faster).
            // Extra ticks added = up to +6 for the farthest columns.
            val depthTickBonus = ((1f - depth) * 6f).toInt()
            val colTicks = (baseTicksPerStep + depthTickBonus + rng.nextInt(2)).coerceAtLeast(1)
            MatrixColumn(
                x            = xPos,
                headRow      = startRow,
                ticksPerStep = colTicks,
                chars        = CharArray(slots) { randomMatrixChar() },
                trailSlots   = slots,
                shuffleEvery = 2 + rng.nextInt(6),
                tickAccum    = rng.nextInt(colTicks),  // stagger so not all step on tick 0
                depth        = depth
            )
        }
    }

    // ── Enable/disable fade ───────────────────────────────────────────────────
    val overlayAlpha by animateFloatAsState(
        targetValue    = if (enabled) 1f else 0f,
        animationSpec  = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label          = "matrix_alpha"
    )

    // ── Frame counter — establishes Compose snapshot dependency on Canvas ─────
    // IMPORTANT: declared before the early-return so the render loop can start
    // on the very first composition and drive the alpha animation forward.
    var frameCount by remember { mutableLongStateOf(0L) }

    // ── Physics tick rate ─────────────────────────────────────────────────────
    // The physics loop runs at a fixed ~60 Hz regardless of display refresh rate.
    // Columns step by whole rows on their own tick counters, so display Hz is
    // irrelevant — render only fires when at least one column has stepped.
    val physicsHz: Long = 60L
    val physicsTargetNs: Long = 1_000_000_000L / physicsHz

    // ── Physics loop — Dispatchers.Default (background thread) ───────────────
    //
    // Each tick: increment each column's tickAccum. When it reaches ticksPerStep,
    // reset it to 0 and advance headRow by 1. This is a discrete row-step — the
    // character grid jumps exactly one row, no sub-pixel movement ever.
    // Zero allocations inside the loop — only integer arithmetic.
    // IMPORTANT: this LaunchedEffect must live before the early-return so that
    // the physics loop is already running when the alpha animation completes.
    LaunchedEffect(columns, enabled, physicsHz) {
        if (!enabled) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val targetNs = physicsTargetNs
            var lastTick = System.nanoTime()
            while (isActive) {
                val now     = System.nanoTime()
                val elapsed = now - lastTick
                if (elapsed >= targetNs) {
                    lastTick = now
                    for (col in columns) {
                        col.tickAccum++
                        if (col.tickAccum >= col.ticksPerStep) {
                            col.tickAccum = 0
                            col.headRow++
                            // Recycle: once entire trail has scrolled off-screen,
                            // reset head to a random position above the top
                            if (col.headRow - col.trailSlots > (screenHeightPx / fontSizePx).toInt() + 1) {
                                col.headRow = -(kotlin.random.Random.nextFloat() *
                                    (screenHeightPx / fontSizePx) * 0.7f).toInt()
                                // Fresh character set for the recycled column
                                for (k in col.chars.indices) col.chars[k] = randomMatrixChar()
                            }
                        }
                        // Periodic character shuffle — gives the rain its "live data" feel
                        col.charAge++
                        if (col.charAge >= col.shuffleEvery) {
                            col.charAge = 0
                            val sz = col.chars.size
                            col.chars[kotlin.random.Random.nextInt(sz)] = randomMatrixChar()
                            if (sz > 4) {
                                col.chars[kotlin.random.Random.nextInt(sz)] = randomMatrixChar()
                            }
                        }
                    }
                } else {
                    delay(((targetNs - elapsed) / 1_000_000L).coerceAtLeast(1L))
                }
            }
        }
    }

    // ── Render trigger — main thread, physics-rate aligned ───────────────────
    // Columns only change state once per physics tick, so we render at that
    // rate too — no point firing the Canvas faster than the data changes.
    // withFrameNanos gates us to vsync boundaries; the elapsed check skips
    // frames where nothing has changed (i.e. most frames at high refresh rates).
    // IMPORTANT: also before the early-return for the same reason as the physics loop.
    LaunchedEffect(enabled, physicsTargetNs) {
        if (!enabled) return@LaunchedEffect
        var lastRender = 0L
        while (isActive) {
            withFrameNanos { ns ->
                if (ns - lastRender >= physicsTargetNs) {
                    lastRender = ns
                    frameCount++
                }
            }
        }
    }

    // Skip drawing when fully invisible (alpha animation not yet started or faded out).
    // The LaunchedEffects above must remain before this guard so they start immediately.
    if (overlayAlpha < 0.005f) return

    // ── Pre-allocated native Paints — reused every frame, ZERO allocation ────
    val nativePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias  = true
            typeface     = android.graphics.Typeface.MONOSPACE
            textAlign    = android.graphics.Paint.Align.CENTER
        }
    }
    // Bloom paint: same text drawn underneath with a large BlurMaskFilter.
    // BlurMaskFilter is expensive to construct — cached and only rebuilt when
    // the bloom radius changes (which only happens when fontSizePx changes,
    // i.e. never at runtime once the overlay is composed).
    val bloomPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias  = true
            typeface     = android.graphics.Typeface.MONOSPACE
            textAlign    = android.graphics.Paint.Align.CENTER
        }
    }
    // Track last bloom radius so we only rebuild BlurMaskFilter when needed
    var lastBloomRadius = remember { -1f }

    // Pre-compute ARGB ints so Color.toArgb() is never called inside the draw loop
    val headArgb  = remember(headColor)  { headColor.toArgb() }
    val rainArgb  = remember(rainColor)  { rainColor.toArgb() }
    val trailArgb = remember(trailColor) { trailColor.toArgb() }
    val bgArgb    = remember(backgroundColor, backgroundAlpha) {
        android.graphics.Color.argb(
            (backgroundAlpha * 255f).toInt().coerceIn(0, 255),
            android.graphics.Color.red(backgroundColor.toArgb()),
            android.graphics.Color.green(backgroundColor.toArgb()),
            android.graphics.Color.blue(backgroundColor.toArgb())
        )
    }

    // ── Trail colour LUT ─────────────────────────────────────────────────────
    // The hot-zone → tail colour lerp (rRain→rTrail, gRain→gTrail, bRain→bTrail)
    // was previously computed per-character per-frame inside the draw loop —
    // three float multiplications + three toInt() calls × up to ~500 characters
    // per frame = tens of thousands of operations/sec at high density.
    //
    // Precompute 64 evenly-spaced RGB steps covering t ∈ [0,1].  At draw time
    // map t → index with one multiply+cast, then read packed RGB from the LUT.
    // The LUT is rebuilt only when rain/trail colours change (never at runtime
    // once the overlay is composed with default colours).
    //
    // 64 steps → max colour error < 2/255 per channel — visually perfect.
    val trailRgbLut: IntArray = remember(rainArgb, trailArgb) {
        val rR = android.graphics.Color.red(rainArgb)
        val gR = android.graphics.Color.green(rainArgb)
        val bR = android.graphics.Color.blue(rainArgb)
        val rT = android.graphics.Color.red(trailArgb)
        val gT = android.graphics.Color.green(trailArgb)
        val bT = android.graphics.Color.blue(trailArgb)
        IntArray(TRAIL_LUT_SIZE) { i ->
            val fade = i.toFloat() / (TRAIL_LUT_SIZE - 1)
            val fi   = 1f - fade
            val r = (rR * fi + rT * fade).toInt().coerceIn(0, 255)
            val g = (gR * fi + gT * fade).toInt().coerceIn(0, 255)
            val b = (bR * fi + bT * fade).toInt().coerceIn(0, 255)
            (r shl 16) or (g shl 8) or b  // packed 0x00RRGGBB
        }
    }

    // ── Canvas ────────────────────────────────────────────────────────────────
    val currentFrame = frameCount

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = overlayAlpha)
            .pointerInput(Unit) {}  // opaque to touch
    ) {
        @Suppress("UNUSED_EXPRESSION") currentFrame
        drawIntoCanvas { composeCanvas ->
            val nc = composeCanvas.nativeCanvas

            // Optional solid background
            if (backgroundAlpha > 0.001f) {
                nc.drawColor(bgArgb)
            }

            // rHead/gHead/bHead and rRain/gRain/bRain/rTrail/gTrail/bTrail previously
            // decomposed here for per-character lerp. Now headArgb/rainArgb are set
            // directly as paint.color, and the trail lerp is replaced by trailRgbLut.
            // These per-frame Color.red/green/blue decompositions are no longer needed.
            val baseA  = overlayAlpha

            // ── Bloom pass then sharp pass ────────────────────────────────────
            // We render all columns twice:
            //   Pass 1 (bloom): head + hot-zone only, large blur, low alpha → glow halo
            //   Pass 2 (sharp): all characters at full crispness on top
            // Drawing bloom first means the sharp characters always render over the glow.
            //
            // Bloom radius scales with fontSizePx so it looks right at all glyph sizes.
            // Only the head and the first few hot-zone slots get bloom — the tail does not,
            // keeping performance budget low (< 10% of columns visible at any time are
            // head/hot-zone) and preserving visual hierarchy.

            val bloomRadius = fontSizePx * 1.6f
            if (bloomRadius != lastBloomRadius) {
                lastBloomRadius = bloomRadius
                bloomPaint.maskFilter = android.graphics.BlurMaskFilter(
                    bloomRadius, android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            bloomPaint.textSize = fontSizePx  // will be overridden per-column below

            // ── PASS 1: Bloom glow ────────────────────────────────────────────
            for (col in columns) {
                val depth   = col.depth
                // Far columns (low depth) get smaller glyphs and lower brightness
                val scaledSize = fontSizePx * (0.45f + depth * 0.55f)  // 0.45×–1.0× font size
                val depthAlpha = 0.25f + depth * 0.75f                  // 0.25–1.0

                bloomPaint.textSize = scaledSize

                val headY  = col.headRow * scaledSize
                val slots  = col.chars.size

                for (slot in 0..minOf(3, slots - 1)) {
                    val charY = headY - slot * scaledSize
                    if (charY < -scaledSize || charY > size.height + scaledSize) continue

                    // Bloom alpha: strong at head, fades quickly through hot zone
                    // Also modulated by depth so far columns have subtler glow
                    val bloomAlpha = when (slot) {
                        0    -> baseA * depthAlpha * 0.55f
                        1    -> baseA * depthAlpha * 0.35f
                        2    -> baseA * depthAlpha * 0.20f
                        else -> baseA * depthAlpha * 0.10f
                    }
                    val bAlphaInt = (bloomAlpha.coerceIn(0f, 1f) * 255f).toInt()
                    if (bAlphaInt < 4) continue

                    // Bloom uses head/rain color depending on slot
                    val bArgb = if (slot == 0) headArgb else rainArgb
                    bloomPaint.color = bArgb
                    bloomPaint.alpha = bAlphaInt
                    // drawText(CharArray, offset, count, x, y, paint) avoids allocating
                    // a new String object for every character drawn — the single biggest
                    // GC source in the matrix rain at high density / long trails.
                    nc.drawText(col.chars, slot % slots, 1, col.x, charY, bloomPaint)
                }
            }

            // ── PASS 2: Sharp characters ──────────────────────────────────────
            nativePaint.maskFilter = null  // ensure sharp (no blur)
            for (col in columns) {
                val depth   = col.depth
                val scaledSize = fontSizePx * (0.45f + depth * 0.55f)
                val depthAlpha = 0.25f + depth * 0.75f

                nativePaint.textSize = scaledSize

                val headY  = col.headRow * scaledSize
                val slots  = col.chars.size
                val slotsM = (slots - 1).coerceAtLeast(1)

                for (slot in 0 until slots) {
                    val charY = headY - slot * scaledSize
                    if (charY < -scaledSize || charY > size.height + scaledSize) continue

                    val t = slot.toFloat() / slotsM   // 0 = head, 1 = tail

                    val alpha: Int
                    val argb: Int

                    when {
                        slot == 0 -> {
                            alpha = (baseA * depthAlpha * 255f).toInt().coerceIn(0, 255)
                            argb  = headArgb
                        }
                        slot <= 3 -> {
                            alpha = (baseA * depthAlpha * 240f).toInt().coerceIn(0, 255)
                            argb  = rainArgb
                        }
                        else -> {
                            val fade = ((t - 0.12f) / 0.88f).coerceIn(0f, 1f)
                            // LUT lookup: one int cast instead of 3 float multiplies + 3 toInt() calls.
                            // Map fade [0,1] → LUT index [0, TRAIL_LUT_SIZE-1], read packed 0x00RRGGBB.
                            val lutIdx = (fade * (TRAIL_LUT_SIZE - 1) + 0.5f).toInt()
                                .coerceIn(0, TRAIL_LUT_SIZE - 1)
                            alpha    = (baseA * depthAlpha * (1f - fade * fade) * 210f).toInt().coerceIn(0, 255)
                            argb     = trailRgbLut[lutIdx] or 0xFF000000.toInt()  // OR in opaque sentinel; alpha set via paint.alpha below
                        }
                    }

                    if (alpha < 3) continue

                    nativePaint.color = argb
                    nativePaint.alpha = alpha
                    // CharArray overload: zero String allocation per character
                    nc.drawText(col.chars, slot % slots, 1, col.x, charY, nativePaint)
                }
            }
        }
    }
}

// CompositionLocals
