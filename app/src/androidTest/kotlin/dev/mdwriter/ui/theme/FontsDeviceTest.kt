package dev.mdwriter.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented: real font loading and rendering on-device (JVM tests fake `Log`/resources for fonts too poorly). */
@RunWith(AndroidJUnit4::class)
class FontsDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sampleText = "Hamburgefonstiv 0123 mmm iii"

    private fun render(
        typeface: Typeface,
        textSize: Float = 100f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(2400, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                color = Color.BLACK
            }
        canvas.drawText(sampleText, 10f, 120f, paint)
        return bitmap
    }

    private fun inkPixelCount(bitmap: Bitmap): Int {
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (p in pixels) if (Color.alpha(p) > 127) count++
        return count
    }

    @Test
    fun facesRenderDifferently() {
        for (font in WriterFont.entries) {
            val faces = PlatformFonts.load(context, font)
            val bitmaps = listOf(faces.regular, faces.italic, faces.bold, faces.boldItalic).map { render(it) }
            for (i in bitmaps.indices) {
                for (j in i + 1 until bitmaps.size) {
                    assertThat(bitmaps[i].sameAs(bitmaps[j])).isFalse()
                }
            }
        }
    }

    @Test
    fun advanceWidthsMatchDesignFacts() {
        val mono = PlatformFonts.load(context, WriterFont.Mono)
        val monoPaint =
            Paint().apply {
                typeface = mono.regular
                textSize = 100f
            }
        assertThat(monoPaint.measureText("n") / 100f).isWithin(0.005f).of(0.60f)
        assertThat(monoPaint.measureText("mmmm")).isWithin(0.5f).of(monoPaint.measureText("iiii"))

        val duo = PlatformFonts.load(context, WriterFont.Duo)
        val duoPaint =
            Paint().apply {
                typeface = duo.regular
                textSize = 100f
            }
        val duoN = duoPaint.measureText("n")
        val duoM = duoPaint.measureText("m")
        assertThat(duoM / duoN).isWithin(0.01f).of(1.5f)

        val quattro = PlatformFonts.load(context, WriterFont.Quattro)
        val quattroPaint =
            Paint().apply {
                typeface = quattro.regular
                textSize = 100f
            }
        val quattroN = quattroPaint.measureText("n")
        val quattroI = quattroPaint.measureText("i")
        assertThat(quattroI / quattroN).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun boldInkIsHeavierThanRegular() {
        for (font in WriterFont.entries) {
            val faces = PlatformFonts.load(context, font)
            val regularInk = inkPixelCount(render(faces.regular))
            val boldInk = inkPixelCount(render(faces.bold))
            assertThat(boldInk.toFloat()).isGreaterThan(regularInk * 1.15f)
        }
    }

    @Test
    fun familyResolvesTheSameFacesAsTheIndividualFiles() {
        for (font in WriterFont.entries) {
            val faces = PlatformFonts.load(context, font)
            val family = PlatformFonts.family(context, font)
            val cases =
                listOf(
                    Triple(400, false, faces.regular),
                    Triple(700, false, faces.bold),
                    Triple(400, true, faces.italic),
                    Triple(700, true, faces.boldItalic),
                )
            for ((weight, italic, expectedFace) in cases) {
                val resolved = Typeface.create(family, weight, italic)
                assertThat(render(resolved).sameAs(render(expectedFace))).isTrue()
            }
        }
    }
}
