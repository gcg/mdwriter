package dev.mdwriter.data.storage

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class LineEnding(
    val chars: String,
) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}

/** How the file looked on disk, so saving writes the same bytes back. [convertedFrom1252]: will be saved as UTF-8. */
data class TextFormat(
    val bom: Boolean,
    val lineEnding: LineEnding,
    val convertedFrom1252: Boolean,
) {
    companion object {
        val DEFAULT = TextFormat(bom = false, lineEnding = LineEnding.LF, convertedFrom1252 = false)
    }
}

sealed interface DecodeResult {
    data class Text(
        val text: String,
        val format: TextFormat,
    ) : DecodeResult

    data object Binary : DecodeResult
}

object TextCodec {
    const val SNIFF_BYTES = 8 * 1024
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun hasBom(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]

    fun decode(bytes: ByteArray): DecodeResult {
        val bom = hasBom(bytes)
        val start = if (bom) 3 else 0
        val sniffEnd = minOf(bytes.size, start + SNIFF_BYTES)
        for (i in start until sniffEnd) if (bytes[i] == 0.toByte()) return DecodeResult.Binary
        var converted = false
        val raw =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.size - start))
                    .toString()
            } catch (e: CharacterCodingException) {
                converted = true
                String(bytes, start, bytes.size - start, CP1252)
            }
        return DecodeResult.Text(normalizeToLf(raw), TextFormat(bom, dominantLineEnding(raw), converted))
    }

    /** [text] should be LF-only (the editor contract); stray CR/CRLF (e.g. pasted) are normalized first. */
    fun encode(
        text: String,
        format: TextFormat,
    ): ByteArray {
        val lf = normalizeToLf(text)
        val body =
            (if (format.lineEnding == LineEnding.LF) lf else lf.replace("\n", format.lineEnding.chars))
                .toByteArray(StandardCharsets.UTF_8)
        return if (format.bom) BOM + body else body
    }

    fun dominantLineEnding(s: CharSequence): LineEnding {
        var lf = 0
        var crlf = 0
        var cr = 0
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '\r' -> {
                    if (i + 1 < s.length && s[i + 1] == '\n') {
                        crlf++
                        i++
                    } else {
                        cr++
                    }
                }

                '\n' -> {
                    lf++
                }
            }
            i++
        }
        return when {
            crlf > lf && crlf >= cr -> LineEnding.CRLF
            cr > lf && cr > crlf -> LineEnding.CR
            else -> LineEnding.LF // ties and "no line breaks" -> LF
        }
    }

    fun normalizeToLf(s: String): String {
        if (s.indexOf('\r') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\r') {
                sb.append('\n')
                if (i + 1 < s.length && s[i + 1] == '\n') i++
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
