package dev.mdwriter.data.document

/** Created on first launch (01 §5, T11 step 8). [TEXT] is copied byte-for-byte from the task spec,
 * including its trailing empty line, so the caret sits on a fresh line at the end. */
object WelcomeNote {
    const val FILE_NAME = "Welcome.md"

    val TEXT: String =
        buildString {
            appendLine("# Welcome to mdwriter")
            appendLine()
            appendLine(
                "mdwriter is a quiet place to write. There is no Save button: every word is saved as you type, as a plain Markdown file on this phone.",
            )
            appendLine()
            appendLine("Swipe right for your notes. Swipe left to preview.")
            appendLine()
            appendLine("Select text to format it. The toolbar appears only while something is selected.")
            appendLine()
            appendLine("## Markdown in thirty seconds")
            appendLine()
            appendLine("# Heading")
            appendLine("## Smaller heading")
            appendLine("**bold** and *italic*")
            appendLine("- a list item")
            appendLine("- [ ] a task")
            appendLine("- [x] a finished task")
            appendLine("> a quote")
            appendLine("`code`")
            appendLine("[a link](https://example.com)")
            appendLine()
            appendLine("## A few more things")
            appendLine()
            appendLine("- Focus Mode and typewriter scrolling live in the menu at the top right.")
            appendLine("- Your notes are ordinary .md files. Nothing leaves your phone unless you share it.")
            appendLine("- Delete this note whenever you like.")
            appendLine()
        }
}
