package dev.mdwriter.editor.spans

import android.content.Context
import android.graphics.Typeface
import dev.mdwriter.ui.theme.PlatformFonts
import dev.mdwriter.ui.theme.WriterFont

/**
 * The six platform faces the editor and (from T06) every span need: the user's chosen font's four faces, plus
 * Mono's regular/bold (code spans, tables, front matter are always Mono, 02 §4). Compare typefaces by identity
 * (`===`), never by name.
 */
class FontSet(
    val regular: Typeface,
    val italic: Typeface,
    val bold: Typeface,
    val boldItalic: Typeface,
    val mono: Typeface,
    val monoBold: Typeface,
) {
    fun isBold(t: Typeface?): Boolean = t === bold || t === boldItalic

    fun isItalic(t: Typeface?): Boolean = t === italic || t === boldItalic

    fun of(
        bold: Boolean,
        italic: Boolean,
    ): Typeface =
        when {
            bold && italic -> boldItalic
            bold -> this.bold
            italic -> this.italic
            else -> regular
        }

    companion object {
        fun load(
            ctx: Context,
            font: WriterFont,
        ): FontSet {
            val f = PlatformFonts.load(ctx, font)
            val m = PlatformFonts.load(ctx, WriterFont.Mono)
            return FontSet(f.regular, f.italic, f.bold, f.boldItalic, m.regular, m.bold)
        }
    }
}
