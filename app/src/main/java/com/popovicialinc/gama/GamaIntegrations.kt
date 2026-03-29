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
// Integrations: Tasker, QS Tiles, Widget panel + cards
// ============================================================

@Composable
fun IntegrationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (url: String, label: String, description: String) -> Unit,
    onInfoRequested: (title: String, body: String) -> Unit,
    isBlurred: Boolean = false,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean = false
) {
    val ts = LocalTypeScale.current

    // Hoisted above BouncyDialog so they exist before enter animation starts
    val scrollState = rememberScrollState()
    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(durationMillis = 500, easing = MotionTokens.Easing.emphasized),
        label = "integrations_back_padding"
    )

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        val animLevel = LocalAnimationLevel.current
        val dismissOnClickOutside = LocalDismissOnClickOutside.current

        val blurAmount by animateDpAsState(
            targetValue = if (isBlurred) 20.dp else 0.dp,
            animationSpec = if (animLevel == 2) snap<Dp>() else tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "integrations_blur"
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
                    text = "Plug GAMA into your existing Android automations and shortcuts",
                    fontSize = ts.labelLarge,
                    color = colors.textSecondary,
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                // ── Tasker ──────────────────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
                    if (isLandscape) {
                        // Two-column: TASKER | QUICK SETTINGS TILES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                IntegrationInfoCard(
                                    title = "TASKER",
                                    description = "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do",
                                    statusLabel = "Available",
                                    statusOk = true,
                                    actionLabel = "Open Guide",
                                    onAction = {
                                        onLinkSelected(
                                            "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
                                            "Tasker Guide",
                                            "This will open the GAMA Tasker integration guide on GitHub. It covers how to use broadcast intents to automate renderer switching based on time, app launch, WiFi network, and more."
                                        )
                                    },
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    oledMode = oledMode,
                                    isSmallScreen = isSmallScreen
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                IntegrationInfoCard(
                                    title = "QUICK SETTINGS TILES",
                                    description = "Three tiles: Vulkan, OpenGL, and Doze. Each one lights up when active and switches instantly on tap, no menu needed",
                                    statusLabel = if (tileAvailable) "3 tiles available" else "Requires Android 7+",
                                    statusOk = tileAvailable,
                                    actionLabel = if (tileAvailable) "How to add" else null,
                                    onAction = if (tileAvailable) ({
                                        onInfoRequested(
                                            "Adding QS Tiles",
                                            "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA ones: Vulkan, OpenGL, and Doze. Drag whichever tiles you want into your active area, then tap Done. Each tile shows as highlighted when its mode is currently active."
                                        )
                                    }) else null,
                                    colors = colors,
                                    cardBackground = cardBackground,
                                    oledMode = oledMode,
                                    isSmallScreen = isSmallScreen
                                )
                            }
                        }
                    } else {
                        IntegrationInfoCard(
                            title = "TASKER",
                            description = "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do",
                            statusLabel = "Available",
                            statusOk = true,
                            actionLabel = "Open Guide",
                            onAction = {
                                onLinkSelected(
                                    "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
                                    "Tasker Guide",
                                    "This will open the GAMA Tasker integration guide on GitHub. It covers how to use broadcast intents to automate renderer switching based on time, app launch, WiFi network, and more."
                                )
                            },
                            colors = colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }

                // ── Quick Settings Tile ─────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
                    if (!isLandscape) {
                        val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        IntegrationInfoCard(
                            title = "QUICK SETTINGS TILES",
                            description = "Three tiles: Vulkan, OpenGL, and Doze. Each one lights up when active and switches instantly on tap, no menu needed",
                            statusLabel = if (tileAvailable) "3 tiles available" else "Requires Android 7+",
                            statusOk = tileAvailable,
                            actionLabel = if (tileAvailable) "How to add" else null,
                            onAction = if (tileAvailable) ({
                                onInfoRequested(
                                    "Adding QS Tiles",
                                    "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA ones: Vulkan, OpenGL, and Doze. Drag whichever tiles you want into your active area, then tap Done. Each tile shows as highlighted when its mode is currently active."
                                )
                            }) else null,
                            colors = colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode,
                            isSmallScreen = isSmallScreen
                        )
                    }
                }

                // ── Home Screen Widget ──────────────────────────────────────
                AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
                    IntegrationInfoCard(
                        title = "HOME SCREEN WIDGET",
                        description = "Put a Vulkan / OpenGL toggle right on your home screen. One tap and you're switched",
                        statusLabel = "Available",
                        statusOk = true,
                        actionLabel = "How to add",
                        onAction = {
                            onInfoRequested(
                                "Adding the Widget",
                                "Long-press an empty area on your home screen and select Widgets from the menu that appears. Scroll through the list until you find GAMA, then long-press the widget and drag it to wherever you want it placed. The widget lets you switch between Vulkan and OpenGL with a single tap, right from your home screen."
                            )
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
// IntegrationInfoDialog — info-only popup for QS Tiles & Widget instructions
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun IntegrationInfoDialog(
    visible: Boolean,
    title: String,
    body: String,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    BouncyDialog(visible = visible, onDismiss = onDismiss) {
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = ts.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = body,
                    fontSize = ts.bodyLarge,
                    lineHeight = (ts.bodyLarge.value * 1.4f).sp,
                    color = colors.textPrimary.copy(alpha = 0.85f),
                    fontFamily = quicksandFontFamily,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                DialogButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    cardBackground = cardBackground
                )
            }
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

    // In OLED mode show a thin accent outline matching the OLED MODE card style.
    val borderColor = when {
        isPressed && onAction != null -> colors.primaryAccent
        oledMode                      -> colors.primaryAccent.copy(alpha = 0.3f)
        else                          -> colors.border
    }
    val borderWidth by animateDpAsState(
        targetValue = if (isPressed && onAction != null) 2.dp else if (oledMode) 0.75.dp else 1.dp,
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