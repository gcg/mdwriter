package dev.mdwriter.data.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AtomicWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Writes [limit] bytes then throws, simulating a crash mid-write. */
    private class ThrowingAfterBytes(
        file: File,
        private val limit: Int,
    ) : FileOutputStream(file) {
        override fun write(b: ByteArray) {
            val toWrite = minOf(b.size, limit)
            super.write(b, 0, toWrite)
            if (toWrite < b.size) throw IOException("boom")
        }
    }

    private fun tmpFilesIn(dir: File): List<File> = dir.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()

    @Test
    fun failureMidWriteKeepsOldFile() {
        val dir = tmp.newFolder()
        val target = File(dir, "note.md")
        target.writeBytes("old".toByteArray())
        val writer = AtomicWriter(openTemp = { ThrowingAfterBytes(it, 2) })

        runBlocking(Dispatchers.IO) {
            try {
                writer.write(target, "new content".toByteArray())
                throw AssertionError("expected write to throw")
            } catch (_: IOException) {
                // expected
            }
        }

        assertThat(target.readText()).isEqualTo("old")
        assertThat(tmpFilesIn(dir)).isEmpty()
    }

    @Test
    fun failureAtRenameKeepsOldFile() {
        val dir = tmp.newFolder()
        val target = File(dir, "note.md")
        target.mkdir()
        File(target, "child").writeText("x") // non-empty directory: rename over it must fail
        val writer = AtomicWriter()

        runBlocking(Dispatchers.IO) {
            try {
                writer.write(target, "new content".toByteArray())
                throw AssertionError("expected write to throw")
            } catch (_: IOException) {
                // expected
            }
        }

        assertThat(target.isDirectory).isTrue()
        assertThat(File(target, "child").exists()).isTrue()
        assertThat(tmpFilesIn(dir)).isEmpty()
    }

    @Test
    fun concurrentWritesAreSerialized() =
        runBlocking(Dispatchers.IO) {
            val dir = tmp.newFolder()
            val target = File(dir, "note.md")
            target.writeBytes(ByteArray(0))
            val writer = AtomicWriter()
            val payloads = (0 until 50).map { "payload-$it".repeat(10) }

            payloads.map { payload -> async { writer.write(target, payload.toByteArray()) } }.awaitAll()

            val finalText = target.readText()
            assertThat(payloads).contains(finalText)
            assertThat(tmpFilesIn(dir)).isEmpty()
        }

    @Test
    fun createsMissingParentDirs() {
        val dir = tmp.newFolder()
        val target = File(File(dir, "a/b/c"), "note.md")
        val writer = AtomicWriter()

        runBlocking(Dispatchers.IO) { writer.write(target, "hello".toByteArray()) }

        assertThat(target.readText()).isEqualTo("hello")
    }
}
