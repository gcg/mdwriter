package dev.mdwriter.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mdwriter.BuildConfig
import dev.mdwriter.R
import dev.mdwriter.ui.theme.WriterFont
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.fontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** About: version, font credit, and the bundled licence texts. Dismiss returns to Settings (root wiring). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val colors = WriterTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.divider) },
    ) {
        AboutContent(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun AboutContent(modifier: Modifier = Modifier) {
    val colors = WriterTheme.colors
    var shown by remember { mutableStateOf<LicenseNotice?>(null) }
    BackHandler(enabled = shown != null) { shown = null }
    val notice = shown
    if (notice != null) {
        LicenseViewer(notice, onBack = { shown = null }, modifier = modifier)
        return
    }
    Column(modifier) {
        Text(stringResource(R.string.about_title), color = colors.text, fontSize = 20.sp)
        Text(
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            color = colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.about_font_credit),
            color = colors.text,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        SettingsGroupHeader(stringResource(R.string.about_licenses_header))
        LicenseNotices.forEach { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button) { shown = n },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(n.titleRes), color = colors.text, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun LicenseViewer(
    notice: LicenseNotice,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    val context = LocalContext.current
    val text by produceState("", notice) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.assets
                        .open(notice.assetPath)
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrDefault("")
            }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, tint = colors.text)
            Text(
                stringResource(notice.titleRes),
                color = colors.text,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text,
            color = colors.text,
            fontFamily = WriterFont.Mono.fontFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
