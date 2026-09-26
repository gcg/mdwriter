package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM table test for [DirtyRange] (Acceptance 1). Each test name states the scenario; the expected numbers
 * are hand-derived from the algorithm in `plans/tasks/T07-incremental-restyle.md` Reference §A.
 */
class DirtyRangeTest {
    @Test
    fun insertBeforeShifts() {
        val d = DirtyRange()
        d.add(10, 20)
        // Insert 3 chars at offset 0, entirely before the dirty range.
        d.onEdit(at = 0, removed = 0, added = 3, newLength = 103)
        assertThat(d.start).isEqualTo(13)
        assertThat(d.end).isEqualTo(23)
        assertThat(d.isEmpty).isFalse()
    }

    @Test
    fun deleteBeforeShifts() {
        val d = DirtyRange()
        d.add(10, 20)
        // Delete 3 chars at offset 0, entirely before the dirty range.
        d.onEdit(at = 0, removed = 3, added = 0, newLength = 97)
        assertThat(d.start).isEqualTo(7)
        assertThat(d.end).isEqualTo(17)
    }

    @Test
    fun insertInsideExpandsEnd() {
        val d = DirtyRange()
        d.add(10, 20)
        // Insert 5 chars at 15, inside the range: start is untouched, end grows to cover the insertion.
        d.onEdit(at = 15, removed = 0, added = 5, newLength = 105)
        assertThat(d.start).isEqualTo(10)
        assertThat(d.end).isEqualTo(25)
    }

    @Test
    fun insertAtStartShifts() {
        val d = DirtyRange()
        d.add(10, 20)
        // Insert exactly AT start (removed == 0): oldEditEnd == start counts as "before", so the whole range
        // shifts right instead of widening to include the insertion.
        d.onEdit(at = 10, removed = 0, added = 4, newLength = 104)
        assertThat(d.start).isEqualTo(14)
        assertThat(d.end).isEqualTo(24)
    }

    @Test
    fun insertAtEndKeepsEnd() {
        val d = DirtyRange()
        d.add(10, 20)
        // Insert exactly AT end (a half-open, exclusive boundary): end == at counts as "after", so the newly
        // inserted text is not pulled into the dirty range by onEdit alone (the caller's own add() of the
        // highlighter delta covers it separately).
        d.onEdit(at = 20, removed = 0, added = 5, newLength = 105)
        assertThat(d.start).isEqualTo(10)
        assertThat(d.end).isEqualTo(20)
    }

    @Test
    fun deleteOverlappingStartClampsToEditStart() {
        val d = DirtyRange()
        d.add(10, 20)
        // Delete [5, 15): overlaps the range's start from before it. The new start clamps to the edit's own
        // start (5), not to some shifted value.
        d.onEdit(at = 5, removed = 10, added = 0, newLength = 20)
        assertThat(d.start).isEqualTo(5)
        assertThat(d.end).isEqualTo(10)
    }

    @Test
    fun deleteCoveringRangeCollapses() {
        val d = DirtyRange()
        d.add(10, 20)
        // Delete [5, 25): fully covers the dirty range. Both ends collapse to the edit's own (single) point.
        d.onEdit(at = 5, removed = 20, added = 0, newLength = 10)
        assertThat(d.start).isEqualTo(5)
        assertThat(d.end).isEqualTo(5)
    }

    @Test
    fun editAfterUnchanged() {
        val d = DirtyRange()
        d.add(10, 20)
        // Edit entirely after the range: neither bound moves.
        d.onEdit(at = 25, removed = 2, added = 3, newLength = 101)
        assertThat(d.start).isEqualTo(10)
        assertThat(d.end).isEqualTo(20)
    }

    @Test
    fun twoEditsBeforeFrame() {
        // A 20-char document. First edit: insert 2 chars at offset 5 (20 -> 22); the highlighter's own delta for
        // that edit is exactly the inserted range [5, 7).
        val d = DirtyRange()
        d.onEdit(at = 5, removed = 0, added = 2, newLength = 22) // isEmpty -> no-op
        d.add(5, 7)
        assertThat(d.start).isEqualTo(5)
        assertThat(d.end).isEqualTo(7)

        // Second edit (before the frame runs): insert 3 chars at offset 0 (22 -> 25). This shifts the first
        // edit's range right by 3 before the second edit's own delta is added.
        d.onEdit(at = 0, removed = 0, added = 3, newLength = 25)
        assertThat(d.start).isEqualTo(8)
        assertThat(d.end).isEqualTo(10)
        d.add(0, 3)

        // Final range is the hull of both (shifted) edits.
        assertThat(d.start).isEqualTo(0)
        assertThat(d.end).isEqualTo(10)
    }

    @Test
    fun markAllIgnoresAdd() {
        val d = DirtyRange()
        d.markAll()
        d.add(100, 200)
        assertThat(d.full).isTrue()
        assertThat(d.isEmpty).isFalse()
        assertThat(d.start).isEqualTo(0)
        assertThat(d.end).isEqualTo(0)

        // onEdit is also a no-op while full.
        d.onEdit(at = 0, removed = 5, added = 1, newLength = 96)
        assertThat(d.full).isTrue()
        assertThat(d.start).isEqualTo(0)
        assertThat(d.end).isEqualTo(0)
    }

    @Test
    fun trimStartConsumes() {
        val d = DirtyRange()
        d.add(10, 30)
        d.trimStart(20)
        assertThat(d.isEmpty).isFalse()
        assertThat(d.start).isEqualTo(20)
        assertThat(d.end).isEqualTo(30)

        // Consuming past (or up to) the end empties the range.
        d.trimStart(30)
        assertThat(d.isEmpty).isTrue()
        assertThat(d.start).isEqualTo(0)
        assertThat(d.end).isEqualTo(0)
        assertThat(d.full).isFalse()
    }

    @Test
    fun clampsToNewLength() {
        val d = DirtyRange()
        d.add(50, 60)
        // The edit itself leaves `end` "unchanged" at 60 (end <= at branch), but the document shrank to 55 —
        // both bounds must clamp into [0, newLength].
        d.onEdit(at = 60, removed = 0, added = 0, newLength = 55)
        assertThat(d.start).isEqualTo(50)
        assertThat(d.end).isEqualTo(55)
    }
}
