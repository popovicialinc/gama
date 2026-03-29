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
// Components: reusable UI building blocks
// ============================================================

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

@Composable
fun TitleSection(colors: ThemeColors, isSmallScreen: Boolean, isLandscape: Boolean, userName: String = "", currentHour: Int = 12, onEasterEgg: (() -> Unit)? = null) {
    val ts = LocalTypeScale.current

    // ── Greeting pools per time period ───────────────────────────────────────
    // Day greetings: warm, energetic, optimistic
    // Night greetings: introspective, calm, thoughtful
    val greetingPool: List<String> = when (currentHour) {
        in 0..5 -> if (userName.isNotEmpty()) listOf(
            "The night belongs to you, $userName. 🌙",
            "Some thoughts only come after midnight, $userName. 🌙",
            "Even the stars are listening, $userName. 🌙",
            "The world shrinks to just this moment, $userName. 🌙",
            "There's a certain clarity to these hours, $userName. 🌙"
        ) else listOf(
            "The night belongs to you. 🌙",
            "Some thoughts only come after midnight. 🌙",
            "Even the stars are listening. 🌙",
            "The world shrinks to just this moment. 🌙",
            "There's a certain clarity to these hours. 🌙"
        )
        in 6..11 -> if (userName.isNotEmpty()) listOf(
            "Good morning, $userName! ☀️ Let's make it count.",
            "Rise and shine, $userName! ☀️ Today has potential.",
            "A brand new day, $userName! ☀️ Anything's possible.",
            "Morning energy, $userName! ☀️ The best kind.",
            "The day is yours, $userName! ☀️ Go get it."
        ) else listOf(
            "Good morning! ☀️ Let's make it count.",
            "Rise and shine! ☀️ Today has potential.",
            "A brand new day! ☀️ Anything's possible.",
            "Morning energy! ☀️ The best kind.",
            "The day is yours! ☀️ Go get it."
        )
        in 12..16 -> if (userName.isNotEmpty()) listOf(
            "Hey, $userName! ☀️ Afternoon momentum is real.",
            "Good afternoon, $userName! ☀️ Keep the energy up.",
            "Still going strong, $userName! ☀️",
            "The day is half won, $userName! ☀️ Finish it.",
            "There you are, $userName! ☀️ Let's do something great."
        ) else listOf(
            "Hey! ☀️ Afternoon momentum is real.",
            "Good afternoon! ☀️ Keep the energy up.",
            "Still going strong! ☀️",
            "The day is half won! ☀️ Finish it.",
            "There you are! ☀️ Let's do something great."
        )
        in 17..22 -> if (userName.isNotEmpty()) listOf(
            "The evening settles in, $userName. 🌙",
            "A quieter pace now, $userName. 🌙",
            "The day fades — worth reflecting on, $userName. 🌙",
            "Winding down has its own kind of beauty, $userName. 🌙",
            "The night is gentle tonight, $userName. 🌙"
        ) else listOf(
            "The evening settles in. 🌙",
            "A quieter pace now. 🌙",
            "The day fades — worth reflecting on. 🌙",
            "Winding down has its own kind of beauty. 🌙",
            "The night is gentle tonight. 🌙"
        )
        else -> if (userName.isNotEmpty()) listOf(
            "The night belongs to you, $userName. 🌙",
            "Some thoughts only come after midnight, $userName. 🌙",
            "There's a certain clarity to these hours, $userName. 🌙",
            "Even the stars are listening, $userName. 🌙",
            "The world shrinks to just this moment, $userName. 🌙"
        ) else listOf(
            "The night belongs to you. 🌙",
            "Some thoughts only come after midnight. 🌙",
            "There's a certain clarity to these hours. 🌙",
            "Even the stars are listening. 🌙",
            "The world shrinks to just this moment. 🌙"
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

        // Cache the Paint and BlurMaskFilter outside drawWithContent.
        // Previously both were allocated fresh on every draw frame (~60x/sec on old
        // devices), generating significant GC pressure on JIT-based Dalvik runtimes.
        // maskFilter = 80f is constant — it never changes, so one allocation is enough.
        val titleGlowPaint = remember(colors.textPrimary, gamaTextSize) {
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                textSize = 1f  // overridden per-draw via the canvas transform
                textAlign = android.graphics.Paint.Align.CENTER
                maskFilter = android.graphics.BlurMaskFilter(80f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                setShadowLayer(80f, 0f, 0f, colors.textPrimary.toArgb())
                typeface = typeface  // inherit whatever was already set
            }
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
                        // Only draw shimmer band while the animation is in flight
                        if (shimmerProgress > 0f && shimmerProgress < 1f) {
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
                    }
                    .background(Brush.horizontalGradient(listOf(colors.primaryAccent.copy(alpha=0f), colors.primaryAccent.copy(alpha=1f)))))
                Text(text = "GAMA", fontSize = gamaTextSize, fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily, color = colors.textPrimary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).pointerInput(onEasterEgg) {
                        if (onEasterEgg != null) detectTapGestures(onLongPress = { onEasterEgg() })
                    }.drawWithContent {
                        drawIntoCanvas { canvas ->
                            // Reuse cached Paint — only update textSize which depends on
                            // the actual measured size available in the draw scope.
                            titleGlowPaint.textSize = gamaTextSize.toPx()
                            val cx = size.width / 2
                            val cy = size.height / 2 + gamaTextSize.toPx() * 0.25f
                            canvas.nativeCanvas.drawText("GAMA", cx, cy, titleGlowPaint)
                        }
                        drawContent()
                    })
                // Right bar — shimmer sweeps right → left (toward text)
                Box(modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .drawWithContent {
                        drawContent()
                        if (shimmerProgress > 0f && shimmerProgress < 1f) {
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
                    }
                    .background(Brush.horizontalGradient(listOf(colors.primaryAccent.copy(alpha=1f), colors.primaryAccent.copy(alpha=0f)))))
                // end right bar
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

// ── Auto-stagger ─────────────────────────────────────────────────────────────
// Instead of hardcoding staggerIndex at every call site, wrap a group of
// AnimatedElements in StaggerScope. Each AnimatedElement inside the scope
// automatically gets the next index and the total count — no manual numbering.
//
// Usage:
//   StaggerScope {
//       AnimatedElement(visible) { CardA() }   // index 0
//       AnimatedElement(visible) { CardB() }   // index 1
//       AnimatedElement(visible) { CardC() }   // index 2
//   }
//
// Outside a StaggerScope the old explicit staggerIndex param still works.

private val LocalStaggerCounter = compositionLocalOf<StaggerCounter?> { null }

class StaggerCounter {
    private var index = 0
    private var total = 0
    fun next(): Int = index++
    fun reset() { index = 0 }
    fun setTotal(n: Int) { total = n }
    fun total(): Int = total
}

@Composable
fun StaggerScope(
    totalItems: Int = 0,   // optional override; if 0, auto-counted
    content: @Composable () -> Unit
) {
    val counter = remember { StaggerCounter() }
    // Reset index at the start of every composition so indices don't
    // accumulate across recompositions.
    counter.reset()
    if (totalItems > 0) counter.setTotal(totalItems)
    CompositionLocalProvider(LocalStaggerCounter provides counter) {
        content()
    }
}

@Composable
fun AnimatedElement(
    visible: Boolean,
    staggerIndex: Int = -1,   // -1 = auto-assign from StaggerScope
    totalItems: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val staggerCounter = LocalStaggerCounter.current
    val resolvedIndex = if (staggerIndex >= 0) staggerIndex
                        else staggerCounter?.next() ?: 0
    val resolvedTotal = if (totalItems > 0) totalItems
                        else staggerCounter?.total() ?: 0
    val animationLevel = LocalAnimationLevel.current
    val staggerEnabled = LocalStaggerEnabled.current
    val density = LocalDensity.current

    // ── Performance-optimised stagger ────────────────────────────────────────
    //
    // Previous implementation: 3 separate animateFloatAsState per card
    // (alpha, scale, translationY) → 21 simultaneous animators for a 7-item
    // panel, all recomposing every frame, causing the frame-drop on open.
    //
    // New implementation: single Animatable<Float> in [0,1] drives all three
    // properties via lerp inside graphicsLayer — ONE animator per card, zero
    // recomposition (graphicsLayer reads are draw-phase only).
    //
    // Stagger gap reduced 95ms → 60ms so the full cascade finishes faster.
    // Duration reduced: 680/460ms → 420/300ms — still feels smooth but doesn't
    // hold the render thread in animation work for nearly a second.
    // keyframes replaced with a single tween + CubicBezier — lighter to evaluate.
    //
    // When staggerEnabled is false, all items animate simultaneously with no
    // cascade delay — faster perceived performance at the cost of the cascade effect.

    var localVisible by remember { mutableStateOf(if (resolvedIndex == 0) visible else !visible) }
    val progress = remember { Animatable(if (localVisible) 1f else 0f) }
    val scope = rememberCoroutineScope()

    val offsetYPx = remember(density, animationLevel) {
        with(density) { (if (animationLevel == 0) 20f else 14f).dp.toPx() }
    }

    LaunchedEffect(visible) {
        if (visible) {
            // Only delay for cascade if stagger is enabled AND we're not in reduced/off animation mode
            if (resolvedIndex > 0 && animationLevel != 2 && staggerEnabled) {
                delay(resolvedIndex * 60L)
            }
            localVisible = true
            if (animationLevel == 2) {
                progress.snapTo(1f)
            } else {
                val duration = if (animationLevel == 0) 420 else 300
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = CubicBezierEasing(0.33f, 1.0f, 0.68f, 1.0f)
                    )
                )
            }
        } else {
            // On dismiss, only stagger the exit cascade if stagger is enabled
            if (resolvedTotal > 0 && resolvedIndex > 0 && animationLevel != 2 && staggerEnabled) {
                delay((resolvedTotal - resolvedIndex) * 40L)
            }
            localVisible = false
            if (animationLevel == 2) {
                progress.snapTo(0f)
            } else {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = if (animationLevel == 0) 220 else 160,
                        easing = CubicBezierEasing(0.32f, 0.0f, 0.67f, 0.0f)
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            // All three properties derived from a single progress value —
            // no recomposition triggered, evaluated entirely in the draw phase.
            val p = progress.value
            alpha       = p.coerceIn(0f, 1f)
            scaleX      = 0.88f + p * 0.12f   // 0.88 → 1.0
            scaleY      = scaleX
            translationY = (1f - p) * offsetYPx
            clip        = false
        }
    ) {
        content()
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
    // --- press-state: single Animatable drives all press effects via graphicsLayer ---
    // Previously 5 separate animateFloatAsState / animateDpAsState all responding to
    // the same isPressed flag — that's 5 animators running simultaneously on every
    // card press/release. Consolidating to one Animatable<Float> in [0,1] lets us
    // derive every visual property with simple lerp in graphicsLayer (draw phase only,
    // zero recompositions).
    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed && enabled) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val p = pressProgress.value  // single read; all derived values computed below

    val density = LocalDensity.current
    // Derive all press visuals from p — evaluated in draw phase via graphicsLayer
    val pressScale       = 1f - p * (1f - MotionTokens.Scale.subtle)
    val chevronScaleVal  = 1f + p * 0.3f
    val chevronTXVal     = p * with(density) { 4.dp.toPx() }
    val chevronAlphaVal  = 0.5f + p * 0.5f
    val borderWidthVal   = (if (oledMode) 0.75f else 1f) + p * ((if (oledMode) 0.75f else 1f))  // 1dp → 2dp
    val cardBorderWidth  = borderWidthVal.dp

    val animatedCardBorderColor = when {
        isPressed && enabled -> colors.primaryAccent
        oledMode             -> colors.primaryAccent.copy(alpha = 0.3f)
        else                 -> colors.border
    }

    val chevronColor = if (isPressed && enabled)
        colors.primaryAccent
    else
        colors.textSecondary.copy(alpha = chevronAlphaVal)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp)
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
                .heightIn(min = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp)
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
                    .heightIn(min = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp)
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
                            scaleX = chevronScaleVal,
                            scaleY = chevronScaleVal,
                            translationX = chevronTXVal
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

    // Single Animatable<Float> [0=rest, 1=pressed] drives ALL press visuals via
    // graphicsLayer — replaces 5 separate animateFloatAsState/animateDpAsState calls
    // that previously ran simultaneously on every press/release.
    val pressProgress = remember { Animatable(0f) }
    val flatBtnScope  = rememberCoroutineScope()
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
            animationSpec = if (animLevel == 2) snap() else spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val pp = pressProgress.value  // single read; all visuals derived below
    val nudgePx = with(density) { 1.5.dp.toPx() }
    val restBorderW = if (oledMode) 0.75f else 1f
    // Derived press visuals — evaluated in draw phase via graphicsLayer, zero recompose
    val pressScale    = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val textScale     = 1f - pp * 0.07f
    val textTranslateY = pp * nudgePx
    val borderWidthDp  = (restBorderW + pp * restBorderW).dp   // 1dp → 2dp
    val borderAlpha    = 0.5f + pp * 0.5f

    val animatedButtonColor = if (!enabled) colors.textSecondary.copy(alpha = 0.05f)
    else if (oledMode) Color.Black
    else if (accent) colors.primaryAccent
    else colors.cardBackground

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    // On press: full accent border. At rest in OLED: thin accent outline matching OLED MODE card.
    val borderColor = when {
        isPressed && enabled -> colors.primaryAccent
        oledMode             -> colors.primaryAccent.copy(alpha = 0.3f)
        !accent              -> if (enabled) colors.border.copy(alpha = borderAlpha) else colors.border.copy(alpha = 0.5f)
        else                 -> if (!enabled) colors.border.copy(alpha = 0.5f) else Color.Transparent
    }

    val shouldShowBorder = true

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
                        width = borderWidthDp,
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


    // Single Animatable<Float> drives ALL press visuals — same pattern as FlatButton.
    // Replaces 5 separate animators (pressScale, textScale, textTranslateY,
    // borderWidth, borderAlpha) that all fired simultaneously on every press.
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
            animationSpec = if (animLevel == 2) snap() else spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val pp = pressProgress.value
    val nudgePx = with(density) { 1.5.dp.toPx() }
    val restBorderW = if (oledMode) 0.75f else 1f
    val pressScale      = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val textScale       = 1f - pp * 0.07f
    val textTranslateY  = pp * nudgePx
    val borderWidthDp   = (restBorderW + pp * restBorderW).dp
    val borderAlpha     = 0.5f + pp * 0.5f
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
        oledMode             -> colors.primaryAccent.copy(alpha = 0.3f)
        !accent              -> if (enabled) colors.border.copy(alpha = borderAlpha) else colors.border.copy(alpha = 0.5f)
        else                 -> if (!enabled) colors.border.copy(alpha = 0.5f) else Color.Transparent
    }

    val shouldShowBorder = true

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
                .then(if (shouldShowBorder) Modifier.border(borderWidthDp, borderColor, shape) else Modifier)
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
    showGpuWatchButton: Boolean = false,
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
                // Both buttons share identical scale/alpha — one animator each instead of four
                val settingsButtonScale by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.85f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_button_scale"
                )
                val settingsButtonAlpha by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.25f,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                    label = "settings_button_alpha"
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
                                    scaleX = settingsButtonScale,
                                    scaleY = settingsButtonScale,
                                    alpha = settingsButtonAlpha
                                )
                                .then(
                                    when {
                                        !shizukuReady -> Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                        currentRenderer == "Vulkan" -> Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(18.dp))
                                        else -> Modifier
                                    }
                                ),
                            accent = true,
                            enabled = true,
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
                                    scaleX = settingsButtonScale,
                                    scaleY = settingsButtonScale,
                                    alpha = settingsButtonAlpha
                                )
                                .then(
                                    when {
                                        !shizukuReady -> Modifier.border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                        currentRenderer == "OpenGL" -> Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(18.dp))
                                        else -> Modifier
                                    }
                                ),
                            accent = true,
                            enabled = true,
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
                            modifier = if (showGpuWatchButton) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            accent = false,
                            enabled = true,
                            colors = colors,
                            oledMode = oledMode,
                            iconType = "resources"
                        )
                        if (showGpuWatchButton) {
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


    // ── Pulse animations: only run when actually needed ───────────────────────
    // Each InfiniteTransition is created only in the branch where it's used.
    // On the happy path (shizukuReady = true) the error transitions don't exist —
    // zero ticks, zero slots, zero per-frame CPU on old chipsets.

    val warningBorderAlpha by if (!shizukuReady) {
        val t = rememberInfiniteTransition(label = "renderer_warning")
        t.animateFloat(
            initialValue = 0.30f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = MotionTokens.Easing.silk), RepeatMode.Reverse
            ),
            label = "warning_border_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0.30f) }
    }

    val glowAlpha by if (!shizukuReady) {
        val t = rememberInfiniteTransition(label = "renderer_glow")
        t.animateFloat(
            initialValue = 0.22f, targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = MotionTokens.Easing.silk), RepeatMode.Reverse
            ),
            label = "renderer_glow_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val rendererTextAlpha by if (shizukuReady && animLevel != 2) {
        val t = rememberInfiniteTransition(label = "renderer_text")
        t.animateFloat(
            initialValue = 0.75f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1800, easing = MotionTokens.Easing.silk), RepeatMode.Reverse),
            label = "renderer_text_alpha"
        )
    } else {
        // animLevel 2 (reduced motion) or Shizuku not ready: static value, no ticker
        remember { mutableFloatStateOf(if (shizukuReady) 0.875f else 0.75f) }
    }

    // ── Press state — single Animatable<Float> [0=rest, 1=pressed] drives all ──
    // press visuals via draw-phase lerp, replacing 3 separate animateFloatAsState
    // calls that all fired simultaneously with identical spring specs on every
    // press/release.  Same pattern used by IllustratedButton and FlatButton.
    var isPressed by remember { mutableStateOf(false) }
    val rendererCardPress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        rendererCardPress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = if (animLevel == 2) snap() else spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val rcp        = rendererCardPress.value  // single read; all visuals derived below
    val nudgePx    = with(density) { -2.dp.toPx() }
    // Derived press visuals — evaluated at draw-phase via graphicsLayer, zero recomposition
    val pressScale = 1f - rcp * (1f - MotionTokens.Scale.subtle)
    val nameScale  = 1f + rcp * 0.08f
    val nameTY     = rcp * nudgePx

    // ── Colors ───────────────────────────────────────────────────────────────
    val borderColor = if (isPressed) {
        colors.primaryAccent
    } else if (!shizukuReady) {
        // Breathing outline: full range 30% → 100% opacity — very noticeable
        stateColor.copy(alpha = warningBorderAlpha)
    } else if (oledMode) {
        colors.primaryAccent.copy(alpha = 0.35f)
    } else {
        // Shizuku is running — use accent color at a visible but subtle alpha
        colors.primaryAccent.copy(alpha = 0.45f)
    }

    val borderWidth by animateDpAsState(
        targetValue = when {
            isPressed       -> 2.dp
            !shizukuReady   -> 2.dp    // thick pulsing outline for error/warning
            oledMode        -> 0.75.dp
            shizukuReady    -> 1.25.dp
            else            -> 1.dp
        },
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
    } else if (shizukuReady) {
        // Subtle accent tint when Shizuku is running — same card background style, just
        // nudged toward the accent color so it shares the "accent outline" aesthetic.
        if (isDarkTheme)
            cardBackground.copy(
                red   = (cardBackground.red   + colors.primaryAccent.red   * 0.07f).coerceAtMost(1f),
                green = (cardBackground.green + colors.primaryAccent.green * 0.07f).coerceAtMost(1f),
                blue  = (cardBackground.blue  + colors.primaryAccent.blue  * 0.07f).coerceAtMost(1f),
                alpha = 1f
            )
        else
            cardBackground.copy(
                red   = (cardBackground.red   + colors.primaryAccent.red   * 0.05f).coerceAtMost(1f),
                green = (cardBackground.green + colors.primaryAccent.green * 0.05f).coerceAtMost(1f),
                blue  = (cardBackground.blue  + colors.primaryAccent.blue  * 0.05f).coerceAtMost(1f),
                alpha = 1f
            )
    } else {
        cardBackground
    }

    // ── Layout: glow blob behind + card on top ───────────────────────────────
    // The glow is larger than the card so it bleeds softly around all edges.
    // When Shizuku is not ready: colored shadow (red = not running, orange = no permission)
    // When Shizuku is ready: no shadow rendered at all (saves a blur pass)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Colored shadow blob — only rendered when Shizuku is NOT ready.
        // API 31+: blurred glow. API < 31: unblurred radial gradient at reduced
        // alpha — same colour signal, zero GPU blur cost on old chipsets.
        if (!shizukuReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSmallScreen) 130.dp else 150.dp)
                        .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    stateColor.copy(alpha = glowAlpha),
                                    stateColor.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isSmallScreen) 130.dp else 150.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    stateColor.copy(alpha = glowAlpha * 0.5f),
                                    stateColor.copy(alpha = glowAlpha * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

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
fun CompactColorPickerCard(
    title: String,
    description: String,
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    advancedPicker: Boolean = false,
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

    // Hex input state — initialised from the current color
    fun Color.toHexString(): String {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    var hexInput by remember(currentColor) { mutableStateOf(currentColor.toHexString()) }
    var hexError by remember { mutableStateOf(false) }

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
                // Content — always uses single-column (portrait) layout inside the card,
                // regardless of screen orientation. The CARD itself may be in a multi-column
                // grid (controlled by the caller), but the swatches stay centred and readable.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(if (isSmallScreen) 20.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title + description — consistent font size matching ToggleCard / DYNAMIC COLORS
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
                            letterSpacing = 2.sp,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = description,
                            color = colors.textSecondary,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Swatch rows
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
                                    isSelected = !advancedPicker && color == currentColor,
                                    onClick = {
                                        onColorChange(color)
                                        hexInput = color.toHexString()
                                        hexError = false
                                    },
                                    colors = colors,
                                    isLandscape = false
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetColors.drop(6).forEach { color ->
                                CompactColorBox(
                                    color = color,
                                    isSelected = !advancedPicker && color == currentColor,
                                    onClick = {
                                        onColorChange(color)
                                        hexInput = color.toHexString()
                                        hexError = false
                                    },
                                    colors = colors,
                                    isLandscape = false
                                )
                            }
                        }
                    }

                    // Hex input — animated in/out when Advanced Color Picker is toggled
                    AnimatedVisibility(
                        visible = advancedPicker,
                        enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                               shrinkVertically(animationSpec = tween(250, easing = FastOutSlowInEasing))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live color preview swatch
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = try {
                                            val hex = hexInput.trimStart('#')
                                            if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else currentColor
                                        } catch (e: Exception) { currentColor },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (hexError) Color(0xFFEF4444) else colors.primaryAccent.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { input ->
                                    hexInput = input
                                    val clean = input.trimStart('#')
                                    if (clean.length == 6) {
                                        try {
                                            val parsed = Color(android.graphics.Color.parseColor("#$clean"))
                                            onColorChange(parsed)
                                            hexError = false
                                        } catch (e: Exception) {
                                            hexError = true
                                        }
                                    } else {
                                        hexError = clean.isNotEmpty() && clean.length != 6
                                    }
                                },
                                label = {
                                    Text(
                                        "Hex color",
                                        fontFamily = quicksandFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = ts.labelSmall
                                    )
                                },
                                singleLine = true,
                                isError = hexError,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedBorderColor = colors.primaryAccent,
                                    unfocusedBorderColor = if (oledMode) colors.primaryAccent.copy(alpha = 0.5f) else colors.border,
                                    errorBorderColor = Color(0xFFEF4444),
                                    cursorColor = colors.primaryAccent,
                                    focusedLabelColor = colors.primaryAccent,
                                    unfocusedLabelColor = colors.textSecondary,
                                    errorLabelColor = Color(0xFFEF4444)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (hexError) {
                            Text(
                                text = "Enter a valid 6-digit hex (e.g. #4895EF)",
                                fontSize = ts.labelSmall,
                                color = Color(0xFFEF4444),
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        } // end Column inside AnimatedVisibility
                    } // end AnimatedVisibility
                }
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

    // Cache the Paint outside drawWithContent — previously allocated fresh on every
    // draw frame. setShadowLayer radius (80f) and text alignment are constant.
    val titleGlowPaint = remember(colors.primaryAccent, fontSize) {
        Paint().asFrameworkPaint().apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(80f, 0f, 0f, colors.primaryAccent.toArgb())
            textAlign = android.graphics.Paint.Align.CENTER
            isDither = true
        }
    }

    // FIXED GRADIENT BARS — always use horizontal gradient regardless of orientation.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = -scrollOffset * 0.4f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left Gradient Bar — fades transparent→accent, shimmer sweeps left→right
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .drawWithContent {
                    drawContent()
                    // Only allocate shimmer Brush while animation is in flight
                    if (shimmerProgress > 0f && shimmerProgress < 1f) {
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
                }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (reverseGradient)
                            listOf(colors.primaryAccent.copy(alpha = 1f), colors.primaryAccent.copy(alpha = 0f))
                        else
                            listOf(colors.primaryAccent.copy(alpha = 0f), colors.primaryAccent.copy(alpha = 1f))
                    )
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
                        // Reuse cached Paint; only textSize depends on draw-scope density
                        titleGlowPaint.textSize = fontSize.toPx()
                        canvas.nativeCanvas.drawText(
                            text,
                            size.width / 2,
                            size.height / 2 + (fontSize.toPx() * 0.25f),
                            titleGlowPaint
                        )
                    }
                    drawContent()
                }
        )

        // Right Gradient Bar — fades accent→transparent, shimmer sweeps right→left
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .drawWithContent {
                    drawContent()
                    // Only allocate shimmer Brush while animation is in flight
                    if (shimmerProgress > 0f && shimmerProgress < 1f) {
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
                }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = if (reverseGradient)
                            listOf(colors.primaryAccent.copy(alpha = 0f), colors.primaryAccent.copy(alpha = 1f))
                        else
                            listOf(colors.primaryAccent.copy(alpha = 1f), colors.primaryAccent.copy(alpha = 0f))
                    )
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
        !enabled            -> 0.3f                  // disabled: same as SettingsNavigationCard
        checked && oledMode -> 0.55f                 // on + oled: prominent
        checked             -> 0.55f                 // on: prominent accent ring
        oledMode            -> 0.3f                  // off + oled: subtle
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
            checked  -> 1.0f
            else     -> 0.7f
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "toggle_title_alpha"
    )
    val titleColor = colors.primaryAccent.copy(alpha = titleAlpha)

    // Uniform card height — cards with a Switch tend to be taller than plain button-only
    // cards (like SettingsNavigationCard) because the Switch widget adds extra height.
    // Wrapping everything in a Box with heightIn(min = …) ensures ToggleCards and
    // NavigationCards share the same minimum height so rows of mixed card types align.
    val cardMinHeight = if (isSmallScreen) 72.dp else 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = cardMinHeight)
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
                .heightIn(min = cardMinHeight)
                .graphicsLayer(alpha = alpha),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = cardMinHeight)
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
                            color = colors.textSecondary,
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

    // Static glow alpha — no infinite transition needed; the blur radius
    // already softens the shape and the button is only visible in dialogs.
    val glowAlpha = 0.22f

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
        // Blurred glow behind button — API 31+ only.
        // On older devices a plain radial gradient gives the same accent-colour
        // hint without touching the GPU blur pipeline.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        } else {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

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

// Panel back button — visually identical to the Settings icon buttons on the
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

    // Static glow alpha — back button is shown inside already-open panels;
    // running an infinite transition here would tick every frame for the
    // entire duration the panel is visible.  Static value is indistinguishable.
    val glowAlpha = 0.26f

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
        // Glow blob — API 31+ only; plain radial gradient fallback on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        } else {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primaryAccent.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
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
    oledMode: Boolean = false,
    borderAlphaOverride: Float? = null // when set, rest-state border alpha matches the surrounding card outline
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
        targetValue = if (isPressed) 2.dp else if (oledMode) 0.75.dp else 1.dp,
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
    // OLED: thin accent outline at rest matching OLED MODE card; full accent on press
    val restBorderAlpha = borderAlphaOverride ?: if (isPressed) 1f else borderAlpha
    val borderColor = when {
        isPressed            -> colors.primaryAccent
        oledMode             -> colors.primaryAccent.copy(alpha = borderAlphaOverride ?: 0.3f)
        accent               -> colors.primaryAccent.copy(alpha = restBorderAlpha)
        else                 -> colors.border.copy(alpha = if (isPressed) 1f else borderAlpha)
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

/**
 * Returns every installed package with its human-readable label.
 *
 * Uses ShizukuHelper.getAllPackageNames() (shell `pm list packages -a`) to
 * get the COMPLETE package list, bypassing Android 11+ PackageManager
 * visibility filtering which silently drops most third-party apps.
 *
 * Label resolution still uses PackageManager.  For packages PM can't see
 * (which is fine — exclusions work by package name) the package name is
 * used as the display label so the entry is still shown and selectable.
 *
 * Falls back to the PM-only approach if Shizuku is unavailable.
 */
suspend fun getAllInstalledPackages(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager

    // Try Shizuku first — gets every package regardless of visibility rules
    val shellPackages = ShizukuHelper.getAllPackageNames()

    if (shellPackages.isNotEmpty()) {
        return shellPackages.map { pkg ->
            val label = try {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (_: Exception) {
                // PM can't see this package due to visibility filtering.
                // Use the package name — it's still shown and can be excluded.
                pkg
            }
            pkg to label
        }.sortedBy { it.second.lowercase() }
    }

    // Shizuku unavailable — fall back to PackageManager (partial list on API 30+)
    return pm.getInstalledApplications(PackageManager.GET_META_DATA).map {
        it.packageName to it.loadLabel(pm).toString()
    }.sortedBy { it.second.lowercase() }
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
        targetValue = if (isPressed) 2.dp else if (oledMode) 0.75.dp else 1.dp,
        animationSpec = tween(durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick),
        label = "sec_btn_border_width"
    )
    // OLED: thin accent outline at rest; full accent on press
    val borderColor = if (isPressed) colors.primaryAccent
    else if (oledMode) colors.primaryAccent.copy(alpha = 0.3f)
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


