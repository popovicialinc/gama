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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────
private val KEY_ACTION = ActionParameters.Key<String>("widget_action")

private object C {
    // Text
    val white      = Color(0xFFFFFFFF)
    val grey1      = Color(0xFFCCCCCC)   // primary secondary
    val grey2      = Color(0xFF888888)   // tertiary
    val grey3      = Color(0xFF444444)   // dimmed labels

    // Accents
    val purple     = Color(0xFF9B8FFF)   // Vulkan
    val purpleDim  = Color(0xFF6B5FCC)
    val green      = Color(0xFF3ECFA0)   // OpenGL
    val greenDim   = Color(0xFF2A9970)
    val amber      = Color(0xFFFFCC44)   // warning

    // Surfaces (used only where Image drawables can't be used)
    val innerCard  = Color(0xCC0D0D10)
    val pillIdle   = Color(0x28FFFFFF)
}

private fun cp(c: Color)  = ColorProvider(c)
private fun sp(v: Float)  = TextUnit(v, TextUnitType.Sp)

private fun rendererColor(r: String) = when (r) {
    "Vulkan" -> C.purple
    "OpenGL" -> C.green
    else     -> C.grey2
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
        // Read state — everything in safe try/catch with sane defaults
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

/** Coloured status dot */
@Composable
private fun Dot(ready: Boolean, sizeDp: Int = 7) {
    Box(
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .background(if (ready) C.green else Color(0xFFFF6B6B))
            .cornerRadius(R.dimen.widget_corner_radius_dot)
    ) {}
}

/** Small "VULKAN" / "OPENGL" badge */
@Composable
private fun RendererBadge(renderer: String, fontSize: Float = 10f) {
    Box(
        modifier = GlanceModifier
            .background(rendererColor(renderer).copy(alpha = 0.20f))
            .cornerRadius(R.dimen.widget_corner_radius_small)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            renderer.uppercase(),
            style = TextStyle(
                color = cp(rendererColor(renderer)),
                fontSize = sp(fontSize),
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * A switch button that uses a gradient XML drawable for the active state
 * and a flat idle drawable for the inactive state — much richer than a
 * plain background colour.
 */
@Composable
private fun SwitchBtn(
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = TextStyle(
                    color      = cp(if (active) C.white else C.grey1),
                    fontSize   = sp(13f),
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            )
            if (active) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    "● active",
                    style = TextStyle(
                        color     = cp(if (action == "vulkan") C.purple else C.green),
                        fontSize  = sp(8f),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

/** Shizuku warning bar — tapping opens the app */
@Composable
private fun ShizukuWarning(launchApp: androidx.glance.action.Action, height: Int = 40) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height.dp)
            .background(C.amber.copy(alpha = 0.14f))
            .cornerRadius(R.dimen.widget_corner_radius_pill)
            .clickable(launchApp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "⚠  Open GAMA to enable Shizuku",
            style = TextStyle(
                color      = cp(C.amber),
                fontSize   = sp(11f),
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 × 1  ── ultra-compact pill
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Pill(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(open)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "GAMA",
                style = TextStyle(color = cp(C.purple), fontSize = sp(12f), fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.width(8.dp))
            RendererBadge(renderer)
            Spacer(GlanceModifier.defaultWeight())
            Dot(shizukuReady)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 × 2  ── small square
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Small(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(open)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Top row
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GAMA", style = TextStyle(color = cp(C.grey2), fontSize = sp(10f), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady)
            }

            Spacer(GlanceModifier.defaultWeight())

            // Big renderer name
            Text(
                renderer.uppercase(),
                style = TextStyle(
                    color      = cp(rendererColor(renderer)),
                    fontSize   = sp(26f),
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                "active renderer",
                style = TextStyle(color = cp(C.grey3), fontSize = sp(9f), textAlign = TextAlign.Center)
            )

            Spacer(GlanceModifier.defaultWeight())

            Text(
                "tap to open →",
                style = TextStyle(color = cp(C.grey3), fontSize = sp(9f), textAlign = TextAlign.Center)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3 × 2  ── medium landscape
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
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GAMA", style = TextStyle(color = cp(C.purple), fontSize = sp(11f), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    if (shizukuReady) "ready" else "no shizuku",
                    style = TextStyle(
                        color    = cp(if (shizukuReady) C.green else Color(0xFFFF6B6B)),
                        fontSize = sp(9f)
                    )
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            // Renderer label
            Text(
                renderer.uppercase(),
                style = TextStyle(color = cp(rendererColor(renderer)), fontSize = sp(22f), fontWeight = FontWeight.Bold)
            )
            Text(
                "active renderer",
                style = TextStyle(color = cp(C.grey3), fontSize = sp(9f))
            )

            Spacer(GlanceModifier.defaultWeight())

            // Buttons or warning
            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    SwitchBtn(
                        label          = "Vulkan",
                        active         = renderer == "Vulkan",
                        action         = "vulkan",
                        activeDrawable = R.drawable.widget_btn_vulkan_active,
                        modifier       = GlanceModifier.defaultWeight().height(38.dp)
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    SwitchBtn(
                        label          = "OpenGL",
                        active         = renderer == "OpenGL",
                        action         = "opengl",
                        activeDrawable = R.drawable.widget_btn_opengl_active,
                        modifier       = GlanceModifier.defaultWeight().height(38.dp)
                    )
                }
            } else {
                ShizukuWarning(open, 38)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 2  ── wide
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Wide(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GAMA", style = TextStyle(color = cp(C.purple), fontSize = sp(11f), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(5.dp))
                Text("Renderer Control", style = TextStyle(color = cp(C.grey3), fontSize = sp(9f)))
                Spacer(GlanceModifier.defaultWeight())
                Dot(shizukuReady)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    if (shizukuReady) "Shizuku ✓" else "Shizuku ✗",
                    style = TextStyle(
                        color = cp(if (shizukuReady) C.green else Color(0xFFFF6B6B)),
                        fontSize = sp(9f), fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(GlanceModifier.defaultWeight())

            // Content: renderer left, buttons right
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("Active", style = TextStyle(color = cp(C.grey3), fontSize = sp(9f)))
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(color = cp(rendererColor(renderer)), fontSize = sp(26f), fontWeight = FontWeight.Bold)
                    )
                }
                if (shizukuReady) {
                    Column(horizontalAlignment = Alignment.End) {
                        SwitchBtn("Vulkan", renderer == "Vulkan", "vulkan",
                            R.drawable.widget_btn_vulkan_active,
                            GlanceModifier.width(112.dp).height(34.dp))
                        Spacer(GlanceModifier.height(6.dp))
                        SwitchBtn("OpenGL", renderer == "OpenGL", "opengl",
                            R.drawable.widget_btn_opengl_active,
                            GlanceModifier.width(112.dp).height(34.dp))
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .width(130.dp).height(56.dp)
                            .background(C.amber.copy(alpha = 0.13f))
                            .cornerRadius(R.dimen.widget_corner_radius_pill)
                            .clickable(open),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Open\nGAMA",
                            style = TextStyle(color = cp(C.amber), fontSize = sp(12f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 3  ── tall-wide
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
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            // Header row
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GAMA", style = TextStyle(color = cp(C.purple), fontSize = sp(12f), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                // Shizuku chip
                Box(
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_chip))
                        .cornerRadius(R.dimen.widget_corner_radius_small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(shizukuReady, 6)
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            if (shizukuReady) "Shizuku" else "No Shizuku",
                            style = TextStyle(
                                color = cp(if (shizukuReady) C.green else Color(0xFFFF6B6B)),
                                fontSize = sp(9f), fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            // Inner renderer card
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_inner_card))
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "ACTIVE RENDERER",
                        style = TextStyle(color = cp(C.grey3), fontSize = sp(8f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    Spacer(GlanceModifier.height(5.dp))
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(
                            color = cp(rendererColor(renderer)), fontSize = sp(30f),
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    Text(
                        when (renderer) {
                            "Vulkan" -> "High-performance GPU pipeline"
                            "OpenGL" -> "Standard GPU pipeline"
                            else     -> "GPU pipeline"
                        },
                        style = TextStyle(color = cp(C.grey2), fontSize = sp(9f), textAlign = TextAlign.Center)
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            // Buttons
            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    SwitchBtn("Vulkan", renderer == "Vulkan", "vulkan",
                        R.drawable.widget_btn_vulkan_active,
                        GlanceModifier.defaultWeight().height(44.dp))
                    Spacer(GlanceModifier.width(10.dp))
                    SwitchBtn("OpenGL", renderer == "OpenGL", "opengl",
                        R.drawable.widget_btn_opengl_active,
                        GlanceModifier.defaultWeight().height(44.dp))
                }
            } else {
                ShizukuWarning(open, 44)
            }

            Spacer(GlanceModifier.defaultWeight())

            // Footer
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("GPU Renderer Control", style = TextStyle(color = cp(C.grey3), fontSize = sp(9f)))
                Spacer(GlanceModifier.defaultWeight())
                Text("tap to open →", style = TextStyle(color = cp(C.purple), fontSize = sp(9f), fontWeight = FontWeight.Bold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 × 4  ── full large
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Full(renderer: String, shizukuReady: Boolean, open: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgDrawable(renderer)))
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {

            // ── Header ───────────────────────────────────────────────────────
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("GAMA", style = TextStyle(color = cp(C.purple), fontSize = sp(15f), fontWeight = FontWeight.Bold))
                    Text("Renderer Control", style = TextStyle(color = cp(C.grey3), fontSize = sp(9f)))
                }
                Spacer(GlanceModifier.defaultWeight())
                Box(
                    modifier = GlanceModifier
                        .background(
                            (if (shizukuReady) C.green else Color(0xFFFF6B6B)).copy(alpha = 0.14f)
                        )
                        .cornerRadius(R.dimen.widget_corner_radius_small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(shizukuReady, 7)
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            if (shizukuReady) "Shizuku Ready" else "No Shizuku",
                            style = TextStyle(
                                color = cp(if (shizukuReady) C.green else Color(0xFFFF6B6B)),
                                fontSize = sp(10f), fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(GlanceModifier.height(14.dp))

            // ── Renderer display card ────────────────────────────────────────
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ImageProvider(R.drawable.widget_inner_card))
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clickable(open),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "CURRENTLY ACTIVE",
                        style = TextStyle(color = cp(C.grey3), fontSize = sp(9f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        renderer.uppercase(),
                        style = TextStyle(
                            color = cp(rendererColor(renderer)), fontSize = sp(36f),
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                    )
                    Spacer(GlanceModifier.height(5.dp))
                    Text(
                        when (renderer) {
                            "Vulkan" -> "High-performance Skia Vulkan pipeline"
                            "OpenGL" -> "Standard Skia OpenGL pipeline"
                            else     -> "Unknown GPU pipeline"
                        },
                        style = TextStyle(color = cp(C.grey2), fontSize = sp(10f), textAlign = TextAlign.Center)
                    )
                }
            }

            Spacer(GlanceModifier.height(12.dp))

            // ── Switch buttons ───────────────────────────────────────────────
            if (shizukuReady) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    SwitchBtn(
                        label          = "Vulkan",
                        active         = renderer == "Vulkan",
                        action         = "vulkan",
                        activeDrawable = R.drawable.widget_btn_vulkan_active,
                        modifier       = GlanceModifier.defaultWeight().height(58.dp)
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    SwitchBtn(
                        label          = "OpenGL",
                        active         = renderer == "OpenGL",
                        action         = "opengl",
                        activeDrawable = R.drawable.widget_btn_opengl_active,
                        modifier       = GlanceModifier.defaultWeight().height(58.dp)
                    )
                }
            } else {
                ShizukuWarning(open, 58)
            }

            Spacer(GlanceModifier.defaultWeight())

            // ── Footer ───────────────────────────────────────────────────────
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tap button to switch  •  Tap card to open",
                    style = TextStyle(color = cp(C.grey3), fontSize = sp(9f))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActionCallback
//
// KEY FIX: use prefs.edit().putString(...).commit()  (synchronous)
// NOT .apply() (async) — so the write is guaranteed complete before
// GamaWidget().updateAll() re-reads prefs in provideGlance().
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    "vulkan" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer skiavk")
                        ShizukuHelper.runCommand("am crash com.android.systemui")
                        // .commit() is SYNCHRONOUS — write is done before updateAll runs
                        prefs.edit().putString("last_renderer", "Vulkan").commit()
                    }
                    "opengl" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer opengl")
                        ShizukuHelper.runCommand("am crash com.android.systemui")
                        prefs.edit().putString("last_renderer", "OpenGL").commit()
                    }
                }
            } catch (_: Exception) {}

            // Now that prefs are committed, trigger a full widget redraw
            try {
                GamaWidget().updateAll(context)
            } catch (_: Exception) {}
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Receiver
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GamaWidget()
}