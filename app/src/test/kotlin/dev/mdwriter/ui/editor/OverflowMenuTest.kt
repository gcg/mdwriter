package dev.mdwriter.ui.editor

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Acceptance 4. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1000dp-h1000dp")
class OverflowMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultFieldsShowUndoRedoAndNewNoteOnly() {
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(expanded = true, onDismiss = {}, actions = OverflowActions())
            }
        }
        composeRule.onNodeWithContentDescription("Undo").assertExists()
        composeRule.onNodeWithContentDescription("Redo").assertExists()
        composeRule.onNodeWithText("New note").assertExists()
        composeRule.onNodeWithText("Preview").assertDoesNotExist()
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Find").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
    }

    @Test
    fun undoDisabledWhenCanUndoIsFalse() {
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(expanded = true, onDismiss = {}, actions = OverflowActions(canUndo = false))
            }
        }
        composeRule.onNodeWithContentDescription("Undo").assertIsNotEnabled()
    }

    @Test
    fun clickingNewNoteCallsOnNewNoteOnceAndDismisses() {
        var newNoteCalls = 0
        var dismissCalls = 0
        composeRule.setContent {
            MdWriterTheme {
                OverflowMenu(
                    expanded = true,
                    onDismiss = { dismissCalls++ },
                    actions = OverflowActions(onNewNote = { newNoteCalls++ }),
                )
            }
        }
        composeRule.onNodeWithText("New note").performClick()
        assertThat(newNoteCalls).isEqualTo(1)
        assertThat(dismissCalls).isEqualTo(1)
    }
}
