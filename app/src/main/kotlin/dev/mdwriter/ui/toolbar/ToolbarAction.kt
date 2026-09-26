package dev.mdwriter.ui.toolbar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.mdwriter.R
import dev.mdwriter.ui.theme.WriterDimens
import kotlin.math.floor

/**
 * Every formatting/clipboard command: keyboard shortcuts ([dev.mdwriter.editor.SmartInput], T08), the selection
 * pill and accessibility custom actions (T09). Plain Kotlin, no Compose — the one `ui` package the editor engine
 * may import (01 §3), so that [dev.mdwriter.editor.EditorController.perform] can take one of these directly.
 */
sealed interface ToolbarAction {
    data object Bold : ToolbarAction

    data object Italic : ToolbarAction

    data object Strike : ToolbarAction

    data object Highlight : ToolbarAction

    data object Code : ToolbarAction

    data object CodeBlock : ToolbarAction

    data object Link : ToolbarAction

    data object HeadingCycle : ToolbarAction

    data class SetHeading(
        val level: Int,
    ) : ToolbarAction {
        init {
            require(level in 0..6)
        }
    }

    data object Quote : ToolbarAction

    data object BulletList : ToolbarAction

    data object NumberedList : ToolbarAction

    data object TaskList : ToolbarAction

    data object ClearFormatting : ToolbarAction

    data object Cut : ToolbarAction

    data object Copy : ToolbarAction

    data object Paste : ToolbarAction

    data object SelectAll : ToolbarAction
}

/** Inline wraps are no-ops on verbatim lines (code/HTML/front matter); Code counts only for single-line selections. */
val ToolbarAction.isInlineWrap: Boolean
    get() =
        this == ToolbarAction.Bold || this == ToolbarAction.Italic ||
            this == ToolbarAction.Strike || this == ToolbarAction.Highlight || this == ToolbarAction.Link

/**
 * A slot on the selection pill (02 §6 priority order): an icon + label, and (except [More]) the [ToolbarAction] it
 * performs. `More` has no action of its own — tapping it opens [dev.mdwriter.ui.toolbar.MoreMenu] instead.
 */
enum class ToolbarItem(
    val action: ToolbarAction?,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    Bold(ToolbarAction.Bold, R.drawable.ic_format_bold, R.string.tb_bold),
    Italic(ToolbarAction.Italic, R.drawable.ic_format_italic, R.string.tb_italic),
    Heading(ToolbarAction.HeadingCycle, R.drawable.ic_format_h1, R.string.tb_heading),
    Link(ToolbarAction.Link, R.drawable.ic_link, R.string.tb_link),
    Copy(ToolbarAction.Copy, R.drawable.ic_content_copy, R.string.tb_copy),
    Paste(ToolbarAction.Paste, R.drawable.ic_content_paste, R.string.tb_paste),
    Cut(ToolbarAction.Cut, R.drawable.ic_content_cut, R.string.tb_cut),
    Code(ToolbarAction.Code, R.drawable.ic_code, R.string.tb_code),
    More(null, R.drawable.ic_more_horiz, R.string.tb_more),
}

/** A text-only row in [dev.mdwriter.ui.toolbar.MoreMenu]. */
enum class MoreEntry(
    val action: ToolbarAction,
    @StringRes val label: Int,
) {
    Strike(ToolbarAction.Strike, R.string.tb_strike),
    Highlight(ToolbarAction.Highlight, R.string.tb_highlight),
    Quote(ToolbarAction.Quote, R.string.tb_quote),
    Bullets(ToolbarAction.BulletList, R.string.tb_bullets),
    Numbered(ToolbarAction.NumberedList, R.string.tb_numbered),
    Task(ToolbarAction.TaskList, R.string.tb_task),
    CodeBlock(ToolbarAction.CodeBlock, R.string.tb_code_block),
    Clear(ToolbarAction.ClearFormatting, R.string.tb_clear),
    SelectAll(ToolbarAction.SelectAll, R.string.tb_select_all),
}

/**
 * How many slots fit, and which items go where (02 §6): `n = min(9, floor((width − 32 dp) / 48 dp))`, the last
 * slot always [ToolbarItem.More]. Display order: formatting group, a 1 px divider, then clipboard (Cut, Copy,
 * Paste), then More — but *priority* order (which items are dropped first when the pill is narrow) is
 * Bold/Italic/Heading/Link/Copy/Paste/Cut/Code, matching 02 §6's numbered list.
 */
object ToolbarSlots {
    val PRIORITY =
        listOf(
            ToolbarItem.Bold,
            ToolbarItem.Italic,
            ToolbarItem.Heading,
            ToolbarItem.Link,
            ToolbarItem.Copy,
            ToolbarItem.Paste,
            ToolbarItem.Cut,
            ToolbarItem.Code,
        )

    data class Layout(
        val formatting: List<ToolbarItem>,
        val clipboard: List<ToolbarItem>,
        val overflow: List<ToolbarItem>,
        val more: List<MoreEntry>,
    ) {
        val buttonCount get() = formatting.size + clipboard.size + 1 // + More
        val hasDivider get() = formatting.isNotEmpty() && clipboard.isNotEmpty()
    }

    fun slotCount(widthDp: Float): Int {
        val usable = widthDp - 2 * WriterDimens.pillScreenPadding.value
        return minOf(WriterDimens.PILL_MAX_SLOTS, floor(usable / WriterDimens.pillButton.value).toInt())
            .coerceAtLeast(2)
    }

    fun compute(
        widthDp: Float,
        highlightEnabled: Boolean,
    ): Layout {
        val shown = PRIORITY.take(slotCount(widthDp) - 1).toSet()
        return Layout(
            formatting =
                listOf(ToolbarItem.Bold, ToolbarItem.Italic, ToolbarItem.Heading, ToolbarItem.Link, ToolbarItem.Code)
                    .filter { it in shown },
            clipboard = listOf(ToolbarItem.Cut, ToolbarItem.Copy, ToolbarItem.Paste).filter { it in shown },
            overflow = PRIORITY.filter { it !in shown }, // shown first in More, priority order
            more = MoreEntry.entries.filter { it != MoreEntry.Highlight || highlightEnabled },
        )
    }
}
