package dev.mdwriter.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mdwriter.BuildConfig
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutContentTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsVersionAndFontCredit() {
        rule.setContent { MdWriterTheme { AboutContent() } }
        rule.onNodeWithText(BuildConfig.VERSION_NAME, substring = true).assertExists()
        rule.onAllNodesWithText("SIL Open Font License 1.1", substring = true).assertCountEquals(2)
    }

    @Test
    fun tappingCommonmarkShowsLicence() {
        rule.setContent { MdWriterTheme { AboutContent() } }
        rule.onNodeWithText("commonmark-java").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTextCount("Redistribution and use in source and binary forms") > 0
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String) =
        onAllNodes(
            androidx.compose.ui.test
                .hasText(t, substring = true),
        ).fetchSemanticsNodes().size
}
