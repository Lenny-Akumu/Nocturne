package app.nocturne.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.data.DesignStyle
import app.nocturne.data.Settings
import app.nocturne.data.ThemeMode
import app.nocturne.ui.kit.LocalDesignStyle
import app.nocturne.ui.kit.LocalPixelIcons
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

val LocalDarkTheme = staticCompositionLocalOf { true }
val LocalBackdrop = staticCompositionLocalOf { false }

val AccentPresets = listOf(
    0xFF7C5CFF, 0xFF4F7BFF, 0xFF00A6A6, 0xFF2E9E5B, 0xFFE0A100,
    0xFFFF6B3D, 0xFFE5397A, 0xFFB04EDB, 0xFF8D9AA5, 0xFFD32F2F,
)

@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?

expect fun platformAccentColor(): Color?

private val NocturneShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private val ArchShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

// Arch: terminal look, monospace everywhere, flat weights, compact ramp.
private fun archTypography(): Typography {
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    val p = Typography()
    fun TextStyle.tui(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = mono, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    return Typography(
        displayLarge = p.displayLarge.tui(40, 48, FontWeight.Bold),
        displayMedium = p.displayMedium.tui(32, 40, FontWeight.Bold),
        displaySmall = p.displaySmall.tui(28, 36, FontWeight.Bold),
        headlineLarge = p.headlineLarge.tui(24, 32, FontWeight.Bold),
        headlineMedium = p.headlineMedium.tui(22, 30, FontWeight.Bold),
        headlineSmall = p.headlineSmall.tui(18, 26, FontWeight.Bold),
        titleLarge = p.titleLarge.tui(17, 24, FontWeight.Bold),
        titleMedium = p.titleMedium.tui(14, 20, FontWeight.Bold),
        titleSmall = p.titleSmall.tui(13, 19, FontWeight.Bold),
        bodyLarge = p.bodyLarge.tui(14, 21),
        bodyMedium = p.bodyMedium.tui(13, 20),
        bodySmall = p.bodySmall.tui(12, 17),
        labelLarge = p.labelLarge.tui(13, 19),
        labelMedium = p.labelMedium.tui(12, 17),
        labelSmall = p.labelSmall.tui(11, 15),
    )
}

// Gruvbox: warm terminal palette; a user-picked accent replaces the yellow.
private fun archColorScheme(base: ColorScheme, dark: Boolean, accent: Color?): ColorScheme {
    val bg = if (dark) Color(0xFF282828) else Color(0xFFFBF1C7)
    val bg1 = if (dark) Color(0xFF32302F) else Color(0xFFF2E5BC)
    val bg2 = if (dark) Color(0xFF3C3836) else Color(0xFFEBDBB2)
    val bg3 = if (dark) Color(0xFF504945) else Color(0xFFD5C4A1)
    val fg = if (dark) Color(0xFFEBDBB2) else Color(0xFF3C3836)
    val fg2 = if (dark) Color(0xFFA89984) else Color(0xFF7C6F64)
    val yellow = accent ?: if (dark) Color(0xFFFABD2F) else Color(0xFFB57614)
    val orange = if (dark) Color(0xFFFE8019) else Color(0xFFAF3A03)
    val aqua = if (dark) Color(0xFF8EC07C) else Color(0xFF427B58)
    val red = if (dark) Color(0xFFFB4934) else Color(0xFF9D0006)
    val ink = if (dark) Color(0xFF282828) else Color(0xFFFBF1C7)
    return base.copy(
        primary = yellow, onPrimary = ink, primaryContainer = bg2, onPrimaryContainer = yellow, inversePrimary = yellow,
        secondary = orange, onSecondary = ink, secondaryContainer = bg2, onSecondaryContainer = orange,
        tertiary = aqua, onTertiary = ink, tertiaryContainer = bg2, onTertiaryContainer = aqua,
        background = bg, onBackground = fg, surface = bg, onSurface = fg,
        surfaceVariant = bg2, onSurfaceVariant = fg2, surfaceTint = Color.Transparent,
        inverseSurface = fg, inverseOnSurface = bg,
        error = red, onError = ink, errorContainer = bg2, onErrorContainer = red,
        outline = Color(0xFF928374), outlineVariant = bg3, scrim = Color.Black,
        surfaceBright = bg2, surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = bg1, surfaceContainer = bg1,
        surfaceContainerHigh = bg2, surfaceContainerHighest = bg3,
    )
}

// Linux: night-violet glass over a Hyprland-style gradient wallpaper, pink→cyan accents.
private fun linuxColorScheme(base: ColorScheme, accent: Color?): ColorScheme {
    val bg = Color(0xFF0D0B14)
    val fg = Color(0xFFECEAF5)
    val pink = accent ?: Color(0xFFEE66FF)
    return base.copy(
        primary = pink, onPrimary = Color(0xFF16101F), primaryContainer = Color(0xFF2A1F3A), onPrimaryContainer = pink, inversePrimary = pink,
        secondary = Color(0xFF33CCFF), onSecondary = Color(0xFF07121A), secondaryContainer = Color(0xFF1B2A3A), onSecondaryContainer = Color(0xFF33CCFF),
        tertiary = Color(0xFF9ECE6A), onTertiary = Color(0xFF0E1408), tertiaryContainer = Color(0xFF1E2A18), onTertiaryContainer = Color(0xFF9ECE6A),
        background = bg, onBackground = fg, surface = Color(0xFF14111F), onSurface = fg,
        surfaceVariant = Color(0xFF221D31), onSurfaceVariant = Color(0xFF8F8BA8), surfaceTint = Color.Transparent,
        inverseSurface = fg, inverseOnSurface = bg,
        error = Color(0xFFFF6B8A), onError = Color.Black, errorContainer = Color(0xFF3A1A24), onErrorContainer = Color(0xFFFF6B8A),
        outline = Color(0xFF3A3450), outlineVariant = Color(0xFF26213A), scrim = Color.Black,
        surfaceBright = Color(0xFF2A243B), surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF14111F), surfaceContainer = Color(0xFF1A1626),
        surfaceContainerHigh = Color(0xFF221D31), surfaceContainerHighest = Color(0xFF2A243B),
    )
}

private fun fontFamilyOf(font: String) = platformFontFamily(font) ?: when (font) {
    "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
    "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
    "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
    "minecraft" -> minecraftFont() ?: androidx.compose.ui.text.font.FontFamily.Monospace
    else -> null
}

private fun nocturneTypography(font: String): Typography {
    val family = fontFamilyOf(font)
    val plain = Typography()
    val base = if (family == null) plain else plain.copy(
        displayLarge = plain.displayLarge.copy(fontFamily = family), displayMedium = plain.displayMedium.copy(fontFamily = family),
        displaySmall = plain.displaySmall.copy(fontFamily = family), headlineLarge = plain.headlineLarge.copy(fontFamily = family),
        headlineMedium = plain.headlineMedium.copy(fontFamily = family), headlineSmall = plain.headlineSmall.copy(fontFamily = family),
        titleLarge = plain.titleLarge.copy(fontFamily = family), titleMedium = plain.titleMedium.copy(fontFamily = family),
        titleSmall = plain.titleSmall.copy(fontFamily = family), bodyLarge = plain.bodyLarge.copy(fontFamily = family),
        bodyMedium = plain.bodyMedium.copy(fontFamily = family), bodySmall = plain.bodySmall.copy(fontFamily = family),
        labelLarge = plain.labelLarge.copy(fontFamily = family), labelMedium = plain.labelMedium.copy(fontFamily = family),
        labelSmall = plain.labelSmall.copy(fontFamily = family),
    )
    return base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

// SF-like iOS text styles mapped onto Material slots.
private fun iosTypography(font: String): Typography {
    val family = fontFamilyOf(font) ?: androidx.compose.ui.text.font.FontFamily.Default
    val p = Typography()
    fun TextStyle.ios(size: Int, line: Int, weight: FontWeight, track: Double) =
        copy(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = track.sp)
    return Typography(
        displayLarge = p.displayLarge.ios(34, 41, FontWeight.Bold, 0.37),
        displayMedium = p.displayMedium.ios(34, 41, FontWeight.Bold, 0.37),
        displaySmall = p.displaySmall.ios(34, 41, FontWeight.Bold, 0.37),
        headlineLarge = p.headlineLarge.ios(34, 41, FontWeight.Bold, 0.37),
        headlineMedium = p.headlineMedium.ios(34, 41, FontWeight.Bold, 0.37),
        headlineSmall = p.headlineSmall.ios(22, 28, FontWeight.Bold, -0.26),
        titleLarge = p.titleLarge.ios(20, 25, FontWeight.SemiBold, -0.45),
        titleMedium = p.titleMedium.ios(17, 22, FontWeight.SemiBold, -0.43),
        titleSmall = p.titleSmall.ios(15, 20, FontWeight.SemiBold, -0.23),
        bodyLarge = p.bodyLarge.ios(17, 22, FontWeight.Normal, -0.43),
        bodyMedium = p.bodyMedium.ios(15, 20, FontWeight.Normal, -0.23),
        bodySmall = p.bodySmall.ios(13, 18, FontWeight.Normal, -0.08),
        labelLarge = p.labelLarge.ios(15, 20, FontWeight.SemiBold, -0.23),
        labelMedium = p.labelMedium.ios(12, 16, FontWeight.Medium, 0.0),
        labelSmall = p.labelSmall.ios(11, 13, FontWeight.Medium, 0.06),
    )
}

// Fluent 2 type ramp: Caption 12, Body 14, BodyStrong 14, Subtitle 20, Title 28, TitleLarge 40, Display 68.
private fun fluentTypography(font: String): Typography {
    val family = fontFamilyOf(font) ?: androidx.compose.ui.text.font.FontFamily.Default
    val p = Typography()
    fun TextStyle.fluent(size: Int, line: Int, weight: FontWeight) =
        copy(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    return Typography(
        displayLarge = p.displayLarge.fluent(68, 92, FontWeight.SemiBold),
        displayMedium = p.displayMedium.fluent(40, 52, FontWeight.SemiBold),
        displaySmall = p.displaySmall.fluent(40, 52, FontWeight.SemiBold),
        headlineLarge = p.headlineLarge.fluent(28, 36, FontWeight.SemiBold),
        headlineMedium = p.headlineMedium.fluent(28, 36, FontWeight.SemiBold),
        headlineSmall = p.headlineSmall.fluent(20, 28, FontWeight.SemiBold),
        titleLarge = p.titleLarge.fluent(20, 28, FontWeight.SemiBold),
        titleMedium = p.titleMedium.fluent(14, 20, FontWeight.SemiBold),
        titleSmall = p.titleSmall.fluent(14, 20, FontWeight.SemiBold),
        bodyLarge = p.bodyLarge.fluent(14, 20, FontWeight.Normal),
        bodyMedium = p.bodyMedium.fluent(14, 20, FontWeight.Normal),
        bodySmall = p.bodySmall.fluent(12, 16, FontWeight.Normal),
        labelLarge = p.labelLarge.fluent(14, 20, FontWeight.Normal),
        labelMedium = p.labelMedium.fluent(12, 16, FontWeight.Normal),
        labelSmall = p.labelSmall.fluent(12, 16, FontWeight.Normal),
    )
}

// WinUI 3 neutrals (Mica fallbacks, layer fills, strokes) over the generated scheme.
private fun fluentColorScheme(base: ColorScheme, dark: Boolean, accent: Color, black: Boolean): ColorScheme {
    val bg = if (dark) (if (black) Color.Black else Color(0xFF202020)) else Color(0xFFF3F3F3)
    val layer = if (dark) (if (black) Color(0xFF141414) else Color(0xFF2B2B2B)) else Color(0xFFFBFBFB)
    val layer2 = if (dark) (if (black) Color(0xFF1C1C1C) else Color(0xFF2D2D2D)) else Color(0xFFF9F9F9)
    val layer3 = if (dark) (if (black) Color(0xFF242424) else Color(0xFF383838)) else Color(0xFFEDEDED)
    val text = if (dark) Color.White else Color(0xFF1B1B1B)
    val text2 = if (dark) Color(0xFFC8C8C8) else Color(0xFF5F5F5F)
    val stroke = if (dark) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    val stroke2 = if (dark) Color(0xFF1D1D1D) else Color(0xFFE9E9E9)
    // Dark mode uses the lighter accent shade with dark text, like WinUI's AccentFillColorDefault.
    val fill = if (dark) lerp(accent, Color.White, 0.38f) else lerp(accent, Color.Black, 0.12f)
    val onFill = if (dark) Color.Black else Color.White
    val tint = lerp(layer, accent, if (dark) 0.24f else 0.14f)
    val error = if (dark) Color(0xFFFF99A4) else Color(0xFFC42B1C)
    return base.copy(
        primary = fill, onPrimary = onFill, primaryContainer = tint, onPrimaryContainer = text, inversePrimary = accent,
        secondary = fill, onSecondary = onFill, secondaryContainer = tint, onSecondaryContainer = text,
        tertiary = fill, onTertiary = onFill, tertiaryContainer = tint, onTertiaryContainer = text,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = layer3, onSurfaceVariant = text2, surfaceTint = Color.Transparent,
        inverseSurface = if (dark) Color(0xFFF3F3F3) else Color(0xFF2B2B2B), inverseOnSurface = if (dark) Color(0xFF1B1B1B) else Color.White,
        error = error, onError = if (dark) Color.Black else Color.White, errorContainer = lerp(layer, error, 0.18f), onErrorContainer = error,
        outline = stroke, outlineVariant = stroke2, scrim = Color.Black,
        surfaceBright = layer2, surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = layer, surfaceContainer = layer,
        surfaceContainerHigh = layer2, surfaceContainerHighest = layer3,
    )
}

// Apple system colors over the generated scheme.
private fun iosColorScheme(base: ColorScheme, dark: Boolean, accent: Color): ColorScheme {
    val bg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val card = if (dark) Color(0xFF1C1C1E) else Color.White
    val card2 = if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val label = if (dark) Color.White else Color.Black
    val sep = if (dark) Color(0xFF38383A) else Color(0xFFC6C6C8)
    val error = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    val blue = if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val tint = lerp(card, accent, if (dark) 0.22f else 0.14f)
    return base.copy(
        primary = accent, onPrimary = Color.White, primaryContainer = tint, onPrimaryContainer = accent, inversePrimary = accent,
        secondary = accent, onSecondary = Color.White, secondaryContainer = tint, onSecondaryContainer = accent,
        tertiary = blue, onTertiary = Color.White, tertiaryContainer = lerp(card, blue, 0.2f), onTertiaryContainer = blue,
        background = bg, onBackground = label, surface = bg, onSurface = label,
        surfaceVariant = card2, onSurfaceVariant = Color(0xFF8E8E93), surfaceTint = accent,
        inverseSurface = if (dark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E), inverseOnSurface = if (dark) Color.Black else Color.White,
        error = error, onError = Color.White, errorContainer = lerp(card, error, 0.2f), onErrorContainer = error,
        outline = sep, outlineVariant = sep, scrim = Color.Black,
        surfaceBright = card2, surfaceDim = bg,
        surfaceContainerLowest = bg, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, surfaceContainerHighest = card2,
    )
}

// Main violet of the app icon (sampled from docs/icon-1024.png).
private val IconViolet = Color(0xFF8B5CF6)
private val IconVioletDark = Color(0xFF9F7AFA)
private val DefaultAccent = Settings().accentColor

private val IosRipple = RippleConfiguration(rippleAlpha = RippleAlpha(draggedAlpha = 0.04f, focusedAlpha = 0.04f, hoveredAlpha = 0.03f, pressedAlpha = 0.07f))

@Composable
fun isDark(settings: Settings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun NocturneTheme(
    settings: Settings,
    seedOverride: Color? = null,
    forceDark: Boolean? = null,
    backdrop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark ?: isDark(settings)
    val platformScheme = if (settings.dynamicColor && seedOverride == null) platformDynamicColorScheme(dark) else null
    val seed = seedOverride
        ?: (if (settings.dynamicColor) platformAccentColor() else null)
        ?: Color(settings.accentColor)
    val generated = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = settings.pureBlack,
        style = when {
            seedOverride == null -> PaletteStyle.entries.firstOrNull { it.name == settings.colorStyle } ?: PaletteStyle.TonalSpot
            seedOverride.isGrayish() -> PaletteStyle.Monochrome
            else -> PaletteStyle.Vibrant
        },
    )
    var scheme = platformScheme ?: generated
    if (dark && settings.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0B0D),
            surfaceContainer = Color(0xFF111114),
        )
    }
    val style = settings.designStyle
    if (style != DesignStyle.MATERIAL && seedOverride == null) {
        // Keep a user-picked accent, default to the icon violet.
        val accent = if (settings.accentColor != DefaultAccent) Color(settings.accentColor) else if (dark) IconVioletDark else IconViolet
        scheme = when (style) {
            DesignStyle.GLASS -> iosColorScheme(scheme, dark, accent)
            DesignStyle.ARCH -> archColorScheme(scheme, dark, Color(settings.accentColor).takeIf { settings.accentColor != DefaultAccent })
            DesignStyle.LINUX -> linuxColorScheme(scheme, Color(settings.accentColor).takeIf { settings.accentColor != DefaultAccent })
            else -> fluentColorScheme(scheme, dark, accent, dark && settings.pureBlack)
        }
    }
    val typography = when (style) {
        DesignStyle.GLASS -> iosTypography(settings.fontStyle)
        DesignStyle.WINDOWS -> fluentTypography(settings.fontStyle)
        DesignStyle.ARCH -> archTypography()
        DesignStyle.LINUX -> fluentTypography(settings.fontStyle)
        DesignStyle.MATERIAL -> nocturneTypography(settings.fontStyle)
    }
    val shapes = when (style) {
        DesignStyle.WINDOWS -> FluentShapes
        DesignStyle.ARCH -> ArchShapes
        else -> NocturneShapes
    }
    CompositionLocalProvider(
        LocalDarkTheme provides dark, LocalBackdrop provides backdrop, LocalDesignStyle provides style,
        LocalPixelIcons provides (settings.fontStyle == "minecraft"),
        app.nocturne.ui.kit.LocalFluentIcons provides settings.fluentIcons,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
            if (style != DesignStyle.MATERIAL) {
                CompositionLocalProvider(LocalContentColor provides scheme.onBackground, LocalRippleConfiguration provides IosRipple, content = content)
            } else {
                CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
            }
        }
    }
}

val MarqueeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)

private fun Color.isGrayish(): Boolean {
    val mx = maxOf(red, green, blue)
    val mn = minOf(red, green, blue)
    return mx - mn < 0.07f || (mx > 0f && (mx - mn) / mx < 0.12f)
}

expect fun minecraftFont(): androidx.compose.ui.text.font.FontFamily?

// Platform-specific family for a font setting, or null to use the generic one.
expect fun platformFontFamily(font: String): androidx.compose.ui.text.font.FontFamily?
