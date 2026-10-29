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
        val glass = supportsBackdrop && backdrop != WindowBackdrop.NONE && window.findSkiaLayer()?.transparency == true
        CustomFrame.regionCorners = !glass
        dwm.set(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, if (CustomFrame.installed && !glass) 1 else 2)
        if (!supportsBackdrop) report("Windows build $build: Mica / Acrylic need Windows 11 (22000+)")
        if (!supportsBackdrop || backdrop == WindowBackdrop.NONE) {
            if (supportsBackdrop) dwm.set(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, 1)
            // keeps the DWM shadow on a window without a title bar
            if (CustomFrame.installed) dwm.DwmExtendFrameIntoClientArea(hwnd, Margins(0, 0, 1, 0))
            return false
        }
        // not prepared before the first show: stay opaque rather than paint black
        if (window.findSkiaLayer()?.transparency != true) { report("Off: $prepared (restart to apply)"); return false }
        // the Swing panes under the Compose layer paint an opaque fill that hides the backdrop
        window.makePanesTransparent()
        val type = when (backdrop) {
            WindowBackdrop.MICA -> 2
            WindowBackdrop.ACRYLIC -> 3
            WindowBackdrop.MICA_ALT -> 4
            WindowBackdrop.NONE -> 1
        }
        val ext = dwm.DwmExtendFrameIntoClientArea(hwnd, Margins())
        val hr = dwm.set(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, type)
        report("$prepared · DWM extend 0x${Integer.toHexString(ext)}, backdrop 0x${Integer.toHexString(hr)}, frame ${if (CustomFrame.installed) "custom" else "system"}")
        return hr == 0
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

    /**
     * Direct3D fixes the swap chain's alpha mode when the layer is first attached, and re-attaching a live
     * layer leaves Compose's GPU layers pointing at a dead context (native crash). So transparency is only
     * switched on before the window is shown; a backdrop picked later takes effect after a restart.
     */
    fun prepare(window: ComposeWindow, backdrop: WindowBackdrop) {
        if (!supportsBackdrop || backdrop == WindowBackdrop.NONE) return
        val layer = window.findSkiaLayer()
        if (layer == null) { prepared = "no Skia layer"; return }
        if (!layer.isDisplayable) layer.transparency = true
        prepared = "layer ${if (layer.transparency) "transparent" else "opaque (already shown)"}, ${layer.renderApi}"
    }

    private var prepared = "not prepared"
    private var armed = false

    /**
     * Compose builds and attaches the window before our content runs, so the layer is caught as it is added
     * to its container (still not displayable) and made transparent there.
     */
    fun arm(backdrop: WindowBackdrop) {
        if (armed || !supportsBackdrop || backdrop == WindowBackdrop.NONE) return
        armed = true
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener({ e ->
            val ce = e as? java.awt.event.ContainerEvent ?: return@addAWTEventListener
            if (ce.id != java.awt.event.ContainerEvent.COMPONENT_ADDED) return@addAWTEventListener
            val layer = ce.child as? SkiaLayer ?: (ce.child as? Container)?.findSkiaLayer() ?: return@addAWTEventListener
            if (!layer.isDisplayable && !layer.transparency) layer.transparency = true
        }, java.awt.AWTEvent.CONTAINER_EVENT_MASK)
    }

    private fun report(s: String) { app.nocturne.ui.WindowChrome.effectStatus = s }

    private fun Container.findSkiaLayer(): SkiaLayer? {
        for (c in components) {
            if (c is SkiaLayer) return c
            if (c is Container) c.findSkiaLayer()?.let { return it }
        }
        return null
    }
}
