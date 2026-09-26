package dev.bench.mdtext

import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection

/**
 * v2: during the span-watcher broadcast phase of replace() (after every TextWatcher ran), return NO
 * SpanWatchers for ranges that do not touch the selection/composing region. The spans broadcast in
 * that phase were merely shifted by the edit (their text did not change), and the edited paragraph
 * was already reflowed by DynamicLayout's TextWatcher callback and invalidated by TextView's.
 * Selection/composing notifications are still delivered because their query range contains them.
 */
class FastEditable2(source: CharSequence) : SpannableStringBuilder(source) {
    private var depth = 0
    private var silent = false
    private var k0 = -1
    private var k1 = -1
    private var k2 = -1
    private var k3 = -1
    var suppressed = 0L

    private val phaseMarker = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (depth == 1) {
                val self = this@FastEditable2
                k0 = Selection.getSelectionStart(self); k1 = Selection.getSelectionEnd(self)
                k2 = BaseInputConnection.getComposingSpanStart(self); k3 = BaseInputConnection.getComposingSpanEnd(self)
                silent = true
            }
        }
    }

    init {
        setSpan(phaseMarker, 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) // priority 0 => last TextWatcher
    }

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        depth++
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            depth--
            if (depth == 0) silent = false
        }
    }

    private fun touchesKeep(a: Int, b: Int): Boolean =
        (k0 in a..b) || (k1 in a..b) || (k2 >= 0 && k2 in a..b) || (k3 >= 0 && k3 in a..b)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        if (silent && kind === SpanWatcher::class.java && !touchesKeep(queryStart, queryEnd)) {
            suppressed++
            return NO_WATCHERS as Array<T>
        }
        return super.getSpans(queryStart, queryEnd, kind)
    }

    companion object Factory : Editable.Factory() {
        private val NO_WATCHERS: Array<SpanWatcher> = emptyArray()
        override fun newEditable(source: CharSequence): Editable = FastEditable2(source)
    }
}
