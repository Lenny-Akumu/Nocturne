package app.nocturne.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

private class PickerDelegate(val onImage: (UIImage?) -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, null)
        onImage(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        onImage(null)
    }
}

// the picker only holds its delegate weakly
private var activeDelegate: PickerDelegate? = null

private fun topController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

@OptIn(ExperimentalForeignApi::class)
private fun shrink(img: UIImage, max: Double = 512.0): ByteArray? {
    val (w, h) = img.size.useContents { width to height }
    val k = minOf(1.0, max / maxOf(w, h))
    val nw = (w * k).coerceAtLeast(1.0)
    val nh = (h * k).coerceAtLeast(1.0)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(nw, nh), true, 1.0)
    img.drawInRect(CGRectMake(0.0, 0.0, nw, nh))
    val small = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return (small ?: img).let { UIImageJPEGRepresentation(it, 0.88) }?.toBytes()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val cb = rememberUpdatedState(onPicked)
    return remember<() -> Unit> {
        {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            val d = PickerDelegate { img ->
                activeDelegate = null
                img?.let(::shrink)?.let { cb.value(it) }
            }
            activeDelegate = d
            picker.delegate = d
            topController()?.presentViewController(picker, true, null)
            Unit
        }
    }
}
