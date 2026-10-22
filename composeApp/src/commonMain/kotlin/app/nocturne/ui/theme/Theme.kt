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
        scheme = if (style == DesignStyle.GLASS) iosColorScheme(scheme, dark, accent) else fluentColorScheme(scheme, dark, accent, dark && settings.pureBlack)
    }
    val typography = when (style) {
        DesignStyle.GLASS -> iosTypography(settings.fontStyle)
        DesignStyle.WINDOWS -> fluentTypography(settings.fontStyle)
        DesignStyle.MATERIAL -> nocturneTypography(settings.fontStyle)
    }
    val shapes = if (style == DesignStyle.WINDOWS) FluentShapes else NocturneShapes
    CompositionLocalProvider(
        LocalDarkTheme provides dark, LocalBackdrop provides backdrop, LocalDesignStyle provides style,
        LocalPixelIcons provides (settings.fontStyle == "minecraft"),
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
