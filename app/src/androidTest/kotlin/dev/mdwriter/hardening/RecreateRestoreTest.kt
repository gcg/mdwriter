package dev.mdwriter.hardening

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.MainActivity
import dev.mdwriter.editor.EditorScrollView
import dev.mdwriter.editor.EditorTestHost
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** T20 Reference D: `recreate()` and a font-scale change restore text, caret and scroll. */
@RunWith(AndroidJUnit4::class)
class RecreateRestoreTest {
    private val body = "# Restore\n\n" + (1..200).joinToString("\n") { "Line $it with *em*" }

    private fun shell(cmd: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        pfd.use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
    }

    @After
    fun resetFontScale() = shell("settings put system font_scale 1.0")

    private fun ActivityScenario<MainActivity>.editor() =
        run {
            var e: dev.mdwriter.editor.MarkdownEditText? = null
            onActivity { e = it.findEditor() }
            e
        }

    private fun prepare(scenario: ActivityScenario<MainActivity>) {
        EditorTestHost.waitUntil(15_000) { scenario.editor() != null }
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.waitForIdleSync()
        Thread.sleep(1_000)
        inst.runOnMainSync {
            scenario.onActivity { a ->
                val et = a.findEditor()!!
                et.text.replace(0, et.text.length, body)
                et.setSelection(420)
                (et.parent as EditorScrollView).scrollTo(0, 3000)
            }
        }
        inst.waitForIdleSync()
        Thread.sleep(1_500) // let autosave write
    }

    private fun assertRestored(
        scenario: ActivityScenario<MainActivity>,
        checkScroll: Boolean,
    ) {
        runCatching { EditorTestHost.waitUntil(8_000) { scenario.editor()?.text?.toString() == body } }
        val actual = scenario.editor()?.text?.toString()
        assertThat(
            "len=" + actual?.length + " head=" + actual?.take(40)?.replace("\n", "|"),
        ).isEqualTo("len=" + body.length + " head=" + body.take(40).replace("\n", "|"))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(800)
        scenario.onActivity { a ->
            val et = a.findEditor()!!
            assertThat(et.selectionStart).isEqualTo(420)
            val sv = et.parent as EditorScrollView
            if (checkScroll) {
                val pitch = et.lineHeight
                assertThat(Math.abs(sv.scrollY - 3000)).isAtMost(pitch)
            } else {
                val line = et.layout.getLineForOffset(420)
                val top = et.layout.getLineTop(line) + et.totalPaddingTop
                assertThat(top).isAtLeast(sv.scrollY - et.lineHeight)
                assertThat(top).isAtMost(sv.scrollY + sv.height)
            }
        }
    }

    @Test
    fun recreateRestoresDocument() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            prepare(scenario)
            scenario.recreate()
            assertRestored(scenario, checkScroll = true)
        }
    }

    @Test
    fun fontScaleChangeRestoresDocument() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            prepare(scenario)
            shell("settings put system font_scale 1.3")
            Thread.sleep(1_500)
            assertRestored(scenario, checkScroll = false)
        }
    }
}
