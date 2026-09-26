package dev.mdwriter.ui.toolbar

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.SelectionState
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose (T09 AC3): [FormatToolbarOverlay] slot contents, enablement and visibility.
 *
 * `w1000dp-h1000dp`: Robolectric's default emulated screen (320x470 px) is narrower than the 448 dp test box
 * itself, which would silently clamp every width in this file to ~305 dp regardless of the `Modifier.size(...)`
 * requested below — a wide qualifier gives the root enough real estate that the requested box size is honoured.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h1000dp")
class FormatToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fakeSelection = SelectionState(true, RectF(100f, 400f, 300f, 440f), 5, 9)

    private fun setContent(
        widthDp: Int = 448,
        highlightEnabled: Boolean = false,
        readOnly: Boolean = false,
        canPaste: () -> Boolean = { true },
        visible: Boolean = true,
        onAction: (ToolbarAction) -> Unit = {},
    ) {
        val state = if (visible) fakeSelection else SelectionState.Hidden
        composeRule.setContent {
            MdWriterTheme {
                Box(Modifier.size(widthDp.dp, 800.dp)) {
                    FormatToolbarOverlay(
                        state = state,
                        highlightEnabled = highlightEnabled,
                        readOnly = readOnly,
                        canPaste = canPaste,
                        onAction = onAction,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `at 448dp shows the full formatting and clipboard groups, Code is overflowed`() {
        var recorded: ToolbarAction? = null
        setContent(widthDp = 448, onAction = { recorded = it })

        composeRule.onNodeWithContentDescription("Bold").assertExists()
        composeRule.onNodeWithContentDescription("Italic").assertExists()
        composeRule.onNodeWithContentDescription("Heading").assertExists()
        composeRule.onNodeWithContentDescription("Link").assertExists()
        composeRule.onNodeWithContentDescription("Cut").assertExists()
        composeRule.onNodeWithContentDescription("Copy").assertExists()
        composeRule.onNodeWithContentDescription("Paste").assertExists()
        composeRule.onNodeWithContentDescription("More formatting").assertExists()
        composeRule.onNodeWithContentDescription("Code").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Bold").performClick()
        composeRule.runOnIdle { assertThat(recorded).isEqualTo(ToolbarAction.Bold) }
    }

    @Test
    fun `Paste is disabled when the clipboard has no text, More lists Code and Strikethrough`() {
        setContent(widthDp = 448, highlightEnabled = false, canPaste = { false })

        composeRule.onNodeWithContentDescription("Paste").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("More formatting").performClick()
        composeRule.onNodeWithText("Code").assertExists()
        composeRule.onNodeWithText("Strikethrough").assertExists()
        composeRule.onNodeWithText("Highlight").assertDoesNotExist()
    }

    @Test
    fun `Highlight only appears in More when highlighting is enabled`() {
        setContent(widthDp = 448, highlightEnabled = true)
        composeRule.onNodeWithContentDescription("More formatting").performClick()
        composeRule.onNodeWithText("Highlight").assertExists()
    }

    @Test
    fun `at 360dp only 5 buttons plus More are shown, More lists Paste Cut Code first`() {
        setContent(widthDp = 360)

        composeRule.onNodeWithContentDescription("Bold").assertExists()
        composeRule.onNodeWithContentDescription("Italic").assertExists()
        composeRule.onNodeWithContentDescription("Heading").assertExists()
        composeRule.onNodeWithContentDescription("Link").assertExists()
        composeRule.onNodeWithContentDescription("Copy").assertExists()
        composeRule.onNodeWithContentDescription("More formatting").assertExists()
        composeRule.onNodeWithContentDescription("Cut").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Paste").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Code").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("More formatting").performClick()
        composeRule.onNodeWithText("Paste").assertExists()
        composeRule.onNodeWithText("Cut").assertExists()
        composeRule.onNodeWithText("Code").assertExists()
        composeRule.onNodeWithText("Strikethrough").assertExists()

        // "first": the overflow group (Paste/Cut/Code) sits above the More-entries group (Strikethrough...).
        val pasteTop =
            composeRule
                .onNodeWithText("Paste")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val strikeTop =
            composeRule
                .onNodeWithText("Strikethrough")
                .fetchSemanticsNode()
                .boundsInRoot.top
        assertThat(pasteTop).isLessThan(strikeTop)
    }

    @Test
    fun `hidden state removes the pill after it settles`() {
        setContent(visible = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()
    }
}
