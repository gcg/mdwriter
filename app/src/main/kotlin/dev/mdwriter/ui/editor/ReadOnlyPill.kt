package dev.mdwriter.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mdwriter.R
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.hairline

/**
 * "Read-only · Save a copy to Library" (T18 step 9): shown top-centre, under the status-bar inset, whenever the
 * open document is a read-only [dev.mdwriter.data.library.DocRef.External]. It shares the same top slot as
 * [ConflictBanner] — the caller only shows one of the two at a time (the banner wins).
 */
@Composable
fun ReadOnlyPill(
    onSaveCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surface)
            .border(hairline(), colors.divider, RoundedCornerShape(50))
            .minimumInteractiveComponentSize()
            .heightIn(min = 32.dp)
            .clickable(onClick = onSaveCopy)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.read_only_prefix), color = colors.textSecondary, fontSize = 13.sp)
        Text(stringResource(R.string.save_copy_to_library), color = colors.accent, fontSize = 13.sp)
    }
}
