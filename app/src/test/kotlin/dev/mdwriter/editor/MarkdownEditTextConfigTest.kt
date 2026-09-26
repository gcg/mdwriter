package dev.mdwriter.editor

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.text.Layout
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric: [MarkdownEditText]'s fixed configuration (Acceptance 2). */
@RunWith(AndroidJUnit4::class)
class MarkdownEditTextConfigTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `never saves state and has no view id`() {
        val et = MarkdownEditText(context)
        assertThat(et.isSaveEnabled).isFalse()
        assertThat(et.id).isEqualTo(View.NO_ID)
    }

    @Test
    fun `input type and ime options`() {
        val et = MarkdownEditText(context)
        assertThat(et.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE).isNotEqualTo(0)
        assertThat(et.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).isNotEqualTo(0)
        assertThat(et.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT).isNotEqualTo(0)
        assertThat(et.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI).isNotEqualTo(0)
        assertThat(et.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN).isNotEqualTo(0)
    }

    @Test
    fun `break strategy is simple and no scroll-to-focus jump`() {
        val et = MarkdownEditText(context)
        assertThat(et.breakStrategy).isEqualTo(Layout.BREAK_STRATEGY_SIMPLE)
        assertThat(et.revealOnFocusHint).isFalse()
    }

    @Test
    fun `never scrolls itself`() {
        val et = MarkdownEditText(context)
        et.scrollTo(0, 500)
        assertThat(et.scrollY).isEqualTo(0)
    }

    @Test
    fun `copy yields a plain String clip even over a styled span`() {
        val et = MarkdownEditText(context)
        val styled =
            SpannableString("hello world").apply {
                setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        et.setText(styled, android.widget.TextView.BufferType.EDITABLE)
        et.setSelection(0, 5)
        et.onTextContextMenuItem(android.R.id.copy)
        val cm = context.getSystemService(ClipboardManager::class.java)
        val item = cm.primaryClip?.getItemAt(0)
        assertThat(item).isNotNull()
        assertThat(item!!.text).isEqualTo("hello")
        assertThat(item.text is String).isTrue()
    }

    @Test
    fun `caret drawable padding is halfExtraPx on both ends`() {
        val caret = CaretDrawable(widthPx = 8, radiusPx = 4f)
        caret.halfExtraPx = 7
        val rect = Rect()
        val hasPadding = caret.getPadding(rect)
        assertThat(hasPadding).isTrue()
        assertThat(rect.top).isEqualTo(7)
        assertThat(rect.bottom).isEqualTo(7)
    }
}
