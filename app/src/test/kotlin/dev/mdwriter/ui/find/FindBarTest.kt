package dev.mdwriter.ui.find

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.FindResult
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric + Compose (T17 step 7): the stateless [FindBar] with recording lambdas — no
 * [dev.mdwriter.editor.EditorController] involved (that wiring is [FindBarHost], covered by the on-device
 * `FindDeviceTest` and manual verification instead).
 *
 * `w1000dp-h1000dp`: Robolectric's default emulated screen is narrower than the widest `widthDp` box requested
 * below, which would silently clamp it (same fix `FormatToolbarTest` uses) — overridden per-test for the 360 dp
 * narrow-width case.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h1000dp")
class FindBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        query: String = "the",
        result: FindResult = FindResult(3, 0, false),
        matchCase: Boolean = false,
        readOnly: Boolean = false,
        replaceOpen: Boolean = false,
        widthDp: Int = 448,
        onNext: () -> Unit = {},
        onPrevious: () -> Unit = {},
        onClose: () -> Unit = {},
        onMatchCase: (Boolean) -> Unit = {},
        onReplaceToggle: () -> Unit = {},
        onReplaceOne: () -> Unit = {},
        onReplaceAll: () -> Unit = {},
        replacedMessage: String? = null,
    ) {
        composeRule.setContent {
            var q by remember { mutableStateOf(TextFieldValue(query, TextRange(0, query.length))) }
            var replaceIsOpen by remember { mutableStateOf(replaceOpen) }
            var replacement by remember { mutableStateOf(TextFieldValue("")) }
            val focusRequester = remember { FocusRequester() }
            MdWriterTheme {
                Box(Modifier.size(widthDp.dp, 400.dp)) {
                    FindBar(
                        query = q,
                        onQueryChange = { q = it },
                        matchCase = matchCase,
                        onMatchCase = onMatchCase,
                        result = result,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onClose = onClose,
                        readOnly = readOnly,
                        replaceOpen = replaceIsOpen,
                        onReplaceToggle = {
                            replaceIsOpen = !replaceIsOpen
                            onReplaceToggle()
                        },
                        replacement = replacement,
                        onReplacementChange = { replacement = it },
                        onReplaceOne = onReplaceOne,
                        onReplaceAll = onReplaceAll,
                        queryFocusRequester = focusRequester,
                        replacedMessage = replacedMessage,
                    )
                }
            }
        }
    }

    @Test
    fun `counter shows index and count for a non-empty query`() {
        setContent(query = "the", result = FindResult(17, 2, false))
        composeRule.onNodeWithTag("find.counter").assertExists()
        composeRule.onNodeWithText("3 / 17").assertExists()
    }

    @Test
    fun `counter shows a plus when truncated`() {
        setContent(query = "the", result = FindResult(100_000, 2, true))
        composeRule.onNodeWithText("3 / 100000+").assertExists()
    }

    @Test
    fun `counter does not exist for an empty query`() {
        setContent(query = "", result = FindResult.NONE)
        composeRule.onNodeWithTag("find.counter").assertDoesNotExist()
    }

    @Test
    fun `zero hits on a non-empty query shows 0 slash 0`() {
        setContent(query = "zzz", result = FindResult.NONE)
        composeRule.onNodeWithText("0 / 0").assertExists()
    }

    @Test
    fun `enter on the query field calls onNext`() {
        var calls = 0
        setContent(onNext = { calls++ })
        composeRule.onNodeWithTag("find.query").requestFocus()
        composeRule.onNodeWithTag("find.query").performKeyInput { pressKey(Key.Enter) }
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `shift enter on the query field calls onPrevious`() {
        var calls = 0
        setContent(onPrevious = { calls++ })
        composeRule.onNodeWithTag("find.query").requestFocus()
        composeRule.onNodeWithTag("find.query").performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) }
        }
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `escape on the query field calls onClose`() {
        var calls = 0
        setContent(onClose = { calls++ })
        composeRule.onNodeWithTag("find.query").requestFocus()
        composeRule.onNodeWithTag("find.query").performKeyInput { pressKey(Key.Escape) }
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `ime search action calls onNext`() {
        var calls = 0
        setContent(onNext = { calls++ })
        composeRule.onNodeWithTag("find.query").performImeAction()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `previous and next are disabled when the count is zero`() {
        setContent(result = FindResult.NONE)
        composeRule.onNodeWithContentDescription("Previous match").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next match").assertIsNotEnabled()
    }

    @Test
    fun `previous and next are enabled when there are matches`() {
        setContent(result = FindResult(3, 0, false))
        composeRule.onNodeWithContentDescription("Previous match").assertExists()
        composeRule.onNodeWithContentDescription("Next match").assertExists()
    }

    @Test
    fun `clicking match case calls onMatchCase true`() {
        var recorded: Boolean? = null
        setContent(matchCase = false, onMatchCase = { recorded = it })
        composeRule.onNodeWithContentDescription("Match case").performClick()
        assertThat(recorded).isEqualTo(true)
    }

    @Test
    fun `match case shows as on when true`() {
        setContent(matchCase = true)
        composeRule.onNodeWithContentDescription("Match case").assertIsOn()
    }

    @Test
    fun `match case shows as off when false`() {
        setContent(matchCase = false)
        composeRule.onNodeWithContentDescription("Match case").assertIsOff()
    }

    @Test
    fun `replace toggle shows the replace field and buttons`() {
        setContent(replaceOpen = true)
        composeRule.onNodeWithTag("find.replace").assertExists()
        composeRule.onNodeWithText("Replace").assertExists()
        composeRule.onNodeWithText("All").assertExists()
    }

    @Test
    fun `replace toggle does not exist when read-only`() {
        setContent(readOnly = true)
        composeRule.onNodeWithContentDescription("Replace").assertDoesNotExist()
        composeRule.onNodeWithTag("find.replace").assertDoesNotExist()
    }

    @Test
    fun `every icon button has a content description`() {
        setContent(replaceOpen = true)
        composeRule.onNodeWithContentDescription("Previous match").assertExists()
        composeRule.onNodeWithContentDescription("Next match").assertExists()
        composeRule.onNodeWithContentDescription("Close find").assertExists()
        composeRule.onNodeWithContentDescription("Match case").assertExists()
        composeRule.onNodeWithContentDescription("Replace").assertExists()
        composeRule.onNodeWithContentDescription("Replace all").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun `at 360dp the query field is at least as wide as the Replace button`() {
        setContent(widthDp = 360, replaceOpen = true)
        composeRule.onNodeWithTag("find.query").assertWidthIsAtLeast(120.dp)
        composeRule.onNodeWithTag("find.replace").assertWidthIsAtLeast(96.dp)
    }
}
