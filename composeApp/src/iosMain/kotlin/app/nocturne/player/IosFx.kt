@file:OptIn(ExperimentalForeignApi::class)

package app.nocturne.player

import kotlin.concurrent.Volatile
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.setAudioMix
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreMedia.CMItemCount
import platform.CoreMedia.CMItemCountVar
import platform.MediaToolbox.MTAudioProcessingTapCallbacks
import platform.MediaToolbox.MTAudioProcessingTapCreate
import platform.MediaToolbox.MTAudioProcessingTapFlags
import platform.MediaToolbox.MTAudioProcessingTapFlagsVar
import platform.MediaToolbox.MTAudioProcessingTapGetSourceAudio
import platform.MediaToolbox.MTAudioProcessingTapRef
import platform.MediaToolbox.MTAudioProcessingTapRefVar
import platform.MediaToolbox.kMTAudioProcessingTapCallbacksVersion_0
import platform.MediaToolbox.kMTAudioProcessingTapCreationFlag_PostEffects
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// Effects for AVPlayer through an MTAudioProcessingTap (progressive / local files; HLS gets no tap).
internal object IosFx {
    @Volatile private var dsp: Dsp? = null
    @Volatile private var interleaved = false
    @Volatile var active = false
        private set
    private var scratch = FloatArray(4096)

    private var eqOn = false
    private var gains = emptyList<Float>()
    private var normalize = false
    private var spatial = false
    private var eightD = false
    private var vocal = false

    fun setEq(on: Boolean, g: List<Float>) { eqOn = on; gains = g; dsp?.setEq(on, g) }

    fun setEffects(n: Boolean, s: Boolean, e: Boolean, v: Boolean) {
        normalize = n; spatial = s; eightD = e; vocal = v
        dsp?.let { apply(it) }
    }

    private fun apply(d: Dsp) {
        d.setEq(eqOn, gains)
        d.normalize = normalize; d.spatial = spatial; d.eightD = eightD; d.vocalRemover = vocal
    }

    fun levels(): FloatArray? = if (active) dsp?.analyzer?.levels() else null

    fun prepare(rate: Int, nonInterleaved: Boolean, float: Boolean) {
        if (!float) { active = false; return }
        interleaved = !nonInterleaved
        val d = dsp?.takeIf { it.rate == rate } ?: Dsp(rate).apply { analyzer.latencyMs = 100 }
        apply(d)
        dsp = d
        active = true
    }

    fun unprepare() { active = false }

    fun process(list: CPointer<AudioBufferList>, frames: Int) {
        val d = dsp ?: return
        if (!active || frames <= 0) return
        val abl = list.pointed
        val n = abl.mNumberBuffers.toInt()
        if (n == 0) return
        if (scratch.size < frames * 2) scratch = FloatArray(frames * 2)
        val buf = scratch
        if (interleaved) {
            val b = abl.mBuffers[0]
            val p = b.mData?.reinterpret<FloatVar>() ?: return
            if (b.mNumberChannels.toInt() != 2) return
            for (i in 0 until frames * 2) buf[i] = p[i]
            d.process(buf, frames)
            for (i in 0 until frames * 2) p[i] = buf[i]
        } else {
            val l = abl.mBuffers[0].mData?.reinterpret<FloatVar>() ?: return
            val r = if (n > 1) abl.mBuffers[1].mData?.reinterpret<FloatVar>() ?: l else l
            for (i in 0 until frames) { buf[2 * i] = l[i]; buf[2 * i + 1] = r[i] }
            d.process(buf, frames)
            for (i in 0 until frames) { l[i] = buf[2 * i]; if (r != l) r[i] = buf[2 * i + 1] }
        }
    }

    /** Hooks the tap into [item] once the asset's tracks are known. */
    fun attach(item: AVPlayerItem, asset: AVAsset) {
        asset.loadValuesAsynchronouslyForKeys(listOf("tracks")) {
            dispatch_async(dispatch_get_main_queue()) {
                val track = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return@dispatch_async
                val tap = createTap() ?: return@dispatch_async
                val params = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track)
                params.setAudioTapProcessor(tap)
                val mix = AVMutableAudioMix()
                mix.setInputParameters(listOf(params))
                item.setAudioMix(mix)
            }
        }
    }

    private fun createTap(): MTAudioProcessingTapRef? = memScoped {
        val cb = alloc<MTAudioProcessingTapCallbacks>()
        cb.version = kMTAudioProcessingTapCallbacksVersion_0
        cb.clientInfo = null
        cb.init = null
        cb.finalize = null
        cb.prepare = staticCFunction(::onPrepare)
        cb.unprepare = staticCFunction(::onUnprepare)
        cb.process = staticCFunction(::onProcess)
        val out = alloc<MTAudioProcessingTapRefVar>()
        val err = MTAudioProcessingTapCreate(kCFAllocatorDefault, cb.ptr, kMTAudioProcessingTapCreationFlag_PostEffects, out.ptr)
        if (err != 0) null else out.value
    }
}

private fun onPrepare(tap: MTAudioProcessingTapRef?, maxFrames: CMItemCount, format: CPointer<AudioStreamBasicDescription>?) {
    val f = format?.pointed ?: return
    IosFx.prepare(f.mSampleRate.toInt(), (f.mFormatFlags and kAudioFormatFlagIsNonInterleaved) != 0u, (f.mFormatFlags and kAudioFormatFlagIsFloat) != 0u)
}

private fun onUnprepare(tap: MTAudioProcessingTapRef?) = IosFx.unprepare()

private fun onProcess(
    tap: MTAudioProcessingTapRef?,
    frames: CMItemCount,
    flags: MTAudioProcessingTapFlags,
    list: CPointer<AudioBufferList>?,
    framesOut: CPointer<CMItemCountVar>?,
    flagsOut: CPointer<MTAudioProcessingTapFlagsVar>?,
) {
    if (MTAudioProcessingTapGetSourceAudio(tap, frames, list, flagsOut, null, framesOut) != 0) return
    val n = framesOut?.pointed?.value ?: frames
    list?.let { IosFx.process(it, n.toInt()) }
}
