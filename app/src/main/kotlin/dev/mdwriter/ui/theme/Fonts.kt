package dev.mdwriter.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.mdwriter.R

/** The three bundled families (02 §1). Editor + UI default: Duo. Code, tables, front matter: always Mono. */
enum class WriterFont(
    val familyRes: Int,
    val regularRes: Int,
    val italicRes: Int,
    val boldRes: Int,
    val boldItalicRes: Int,
) {
    Duo(R.font.duo, R.font.duo_regular, R.font.duo_italic, R.font.duo_bold, R.font.duo_bold_italic),
    Quattro(
        R.font.quattro,
        R.font.quattro_regular,
        R.font.quattro_italic,
        R.font.quattro_bold,
        R.font.quattro_bold_italic,
    ),
    Mono(R.font.mono, R.font.mono_regular, R.font.mono_italic, R.font.mono_bold, R.font.mono_bold_italic),
}

/** Compose family (identity-stable per font; safe as a remember key). Lists files: Compose can't read family XML. */
val WriterFont.fontFamily: FontFamily get() = composeFamilies.getValue(this)

private val composeFamilies: Map<WriterFont, FontFamily> =
    WriterFont.entries.associateWith { f ->
        FontFamily(
            Font(f.regularRes, FontWeight.Normal, FontStyle.Normal),
            Font(f.italicRes, FontWeight.Normal, FontStyle.Italic),
            Font(f.boldRes, FontWeight.Bold, FontStyle.Normal),
            Font(f.boldItalicRes, FontWeight.Bold, FontStyle.Italic),
        )
    }

/** The four static faces of one family as platform Typefaces, each loaded from its own TTF. */
class PlatformFaces(
    val regular: Typeface,
    val italic: Typeface,
    val bold: Typeface,
    val boldItalic: Typeface,
)

/**
 * Platform typefaces for the View-based editor. T06's FontSet = load(ctx, userFont) + load(ctx, Mono).
 * Faces come from the individual files (not family resolution), so span code can compare them by identity
 * and the Quattro-Bold weight metadata bug is irrelevant. Resources caches fonts; cheap to call again.
 */
object PlatformFonts {
    fun load(
        context: Context,
        font: WriterFont,
    ): PlatformFaces {
        val r = context.resources
        return PlatformFaces(
            r.getFont(font.regularRes),
            r.getFont(font.italicRes),
            r.getFont(font.boldRes),
            r.getFont(font.boldItalicRes),
        )
    }

    /** Family typeface from res/font/<font>.xml; `Typeface.create(it, 700, false)` resolves the real Bold file. */
    fun family(
        context: Context,
        font: WriterFont,
    ): Typeface = context.resources.getFont(font.familyRes)
}
