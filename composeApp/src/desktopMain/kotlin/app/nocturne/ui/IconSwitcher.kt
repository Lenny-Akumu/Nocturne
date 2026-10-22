package app.nocturne.ui

import app.nocturne.data.IconBackground
import app.nocturne.data.IconPalette
import app.nocturne.ui.theme.platformAccentColor

actual fun systemIconColors(): IconColors? = platformAccentColor()?.let(::iconColorsFrom)

// Window, floating player, tray and (Windows) Start menu / desktop shortcut icons follow the settings in Main.kt.
actual fun applyAppIcon(background: IconBackground, palette: IconPalette) = Unit
