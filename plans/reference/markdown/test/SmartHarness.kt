import mdwriter.markdown.*

/** Notation: '|' = collapsed cursor, '«' '»' = selection start/end. */
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

fun render(t: String, a: Int, b: Int): String =
    if (a == b) t.substring(0, a) + "|" + t.substring(a) else t.substring(0, a) + "«" + t.substring(a, b) + "»" + t.substring(b)

fun show(s: String) = "`" + s.replace("\n", "⏎") + "`"

fun run(op: String, input: String, f: (String, Int, Int) -> TextEdit?) {
    val (t, a, b) = parseState(input)
    val e = f(t, a, b)
    val out = if (e == null) "(default newline)" else { val (nt, sel) = e.applyTo(t); render(nt, sel.first, sel.last) }
    println("| $op | ${show(input)} | ${show(out)} |")
}

fun main() {
    println("| Command | Before | After |")
    println("|---|---|---|")
    val enter = { t: String, a: Int, _: Int -> SmartEdit.onEnter(t, a) }
    val bold0 = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "**") }
    val ital0 = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "*") }
    run("Enter", "- item|", enter)
    run("Enter", "* item|", enter)
    run("Enter", "+ item|", enter)
    run("Enter", "- it|em", enter)
    run("Enter", "1. one|", enter)
    run("Enter", "9) nine|", enter)
    run("Enter", "1. one|\n2. two\n3. three", enter)
    run("Enter", "- [ ] task|", enter)
    run("Enter", "- [x] done|", enter)
    run("Enter", "> quote|", enter)
    run("Enter", "> - quoted item|", enter)
    run("Enter", "- |", enter)
    run("Enter", "1. |", enter)
    run("Enter", "- [ ] |", enter)
    run("Enter", "  - |", enter)
    run("Enter", "- a\n  - |", enter)
    run("Enter", "> |", enter)
    run("Enter", "> - |", enter)
    run("Enter", "|- item", enter)
    run("Enter", "plain text|", enter)
    run("Enter", "---|", enter)
    run("Enter", "-not a list|", enter)
    run("Enter(code line)", "    code|") { t, a, _ -> SmartEdit.onEnter(t, a, lineType = BlockType.INDENTED_CODE) }
    run("Enter(fenced, list)", "- ```\n  - not a list|") { t, a, _ -> SmartEdit.onEnter(t, a, lineType = BlockType.FENCED_CODE) }
    run("Enter(unclosed fence)", "```kotlin|") { t, a, _ -> SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = true) }
    run("Enter(unclosed fence in quote)", "> ~~~~|") { t, a, _ -> SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = true) }
    run("Enter(closed fence)", "```kotlin|\n```") { t, a, _ -> SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = false) }
    run("Enter", "1. a\n1. b|", enter)
    run("Enter", "  1. nested|", enter)
    run("Enter", "- a|\n- b", enter)
    run("Quote", "some |text", { t, a, b -> SmartEdit.toggleQuote(t, a, b) })
    run("Quote", "> some |text", { t, a, b -> SmartEdit.toggleQuote(t, a, b) })
    run("Quote", "«one\n\ntwo»", { t, a, b -> SmartEdit.toggleQuote(t, a, b) })
    run("Quote", "«> one\n>\n> two»", { t, a, b -> SmartEdit.toggleQuote(t, a, b) })
    run("Quote", "> > dee|p", { t, a, b -> SmartEdit.toggleQuote(t, a, b) })
    run("SetHeading(2)", "Title|", { t, a, _ -> SmartEdit.setHeading(t, a, 2) })
    run("SetHeading(0)", "### Ti|tle", { t, a, _ -> SmartEdit.setHeading(t, a, 0) })
    run("SetHeading(1)", "- ## item|", { t, a, _ -> SmartEdit.setHeading(t, a, 1) })
    run("Backspace", "- |item", { t, a, _ -> SmartEdit.onBackspace(t, a) })
    run("Backspace", "  - [ ] |task", { t, a, _ -> SmartEdit.onBackspace(t, a) })
    run("Backspace", "> > |q", { t, a, _ -> SmartEdit.onBackspace(t, a) })
    run("Backspace", "- it|em", { t, a, _ -> SmartEdit.onBackspace(t, a) })
    run("Highlight", "«key»", { t, a, b -> SmartEdit.toggleWrap(t, a, b, "==") })
    run("Italic", "*«x»*", ital0)
    run("Bold", "*«x»*", bold0)
    val bold = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "**") }
    val ital = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "*") }
    run("Bold", "make «this» bold", bold)
    run("Bold", "make **«this»** bold", bold)
    run("Bold", "make «**this**» bold", bold)
    run("Bold", "make th|is bold", bold)
    run("Bold", "make «this » bold", bold)
    run("Bold", "empty | here", bold)
    run("Bold", "***«x»***", bold)
    run("Italic", "make «this» it", ital)
    run("Italic", "make *«this»* it", ital)
    run("Italic", "make **«this»** it", ital)
    run("Italic", "***«x»***", ital)
    run("Italic", "«line one\nline two»", ital)
    run("Strike", "«gone»", { t, a, b -> SmartEdit.toggleWrap(t, a, b, "~~") })
    run("Strike", "~~«gone»~~", { t, a, b -> SmartEdit.toggleWrap(t, a, b, "~~") })
    run("Code", "«val x»", { t, a, b -> SmartEdit.toggleWrap(t, a, b, "`") })
    run("Code", "«a `tick`»", { t, a, b -> SmartEdit.toggleWrap(t, a, b, "`") })
    run("Link", "see «docs» here", { t, a, b -> SmartEdit.insertLink(t, a, b) })
    run("Link", "«https://ia.net»", { t, a, b -> SmartEdit.insertLink(t, a, b) })
    run("Link", "at | end", { t, a, b -> SmartEdit.insertLink(t, a, b) })
    run("Image", "«diagram»", { t, a, b -> SmartEdit.insertLink(t, a, b, image = true) })
    val h = { t: String, a: Int, _: Int -> SmartEdit.cycleHeading(t, a) }
    run("Heading", "Title|", h)
    run("Heading", "# Title|", h)
    run("Heading", "## Title|", h)
    run("Heading", "### Title|", h)
    run("Heading", "#### Title|", h)
    run("Heading", "> Quote| title", h)
    run("Indent", "- a\n- b|", { t, a, _ -> SmartEdit.indentListItem(t, a) })
    run("Indent", "1. a\n2. b|", { t, a, _ -> SmartEdit.indentListItem(t, a) })
    run("Indent", "10. a\n11. b|", { t, a, _ -> SmartEdit.indentListItem(t, a) })
    run("Outdent", "- a\n  - b|", { t, a, _ -> SmartEdit.outdentListItem(t, a) })
    run("Outdent", "- b|", { t, a, _ -> SmartEdit.outdentListItem(t, a) })
    run("Task", "- [ ] t|odo", { t, a, _ -> SmartEdit.toggleTask(t, a) })
    run("Task", "- [x] t|odo", { t, a, _ -> SmartEdit.toggleTask(t, a) })
    // property: toggle twice == identity
    var bad = 0
    val samples = listOf("a «b» c", "«word»", "x **«y»** z", "«**y**»", "*«i»*", "«multi\nline»", "one tw|o three")
    for (s in samples) for (m in listOf("**", "*", "~~", "==", "`")) {
        val (t, a, b) = parseState(s)
        val e1 = SmartEdit.toggleWrap(t, a, b, m); val (t1, s1) = e1.applyTo(t)
        val e2 = SmartEdit.toggleWrap(t1, s1.first, s1.last, m); val (t2, _) = e2.applyTo(t1)
        if (t2 != t) { bad++; println("NOT IDEMPOTENT: $m ${show(s)} -> ${show(t1)} -> ${show(t2)}") }
    }
    println("toggle-twice identity violations: $bad")
    // minimize() must not change the result
    var badMin = 0
    for (s0 in listOf("1. one|\n2. two\n3. three", "make «this» bold", "> some |text", "### Ti|tle")) {
        val (t, a, b) = parseState(s0)
        for (e in listOfNotNull(SmartEdit.onEnter(t, a), SmartEdit.toggleWrap(t, a, b, "**"), SmartEdit.toggleQuote(t, a, b), SmartEdit.cycleHeading(t, a))) {
            if (e.applyTo(t) != e.minimize(t).applyTo(t)) badMin++
        }
    }
    println("minimize() violations: $badMin")
}
