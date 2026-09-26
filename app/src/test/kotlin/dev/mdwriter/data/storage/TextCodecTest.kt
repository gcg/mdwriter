package dev.mdwriter.data.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.StandardCharsets

class TextCodecTest {
    private fun bytesOf(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun lfRoundTrip() {
        val original = "a\nb\nc\n".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.format.lineEnding).isEqualTo(LineEnding.LF)
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun crlfRoundTrip() {
        val original = "a\r\nb\r\n".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.text).doesNotContain("\r")
        assertThat(decoded.format.lineEnding).isEqualTo(LineEnding.CRLF)
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun crRoundTrip() {
        val original = "a\rb\rc\r".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.format.lineEnding).isEqualTo(LineEnding.CR)
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun bomRoundTrip() {
        val bom = bytesOf(0xEF, 0xBB, 0xBF)
        val original = bom + "hello".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.format.bom).isTrue()
        assertThat(decoded.text.startsWith("﻿")).isFalse()
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun bomOnlyFile() {
        val original = bytesOf(0xEF, 0xBB, 0xBF)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.text).isEqualTo("")
        assertThat(decoded.format.bom).isTrue()
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun emptyFile() {
        val decoded = TextCodec.decode(ByteArray(0)) as DecodeResult.Text
        assertThat(decoded.text).isEqualTo("")
        assertThat(decoded.format).isEqualTo(TextFormat.DEFAULT)
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(ByteArray(0))
    }

    @Test
    fun emojiRoundTrip() {
        val original = "hi 😀 bye\n".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(TextCodec.encode(decoded.text, decoded.format)).isEqualTo(original)
    }

    @Test
    fun mixedEndingsNormalizeToDominant() {
        val original = "a\r\nb\nc\r\n".toByteArray(StandardCharsets.UTF_8)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.format.lineEnding).isEqualTo(LineEnding.CRLF)
        val reencoded = TextCodec.encode(decoded.text, decoded.format)
        assertThat(reencoded).isNotEqualTo(original)
        assertThat(String(reencoded, StandardCharsets.UTF_8)).isEqualTo("a\r\nb\r\nc\r\n")
    }

    @Test
    fun invalidUtf8FallsBackTo1252() {
        val original = bytesOf(0x63, 0x61, 0x66, 0xE9) // "caf" + 0xE9 (é in windows-1252)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.text).isEqualTo("café")
        assertThat(decoded.format.convertedFrom1252).isTrue()
        assertThat(TextCodec.encode(decoded.text, decoded.format))
            .isEqualTo(bytesOf(0x63, 0x61, 0x66, 0xC3, 0xA9))
    }

    @Test
    fun truncatedUtf8FallsBack() {
        // 0xC3 starts a 2-byte sequence but is not followed by a continuation byte.
        val original = bytesOf(0x61, 0xC3)
        val decoded = TextCodec.decode(original) as DecodeResult.Text
        assertThat(decoded.format.convertedFrom1252).isTrue()
    }

    @Test
    fun nulInFirst8KbIsBinary() {
        val bytes = ByteArray(200) { 'a'.code.toByte() }
        bytes[100] = 0
        assertThat(TextCodec.decode(bytes)).isEqualTo(DecodeResult.Binary)
    }

    @Test
    fun nulAfter8KbIsText() {
        val bytes = ByteArray(9001) { 'a'.code.toByte() }
        bytes[9000] = 0
        assertThat(TextCodec.decode(bytes)).isInstanceOf(DecodeResult.Text::class.java)
    }

    @Test
    fun encodeNormalizesStrayCr() {
        val result = TextCodec.encode("a\r\nb", TextFormat.DEFAULT)
        assertThat(String(result, StandardCharsets.UTF_8)).isEqualTo("a\nb")
    }
}
