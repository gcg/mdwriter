package dev.mdwriter.data.library

import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.markdown.DocTitle

/** Why we are leaving [DocRef] right now — controls whether an empty untitled note is deleted (only [Switch]). */
enum class LeaveReason { Switch, DrawerOpened, Stopped }

sealed interface LeaveOutcome {
    data object Kept : LeaveOutcome

    data object Deleted : LeaveOutcome

    data class Renamed(
        val newRef: DocRef,
    ) : LeaveOutcome
}

/**
 * Notes created with "+" are named from their first line when you leave them, and deleted if left empty (only on
 * a real switch — 02 §7, T12 pitfalls). Only acts on documents whose key is in `settings.autoNamed` (once the user
 * renames a note by hand, it stops being auto-named).
 */
class AutoNamer(
    private val library: LibraryRepository,
    private val documents: DocumentRepository,
    private val settings: SettingsRepository,
    private val positions: PositionStore,
) {
    suspend fun onLeave(
        ref: DocRef,
        reason: LeaveReason,
    ): LeaveOutcome {
        if (ref !is DocRef.InternalFile) return LeaveOutcome.Kept
        val key = ref.key()
        if (key.value !in settings.current().autoNamed) return LeaveOutcome.Kept // user renamed it once
        val text =
            runCatching { documents.load(ref).text }.getOrElse { return LeaveOutcome.Kept }
        if (text.isBlank()) { // untitled + empty: delete only on a real switch
            if (reason != LeaveReason.Switch) return LeaveOutcome.Kept
            library.trash(ref)
            settings.update { it.copy(autoNamed = it.autoNamed - key.value) }
            positions.remove(key)
            return LeaveOutcome.Deleted
        }
        val base = DocTitle.fromContent(text)?.let(DocTitle::sanitizeFileName)?.trim()
        if (base.isNullOrEmpty()) return LeaveOutcome.Kept
        val folder = library.parentOf(ref) ?: return LeaveOutcome.Kept
        val current = UniqueName.splitName(library.nameOf(ref) ?: return LeaveOutcome.Kept).first
        if (current == base || Regex("^${Regex.escape(base)} \\d+$").matches(current)) return LeaveOutcome.Kept
        val newRef = library.rename(ref, folder, base) // "Groceries.md" or "Groceries 2.md"
        settings.update { it.copy(autoNamed = it.autoNamed - key.value + newRef.key().value) }
        positions.move(key, newRef.key())
        return LeaveOutcome.Renamed(newRef)
    }
}
