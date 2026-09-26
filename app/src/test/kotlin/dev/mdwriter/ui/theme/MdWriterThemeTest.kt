package dev.mdwriter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric: confirms MdWriterTheme wires WriterColors/WriterTypography and the M3 colour scheme correctly. */
@RunWith(AndroidJUnit4::class)
class MdWriterThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "notnight")
    fun `dark plus pureBlack gives the black palette`() {
        var colors: WriterColors? = null
        var background: Color? = null
        composeRule.setContent {
            MdWriterTheme(themeMode = ThemeMode.Dark, pureBlack = true) {
                colors = WriterTheme.colors
                background = MaterialTheme.colorScheme.background
            }
        }
        composeRule.runOnIdle {
            assertThat(colors).isEqualTo(BlackWriterColors)
            assertThat(background).isEqualTo(Color(0xFF000000))
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `light plus pureBlack still gives the light palette`() {
        var colors: WriterColors? = null
        composeRule.setContent {
            MdWriterTheme(themeMode = ThemeMode.Light, pureBlack = true) {
                colors = WriterTheme.colors
            }
        }
        composeRule.runOnIdle {
            assertThat(colors).isEqualTo(LightWriterColors)
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun `System theme mode follows a night qualifier`() {
        var colors: WriterColors? = null
        composeRule.setContent {
            MdWriterTheme(themeMode = ThemeMode.System) {
                colors = WriterTheme.colors
            }
        }
        composeRule.runOnIdle {
            assertThat(colors).isEqualTo(DarkWriterColors)
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `System theme mode follows a notnight qualifier`() {
        var colors: WriterColors? = null
        composeRule.setContent {
            MdWriterTheme(themeMode = ThemeMode.System) {
                colors = WriterTheme.colors
            }
        }
        composeRule.runOnIdle {
            assertThat(colors).isEqualTo(LightWriterColors)
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `font parameter changes the typography family`() {
        var bodyFamily: androidx.compose.ui.text.font.FontFamily? = null
        composeRule.setContent {
            MdWriterTheme(font = WriterFont.Mono) {
                bodyFamily = WriterTheme.typography.body.fontFamily
            }
        }
        composeRule.runOnIdle {
            assertThat(bodyFamily).isEqualTo(WriterFont.Mono.fontFamily)
        }
    }
}
