package dev.mdwriter.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the actual `res/` and `assets/` files T02 is responsible for and checks them against 02-design-spec and
 * `plans/reference/fonts.md`. No Android runtime needed: everything here is plain file/XML/digest work.
 */
class DesignResourcesTest {
    private val resDir: File by lazy {
        val fromModuleRoot = File("src/main/res")
        if (fromModuleRoot.isDirectory) fromModuleRoot else File("app/src/main/res")
    }
    private val assetsDir: File by lazy {
        val fromModuleRoot = File("src/main/assets")
        if (fromModuleRoot.isDirectory) fromModuleRoot else File("app/src/main/assets")
    }
    private val manifestFile: File by lazy {
        val fromModuleRoot = File("src/main/AndroidManifest.xml")
        if (fromModuleRoot.isFile) fromModuleRoot else File("app/src/main/AndroidManifest.xml")
    }

    private val expectedFontSizes =
        mapOf(
            "duo_regular.ttf" to 120696,
            "duo_bold.ttf" to 121460,
            "duo_italic.ttf" to 105824,
            "duo_bold_italic.ttf" to 105252,
            "quattro_regular.ttf" to 119772,
            "quattro_bold.ttf" to 120404,
            "quattro_italic.ttf" to 105028,
            "quattro_bold_italic.ttf" to 104520,
            "mono_regular.ttf" to 97044,
            "mono_bold.ttf" to 96168,
            "mono_italic.ttf" to 104120,
            "mono_bold_italic.ttf" to 103400,
        )

    private val expectedIconNames =
        listOf(
            "left_panel_open",
            "left_panel_close",
            "more_vert",
            "more_horiz",
            "arrow_back",
            "share",
            "undo",
            "redo",
            "search",
            "edit_square",
            "preview",
            "center_focus_strong",
            "tune",
            "format_bold",
            "format_italic",
            "format_h1",
            "format_h2",
            "format_h3",
            "format_h4",
            "format_h5",
            "format_h6",
            "notes",
            "link",
            "code",
            "code_blocks",
            "content_copy",
            "content_paste",
            "content_cut",
            "select_all",
            "format_quote",
            "format_list_bulleted",
            "format_list_numbered",
            "checklist",
            "format_strikethrough",
            "ink_highlighter",
            "format_clear",
            "close",
            "phone_android",
            "folder",
            "folder_open",
            "create_new_folder",
            "sort",
            "check",
            "chevron_right",
            "link_off",
            "drive_file_rename_outline",
            "drive_folder_upload",
            "delete",
            "check_box",
            "check_box_outline_blank",
            "info",
            "expand_less",
            "expand_more",
            "match_case",
            "find_replace",
        )

    private fun md5(file: File): String =
        MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun parseXml(file: File): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    // -------------------------------------------------------------- (a) TTFs
    @Test
    fun `all 12 font files exist with the exact sizes from fonts dot md`() {
        val fontDir = File(resDir, "font")
        val actual = expectedFontSizes.keys.associateWith { File(fontDir, it).length().toInt() }
        assertThat(actual).isEqualTo(expectedFontSizes)
        assertThat(expectedFontSizes.values.sum()).isEqualTo(1_303_688)
    }

    // -------------------------------------------------------------- (b) licence
    @Test
    fun `the OFL licence is present unmodified`() {
        val licence = File(assetsDir, "licenses/iA-Writer-fonts-OFL.txt")
        assertThat(licence.length()).isEqualTo(4506L)
        assertThat(md5(licence)).isEqualTo("24cd6c256d592d23fdc3ef640e05f0ed")
        assertThat(licence.readText()).contains("Reserved Font Name")
    }

    // -------------------------------------------------------------- (c) family XMLs
    @Test
    fun `family XMLs declare all four weight-style combinations explicitly`() {
        for (family in listOf("duo", "quattro", "mono")) {
            val root = parseXml(File(resDir, "font/$family.xml"))
            assertThat(root.tagName).isEqualTo("font-family")
            val fonts = root.getElementsByTagName("font")
            val actual =
                (0 until fonts.length)
                    .map { i ->
                        val e = fonts.item(i) as Element
                        Triple(
                            e.getAttribute("android:font"),
                            e.getAttribute("android:fontStyle"),
                            e.getAttribute("android:fontWeight"),
                        )
                    }.toSet()
            val expected =
                setOf(
                    Triple("@font/${family}_regular", "normal", "400"),
                    Triple("@font/${family}_italic", "italic", "400"),
                    Triple("@font/${family}_bold", "normal", "700"),
                    Triple("@font/${family}_bold_italic", "italic", "700"),
                )
            assertThat(actual).isEqualTo(expected)
        }
    }

    // -------------------------------------------------------------- (d) icons
    @Test
    fun `exactly the 55 spec'd icons are present, tint-free and well-formed`() {
        val drawableDir = File(resDir, "drawable")
        val actualNames =
            drawableDir
                .listFiles { f -> f.name.startsWith("ic_") && f.name.endsWith(".xml") }!!
                .map { it.name.removePrefix("ic_").removeSuffix(".xml") }
                .filter { !it.startsWith("launcher") }
                .toSet()
        assertThat(actualNames).isEqualTo(expectedIconNames.toSet())
        for (name in expectedIconNames) {
            val file = File(drawableDir, "ic_$name.xml")
            parseXml(file) // must parse without throwing
            assertThat(file.readText()).doesNotContain("colorControlNormal")
        }
    }

    // -------------------------------------------------------------- (e) launcher icon
    @Test
    fun `adaptive icon wires background foreground and monochrome`() {
        val drawableDir = File(resDir, "drawable")
        val adaptive = parseXml(File(resDir, "mipmap-anydpi/ic_launcher.xml"))
        assertThat(adaptive.tagName).isEqualTo("adaptive-icon")

        fun layerDrawable(tag: String): String =
            (adaptive.getElementsByTagName(tag).item(0) as Element).getAttribute("android:drawable")
        assertThat(layerDrawable("background")).isEqualTo("@drawable/ic_launcher_background")
        assertThat(layerDrawable("foreground")).isEqualTo("@drawable/ic_launcher_foreground")
        assertThat(layerDrawable("monochrome")).isEqualTo("@drawable/ic_launcher_monochrome")

        fun paths(file: File): List<String> {
            val root = parseXml(file)
            val nodes = root.getElementsByTagName("path")
            return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:pathData") }
        }

        fun fillColors(file: File): List<String> {
            val root = parseXml(file)
            val nodes = root.getElementsByTagName("path")
            return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:fillColor") }
        }
        val foreground = File(drawableDir, "ic_launcher_foreground.xml")
        val monochrome = File(drawableDir, "ic_launcher_monochrome.xml")
        assertThat(paths(monochrome)).isEqualTo(paths(foreground))
        assertThat(fillColors(monochrome)).isNotEmpty()
        assertThat(fillColors(monochrome).all { it == "#FFFFFFFF" }).isTrue()

        val manifestText = manifestFile.readText()
        assertThat(manifestText).contains("android:icon=\"@mipmap/ic_launcher\"")
    }

    // -------------------------------------------------------------- (f) theme + splash colours
    @Test
    fun `themes declare the splash background and colors match the token palettes`() {
        val lightTheme = File(resDir, "values/themes.xml").readText()
        val nightTheme = File(resDir, "values-night/themes.xml").readText()
        assertThat(lightTheme).contains("android:windowSplashScreenBackground\">@color/window_bg<")
        assertThat(nightTheme).contains("android:windowSplashScreenBackground\">@color/window_bg<")

        val lightColors = parseXml(File(resDir, "values/colors.xml"))
        val nightColors = parseXml(File(resDir, "values-night/colors.xml"))

        fun colorValue(
            root: Element,
            name: String,
        ): String? {
            val nodes = root.getElementsByTagName("color")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                if (e.getAttribute("name") == name) return e.textContent.trim()
            }
            return null
        }
        val lightWindowBg =
            colorValue(
                lightColors,
                "window_bg",
            )!!.removePrefix("#").let { java.lang.Long.parseLong(it, 16) }
        val nightWindowBg =
            colorValue(
                nightColors,
                "window_bg",
            )!!.removePrefix("#").let { java.lang.Long.parseLong(it, 16) }
        assertThat(lightWindowBg).isEqualTo(0xFFF7F7F7L)
        assertThat(nightWindowBg).isEqualTo(0xFF1A1A1AL)
    }
}
