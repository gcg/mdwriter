package dev.bench.mdtext

import java.util.Random

const val K_H = 1
const val K_MARK = 2
const val K_BOLD = 3
const val K_ITAL = 4
const val K_CODE = 5
const val K_FENCE = 6
const val K_QUOTE = 7
const val K_URL = 8

/** Struct-of-arrays list of style ranges. */
class Spans(cap: Int = 1024) {
    var kind = IntArray(cap)
    var start = IntArray(cap)
    var end = IntArray(cap)
    var level = IntArray(cap)
    var size = 0

    fun add(k: Int, s: Int, e: Int, l: Int = 0) {
        if (e <= s) return
        if (size == kind.size) grow()
        kind[size] = k; start[size] = s; end[size] = e; level[size] = l
        size++
    }

    private fun grow() {
        val n = kind.size * 2
        kind = kind.copyOf(n); start = start.copyOf(n); end = end.copyOf(n); level = level.copyOf(n)
    }
}

object Doc {
    private val words = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed", "do",
        "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "writing",
        "focus", "editor", "markdown", "syntax", "paragraph", "sentence", "quiet", "window", "river",
    )

    fun generate(target: Int): String {
        val rnd = Random(42)
        val sb = StringBuilder(target + 2000)
        var i = 0
        fun sentence(): String {
            val n = 8 + rnd.nextInt(14)
            val s = StringBuilder()
            for (w in 0 until n) {
                if (w > 0) s.append(' ')
                val word = words[rnd.nextInt(words.size)]
                when (rnd.nextInt(30)) {
                    0 -> s.append("**").append(word).append("**")
                    1 -> s.append('_').append(word).append('_')
                    2 -> s.append('`').append(word).append('`')
                    3 -> s.append('[').append(word).append("](https://example.com/").append(word).append(')')
                    else -> s.append(word)
                }
            }
            s.append('.')
            s.setCharAt(0, s[0].uppercaseChar())
            return s.toString()
        }
        while (sb.length < target) {
            val level = 1 + (i % 3)
            repeat(level) { sb.append('#') }
            sb.append(" Section ").append(i).append(' ').append(words[i % words.size]).append("\n\n")
            repeat(3) {
                repeat(3 + rnd.nextInt(3)) { k -> if (k > 0) sb.append(' '); sb.append(sentence()) }
                sb.append("\n\n")
            }
            sb.append("- item one with **bold** text\n- item two\n- [ ] a task item\n\n")
            sb.append("> A quote line with _emphasis_ inside it.\n\n")
            if (i % 4 == 0) sb.append("```\nval x = 1\nfun f() = x + 1\n```\n\n")
            i++
        }
        return sb.toString()
    }
}

object MdStyler {
    private val BOLD_RE = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")
    private val ITAL_RE = Regex("""(?<![\w*])_(?=\S)(.+?)(?<=\S)_(?!\w)""")
    private val CODE_RE = Regex("""`([^`\n]+)`""")
    private val LINK_RE = Regex("""\[([^\]\n]+)\]\(([^)\n]+)\)""")

    private fun lineEnd(t: CharSequence, from: Int, to: Int): Int {
        var i = from
        while (i < to && t[i] != '\n') i++
        return i
    }

    private fun startsWith(t: CharSequence, s: Int, e: Int, p: String): Boolean {
        if (e - s < p.length) return false
        for (k in p.indices) if (t[s + k] != p[k]) return false
        return true
    }

    /** Parse [from, to) which must start at a line start. */
    fun parse(text: CharSequence, from: Int = 0, to: Int = text.length, out: Spans = Spans()): Spans {
        var i = from
        var inFence = false
        while (i < to) {
            val le = lineEnd(text, i, to)
            if (startsWith(text, i, le, "```")) {
                inFence = !inFence
                out.add(K_FENCE, i, le); out.add(K_MARK, i, le)
                i = le + 1; continue
            }
            if (inFence) { out.add(K_FENCE, i, le); i = le + 1; continue }
            var h = 0
            while (i + h < le && h < 6 && text[i + h] == '#') h++
            if (h > 0 && i + h < le && text[i + h] == ' ') {
                out.add(K_H, i, le, h); out.add(K_MARK, i, i + h + 1)
            } else if (le - i >= 2 && (text[i] == '-' || text[i] == '*' || text[i] == '+') && text[i + 1] == ' ') {
                out.add(K_MARK, i, i + 2)
            } else if (le - i >= 1 && text[i] == '>') {
                out.add(K_QUOTE, i, le); out.add(K_MARK, i, minOf(i + 2, le))
            }
            if (le > i) inline(text, i, le, out)
            i = le + 1
        }
        return out
    }

    private fun inline(text: CharSequence, s: Int, e: Int, out: Spans) {
        val line = text.subSequence(s, e)
        for (m in BOLD_RE.findAll(line)) {
            val a = s + m.range.first; val b = s + m.range.last + 1
            out.add(K_BOLD, a, b); out.add(K_MARK, a, a + 2); out.add(K_MARK, b - 2, b)
        }
        for (m in ITAL_RE.findAll(line)) {
            val a = s + m.range.first; val b = s + m.range.last + 1
            out.add(K_ITAL, a, b); out.add(K_MARK, a, a + 1); out.add(K_MARK, b - 1, b)
        }
        for (m in CODE_RE.findAll(line)) {
            val a = s + m.range.first; val b = s + m.range.last + 1
            out.add(K_CODE, a, b); out.add(K_MARK, a, a + 1); out.add(K_MARK, b - 1, b)
        }
        for (m in LINK_RE.findAll(line)) {
            val g2 = m.groups[2]!!.range
            out.add(K_URL, s + g2.first - 2, s + g2.last + 2)
            out.add(K_MARK, s + m.range.first, s + m.range.first + 1)
        }
    }
}
