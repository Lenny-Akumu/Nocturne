package app.nocturne.data

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.nocturne.AndroidContext
import app.nocturne.downloadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun downloadAndInstall(url: String, fileName: String, onProgress: (Float) -> Unit) {
    val ctx = AndroidContext.app
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        error(app.nocturne.i18n.t("Allow Nocturne to install apps, then tap Update again"))
    }
    val file = File(File(ctx.cacheDir, "updates"), fileName)
    withContext(Dispatchers.IO) { downloadFile(url, file, onProgress) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private const val CHANNEL = "updates"
private const val NOTIFICATION_ID = 4210

private fun manager(): android.app.NotificationManager? {
    val ctx = AndroidContext.app
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return null
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
        nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, app.nocturne.i18n.t("App updates"), android.app.NotificationManager.IMPORTANCE_DEFAULT))
    }
    return nm
}

private fun openApp(): android.app.PendingIntent {
    val ctx = AndroidContext.app
    val intent = Intent(ctx, app.nocturne.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return android.app.PendingIntent.getActivity(ctx, 7, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
}

actual fun notifyUpdateAvailable(version: String, notes: String) {
    val nm = manager() ?: return
    val n = androidx.core.app.NotificationCompat.Builder(AndroidContext.app, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(app.nocturne.i18n.t("Nocturne %s is available", version))
        .setContentText(app.nocturne.i18n.t("Tap to update"))
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()
    runCatching { nm.notify(NOTIFICATION_ID, n) }
}

actual fun notifyUpdateProgress(version: String, progress: Float?) {
    val nm = manager() ?: return
    if (progress == null) {
        nm.cancel(NOTIFICATION_ID)
        return
    }
    val n = androidx.core.app.NotificationCompat.Builder(AndroidContext.app, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(app.nocturne.i18n.t("Downloading Nocturne %s", version))
        .setContentText("${(progress * 100).toInt()}%")
        .setProgress(100, (progress * 100).toInt(), false)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .build()
    runCatching { nm.notify(NOTIFICATION_ID, n) }
}

actual fun notifyRelease(title: String, text: String) {
    val ctx = AndroidContext.app
    val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("releases") == null) {
        nm.createNotificationChannel(android.app.NotificationChannel("releases", app.nocturne.i18n.t("New releases"), android.app.NotificationManager.IMPORTANCE_DEFAULT))
    }
    val n = androidx.core.app.NotificationCompat.Builder(ctx, "releases")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()
    runCatching { nm.notify(4300 + (title.hashCode() and 0xff), n) }
}
