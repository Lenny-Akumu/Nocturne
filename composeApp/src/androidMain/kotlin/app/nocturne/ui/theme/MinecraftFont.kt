package app.nocturne.ui.theme

import android.annotation.SuppressLint
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.nocturne.AndroidContext

private val family: FontFamily? by lazy {
    val ctx = AndroidContext.app
    @SuppressLint("DiscouragedApi")
    val id = ctx.resources.getIdentifier("monocraft", "font", ctx.packageName)
    if (id == 0) null else FontFamily(Font(id))
}

actual fun minecraftFont(): FontFamily? = family
