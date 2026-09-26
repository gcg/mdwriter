package dev.mdwriter.editor.spans

import android.content.Context
import android.text.NoCopySpan
import android.text.ParcelableSpan
import android.text.style.UpdateLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.theme.LightWriterColors
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric: every [SpanMaterializer]-produced object obeys 01 §10 rules 4/6/7 (Acceptance 2). */
@RunWith(AndroidJUnit4::class)
class SpanMaterializerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun style(gutterPx: Int = 0) =
        EditorStyle.create(context, LightWriterColors).also {
            it.textSizePx = 40f
            it.gutterPx = gutterPx
        }

    private fun allSpecs(): List<SpanSpec> =
        listOf(
            SpanSpec(SpanKind.HEADING, 1, 0, 1),
            SpanSpec(SpanKind.STRONG, 0, 0, 1),
            SpanSpec(SpanKind.EMPHASIS, 0, 0, 1),
            SpanSpec(SpanKind.CODE, 0, 0, 1),
            SpanSpec(SpanKind.CODE_BLOCK, 0, 0, 1),
            SpanSpec(SpanKind.MARKER, 0, 0, 1),
            SpanSpec(SpanKind.STRIKE, 0, 0, 1),
            SpanSpec(SpanKind.MARK, 0, 0, 1),
            SpanSpec(SpanKind.DONE_TASK, 0, 0, 1),
            SpanSpec(SpanKind.LINK_UNDERLINE, 0, 0, 1),
            SpanSpec(SpanKind.TABLE_ROW, 0, 0, 1),
            SpanSpec(SpanKind.TASK, 1, 0, 1),
            SpanSpec(SpanKind.HEADING_HANG, 30, 0, 1),
            SpanSpec(SpanKind.HANGING_INDENT, 30, 0, 1),
            SpanSpec(SpanKind.MONO, 0, 0, 1),
        )

    @Test
    fun everySpanKindIsAnMdStyleSpanNeverParcelableOrNoCopy() {
        val mat = SpanMaterializer(style())
        for (spec in allSpecs()) {
            val obj = mat.create(spec)
            assertThat(obj).isInstanceOf(MdStyleSpan::class.java)
            assertThat(obj).isNotInstanceOf(ParcelableSpan::class.java)
            assertThat(obj).isNotInstanceOf(NoCopySpan::class.java)
        }
    }

    @Test
    fun headingHangAndHangingIndentAreUpdateLayout() {
        val mat = SpanMaterializer(style())
        assertThat(mat.create(SpanSpec(SpanKind.HEADING_HANG, 30, 0, 1))).isInstanceOf(UpdateLayout::class.java)
        assertThat(mat.create(SpanSpec(SpanKind.HANGING_INDENT, 30, 0, 1))).isInstanceOf(UpdateLayout::class.java)
    }

    @Test
    fun hangRoomSpanIsNeitherUpdateLayoutNorMdStyleSpan() {
        // Rule 7 exception: HangRoomSpan is set once on install and never reconciled — T07 must never touch it.
        val hangRoom = HangRoomSpan(style())
        assertThat(hangRoom).isNotInstanceOf(UpdateLayout::class.java)
        assertThat(hangRoom).isNotInstanceOf(MdStyleSpan::class.java)
    }

    @Test
    fun headingHangClampsToTheCurrentGutter() {
        assertThat(HeadingHangSpan(30, style(gutterPx = 0)).getLeadingMargin(true)).isEqualTo(0)
        assertThat(HeadingHangSpan(30, style(gutterPx = 40)).getLeadingMargin(true)).isEqualTo(-30)
        // arg (30) itself is clamped when the gutter is narrower than the marker.
        assertThat(HeadingHangSpan(30, style(gutterPx = 10)).getLeadingMargin(true)).isEqualTo(-10)
        assertThat(HeadingHangSpan(30, style(gutterPx = 40)).getLeadingMargin(false)).isEqualTo(0)
    }

    @Test
    fun taskSpanIsNotNoCopySpan() {
        // factcheck A12: the SpannableStringBuilder copy constructor (MdEditableFactory.newEditable) drops
        // NoCopySpan spans, which would silently lose T08's task hit-test marker on every setText.
        assertThat(TaskSpan(1)).isNotInstanceOf(NoCopySpan::class.java)
    }
}
