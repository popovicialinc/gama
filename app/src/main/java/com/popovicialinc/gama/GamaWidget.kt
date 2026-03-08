package com.popovicialinc.gama

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private val KEY_ACTION = ActionParameters.Key<String>("widget_action")

/**
 * Minimalist glass palette.
 * Everything is expressed as white-with-alpha so the widget reads cleanly
 * against ANY wallpaper, dark or light.  Accent colours are kept soft so
 * they feel luminous rather than garish.
 */
private object C {
    val white80     = Color(0xCCFFFFFF)
    val white60     = Color(0x99FFFFFF)
    val white30     = Color(0x4DFFFFFF)
    val white10     = Color(0x1AFFFFFF)

    // Renderer accents — intentionally muted, not neon
    val vulkan      = Color(0xFFB39DFF)   // soft lavender
    val opengl      = Color(0xFF5DEFC4)   // soft mint
    val error       = Color(0xFFFF6B6B)

    val amber       = Color(0xFFFFD060)

    // Glass surface
    val glass       = Color(0x26FFFFFF)
}

private fun cp(c: Color) = ColorProvider(c)
private fun sp(v: Float) = TextUnit(v, TextUnitType.Sp)

private fun rendererColor(r: String) = when (r) {
    "Vulkan" -> C.vulkan
    "OpenGL" -> C.opengl
    else     -> C.white60
}

private fun bgDrawable(r: String) = when (r) {
    "Vulkan" -> R.drawable.widget_bg_vulkan
    "OpenGL" -> R.drawable.widget_bg_opengl
    else     -> R.drawable.widget_bg_default
}

// ─────────────────────────────────────────────────────────────────────────────
// Sizes  (Samsung One UI cell ≈ 74 dp)
// ─────────────────────────────────────────────────────────────────────────────
private val SZ_2x1 = DpSize(148.dp,  74.dp)
private val SZ_2x2 = DpSize(148.dp, 148.dp)
private val SZ_3x2 = DpSize(222.dp, 148.dp)
private val SZ_4x2 = DpSize(296.dp, 148.dp)
private val SZ_4x3 = DpSize(296.dp, 222.dp)
private val SZ_4x4 = DpSize(296.dp, 296.dp)

// ─────────────────────────────────────────────────────────────────────────────
// Widget
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*>
        get() = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(SZ_2x1, SZ_2x2, SZ_3x2, SZ_4x2, SZ_4x3, SZ_4x4)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        var renderer     = "OpenGL"
        var shizukuReady = false

        try {
            renderer = context
                .getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
                .getString("last_renderer", "OpenGL") ?: "OpenGL"
        } catch (_: Exception) {}

        try {
            shizukuReady = ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()
        } catch (_: Exception) {}

        provideContent {
            GlanceTheme {
                WidgetRoot(renderer, shizukuReady)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root dispatcher
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WidgetRoot(renderer: String, shizukuReady: Boolean) {
    val sz      = LocalSize.current
    val context = LocalContext.current
    val open    = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )
    when {
        sz.width <= SZ_2x1.width && sz.height <= SZ_2x1.height -> Pill(renderer, shizukuReady, open)
        sz.width <= SZ_2x2.width                               -> Small(renderer, shizukuReady, open)
        sz.width <= SZ_3x2.width                               -> Medium(renderer, shizukuReady, open)
        sz.height <= SZ_4x2.height                             -> Wide(renderer, shizukuReady, open)
        sz.height <= SZ_4x3.height                             -> Tall(renderer, shizukuReady, open)
        else                                                    -> Full(renderer, shizukuReady, open)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared micro-components
// ─────────────────────────────────────────────────────────────────────────────

/** Tiny glowing status dot */
@Composable
private fun Dot(ready: Boolean, sizeDp: Int = 6) {
    Box(
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .background(if (ready) C.opengl else C.error)
            .cornerRadius(R.dimen.widget_corner_radius_dot)
    ) {}
}

/**
 * Clean pill button.
 * Active = gradient drawable (branded).
 * Idle   = subtle frosted glass pill (near-invisible, very minimal).
 */
@Composable
private fun PillBtn(
    label: String,
    active: Boolean,
    action: String,
    activeDrawable: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .background(ImageProvider(if (active) activeDrawable else R.drawable.widget_btn_idle))
            .cornerRadius(R.dimen.widget_corner_radius_pill)
            .clickable(
                actionRunCallback<WidgetActionCallback>(
                    actionParametersOf(KEY_ACTION to action)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            style = TextStyle(
                color      = cp(if (active) Color(0xFFFFFFFF) else C.white60),
                fontSize   = sp(11f),
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        )
    }
}

/** Amber "needs Shizuku" warning — tap opens app */
@Composable
private fun ShizukuPill(launchApp: androidx.glance.action.Action, heightDp: Int = 36) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(C.amber.copy(alpha = 0.14f))
            .cornerRadius(R.dimen.widget_corner_radius_pill)
            .clickable(launchApp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "⚠  Shizuku needed  —  tap to open",
            style = TextStyle(
                color      = cp(C.amber),
                fontSize   = sp(10f),
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 × 1  ── pill  (ultra-compact: just the renderer name)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Pill(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(open)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                renderer.uppercase(),
                style = TextStyle(
                    color      = cp(rendererColor(renderer)),
                    fontSize   = sp(14f),
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Dot(shizukuReady, 7)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 × 2  ── small square  (renderer name, big; minimal chrome)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Small(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(open)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalAlignment   = Alignment.Top
        ) {
            // Top row: wordmark + dot
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GAMA",
                    style = TextStyle(color = cp(C.white30), fontSize = sp(9f), fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady)
            }

            Spacer(GlanceModifier.defaultWeight())

            // Renderer — the hero
            Text(
                renderer.uppercase(),
                style = TextStyle(
                    color      = cp(rendererColor(renderer)),
                    fontSize   = sp(28f),
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Start
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                "active",
                style = TextStyle(color = cp(C.white30), fontSize = sp(9f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3 × 2  ── medium  (renderer + two pills)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Medium(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GAMA",
                    style = TextStyle(color = cp(C.white60), fontSize = sp(10f), fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady, 6)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    if (shizukuReady) "ready" else "offline",
                    style = TextStyle(
                        color    = cp(if (shizukuReady) C.opengl else C.error),
                        fontSize = sp(9f)
                    )
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                renderer.uppercase(),
                style = TextStyle(
                    color      = cp(rendererColor(renderer)),
                    fontSize   = sp(24f),
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                "active renderer",
                style = TextStyle(color = cp(C.white30), fontSize = sp(9f))
            )

            Spacer(GlanceModifier.defaultWeight())

            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    PillBtn("Vulkan", renderer == "Vulkan", "vulkan",
                        R.drawable.widget_btn_vulkan_active,
                        GlanceModifier.defaultWeight().height(36.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    PillBtn("OpenGL", renderer == "OpenGL", "opengl",
                        R.drawable.widget_btn_opengl_active,
                        GlanceModifier.defaultWeight().height(36.dp))
                }
            } else {
                ShizukuPill(open, 36)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 2  ── wide  (split: big renderer label left, stacked pills right)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Wide(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GAMA",
                    style = TextStyle(color = cp(C.white60), fontSize = sp(10f), fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.width(5.dp))
                Text("·  Renderer", style = TextStyle(color = cp(C.white30), fontSize = sp(9f)))
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    if (shizukuReady) "Shizuku ✓" else "Shizuku ✗",
                    style = TextStyle(
                        color      = cp(if (shizukuReady) C.opengl else C.error),
                        fontSize   = sp(9f),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Left: dominant renderer label
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(
                            color      = cp(rendererColor(renderer)),
                            fontSize   = sp(30f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text("active", style = TextStyle(color = cp(C.white30), fontSize = sp(9f)))
                }

                // Right: stacked pills or amber CTA
                if (shizukuReady) {
                    Column(horizontalAlignment = Alignment.End) {
                        PillBtn("Vulkan", renderer == "Vulkan", "vulkan",
                            R.drawable.widget_btn_vulkan_active,
                            GlanceModifier.width(108.dp).height(32.dp))
                        Spacer(GlanceModifier.height(6.dp))
                        PillBtn("OpenGL", renderer == "OpenGL", "opengl",
                            R.drawable.widget_btn_opengl_active,
                            GlanceModifier.width(108.dp).height(32.dp))
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .width(108.dp).height(56.dp)
                            .background(C.amber.copy(alpha = 0.12f))
                            .cornerRadius(R.dimen.widget_corner_radius_pill)
                            .clickable(open),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Open\nGAMA",
                            style = TextStyle(
                                color      = cp(C.amber),
                                fontSize   = sp(11f),
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 3  ── tall  (glass inner hero card + wide pills)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Tall(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "GAMA",
                    style = TextStyle(color = cp(C.white80), fontSize = sp(12f), fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.defaultWeight())
                // Frosted status chip
                Box(
                    modifier = GlanceModifier
                        .background(C.glass)
                        .cornerRadius(R.dimen.widget_corner_radius_small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(shizukuReady, 5)
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            if (shizukuReady) "Ready" else "Offline",
                            style = TextStyle(
                                color      = cp(if (shizukuReady) C.opengl else C.error),
                                fontSize   = sp(9f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(12.dp))

            // Glass hero card — centrepiece of the widget
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_inner_card))
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        "ACTIVE",
                        style = TextStyle(
                            color      = cp(C.white30),
                            fontSize   = sp(8f),
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(
                            color      = cp(rendererColor(renderer)),
                            fontSize   = sp(32f),
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            // Pills
            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    PillBtn("Vulkan", renderer == "Vulkan", "vulkan",
                        R.drawable.widget_btn_vulkan_active,
                        GlanceModifier.defaultWeight().height(42.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    PillBtn("OpenGL", renderer == "OpenGL", "opengl",
                        R.drawable.widget_btn_opengl_active,
                        GlanceModifier.defaultWeight().height(42.dp))
                }
            } else {
                ShizukuPill(open, 42)
            }

            Spacer(GlanceModifier.defaultWeight())

            Text(
                "tap card to open  →",
                style = TextStyle(color = cp(C.white30), fontSize = sp(9f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 4  ── full  (most spacious; full glass card + tall pills + footer)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Full(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────────────────────
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "GAMA",
                        style = TextStyle(color = cp(C.white80), fontSize = sp(16f), fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Renderer Control",
                        style = TextStyle(color = cp(C.white30), fontSize = sp(9f))
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                // Coloured status chip
                Box(
                    modifier = GlanceModifier
                        .background(
                            if (shizukuReady) C.opengl.copy(alpha = 0.15f)
                            else C.error.copy(alpha = 0.15f)
                        )
                        .cornerRadius(R.dimen.widget_corner_radius_small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(shizukuReady, 6)
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            if (shizukuReady) "Shizuku" else "No Shizuku",
                            style = TextStyle(
                                color      = cp(if (shizukuReady) C.opengl else C.error),
                                fontSize   = sp(10f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(16.dp))

            // ── Hero glass card ─────────────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_inner_card))
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 18.dp, vertical = 20.dp)
                    .clickable(open),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(
                        "ACTIVE RENDERER",
                        style = TextStyle(
                            color      = cp(C.white30),
                            fontSize   = sp(9f),
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(
                            color      = cp(rendererColor(renderer)),
                            fontSize   = sp(38f),
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center
                        )
                    )
                    Spacer(GlanceModifier.height(5.dp))
                    Text(
                        when (renderer) {
                            "Vulkan" -> "Skia Vulkan pipeline"
                            "OpenGL" -> "Skia OpenGL pipeline"
                            else     -> "GPU pipeline"
                        },
                        style = TextStyle(
                            color     = cp(C.white60),
                            fontSize  = sp(10f),
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(GlanceModifier.height(12.dp))

            // ── Pill buttons ────────────────────────────────────────────────
            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    PillBtn("Vulkan", renderer == "Vulkan", "vulkan",
                        R.drawable.widget_btn_vulkan_active,
                        GlanceModifier.defaultWeight().height(56.dp))
                    Spacer(GlanceModifier.width(10.dp))
                    PillBtn("OpenGL", renderer == "OpenGL", "opengl",
                        R.drawable.widget_btn_opengl_active,
                        GlanceModifier.defaultWeight().height(56.dp))
                }
            } else {
                ShizukuPill(open, 56)
            }

            Spacer(GlanceModifier.defaultWeight())

            // ── Footer ───────────────────────────────────────────────────────
            Text(
                "tap card to open GAMA",
                style = TextStyle(color = cp(C.white30), fontSize = sp(9f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActionCallback
//
// Uses .commit() (synchronous) so prefs are guaranteed written before
// GamaWidget().updateAll() re-reads them in provideGlance().
// ─────────────────────────────────────────────────────────────────────────────
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context:    Context,
        glanceId:   GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[KEY_ACTION] ?: return
        val prefs  = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)

        val shizukuReady = try {
            ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission()
        } catch (_: Exception) { false }

        if (!shizukuReady) {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            return
        }

        // onAction is already a suspend function — no manual scope needed.
        // withContext(Dispatchers.IO) runs the shell commands on the IO dispatcher
        // without creating an unowned CoroutineScope that outlives the callback.
        withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "vulkan" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer skiavk")
                        ShizukuHelper.runCommand("am crash com.android.systemui")
                        prefs.edit().putString("last_renderer", "Vulkan").commit()
                    }
                    "opengl" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer opengl")
                        ShizukuHelper.runCommand("am crash com.android.systemui")
                        prefs.edit().putString("last_renderer", "OpenGL").commit()
                    }
                }
            } catch (_: Exception) {}

            try { GamaWidget().updateAll(context) } catch (_: Exception) {}
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Receiver
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GamaWidget()
}
