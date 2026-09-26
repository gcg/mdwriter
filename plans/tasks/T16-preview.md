# T16 — Preview (WebView) + swipe to preview

**Goal** An end→start swipe in the editor slides in a full-screen, read-only rendered preview of the note. The same preview opens from overflow › Preview, Ctrl+R, or the accessibility action "Show preview". It uses the same fonts, colours, heading scale and column as the editor, opens scrolled to the caret's heading, and closes with a start→end swipe, predictive Back, Esc, Ctrl+R or the `arrow_back` glyph. The preview is locked down: no JS, no file or content access, no network. Relative images next to the note are shown.

**Depends on**
- T13:
  - `Modifier.editorSwipeNav(enabled, accepts, onArmedDown, onSwipe)` and `SwipeDir`.
  - `MdWriterRoot`'s `onOpenPreview` / `previewAvailable` hooks and the `PreviewSlot()` position (before `RootBackHandlers`).
  - The mutual exclusion of drawer, preview and find.
  - `OverflowActions.onPreview`, `AppCommand` + `AppShortcuts`, and the "Open library" a11y action pattern in `EditorHost`.
- T04: `MarkdownHtml(allowRawHtml = true).renderPage(markdown, themeClass, cssVars: Map<String, String>, title)`, with the CSP meta tag and the `/assets/preview/preview.css` link (from `plans/reference/markdown/MarkdownHtml.kt`).
- T02: the 12 TTFs in `res/font/` (`duo_regular`, `duo_italic`, `duo_bold`, `duo_bold_italic`, and the same for `quattro_*` and `mono_*`), `WriterColors`, `WriterFont`, `EditorMetrics`, `WriterMotion.PREVIEW_TRANSITION_MS`, `emphasizedDecelerate/Accelerate`, and the icons `ic_arrow_back`, `ic_share`, `ic_preview`.
- T10/T14: `InternalStore`'s library root (`filesDir/library`) and `DocRef.TreeDoc` (T14 is executed before T16 in index order).

**Read first**
- `plans/01-architecture.md` §4.6, §6.1 (MarkdownHtml), §6.4, §7 (Preview HTML row), §10 rule 1
- `plans/02-design-spec.md` §3 (heading scale table, `sed -n '76,89p'`), §8, §11
- `plans/research/markdown.md` §3.1–§3.2 (`sed -n '109,200p'`)
- `plans/research/design.md` §3.5 (`sed -n '241,252p'`), §4.6 (`sed -n '325,329p'`), §6.5 (`sed -n '550,564p'`)
- `plans/research/platform.md` §5 (predictive back, `sed -n '468,497p'`)
- `plans/tasks/T13-swipe-and-chrome.md` Reference §B, §E, §G, §H, §I
- `plans/research/README.md` (C13: never duplicate fonts into assets)

## Scope — In / Out
In:
- `PreviewOverlay` (slide + fade 250 ms, predictive-back progress, glyph row, swipe-to-close) and `PreviewWebView` (locked-down settings, asset loader, link policy, scroll sync).
- `DocumentImagePathHandler` (relative images, internal + SAF, escape-safe), `preview.css`, `PreviewTheme` (CSS variables), `PreviewRenderer` (off-main render), and `MarkdownHtml.headingCount`.
- The four entry points. A `share` glyph that renders only when an `onShare` callback is given.

Out (owner):
- Share-out of the note → **T18**, which passes `onShare`.
- Re-rendering on runtime settings changes is already handled here by keying on `PreviewTheme`; the settings UI itself → **T19**.
- `==highlight==` → `<mark>` in the preview (optional custom DelimiterProcessor) → not in v1; record it in STATUS "Later".
- Side-by-side preview on tablets → not in v1. Restoring an open preview after process death (beyond the saved flag) → **T20**.
- Opening linked `.md` notes from preview links → not in v1 (relative links are swallowed).

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewOverlay.kt` — overlay composable, animation, back progress, glyphs, swipe-to-close
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewWebView.kt` — `class PreviewWebView : WebView` (settings, client, `show(page)`, Esc) + `PreviewWebViewHolder` (lazy, destroy)
- `app/src/main/kotlin/dev/mdwriter/ui/preview/DocumentImagePathHandler.kt` — `WebViewAssetLoader.PathHandler` for `/doc/`
- `app/src/main/kotlin/dev/mdwriter/ui/preview/ImagePath.kt` — pure: `split`, `walk`, `imageMime`
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewLinkPolicy.kt` — pure: `decide(url): LinkAction`
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewTheme.kt` — `PreviewTheme(themeClass, cssVars)` + `PreviewThemes.build(...)`
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewRenderer.kt` — `PreviewPage` + `render(...)` on `Dispatchers.Default`
- `app/src/main/kotlin/dev/mdwriter/ui/preview/PreviewSync.kt` — pure: `anchorIds(html)`, `anchorFor(html, headingIndex)`
- `app/src/main/assets/preview/preview.css` — 02 §8 typography driven by CSS variables
- `app/src/main/res/raw/mdwriter_keep.xml` — `tools:keep="@font/*"` (merge if the file exists)
- `core/markdown/src/main/kotlin/dev/mdwriter/markdown/MarkdownHtml.kt` (modify) — `fun headingCount(markdown: String): Int`; viewport meta if missing
- `core/markdown/src/test/kotlin/dev/mdwriter/markdown/MarkdownHtmlPreviewTest.kt` — headingCount + page checks
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` (modify) — `preview: StateFlow<PreviewPage?>`, `openPreview`, `closePreview`, `previewOpen` in `SavedStateHandle`
- `app/src/main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt`, `ui/root/AppCommands.kt`, `ui/editor/EditorHost.kt` (modify) — wiring, `AppCommand.Preview` (Ctrl+R), "Show preview" action
- `app/src/main/res/values/strings.xml` (modify)
- `app/src/test/kotlin/dev/mdwriter/ui/preview/ImagePathTest.kt`, `PreviewLinkPolicyTest.kt`, `PreviewThemeTest.kt`, `PreviewSyncTest.kt` — JVM
- `app/src/androidTest/kotlin/dev/mdwriter/ui/preview/PreviewSmokeTest.kt` — device

## Steps
1. Check the prerequisites. `grep -n webkit gradle/libs.versions.toml app/build.gradle.kts` (add `implementation(libs.androidx.webkit)` if missing). Read the current `MarkdownHtml.renderPage` and confirm three things:
   - The CSP `font-src`, `img-src` and `style-src` are the bare origin `https://appassets.androidplatform.net`. A host-source without a path matches `/res/`, `/assets/` and `/doc/`. If a path was added, remove it.
   - `script-src 'none'` is present.
   - `<meta name="viewport" content="width=device-width, initial-scale=1">` is present. Add it if missing, so that 1 CSS px = 1 dp.
2. Add `MarkdownHtml.headingCount(markdown)` (Reference §E) and `MarkdownHtmlPreviewTest`: `"# A\n\ntext\n\n## B"` → 2; setext `"A\n==="` → 1; `"```\n# no\n```"` → 0; front matter `---\ntitle: x\n---` → 0; the rendered page contains `id="a"`, the viewport meta and `script-src 'none'`. Run `make test`.
3. Write the pure helpers and their JVM tests: `ImagePath` (§C), `PreviewLinkPolicy` (§D), `PreviewSync` (§E), `PreviewThemes` (§F).
4. Write `preview.css` (§G). The heading sizes use the **same multipliers** as the editor (02 §3 heading scale table).
5. Write `mdwriter_keep.xml`: `<resources xmlns:tools="http://schemas.android.com/tools" tools:keep="@font/*" />`.
6. Write `DocumentImagePathHandler` (§C) and `PreviewWebView` + `PreviewWebViewHolder` (§B).
7. Write `PreviewRenderer` (§E) and the ViewModel API:
   - `openPreview(text: String, caret: Int, theme: PreviewTheme, title: String)` sets `previewOpen = true`, cancels any previous render job, and renders on `container.dispatchers.default` into `preview`.
   - `closePreview()` sets `previewOpen = false`. Keep the last page so the exit animation still shows content.
8. Write `PreviewOverlay` (§A) and compose it at T13's `PreviewSlot()` position (**before** `RootBackHandlers`).
9. Wire the entry points in `MdWriterRoot`:
   - Replace T13's no-op with `onOpenPreview = ::openPreview` and set `previewAvailable = true`. `openPreview()` collapses the selection, calls `controller.hideIme()`, closes the drawer and find, then calls `editorVm.openPreview(controller.snapshot(), controller.caret(), previewTheme, ui.title)`.
   - `OverflowActions(onPreview = ::openPreview)`.
   - Add `AppCommand.Preview` with `KEYCODE_R → Preview`. It toggles: close if open, else open.
   - Add the a11y action "Show preview" next to "Open library" in `EditorHost`.
   - `previewTheme` = `remember(colors, font, sizeStep, lineLength, widthClass, fontScale) { PreviewThemes.build(...) }`. While the preview is open, a theme change re-renders it via `LaunchedEffect(previewTheme)`.
10. Write `PreviewSmokeTest` (Acceptance 5). Run `make check`, `make test-device`, then the release font check (Verification). Write STATUS and commit.

## Reference code
§A `PreviewOverlay` — **sketch**
```kotlin
@Composable fun PreviewOverlay(
    open: Boolean, page: PreviewPage?, holder: PreviewWebViewHolder, currentDoc: DocRef?,
    onClose: () -> Unit, onShare: (() -> Unit)?, modifier: Modifier = Modifier,
) {
    val colors = LocalWriterColors.current
    var back by remember { mutableFloatStateOf(0f) }; var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    LaunchedEffect(open) { if (open) back = 0f }
    PredictiveBackHandler(enabled = open) { events ->
        try { events.collect { back = it.progress; edge = it.swipeEdge }; onClose() }
        catch (e: CancellationException) { back = 0f; throw e }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spec = tween<IntOffset>(WriterMotion.PREVIEW_TRANSITION_MS, easing = WriterMotion.emphasizedDecelerate)
    AnimatedVisibility(open, modifier,
        enter = slideInHorizontally(spec) { w -> if (rtl) -w / 4 else w / 4 } + fadeIn(tween(WriterMotion.PREVIEW_TRANSITION_MS)),
        exit = slideOutHorizontally(tween(WriterMotion.PREVIEW_TRANSITION_MS, easing = WriterMotion.emphasizedAccelerate))
            { w -> if (rtl) -w / 4 else w / 4 } + fadeOut(tween(WriterMotion.PREVIEW_TRANSITION_MS))) {
        Box(Modifier.fillMaxSize().background(colors.bg)
            .graphicsLayer { val s = 1f - 0.1f * back; scaleX = s; scaleY = s
                translationX = (if (edge == BackEventCompat.EDGE_LEFT) 1 else -1) * back * 24.dp.toPx() }
            .editorSwipeNav(enabled = { true }, accepts = { it == SwipeDir.TowardEnd }, onArmedDown = {}, onSwipe = { onClose() })) {
            AndroidView(factory = { holder.get().also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(), onRelease = { /* keep alive; holder destroys */ },
                update = { wv -> wv.setBackgroundColor(colors.bg.toArgb()); wv.onEscape = onClose
                    holder.currentDoc = currentDoc; page?.let(wv::show) })
            // glyph row: arrow_back (top-start, onClose) · share (top-end, only if onShare != null); safeDrawing insets
        }
    }
}
```
- `PreviewWebView.show(page)` is idempotent: it keeps `lastShown` and reloads only when `page !== lastShown`.
- In `MdWriterRoot`, create `holder = remember { PreviewWebViewHolder(context, libraryRoot) }` and `DisposableEffect(Unit) { onDispose { holder.destroy() } }`. No ViewModel ever holds the WebView (01 §5).

§B `PreviewWebView` — **copy the settings, sketch the rest** (markdown §3.2 + fact-check corrections)
```kotlin
class PreviewWebView(context: Context, imageHandler: DocumentImagePathHandler) : WebView(context) {
    var onEscape: (() -> Unit)? = null
    @VisibleForTesting var requestLog: ((url: String, served: Boolean) -> Unit)? = null
    @VisibleForTesting var onPageFinishedForTest: (() -> Unit)? = null
    private val loader = WebViewAssetLoader.Builder()          // domain appassets.androidplatform.net, https only
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))     // preview/preview.css
        .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))    // font/duo_regular.ttf … (C13)
        .addPathHandler("/doc/", imageHandler)                                        // images next to the note
        .build()
    private var pending: PreviewPage? = null
    init {
        isSaveEnabled = false
        settings.apply {
            javaScriptEnabled = false; allowFileAccess = false; allowContentAccess = false  // content default TRUE
            blockNetworkLoads = true      // already true without INTERNET; setting false throws SecurityException
            domStorageEnabled = false; setGeolocationEnabled(false); setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false; mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true; displayZoomControls = false; textZoom = 100
        }
        webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url).also { requestLog?.invoke(request.url.toString(), it != null) }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                when (PreviewLinkPolicy.decide(request.url.toString())) {
                    LinkAction.InPage -> false
                    LinkAction.External -> { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)
                        .addCategory(Intent.CATEGORY_BROWSABLE)) }; true }
                    LinkAction.Block -> true
                }
            override fun onPageFinished(view: WebView, url: String?) {
                val p = pending ?: return
                view.postVisualStateCallback(0L, object : VisualStateCallback() { override fun onComplete(id: Long) { applyScroll(p) } })
                onPageFinishedForTest?.invoke()
            }
        }
    }
    fun show(page: PreviewPage) { pending = page
        loadDataWithBaseURL(BASE + (page.anchor?.let { "#$it" } ?: ""), page.html, "text/html", "utf-8", null) }
    private fun applyScroll(p: PreviewPage) {        // fallback when there is no heading anchor (or fragments don't scroll)
        if (p.anchor != null && scrollY > 0) return
        val max = (contentHeight * resources.displayMetrics.density - height).coerceAtLeast(0f)
        scrollTo(0, (p.fraction * max).toInt())
    }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_ESCAPE) { if (e.action == KeyEvent.ACTION_UP) onEscape?.invoke(); return true }
        return super.dispatchKeyEvent(e)
    }
    companion object { const val BASE = "https://appassets.androidplatform.net/doc/" }
}
```
Scroll sync decision rule:
- Try the fragment in the base URL first. This is **UNVERIFIED**; Acceptance 5d checks it.
- If the fragment does not scroll on the emulator, drop the fragment and always use the proportional `fraction`. Record that in STATUS "Deviations".
- `requestFocus()` the WebView after `show` so that Esc arrives.

§C `ImagePath` + `DocumentImagePathHandler` — **sketch**
```kotlin
object ImagePath {
    /** Decoded path after "/doc/" → tokens ("." and "" dropped, ".." kept); null if it contains '\\' or NUL or is absolute. */
    fun split(path: String): List<String>?
    /** Walks from base (root..dir, non-empty); ".." pops but never past base[0]; null on escape or missing child. */
    fun <T> walk(base: List<T>, tokens: List<String>, child: (parent: T, name: String) -> T?): T?
    fun imageMime(name: String): String?   // png jpg jpeg gif webp avif bmp svg(image/svg+xml); else null
}
class DocumentImagePathHandler(private val context: Context, private val libraryRoot: File,
                               private val currentDoc: () -> DocRef?) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {   // runs on a WebView background thread: blocking I/O OK
        val tokens = ImagePath.split(path) ?: return null
        val mime = ImagePath.imageMime(tokens.lastOrNull() ?: return null) ?: return null
        return when (val doc = currentDoc()) {
            is DocRef.InternalFile -> internal(doc, tokens)?.let { WebResourceResponse(mime, null, it.inputStream()) }
            is DocRef.TreeDoc -> tree(doc, tokens)?.let { uri -> context.contentResolver.openInputStream(uri)
                ?.let { WebResourceResponse(mime, null, it) } }
            else -> null                                              // External: no directory access
        }
    }
    // internal: base = [libraryRoot, …dirs of relPath]; child = File(p, n).takeIf(File::isFile or isDirectory);
    //   final check: file.canonicalPath.startsWith(libraryRoot.canonicalPath + File.separator)
    // tree: DocumentsContract.findDocumentPath(resolver, buildDocumentUriUsingTree(treeUri, docId))?.path
    //   → ids root..doc; base = ids.dropLast(1); child = query buildChildDocumentsUriUsingTree(treeUri, parentId)
    //   (projection DOCUMENT_ID, DISPLAY_NAME) for DISPLAY_NAME == name; any exception → null
}
```
`currentDoc` reads a `@Volatile var currentDoc` on the holder, which the overlay sets from the main thread.

§D `PreviewLinkPolicy` — **copy** (pure; `java.net.URI`, no `android.net.Uri`, so it is JVM-testable)
```kotlin
enum class LinkAction { InPage, External, Block }
object PreviewLinkPolicy {
    fun decide(url: String): LinkAction {
        val u = runCatching { URI(url) }.getOrNull() ?: return LinkAction.Block
        if (u.host == "appassets.androidplatform.net")      // sanitized javascript: → href="" → base URL: swallow
            return if (!u.rawFragment.isNullOrEmpty() && u.path == "/doc/") LinkAction.InPage else LinkAction.Block
        return when (u.scheme?.lowercase()) { "http", "https", "mailto" -> LinkAction.External; else -> LinkAction.Block }
    }
}
```
§E Sync + renderer — **sketch**
```kotlin
// MarkdownHtml (core): uses the SAME parser/extensions as renderPage
fun headingCount(markdown: String): Int { var n = 0
    parser.parse(markdown).accept(object : AbstractVisitor() { override fun visit(h: Heading) { n++; visitChildren(h) } }); return n }
object PreviewSync {
    private val ID = Regex("""<h[1-6]\b[^>]*\bid="([^"]+)"""")
    fun anchorIds(html: String): List<String> = ID.findAll(html).map { it.groupValues[1] }.toList()
    fun anchorFor(html: String, headingIndex: Int): String? = if (headingIndex < 0) null else anchorIds(html).getOrNull(headingIndex)
}
data class PreviewPage(val html: String, val anchor: String?, val fraction: Float)
class PreviewRenderer(private val default: CoroutineDispatcher, private val md: MarkdownHtml = MarkdownHtml(allowRawHtml = true)) {
    suspend fun render(text: String, caret: Int, theme: PreviewTheme, title: String): PreviewPage = withContext(default) {
        val c = caret.coerceIn(0, text.length)
        val lineEnd = text.indexOf('\n', c).let { if (it < 0) text.length else it }
        val html = md.renderPage(text, theme.themeClass, theme.cssVars, title)
        PreviewPage(html, PreviewSync.anchorFor(html, md.headingCount(text.substring(0, lineEnd)) - 1),
            if (text.isEmpty()) 0f else c.toFloat() / text.length)
    }
}
```
§F `PreviewThemes.build(colors, pureBlack, font, bodyCssPx, measureChars: Int?, sideDp, topDp, density)` — **contract**
- `themeClass` = `"light"`, `"dark"` or `"dark black"`. It is set on `<html class>`; never rely on `prefers-color-scheme`.
- `cssVars` keys: `--bg --text --text-secondary --markup --code-bg --divider --accent --highlight-bg` (as `rgba(r,g,b,a)`), `--font-body` (`'Duo'`, `'Quattro'` or `'Mono'`), `--font-mono` (`'Mono'`), `--size` (e.g. `17px`), `--measure` (`64ch`, or `none` below 600 dp), `--side` (24/32/48px), `--top` (56/64/72px), `--hairline` (`1/density` px).
- `bodyCssPx = TypedValue.applyDimension(COMPLEX_UNIT_SP, EditorMetrics.bodyTextSizeSp(step, widthClass).toFloat(), dm) / dm.density`. This honours non-linear font scale, and `textZoom` stays 100.
- `require` that every value matches `^[#(),.%\w\s'-]+$`. Values are joined into the page's style, so nothing like `;`, `<` or `}` may pass.

§G `preview.css` — **sketch** (fill in every construct of 02 §8)
```css
@font-face { font-family: "Duo"; src: url("/res/font/duo_regular.ttf"); font-weight: 400; font-style: normal; }
/* …11 more: duo_italic (400 italic), duo_bold (700), duo_bold_italic, quattro_*, mono_* */
html { background: var(--bg); color: var(--text); -webkit-text-size-adjust: 100%; }
body { margin: 0 auto; max-width: var(--measure); padding: var(--top) var(--side) 50vh;
       font: var(--size)/1.6 var(--font-body), sans-serif; overflow-wrap: break-word; }
p, ul, ol, blockquote, pre, table, .markdown-alert, section.footnotes { margin: 0 0 1.6em; }   /* one pitch */
ul { list-style: none; padding-left: 0; } ul > li { position: relative; }
ul > li::before { content: "\2013"; position: absolute; left: -1.1em; }            /* hanging en dash */
ul > li:has(> input[type=checkbox])::before { content: none; }
input[type=checkbox] { accent-color: var(--accent); pointer-events: none; }
blockquote { margin-left: 0; padding-left: 1em; border-left: 2px solid var(--markup); }
code { font-family: var(--font-mono); font-size: .9em; background: var(--code-bg); border-radius: 3px; padding: 0 .2em; }
pre { font-family: var(--font-mono); background: var(--code-bg); padding: 12px; border-radius: 6px; overflow-x: auto; }
pre code { background: none; padding: 0; font-size: .9em; }
table { border-collapse: collapse; display: block; overflow-x: auto; }
th, td { border-bottom: var(--hairline) solid var(--divider); padding: .3em .8em .3em 0; text-align: left; }
a { color: var(--text); text-decoration: underline 1px var(--markup); text-underline-offset: .15em; }
hr, section.footnotes { border: 0; border-top: var(--hairline) solid var(--divider); }
img { max-width: 100%; height: auto; } mark { background: var(--highlight-bg); color: inherit; }
/* h1–h6: bold, sizes = 02 §3 multipliers × var(--size), no extra padding beyond one pitch */
```

## Acceptance criteria
1. `MarkdownHtmlPreviewTest`: all cases of step 2 pass (`make test`).
2. `ImagePathTest`:
   - `split("img/a.png")` = [img, a.png]; `split("a//./b.png")` = [a, b.png]; `split("a\\b.png")` = null; `split("/abs.png")` = null.
   - `walk` with base [root, notes] and tokens [.., .., x.png] = null (escape); with [.., img, x.png] it resolves under root.
   - `imageMime("A.PNG")` = image/png; `imageMime("notes.md")` = null.
3. `PreviewLinkPolicyTest`:
   - `javascript:alert(1)` → Block; `https://appassets.androidplatform.net/doc/` → Block; `…/doc/#fn-1` → InPage.
   - `https://example.com` → External; `mailto:a@b.c` → External.
   - `intent://x#Intent;end` → Block; `file:///etc/hosts` → Block; `content://x` → Block; `other.md` (resolved as `…/doc/other.md`) → Block.
4. `PreviewThemeTest`: dark + pureBlack → class `dark black` and `--bg` is `rgba(0,0,0,1.0)` (format up to you, test it). Compact → `--measure` = `none`; Medium with lineLength 72 → `72ch`. A value containing `;` fails `require`. `PreviewSyncTest`: the 3rd heading id is returned for index 2; -1 → null; an out-of-range index → null.
5. `PreviewSmokeTest` (device, `PreviewWebView` added to `MainActivity` via `addContentView`):
   - a) `# Hello Preview` → `findAllAsync("Hello Preview")` ≥ 1 and `findAllAsync("# Hello")` = 0.
   - b) With `![x](https://example.com/x.png)` followed by `after image`, the page finishes, "after image" is found, and `requestLog` has no `example.com` entry served.
   - c) `requestLog` shows `/assets/preview/preview.css` and `/res/font/duo_regular.ttf` as `served = true`, on the **debug** app `dev.mdwriter.debug`.
   - d) A doc with 40 headings and the caret under heading 30 → `scrollY > 0` after `postVisualStateCallback`.
   - e) `library/PreviewTest/img/dot.png` (1×1 PNG written in `@Before`) referenced as `img/dot.png` → served true, and `../../../../etc/hosts.png` → served false.
   - f) `PreviewLinkPolicy` already covers `javascript:`. On device, clicking a sanitized link (`webView.loadUrl` is not needed; verify with a UiAutomator tap on the link text) starts no activity: `ActivityMonitor` hits = 0.
6. Emulator:
   - An end→start swipe in the editor slides in the preview in about 250 ms. H1 is ≥ 1.5× the body glyph height, `#` markers are hidden, and code is monospaced on `codeBg`.
   - Predictive Back (a slow edge drag) visibly scales the preview before it closes. A start→end swipe closes it, and so does Ctrl+R (`adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_R`).
   - Screenshots of the preview in light and dark.
7. Release (`KEYSTORE_DIR=/tmp/mdwriter-agent-key`): `aapt2 dump resources` lists `font/duo_regular`, `font/mono_regular` and `font/quattro_regular`. The release preview screenshot shows the same glyph shapes as debug (Mono code, Duo body, no Roboto fallback).
8. `make check` is green; no `INTERNET` permission: `grep -c INTERNET app/src/main/AndroidManifest.xml` → 0.

## Verification commands
```sh
make test && make check
make test-device DEVICE=emulator-5554
make install-debug DEVICE=emulator-5554
adb -s emulator-5554 exec-out screencap -p > /tmp/t16-preview-light.png
make apk KEYSTORE_DIR=/tmp/mdwriter-agent-key
~/Library/Android/sdk/build-tools/36.0.0/aapt2 dump resources app/build/outputs/apk/release/*.apk | grep -E 'font/(duo|quattro|mono)_regular'
make install DEVICE=emulator-5554 KEYSTORE_DIR=/tmp/mdwriter-agent-key   # INSTALL_FAILED_UPDATE_INCOMPATIBLE → STOP-AND-ASK, never uninstall
adb -s emulator-5554 exec-out screencap -p > /tmp/t16-preview-release.png
```

## Pitfalls
- **C13**: never copy the TTFs into `assets/`. Serve `res/font` via `ResourcesPathHandler`. If 5c or 7 fails (the debug `applicationIdSuffix` breaks `getIdentifier`, or release renames resources), replace `/res/` with a custom `FontPathHandler` that maps `font/<name>.ttf` → `R.font.<name>` through `WriterFont` and `resources.openRawResource(id)`. Record that as a deviation.
- `allowContentAccess` defaults to **true**, so turn it off. Never set `blockNetworkLoads = false` (SecurityException without INTERNET; HARD RULE 1).
- Sanitized `javascript:` links become `href=""`, which navigates to the base URL. That must be **Block**, never sent to a browser.
- JS is off: no `evaluateJavascript`, no `prefers-color-scheme` (WebView follows the Activity theme, not the in-app theme). Theme = class + vars.
- `shouldInterceptRequest` and `PathHandler.handle` run off the main thread. Never read Compose state there; use the `@Volatile` `currentDoc`.
- Base URL must be `https://appassets.androidplatform.net/...`, because the asset loader ignores other hosts and `http`.
- Create the WebView lazily with the Activity context, keep it in the holder (not in a ViewModel), and `destroy()` it in `onDispose`. Call `setBackgroundColor(bg)` before loading, or you get a white flash in dark mode.
- The preview swipe `accepts` only TowardEnd. A start→end swipe over a horizontally scrolled code block closes the preview (known trade-off; note it for T22 QA).
- Rethrow `CancellationException` in `PredictiveBackHandler` after resetting the progress.
- Rendering happens on `Default` (01 §7). `controller.snapshot()` happens on main, before the launch.

## Definition of done
- [ ] Every Acceptance criterion is met; `make check` and `make test-device DEVICE=emulator-5554` are green.
- [ ] The STATUS entry records the scroll-sync outcome (fragment works / proportional-only), the font-path outcome (ResourcesPathHandler / FontPathHandler), and the screenshot paths.
- [ ] 01 §6.1 mentions `MarkdownHtml.headingCount`.
- [ ] Commit `T16: WebView preview with swipe, predictive back, local images and scroll sync`.
