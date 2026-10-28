@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.nocturne.data

import app.nocturne.documentsDir
import app.nocturne.source.InnerTube
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData
import platform.UIKit.UIApplication
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

private val client by lazy { HttpClient() }

actual fun downloadsDir(): String = "$documentsDir/downloads".also {
    NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)
}

actual fun fileUri(path: String): String = NSURL.fileURLWithPath(path).absoluteString ?: "file://$path"

actual fun deleteFile(path: String) {
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}

actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun moveFile(from: String, to: String): Boolean = NSFileManager.defaultManager.moveItemAtPath(from, to, null)

private const val CHUNK = 4L * 1024 * 1024

actual suspend fun downloadToFile(url: String, path: String, onProgress: (Float) -> Unit): Long {
    val part = "$path.part"
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(part, null)
    fm.createFileAtPath(part, null, null)
    val handle = NSFileHandle.fileHandleForWritingAtPath(part) ?: error("Couldn't create the file")
    var done = 0L
    var total = -1L
    try {
        while (total < 0 || done < total) {
            val r = client.get(url) {
                header(HttpHeaders.UserAgent, app.nocturne.source.IosBackend.uaFor(url) ?: InnerTube.USER_AGENT)
                header(HttpHeaders.Range, "bytes=$done-${done + CHUNK - 1}")
            }
            if (!r.status.isSuccess()) error("HTTP ${r.status.value}")
            if (total < 0) total = r.headers[HttpHeaders.ContentRange]?.substringAfter('/')?.toLongOrNull()
                ?: r.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
            val bytes = r.bodyAsBytes()
            if (bytes.isEmpty()) break
            handle.seekToEndOfFile()
            bytes.usePinned { handle.writeData(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())) }
            done += bytes.size
            if (total > 0) onProgress(done.toFloat() / total)
            if (r.status.value == 200) break
        }
    } finally {
        handle.closeFile()
    }
    fm.removeItemAtPath(path, null)
    if (!fm.moveItemAtPath(part, path, null)) error("Couldn't save the file")
    return done
}

private var notificationsAsked = false

private fun notify(heading: String, text: String) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    if (!notificationsAsked) {
        notificationsAsked = true
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }
    val content = UNMutableNotificationContent()
    content.setTitle(heading)
    content.setBody(text)
    center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("nocturne-${heading.hashCode()}", content, null), null)
}

actual fun notifyRelease(title: String, text: String) = notify(title, text)

actual fun notifyUpdateAvailable(version: String, notes: String) = notify(app.nocturne.i18n.t("Nocturne %s is available", version), app.nocturne.i18n.t("Open Nocturne to get the new version."))

actual fun notifyUpdateProgress(version: String, progress: Float?) = Unit

actual suspend fun downloadAndInstall(url: String, fileName: String, onProgress: (Float) -> Unit) {
    NSURL.URLWithString("https://github.com/Lenny-Akumu/Nocturne/releases/latest")?.let {
        UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
    }
}

actual fun isUpdateAsset(name: String): Boolean = name.endsWith(".ipa")
