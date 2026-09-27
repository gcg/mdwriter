package dev.mdwriter.ui.preview

import androidx.compose.ui.graphics.Color
import dev.mdwriter.ui.theme.WriterColors
import dev.mdwriter.ui.theme.WriterFont
import kotlin.math.roundToInt

/**
 * What [preview.css][/assets/preview/preview.css] needs to look like the editor (02 §3/§8): a class on `<html>`
 * (never `prefers-color-scheme` — the WebView follows the Activity theme, not the in-app one) plus CSS variables.
 */
data class PreviewTheme(
    val themeClass: String,
    val cssVars: Map<String, String>,
)

/**
 * Builds [PreviewTheme] from the SAME tokens the editor uses (`WriterColors`/`WriterFont`/`EditorMetrics`), so the
 * preview matches the editor pixel-for-pixel-ish. Pure — every dimension is passed in already resolved (no
 * `android.util.TypedValue`/`Context` here), so [PreviewThemeTest] needs no Robolectric.
 */
object PreviewThemes {
    /**
     * Every CSS-variable value is joined straight into the page's `<style>` block (see
     * [MarkdownHtml.renderPage][dev.mdwriter.markdown.MarkdownHtml.renderPage]), so nothing may pass through that
     * could break out of it — this is deliberately narrow (letters/digits, `#(),.%'-` and whitespace only).
     */
    private val SAFE_CSS_VALUE = Regex("""^[#(),.%\w\s'-]+$""")

    fun isSafeCssValue(value: String): Boolean = SAFE_CSS_VALUE.matches(value)

    fun build(
        colors: WriterColors,
        pureBlack: Boolean,
        font: WriterFont,
        bodyCssPx: Float,
        measureChars: Int?,
        sideDp: Float,
        topDp: Float,
        density: Float,
    ): PreviewTheme {
        val themeClass =
            when {
                !colors.isDark -> "light"
                pureBlack -> "dark black"
                else -> "dark"
            }
        val fontBody =
            when (font) {
                WriterFont.Duo -> "'Duo'"
                WriterFont.Quattro -> "'Quattro'"
                WriterFont.Mono -> "'Mono'"
            }
        val vars =
            mapOf(
                "--bg" to rgba(colors.bg),
                "--text" to rgba(colors.text),
                "--text-secondary" to rgba(colors.textSecondary),
                "--markup" to rgba(colors.markup),
                "--code-bg" to rgba(colors.codeBg),
                "--divider" to rgba(colors.divider),
                "--accent" to rgba(colors.accent),
                "--highlight-bg" to rgba(colors.highlightBg),
                "--font-body" to fontBody,
                "--font-mono" to "'Mono'",
                "--size" to "${bodyCssPx}px",
                "--measure" to (measureChars?.let { "${it}ch" } ?: "none"),
                "--side" to "${sideDp}px",
                "--top" to "${topDp}px",
                "--hairline" to "${1f / density}px",
            )
        vars.forEach { (name, value) ->
            require(isSafeCssValue(value)) { "unsafe preview css value for $name: $value" }
        }
        return PreviewTheme(themeClass, vars)
    }

    private fun rgba(c: Color): String {
        val r = (c.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (c.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (c.blue * 255f).roundToInt().coerceIn(0, 255)
        return "rgba($r,$g,$b,${c.alpha})"
    }
}
