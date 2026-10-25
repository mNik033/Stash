package com.stash.core.media.output

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * The decoder's input [Format] as the signal path presents it.
 *
 * Bit depth comes from [Format.pcmEncoding], which Media3's extractors set for
 * the formats where it means something (FLAC from STREAMINFO, WAV, raw PCM);
 * lossy codecs have none, and the sheet shows none.
 */
@OptIn(UnstableApi::class)
fun Format.toSourceFormat(): SourceFormat {
    val mime = sampleMimeType.orEmpty()
    val (codec, lossless) = when (mime) {
        MimeTypes.AUDIO_FLAC -> "FLAC" to true
        MimeTypes.AUDIO_ALAC -> "ALAC" to true
        MimeTypes.AUDIO_RAW, MimeTypes.AUDIO_WAV -> "PCM" to true
        MimeTypes.AUDIO_AAC -> "AAC" to false
        MimeTypes.AUDIO_OPUS -> "Opus" to false
        MimeTypes.AUDIO_MPEG -> "MP3" to false
        MimeTypes.AUDIO_VORBIS -> "Vorbis" to false
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3 -> "Dolby Digital" to false
        else -> (mime.substringAfter('/').uppercase().ifBlank { "Audio" }) to false
    }
    val bits = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }
    return SourceFormat(
        codec = codec,
        lossless = lossless,
        sampleRate = sampleRate.takeIf { it > 0 } ?: 0,
        bitsPerSample = bits,
        channelCount = channelCount.takeIf { it > 0 } ?: 2,
    )
}
