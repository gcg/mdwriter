package dev.mdwriter.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Ported from `test/HtmlCheck.kt`'s `md` sample and §H's exact assertions. */
class MarkdownHtmlTest {
    companion object {
        // Verbatim from HtmlCheck.kt's `md` value.
        private val SAMPLE =
            "---\ntitle: T\n---\n# Head *x*\n\n| a | b |\n|:-|-:|\n| 1 | 2 |\n\n- [x] done\n\n" +
                "Note[^1] ~~s~~ www.x.com\n\n[^1]: fn\n\n<script>alert(1)</script>\n\n" +
                "[js](javascript:alert(1)) ![i](img/a.png)\n\n> [!NOTE]\n> alert\n"
    }

    @Test
    fun renderBodyFeaturesAndSanitizing() {
        val body = MarkdownHtml().renderBody(SAMPLE)
        assertThat(body).contains("type=\"checkbox\"")
        assertThat(body).contains("checked")
        assertThat(body).contains("footnote")
        assertThat(body).contains("href=\"#fn")
        assertThat(body).contains("align=\"left\"")
        assertThat(body).contains("align=\"right\"")
        assertThat(body).contains("<h1 id=\"head-x\">")
        assertThat(body).contains("href=\"\"")
        assertThat(body).doesNotContain("javascript:")
        assertThat(body).doesNotContain("title: T")
    }

    @Test
    fun alertExtension() {
        val body = MarkdownHtml().renderBody("> [!NOTE]\n> hi")
        assertThat(body).contains("markdown-alert-note")
    }

    @Test
    fun rawHtmlEscapedWhenDisallowed() {
        val body = MarkdownHtml(allowRawHtml = false).renderBody("<script>x</script>")
        assertThat(body).contains("&lt;script&gt;")
    }

    @Test
    fun renderPageCspAndTheme() {
        val page = MarkdownHtml().renderPage(SAMPLE, "dark", mapOf("--font-size" to "18px"), "A<B")
        assertThat(page).contains("http-equiv=\"Content-Security-Policy\"")
        assertThat(page).contains("default-src 'none'")
        assertThat(page).contains("<html class=\"dark\"")
        assertThat(page).contains(":root{--font-size:18px}")
        assertThat(page).contains("<title>A&lt;B</title>")
        assertThat(page).doesNotContain("<script src")
    }
}
