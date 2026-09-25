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
     * Accepts a bitrate from 500 up to 300000 times the number of channels, or one of the special
     * values [BITRATE_AUTO] (the default) and [BITRATE_MAX]. Values from 1 to 499 are clamped up
     * to 500, values above 300000 times the number of channels are clamped down to that maximum,
     * and any other value less than or equal to 0 is rejected; see
     * https://opus-codec.org/docs/opus_api-1.5/group__opus__encoderctls.html#gaa89264fd93c9da70362a0c9b96b9ca88.
     *
     * @param bitrate Target bitrate in bits per second, or [BITRATE_AUTO]/[BITRATE_MAX].
     * @throws IllegalArgumentException if libopus rejects [bitrate].
     * @throws IllegalStateException if the encoder has been closed.
     */
    public fun setBitrate(bitrate: Int)

    /**
     * Returns the encoder's algorithmic delay ("lookahead"), in samples at the encoder's
     * configured sample rate; see
     * https://opus-codec.org/docs/opus_api-1.5/group__opus__encoderctls.html#ga48b3e5b2c1fe4ab4caa1b1f0e07ee61f.
     *
     * An Ogg Opus stream's pre-skip (RFC 7845, section 5.1) is always counted at 48 kHz, so when
     * writing one from an encoder configured at a lower sample rate, scale this value by
     * `48000 / sampleRate` rather than recording it directly.
     *
     * @return Number of lookahead samples at the encoder's sample rate.
     * @throws IllegalStateException if the encoder has been closed.
     */
    public fun getLookahead(): Int

    public companion object {
        /**
         * Default buffer size for Opus encoded output. It's set to 4000 bytes, as recommended by
         * the Opus documentation: https://opus-codec.org/docs/opus_api-1.5/group__opus__encoder.html.
         */
        public const val DEFAULT_OUTPUT_BUFFER_SIZE: Int = 4000

        /**
         * [setBitrate] value letting the encoder pick a bitrate from the sample rate and channel
         * count. This is the encoder's initial setting. Equal to libopus's `OPUS_AUTO`.
         */
        public const val BITRATE_AUTO: Int = -1000

        /**
         * [setBitrate] value telling the encoder to use as much rate as it can, useful when the
         * rate is instead controlled by the output buffer size. Equal to libopus's
         * `OPUS_BITRATE_MAX`.
         */
        public const val BITRATE_MAX: Int = -1
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