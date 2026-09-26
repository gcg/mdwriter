# Markdown track: parsing, live-highlight token model, incremental strategy, preview, smart editing, stats

Status: COMPLETE (2026-09-25). The reference code in `proto-final/` compiles and all checks pass (numbers below).
Date: 2026-09-25. Author: research agent (retry of interrupted track; salvaged proto/ and exp/ artifacts).

## 0. TL;DR decisions

| # | Topic | Decision | Confidence |
|---|---|---|---|
| 1 | Live-highlight parser | **Hand-written incremental per-line block-state scanner + per-paragraph CommonMark inline scanner**, pure Kotlin in `:core:markdown`. Reference implementation in `proto-final/` is verified: 641/652 spec examples agree with commonmark-java, fuzz shows incremental == full, `update` takes about 0.01 ms per keystroke. commonmark-java/JetBrains ASTs are rejected for live use: no incremental API, no marker spans, 6-10 ms full re-parse per keystroke at 100 KB desktop. | high |
| 2 | Preview renderer | **commonmark-java 0.30.0** `HtmlRenderer` + tables, strikethrough, task-list, autolink, footnotes, yaml-front-matter, gfm-alerts, heading-anchor extensions (all 0.30.0), `sanitizeUrls(true)`, raw HTML allowed. Displayed in an **Android WebView** via `loadDataWithBaseURL("https://appassets.androidplatform.net/doc/", …)` + `WebViewAssetLoader` (androidx.webkit 1.17.1). Not mikepenz 0.45.0 (JetBrains-based: no footnotes or front matter, a different dialect). | high |
| 3 | WebView security | JS off; `allowFileAccess=false`; `allowContentAccess=false` (default is true!); `blockNetworkLoads=true` (already forced by the missing INTERNET permission, and setting false throws); CSP meta `default-src 'none'`, assets-only images/fonts/styles; only `http`/`https`/`mailto`/`data` URLs survive the sanitizer; link taps → `ACTION_VIEW`; relative images through a custom `/doc/` PathHandler; theme via an injected class rather than `prefers-color-scheme`. | high |
| 4 | Token model | `MdSpan(kind, start, end, arg)` with 37 `MdKind`s: content kinds cover the whole construct and separate marker kinds cover `#`, `**`, `>`, `-`, `[ ]`, `](`, fences, pipes. Spans never cross `\n`. Also per-line `LineInfo(type: BlockType, contentStart, quoteDepth, listDepth)`. | high |
| 5 | Incremental algorithm | Per-line entry `LineState` (mode, containers, fence/html/table info). On an edit: splice lines, walk back to the start of the enclosing paragraph/table, rescan until the recomputed entry state equals the stored one, and return `HighlightDelta(firstLine, endLine, startOffset, endOffset, full)`. A change to the set of reference-definition labels triggers a full rescan. | high |
| 6 | API | `fullScan(text)`, `update(text, changeStart, removedLen, addedLen)` (from TextWatcher), `update(text)` (diff fallback), `spansForLines(delta)`, `lineInfo(line)`, `headings()`, `isFenceUnclosed(line)`. **Main-thread synchronous**; not thread-safe. | high |
| 7 | Budgets | `update` < 1 ms p95 at 100 KB on device (desktop: 0.012 ms p50 / 0.025 ms p95). `fullScan` 1.7 ms per 100 KB, 15 ms per 1 MB (desktop). Background `fullScan` for > 300 KB. | medium (device numbers unverified) |
| 8 | Tests | 50 static + 7 typing cases (section 7, exact expected strings, oracle-checked); spec differential with 11 pinned diffs; seeded fuzz; pathological timing. | high |
| 9 | Smart editing | `SmartEdit` pure functions → `TextEdit`. Covers list continuation (`- * +`, `1.`/`1)` auto-increment + renumber, tasks, quotes); Enter on an empty item ends/outdents; verbatim-aware Enter; fence auto-close; indent/outdent; wrap toggles with un-toggle (bold, italic, strike, highlight, inline code); link/image; heading cycle none→#→##→###→none and Ctrl+0..6; quote toggle; task toggle; smart Backspace. Apply via an InputConnection wrapper as one edit. | high (functions), medium (IME integration) |
| 10 | Stats | Words exclude markup, CJK counts per ideograph, chars = code points without line breaks (markup included), sentences, tasks. Reading time = words / **238 wpm** (Brysbaert 2019). Selection-aware. Debounced 400 ms background full pass (1.85 ms per 100 KB). | high |
| 11 | Reference code | Commit `proto-final/` to `plans/reference/markdown/`. It compiles under `explicitApi()` + JVM 17. It needs ktlint reformatting only. | high |

## 1. Evidence gathered (artifacts, harness numbers, benchmarks)

All numbers were measured on 2026-09-25 on the dev Mac (Apple Silicon, JBR 21.0.11, HotSpot JIT, warmed up). Phone numbers are **UNVERIFIED estimates**: assume 3-5x slower on a mid-range Android device (ART JIT/AOT). Kotlin sources were compiled with the kotlinc 2.3.10 that ships inside Android Studio (`/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc`, run with `JAVA_HOME=<JBR 21>`). There is no kotlinc on PATH.

### 1.1 Library versions (verified on Maven Central / Google Maven, 2026-09-25)
| Artifact | Latest stable | Notes |
|---|---|---|
| `org.commonmark:commonmark` | **0.30.0** (2026-08-06) | Java 11 bytecode (class major 55), has `module-info.class`. CommonMark spec 0.31.2. README: "works on Android too, but that is on a best-effort basis". |
| `org.commonmark:commonmark-ext-gfm-tables`, `-gfm-strikethrough`, `-task-list-items`, `-autolink`, `-footnotes`, `-yaml-front-matter`, `-gfm-alerts`, `-heading-anchor`, `-image-attributes`, `-ins` | **0.30.0** | `-autolink` depends on `org.nibor.autolink:autolink:0.12.0`. commonmark core + 6 extensions + autolink come to about 376 KB of jars before R8. |
| `org.jetbrains:markdown` (-jvm) | **0.7.14** (2026-09-16) | GFM flavour. No footnotes and no YAML front matter (verified in the AST dump: `[^1]` parses as `SHORT_REFERENCE_LINK`). |
| `com.mikepenz:multiplatform-markdown-renderer(-m3/-android)` | **0.45.0** (2026-08-28) | Compose renderer built on `org.jetbrains:markdown`. |
| `androidx.webkit:webkit` | **1.17.1** stable (1.18.0-alpha02 newest) | `WebViewAssetLoader` (`DEFAULT_DOMAIN = "appassets.androidplatform.net"`), `AssetsPathHandler`, `ResourcesPathHandler`, `InternalStoragePathHandler`, custom `PathHandler`. |

commonmark-java 0.30.0 changelog highlights that matter here: it fixes quadratic runtime on pathological inputs (`"x <!--".repeat(100_000)`, `"<".repeat(100_000)`, emphasis like `"a**b" + "c* ".repeat(100_000)`, backtick runs of different lengths), caps nesting (`maxInlineNesting` 100, `maxOpenBlockParsers` 100), and caps table cells (`maxCells` 1,000,000; it **throws** when exceeded). It also added raw-YAML extraction for front matter.

### 1.2 Parser benchmarks: full parse, steady state (exp/Bench.java; docs are generated realistic Markdown)
| Doc | commonmark (no spans) | commonmark `BLOCKS_AND_INLINES` spans | commonmark `HtmlRenderer.render` | JetBrains GFM `buildMarkdownTreeFromString` | **Our line-state highlighter, full scan** |
|---|---|---|---|---|---|
| 100 KB (2,047 lines) | 4.11 ms | 5.92 ms | 0.77 ms | 10.12 ms | **2.61 ms** (+0.65 ms flatten/sort) |
| 300 KB (6,307 lines) | 11.24 ms | 12.33 ms | 1.51 ms | 25.44 ms | **6.53 ms** |
| 1 MB (21,051 lines) | 35.93 ms | 41.73 ms | 5.55 ms | 84.28 ms | **20.32 ms** |

The first (cold, pre-JIT) commonmark parse of 100 KB took 26-46 ms. JetBrains took 126 ms.

### 1.3 Incremental re-highlight (proto `MarkdownHighlighter.update`, one char typed per call, 400 edits mid-document)
| Doc | p50 | p95 | max | max lines re-scanned | Open an unclosed fence at the top | incremental == full? |
|---|---|---|---|---|---|---|
| 100 KB | 0.139 ms | 0.188 ms | 6.2 ms (GC/JIT outlier) | 11 | 0.44 ms | **true** |
| 300 KB | 0.384 ms | 0.453 ms | 8.4 ms | 7 | 0.49 ms | **true** |
| 1 MB | 1.260 ms | 1.565 ms | 10.6 ms | 10 | 11.0 ms | **true** |

The p50 grows with document size because this salvaged `update(newText)` diffs the whole old and new text (common prefix and suffix, O(n)). The final API takes the change position from the TextWatcher instead, which removes the O(n) diff (see section 6 for the re-measured numbers). For comparison, commonmark-java cannot parse incrementally: every keystroke would cost a full 6 ms parse at 100 KB (desktop), roughly 20-30 ms on a phone, plus converting the AST to spans.

### 1.4 Correctness of the hand-written highlighter against the commonmark-java 0.30.0 oracle
The differential harness (`proto/Harness.kt diff`) runs all **652 CommonMark 0.31.2 spec examples** through both parsers (commonmark-java with the GFM extensions and `IncludeSourceSpans.BLOCKS_AND_INLINES`) and compares three things:
- (a) the exact ranges of emphasis, strong, strikethrough, code span, link, image and autolink nodes;
- (b) which lines are headings, and at what level;
- (c) which lines are code (fenced or indented).

Result for the salvaged proto: **640/652 agree on everything** (98.2%). For **proto-final: 641/652** (email autolinks added). Inline ranges agree 642/652, headings 652/652, and code lines 650/652. All 132 emphasis examples agree, as do all 44 HTML-block, 29 fenced-code, 27 setext and 18 ATX examples. The 12 disagreements:
- 5 × **multi-line link reference definitions** (ex 193, 195, 196, 198, 217) and 1 × a multi-line label (ex 541). The proto only recognizes single-line definitions, so references to them stay unstyled. This is acceptable for an editor.
- 3 × GFM email and bare-URL autolinks (ex 602, 606, 612). These come from the autolink extension in the oracle, not from CommonMark. Email bare autolinks were missing and are **fixed in proto-final**.
- 1 × `[foo][bar][baz]` chained reference (ex 571).
- 2 × tab and indentation corner cases in nested containers (ex 6 `>\t\tfoo`, ex 259 `> > 1.  one` lazy).

**Random-edit fuzz** (`fuzz`, 3,000 random inserts and deletes of Markdown syntax fragments into a 6 KB seed): incremental result == fresh full scan in **3000/3000** rounds (0 mismatches).

### 1.5 Marker-offset precision of the library parsers (exp/CmDump.java, exp/JbDump.java on exp/cases.txt)
- **commonmark-java**: gives source spans for **nodes only**. Emphasis/strong spans include the delimiters and the node exposes `getOpeningDelimiter()`, so marker ranges can be derived. Headings, list items, block quotes and code fences have **no marker spans**: you must re-scan the line to find `#`, `>`, `-`, the fence and the info string. Links have no spans for the destination or title. Half-typed input degrades cleanly (`Typing **unclosed bold` gives a plain Text node).
- **JetBrains markdown**: gives token-level leaves (`EMPH` per `*` char, `BACKTICK`, `ATX_HEADER`, `LINK_DESTINATION`, `GFM_AUTOLINK`), which is precise for markers. But there are no footnotes and no front matter. It is 2-4x slower than commonmark-java and builds 2.4x more nodes. It also has no incremental API.
- Neither library offers incremental re-parse, per-line block state or a "restart from line N" entry point.

### 1.6 HTML rendering check (exp/HtmlTest.java, commonmark 0.30.0 + tables, strikethrough, task list, autolink, footnotes, front matter, alerts)
- The default `HtmlRenderer` passes raw HTML through unchanged (`<script>alert(1)</script>`, `<b onclick=x>`) and keeps `javascript:` hrefs.
- `escapeHtml(true)` turns raw HTML into text. `sanitizeUrls(true)` blanks `javascript:` URLs (`href=""`) and adds `rel="nofollow"`.
- Task items render as `<input type="checkbox" disabled="">`. Footnotes render as `<sup class="footnote-ref"><a href="#fn-1" …>` plus `<section class="footnotes">`. Alerts render as `<div class="markdown-alert markdown-alert-note">`. Front matter is removed from the output and available through `YamlFrontMatterVisitor().data`.

## 2. Live-highlight parser strategy decision

**Decision: use a hand-written, per-line block-state scanner plus a per-paragraph CommonMark inline scanner, in the pure-Kotlin `:core:markdown` module with zero dependencies.** Start from the reference implementation in `plans/reference/markdown/` (cleaned copy of `proto-final/`). Do not use a library AST for live highlighting. Confidence: high.

| Criterion | Hand-written line-state scanner | commonmark-java source spans | JetBrains markdown |
|---|---|---|---|
| Marker precision (the dimmed `#`, `**`, `>`, `-`, `[`, `](`, fence, info string, table pipes) | Exact, emitted directly as separate marker spans | Node ranges only. Block markers, link destinations and titles must be re-scanned by hand | Exact token leaves |
| Half-typed Markdown (`**unclosed`, `# `, lone ```` ``` ````) | Follows CommonMark: unclosed stays literal. An unclosed fence styles to end of document, exactly as the preview will | Same (spec-compliant) | Same, but with deviations |
| Spec agreement | 640/652 vs commonmark-java oracle (section 1.4) | Reference (0.31.2) | Not measured. Known gaps: footnotes, front matter |
| Speed, full scan 100 KB | **2.6 ms** | 5.9 ms (+ span conversion) | 10.1 ms |
| Per keystroke | **~0.15 ms at 100 KB**. Only changed lines plus the paragraph are re-scanned, and it stops when the line state converges | Full re-parse every time (≥ 6 ms at 100 KB desktop) | Full re-parse (≥ 10 ms) |
| Incremental feasibility | Built in: per-line entry state, convergence check | None | None |
| Dependencies in the live path | None (pure Kotlin, JVM-testable) | commonmark jar (fine) | 600 KB jar |
| Risk | We own the code: bugs are ours. Mitigated by the differential test vs commonmark-java in JVM unit tests, plus fuzz | Low | Medium |

Why not "commonmark-java for everything": the editor must restyle within one frame (16.6 ms at 60 Hz, 8.3 ms at 120 Hz). A full parse per keystroke is 20-30 ms on a phone at 100 KB (**UNVERIFIED** phone estimate from 5.9 ms desktop), and even more once converted to spans and diffed. A debounced background parse causes visible lag, where `# ` would only get big 100-300 ms later. That misses the core requirement.

**commonmark-java stays in the project** for three jobs:
1. The Preview/Export HTML renderer (section 3).
2. A **test-only oracle**: a JUnit 6 `@TestFactory` in `:core:markdown` that runs the 652 spec examples (`spec-0.31.2.json` copied to `core/markdown/src/test/resources/`) through both parsers and asserts agreement. Keep an explicit allow-list of the known 12 diffs (section 1.4) so any regression fails the build.
3. Optionally, as a periodic consistency check in debug builds. Not needed.

GFM/extension scope of the live highlighter:
- CommonMark 0.31.2 blocks and inlines;
- GFM tables, task list items, strikethrough (`~x~`, `~~x~~`), and extended autolinks (`www.`, `http(s)://`, bare email);
- footnote references and definitions;
- YAML front matter (`---` … `---`/`...` at document start, only when closed within the first 256 lines);
- optional `==highlight==` (iA Writer supports it; default **off** in the parser; expose as a setting);
- GFM alerts need no special highlighting (they are block quotes).

## 3. Preview renderer decision + WebView security

**Decision: render the preview with commonmark-java 0.30.0 `HtmlRenderer` and GFM extensions into an `android.webkit.WebView`. Load it with `loadDataWithBaseURL` using the virtual origin `https://appassets.androidplatform.net/`, served by `androidx.webkit.WebViewAssetLoader` 1.17.1. Use local CSS and the bundled fonts, JavaScript OFF, and all network blocked.** Do not use mikepenz multiplatform-markdown-renderer. Confidence: high.

Why WebView + commonmark-java over mikepenz 0.45.0 (Compose):
- **Same dialect as the editor.** Our highlighter is validated against commonmark-java, so what is styled in the editor is what the preview shows. mikepenz uses JetBrains markdown, which has no footnotes and no front matter, and its deviations would make "editor says bold, preview says not" bugs likely.
- **iA look = CSS.** Typography (hanging punctuation, `hyphens: auto`, `text-wrap: pretty`, the same bundled iA-like fonts via `@font-face` served from assets, tables, footnotes) is easy in CSS and hard in Compose.
- **Reuse for Export.** The same HTML string becomes "Export HTML" and "Print/Save as PDF" (`WebView.createPrintDocumentAdapter`), for free.
- **Reader behaviors come free.** Text selection and copy, find-in-page (`WebView.findAllAsync`), and large-document scrolling are all built in.
- **Cost:** WebView has a first-instantiation cost (often 100-300 ms, **UNVERIFIED** on target device). Mitigation: create the preview WebView lazily on first open, and keep it alive while the editor lives.

### 3.1 Renderer setup (in `:core:markdown`, pure JVM, unit-testable)
```kotlin
// build.gradle.kts of :core:markdown
dependencies {
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.0")      // pulls org.nibor.autolink:autolink:0.12.0
    implementation("org.commonmark:commonmark-ext-footnotes:0.30.0")
    implementation("org.commonmark:commonmark-ext-yaml-front-matter:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-alerts:0.30.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.30.0") // ids on headings for in-page anchors/TOC
}

class MarkdownHtml(private val allowRawHtml: Boolean = true) {
    private val exts = listOf(
        TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
        AutolinkExtension.create(), FootnotesExtension.create(), YamlFrontMatterExtension.create(),
        AlertsExtension.create(), HeadingAnchorExtension.create(),
    )
    private val parser = Parser.builder().extensions(exts).build()           // no source spans needed for preview
    private val renderer = HtmlRenderer.builder().extensions(exts)
        .escapeHtml(!allowRawHtml)   // raw HTML shown as text when false
        .sanitizeUrls(true)          // DefaultUrlSanitizer: only http, https, mailto, data (+ relative URLs) survive; others -> ""
        .softbreak("\n")
        .build()
    /** Body HTML only; the app wraps it in the page template (CSS, theme class, CSP). */
    fun renderBody(markdown: String): String = renderer.render(parser.parse(markdown))
}
```
- Default: `allowRawHtml = true`. iA Writer renders inline HTML in its preview, and it is safe because JS is off (see below). Expose it as a hidden setting only if needed.
- `sanitizeUrls(true)` adds `rel="nofollow"` and neutralizes `javascript:` hrefs (verified in HtmlTest). In-page `#fn-1` footnote anchors keep working.
- The `==highlight==` preview (if the setting is on) needs a tiny custom `DelimiterProcessor` for `=` → `<mark>`. Optional phase-2 item. Note that commonmark-java 0.30.0 has an `ins` extension for `++`, not `==`.
- Render off the main thread (`Dispatchers.Default`): 0.8 ms render + 4 ms parse per 100 KB on desktop (steady state). The reference `MarkdownHtml.renderBody` measured 7.7 ms per 100 KB in a short run.
- **Reference code:** `plans/reference/markdown/MarkdownHtml.kt` (from `proto-final/MarkdownHtml.kt`). It compiles under explicit API and implements `renderBody()` and `renderPage(markdown, themeClass, cssVars, title)` with the CSP meta tag and the `https://appassets.androidplatform.net/assets/preview/preview.css` link.
  - Verified output: task list checkboxes, footnotes, `<del>`, `www.` autolinks with `rel="nofollow"`, alerts, heading ids, and `javascript:` → `href=""`.
  - Raw `<script>` is passed through **on purpose**. It is inert because JS is disabled and CSP has `script-src 'none'`.
  - Relative `img/a.png` resolves against the base URL to `/doc/img/a.png`, which goes to the custom path handler.

### 3.2 WebView configuration (in `:app`)
```kotlin
val assetLoader = WebViewAssetLoader.Builder()        // domain defaults to appassets.androidplatform.net, https only
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))   // preview.css, fonts/*.woff2|ttf
    .addPathHandler("/doc/", DocumentImagePathHandler(currentDocLocation))      // custom: resolves relative image paths
    .build()
webView.settings.apply {
    javaScriptEnabled = false            // default false; set explicitly
    allowFileAccess = false              // default false when targeting R+; set explicitly
    allowContentAccess = false           // default TRUE -> turn off; images go through /doc/ handler instead
    blockNetworkLoads = true             // default is already true without INTERNET permission; setting false would throw SecurityException
    domStorageEnabled = false
    setGeolocationEnabled(false)
    setSupportMultipleWindows(false)
    javaScriptCanOpenWindowsAutomatically = false
    mediaPlaybackRequiresUserGesture = true
    builtInZoomControls = true; displayZoomControls = false   // pinch zoom for tables
    textZoom = 100                        // font size controlled via CSS variables from app settings
}
webView.webViewClient = object : WebViewClientCompat() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
        assetLoader.shouldInterceptRequest(request.url)          // null for everything else -> blocked anyway
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val u = request.url
        if (u.host == "appassets.androidplatform.net" && u.fragment != null) return false // in-page anchors (#fn-1)
        // external link: hand to the system browser (the app itself has no network)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)) }
        return true
    }
}
webView.loadDataWithBaseURL("https://appassets.androidplatform.net/doc/", pageHtml, "text/html", "utf-8", null)
```
- The page template contains a CSP meta tag as defense-in-depth:
  `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https://appassets.androidplatform.net data:; style-src https://appassets.androidplatform.net 'unsafe-inline'; font-src https://appassets.androidplatform.net; media-src 'none'; script-src 'none'; frame-src 'none'; form-action 'none'">`.
  CSP in Android WebView (Chromium) is standard behavior, but this exact policy is **UNVERIFIED** on device. The plan needs a manual QA step: an `<img src="https://example.com/x.png">` must not load, and the page must still render. `base-uri` is intentionally left open for `loadDataWithBaseURL`.
- `blockNetworkLoads` doc (verified in the WebSettings reference): "If the application does not have the INTERNET permission, attempts to set a value of false will cause a SecurityException … The default value is false if the application has the INTERNET permission, otherwise it is true." So in this app, remote content can never load.
- `setAllowFileAccess` doc: default false when targeting R and above, and "It's recommended to always use androidx.webkit.WebViewAssetLoader". `setAllowContentAccess` defaults to **enabled**, so the plan must disable it explicitly.
- **Images:** the custom `DocumentImagePathHandler` maps `/doc/<relative path>` to a file next to the current document. For the internal library, resolve the path under `filesDir/library/` and reject `..` escapes via canonical-path prefix check. For SAF, resolve by walking the tree children (DocumentsContract query), then open with `ContentResolver.openInputStream`. Return `WebResourceResponse(mime, null, stream)`. Absolute `http(s)` images show the broken-image alt text (no network). `data:` URIs are allowed by the CSP.
- **Dark mode:** do not rely on `prefers-color-scheme`. Per the WebView dark-theme guide, "WebView always sets prefers-color-scheme according to isLightTheme" of the Activity theme, which will not follow an in-app theme override. Instead, inject the resolved theme into the template (`<html class="dark">` plus CSS custom properties for colors, font family, font size and line width taken from the editor settings). Leave `WebSettingsCompat.setAlgorithmicDarkeningAllowed` at its default (false).
- Preview CSS should mirror the editor: the same font files (from `assets/fonts/`), the same max line length (`max-width: 64ch/72ch/80ch`), and headings in the same font but bold/bigger. That typography comes from the design/typography track.

## 4. Token model (Kotlin, `:core:markdown`, pure JVM)

Package `mdwriter.markdown` (the plan may rename it to the app's package, e.g. `app.mdwriter.core.markdown`). The exact source is in `plans/reference/markdown/MdModel.kt` (from `proto-final/MdModel.kt`). Summary:

```kotlin
enum class MdKind(val isMarker: Boolean) { /* 37 kinds, see table */ }
data class MdSpan(val kind: MdKind, val start: Int, val end: Int, val arg: Int = 0)   // [start,end) UTF-16 offsets
enum class BlockType { BLANK, PARAGRAPH, ATX_HEADING, SETEXT_HEADING, SETEXT_UNDERLINE, THEMATIC_BREAK,
    FENCE_OPEN, FENCE_CLOSE, FENCED_CODE, INDENTED_CODE, HTML, TABLE_HEADER, TABLE_DELIMITER, TABLE_ROW,
    FRONT_MATTER_FENCE, FRONT_MATTER, LINK_DEF, FOOTNOTE_DEF; val isVerbatim: Boolean }
data class LineInfo(val line: Int, val start: Int, val end: Int, val contentStart: Int,
                    val type: BlockType, val quoteDepth: Int, val listDepth: Int)
data class HighlightDelta(val firstLine: Int, val endLine: Int, val startOffset: Int, val endOffset: Int, val full: Boolean)
data class HeadingItem(val level: Int, val line: Int, val start: Int, val text: String)
```

**Conventions (all tested):**
1. Offsets are UTF-16 `Char` indices, matching Android `Editable`. `\n` is the only line break: the storage track normalizes CRLF to LF on load.
2. **No span crosses a `\n`.** Multi-line constructs are split per line: emphasis over a soft break, code lines, block-quote lines. This lets the editor re-apply spans line by line.
3. Content spans of inline constructs cover the whole construct **including markers**, the same convention as commonmark-java source spans. Marker spans are emitted separately and layered on top. For example, `**b**` produces `STRONG[0,5)`, `EMPHASIS_MARKER[0,2)` and `EMPHASIS_MARKER[3,5)`.
4. Ordering: `SPAN_ORDER = compareBy({ it.kind.isMarker }, { it.start }, { -it.end }, { it.kind.ordinal })` gives content before markers and outer before inner. Apply in this order. For MetricAffectingSpans the editor must use explicit `SPAN_PRIORITY` if order matters.
5. A heading span starts at the first `#`, not at the line start (`  ### x` gives `HEADING(3)[2,…)`). This keeps leading indentation at normal size.

| Kind | marker? | Range & `arg` | Suggested editor styling (final call: editor/design track) |
|---|---|---|---|
| HEADING | no | ATX: first `#` → line end; setext: each content line. `arg` = level 1-6 | Size scale (e.g. H1 1.60, H2 1.35, H3 1.17, H4-6 1.0) + bold. **Required: `# Title` gets visibly bigger.** |
| HEADING_MARKER | yes | `#…` run **incl. the following blanks**; closing `#` run; setext underline | Dimmed (≈40% alpha). Optionally hung into the left margin (iA style) |
| BLOCKQUOTE | no | `arg` = depth; first `>` → line end (also lazy lines) | Text color/italic/indent per design |
| QUOTE_MARKER | yes | `>` + at most one following space | Dimmed, hung |
| LIST_MARKER | yes | `-` `*` `+` `1.` `1)` without trailing space; `arg` = depth (0 = top) | Dimmed, hung; wrap indent via `LineInfo.contentStart` |
| TASK_MARKER | yes | `[ ]`/`[x]`/`[X]`; `arg`=1 checked | Dimmed; checked item text optionally struck/dimmed (use `LineInfo` range) |
| CODE_BLOCK | no | per code line, content after fence indentation / 4-col indent | Monospace + subtle background |
| CODE_FENCE | yes | the backtick/tilde run of an opening/closing fence | Dimmed monospace |
| CODE_INFO | no | info string (`kotlin`) | Dimmed monospace |
| HTML_BLOCK | no | per line; `arg` = HTML block type 1-7 (2 = comment) | Dimmed monospace (comments more dimmed) |
| TABLE_ROW | no | `arg` 0 header, 1 delimiter, 2 body; content range | Monospace optional; header bold |
| TABLE_PIPE | yes | each unescaped `\|` (GFM: also inside backticks) | Dimmed |
| THEMATIC_BREAK | yes | whole `***`/`---`/`___` content | Dimmed |
| FRONT_MATTER / FRONT_MATTER_FENCE | no / yes | body lines / the `---` or `...` lines | Dimmed monospace |
| LINK_DEF | no | whole `[x]: url "t"` line | Dimmed |
| EMPHASIS / STRONG / STRIKETHROUGH / HIGHLIGHT | no | incl. delimiters | Italic / bold / strike / background |
| EMPHASIS_MARKER | yes | the delimiter chars actually used (`*`,`_`,`**`,`__`,`~`,`~~`,`==`) | Dimmed |
| CODE_SPAN / CODE_SPAN_MARKER | no / yes | whole span / each backtick run | Monospace (+bg) / dimmed |
| LINK | no | whole `[t](u "x")`, `![a](u)`, `[t][r]`, `[r]` (refs only when the label is defined); `arg`=1 image | No style itself; container |
| LINK_TEXT | no | text inside brackets | Link color / underline |
| LINK_URL / LINK_TITLE / LINK_LABEL | no | destination / title incl. quotes / reference label | Dimmed |
| LINK_MARKER | yes | `!` `[` `]` `(` `)`, `<` `>` around destinations/autolinks, `]:` of definitions | Dimmed |
| AUTOLINK | no | `<scheme:…>` incl. `<>`, bare `www.`/`http(s)://`, bare e-mail | Link color |
| FOOTNOTE_REF | no | `[^id]`; `arg`=1 when it is a definition label | Superscript-ish / dimmed |
| HTML_INLINE | no | tag / comment / PI / declaration / CDATA | Dimmed |
| ENTITY | no | `&amp;` `&#123;` `&#x1F;` (names not validated against the HTML5 list) | Dimmed |
| ESCAPE_MARKER | yes | the backslash of `\*` | Dimmed |
| HARD_BREAK | yes | ≥2 trailing spaces, or the `\` before a line break | Optional `↵` hint |

`BlockType.isVerbatim` is true for FENCE_OPEN, FENCE_CLOSE, FENCED_CODE, INDENTED_CODE, HTML, FRONT_MATTER and FRONT_MATTER_FENCE. On these lines smart Enter keeps only the indentation, and the selection toolbar disables inline formatting.

## 5. Incremental per-line block-state algorithm

Implemented in `MarkdownHighlighter` (reference code). It follows CommonMark's "Appendix A: parsing strategy" (container matching, then leaf blocks), done one line at a time with a comparable **entry state** per line:

```kotlin
enum class Mode { NONE, PARAGRAPH, FENCE, HTML, TABLE, FRONT_MATTER }
data class LineState(                  // entry state of a line = everything needed from previous lines
    val mode: Mode = Mode.NONE,
    val containers: List<Int> = emptyList(),   // open containers outermost-first: QUOTE(-1) or list-item content column
    val fenceChar: Char = ' ', val fenceLen: Int = 0, val fenceIndent: Int = 0,
    val htmlType: Int = 0,                     // CommonMark HTML block type 1..7 while in Mode.HTML
    val tableCols: Int = 0,
)
```
Per-line storage (parallel lists, spliced on edit): `lineStarts: IntList` (absolute), `entry: LineState?`, `lineSpans: ArrayList<MdSpan>` with offsets **relative to the line start** (so an edit only shifts `lineStarts`, never the spans of other lines), `defLabel: String?` (link-ref-def label defined on the line), and `meta: Long` (packed `LineInfo`). There is also a global `labelCounts: HashMap<String, Int>` of defined reference labels.

**scanLine(line, entryState) → exitState**:
1. **Front matter**: only at line 0, only when it is exactly `---` and a closing `---`/`...` exists within the first 256 lines.
2. **Match open containers**, outermost first. A quote needs `>` after 0-3 spaces. A list item needs indentation ≥ its content column, or a blank line. Tabs expand to 4-column stops.
3. If all containers matched and the mode is **FENCE**, check for a closing fence (same char, run ≥ opening length, ≤3 spaces indent, nothing else); otherwise the line is a code line. If the mode is **HTML**, check the end condition for types 1-5 (`</pre>`/`</script>`/`</style>`/`</textarea>`, `-->`, `?>`, `>`, `]]>`). Types 6-7 end at a blank line.
4. **Open new containers**: `>`, then list markers (`-*+`, or 1-9 digits + `.`/`)`). Apply the rules: an empty item or an ordered item not starting at 1 cannot interrupt a paragraph; a `-`/`*` line that is a thematic break is not a list; 1-4 spaces after the marker (5+ = 1 space + indented code); a GFM task box `[ ]`/`[x]`.
5. **Lazy continuation**: when containers did not all match, nothing new was opened, the paragraph is open, and the line does not start a block, the line continues the paragraph with the old containers.
6. **Leaf blocks** in precedence order:
   - blank line;
   - indented code (≥4 cols, not in a paragraph);
   - ATX heading (1-6 `#` then space/tab/EOL: `#hashtag` and `#######` stay paragraphs);
   - fence open (backtick fences cannot have backticks in the info string);
   - HTML block start (types 1-7; type 7 cannot interrupt a paragraph);
   - setext underline (only directly under paragraph lines; turns **all** pending paragraph lines into HEADING);
   - thematic break;
   - GFM table (a delimiter row whose cell count equals the header's, directly under a one-line paragraph);
   - table body row;
   - link reference definition (single line, cannot interrupt a paragraph);
   - footnote definition `[^x]:`;
   - otherwise paragraph text (pending until the paragraph closes).
7. When a paragraph closes (blank line, block start, container change, end of document), its lines are joined with `\n` and the **inline scanner** runs once over the whole paragraph. This matters because emphasis and links span soft line breaks. The resulting spans are split back to the lines through an offset map. The distribution uses a binary search: the salvaged proto did this in O(spans × lines), which **was quadratic** and took 44 ms for a 2000-line paragraph. It is fixed in proto-final (1.6 ms).

**Inline scanner** (`InlineScanner`, one inline container at a time: paragraph, heading content, table cell):
- the CommonMark delimiter-stack algorithm with left/right-flanking rules (Unicode whitespace and punctuation classes), the `_` intraword rules, the rule of 3, and `openers_bottom`;
- code spans by backtick-run matching;
- links and images: inline dest/title, and full/collapsed/shortcut references **only if the label is defined** (normalized: whitespace collapsed, case-folded);
- `<autolinks>`, inline HTML (tags, comments, PIs, declarations, CDATA), entities, backslash escapes and hard breaks;
- GFM strikethrough (`~`/`~~`, equal lengths), extended www/http(s) autolinks (preceded by start/space/`*_~(`, trailing punctuation and `)` balancing, entity-like `&x;` trimming), and a post-pass for bare e-mails;
- linear-time guards: memoized failed searches, see the file header.

**update(text, changeStart, removedLen, addedLen)**:
1. Validate lengths; fall back to `fullScan` on mismatch.
2. `first` = line of `changeStart`; `lastOld` = old line containing `changeStart+removedLen`. Recompute the new line starts only from `lineStarts[first]` up to the line containing `changeStart+addedLen`. Shift all later `lineStarts` by `addedLen-removedLen` (O(lines), about 20 µs at 21k lines). Splice the per-line lists. `entry[first]` is kept because it depends only on earlier lines.
3. **Restart line** `r`: walk back from `first` while `entry[r].mode ∈ {PARAGRAPH, TABLE, FRONT_MATTER}` (the paragraph or table containing the edit must be re-inlined as a whole, and setext/table detection needs its first line). If front matter is enabled, line 0 is `---` and `first < 256`, restart at 0.
4. `scan(r, minEnd = last new line)`: rescan lines, storing each entry state. **Stop at the first line `i > minEnd` whose freshly computed entry state `== entry[i]` (stored) while no paragraph is pending.** Otherwise continue, possibly to EOF. Examples: an unclosed fence re-styles the rest of the document, and closing it converges again at the next line.
5. If the **set** of defined reference labels changed (a definition was added, removed or renamed), rescan everything and return `full = true`. This is rare, costs 1.7 ms at 100 KB, and makes `[foo]` references anywhere style/unstyle correctly.
6. Return `HighlightDelta(firstLine = r, endLine = i, startOffset = lineStart(r), endOffset = lineEnd(i-1), full = false)`.

Multi-line constructs and how they converge:
- **fenced code**: state carries fence char/len/indent, so everything until the closer flips, and convergence happens right after it;
- **front matter**: restart at 0 while editing the first 256 lines;
- **HTML blocks**: `htmlType` in state;
- **lazy continuation and setext**: handled by the paragraph walk-back;
- **list and quote nesting**: container columns in state;
- **tables**: `Mode.TABLE` + `tableCols`.

**Verification in proto-final** (fuzz, 3,000 random edits on a 6 KB seed, alternating both `update` overloads):
- span mismatches vs a fresh full scan: **0**;
- `LineInfo` mismatches: **0**;
- changed spans outside the reported `HighlightDelta`: **0**.

The plan must keep this fuzz test as a JUnit test (seeded `java.util.Random(7)`, 3,000 rounds, runs in < 2 s).

**Known limitations (documented, acceptable for v1):**
- multi-line link reference definitions are not recognized;
- `[a][b][c]` chains differ in one spec example;
- two tab/lazy corner cases in nested containers;
- entity names are not validated;
- bare `http://localhost` (no dot) is still linked (GFM requires a dot);
- incremental cost is O(size of the enclosing paragraph or table): 0.38 ms for a 500-line paragraph and 4.3 ms for a 5,000-line paragraph (desktop);
- typing an unclosed fence at the top of a document rescans to EOF (15 ms per 1 MB desktop, once).

## 6. Public API + budgets

```kotlin
class MarkdownHighlighter(
    enableHighlight: Boolean = true,       // ==mark== (setting; set false for strict CommonMark/GFM)
    enableFrontMatter: Boolean = true,
) {
    fun fullScan(text: CharSequence): HighlightDelta                                  // on open / reload
    fun update(text: CharSequence, changeStart: Int, removedLen: Int, addedLen: Int): HighlightDelta  // per edit
    fun update(text: CharSequence): HighlightDelta                                    // fallback: diffs old vs new
    fun spans(): List<MdSpan>                                     // all spans, sorted (tests/export only)
    fun spansForLines(fromLine: Int, endLine: Int): List<MdSpan>  // sorted; use with a delta
    fun lineInfo(line: Int): LineInfo;  fun lineInfoAt(offset: Int): LineInfo;  fun lineIndexOf(offset: Int): Int
    fun lineStart(line: Int): Int;  fun lineEnd(line: Int): Int;  val lineCount: Int
    fun headings(): List<HeadingItem>                            // outline (optional UI)
    fun isFenceUnclosed(line: Int): Boolean                       // smart Enter auto-close
    val lastRescannedLines: Int                                   // diagnostics/tests
}
```

**Threading contract:** the highlighter is **not thread-safe** and keeps a *reference* to the text (it does not copy it). Recommended integration (**main thread, synchronous**):
- In the editor's `TextWatcher.onTextChanged(s, start, before, count)`, record `(start, before, count)`.
- In `afterTextChanged(s)`, call `hl.update(s, start, before, count)` with the live `Editable`. It is read only during the call, so this is safe on the main thread.
- Then remove this app's spans in `[delta.startOffset, delta.endOffset]` and apply `hl.spansForLines(delta.firstLine, delta.endLine)`.
- Each `Editable.replace` normally delivers exactly one before/on/after triple. As a defensive measure, if more than one `onTextChanged` is seen before `afterTextChanged`, or the recorded change doesn't match the length delta, use `update(s)` (the diff overload). `update(text, start, removed, added)` already falls back to `fullScan` when the lengths are inconsistent.
- To run it off-thread, pass an immutable `String` snapshot and apply results only if the document version still matches. That adds an O(n) copy per keystroke, which is why main thread is recommended.

**Measured budgets (desktop JIT, 2026-09-25; phone ≈ 3-5x, UNVERIFIED):**
| Operation | 100 KB | 300 KB | 1 MB |
|---|---|---|---|
| `fullScan` | 1.71 ms | 4.44 ms | 14.99 ms |
| `update(text, start, removed, added)` p50 / p95 / max | **0.012 / 0.025 / 0.054 ms** | 0.009 / 0.015 / 0.052 ms | 0.010 / 0.021 / 0.029 ms |
| `update(text)` (diff overload) p50 | 0.139 ms | 0.363 ms | 1.205 ms |
| `spans()` flatten+sort | 0.57 ms | 1.58 ms | 5.36 ms |
| Open unclosed fence at top (worst case, rescans to the next fence or EOF) | 0.38 ms | 0.46 ms | 1.57 ms (to next fence); ~15 ms to EOF |

Pathological single-paragraph inputs (20,000 repetitions, `fullScan`, proto-final):

| Input | Before the fix | proto-final |
|---|---|---|
| `"x <!--" * 20000` (120 KB) | 1,984 ms | **23.5 ms** |
| `"[a](b \""` | — | 21.8 ms |
| `"<a href=\""` (180 KB) | — | 34.1 ms |
| backtick runs of mixed length (530 KB) | — | 7.6 ms |
| `"*a " * n + " b*" * n` | — | 13.0 ms |
| `"![" * n + "x" + "](u)" * n` | — | 14.8 ms |
| 20,000-column table | — | 22.1 ms |
| 2,000-deep nested list (4 MB) | — | 79.5 ms |

All of these are linear or near-linear now.

**Budget rules for the plan:**
1. `update` must stay < 1 ms p95 at 100 KB on the Pixel test device. Add a debug-build log line when `lastRescannedLines > 2000`.
2. Run `fullScan` on the main thread for documents < 300 KB (< ~20 ms on a phone). For larger files, run it on `Dispatchers.Default` with a String snapshot before the text is shown. This goes together with the storage track's size warning.
3. Apply spans per delta only; never re-apply the whole document per keystroke.

## 7. Curated highlight test-case table

There are **50 static cases and 7 typing sequences**. The source of truth is `plans/reference/markdown/test/HighlighterCases.kt` (from `proto-final/test/HighlighterCases.kt`), which is reproduced in full below.

The expectations are exact strings, so they are cheap to assert:
- `expectedSpans == hl.spans().joinToString(" ")`, using the `MdSpan.toString()` format `KIND(arg)[start,end)` with `(arg)` omitted when 0;
- `expectedLines == (0 until hl.lineCount).joinToString(" ") { hl.lineInfo(it).type.name }`.

Verification done:
1. All 57 pass on proto-final (`test/CasesCheck.kt`: failures=0).
2. All 50 static inputs were also run through the commonmark-java 0.30.0 oracle (GFM extensions, `BLOCKS_AND_INLINES` spans). They agree 50/50 on inline node ranges, heading lines/levels and code lines. The expectations therefore encode CommonMark/GFM behavior, not just whatever the prototype outputs.

**JUnit 6 translation (for the plan):**
```kotlin
class HighlighterCasesTest {
    @TestFactory fun static() = HIGHLIGHTER_CASES.map { c -> DynamicTest.dynamicTest("${c.id} ${c.note}") {
        val h = MarkdownHighlighter(enableHighlight = c.highlight).apply { fullScan(c.input) }
        assertEquals(c.expectedSpans, h.spans().joinToString(" "))
        assertEquals(c.expectedLines, (0 until h.lineCount).joinToString(" ") { h.lineInfo(it).type.name })
    } }
    @TestFactory fun typing() = TYPING_CASES.map { t -> DynamicTest.dynamicTest(t.id) {
        val h = MarkdownHighlighter(enableHighlight = false); var cur = t.start; h.fullScan(cur); var pos = t.at
        t.typed.forEachIndexed { i, ch ->
            cur = cur.substring(0, pos) + ch + cur.substring(pos)
            val d = h.update(cur, pos, 0, 1); pos++
            assertEquals(t.stepSpans[i], h.spans().joinToString(" "), "step $i")
            assertEquals(t.stepFull[i], d.full)
            assertEquals(MarkdownHighlighter(enableHighlight = false).apply { fullScan(cur) }.spans(), h.spans())
        }
    } }
}
```
Additional required tests (already written as `main()` harnesses in `test/Harness.kt`; port them to JUnit):
- **Spec differential vs commonmark-java**: 652 examples. Expect ≥ 641 to agree, and pin the 11 known IDs {6, 193, 195, 196, 198, 217, 259, 541, 571, 602, 606} as the allowed failures. commonmark-java is then a `testImplementation` dependency of `:core:markdown`.
- **Fuzz**: seed 7, 3,000 edits. Expect 0 span mismatches, 0 LineInfo mismatches, and 0 changes outside the delta.
- **Pathological timing**: `test/Patho.kt` inputs at n = 20,000 must each finish in < 500 ms. A generous CI bound catches quadratic regressions.

Behavioral notes the tests lock in (these are UX facts for the plan):
- `#` alone and `# ` alone are already H1 (C2, C3, T1), so the line grows the moment `#` is typed. `#hashtag` shrinks back on the next char.
- `a **b*` briefly shows `*b*` as italic while `**b**` is being typed (T2). This is CommonMark-correct and the same in the preview.
- Typing ```` ``` ```` turns the rest of the document into code until the fence is closed (T3, C40). Smart Enter auto-inserts the closing fence (section 8) so the flash is brief.
- A reference link only styles once its definition exists (T6 last step returns `full=true`).

```kotlin
package mdwriter.markdown

/**
 * Curated highlighter cases (generated from proto-final on 2026-09-25 and reviewed against CommonMark 0.31.2 / GFM 0.29).
 * expectedSpans == highlighter.spans().joinToString(" ")   (MdSpan.toString format: KIND(arg)[start,end) , arg omitted when 0)
 * expectedLines == (0 until lineCount).joinToString(" ") { lineInfo(it).type.name }
 */
data class HlCase(val id: String, val input: String, val expectedSpans: String, val expectedLines: String, val note: String, val highlight: Boolean = false)

val HIGHLIGHTER_CASES = listOf(
    HlCase("C1", "# Title",
        "HEADING(1)[0,7) HEADING_MARKER[0,2)",
        "ATX_HEADING",
        "ATX H1: HEADING from '#' to EOL; marker incl. space"),
    HlCase("C2", "# ",
        "HEADING(1)[0,2) HEADING_MARKER[0,2)",
        "ATX_HEADING",
        "'# ' alone is already a heading (line gets big as soon as '# ' is typed)"),
    HlCase("C3", "#",
        "HEADING(1)[0,1) HEADING_MARKER[0,1)",
        "ATX_HEADING",
        "'#' alone = empty H1 (CommonMark)"),
    HlCase("C4", "#hashtag not heading",
        "",
        "PARAGRAPH",
        "#hashtag is NOT a heading"),
    HlCase("C5", "####### seven",
        "",
        "PARAGRAPH",
        "7 hashes = paragraph"),
    HlCase("C6", "## Title ##",
        "HEADING(2)[0,11) HEADING_MARKER[0,3) HEADING_MARKER[9,11)",
        "ATX_HEADING",
        "closing # run is a marker"),
    HlCase("C7", "  ### indented",
        "HEADING(3)[2,14) HEADING_MARKER[2,6)",
        "ATX_HEADING",
        "<=3 spaces indent allowed; HEADING starts at first '#'"),
    HlCase("C8", "Setext H1\n===",
        "HEADING(1)[0,9) HEADING_MARKER[10,13)",
        "SETEXT_HEADING SETEXT_UNDERLINE",
        "setext H1: content line HEADING, underline = HEADING_MARKER", highlight = true),
    HlCase("C9", "Setext H2\n---",
        "HEADING(2)[0,9) HEADING_MARKER[10,13)",
        "SETEXT_HEADING SETEXT_UNDERLINE",
        "setext H2 ('---' under a paragraph is NOT a thematic break)"),
    HlCase("C10", "**bold *nested* bold**",
        "STRONG[0,22) EMPHASIS[7,15) EMPHASIS_MARKER[0,2) EMPHASIS_MARKER[7,8) EMPHASIS_MARKER[14,15) EMPHASIS_MARKER[20,22)",
        "PARAGRAPH",
        "nested emphasis inside strong"),
    HlCase("C11", "***both***",
        "EMPHASIS[0,10) STRONG[1,9) EMPHASIS_MARKER[0,1) EMPHASIS_MARKER[1,3) EMPHASIS_MARKER[7,9) EMPHASIS_MARKER[9,10)",
        "PARAGRAPH",
        "*** = EMPHASIS(outer) + STRONG(inner), spec order"),
    HlCase("C12", "_intra_word_ and snake_case_name",
        "EMPHASIS[0,12) EMPHASIS_MARKER[0,1) EMPHASIS_MARKER[11,12)",
        "PARAGRAPH",
        "'_' intraword does not close; outer _..._ is emphasis"),
    HlCase("C13", "foo*bar*baz",
        "EMPHASIS[3,8) EMPHASIS_MARKER[3,4) EMPHASIS_MARKER[7,8)",
        "PARAGRAPH",
        "'*' intraword emphasis IS allowed"),
    HlCase("C14", "`code with ** inside` **x**",
        "CODE_SPAN[0,21) STRONG[22,27) CODE_SPAN_MARKER[0,1) CODE_SPAN_MARKER[20,21) EMPHASIS_MARKER[22,24) EMPHASIS_MARKER[25,27)",
        "PARAGRAPH",
        "'**' inside code span is literal; code span markers"),
    HlCase("C15", "``a ` b``",
        "CODE_SPAN[0,9) CODE_SPAN_MARKER[0,2) CODE_SPAN_MARKER[7,9)",
        "PARAGRAPH",
        "double-backtick code span containing a single backtick"),
    HlCase("C16", "Typing **unclosed bold",
        "",
        "PARAGRAPH",
        "unclosed ** while typing: no styling at all (stays literal)"),
    HlCase("C17", "Typing **closed** later *",
        "STRONG[7,17) EMPHASIS_MARKER[7,9) EMPHASIS_MARKER[15,17)",
        "PARAGRAPH",
        "trailing lone '*' ignored"),
    HlCase("C18", "~~strike~~ and ~one~",
        "STRIKETHROUGH[0,10) STRIKETHROUGH[15,20) EMPHASIS_MARKER[0,2) EMPHASIS_MARKER[8,10) EMPHASIS_MARKER[15,16) EMPHASIS_MARKER[19,20)",
        "PARAGRAPH",
        "GFM strikethrough ~~x~~ and ~x~"),
    HlCase("C19", "==mark== text",
        "HIGHLIGHT[0,8) EMPHASIS_MARKER[0,2) EMPHASIS_MARKER[6,8)",
        "PARAGRAPH",
        "==highlight== (only with enableHighlight=true)", highlight = true),
    HlCase("C20", "\\*not emphasis\\* \\# no",
        "ESCAPE_MARKER[0,1) ESCAPE_MARKER[14,15) ESCAPE_MARKER[17,18)",
        "PARAGRAPH",
        "backslash escapes: only the backslash is a marker"),
    HlCase("C21", "line one  \nline two\\\nline three",
        "HARD_BREAK[8,10) HARD_BREAK[19,20)",
        "PARAGRAPH PARAGRAPH PARAGRAPH",
        "hard breaks: 2+ trailing spaces, backslash-newline; spans never cross newline"),
    HlCase("C22", "[text](http://x.y \"title\")",
        "LINK[0,26) LINK_TEXT[1,5) LINK_URL[7,17) LINK_TITLE[18,25) LINK_MARKER[0,1) LINK_MARKER[5,6) LINK_MARKER[6,7) LINK_MARKER[25,26)",
        "PARAGRAPH",
        "inline link with title: text/url/title/markers"),
    HlCase("C23", "![alt](img.png)",
        "LINK(1)[0,15) LINK_TEXT[2,5) LINK_URL[7,14) LINK_MARKER[0,2) LINK_MARKER[5,6) LINK_MARKER[6,7) LINK_MARKER[14,15)",
        "PARAGRAPH",
        "image: arg=1, '![' marker"),
    HlCase("C24", "[ref][r] and [r]\n\n[r]: https://e.com \"T\"",
        "LINK[0,8) LINK_TEXT[1,4) LINK_LABEL[6,7) LINK[13,16) LINK_TEXT[14,15) LINK_DEF[18,40) LINK_LABEL[19,20) LINK_URL[23,36) LINK_TITLE[37,40) LINK_MARKER[0,1) LINK_MARKER[4,5) LINK_MARKER[5,6) LINK_MARKER[7,8) LINK_MARKER[13,14) LINK_MARKER[15,16) LINK_MARKER[18,19) LINK_MARKER[20,22)",
        "PARAGRAPH BLANK LINK_DEF",
        "full + shortcut reference links resolve only because [r] is defined; LINK_DEF line"),
    HlCase("C25", "[undefined ref]",
        "",
        "PARAGRAPH",
        "undefined reference is plain text"),
    HlCase("C26", "<https://auto.link> www.example.com https://bare.url/p. mail me@x.org",
        "AUTOLINK[0,19) LINK_URL[1,18) AUTOLINK[20,35) AUTOLINK[36,54) AUTOLINK[61,69) LINK_MARKER[0,1) LINK_MARKER[18,19)",
        "PARAGRAPH",
        "<autolink>, www., https:// with trailing '.' trimmed, bare e-mail"),
    HlCase("C27", "Footnote[^1].\n\n[^1]: The note.",
        "FOOTNOTE_REF[8,12) FOOTNOTE_REF(1)[15,19) LINK_MARKER[19,20)",
        "PARAGRAPH BLANK FOOTNOTE_DEF",
        "footnote ref + footnote definition label (arg=1)"),
    HlCase("C28", "&amp; &#123; &notanentity",
        "ENTITY[0,5) ENTITY[6,12)",
        "PARAGRAPH",
        "named/numeric entities; '&notanentity' without ';' is text"),
    HlCase("C29", "text <span>inline</span> <!-- c -->",
        "HTML_INLINE[5,11) HTML_INLINE[17,24) HTML_INLINE[25,35)",
        "PARAGRAPH",
        "inline HTML tags and comment"),
    HlCase("C30", "<!-- block\ncomment -->\nafter",
        "HTML_BLOCK(2)[0,10) HTML_BLOCK(2)[11,22)",
        "HTML HTML PARAGRAPH",
        "HTML block type 2 (comment) spans 2 lines; next line is paragraph"),
    HlCase("C31", "- item\n- [ ] task\n- [x] done",
        "LIST_MARKER[0,1) LIST_MARKER[7,8) TASK_MARKER[9,12) LIST_MARKER[18,19) TASK_MARKER(1)[20,23)",
        "PARAGRAPH PARAGRAPH PARAGRAPH",
        "bullet list + GFM task items (arg=1 checked); contentStart after '[ ] '"),
    HlCase("C32", "1) one\n2) two",
        "LIST_MARKER[0,2) LIST_MARKER[7,9)",
        "PARAGRAPH PARAGRAPH",
        "'1)' ordered list"),
    HlCase("C33", "3. three",
        "LIST_MARKER[0,2)",
        "PARAGRAPH",
        "ordered list starting at 3 (not after a paragraph)"),
    HlCase("C34", "- a\n  - b\n    - c",
        "LIST_MARKER[0,1) LIST_MARKER(1)[6,7) LIST_MARKER(2)[14,15)",
        "PARAGRAPH PARAGRAPH PARAGRAPH",
        "nested lists: LIST_MARKER arg = depth"),
    HlCase("C35", "- item\n\n      indented code in item",
        "CODE_BLOCK[14,35) LIST_MARKER[0,1)",
        "PARAGRAPH BLANK INDENTED_CODE",
        "indented code inside a list item (content column + 4)"),
    HlCase("C36", "- item\ncontinued lazily",
        "LIST_MARKER[0,1)",
        "PARAGRAPH PARAGRAPH",
        "lazy continuation keeps the list container (listDepth=1)"),
    HlCase("C37", "    indented code",
        "CODE_BLOCK[4,17)",
        "INDENTED_CODE",
        "top-level indented code"),
    HlCase("C38", "> quote **b**\n> > nested\nlazy",
        "BLOCKQUOTE(1)[0,13) STRONG[8,13) BLOCKQUOTE(2)[14,24) BLOCKQUOTE(2)[25,29) QUOTE_MARKER[0,2) EMPHASIS_MARKER[8,10) EMPHASIS_MARKER[11,13) QUOTE_MARKER[14,16) QUOTE_MARKER[16,18)",
        "PARAGRAPH PARAGRAPH PARAGRAPH",
        "quote with inline, nested quote, lazy line keeps depth 2"),
    HlCase("C39", "```kotlin\nval x = 1\n```",
        "CODE_INFO[3,9) CODE_BLOCK[10,19) CODE_FENCE[0,3) CODE_FENCE[20,23)",
        "FENCE_OPEN FENCED_CODE FENCE_CLOSE",
        "fenced code with info string"),
    HlCase("C40", "```\nunclosed\nfence",
        "CODE_BLOCK[4,12) CODE_BLOCK[13,18) CODE_FENCE[0,3)",
        "FENCE_OPEN FENCED_CODE FENCED_CODE",
        "unclosed fence runs to end of document"),
    HlCase("C41", "~~~\n`~~~` inside\n~~~",
        "CODE_BLOCK[4,16) CODE_FENCE[0,3) CODE_FENCE[17,20)",
        "FENCE_OPEN FENCED_CODE FENCE_CLOSE",
        "tilde fence; backticks inside are code"),
    HlCase("C42", "| a | b |\n|:--|--:|\n| 1 | `|` |",
        "TABLE_ROW[0,9) TABLE_ROW(1)[10,19) TABLE_ROW(2)[20,31) TABLE_PIPE[0,1) TABLE_PIPE[4,5) TABLE_PIPE[8,9) TABLE_PIPE[10,11) TABLE_PIPE[14,15) TABLE_PIPE[18,19) TABLE_PIPE[20,21) TABLE_PIPE[24,25) TABLE_PIPE[27,28) TABLE_PIPE[30,31)",
        "TABLE_HEADER TABLE_DELIMITER TABLE_ROW",
        "GFM table: header/delimiter/body rows; the pipe inside backticks is a cell separator (GFM)"),
    HlCase("C43", "| not | table |\nno delimiter row",
        "",
        "PARAGRAPH PARAGRAPH",
        "no delimiter row -> not a table"),
    HlCase("C44", "---\ntitle: Hello\n---\n# Body",
        "FRONT_MATTER[4,16) HEADING(1)[21,27) FRONT_MATTER_FENCE[0,3) FRONT_MATTER_FENCE[17,20) HEADING_MARKER[21,23)",
        "FRONT_MATTER_FENCE FRONT_MATTER FRONT_MATTER_FENCE ATX_HEADING",
        "YAML front matter (closed) then heading"),
    HlCase("C45", "---\nnot front matter (no close)",
        "THEMATIC_BREAK[0,3)",
        "THEMATIC_BREAK PARAGRAPH",
        "'---' without closing fence = thematic break, not front matter"),
    HlCase("C46", "***\n- - -",
        "THEMATIC_BREAK[0,3) THEMATIC_BREAK[4,9)",
        "THEMATIC_BREAK THEMATIC_BREAK",
        "thematic breaks *** and - - -"),
    HlCase("C47", "a\n***\nb",
        "THEMATIC_BREAK[2,5)",
        "PARAGRAPH THEMATIC_BREAK PARAGRAPH",
        "*** interrupts a paragraph"),
    HlCase("C48", "Para\n    not code (lazy)",
        "",
        "PARAGRAPH PARAGRAPH",
        "indented line after a paragraph is lazy text, not code"),
    HlCase("C49", "*a\nb*",
        "EMPHASIS[0,2) EMPHASIS[3,5) EMPHASIS_MARKER[0,1) EMPHASIS_MARKER[4,5)",
        "PARAGRAPH PARAGRAPH",
        "emphasis across a soft line break is split per line"),
    HlCase("C50", "<div>\n**not styled**\n</div>\n\n**styled**",
        "HTML_BLOCK(6)[0,5) HTML_BLOCK(6)[6,20) HTML_BLOCK(6)[21,27) STRONG[29,39) EMPHASIS_MARKER[29,31) EMPHASIS_MARKER[37,39)",
        "HTML HTML HTML BLANK PARAGRAPH",
        "HTML block type 6 (<div>) until blank line: inner ** not styled"),
)

/** Type [typed] one char at a time at [at] using update(text, pos, 0, 1). After EVERY step: incremental spans == fullScan spans.
 * stepSpans[i] = expected spans().joinToString(" ") after typing i+1 chars; stepFull[i] = expected HighlightDelta.full. */
data class TypingCase(val id: String, val start: String, val at: Int, val typed: String, val stepSpans: List<String>, val stepFull: List<Boolean>)

val TYPING_CASES = listOf(
    TypingCase("T1", "", 0, "# Hi",
        listOf("HEADING(1)[0,1) HEADING_MARKER[0,1)", "HEADING(1)[0,2) HEADING_MARKER[0,2)", "HEADING(1)[0,3) HEADING_MARKER[0,2)", "HEADING(1)[0,4) HEADING_MARKER[0,2)"),
        listOf(false, false, false, false)),
    TypingCase("T2", "a ", 2, "**b**",
        listOf("", "", "", "EMPHASIS[3,6) EMPHASIS_MARKER[3,4) EMPHASIS_MARKER[5,6)", "STRONG[2,7) EMPHASIS_MARKER[2,4) EMPHASIS_MARKER[5,7)"),
        listOf(false, false, false, false, false)),
    TypingCase("T3", "x\n\ny", 1, "\n```",
        listOf("", "", "", "CODE_BLOCK[7,8) CODE_FENCE[2,5)"),
        listOf(false, false, false, false)),
    TypingCase("T4", "Title\n", 6, "==",
        listOf("HEADING(1)[0,5) HEADING_MARKER[6,7)", "HEADING(1)[0,5) HEADING_MARKER[6,8)"),
        listOf(false, false)),
    TypingCase("T5", "| a | b |\n", 10, "|-|-|",
        listOf("", "", "", "TABLE_ROW[0,9) TABLE_ROW(1)[10,14) TABLE_PIPE[0,1) TABLE_PIPE[4,5) TABLE_PIPE[8,9) TABLE_PIPE[10,11) TABLE_PIPE[12,13)", "TABLE_ROW[0,9) TABLE_ROW(1)[10,15) TABLE_PIPE[0,1) TABLE_PIPE[4,5) TABLE_PIPE[8,9) TABLE_PIPE[10,11) TABLE_PIPE[12,13) TABLE_PIPE[14,15)"),
        listOf(false, false, false, false, false)),
    TypingCase("T6", "[r]\n\n", 5, "[r]: u",
        listOf("", "", "", "", "", "LINK[0,3) LINK_TEXT[1,2) LINK_DEF[5,11) LINK_LABEL[6,7) LINK_URL[10,11) LINK_MARKER[0,1) LINK_MARKER[2,3) LINK_MARKER[5,6) LINK_MARKER[7,9)"),
        listOf(false, false, false, false, false, true)),
    TypingCase("T7", "", 0, "- [x] t",
        listOf("LIST_MARKER[0,1)", "LIST_MARKER[0,1)", "LIST_MARKER[0,1)", "LIST_MARKER[0,1)", "LIST_MARKER[0,1) TASK_MARKER(1)[2,5)", "LIST_MARKER[0,1) TASK_MARKER(1)[2,5)", "LIST_MARKER[0,1) TASK_MARKER(1)[2,5)"),
        listOf(false, false, false, false, false, false, false)),
)
```

## 8. Smart editing spec table

`object SmartEdit` (reference: `plans/reference/markdown/SmartEdit.kt`) is **pure**. It takes `(text: String, selStart, selEnd)` and returns a `TextEdit(start, end, replacement, selStart, selEnd)`, where the selection is in new-text coordinates. `null` means "let the default behavior happen". `TextEdit.minimize(text)` trims the common prefix and suffix so the UI replaces as little as possible (verified: it gives the same result on all harness cases).

| Command | Function | Trigger in UI |
|---|---|---|
| Smart Enter | `onEnter(text, cursor, lineType, fenceUnclosed, renumber = true)` | IME Enter / hardware Enter (see integration below) |
| Smart Backspace (optional, default on) | `onBackspace(text, cursor)` | Backspace at a collapsed cursor exactly at a list item's / quote line's content start |
| Indent / outdent list item | `indentListItem`, `outdentListItem` | Tab / Shift+Tab (hardware), toolbar only if the design wants it |
| Bold / italic / strike / highlight / inline code toggle | `toggleWrap(text, a, b, "**" / "*" / "~~" / "==" / "`")` | Selection toolbar B / I / S / code; Ctrl+B, Ctrl+I, Ctrl+Shift+C |
| Link / image | `insertLink(text, a, b, image = false)` | Toolbar link; Ctrl+K |
| Heading cycle | `cycleHeading(text, cursor)`: none → `#` → `##` → `###` → none (levels 4-6 → none) | Toolbar "H" |
| Set heading level | `setHeading(text, cursor, level 0..6)` | Ctrl+0…Ctrl+6 |
| Quote toggle | `toggleQuote(text, a, b)`: add `> ` to all touched lines, or remove one level if all are quoted; blank lines inside become `>` | Toolbar quote |
| Task toggle | `toggleTask(text, cursor)`: `[ ]` ↔ `[x]` | Tap on TASK_MARKER span (optional), Ctrl+Enter (optional) |

**Rules encoded (all shown in the harness output below):**
- **List continuation** for `-`, `*`, `+`, `1.`, `1)`: the new item copies indentation, quote prefix, marker and spacing (spacing normalized to 1 space if it was more than 4).
  - Ordered lists **auto-increment** and **renumber following siblings** in the same edit (`1. one|⏎2. two` → `1. one⏎2. |⏎3. two`).
  - With "lazy numbering" (`1.` then `1.`), the number is kept.
  - A task item continues as `- [ ] ` (unchecked, even when the current one is checked).
  - A quote line continues with `> `, as does `> - item`.
  - Enter in the middle of an item splits it.
  - Enter with the cursor inside the prefix, and on `---` or `-not a list`, gives a plain newline.
- **Enter on an empty item ends the list.**
  - A top-level empty item has its marker removed and the cursor stays on the now-empty line: `- |` → `|`, and `> - |` → `> |`.
  - A nested empty item outdents one level: `  - |` → `- |`.
  - An empty quote line `> |` removes the quote.
- **Verbatim lines** (`lineType.isVerbatim`: code, HTML, front matter): Enter keeps only the leading whitespace and `>` prefix. No list continuation inside code.
- **Unclosed fence:** Enter at the end of an opening fence line whose fence is unclosed (`highlighter.isFenceUnclosed(line)`) inserts the closing fence: ```` ```kotlin| ```` → ```` ```kotlin⏎|⏎``` ````. This also works inside quotes (`> ~~~~`).
- **Inline toggles:**
  - Wrap the selection. With a collapsed cursor, wrap the word under it. With a collapsed cursor and no word, insert an empty pair with the cursor inside.
  - Surrounding whitespace is excluded from the wrap (emphasis can't start or end with a space).
  - **Un-toggle** when the markers are just outside the selection or included in it.
  - Bold on italic gives `***x***`; italic on `***x***` gives `**x**`.
  - Multi-line selections wrap each non-blank line.
  - Code uses a backtick fence one longer than the longest run inside, padded with a space when needed.
  - Property checked: **toggle twice = identity** for all 5 markers on 7 samples (0 violations).
- **Link:** `see «docs»` → `see [docs](|)` (cursor in the URL). A selected URL → `[|](url)` (cursor in the text). Nothing selected → `[](|)`. Image adds `!`.
- **Indent:** nests under the previous sibling by the sibling's marker width (`- ` = 2, `1. ` = 3, `10. ` = 4). A newly nested ordered item restarts at `1`. **Outdent:** aligns to the nearest less-indented list item above, or returns null at top level.

**Harness output** (`test/SmartHarness.kt`; `|` = cursor, `«…»` = selection, `⏎` = newline; "(default newline)" means the function returned `null`, i.e. default behavior):
```text
| Command | Before | After |
|---|---|---|
| Enter | `- item|` | `- item⏎- |` |
| Enter | `* item|` | `* item⏎* |` |
| Enter | `+ item|` | `+ item⏎+ |` |
| Enter | `- it|em` | `- it⏎- |em` |
| Enter | `1. one|` | `1. one⏎2. |` |
| Enter | `9) nine|` | `9) nine⏎10) |` |
| Enter | `1. one|⏎2. two⏎3. three` | `1. one⏎2. |⏎3. two⏎4. three` |
| Enter | `- [ ] task|` | `- [ ] task⏎- [ ] |` |
| Enter | `- [x] done|` | `- [x] done⏎- [ ] |` |
| Enter | `> quote|` | `> quote⏎> |` |
| Enter | `> - quoted item|` | `> - quoted item⏎> - |` |
| Enter | `- |` | `|` |
| Enter | `1. |` | `|` |
| Enter | `- [ ] |` | `|` |
| Enter | `  - |` | `- |` |
| Enter | `- a⏎  - |` | `- a⏎- |` |
| Enter | `> |` | `|` |
| Enter | `> - |` | `> |` |
| Enter | `|- item` | `(default newline)` |
| Enter | `plain text|` | `(default newline)` |
| Enter | `---|` | `(default newline)` |
| Enter | `-not a list|` | `(default newline)` |
| Enter(code line) | `    code|` | `    code⏎    |` |
| Enter(fenced, list) | `- ```⏎  - not a list|` | `- ```⏎  - not a list⏎  |` |
| Enter(unclosed fence) | ````kotlin|` | ````kotlin⏎|⏎```` |
| Enter(unclosed fence in quote) | `> ~~~~|` | `> ~~~~⏎> |⏎> ~~~~` |
| Enter(closed fence) | ````kotlin|⏎```` | `(default newline)` |
| Enter | `1. a⏎1. b|` | `1. a⏎1. b⏎1. |` |
| Enter | `  1. nested|` | `  1. nested⏎  2. |` |
| Enter | `- a|⏎- b` | `- a⏎- |⏎- b` |
| Quote | `some |text` | `> some |text` |
| Quote | `> some |text` | `some |text` |
| Quote | `«one⏎⏎two»` | `> «one⏎>⏎> two»` |
| Quote | `«> one⏎>⏎> two»` | `«one⏎⏎two»` |
| Quote | `> > dee|p` | `> dee|p` |
| SetHeading(2) | `Title|` | `## Title|` |
| SetHeading(0) | `### Ti|tle` | `Ti|tle` |
| SetHeading(1) | `- ## item|` | `- # item|` |
| Backspace | `- |item` | `|item` |
| Backspace | `  - [ ] |task` | `  |task` |
| Backspace | `> > |q` | `> |q` |
| Backspace | `- it|em` | `(default newline)` |
| Highlight | `«key»` | `==«key»==` |
| Italic | `*«x»*` | `«x»` |
| Bold | `*«x»*` | `***«x»***` |
| Bold | `make «this» bold` | `make **«this»** bold` |
| Bold | `make **«this»** bold` | `make «this» bold` |
| Bold | `make «**this**» bold` | `make «this» bold` |
| Bold | `make th|is bold` | `make **«this»** bold` |
| Bold | `make «this » bold` | `make **«this»**  bold` |
| Bold | `empty | here` | `empty **|** here` |
| Bold | `***«x»***` | `*«x»*` |
| Italic | `make «this» it` | `make *«this»* it` |
| Italic | `make *«this»* it` | `make «this» it` |
| Italic | `make **«this»** it` | `make ***«this»*** it` |
| Italic | `***«x»***` | `**«x»**` |
| Italic | `«line one⏎line two»` | `«*line one*⏎*line two*»` |
| Strike | `«gone»` | `~~«gone»~~` |
| Strike | `~~«gone»~~` | `«gone»` |
| Code | `«val x»` | ``«val x»`` |
| Code | `«a `tick`»` | ``` «a `tick`» ``` |
| Link | `see «docs» here` | `see [docs](|) here` |
| Link | `«https://ia.net»` | `[|](https://ia.net)` |
| Link | `at | end` | `at [](|) end` |
| Image | `«diagram»` | `![diagram](|)` |
| Heading | `Title|` | `# Title|` |
| Heading | `# Title|` | `## Title|` |
| Heading | `## Title|` | `### Title|` |
| Heading | `### Title|` | `Title|` |
| Heading | `#### Title|` | `Title|` |
| Heading | `> Quote| title` | `> # Quote| title` |
| Indent | `- a⏎- b|` | `- a⏎  - b|` |
| Indent | `1. a⏎2. b|` | `1. a⏎   1. b|` |
| Indent | `10. a⏎11. b|` | `10. a⏎    1. b|` |
| Outdent | `- a⏎  - b|` | `- a⏎- b|` |
| Outdent | `- b|` | `(default newline)` |
| Task | `- [ ] t|odo` | `- [x] t|odo` |
| Task | `- [x] t|odo` | `- [ ] t|odo` |
toggle-twice identity violations: 0
minimize() violations: 0
```

**Integration recommendation for `:app`** (the editor track owns the final choice; flagged **medium** confidence):
- Intercept Enter in the EditText subclass.
  - Wrap the `InputConnection` returned by `onCreateInputConnection` in an `InputConnectionWrapper` that intercepts `commitText("\n", …)` and `sendKeyEvent(KEYCODE_ENTER down)`.
  - Also override `onKeyDown(KEYCODE_ENTER)` for hardware keyboards.
  - Compute `SmartEdit.onEnter(editable.toString(), selectionStart, hl.lineInfoAt(sel).type, hl.isFenceUnclosed(line))`. If it returns non-null, apply it inside `beginBatchEdit()/endBatchEdit()` as `editable.replace(e.start, e.end, e.replacement)` + `setSelection(e.selStart, e.selEnd)`, using `e.minimize(editable)` first. Otherwise pass the Enter through.
  - This keeps each smart edit a single text change (one undo step, one highlighter `update`).
  - Do not use a post-hoc `TextWatcher` rewrite: that makes two changes and two undo steps, and it re-enters the watcher.
- Only when the selection is collapsed and the IME is not composing (`BaseInputConnection.getComposingSpanStart(editable) == -1`). With a non-empty selection, Enter replaces the selection normally.
- The selection toolbar actions call `toggleWrap`, `insertLink`, `cycleHeading` and `toggleQuote` with the current selection, and apply the result the same way.
- `editable.toString()` per command is O(n): about 1 ms per MB. That is fine because commands are rare (not per keystroke).

## 9. Stats (words, chars, reading time)

`object TextStats` (reference `plans/reference/markdown/TextStats.kt`):

```kotlin
data class Stats(val words: Int, val chars: Int, val charsNoSpaces: Int, val sentences: Int, val tasks: Int, val tasksDone: Int) {
    fun readingMinutes(wpm: Int = 238): Double
    fun readingMinutesRounded(wpm: Int = 238): Int
}
TextStats.compute(text, from, to, spans): Stats
```
- `spans` is `hl.spans()`.
- `[from, to)` is the whole document, or the selection. iA Writer shows stats for the selection when text is selected ("The statistics adapt to the selected text", ia.net Stats iPhone page).

**Decisions:**
1. **Words exclude markup.**
   - Excluded: every marker span (`#`, `**`, `>`, `-`, `[ ]`, `](`, …), link destinations and titles, HTML tags and comments, front matter, link reference definitions, entities, footnote labels, and fence info strings.
   - Text inside HTML blocks between tags counts. HTML comment blocks (type 2) are excluded entirely.
   - Code spans and code blocks **count** as words.
   - A word is a run of letters, digits and combining marks. Inner `'` `’` `-` `.` `_` join words (`don't`, `e-mail`, `3.14`, `snake_case` each count as 1). `,` joins only between digits (`1,000`).
   - **CJK:** every Han, Hiragana or Katakana code point counts as one word (the MS Word convention). Hangul is space-separated, so it is counted normally.
   - **Known limitation:** Thai, Lao, Khmer and Myanmar runs count as one word each (`สวัสดีครับ ภาษาไทย` counts 2, true count is about 4). Optional phase-2 fix: an injectable `WordSegmenter` interface in `:core`, implemented in `:app` with `android.icu.text.BreakIterator.getWordInstance()` for those scripts only. This keeps JVM tests deterministic.
2. **Characters**: Unicode code points excluding line breaks, **including** markup and spaces. `charsNoSpaces` excludes whitespace. This is simple and predictable, and matches the selection length. Whether iA Writer excludes markup from character counts is **UNVERIFIED**.
   - Grapheme clusters (`👍🏽` = 1 instead of 2) are an optional UI-layer refinement via `android.icu.text.BreakIterator.getCharacterInstance()`.
3. **Sentences**: `.` `!` `?` `。` `！` `？` after at least one word, plus a final unterminated sentence.
4. **Reading time**: words / **238 wpm**. This is Brysbaert (2019), *Journal of Memory and Language* 109:104047: a meta-analysis of 190 studies and 18,573 participants, 238 wpm for English non-fiction and 260 wpm for fiction (verified via search on 2026-09-25). Display it as `ceil`, at least 1 min when words > 0.
5. **Incremental/cheap approach**: a full pass costs **1.85 ms per 100 KB** and **18.4 ms per 1 MB** (desktop).
   - After edits, debounce **400 ms**. Snapshot `text.toString()` + `hl.spans()` on the main thread (≈0.6 ms per 100 KB), then compute on `Dispatchers.Default` and post the result. Drop stale results by document version.
   - Selection stats are computed on selection change, on the same debounce. For a small selection they are cheap enough to run synchronously.
   - A per-line cache (words and chars are additive per line because a line break always ends a word) is a possible later optimization. It is **not needed** at these costs.
   - Stats UI is optional and hidden by default, so everything should stay out of the main writing area (iA style: a small counter only when enabled).

Harness output (`test/StatsHarness.kt`, proto-final):
```text
| Input | words | chars | charsNoSpaces | sentences | tasks(done) | read min |
|---|---|---|---|---|---|---|
| `Hello world` | 2 | 11 | 10 | 1 | 0(0) | 1 |
| `# Heading with **bold** text` | 4 | 28 | 24 | 1 | 0(0) | 1 |
| `[link text](https://example.com/a/b "Title")` | 2 | 44 | 42 | 1 | 0(0) | 1 |
| `- [x] done task⏎- [ ] open task` | 4 | 30 | 23 | 1 | 2(1) | 1 |
| `don't stop e-mail 3.14 1,000 snake_case` | 6 | 39 | 34 | 1 | 0(0) | 1 |
| `日本語のテキスト` | 8 | 8 | 8 | 1 | 0(0) | 1 |
| `中文 English 混合` | 5 | 13 | 11 | 1 | 0(0) | 1 |
| `한국어 단어 세 개` | 4 | 10 | 7 | 1 | 0(0) | 1 |
| `Café naïve résumé` | 3 | 17 | 15 | 1 | 0(0) | 1 |
| ``inline code` and⏎⏎```kotlin⏎val x = 1⏎```` | 6 | 38 | 33 | 1 | 0(0) | 1 |
| `---⏎title: Front matter words⏎---⏎Body only.` | 2 | 41 | 37 | 1 | 0(0) | 1 |
| `<!-- hidden comment --> visible` | 0 | 31 | 27 | 0 | 0(0) | 0 |
| `Emoji 👍🏽 test` | 2 | 13 | 11 | 1 | 0(0) | 1 |
| `Two sentences. Here! Right?` | 4 | 27 | 24 | 3 | 0(0) | 1 |
| `[^1] footnote⏎⏎[^1]: note text` | 3 | 28 | 25 | 1 | 0(0) | 1 |
| `&amp; &copy; entity` | 1 | 19 | 17 | 1 | 0(0) | 1 |
| `[ref]: http://x.y "t"` | 0 | 21 | 19 | 0 | 0(0) | 0 |
| `<div>⏎Inside div text⏎</div>` | 3 | 26 | 24 | 1 | 0(0) | 1 |
| `สวัสดีครับ ภาษาไทย` | 2 | 18 | 17 | 1 | 0(0) | 1 |
STATS BENCH exp/doc100k.md chars=101110 1.85ms/full pass
STATS BENCH exp/doc1m.md chars=1000939 18.35ms/full pass
```

## 10. Reference prototype status (`proto-final/`)

**Verdict: yes, commit it** to `plans/reference/markdown/` as the starting point for Sonnet. It is verified, not a sketch:

| Check | Result |
|---|---|
| Compiles (Kotlin 2.3.10, `-Xexplicit-api=strict -jvm-target 17`, no Android deps) | yes, no warnings |
| Curated cases (section 7) | 57/57 pass; 50/50 agree with the commonmark-java oracle |
| CommonMark 0.31.2 spec differential vs commonmark-java 0.30.0 | 641/652 agree; 11 known and documented diffs |
| Incremental == full fuzz (3 seeds × 3,000 edits, both `update` overloads) | 0 span, 0 LineInfo, 0 outside-delta mismatches |
| Pathological inputs | all linear/near-linear (≤ 35 ms per 120-530 KB paragraph) |
| SmartEdit | 80+ behavior rows as expected; toggle-twice identity; minimize safe |
| TextStats | table as in section 9; 1.85 ms per 100 KB |

Honest quality notes:
- The **salvaged `proto/` had two real performance bugs**. Both are fixed in `proto-final/`:
  - quadratic span distribution in long paragraphs (44 ms → 1.6 ms for 2,000 lines);
  - quadratic unclosed `<!--` scans (1,984 ms → 23 ms).
- The salvaged proto also lacked email autolinks, change-info updates, deltas, `LineInfo`, verbatim-aware Enter, quote toggle and set-heading. All are added.
- **Style:** the code is dense (one-liners, `;`) and **will not pass ktlint_official with 120 columns as is**. Sonnet must run `spotlessApply` and hand-wrap the rest without changing logic, re-running the tests after each pass.
- **Remaining known limitations:** see section 5 and `proto-final/README.md`. None blocks v1.
- Numbers are from a desktop JIT. **The plan must include an on-device check**: a debug-only timing log of `update()` p95 on the Pixel emulator/device with a 100 KB file, target < 1 ms.

Files to copy into the repo: `plans/reference/markdown/{MdModel.kt, MarkdownHighlighter.kt, InlineScanner.kt, SmartEdit.kt, TextStats.kt, MarkdownHtml.kt, README.md, test/*.kt, test/resources/*}`.

## 11. Suggested plan tasks (Markdown track), in order
1. **`:core:markdown` engine port.**
   - Copy MdModel, InlineScanner, MarkdownHighlighter and TextStats into `core/markdown/src/main/kotlin/…`.
   - Port `HighlighterCases` + `CasesCheck` to a JUnit 6 `@TestFactory`.
   - Port the fuzz test (seed 7, 3,000 rounds, both overloads, all three assertions).
   - Add a spec differential test (read `spec-0.31.2.json` with a tiny hand parser or kotlinx-serialization-free regex; oracle = commonmark-java `testImplementation`; pin the 11 allowed IDs).
   - Add the Patho timing test (< 500 ms each).
   - Then `spotlessApply` + reformat.
2. **SmartEdit port** + table-driven tests from the harness rows in section 8 (each row is one `assertEquals(after, render(apply(before)))`, using the `|`/`«»` notation helpers from `SmartHarness.kt`).
3. **TextStats port** + tests from the section 9 table.
4. **MarkdownHtml** (commonmark 0.30.0 deps) + tests:
   - checkbox, footnote, table-align, alert and sanitized-`javascript:` output;
   - `renderPage` contains the CSP;
   - front matter is not rendered.
5. **`:app` editor integration** (with the editor track):
   - TextWatcher → `update(s, start, before, count)` → apply `spansForLines(delta)`;
   - `fullScan` on open;
   - the InputConnection Enter interception → `SmartEdit.onEnter(…, lineInfoAt(sel).type, isFenceUnclosed(line))`;
   - the selection toolbar → toggles;
   - hardware shortcuts.
6. **Preview:**
   - a WebView overlay with the settings in section 3.2;
   - `WebViewAssetLoader` (`androidx.webkit:webkit:1.17.1`) serving `assets/preview/preview.css` + fonts;
   - `DocumentImagePathHandler`;
   - external links via `ACTION_VIEW`;
   - theme via class + CSS vars.
7. **Stats UI** (optional, hidden by default): a debounced background computation plus selection stats.
8. **On-device performance check** task: log `update` p95 and `fullScan` for 100 KB / 1 MB files, and put the numbers in the PR.

**Open questions for the user** (non-blocking; defaults chosen):
- (a) `==highlight==` on by default? The default is **off**, as a setting.
- (b) Render raw HTML in the preview? The default is **yes** (inert: no JS, no network).
- (c) Should characters exclude markup? The default is **no**.
- (d) Smart Backspace removing list markers? The default is **on**.

## 12. Sources
Primary sources, fetched or verified 2026-09-25 unless noted:
- CommonMark spec 0.31.2 and its test examples: https://spec.commonmark.org/0.31.2/ (local copies `research/spec.txt`, `research/spec-0.31.2.json`; fetched 2026-09-24 by the interrupted agent).
- GitHub Flavored Markdown spec 0.29-gfm: https://github.github.com/gfm/ (local `research/gfm.txt`). Verified in it:
  - "Include a pipe in a cell's content by escaping it, including inside other inline spans";
  - the e-mail autolink rules "There must be at least one period. The last character must not be one of - or _".
- commonmark-java README and CHANGELOG (0.30.0, 2026-08-06): https://github.com/commonmark/commonmark-java (local `research/cm-readme.md`, `research/cm-changelog.md`). `DefaultUrlSanitizer` protocols (http, https, mailto, data) were verified by `javap` on commonmark-0.30.0.jar.
- Maven Central metadata (versions and lastUpdated), fetched with `scratchpad/mvn.sh`:
  - https://repo1.maven.org/maven2/org/commonmark/commonmark/maven-metadata.xml and each `commonmark-ext-*` → 0.30.0;
  - https://repo1.maven.org/maven2/org/jetbrains/markdown/maven-metadata.xml → 0.7.14;
  - https://repo1.maven.org/maven2/com/mikepenz/multiplatform-markdown-renderer-android/maven-metadata.xml → 0.45.0;
  - https://repo1.maven.org/maven2/org/nibor/autolink/autolink/maven-metadata.xml → 0.12.0;
  - https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/maven-metadata.xml → 1.17.1 stable.
- JetBrains markdown README: https://github.com/JetBrains/markdown (local `research/jb-readme.md`).
- mikepenz multiplatform-markdown-renderer README: https://github.com/mikepenz/multiplatform-markdown-renderer (local `research/mpmr-readme.md`). It is built on JetBrains markdown and uses GFM flavour.
- Android `WebSettings` reference: https://developer.android.com/reference/android/webkit/WebSettings. Verified:
  - `setBlockNetworkLoads` SecurityException/default without INTERNET;
  - `setAllowFileAccess` default false for R+ and the recommendation of WebViewAssetLoader;
  - `setAllowContentAccess` default enabled.
- WebView dark theme guide: https://developer.android.com/develop/ui/views/layout/webapps/dark-theme ("WebView always sets prefers-color-scheme according to isLightTheme").
- `androidx.webkit.WebViewAssetLoader` source (webkit sources jar in `research/src/webkit`): `DEFAULT_DOMAIN = "appassets.androidplatform.net"`, `AssetsPathHandler`, `ResourcesPathHandler`, `InternalStoragePathHandler`, `Builder.setHttpAllowed`, `addPathHandler`.
- iA Writer Stats (iPhone) support page: https://ia.net/writer/support/editor/stats/stats-iphone (local capture `scratchpad/live/writer_support_editor_stats_stats_iphone.txt`). It covers character, sentence and task counts, reading time, and stats for the selection.
- Brysbaert, M. (2019). How many words do we read per minute? A review and meta-analysis of reading rate. *Journal of Memory and Language* 109:104047. It reports 238 wpm for non-fiction and 260 wpm for fiction over 190 studies. https://www.researchgate.net/publication/335174808 and https://gwern.net/doc/psychology/linguistics/2019-brysbaert.pdf (via web search, 2026-09-25).
- Measurements: `research/exp/Bench.java`, `HtmlTest.java`, `CmDump.java`, `JbDump.java` (outputs `cm-out.txt`, `jb-out.txt`) and `research/proto-final/test/*.kt`, all run 2026-09-25 on JBR 21.0.11.

**UNVERIFIED items** (flagged for on-device QA):
- phone performance (assumed 3-5x desktop);
- WebView first-instantiation cost;
- the exact CSP behavior in Android WebView for `loadDataWithBaseURL` content;
- whether iA Writer counts markup in characters;
- the InputConnection Enter interception with every IME. Test at least Gboard and Samsung Keyboard; the editor track owns this.
