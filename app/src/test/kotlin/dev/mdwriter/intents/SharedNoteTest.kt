package dev.mdwriter.intents

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedNoteTest {
    @Test
    fun subjectBecomesH1() {
        val note = SharedNote.compose("Idea", "from adb")
        assertThat(note.body).isEqualTo("# Idea\n\nfrom adb\n")
        assertThat(note.baseName).isEqualTo("Idea")
    }

    @Test
    fun noDuplicateHeading() {
        val note = SharedNote.compose("Idea", "# Idea\nbody")
        assertThat(note.body).isEqualTo("# Idea\nbody\n")
    }

    @Test
    fun noDuplicateHeadingWhenBodyIsJustTheSubject() {
        val note = SharedNote.compose("Idea", "Idea")
        assertThat(note.body).isEqualTo("Idea\n")
    }

    @Test
    fun crlfNormalized() {
        val note = SharedNote.compose(null, "line one\r\nline two\r\n")
        assertThat(note.body).isEqualTo("line one\nline two\n")
    }

    @Test
    fun singleTrailingNewline() {
        assertThat(SharedNote.compose(null, "text").body).isEqualTo("text\n")
        assertThat(SharedNote.compose(null, "text\n\n\n").body).isEqualTo("text\n")
        assertThat(SharedNote.compose(null, "text\n").body).isEqualTo("text\n")
    }

    @Test
    fun baseNameFromFirstLine() {
        val note = SharedNote.compose(null, "Buy milk\nand eggs")
        assertThat(note.baseName).isEqualTo("Buy milk")
    }

    @Test
    fun blankSubjectIsIgnored() {
        val note = SharedNote.compose("   ", "hello")
        assertThat(note.body).isEqualTo("hello\n")
    }

    @Test
    fun noSubjectFallsBackToSharedNote() {
        val note = SharedNote.compose(null, "")
        assertThat(note.baseName).isEqualTo("Shared note")
    }
}
