package dev.mdwriter.ui.preview

import java.net.URI

/** What [PreviewWebView.shouldOverrideUrlLoading][android.webkit.WebViewClient] does with a link tap. */
enum class LinkAction { InPage, External, Block }

/**
 * Pure (uses [java.net.URI], never `android.net.Uri`, so [PreviewLinkPolicyTest] needs no Robolectric): decides
 * what a tapped preview link should do. The preview has no navigation of its own (relative `.md` links are out of
 * scope — Scope/Out), so anything landing back on our own asset-loader host is either an in-page footnote jump or
 * refused; `http(s)`/`mailto` go to the system browser/mail app; everything else (including a sanitized
 * `javascript:` link, which commonmark-java turns into `href=""` — i.e. a navigation back to the base URL with no
 * fragment) is blocked.
 */
object PreviewLinkPolicy {
    fun decide(url: String): LinkAction {
        val u = runCatching { URI(url) }.getOrNull() ?: return LinkAction.Block
        if (u.host == "appassets.androidplatform.net") {
            return if (!u.rawFragment.isNullOrEmpty() && u.path == "/doc/") LinkAction.InPage else LinkAction.Block
        }
        return when (u.scheme?.lowercase()) {
            "http", "https", "mailto" -> LinkAction.External
            else -> LinkAction.Block
        }
    }
}
