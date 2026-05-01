package com.popovicialinc.gama

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    leadingFloatingButton: (@Composable (Modifier) -> Unit)? = null,
    content: @Composable ColumnScope.(scrollState: ScrollState) -> Unit
) {
    val dismissOnClickOutside = LocalDismissOnClickOutside.current
    val scrollState = rememberScrollState()
    LaunchedEffect(visible) { if (visible) scrollState.scrollTo(0) }
    val animLevel = LocalAnimationLevel.current

    // Back button slide-in. Use offset instead of graphicsLayer translation so the
    // visual position and the touch hitbox always move together.
    val backButtonOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 420.dp,
        animationSpec = if (animLevel == 2) snap() else spring(
            dampingRatio = 0.62f,
            stiffness = 430f
        ),
        label = "panel_back_ty"
    )

    // Static bottom padding — reserves space for the floating back button so the
    // last card is never obscured.  Previously this was an animated spring, but
    // animating a .padding() value triggers a full layout pass every frame during
    // the ~300 ms panel-enter transition, causing the visible stutter/freeze.
    // The back button already animates in via graphicsLayer translationY (draw-only,
    // zero layout cost), so the padding just needs to be the correct resting size
    // from the first frame — no animation required.
    val bottomPaddingDp = if (isSmallScreen) 120f else 132f  // dp: button height + gap + clearance
    // When a sub-dialog opens (isBlurred = true) the panel dims so the dialog feels
    // on a higher layer. The blur itself snaps on/off (no animated radius — see below).
    //
    // panelAlpha  — 1→0.35 dims the whole panel as the sub-dialog arrives
    val panelAlpha by animateFloatAsState(
        targetValue = if (isBlurred) 0.38f else 1f,
        animationSpec = if (animLevel == 2) snap() else tween(
            durationMillis = if (isBlurred) 300 else 190,
            easing = if (isBlurred) MotionTokens.Easing.emphasizedDecelerate else MotionTokens.Easing.enter
        ),
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

        // Snap blur radius — never animate it per-frame.
        // Previously `(blurAlpha * 20f).dp` caused a new RenderEffect (Gaussian kernel
        // rebuild) every vsync during the ~300 ms transition: ~18 kernel rebuilds back-to-back.
        // Fixed radius set once when isBlurred becomes true → zero per-frame GPU rebuild cost.
        // The panelAlpha fade still provides smooth visual feedback of the transition.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = panelAlpha }
                .then(
                    if (useBlur && isBlurred)
                        Modifier.blur(
                            radius = 20.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                    else Modifier
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Background tap layer. It sits behind the cards/back button, so it
                // keeps outside-tap dismissal without stealing child clicks.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dismissOnClickOutside) {
                            if (dismissOnClickOutside) detectTapGestures { onDismiss() }
                            else detectTapGestures { }
                        }
                )

                val backButtonSize = if (isSmallScreen) 48.dp else 52.dp
                val horizontalPadding = if (isLandscape) 32.dp else 24.dp

                CompositionLocalProvider(
                    LocalFloatingBackButtonAvoidance provides FloatingBackButtonAvoidance(
                        enabled = visible && !isLandscape,
                        endPadding = backButtonSize + 32.dp,
                        bottomPadding = if (isSmallScreen) 44.dp else 52.dp,
                        buttonSize = backButtonSize
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = if (isLandscape) 800.dp else 500.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = horizontalPadding)
                            .padding(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                                bottom = bottomPaddingDp.dp
                            )
                            .pointerInput(Unit) { detectTapGestures { } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 16.dp else 20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 40.dp))
                        content(scrollState)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                leadingFloatingButton?.invoke(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = if (isSmallScreen) 18.dp else 24.dp)
                        .offset(y = backButtonOffsetY)
                )

                PanelBackButton(
                    onClick = onDismiss,
                    colors = colors,
                    oledMode = oledMode,
                    isSmallScreen = isSmallScreen,
                    enabled = visible,
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = if (isSmallScreen) 18.dp else 24.dp)
                        .offset(y = backButtonOffsetY)
                )
            }
        }
    }
}



@Composable
private fun PanelCaption(
    text: String,
    colors: ThemeColors,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val ts = LocalTypeScale.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colors.background.copy(alpha = 0.18f),
                        colors.background.copy(alpha = 0.26f),
                        colors.background.copy(alpha = 0.18f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 30.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = ts.bodySmall,
            color = if (accent) colors.primaryAccent.copy(alpha = 0.70f)
                    else colors.textPrimary.copy(alpha = 0.62f),
            fontFamily = quicksandFontFamily,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 19.sp
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// SettingsPanel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSearchClick: () -> Unit,
    onAppearanceClick: () -> Unit,
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
    val strings = LocalStrings.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors,
        leadingFloatingButton = { floatingModifier ->
            PanelSearchButton(
                onClick = { performHaptic(); onSearchClick() },
                colors = colors,
                oledMode = oledMode,
                isSmallScreen = isSmallScreen,
                enabled = visible,
                modifier = floatingModifier
            )
        }
    ) { scrollState ->
        CleanTitle(
            text = strings["settings.title"].ifEmpty { "SETTINGS" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        PanelCaption(
            text = "Core app sections. Use search to jump directly to toggles, sliders, and selectors.",
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3) {
            SettingsNavigationCard(
                title = strings["settings.appearance"].ifEmpty { "VISUALS" },
                description = strings["settings.appearance_desc"].ifEmpty { "Colors, theme, effects, animations, and interface scale" },
                onClick = { performHaptic(); onAppearanceClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3) {
            SettingsNavigationCard(
                title = strings["settings.renderer"].ifEmpty { "RENDERER" },
                description = strings["settings.renderer_desc"].ifEmpty { "Switching engine, aggressive mode, doze, and launcher behavior" },
                onClick = { performHaptic(); onRendererClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3) {
            SettingsNavigationCard(
                title = strings["settings.system"].ifEmpty { "APP" },
                description = strings["settings.system_desc"].ifEmpty { "Notifications, backup, language, integrations, and crash logs" },
                onClick = { performHaptic(); onSystemClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}



private class SettingsSearchItem(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val path: String,         // breadcrumb path shown above the card, outside the shadow
    val render: @Composable () -> Unit
) {
    // Pre-computed once at construction — never rebuilt during scoring.
    val haystack: String = (listOf(id, title) + keywords).joinToString(" ").lowercase()
    val words: List<String> = haystack.split(nonAlphaNumRegex).filter { it.isNotBlank() }

    companion object {
        private val nonAlphaNumRegex = Regex("[^a-z0-9]+")
    }
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val previous = IntArray(b.length + 1) { it }
    val current = IntArray(b.length + 1)

    for (i in a.indices) {
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }
        for (j in previous.indices) previous[j] = current[j]
    }

    return previous[b.length]
}

// queryTokens split is done once per query in the caller, not per-item here.
private fun settingsSearchScore(q: String, queryTokens: List<String>, item: SettingsSearchItem): Int {
    if (q.isBlank()) return 0

    if (item.haystack.contains(q)) return 120

    var best = 0
    for (token in queryTokens) {
        for (word in item.words) {
            if (word == token) { best = maxOf(best, 115); continue }
            if (word.startsWith(token)) best = maxOf(best, 96)
            else if (word.contains(token)) best = maxOf(best, 82)

            if (token.length >= 3 && word.length >= 3 && word[0] == token[0] && word[1] == token[1] && word[2] == token[2]) {
                best = maxOf(best, 72)
            } else if (token.length >= 2 && word.length >= 2 && word[0] == token[0] && word[1] == token[1]) {
                best = maxOf(best, 54)
            }

            val distance = levenshteinDistance(token, word)
            val maxLen = maxOf(token.length, word.length).coerceAtLeast(1)
            best = maxOf(best, ((1f - distance.toFloat() / maxLen) * 82).toInt())
        }
    }

    return best
}

@Composable
private fun SearchInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: ThemeColors,
    cardBackground: Color,
    isSmallScreen: Boolean
) {
    val ts = LocalTypeScale.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(cardBackground)
            .padding(horizontal = if (isSmallScreen) 18.dp else 22.dp, vertical = if (isSmallScreen) 14.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Canvas(modifier = Modifier.size(if (isSmallScreen) 21.dp else 24.dp)) {
                val strokeWidth = 2.2.dp.toPx()
                val radius = size.minDimension * 0.29f
                val center = Offset(size.width * 0.43f, size.height * 0.43f)
                drawCircle(
                    color = colors.primaryAccent.copy(alpha = 0.85f),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawLine(
                    color = colors.primaryAccent.copy(alpha = 0.85f),
                    start = Offset(center.x + radius * 0.70f, center.y + radius * 0.70f),
                    end = Offset(size.width * 0.80f, size.height * 0.80f),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = ts.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = quicksandFontFamily
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = SolidColor(colors.primaryAccent),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (query.isBlank()) {
                            Text(
                                text = "Search settings, toggles, sliders...",
                                color = colors.textSecondary.copy(alpha = 0.68f),
                                fontSize = ts.bodyLarge,
                                fontFamily = quicksandFontFamily,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchSelectorCard(
    title: String,
    description: String,
    colors: ThemeColors,
    cardBackground: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val ts = LocalTypeScale.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(cardBackground)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.primaryAccent)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent.copy(alpha = 0.7f)
                    )
                    Text(
                        text = description,
                        fontSize = ts.bodySmall,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsSearchPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    // ── VISUALS / Appearance ──────────────────────────────────────────────────
    themePreference: Int,
    onThemeChange: (Int) -> Unit,
    animationLevel: Int,
    onAnimationLevelChange: (Int) -> Unit,
    uiScale: Int,
    onUiScaleChange: (Int) -> Unit,
    staggerEnabled: Boolean,
    onStaggerEnabledChange: (Boolean) -> Unit,
    shadowsEnabled: Boolean,
    onShadowsEnabledChange: (Boolean) -> Unit,
    // ── Colors ────────────────────────────────────────────────────────────────
    oledMode: Boolean,
    darkModeActive: Boolean,
    onOledModeChange: (Boolean) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    advancedColorPicker: Boolean,
    onAdvancedColorPickerChange: (Boolean) -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    // ── Effects ───────────────────────────────────────────────────────────────
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
    // ── Particles ─────────────────────────────────────────────────────────────
    particlesEnabled: Boolean,
    onParticlesChange: (Boolean) -> Unit,
    matrixMode: Boolean,
    onMatrixModeChange: (Boolean) -> Unit,
    particleStarMode: Boolean,
    onParticleStarModeChange: (Boolean) -> Unit,
    particleTimeMode: Boolean,
    onParticleTimeModeChange: (Boolean) -> Unit,
    particleParallaxEnabled: Boolean,
    onParticleParallaxEnabledChange: (Boolean) -> Unit,
    particleParallaxSensitivity: Int,
    onParticleParallaxSensitivityChange: (Int) -> Unit,
    particleCount: Int,
    onParticleCountChange: (Int) -> Unit,
    particleSpeed: Int,
    onParticleSpeedChange: (Int) -> Unit,
    nativeRefreshRate: Boolean,
    onNativeRefreshRateChange: (Boolean) -> Unit,
    quarterRefreshRate: Boolean,
    onQuarterRefreshRateChange: (Boolean) -> Unit,
    matrixSpeed: Int,
    onMatrixSpeedChange: (Int) -> Unit,
    matrixDensity: Int,
    onMatrixDensityChange: (Int) -> Unit,
    matrixFontSize: Int,
    onMatrixFontSizeChange: (Int) -> Unit,
    matrixFadeLength: Int,
    onMatrixFadeLengthChange: (Int) -> Unit,
    // ── Renderer ──────────────────────────────────────────────────────────────
    aggressiveMode: Boolean,
    onAggressiveModeChange: (Boolean) -> Unit,
    killLauncher: Boolean,
    onKillLauncherChange: (Boolean) -> Unit,
    killKeyboard: Boolean,
    onKillKeyboardChange: (Boolean) -> Unit,
    dozeMode: Boolean,
    onDozeModeChange: (Boolean) -> Unit,
    showGpuWatchButton: Boolean,
    onShowGpuWatchButtonChange: (Boolean) -> Unit,
    // ── System / App ──────────────────────────────────────────────────────────
    verboseMode: Boolean,
    onVerboseModeChange: (Boolean) -> Unit,
    dismissOnClickOutside: Boolean,
    onDismissOnClickOutsideChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notifIntervalIndex: Int,
    onNotifIntervalChange: (Int) -> Unit,
    // ── Common ────────────────────────────────────────────────────────────────
    oledModeLocked: Boolean = false,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    var query by remember { mutableStateOf("") }
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val gradientAvailable = !darkModeActive

    // ── Helper: a breadcrumb pill shown above each result ─────────────────────
    @Composable
    fun PathLabel(path: String) {
        val segments = path.split("→").map { it.trim() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .padding(start = 2.dp, bottom = 6.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, colors.primaryAccent.copy(alpha = 0.28f), RoundedCornerShape(50))
                .background(colors.primaryAccent.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            segments.forEachIndexed { index, segment ->
                Text(
                    text = segment,
                    fontSize = ts.bodySmall,
                    color = colors.primaryAccent.copy(alpha = if (index == segments.lastIndex) 0.85f else 0.5f),
                    fontFamily = quicksandFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (index < segments.lastIndex) {
                    Text(
                        text = "  ›  ",
                        fontSize = ts.bodySmall,
                        color = colors.primaryAccent.copy(alpha = 0.35f),
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                }
            }
        }
    }

    // The render lambdas capture state by closure reference — they always see
    // fresh values at composition time without being in the remember key.
    // Only id/title/keywords need to be stable for scoring, and those are constants.
    // Wrapping in remember{} prevents ~35 lambda allocations on every recomposition.
    val items = remember {
    listOf(
        // ── VISUALS ───────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "theme",
            title = "THEME",
            keywords = listOf("theme", "mode", "auto", "dark", "light", "appearance", "visuals", "oled", "color mode", "them", "thme", "teme", "night"),
            path = "VISUALS"
        ) {
            Column {
            SearchSelectorCard(title = "THEME", description = if (oledMode) "Forced to Dark while OLED mode is on." else "Choose how GAMA follows light, dark, or system appearance.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Auto", "Dark", "Light"), selectedIndex = if (oledMode) 1 else themePreference,
                    onOptionSelected = { if (!oledMode) { performHaptic(); onThemeChange(it) } }, colors = colors, modifier = Modifier.fillMaxWidth(), enabled = !oledMode)
            } }
        },
        SettingsSearchItem(
            id = "animations",
            title = "ANIMATIONS",
            keywords = listOf("animations", "animation", "motion", "movement", "reduce motion", "off", "full", "anim", "smooth", "transition", "reduced"),
            path = "VISUALS"
        ) {
            Column {
            SearchSelectorCard(title = "ANIMATIONS", description = "Control how much motion the interface uses.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Full", "Reduced", "Off"), selectedIndex = animationLevel,
                    onOptionSelected = { performHaptic(); onAnimationLevelChange(it) }, colors = colors, modifier = Modifier.fillMaxWidth())
            } }
        },
        SettingsSearchItem(
            id = "ui_scale",
            title = "UI SCALE",
            keywords = listOf("ui", "scale", "size", "interface size", "zoom", "75", "100", "125", "text size", "font size", "big", "small", "large"),
            path = "VISUALS"
        ) {
            Column {
            SearchSelectorCard(title = "UI SCALE", description = "Adjust the size of the interface.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("75%", "100%", "125%"), selectedIndex = uiScale,
                    onOptionSelected = { performHaptic(); onUiScaleChange(it) }, colors = colors, modifier = Modifier.fillMaxWidth())
            } }
        },
        SettingsSearchItem(
            id = "stagger_animations",
            title = "STAGGER ANIMATIONS",
            keywords = listOf("stagger", "stag", "stager", "staggered", "cards", "cascade", "panel cards", "entrance", "animations", "motion", "one by one", "instant"),
            path = "VISUALS"
        ) {
            Column {
            ToggleCard(title = "STAGGER ANIMATIONS", description = "Panel cards animate in one by one — turn off for instant panel opens.",
                checked = staggerEnabled, onCheckedChange = { performHaptic(); onStaggerEnabledChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "card_shadows",
            title = "CARD SHADOWS",
            keywords = listOf("card", "cards", "shadow", "shadows", "drop shadow", "gpu", "performance", "visual", "depth", "elevation"),
            path = "VISUALS"
        ) {
            Column {
            ToggleCard(title = "CARD SHADOWS", description = "Drop shadows under cards — disable to reduce GPU load or fix visual glitches during animations.",
                checked = shadowsEnabled, onCheckedChange = { performHaptic(); onShadowsEnabledChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        // ── COLORS ────────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "oled_mode",
            title = "OLED MODE",
            keywords = listOf("oled", "amoled", "black", "battery", "pure black", "true black", "power", "screen", "dark"),
            path = "VISUALS → COLORS"
        ) {
            Column {
            ToggleCard(title = "OLED MODE", description = if (oledMode) "Active — all backgrounds are pure black" else "Off — using your regular theme background color",
                checked = oledMode, onCheckedChange = { performHaptic(); onOledModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "dynamic_color",
            title = "DYNAMIC COLOR",
            keywords = listOf("dynamic", "material you", "wallpaper", "accent", "monet", "android 12", "auto color", "system color", "automatic"),
            path = "VISUALS → COLORS"
        ) {
            Column {
            ToggleCard(title = "DYNAMIC COLOR",
                description = if (dynamicColorAvailable) "Picks accent colors from your wallpaper automatically via Material You" else "Requires Android 12 or newer",
                checked = useDynamicColor && dynamicColorAvailable,
                onCheckedChange = { performHaptic(); onDynamicColorChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = dynamicColorAvailable) }
        },
        SettingsSearchItem(
            id = "advanced_color_picker",
            title = "ADVANCED COLOR PICKER",
            keywords = listOf("advanced", "hex", "color picker", "hex input", "custom color", "type color", "#", "picker", "colour"),
            path = "VISUALS → COLORS"
        ) {
            Column {
            ToggleCard(title = "ADVANCED COLOR PICKER",
                description = "Adds a hex input field to the color pickers — type any color directly, e.g. #4895EF",
                checked = advancedColorPicker,
                onCheckedChange = { performHaptic(); onAdvancedColorPickerChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = !useDynamicColor || !dynamicColorAvailable) }
        },
        SettingsSearchItem(
            id = "gradient_background",
            title = "GRADIENT BACKGROUND",
            keywords = listOf("gradient", "background", "gradient background", "color shift", "wallpaper color", "shifting", "background color", "fade"),
            path = "VISUALS → COLORS → BACKGROUND GRADIENT"
        ) {
            Column {
            val gradientCardScale by animateFloatAsState(
                targetValue = if (gradientAvailable) 1f else 0.92f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                    stiffness = MotionTokens.Springs.smooth.stiffness
                ),
                label = "search_gradient_card_dark_scale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = gradientCardScale,
                        scaleY = gradientCardScale,
                        alpha = if (gradientAvailable) 1f else 0.42f
                    )
            ) {
                ToggleCard(title = "GRADIENT BACKGROUND",
                    description = when {
                        oledMode -> "Unavailable in OLED mode — background is pure black"
                        darkModeActive -> "Unavailable in Dark mode — background is pure black"
                        else -> "A slow-shifting color gradient behind your home screen"
                    },
                    checked = gradientEnabled && gradientAvailable,
                    onCheckedChange = { if (gradientAvailable) { performHaptic(); onGradientChange(it) } },
                    colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                    oledMode = oledMode, enabled = gradientAvailable)
            } }
        },
        // ── EFFECTS ───────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "blur",
            title = "BLUR",
            keywords = listOf("blur", "frosted", "glass", "frosted glass", "panel blur", "backdrop", "depth", "premium", "translucent", "blured"),
            path = "VISUALS → EFFECTS"
        ) {
            Column {
            ToggleCard(title = "BLUR",
                description = "Frosted glass behind panels and dialogs — subtle depth that makes the UI feel premium",
                checked = blurEnabled, onCheckedChange = { performHaptic(); onBlurChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        // ── PARTICLES ─────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "particles",
            title = "PARTICLES",
            keywords = listOf("particles", "stars", "floating", "dots", "particle", "animation", "background animation", "star", "sparkle", "living", "feel"),
            path = "VISUALS → EFFECTS → PARTICLES"
        ) {
            Column {
            ToggleCard(title = "PARTICLES",
                description = "Animates the background with floating particles or Matrix rain",
                checked = particlesEnabled, onCheckedChange = { performHaptic(); onParticlesChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "particle_style",
            title = "PARTICLE STYLE",
            keywords = listOf("style", "stars", "matrix", "rain", "matrix rain", "digital rain", "glyphs", "particle style", "mode"),
            path = "VISUALS → EFFECTS → PARTICLES"
        ) {
            Column {
            SearchSelectorCard(title = "PARTICLE STYLE",
                description = if (matrixMode) "Cascading columns of glyphs — the classic Matrix digital rain effect" else "Twinkling stars that float and shift with device tilt via parallax",
                colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Stars", "Matrix"), selectedIndex = if (matrixMode) 1 else 0,
                    onOptionSelected = { performHaptic(); if (particlesEnabled) onMatrixModeChange(it == 1) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled)
            } }
        },
        SettingsSearchItem(
            id = "star_mode",
            title = "STAR MODE",
            keywords = listOf("star", "star mode", "stars", "shape", "look", "glow", "twinkle", "sparkle", "star shape"),
            path = "VISUALS → EFFECTS → PARTICLES → SHAPE & LOOK"
        ) {
            Column {
            ToggleCard(title = "STAR MODE",
                description = "Renders particles as glowing 5-pointed stars instead of soft dots",
                checked = particleStarMode, onCheckedChange = { performHaptic(); onParticleStarModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled && !matrixMode) }
        },
        SettingsSearchItem(
            id = "time_mode",
            title = "TIME MODE",
            keywords = listOf("time", "time mode", "sun", "moon", "day", "night", "sky", "real time", "clock", "daytime", "sunrise", "sunset"),
            path = "VISUALS → EFFECTS → PARTICLES → SHAPE & LOOK"
        ) {
            Column {
            ToggleCard(title = "TIME MODE",
                description = "A sun and moon travel across the sky in sync with the real time of day",
                checked = particleTimeMode, onCheckedChange = { performHaptic(); onParticleTimeModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled && !matrixMode) }
        },
        SettingsSearchItem(
            id = "parallax",
            title = "PARALLAX",
            keywords = listOf("parallax", "tilt", "gyro", "gyroscope", "motion", "sensor", "depth", "3d", "perspective"),
            path = "VISUALS → EFFECTS → PARTICLES → MOTION"
        ) {
            Column {
            ToggleCard(title = "PARALLAX",
                description = "Particles shift with device tilt via the gyroscope for a depth effect",
                checked = particleParallaxEnabled, onCheckedChange = { performHaptic(); onParticleParallaxEnabledChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = particlesEnabled && !matrixMode) }
        },
        SettingsSearchItem(
            id = "parallax_sensitivity",
            title = "PARALLAX SENSITIVITY",
            keywords = listOf("parallax", "sensitivity", "tilt", "gyro", "strength", "intensity", "low", "medium", "high", "motion"),
            path = "VISUALS → EFFECTS → PARTICLES → MOTION"
        ) {
            Column {
            SearchSelectorCard(title = "PARALLAX SENSITIVITY", description = "How strongly the particles react to device tilt.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Low", "Medium", "High"), selectedIndex = particleParallaxSensitivity.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onParticleParallaxSensitivityChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && particleParallaxEnabled && !matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "particle_speed",
            title = "PARTICLE SPEED",
            keywords = listOf("speed", "fast", "slow", "velocity", "float speed", "particle speed", "drift"),
            path = "VISUALS → EFFECTS → PARTICLES → MOTION"
        ) {
            Column {
            SearchSelectorCard(title = "PARTICLE SPEED", description = "How fast particles drift across the screen.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Low", "Medium", "High"), selectedIndex = particleSpeed.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onParticleSpeedChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && !matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "particle_count",
            title = "PARTICLE COUNT",
            keywords = listOf("count", "number", "amount", "density", "particles", "how many", "more", "less", "performance", "75", "150", "300"),
            path = "VISUALS → EFFECTS → PARTICLES → PERFORMANCE"
        ) {
            Column {
            SearchSelectorCard(title = "PARTICLE COUNT", description = "Low = 75 · Medium = 150 · High = 300", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Low", "Medium", "High"), selectedIndex = particleCount.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onParticleCountChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && !matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "particle_refresh_rate",
            title = "PARTICLE REFRESH RATE",
            keywords = listOf("refresh", "refresh rate", "fps", "frame", "native", "performance", "battery", "poll", "sensor"),
            path = "VISUALS → EFFECTS → PARTICLES → PERFORMANCE"
        ) {
            Column {
            val refreshOption = when { nativeRefreshRate -> 0; quarterRefreshRate -> 2; else -> 1 }
            SearchSelectorCard(title = "PARTICLE REFRESH RATE", description = "Controls how often particles update. Native = every frame, 1/2 = every other, 1/4 = every fourth.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Native", "1/2", "1/4"), selectedIndex = refreshOption,
                    onOptionSelected = { opt -> performHaptic()
                        when (opt) { 0 -> { onNativeRefreshRateChange(true); onQuarterRefreshRateChange(false) }
                                     2 -> { onNativeRefreshRateChange(false); onQuarterRefreshRateChange(true) }
                                     else -> { onNativeRefreshRateChange(false); onQuarterRefreshRateChange(false) } } },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && !matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "matrix_speed",
            title = "MATRIX SPEED",
            keywords = listOf("matrix", "speed", "rain", "fall speed", "cascade", "slow", "fast", "velocity"),
            path = "VISUALS → EFFECTS → PARTICLES → MATRIX SETTINGS"
        ) {
            Column {
            SearchSelectorCard(title = "MATRIX SPEED", description = "How fast glyphs fall down the screen.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Slow", "Medium", "Fast"), selectedIndex = matrixSpeed.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onMatrixSpeedChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "matrix_density",
            title = "MATRIX DENSITY",
            keywords = listOf("matrix", "density", "rain", "columns", "sparse", "dense", "column density", "coverage"),
            path = "VISUALS → EFFECTS → PARTICLES → MATRIX SETTINGS"
        ) {
            Column {
            SearchSelectorCard(title = "MATRIX DENSITY", description = "How many columns of glyphs appear on screen.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Sparse", "Medium", "Dense"), selectedIndex = matrixDensity.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onMatrixDensityChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "matrix_font_size",
            title = "MATRIX FONT SIZE",
            keywords = listOf("matrix", "font", "size", "glyph", "text size", "characters", "small", "large", "big"),
            path = "VISUALS → EFFECTS → PARTICLES → MATRIX SETTINGS"
        ) {
            Column {
            SearchSelectorCard(title = "MATRIX FONT SIZE", description = "The size of individual glyphs in the rain.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Small", "Medium", "Large"), selectedIndex = matrixFontSize.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onMatrixFontSizeChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode)
            } }
        },
        SettingsSearchItem(
            id = "matrix_fade",
            title = "MATRIX TRAIL LENGTH",
            keywords = listOf("matrix", "fade", "trail", "length", "tail", "ghost", "streak", "short", "long", "full"),
            path = "VISUALS → EFFECTS → PARTICLES → MATRIX SETTINGS"
        ) {
            Column {
            SearchSelectorCard(title = "MATRIX TRAIL LENGTH", description = "How long the glowing trail behind each glyph is.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("Short", "Medium", "Full"), selectedIndex = matrixFadeLength.coerceIn(0, 2),
                    onOptionSelected = { performHaptic(); onMatrixFadeLengthChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = particlesEnabled && matrixMode)
            } }
        },
        // ── RENDERER ──────────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "aggressive_mode",
            title = "AGGRESSIVE MODE",
            keywords = listOf("aggressive", "mode", "renderer", "all packages", "coverage", "broad", "every app", "all apps", "force", "global"),
            path = "RENDERER"
        ) {
            Column {
            ToggleCard(title = "AGGRESSIVE MODE",
                description = "Applies the renderer to every installed package — broader coverage, but read the warning before enabling",
                checked = aggressiveMode, onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true) }
        },
        SettingsSearchItem(
            id = "restart_launcher",
            title = "RESTART LAUNCHER ON SWITCH",
            keywords = listOf("restart", "launcher", "switch", "kill", "force stop", "miui", "xiaomi", "immediately", "reload"),
            path = "RENDERER"
        ) {
            Column {
            ToggleCard(title = "RESTART LAUNCHER ON SWITCH",
                description = "Force-stops the launcher after switching so it picks up the new renderer immediately — leave off on Xiaomi / MIUI",
                checked = killLauncher, onCheckedChange = { performHaptic(); onKillLauncherChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true) }
        },
        SettingsSearchItem(
            id = "restart_keyboard",
            title = "RESTART KEYBOARD ON SWITCH",
            keywords = listOf("keyboard", "input method", "ime", "restart", "kill", "force stop", "gboard", "samsung keyboard", "after applying api", "renderer"),
            path = "RENDERER"
        ) {
            Column {
            ToggleCard(title = "RESTART KEYBOARD ON SWITCH",
                description = "Force-stops the currently selected keyboard after applying the renderer, so it reloads with the new graphics API",
                checked = killKeyboard, onCheckedChange = { performHaptic(); onKillKeyboardChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true) }
        },
        SettingsSearchItem(
            id = "doze",
            title = "DOZE",
            keywords = listOf("doze", "sleep", "deep sleep", "battery", "power", "idle", "standby", "aggressive doze"),
            path = "RENDERER"
        ) {
            Column {
            ToggleCard(title = "DOZE",
                description = "Puts the device into deep sleep immediately — squeezes extra battery life when you're not using it",
                checked = dozeMode, onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true) }
        },
        SettingsSearchItem(
            id = "gpuwatch_shortcut",
            title = "GPUWATCH SHORTCUT",
            keywords = listOf("gpuwatch", "gpu watch", "samsung", "shortcut", "gpu", "button", "monitor", "overlay"),
            path = "RENDERER"
        ) {
            Column {
            ToggleCard(title = "GPUWATCH SHORTCUT",
                description = "Adds an Open GPUWatch button on the main screen. Samsung devices only.",
                checked = showGpuWatchButton, onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode, accentBorder = true) }
        },
        // ── APP / SYSTEM ──────────────────────────────────────────────────────
        SettingsSearchItem(
            id = "verbose_output",
            title = "VERBOSE OUTPUT",
            keywords = listOf("verbose", "output", "log", "shell", "command", "debug", "terminal", "output", "show output"),
            path = "APP"
        ) {
            Column {
            ToggleCard(title = "VERBOSE OUTPUT",
                description = "Shows the full shell command output when switching renderers",
                checked = verboseMode, onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "tap_outside_to_close",
            title = "TAP OUTSIDE TO CLOSE",
            keywords = listOf("tap", "outside", "close", "dismiss", "panel", "back button", "click outside", "touch outside", "gesture"),
            path = "APP"
        ) {
            Column {
            ToggleCard(title = "TAP OUTSIDE TO CLOSE",
                description = "Tap anywhere outside an open panel to dismiss it — turn off to require the back button instead",
                checked = dismissOnClickOutside, onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "notifications_reminders",
            title = "NOTIFICATIONS / REMINDERS",
            keywords = listOf("notifications", "reminders", "alert", "notify", "opengl", "reminder", "ping", "notification", "notif"),
            path = "APP → NOTIFICATIONS"
        ) {
            Column {
            ToggleCard(title = "REMINDERS",
                description = "Sends an alert if you switch to OpenGL and forget to switch back",
                checked = notificationsEnabled, onCheckedChange = { performHaptic(); onNotificationsEnabledChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen, oledMode = oledMode) }
        },
        SettingsSearchItem(
            id = "reminder_interval",
            title = "REMINDER INTERVAL",
            keywords = listOf("interval", "reminder", "frequency", "how often", "notification", "2h", "4h", "6h", "12h", "24h", "hours"),
            path = "APP → NOTIFICATIONS"
        ) {
            Column {
            SearchSelectorCard(title = "REMINDER INTERVAL", description = "How often GAMA reminds you that you're on OpenGL.", colors = colors, cardBackground = cardBackground) {
                GlideOptionSelector(options = listOf("2 h", "4 h", "6 h", "12 h", "24 h"), selectedIndex = notifIntervalIndex.coerceIn(0, 4),
                    onOptionSelected = { performHaptic(); onNotifIntervalChange(it) },
                    colors = colors, modifier = Modifier.fillMaxWidth(), enabled = notificationsEnabled)
            } }
        }
    ) } // end remember { listOf(... }

    // ── Debounced async search — scoring runs off the UI thread ───────────────
    // Previously: remember(query, …30 state keys…) recomputed Levenshtein for
    // every keystroke synchronously on the composition thread, blocking the frame.
    // Also: the 30-key remember invalidated on ANY state change (e.g. a particle
    // slider moving in another panel), re-running scoring even with no query change.
    //
    // Now:
    //  • Scoring runs in a background coroutine (Dispatchers.Default) — the UI
    //    thread is never blocked by string distance math.
    //  • 80 ms debounce absorbs fast typing so scoring fires once per "pause",
    //    not once per character.
    //  • The only dependency is `query`; the rendered state (toggles, sliders)
    //    does not affect which items are returned, only how they look when rendered.
    var results by remember { mutableStateOf(items) }
    val searchScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(query) {
        debounceJob?.cancel()
        debounceJob = searchScope.launch {
            val q = query.trim()
            if (q.isBlank()) {
                results = items
                return@launch
            }
            delay(80L) // debounce: don't score on every single keystroke
            val qLower = q.lowercase()
            val queryTokens = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }
            val scored = withContext(Dispatchers.Default) {
                items
                    .map { it to settingsSearchScore(qLower, queryTokens, it) }
                    .filter { (_, score) -> score >= 45 }
                    .sortedByDescending { (_, score) -> score }
                    .map { it.first }
            }
            results = scored
        }
    }

    PanelScaffold(
        visible = visible,
        onDismiss = onDismiss,
        isLandscape = isLandscape,
        isSmallScreen = isSmallScreen,
        oledMode = oledMode,
        colors = colors
    ) { scrollState ->
        CleanTitle(
            text = "SEARCH",
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors,
            scrollOffset = scrollState.value
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = results.size + 2) {
            SearchInputCard(
                query = query,
                onQueryChange = { query = it },
                colors = colors,
                cardBackground = cardBackground,
                isSmallScreen = isSmallScreen
            )
        }

        if (results.isEmpty() && query.isNotBlank()) {
            AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2) {
                SettingsNavigationCard(
                    title = "NO RESULTS",
                    description = "Try: blur, theme, dark, matrix, particles, doze, aggressive, notifications, gradient…",
                    onClick = {},
                    isSmallScreen = isSmallScreen,
                    colors = colors,
                    cardBackground = cardBackground,
                    oledMode = oledMode
                )
            }
        } else {
            results.forEachIndexed { index, item ->
                // PathLabel renders OUTSIDE AnimatedElement so the shadow only
                // covers the card below it, not the breadcrumb pill above.
                PathLabel(item.path)
                AnimatedElement(
                    visible = visible,
                    cardShadow = true,
                    staggerIndex = index + 2,
                    totalItems = results.size + 2
                ) {
                    item.render()
                }
            }
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
    shadowsEnabled: Boolean,
    onShadowsEnabledChange: (Boolean) -> Unit,
    onEffectsClick: () -> Unit,
    onColorsClick: () -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = LocalStrings.current["particles.appearance_title"].ifEmpty { "VISUALS" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        // EFFECTS and COLORS — at the top so they're always easy to reach
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 8) {
            SettingsNavigationCard(
                title = LocalStrings.current["effects.title"].ifEmpty { "EFFECTS" },
                description = LocalStrings.current["effects.effects_desc"].ifEmpty { "Background gradient, frosted glass blur, and floating particles" },
                onClick = { performHaptic(); onEffectsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 8) {
            SettingsNavigationCard(
                title = LocalStrings.current["colors.title"].ifEmpty { "COLORS" },
                description = LocalStrings.current["colors.colors_desc"].ifEmpty { "Accent color, gradient palette, OLED mode, and Material You theming" },
                onClick = { performHaptic(); onColorsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }

        // Theme — zooms out when OLED mode is active (locked to Dark, less relevant)
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 8) {
            val themeCardScale by animateFloatAsState(
                targetValue = if (oledMode) 0.92f else 1f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                    stiffness = MotionTokens.Springs.smooth.stiffness
                ),
                label = "theme_card_oled_scale"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = themeCardScale, scaleY = themeCardScale)
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.theme"].ifEmpty { "THEME" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = if (oledMode) 0.3f else 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Auto", "Dark", "Light"),
                            selectedIndex = if (oledMode) 1 else themePreference,
                            onOptionSelected = { if (!oledMode) { performHaptic(); onThemeChange(it) } },
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = !oledMode
                        )
                        if (oledMode) {
                            Text(
                                text = LocalStrings.current["appearance.theme_locked_oled"].ifEmpty { "Forced to Dark while OLED mode is on" },
                                fontSize = ts.bodySmall, color = colors.textSecondary.copy(alpha = 0.5f),
                                fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } // end Box
        }

        // Animations
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.animations"].ifEmpty { "ANIMATIONS" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
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
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.ui_scale"].ifEmpty { "UI SCALE" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
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

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 6, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["appearance.stagger_animations"].ifEmpty { "STAGGER ANIMATIONS" },
                description = LocalStrings.current["appearance.stagger_animations_desc"].ifEmpty { "Panel cards animate in one by one — turn off for instant panel opens" },
                checked = staggerEnabled,
                onCheckedChange = { performHaptic(); onStaggerEnabledChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 7, totalItems = 8) {
            ToggleCard(
                title = LocalStrings.current["appearance.card_shadows"].ifEmpty { "CARD SHADOWS" },
                description = LocalStrings.current["appearance.card_shadows_desc"].ifEmpty { "Drop shadows under cards — disable to reduce GPU load or fix visual glitches during animations" },
                checked = shadowsEnabled,
                onCheckedChange = { performHaptic(); onShadowsEnabledChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 8, totalItems = 8) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["appearance.your_name"].ifEmpty { "YOUR NAME" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = LocalStrings.current["appearance.your_name_desc"].ifEmpty { "Used in greetings and notifications — completely optional" },
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
        CleanTitle(text = LocalStrings.current["oled_panel.title"].ifEmpty { "OLED MODE" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)
        PanelCaption(
            text = LocalStrings.current["oled_panel.subtitle"].ifEmpty { "On OLED and AMOLED screens, true black pixels draw zero power — enabling this can meaningfully extend battery life" },
            colors = colors
        )
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 1) {
            ToggleCard(
                title = LocalStrings.current["colors.oled_mode"].ifEmpty { "OLED MODE" },
                description = if (oledMode) "Active — all backgrounds are pure black" else "Off — using your regular theme background color",
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
    blurEnabled: Boolean,
    onBlurChange: (Boolean) -> Unit,
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
        CleanTitle(text = LocalStrings.current["effects.title"].ifEmpty { "EFFECTS" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2) {
            ToggleCard(
                title = LocalStrings.current["blur.blur_toggle"].ifEmpty { "BLUR" },
                description = LocalStrings.current["blur.blur_toggle_desc"].ifEmpty { "Frosted glass behind panels and dialogs — subtle depth that makes the UI feel premium" },
                checked = blurEnabled,
                onCheckedChange = { performHaptic(); onBlurChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2) {
            SettingsNavigationCard(
                title = LocalStrings.current["particles.toggle"].ifEmpty { "PARTICLES" }, description = if (oledMode) LocalStrings.current["effects.particles_oled_desc"].ifEmpty { "Visible in OLED mode — some effects are limited by black backgrounds" } else LocalStrings.current["effects.particles_desc"].ifEmpty { "Floating dots, twinkling stars, or Matrix rain — choose your style" },
                onClick = { performHaptic(); onParticlesClick() },
                isSmallScreen = isSmallScreen, colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// ParticlesPanel  — hub with enable toggle + style selector + two settings buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParticlesPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    onParticlesChange: (Boolean) -> Unit,
    // ── style selector ────────────────────────────────────────────────────────
    matrixMode: Boolean,
    onMatrixModeChange: (Boolean) -> Unit,
    onParticlesSettingsClick: () -> Unit,
    onMatrixSettingsClick: () -> Unit,
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
        CleanTitle(
            text = LocalStrings.current["particles.title"].ifEmpty { "PARTICLES" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        // ── Master enable toggle ───────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
            ToggleCard(
                title       = LocalStrings.current["particles.toggle"].ifEmpty { "PARTICLES" },
                description = LocalStrings.current["particles.toggle_desc"].ifEmpty {
                    "Animates the background with floating particles or Matrix rain"
                },
                checked         = particlesEnabled,
                onCheckedChange = { performHaptic(); onParticlesChange(it) },
                colors          = colors, cardBackground = cardBackground,
                isSmallScreen   = isSmallScreen, oledMode = oledMode
            )
        }

        // ── Style selector — Stars vs Matrix Rain ─────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4, enabled = particlesEnabled) {
            val styleCardScale by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.85f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.Springs.gentle.stiffness
                ),
                label = "style_card_scale"
            )
            val styleCardAlpha by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.velvet),
                label = "style_card_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = styleCardScale, scaleY = styleCardScale, alpha = styleCardAlpha)
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors    = CardDefaults.cardColors(containerColor = cardBackground),
                    shape     = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier  = Modifier.fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text         = LocalStrings.current["particles.style"].ifEmpty { "STYLE" },
                            fontSize     = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color        = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text         = if (matrixMode)
                                LocalStrings.current["particles.style_matrix_desc"].ifEmpty { "Cascading columns of glyphs — the classic Matrix digital rain effect" }
                            else
                                LocalStrings.current["particles.style_particles_desc"].ifEmpty { "Twinkling stars that float and shift with device tilt via parallax" },
                            fontSize     = ts.bodyMedium, color = colors.textSecondary,
                            fontFamily   = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        GlideOptionSelector(
                            options         = listOf(
                                LocalStrings.current["particles.style_particles"].ifEmpty { "Stars" },
                                LocalStrings.current["particles.style_matrix"].ifEmpty { "Matrix" }
                            ),
                            selectedIndex   = if (matrixMode) 1 else 0,
                            onOptionSelected = { idx ->
                                performHaptic()
                                if (particlesEnabled) onMatrixModeChange(idx == 1)
                            },
                            colors  = colors,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = particlesEnabled
                        )
                    }
                }
            }
        }

        // ── PARTICLES SETTINGS nav card ───────────────────────────────────────
        // Active when style = Particles; disabled (scaled down + dimmed) when Matrix
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4, enabled = particlesEnabled && !matrixMode) {
            val particlesSettingsEnabled = particlesEnabled && !matrixMode
            SettingsNavigationCard(
                title       = LocalStrings.current["particles.particles_settings_title"].ifEmpty { "PARTICLE SETTINGS" },
                description = LocalStrings.current["particles.particles_settings_desc"].ifEmpty {
                    "Shape, star mode, time mode, speed, parallax, and count"
                },
                onClick       = { if (particlesSettingsEnabled) { performHaptic(); onParticlesSettingsClick() } },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode,
                enabled       = particlesSettingsEnabled
            )
        }

        // ── MATRIX SETTINGS nav card ──────────────────────────────────────────
        // Active when style = Matrix; disabled (scaled down + dimmed) when Particles
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 4, enabled = particlesEnabled && matrixMode) {
            val matrixSettingsEnabled = particlesEnabled && matrixMode
            SettingsNavigationCard(
                title       = LocalStrings.current["particles.matrix_settings_title"].ifEmpty { "MATRIX SETTINGS" },
                description = LocalStrings.current["particles.matrix_settings_desc"].ifEmpty {
                    "Glyph colors, fall speed, column density, font size, and trail length"
                },
                onClick       = { if (matrixSettingsEnabled) { performHaptic(); onMatrixSettingsClick() } },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode,
                enabled       = matrixSettingsEnabled
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ParticlesSettingsPanel  — sub-panel with Appearance / Motion / Performance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParticlesSettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    onAppearanceClick: () -> Unit,
    onMotionClick: () -> Unit,
    onPerformanceClick: () -> Unit,
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
        CleanTitle(
            text     = LocalStrings.current["particles.particles_settings_title"].ifEmpty { "PARTICLE SETTINGS" },
            fontSize = if (isLandscape) ts.displaySmall else ts.displayMedium,
            colors   = colors
        )

        val navAlpha by animateFloatAsState(
            targetValue   = if (particlesEnabled) 1f else 0.38f,
            animationSpec = tween(durationMillis = 260, easing = MotionTokens.Easing.velvet),
            label         = "ps_nav_alpha"
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3, enabled = particlesEnabled) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                SettingsNavigationCard(
                    title       = LocalStrings.current["particles.appearance_title"].ifEmpty { "SHAPE & LOOK" },
                    description = LocalStrings.current["particles.appearance_desc"].ifEmpty {
                        "Star mode, time-of-day sky, and visual style"
                    },
                    onClick       = { if (particlesEnabled) { performHaptic(); onAppearanceClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode,
                    enabled       = particlesEnabled
                )
            }
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3, enabled = particlesEnabled) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                SettingsNavigationCard(
                    title       = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" },
                    description = LocalStrings.current["particles.motion_desc"].ifEmpty {
                        "Float speed, parallax tilt, and sensitivity"
                    },
                    onClick       = { if (particlesEnabled) { performHaptic(); onMotionClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode,
                    enabled       = particlesEnabled
                )
            }
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3, enabled = particlesEnabled) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                SettingsNavigationCard(
                    title       = LocalStrings.current["particles.performance_title"].ifEmpty { "PERFORMANCE" },
                    description = LocalStrings.current["particles.performance_desc"].ifEmpty {
                        "Particle count and render refresh rate"
                    },
                    onClick       = { if (particlesEnabled) { performHaptic(); onPerformanceClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode,
                    enabled       = particlesEnabled
                )
            }
        }
    }
}





// ─────────────────────────────────────────────────────────────────────────────
// ParticlesAppearancePanel  — star mode, time mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParticlesAppearancePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    particleStarMode: Boolean,
    onParticleStarModeChange: (Boolean) -> Unit,
    particleTimeMode: Boolean,
    onParticleTimeModeChange: (Boolean) -> Unit,
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
        CleanTitle(text = LocalStrings.current["particles.appearance_title"].ifEmpty { "SHAPE & LOOK" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2, enabled = particlesEnabled) {
            ToggleCard(
                title = LocalStrings.current["particles.time_mode"].ifEmpty { "TIME MODE" },
                description = LocalStrings.current["particles.time_mode_desc"].ifEmpty { "A sun and moon travel across the sky in sync with the real time of day" },
                checked = particleTimeMode,
                onCheckedChange = { performHaptic(); onParticleTimeModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                enabled = particlesEnabled
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2, enabled = particlesEnabled) {
            ToggleCard(
                title = LocalStrings.current["particles.star_mode"].ifEmpty { "STAR MODE" },
                description = LocalStrings.current["particles.star_mode_desc"].ifEmpty { "Replaces floating dots with tiny twinkling stars — pairs well with Time Mode at night" },
                checked = particleStarMode,
                onCheckedChange = { performHaptic(); onParticleStarModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                enabled = particlesEnabled && !particleTimeMode
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ParticlesMotionPanel  — speed, parallax, parallax sensitivity
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParticlesMotionPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    particleSpeed: Int,
    onParticleSpeedChange: (Int) -> Unit,
    particleParallaxEnabled: Boolean,
    onParticleParallaxChange: (Boolean) -> Unit,
    particleParallaxSensitivity: Int,
    onParticleParallaxSensitivityChange: (Int) -> Unit,
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
        CleanTitle(text = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        // Speed card
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3, enabled = particlesEnabled) {
            val cardScale by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.85f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.Springs.gentle.stiffness
                ),
                label = "speed_scale"
            )
            val cardAlpha by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 260, easing = MotionTokens.Easing.velvet),
                label = "speed_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = cardScale, scaleY = cardScale, alpha = cardAlpha)
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = LocalStrings.current["particles.speed"].ifEmpty { "SPEED" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = LocalStrings.current["particles.speed_desc"].ifEmpty { "Slow is 1× · Medium is 3× · Fast is 6×" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        GlideOptionSelector(
                            options = listOf("Slow", "Medium", "Fast"),
                            selectedIndex = particleSpeed.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onParticleSpeedChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = particlesEnabled
                        )
                    }
                }
            }
        }

        // Parallax toggle
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3, enabled = particlesEnabled) {
            ToggleCard(
                title = LocalStrings.current["particles.parallax"].ifEmpty { "PARALLAX" },
                description = LocalStrings.current["particles.parallax_desc"].ifEmpty { "Tilt your device and the particles shift with it for a subtle 3D depth effect" },
                checked = particleParallaxEnabled,
                onCheckedChange = { performHaptic(); onParticleParallaxChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                enabled = particlesEnabled
            )
        }

        // Parallax sensitivity — only meaningful when parallax is enabled
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3, enabled = particlesEnabled && particleParallaxEnabled) {
            val sensEnabled = particlesEnabled && particleParallaxEnabled
            val sensAlpha by animateFloatAsState(
                targetValue = if (sensEnabled) 1f else 0.38f,
                animationSpec = tween(durationMillis = 260, easing = MotionTokens.Easing.velvet),
                label = "sens_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(alpha = sensAlpha)
                    .then(
                        if (sensEnabled) Modifier.border(
                            width = 1.dp,
                            color = colors.primaryAccent.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(28.dp)) else Modifier
                    )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = LocalStrings.current["particles.parallax_sensitivity"].ifEmpty { "PARALLAX SENSITIVITY" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        GlideOptionSelector(
                            options = listOf("Low", "Medium", "High"),
                            selectedIndex = particleParallaxSensitivity.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onParticleParallaxSensitivityChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = sensEnabled
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ParticlesPerformancePanel  — count, native refresh rate
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ParticlesPerformancePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    particleCount: Int,
    onParticleCountChange: (Int) -> Unit,
    nativeRefreshRate: Boolean,
    onNativeRefreshRateChange: (Boolean) -> Unit,
    quarterRefreshRate: Boolean,
    onQuarterRefreshRateChange: (Boolean) -> Unit,
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
        CleanTitle(text = LocalStrings.current["particles.performance_title"].ifEmpty { "PERFORMANCE" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        // Count card
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3, enabled = particlesEnabled) {
            val cardScale by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.85f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.Springs.gentle.stiffness
                ),
                label = "count_scale"
            )
            val cardAlpha by animateFloatAsState(
                targetValue = if (particlesEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 260, easing = MotionTokens.Easing.velvet),
                label = "count_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = cardScale, scaleY = cardScale, alpha = cardAlpha)
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            shape = RoundedCornerShape(28.dp)
                            clip = false
                        },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = LocalStrings.current["particles.count"].ifEmpty { "COUNT" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = LocalStrings.current["particles.count_desc"].ifEmpty { "Low = 75 · Medium = 150 · High = 300" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        GlideOptionSelector(
                            options = listOf("Low", "Medium", "High"),
                            selectedIndex = particleCount.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onParticleCountChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(),
                            enabled = particlesEnabled
                        )
                    }
                }
            }
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2, enabled = particlesEnabled) {
            val refreshOption = when {
                nativeRefreshRate -> 0
                quarterRefreshRate -> 2
                else -> 1
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(cardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PARTICLE REFRESH RATE",
                        fontSize = ts.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = quicksandFontFamily,
                        color = colors.primaryAccent.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Controls how often particles update and how often the rotation sensor is polled. Native = every frame, 1/2 = every other frame, 1/4 = every fourth frame.",
                        fontSize = ts.bodySmall,
                        color = colors.textSecondary,
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    GlideOptionSelector(
                        options = listOf("Native", "1/2", "1/4"),
                        selectedIndex = refreshOption,
                        onOptionSelected = { option ->
                            performHaptic()
                            when (option) {
                                0 -> {
                                    onNativeRefreshRateChange(true)
                                    onQuarterRefreshRateChange(false)
                                }
                                1 -> {
                                    onNativeRefreshRateChange(false)
                                    onQuarterRefreshRateChange(false)
                                }
                                else -> {
                                    onNativeRefreshRateChange(false)
                                    onQuarterRefreshRateChange(true)
                                }
                            }
                        },
                        colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = particlesEnabled
                    )
                }
            }
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
    onGradientClick: () -> Unit,
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
    val gradientAvailable = !oledMode && !isDarkTheme

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(text = LocalStrings.current["colors.title"].ifEmpty { "COLORS" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 4, enabled = dynamicColorAvailable) {
            ToggleCard(
                title = LocalStrings.current["colors.dynamic_color"].ifEmpty { "DYNAMIC COLOR" },
                description = if (dynamicColorAvailable) LocalStrings.current["colors.dynamic_color_desc"].ifEmpty { "Picks accent colors from your wallpaper automatically via Material You — also colors the Matrix rain when active" } else LocalStrings.current["colors.dynamic_color_unavailable"].ifEmpty { "Requires Android 12 or newer" },
                checked = useDynamicColor && dynamicColorAvailable,
                onCheckedChange = { performHaptic(); onDynamicColorChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4, enabled = !useDynamicColor || !dynamicColorAvailable) {
            ToggleCard(
                title = LocalStrings.current["colors.advanced_picker"].ifEmpty { "ADVANCED COLOR PICKER" },
                description = LocalStrings.current["colors.advanced_picker_desc"].ifEmpty { "Adds a hex input field to the color pickers — type any color directly, e.g. #4895EF" },
                checked = advancedColorPicker,
                onCheckedChange = { performHaptic(); onAdvancedColorPickerChange(it) },
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = !useDynamicColor || !dynamicColorAvailable
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4,
            enabled = !useDynamicColor || !dynamicColorAvailable) {
            CompactColorPickerCard(
                title = LocalStrings.current["colors.accent_color"].ifEmpty { "ACCENT COLOR" },
                description = LocalStrings.current["colors.accent_color_desc"].ifEmpty { "The highlight color used on buttons, borders, and interactive elements" },
                currentColor = customAccentColor,
                onColorChange = onAccentColorChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = !useDynamicColor || !dynamicColorAvailable, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 4, enabled = gradientAvailable) {
            val gradientNavScale by animateFloatAsState(
                targetValue = if (gradientAvailable) 1f else 0.92f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                    stiffness = MotionTokens.Springs.smooth.stiffness
                ),
                label = "gradient_nav_dark_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = gradientNavScale,
                        scaleY = gradientNavScale,
                        alpha = if (gradientAvailable) 1f else 0.42f
                    )
            ) {
                SettingsNavigationCard(
                    title = LocalStrings.current["colors.gradient_background"].ifEmpty { "BACKGROUND GRADIENT" },
                    description = when {
                        oledMode -> LocalStrings.current["colors.gradient_background_oled_desc"].ifEmpty { "Unavailable in OLED mode — background is pure black" }
                        isDarkTheme -> "Unavailable in Dark mode — background is pure black"
                        else -> LocalStrings.current["colors.gradient_background_desc"].ifEmpty { "Toggle the gradient and customize its start and end colors" }
                    },
                    onClick = { if (gradientAvailable) { performHaptic(); onGradientClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// GradientPanel  — toggle + start/end color pickers, child of Colors
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradientPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    gradientEnabled: Boolean,
    onGradientChange: (Boolean) -> Unit,
    customGradientStart: Color,
    onGradientStartChange: (Color) -> Unit,
    customGradientEnd: Color,
    onGradientEndChange: (Color) -> Unit,
    useDynamicColor: Boolean,
    advancedColorPicker: Boolean,
    oledMode: Boolean,
    darkModeActive: Boolean,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val gradientAvailable = !darkModeActive
    val pickersEnabled = (!useDynamicColor || !dynamicColorAvailable) && gradientAvailable

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = LocalStrings.current["colors.gradient_background"].ifEmpty { "BACKGROUND GRADIENT" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        // ── Enable toggle ─────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3, enabled = gradientAvailable) {
            val gradientCardScale by animateFloatAsState(
                targetValue = if (gradientAvailable) 1f else 0.92f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.smooth.dampingRatio,
                    stiffness = MotionTokens.Springs.smooth.stiffness
                ),
                label = "gradient_card_dark_scale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = gradientCardScale,
                        scaleY = gradientCardScale,
                        alpha = if (gradientAvailable) 1f else 0.42f
                    )
            ) {
                ToggleCard(
                    title = LocalStrings.current["effects.gradient_background"].ifEmpty { "GRADIENT BACKGROUND" },
                    description = when {
                        oledMode -> LocalStrings.current["effects.gradient_background_oled_desc"].ifEmpty { "Unavailable in OLED mode — background is pure black" }
                        darkModeActive -> "Unavailable in Dark mode — background is pure black"
                        else -> LocalStrings.current["effects.gradient_background_on_desc"].ifEmpty { "A slow-shifting color gradient behind your home screen" }
                    },
                    checked = gradientEnabled && gradientAvailable,
                    onCheckedChange = { if (gradientAvailable) { performHaptic(); onGradientChange(it) } },
                    colors = colors, cardBackground = cardBackground,
                    isSmallScreen = isSmallScreen, oledMode = oledMode,
                    enabled = gradientAvailable
                )
            }
        }

        // ── Gradient start color ───────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3,
            enabled = pickersEnabled) {
            CompactColorPickerCard(
                title = LocalStrings.current["colors.gradient_start"].ifEmpty { "GRADIENT START" },
                description = when {
                    oledMode -> LocalStrings.current["colors.gradient_start_oled_desc"].ifEmpty { "Disabled in OLED mode" }
                    darkModeActive -> "Disabled in Dark mode"
                    useDynamicColor && dynamicColorAvailable -> LocalStrings.current["colors.gradient_start_dynamic_desc"].ifEmpty { "Controlled by Dynamic Color — disable it to set a custom color" }
                    else -> LocalStrings.current["colors.gradient_start_desc"].ifEmpty { "The color the background gradient fades from at the top of the screen" }
                },
                currentColor = customGradientStart,
                onColorChange = onGradientStartChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = pickersEnabled, oledMode = oledMode
            )
        }

        // ── Gradient end color ─────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3,
            enabled = pickersEnabled) {
            CompactColorPickerCard(
                title = LocalStrings.current["colors.gradient_end"].ifEmpty { "GRADIENT END" },
                description = when {
                    oledMode -> LocalStrings.current["colors.gradient_end_oled_desc"].ifEmpty { "Disabled in OLED mode" }
                    darkModeActive -> "Disabled in Dark mode"
                    useDynamicColor && dynamicColorAvailable -> LocalStrings.current["colors.gradient_end_dynamic_desc"].ifEmpty { "Controlled by Dynamic Color — disable it to set a custom color" }
                    else -> LocalStrings.current["colors.gradient_end_desc"].ifEmpty { "The color the background gradient fades into at the bottom of the screen" }
                },
                currentColor = customGradientEnd,
                onColorChange = onGradientEndChange,
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, isLandscape = isLandscape,
                advancedPicker = advancedColorPicker,
                enabled = pickersEnabled, oledMode = oledMode
            )
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
            text = LocalStrings.current["renderer.title"].ifEmpty { "RENDERER" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
            SettingsNavigationCard(
                title = LocalStrings.current["renderer.title"].ifEmpty { "SWITCHING ENGINE" },
                description = LocalStrings.current["renderer.aggressive_mode_desc"].ifEmpty { "Aggressive mode, launcher restart, doze, and GPU Watch shortcut" },
                onClick = { performHaptic(); onRendererClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 5) {
            SettingsNavigationCard(
                title = LocalStrings.current["renderer.behavior_title"].ifEmpty { "BEHAVIOUR" },
                description = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "Verbose output, tap-outside dismissal, and panel interaction tweaks" },
                onClick = { performHaptic(); onBehaviorClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 5) {
            SettingsNavigationCard(
                title = LocalStrings.current["notifications.title"].ifEmpty { "NOTIFICATIONS" },
                description = LocalStrings.current["system.notifications_desc"].ifEmpty { "Reminder alerts if you've been on OpenGL longer than intended" },
                onClick = { performHaptic(); onNotificationsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 5) {
            SettingsNavigationCard(
                title = LocalStrings.current["system.backup"].ifEmpty { "BACKUP & RESTORE" },
                description = LocalStrings.current["system.backup_desc"].ifEmpty { "Export all settings to a file or restore from a previous backup" },
                onClick = { performHaptic(); onBackupClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 5) {
            SettingsNavigationCard(
                title = LocalStrings.current["crash_log.title"].ifEmpty { "CRASH LOG" },
                description = LocalStrings.current["system.crash_log_desc"].ifEmpty { "View recent crash reports and copy them for troubleshooting" },
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
    killKeyboard: Boolean,
    onKillKeyboardChange: (Boolean) -> Unit,
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
            text = LocalStrings.current["renderer.title"].ifEmpty { "RENDERER" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.aggressive_mode"].ifEmpty { "AGGRESSIVE MODE" },
                description = LocalStrings.current["renderer.aggressive_mode_desc"].ifEmpty { "Applies the renderer to every installed package — broader coverage, but read the warning before enabling" },
                checked = aggressiveMode,
                onCheckedChange = { performHaptic(); onAggressiveModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.kill_launcher"].ifEmpty { "RESTART LAUNCHER ON SWITCH" },
                description = LocalStrings.current["renderer.kill_launcher_desc"].ifEmpty { "Force-stops the launcher after switching so it picks up the new renderer immediately — leave off on Xiaomi / MIUI" },
                checked = killLauncher,
                onCheckedChange = { performHaptic(); onKillLauncherChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.kill_keyboard"].ifEmpty { "RESTART KEYBOARD ON SWITCH" },
                description = LocalStrings.current["renderer.kill_keyboard_desc"].ifEmpty { "Force-stops the currently selected keyboard after applying the renderer, so it reloads with the new graphics API" },
                checked = killKeyboard,
                onCheckedChange = { performHaptic(); onKillKeyboardChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.doze_mode"].ifEmpty { "DOZE" },
                description = LocalStrings.current["renderer.doze_mode_desc"].ifEmpty { "Puts the device into deep sleep immediately — squeezes extra battery life when you're not using it" },
                checked = dozeMode,
                onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.show_gpuwatch_toggle"].ifEmpty { "GPUWATCH SHORTCUT" },
                description = LocalStrings.current["renderer.show_gpuwatch_desc"].ifEmpty { "Adds an Open GPUWatch button on the main screen. Samsung devices only." },
                checked = showGpuWatchButton,
                onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
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
    onLanguageClick: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    oledMode: Boolean,
    performHaptic: () -> Unit
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current
    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text = strings["system.title"].ifEmpty { "APP" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 6) {
            SettingsNavigationCard(
                title = strings["system.notifications"].ifEmpty { "NOTIFICATIONS" },
                description = strings["system.notifications_desc"].ifEmpty { "Reminder alerts if you've left OpenGL running longer than intended" },
                onClick = { performHaptic(); onNotificationsClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 6) {
            SettingsNavigationCard(
                title = strings["system.backup"].ifEmpty { "BACKUP & RESTORE" },
                description = strings["system.backup_desc"].ifEmpty { "Export all settings to a file, or restore from a previous backup" },
                onClick = { performHaptic(); onBackupClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 6) {
            SettingsNavigationCard(
                title = strings["settings.language"].ifEmpty { "LANGUAGE" },
                description = strings["settings.language_desc"].ifEmpty { "Change the display language used throughout the app" },
                onClick = { performHaptic(); onLanguageClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 6) {
            SettingsNavigationCard(
                title = strings["system.crash_log"].ifEmpty { "CRASH LOG" },
                description = strings["system.crash_log_desc"].ifEmpty { "View recent crash reports and copy details for troubleshooting" },
                onClick = { performHaptic(); onCrashLogClick() },
                isSmallScreen = isSmallScreen, colors = colors,
                cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 6) {
            ToggleCard(
                title = LocalStrings.current["renderer.verbose_mode"].ifEmpty { "VERBOSE OUTPUT" },
                description = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "Shows the full shell command output when switching renderers" },
                checked = verboseMode,
                onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 6, totalItems = 6) {
            ToggleCard(
                title = LocalStrings.current["renderer.tap_outside_to_close"].ifEmpty { "TAP OUTSIDE TO CLOSE" },
                description = LocalStrings.current["renderer.tap_outside_to_close_desc"].ifEmpty { "Tap anywhere outside an open panel to dismiss it — turn off to require the back button instead" },
                checked = dismissOnClickOutside,
                onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
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
            text = LocalStrings.current["renderer.behavior_title"].ifEmpty { "BEHAVIOUR" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.verbose_mode"].ifEmpty { "VERBOSE OUTPUT" },
                description = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "Shows the full shell command output when switching renderers" },
                checked = verboseMode,
                onCheckedChange = { performHaptic(); onVerboseModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.tap_outside_to_close"].ifEmpty { "TAP OUTSIDE TO CLOSE" },
                description = LocalStrings.current["renderer.tap_outside_to_close_desc"].ifEmpty { "Tap anywhere outside an open panel to dismiss it — turn off to require the back button instead" },
                checked = dismissOnClickOutside,
                onCheckedChange = { performHaptic(); onDismissOnClickOutsideChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.doze_mode"].ifEmpty { "DOZE" },
                description = LocalStrings.current["renderer.doze_mode_desc"].ifEmpty { "Puts the device into deep sleep immediately — squeezes extra battery life when you're not using it" },
                checked = dozeMode,
                onCheckedChange = { performHaptic(); onDozeModeChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 5, totalItems = 5) {
            ToggleCard(
                title = LocalStrings.current["renderer.show_gpuwatch_toggle"].ifEmpty { "GPUWATCH SHORTCUT" },
                description = LocalStrings.current["renderer.show_gpuwatch_desc"].ifEmpty { "Adds an Open GPUWatch button on the main screen. Samsung devices only." },
                checked = showGpuWatchButton,
                onCheckedChange = { performHaptic(); onShowGpuWatchButtonChange(it) },
                colors = colors, cardBackground = cardBackground,
                isSmallScreen = isSmallScreen, oledMode = oledMode,
                accentBorder = true
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
        CleanTitle(text = LocalStrings.current["notifications.title"].ifEmpty { "NOTIFICATIONS" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        PanelCaption(
            text = LocalStrings.current["notifications.subtitle"].ifEmpty { "GAMA can ping you if you've left OpenGL running and haven't switched back to Vulkan" },
            colors = colors
        )

        if (!hasPermission) {
            AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 5) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.errorColor.copy(alpha = 0.4f), RoundedCornerShape(36.dp)),
                    colors = CardDefaults.cardColors(containerColor = colors.errorColor.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(36.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["notifications.permission_required"].ifEmpty { "PERMISSION REQUIRED" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily, color = colors.errorColor
                        )
                        Text(
                            text = LocalStrings.current["notifications.permission_required_desc"].ifEmpty { "GAMA needs permission to send you notifications — tap below to grant it." },
                            fontSize = ts.bodyMedium, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold
                        )
                        FlatButton(
                            text = LocalStrings.current["notifications.grant_permission"].ifEmpty { "Grant Permission" }, onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth(), accent = true, colors = colors, maxLines = 1,
                            cornerRadius = 16.dp
                        )
                    }
                }
            }
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 4, enabled = hasPermission) {
            ToggleCard(
                title = LocalStrings.current["notifications.reminders"].ifEmpty { "REMINDERS" },
                description = if (currentRenderer == "OpenGL") "You're on OpenGL right now — reminder is active" else "Sends an alert if you switch to OpenGL and forget to switch back",
                checked = notificationsEnabled, onCheckedChange = onNotificationsEnabledChange,
                colors = colors, cardBackground = cardBackground, isSmallScreen = isSmallScreen,
                oledMode = oledMode, enabled = hasPermission
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 4, enabled = hasPermission && notificationsEnabled) {
            val intervalEnabled = hasPermission && notificationsEnabled
            val intervalScale by animateFloatAsState(
                targetValue = if (intervalEnabled) 1f else 0.85f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.Springs.gentle.stiffness
                ),
                label = "interval_scale"
            )
            val intervalAlpha by animateFloatAsState(
                targetValue = if (intervalEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.velvet),
                label = "interval_alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(scaleX = intervalScale, scaleY = intervalScale, alpha = intervalAlpha)
                    .border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp))
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
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
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = LocalStrings.current["notifications.interval"].ifEmpty { "REMINDER INTERVAL" }, fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
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
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 4, totalItems = 4, enabled = hasPermission) {
            val testBtnEnabled = hasPermission
            val testBtnScale by animateFloatAsState(
                targetValue = if (testBtnEnabled) 1f else 0.85f,
                animationSpec = spring(
                    dampingRatio = MotionTokens.Springs.gentle.dampingRatio,
                    stiffness = MotionTokens.Springs.gentle.stiffness
                ),
                label = "test_btn_scale"
            )
            val testBtnAlpha by animateFloatAsState(
                targetValue = if (testBtnEnabled) 1f else 0.25f,
                animationSpec = tween(durationMillis = 300, easing = MotionTokens.Easing.velvet),
                label = "test_btn_alpha"
            )
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = testBtnScale, scaleY = testBtnScale, alpha = testBtnAlpha)) {
                FlatButton(
                    text = LocalStrings.current["notifications.send_test"].ifEmpty { "Send Test Notification" }, onClick = onTestNotification,
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
        CleanTitle(text = LocalStrings.current["backup.title"].ifEmpty { "BACKUP" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        PanelCaption(
            text = LocalStrings.current["backup.subtitle"].ifEmpty { "Save all your GAMA settings to a file, or load them back from a previous backup — useful before reinstalling or switching devices" },
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2) {
            SettingsNavigationCard(
                title = LocalStrings.current["backup.export"].ifEmpty { "EXPORT BACKUP" }, description = LocalStrings.current["backup.export_desc"].ifEmpty { "Saves your theme, preferences, and excluded apps to a JSON file" },
                onClick = onExport, isSmallScreen = isSmallScreen,
                colors = colors, cardBackground = cardBackground, oledMode = oledMode
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2) {
            SettingsNavigationCard(
                title = LocalStrings.current["backup.restore"].ifEmpty { "RESTORE BACKUP" }, description = LocalStrings.current["backup.restore_desc"].ifEmpty { "Load a backup file to bring all your settings back exactly as they were" },
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
        val rawText = withContext(Dispatchers.IO) {
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
            systemCrashes = withContext(Dispatchers.IO) {
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
        CleanTitle(text = LocalStrings.current["crash_log.title"].ifEmpty { "CRASH LOG" }, fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge, colors = colors)

        // ── GAMA crashes section header ───────────────────────────────────────
        AnimatedElement(visible = listItemsVisible, staggerIndex = 0, totalItems = totalListItems) {
            Text(
                text = LocalStrings.current["crash_log.gama_section"].ifEmpty { "GAMA CRASHES" },
                fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                color = colors.primaryAccent.copy(alpha = 0.7f)
            )
        }

        if (gamaCount == 0) {
            AnimatedElement(visible = listItemsVisible, staggerIndex = 1, totalItems = totalListItems) {
                Box(modifier = Modifier.fillMaxWidth()
                    .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBackground),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = LocalStrings.current["crash_log.no_gama_crashes"].ifEmpty { "No GAMA crashes recorded." },
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                            color = colors.textSecondary, fontWeight = FontWeight.Bold
                        )
                    }
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
                    text = LocalStrings.current["crash_log.clear_all"].ifEmpty { "Clear All GAMA Logs" },
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
                text = LocalStrings.current["crash_log.system_section"].ifEmpty { "SYSTEM CRASHES" },
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
                    Box(modifier = Modifier.fillMaxWidth()
                        .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = LocalStrings.current["crash_log.shizuku_required"].ifEmpty { "Shizuku required to read system crash logs." },
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                                color = colors.textSecondary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            systemCrashes.isEmpty() -> {
                AnimatedElement(visible = listItemsVisible, staggerIndex = systemHeaderIdx + 1, totalItems = totalListItems) {
                    Box(modifier = Modifier.fillMaxWidth()
                        .border(1.dp, colors.primaryAccent.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardBackground),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = LocalStrings.current["crash_log.no_system_crashes"].ifEmpty { "No relevant system crashes found." },
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                fontSize = ts.labelSmall, fontFamily = quicksandFontFamily,
                                color = colors.textSecondary, fontWeight = FontWeight.Bold
                            )
                        }
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
        AnimatedElement(visible = visible, cardShadow = false, staggerIndex = 1, totalItems = 3) {
            PanelCaption(
                text = subtitle,
                colors = colors,
                accent = isSystemUI
            )
        }

        // Full log body in a scrollable card
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isSystemUI) colors.primaryAccent.copy(alpha = 0.4f) else colors.border,
                        shape = RoundedCornerShape(28.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3) {
            FlatButton(
                text     = LocalStrings.current["crash_log.save_button"].ifEmpty { "Save Log" },
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

        PanelCaption(
            text = LocalStrings.current["renderer.verbose_mode_desc"].ifEmpty { "A little playground for testing things" },
            colors = colors
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3) {
            FlatButton(
                text = LocalStrings.current["notifications.send_test"].ifEmpty { "Send Test Notification" }, onClick = { performHaptic(); onTestNotification() },
                modifier = Modifier.fillMaxWidth(), accent = false, colors = colors, maxLines = 1
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3) {
            FlatButton(
                text = LocalStrings.current["notifications.boot_notification"].ifEmpty { "Send Boot Notification" }, onClick = { performHaptic(); onTestBootNotification() },
                modifier = Modifier.fillMaxWidth(), accent = false, colors = colors, maxLines = 1
            )
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TIME OFFSET: ${if (timeOffsetHours >= 0) "+" else ""}${timeOffsetHours.toInt()}h",
                    fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                    color = colors.primaryAccent.copy(alpha = 0.7f)
                )
                Text(
                    text = LocalStrings.current["particles.time_mode_desc"].ifEmpty { "Shift the clock that time-mode particles use — lets you preview dawn, dusk, or midnight without waiting" },
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

    val strings = LocalStrings.current
    data class Link(val title: String, val desc: String, val url: String, val linkDesc: String)
    val links = listOf(
        Link(strings["integrations.github"].ifEmpty { "GITHUB" },
            strings["integrations.github_desc"].ifEmpty { "Source code, issue tracker, and the latest releases" },
            "https://github.com/popovicialinc/gama",
            strings["integrations.github_link_desc"].ifEmpty { "Opens the GAMA GitHub repository where you can browse source code, report issues, and download releases." }),
        Link(strings["integrations.discord"].ifEmpty { "DISCORD" },
            strings["integrations.discord_desc"].ifEmpty { "Chat with the GAMA community — get help, share feedback, and stay updated" },
            "https://discord.gg/YYXSedBAS9",
            strings["integrations.discord_link_desc"].ifEmpty { "Opens the official GAMA Discord server — ask questions, share feedback, and stay up to date." }),
        Link(strings["integrations.shizuku"].ifEmpty { "SHIZUKU" },
            strings["integrations.shizuku_desc"].ifEmpty { "Required for GAMA to function — install this first if you haven't already" },
            "https://shizuku.rikka.app",
            strings["integrations.shizuku_link_desc"].ifEmpty { "Opens the official Shizuku website. Shizuku is required for GAMA to execute renderer switching commands." })
    )
    // 3 link cards + 3 integration cards = 6 total
    val totalItems = links.size + 3

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        isBlurred = isBlurred, oledMode = oledMode, colors = colors
    ) { scrollState ->
        CleanTitle(
            text = LocalStrings.current["resources.title"].ifEmpty { "RESOURCES" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors = colors, scrollOffset = scrollState.value
        )

        // ── Links ─────────────────────────────────────────────────────────────
        links.forEachIndexed { i, link ->
            AnimatedElement(visible = visible, cardShadow = true, staggerIndex = i + 1, totalItems = totalItems) {
                SettingsNavigationCard(
                    title = link.title, description = link.desc,
                    onClick = { onLinkSelected(link.url, link.title, link.linkDesc) },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode
                )
            }
        }

        // ── Integrations ──────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = links.size + 1, totalItems = totalItems) {
            IntegrationInfoCard(
                title = LocalStrings.current["integrations.tasker"].ifEmpty { "TASKER" },
                description = LocalStrings.current["integrations.tasker_desc"].ifEmpty { "Automate renderer switching based on app launch, time, WiFi, or any Tasker trigger via broadcast intents" },
                statusLabel = strings["integrations.tasker_status"].ifEmpty { "Available" },
                statusOk = true,
                actionLabel = strings["integrations.tasker_action"].ifEmpty { "Open guide" },
                onAction = {
                    onLinkSelected(
                        "https://github.com/popovicialinc/gama/blob/main/!assets/GAMA_Tasker_Guide.pdf",
                        strings["integrations.tasker"].ifEmpty { "Tasker Guide" },
                        strings["integrations.tasker_link_desc"].ifEmpty { "This will open the GAMA Tasker integration guide on GitHub. It covers how to use broadcast intents to automate renderer switching based on time, app launch, WiFi network, and more." }
                    )
                },
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = links.size + 2, totalItems = totalItems) {
            val tileAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            IntegrationInfoCard(
                title = LocalStrings.current["integrations.qs_tiles"].ifEmpty { "QUICK SETTINGS TILES" },
                description = LocalStrings.current["integrations.qs_tiles_desc"].ifEmpty { "Three quick-settings tiles — Vulkan, OpenGL, and Doze — each lights up when active and switches instantly on tap" },
                statusLabel = if (tileAvailable) strings["integrations.qs_tiles_available"].ifEmpty { "3 tiles available" } else strings["integrations.qs_tiles_unavailable"].ifEmpty { "Requires Android 7+" },
                statusOk = tileAvailable,
                actionLabel = if (tileAvailable) strings["integrations.qs_tiles_action"].ifEmpty { "Library" } else null,
                onAction = if (tileAvailable) ({
                    onInfoRequested(
                        strings["integrations.qs_tiles_dialog_title"].ifEmpty { "Adding QS Tiles" },
                        strings["integrations.qs_tiles_dialog_body"].ifEmpty { "Pull down your notification shade and tap the Edit button (pencil icon). Scroll through the available tiles until you find the GAMA ones: Vulkan, OpenGL, and Doze. Drag whichever tiles you want into your active area, then tap Done. Each tile shows as highlighted when its mode is currently active." }
                    )
                }) else null,
                colors = colors, cardBackground = cardBackground,
                oledMode = oledMode, isSmallScreen = isSmallScreen
            )
        }

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = links.size + 3, totalItems = totalItems) {
            IntegrationInfoCard(
                title = LocalStrings.current["integrations.widget"].ifEmpty { "HOME SCREEN WIDGET" },
                description = LocalStrings.current["integrations.widget_desc"].ifEmpty { "A Vulkan / OpenGL toggle you can place on your home screen — switch renderers without opening the app" },
                statusLabel = strings["integrations.widget_status"].ifEmpty { "Available" },
                statusOk = true,
                actionLabel = strings["integrations.widget_action"].ifEmpty { "Library" },
                onAction = {
                    onInfoRequested(
                        strings["integrations.widget_dialog_title"].ifEmpty { "Adding the Widget" },
                        strings["integrations.widget_dialog_body"].ifEmpty { "Long-press an empty area on your home screen and select Widgets from the menu that appears. Scroll through the list until you find GAMA, then long-press the widget and drag it to wherever you want it placed. The widget lets you switch between Vulkan and OpenGL with a single tap, right from your home screen." }
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
        CleanTitle(text = LocalStrings.current["renderer.verbose_mode"].ifEmpty { "VERBOSE OUTPUT" }, fontSize = ts.displaySmall, colors = colors)

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
// LanguagePanel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen settings panel for choosing the UI language.
 *
 * Lists every JSON file found in assets/translations/, supports live search,
 * highlights the active language with an accent badge, and persists the
 * selection to SharedPreferences via [LocalizationManager].
 */
@Composable
fun LanguagePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    isTablet: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts      = LocalTypeScale.current
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE) }
    val strings = LocalStrings.current

    // Read + write the shared state owned by GamaLocalizationProvider —
    // updating this triggers an immediate strings reload with no restart needed.
    val languageCodeState = LocalLanguageCode.current
    val selectedCode by languageCodeState

    var availableLanguages by remember { mutableStateOf<List<LanguageEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    // Reload the language list every time the panel opens; clear it on close
    // so stale results from a previous session never persist.
    LaunchedEffect(visible) {
        if (visible) {
            availableLanguages = LocalizationManager.loadAvailableLanguages(context)
        } else {
            availableLanguages = emptyList()
            searchQuery = ""
        }
    }

    val filtered = remember(availableLanguages, searchQuery) {
        if (searchQuery.isBlank()) availableLanguages
        else availableLanguages.filter { lang ->
            lang.name.contains(searchQuery, ignoreCase = true) ||
            lang.nativeName.contains(searchQuery, ignoreCase = true) ||
            lang.code.contains(searchQuery, ignoreCase = true)
        }
    }

    PanelScaffold(
        visible       = visible,
        onDismiss     = onDismiss,
        isLandscape   = isLandscape,
        isSmallScreen = isSmallScreen,
        oledMode      = oledMode,
        colors        = colors
    ) { _ ->

        CleanTitle(
            text         = strings["language_panel.title"].ifEmpty { "LANGUAGE" },
            fontSize     = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors       = colors,
            scrollOffset = 0
        )

        // ── Search bar ─────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (searchQuery.isNotEmpty())
                            colors.primaryAccent.copy(alpha = 0.7f)
                        else
                            colors.border.copy(alpha = if (oledMode) 0.4f else 0.25f),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = cardBackground),
                    shape    = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text       = "⌕",
                            fontSize   = 24.sp,
                            color      = colors.textSecondary.copy(alpha = 0.58f),
                            fontFamily = quicksandFontFamily
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text       = strings["language_panel.search_placeholder"].ifEmpty { "Search languages…" },
                                    fontSize   = ts.bodyMedium,
                                    color      = colors.textSecondary.copy(alpha = 0.35f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            BasicTextField(
                                value         = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine    = true,
                                textStyle     = TextStyle(
                                    fontSize   = ts.bodyMedium,
                                    color      = colors.textPrimary,
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush   = SolidColor(colors.primaryAccent),
                                modifier      = Modifier.fillMaxWidth()
                            )
                        }
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter   = fadeIn(tween(160, easing = MotionTokens.Easing.enter)) + scaleIn(
                                animationSpec = tween(160, easing = MotionTokens.Easing.enter),
                                initialScale = 0.72f
                            ),
                            exit    = fadeOut(tween(120, easing = MotionTokens.Easing.exit)) + scaleOut(
                                animationSpec = tween(120, easing = MotionTokens.Easing.exit),
                                targetScale = 0.72f
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(colors.textSecondary.copy(alpha = 0.12f))
                                    .clickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = "✕",
                                    fontSize   = 10.sp,
                                    color      = colors.textSecondary.copy(alpha = 0.6f),
                                    fontFamily = quicksandFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Language list ──────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3) {
            if (filtered.isEmpty() && searchQuery.isNotEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = strings["language_panel.no_results"].ifEmpty { "No languages match \"%s\"" }.replace("%s", searchQuery),
                        fontSize   = ts.bodyMedium,
                        color      = colors.textSecondary.copy(alpha = 0.4f),
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Medium,
                        textAlign  = TextAlign.Center
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = colors.primaryAccent.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(28.dp))
                ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(containerColor = cardBackground),
                        shape    = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            filtered.forEachIndexed { index, lang ->
                                LanguageRow(
                                    lang       = lang,
                                    isSelected = lang.code == selectedCode,
                                    isLast     = index == filtered.lastIndex,
                                    colors     = colors,
                                    oledMode   = oledMode,
                                    onClick    = {
                                        performHaptic()
                                        // Write to the shared state — provider reacts immediately
                                        languageCodeState.value = lang.code
                                        // Persist to prefs so it survives restarts
                                        LocalizationManager.saveCode(prefs, lang.code)
                                        LocalizationManager.invalidateCache()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }


    }
}


// ─────────────────────────────────────────────────────────────────────────────
// LanguageRow  — single row inside the language list card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguageRow(
    lang: LanguageEntry,
    isSelected: Boolean,
    isLast: Boolean,
    colors: ThemeColors,
    oledMode: Boolean,
    onClick: () -> Unit
) {
    val ts = LocalTypeScale.current
    val strings = LocalStrings.current

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue   = if (isPressed) MotionTokens.Scale.subtle else 1f,
        animationSpec = spring(
            dampingRatio = if (isPressed) MotionTokens.Springs.pressDown.dampingRatio
                           else MotionTokens.Springs.pressUp.dampingRatio,
            stiffness    = if (isPressed) MotionTokens.Springs.pressDown.stiffness
                           else MotionTokens.Springs.pressUp.stiffness
        ),
        label = "lang_row_press"
    )

    val accentBarAlpha by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = tween(220, easing = MotionTokens.Easing.enter),
        label         = "accent_bar_alpha"
    )
    val accentBarHeight by animateDpAsState(
        targetValue   = if (isSelected) 28.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = MotionTokens.Springs.snappy.dampingRatio,
            stiffness    = MotionTokens.Springs.snappy.stiffness
        ),
        label         = "accent_bar_height"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = pressScale, scaleY = pressScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) onClick()
                    }
                )
            }
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Animated left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(accentBarHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.primaryAccent.copy(alpha = accentBarAlpha))
            )

            // Flag
            Text(
                text       = lang.flag,
                fontSize   = if (isSelected) 22.sp else 20.sp,
                modifier   = Modifier.width(28.dp),
                textAlign  = TextAlign.Center
            )

            // Names
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = lang.nativeName,
                    fontSize   = ts.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color      = if (isSelected) colors.primaryAccent else colors.textPrimary,
                    fontFamily = quicksandFontFamily
                )
                if (lang.nativeName != lang.name) {
                    Text(
                        text       = lang.name,
                        fontSize   = ts.bodySmall,
                        color      = colors.textSecondary.copy(alpha = 0.55f),
                        fontFamily = quicksandFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // "Active" badge
            AnimatedVisibility(
                visible = isSelected,
                enter   = fadeIn(tween(200, easing = MotionTokens.Easing.enter)) +
                          slideInVertically(tween(200, easing = MotionTokens.Easing.emphasizedDecelerate)) { -it / 3 },
                exit    = fadeOut(tween(130, easing = MotionTokens.Easing.exit)) +
                          slideOutVertically(tween(130, easing = MotionTokens.Easing.exit)) { -it / 4 }
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            colors.primaryAccent.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text          = strings["language_panel.active_badge"].ifEmpty { "Active" },
                        fontSize      = ts.labelSmall,
                        color         = colors.primaryAccent,
                        fontFamily    = quicksandFontFamily,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Divider (skip on last row)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = 20.dp)
                    .height(0.5.dp)
                    .background(colors.border.copy(alpha = 0.12f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MatrixSettingsPanel  — nav panel with Appearance / Motion / Performance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixSettingsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    onAppearanceClick: () -> Unit,
    onMotionClick: () -> Unit,
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
        CleanTitle(
            text     = LocalStrings.current["particles.matrix_settings_title"].ifEmpty { "MATRIX SETTINGS" },
            fontSize = if (isLandscape) ts.displaySmall else ts.displayMedium,
            colors   = colors
        )

        val navAlpha by animateFloatAsState(
            targetValue   = if (particlesEnabled) 1f else 0.38f,
            animationSpec = tween(280, easing = MotionTokens.Easing.silk),
            label         = "ms_nav_alpha"
        )

        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 2, enabled = particlesEnabled) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                SettingsNavigationCard(
                    title       = LocalStrings.current["particles.appearance_title"].ifEmpty { "GLYPH SETTINGS" },
                    description = LocalStrings.current["matrix.appearance_desc"].ifEmpty {
                        "Glyph font size and background opacity"
                    },
                    onClick       = { if (particlesEnabled) { performHaptic(); onAppearanceClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode,
                    enabled       = particlesEnabled
                )
            }
        }
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 2, enabled = particlesEnabled) {
            Box(modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = navAlpha)) {
                SettingsNavigationCard(
                    title       = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" },
                    description = LocalStrings.current["matrix.motion_desc"].ifEmpty {
                        "Fall speed, column density, and trail fade length"
                    },
                    onClick       = { if (particlesEnabled) { performHaptic(); onMotionClick() } },
                    isSmallScreen = isSmallScreen, colors = colors,
                    cardBackground = cardBackground, oledMode = oledMode,
                    enabled       = particlesEnabled
                )
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// MatrixAppearancePanel  — colors, glyph size, background opacity
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixAppearancePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    matrixFontSize: Int,
    onMatrixFontSizeChange: (Int) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val enabled = particlesEnabled

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text     = LocalStrings.current["particles.appearance_title"].ifEmpty { "GLYPH SETTINGS" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors   = colors
        )

        // ── Glyph size ────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 1, enabled = enabled) {
            val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(260, easing = MotionTokens.Easing.velvet), label = "ma_font_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha)
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.font_size"].ifEmpty { "GLYPH SIZE" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.font_size_desc"].ifEmpty { "Small (11 sp) · Medium (15 sp) · Large (20 sp)" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf("Small", "Medium", "Large"),
                            selectedIndex = matrixFontSize.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixFontSizeChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// MatrixMotionPanel  — speed, density, trail length
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MatrixMotionPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    particlesEnabled: Boolean,
    matrixSpeed: Int,
    onMatrixSpeedChange: (Int) -> Unit,
    matrixDensity: Int,
    onMatrixDensityChange: (Int) -> Unit,
    matrixFadeLength: Int,
    onMatrixFadeLengthChange: (Int) -> Unit,
    isSmallScreen: Boolean,
    isLandscape: Boolean,
    colors: ThemeColors,
    cardBackground: Color,
    performHaptic: () -> Unit,
    oledMode: Boolean
) {
    val ts = LocalTypeScale.current
    val enabled = particlesEnabled

    PanelScaffold(
        visible = visible, onDismiss = onDismiss,
        isLandscape = isLandscape, isSmallScreen = isSmallScreen,
        oledMode = oledMode, colors = colors
    ) { _ ->
        CleanTitle(
            text     = LocalStrings.current["particles.motion_title"].ifEmpty { "MOTION" },
            fontSize = if (isLandscape) ts.displayMedium else ts.displayLarge,
            colors   = colors
        )

        // ── Speed ─────────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 1, totalItems = 3, enabled = enabled) {
            val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_speed_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha)
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["particles.speed"].ifEmpty { "SPEED" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.speed_desc"].ifEmpty { "How fast characters fall down the screen" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf("Slow", "Medium", "Fast"),
                            selectedIndex = matrixSpeed.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixSpeedChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
            }
        }

        // ── Density ───────────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 2, totalItems = 3, enabled = enabled) {
            val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_den_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha)
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.density"].ifEmpty { "DENSITY" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.density_desc"].ifEmpty { "How many columns of glyphs appear across the screen" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf("Sparse", "Medium", "Dense"),
                            selectedIndex = matrixDensity.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixDensityChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
            }
        }

        // ── Trail length ──────────────────────────────────────────────────────
        AnimatedElement(visible = visible, cardShadow = true, staggerIndex = 3, totalItems = 3, enabled = enabled) {
            val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, tween(260, easing = MotionTokens.Easing.velvet), label = "mm_trail_a")
            Box(
                modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha)
                    .then(if (enabled) Modifier.border(
                        width = 1.dp,
                        color = colors.primaryAccent.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp)) else Modifier)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                }
                Card(
                    modifier = Modifier.fillMaxWidth().graphicsLayer { shape = RoundedCornerShape(28.dp); clip = false },
                    colors = CardDefaults.cardColors(containerColor = cardBackground),
                    shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(LocalStrings.current["matrix.trail_length"].ifEmpty { "TRAIL LENGTH" },
                            fontSize = ts.labelLarge, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp, fontFamily = quicksandFontFamily,
                            color = colors.primaryAccent.copy(alpha = 0.7f))
                        Text(LocalStrings.current["matrix.trail_length_desc"].ifEmpty { "How far the fading tail extends behind each falling column" },
                            fontSize = ts.bodySmall, color = colors.textSecondary,
                            fontFamily = quicksandFontFamily, fontWeight = FontWeight.Bold)
                        GlideOptionSelector(
                            options = listOf("Short", "Medium", "Full"),
                            selectedIndex = matrixFadeLength.coerceIn(0, 2),
                            onOptionSelected = { performHaptic(); onMatrixFadeLengthChange(it) },
                            colors = colors, modifier = Modifier.fillMaxWidth(), enabled = enabled
                        )
                    }
                }
            }
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────

