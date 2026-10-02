package dev.mdwriter.debug

import java.util.Random

/**
 * Debug-only sample documents (gated by `BuildConfig.DEBUG` at every call site — see `MainActivity`). Never
 * referenced from a release code path.
 */
internal object SampleDocs {
    /**
     * One of every construct 02 §4 styles, one per line/block (T06 screenshots use this exact document — keep it
     * stable).
     */
    const val SMALL =
        """# Heading one

## Heading two

### Heading three

A paragraph with **bold**, *italic*, `code`, ~~strike~~, [a link](https://example.com), <https://example.org>.

```kotlin
val x = 1
fun f() = x + 1
```

> A quote that is long enough to wrap onto a second line on a phone screen.

- item
- a long list item that wraps onto a second line on the phone
1. first

- [ ] open task
- [x] done task

| a | b |
|---|---|
| 1 | 2 |

***
"""

    // Verbatim port of plans/reference/bench/Md.kt's `object Doc` (same word list, same seeded RNG, same
    // construct mix) — kept deterministic so a `100k`/`300k` sample is reproducible run to run.
    private val words =
        listOf(
            "lorem",
            "ipsum",
            "dolor",
            "sit",
            "amet",
            "consectetur",
            "adipiscing",
            "elit",
            "sed",
            "do",
            "eiusmod",
            "tempor",
            "incididunt",
            "ut",
            "labore",
            "et",
            "dolore",
            "magna",
            "aliqua",
            "writing",
            "focus",
            "editor",
            "markdown",
            "syntax",
            "paragraph",
            "sentence",
            "quiet",
            "window",
            "river",
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
                    0 -> {
                        s.append("**").append(word).append("**")
                    }

                    1 -> {
                        s.append('_').append(word).append('_')
                    }

                    2 -> {
                        s.append('`').append(word).append('`')
                    }

                    3 -> {
                        s
                            .append('[')
                            .append(word)
                            .append("](https://example.com/")
                            .append(word)
                            .append(')')
                    }

                    else -> {
                        s.append(word)
                    }
                }
            }
            s.append('.')
            s.setCharAt(0, s[0].uppercaseChar())
            return s.toString()
        }
        while (sb.length < target) {
            val level = 1 + (i % 3)
            repeat(level) { sb.append('#') }
            sb
                .append(" Section ")
                .append(i)
                .append(' ')
                .append(words[i % words.size])
                .append("\n\n")
            repeat(3) {
                repeat(3 + rnd.nextInt(3)) { k ->
                    if (k > 0) sb.append(' ')
                    sb.append(sentence())
                }
                sb.append("\n\n")
            }
            sb.append("- item one with **bold** text\n- item two\n- [ ] a task item\n\n")
            sb.append("> A quote line with _emphasis_ inside it.\n\n")
            if (i % 4 == 0) sb.append("```\nval x = 1\nfun f() = x + 1\n```\n\n")
            i++
        }
        return sb.toString()
    }

    fun forExtra(v: String?): String? =
        when (v) {
            "small" -> SMALL
            "20k" -> generate(20_000)
            "100k" -> generate(100_000)
            "300k" -> generate(300_000)
            else -> null
        }
}
