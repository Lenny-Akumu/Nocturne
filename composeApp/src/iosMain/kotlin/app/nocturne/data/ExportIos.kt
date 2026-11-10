package app.nocturne.data

import app.nocturne.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

// iOS has no MP3 encoder either: the original audio goes to the share sheet (Save to Files, AirDrop, …).
actual val exportLabel: String = "Save to device"

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual suspend fun exportAudio(track: Track, file: String, ext: String): String? {
    val name = listOfNotNull(track.artistNames.takeIf { it.isNotBlank() }, track.title).joinToString(" - ")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "track" } + "." + ext
    val out = NSTemporaryDirectory() + name
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(out, null)
    if (!fm.copyItemAtPath(file, out, null)) error("Couldn't prepare the file")
    withContext(Dispatchers.Main) {
        val sheet = UIActivityViewController(listOf(NSURL.fileURLWithPath(out)), null)
        var vc: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        // iPad: anchor the popover to the middle of the screen
        sheet.popoverPresentationController?.sourceView = vc?.view
        vc?.presentViewController(sheet, animated = true, completion = null)
    }
    return null
}
