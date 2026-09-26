package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterFont
import org.junit.Test

/**
 * Pure-JVM table test for [EditorGeometry.compute] (Acceptance 1). density 1, `spToPx = identity`, so px == sp,
 * making the expected numbers hand-checkable against 02 §3.
 */
class EditorGeometryTest {
    private val identity: (Float) -> Float = { it }

    private fun geometry(
        widthDp: Int,
        font: WriterFont = WriterFont.Duo,
        measureChars: Int = 64,
        windowHeightPx: Int = 1000,
        topInsetPx: Int = 0,
    ) = EditorGeometry.compute(
        widthPx = widthDp,
        windowHeightPx = windowHeightPx,
        topInsetPx = topInsetPx,
        density = 1f,
        font = font,
        textSizeStep = 2,
        measureChars = measureChars,
        spToPx = identity,
    )

    @Test
    fun compact_360dp() {
        val g = geometry(360)
        assertThat(g.widthClass).isEqualTo(WidthClass.Compact)
        assertThat(g.textSizePx).isEqualTo(17f)
        assertThat(g.lineSpacingExtraPx).isWithin(0.01f).of(5.95f)
        assertThat(g.gutterPx).isEqualTo(0)
        assertThat(g.paddingStart).isEqualTo(24)
        assertThat(g.paddingEnd).isEqualTo(24)
        assertThat(g.paddingTop).isEqualTo(56)
        assertThat(g.paddingBottom).isEqualTo(500)
    }

    @Test
    fun compact_448dp_sameAs360() {
        val g360 = geometry(360)
        val g448 = geometry(448)
        assertThat(g448.widthClass).isEqualTo(g360.widthClass)
        assertThat(g448.textSizePx).isEqualTo(g360.textSizePx)
        assertThat(g448.lineSpacingExtraPx).isWithin(0.01f).of(g360.lineSpacingExtraPx)
        assertThat(g448.gutterPx).isEqualTo(g360.gutterPx)
        assertThat(g448.paddingStart).isEqualTo(g360.paddingStart)
        assertThat(g448.paddingEnd).isEqualTo(g360.paddingEnd)
        assertThat(g448.paddingTop).isEqualTo(g360.paddingTop)
        assertThat(g448.paddingBottom).isEqualTo(g360.paddingBottom)
    }

    @Test
    fun medium_600dp() {
        val g = geometry(600)
        assertThat(g.widthClass).isEqualTo(WidthClass.Medium)
        assertThat(g.textSizePx).isEqualTo(18f)
        assertThat(g.lineSpacingExtraPx).isWithin(0.01f).of(8.10f)
        assertThat(g.gutterPx).isEqualTo(32)
        assertThat(g.paddingStart).isEqualTo(0)
        assertThat(g.paddingEnd).isEqualTo(32)
        assertThat(g.paddingTop).isEqualTo(64)
    }

    @Test
    fun expanded_840dp() {
        val g = geometry(840)
        assertThat(g.widthClass).isEqualTo(WidthClass.Expanded)
        assertThat(g.gutterPx).isEqualTo(65)
        assertThat(g.paddingStart).isEqualTo(10)
        assertThat(g.paddingEnd).isEqualTo(74)
        assertThat(g.paddingTop).isEqualTo(72)
    }

    @Test
    fun expanded_1280dp() {
        val g = geometry(1280)
        assertThat(g.gutterPx).isEqualTo(65)
        assertThat(g.paddingStart).isEqualTo(230)
        assertThat(g.paddingEnd).isEqualTo(294)
    }

    @Test
    fun expanded_1280dp_measure80() {
        val g = geometry(1280, measureChars = 80)
        assertThat(g.paddingEnd).isEqualTo(208)
    }

    @Test
    fun quattro_448dp_extraSpacing() {
        val g = geometry(448, font = WriterFont.Quattro)
        // extra = 1.55 * 17 - 22.1 = 4.25
        assertThat(g.lineSpacingExtraPx).isWithin(0.01f).of(4.25f)
    }

    @Test
    fun bottomRoom_dependsOnlyOnWindowHeight() {
        val short = geometry(360, windowHeightPx = 1000)
        val tall = geometry(1280, windowHeightPx = 1000, measureChars = 80, topInsetPx = 40)
        assertThat(short.paddingBottom).isEqualTo(tall.paddingBottom)
        assertThat(geometry(360, windowHeightPx = 2000).paddingBottom).isEqualTo(1000)
    }
}
