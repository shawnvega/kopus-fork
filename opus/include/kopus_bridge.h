#ifndef KOPUS_BRIDGE_H
#define KOPUS_BRIDGE_H

/*
 * Kotlin/Native cinterop cannot reliably bind to variadic C functions (opus_encoder_ctl) or to
 * function-like macros (OPUS_SET_BITRATE/OPUS_GET_LOOKAHEAD), so this header provides tiny,
 * non-variadic wrapper functions that cinterop *can* bind to. Included by the generated opus.def
 * (see kopus/build.gradle.kts's generateOpusDef task) alongside opus.h.
 */

#include "opus.h"
#include "opus_defines.h"

/**
 * Sets the target bitrate, in bits per second, on an Opus encoder.
 *
 * Wraps `opus_encoder_ctl(st, OPUS_SET_BITRATE(bitrate))`; see OPUS_SET_BITRATE's documentation
 * in opus_defines.h for the valid range and special values.
 *
 * @return OPUS_OK on success, or a negative Opus error code.
 */
static inline int kopus_encoder_set_bitrate(OpusEncoder *st, opus_int32 bitrate) {
    return opus_encoder_ctl(st, OPUS_SET_BITRATE(bitrate));
}

/**
 * Gets the encoder's total algorithmic delay ("lookahead"), in samples.
 *
 * Wraps `opus_encoder_ctl(st, OPUS_GET_LOOKAHEAD(lookahead))`; see OPUS_GET_LOOKAHEAD's
 * documentation in opus_defines.h.
 *
 * @return OPUS_OK on success, or a negative Opus error code.
 */
static inline int kopus_encoder_get_lookahead(OpusEncoder *st, opus_int32 *lookahead) {
    return opus_encoder_ctl(st, OPUS_GET_LOOKAHEAD(lookahead));
}

#endif // KOPUS_BRIDGE_H
