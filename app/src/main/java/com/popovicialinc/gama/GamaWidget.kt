package com.popovicialinc.gama

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Alignment as GA
import androidx.glance.layout.Box as GB
import androidx.glance.layout.Column as GC
import androidx.glance.layout.Row as GR
import androidx.glance.layout.Spacer as GS
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight as GFW
import androidx.glance.text.Text as GT
import androidx.glance.text.TextAlign as GTA
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Prefs  –  0=match app  1=dark  2=light
// Transparency/blur is always on; only the base hue changes
// ─────────────────────────────────────────────────────────────────────────────
private const val PREF_BG = "widget_bg_mode"
private val KEY_ACT = ActionParameters.Key<String>("widget_action")

// ─────────────────────────────────────────────────────────────────────────────
// Samsung-inspired muted palette
// ─────────────────────────────────────────────────────────────────────────────
private object W {
    // Dark
    val dkBg       = Color(0xD4141418)   // 83 % deep charcoal — frosted
    val dkSurf     = Color(0x1FFFFFFF)   // inner pill surface
    val dkSurfMed  = Color(0x14FFFFFF)
    val dkText     = Color(0xFFFFFFFF)
    val dkSub      = Color(0xB3FFFFFF)   // 70 %
    val dkMuted    = Color(0x66FFFFFF)   // 40 %
    val dkDivider  = Color(0x1AFFFFFF)
    // Light
    val ltBg       = Color(0xD4F5F5F7)   // 83 % near-white
    val ltSurf     = Color(0x22000000)
    val ltSurfMed  = Color(0x14000000)
    val ltText     = Color(0xFF0D0D0D)
    val ltSub      = Color(0x99000000)   // 60 %
    val ltMuted    = Color(0x55000000)   // 33 %
    val ltDivider  = Color(0x18000000)
    // Renderer accents
    val vulkan     = Color(0xFFA78BFA)   // violet
    val opengl     = Color(0xFF34D399)   // emerald
    // Status
    val dotGreen   = Color(0xFF30D158)
    val dotRed     = Color(0xFFFF453A)
    val dotAmber   = Color(0xFFFF9F0A)
    // Settings accent
    val blue       = Color(0xFF4F97FF)

    fun accent(r: String) = if (r == "Vulkan") vulkan else opengl
}

private data class WC(
    val bg: Color, val surf: Color, val surfMed: Color,
    val text: Color, val sub: Color, val muted: Color, val divider: Color,
    val dark: Boolean
)

private fun wc(ctx: Context): WC {
    val p  = ctx.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
    val bm = p.getInt(PREF_BG, 0)
    val at = p.getInt("theme_preference", 0)
    val d  = when (bm) { 1 -> true; 2 -> false; else -> at != 2 }
    return if (d)
        WC(W.dkBg, W.dkSurf, W.dkSurfMed, W.dkText, W.dkSub, W.dkMuted, W.dkDivider, true)
    else
        WC(W.ltBg, W.ltSurf, W.ltSurfMed, W.ltText, W.ltSub, W.ltMuted, W.ltDivider, false)
}

private fun sp(v: Float) = TextUnit(v, TextUnitType.Sp)
private fun cp(c: Color) = ColorProvider(c)

// ─────────────────────────────────────────────────────────────────────────────
// Responsive size breakpoints
// ─────────────────────────────────────────────────────────────────────────────
private val S21 = DpSize(146.dp,  73.dp)
private val S22 = DpSize(146.dp, 146.dp)
private val S32 = DpSize(219.dp, 146.dp)
private val S42 = DpSize(292.dp, 146.dp)
private val S43 = DpSize(292.dp, 219.dp)
private val S44 = DpSize(292.dp, 292.dp)

// ─────────────────────────────────────────────────────────────────────────────
// GamaWidget
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*>
        get() = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(S21, S22, S32, S42, S43, S44))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)

        // Multi-source renderer detection — tries shell when Shizuku is available,
        // falls back to an educated guess (boot-time aware) when it isn't.
        val r: String = try {
            val binderOk = ShizukuHelper.checkBinder()
            val permOk   = ShizukuHelper.checkPermission()
            if (binderOk && permOk) {
                val detected = withContext(Dispatchers.IO) { ShizukuHelper.getCurrentRenderer() }
                if (detected != "Unknown") {
                    p.edit().putString("last_renderer", detected).apply()
                    detected
                } else {
                    ShizukuHelper.guessRendererWithoutShizuku(p)
                }
            } else {
                ShizukuHelper.guessRendererWithoutShizuku(p)
            }
        } catch (_: Exception) {
            p.getString("last_renderer", "OpenGL") ?: "OpenGL"
        }

        val binderOk = try { ShizukuHelper.checkBinder() } catch (_: Exception) { false }
        val permOk   = try { ShizukuHelper.checkPermission() } catch (_: Exception) { false }
        val s        = binderOk && permOk
        val c        = wc(context)

        provideContent { GlanceTheme { Root(r, s, binderOk, c) } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root dispatcher
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun Root(r: String, s: Boolean, binderOk: Boolean, c: WC) {
    val sz  = androidx.glance.LocalSize.current
    val ctx = androidx.glance.LocalContext.current
    val op  = actionStartActivity(
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )
    when {
        sz.width <= S21.width && sz.height <= S21.height -> WNano(r, s, binderOk, c, op)
        sz.width <= S22.width && sz.height <= S22.height -> WSmall(r, s, binderOk, c, op)
        sz.width <= S32.width                            -> WMedium(r, s, binderOk, c, op)
        sz.height <= S42.height                          -> WWide(r, s, binderOk, c, op)
        sz.height <= S43.height                          -> WTall(r, s, binderOk, c, op)
        else                                             -> WFull(r, s, binderOk, c, op)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared atoms
// ─────────────────────────────────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun StatusDot(s: Boolean, binderOk: Boolean, size: Int = 7) {
    val color = when { s -> W.dotGreen; !binderOk -> W.dotRed; else -> W.dotAmber }
    GB(modifier = GlanceModifier.size(size.dp).background(color)
        .cornerRadius(R.dimen.widget_corner_radius_dot)) {}
}

@androidx.compose.runtime.Composable
private fun StatusBadge(s: Boolean, binderOk: Boolean, c: WC) {
    val label = when { s -> "Ready"; !binderOk -> "No Shizuku"; else -> "No permission" }
    val color = when { s -> W.dotGreen; !binderOk -> W.dotRed; else -> W.dotAmber }
    GB(
        modifier = GlanceModifier
            .background(color.copy(alpha = if (c.dark) 0.14f else 0.10f))
            .cornerRadius(R.dimen.widget_corner_radius_small)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = GA.Center
    ) {
        GR(verticalAlignment = GA.CenterVertically) {
            StatusDot(s, binderOk, 5)
            GS(GlanceModifier.width(4.dp))
            GT(label, style = TextStyle(color = cp(color), fontSize = sp(9f), fontWeight = GFW.Bold))
        }
    }
}

@androidx.compose.runtime.Composable
private fun RendererPill(
    label: String, isActive: Boolean, actionKey: String,
    accentColor: Color, c: WC, mod: GlanceModifier = GlanceModifier
) {
    val bg = if (isActive)
        accentColor.copy(alpha = if (c.dark) 0.22f else 0.15f)
    else
        c.surf
    GB(
        modifier = mod
            .background(bg)
            .cornerRadius(R.dimen.widget_corner_radius_pill)
            .clickable(actionRunCallback<WidgetActionCallback>(actionParametersOf(KEY_ACT to actionKey))),
        contentAlignment = GA.Center
    ) {
        GR(modifier = GlanceModifier.padding(horizontal = 10.dp), verticalAlignment = GA.CenterVertically) {
            if (isActive) {
                GB(modifier = GlanceModifier.size(4.dp).background(accentColor)
                    .cornerRadius(R.dimen.widget_corner_radius_dot)) {}
                GS(GlanceModifier.width(5.dp))
            }
            GT(label, style = TextStyle(
                color      = cp(if (isActive) accentColor else c.sub),
                fontSize   = sp(11f),
                fontWeight = if (isActive) GFW.Bold else GFW.Medium,
                textAlign  = GTA.Center
            ))
        }
    }
}

@androidx.compose.runtime.Composable
private fun OfflineBanner(binderOk: Boolean, op: androidx.glance.action.Action, c: WC, h: Int = 36) {
    val color = if (!binderOk) W.dotRed else W.dotAmber
    val msg   = if (!binderOk) "Shizuku needed  ·  Tap to open" else "Permission needed  ·  Tap to fix"
    GB(
        modifier = GlanceModifier.fillMaxWidth().height(h.dp)
            .background(color.copy(alpha = 0.10f))
            .cornerRadius(R.dimen.widget_corner_radius_pill)
            .clickable(op),
        contentAlignment = GA.Center
    ) {
        GT(msg, style = TextStyle(color = cp(color), fontSize = sp(9.5f), fontWeight = GFW.Bold, textAlign = GTA.Center))
    }
}

@androidx.compose.runtime.Composable
private fun ThinDivider(c: WC) {
    GB(modifier = GlanceModifier.fillMaxWidth().height(0.5.dp).background(c.divider)) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// 2×1  Nano bar
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WNano(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(op).padding(horizontal = 14.dp),
        contentAlignment = GA.Center
    ) {
        GR(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = GA.CenterVertically) {
            // Colored accent bar — immediate renderer context even at tiny size
            GB(modifier = GlanceModifier.width(3.dp).height(30.dp).background(accent)
                .cornerRadius(R.dimen.widget_corner_radius_dot)) {}
            GS(GlanceModifier.width(10.dp))
            GC(verticalAlignment = GA.CenterVertically) {
                GT("GAMA", style = TextStyle(color = cp(c.muted), fontSize = sp(7.5f), fontWeight = GFW.Bold))
                GS(GlanceModifier.height(1.dp))
                GT(r, style = TextStyle(color = cp(accent), fontSize = sp(15f), fontWeight = GFW.Bold))
            }
            GS(GlanceModifier.defaultWeight())
            StatusDot(s, binderOk, 7)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2×2  Small square
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WSmall(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .clickable(op).padding(16.dp),
        contentAlignment = GA.TopStart
    ) {
        GC(modifier = GlanceModifier.fillMaxSize()) {
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("GAMA", style = TextStyle(color = cp(c.muted), fontSize = sp(9f), fontWeight = GFW.Bold))
                GS(GlanceModifier.defaultWeight())
                StatusDot(s, binderOk, 6)
            }
            GS(GlanceModifier.defaultWeight())
            GT("RENDERER", style = TextStyle(color = cp(c.muted), fontSize = sp(7.5f), fontWeight = GFW.Bold))
            GS(GlanceModifier.height(2.dp))
            GT(r, style = TextStyle(color = cp(accent), fontSize = sp(27f), fontWeight = GFW.Bold))
            GS(GlanceModifier.height(6.dp))
            StatusBadge(s, binderOk, c)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3×2  Medium
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WMedium(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = GA.Center
    ) {
        GC(modifier = GlanceModifier.fillMaxSize()) {
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("GAMA", style = TextStyle(color = cp(c.sub), fontSize = sp(11f), fontWeight = GFW.Bold))
                GS(GlanceModifier.defaultWeight())
                StatusBadge(s, binderOk, c)
            }
            GS(GlanceModifier.defaultWeight())
            GT(r, style = TextStyle(color = cp(accent), fontSize = sp(30f), fontWeight = GFW.Bold))
            GT("Active renderer", style = TextStyle(color = cp(c.muted), fontSize = sp(9f)))
            GS(GlanceModifier.defaultWeight())
            if (s) {
                GR(modifier = GlanceModifier.fillMaxWidth()) {
                    RendererPill("Vulkan", r == "Vulkan", "vulkan", W.vulkan, c,
                        GlanceModifier.defaultWeight().height(34.dp))
                    GS(GlanceModifier.width(8.dp))
                    RendererPill("OpenGL", r == "OpenGL", "opengl", W.opengl, c,
                        GlanceModifier.defaultWeight().height(34.dp))
                }
            } else {
                OfflineBanner(binderOk, op, c, 34)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4×2  Wide bar
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WWide(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = GA.Center
    ) {
        GC(modifier = GlanceModifier.fillMaxSize()) {
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("GAMA", style = TextStyle(color = cp(c.sub), fontSize = sp(11f), fontWeight = GFW.Bold))
                GS(GlanceModifier.width(5.dp))
                GT("·  Renderer Control", style = TextStyle(color = cp(c.muted), fontSize = sp(10f)))
                GS(GlanceModifier.defaultWeight())
                StatusBadge(s, binderOk, c)
            }
            GS(GlanceModifier.defaultWeight())
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GC(modifier = GlanceModifier.defaultWeight()) {
                    GT(r, style = TextStyle(color = cp(accent), fontSize = sp(36f), fontWeight = GFW.Bold))
                    GT("Active pipeline", style = TextStyle(color = cp(c.muted), fontSize = sp(9f)))
                }
                GS(GlanceModifier.width(12.dp))
                if (s) {
                    GC(horizontalAlignment = GA.End) {
                        RendererPill("Vulkan", r == "Vulkan", "vulkan", W.vulkan, c,
                            GlanceModifier.width(96.dp).height(28.dp))
                        GS(GlanceModifier.height(6.dp))
                        RendererPill("OpenGL", r == "OpenGL", "opengl", W.opengl, c,
                            GlanceModifier.width(96.dp).height(28.dp))
                    }
                } else {
                    val errColor = if (!binderOk) W.dotRed else W.dotAmber
                    GB(
                        modifier = GlanceModifier.width(96.dp).height(62.dp)
                            .background(errColor.copy(alpha = 0.10f))
                            .cornerRadius(R.dimen.widget_corner_radius_medium)
                            .clickable(op),
                        contentAlignment = GA.Center
                    ) {
                        GT("Open\nGAMA", style = TextStyle(
                            color = cp(errColor), fontSize = sp(11f),
                            fontWeight = GFW.Bold, textAlign = GTA.Center
                        ))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4×3  Tall — hero card + pills + footer
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WTall(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = GA.Center
    ) {
        GC(modifier = GlanceModifier.fillMaxSize()) {
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("GAMA", style = TextStyle(color = cp(c.text), fontSize = sp(13f), fontWeight = GFW.Bold))
                GS(GlanceModifier.defaultWeight())
                StatusBadge(s, binderOk, c)
            }
            GS(GlanceModifier.height(10.dp))
            // Hero card — frosted inner surface
            GB(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(c.surf)
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .clickable(op),
                contentAlignment = GA.Center
            ) {
                GC(horizontalAlignment = GA.CenterHorizontally, verticalAlignment = GA.CenterVertically) {
                    GT("CURRENT RENDERER", style = TextStyle(
                        color = cp(c.muted), fontSize = sp(8f), fontWeight = GFW.Bold, textAlign = GTA.Center))
                    GS(GlanceModifier.height(5.dp))
                    GT(r, style = TextStyle(color = cp(accent), fontSize = sp(38f), fontWeight = GFW.Bold, textAlign = GTA.Center))
                    GS(GlanceModifier.height(3.dp))
                    GT(
                        if (r == "Vulkan") "Skia Vulkan pipeline" else "Skia OpenGL pipeline",
                        style = TextStyle(color = cp(c.muted), fontSize = sp(9f), textAlign = GTA.Center)
                    )
                }
            }
            GS(GlanceModifier.height(9.dp))
            if (s) {
                GR(modifier = GlanceModifier.fillMaxWidth()) {
                    RendererPill("Vulkan", r == "Vulkan", "vulkan", W.vulkan, c,
                        GlanceModifier.defaultWeight().height(40.dp))
                    GS(GlanceModifier.width(8.dp))
                    RendererPill("OpenGL", r == "OpenGL", "opengl", W.opengl, c,
                        GlanceModifier.defaultWeight().height(40.dp))
                }
            } else {
                OfflineBanner(binderOk, op, c, 40)
            }
            GS(GlanceModifier.defaultWeight())
            ThinDivider(c)
            GS(GlanceModifier.height(7.dp))
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("Tap card to open GAMA", style = TextStyle(color = cp(c.muted), fontSize = sp(8.5f)))
                GS(GlanceModifier.defaultWeight())
                GT("→", style = TextStyle(color = cp(c.muted), fontSize = sp(10f), fontWeight = GFW.Bold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4×4  Full — header + hero + pills + footer
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WFull(r: String, s: Boolean, binderOk: Boolean, c: WC, op: androidx.glance.action.Action) {
    val accent = W.accent(r)
    GB(
        modifier = GlanceModifier.fillMaxSize().background(c.bg)
            .cornerRadius(R.dimen.widget_corner_radius_large)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        contentAlignment = GA.Center
    ) {
        GC(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GC {
                    GT("GAMA", style = TextStyle(color = cp(c.text), fontSize = sp(18f), fontWeight = GFW.Bold))
                    GT("Renderer Control", style = TextStyle(color = cp(c.muted), fontSize = sp(9f)))
                }
                GS(GlanceModifier.defaultWeight())
                // Shizuku status pill
                GB(
                    modifier = GlanceModifier
                        .background(when { s -> W.dotGreen.copy(alpha = 0.14f); !binderOk -> W.dotRed.copy(alpha = 0.12f); else -> W.dotAmber.copy(alpha = 0.12f) })
                        .cornerRadius(R.dimen.widget_corner_radius_small)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = GA.Center
                ) {
                    GR(verticalAlignment = GA.CenterVertically) {
                        StatusDot(s, binderOk, 5)
                        GS(GlanceModifier.width(5.dp))
                        GT(
                            when { s -> "Shizuku"; !binderOk -> "Shizuku off"; else -> "No permission" },
                            style = TextStyle(
                                color = cp(when { s -> W.dotGreen; !binderOk -> W.dotRed; else -> W.dotAmber }),
                                fontSize = sp(10f), fontWeight = GFW.Bold
                            )
                        )
                    }
                }
            }
            GS(GlanceModifier.height(14.dp))
            ThinDivider(c)
            GS(GlanceModifier.height(14.dp))
            // Hero renderer card
            GB(
                modifier = GlanceModifier.fillMaxWidth()
                    .background(c.surf)
                    .cornerRadius(R.dimen.widget_corner_radius_medium)
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .clickable(op),
                contentAlignment = GA.Center
            ) {
                GC(horizontalAlignment = GA.CenterHorizontally, verticalAlignment = GA.CenterVertically) {
                    GT("ACTIVE RENDERER", style = TextStyle(
                        color = cp(c.muted), fontSize = sp(8f), fontWeight = GFW.Bold, textAlign = GTA.Center))
                    GS(GlanceModifier.height(6.dp))
                    GT(r, style = TextStyle(color = cp(accent), fontSize = sp(44f), fontWeight = GFW.Bold, textAlign = GTA.Center))
                    GS(GlanceModifier.height(4.dp))
                    GT(
                        if (r == "Vulkan") "Skia Vulkan  ·  High performance" else "Skia OpenGL  ·  Compatibility mode",
                        style = TextStyle(color = cp(c.muted), fontSize = sp(9f), textAlign = GTA.Center)
                    )
                }
            }
            GS(GlanceModifier.height(10.dp))
            // Action pills
            if (s) {
                GR(modifier = GlanceModifier.fillMaxWidth()) {
                    RendererPill("Vulkan", r == "Vulkan", "vulkan", W.vulkan, c,
                        GlanceModifier.defaultWeight().height(48.dp))
                    GS(GlanceModifier.width(9.dp))
                    RendererPill("OpenGL", r == "OpenGL", "opengl", W.opengl, c,
                        GlanceModifier.defaultWeight().height(48.dp))
                }
            } else {
                OfflineBanner(binderOk, op, c, 48)
            }
            GS(GlanceModifier.defaultWeight())
            ThinDivider(c)
            GS(GlanceModifier.height(8.dp))
            // Footer
            GR(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = GA.CenterVertically) {
                GT("Tap card to open GAMA", style = TextStyle(color = cp(c.muted), fontSize = sp(8.5f)))
                GS(GlanceModifier.defaultWeight())
                GT("→", style = TextStyle(color = cp(c.muted), fontSize = sp(10f), fontWeight = GFW.Bold))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action callback
// ─────────────────────────────────────────────────────────────────────────────
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[KEY_ACT] ?: return
        val prefs  = context.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE)
        val ready  = try { ShizukuHelper.checkBinder() && ShizukuHelper.checkPermission() } catch (_: Exception) { false }
        if (!ready) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            return
        }
        withContext(Dispatchers.IO) {
            try {
                when (action) {
                    "vulkan" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer skiavk")
                        prefs.edit()
                            .putString("last_renderer", "Vulkan")
                            .putLong("last_switch_time", System.currentTimeMillis())
                            .putLong("last_switch_uptime", android.os.SystemClock.elapsedRealtime())
                            .commit()
                    }
                    "opengl" -> {
                        ShizukuHelper.runCommand("setprop debug.hwui.renderer opengl")
                        prefs.edit()
                            .putString("last_renderer", "OpenGL")
                            .putLong("last_switch_time", System.currentTimeMillis())
                            .putLong("last_switch_uptime", android.os.SystemClock.elapsedRealtime())
                            .commit()
                    }
                }
            } catch (_: Exception) {}
            try { GamaWidget().updateAll(context) } catch (_: Exception) {}
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Configure activity — opened by tap-and-hold on the widget
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent { WidgetSettingsSheet(id) { finish() } }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Widget settings bottom sheet
// Samsung One UI aesthetic: dark sheet, list rows, Samsung-blue apply button
// ─────────────────────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun WidgetSettingsSheet(widgetId: Int, onDone: () -> Unit) {
    val ctx    = LocalContext.current
    val prefs  = remember { ctx.getSharedPreferences("gama_prefs", Context.MODE_PRIVATE) }
    var bgMode by remember { mutableStateOf(prefs.getInt(PREF_BG, 0)) }

    val sheetBg  = Color(0xFF1C1C1E)
    val divColor = Color(0xFF2C2C2E)
    val accent   = W.blue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDone() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth().clickable(enabled = false) {},
            shape          = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color          = sheetBg,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Handle
                Spacer(Modifier.height(12.dp))
                Box(Modifier.width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x40FFFFFF)))
                Spacer(Modifier.height(20.dp))
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Widget Settings", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Background is always blurred", color = Color(0x80FFFFFF), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                // Section header
                Box(
                    modifier = Modifier.fillMaxWidth().background(divColor)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("BACKGROUND", color = Color(0x80FFFFFF), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                // Option rows
                listOf(
                    Triple(0, "Match App Theme",  "Follows your GAMA dark/light setting"),
                    Triple(1, "Dark",              "Near-black translucent"),
                    Triple(2, "Light",             "Near-white translucent")
                ).forEachIndexed { i, (v, label, desc) ->
                    WidgetSettingsRow(label, desc, bgMode == v, accent) { bgMode = v }
                    if (i < 2) {
                        Box(Modifier.fillMaxWidth().padding(start = 20.dp)
                            .height(0.5.dp).background(divColor))
                    }
                }
                Spacer(Modifier.height(24.dp))
                // Apply button
                Box(
                    modifier = Modifier
                        .fillMaxWidth().padding(horizontal = 20.dp).height(52.dp)
                        .clip(RoundedCornerShape(14.dp)).background(accent)
                        .clickable {
                            prefs.edit().putInt(PREF_BG, bgMode).apply()
                            (ctx as? Activity)?.setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            )
                            (ctx as? ComponentActivity)?.lifecycleScope?.launch {
                                try { GamaWidget().updateAll(ctx) } catch (_: Exception) {}
                            }
                            onDone()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Apply", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetSettingsRow(
    label: String, desc: String, selected: Boolean, accent: Color, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Color(0x80FFFFFF), fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        // Samsung-style filled radio circle
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape)
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) Color.Transparent else Color(0x60FFFFFF),
                    shape = CircleShape
                )
                .background(if (selected) accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Receiver
// ─────────────────────────────────────────────────────────────────────────────
class GamaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GamaWidget()
}
