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
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.Rect as AndroidRect
import android.view.HapticFeedbackConstants
import android.view.PixelCopy
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
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

// Dynamic right-side cutout used by cards when the floating panel back button
// overlaps them. Default is disabled so the rest of the app is unaffected.
@Immutable
data class FloatingBackButtonAvoidance(
    val enabled: Boolean = false,
    val endPadding: Dp = 0.dp,
    val bottomPadding: Dp = 0.dp,
    val buttonSize: Dp = 0.dp
)

val LocalFloatingBackButtonAvoidance = compositionLocalOf { FloatingBackButtonAvoidance() }


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
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current
    var dragIndicatorCenterPx by remember { mutableStateOf<Float?>(null) }

    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
            stiffness = MotionTokens.Springs.gentle.stiffness
        ),
        label = "glide_selector_scale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.42f,
        animationSpec = tween(durationMillis = 340, easing = MotionTokens.Easing.velvet),
        label = "glide_selector_alpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(28.dp))
            .background(colors.primaryAccent.copy(alpha = if (enabled) 0.07f else 0.06f))
    ) {
        val maxWidth = maxWidth
        val itemWidth = maxWidth / options.size

        val itemWidthPxForIndicator = with(density) { itemWidth.toPx() }
        val selectedOffsetPx = itemWidthPxForIndicator * selectedIndex
        val liveOffsetPx = dragIndicatorCenterPx
            ?.let { (it - itemWidthPxForIndicator / 2f).coerceIn(0f, itemWidthPxForIndicator * (options.size - 1)) }
            ?: selectedOffsetPx

        // Smart live indicator: while dragging it follows the finger continuously,
        // then eases to the nearest stop. This avoids jitter when the user skips
        // several stops quickly.
        val animatedIndicatorPx by animateFloatAsState(
            targetValue = liveOffsetPx,
            animationSpec = if (animLevel == 2) {
                snap()
            } else if (dragIndicatorCenterPx != null) {
                tween(durationMillis = 42, easing = CubicBezierEasing(0.22f, 0.0f, 0.2f, 1.0f))
            } else {
                tween(durationMillis = if (animLevel == 0) 170 else 120, easing = CubicBezierEasing(0.18f, 0.85f, 0.32f, 1.0f))
            },
            label = "indicator"
        )
        val indicatorOffset = with(density) { animatedIndicatorPx.toDp() }

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.primaryAccent.copy(alpha = contentAlpha))
        )

        Row(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = contentAlpha)) {
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

        // ── Unified gesture overlay ───────────────────────────────────────────
        // Previously: two separate pointerInput blocks (one for tap, one for drag)
        // caused gesture conflicts — detectDragGestures consumed onDragStart even
        // for finger movements that were really taps, so taps often silently fired
        // onDragStart and then the drag never moved far enough to trigger onDrag,
        // leaving the selection unchanged.
        //
        // Fix: one awaitPointerEventScope loop that decides tap-vs-drag from the
        // first movement after touch-down, so neither detector can steal from the other.
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, options.size) {
                        val itemWidthPx = size.width.toFloat() / options.size
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragIndicatorCenterPx = down.position.x
                            var lastIndex = (down.position.x / itemWidthPx)
                                .toInt().coerceIn(0, options.size - 1)
                            var isDrag = false

                            do {
                                val event = awaitPointerEvent()
                                val ptr = event.changes.firstOrNull() ?: break
                                if (!ptr.pressed) break // finger lifted

                                dragIndicatorCenterPx = ptr.position.x
                                val newIndex = (ptr.position.x / itemWidthPx)
                                    .toInt().coerceIn(0, options.size - 1)

                                if (!isDrag) {
                                    // Treat as drag once the finger moves more than a few px
                                    val dx = kotlin.math.abs(ptr.position.x - down.position.x)
                                    if (dx > viewConfiguration.touchSlop) isDrag = true
                                }

                                if (isDrag && newIndex != lastIndex) {
                                    currentOnOptionSelected(newIndex)
                                    lastIndex = newIndex
                                    ptr.consume()
                                }
                            } while (true)

                            // Finger up — if it never became a drag, treat as a tap
                            if (!isDrag) {
                                val tapIndex = (down.position.x / itemWidthPx)
                                    .toInt().coerceIn(0, options.size - 1)
                                if (tapIndex != currentSelectedIndex) {
                                    currentOnOptionSelected(tapIndex)
                                }
                            }
                            dragIndicatorCenterPx = null
                        }
                    }
            )
        } else {
            // Disabled: swallow taps so they don't leak through to parent
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { }
            })
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

@Composable
fun TitleSection(colors: ThemeColors, isSmallScreen: Boolean, isLandscape: Boolean, userName: String = "", currentHour: Int = 12, onEasterEgg: (() -> Unit)? = null) {
    val ts = LocalTypeScale.current

    val strings = LocalStrings.current
    val languageCode = LocalLanguageCode.current.value

    // ── Greeting pools per time period ───────────────────────────────────────
    // Day greetings: warm, energetic, optimistic
    // Night greetings: introspective, calm, thoughtful
    val greetingPool: List<String> = when (currentHour) {
        in 0..5 -> if (userName.isNotEmpty()) listOf(
            strings["greetings.late_night_named_1"].replace("%s", userName).ifEmpty { "The night is yours, $userName. 🌙" },
            strings["greetings.late_night_named_2"].replace("%s", userName).ifEmpty { "Some thoughts only come after midnight, $userName. 🌙" },
            strings["greetings.late_night_named_3"].replace("%s", userName).ifEmpty { "Even the stars are listening, $userName. 🌙" },
            strings["greetings.late_night_named_4"].replace("%s", userName).ifEmpty { "The world narrows to this moment, $userName. 🌙" },
            strings["greetings.late_night_named_5"].replace("%s", userName).ifEmpty { "There's a particular clarity to these hours, $userName. 🌙" }
        ) else listOf(
            strings["greetings.late_night_1"].ifEmpty { "The night is yours. 🌙" },
            strings["greetings.late_night_2"].ifEmpty { "Some thoughts only come after midnight. 🌙" },
            strings["greetings.late_night_3"].ifEmpty { "Even the stars are listening. 🌙" },
            strings["greetings.late_night_4"].ifEmpty { "The world narrows to this moment. 🌙" },
            strings["greetings.late_night_5"].ifEmpty { "There's a particular clarity to these hours. 🌙" }
        )
        in 6..11 -> if (userName.isNotEmpty()) listOf(
            strings["greetings.morning_named_1"].replace("%s", userName).ifEmpty { "Good morning, $userName! ☀️ Make it a productive one." },
            strings["greetings.morning_named_2"].replace("%s", userName).ifEmpty { "Up and at it, $userName! ☀️ The day has potential." },
            strings["greetings.morning_named_3"].replace("%s", userName).ifEmpty { "A brand new day, $userName! ☀️ Anything is possible." },
            strings["greetings.morning_named_4"].replace("%s", userName).ifEmpty { "Morning energy, $userName! ☀️ The best kind." },
            strings["greetings.morning_named_5"].replace("%s", userName).ifEmpty { "The day is yours, $userName! ☀️ Go and take it." }
        ) else listOf(
            strings["greetings.morning_1"].ifEmpty { "Good morning! ☀️ Make it a productive one." },
            strings["greetings.morning_2"].ifEmpty { "Up and at it! ☀️ The day has potential." },
            strings["greetings.morning_3"].ifEmpty { "A brand new day! ☀️ Anything is possible." },
            strings["greetings.morning_4"].ifEmpty { "Morning energy! ☀️ The best kind." },
            strings["greetings.morning_5"].ifEmpty { "The day is yours! ☀️ Go and take it." }
        )
        in 12..16 -> if (userName.isNotEmpty()) listOf(
            strings["greetings.afternoon_named_1"].replace("%s", userName).ifEmpty { "Hey, $userName! ☀️ That afternoon rhythm is real." },
            strings["greetings.afternoon_named_2"].replace("%s", userName).ifEmpty { "Good afternoon, $userName! ☀️ Keep the energy up." },
            strings["greetings.afternoon_named_3"].replace("%s", userName).ifEmpty { "Keep it going, $userName! ☀️" },
            strings["greetings.afternoon_named_4"].replace("%s", userName).ifEmpty { "Halfway through, $userName! ☀️ Finish what you started." },
            strings["greetings.afternoon_named_5"].replace("%s", userName).ifEmpty { "There you are, $userName! ☀️ Let's do something great." }
        ) else listOf(
            strings["greetings.afternoon_1"].ifEmpty { "Hey! ☀️ That afternoon rhythm is real." },
            strings["greetings.afternoon_2"].ifEmpty { "Good afternoon! ☀️ Keep the energy up." },
            strings["greetings.afternoon_3"].ifEmpty { "Keep it going! ☀️" },
            strings["greetings.afternoon_4"].ifEmpty { "Halfway through! ☀️ Finish what you started." },
            strings["greetings.afternoon_5"].ifEmpty { "There you are! ☀️ Let's do something great." }
        )
        in 17..22 -> if (userName.isNotEmpty()) listOf(
            strings["greetings.evening_named_1"].replace("%s", userName).ifEmpty { "Evening is settling in, $userName. 🌙" },
            strings["greetings.evening_named_2"].replace("%s", userName).ifEmpty { "A quieter pace now, $userName. 🌙" },
            strings["greetings.evening_named_3"].replace("%s", userName).ifEmpty { "The day is winding down — worth reflecting on, $userName. 🌙" },
            strings["greetings.evening_named_4"].replace("%s", userName).ifEmpty { "Unwinding has its own kind of beauty, $userName. 🌙" },
            strings["greetings.evening_named_5"].replace("%s", userName).ifEmpty { "The night is gentle tonight, $userName. 🌙" }
        ) else listOf(
            strings["greetings.evening_1"].ifEmpty { "Evening is settling in. 🌙" },
            strings["greetings.evening_2"].ifEmpty { "A quieter pace now. 🌙" },
            strings["greetings.evening_3"].ifEmpty { "The day is winding down — worth reflecting on. 🌙" },
            strings["greetings.evening_4"].ifEmpty { "Unwinding has its own kind of beauty. 🌙" },
            strings["greetings.evening_5"].ifEmpty { "The night is gentle tonight. 🌙" }
        )
        else -> if (userName.isNotEmpty()) listOf(
            strings["greetings.late_night_named_1"].replace("%s", userName).ifEmpty { "The night is yours, $userName. 🌙" },
            strings["greetings.late_night_named_2"].replace("%s", userName).ifEmpty { "Some thoughts only come after midnight, $userName. 🌙" },
            strings["greetings.late_night_named_5"].replace("%s", userName).ifEmpty { "There's a particular clarity to these hours, $userName. 🌙" },
            strings["greetings.late_night_named_3"].replace("%s", userName).ifEmpty { "Even the stars are listening, $userName. 🌙" },
            strings["greetings.late_night_named_4"].replace("%s", userName).ifEmpty { "The world narrows to this moment, $userName. 🌙" }
        ) else listOf(
            strings["greetings.late_night_1"].ifEmpty { "The night is yours. 🌙" },
            strings["greetings.late_night_2"].ifEmpty { "Some thoughts only come after midnight. 🌙" },
            strings["greetings.late_night_5"].ifEmpty { "There's a particular clarity to these hours. 🌙" },
            strings["greetings.late_night_3"].ifEmpty { "Even the stars are listening. 🌙" },
            strings["greetings.late_night_4"].ifEmpty { "The world narrows to this moment. 🌙" }
        )
    }
    val displayText = remember(currentHour, userName, strings) { greetingPool.random() }

    val configuration = LocalConfiguration.current
    val isLandscapeTitle = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDpTitle = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val titleHPad = (screenMinDpTitle * 0.06f).dp.coerceIn(16.dp, 32.dp)
    val titleVPad = when {
        isSmallScreen    -> 8.dp
        isLandscapeTitle -> (screenMinDpTitle * 0.020f).dp.coerceIn(6.dp, 12.dp)
        else             -> (screenMinDpTitle * 0.038f).dp.coerceIn(12.dp, 22.dp)
    }
    val titleItemSpacing = when {
        isSmallScreen    -> 8.dp
        isLandscapeTitle -> (screenMinDpTitle * 0.022f).dp.coerceIn(7.dp, 12.dp)
        else             -> (screenMinDpTitle * 0.032f).dp.coerceIn(10.dp, 18.dp)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(titleItemSpacing),
        modifier = Modifier
            .padding(horizontal = titleHPad, vertical = titleVPad)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.emphasizedDecelerate))
    ) {
        // Use AnimatedContent for smooth text substitution if the greeting changes,
        // though typically it just appends the name. animateContentSize on parent handles the width change.
        Text(
            text = displayText,
            fontSize = ts.bodyLarge,
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Start,
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

        // ── Fake-glow + chromatic-aberration paints ───────────────────────────
        // Previously used BlurMaskFilter(80f) — a software CPU gaussian that runs
        // on every draw invalidation.  Replaced with 3 alpha layers + CA offsets:
        //   • ZERO BlurMaskFilter / ShadowLayer calls — purely GPU compositing.
        //   • 5 extra drawText() calls per frame vs 1 blurred call — net faster.
        //   • CA shifts R left, B right — gives the title its cyberpunk split-prism look.
        val titleGlowBase = remember(colors.textPrimary, quicksandBoldTypeface) {
            android.graphics.Paint().apply {
                isAntiAlias  = true
                textAlign    = android.graphics.Paint.Align.CENTER
                typeface     = quicksandBoldTypeface
            }
        }
        // Pre-compute accent ARGB so no Color.toArgb() allocations inside draw
        val glowArgb  = remember(colors.textPrimary)  { colors.textPrimary.copy(alpha = 1f).toArgb() }
        val caRedArgb = remember { android.graphics.Color.argb(200, 255, 60,  60) }
        val caBluArgb = remember { android.graphics.Color.argb(200, 60,  100, 255) }

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
                    fontFamily = quicksandFontFamily, color = colors.textPrimary, textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 24.dp).pointerInput(onEasterEgg) {
                        if (onEasterEgg != null) detectTapGestures(onLongPress = { onEasterEgg() })
                    }.drawWithContent {
                        val baseSize = gamaTextSize.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f + baseSize * 0.25f
                        // Chromatic aberration offset — ~2% of glyph height
                        val caOff = baseSize * 0.040f
                        drawIntoCanvas { canvas ->
                            val nc = canvas.nativeCanvas
                            // ── Glow halos (3 layers, no blur — pure alpha compositing) ───
                            titleGlowBase.color = glowArgb
                            // Layer 3 — outermost halo (biggest, most transparent)
                            titleGlowBase.alpha = 18
                            titleGlowBase.textSize = baseSize * 1.13f
                            nc.drawText("GAMA", cx, cy, titleGlowBase)
                            // Layer 2
                            titleGlowBase.alpha = 35
                            titleGlowBase.textSize = baseSize * 1.06f
                            nc.drawText("GAMA", cx, cy, titleGlowBase)
                            // Layer 1 — tightest bloom
                            titleGlowBase.alpha = 60
                            titleGlowBase.textSize = baseSize * 1.02f
                            nc.drawText("GAMA", cx, cy, titleGlowBase)
                            // ── Chromatic aberration — R left, B right ────────────────
                            titleGlowBase.textSize = baseSize
                            // Red fringe
                            titleGlowBase.color = caRedArgb
                            titleGlowBase.alpha = android.graphics.Color.alpha(caRedArgb)
                            nc.drawText("GAMA", cx - caOff, cy, titleGlowBase)
                            // Blue fringe
                            titleGlowBase.color = caBluArgb
                            titleGlowBase.alpha = android.graphics.Color.alpha(caBluArgb)
                            nc.drawText("GAMA", cx + caOff, cy, titleGlowBase)
                        }
                        // Crisp white Compose text on top
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
            text = LocalStrings.current["main.standing_by"].ifEmpty { "Standing by and awaiting your command" },
            fontSize = ts.bodyLarge, // greeting name
            fontFamily = quicksandFontFamily,
            color = colors.textSecondary,
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.Bold
        )

        // CHOOSE YOUR PATH. text in accent color - slightly bigger
        Text(
            text = LocalStrings.current["main.whats_next"].ifEmpty { "CHOOSE YOUR PATH." },
            fontSize = ts.headlineSmall, // slightly bigger than bodyLarge
            fontFamily = quicksandFontFamily,
            color = colors.primaryAccent.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold, // Force Bold
            letterSpacing = 2.sp,
            textAlign = TextAlign.Start
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
    cardShadow: Boolean = false,  // true = draw directional shadow (cards only, not buttons/text)
    enabled: Boolean = true,      // false = suppress shadow with ease-in-out animation
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
    val view = LocalView.current
    val context = LocalContext.current
    val backButtonAvoidance = LocalFloatingBackButtonAvoidance.current
    val backButtonInversed = LocalBackButtonInversed.current
    var overlapsFloatingBackButton by remember { mutableStateOf(false) }
    var lastAvoidanceHapticAtMs by remember { mutableStateOf(0L) }
    val avoidEndPadding by animateDpAsState(
        targetValue = if (cardShadow && backButtonAvoidance.enabled && overlapsFloatingBackButton)
            backButtonAvoidance.endPadding
        else 0.dp,
        animationSpec = when (animationLevel) {
            2 -> snap()
            1 -> tween(durationMillis = 180, easing = MotionTokens.Easing.emphasized)
            else -> spring(dampingRatio = 0.50f, stiffness = 300f)
        },
        label = "floating_back_button_avoidance"
    )

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
        with(density) { when (animationLevel) { 0 -> 20f; 1 -> 8f; else -> 0f }.dp.toPx() }
    }

    // settled = false while this card is mid-stagger, true once it lands.
    // Cards inside read LocalCardSettled to drive their shadow fade-in.
    var settled by remember { mutableStateOf(progress.value >= 0.999f) }

    LaunchedEffect(visible) {
        settled = false
        if (visible) {
            if (resolvedIndex > 0 && animationLevel != 2 && staggerEnabled) {
                delay(resolvedIndex * 60L)
            }
            localVisible = true
            if (animationLevel == 2) {
                progress.snapTo(1f)
            } else {
                // Full = springy. Reduced = no bounce, simple ease-in-out.
                val enterSpec: AnimationSpec<Float> = if (animationLevel == 0)
                    spring(dampingRatio = 0.55f, stiffness = 260f)
                else
                    tween(durationMillis = 210, easing = MotionTokens.Easing.emphasizedDecelerate)
                progress.animateTo(targetValue = 1f, animationSpec = enterSpec)
            }
        } else {
            if (resolvedTotal > 0 && resolvedIndex > 0 && animationLevel != 2 && staggerEnabled) {
                delay((resolvedTotal - resolvedIndex) * 40L)
            }
            localVisible = false
            if (animationLevel == 2) {
                progress.snapTo(0f)
            } else {
                // Exit: swift, decisive. Items don't linger — they depart with purpose.
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = if (animationLevel == 0) 200 else 150,
                        easing = MotionTokens.Easing.exit
                    )
                )
            }
        }
        settled = true
    }

    CompositionLocalProvider(
        LocalCardSettled  provides settled,
        LocalCardProgress provides progress.value,
        LocalCardEnabled  provides enabled
    ) {
        val avoidanceModifier = if (cardShadow && backButtonAvoidance.enabled) {
            Modifier.onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                val itemTop = position.y
                val itemBottom = itemTop + coordinates.size.height
                val screenHeight = view.height.toFloat().takeIf { it > 0f } ?: return@onGloballyPositioned

                val avoidZoneTop = screenHeight - with(density) {
                    (backButtonAvoidance.bottomPadding + backButtonAvoidance.buttonSize + 16.dp).toPx()
                }

                val overlaps = itemBottom > avoidZoneTop && itemTop < screenHeight
                if (overlapsFloatingBackButton != overlaps) {
                    overlapsFloatingBackButton = overlaps
                    val now = SystemClock.uptimeMillis()
                    if (now - lastAvoidanceHapticAtMs > 140L) {
                        lastAvoidanceHapticAtMs = now
                        if (overlaps) {
                            GamaHaptics.avoidanceDodge(context, view)
                        } else {
                            GamaHaptics.avoidanceReturn(context, view)
                        }
                    }
                }
            }
        } else Modifier

        val safeAvoidEndPadding = avoidEndPadding.coerceAtLeast(0.dp)
        val cardModifier = modifier
            .then(avoidanceModifier)
            .padding(
                start = if (backButtonInversed) safeAvoidEndPadding else 0.dp,
                end = if (backButtonInversed) 0.dp else safeAvoidEndPadding
            )

        Box(
            modifier = (if (cardShadow) cardModifier.directionalShadow() else cardModifier)
                .graphicsLayer {
                    val p = progress.value
                    alpha        = p.coerceIn(0f, 1f)
                    scaleX       = 0.94f + p * 0.06f
                    scaleY       = scaleX
                    translationY = (1f - p) * offsetYPx
                    clip         = false
                }
        ) {
            content()
        }
    }
}



/**
 * Draws a directional drop-shadow that mimics light shining from the top-left.
 * The shadow is offset toward the bottom-right and blurred with a BlurMaskFilter
 * so it looks soft and natural. This replaces graphicsLayer(shadowElevation=…)
 * which only produces a centred, non-directional shadow on Android.
 *
 * @param elevation Controls how large/dark the shadow is (0 = none, matches shadowsEnabled)
 * @param color     Shadow colour; default is black at 28% opacity
 * @param dx        Horizontal shadow offset in dp (positive = right)
 * @param dy        Vertical shadow offset in dp (positive = down)
 * @param blurRadius Blur spread in dp
 * @param cornerRadius Corner radius of the shadow shape, should match the card shape
 */
fun Modifier.directionalShadow(
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f),
    dx: Dp = 4.dp,
    dy: Dp = 6.dp,
    blurRadius: Dp = 12.dp,
    cornerRadius: Dp = 18.dp
): Modifier = composed {
    val shadowsEnabled = LocalShadowsEnabled.current
    val cardProgress   = LocalCardProgress.current
    val cardEnabled    = LocalCardEnabled.current
    val density        = LocalDensity.current
    val dxPx           = with(density) { dx.toPx() }
    val dyPx           = with(density) { dy.toPx() }
    val blurPx         = with(density) { blurRadius.toPx() }
    val cornerPx       = with(density) { cornerRadius.toPx() }

    // Animate shadow opacity when enabled/disabled — ease-in-out so it doesn't snap.
    val enabledAlpha by animateFloatAsState(
        targetValue = if (cardEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = MotionTokens.Easing.velvet),
        label = "shadowEnabledAlpha"
    )

    // Animate the shadows-enabled toggle so shadows fade in/out smoothly rather than snapping.
    val shadowsAlpha by animateFloatAsState(
        targetValue = if (shadowsEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "shadowsToggleAlpha"
    )

    // ── Cached native objects — allocated ONCE, never per frame ──────────────
    // Previously: android.graphics.Paint() + BlurMaskFilter() were constructed
    // inside drawBehind on every draw call (60–120×/sec × number of visible cards).
    // On a screen with 8 AnimatedElements that's ~960 Paint allocations/sec at 60Hz,
    // each triggering a GC pause on old JIT-based devices.
    //
    // Now: one Paint per Modifier instance. BlurMaskFilter is rebuilt only when
    // blurPx changes (never at runtime — blurRadius is a constant parameter).
    val cachedShadowPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    // Track last blur radius so we only rebuild BlurMaskFilter when it actually changes.
    var cachedBlurFraction = remember { -1f }

    // Pre-extract color channels so we avoid per-frame Color field reads inside draw
    val colorR = (color.red   * 255).toInt()
    val colorG = (color.green * 255).toInt()
    val colorB = (color.blue  * 255).toInt()
    val colorA = color.alpha

    drawBehind {
        if (cardProgress <= 0f || enabledAlpha <= 0f || shadowsAlpha <= 0f) return@drawBehind
        val fraction = (cardProgress * cardProgress * enabledAlpha * shadowsAlpha).coerceIn(0f, 1f)

        // Only rebuild BlurMaskFilter when the effective blur radius changes.
        // Because fraction animates smoothly this will update on most frames, BUT
        // BlurMaskFilter on Android is resolved to a fixed-radius GPU convolution at
        // draw time — rebuilding it is cheap (~microseconds) compared to the old
        // approach of also constructing a new Paint every frame.
        val newBlurFraction = blurPx * fraction
        if (newBlurFraction != cachedBlurFraction) {
            cachedBlurFraction = newBlurFraction
            cachedShadowPaint.maskFilter = BlurMaskFilter(
                newBlurFraction.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL
            )
        }

        val shadowArgb = android.graphics.Color.argb(
            (colorA * fraction * 255).toInt().coerceIn(0, 255),
            colorR, colorG, colorB
        )
        cachedShadowPaint.color = shadowArgb

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                dxPx * fraction,
                dyPx * fraction,
                size.width  + dxPx * fraction,
                size.height + dyPx * fraction,
                cornerPx, cornerPx,
                cachedShadowPaint
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
    val context = LocalContext.current
    val view = LocalView.current
    // --- disabled-state animations (unchanged) ---
    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = 0.60f,
            stiffness = 440f
        ),
        label = "settings_nav_disabled_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.32f,
        animationSpec = tween(durationMillis = 360, easing = MotionTokens.Easing.emphasized),
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
    val animLevel = LocalAnimationLevel.current
    LaunchedEffect(isPressed) {
        if (animLevel == 2) {
            pressProgress.snapTo(if (isPressed && enabled) 1f else 0f)
        } else {
            pressProgress.animateTo(
                targetValue = if (isPressed && enabled) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            )
        }
    }
    val p = pressProgress.value  // single read; all derived values computed below

    val density = LocalDensity.current
    // Derive all press visuals from p — evaluated in draw phase via graphicsLayer
    val pressScale       = 1f - p * (1f - MotionTokens.Scale.subtle)
    // Chevron: scales up AND nudges right on press — feels like it's pointing the way
    val chevronScaleVal  = 1f + p * 0.25f
    val chevronTXVal     = p * with(density) { 3.dp.toPx() }   // subtler nudge: 3dp instead of 4dp
    val chevronAlphaVal  = 0.55f + p * 0.45f                   // starts slightly more visible at rest
    val borderWidthVal   = (if (oledMode) 0.75f else 1f) + p * ((if (oledMode) 0.75f else 1f))  // 1dp → 2dp
    val cardBorderWidth  = borderWidthVal.dp

    val animatedCardBorderColor = when {
        !enabled             -> colors.primaryAccent.copy(alpha = 0.25f)
        isPressed            -> colors.primaryAccent
        else                 -> colors.primaryAccent.copy(alpha = 0.55f)
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
                scaleY = disabledScale * pressScale,
                clip = false
            )
            .border(width = cardBorderWidth, color = animatedCardBorderColor, shape = RoundedCornerShape(28.dp))
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Note: blurred border overlay removed — the solid border already reads as accent neon on dark backgrounds,
            // and the blur(8dp) + border Box was adding one RenderEffect per card (~8–12 extra GPU passes when scrolling).
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp)
                .graphicsLayer(alpha = alpha)
                .then(if (!enabled) Modifier.pointerInput(enabled) {
                    // Intercept taps only — vertical scroll events pass through to parent scrollable
                    detectTapGestures { }
                } else Modifier)
                // Replace .clickable with pointerInput so we can track press/release
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) onClick()
                        }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

                    // Animated description text with crossfade + subtle slide
                    androidx.compose.animation.AnimatedContent(
                        targetState = description,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(280, easing = MotionTokens.Easing.enter)) +
                             slideInVertically(animationSpec = tween(280, easing = MotionTokens.Easing.enter)) { it / 4 }) togetherWith
                            (fadeOut(animationSpec = tween(160, easing = MotionTokens.Easing.exit)) +
                             slideOutVertically(animationSpec = tween(160, easing = MotionTokens.Easing.exit)) { -it / 4 })
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
    oledMode: Boolean = false,
    cornerRadius: Dp = 28.dp
) {
    val animLevel = LocalAnimationLevel.current
    var isPressed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val density = LocalDensity.current

    val baseHeight = if (isSmallScreen) 54.dp else 58.dp
    val ts = LocalTypeScale.current
    val baseFontSize = ts.buttonLarge
    val shape = RoundedCornerShape(cornerRadius)

    // Single Animatable<Float> [0=rest, 1=pressed] drives ALL press visuals via
    // graphicsLayer — replaces 5 separate animateFloatAsState/animateDpAsState calls
    // that previously ran simultaneously on every press/release.
    val pressProgress = remember { Animatable(0f) }
    val flatBtnScope  = rememberCoroutineScope()
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
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
    else if (accent && !oledMode) colors.primaryAccent
    else colors.cardBackground

    val contentColor = if (accent && !oledMode) {
        if (colors.primaryAccent.luminance() > 0.5f) Color.Black else Color.White
    } else colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    // On press: full accent border. At rest: accent at consistent alpha.
    val borderColor = when {
        !enabled             -> colors.primaryAccent.copy(alpha = 0.25f)
        isPressed            -> colors.primaryAccent
        accent               -> colors.primaryAccent.copy(alpha = 0.55f)
        else                 -> colors.primaryAccent.copy(alpha = 0.55f)
    }

    val shouldShowBorder = true

    // Outer Box: handles layout sizing (modifier carries weight/fill/etc.) — never scaled
    Box(
        modifier = modifier.height(baseHeight),
        contentAlignment = Alignment.Center
    ) {
        // Neon border glow — previously a Modifier.blur(9dp) Box (= full RenderEffect per button).
        // Replaced: a cheap drawBehind radial gradient gives the same "lit edge" feel at zero blur cost.
        // Four buttons on screen × one RenderEffect each = four GPU pipelines removed per frame.
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
                                val hapticStartedAt = GamaHaptics.pressStart(context, view)
                                isPressed = true
                                val released = tryAwaitRelease()
                                isPressed = false
                                GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
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
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp.dp < 360.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val density = LocalDensity.current
    val ts = LocalTypeScale.current

    // Height scales with the smaller screen dimension so the button is always
    // proportionate regardless of orientation, resolution, or form factor.
    val baseHeight = when {
        isSmallScreen -> 46.dp
        isLandscape   -> (screenMinDp * 0.125f).dp.coerceIn(44.dp, 54.dp)
        else          -> (screenMinDp * 0.145f).dp.coerceIn(54.dp, 68.dp)
    }
    // Corner radius scales with button height for consistent curvature at any size.
    val cornerRadius = (baseHeight.value * 0.48f).dp.coerceIn(22.dp, 32.dp)
    val shape = RoundedCornerShape(cornerRadius)


    // Single Animatable<Float> drives ALL press visuals — same pattern as FlatButton.
    // Replaces 5 separate animators (pressScale, textScale, textTranslateY,
    // borderWidth, borderAlpha) that all fired simultaneously on every press.
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp = pressProgress.value
    val nudgePx = with(density) { 2.dp.toPx() }
    val pressScale      = 1f - pp * (1f - MotionTokens.Scale.moderate)
    val textScale       = 1f - pp * 0.07f
    val textTranslateY  = pp * nudgePx

    // Same physical reaction language as settings cards: slight zoom-in, thicker outline.
    val restBorderWidth = if (oledMode) 0.75f else 1f
    val borderWidthDp = (restBorderWidth + pp * restBorderWidth).dp
    val animatedButtonColor = if (!enabled) colors.cardBackground
    else if (iconType == "vulkan" || iconType == "opengl") Color.Transparent
    else colors.cardBackground

    val contentColor = colors.textPrimary
    val animatedTextColor = if (!enabled) colors.textSecondary.copy(alpha = 0.3f) else contentColor

    // Outline brightens on press, matching SettingsNavigationCard.
    val borderColor = when {
        !enabled  -> colors.primaryAccent.copy(alpha = 0.25f)
        isPressed -> colors.primaryAccent
        else      -> colors.primaryAccent.copy(alpha = 0.55f)
    }

    // The icon colour: vulkan bolt always neon accent; accent buttons get full accent;
    // non-accent (Resources, GPUWatch) get slightly dimmed accent.
    val iconColor = when {
        iconType == "vulkan"    -> colors.primaryAccent
        accent && !oledMode     -> colors.primaryAccent
        enabled                 -> colors.primaryAccent.copy(alpha = 0.85f)
        else                    -> colors.primaryAccent.copy(alpha = 0.3f)
    }

    // Screen-reader description
    val iconDescription = when (iconType) {
        "vulkan"   -> "Vulkan lightning bolt icon"
        "opengl"   -> "OpenGL hexagon icon"
        "resources"-> "Resources list icon"
        "gpuwatch" -> "GPU activity waveform icon"
        else       -> "Button icon"
    }
    val semanticsLabel = if (enabled) "$iconDescription, $text button"
    else         "$iconDescription, $text button, disabled"

    // Vulkan bloom glow radius — matches container glow radius formula
    val glowBlurRadius = (screenMinDp * 0.027f).dp.coerceIn(7.dp, 14.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(baseHeight)
            .semantics(mergeDescendants = true) { contentDescription = semanticsLabel },
        contentAlignment = Alignment.Center
    ) {
        // Outer Box owns the press scale transform — must be OUTSIDE clip so the
        // scale actually moves pixels. graphicsLayer before clip is a no-op visually.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale),
            contentAlignment = Alignment.Center
        ) {

            // Same soft accent glow style used by CURRENT RENDERER.
            // Drawn outside the clipped button so the glow can bleed past the edges.
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val buttonGlowAlpha = if (oledMode) 0.62f else 0.42f
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(glowBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .border(borderWidthDp, colors.primaryAccent.copy(alpha = buttonGlowAlpha), shape)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(animatedButtonColor)
                    .background(
                        Brush.radialGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.12f else 0.04f),
                            0.58f to colors.primaryAccent.copy(alpha = if (enabled) 0.040f else 0.015f),
                            1.0f to Color.Transparent,
                            radius = with(density) { 150.dp.toPx() },
                            center = Offset.Unspecified
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.030f else 0.0f),
                            0.55f to Color.Transparent,
                            1.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.065f else 0.0f)
                        )
                    )
                    .border(borderWidthDp, borderColor, shape)
                    .then(
                        if (enabled) Modifier.pointerInput(enabled) {
                            detectTapGestures(
                                onPress = {
                                    val hapticStartedAt = if (iconType == "vulkan" || iconType == "opengl") {
                                        GamaHaptics.rendererPressStart(context, view)
                                    } else {
                                        GamaHaptics.pressStart(context, view)
                                    }
                                    isPressed = true
                                    val released = tryAwaitRelease()
                                    isPressed = false
                                    GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                                    if (released) onClick()
                                }
                            )
                        } else Modifier
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .graphicsLayer(scaleX = textScale, scaleY = textScale, translationY = textTranslateY)
                        .padding(horizontal = 8.dp)
                ) {
                // ── Custom Canvas illustration ───────────────────────────────
                // Icon and spacer scale with button height for visual harmony at any size/orientation.
                val iconSizeDp = (baseHeight.value * 0.43f).dp.coerceIn(18.dp, 30.dp)
                val spacerWidth = (baseHeight.value * 0.13f).dp.coerceIn(6.dp, 12.dp)
                Box(contentAlignment = Alignment.Center) {
                    // Icon bloom — previously two Modifier.blur() Canvas layers (2 RenderEffects per button).
                    // Replaced: a single radial gradient drawn on the same Canvas as the icon.
                    // drawCircle with a radialGradient brush is a single GPU draw call — zero extra layers.
                    Canvas(modifier = Modifier.size(iconSizeDp)) {
                        val w = size.width; val h = size.height
                        val r = w * 0.72f
                        // Fake bloom: outer halo + tight ring, both purely alpha composited
                        if (oledMode) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to iconColor.copy(alpha = 0.28f),
                                    0.5f to iconColor.copy(alpha = 0.10f),
                                    1.0f to Color.Transparent,
                                    radius = r * 1.45f
                                ),
                                radius = r * 1.45f
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to iconColor.copy(alpha = 0.55f),
                                    0.6f to iconColor.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent,
                                    radius = r * 0.80f
                                ),
                                radius = r * 0.80f
                            )
                        }
                        drawIconShapeInline(iconType, w, h, iconColor)
                    }
                }
                Spacer(modifier = Modifier.width(spacerWidth))
                Text(
                    text = text,
                    fontSize = ts.buttonLarge,
                    fontWeight = FontWeight.Bold,
                    color = animatedTextColor,
                    fontFamily = quicksandFontFamily,
                    maxLines = 1,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
}

private fun DrawScope.drawIconShapeInline(iconType: String, w: Float, h: Float, color: Color) {
    val stroke = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val strokeThin = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (iconType) {
        "vulkan" -> {
            // Lightning bolt tracing the reference image exactly:
            //   Upper body: wide parallelogram, top-right to top-left, sweeping down-left
            //   Step: sharp horizontal notch — right protrusion at mid-height
            //   Lower spike: long narrow triangle pointing to bottom-left tip
            //
            //   P1 top-right corner of upper body   (0.72, 0.04)
            //   P2 top-left  corner of upper body   (0.38, 0.04)
            //   P3 left edge at step height          (0.18, 0.50)
            //   P4 notch inner-left (step base)      (0.42, 0.50)
            //   P5 bottom spike tip                  (0.28, 0.97)
            //   P6 notch outer-right (step tip)      (0.82, 0.44)
            //   P7 right edge of upper body          (0.58, 0.44)  ← back up to close
            drawPath(Path().apply {
                moveTo(w * 0.72f, h * 0.04f)   // P1: top-right
                lineTo(w * 0.38f, h * 0.04f)   // P2: top-left
                lineTo(w * 0.18f, h * 0.50f)   // P3: lower-left of upper body
                lineTo(w * 0.42f, h * 0.50f)   // P4: step inner corner
                lineTo(w * 0.28f, h * 0.97f)   // P5: bottom spike tip
                lineTo(w * 0.82f, h * 0.44f)   // P6: step outer-right tip
                lineTo(w * 0.58f, h * 0.44f)   // P7: right edge of upper body
                close()
            }, color = color)
        }
        "opengl" -> {
            fun hexPath(cx: Float, cy: Float, r: Float): Path {
                val pts = (0..5).map { i -> val a = Math.toRadians(60.0 * i - 30.0); Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat()) }
                return Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            }
            drawPath(hexPath(w*0.5f, h*0.5f, w*0.46f), color = color, style = stroke)
            drawPath(hexPath(w*0.5f, h*0.5f, w*0.26f), color = color.copy(alpha = 0.55f), style = strokeThin)
            drawCircle(color = color.copy(alpha = 0.8f), radius = w * 0.09f, center = Offset(w*0.5f, h*0.5f))
        }
        "resources" -> {
            val sw = w * 0.09f
            listOf(Triple(w*0.12f, w*0.88f, h*0.25f), Triple(w*0.12f, w*0.72f, h*0.50f), Triple(w*0.12f, w*0.56f, h*0.75f))
                .forEachIndexed { i, (x1, x2, y) -> drawLine(color.copy(alpha = 1f - i * 0.18f), Offset(x1, y), Offset(x2, y), sw, StrokeCap.Round) }
            drawCircle(color = color, radius = w * 0.10f, center = Offset(w * 0.84f, h * 0.75f))
        }
        "gpuwatch" -> {
            drawPath(Path().apply {
                moveTo(w*0.04f, h*0.50f); lineTo(w*0.28f, h*0.50f); lineTo(w*0.40f, h*0.18f)
                lineTo(w*0.52f, h*0.82f); lineTo(w*0.64f, h*0.50f); lineTo(w*0.96f, h*0.50f)
            }, color = color, style = Stroke(width = w * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// Draws the icon shape — shared across all bloom Canvas layers
private fun DrawScope.drawIconShape(iconType: String, canvasSize: Size, color: Color) {
    val w = canvasSize.width; val h = canvasSize.height
    when (iconType) {
        "vulkan" -> {
            val path = Path().apply {
                moveTo(w * 0.72f, h * 0.04f)   // P1: top-right
                lineTo(w * 0.38f, h * 0.04f)   // P2: top-left
                lineTo(w * 0.18f, h * 0.50f)   // P3: lower-left of upper body
                lineTo(w * 0.42f, h * 0.50f)   // P4: step inner corner
                lineTo(w * 0.28f, h * 0.97f)   // P5: bottom spike tip
                lineTo(w * 0.82f, h * 0.44f)   // P6: step outer-right tip
                lineTo(w * 0.58f, h * 0.44f)   // P7: right edge of upper body
                close()
            }
            drawPath(path, color = color)
        }
        "opengl" -> {
            fun hexPath(cx: Float, cy: Float, r: Float): Path {
                val pts = (0..5).map { i ->
                    val a = Math.toRadians(60.0 * i - 30.0)
                    Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
                }
                return Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            }
            drawPath(hexPath(w*0.5f,h*0.5f,w*0.46f), color=color, style=Stroke(w*0.10f, cap=StrokeCap.Round, join=StrokeJoin.Round))
            drawPath(hexPath(w*0.5f,h*0.5f,w*0.26f), color=color.copy(alpha=0.65f), style=Stroke(w*0.075f, cap=StrokeCap.Round, join=StrokeJoin.Round))
            drawCircle(color=color, radius=w*0.09f, center=Offset(w*0.5f,h*0.5f))
        }
    }
}

// BigRendererButton — square card: blurred glow blob behind card + 3-layer neon icon bloom.
// isSelected = this renderer is active → strong green glow + accent border.
@Composable
fun BigRendererButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    forceHighlight: Boolean = false,
    enabled: Boolean = true,
    colors: ThemeColors,
    oledMode: Boolean = false,
    iconType: String
) {
    val animLevel = LocalAnimationLevel.current
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val density   = LocalDensity.current
    val ts        = LocalTypeScale.current
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenWidthDp.dp < 360.dp
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp   = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val buttonHeight = when {
        isSmallScreen -> 88.dp
        isLandscape   -> (screenMinDp * 0.26f).dp.coerceIn(80.dp, 110.dp)
        else          -> (screenMinDp * 0.30f).dp.coerceIn(90.dp, 130.dp)
    }

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed && enabled) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp         = pressProgress.value
    val pressScale = 1f - pp * (1f - MotionTokens.Scale.moderate)
    val contentTY  = pp * with(density) { 2.dp.toPx() }
    val rendererBorderWidth = (1f + pp * 1.2f).dp
    // Shape and icon size are derived at layout time via BoxWithConstraints so they
    // scale correctly across all screen sizes, densities, and orientations.

    val highlighted = isSelected || forceHighlight
    val bgColor = when {
        !enabled -> colors.cardBackground
        oledMode -> colors.cardBackground
        else     -> colors.cardBackground
    }
    // Border: both buttons use the same accent-based style as the Resources button
    // (primaryAccent at 0.55f at rest, full accent on highlight/press).
    val borderColor = when {
        !enabled    -> colors.primaryAccent.copy(alpha = 0.20f)
        highlighted -> colors.primaryAccent
        isPressed   -> colors.primaryAccent
        else        -> colors.primaryAccent.copy(alpha = 0.55f)
    }
    // Icon: both use primaryAccent; dimmed slightly for the non-selected OpenGL state.
    val openGLGray = Color(0xFFE5E7EB)  // light gray for OpenGL text only
    val iconColor = when {
        !enabled                             -> colors.primaryAccent.copy(alpha = 0.55f)
        iconType == "opengl" && !highlighted -> colors.primaryAccent.copy(alpha = 0.65f)
        else                                 -> colors.primaryAccent
    }
    // Text: Vulkan = bright white (maximum legibility on dark card); OpenGL = light gray.
    // colors.primaryAccent is already derived from the user's wallpaper via ThemeColors,
    // so the Vulkan label inherits the correct wallpaper accent automatically.
    // Text is always full-brightness textPrimary — same as the Resources button.
    // We do NOT dim when disabled; the disabled state is communicated via icon/bg alpha.
    val textColor = colors.textPrimary

    // ── Outer wrapper — NOT clipped, so the glow blob bleeds freely ──────────
    // When disabled (Shizuku not ready): zoom out + fade to match REMINDERS card style.
    val disabledScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 430f),
        label = "big_btn_scale"
    )
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.32f,
        animationSpec = tween(durationMillis = 360, easing = MotionTokens.Easing.emphasized),
        label = "big_btn_alpha"
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .graphicsLayer(scaleX = disabledScale, scaleY = disabledScale, alpha = disabledAlpha),
        contentAlignment = Alignment.Center
    ) {
        // Derive sizes from actual rendered dimensions — correct at every resolution and orientation.
        val buttonSize = minOf(maxWidth, maxHeight)
        val shape      = RoundedCornerShape((buttonSize.value * 0.18f).dp.coerceIn(16.dp, 32.dp))
        val iconSizeDp = (buttonSize.value * 0.34f).dp.coerceIn(36.dp, 72.dp)
        val spacerDp   = (buttonSize.value * 0.065f).dp.coerceIn(6.dp, 14.dp)
        val glowBlurRadius = (buttonSize.value * 0.11f).dp.coerceIn(8.dp, 16.dp)



        // ── Outer box owns scale transform — graphicsLayer MUST be outside clip ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale),
            contentAlignment = Alignment.Center
        ) {
        // Same soft accent glow style used by CURRENT RENDERER.
        // It sits behind the clipped card so the glow can bleed around the square buttons.
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val buttonGlowAlpha = when {
                highlighted -> if (oledMode) 0.78f else 0.55f
                oledMode    -> 0.58f
                else        -> 0.38f
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(glowBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .border(rendererBorderWidth, colors.primaryAccent.copy(alpha = buttonGlowAlpha), shape)
            )
        }

        // ── Card (clipped) ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(bgColor)
                .background(
                    Brush.radialGradient(
                        0.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
                        0.58f to colors.primaryAccent.copy(alpha = if (enabled) 0.045f else 0.015f),
                        1.0f to Color.Transparent,
                        radius = with(density) { 210.dp.toPx() },
                        center = Offset.Unspecified
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.035f else 0.0f),
                        0.55f to Color.Transparent,
                        1.0f to colors.primaryAccent.copy(alpha = if (enabled) 0.075f else 0.0f)
                    )
                )
                .border(rendererBorderWidth, borderColor, shape)
                .then(if (enabled) Modifier.pointerInput(enabled) {
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.rendererPressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) onClick()
                        }
                    )
                } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.graphicsLayer(translationY = contentTY).padding(horizontal = (buttonSize.value * 0.05f).dp.coerceIn(4.dp, 12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ── Icon bloom — previously 2 blur() Canvas layers (2 RenderEffects per button per frame) ──
                // Replaced: single Canvas with radial gradient under the icon shape.
                // drawCircle(brush=radialGradient) is one GPU call — no extra compositor layers.
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(iconSizeDp)) {
                        val r = size.minDimension * 0.52f
                        // Outer soft halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to iconColor.copy(alpha = 0.30f),
                                0.55f to iconColor.copy(alpha = 0.10f),
                                1.0f to Color.Transparent,
                                radius = r * 1.60f
                            ),
                            radius = r * 1.60f
                        )
                        // Tight inner bloom
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.0f to iconColor.copy(alpha = 0.60f),
                                0.50f to iconColor.copy(alpha = 0.18f),
                                1.0f to Color.Transparent,
                                radius = r * 0.85f
                            ),
                            radius = r * 0.85f
                        )
                        // Crisp icon on top
                        drawIconShape(iconType, size, iconColor)
                    }
                }
                Spacer(modifier = Modifier.height(spacerDp))
                Text(text, fontSize = ts.buttonLarge, fontWeight = FontWeight.Bold,
                    color = textColor, fontFamily = quicksandFontFamily,
                    maxLines = 1, textAlign = TextAlign.Start)
            }
        }
        } // close outer graphicsLayer scale Box
    }
}

// ── RealtimeBlurBox ──────────────────────────────────────────────────────────
// Captures the screen region behind this composable at 1/4 resolution every
// ~33 ms (≈30 fps, half of 60 Hz), blurs it with a Gaussian kernel, and draws
// the result as the composable's background — giving a true real-time frosted-
// glass effect without RenderEffect (which only blurs the node's own content).
//
// Falls back to a plain semi-transparent tint on API < 26 (no PixelCopy).
@Composable
fun RealtimeBlurBox(
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Black.copy(alpha = 0.30f),
    blurRadius: Float = 12f,          // blur radius applied to the 1/4-res capture
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    borderColor: Color = Color.Transparent,
    borderWidth: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        // Pre-API-26 fallback: plain tinted box, no blur
        Box(
            modifier = modifier
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
                .clip(shape)
                .background(tintColor),
            content = content
        )
        return
    }

    val view  = LocalView.current
    val density = LocalDensity.current

    // Mutable state holding the latest blurred bitmap to paint as background
    var blurredBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Track the position and size of this box in window coordinates
    var windowX  by remember { mutableStateOf(0) }
    var windowY  by remember { mutableStateOf(0) }
    var boxW     by remember { mutableStateOf(0) }
    var boxH     by remember { mutableStateOf(0) }

    // ── Cached blur Paint — allocated ONCE, never per capture ────────────────
    // Previously: new android.graphics.Paint() + BlurMaskFilter() were allocated
    // inside the PixelCopy callback on every ~33ms capture, at ~30fps = ~30 Paint
    // + ~30 BlurMaskFilter allocations per second per RealtimeBlurBox instance.
    // Each triggers a GC pause on old devices. Cached here; BlurMaskFilter only
    // rebuilt when blurRadius changes (never at runtime).
    val cachedBlurPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true }
    }
    var cachedBlurRadiusApplied = remember { -1f }

    // Background coroutine: capture → scale-down → blur → publish at ~30 fps
    LaunchedEffect(Unit) {
        while (isActive) {
            val w = boxW; val h = boxH
            if (w > 4 && h > 4 && view.isAttachedToWindow) {
                // 1/4 resolution capture target
                val capW = (w / 4).coerceAtLeast(2)
                val capH = (h / 4).coerceAtLeast(2)
                val dest = Bitmap.createBitmap(capW, capH, Bitmap.Config.ARGB_8888)
                val srcRect = AndroidRect(windowX, windowY, windowX + w, windowY + h)

                try {
                    val window = (view.context as? android.app.Activity)?.window
                    if (window != null) {
                        // PixelCopy is async; suspend until callback fires
                        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                            PixelCopy.request(
                                window, srcRect, dest,
                                { result ->
                                    if (result == PixelCopy.SUCCESS) {
                                        // Rebuild BlurMaskFilter only when radius changes
                                        val clampedRadius = blurRadius.coerceAtLeast(1f)
                                        if (clampedRadius != cachedBlurRadiusApplied) {
                                            cachedBlurRadiusApplied = clampedRadius
                                            cachedBlurPaint.maskFilter = BlurMaskFilter(
                                                clampedRadius, BlurMaskFilter.Blur.NORMAL
                                            )
                                        }
                                        val blurred = Bitmap.createBitmap(capW, capH, Bitmap.Config.ARGB_8888)
                                        val canvas  = android.graphics.Canvas(blurred)
                                        canvas.drawBitmap(dest, 0f, 0f, cachedBlurPaint)
                                        dest.recycle()
                                        blurredBitmap = blurred.asImageBitmap()
                                    } else {
                                        dest.recycle()
                                    }
                                    if (cont.isActive) cont.resume(Unit) {}
                                },
                                android.os.Handler(android.os.Looper.getMainLooper())
                            )
                        }
                    } else {
                        dest.recycle()
                    }
                } catch (_: Exception) {
                    dest.recycle()
                }
            }
            delay(33L) // ~30 fps
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                windowX = pos.x.toInt()
                windowY = pos.y.toInt()
                boxW    = coords.size.width
                boxH    = coords.size.height
            }
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .clip(shape)
            .drawBehind {
                // Draw the blurred capture (scaled back up to full size) as background
                blurredBitmap?.let { bmp ->
                    drawImage(
                        image  = bmp,
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                    )
                }
                // Tint overlay on top of the blur
                drawRect(color = tintColor)
            }
    ) {
        content()
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

    // Responsive sizing — derived from the available space rather than hardcoded dp values.
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp   = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    // Outer gap between RendererCard and the button container
    val cardSpacing = when {
        isSmallScreen -> 10.dp
        isLandscape   -> (screenMinDp * 0.025f).dp.coerceIn(10.dp, 16.dp)
        else          -> (screenMinDp * 0.038f).dp.coerceIn(14.dp, 22.dp)
    }
    // Padding inside the button container box
    val containerPadding = when {
        isSmallScreen -> 10.dp
        isLandscape   -> (screenMinDp * 0.027f).dp.coerceIn(10.dp, 16.dp)
        else          -> (screenMinDp * 0.040f).dp.coerceIn(14.dp, 20.dp)
    }
    // Gap between button rows and between buttons in the same row
    val buttonSpacing = when {
        isSmallScreen -> 8.dp
        isLandscape   -> (screenMinDp * 0.022f).dp.coerceIn(8.dp, 14.dp)
        else          -> (screenMinDp * 0.030f).dp.coerceIn(10.dp, 16.dp)
    }
    // Container corner radius
    val containerRadius = (screenMinDp * 0.030f).dp.coerceIn(10.dp, 18.dp)

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(cardSpacing)
        ) {
            // Row 2: Renderer Card (Delay 150)
            AnimatedElement(visible = isVisible, staggerIndex = 1, cardShadow = true,
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

            // Rows 3 & 4: Vulkan | OpenGL and Resources | GPUWatch — unified glassmorphism container
            AnimatedElement(visible = isVisible, staggerIndex = 2, cardShadow = true,
                totalItems = 4, enabled = shizukuReady) {
                // Vulkan/OpenGL row: zoom out and dim when Shizuku is not ready,
                // matching the REMINDERS card style (scale 0.85f, alpha 0.25f).
                val rendererButtonScale by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.85f,
                    animationSpec = spring(
                        dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                        stiffness = MotionTokens.Springs.gentle.stiffness
                    ),
                    label = "renderer_button_scale"
                )
                val rendererButtonAlpha by animateFloatAsState(
                    targetValue = if (shizukuReady) 1f else 0.25f,
                    animationSpec = tween(durationMillis = 320, easing = MotionTokens.Easing.velvet),
                    label = "renderer_button_alpha"
                )

                // Container with cleaner, even glow — avoids the faint-corner artifact from blur()+border.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            color = colors.primaryAccent.copy(alpha = if (oledMode) 0.16f else 0.12f),
                            cornerRadius = CornerRadius(containerRadius.toPx(), containerRadius.toPx())
                        )
                        drawRoundRect(
                            color = colors.primaryAccent.copy(alpha = 0.55f),
                            style = Stroke(width = stroke),
                            cornerRadius = CornerRadius(containerRadius.toPx(), containerRadius.toPx())
                        )
                    }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(containerRadius))
                        .clip(RoundedCornerShape(containerRadius))
                        .background(cardBackground)
                ) {
                    Box(modifier = Modifier.padding(containerPadding)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(buttonSpacing)
                    ) {
                        // Row 3: Vulkan | OpenGL
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                        ) {
                            IllustratedButton(
                                text = LocalStrings.current["renderer.vulkan"].ifEmpty { "Vulkan" },
                                onClick = onVulkanClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer(
                                        scaleX = rendererButtonScale,
                                        scaleY = rendererButtonScale,
                                        alpha = rendererButtonAlpha
                                    ),
                                accent = true,
                                enabled = true,
                                colors = colors,
                                oledMode = oledMode,
                                iconType = "vulkan"
                            )
                            IllustratedButton(
                                text = LocalStrings.current["renderer.opengl"].ifEmpty { "OpenGL" },
                                onClick = onOpenGLClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer(
                                        scaleX = rendererButtonScale,
                                        scaleY = rendererButtonScale,
                                        alpha = rendererButtonAlpha
                                    ),
                                accent = false,
                                enabled = true,
                                colors = colors,
                                oledMode = oledMode,
                                iconType = "opengl"
                            )
                        }

                        // Row 4: Resources | Open GPUWatch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(buttonSpacing)
                        ) {
                            IllustratedButton(
                                text = LocalStrings.current["integrations.widget_action"].ifEmpty { "Library" },
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
                                    text = LocalStrings.current["renderer.show_gpuwatch"].ifEmpty { "Open GPUWatch" },
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
                    } // close Column
                    } // close padding Box (Layer 3)
                } // close inner border Box
                } // close outer glow Box
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
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val configuration = LocalConfiguration.current
    val isLandscape   = configuration.screenWidthDp > configuration.screenHeightDp
    val screenMinDp   = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    // All spatial values derived from screen size — stays proportional at any resolution/orientation.
    val cardCorner  = 28.dp
    val cardPadding = when {
        isSmallScreen -> 16.dp
        isLandscape   -> (screenMinDp * 0.040f).dp.coerceIn(16.dp, 22.dp)
        else          -> (screenMinDp * 0.055f).dp.coerceIn(20.dp, 28.dp)
    }
    val cardInnerSpacing = (screenMinDp * 0.024f).dp.coerceIn(9.dp, 15.dp)
    val glowBlurRadius   = (screenMinDp * 0.027f).dp.coerceIn(8.dp, 14.dp)

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

    // (rendererTextAlpha removed — was declared but never consumed anywhere in RendererCard)

    // ── Press state — single Animatable<Float> [0=rest, 1=pressed] drives all ──
    // press visuals via draw-phase lerp, replacing 3 separate animateFloatAsState
    // calls that all fired simultaneously with identical spring specs on every
    // press/release.  Same pattern used by IllustratedButton and FlatButton.
    var isPressed by remember { mutableStateOf(false) }
    val rendererCardPress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        rendererCardPress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
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
        // Breathing outline in error/warning state
        stateColor.copy(alpha = warningBorderAlpha)
    } else {
        colors.primaryAccent.copy(alpha = 0.55f)
    }

    // Border width: use animateFloatAsState so the value is read at draw time via .dp
    // conversion inside graphicsLayer-adjacent code — avoids a layout pass on change.
    val borderWidthF by animateFloatAsState(
        targetValue = when {
            isPressed       -> 2f
            !shizukuReady   -> 2f
            oledMode        -> 0.75f
            shizukuReady    -> 1f
            else            -> 1f
        },
        animationSpec = if (animLevel == 2) snap() else tween(
            durationMillis = if (isPressed) MotionTokens.Duration.flash else MotionTokens.Duration.quick,
            easing = MotionTokens.Easing.velvet
        ),
        label = "renderer_border_width"
    )
    val borderWidth = borderWidthF.dp

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
        cardBackground
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
                        .matchParentSize()
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
                        .matchParentSize()
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
            // Neon glow behind the card border
            if (shizukuReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cardGlowAlpha = if (oledMode) 0.80f else 0.55f
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(glowBlurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .border(1.dp, colors.primaryAccent.copy(alpha = cardGlowAlpha), RoundedCornerShape(cardCorner))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                    .clip(RoundedCornerShape(cardCorner))
                    .background(subtleWarningBackground)
                    .background(
                        Brush.radialGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (shizukuReady) 0.13f else 0.05f),
                            0.58f to colors.primaryAccent.copy(alpha = if (shizukuReady) 0.045f else 0.02f),
                            1.0f to Color.Transparent,
                            radius = with(density) { 210.dp.toPx() },
                            center = Offset.Unspecified
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.primaryAccent.copy(alpha = if (shizukuReady) 0.035f else 0.0f),
                            0.55f to Color.Transparent,
                            1.0f to colors.primaryAccent.copy(alpha = if (shizukuReady) 0.075f else 0.0f)
                        )
                    )
                    .border(
                        width = borderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(cardCorner)
                    )
                    .pointerInput(shizukuReady) {
                        detectTapGestures(
                            onPress = {
                                val hapticStartedAt = GamaHaptics.pressStart(context, view)
                                isPressed = true
                                val released = tryAwaitRelease()
                                isPressed = false
                                GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
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
                        .padding(cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(cardInnerSpacing)
                ) {
                    Text(
                        text = LocalStrings.current["widget.current_renderer"].ifEmpty { "CURRENT RENDERER" },
                        color = if (!shizukuReady) stateColor else colors.primaryAccent.copy(alpha = 0.86f),
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
                            color = colors.textPrimary,
                            fontSize = ts.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.graphicsLayer(
                                scaleX = nameScale,
                                scaleY = nameScale,
                                translationY = nameTY
                            )
                        )
                    }

                    // Last-switched timestamp
                    if (lastSwitchTime > 0L && commandOutput.isEmpty()) {
                        val relativeTime = remember(lastSwitchTime, strings) {
                            val diff = System.currentTimeMillis() - lastSwitchTime
                            val minutes = diff / 60_000
                            val hours   = diff / 3_600_000
                            val days    = diff / 86_400_000
                            when {
                                minutes < 1   -> strings["renderer.api_changed_now"].ifEmpty { "Graphics API last changed just now" }
                                minutes < 60  -> strings["renderer.api_changed_minutes"].replace("%d", minutes.toString()).ifEmpty { "Graphics API last changed ${minutes}m ago" }
                                hours   < 24  -> strings["renderer.api_changed_hours"].replace("%d", hours.toString()).ifEmpty { "Graphics API last changed ${hours}h ago" }
                                days    == 1L -> strings["renderer.api_changed_yesterday"].ifEmpty { "Graphics API last changed yesterday" }
                                else          -> strings["renderer.api_changed_days"].replace("%d", days.toString()).ifEmpty { "Graphics API last changed ${days}d ago" }
                            }
                        }
                        Text(
                            text = relativeTime,
                            color = colors.textSecondary.copy(alpha = 0.45f),
                            fontSize = ts.labelSmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
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
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (!shizukuReady) {
                        Spacer(modifier = Modifier.height(2.dp))
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
    val cardBorderColor = colors.primaryAccent.copy(alpha = if (enabled) 0.55f else 0.25f)

    val colorCardScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = MotionTokens.Springs.balanced.dampingRatio,
            stiffness = MotionTokens.Springs.balanced.stiffness
        ),
        label = "color_card_scale"
    )
    val colorCardAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.32f,
        animationSpec = tween(durationMillis = 360, easing = MotionTokens.Easing.emphasized),
        label = "color_card_alpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = colorCardScale, scaleY = colorCardScale)
            .border(width = 1.dp, color = cardBorderColor, shape = RoundedCornerShape(28.dp))
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Blurred border overlay removed for performance (one RenderEffect per card was the main overdraw culprit).
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = colorCardAlpha)
                .then(if (!enabled) Modifier.pointerInput(enabled) {
                    // Intercept taps only — vertical scroll events pass through to parent scrollable
                    detectTapGestures { }
                } else Modifier),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                        )
                )
                // Content — always uses single-column (portrait) layout inside the card,
                // regardless of screen orientation. The CARD itself may be in a multi-column
                // grid (controlled by the caller), but the swatches stay centred and readable.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(if (isSmallScreen) 20.dp else 24.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title + description — consistent font size matching ToggleCard / DYNAMIC COLORS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            color = colors.primaryAccent.copy(alpha = 0.8f),
                            fontSize = ts.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = description,
                            color = colors.textSecondary,
                            fontSize = ts.bodyMedium,
                            fontFamily = quicksandFontFamily,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Swatch rows
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
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

                    // Hex input — animated in/out when Advanced Color Picker is toggled.
                    // Also hidden when the card is disabled (e.g. OLED mode disables gradient cards)
                    // so toggling Advanced Color Picker while in OLED mode has no visible effect here.
                    AnimatedVisibility(
                        visible = advancedPicker && enabled,
                        enter = expandVertically(
                            animationSpec = tween(320, easing = MotionTokens.Easing.emphasizedDecelerate)
                        ) + fadeIn(animationSpec = tween(240, delayMillis = 60, easing = MotionTokens.Easing.enter)),
                        exit = shrinkVertically(
                            animationSpec = tween(220, easing = MotionTokens.Easing.emphasizedAccelerate)
                        ) + fadeOut(animationSpec = tween(140, easing = MotionTokens.Easing.exit))
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
                                text = LocalStrings.current["colors.advanced_picker_desc"].ifEmpty { "Enter a valid 6-digit hex (e.g. #4895EF)" },
                                fontSize = ts.labelSmall,
                                color = Color(0xFFEF4444),
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
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
            textAlign = TextAlign.Start,
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
    enabled: Boolean = true,
    accentBorder: Boolean = false
) {
    val ts = LocalTypeScale.current
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = 430f
        ),
        label = "toggle_card_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.32f,
        animationSpec = tween(durationMillis = 360, easing = MotionTokens.Easing.emphasized),
        label = "toggle_card_alpha"
    )
    // --- checked-state colour expressions ---
    // Only animate the alpha/opacity of the accent — the color itself comes from the
    // global animated `colors.*`, so no local color animation is needed. Adding a
    // second animateColorAsState on top of an already-animating color causes it to
    // perpetually chase a moving target, producing the "delayed" appearance.

    // Border alpha: checked cards should visibly lift, even when accentBorder is true.
    // Renderer-panel toggles use accentBorder=true, so the unchecked state must be
    // calmer than the checked state instead of using the same outline opacity.
    val targetBorderAlpha = when {
        !enabled            -> 0.24f
        checked             -> 0.82f
        accentBorder        -> 0.55f
        oledMode            -> 0.30f
        else                -> 0.20f
    }
    val borderAlpha by animateFloatAsState(
        targetValue = targetBorderAlpha,
        animationSpec = tween(durationMillis = 240, easing = MotionTokens.Easing.enter),
        label = "toggle_border_alpha"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (checked && enabled) 1.35.dp else 1.dp,
        animationSpec = tween(durationMillis = 240, easing = MotionTokens.Easing.enter),
        label = "toggle_border_width"
    )
    val cardBorderColor = colors.primaryAccent.copy(alpha = borderAlpha)

    // Left accent edge alpha — only the alpha transitions (boolean state change)
    val accentEdgeAlpha by animateFloatAsState(
        targetValue = if (checked && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = MotionTokens.Easing.enter),
        label = "toggle_accent_edge_alpha"
    )

    val cardBg = cardBackground

    // Title alpha — only the opacity transitions, color follows global
    val titleAlpha by animateFloatAsState(
        targetValue = when {
            checked  -> 1.0f
            else     -> 0.7f
        },
        animationSpec = tween(durationMillis = 240, easing = MotionTokens.Easing.enter),
        label = "toggle_title_alpha"
    )
    val titleColor = colors.primaryAccent.copy(alpha = titleAlpha)

    // Uniform card height — cards with a Switch tend to be taller than plain button-only
    // cards (like SettingsNavigationCard) because the Switch widget adds extra height.
    // Wrapping everything in a Box with heightIn(min = …) ensures ToggleCards and
    // NavigationCards share the same minimum height so rows of mixed card types align.
    val cardMinHeight = if (isSmallScreen) 72.dp else 80.dp

    CompositionLocalProvider(LocalCardEnabled provides enabled) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = cardMinHeight)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                clip = false
            )
            .border(
                width = borderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(28.dp)
            )
            .then(if (!enabled) Modifier.pointerInput(enabled) {
                // Intercept taps only — vertical scroll events pass through to parent scrollable
                detectTapGestures { }
            } else Modifier)
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Blurred border overlay removed — RenderEffect per-card hurt scrolling fps badly on Snapdragon 8 Gen 2.
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = cardMinHeight)
                .graphicsLayer(alpha = alpha),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = cardMinHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .drawBehind {
                        // Minimalist left-edge indicator: a clean 3dp solid line, full card height
                        if (accentEdgeAlpha > 0f) {
                            val lineWidth = 3.dp.toPx()
                            drawRect(
                                color = colors.primaryAccent.copy(alpha = accentEdgeAlpha),
                                topLeft = Offset(0f, 0f),
                                size = Size(lineWidth, size.height)
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = cardMinHeight)
                        .padding(
                            top = if (isSmallScreen) 20.dp else 24.dp,
                            bottom = if (isSmallScreen) 20.dp else 24.dp,
                            start = if (isSmallScreen) 20.dp else 24.dp,
                            end = if (isSmallScreen) 20.dp else 24.dp
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
                    // In light mode: soft accent-tinted track instead of harsh grey
                    val uncheckedTrackColor = lerp(
                        colors.primaryAccent.copy(alpha = 0.20f),
                        Color(0xFF1A1A1A),
                        oledTrackAlpha
                    )
                    val checkedThumbColor = lerp(Color.White, colors.primaryAccent, oledTrackAlpha)
                    // Unchecked thumb: white in light mode (clean, pill-shaped look), accent in oled
                    val uncheckedThumbColor = lerp(Color.White, colors.primaryAccent.copy(alpha = 0.6f), oledTrackAlpha)

                    val switchColors = SwitchDefaults.colors(
                        checkedThumbColor = checkedThumbColor,
                        checkedTrackColor = checkedTrackColor,
                        uncheckedThumbColor = uncheckedThumbColor,
                        uncheckedTrackColor = uncheckedTrackColor,
                        checkedBorderColor = Color.Transparent,
                        uncheckedBorderColor = Color.Transparent,
                        disabledCheckedThumbColor = colors.textSecondary.copy(alpha = 0.4f),
                        disabledCheckedTrackColor = colors.primaryAccent.copy(alpha = 0.15f),
                        disabledUncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        disabledUncheckedTrackColor = colors.primaryAccent.copy(alpha = 0.10f)
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
    } // end CompositionLocalProvider(LocalCardEnabled)
}

@Composable
fun BackArrowButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val buttonSize = if (isSmallScreen) 48.dp else 56.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Static glow alpha — no infinite transition needed; the blur radius
    // already softens the shape and the button is only visible in dialogs.
    val glowAlpha = 0.22f

    var isPressed by remember { mutableStateOf(false) }
    // ── Single Animatable<Float> replaces 2 separate animators ───────────────
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
            )
        )
    }
    val pp = pressProgress.value
    val pressScale = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderColor = if (pp > 0.5f) colors.primaryAccent
    else colors.primaryAccent.copy(alpha = 0.35f + pp * 0.65f)

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
                .border(borderWidthDp, borderColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
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
    enabled: Boolean = true,
    scrollState: ScrollState? = null,
    modifier: Modifier = Modifier
) {
    // scrollState is kept as a parameter for future use (e.g. auto-scroll-to-top on tap),
    // but the back button is always rendered regardless of whether the content scrolls.
    // Hiding it when content fits on screen caused panels to have no visible back button
    // on larger displays or when panel content was short.

    val animLevel = LocalAnimationLevel.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 22.dp else 26.dp

    // Static glow alpha — back button is shown inside already-open panels;
    // running an infinite transition here would tick every frame for the
    // entire duration the panel is visible.  Static value is indistinguishable.
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    // ── Single Animatable<Float> replaces 4 separate animators ───────────────
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp = pressProgress.value
    val density = LocalDensity.current
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f),
        label = "panel_back_appear_scale"
    )
    val pressScale    = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val chevronTX     = pp * with(density) { -3.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f

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
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) currentOnClick()
                        }
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
fun PanelSearchButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animLevel = LocalAnimationLevel.current
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 23.dp else 27.dp
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }

    val pp = pressProgress.value
    val density = LocalDensity.current
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f),
        label = "panel_search_appear_scale"
    )
    val pressScale = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val iconTY = pp * with(density) { 1.5.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f
    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
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

        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .semantics { contentDescription = strings["search.content_description"].ifEmpty { "Search settings" } }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) currentOnClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationY = iconTY)
            ) {
                val strokeWidth = 2.3.dp.toPx()
                val radius = size.minDimension * 0.30f
                val center = Offset(size.width * 0.43f, size.height * 0.43f)
                drawCircle(
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = colors.primaryAccent.copy(alpha = 0.9f),
                    start = Offset(center.x + radius * 0.70f, center.y + radius * 0.70f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}


@Composable
fun PanelGlobalButton(
    onClick: () -> Unit,
    colors: ThemeColors,
    oledMode: Boolean = false,
    isSmallScreen: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val animLevel = LocalAnimationLevel.current
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val btnSize  = if (isSmallScreen) 48.dp else 52.dp
    val iconSize = if (isSmallScreen) 24.dp else 28.dp
    val glowAlpha = 0.26f

    var isPressed by remember { mutableStateOf(false) }
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }

    val pp = pressProgress.value
    val density = LocalDensity.current
    val appearScale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f),
        label = "panel_global_appear_scale"
    )
    val pressScale = (1f - pp * (1f - MotionTokens.Scale.subtle)) * appearScale
    val iconTY = pp * with(density) { 1.5.dp.toPx() }
    val borderWidthDp = (1.5f + pp * 0.5f).dp
    val borderAlphaVal = 0.4f + pp * 0.6f
    val glowSize = btnSize * 1.8f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
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

        Box(
            modifier = Modifier
                .size(btnSize)
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            colors.primaryAccent.copy(alpha = 0.22f),
                            colors.primaryAccent.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    borderWidthDp,
                    colors.primaryAccent.copy(alpha = borderAlphaVal),
                    RoundedCornerShape(28.dp)
                )
                .semantics { contentDescription = strings["search.global_shortcut_content_description"].ifEmpty { "Open global settings list" } }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
                            if (released) currentOnClick()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(translationY = iconTY)
            ) {
                val strokeWidth = 2.1.dp.toPx()
                val globeColor = colors.primaryAccent.copy(alpha = 0.92f)
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.39f
                val meridianSize = Size(radius * 1.05f, radius * 2.0f)
                val meridianTopLeft = Offset(center.x - meridianSize.width / 2f, center.y - meridianSize.height / 2f)

                drawCircle(
                    color = globeColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = globeColor,
                    start = Offset(center.x - radius * 0.88f, center.y),
                    end = Offset(center.x + radius * 0.88f, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawArc(
                    color = globeColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = meridianTopLeft,
                    size = meridianSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = globeColor,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = meridianTopLeft,
                    size = meridianSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                val latitudeSize = Size(radius * 1.65f, radius * 0.82f)
                val latitudeTopLeft = Offset(center.x - latitudeSize.width / 2f, center.y - latitudeSize.height / 2f)

                drawArc(
                    color = globeColor.copy(alpha = 0.78f),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = latitudeTopLeft.copy(y = latitudeTopLeft.y - radius * 0.33f),
                    size = latitudeSize,
                    style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = globeColor.copy(alpha = 0.78f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = latitudeTopLeft.copy(y = latitudeTopLeft.y + radius * 0.33f),
                    size = latitudeSize,
                    style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
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
    val context = LocalContext.current
    val view = LocalView.current
    val isSmallScreen = LocalConfiguration.current.screenWidthDp.dp < 360.dp
    val density = LocalDensity.current
    val shape = RoundedCornerShape(28.dp)

    // ── Single Animatable<Float> [0=rest, 1=pressed] drives ALL press visuals ─
    // Replaces 4 separate animateFloatAsState/animateDpAsState calls that all
    // fired simultaneously on every press/release — same optimisation as FlatButton.
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp = pressProgress.value
    val nudgePx = with(density) { 1.5.dp.toPx() }
    // Derive all press visuals from pp — evaluated in draw phase via graphicsLayer
    val pressScale       = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val textScaleVal     = 0.93f + (1f - 0.93f) * (1f - pp)
    val textTranslateYVal = pp * nudgePx
    val borderWidthDp    = (if (oledMode) 0.75f else 1f) + pp * (if (oledMode) 0.75f else 1f)  // 1dp → 2dp
    val borderAlphaVal   = 0.5f + pp * 0.5f

    val containerColor = if (accent) colors.primaryAccent else cardBackground
    val borderColor = when {
        isPressed -> colors.primaryAccent
        else      -> colors.primaryAccent.copy(alpha = borderAlphaOverride ?: 0.55f)
    }

    // Outer Box: takes the caller's modifier (weight/fill) — never scaled, so layout is stable
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (oledMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(Brush.radialGradient(listOf(
                        colors.primaryAccent.copy(alpha = 0.16f),
                        Color.Transparent
                    )))
            )
        }
        // Inner Box: visually scaled on press, but layout is already committed by outer Box,
        // so background, border, clip and highlight are always perfectly coincident
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
                .clip(shape)
                .background(containerColor)
                .border(width = borderWidthDp.dp, color = borderColor, shape = shape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val hapticStartedAt = GamaHaptics.pressStart(context, view)
                            isPressed = true
                            val released = tryAwaitRelease()
                            isPressed = false
                            GamaHaptics.releaseAfterPress(context, view, hapticStartedAt, released)
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
                        scaleX = textScaleVal,
                        scaleY = textScaleVal,
                        translationY = textTranslateYVal
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
    // Capture the latest onDismiss without restarting pointerInput on every recomposition.
    // Using the lambda directly as a pointerInput key causes the swipe coroutine to cancel
    // and restart whenever the lambda reference changes (e.g. after pressing the back button
    // and triggering a recomposition), which throws a CancellationException mid-gesture.
    val currentOnDismiss by rememberUpdatedState(onDismiss)

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
    // `renderContent` is the mount gate:
    //   • true  immediately on open  (before enter animation starts)
    //   • false only after the exit alpha reaches 0

    var renderContent by remember { mutableStateOf(visible) }
    // isExiting: true from the moment visible flips false until renderContent is cleared.
    // While this is true, outside-tap dismissal is ignored so closing/opening panels
    // cannot accidentally dismiss the wrong layer.
    var isExiting     by remember { mutableStateOf(false) }
    val animScale     = remember { Animatable(if (visible) 1f else 0.88f) }
    val animAlpha     = remember { Animatable(if (visible) 1f else 0f) }
    val scope         = rememberCoroutineScope()

    LaunchedEffect(visible) {
        if (visible) {
            // Panel is opening — clear the exit guard before any animation.
            isExiting = false

            renderContent = true

            if (animLevel == 2) {
                animScale.snapTo(1f)
                animAlpha.snapTo(1f)
                return@LaunchedEffect
            }

            // Start from compressed/invisible state — slightly less compressed (0.88 vs 0.80)
            // so the enter doesn't feel like it's coming from far away.
            animScale.snapTo(0.88f)
            animAlpha.snapTo(0f)

            // Full = springy pop. Reduced = no bounce, lighter fade/scale.
            val enterSpec: AnimationSpec<Float> = when (animLevel) {
                0 -> spring(dampingRatio = 0.42f, stiffness = 190f)
                else -> tween(durationMillis = 210, easing = MotionTokens.Easing.emphasizedDecelerate)
            }
            val alphaEnterDuration = when (animLevel) { 0 -> 280; else -> 190 }

            scope.launch {
                animScale.animateTo(targetValue = 1f, animationSpec = enterSpec)
            }
            animAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = alphaEnterDuration, easing = MotionTokens.Easing.enter)
            )

        } else {
            // Panel is closing — raise the guard immediately so outside-tap
            // dismissal ignores any pointer events that arrive during the exit animation.
            isExiting = true

            if (animLevel == 2) {
                renderContent = false
                isExiting = false
                animScale.snapTo(0.88f)
                animAlpha.snapTo(0f)
                return@LaunchedEffect
            }

            val exitDuration = when (animLevel) { 0 -> 340; else -> 190 }

            scope.launch {
                animScale.animateTo(
                    targetValue = if (animLevel == 0) 0.78f else 0.96f,
                    animationSpec = tween(durationMillis = exitDuration, easing = MotionTokens.Easing.exit)
                )
            }

            animAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = exitDuration - 30, easing = MotionTokens.Easing.exit)
            )

            renderContent = false
            isExiting = false
            animScale.snapTo(0.88f)
            animAlpha.snapTo(0f)
        }
    }

    if (!renderContent) return

    // Outside-tap barrier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (visible && !isExiting) Modifier.pointerInput(dismissOnClickOutside) {
                    if (dismissOnClickOutside) detectTapGestures { currentOnDismiss() }
                    else detectTapGestures { }
                } else Modifier
            )
    )

    // Content — scale and alpha only. Swipe-to-dismiss was removed because it
    // interfered with sliders and horizontal controls inside panels.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = animScale.value
                scaleY = animScale.value
                alpha  = animAlpha.value
            },
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

    // ── Single Animatable<Float> replaces 3 separate animators ───────────────
    val pressProgress = remember { Animatable(0f) }
    LaunchedEffect(isPressed) {
        pressProgress.animateTo(
            targetValue   = if (isPressed) 1f else 0f,
            animationSpec = when (animLevel) {
                2 -> snap()
                1 -> tween(durationMillis = 120, easing = MotionTokens.Easing.emphasized)
                else -> spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness    else MotionTokens.Springs.pressUp.stiffness
                )
            }
        )
    }
    val pp = pressProgress.value
    val pressScale    = 1f - pp * (1f - MotionTokens.Scale.subtle)
    val borderAlphaVal = 0.45f + pp * 0.55f
    val borderWidthDp  = (if (oledMode) 0.75f else 1f) + pp * (if (oledMode) 0.75f else 1f)
    val borderColor    = colors.primaryAccent.copy(alpha = borderAlphaVal)

    val bgColor = colors.cardBackground
    val iconSizeDp = 18.dp

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(borderWidthDp.dp, borderColor, RoundedCornerShape(14.dp))
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


