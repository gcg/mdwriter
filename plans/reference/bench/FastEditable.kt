package dev.bench.mdtext

import android.text.DynamicLayout
import android.text.Editable
import android.text.SpanWatcher
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher

/**
 * SpannableStringBuilder that hides DynamicLayout's ChangeWatcher from the span-watcher
 * broadcast that SSB.replace() sends for every span merely *shifted* by an edit.
 * The edited paragraph is still reflowed via DynamicLayout's TextWatcher callback.
 */
class FastEditable(source: CharSequence) : SpannableStringBuilder(source) {
    private var depth = 0
    private var suppress = false

    private val phaseMarker = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Lowest priority TextWatcher => runs after DynamicLayout/TextView watchers.
            // Everything after this point inside replace() is sendToSpanWatchers().
            if (depth == 1) suppress = true
        }
    }

    init {
        setSpan(phaseMarker, 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE) // priority 0
    }

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        depth++
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            depth--
            if (depth == 0) suppress = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        val result = super.getSpans(queryStart, queryEnd, kind)
        if (!suppress || kind != SpanWatcher::class.java || result.isEmpty()) return result
        val filtered = result.filter { (it as Any).javaClass.enclosingClass != DynamicLayout::class.java }
        if (filtered.size == result.size) return result
        val out = java.lang.reflect.Array.newInstance(kind, filtered.size) as Array<T>
        filtered.forEachIndexed { i, w -> out[i] = w }
        return out
    }

    companion object Factory : Editable.Factory() {
        override fun newEditable(source: CharSequence): Editable = FastEditable(source)
    }
}
