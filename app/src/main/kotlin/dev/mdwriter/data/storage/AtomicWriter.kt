package dev.mdwriter.data.storage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

class KeyedMutex {
    private val map = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(
        key: String,
        block: suspend () -> T,
    ): T = map.computeIfAbsent(key) { Mutex() }.withLock { block() }
}

/**
 * Crash-safe replace: write a dot-prefixed temp file in the SAME directory, fsync it, then rename over the target
 * (rename(2) is atomic on one filesystem). A crash at any point leaves either the old or the new file, never a mix.
 * [openTemp] exists only so tests can inject a failing stream.
 */
class AtomicWriter(
    private val openTemp: (File) -> FileOutputStream = { FileOutputStream(it) },
) {
    private val locks = KeyedMutex()

    /** Serialized per target path (one writer per document at a time). */
    suspend fun write(
        target: File,
        bytes: ByteArray,
    ) = locks.withLock(target.absolutePath) { writeBlocking(target, bytes) }

    fun writeBlocking(
        target: File,
        bytes: ByteArray,
    ) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("no parent directory: $target")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val tmp = File(dir, ".${target.name}.${System.nanoTime()}.tmp") // dot prefix = hidden from listings
        try {
            openTemp(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        // Best effort: persist the directory entry too (works on Linux/Android; ignored where unsupported).
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }
}
