package com.popovicialinc.gama

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
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
                    text = LocalStrings.current["integrations.title"].ifEmpty { "INTEGRATIONS" },
                    fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                    colors = colors,
                    reverseGradient = false,
                    scrollOffset = scrollState.value
                )

                Text(
                    text = LocalStrings.current["integrations.subtitle"].ifEmpty { "Plug GAMA into your existing Android automations and shortcuts" },
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
                                    title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                                    description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do" },
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
                                    title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                                    description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "Three tiles: Vulkan, OpenGL, and Doze. Each one lights up when active and switches instantly on tap, no menu needed" },
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
                            title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                            description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Use broadcast intents to switch renderers automatically based on time, app launch, WiFi, or anything Tasker can do" },
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
                            title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                            description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "Three tiles: Vulkan, OpenGL, and Doze. Each one lights up when active and switches instantly on tap, no menu needed" },
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
                        title = LocalStrings.current["integrations.widget"].ifEmpty { "HOME SCREEN WIDGET" },
                        description = LocalStrings.current["integrations.widget_desc"].ifEmpty { "Put a Vulkan / OpenGL toggle right on your home screen. One tap and you're switched" },
                        statusLabel = "Available",
                        statusOk = true,
                        actionLabel = "Add widget",
                        onAction = {
                            onInfoRequested(
                                "Adding the Widget",
                                "Use the launcher's widget picker, or tap the add button below to open Android's native widget pin sheet when supported. Once placed, the GAMA widget gives you quick renderer switching, live status, and a fast shortcut back into the app."
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
    val context = LocalContext.current
    val isWidgetDialog = title.contains("widget", ignoreCase = true)
    val canPinWidget = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    BouncyDialog(visible = visible, onDismiss = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape && !isTablet) 0.64f else 0.9f)
                .widthIn(max = 540.dp)
                .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } },
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 22.dp else 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 18.dp else 22.dp)
            ) {
                Text(
                    text = title,
                    fontSize = ts.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isWidgetDialog) {
                    WidgetSetupPreview(colors = colors, cardBackground = cardBackground)

                    Text(
                        text = "the easiest way is the native android pin sheet. if your launcher supports it, tap the button below and android will offer the widget instantly.",
                        fontSize = ts.bodyMedium,
                        lineHeight = (ts.bodyMedium.value * 1.35f).sp,
                        color = colors.textPrimary.copy(alpha = 0.85f),
                        fontFamily = quicksandFontFamily,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WidgetInstructionRow(
                            step = "1",
                            title = if (canPinWidget) "tap add widget" else "open the widget picker",
                            body = if (canPinWidget)
                                "this opens android's own widget pin panel, which is the fastest way to place the widget."
                            else
                                "long-press an empty area on your home screen and choose widgets."
                            ,
                            colors = colors,
                            cardBackground = cardBackground
                        )
                        WidgetInstructionRow(
                            step = "2",
                            title = "find gama",
                            body = "look for the GAMA widget, then place it wherever you want on your home screen.",
                            colors = colors,
                            cardBackground = cardBackground
                        )
                        WidgetInstructionRow(
                            step = "3",
                            title = "switch instantly",
                            body = "the revamped widget shows renderer status, lets you switch fast, and gives you a direct route back into the app.",
                            colors = colors,
                            cardBackground = cardBackground
                        )
                    }

                    if (canPinWidget) {
                        DialogButton(
                            text = "Add full widget",
                            onClick = {
                                val ok = requestPinGamaWidget(context, compactToggle = false)
                                if (ok) {
                                    Toast.makeText(context, "launcher widget sheet opened", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "your launcher does not support widget pin requests", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground
                        )
                        DialogButton(
                            text = "Add 1x1 toggle",
                            onClick = {
                                val ok = requestPinGamaWidget(context, compactToggle = true)
                                if (ok) {
                                    Toast.makeText(context, "launcher toggle sheet opened", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "your launcher does not support widget pin requests", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = colors,
                            cardBackground = cardBackground
                        )
                    }

                    DialogButton(
                        text = if (canPinWidget) "Close" else "Got it",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                } else {
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
                        text = LocalStrings.current["dialogs.btn_close"].ifEmpty { "Close" },
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = colors,
                        cardBackground = cardBackground
                    )
                }
            }
        }
    }
}

private fun requestPinGamaWidget(context: Context, compactToggle: Boolean = false): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return try {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val providerClass = if (compactToggle) GamaToggleWidgetReceiver::class.java else GamaWidgetReceiver::class.java
        val provider = ComponentName(context, providerClass)
        manager.requestPinAppWidget(provider, null, null)
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun WidgetSetupPreview(
    colors: ThemeColors,
    cardBackground: Color
) {
    val chipBg = colors.primaryAccent.copy(alpha = 0.12f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
            .background(cardBackground, RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "gama widget",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily
                )
                Text(
                    text = "quick renderer control, straight from the launcher",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = quicksandFontFamily
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(chipBg)
                    .border(0.8.dp, colors.primaryAccent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "live",
                    color = colors.primaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.primaryAccent.copy(alpha = 0.08f))
                .border(0.9.dp, colors.primaryAccent.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "current renderer",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "vulkan",
                    color = colors.primaryAccent,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("vulkan", "opengl").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (index == 0) colors.primaryAccent.copy(alpha = 0.16f) else colors.textSecondary.copy(alpha = 0.08f))
                                .border(
                                    0.9.dp,
                                    if (index == 0) colors.primaryAccent.copy(alpha = 0.45f) else colors.textSecondary.copy(alpha = 0.18f),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (index == 0) colors.primaryAccent else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = quicksandFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetInstructionRow(
    step: String,
    title: String,
    body: String,
    colors: ThemeColors,
    cardBackground: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBackground)
            .border(0.75.dp, colors.primaryAccent.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = colors.primaryAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = quicksandFontFamily
            )
            Text(
                text = body,
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = quicksandFontFamily
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
    val enabled = onAction != null

    // Keep this outline mathematically identical to SettingsNavigationCard,
    // so TASKER / QUICK SETTINGS TILES / HOME SCREEN WIDGET match
    // GITHUB / DISCORD / SHIZUKU in color, thickness, radius, and press behavior.
    val pressProgress = remember { Animatable(0f) }
    val animLevel = LocalAnimationLevel.current

    LaunchedEffect(isPressed, enabled) {
        val target = if (isPressed && enabled) 1f else 0f
        if (animLevel == 2) {
            pressProgress.snapTo(target)
        } else {
            pressProgress.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio else MotionTokens.Springs.pressUp.dampingRatio,
                    stiffness = if (isPressed) MotionTokens.Springs.pressDown.stiffness else MotionTokens.Springs.pressUp.stiffness
                )
            )
        }
    }

    val p = pressProgress.value
    val pressScale = 1f - p * (1f - MotionTokens.Scale.subtle)
    val baseBorderWidth = if (oledMode) 0.75f else 1f
    val cardBorderWidth = (baseBorderWidth + p * baseBorderWidth).dp
    val cardBorderColor = if (isPressed && enabled) {
        colors.primaryAccent
    } else {
        colors.primaryAccent.copy(alpha = 0.55f)
    }
    val statusColor = if (statusOk) colors.successColor else Color(0xFFF59E0B)
    val minCardHeight = if (LocalConfiguration.current.screenWidthDp.dp < 360.dp) 72.dp else 80.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minCardHeight)
            .graphicsLayer(
                scaleX = pressScale,
                scaleY = pressScale,
                clip = false
            )
            .pressedAccentOutlineGlow(
                pressProgress = p,
                color = colors.primaryAccent,
                cornerRadius = 28.dp,
                strokeWidth = cardBorderWidth,
                glowRadius = 11.dp
            )
            .border(
                width = cardBorderWidth,
                color = cardBorderColor,
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minCardHeight)
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    val released = tryAwaitRelease()
                                    isPressed = false
                                    if (released) onAction?.invoke()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = cardBackground),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minCardHeight)
                    .background(colors.primaryAccent.copy(alpha = if (enabled) p * 0.10f else 0f))
                    .padding(if (isSmallScreen) 20.dp else 24.dp),
                verticalArrangement = Arrangement.Center
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
                        fontFamily = quicksandFontFamily,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

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
                            fontFamily = quicksandFontFamily,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = ts.bodyMedium,
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (ts.bodyMedium.value * 1.4f).sp
                )

                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "$actionLabel  →",
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
