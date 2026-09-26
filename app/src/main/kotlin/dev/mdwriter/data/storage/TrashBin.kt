package dev.mdwriter.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Layout: `root/<uuid>/<displayName>` + `root/<uuid>/meta.json`. `meta.json` is a flat JSON object of string values.
 * Keys always present: `displayName`, `deletedAt` (epoch ms as a string), `source` (`"internal"`; T14 adds `"tree"`).
 * InternalStore adds `originalRelPath`.
 */
class TrashBin(
    private val root: File,
) {
    data class Entry(
        val id: String,
        val dir: File,
        val meta: Map<String, String>,
    ) {
        val displayName: String get() = meta["displayName"].orEmpty()
        val deletedAt: Long get() = meta["deletedAt"]?.toLongOrNull() ?: 0L
        val payload: File get() = File(dir, displayName)
    }

    private val writer = AtomicWriter()

    private fun metaFile(dir: File) = File(dir, "meta.json")

    private fun writeMeta(
        dir: File,
        meta: Map<String, String>,
    ) {
        writer.writeBlocking(metaFile(dir), FlatJson.write(meta).toByteArray(Charsets.UTF_8))
    }

    /** Files.move into a new uuid dir; returns id. */
    fun moveIn(
        file: File,
        meta: Map<String, String>,
    ): String {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        if (!dir.mkdirs()) throw java.io.IOException("cannot create $dir")
        // Write metadata BEFORE moving the payload in, so a crash never leaves a payload without metadata.
        writeMeta(dir, meta)
        val target = File(dir, meta["displayName"].orEmpty())
        Files.move(file.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        return id
    }

    /** Used by T14: trash a copy of bytes (e.g. from a SAF document) rather than moving a local file. */
    fun copyIn(
        displayName: String,
        bytes: ByteArray,
        meta: Map<String, String>,
    ): String {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        if (!dir.mkdirs()) throw java.io.IOException("cannot create $dir")
        writeMeta(dir, meta)
        val target = File(dir, displayName)
        writer.writeBlocking(target, bytes)
        return id
    }

    /** null if missing/corrupt. */
    fun get(id: String): Entry? {
        val dir = File(root, id)
        val meta = metaFile(dir)
        if (!dir.isDirectory || !meta.isFile) return null
        val map =
            try {
                FlatJson.read(meta.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                return null
            }
        return Entry(id, dir, map)
    }

    fun remove(id: String) {
        File(root, id).deleteRecursively()
    }

    /** Returns the number of entries deleted. */
    fun purgeOlderThan(cutoffMillis: Long): Int {
        val dirs = root.listFiles { f -> f.isDirectory } ?: return 0
        var count = 0
        for (dir in dirs) {
            val entry = get(dir.name)
            val shouldDelete = entry == null || entry.deletedAt < cutoffMillis
            if (shouldDelete) {
                dir.deleteRecursively()
                count++
            }
        }
        return count
    }
}

/** `org.json` is not usable in JVM unit tests; this is a small hand-rolled flat-object codec. */
internal object FlatJson {
    fun write(map: Map<String, String>): String =
        map.entries.joinToString(",", "{", "}") { (k, v) -> "${q(k)}:${q(v)}" }

    private fun q(s: String): String =
        buildString {
            append('"')
            for (c in s) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    /** Parses exactly what [write] produces (flat object, string values). Throws IllegalArgumentException otherwise. */
    fun read(s: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = s.indexOf('{').also { require(it >= 0) } + 1

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun str(): String {
            skipWs()
            require(s[i] == '"')
            i++
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'u' -> {
                            sb.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                            i += 4
                        }

                        'n' -> {
                            sb.append('\n')
                        }

                        't' -> {
                            sb.append('\t')
                        }

                        'r' -> {
                            sb.append('\r')
                        }

                        else -> {
                            sb.append(s[i])
                        }
                    }
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++
            return sb.toString()
        }
        skipWs()
        if (s[i] == '}') return out
        while (true) {
            val k = str()
            skipWs()
            require(s[i] == ':')
            i++
            out[k] = str()
            skipWs()
            if (s[i] == ',') {
                i++
                continue
            }
            require(s[i] == '}')
            return out
        }
    }
}
