package app.nocturne

import androidx.compose.ui.awt.ComposeWindow
import app.nocturne.data.WindowBackdrop
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container

private interface Dwm : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    fun DwmExtendFrameIntoClientArea(hwnd: WinDef.HWND, margins: Margins): Int

    companion object {
        val INSTANCE: Dwm? = runCatching { Native.load("dwmapi", Dwm::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
    }
}

@Structure.FieldOrder("left", "right", "top", "bottom")
class Margins(@JvmField var left: Int = -1, @JvmField var right: Int = -1, @JvmField var top: Int = -1, @JvmField var bottom: Int = -1) : Structure()

object WindowEffects {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38

    private val build: Int by lazy {
        runCatching {
            com.sun.jna.platform.win32.Advapi32Util.registryGetStringValue(
                com.sun.jna.platform.win32.WinReg.HKEY_LOCAL_MACHINE,
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentBuildNumber",
            ).toInt()
        }.getOrDefault(0)
    }

    val supportsBackdrop: Boolean get() = isWindows && build >= 22000 && Dwm.INSTANCE != null

    fun apply(window: ComposeWindow, backdrop: WindowBackdrop, dark: Boolean): Boolean {
        if (!isWindows) return false
        val dwm = Dwm.INSTANCE ?: return false
        val hwnd = runCatching { WinDef.HWND(Native.getComponentPointer(window)) }.getOrNull() ?: return false
        dwm.set(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        dwm.set(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, 2)
        if (!supportsBackdrop || backdrop == WindowBackdrop.NONE) {
            if (supportsBackdrop) dwm.set(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, 1)
            window.findSkiaLayer()?.transparency = false
            return false
        }
        window.findSkiaLayer()?.transparency = true
        // the Swing panes under the Compose layer paint an opaque fill that hides the backdrop
        window.makePanesTransparent()
        dwm.DwmExtendFrameIntoClientArea(hwnd, Margins())
        val type = when (backdrop) {
            WindowBackdrop.MICA -> 2
            WindowBackdrop.ACRYLIC -> 3
            WindowBackdrop.MICA_ALT -> 4
            WindowBackdrop.NONE -> 1
        }
        return dwm.set(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, type) == 0
    }

    private fun Dwm.set(hwnd: WinDef.HWND, attr: Int, value: Int): Int {
        val ref = IntByReference(value)
        return DwmSetWindowAttribute(hwnd, attr, ref.pointer, 4)
    }

    private val clear = java.awt.Color(0, 0, 0, 0)

    private fun ComposeWindow.makePanesTransparent() {
        if (contentPane !is ClearPane) {
            val old = contentPane
            val pane = ClearPane().apply {
                name = "${rootPane.name}.contentPane"
                layout = java.awt.BorderLayout()
                background = clear
            }
            old.components.toList().forEach { c -> old.remove(c); pane.add(c, java.awt.BorderLayout.CENTER) }
            contentPane = pane
        }
        rootPane.background = clear
        rootPane.isOpaque = false
        layeredPane.isOpaque = false
        (contentPane as? javax.swing.JComponent)?.isOpaque = false
        revalidate()
        repaint()
    }

    // Clears to transparent instead of filling with the look-and-feel gray.
    private class ClearPane : javax.swing.JPanel() {
        override fun paint(g: java.awt.Graphics) {
            if (background.alpha != 255) {
                val g2 = g.create() as java.awt.Graphics2D
                g2.composite = java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC)
                g2.color = background
                g2.fillRect(0, 0, width, height)
                g2.dispose()
            }
            super.paint(g)
        }
    }

    private fun Container.findSkiaLayer(): SkiaLayer? {
        for (c in components) {
            if (c is SkiaLayer) return c
            if (c is Container) c.findSkiaLayer()?.let { return it }
        }
        return null
    }
}
