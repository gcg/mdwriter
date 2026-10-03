package dev.mdwriter.ui.preview

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.MainActivity
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.ui.theme.LightWriterColors
import dev.mdwriter.ui.theme.WriterFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device smoke test for the whole locked-down preview pipeline (T16 Acceptance 5): a real [PreviewWebView],
 * added straight to the real [MainActivity]'s window via `addContentView` (not through the Compose overlay — this
 * tests the WebView/asset-loader/link-policy machinery directly, at the level `PreviewOverlay` itself just wires
 * up). Runs on the debug app (`me.gcg.mdwriter.debug`, applicationIdSuffix), which is exactly what 5c checks.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PreviewSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val activity get() = composeRule.activity
    private val theme =
        PreviewThemes.build(
            colors = LightWriterColors,
            pureBlack = false,
            font = WriterFont.Duo,
            bodyCssPx = 17f,
            measureChars = null,
            sideDp = 24f,
            topDp = 56f,
            density = 3f,
        )
    private val renderer = PreviewRenderer(Dispatchers.Default)

    private lateinit var libraryRoot: File
    private lateinit var currentDoc: DocRef
    private lateinit var webView: PreviewWebView
    private val requestLog = mutableListOf<Pair<String, Boolean>>()

    @Before
    fun setUp() {
        libraryRoot = File(activity.filesDir, "library")
        File(libraryRoot, "PreviewTest/img").mkdirs()
        val png =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).let { bmp ->
                ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
        File(libraryRoot, "PreviewTest/img/dot.png").writeBytes(png)
        currentDoc = DocRef.InternalFile("PreviewTest/note.md")

        val handler = DocumentImagePathHandler(activity, libraryRoot) { currentDoc }
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            webView =
                PreviewWebView(activity, handler).also { wv ->
                    // WebViewAssetLoader marks its responses `Cache-Control: private, max-age=31536000` (a whole
                    // year) — real, desirable behaviour in the app (fonts/CSS never change at runtime), but it
                    // means the SAME fixed URLs (e.g. .../res/font/duo_regular.ttf), already fetched by an EARLIER
                    // test in this same instrumentation process (one shared WebView renderer/cache for the whole
                    // app process), get served straight out of the renderer's own cache for every test after it —
                    // without ever calling shouldInterceptRequest (and so without ever reaching requestLog) again.
                    // `cacheMode` alone does not prevent this (it governs the HTTP-cache layer, not blink's own
                    // in-renderer resource cache, which honours the response's Cache-Control regardless); clearing
                    // BOTH caches before every test is what actually makes each test see a fresh request.
                    wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    wv.clearCache(true)
                    wv.requestLog = { url, served -> synchronized(requestLog) { requestLog.add(url to served) } }
                    activity.addContentView(
                        wv,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        libraryRoot.deleteRecursively()
    }

    /** Renders [markdown] and loads it, waiting for `onPageFinished` plus a short settle for the
     * `postVisualStateCallback`-driven scroll application. */
    private fun renderAndShow(
        markdown: String,
        caret: Int = markdown.length,
    ): PreviewPage {
        val page = runBlocking { renderer.render(markdown, caret, theme, title = "T") }
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            webView.onPageFinishedForTest = { latch.countDown() }
            webView.show(page)
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
        Thread.sleep(500) // postVisualStateCallback + scroll application
        return page
    }

    private fun findCount(query: String): Int {
        val latch = CountDownLatch(1)
        var count = 0
        activity.runOnUiThread {
            webView.setFindListener { _, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    count = numberOfMatches
                    latch.countDown()
                }
            }
            webView.findAllAsync(query)
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        activity.runOnUiThread { webView.clearMatches() }
        return count
    }

    private fun uiThreadValue(block: (WebView) -> Int): Int {
        var result = 0
        val latch = CountDownLatch(1)
        activity.runOnUiThread {
            result = block(webView)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return result
    }

    /** Chromium fetches an `@font-face` lazily, only once it actually needs to paint text in that face — which can
     * still be in flight for a little while after `onPageFinished` under device load (the full instrumented suite
     * running back to back). Polls [requestLog] instead of a single fixed sleep. */
    private fun waitUntilLogged(
        timeoutMs: Long = 8_000,
        predicate: (List<Pair<String, Boolean>>) -> Boolean,
    ): List<Pair<String, Boolean>> {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val snapshot = synchronized(requestLog) { requestLog.toList() }
            if (predicate(snapshot)) return snapshot
            Thread.sleep(50)
        }
        return synchronized(requestLog) { requestLog.toList() }
    }

    // ---- (a) rendered content, markers hidden --------------------------------------------------------------------

    @Test
    fun renderedContentShowsTextNotMarkers() {
        renderAndShow("# Hello Preview")
        assertThat(findCount("Hello Preview")).isAtLeast(1)
        assertThat(findCount("# Hello")).isEqualTo(0)
    }

    // ---- (b) a remote image is never fetched (no INTERNET; blockNetworkLoads) -----------------------------------

    @Test
    fun remoteImageIsBlockedButRestOfPageRenders() {
        renderAndShow("![x](https://example.com/x.png)\n\nafter image")
        assertThat(findCount("after image")).isAtLeast(1)
        val servedExample =
            synchronized(requestLog) { requestLog.any { it.first.contains("example.com") && it.second } }
        assertThat(servedExample).isFalse()
    }

    // ---- (c) local assets/fonts served from res/asset, on the debug app -------------------------------------------

    @Test
    fun localCssAndFontAreServedOnTheDebugApp() {
        assertThat(
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
        ).isEqualTo("me.gcg.mdwriter.debug")
        // Plain body text (not just the H1, which is rendered bold — duo_bold.ttf — by preview.css) so the
        // NORMAL-weight face is the one actually requested.
        renderAndShow("# Hello Preview\n\nSome plain body text.")
        val served =
            waitUntilLogged { log ->
                log.any { it.first.endsWith("/assets/preview/preview.css") && it.second } &&
                    log.any { it.first.endsWith("/res/font/duo_regular.ttf") && it.second }
            }
        assertThat(served.any { it.first.endsWith("/assets/preview/preview.css") && it.second }).isTrue()
        assertThat(served.any { it.first.endsWith("/res/font/duo_regular.ttf") && it.second }).isTrue()
    }

    // ---- (d) heading-scroll-sync (a fragment jump, or the proportional-fraction fallback) --------------------------

    @Test
    fun openingUnderAMidDocumentHeadingScrollsThePreview() {
        val sb = StringBuilder()
        val headingStarts = mutableListOf<Int>()
        repeat(40) { i ->
            headingStarts += sb.length
            sb.append("# Heading ").append(i + 1).append("\n\n")
            sb.append("Body text for heading ").append(i + 1).append(".\n\n")
        }
        val text = sb.toString()
        val caret = headingStarts[29] + 2 // inside heading 30's own line
        renderAndShow(text, caret)
        var scrollY = 0
        val end = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < end) {
            scrollY = uiThreadValue { it.scrollY }
            if (scrollY > 0) break
            Thread.sleep(50)
        }
        assertThat(scrollY).isGreaterThan(0)
    }

    // ---- (e) relative images: served for a real neighbour, blocked past the note's directory -----------------------

    @Test
    fun relativeImageNextToTheNoteIsServedAndTraversalIsBlocked() {
        renderAndShow("![a](img/dot.png)\n\n![b](../../../../etc/hosts.png)\n\ntext")
        val served = waitUntilLogged { log -> log.any { it.first.endsWith("/doc/img/dot.png") && it.second } }
        assertThat(served.any { it.first.endsWith("/doc/img/dot.png") && it.second }).isTrue()
        assertThat(served.any { it.first.contains("hosts.png") && it.second }).isFalse()
    }

    // ---- (f) a sanitized javascript: link never launches an activity ------------------------------------------------

    @Test
    fun sanitizedJavascriptLinkStartsNoActivity() {
        renderAndShow("[js](javascript:alert(1))")
        val monitor =
            InstrumentationRegistry.getInstrumentation().addMonitor(
                IntentFilter(Intent.ACTION_VIEW),
                null,
                false,
            )
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val link = device.wait(Until.findObject(By.text("js")), 5_000)
            assertThat(link).isNotNull()
            link!!.click()
            Thread.sleep(1_000)
            assertThat(monitor.hits).isEqualTo(0)
        } finally {
            InstrumentationRegistry.getInstrumentation().removeMonitor(monitor)
        }
    }
}
