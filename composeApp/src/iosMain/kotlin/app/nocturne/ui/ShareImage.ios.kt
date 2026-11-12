package app.nocturne.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

actual fun ImageBitmap.encodePng(): ByteArray =
    org.jetbrains.skia.Image.makeFromBitmap(asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual suspend fun shareImage(png: ByteArray, name: String): String? {
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return null
    withContext(Dispatchers.Main) {
        val sheet = UIActivityViewController(listOf(image), null)
        var vc: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        sheet.popoverPresentationController?.sourceView = vc?.view
        vc?.presentViewController(sheet, animated = true, completion = null)
    }
    return null
}
