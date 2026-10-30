package app.nocturne

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

private interface TbProc : StdCallLibrary.StdCallCallback {
    fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
}

private interface TbUser32 : StdCallLibrary {
    fun SetWindowLongPtrW(hwnd: HWND, index: Int, proc: TbProc): Pointer?
    fun CallWindowProcW(prev: Pointer, hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun RegisterWindowMessageW(name: com.sun.jna.WString): Int
    fun LoadImageW(inst: Pointer?, name: com.sun.jna.WString, type: Int, cx: Int, cy: Int, flags: Int): Pointer?
    fun GetSystemMetrics(index: Int): Int

    companion object {
        val INSTANCE: TbUser32? = runCatching { Native.load("user32", TbUser32::class.java) }.getOrNull()
    }
}

@Structure.FieldOrder("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
class ThumbButton : Structure() {
    @JvmField var dwMask = 0
    @JvmField var iId = 0
    @JvmField var iBitmap = 0
    @JvmField var hIcon: Pointer? = null
    @JvmField var szTip = CharArray(260)
    @JvmField var dwFlags = 0
}

/** Windows: ⏮ ⏯ ⏭ under the window thumbnail on the taskbar. */
object Taskbar {
    private const val WM_COMMAND = 0x0111
    private const val THBN_CLICKED = 0x1800
    private const val THB_ICON = 0x2
    private const val THB_TOOLTIP = 0x4
    private const val THB_FLAGS = 0x8
    private const val PREV = 1
    private const val PLAY = 2
    private const val NEXT = 3

    private var list: Pointer? = null
    private var hwnd: HWND? = null
    private var proc: TbProc? = null
    private var prev: Pointer? = null
    private var created = 0
    private var added = false
    private var playing = false
    private val icons = mutableMapOf<String, Pointer?>()
    var onClick: (Int) -> Unit = {}

    fun install(window: java.awt.Window, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
        if (!isWindows || proc != null) return
        val u = TbUser32.INSTANCE ?: return
        val h = runCatching { HWND(Native.getComponentPointer(window)) }.getOrNull() ?: return
        onClick = { id -> when (id) { PREV -> onPrev(); PLAY -> onToggle(); NEXT -> onNext() } }
        created = u.RegisterWindowMessageW(com.sun.jna.WString("TaskbarButtonCreated"))
        val p = object : TbProc {
            override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
                if (msg == WM_COMMAND && (wParam.toInt() ushr 16) and 0xFFFF == THBN_CLICKED) {
                    val id = wParam.toInt() and 0xFFFF
                    javax.swing.SwingUtilities.invokeLater { onClick(id) }
                    return LRESULT(0)
                }
                // Explorer restarted: the buttons have to be added again
                if (msg == created && created != 0) { added = false; javax.swing.SwingUtilities.invokeLater { addButtons() } }
                return u.CallWindowProcW(prev!!, hwnd, msg, wParam, lParam)
            }
        }
        prev = u.SetWindowLongPtrW(h, -4, p) ?: return
        proc = p
        hwnd = h
        javax.swing.SwingUtilities.invokeLater { addButtons() }
    }

    fun setPlaying(on: Boolean) {
        playing = on
        if (!added) return
        javax.swing.SwingUtilities.invokeLater { update() }
    }

    private fun taskbar(): Pointer? {
        list?.let { return it }
        Ole32.INSTANCE.CoInitializeEx(null, 0x2)
        val ref = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            Guid.GUID.fromString("{56FDF344-FD6D-11d0-958A-006097C9A090}"), null, 1,
            Guid.GUID.fromString("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}"), ref,
        )
        if (hr.toInt() != 0) return null
        val obj = ref.value ?: return null
        vcall(obj, 3) // HrInit
        list = obj
        return obj
    }

    private fun vcall(obj: Pointer, index: Int, vararg args: Any?): Int {
        val fn = Function.getFunction(obj.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE))
        return fn.invokeInt(arrayOf(obj, *args))
    }

    private fun buttons(): Array<Structure> {
        val arr = ThumbButton().toArray(3)
        listOf(Triple(PREV, "prev", "Previous"), Triple(PLAY, if (playing) "pause" else "play", if (playing) "Pause" else "Play"), Triple(NEXT, "next", "Next")).forEachIndexed { i, (id, icon, tip) ->
            val b = arr[i] as ThumbButton
            b.dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
            b.iId = id
            b.hIcon = icon(icon)
            val chars = app.nocturne.i18n.t(tip).toCharArray()
            chars.copyInto(b.szTip, 0, 0, minOf(chars.size, 259))
            b.dwFlags = 0
            b.write()
        }
        return arr
    }

    private fun addButtons() {
        if (added) return
        val h = hwnd ?: return
        val tb = taskbar() ?: return
        val arr = buttons()
        if (vcall(tb, 15, h, 3, arr[0].pointer) == 0) added = true // ThumbBarAddButtons
    }

    private fun update() {
        val h = hwnd ?: return
        val tb = taskbar() ?: return
        val arr = buttons()
        vcall(tb, 16, h, 3, arr[0].pointer) // ThumbBarUpdateButtons
    }

    private fun icon(name: String): Pointer? = icons.getOrPut(name) {
        val u = TbUser32.INSTANCE ?: return@getOrPut null
        val size = u.GetSystemMetrics(49).takeIf { it > 0 } ?: 16 // SM_CXSMICON
        val file = File(System.getProperty("java.io.tmpdir"), "nocturne-tb-$name-$size.ico")
        runCatching { file.writeBytes(ico(glyph(name, size * 2))) }
        u.LoadImageW(null, com.sun.jna.WString(file.absolutePath), 1, size, size, 0x10)
    }

    private fun glyph(name: String, s: Int): BufferedImage {
        val img = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        fun tri(x0: Double, x1: Double) = Polygon(intArrayOf((s * x0).toInt(), (s * x1).toInt(), (s * x0).toInt()), intArrayOf((s * 0.2).toInt(), s / 2, (s * 0.8).toInt()), 3)
        fun triL(x0: Double, x1: Double) = Polygon(intArrayOf((s * x1).toInt(), (s * x0).toInt(), (s * x1).toInt()), intArrayOf((s * 0.2).toInt(), s / 2, (s * 0.8).toInt()), 3)
        when (name) {
            "play" -> g.fill(tri(0.3, 0.8))
            "pause" -> { g.fillRect((s * 0.28).toInt(), (s * 0.2).toInt(), (s * 0.15).toInt(), (s * 0.6).toInt()); g.fillRect((s * 0.57).toInt(), (s * 0.2).toInt(), (s * 0.15).toInt(), (s * 0.6).toInt()) }
            "next" -> { g.fill(tri(0.2, 0.62)); g.fillRect((s * 0.66).toInt(), (s * 0.2).toInt(), (s * 0.12).toInt(), (s * 0.6).toInt()) }
            "prev" -> { g.fill(triL(0.38, 0.8)); g.fillRect((s * 0.22).toInt(), (s * 0.2).toInt(), (s * 0.12).toInt(), (s * 0.6).toInt()) }
        }
        g.stroke = BasicStroke(1f)
        g.dispose()
        return img
    }

    // .ico holding one PNG image (Vista+)
    private fun ico(img: BufferedImage): ByteArray {
        val png = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        val out = ByteArrayOutputStream()
        fun le16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        fun le32(v: Int) { le16(v and 0xffff); le16((v shr 16) and 0xffff) }
        le16(0); le16(1); le16(1)
        out.write(if (img.width >= 256) 0 else img.width); out.write(if (img.height >= 256) 0 else img.height); out.write(0); out.write(0)
        le16(1); le16(32); le32(png.size); le32(22)
        out.write(png)
        return out.toByteArray()
    }
}
