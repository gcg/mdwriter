package dev.mdwriter.ui.preview

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebView.VisualStateCallback
import androidx.annotation.VisibleForTesting
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import dev.mdwriter.data.library.DocRef
import java.io.File

/**
 * The locked-down preview WebView (T16, 01 §4.6/§10 rule 1): JS off, no file/content access, no network (the app
 * has no INTERNET permission anyway — `blockNetworkLoads` must stay `true`; setting it `false` throws
 * `SecurityException`, which is the point). Everything it shows comes from [WebViewAssetLoader] over the fixed
 * `https://appassets.androidplatform.net` origin: `/assets/` (preview.css), `/res/` (the bundled fonts, C13 — never
 * copy them into `assets/`), `/doc/` ([DocumentImagePathHandler], relative images next to the note).
 */
class PreviewWebView(
    context: Context,
    imageHandler: DocumentImagePathHandler,
) : WebView(context) {
    /** Predictive-back/Esc/glyph all call the same close action; wired by [dev.mdwriter.ui.preview.PreviewOverlay]. */
    var onEscape: (() -> Unit)? = null

    @VisibleForTesting
    var requestLog: ((url: String, served: Boolean) -> Unit)? = null

    @VisibleForTesting
    var onPageFinishedForTest: (() -> Unit)? = null

    private val loader =
        WebViewAssetLoader
            .Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
            .addPathHandler("/doc/", imageHandler)
            .build()

    private var pending: PreviewPage? = null
    private var lastShown: PreviewPage? = null

    init {
        isSaveEnabled = false
        settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false // defaults to TRUE — must be turned off explicitly (a real bug otherwise)
            blockNetworkLoads = true // already true without INTERNET; NEVER set false (SecurityException, rule 1)
            domStorageEnabled = false
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }
        webViewClient =
            object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val response = loader.shouldInterceptRequest(request.url)
                    requestLog?.invoke(request.url.toString(), response != null)
                    return response
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean =
                    when (PreviewLinkPolicy.decide(request.url.toString())) {
                        LinkAction.InPage -> {
                            false
                        }

                        LinkAction.External -> {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE),
                                )
                            }
                            true
                        }

                        LinkAction.Block -> {
                            true
                        }
                    }

                override fun onPageFinished(
                    view: WebView,
                    url: String?,
                ) {
                    val page = pending
                    if (page != null) {
                        view.postVisualStateCallback(
                            0L,
                            object : VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    applyScroll(page)
                                }
                            },
                        )
                    }
                    onPageFinishedForTest?.invoke()
                }
            }
    }

    /** Idempotent: reloads only when [page] is a genuinely new render (`!==` [lastShown]) — a redundant `show` of
     * the same page (e.g. a recomposition) never restarts scroll/animation. */
    fun show(page: PreviewPage) {
        if (page === lastShown) return
        lastShown = page
        pending = page
        loadDataWithBaseURL(BASE + (page.anchor?.let { "#$it" } ?: ""), page.html, "text/html", "utf-8", null)
        requestFocus() // so KEYCODE_ESCAPE (dispatchKeyEvent) reaches this view, not the Compose tree behind it
    }

    /** Scroll-sync fallback (T16 STATUS: whether the fragment anchor alone was enough, or this fraction fallback
     * was needed for every case, is recorded there — see the task's own "UNVERIFIED" note). Runs after the page's
     * first real frame ([android.webkit.WebView.VisualStateCallback]), not `onPageFinished` alone (that fires
     * before layout/paint). If an anchor was given AND the WebView's own in-page navigation already scrolled
     * (`scrollY > 0`), this is a no-op; otherwise it scrolls to the caret's proportional position in the document. */
    private fun applyScroll(page: PreviewPage) {
        if (page.anchor != null && scrollY > 0) return
        val maxScroll = (contentHeight * resources.displayMetrics.density - height).coerceAtLeast(0f)
        scrollTo(0, (page.fraction * maxScroll).toInt())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) onEscape?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val BASE = "https://appassets.androidplatform.net/doc/"
    }
}

/**
 * Lazily creates and owns the single [PreviewWebView] instance for the app's lifetime — created with the Activity
 * [context], never stored in a ViewModel (01 §5: no View reference may live in a ViewModel). `currentDoc` is
 * `@Volatile` because [DocumentImagePathHandler.handle] reads it from a WebView background thread (T16 pitfalls).
 */
class PreviewWebViewHolder(
    private val context: Context,
    private val libraryRoot: File,
) {
    @Volatile
    var currentDoc: DocRef? = null

    private var webView: PreviewWebView? = null

    fun get(): PreviewWebView =
        webView ?: PreviewWebView(context, DocumentImagePathHandler(context, libraryRoot) { currentDoc }).also {
            webView = it
        }

    /** Called from `MdWriterRoot`'s `DisposableEffect(Unit) { onDispose { ... } }` — the WebView must not outlive
     * the composition that created it. */
    fun destroy() {
        webView?.destroy()
        webView = null
    }
}
