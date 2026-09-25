package io.voxkit.kopus

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpusTest {
    @Test
    fun testVersion() {
        assertEquals("libopus 1.5.2", Opus.version)
    }

    @Test
    fun testGetErrorString() {
        assertEquals("success", Opus.getErrorString(0))
    }

    @Test
    fun testCreateEncoder() {
        val encoder = Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        )
        encoder.close()
    }

    @Test
    fun testGetLookahead() {
        Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        ).use { encoder ->
            val lookahead = encoder.getLookahead()
            // The encoder's algorithmic delay is bounded by a small multiple of a 20ms frame at
            // the configured sample rate; a much larger value would indicate the wrong CTL/value
            // is being read.
            assertTrue(lookahead > 0, "Lookahead should be greater than 0")
            assertTrue(
                lookahead < SampleRate.RATE_48K.value / 10,
                "Lookahead should be well under 100ms worth of samples"
            )
        }
    }

    @Test
    fun testGetLookaheadDependsOnApplication() {
        // OPUS_APPLICATION_RESTRICTED_LOWDELAY skips the extra encoder delay compensation that
        // AUDIO/VOIP incur (see opus_encoder.c's OPUS_GET_LOOKAHEAD_REQUEST handling), so it must
        // report a strictly smaller lookahead at the same sample rate. Asserting this (rather
        // than just a positive bound) catches an implementation that ignores the real CTL value
        // and returns a hardcoded constant instead.
        val lowDelayLookahead = Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.LOW_DELAY,
        ).use { it.getLookahead() }

        val audioLookahead = Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        ).use { it.getLookahead() }

        assertTrue(
            lowDelayLookahead < audioLookahead,
            "LOW_DELAY lookahead ($lowDelayLookahead) should be less than AUDIO lookahead " +
                "($audioLookahead)"
        )
    }

    @Test
    fun testSetBitrateAffectsEncodedSize() {
        val sampleRate = SampleRate.RATE_48K
        val channels = Channels.STEREO
        val frameSize = sampleRate.value / 1000 * 20 // 20 ms frame size
        val frameCount = 5

        val lowBitrateSize = encodeTotalBytes(sampleRate, channels, frameSize, frameCount, 8_000)
        val highBitrateSize =
            encodeTotalBytes(sampleRate, channels, frameSize, frameCount, 128_000)

        assertTrue(
            lowBitrateSize < highBitrateSize,
            "Encoding at 8kbps ($lowBitrateSize bytes) should produce less data than at " +
                "128kbps ($highBitrateSize bytes)"
        )
    }

    @Test
    fun testSetBitrateThenEncodeStillSucceeds() {
        val sampleRate = SampleRate.RATE_48K
        val channels = Channels.STEREO
        val frameSize = sampleRate.value / 1000 * 20 // 20 ms frame size
        val pcmInput = generateSineWaveOfShorts(
            sampleRate = sampleRate,
            channels = channels.value,
            durationMillis = 20L
        )
        val encodedBuffer = ByteArray(OpusEncoder.DEFAULT_OUTPUT_BUFFER_SIZE)

        Opus.encoder(sampleRate, channels, OpusApplication.AUDIO).use { encoder ->
            encoder.setBitrate(24_000)
            val encodedLength = encoder.encode(pcmInput, frameSize, encodedBuffer)
            assertTrue(encodedLength > 0, "Encoding failed, length should be greater than 0")
        }
    }

    @Test
    fun testSetBitrateRejectsInvalidValue() {
        Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        ).use { encoder ->
            // Per opus_encoder.c, any value <= 0 other than the special OPUS_AUTO (-1000) and
            // OPUS_BITRATE_MAX (-1) values is rejected with OPUS_BAD_ARG.
            assertFailsWith<IllegalArgumentException> { encoder.setBitrate(0) }
        }
    }

    @Test
    fun testSetBitrateAcceptsSpecialValues() {
        Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        ).use { encoder ->
            encoder.setBitrate(OpusEncoder.BITRATE_MAX)
            encoder.setBitrate(OpusEncoder.BITRATE_AUTO)
        }
    }

    @Test
    fun testSetBitrateAfterCloseThrows() {
        val encoder = Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        )
        encoder.close()
        assertFailsWith<IllegalStateException> { encoder.setBitrate(24_000) }
    }

    @Test
    fun testGetLookaheadAfterCloseThrows() {
        val encoder = Opus.encoder(
            SampleRate.RATE_48K,
            channels = Channels.STEREO,
            application = OpusApplication.AUDIO,
        )
        encoder.close()
        assertFailsWith<IllegalStateException> { encoder.getLookahead() }
    }

    private fun encodeTotalBytes(
        sampleRate: SampleRate,
        channels: Channels,
        frameSize: Int,
        frameCount: Int,
        bitrate: Int,
    ): Int {
        val pcmInput = generateSineWaveOfShorts(
            sampleRate = sampleRate,
            channels = channels.value,
            durationMillis = frameSize.toLong() * frameCount * 1000 / sampleRate.value,
        )
        val encodedBuffer = ByteArray(OpusEncoder.DEFAULT_OUTPUT_BUFFER_SIZE)

        return Opus.encoder(sampleRate, channels, OpusApplication.AUDIO).use { encoder ->
            encoder.setBitrate(bitrate)
            var totalBytes = 0
            for (frame in 0 until frameCount) {
                val offset = frame * frameSize * channels.value
                val frameInput = pcmInput.copyOfRange(offset, offset + frameSize * channels.value)
                totalBytes += encoder.encode(frameInput, frameSize, encodedBuffer)
            }
            totalBytes
        }
    }

    @Test
    fun testCreateDecoder() {
        val decoder = Opus.decoder(SampleRate.RATE_48K, channels = Channels.STEREO)
        decoder.close()
    }

    @Test
    fun testEncodeAndDecodeShorts() {
        val sampleRate = SampleRate.RATE_48K
        val channels = Channels.STEREO
        val timeMillis = 20L
        val frameSize = sampleRate.value / 1000 * 20 // 20 ms frame size
        val pcmInput = generateSineWaveOfShorts(
            sampleRate = sampleRate,
            channels = channels.value,
            durationMillis = timeMillis
        )
        val encodedBuffer = ByteArray(OpusEncoder.DEFAULT_OUTPUT_BUFFER_SIZE)
        val decodedBuffer = ShortArray(pcmInput.size)

        // Encode
        Opus.encoder(sampleRate, channels, OpusApplication.AUDIO).use { encoder ->
            val encodedLength = encoder.encode(pcmInput, frameSize, encodedBuffer)
            assertTrue(encodedLength > 0, "Encoding failed, length should be greater than 0")

            // Decode
            Opus.decoder(sampleRate, channels).use { decoder ->
                val encodedData = encodedBuffer.copyOf(encodedLength)
                val numberOfDecodedSamples = decoder.decode(encodedData, frameSize, decodedBuffer)
                assertEquals(
                    frameSize,
                    numberOfDecodedSamples,
                    "Decoded samples count should equal frame size"
                )
            }
        }
    }

    @Test
    fun testEncodeAndDecodeFloats() {
        val sampleRate = SampleRate.RATE_48K
        val channels = Channels.STEREO
        val timeMillis = 20L
        val frameSize = sampleRate.value / 1000 * 20 // 20 ms frame size
        val pcmInput = generateSineWaveOfFloats(
            sampleRate = sampleRate,
            channels = channels.value,
            durationMillis = timeMillis
        )
        val encodedBuffer = ByteArray(OpusEncoder.DEFAULT_OUTPUT_BUFFER_SIZE)
        val decodedBuffer = FloatArray(frameSize * 2) // 2 channels

        // Encode
        Opus.encoder(sampleRate, Channels.STEREO, OpusApplication.AUDIO).use { encoder ->
            val encodedLength = encoder.encode(pcmInput, frameSize, encodedBuffer)
            assertTrue(encodedLength > 0, "Encoding failed, length should be greater than 0")

            // Decode
            Opus.decoder(sampleRate, Channels.STEREO).use { decoder ->
                val encodedData = encodedBuffer.copyOf(encodedLength)
                val numberOfDecodedSamples = decoder.decode(encodedData, frameSize, decodedBuffer)
                assertEquals(
                    frameSize,
                    numberOfDecodedSamples,
                    "Decoded samples count should equal frame size"
                )
            }
        }
    }

    private fun generateSineWaveOfShorts(
        sampleRate: SampleRate,
        frequency: Int = 440,
        channels: Int = 1,
        durationMillis: Long = 1000L,
    ): ShortArray {
        val numSamples = (sampleRate.value / 1000 * durationMillis).toInt()
        return sinWave(sampleRate, frequency, channels, amplitude = Short.MAX_VALUE.toFloat())
            .take(numSamples * channels)
            .map { it.toInt().toShort() }
            .toList()
            .toShortArray()
    }

    private fun generateSineWaveOfFloats(
        sampleRate: SampleRate,
        frequency: Int = 440,
        channels: Int = 1,
        durationMillis: Long = 1000L,
    ): FloatArray {
        val numSamples = (sampleRate.value / 1000 * durationMillis).toInt()
        return sinWave(sampleRate, frequency, channels, amplitude = 1.0f)
            .take(numSamples * channels)
            .toList()
            .toFloatArray()
    }

    private fun sinWave(
        rate: SampleRate,
        frequency: Int,
        channels: Int,
        amplitude: Float,
    ): Sequence<Float> = sequence {
        var t = 0.0
        while (true) {
            val sample = amplitude * sin(2 * PI * frequency * t / rate.value)
            repeat(channels) { yield(sample.toFloat()) }
            t += 1.0 / rate.value
        }
    }
}
