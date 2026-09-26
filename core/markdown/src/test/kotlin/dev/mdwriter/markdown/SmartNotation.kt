package dev.mdwriter.markdown

// Notation shared by SmartEditTest and SmartEditTogglesTest, ported verbatim from
// `plans/reference/markdown/test/SmartHarness.kt`'s `parseState`/`render`/`show`.
// '|' = collapsed cursor, '«'/'»' = selection start/end, '⏎' (in test source) = '\n'.

/** Parses a notated string into (plain text, selStart, selEnd). A collapsed cursor has selStart == selEnd. */
fun parseState(s: String): Triple<String, Int, Int> {
    val a = s.indexOf('«')
    if (a >= 0) {
        val b = s.indexOf('»')
        val t = s.replace("«", "").replace("»", "")
        return Triple(t, a, b - 1)
    }
    val c = s.indexOf('|')
    return Triple(s.replace("|", ""), c, c)
}

/** Renders (text, selStart, selEnd) back into notation. */
fun render(
    t: String,
    a: Int,
    b: Int,
): String =
    if (a == b) {
        t.substring(0, a) + "|" + t.substring(a)
    } else {
        t.substring(0, a) + "«" + t.substring(a, b) + "»" + t.substring(b)
    }

/**
 * Applies [f] to the state parsed from [state] and renders the result back into notation.
 * Null -> "(default newline)".
 */
fun apply(
    state: String,
    f: (String, Int, Int) -> TextEdit?,
): String {
    val (t, a, b) = parseState(state)
    val e = f(t, a, b)
    return if (e == null) {
        "(default newline)"
    } else {
        val (nt, sel) = e.applyTo(t)
        render(nt, sel.first, sel.last)
    }
}
