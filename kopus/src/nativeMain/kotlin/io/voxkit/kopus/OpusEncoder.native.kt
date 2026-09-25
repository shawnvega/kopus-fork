package io.voxkit.kopus

import kotlinx.cinterop.*
import opus.OPUS_OK
import opus.kopus_encoder_get_lookahead
import opus.kopus_encoder_set_bitrate
import opus.opus_encode
import opus.opus_encode_float
import opus.opus_encoder_create
import opus.opus_encoder_destroy

public actual fun Opus.encoder(
    sampleRate: SampleRate,
    channels: Channels,
    application: OpusApplication
): OpusEncoder = OpusEncoderImpl(sampleRate, channels, application)

@OptIn(ExperimentalForeignApi::class)
private class OpusEncoderImpl(
    sampleRate: SampleRate,
    channels: Channels,
    application: OpusApplication
) : OpusEncoder {

    private var encoder: CPointer<cnames.structs.OpusEncoder>? = null

    init {
        val result = memScoped {
            val error = alloc<IntVar>()
            encoder = opus_encoder_create(
                Fs = sampleRate.value,
                channels = channels.value,
                application = application.value,
                error = error.ptr
            )
            error.value
        }

        check(result == OPUS_OK) {
            "Failed to create Opus encoder: ${Opus.getErrorString(result)}"
        }
    }

    override fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray): Int {
        return pcm.usePinned { pinnedPcm ->
            output.usePinned { pinnedOutput ->
                opus_encode(
                    st = encoder,
                    pcm = pinnedPcm.addressOf(0),
                    frame_size = frameSize,
                    data = pinnedOutput.addressOf(0).reinterpret(),
                    max_data_bytes = output.size
                )
            }
        }
    }

    override fun encode(pcm: FloatArray, frameSize: Int, output: ByteArray): Int {
        return pcm.usePinned { pinnedPcm ->
            output.usePinned { pinnedOutput ->
                opus_encode_float(
                    st = encoder,
                    pcm = pinnedPcm.addressOf(0),
                    frame_size = frameSize,
                    data = pinnedOutput.addressOf(0).reinterpret(),
                    max_data_bytes = output.size
                )
            }
        }
    }

    override fun setBitrate(bitrate: Int) {
        val enc = checkNotNull(encoder) { "Encoder has been closed." }
        val result = kopus_encoder_set_bitrate(enc, bitrate)
        require(result == OPUS_OK) {
            "Failed to set Opus encoder bitrate: ${Opus.getErrorString(result)}"
        }
    }

    override fun getLookahead(): Int {
        val enc = checkNotNull(encoder) { "Encoder has been closed." }
        val lookahead = memScoped {
            val value = alloc<IntVar>()
            val result = kopus_encoder_get_lookahead(enc, value.ptr)
            require(result == OPUS_OK) {
                "Failed to get Opus encoder lookahead: ${Opus.getErrorString(result)}"
            }
            value.value
        }
        return lookahead
    }

    override fun close() {
        encoder ?: return
        opus_encoder_destroy(encoder)
        encoder = null
    }
}
