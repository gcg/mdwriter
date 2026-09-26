package dev.mdwriter.data.storage

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object Hashes {
    fun sha1Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    fun sha1Hex(text: String): String = sha1Hex(text.toByteArray(StandardCharsets.UTF_8))
}
