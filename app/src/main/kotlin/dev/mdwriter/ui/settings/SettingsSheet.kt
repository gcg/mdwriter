package dev.mdwriter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mdwriter.R
import dev.mdwriter.data.settings.LineLengths
import dev.mdwriter.data.settings.Settings
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.ui.theme.ThemeMode
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterFont
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.fontFamily

/** Flat bottom sheet with exactly the rows of 02 §10. Every change is applied live by the root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    showLineLength: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onExportAll: () -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WriterTheme.colors
    val widthClass = WidthClass.fromWidthDp(LocalConfiguration.current.screenWidthDp.toFloat())
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
        SettingsContent(
            settings = settings,
            showLineLength = showLineLength,
            onUpdate = onUpdate,
            onExportAll = onExportAll,
            onAbout = onAbout,
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = WriterDimens.sheetPadding(widthClass))
                    .navigationBarsPadding(),
        )
    }
}

@Composable
fun SettingsContent(
    settings: Settings,
    showLineLength: Boolean,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onExportAll: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthClass = WidthClass.fromWidthDp(LocalConfiguration.current.screenWidthDp.toFloat())
    Column(modifier) {
        SettingsGroupHeader(stringResource(R.string.settings_group_appearance))
        SegmentedRow(
            label = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            labelOf = {
                stringResource(
                    when (it) {
                        ThemeMode.System -> R.string.settings_theme_system
                        ThemeMode.Light -> R.string.settings_theme_light
                        ThemeMode.Dark -> R.string.settings_theme_dark
                    },
                )
            },
            onSelect = { m -> onUpdate { it.copy(themeMode = m) } },
        )
        SwitchRow(stringResource(R.string.settings_pure_black), settings.pureBlack, onChange = { v ->
            onUpdate { it.copy(pureBlack = v) }
        })

        SettingsGroupHeader(stringResource(R.string.settings_group_text))
        SegmentedRow(
            label = stringResource(R.string.settings_typeface),
            options = WriterFont.entries,
            selected = settings.typeface,
            labelOf = { it.name },
            fontFamilyOf = { it.fontFamily },
            onSelect = { f -> onUpdate { it.copy(typeface = f) } },
        )
        TextSizeRow(settings.textSizeStep, settings.typeface, widthClass, onCommit = { step ->
            onUpdate { it.copy(textSizeStep = step) }
        })
        if (showLineLength) {
            SegmentedRow(
                label = stringResource(R.string.settings_line_length),
                options = LineLengths.ALLOWED,
                selected = settings.lineLength,
                labelOf = { it.toString() },
                onSelect = { n -> onUpdate { it.copy(lineLength = n) } },
            )
        }

        SettingsGroupHeader(stringResource(R.string.settings_group_writing))
        SegmentedRow(
            label = stringResource(R.string.settings_focus),
            options = FocusModeKind.entries,
            selected = settings.focusMode,
            labelOf = {
                stringResource(
                    when (it) {
                        FocusModeKind.Off -> R.string.focus_off
                        FocusModeKind.Sentence -> R.string.focus_sentence
                        FocusModeKind.Paragraph -> R.string.focus_paragraph
                    },
                )
            },
            onSelect = { k -> onUpdate { it.copy(focusMode = k) } },
        )
        SwitchRow(stringResource(R.string.settings_typewriter), settings.typewriter, onChange = { v ->
            onUpdate { it.copy(typewriter = v) }
        })
        SwitchRow(stringResource(R.string.settings_word_count), settings.wordCount, onChange = { v ->
            onUpdate { it.copy(wordCount = v) }
        })
        SwitchRow(stringResource(R.string.settings_swipe), settings.swipeNavigation, onChange = { v ->
            onUpdate { it.copy(swipeNavigation = v) }
        })
        SwitchRow(stringResource(R.string.settings_highlight), settings.highlightSyntax, onChange = { v ->
            onUpdate { it.copy(highlightSyntax = v) }
        })

        SettingsGroupHeader(stringResource(R.string.settings_group_files))
        SegmentedRow(
            label = stringResource(R.string.settings_new_note_ext),
            options = listOf("md", "txt"),
            selected = settings.newNoteExtension,
            labelOf = { ".$it" },
            onSelect = { e -> onUpdate { it.copy(newNoteExtension = e) } },
        )
        SwitchRow(stringResource(R.string.settings_show_ext), settings.showExtensions, onChange = { v ->
            onUpdate { it.copy(showExtensions = v) }
        })
        ActionRow(stringResource(R.string.settings_export_all), onExportAll)

        SettingsGroupHeader(stringResource(R.string.settings_group_about))
        ActionRow(stringResource(R.string.settings_about), onAbout)
    }
}
