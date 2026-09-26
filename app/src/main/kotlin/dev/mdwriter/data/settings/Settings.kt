package dev.mdwriter.data.settings

import dev.mdwriter.data.library.DocKey
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.ui.theme.EditorMetrics
import dev.mdwriter.ui.theme.ThemeMode
import dev.mdwriter.ui.theme.WriterFont

/** Sort order for the library drawer (T12). Names are fixed: they are persisted by [SortOrder.name]. */
enum class SortOrder { ModifiedNewestFirst, ModifiedOldestFirst, NameAToZ, NameZToA }

object LineLengths {
    val ALLOWED = listOf(64, 72, 80)
}

/**
 * Every setting the app will ever need (01 §5, T11-T19). The field list and defaults are a contract: later tasks
 * read them directly. [ThemeMode] and [WriterFont] are T02's enums (dev.mdwriter.ui.theme) — never redeclare them.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val pureBlack: Boolean = false,
    val typeface: WriterFont = WriterFont.Duo,
    val textSizeStep: Int = EditorMetrics.DEFAULT_TEXT_SIZE_STEP,
    val lineLength: Int = 64,
    val focusMode: FocusModeKind = FocusModeKind.Off,
    val typewriter: Boolean = false,
    val wordCount: Boolean = false,
    val swipeNavigation: Boolean = true,
    val highlightSyntax: Boolean = false,
    val newNoteExtension: String = "md",
    val showExtensions: Boolean = false,
    val sortOrder: SortOrder = SortOrder.ModifiedNewestFirst,
    val lastOpenDoc: DocKey? = null,
    // Tree URI strings, link order (T14).
    val linkedTrees: List<String> = emptyList(),
    // DocKey.value's of notes that are still auto-named from their first line (T12).
    val autoNamed: Set<String> = emptySet(),
    val hintDismissed: Boolean = false,
    // DocKey.value's, newest first, cap 100 (T18).
    val recentExternal: List<String> = emptyList(),
    val welcomeCreated: Boolean = false,
)
