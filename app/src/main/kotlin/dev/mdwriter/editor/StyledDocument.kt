package dev.mdwriter.editor

import android.text.SpannableString
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
    val specs = ArrayList<SpanSpec>(text.length / 8)
    factory.specsForLines(text, hl, 0, hl.lineCount, specs)
    // SpannableString.setSpan is O(1) per span, while SpannableStringBuilder.setSpan degrades with the span count
    // (quadratic: 3 s for 28k spans at 300k chars). The builder's copy constructor then copies all spans in bulk.
    val tmp = SpannableString(text)
    for (spec in specs) {
        tmp.setSpan(mat.create(spec), spec.start, spec.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (hangRoom != null) tmp.setSpan(hangRoom, 0, tmp.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    return SpannableStringBuilder(tmp)
}
