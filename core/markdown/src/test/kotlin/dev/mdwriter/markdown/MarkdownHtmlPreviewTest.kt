package dev.mdwriter.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** T16 additions to [MarkdownHtml]: `headingCount` (step 2) plus the page-shape checks the preview relies on. */
class MarkdownHtmlPreviewTest {
    private val md = MarkdownHtml(allowRawHtml = true)

    @Test
    fun headingCountCountsAtxHeadings() {
        assertThat(md.headingCount("# A\n\ntext\n\n## B")).isEqualTo(2)
    }

    @Test
    fun headingCountCountsSetextHeadings() {
        assertThat(md.headingCount("A\n===")).isEqualTo(1)
    }

    @Test
    fun headingCountIgnoresHeadingLookingLinesInsideFencedCode() {
        assertThat(md.headingCount("```\n# no\n```")).isEqualTo(0)
    }

    @Test
    fun headingCountIgnoresFrontMatter() {
        assertThat(md.headingCount("---\ntitle: x\n---")).isEqualTo(0)
    }

    @Test
    fun renderedPageHasHeadingAnchorViewportAndCsp() {
        val page = md.renderPage("# A\n\ntext\n\n## B", "light", emptyMap(), "T")
        assertThat(page).contains("id=\"a\"")
        assertThat(page).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        assertThat(page).contains("script-src 'none'")
    }
}
