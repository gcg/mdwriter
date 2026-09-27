package dev.mdwriter.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentListTest {
    @Test
    fun pushFront() {
        val (list, evicted) = RecentList.push(listOf("b", "c"), "a", cap = 100)
        assertThat(list).containsExactly("a", "b", "c").inOrder()
        assertThat(evicted).isEmpty()
    }

    @Test
    fun dedupe() {
        val (list, evicted) = RecentList.push(listOf("a", "b", "c"), "b", cap = 100)
        assertThat(list).containsExactly("b", "a", "c").inOrder()
        assertThat(evicted).isEmpty()
    }

    @Test
    fun capEvictsOldest() {
        val (list, evicted) = RecentList.push(listOf("a", "b"), "c", cap = 2)
        assertThat(list).containsExactly("c", "a").inOrder()
        assertThat(evicted).containsExactly("b")
    }
}
