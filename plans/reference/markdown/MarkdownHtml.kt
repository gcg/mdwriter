package mdwriter.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.footnotes.FootnotesExtension
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.gfm.alerts.AlertsExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Preview / export renderer (commonmark-java 0.30.0 + GFM extensions). Pure JVM; call off the main thread.
 * Thread-safe: commonmark Parser and HtmlRenderer are immutable after build().
 */
public class MarkdownHtml(allowRawHtml: Boolean = true) {
    private val exts: List<Extension> = listOf(
        TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
        AutolinkExtension.create(), FootnotesExtension.create(), YamlFrontMatterExtension.create(),
        AlertsExtension.create(), HeadingAnchorExtension.create(),
    )
    private val parser: Parser = Parser.builder().extensions(exts).build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder().extensions(exts)
        .escapeHtml(!allowRawHtml)
        .sanitizeUrls(true)
        .build()

    /** HTML body fragment for [markdown] (front matter is dropped). */
    public fun renderBody(markdown: String): String = renderer.render(parser.parse(markdown))

    /**
     * Full page for WebView.loadDataWithBaseURL("https://appassets.androidplatform.net/doc/", page, "text/html", "utf-8", null).
     * [themeClass] = "light" | "dark" (resolved by the app, not prefers-color-scheme); [cssVars] e.g.
     * mapOf("--font-size" to "18px", "--measure" to "64ch", "--fg" to "#1a1a1a", "--bg" to "#f7f7f5").
     */
    public fun renderPage(markdown: String, themeClass: String, cssVars: Map<String, String>, title: String = ""): String {
        val vars = cssVars.entries.joinToString(";") { "${it.key}:${it.value}" }
        return """<!DOCTYPE html>
<html class="$themeClass" lang="">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https://appassets.androidplatform.net data:; style-src https://appassets.androidplatform.net 'unsafe-inline'; font-src https://appassets.androidplatform.net; script-src 'none'; frame-src 'none'; form-action 'none'">
<title>${escape(title)}</title>
<link rel="stylesheet" href="https://appassets.androidplatform.net/assets/preview/preview.css">
<style>:root{$vars}</style>
</head>
<body><article class="md">
${renderBody(markdown)}</article></body>
</html>
"""
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
