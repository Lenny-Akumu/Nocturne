package app.nocturne.ui

import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import app.nocturne.ui.theme.platformAccentColor

actual fun systemIconColors(): IconColors? = platformAccentColor()?.let(::iconColorsFrom)

// Window, floating player and tray icons follow the settings in Main.kt; the exe/installer icon stays default.
actual fun applyAppIcon(background: IconBackground, palette: IconPalette) = Unit
