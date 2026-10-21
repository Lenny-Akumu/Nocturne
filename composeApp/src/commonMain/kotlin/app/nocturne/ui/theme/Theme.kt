package app.nocturne.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nocturne.data.Settings
import app.nocturne.data.ThemeMode
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

private fun nocturneTypography(font: String): Typography {
    val family = when (font) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
        else -> null
    }
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
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalBackdrop provides backdrop) {
        MaterialTheme(colorScheme = scheme, typography = nocturneTypography(settings.fontStyle), shapes = NocturneShapes) {
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

val MarqueeStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)

private fun Color.isGrayish(): Boolean {
    val mx = maxOf(red, green, blue)
    val mn = minOf(red, green, blue)
    return mx - mn < 0.07f || (mx > 0f && (mx - mn) / mx < 0.12f)
}
