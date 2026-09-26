# T02 — Design system: theme, fonts, icons, launcher icon, splash

**Goal**
Give the app its iA-style look foundation: the three bundled iA Writer font families, the Light/Dark/Pure-black colour
tokens, a flat Material 3 theme built from those tokens, the 55 Material Symbols icons, the adaptive launcher icon
(themed-icon ready), and a splash in the page colour. A debug-only "design gallery" screen shows swatches, fonts and
icons. It follows the system dark mode live, without recreating the activity. Every later UI task builds on the names
defined here.

**Depends on**
T01, which delivered: a buildable `:app` + `:core:markdown`, a verbatim `Makefile`, `MainActivity` (with the
`Log.d("MainActivity") { "onCreate …" }` line), the placeholder `Theme.MdWriter`, the `configChanges` list including
`uiMode`, no `android:icon` yet, and a green `make check`. Read the T01 STATUS entry (emulator serial, deviations).

**Read first**
- `plans/02-design-spec.md` §1 (fonts), §2 (colour tokens + M3 mapping), §3 (typography & layout numbers), §5 (caret,
  chrome, stats numbers), §6 (pill dimensions), §7 (drawer dimensions), §8–§10 (menu/sheet/preview dimensions),
  §11 (motion), §13 (launcher icon), §14 (icons).
- `plans/01-architecture.md` §3 (`ui/theme/` files), §8 (configChanges: uiMode is handled, so there is no
  recreation), §10 HARD RULES 1 and 14.
- `plans/reference/fonts.md` (files, sizes, family XML), `plans/reference/scripts/fetch-fonts.sh`, `fetch-icons.sh`.
- `plans/research/design.md` §1.3 (lines 76–95: font facts, the Quattro weight bug), §5 (374–467: token sketches),
  §8.2 (662–697: launcher icon vectors, **copy them**), §9 (700–717: icon usage).
- `plans/research/platform.md` §6 (497–526: edge-to-edge, `SystemBarAppearance` sketch), §10.4–10.6 (703–714:
  splash, launcher icon, configChanges).
- `plans/research/factcheck.md` §2 F1–F5 (lines 37–46: fonts/URLs/contrast confirmed) and C13 (fonts for Preview).

## Scope — In / Out

**In**
- Download and commit the 12 TTFs, the OFL licence and the 55 icons using the two verified scripts.
- Font-family XMLs `duo.xml`, `quattro.xml`, `mono.xml` with explicit weights.
- `ui/theme/`: `WriterColors.kt`, `Theme.kt`, `Fonts.kt`, `Tokens.kt`, `SystemBars.kt`, and the debug-only
  `DesignGallery.kt`.
- Adaptive launcher icon (background/foreground/monochrome), `android:icon`, platform splash and window
  background (light + night), and `colorAccent`/`colorControlActivated` = accent in the platform theme.
- `.editorconfig`: add the CompositionLocal allowlist (compose-rules requires it).
- `tools/PngPixel.java`: a tiny screenshot pixel sampler for objective colour checks (later UI tasks reuse it).
- Tests: JVM (tokens, mapping, resources), Robolectric (theme wiring) and instrumented (real font rendering).
- `MainActivity`: wrap the content in `MdWriterTheme`; debug shows `DesignGallery()`, release a themed placeholder.

**Out (owned by later tasks)**
- The editor widget, caret drawable, programmatic handle/selection colours → **T05**. **T05 also replaces
  `DesignGallery()` in `MainActivity`** with the editor host. Keep `DesignGallery.kt` as a debug tool.
- Span classes, `FontSet`, `EditorStyle`, `SpanFactory` → **T06**. T02 only provides `PlatformFonts`, colour tokens
  and `EditorMetrics` numbers for them.
- The selection pill → **T09**. Drawer/library UI → **T12**. Chrome glyphs, overflow menu and the status-bar
  protection strip → **T13**. Focus overlay → **T15**. Preview CSS/fonts in the WebView → **T16**.
- Persisting theme/font/size settings, the settings sheet, About + licence screen → **T19**. In T02,
  `MdWriterTheme` is called with its defaults (System, not pure black, Duo).
- No `core-splashscreen`, no `material-icons-*`, no AppCompat/MDC, no new dependencies (HARD RULE 1, README rule 3).
- No dynamic colour (Material You), ever (02 §2).

## Files to create / modify

| Path | Purpose |
|---|---|
| `app/src/main/res/font/{duo,quattro,mono}_{regular,bold,italic,bold_italic}.ttf` | 12 unmodified TTFs (fetch-fonts.sh) |
| `app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt` | OFL licence (fetch-fonts.sh); T19 shows it |
| `app/src/main/res/font/duo.xml`, `quattro.xml`, `mono.xml` | family XMLs with explicit `fontStyle`/`fontWeight` |
| `app/src/main/res/drawable/ic_<name>.xml` ×55 | Material Symbols Rounded (fetch-icons.sh) |
| `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | launcher layers (design.md §8.2) |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | adaptive icon with a `<monochrome>` layer |
| `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml` | `window_bg` (= `bg` token), `accent` |
| `app/src/main/res/values/themes.xml` (modify), `app/src/main/res/values-night/themes.xml` (new) | platform theme, splash, accent |
| `app/src/main/AndroidManifest.xml` (modify) | add `android:icon="@mipmap/ic_launcher"` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/WriterColors.kt` | `WriterColors`, 3 palettes, `LocalWriterColors` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/Theme.kt` | `ThemeMode`, `writerColorsFor`, `toMaterialColorScheme`, `WriterTheme`, `MdWriterTheme` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/Fonts.kt` | `WriterFont`, Compose `fontFamily`, `PlatformFaces`, `PlatformFonts` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/Tokens.kt` | `WidthClass`, `EditorMetrics`, `WriterDimens`, `WriterMotion`, `WriterTypography`, `hairline()` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/SystemBars.kt` | `SystemBarsAppearance(darkIcons)` |
| `app/src/main/kotlin/dev/mdwriter/ui/theme/DesignGallery.kt` | debug-only gallery screen |
| `app/src/main/kotlin/dev/mdwriter/MainActivity.kt` (modify) | `MdWriterTheme { if (BuildConfig.DEBUG) DesignGallery() else LaunchPlaceholder() }` |
| `.editorconfig` (modify) | `compose_allowed_composition_locals = LocalWriterColors,LocalWriterTypography` |
| `tools/PngPixel.java` | `java tools/PngPixel.java shot.png x y …` prints `#RRGGBB` |
| `app/src/test/kotlin/dev/mdwriter/ui/theme/WriterColorsTest.kt` | token table + focus-overlay alpha (JVM) |
| `app/src/test/kotlin/dev/mdwriter/ui/theme/ThemeResolutionTest.kt` | mode resolution + M3 mapping (JVM) |
| `app/src/test/kotlin/dev/mdwriter/ui/theme/TokensTest.kt` | editor metrics, width classes, type scale (JVM) |
| `app/src/test/kotlin/dev/mdwriter/ui/theme/DesignResourcesTest.kt` | font/licence/icon/launcher/theme resource files (JVM) |
| `app/src/test/kotlin/dev/mdwriter/ui/theme/MdWriterThemeTest.kt` | CompositionLocal + MaterialTheme wiring (Robolectric) |
| `app/src/androidTest/kotlin/dev/mdwriter/ui/theme/FontsDeviceTest.kt` | real font loading/rendering (instrumented) |
| `README.md` (modify) | one "Fonts" credit line (OFL, licence path) |

## Steps
1. **Baseline.** `make check` is green on the T01 tree. Start the emulator (`make emulator`, `make devices`).
2. **Fonts.** From the repo root run `bash plans/reference/scripts/fetch-fonts.sh`. It must print
   `fonts: 12 files, 1303688 bytes`. Then check `md5 -q app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt`
   = `24cd6c256d592d23fdc3ef640e05f0ed`. If a download fails, retry. If it still fails, **STOP-AND-ASK**. Never
   substitute other fonts and never modify or subset the files (02 §1, README rule 7).
3. **Icons.** Run `bash plans/reference/scripts/fetch-icons.sh`. It must print `icons: 55 requested, 55 present`
   (run it **before** step 5, otherwise the three `ic_launcher_*` files are counted too). Then
   `for f in app/src/main/res/drawable/ic_*.xml; do xmllint --noout "$f" || echo "BAD $f"; done` prints nothing.
4. **Family XMLs.** Write `res/font/duo.xml`, `quattro.xml` and `mono.xml` exactly as in `plans/reference/fonts.md`,
   replacing `duo` with the family name. Use `android:` attributes and declare weights **400/700 explicitly**:
   `iAWriterQuattroS-Bold` claims weight 400.
5. **Launcher icon.** Copy the verified XML out of design.md. Do not retype the path data:
   ```bash
   D=app/src/main/res/drawable; mkdir -p app/src/main/res/mipmap-anydpi
   sed -n '667,670p' plans/research/design.md > $D/ic_launcher_background.xml
   sed -n '674,682p' plans/research/design.md > $D/ic_launcher_foreground.xml
   sed 's/android:fillColor="#[0-9A-Fa-f]*"/android:fillColor="#FFFFFFFF"/' $D/ic_launcher_foreground.xml > $D/ic_launcher_monochrome.xml
   sed -n '688,692p' plans/research/design.md > app/src/main/res/mipmap-anydpi/ic_launcher.xml
   xmllint --noout $D/ic_launcher_*.xml app/src/main/res/mipmap-anydpi/ic_launcher.xml
   head -1 $D/ic_launcher_background.xml   # must start with <vector ; ic_launcher.xml must start with <adaptive-icon
   ```
   Add `android:icon="@mipmap/ic_launcher"` to `<application>` in the manifest. Do not add `roundIcon`.
6. **Platform theme + splash.** Write `values/colors.xml`, `values-night/colors.xml`, `values/themes.xml` and
   `values-night/themes.xml` (Reference code §F).
7. **Kotlin theme files.** Write `WriterColors.kt`, `Fonts.kt`, `Tokens.kt`, `Theme.kt` and `SystemBars.kt`
   (Reference code §A–§E). Names, signatures and values are the contract for later tasks. Keep them.
8. **Gallery + MainActivity.** Write `DesignGallery.kt` (§G) and update `MainActivity` (§H).
9. **`.editorconfig`.** Under the `[*.{kt,kts}]` section add
   `compose_allowed_composition_locals = LocalWriterColors,LocalWriterTypography`. The compose-rules check
   `compose:compositionlocal-allowlist` (editorconfig key verified in compose-rules 0.6.6) fails `spotlessCheck` on
   any other new `staticCompositionLocalOf`.
10. **Tests.** Write the five JVM/Robolectric test classes and the instrumented one (§I; assertions listed below).
11. **`tools/PngPixel.java`** (§J). **README:** add a short "Fonts" line under Project layout: "iA Writer Duo,
    Quattro, Mono by Information Architects Inc., SIL OFL 1.1 — see `app/src/main/assets/licenses/`".
12. `make format`, then `make check` (green; lint 0 errors).
13. **Device checks** (Verification commands): run `make test-device` first, because it may uninstall the debug app
    when it finishes. Then `make install-debug`, the light/dark screenshots with pixel samples, the
    no-recreation proof, the App info icon screenshot and the aapt2 monochrome check. Open every PNG you capture and
    look at it; compare against 02 §1–§2.
14. `adb -s <serial> shell cmd uimode night no` (leave the emulator in light mode). Write STATUS, then commit
    `T02: design system — theme, fonts, icons, launcher icon, splash`.

## Reference code
Everything in §A–§H, §J is a **sketch written for this plan** (not compiled). It must compile against Compose BOM
2026.09.00 / material3 1.4.0 without changes other than formatting. If an M3 named parameter doesn't exist, check the
real signature (IDE, or `javap` on the AAR) and keep the intent. The values (hex, dp, sp, ms) are copied from 02 and
**must not change**. §F XML and the launcher vectors from step 5 are verbatim.

### A. `WriterColors.kt` (02 §2, all 15 tokens, three palettes)
```kotlin
package dev.mdwriter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens of 02-design-spec §2. The View-based editor converts them with `toArgb()`. */
@Immutable
data class WriterColors(
    val bg: Color, val surface: Color, val surfaceHover: Color, val text: Color, val textSecondary: Color,
    val markup: Color, val focusDim: Color, val accent: Color, val selection: Color, val codeBg: Color,
    val highlightBg: Color, val highlightText: Color, val divider: Color, val scrim: Color, val danger: Color,
    val isDark: Boolean,
) {
    /** Focus Mode overlay alpha (02 §2): bg drawn at this alpha over text gives focusDim (grey channel). */
    val focusOverlayAlpha: Float get() = (focusDim.red - text.red) / (bg.red - text.red)
}

val LightWriterColors = WriterColors(
    bg = Color(0xFFF7F7F7), surface = Color(0xFFFCFCFC), surfaceHover = Color(0xFFEFEFEF),
    text = Color(0xFF1A1A1A), textSecondary = Color(0xFF6E6E6E), markup = Color(0xFF868686),
    focusDim = Color(0xFFC0C0C0), accent = Color(0xFF00B2FF), selection = Color(0x4000B2FF),
    codeBg = Color(0xFFEDEDED), highlightBg = Color(0x59FFD900), highlightText = Color(0xFF1A1A1A),
    divider = Color(0xFFE2E2E2), scrim = Color(0x52000000), danger = Color(0xFFD93A2B), isDark = false,
)
val DarkWriterColors = WriterColors(
    bg = Color(0xFF1A1A1A), surface = Color(0xFF141414), surfaceHover = Color(0xFF242424),
    text = Color(0xFFD0D0D0), textSecondary = Color(0xFF8C8C8C), markup = Color(0xFF7A7A7A),
    focusDim = Color(0xFF5E5E5E), accent = Color(0xFF00B2FF), selection = Color(0x4D00B2FF),
    codeBg = Color(0xFF242424), highlightBg = Color(0x2DFFD900), highlightText = Color(0xFFDAD094),
    divider = Color(0xFF2E2E2E), scrim = Color(0x99000000), danger = Color(0xFFFF6B5E), isDark = true,
)
val BlackWriterColors = WriterColors(
    bg = Color(0xFF000000), surface = Color(0xFF0A0A0A), surfaceHover = Color(0xFF161616),
    text = Color(0xFFC8C8C8), textSecondary = Color(0xFF8A8A8A), markup = Color(0xFF707070),
    focusDim = Color(0xFF4A4A4A), accent = Color(0xFF00B2FF), selection = Color(0x5900B2FF),
    codeBg = Color(0xFF141414), highlightBg = Color(0x2DFFD900), highlightText = Color(0xFFDAD094),
    divider = Color(0xFF1F1F1F), scrim = Color(0xB3000000), danger = Color(0xFFFF6B5E), isDark = true,
)

val LocalWriterColors = staticCompositionLocalOf { LightWriterColors }
```

### B. `Fonts.kt`
```kotlin
package dev.mdwriter.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.mdwriter.R

/** The three bundled families (02 §1). Editor + UI default: Duo. Code, tables, front matter: always Mono. */
enum class WriterFont(
    val familyRes: Int, val regularRes: Int, val italicRes: Int, val boldRes: Int, val boldItalicRes: Int,
) {
    Duo(R.font.duo, R.font.duo_regular, R.font.duo_italic, R.font.duo_bold, R.font.duo_bold_italic),
    Quattro(R.font.quattro, R.font.quattro_regular, R.font.quattro_italic, R.font.quattro_bold, R.font.quattro_bold_italic),
    Mono(R.font.mono, R.font.mono_regular, R.font.mono_italic, R.font.mono_bold, R.font.mono_bold_italic),
}

/** Compose family (identity-stable per font; safe as a remember key). Lists files: Compose can't read family XML. */
val WriterFont.fontFamily: FontFamily get() = composeFamilies.getValue(this)

private val composeFamilies: Map<WriterFont, FontFamily> =
    WriterFont.entries.associateWith { f ->
        FontFamily(
            Font(f.regularRes, FontWeight.Normal, FontStyle.Normal),
            Font(f.italicRes, FontWeight.Normal, FontStyle.Italic),
            Font(f.boldRes, FontWeight.Bold, FontStyle.Normal),
            Font(f.boldItalicRes, FontWeight.Bold, FontStyle.Italic),
        )
    }

/** The four static faces of one family as platform Typefaces, each loaded from its own TTF. */
class PlatformFaces(val regular: Typeface, val italic: Typeface, val bold: Typeface, val boldItalic: Typeface)

/**
 * Platform typefaces for the View-based editor. T06's FontSet = load(ctx, userFont) + load(ctx, Mono).
 * Faces come from the individual files (not family resolution), so span code can compare them by identity
 * and the Quattro-Bold weight metadata bug is irrelevant. Resources caches fonts; cheap to call again.
 */
object PlatformFonts {
    fun load(context: Context, font: WriterFont): PlatformFaces {
        val r = context.resources
        return PlatformFaces(r.getFont(font.regularRes), r.getFont(font.italicRes), r.getFont(font.boldRes), r.getFont(font.boldItalicRes))
    }

    /** Family typeface from res/font/<font>.xml; `Typeface.create(it, 700, false)` resolves the real Bold file. */
    fun family(context: Context, font: WriterFont): Typeface = context.resources.getFont(font.familyRes)
}
```

### C. `Tokens.kt` (numbers from 02 §3, §5–§11; names are the contract)
```kotlin
package dev.mdwriter.ui.theme
// imports: CubicBezierEasing, Easing, FastOutSlowInEasing, LinearOutSlowInEasing, Composable, Immutable,
// ReadOnlyComposable, staticCompositionLocalOf, LocalDensity, TextStyle, FontFamily, FontWeight, Dp, dp, sp

/** 02 §3 width buckets: phone < 600 dp ≤ medium < 840 dp ≤ expanded. */
enum class WidthClass {
    Compact, Medium, Expanded;

    companion object {
        const val MEDIUM_MIN_DP = 600f
        const val EXPANDED_MIN_DP = 840f

        fun fromWidthDp(widthDp: Float): WidthClass = when {
            widthDp >= EXPANDED_MIN_DP -> Expanded
            widthDp >= MEDIUM_MIN_DP -> Medium
            else -> Compact
        }
    }
}

/** Editor typography/layout (02 §3). Consumed by T05 (margins, room) and T06 (EditorStyle). */
object EditorMetrics {
    val textSizeStepsSp: List<Int> = listOf(15, 16, 17, 19, 21, 24) // XS S M L XL XXL
    const val DEFAULT_TEXT_SIZE_STEP = 2 // M
    const val LARGE_SCREEN_EXTRA_SP = 1 // +1 sp at >= 600 dp

    fun bodyTextSizeSp(step: Int, widthClass: WidthClass): Int =
        textSizeStepsSp[step.coerceIn(textSizeStepsSp.indices)] +
            if (widthClass == WidthClass.Compact) 0 else LARGE_SCREEN_EXTRA_SP

    fun linePitchMultiplier(font: WriterFont, widthClass: WidthClass): Float {
        val large = widthClass != WidthClass.Compact
        return when (font) {
            WriterFont.Quattro -> if (large) 1.65f else 1.55f
            WriterFont.Duo, WriterFont.Mono -> if (large) 1.75f else 1.65f
        }
    }

    /** Index = heading level (0 = body). H1 1.60, H2 1.40, H3 1.25, H4 1.10, H5 1.00, H6 1.00. */
    val headingScale: List<Float> = listOf(1.00f, 1.60f, 1.40f, 1.25f, 1.10f, 1.00f, 1.00f)
    const val NATURAL_LINE_BOX_EM = 1.30f // hhea 1025/-275 on UPM 1000
    const val N_WIDTH_EM = 0.6f // measure math: columnPx = measure * 0.6 * textSizePx

    fun sideMarginMin(w: WidthClass): Dp = when (w) { WidthClass.Compact -> 24.dp; WidthClass.Medium -> 32.dp; WidthClass.Expanded -> 48.dp }
    fun gutterChars(w: WidthClass): Int = when (w) { WidthClass.Compact -> 0; WidthClass.Medium -> 4; WidthClass.Expanded -> 6 }
    fun topRoom(w: WidthClass): Dp = when (w) { WidthClass.Compact -> 56.dp; WidthClass.Medium -> 64.dp; WidthClass.Expanded -> 72.dp }

    val measureCharsOptions: List<Int> = listOf(64, 72, 80)
    const val DEFAULT_MEASURE_CHARS = 64
    const val BOTTOM_ROOM_FRACTION = 0.50f // of window height
    const val TYPEWRITER_TOP_FRACTION = 0.45f
    const val TYPEWRITER_BOTTOM_FRACTION = 0.55f
    const val TYPEWRITER_CARET_FRACTION = 0.45f // caret line at 45 % of the visible height (02 §5)
    val caretWidth: Dp = 2.dp
    val caretCornerRadius: Dp = 1.dp
}

/** UI chrome dimensions (02 §5–§10). */
object WriterDimens {
    val touchTarget = 48.dp
    val icon = 24.dp
    fun chromeGlyphInset(w: WidthClass): Dp = when (w) { WidthClass.Compact -> 4.dp; WidthClass.Medium -> 8.dp; WidthClass.Expanded -> 12.dp }
    val chromeTapZone = 56.dp // tap in the top 56 dp shows the glyphs
    val chromeScrollUpThreshold = 24.dp
    const val STATUS_PROTECTION_ALPHA = 0.94f
    // library drawer (02 §7)
    val drawerMaxWidth = 360.dp
    val drawerEdgeGap = 56.dp // modal width = min(360 dp, screenWidth - 56 dp)
    val permanentPaneWidth = 320.dp
    val drawerHeaderHeight = 56.dp
    val fileRowHeight = 72.dp
    val folderRowHeight = 48.dp
    val locationRowHeight = 44.dp
    val rowPaddingHorizontal = 20.dp
    val folderIcon = 20.dp
    val activeFileBarWidth = 3.dp
    val searchFieldCornerRadius = 20.dp
    // selection pill (02 §6)
    val pillHeight = 48.dp
    val pillButton = 48.dp
    val pillCornerRadius = 24.dp
    val pillGapAboveSelection = 8.dp
    val pillGapBelowSelection = 28.dp // below the selection: leave room for the handles
    val pillScreenPadding = 16.dp // slots n = min(9, floor((width - 2 * 16 dp) / 48 dp))
    const val PILL_MAX_SLOTS = 9
    // menus, sheets, preview (02 §6, §8, §9, §10)
    val menuCornerRadius = 12.dp
    val overflowMenuWidth = 240.dp
    val sheetTopCornerRadius = 20.dp
    fun sheetPadding(w: WidthClass): Dp = if (w == WidthClass.Expanded) 32.dp else 24.dp
    val previewCodePadding = 12.dp
    val previewCodeCornerRadius = 6.dp
    val blockquoteRuleWidth = 2.dp
}

/** Motion (02 §11). Respect "Remove animations": Compose does automatically; Views use ValueAnimator.areAnimatorsEnabled(). */
object WriterMotion {
    const val CHROME_FADE_OUT_MS = 150
    const val CHROME_FADE_IN_MS = 220
    val chromeFadeOutEasing: Easing = LinearOutSlowInEasing
    val chromeFadeInEasing: Easing = FastOutSlowInEasing
    const val CHROME_IDLE_SHOW_DELAY_MS = 1_500L
    const val PILL_IN_MS = 120
    const val PILL_OUT_MS = 90
    const val PILL_IN_SCALE_FROM = 0.96f
    val pillInOffsetY = 8.dp
    const val PILL_RESHOW_DELAY_MS = 150L
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val PREVIEW_TRANSITION_MS = 250
    const val TYPEWRITER_RECENTER_MS = 150
}

/** UI text roles (02 §5, §7; design.md §5.2). Colour is NOT part of the style: pick it from WriterColors. */
@Immutable
data class WriterTypography(
    val body: TextStyle, // 17/28 — placeholder, gallery (the editor itself is a View)
    val drawerTitle: TextStyle, // "Library" 20/28 bold
    val rowTitle: TextStyle, // file/folder row title, settings row label 16/22
    val rowExcerpt: TextStyle, // excerpt, settings value, "Locations", breadcrumb 13/18
    val caption: TextStyle, // relative date 12/16
    val stats: TextStyle, // stats line 12/16, tabular figures
    val menuItem: TextStyle, // menu items 15/20
)

fun writerTypography(family: FontFamily): WriterTypography {
    fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = 0.sp)
    return WriterTypography(
        body = style(17, 28), drawerTitle = style(20, 28, FontWeight.Bold), rowTitle = style(16, 22),
        rowExcerpt = style(13, 18), caption = style(12, 16), stats = style(12, 16).copy(fontFeatureSettings = "tnum"),
        menuItem = style(15, 20),
    )
}

val LocalWriterTypography = staticCompositionLocalOf { writerTypography(FontFamily.Default) }

/** 1 physical pixel (02 §2 "1 px hairlines"; design.md §5.4). */
@Composable
@ReadOnlyComposable
fun hairline(): Dp = with(LocalDensity.current) { 1.toDp() }
```

### D. `Theme.kt` (02 §2 mapping; flat; no dynamic colour)
```kotlin
package dev.mdwriter.ui.theme
// imports: isSystemInDarkTheme, LocalTextSelectionColors, TextSelectionColors, ColorScheme, LocalContentColor,
// MaterialTheme, Typography, darkColorScheme, lightColorScheme, Composable, CompositionLocalProvider,
// ReadOnlyComposable, remember, Color, TextStyle

enum class ThemeMode { System, Light, Dark }

fun writerColorsFor(themeMode: ThemeMode, pureBlack: Boolean, systemDark: Boolean): WriterColors {
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    return when {
        !dark -> LightWriterColors
        pureBlack -> BlackWriterColors
        else -> DarkWriterColors
    }
}

/** 02 §2. surfaceTint = Transparent makes tonal elevation a no-op; containers the spec doesn't name use surfaceHover. */
fun WriterColors.toMaterialColorScheme(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent, onPrimary = Color.White, primaryContainer = surfaceHover, onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = text, onSecondary = bg, secondaryContainer = surfaceHover, onSecondaryContainer = text,
        tertiary = accent, onTertiary = Color.White, tertiaryContainer = surfaceHover, onTertiaryContainer = text,
        background = bg, onBackground = text,
        surface = surface, onSurface = text, surfaceVariant = surfaceHover, onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent, inverseSurface = text, inverseOnSurface = bg,
        error = danger, onError = Color.White, errorContainer = surfaceHover, onErrorContainer = danger,
        outline = divider, outlineVariant = divider, scrim = scrim,
        surfaceBright = surface, surfaceDim = surface, surfaceContainerLowest = surface, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerHighest = surface,
    )

fun WriterTypography.toMaterialTypography(): Typography {
    val base = Typography()
    val family = body.fontFamily
    fun TextStyle.inFamily() = copy(fontFamily = family)
    return base.copy(
        displayLarge = base.displayLarge.inFamily(), displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(), headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(), headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = drawerTitle, titleMedium = base.titleMedium.inFamily(), titleSmall = base.titleSmall.inFamily(),
        bodyLarge = rowTitle, bodyMedium = rowExcerpt, bodySmall = caption,
        labelLarge = menuItem, labelMedium = base.labelMedium.inFamily(), labelSmall = base.labelSmall.inFamily(),
    )
}

/** Accessors like MaterialTheme: `WriterTheme.colors.text`, `WriterTheme.typography.rowTitle`. */
object WriterTheme {
    val colors: WriterColors
        @Composable @ReadOnlyComposable
        get() = LocalWriterColors.current
    val typography: WriterTypography
        @Composable @ReadOnlyComposable
        get() = LocalWriterTypography.current
}

/** App theme. UI chrome uses the editor's family (02 §1). T19 feeds the three parameters from Settings. */
@Composable
fun MdWriterTheme(
    themeMode: ThemeMode = ThemeMode.System,
    pureBlack: Boolean = false,
    font: WriterFont = WriterFont.Duo,
    content: @Composable () -> Unit,
) {
    // isSystemInDarkTheme() reads LocalConfiguration: it updates on `cmd uimode night` WITHOUT recreation (uiMode is
    // in configChanges). XML theme attributes do NOT update then, so never read colours from resources.
    val colors = writerColorsFor(themeMode, pureBlack, systemDark = isSystemInDarkTheme())
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    val writerType = remember(font) { writerTypography(font.fontFamily) }
    val materialType = remember(writerType) { writerType.toMaterialTypography() }
    val selectionColors = remember(colors) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.selection) }
    SystemBarsAppearance(darkIcons = !colors.isDark)
    CompositionLocalProvider(LocalWriterColors provides colors, LocalWriterTypography provides writerType) {
        MaterialTheme(colorScheme = colorScheme, typography = materialType) {
            // inside MaterialTheme: M3 provides its own selection colours, ours must win
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColors,
                LocalContentColor provides colors.text,
                content = content,
            )
        }
    }
}
```

### E. `SystemBars.kt` (platform.md §6 sketch, adapted)
```kotlin
package dev.mdwriter.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Transparent bars from enableEdgeToEdge(); icon contrast follows the IN-APP theme (it may differ from the system). */
@Composable
fun SystemBarsAppearance(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = LocalActivity.current?.window ?: return
    SideEffect {
        window.isNavigationBarContrastEnforced = false // no translucent scrim on 3-button nav
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = darkIcons // light theme -> dark icons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
```

### F. Platform theme, splash, colours (verbatim; `values-night/themes.xml` is identical except the parent)
```xml
<!-- values/colors.xml -->
<resources>
    <!-- = the `bg` token (02 §2). Only the window/splash use it; Compose paints everything from WriterColors. -->
    <color name="window_bg">#FFF7F7F7</color>
    <color name="accent">#FF00B2FF</color>
</resources>
<!-- values-night/colors.xml -->
<resources>
    <color name="window_bg">#FF1A1A1A</color>
</resources>
<!-- values/themes.xml   (values-night: parent="android:Theme.Material.NoActionBar") -->
<resources>
    <style name="Theme.MdWriter" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_bg</item>
        <!-- Platform SplashScreen (API 31+): page colour + the adaptive launcher icon. No core-splashscreen. -->
        <item name="android:windowSplashScreenBackground">@color/window_bg</item>
        <!-- Platform EditText selection handles/cursor tint = accent (02 §5); T05 also sets its own caret. -->
        <item name="android:colorAccent">@color/accent</item>
        <item name="android:colorControlActivated">@color/accent</item>
    </style>
</resources>
```

### G. `DesignGallery.kt` (sketch; content matters, layout details are free)
`@Composable fun DesignGallery(modifier: Modifier = Modifier)`: one root `Column`, with
`modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState()).safeDrawingPadding()`
followed by `.padding(horizontal = EditorMetrics.sideMarginMin(WidthClass.Compact), vertical = 16.dp)`. The page
background must reach every screen edge (the pixel checks sample x = 10 px). Sections, top to bottom:
1. Title `"Design gallery · light|dark|black"` (`drawerTitle`, `text`). Get the palette name with
   `when (colors) { LightWriterColors -> "light"; DarkWriterColors -> "dark"; BlackWriterColors -> "black"; else -> "custom" }`.
2. **Token swatches** of the active palette: all 15 tokens in rows of 3. Each cell is a 40 dp tall box of the colour
   with a `hairline()` `divider` border, then the token name (`caption`, `text`), then `"#%08X".format(c.toArgb())`
   (`caption` in `WriterFont.Mono.fontFamily`, `textSecondary`).
3. **Palette comparison**: three 72 dp boxes (Light / Dark / Black), each on its own `bg`, containing "Aa" in its
   `text` colour plus a `EditorMetrics.caretWidth` × 24 dp `accent` bar.
4. **Font specimens** for each `WriterFont`: the name (`rowExcerpt`, `textSecondary`), four 17 sp lines
   `"Hamburgefonstiv 0123 mmm iii"` (Regular, Italic, Bold, Bold Italic), then `"H1 Heading"`…`"H6 Heading"`, bold,
   at `(17f * EditorMetrics.headingScale[level]).sp`. H6 is in `textSecondary`.
5. **Icons**: `Icon(painterResource(R.drawable.ic_left_panel_open / ic_more_vert / ic_format_bold / ic_format_italic /
   ic_link / ic_edit_square), null, tint = colors.text)` at `WriterDimens.icon`. Also a 72 dp `Box` holding
   `Image(painterResource(R.drawable.ic_launcher_background))` + `Image(…ic_launcher_foreground)`.
Private helpers each take `modifier: Modifier = Modifier` and have one root (compose-rules).

### H. `MainActivity.kt` change
```kotlin
setContent {
    MdWriterTheme {
        if (BuildConfig.DEBUG) DesignGallery() else LaunchPlaceholder() // T05 replaces this with the editor host
    }
}
// same file:
@Composable
private fun LaunchPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(WriterTheme.colors.bg), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.app_name), style = WriterTheme.typography.body, color = WriterTheme.colors.text)
    }
}
```
Keep `enableEdgeToEdge()` and the T01 `Log.d(TAG) { "onCreate …" }` line unchanged.

### I. Tests (what each asserts)
- **`WriterColorsTest`** (JVM, JUnit4 + Truth). For each palette, every token's `toArgb()` equals the 02 §2 hex. Write
  the table as literals in the test: it is the executable copy of the spec. `accent` is `0xFF00B2FF` in all three
  palettes. `LightWriterColors.highlightText == LightWriterColors.text`. `isDark` is false/true/true.
  `focusOverlayAlpha` is within 0.01 of 0.75 (light), 0.63 (dark) and 0.63 (black). And for every palette,
  `text.red + a * (bg.red - text.red)` is within 1/255 of `focusDim.red`.
- **`ThemeResolutionTest`** (JVM). `writerColorsFor` over all 12 combinations: Light always gives
  `LightWriterColors`; Dark gives Dark, or Black when `pureBlack`; System follows `systemDark`. For each palette,
  `toMaterialColorScheme()` maps: `background=bg`, `onBackground=onSurface=text`, `surface` and all 5
  `surfaceContainer*` = `surface`, `onSurfaceVariant=textSecondary`, `outline=outlineVariant=divider`,
  `primary=accent`, `onPrimary=Color.White`, `secondary=text`, `scrim=scrim`, `error=danger`,
  `surfaceTint=Color.Transparent`.
- **`TokensTest`** (JVM). `bodyTextSizeSp(2, Compact)=17`, `(2, Medium)=18`, `(2, Expanded)=18`, `(0, Compact)=15`,
  `(5, Expanded)=25`, out-of-range steps are clamped. `linePitchMultiplier`: Duo/Mono 1.65 compact and 1.75 medium;
  Quattro 1.55/1.65. `headingScale[1..6] = 1.60, 1.40, 1.25, 1.10, 1.00, 1.00`. `WidthClass.fromWidthDp`: 448 →
  Compact, 599.9 → Compact, 600 → Medium, 839.9 → Medium, 840 → Expanded. `gutterChars` 0/4/6, `sideMarginMin`
  24/32/48 dp, `topRoom` 56/64/72 dp. `writerTypography(FontFamily.Default)`: `drawerTitle` is 20 sp Bold, `stats`
  has `fontFeatureSettings == "tnum"`, `body` is 17 sp with a 28 sp line height.
- **`DesignResourcesTest`** (JVM; reads files, resolving `res = File("src/main/res")`, falling back to
  `File("app/src/main/res")`).
  (a) The `res/font/*.ttf` name → size map equals the 12-entry table in `plans/reference/fonts.md` (sum 1,303,688).
  (b) The licence is 4506 B, has MD5 `24cd6c256d592d23fdc3ef640e05f0ed` (via `java.security.MessageDigest`) and
  contains "Reserved Font Name".
  (c) Each family XML (DOM-parsed) has exactly the set {(`@font/<f>_regular`, normal, 400), (`_italic`, italic, 400),
  (`_bold`, normal, 700), (`_bold_italic`, italic, 700)}.
  (d) The `ic_*.xml` files other than `ic_launcher_*` are exactly the 55 names from `fetch-icons.sh`. Paste the list
  into the test. Each one parses and none contains `colorControlNormal`.
  (e) `mipmap-anydpi/ic_launcher.xml` is an `adaptive-icon` whose background/foreground/monochrome point at the three
  drawables. The monochrome layer's `pathData` list equals the foreground's, and all its `fillColor`s are
  `#FFFFFFFF`. The manifest `<application android:icon>` is `@mipmap/ic_launcher`.
  (f) In `values/` and `values-night/`, `themes.xml` has `android:windowSplashScreenBackground` =
  `@color/window_bg`. `colors.xml` `window_bg` equals `LightWriterColors.bg` / `DarkWriterColors.bg` (compare ARGB).
- **`MdWriterThemeTest`** (Robolectric: `@RunWith(AndroidJUnit4::class)`, `createComposeRule()` imported from
  `androidx.compose.ui.test.junit4.v2`). Capture `WriterTheme.colors` and `MaterialTheme.colorScheme.background`
  inside `setContent { MdWriterTheme(...) { … } }`, then assert in `runOnIdle`:
  Dark + pureBlack → `BlackWriterColors` and background `#000000`; Light + pureBlack → Light;
  `@Config(qualifiers = "night")` with System → Dark; `@Config(qualifiers = "notnight")` with System → Light.
  `font = WriterFont.Mono` → `WriterTheme.typography.body.fontFamily == WriterFont.Mono.fontFamily`.
- **`FontsDeviceTest`** (instrumented, `AndroidJUnit4`, `InstrumentationRegistry.getInstrumentation().targetContext`).
  Render text into an ARGB_8888 bitmap (2400 × 160, `Paint(ANTI_ALIAS_FLAG)`, `textSize = 100f`):
  1. For every font, `PlatformFonts.load` gives 4 distinct Typefaces whose renders of
     `"Hamburgefonstiv 0123 mmm iii"` differ pairwise (`!Bitmap.sameAs`).
  2. Advance widths (design.md §1.3): Mono `measureText("n")/textSize` = 0.60 ± 0.005 and `"mmmm"` = `"iiii"` ± 0.5 px;
     Duo `m/n` = 1.5 ± 0.01; Quattro `i/n` = 0.5 ± 0.01.
  3. For every font, bold ink (pixels with alpha > 127) is > 1.15 × regular ink.
  4. **Family weights:** for every font, `Typeface.create(PlatformFonts.family(ctx, f), w, italic)` renders
     `sameAs` the matching single-file face for (400,false), (700,false), (400,true) and (700,true). This is the
     regression check for the Quattro weight bug.

### J. `tools/PngPixel.java` (dev tool, run with the JDK's single-file launcher; not part of the build)
```java
import java.io.File;
import javax.imageio.ImageIO;

/** Usage: java -Djava.awt.headless=true tools/PngPixel.java shot.png x y [x y ...] -> "WxH", then "x,y #RRGGBB". */
public class PngPixel {
    public static void main(String[] a) throws Exception {
        var img = ImageIO.read(new File(a[0]));
        System.out.println(img.getWidth() + "x" + img.getHeight());
        for (int i = 1; i + 1 < a.length; i += 2) {
            int x = Integer.parseInt(a[i]), y = Integer.parseInt(a[i + 1]);
            System.out.printf("%d,%d #%06X%n", x, y, img.getRGB(x, y) & 0xFFFFFF);
        }
    }
}
```

## Acceptance criteria
1. `fetch-fonts.sh` output: `fonts: 12 files, 1303688 bytes`. `fetch-icons.sh` output: `icons: 55 requested, 55 present`.
   `DesignResourcesTest` passes (sizes, MD5, family weights, 55 tint-free icons, launcher layers, splash colours).
2. `make check` exits 0 (spotless, lint 0 errors, all JVM + Robolectric tests incl. `WriterColorsTest`,
   `ThemeResolutionTest`, `TokensTest`, `DesignResourcesTest`, `MdWriterThemeTest`, plus the release R8 build).
3. `make test-device DEVICE=emulator-5554` passes all 4 `FontsDeviceTest` tests.
4. Light screenshot (`cmd uimode night no`, debug gallery): `PngPixel` gives `#F7F7F7` at (10,10), (10,1500) and
   (10, H−10), where H is the screenshot height: edge-to-edge with no bar scrims. Status-bar icons are dark. The
   gallery shows 15 swatches, 3 palette boxes, 3 font specimens (Duo/Quattro/Mono; in Mono "mmm" and "iii" have equal
   width, in Duo "mmm" is visibly wider) and the icon row. H1 glyphs are visibly ≥ 1.5× the body line.
5. Dark screenshot after `cmd uimode night yes` (wait 2 s): `#1A1A1A` at the same three points, status-bar icons are
   light, the title reads "Design gallery · dark". **No recreation:** `pidof dev.mdwriter.debug` is unchanged, and
   `logcat -d -s MainActivity:D | grep -c onCreate` prints `1` (logcat cleared before the launch).
6. App info screenshot (`am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:dev.mdwriter.debug`)
   shows the new icon: off-white square, grey `#`, black heading bar, two text bars, blue caret. It is not the
   default Android robot. The label reads "mdwriter (debug)".
7. Themed icon: `aapt2 dump xmltree` of the debug APK's launcher XML (path from `unzip -l`) shows `E: adaptive-icon`
   with `background`, `foreground` **and** `monochrome` children. `aapt2 dump badging` shows an `application-icon-*`
   entry pointing at that XML. Optional: if you can turn on Themed icons (Wallpaper & style), screenshot the app
   drawer and describe it in STATUS.
8. `aapt2 dump badging` of the release APK still lists no permissions other than
   `dev.mdwriter.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (no dependency was added).
9. `git show --stat HEAD` lists the 12 TTFs, the licence, 55 + 3 drawables, the mipmap, and no build outputs.

## Verification commands
```bash
cd /Users/gcg/Work/src/github.com/gcg/mdwriter
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"; ADB=~/Library/Android/sdk/platform-tools/adb; BT=~/Library/Android/sdk/build-tools/36.0.0
PX() { "$JAVA_HOME/bin/java" -Djava.awt.headless=true tools/PngPixel.java "$@"; }
bash plans/reference/scripts/fetch-fonts.sh && bash plans/reference/scripts/fetch-icons.sh
make format && make check
make emulator && make devices                                  # serial assumed emulator-5554
make test-device DEVICE=emulator-5554                          # may uninstall the debug app afterwards
make install-debug DEVICE=emulator-5554
$ADB -s emulator-5554 shell cmd uimode night no
$ADB -s emulator-5554 shell am force-stop dev.mdwriter.debug && $ADB -s emulator-5554 logcat -c
make run-debug DEVICE=emulator-5554 && PID=$($ADB -s emulator-5554 shell pidof dev.mdwriter.debug)
$ADB -s emulator-5554 exec-out screencap -p > /tmp/t02-light.png
PX /tmp/t02-light.png 10 10 10 1500                            # + (10, H-10) using the printed WxH
$ADB -s emulator-5554 shell cmd uimode night yes; sleep 2       # (sleep is fine in your shell)
$ADB -s emulator-5554 exec-out screencap -p > /tmp/t02-dark.png
PX /tmp/t02-dark.png 10 10 10 1500
[ "$PID" = "$($ADB -s emulator-5554 shell pidof dev.mdwriter.debug)" ] && echo "same process"
$ADB -s emulator-5554 logcat -d -s MainActivity:D | grep -c onCreate   # expect 1
$ADB -s emulator-5554 shell cmd uimode night no
$ADB -s emulator-5554 shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:dev.mdwriter.debug
$ADB -s emulator-5554 exec-out screencap -p > /tmp/t02-appinfo.png
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep ic_launcher
$BT/aapt2 dump xmltree --file <path-from-unzip>/ic_launcher.xml app/build/outputs/apk/debug/app-debug.apk
$BT/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "application-icon|application-label:"
$BT/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```
Open `/tmp/t02-light.png`, `/tmp/t02-dark.png` and `/tmp/t02-appinfo.png` and describe them in STATUS.

## Pitfalls
- **Tint attribute (HARD RULE 1).** Material Symbols XML ships `android:tint="?attr/colorControlNormal"`, an
  AppCompat-only attribute. AAPT fails to link without AppCompat. The script strips it; never add AppCompat to "fix"
  it. The icons are white by default and `Icon(tint = …)` colours them.
- **Quattro Bold metadata** says weight 400. Always declare `android:fontWeight` in the family XML (FontsDeviceTest
  item 4 catches it). Compose cannot load a family XML: list the four files in `FontFamily(...)` (Fonts.kt does).
- **Fonts are byte-for-byte unmodified** (OFL "Modified Version" + Reserved Font Name). No subsetting, no
  `fonttools`, no re-encoding. Renaming the files is fine (done by the script).
- **uiMode is in `configChanges`**, so `cmd uimode night yes` does **not** recreate the activity. Resource-based
  colours (windowBackground, anything from `values-night`) keep their launch-time value. All visible colour must come
  from `WriterColors` via Compose. The same rule applies to the View editor later: T05/T06 push colours from Compose
  state, never from theme attributes (platform.md §10.6).
- **compose-rules**: new `staticCompositionLocalOf` declarations fail `compose:compositionlocal-allowlist` unless
  listed in `.editorconfig`. Public composables that emit UI need `modifier: Modifier = Modifier` as the first
  optional parameter. Composables that return a value must be lowerCamel (`hairline()`). Other rules to expect:
  one root emitter per composable, and `const val` names in SCREAMING_SNAKE_CASE (ktlint `property-naming`). Object
  and top-level `val`s may use any case.
- **M3 elevation**: `surfaceTint = Transparent` neutralises *tonal* elevation. It does not remove *shadows*. Later
  tasks must pass `shadowElevation = 0.dp` / `tonalElevation = 0.dp` to Surface, menus and sheets (02 §2 "Flat").
- **`accent` is never text** on light backgrounds (2.2:1 contrast, 02 §2). The gallery uses it only for the caret
  bar and swatches.
- **Splash**: no `core-splashscreen` (it pulls `appcompat-resources`). With dark `bg`, the system shows the adaptive
  icon on its own off-white background (enough contrast). Don't add `windowSplashScreenIconBackgroundColor` or an
  animated icon.
- **Launcher XML must not be retyped.** Use the `sed` ranges in step 5; `xmllint` must pass. `mipmap-anydpi`
  without `-v26` is correct at minSdk 36.
- **`make test-device`** installs and runs the debug + test APKs and may uninstall them afterwards. Reinstall with
  `make install-debug` before the screenshots. Emulator only, always `DEVICE=` (README rule 5).
- **JVM tests and `android.util.Log`**: plain JVM tests cannot call it ("not mocked"). Nothing in `ui/theme` logs.
- **Don't touch** `DIM_EMPHASIS_MARKERS`, span styling, or the editor. That is T06's job, and T02 only exposes numbers.

## Definition of done
- [ ] Acceptance criteria 1–9 verified. The evidence in STATUS includes the script outputs, test counts, pixel
      values, the PID/onCreate proof, and descriptions of the light/dark/app-info screenshots.
- [ ] `make check` green; `make test-device DEVICE=<emulator>` green.
- [ ] The emulator is left in light mode (`cmd uimode night no`).
- [ ] `plans/STATUS.md` entry `## T02 — Design system: theme, fonts, icons, launcher icon, splash — DONE — <date>`. Under
      "Notes for the next task" it lists: `WriterTheme.colors/typography`, `PlatformFonts.load`, `EditorMetrics`,
      `WriterDimens`, `WriterMotion`, `ThemeMode`/`WriterFont` living in `dev.mdwriter.ui.theme`, the `.editorconfig`
      allowlist, and `tools/PngPixel.java`.
- [ ] One commit `T02: design system — theme, fonts, icons, launcher icon, splash`. No build outputs, no keys. Not pushed.
