package io.voxkit.kopus

/**
 * OpusEncoder provides methods to encode PCM audio data to Opus format.
 */
public interface OpusEncoder : AutoCloseable {
    /**
     * Encodes 16-bit PCM samples to Opus.
     *
     * @param pcm Input PCM data as a [ShortArray] (interleaved if 2 channels.
     * @param frameSize Number of samples per channel in the frame. This must be an Opus frame size
     * for the encoder's sampling rate. For example, at 48 kHz the permitted values are
     * 120, 240, 480, 960, 1920, and 2880. Passing in a duration of less than
     * 10 ms (480 samples at 48 kHz) will prevent the encoder from using the LPC or hybrid modes.
     * @param output Output buffer for encoded Opus data.
     * @return Number of bytes written to [output].
     */
    public fun encode(pcm: ShortArray, frameSize: Int, output: ByteArray): Int

    /**
     * Encodes floating-point PCM samples to Opus.
     *
     * @param pcm Input PCM data as a [FloatArray] (interleaved if 2 channels).
     * @param frameSize Number of samples per channel in the frame. This must be an Opus frame size
     * for the encoder's sampling rate. For example, at 48 kHz the permitted values are
     * 120, 240, 480, 960, 1920, and 2880. Passing in a duration of less than
     * 10 ms (480 samples at 48 kHz) will prevent the encoder from using the LPC or hybrid modes.
     * @param output Output buffer for encoded Opus data.
     * @return Number of bytes written to [output].
     */
    public fun encode(pcm: FloatArray, frameSize: Int, output: ByteArray): Int

    /**
     * Sets the target bitrate, in bits per second, for subsequent encoding calls.
     *
     * Values from 500 to 512000 are meaningful, as well as the special values `OPUS_AUTO`
     * (-1000; let the encoder pick a bitrate based on the sample rate and channel count, the
     * default) and `OPUS_BITRATE_MAX` (-1; use as much rate as available, useful when the rate is
     * instead controlled by the output buffer size). Any other value less than or equal to 0 is
     * rejected. Values from 1 to 500 are clamped up to 500, and values above 300000 times the
     * number of channels are clamped down to that maximum; see
     * https://opus-codec.org/docs/opus_api-1.5/group__opus__encoderctls.html#gaa89264fd93c9da70362a0c9b96b9ca88.
     *
     * @param bitrate Target bitrate in bits per second, or the special values described above.
     */
    public fun setBitrate(bitrate: Int)

    /**
     * Returns the encoder's algorithmic delay ("lookahead"), in samples at the encoder's
     * configured sample rate. This is the number of samples of silence that were prepended to
     * the encoded signal, and is the value that must be recorded as an Ogg Opus stream's
     * pre-skip so a decoder can time-align its output with the original input; see
     * https://opus-codec.org/docs/opus_api-1.5/group__opus__encoderctls.html#ga48b3e5b2c1fe4ab4caa1b1f0e07ee61f.
     *
     * @return Number of lookahead samples.
     */
    public fun getLookahead(): Int

    public companion object {
        /**
         * Default buffer size for Opus encoded output. It's set to 4000 bytes, as recommended by
         * the Opus documentation: https://opus-codec.org/docs/opus_api-1.5/group__opus__encoder.html.
         */
        public const val DEFAULT_OUTPUT_BUFFER_SIZE: Int = 4000
    }
}

/**
 * Creates a new [OpusEncoder] instance.
 *
 * @param sampleRate Sample rate of input audio.
 * @param channels Number of audio channels: mono or stereo.
 * @param application Opus application mode.
 * @return An [OpusEncoder] instance.
 */
public expect fun Opus.encoder(
    sampleRate: SampleRate,
    channels: Channels,
    application: OpusApplication
): OpusEncoder