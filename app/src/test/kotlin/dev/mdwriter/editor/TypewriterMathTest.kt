package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Task T15, Reference §C. */
class TypewriterMathTest {
    @Test
    fun targetScrollYCentresTheCaretLineAt45Percent() {
        assertThat(TypewriterMath.targetScrollY(0, 1000, 800, 5000)).isEqualTo(640)
    }

    @Test
    fun negativeResultClampsToZero() {
        assertThat(TypewriterMath.targetScrollY(0, 0, 800, 5000)).isEqualTo(0)
    }

    @Test
    fun resultAboveMaxClampsToMax() {
        assertThat(TypewriterMath.targetScrollY(0, 100_000, 800, 5000)).isEqualTo(5000)
    }

    @Test
    fun paddingsSplit45And55PercentOfWindowHeight() {
        assertThat(TypewriterMath.paddings(2000)).isEqualTo(900 to 1100)
    }
}
