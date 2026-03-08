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
// Dialogs: all modal dialogs
// ============================================================

@Composable
fun ShizukuHelpDialog(
    visible: Boolean,
    helpType: String,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    val dialogBorderAlpha = 0.3f
    val dialogBorderWidth = 0.75.dp
    val dialogShape = RoundedCornerShape(24.dp)

    BouncyDialog(visible = visible, onDismiss = onDismiss) {
    Card(
        modifier = Modifier
            .fillMaxWidth(if (isLandscape && !isTablet) 0.6f else 0.9f)
            .widthIn(max = 500.dp)
            .border(
                width = dialogBorderWidth,
                color = colors.primaryAccent.copy(alpha = dialogBorderAlpha),
                shape = dialogShape
            )
            .pointerInput(Unit) {
                detectTapGestures { /* Block taps on card from dismissing dialog */ }
            },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = dialogShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isSmallScreen) 24.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 20.dp else 24.dp)
        ) {
            // Title — accent-coloured, matches ExternalLinkConfirmDialog
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (helpType == "not_running") "Shizuku Not Running" else "Permission Needed",
                    fontSize = ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent
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
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            // Button border uses the same accent alpha as the card outline for consistency
            DialogButton(
                text = "Okay",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                cardBackground = cardBackground,
                accent = true,
                borderAlphaOverride = dialogBorderAlpha
            )
        }
    }
    } // BouncyDialog
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
                    width = 0.75.dp,
                    color = colors.primaryAccent.copy(alpha = 0.3f),
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
                // Hourglass / pause icon — conveys "brief wait"
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val s = size.minDimension
                    val stroke = 3.dp.toPx()
                    val color = colors.primaryAccent
                    // Outer circle
                    drawCircle(color = color, radius = s / 2f, style = Stroke(width = stroke))
                    // Two horizontal lines (pause symbol)
                    val barW = s * 0.14f
                    val barH = s * 0.32f
                    val cx = s / 2f
                    val cy = s / 2f
                    listOf(cx - barW * 1.4f, cx + barW * 0.4f).forEach { x ->
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, cy - barH / 2f),
                            size = androidx.compose.ui.geometry.Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2f),
                        )
                    }
                }

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
        targetValue   = if (isSwitching) 360f else 0f,
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
                    width = 0.75.dp,
                    color = colors.primaryAccent.copy(alpha = 0.3f),
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
                    width = 0.75.dp,
                    color = colors.primaryAccent.copy(alpha = 0.3f),
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
                    width = 0.75.dp,
                    color = colors.primaryAccent.copy(alpha = 0.3f),
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
                .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
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
                // External link / arrow-out-of-box icon
                Canvas(modifier = Modifier.size(if (isSmallScreen) 56.dp else 64.dp)) {
                    val s = size.minDimension
                    val stroke = 3.dp.toPx()
                    val color = colors.primaryAccent
                    // Circle
                    drawCircle(color = color, radius = s / 2f, style = Stroke(width = stroke))
                    // Arrow shaft: bottom-left → top-right
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(s * 0.33f, s * 0.67f),
                        end   = androidx.compose.ui.geometry.Offset(s * 0.67f, s * 0.33f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    // Arrowhead
                    val headPath = Path().apply {
                        moveTo(s * 0.46f, s * 0.33f)
                        lineTo(s * 0.67f, s * 0.33f)
                        lineTo(s * 0.67f, s * 0.54f)
                    }
                    drawPath(headPath, color = color,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

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
                        text = "Close",
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

// New Advanced Settings Panel
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
                    // ── Ambient glow blob at the top — only runs while dialog is visible ──
                    val glowPulse = rememberInfiniteTransition(label = "egg_glow")
                    val glowAlpha by glowPulse.animateFloat(
                        initialValue = if (visible) 0.28f else 0f,
                        targetValue  = if (visible) 0.48f else 0f,
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
                    .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDontShowAgainChange(!dontShowAgain) }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
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
                .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
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
                    text = "Open GPUWatch",
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
