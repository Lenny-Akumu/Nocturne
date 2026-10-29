package app.nocturne

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.win32.StdCallLibrary
import java.awt.Rectangle

private interface WndProc : StdCallLibrary.StdCallCallback {
    fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
}

private interface U32 : StdCallLibrary {
    fun SetWindowLongPtrW(hwnd: HWND, index: Int, proc: WndProc): Pointer?
    fun CallWindowProcW(prev: Pointer, hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun GetWindowRect(hwnd: HWND, rect: WinDef.RECT): Boolean
    fun IsZoomed(hwnd: HWND): Boolean
    fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
    fun GetDpiForWindow(hwnd: HWND): Int
    fun SetWindowPos(hwnd: HWND, after: HWND?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    fun SetWindowRgn(hwnd: HWND, rgn: Pointer?, redraw: Boolean): Int
    fun IsIconic(hwnd: HWND): Boolean

    companion object {
        val INSTANCE: U32? = runCatching { Native.load("user32", U32::class.java) }.getOrNull()
    }
}

private interface Gdi : StdCallLibrary {
    fun CreateRoundRectRgn(x1: Int, y1: Int, x2: Int, y2: Int, w: Int, h: Int): Pointer?

    companion object {
        val INSTANCE: Gdi? = runCatching { Native.load("gdi32", Gdi::class.java) }.getOrNull()
    }
}

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int

    companion object {
        val INSTANCE: DwmApi? = runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }
}

/**
 * Windows: drops the system title bar but keeps the real frame (snap, shadow, resize, Mica).
 * The top [captionPx] strip drags the window except over [buttons], which Compose handles.
 */
object CustomFrame {
    @Volatile var captionPx = 0
    @Volatile var buttons = Rectangle()
    private var proc: WndProc? = null
    private var hwndRef: HWND? = null
    /** Our own rounded region; off while a DWM backdrop is on (regions can switch it off). */
    @Volatile var regionCorners = true
        set(v) { field = v; hwndRef?.let { h -> U32.INSTANCE?.let { round(it, h) } } }
    val installed: Boolean get() = proc != null
    private var prev: Pointer? = null

    /** Corner radius in dp; DWM's own rounding is only ~8 px. */
    const val RADIUS_DP = 14
    /** Set by the window morph: system minimize / restore go through it instead. */
    @Volatile var onMinimize: (() -> Boolean)? = null
    @Volatile var onRestore: (() -> Boolean)? = null

    private const val WM_SIZE = 0x0005
    private const val WM_SYSCOMMAND = 0x0112
    private const val SC_MINIMIZE = 0xF020
    private const val SC_RESTORE = 0xF120
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val HTCLIENT = 1
    private const val HTCAPTION = 2
    private const val HTLEFT = 10
    private const val HTRIGHT = 11
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val HTBOTTOM = 15
    private const val HTBOTTOMLEFT = 16
    private const val HTBOTTOMRIGHT = 17

    fun install(window: java.awt.Window): Boolean {
        if (!isWindows || proc != null) return proc != null
        val u = U32.INSTANCE ?: return false
        val hwnd = runCatching { HWND(Native.getComponentPointer(window)) }.getOrNull() ?: return false
        val p = object : WndProc {
            override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
                when (msg) {
                    WM_NCCALCSIZE -> if (wParam.toInt() != 0) {
                        if (u.IsZoomed(hwnd)) {
                            // maximized windows overhang the monitor by the frame width
                            val dpi = u.GetDpiForWindow(hwnd).takeIf { it > 0 } ?: 96
                            val fx = u.GetSystemMetricsForDpi(32, dpi) + u.GetSystemMetricsForDpi(92, dpi)
                            val fy = u.GetSystemMetricsForDpi(33, dpi) + u.GetSystemMetricsForDpi(92, dpi)
                            val r = lParam.toPointer()
                            r.setInt(0, r.getInt(0) + fx); r.setInt(4, r.getInt(4) + fy)
                            r.setInt(8, r.getInt(8) - fx); r.setInt(12, r.getInt(12) - fy)
                        }
                        return LRESULT(0)
                    }
                    WM_NCHITTEST -> return LRESULT(hitTest(u, hwnd, lParam).toLong())
                    WM_SYSCOMMAND -> when (wParam.toInt() and 0xFFF0) {
                        SC_MINIMIZE -> if (onMinimize?.invoke() == true) return LRESULT(0)
                        SC_RESTORE -> if (u.IsIconic(hwnd) && onRestore?.invoke() == true) return LRESULT(0)
                    }
                    WM_SIZE -> {
                        val r = u.CallWindowProcW(prev!!, hwnd, msg, wParam, lParam)
                        round(u, hwnd)
                        return r
                    }
                }
                return u.CallWindowProcW(prev!!, hwnd, msg, wParam, lParam)
            }
        }
        prev = u.SetWindowLongPtrW(hwnd, -4, p) ?: return false
        hwndRef = hwnd
        proc = p
        DwmApi.INSTANCE?.let { d ->
            // our own morph replaces the system minimize/restore animation; corners come from the region
            d.DwmSetWindowAttribute(hwnd, 3, com.sun.jna.ptr.IntByReference(1).pointer, 4)
            d.DwmSetWindowAttribute(hwnd, 33, com.sun.jna.ptr.IntByReference(1).pointer, 4)
        }
        // SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED
        u.SetWindowPos(hwnd, null, 0, 0, 0, 0, 0x0002 or 0x0001 or 0x0004 or 0x0020)
        round(u, hwnd)
        return true
    }

    private fun round(u: U32, hwnd: HWND) {
        val g = Gdi.INSTANCE ?: return
        if (!regionCorners || u.IsZoomed(hwnd) || u.IsIconic(hwnd)) { u.SetWindowRgn(hwnd, null, true); return }
        val r = WinDef.RECT()
        u.GetWindowRect(hwnd, r)
        val dpi = u.GetDpiForWindow(hwnd).takeIf { it > 0 } ?: 96
        val d = 2 * RADIUS_DP * dpi / 96
        // the system owns the region after this call
        u.SetWindowRgn(hwnd, g.CreateRoundRectRgn(0, 0, r.right - r.left + 1, r.bottom - r.top + 1, d, d), true)
    }

    private fun hitTest(u: U32, hwnd: HWND, lParam: LPARAM): Int {
        val v = lParam.toLong()
        val sx = (v and 0xFFFF).toShort().toInt()
        val sy = ((v shr 16) and 0xFFFF).toShort().toInt()
        val r = WinDef.RECT()
        u.GetWindowRect(hwnd, r)
        val x = sx - r.left
        val y = sy - r.top
        val w = r.right - r.left
        val h = r.bottom - r.top
        val zoomed = u.IsZoomed(hwnd)
        if (!zoomed) {
            val dpi = u.GetDpiForWindow(hwnd).takeIf { it > 0 } ?: 96
            val b = 6 * dpi / 96
            val left = x < b; val right = x >= w - b; val top = y < b; val bottom = y >= h - b
            when {
                top && left -> return HTTOPLEFT
                top && right -> return HTTOPRIGHT
                bottom && left -> return HTBOTTOMLEFT
                bottom && right -> return HTBOTTOMRIGHT
                left -> return HTLEFT
                right -> return HTRIGHT
                top -> return HTTOP
                bottom -> return HTBOTTOM
            }
        }
        if (y < captionPx && !buttons.contains(x, y)) return HTCAPTION
        return HTCLIENT
    }
}
