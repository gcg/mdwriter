package dev.mdwriter.data.storage

import dev.mdwriter.data.library.DocKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

data class RecoveryCopy(
    val text: String,
    val savedAt: Long,
)

/** Latest unsaved buffer per document (crash / SAF safety net). root = noBackupFilesDir/recovery (NOT backed up). */
class RecoveryStore(
    private val root: File,
    private val writer: AtomicWriter = AtomicWriter(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun fileFor(key: DocKey): File = File(root, Hashes.sha1Hex(key.value) + ".md")

    suspend fun write(
        key: DocKey,
        text: String,
    ) {
        withContext(io) {
            writer.write(fileFor(key), TextCodec.normalizeToLf(text).toByteArray(StandardCharsets.UTF_8))
        }
    }

    suspend fun read(key: DocKey): RecoveryCopy? =
        withContext(io) {
            val file = fileFor(key)
            if (!file.isFile) return@withContext null
            RecoveryCopy(text = file.readText(StandardCharsets.UTF_8), savedAt = file.lastModified())
        }

    suspend fun delete(key: DocKey) {
        withContext(io) {
            fileFor(key).delete()
        }
    }

    suspend fun newerThan(
        key: DocKey,
        millis: Long,
    ): Boolean =
        withContext(io) {
            val file = fileFor(key)
            file.isFile && file.lastModified() > millis
        }
}
