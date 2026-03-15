package com.popovicialinc.gama

import android.os.Build
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
    onVisualEffectsClick: () -> Unit,
    onFunctionalityClick: () -> Unit,
    onIntegrationsClick: () -> Unit,
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

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 2) {
            SettingsNavigationCard(
                title = "VISUALS",
                description = "Make it yours — tweak how GAMA looks and feels",
                onClick = { performHaptic(); onVisualEffectsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 2) {
            SettingsNavigationCard(
                title = "FUNCTIONALITY",
                description = "Control how GAMA switches renderers and behaves",
                onClick = { performHaptic(); onFunctionalityClick() },
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
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = "VISUALS",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        // Effects nav — FIRST at the top of Visuals
        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 6) {
            SettingsNavigationCard(
                title = "EFFECTS",
                description = "Frosted glass blur, animated gradient, and floating particles",
                onClick = { performHaptic(); onEffectsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }

        // Theme
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 6) {
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
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 6) {
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
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 6) {
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

        // OLED toggle
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 6) {
            ToggleCard(
                title = "OLED MODE",
                description = "Turns backgrounds pure black — great for battery life on OLED screens",
                checked = oledMode,
                onCheckedChange = { performHaptic(); onOledModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        // Colors nav
        AnimatedElement(visible = visible, staggerIndex = 6, totalItems = 6) {
            SettingsNavigationCard(
                title = "COLORS",
                description = "Pick your accent color and customize the background gradient",
                onClick = { performHaptic(); onColorCustomizationClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
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
                                "Optimised — blur is pre-rendered and faded in with transparency. Fast, smooth, recommended."
                            else
                                "Real — the blur radius is animated from 0 to full strength. More accurate but heavier on the GPU.",
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

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 6) {
            ToggleCard(
                title = "PARTICLES", description = "Small dots drift around in the background — gives the app a living feel",
                checked = particlesEnabled, onCheckedChange = { performHaptic(); onParticlesChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 6) {
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
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 6) {
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
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 6) {
            ToggleCard(
                title = "TIME MODE", description = "A sun and moon move across the sky based on the actual time of day",
                checked = particleTimeMode,
                onCheckedChange = { performHaptic(); onParticleTimeModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 6) {
            ToggleCard(
                title = "STAR MODE", description = "Swaps regular particles for tiny twinkling stars — looks great at night",
                checked = particleStarMode,
                onCheckedChange = { performHaptic(); onParticleStarModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled && !particleTimeMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 6, totalItems = 6) {
            ToggleCard(
                title = "PARALLAX", description = "Tilt your phone and the particles shift — adds a cool 3D depth effect",
                checked = particleParallaxEnabled,
                onCheckedChange = { performHaptic(); onParticleParallaxChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled
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
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "COLORS", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 4) {
            ToggleCard(
                title = "DYNAMIC COLOR",
                description = if (dynamicColorAvailable) "Pulls accent colors from your wallpaper — changes automatically with Material You" else "Requires Android 12 or newer",
                checked = useDynamicColor && dynamicColorAvailable,
                onCheckedChange = { performHaptic(); onDynamicColorChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 4) {
            ToggleCard(
                title = "ADVANCED COLOR PICKER",
                description = "Adds a hex input field so you can type any color directly — e.g. #4895EF",
                checked = advancedColorPicker,
                onCheckedChange = { performHaptic(); onAdvancedColorPickerChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = !useDynamicColor || !dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 4) {
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
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 4) {
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
// FunctionalityPanel
// ─────────────────────────────────────────────────────────────────────────────

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
    dozeMode: Boolean,
    onDozeModeChange: (Boolean) -> Unit,
    onAppSelectorClick: () -> Unit,
    onShowAggressiveWarning: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBackupClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    userName: String,
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
        CleanTitle(text = "FUNCTIONALITY", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 7) {
            ToggleCard(
                title = "VERBOSE MODE", description = "Shows the full shell output when switching — handy for debugging",
                checked = verboseMode, onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 2, totalItems = 7) {
            ToggleCard(
                title = "AGGRESSIVE MODE", description = "Applies the renderer to every single installed package — more thorough, but read the warning first",
                checked = aggressiveMode, onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 3, totalItems = 7) {
            ToggleCard(
                title = "TAP OUTSIDE TO CLOSE", description = "Tap anywhere outside a panel to dismiss it — or turn this off if you prefer the back button",
                checked = dismissOnClickOutside, onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 4, totalItems = 7) {
            ToggleCard(
                title = "DOZE", description = "Forces Android into deep doze immediately — saves battery the moment you put the phone down",
                checked = dozeMode, onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 5, totalItems = 7) {
            SettingsNavigationCard(
                title = "NOTIFICATIONS", description = "Get a nudge if you've been on OpenGL for a while and forget to switch back",
                onClick = { performHaptic(); onNotificationsClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 6, totalItems = 7) {
            SettingsNavigationCard(
                title = "BACKUP & RESTORE", description = "Save all your settings to a file or bring them back from a backup",
                onClick = { performHaptic(); onBackupClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, staggerIndex = 7, totalItems = 7) {
            SettingsNavigationCard(
                title = "CRASH LOG", description = "Something went wrong? Check here for clues on what happened",
                onClick = { performHaptic(); onCrashLogClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
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

@Composable
fun CrashLogPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val context = LocalContext.current
    val logText = remember {
        try {
            val f = java.io.File(context.filesDir, "crash_log.txt")
            if (f.exists()) f.readText() else "No crash logs recorded."
        } catch (e: Exception) { "Unable to read crash log: ${e.message}" }
    }

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = "CRASH LOG", fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, staggerIndex = 1, totalItems = 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = logText,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                    color = colors.textSecondary, fontWeight = FontWeight.Bold
                )
            }
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
            "https://discord.gg/9FRQEFR7eE",
            "Opens the official GAMA Discord server — ask questions, share feedback, and stay up to date."),
        Link("SHIZUKU", "GAMA needs this to run — grab it here if you haven't already",
            "https://shizuku.rikka.app",
            "Opens the official Shizuku website. Shizuku is required for GAMA to execute renderer switching commands."),
        Link("TASKER GUIDE", "Step-by-step guide to automating renderer switches with Tasker",
            "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
            "Opens the GAMA Tasker guide — covers automating renderer switching based on time, app launch, WiFi, and more.")
    )

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
        links.forEachIndexed { i, link ->
            AnimatedElement(visible = visible, staggerIndex = i + 1, totalItems = links.size) {
                SettingsNavigationCard(
                    title = link.title, description = link.desc,
                    onClick = { onLinkSelected(link.url, link.title, link.linkDesc) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
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
                    .heightIn(min = 200.dp)
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


// ─────────────────────────────────────────────────────────────────────────────
// AppSelectorPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppSelectorPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    excludedApps: List<String>,
    onExcludedAppsChange: () -> Unit,
    isSmallScreen: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    blurEnabled: Boolean,
    performHaptic: () -> Unit,
    oledMode: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    preloadedApps: List<Pair<String, String>>,
    isPreloading: Boolean
) {
    val ts = LocalTypeScale.current
    val dismissOnClickOutside = LocalDismissOnClickOutside.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(preloadedApps, searchQuery) {
        if (searchQuery.isBlank()) preloadedApps
        else preloadedApps.filter { (pkg, label) ->
            label.contains(searchQuery, ignoreCase = true) || pkg.contains(searchQuery, ignoreCase = true)
        }
    }

    BouncyDialog(visible = visible, onDismiss = onDismiss, fullScreen = true) {
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
                    .fillMaxHeight()
                    .padding(horizontal = if (isLandscape) 32.dp else 24.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))

                CleanTitle(
                    text = "APP SELECTOR",
                    fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
                    colors = colors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Apps you exclude here won't have their renderer touched — useful for anything that doesn't play nice with Vulkan",
                    fontSize = ts.bodyMedium, color = colors.textSecondary,
                    fontFamily = quicksandFontFamily, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search apps…", fontFamily = quicksandFontFamily,
                            fontWeight = FontWeight.Bold, color = colors.textSecondary.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.primaryAccent,
                        focusedContainerColor = cardBackground,
                        unfocusedContainerColor = cardBackground
                    ),
                    textStyle = TextStyle(
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = ts.bodyMedium
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isPreloading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps, key = { it.first }) { (pkg, label) ->
                            val isExcluded = excludedApps.contains(pkg)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isExcluded) colors.primaryAccent.copy(alpha = 0.5f)
                                        else colors.border.copy(alpha = 0.3f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        performHaptic()
                                        if (isExcluded) (excludedApps as? MutableList)?.remove(pkg)
                                        else (excludedApps as? MutableList)?.add(pkg)
                                        onExcludedAppsChange()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExcluded)
                                        colors.primaryAccent.copy(alpha = 0.1f) else cardBackground
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                                            fontFamily = quicksandFontFamily,
                                            color = if (isExcluded) colors.primaryAccent else colors.textPrimary
                                        )
                                        Text(
                                            text = pkg, fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                                            fontWeight = FontWeight.Bold, color = colors.textSecondary
                                        )
                                    }
                                    if (isExcluded) {
                                        Text(
                                            text = "EXCLUDED", fontSize = ts.labelSmall, fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp, fontFamily = quicksandFontFamily,
                                            color = colors.primaryAccent
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }

            PanelBackButton(
                onClick = onDismiss, colors = colors, oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
            )
        }
    }
}