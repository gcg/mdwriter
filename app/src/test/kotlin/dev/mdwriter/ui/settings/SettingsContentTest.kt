package dev.mdwriter.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.ui.theme.MdWriterTheme
import dev.mdwriter.ui.theme.ThemeMode
import dev.mdwriter.ui.theme.WriterFont
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w700dp-h3000dp")
class SettingsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private var settings by mutableStateOf(Settings())
    private var updates = 0
    private var exported = 0
    private var about = 0

    private fun show(showLineLength: Boolean = true) {
        rule.setContent {
            MdWriterTheme {
                SettingsContent(
                    settings = settings,
                    showLineLength = showLineLength,
                    onUpdate = {
                        updates++
                        settings = it(settings)
                    },
                    onExportAll = { exported++ },
                    onAbout = { about++ },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    @Test
    fun allGroupsAndRowsExist() {
        show()
        listOf("Appearance", "Text", "Writing", "Files", "About").forEach {
            rule.onNodeWithText(it).assertExists()
        }
        listOf(
            "Theme",
            "Pure black in dark",
            "Typeface",
            "Text size",
            "Line length",
            "Focus",
            "Typewriter scrolling",
            "Word count",
            "Swipe to library & preview",
            "==highlight== syntax",
            "New note extension",
            "Show file extensions",
            "Export all notes…",
            "About mdwriter",
        ).forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun lineLengthHiddenOnPhones() {
        show(showLineLength = false)
        rule.onNodeWithText("Line length").assertDoesNotExist()
    }

    @Test
    fun themeDark() {
        show()
        rule.onNodeWithText("Dark").performScrollTo().performClick()
        assertThat(settings.themeMode).isEqualTo(ThemeMode.Dark)
        rule.onNodeWithText("Dark").assertIsSelected()
    }

    @Test
    fun pureBlackRow() {
        show()
        rule.onNodeWithText("Pure black in dark").performScrollTo().performClick()
        assertThat(settings.pureBlack).isTrue()
    }

    @Test
    fun typefaceMono() {
        show()
        rule.onNodeWithText("Mono").performScrollTo().performClick()
        assertThat(settings.typeface).isEqualTo(WriterFont.Mono)
    }

    @Test
    fun sliderPersistsOnce() {
        show()
        rule
            .onNode(
                SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
            ).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) {
                it(5f)
            }
        rule.waitForIdle()
        assertThat(settings.textSizeStep).isEqualTo(5)
        assertThat(updates).isEqualTo(1)
    }

    @Test
    fun txtExtension() {
        show()
        rule.onNodeWithText(".txt").performScrollTo().performClick()
        assertThat(settings.newNoteExtension).isEqualTo("txt")
    }

    @Test
    fun actionRowsFire() {
        show()
        rule.onNodeWithText("Export all notes…").performScrollTo().performClick()
        rule.onNodeWithText("About mdwriter").performScrollTo().performClick()
        assertThat(exported).isEqualTo(1)
        assertThat(about).isEqualTo(1)
    }
}
