package dev.mdwriter.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mdwriter.R

/**
 * Debug-only screen showing the active palette's tokens, the three palettes side by side, the three bundled
 * fonts and a sample of icons. Not shown in release builds (MainActivity shows [LaunchPlaceholder] instead).
 */
@Composable
fun DesignGallery(modifier: Modifier = Modifier) {
    val colors = WriterTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = EditorMetrics.sideMarginMin(WidthClass.Compact), vertical = 16.dp),
    ) {
        GalleryTitle(colors = colors)
        TokenSwatches(colors = colors, modifier = Modifier.padding(top = 24.dp))
        PaletteComparison(modifier = Modifier.padding(top = 24.dp))
        FontSpecimens(modifier = Modifier.padding(top = 24.dp))
        IconsRow(colors = colors, modifier = Modifier.padding(top = 24.dp, bottom = 24.dp))
    }
}

private fun paletteName(colors: WriterColors): String =
    when (colors) {
        LightWriterColors -> "light"
        DarkWriterColors -> "dark"
        BlackWriterColors -> "black"
        else -> "custom"
    }

private fun tokenEntries(colors: WriterColors): List<Pair<String, Color>> =
    listOf(
        "bg" to colors.bg,
        "surface" to colors.surface,
        "surfaceHover" to colors.surfaceHover,
        "text" to colors.text,
        "textSecondary" to colors.textSecondary,
        "markup" to colors.markup,
        "focusDim" to colors.focusDim,
        "accent" to colors.accent,
        "selection" to colors.selection,
        "codeBg" to colors.codeBg,
        "highlightBg" to colors.highlightBg,
        "highlightText" to colors.highlightText,
        "divider" to colors.divider,
        "scrim" to colors.scrim,
        "danger" to colors.danger,
    )

@Composable
private fun GalleryTitle(
    colors: WriterColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Design gallery · ${paletteName(colors)}",
        style = WriterTheme.typography.drawerTitle,
        color = colors.text,
        modifier = modifier,
    )
}

@Composable
private fun TokenSwatches(
    colors: WriterColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        tokenEntries(colors).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) ->
                    TokenSwatch(name = name, color = color, colors = colors, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TokenSwatch(
    name: String,
    color: Color,
    colors: WriterColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color)
                .border(hairline(), colors.divider),
        )
        Text(text = name, style = WriterTheme.typography.caption, color = colors.text)
        Text(
            text = "#%08X".format(color.toArgb()),
            style = WriterTheme.typography.caption,
            color = colors.textSecondary,
            fontFamily = WriterFont.Mono.fontFamily,
        )
    }
}

@Composable
private fun PaletteComparison(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(LightWriterColors, DarkWriterColors, BlackWriterColors).forEach { palette ->
            PaletteBox(palette = palette, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaletteBox(
    palette: WriterColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(72.dp)
            .background(palette.bg),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Aa", color = palette.text, style = WriterTheme.typography.body)
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .width(EditorMetrics.caretWidth)
                    .height(24.dp)
                    .background(palette.accent),
            )
        }
    }
}

@Composable
private fun FontSpecimens(modifier: Modifier = Modifier) {
    Column(modifier) {
        WriterFont.entries.forEach { font ->
            FontSpecimen(font = font, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

private const val SPECIMEN_TEXT = "Hamburgefonstiv 0123 mmm iii"

@Composable
private fun FontSpecimen(
    font: WriterFont,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Column(modifier.fillMaxWidth()) {
        Text(text = font.name, style = WriterTheme.typography.rowExcerpt, color = colors.textSecondary)
        Text(
            text = SPECIMEN_TEXT,
            fontFamily = font.fontFamily,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal,
            fontSize = 17.sp,
            color = colors.text,
        )
        Text(
            text = SPECIMEN_TEXT,
            fontFamily = font.fontFamily,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Italic,
            fontSize = 17.sp,
            color = colors.text,
        )
        Text(
            text = SPECIMEN_TEXT,
            fontFamily = font.fontFamily,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Normal,
            fontSize = 17.sp,
            color = colors.text,
        )
        Text(
            text = SPECIMEN_TEXT,
            fontFamily = font.fontFamily,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontSize = 17.sp,
            color = colors.text,
        )
        for (level in 1..6) {
            Text(
                text = "H$level Heading",
                fontFamily = font.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (17f * EditorMetrics.headingScale[level]).sp,
                color = if (level == 6) colors.textSecondary else colors.text,
            )
        }
    }
}

@Composable
private fun IconsRow(
    colors: WriterColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryIcon(R.drawable.ic_left_panel_open, colors)
        GalleryIcon(R.drawable.ic_more_vert, colors)
        GalleryIcon(R.drawable.ic_format_bold, colors)
        GalleryIcon(R.drawable.ic_format_italic, colors)
        GalleryIcon(R.drawable.ic_link, colors)
        GalleryIcon(R.drawable.ic_edit_square, colors)
        Box(Modifier.size(72.dp)) {
            Image(
                painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GalleryIcon(
    iconRes: Int,
    colors: WriterColors,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = colors.text,
        modifier = modifier.size(WriterDimens.icon),
    )
}
