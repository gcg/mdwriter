package dev.mdwriter.editor

import android.text.SpannableStringBuilder
import android.text.Spanned
import dev.mdwriter.editor.spans.HangRoomSpan
import dev.mdwriter.editor.spans.SpanFactory
import dev.mdwriter.editor.spans.SpanMaterializer
import dev.mdwriter.editor.spans.SpanSpec
import dev.mdwriter.markdown.MarkdownHighlighter

/**
 * Builds a fully styled [SpannableStringBuilder] for [text] (meant to run on `Dispatchers.Default`, see
 * [EditorController.install]): [hl] is `fullScan`ned, then every line's [SpanSpec]s are materialized and set
 * with `SPAN_EXCLUSIVE_EXCLUSIVE` (01 §10 rule 7) — except [hangRoom], the one document-wide exception, set
 * with `SPAN_INCLUSIVE_INCLUSIVE` so text typed at the very start/end of the document stays inside the gutter.
 */
fun buildStyledDocument(
    text: String,
    hl: MarkdownHighlighter,
    factory: SpanFactory,
    mat: SpanMaterializer,
    hangRoom: HangRoomSpan?,
): SpannableStringBuilder {
    hl.fullScan(text)
    val ssb = SpannableStringBuilder(text)
    val specs = ArrayList<SpanSpec>(text.length / 8)
    factory.specsForLines(text, hl, 0, hl.lineCount, specs)
    for (spec in specs) {
        ssb.setSpan(mat.create(spec), spec.start, spec.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (hangRoom != null) {
        ssb.setSpan(hangRoom, 0, ssb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }
    return ssb
}
