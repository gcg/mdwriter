package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ports `Harness.differential` (652 CommonMark spec examples) and the curated-cases-vs-oracle check. */
class SpecDifferentialTest {
    private val pinned = setOf(6, 193, 195, 196, 198, 217, 259, 541, 571, 602, 606)
    private val rx = Regex("@@@@ (\\d+) ([^\\n]*)\\n([\\s\\S]*?)\\n@@@@END\\n") // same as Harness.differential

    @Test
    fun specExamplesAgreeWithCommonmarkJava() {
        var total = 0
        val failures = sortedSetOf<Int>()
        for (m in rx.findAll(resourceText("spec-cases.txt"))) {
            total++
            val md = m.groupValues[3]
            val agree = runCatching { SpecOracle.oracle(md) == SpecOracle.ours(md) }.getOrDefault(false)
            if (!agree) failures += m.groupValues[1].toInt()
        }
        assertEquals(652, total, "spec examples parsed")
        assertTrue(failures.all { it in pinned }, "new disagreements: ${failures - pinned}")
        assertTrue(total - failures.size >= 641, "agree=${total - failures.size}")
    }

    @Test
    fun curatedCasesAgreeWithOracle() { // reference README: 50/50
        val bad = HIGHLIGHTER_CASES.filter { SpecOracle.oracle(it.input) != SpecOracle.ours(it.input) }.map { it.id }
        assertEquals(emptyList<String>(), bad)
    }
}
