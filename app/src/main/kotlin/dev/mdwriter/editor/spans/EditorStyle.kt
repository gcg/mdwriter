package dev.mdwriter.editor.spans

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import dev.mdwriter.editor.focusOverlayArgb
import dev.mdwriter.ui.theme.EditorMetrics
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterColors
import dev.mdwriter.ui.theme.WriterFont

/** `WriterColors`, converted once to `@ColorInt` for the View-based editor (spans/paints take `Int`, not `Color`). */
data class EditorColors(
    val bg: Int,
    val text: Int,
    val textSecondary: Int,
    val markup: Int,
    val accent: Int,
    val selection: Int,
    val codeBg: Int,
    val highlightBg: Int,
    val highlightText: Int,
    val focusDim: Int,
    val searchMatch: Int,
    val searchMatchFocused: Int,
)

fun WriterColors.toEditorColors(): EditorColors =
    EditorColors(
        bg = bg.toArgb(),
        text = text.toArgb(),
        textSecondary = textSecondary.toArgb(),
        markup = markup.toArgb(),
        accent = accent.toArgb(),
        selection = selection.toArgb(),
        codeBg = codeBg.toArgb(),
        highlightBg = highlightBg.toArgb(),
        highlightText = highlightText.toArgb(),
        focusDim = focusDim.toArgb(),
        searchMatch = searchMatch.toArgb(),
        searchMatchFocused = searchMatchFocused.toArgb(),
    )

/**
 * Mutable, shared by the widget and (from T06) every span. Mutate a field, then call
 * [dev.mdwriter.editor.EditorController.setStyle] — nothing reads a stale copy because there is only ever one
 * instance per controller.
 */
class EditorStyle(
    var font: WriterFont,
    var fonts: FontSet,
    var colors: EditorColors,
    var textSizeStep: Int = EditorMetrics.DEFAULT_TEXT_SIZE_STEP,
    var measureChars: Int = EditorMetrics.DEFAULT_MEASURE_CHARS,
    var highlightSyntax: Boolean = false,
) {
    // Derived by EditorScrollView from EditorGeometry on width/settings change; read-only for everyone else.
    var widthClass: WidthClass = WidthClass.Compact
    var textSizePx: Float = 0f
    var lineSpacingExtraPx: Float = 0f
    var gutterPx: Int = 0

    /** 1 dp, set from density in [create]. */
    var underlinePx: Float = 1f

    /** T15 (02 §2): the Focus Mode dim overlay colour, always derived live from [colors] — never stale. */
    val focusOverlayColor: Int get() = focusOverlayArgb(colors.bg, colors.text, colors.focusDim)

    /** index = heading level (0 = body); 02 §3. */
    val headingScale: FloatArray = floatArrayOf(1f, 1.60f, 1.40f, 1.25f, 1.10f, 1.00f, 1.00f)

    companion object {
        fun create(
            ctx: Context,
            colors: WriterColors,
            font: WriterFont = WriterFont.Duo,
        ): EditorStyle =
            EditorStyle(font, FontSet.load(ctx, font), colors.toEditorColors()).also {
                it.underlinePx = ctx.resources.displayMetrics.density
            }
    }
}
