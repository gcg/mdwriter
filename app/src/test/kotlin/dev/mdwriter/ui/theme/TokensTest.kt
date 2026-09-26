package dev.mdwriter.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokensTest {
    @Test
    fun `bodyTextSizeSp applies the plus-1sp large-screen rule`() {
        assertThat(EditorMetrics.bodyTextSizeSp(2, WidthClass.Compact)).isEqualTo(17)
        assertThat(EditorMetrics.bodyTextSizeSp(2, WidthClass.Medium)).isEqualTo(18)
        assertThat(EditorMetrics.bodyTextSizeSp(2, WidthClass.Expanded)).isEqualTo(18)
        assertThat(EditorMetrics.bodyTextSizeSp(0, WidthClass.Compact)).isEqualTo(15)
        assertThat(EditorMetrics.bodyTextSizeSp(5, WidthClass.Expanded)).isEqualTo(25)
    }

    @Test
    fun `bodyTextSizeSp clamps out-of-range steps`() {
        assertThat(EditorMetrics.bodyTextSizeSp(-1, WidthClass.Compact)).isEqualTo(15)
        assertThat(EditorMetrics.bodyTextSizeSp(99, WidthClass.Compact)).isEqualTo(24)
    }

    @Test
    fun `linePitchMultiplier matches 02 section 3`() {
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Duo, WidthClass.Compact)).isEqualTo(1.65f)
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Duo, WidthClass.Medium)).isEqualTo(1.75f)
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Mono, WidthClass.Compact)).isEqualTo(1.65f)
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Mono, WidthClass.Expanded)).isEqualTo(1.75f)
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Quattro, WidthClass.Compact)).isEqualTo(1.55f)
        assertThat(EditorMetrics.linePitchMultiplier(WriterFont.Quattro, WidthClass.Medium)).isEqualTo(1.65f)
    }

    @Test
    fun `headingScale matches 02 section 3`() {
        assertThat(EditorMetrics.headingScale[1]).isEqualTo(1.60f)
        assertThat(EditorMetrics.headingScale[2]).isEqualTo(1.40f)
        assertThat(EditorMetrics.headingScale[3]).isEqualTo(1.25f)
        assertThat(EditorMetrics.headingScale[4]).isEqualTo(1.10f)
        assertThat(EditorMetrics.headingScale[5]).isEqualTo(1.00f)
        assertThat(EditorMetrics.headingScale[6]).isEqualTo(1.00f)
    }

    @Test
    fun `WidthClass fromWidthDp uses the 600 and 840 dp breakpoints`() {
        assertThat(WidthClass.fromWidthDp(448f)).isEqualTo(WidthClass.Compact)
        assertThat(WidthClass.fromWidthDp(599.9f)).isEqualTo(WidthClass.Compact)
        assertThat(WidthClass.fromWidthDp(600f)).isEqualTo(WidthClass.Medium)
        assertThat(WidthClass.fromWidthDp(839.9f)).isEqualTo(WidthClass.Medium)
        assertThat(WidthClass.fromWidthDp(840f)).isEqualTo(WidthClass.Expanded)
    }

    @Test
    fun `gutterChars sideMarginMin and topRoom match 02 section 3`() {
        assertThat(EditorMetrics.gutterChars(WidthClass.Compact)).isEqualTo(0)
        assertThat(EditorMetrics.gutterChars(WidthClass.Medium)).isEqualTo(4)
        assertThat(EditorMetrics.gutterChars(WidthClass.Expanded)).isEqualTo(6)

        assertThat(EditorMetrics.sideMarginMin(WidthClass.Compact)).isEqualTo(24.dp)
        assertThat(EditorMetrics.sideMarginMin(WidthClass.Medium)).isEqualTo(32.dp)
        assertThat(EditorMetrics.sideMarginMin(WidthClass.Expanded)).isEqualTo(48.dp)

        assertThat(EditorMetrics.topRoom(WidthClass.Compact)).isEqualTo(56.dp)
        assertThat(EditorMetrics.topRoom(WidthClass.Medium)).isEqualTo(64.dp)
        assertThat(EditorMetrics.topRoom(WidthClass.Expanded)).isEqualTo(72.dp)
    }

    @Test
    fun `writerTypography matches the 02 spec type roles`() {
        val typography = writerTypography(FontFamily.Default)
        assertThat(typography.drawerTitle.fontSize).isEqualTo(20.sp)
        assertThat(typography.drawerTitle.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(typography.stats.fontFeatureSettings).isEqualTo("tnum")
        assertThat(typography.body.fontSize).isEqualTo(17.sp)
        assertThat(typography.body.lineHeight).isEqualTo(28.sp)
    }
}
