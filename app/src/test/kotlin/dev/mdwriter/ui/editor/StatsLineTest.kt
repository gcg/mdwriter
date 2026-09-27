package dev.mdwriter.ui.editor

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.markdown.TextStats
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Task T15, step 8. */
@RunWith(AndroidJUnit4::class)
class StatsLineTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sample = "Two sentences. Here! Right?"
    private val stats = TextStats.compute(sample, 0, sample.length, emptyList())

    @Test
    fun hiddenWhenStatsIsNull() {
        composeRule.setContent {
            MdWriterTheme {
                StatsLine(stats = null, isSelection = false, display = StatsDisplay.Words, typing = false, onCycle = {})
            }
        }
        composeRule.onNodeWithTag("statsLine").assertDoesNotExist()
    }

    @Test
    fun showsWordsThenCyclesOnTap() {
        var display = StatsDisplay.Words
        composeRule.setContent {
            MdWriterTheme {
                StatsLine(
                    stats = stats,
                    isSelection = false,
                    display = display,
                    typing = false,
                    onCycle = { display = display.next() },
                )
            }
        }
        composeRule.onNodeWithText("4 words · 1 min").assertExists()
        assertThat(display).isEqualTo(StatsDisplay.Words)
        composeRule.onNodeWithTag("statsLine").performClick()
        assertThat(display).isEqualTo(StatsDisplay.Characters)
    }

    @Test
    fun selectionShowsSelectedPrefix() {
        composeRule.setContent {
            MdWriterTheme {
                StatsLine(
                    stats = stats,
                    isSelection = true,
                    display = StatsDisplay.Words,
                    typing = false,
                    onCycle = {},
                )
            }
        }
        composeRule.onNodeWithText("Selected: 4 words").assertExists()
    }
}
