package dev.mdwriter.editor

import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.TextAttribute
import dev.mdwriter.markdown.MarkdownHighlighter
import dev.mdwriter.markdown.SmartEdit
import dev.mdwriter.markdown.TextEdit
import dev.mdwriter.ui.toolbar.ToolbarAction

/** What [MarkdownEditText] dispatches keyboard/menu commands to — implemented by [EditorController] via a
 * delegating `object` (never by the controller class itself: `EditorCommands` is `internal`, and a public
 * class cannot implement an internal interface — "exposed supertype" error). */
internal interface EditorCommands {
    fun perform(action: ToolbarAction)

    fun undo()

    fun redo()

    fun toggleTaskAt(offset: Int)
}

/** Result of [EditorShortcuts.map]: a keyboard shortcut is either our own undo/redo, or a [ToolbarAction]. */
internal sealed interface ShortcutCommand {
    data object Undo : ShortcutCommand

    data object Redo : ShortcutCommand

    data class Action(
        val action: ToolbarAction,
    ) : ShortcutCommand
}

/**
 * Editor-level Ctrl shortcuts (01 §6.2). Pure: every `KeyEvent` constant used here is a Java `public static final
 * int`, which the Kotlin compiler inlines as a literal — this object touches no live `KeyEvent` instance, so it
 * runs on a plain JVM unit test with no Android runtime. Anything not listed here (Ctrl+C/X/V/A, Ctrl+Shift+V, Alt
 * or Meta held, no Ctrl at all) returns `null` so [MarkdownEditText.onKeyShortcut] falls back to `super` and
 * `TextView`'s own default handling.
 */
internal object EditorShortcuts {
    fun map(
        keyCode: Int,
        meta: Int,
    ): ShortcutCommand? {
        if (meta and KeyEvent.META_CTRL_ON == 0) return null
        if (meta and KeyEvent.META_ALT_ON != 0 || meta and KeyEvent.META_META_ON != 0) return null
        val shift = meta and KeyEvent.META_SHIFT_ON != 0
        return when (keyCode) {
            KeyEvent.KEYCODE_Z -> {
                if (shift) ShortcutCommand.Redo else ShortcutCommand.Undo
            }

            KeyEvent.KEYCODE_Y -> {
                if (shift) null else ShortcutCommand.Redo
            }

            KeyEvent.KEYCODE_B -> {
                if (shift) null else ShortcutCommand.Action(ToolbarAction.Bold)
            }

            KeyEvent.KEYCODE_I -> {
                if (shift) null else ShortcutCommand.Action(ToolbarAction.Italic)
            }

            KeyEvent.KEYCODE_K -> {
                if (shift) null else ShortcutCommand.Action(ToolbarAction.Link)
            }

            KeyEvent.KEYCODE_C -> {
                if (shift) ShortcutCommand.Action(ToolbarAction.Code) else null
            }

            KeyEvent.KEYCODE_X -> {
                if (shift) ShortcutCommand.Action(ToolbarAction.Strike) else null
            }

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_6 -> {
                if (shift) null else ShortcutCommand.Action(ToolbarAction.SetHeading(keyCode - KeyEvent.KEYCODE_0))
            }

            else -> {
                null
            }
        }
    }
}

/**
 * `InputConnection` wrapper: intercepts a soft-keyboard newline (smart Enter) and forwards Backspace-shaped
 * deletes to [SmartInput], and — while [SmartInput.readOnly] — silently drops every mutating IME call so a
 * read-only document cannot be typed into (01 §6.2's engine-side read-only enforcement; select + copy still
 * work, since those never reach this wrapper). ONE `Editable.replace` per smart edit (never an insert followed by
 * a correcting rewrite): [intercept] either fully replaces the keyboard's newline with [SmartInput.onEnter]'s own
 * edit, or lets it through unchanged.
 */
internal class SmartInputConnection(
    target: InputConnection,
    private val smart: SmartInput,
) : InputConnectionWrapper(target, false) {
    private var swallowUp = -1

    private fun intercept(t: CharSequence?): Boolean {
        if (t == null || !t.contains('\n')) return false
        if (t.length == 1 && smart.onEnter()) return true
        smart.breakUndo() // multi-char commit containing '\n' (IME paste): let it through as plain text
        return false
    }

    override fun commitText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean {
        if (smart.readOnly) return true
        return intercept(text) || super.commitText(text, newCursorPosition)
    }

    // API 33 overload: InputConnectionWrapper forwards it straight to the target, bypassing the 2-arg override.
    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?,
    ): Boolean {
        if (smart.readOnly) return true
        return intercept(text) || super.commitText(text, newCursorPosition, textAttribute)
    }

    override fun setComposingText(
        text: CharSequence?,
        newCursorPosition: Int,
    ): Boolean = if (smart.readOnly) true else super.setComposingText(text, newCursorPosition)

    override fun setComposingText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?,
    ): Boolean = if (smart.readOnly) true else super.setComposingText(text, newCursorPosition, textAttribute)

    override fun commitContent(
        inputContentInfo: InputContentInfo,
        flags: Int,
        opts: Bundle?,
    ): Boolean = if (smart.readOnly) true else super.commitContent(inputContentInfo, flags, opts)

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.hasNoModifiers() &&
            ((k == KeyEvent.KEYCODE_ENTER && smart.onEnter()) || (k == KeyEvent.KEYCODE_DEL && smart.onBackspace()))
        ) {
            swallowUp = k
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && k == swallowUp) {
            swallowUp = -1
            return true
        }
        return super.sendKeyEvent(event)
    }

    override fun deleteSurroundingText(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean {
        if (smart.readOnly) return true
        return (beforeLength == 1 && afterLength == 0 && smart.onBackspace()) ||
            super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int,
    ): Boolean {
        if (smart.readOnly) return true
        return (beforeLength == 1 && afterLength == 0 && smart.onBackspace()) ||
            super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }
}

/**
 * Smart Enter / Backspace / Tab (01 §6.2, `plans/research/markdown.md` §8): each turns into exactly ONE
 * [EditorController.apply] call (one `Editable.replace`, one undo step), never a post-hoc rewrite. [hl] reads the
 * controller's current highlighter lazily (it may still be `null` before the first `install`).
 */
internal class SmartInput(
    private val view: MarkdownEditText,
    private val hl: () -> MarkdownHighlighter?,
    private val undo: MdUndoManager,
    private val apply: (TextEdit) -> Unit,
) {
    val readOnly: Boolean get() = view.readOnly

    /** The collapsed, editable, non-composing caret — smart paths never act on a real selection or mid-IME-composition. */
    private fun caret(): Int? {
        val ed = view.text ?: return null
        val s = view.selectionStart
        return if (view.readOnly || s < 0 || s != view.selectionEnd ||
            BaseInputConnection.getComposingSpanStart(ed) != -1
        ) {
            null
        } else {
            s
        }
    }

    fun onEnter(): Boolean {
        val c = caret() ?: return false
        val h = hl() ?: return false
        val li = h.lineInfoAt(c)
        apply(SmartEdit.onEnter(view.text.toString(), c, li.type, h.isFenceUnclosed(li.line)) ?: return false)
        return true
    }

    /** Runs on EVERY Backspace: O(line), never O(doc) — only the touched line's text is passed to [SmartEdit]. */
    fun onBackspace(): Boolean {
        val c = caret() ?: return false
        val li = hl()?.lineInfoAt(c) ?: return false
        if (c == li.start || li.type.isVerbatim || (li.listDepth == 0 && li.quoteDepth == 0)) return false
        val e = SmartEdit.onBackspace(TextUtils.substring(view.text, li.start, li.end), c - li.start) ?: return false
        apply(TextEdit(e.start + li.start, e.end + li.start, e.replacement, e.selStart + li.start, e.selEnd + li.start))
        return true
    }

    /** Tab is ALWAYS consumed: `TextView.shouldAdvanceFocusOnTab()` is true for our `inputType`, so "default" =
     * focus leaves the editor. Shift+Tab outside a list is a no-op (never inserts a literal tab backwards). */
    fun onTab(outdent: Boolean) {
        val c = caret()
        val li = c?.let { hl()?.lineInfoAt(it) }
        if (c != null && li != null && !li.type.isVerbatim && li.listDepth > 0) {
            val t = view.text.toString()
            (if (outdent) SmartEdit.outdentListItem(t, c) else SmartEdit.indentListItem(t, c))?.let {
                apply(it)
                return
            }
        }
        val a = minOf(view.selectionStart, view.selectionEnd)
        if (!outdent && !view.readOnly) {
            apply(TextEdit(a, maxOf(view.selectionStart, view.selectionEnd), "\t", a + 1))
        }
    }

    fun breakUndo() = undo.hardBreak()
}
