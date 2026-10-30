package app.nocturne.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual fun hasMicAccess(): Boolean = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted

actual fun requestMicAccess(onResult: (Boolean) -> Unit) {
    AVAudioSession.sharedInstance().requestRecordPermission { granted -> dispatch_async(dispatch_get_main_queue()) { onResult(granted) } }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun recordMicWav(seconds: Int): ByteArray? {
    if (!hasMicAccess()) return null
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayAndRecord, AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionDefaultToSpeaker, null)
    session.setActive(true, null)
    val path = NSTemporaryDirectory() + "nocturne-clip.wav"
    val url = NSURL.fileURLWithPath(path)
    val settings = mapOf<Any?, Any>(
        AVFormatIDKey to kAudioFormatLinearPCM.toInt(),
        AVSampleRateKey to 44100.0,
        AVNumberOfChannelsKey to 1,
        AVLinearPCMBitDepthKey to 16,
        AVLinearPCMIsFloatKey to false,
        AVLinearPCMIsBigEndianKey to false,
    )
    val rec = AVAudioRecorder(url, settings, null)
    try {
        if (!rec.recordForDuration(seconds.toDouble())) return null
        delay(seconds * 1000L + 300)
        rec.stop()
    } finally {
        session.setCategory(AVAudioSessionCategoryPlayback, null)
    }
    val data = NSData.create(contentsOfURL = url) ?: return null
    val n = data.length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}
