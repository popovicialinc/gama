package com.popovicialinc.gama

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// ─────────────────────────────────────────────────────────────────────────────
// Shared panel scaffold — full-screen BouncyDialog with scroll + back button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PanelScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    isLandscape: Boolean,
    isSmallScreen: Boolean,
    isBlurred: Boolean = false,
    oledMode: Boolean = false,
    colors: ThemeColors,
    content: @Composable ColumnScope.(scrollState: ScrollState) -> Unit
) {
    val dismissOnClickOutside = LocalDismissOnClickOutside.current
    val scrollState = rememberScrollState()

    val backPadding by animateDpAsState(
        targetValue = if (visible) (if (isSmallScreen) 72.dp else 84.dp) else 0.dp,
        animationSpec = tween(500, easing = MotionTokens.Easing.emphasized),
        label = "panel_back_padding"
    )
    // When a sub-dialog opens (isBlurred = true) the panel fades down to a dim
    // blurred state so the dialog feels like it's on a higher layer.
    // When the sub-dialog closes the panel fades back to full opacity/sharpness.
    //
    // Two animated values:
    //   blurAlpha   — 0→1 drives the blurred copy fading IN
    //   panelAlpha  — 1→0.35 dims the whole panel as the sub-dialog arrives
    //
    // The net effect: panel softly recedes, sub-dialog pops on top, then panel
    // snaps back to life when the dialog is dismissed.
    val animLevel = LocalAnimationLevel.current
    val blurAlpha by animateFloatAsState(
        targetValue = if (isBlurred) 1f else 0f,
        animationSpec = if (animLevel == 2) snap() else tween(320, easing = FastOutSlowInEasing),
        label = "panel_blur_alpha"
    )
    // Overall panel dim: fades to 35% when sub-dialog is shown so it visually
    // recedes behind the dialog, then snaps back to 100% when dialog closes.
    val panelAlpha by animateFloatAsState(
        targetValue = if (isBlurred) 0.35f else 1f,
        animationSpec = if (animLevel == 2) snap() else tween(320, easing = FastOutSlowInEasing),
        label = "panel_dim_alpha"
    )

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
        // Single render of panel content — no double composition.
        //
        // Previously: panelContent() was called twice (sharp copy + blurred copy),
        // composing the entire panel tree twice during the ~320ms transition.
        //
        // Now: one render, always.  When a sub-dialog opens:
        //   • API 31+: Modifier.blur() is applied in the draw phase only (zero
        //     recomposition overhead) and the panel dims to 35% alpha.
        //   • API < 31: blur is unavailable, the panel just dims to 35% — still
        //     communicates depth without any GPU blur cost.
        //
        // The visual difference on API 31+ is imperceptible: the user is looking
        // at a dialog, not scrutinising the blurred panel behind it.
        val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = panelAlpha }
                .then(
                    if (useBlur && (isBlurred || blurAlpha > 0f))
                        Modifier.blur(
                            radius = (blurAlpha * 20f).dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                    else Modifier
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dismissOnClickOutside) {
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
                    content(scrollState)
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
}


// ─────────────────────────────────────────────────────────────────────────────
// SettingsPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onAppearanceClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onColorsClick: () -> Unit,
    onRendererClick: () -> Unit,
    onSystemClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = "SETTINGS",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 5) {
            SettingsNavigationCard(
                title = "APPEARANCE",
                description = "Theme, animations, UI scale, stagger, and your display name",
                onClick = { performHaptic(); onAppearanceClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 5) {
            SettingsNavigationCard(
                title = "EFFECTS",
                description = "Gradient background, frosted glass blur, and particle animations",
                onClick = { performHaptic(); onEffectsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 5) {
            SettingsNavigationCard(
                title = "COLORS",
                description = "Accent color, gradient palette, OLED mode, and dynamic theming",
                onClick = { performHaptic(); onColorsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 5) {
            SettingsNavigationCard(
                title = "RENDERER",
                description = "Aggressive mode, launcher restart, doze, and switching behavior",
                onClick = { performHaptic(); onRendererClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 5) {
            SettingsNavigationCard(
                title = "SYSTEM",
                description = "Notifications, integrations, backup, and advanced options",
                onClick = { performHaptic(); onSystemClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// VisualEffectsPanel
// ─────────────────────────────────────────────────────────────────────────────

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
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    staggerEnabled: Boolean,
    onStaggerEnabledChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = "APPEARANCE",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        // Theme
        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "THEME", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Auto", "Dark", "Light"),
                            selectedIndex = themePreference,
                            onOptionSelected = { performHaptic(); onThemeChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } // end Box
        }

        // Animations
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "ANIMATIONS", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Full", "Reduced", "Off"),
                            selectedIndex = animationLevel,
                            onOptionSelected = { performHaptic(); onAnimationLevelChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } // end Box
        }

        // UI Scale
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "UI SCALE", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("75%", "100%", "125%"),
                            selectedIndex = uiScale,
                            onOptionSelected = { performHaptic(); onUiScaleChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } // end Box
        }

        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 5) {
            ToggleCard(
                title = "STAGGER ANIMATIONS",
                description = "Cards inside panels cascade in one by one. Turn this off for snappier panel opens",
                checked = staggerEnabled,
                onCheckedChange = { performHaptic(); onStaggerEnabledChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "YOUR NAME", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Used in greetings and notifications — completely optional",
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { onUserNameChange(it) },
                            placeholder = {
                                Text(
                                    "e.g. Alex",
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = ts.bodyMedium,
                                    color = colors.textSecondary.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = ts.bodyMedium,
                                color = colors.textPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = colors.primaryAccent,
                                unfocusedBorderColor = colors.border.copy(alpha = 0.4f),
                                cursorColor          = colors.primaryAccent
                            ),
                            shape  = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// OLEDPanel
// ─────────────────────────────────────────────────────────────────────────────

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
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "OLED MODE", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)
        Text(
            text = "On OLED and AMOLED screens, true black pixels are completely off — so this saves real battery",
            fontSize = ts.bodyMedium, color = colors.textSecondary,
            fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
        )
        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 1) {
            ToggleCard(
                title = "OLED MODE",
                description = if (oledMode) "Active — backgrounds are pure black" else "Off — using your regular theme background",
                checked = oledMode,
                onCheckedChange = { performHaptic(); onOledModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// EffectsPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EffectsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    onBlurSettingsClick: () -> Unit,
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
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "EFFECTS", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 3) {
            SettingsNavigationCard(
                title = "BLUR",
                description = "Frosted glass look on panels — toggle and choose transition style",
                onClick = { performHaptic(); onBlurSettingsClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 3) {
            ToggleCard(
                title = "GRADIENT BACKGROUND", description = "A slow, shifting color gradient behind the main screen",
                checked = gradientEnabled, onCheckedChange = { performHaptic(); onGradientChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 3) {
            SettingsNavigationCard(
                title = "PARTICLES", description = "Little floating dots, stars, or a live sky — pick your vibe",
                onClick = { performHaptic(); onParticlesClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BlurPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BlurPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    blurOptimised: Boolean,
    onBlurOptimisedChange: (Boolean) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "BLUR", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 2) {
            ToggleCard(
                title = "BLUR",
                description = "Frosted glass effect behind panels and dialogs — subtle but really polished",
                checked = blurEnabled,
                onCheckedChange = { performHaptic(); onBlurChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f)
                        else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "BLUR TRANSITION",
                            fontSize = ts.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = if (blurEnabled) 0.7f else 0.3f)
                        )
                        Text(
                            text = if (blurOptimised)
                                "Optimised: the blur is calculated once at full strength, then faded in and out using alpha. Fast and smooth on any device."
                            else
                                "Real: the blur radius is recalculated every single frame as the panel opens. Accurate, but heavier on the GPU.",
                            fontSize = ts.bodySmall,
                            fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary.copy(alpha = if (blurEnabled) 1f else 0.4f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        GlideOptionSelector(
                            options = listOf("Optimised", "Real"),
                            selectedIndex = if (blurOptimised) 0 else 1,
                            onOptionSelected = { idx ->
                                performHaptic()
                                onBlurOptimisedChange(idx == 0)
                            },
                            colors = colors,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = blurEnabled
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ParticlesPanel
// ─────────────────────────────────────────────────────────────────────────────

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
    nativeRefreshRate: Boolean,
    onNativeRefreshRateChange: (Boolean) -> Unit,
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
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "PARTICLES", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 7) {
            ToggleCard(
                title = "PARTICLES", description = "Small dots drift around in the background — gives the app a living feel",
                checked = particlesEnabled, onCheckedChange = { performHaptic(); onParticlesChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "SPEED", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Slow", "Medium", "Fast"),
                            selectedIndex = particleSpeed.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onParticleSpeedChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled
                        )
                    }
                }
            } // end Box
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "COUNT", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Low", "Medium", "High"),
                            selectedIndex = particleCount.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onParticleCountChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled
                        )
                    }
                }
            } // end Box
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 7) {
            ToggleCard(
                title = "TIME MODE", description = "A sun and moon move across the sky based on the actual time of day",
                checked = particleTimeMode,
                onCheckedChange = { performHaptic(); onParticleTimeModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 7) {
            ToggleCard(
                title = "STAR MODE", description = "Swaps regular particles for tiny twinkling stars — looks great at night",
                checked = particleStarMode,
                onCheckedChange = { performHaptic(); onParticleStarModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled && !particleTimeMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 6, totalItems = 7) {
            ToggleCard(
                title = "PARALLAX", description = "Tilt your device and the particles shift with it, adding a nice 3D depth effect",
                checked = particleParallaxEnabled,
                onCheckedChange = { performHaptic(); onParticleParallaxChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 7, totalItems = 7) {
            ToggleCard(
                title = "NATIVE REFRESH RATE",
                description = "Runs particle physics at your display's full refresh rate. Silky smooth on 120Hz devices, but uses more battery. Leave this off on older or lower-end devices",
                checked = nativeRefreshRate,
                onCheckedChange = { performHaptic(); onNativeRefreshRateChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                enabled = particlesEnabled
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ColorCustomizationPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ColorCustomizationPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    oledMode: Boolean,
    onOledModeChange: (Boolean) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    advancedColorPicker: Boolean,
    onAdvancedColorPickerChange: (Boolean) -> Unit,
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
    cardBackground: Color
) {
    val ts = LocalTypeScale.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "COLORS", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 5) {
            ToggleCard(
                title = "OLED MODE",
                description = "Turns backgrounds pure black — great for battery life on OLED screens",
                checked = oledMode,
                onCheckedChange = { performHaptic(); onOledModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 5) {
            ToggleCard(
                title = "DYNAMIC COLOR",
                description = if (dynamicColorAvailable) "Pulls accent colors from your wallpaper — changes automatically with Material You" else "Requires Android 12 or newer",
                checked = useDynamicColor && dynamicColorAvailable,
                onCheckedChange = { performHaptic(); onDynamicColorChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 5) {
            ToggleCard(
                title = "ADVANCED COLOR PICKER",
                description = "Adds a hex input field so you can type any color directly — e.g. #4895EF",
                checked = advancedColorPicker,
                onCheckedChange = { performHaptic(); onAdvancedColorPickerChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = !useDynamicColor || !dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 5) {
            CompactColorPickerCard(
                title = "ACCENT COLOR",
                description = "Used for buttons, highlights, and borders throughout the app",
                currentColor = customAccentColor,
                onColorChange = onAccentColorChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = !useDynamicColor || !dynamicColorAvailable, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 5) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactColorPickerCard(
                    title = "GRADIENT START",
                    description = "The color the gradient fades from at the top of the screen",
                    currentColor = customGradientStart,
                    onColorChange = onGradientStartChange,
                    colors = colors, cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                    advancedPicker = advancedColorPicker,
                    enabled = !useDynamicColor || !dynamicColorAvailable, oledMode = oledMode
                )
                CompactColorPickerCard(
                    title = "GRADIENT END",
                    description = "The color the gradient fades into at the bottom of the screen",
                    currentColor = customGradientEnd,
                    onColorChange = onGradientEndChange,
                    colors = colors, cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                    advancedPicker = advancedColorPicker,
                    enabled = !useDynamicColor || !dynamicColorAvailable, oledMode = oledMode
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// FunctionalityPanel  — top-level nav hub
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FunctionalityPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onRendererClick: () -> Unit,
    onBehaviorClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = "FUNCTIONALITY",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 5) {
            SettingsNavigationCard(
                title = "RENDERER",
                description = "Aggressive mode, launcher restart, and per-app exclusions",
                onClick = { performHaptic(); onRendererClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 5) {
            SettingsNavigationCard(
                title = "BEHAVIOUR",
                description = "Fine-tune how GAMA acts — verbose output, dismissal style, doze, and more",
                onClick = { performHaptic(); onBehaviorClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 5) {
            SettingsNavigationCard(
                title = "NOTIFICATIONS",
                description = "Get a nudge if you've been on OpenGL for a while and forget to switch back",
                onClick = { performHaptic(); onNotificationsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 5) {
            SettingsNavigationCard(
                title = "BACKUP & RESTORE",
                description = "Save all your settings to a file or bring them back from a backup",
                onClick = { performHaptic(); onBackupClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 5) {
            SettingsNavigationCard(
                title = "CRASH LOG",
                description = "Something went wrong? Check here for clues on what happened",
                onClick = { performHaptic(); onCrashLogClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// RendererPanel  — sub-panel of Functionality
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RendererPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    aggressiveMode: Boolean,
    onAggressiveModeChange: (Boolean) -> Unit,
    killLauncher: Boolean,
    onKillLauncherChange: (Boolean) -> Unit,
    dozeMode: Boolean,
    onDozeModeChange: (Boolean) -> Unit,
    showGpuWatchButton: Boolean,
    onShowGpuWatchButtonChange: (Boolean) -> Unit,
    onShowAggressiveWarning: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = "RENDERER",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
            ToggleCard(
                title = "AGGRESSIVE MODE",
                description = "Applies the renderer to every installed package — more thorough, but make sure you've read the warning first",
                checked = aggressiveMode,
                onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
            ToggleCard(
                title = "KILL LAUNCHER ON SWITCH",
                description = "Force-stops the launcher after switching so it picks up the new renderer immediately. Leave OFF on Xiaomi / MIUI — enabling it there can trigger MIUI's app restriction system.",
                checked = killLauncher,
                onCheckedChange = { performHaptic(); onKillLauncherChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
            ToggleCard(
                title = "DOZE",
                description = "Forces your device into deep doze right away — great for squeezing out extra battery life",
                checked = dozeMode,
                onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 4) {
            ToggleCard(
                title = "SHOW GPUWATCH BUTTON",
                description = "Shows the GPUWatch shortcut on the main screen alongside the Resources button. Samsung devices only — requires Game Booster / Game Tools (enable in Developer Options).",
                checked = showGpuWatchButton,
                onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// SystemPanel  — replaces FunctionalityPanel + BehaviorPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SystemPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    onNotificationsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = "SYSTEM",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 5) {
            ToggleCard(
                title = "VERBOSE MODE",
                description = "Shows the full shell output while switching — great for seeing exactly what's happening",
                checked = verboseMode,
                onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 5) {
            ToggleCard(
                title = "TAP OUTSIDE TO CLOSE",
                description = "Tap anywhere outside a panel to close it — turn this off if you'd rather use the back button",
                checked = dismissOnClickOutside,
                onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 5) {
            SettingsNavigationCard(
                title = "NOTIFICATIONS",
                description = "Get a nudge if you've been on OpenGL for a while and forget to switch back",
                onClick = { performHaptic(); onNotificationsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 5) {
            SettingsNavigationCard(
                title = "BACKUP & RESTORE",
                description = "Save all your settings to a file or bring them back from a backup",
                onClick = { performHaptic(); onBackupClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 5) {
            SettingsNavigationCard(
                title = "CRASH LOG",
                description = "Something went wrong? Check here for clues on what happened",
                onClick = { performHaptic(); onCrashLogClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BehaviorPanel  — kept for backward compat reference; superseded by SystemPanel

@Composable
fun BehaviorPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    dozeMode: Boolean,
    onDozeModeChange: (Boolean) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    showGpuWatchButton: Boolean,
    onShowGpuWatchButtonChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = "BEHAVIOUR",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
            ToggleCard(
                title = "VERBOSE MODE",
                description = "Shows the full shell output while switching — great for seeing exactly what's happening",
                checked = verboseMode,
                onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
            ToggleCard(
                title = "TAP OUTSIDE TO CLOSE",
                description = "Tap anywhere outside a panel to close it — turn this off if you'd rather use the back button",
                checked = dismissOnClickOutside,
                onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
            ToggleCard(
                title = "DOZE",
                description = "Forces your device into deep doze right away — great for squeezing out extra battery life",
                checked = dozeMode,
                onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 4) {
            ToggleCard(
                title = "SHOW GPUWATCH BUTTON",
                description = "Shows the GPUWatch shortcut on the main screen alongside the Resources button. Samsung devices only — requires Game Booster / Game Tools (enable in Developer Options).",
                checked = showGpuWatchButton,
                onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// NotificationsPanel
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
    val intervalLabels = listOf("2 h", "4 h", "6 h", "12 h", "24 h")

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "NOTIFICATIONS", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        Text(
            text = "GAMA can remind you to switch back to Vulkan if you've been on OpenGL for too long",
            fontSize = ts.bodyMedium, color = colors.textSecondary,
            fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
        )

        if (!hasPermission) {
            AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.errorColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.errorColor.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "PERMISSION REQUIRED", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily, color = colors.errorColor
                        )
                        Text(
                            text = "GAMA needs permission to send you notifications — tap below to grant it.",
                            fontSize = ts.bodyMedium, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        FlatButton(
                            text = "Grant Permission", onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(), accent = true, colors = colors, maxLines = 1
                        )
                    }
                }
            }
        }

        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
            ToggleCard(
                title = "REMINDERS",
                description = if (currentRenderer == "OpenGL") "You're on OpenGL right now — reminders are watching" else "Will ping you if you switch to OpenGL and leave it running",
                checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange,
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = hasPermission
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
            val intervalEnabled = hasPermission && notificationsEnabled
            val intervalScale by animateFloatAsState(
                targetValue = if (intervalEnabled) 1f else 0.85f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "interval_scale"
            )
            val intervalAlpha by animateFloatAsState(
                targetValue = if (intervalEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "interval_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = intervalScale, scaleY = intervalScale, alpha = intervalAlpha)
                    .border(
                        width = if (oledMode) 0.75.dp else 1.dp,
                        color = if (oledMode) colors.primaryAccent.copy(alpha = 0.3f) else colors.border.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!intervalEnabled) Modifier.pointerInput(intervalEnabled) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "REMINDER INTERVAL", fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = intervalLabels,
                            selectedIndex = notifIntervalIndex.coerceIn(0, intervalLabels.size - 1),
                            onOptionSelected = onNotifIntervalChange,
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                    }
                }
            }
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 4) {
            val testBtnEnabled = hasPermission
            val testBtnScale by animateFloatAsState(
                targetValue = if (testBtnEnabled) 1f else 0.85f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "test_btn_scale"
            )
            val testBtnAlpha by animateFloatAsState(
                targetValue = if (testBtnEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "test_btn_alpha"
            )
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = testBtnScale, scaleY = testBtnScale, alpha = testBtnAlpha)) {
                FlatButton(
                    text = "Send Test Notification", onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(), accent = false, enabled = testBtnEnabled,
                    colors = colors, maxLines = 1, oledMode = oledMode
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BackupPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BackupPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "BACKUP", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        Text(
            text = "Pack up all your GAMA settings into a file, or restore them from a previous backup — great before reinstalling",
            fontSize = ts.bodyMedium, color = colors.textSecondary,
            fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 2) {
            SettingsNavigationCard(
                title = "EXPORT BACKUP", description = "Saves everything — theme, preferences, excluded apps — to a JSON file",
                onClick = onExport, isSmallScreen = isSmallScreen,
                colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 2) {
            SettingsNavigationCard(
                title = "RESTORE BACKUP", description = "Pick a backup file to bring all your settings back exactly as they were",
                onClick = onImport, isSmallScreen = isSmallScreen,
                colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CrashLogPanel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single parsed GAMA crash entry, split out of crash_log.txt.
 * Each entry corresponds to one ── timestamp ── header block.
 */
private data class GamaCrashEntry(
    val timestamp: String,   // e.g. "2025-06-14 03:22:11"
    val thread: String,      // e.g. "main"
    val summary: String,     // first exception line, used as subtitle on list card
    val fullText: String     // the entire block text, saved verbatim to .txt
)

/**
 * Parse the raw crash_log.txt content into individual [GamaCrashEntry] objects.
 * Entries are separated by the "── YYYY-MM-DD HH:mm:ss ─…" header written by
 * the crash handler in MainActivity.
 */
private fun parseGamaCrashLog(raw: String): List<GamaCrashEntry> {
    if (raw.isBlank()) return emptyList()
    // Split on the separator line "── <timestamp> ──…"
    val sections = raw.split(Regex("(?=── \\d{4}-\\d{2}-\\d{2})"))
    return sections
        .filter { it.isNotBlank() }
        .mapNotNull { block ->
            val lines = block.lines()
            val headerLine = lines.firstOrNull()?.trim() ?: return@mapNotNull null
            // Extract timestamp from "── 2025-06-14 03:22:11 ──…"
            val ts = Regex("── (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})")
                .find(headerLine)?.groupValues?.get(1) ?: "Unknown time"
            // Thread line is "Thread: <name>"
            val thread = lines.firstOrNull { it.startsWith("Thread:") }
                ?.removePrefix("Thread:")?.trim() ?: "unknown"
            // First exception or "at " line as the summary
            val summary = lines
                .firstOrNull { it.contains("Exception") || it.contains("Error:") || it.startsWith("\tat ") }
                ?.trim()
                ?: lines.firstOrNull { it.isNotBlank() && !it.startsWith("──") && !it.startsWith("Thread:") }
                    ?.trim()
                ?: "No details"
            GamaCrashEntry(
                timestamp = ts,
                thread    = thread,
                summary   = summary.take(160),
                fullText  = block.trim()
            )
        }
        // Newest first (the file is already written newest-first, but sort to be safe)
        .sortedByDescending { it.timestamp }
}

// ── CrashLogPanel (list) ──────────────────────────────────────────────────────

@Composable
fun CrashLogPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    onExportCrashLog: (content: String, fileName: String) -> Unit = { _, _ -> }
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current

    var gamaCrashes by remember { mutableStateOf<List<GamaCrashEntry>>(emptyList()) }
    var rawLogExists by remember { mutableStateOf(false) }
    var systemCrashes by remember { mutableStateOf<List<ShizukuHelper.CrashEntry>>(emptyList()) }
    var systemCrashesLoading by remember { mutableStateOf(false) }

    // Which detail sub-panel is open: "gama:<index>" or "system:<index>" or null
    var openedEntryKey by remember { mutableStateOf<String?>(null) }

    // Re-read both sources every time the panel becomes visible
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        openedEntryKey = null

        // GAMA file log — read & parse on IO
        val rawText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val f = java.io.File(context.filesDir, "crash_log.txt")
                if (f.exists() && f.length() > 0) f.readText() else ""
            } catch (_: Exception) { "" }
        }
        rawLogExists = rawText.isNotEmpty()
        gamaCrashes = parseGamaCrashLog(rawText)

        // System dropbox crashes — only attempt if Shizuku is available
        if (ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()) {
            systemCrashesLoading = true
            systemCrashes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ShizukuHelper.fetchCrashLogs()
            }
            systemCrashesLoading = false
        }
    }

    // anyDetailOpen drives both the blur on the list panel AND the stagger
    // re-trigger: when it flips false the list cards see visible=true again
    // after having seen visible=false, so AnimatedElement re-runs its enter cascade.
    val anyDetailOpen = openedEntryKey != null

    // ── Detail panels (one per entry type) ───────────────────────────────────
    // Each detail panel gets its own BackHandler so the system back press only
    // closes the detail, never the whole crash log behind it.
    gamaCrashes.forEachIndexed { idx, entry ->
        val isThisOpen = openedEntryKey == "gama:$idx"
        BackHandler(enabled = isThisOpen) { openedEntryKey = null }
        CrashDetailPanel(
            visible       = isThisOpen,
            onDismiss     = { openedEntryKey = null },
            title         = entry.timestamp,
            subtitle      = "Thread: ${entry.thread}",
            fullText      = entry.fullText,
            isSystemUI    = false,
            isSmallScreen = isSmallScreen,
            isLandscape   = isLandscape,
            colors        = colors,
            cardBackground = cardBackground,
            oledMode      = oledMode,
            onSave        = { onExportCrashLog(entry.fullText, "GAMA_crash_${entry.timestamp.replace(" ", "_").replace(":", "-")}.txt") }
        )
    }
    systemCrashes.forEachIndexed { idx, entry ->
        val isThisOpen = openedEntryKey == "system:$idx"
        val fileTimestamp = remember(entry.timeMillis) {
            if (entry.timeMillis > 0L)
                java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                    .format(java.util.Date(entry.timeMillis))
            else "unknown"
        }
        val displayTimestamp = remember(entry.timeMillis) {
            if (entry.timeMillis > 0L)
                java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(entry.timeMillis))
            else "unknown time"
        }
        BackHandler(enabled = isThisOpen) { openedEntryKey = null }
        CrashDetailPanel(
            visible       = isThisOpen,
            onDismiss     = { openedEntryKey = null },
            title         = entry.tag,
            subtitle      = displayTimestamp,
            fullText      = entry.fullText,
            isSystemUI    = entry.isSystemUI,
            isSmallScreen = isSmallScreen,
            isLandscape   = isLandscape,
            colors        = colors,
            cardBackground = cardBackground,
            oledMode      = oledMode,
            onSave        = { onExportCrashLog(entry.fullText, "GAMA_system_crash_${entry.tag}_$fileTimestamp.txt") }
        )
    }

    // ── List panel ────────────────────────────────────────────────────────────
    // listItemsVisible flips false when a detail opens and back to true when it
    // closes — this is what makes AnimatedElement re-run its stagger cascade on
    // return, since LaunchedEffect(visible) only fires on value changes.
    val listItemsVisible = visible && !anyDetailOpen

    // Compute a stable total item count up-front so every AnimatedElement in
    // this panel shares the same value (required for the exit stagger to work).
    val gamaCount   = gamaCrashes.size
    val systemCount = if (!systemCrashesLoading && ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission())
        systemCrashes.size.coerceAtMost(20) else 0
    // Slots: 1 section header + gamaCount cards (or 1 empty card) + clear button
    //      + 1 section header + systemCount cards (or 1 status card)
    val emptyGamaSlots  = if (gamaCount == 0) 1 else gamaCount + 1   // cards + clear btn
    val systemSlots     = if (systemCount == 0) 1 else systemCount
    val totalListItems  = 1 + emptyGamaSlots + 1 + systemSlots        // two section headers

    PanelScaffold(
        visible   = visible,
        onDismiss = onDismiss,
        isLandscape   = isLandscape,
        isSmallScreen = isSmallScreen,
        isBlurred = anyDetailOpen,
        oledMode  = oledMode,
        colors    = colors
    ) { _ ->
        CleanTitle(text = "CRASH LOG", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        // ── GAMA crashes section header ───────────────────────────────────────
        AnimatedElement(visible = listItemsVisible, staggerIndex = 0, totalItems = totalListItems) {
            Text(
                text = "GAMA CRASHES",
                fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
        }

        if (gamaCount == 0) {
            AnimatedElement(visible = listItemsVisible, staggerIndex = 1, totalItems = totalListItems) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "No GAMA crashes recorded.",
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                        color = colors.textSecondary, fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            gamaCrashes.forEachIndexed { idx, entry ->
                // staggerIndex: header=0, cards start at 1
                AnimatedElement(visible = listItemsVisible, staggerIndex = idx + 1, totalItems = totalListItems) {
                    SettingsNavigationCard(
                        title = entry.timestamp,
                        description = entry.summary,
                        onClick = { openedEntryKey = "gama:$idx" },
                        isSmallScreen = isSmallScreen,
                        colors = colors,
                        cardBackground = cardBackground,
                        oledMode = oledMode
                    )
                }
            }
            // Clear button sits right after the last card
            AnimatedElement(visible = listItemsVisible, staggerIndex = gamaCount + 1, totalItems = totalListItems) {
                FlatButton(
                    text = "Clear All GAMA Logs",
                    onClick = {
                        try {
                            java.io.File(context.filesDir, "crash_log.txt").delete()
                            gamaCrashes = emptyList()
                            rawLogExists = false
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accent = false, colors = colors, maxLines = 1
                )
            }
        }

        // ── System crashes section header ─────────────────────────────────────
        // Its staggerIndex is always right after all the gama slots
        val systemHeaderIdx = 1 + emptyGamaSlots
        AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx, totalItems = totalListItems) {
            Text(
                text = "SYSTEM CRASHES",
                fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
        }

        when {
            systemCrashesLoading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryAccent, modifier = Modifier.size(28.dp))
                }
            }
            !ShizukuHelper.checkBinder() || !ShizukuHelper.checkPermission() -> {
                AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx + 1, totalItems = totalListItems) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "Shizuku required to read system crash logs.",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                            color = colors.textSecondary, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            systemCrashes.isEmpty() -> {
                AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx + 1, totalItems = totalListItems) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = "No relevant system crashes found.",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                            color = colors.textSecondary, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            else -> {
                systemCrashes.take(20).forEachIndexed { idx, entry ->
                    val displayTimestamp = remember(entry.timeMillis) {
                        if (entry.timeMillis > 0L)
                            java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date(entry.timeMillis))
                        else "unknown time"
                    }
                    AnimatedElement(
                        visible      = listItemsVisible,
                        staggerIndex = systemHeaderIdx + 1 + idx,
                        totalItems   = totalListItems
                    ) {
                        SettingsNavigationCard(
                            title = entry.tag,
                            description = "$displayTimestamp  ·  ${entry.summary}",
                            onClick = { openedEntryKey = "system:$idx" },
                            isSmallScreen = isSmallScreen,
                            colors = if (entry.isSystemUI)
                                colors.copy(border = colors.primaryAccent.copy(alpha = 0.4f))
                            else colors,
                            cardBackground = cardBackground,
                            oledMode = oledMode
                        )
                    }
                }
            }
        }
    }
}

// ── CrashDetailPanel (single-entry view) ─────────────────────────────────────

@Composable
private fun CrashDetailPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String,
    fullText: String,
    isSystemUI: Boolean,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    onSave: () -> Unit
) {
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible   = visible,
        onDismiss = onDismiss,
        isLandscape   = isLandscape,
        isSmallScreen = isSmallScreen,
        oledMode  = oledMode,
        colors    = colors
    ) { _ ->
        // Title: timestamp or tag
        CleanTitle(
            text     = title,
            fontSize = if (isLandscape) ts.displaySmall else ts.displayMedium,
            colors   = colors
        )

        // Subtitle: thread or formatted time
        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 3) {
            Text(
                text = subtitle,
                fontSize = ts.bodyMedium,
                fontFamily = quicksandFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isSystemUI) colors.primaryAccent.copy(alpha = 0.85f)
                        else colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        // Full log body in a scrollable card
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isSystemUI) colors.primaryAccent.copy(alpha = 0.4f) else colors.border,
                        shape = RoundedCornerShape(18.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = fullText,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    fontSize = ts.labelSmall,
                    fontFamily = quicksandFontFamily,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Save button — triggers SAF file picker via the parent callback
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 3) {
            FlatButton(
                text     = "Save Log",
                onClick  = onSave,
                modifier = Modifier.fillMaxWidth(),
                accent   = true,
                colors   = colors,
                maxLines = 1
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// DeveloperPanel
// ─────────────────────────────────────────────────────────────────────────────

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
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "DEVELOPER", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        Text(
            text = "A little playground for testing things — don't worry, nothing here is permanent",
            fontSize = ts.bodyMedium, color = colors.textSecondary,
            fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
        )

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 3) {
            FlatButton(
                text = "Send Test Notification", onClick = { performHaptic(); onTestNotification() },
                modifier = Modifier.fillMaxWidth(), accent = false, colors = colors, maxLines = 1
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 3) {
            FlatButton(
                text = "Send Boot Notification", onClick = { performHaptic(); onTestBootNotification() },
                modifier = Modifier.fillMaxWidth(), accent = false, colors = colors, maxLines = 1
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TIME OFFSET: ${if (timeOffsetHours >= 0) "+" else ""}${timeOffsetHours.toInt()}h",
                    fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent.copy(alpha = 0.7f)
                )
                Text(
                    text = "Shift the clock that time-mode particles use — lets you preview dawn, dusk, or midnight without waiting",
                    fontSize = ts.bodySmall, color = colors.textSecondary,
                    fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                )
                Slider(
                    value = timeOffsetHours, onValueChange = onTimeOffsetChange,
                    valueRange = -12f..12f, steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primaryAccent,
                        activeTrackColor = colors.primaryAccent,
                        inactiveTrackColor = colors.primaryAccent.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ResourcesPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResourcesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLinkSelected: (url: String, label: String, description: String) -> Unit,
    onInfoRequested: (title: String, body: String) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    isBlurred: Boolean,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current

    data class Link(val title: String, val desc: String, val url: String, val linkDesc: String)
    val links = listOf(
        Link("GITHUB", "Browse the source code, report issues, or grab the latest release",
            "https://github.com/popovicialinc/gama",
            "Opens the GAMA GitHub repository where you can browse source code, report issues, and download releases."),
        Link("DISCORD", "Join the official GAMA community server",
            "https://discord.gg/YYXSedBAS9",
            "Opens the official GAMA Discord server — ask questions, share feedback, and stay up to date."),
        Link("SHIZUKU", "GAMA needs this to run — grab it here if you haven't already",
            "https://shizuku.rikka.app",
            "Opens the official Shizuku website. Shizuku is required for GAMA to execute renderer switching commands.")
    )
    // 3 link cards + 3 integration cards = 6 total
    val totalItems = links.size + 3

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        isBlurred = isBlurred, oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = "RESOURCES",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        // ── Links ─────────────────────────────────────────────────────────────
        links.forEachIndexed { i, link ->
            AnimatedElement(visible = visible, staggerIndex = i + 1, totalItems = totalItems) {
                SettingsNavigationCard(
                    title = link.title, description = link.desc,
                    onClick = { onLinkSelected(link.url, link.title, link.linkDesc) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        }

        // ── Integrations ──────────────────────────────────────────────────────
        AnimatedElement(visible = visible, staggerIndex = links.size + 1, totalItems = totalItems) {
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
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
        }

        AnimatedElement(visible = visible, staggerIndex = links.size + 2, totalItems = totalItems) {
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
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
        }

        AnimatedElement(visible = visible, staggerIndex = links.size + 3, totalItems = totalItems) {
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
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// VerbosePanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VerbosePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    verboseOutput: String,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    blurEnabled: Boolean,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = false, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "VERBOSE OUTPUT", fontSize = ts.displaySmall, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(18.dp)
            ) {
                val innerScroll = rememberScrollState(Int.MAX_VALUE)
                Text(
                    text = verboseOutput.ifEmpty { "No output yet. Run a renderer switch to see verbose logs." },
                    modifier = Modifier.fillMaxWidth().verticalScroll(innerScroll).padding(20.dp),
                    fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                    color = colors.textSecondary, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


