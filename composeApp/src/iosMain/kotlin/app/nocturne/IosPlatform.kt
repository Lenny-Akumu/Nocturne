@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalUnsignedTypes::class)

package app.nocturne

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.countryCode
import platform.Foundation.create
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun currentHour(): Int = NSCalendar.currentCalendar.component(NSCalendarUnitHour, NSDate()).toInt()

actual fun minuteOfDay(): Int = NSCalendar.currentCalendar.let {
    it.component(NSCalendarUnitHour, NSDate()).toInt() * 60 + it.component(platform.Foundation.NSCalendarUnitMinute, NSDate()).toInt()
}

actual fun currentMonthDay(): Pair<Int, Int> = NSCalendar.currentCalendar.let {
    it.component(platform.Foundation.NSCalendarUnitMonth, NSDate()).toInt() to it.component(platform.Foundation.NSCalendarUnitDay, NSDate()).toInt()
}

val documentsDir: String by lazy {
    (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: "."
}

actual object FileStore {
    private fun path(name: String) = "$documentsDir/$name"

    actual fun read(name: String): String? =
        NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null)

    actual fun write(name: String, content: String) {
        NSString.create(string = content).writeToFile(path(name), true, NSUTF8StringEncoding, null)
    }

    actual fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath(path(name), null)
    }
}

actual val platformKind: PlatformKind = PlatformKind.IOS

private fun hash(bytes: ByteArray, size: Int, fn: (kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>, UInt, kotlinx.cinterop.CPointer<kotlinx.cinterop.UByteVar>) -> Unit): ByteArray {
    val input = if (bytes.isEmpty()) ByteArray(1) else bytes
    val out = UByteArray(size)
    input.usePinned { i -> out.usePinned { o -> fn(i.addressOf(0), bytes.size.toUInt(), o.addressOf(0)) } }
    return out.toByteArray()
}

actual fun sha1Hex(text: String): String =
    hash(text.encodeToByteArray(), CC_SHA1_DIGEST_LENGTH) { i, n, o -> CC_SHA1(i, n, o) }
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

actual fun sha256(bytes: ByteArray): ByteArray = hash(bytes, CC_SHA256_DIGEST_LENGTH) { i, n, o -> CC_SHA256(i, n, o) }

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { b ->
    if (size > 0) b.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
}

actual val spotifyRedirectUri: String = "nocturne://spotify-callback"

private class AuthAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

private var authSession: ASWebAuthenticationSession? = null
private val anchor = AuthAnchor()

actual suspend fun authorizeInBrowser(url: String): String = withContext(Dispatchers.Main) {
    val result = CompletableDeferred<String>()
    val session = ASWebAuthenticationSession(NSURL.URLWithString(url)!!, "nocturne") { callback, error ->
        val u = callback?.absoluteString
        // code 1 = ASWebAuthenticationSessionErrorCodeCanceledLogin
        if (u != null) result.complete(u)
        else result.completeExceptionally(IllegalStateException(if (error == null || error.code == 1L) app.nocturne.i18n.t("Sign-in was cancelled") else error.localizedDescription))
    }
    session.presentationContextProvider = anchor
    session.prefersEphemeralWebBrowserSession = false
    authSession = session
    if (!session.start()) {
        authSession = null
        throw IllegalStateException(app.nocturne.i18n.t("Couldn't open the sign-in page"))
    }
    try {
        result.await()
    } finally {
        if (!result.isCompleted) session.cancel()
        authSession = null
    }
}

actual fun systemLocale(): Pair<String, String> {
    val l = NSLocale.currentLocale
    return (l.languageCode.ifBlank { "en" }) to (l.countryCode ?: "US")
}

actual fun aesEncrypt(key: ByteArray, plain: ByteArray): ByteArray = throw UnsupportedOperationException("Not available on iPhone yet")

actual fun aesDecrypt(key: ByteArray, data: ByteArray): ByteArray = throw UnsupportedOperationException("Not available on iPhone yet")

actual fun deviceName(): String = platform.UIKit.UIDevice.currentDevice.name.ifBlank { "iPhone" }
