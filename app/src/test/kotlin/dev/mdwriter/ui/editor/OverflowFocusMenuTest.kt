package dev.mdwriter.ui.editor

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Task T15, step 9/Reference §E. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h1000dp")
class OverflowFocusMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingFocusThenSentenceCallsOnFocusModeOnceAndDismisses() {
        var selected: FocusModeKind? = null
        var dismissCalls = 0
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(
                    expanded = true,
                    onDismiss = { dismissCalls++ },
                    actions =
                        OverflowActions(
                            focus =
                                OverflowChoice(
                                    options = listOf("Off", "Sentence", "Paragraph"),
                                    selected = 0,
                                    onSelect = { i -> selected = FocusModeKind.entries[i] },
                                ),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Focus").performClick() // the header row: disabled, a no-op
        composeRule.onNodeWithText("Sentence").performClick()
        assertThat(selected).isEqualTo(FocusModeKind.Sentence)
        assertThat(dismissCalls).isEqualTo(1)
    }

    @Test
    fun clickingWordCountTogglesAndDoesNotDismiss() {
        var checked = false
        var dismissCalls = 0
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(
                    expanded = true,
                    onDismiss = { dismissCalls++ },
                    actions =
                        OverflowActions(
                            wordCount = OverflowToggle(checked = checked, onChange = { checked = it }),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Word count").performClick()
        assertThat(checked).isTrue()
        assertThat(dismissCalls).isEqualTo(0)
    }

    @Test
    fun checkedStateExposedThroughSemantics() {
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(
                    expanded = true,
                    onDismiss = {},
                    actions =
                        OverflowActions(
                            typewriter = OverflowToggle(checked = true, onChange = {}),
                            wordCount = OverflowToggle(checked = false, onChange = {}),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Typewriter scrolling").assertIsOn()
        composeRule.onNodeWithText("Word count").assertIsOff()
    }
}
