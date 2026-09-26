package dev.mdwriter.editor

import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import dev.mdwriter.BuildConfig

/**
 * `SpannableStringBuilder` that does not broadcast "span shifted" events to `SpanWatcher`s after an edit,
 * except for broadcasts whose query range touches the selection or composing region (01 §4.3, factcheck A12/A13).
 *
 * Without this, `DynamicLayout.ChangeWatcher` (a `SpanWatcher`) is notified for every styled span whose bounds
 * merely shifted with the edit (their text did not change — the edited paragraph itself was already reflowed
 * via the ordinary `TextWatcher` path), which forces a full re-layout of every styled paragraph after the
 * cursor on each keystroke (162 ms at 100k chars). See `plans/reference/bench/FastEditable2.kt` (verified
 * benchmark code) — this class copies its logic verbatim, renamed.
 */
class MdEditable(
    source: CharSequence,
) : SpannableStringBuilder(source) {
    private var depth = 0
    private var silent = false
    private var k0 = -1
    private var k1 = -1
    private var k2 = -1
    private var k3 = -1

    /** Debug-only counter of suppressed `SpanWatcher` broadcasts (perf diagnostics; not read in release). */
    var suppressed = 0L
        private set

    private val phaseMarker =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (depth == 1) {
                    val self = this@MdEditable
                    k0 = Selection.getSelectionStart(self)
                    k1 = Selection.getSelectionEnd(self)
                    k2 = BaseInputConnection.getComposingSpanStart(self)
                    k3 = BaseInputConnection.getComposingSpanEnd(self)
                    silent = true
                }
            }
        }

    init {
        setSpan(phaseMarker, 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) // priority 0 => last TextWatcher
    }

    override fun replace(
        start: Int,
        end: Int,
        tb: CharSequence,
        tbstart: Int,
        tbend: Int,
    ): SpannableStringBuilder {
        depth++
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            depth--
            if (depth == 0) silent = false
        }
    }

    private fun touchesKeep(
        a: Int,
        b: Int,
    ): Boolean = (k0 in a..b) || (k1 in a..b) || (k2 >= 0 && k2 in a..b) || (k3 >= 0 && k3 in a..b)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(
        queryStart: Int,
        queryEnd: Int,
        kind: Class<T>?,
    ): Array<T> {
        if (silent && kind === SpanWatcher::class.java && !touchesKeep(queryStart, queryEnd)) {
            if (BuildConfig.DEBUG) suppressed++
            return NO_WATCHERS as Array<T>
        }
        return super.getSpans(queryStart, queryEnd, kind)
    }

    companion object {
        private val NO_WATCHERS: Array<SpanWatcher> = emptyArray()
    }
}

/** Installed via `MarkdownEditText.setEditableFactory` before any `setText` call. [enabled] is a debug kill switch. */
object MdEditableFactory : Editable.Factory() {
    @Volatile
    @JvmField
    var enabled = true

    override fun newEditable(source: CharSequence): Editable =
        if (enabled) MdEditable(source) else SpannableStringBuilder(source)
}
