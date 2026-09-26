package dev.mdwriter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.editor.FocusModeKind
import dev.mdwriter.ui.theme.ThemeMode
import dev.mdwriter.ui.theme.WriterFont
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore Preferences keys (snake_case of [Settings]'s field names). Enums are stored by [Enum.name]; ordered
 * lists are ONE string joined with `'\n'`; [Settings.autoNamed] is a genuine string set; a null [Settings.lastOpenDoc]
 * removes its key entirely. */
private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PURE_BLACK = booleanPreferencesKey("pure_black")
    val TYPEFACE = stringPreferencesKey("typeface")
    val TEXT_SIZE_STEP = intPreferencesKey("text_size_step")
    val LINE_LENGTH = intPreferencesKey("line_length")
    val FOCUS_MODE = stringPreferencesKey("focus_mode")
    val TYPEWRITER = booleanPreferencesKey("typewriter")
    val WORD_COUNT = booleanPreferencesKey("word_count")
    val SWIPE_NAVIGATION = booleanPreferencesKey("swipe_navigation")
    val HIGHLIGHT_SYNTAX = booleanPreferencesKey("highlight_syntax")
    val NEW_NOTE_EXTENSION = stringPreferencesKey("new_note_extension")
    val SHOW_EXTENSIONS = booleanPreferencesKey("show_extensions")
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val LAST_OPEN_DOC = stringPreferencesKey("last_open_doc")
    val LINKED_TREES = stringPreferencesKey("linked_trees")
    val AUTO_NAMED = stringSetPreferencesKey("auto_named")
    val HINT_DISMISSED = booleanPreferencesKey("hint_dismissed")
    val RECENT_EXTERNAL = stringPreferencesKey("recent_external")
    val WELCOME_CREATED = booleanPreferencesKey("welcome_created")
}

/** Ordered lists are stored as one string joined with `'\n'`; an empty list is stored as an absent key (a lone
 * empty string would otherwise round-trip as a one-element list of ""). */
private fun encodeList(list: List<String>): String = list.joinToString("\n")

private fun decodeList(raw: String?): List<String> = if (raw.isNullOrEmpty()) emptyList() else raw.split("\n")

private inline fun <reified T : Enum<T>> decodeEnum(
    raw: String?,
    default: T,
): T = raw?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

internal fun Preferences.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        themeMode = decodeEnum(this[Keys.THEME_MODE], defaults.themeMode),
        pureBlack = this[Keys.PURE_BLACK] ?: defaults.pureBlack,
        typeface = decodeEnum(this[Keys.TYPEFACE], defaults.typeface),
        textSizeStep = this[Keys.TEXT_SIZE_STEP] ?: defaults.textSizeStep,
        lineLength = this[Keys.LINE_LENGTH] ?: defaults.lineLength,
        focusMode = decodeEnum(this[Keys.FOCUS_MODE], defaults.focusMode),
        typewriter = this[Keys.TYPEWRITER] ?: defaults.typewriter,
        wordCount = this[Keys.WORD_COUNT] ?: defaults.wordCount,
        swipeNavigation = this[Keys.SWIPE_NAVIGATION] ?: defaults.swipeNavigation,
        highlightSyntax = this[Keys.HIGHLIGHT_SYNTAX] ?: defaults.highlightSyntax,
        newNoteExtension = this[Keys.NEW_NOTE_EXTENSION] ?: defaults.newNoteExtension,
        showExtensions = this[Keys.SHOW_EXTENSIONS] ?: defaults.showExtensions,
        sortOrder = decodeEnum(this[Keys.SORT_ORDER], defaults.sortOrder),
        lastOpenDoc = this[Keys.LAST_OPEN_DOC]?.let(::DocKey),
        linkedTrees = decodeList(this[Keys.LINKED_TREES]),
        autoNamed = this[Keys.AUTO_NAMED] ?: defaults.autoNamed,
        hintDismissed = this[Keys.HINT_DISMISSED] ?: defaults.hintDismissed,
        recentExternal = decodeList(this[Keys.RECENT_EXTERNAL]),
        welcomeCreated = this[Keys.WELCOME_CREATED] ?: defaults.welcomeCreated,
    )
}

internal fun MutablePreferences.writeAll(s: Settings) {
    this[Keys.THEME_MODE] = s.themeMode.name
    this[Keys.PURE_BLACK] = s.pureBlack
    this[Keys.TYPEFACE] = s.typeface.name
    this[Keys.TEXT_SIZE_STEP] = s.textSizeStep
    this[Keys.LINE_LENGTH] = s.lineLength
    this[Keys.FOCUS_MODE] = s.focusMode.name
    this[Keys.TYPEWRITER] = s.typewriter
    this[Keys.WORD_COUNT] = s.wordCount
    this[Keys.SWIPE_NAVIGATION] = s.swipeNavigation
    this[Keys.HIGHLIGHT_SYNTAX] = s.highlightSyntax
    this[Keys.NEW_NOTE_EXTENSION] = s.newNoteExtension
    this[Keys.SHOW_EXTENSIONS] = s.showExtensions
    this[Keys.SORT_ORDER] = s.sortOrder.name
    if (s.lastOpenDoc == null) remove(Keys.LAST_OPEN_DOC) else this[Keys.LAST_OPEN_DOC] = s.lastOpenDoc.value
    this[Keys.LINKED_TREES] = encodeList(s.linkedTrees)
    this[Keys.AUTO_NAMED] = s.autoNamed
    this[Keys.HINT_DISMISSED] = s.hintDismissed
    this[Keys.RECENT_EXTERNAL] = encodeList(s.recentExternal)
    this[Keys.WELCOME_CREATED] = s.welcomeCreated
}

/** DataStore "settings" (`filesDir/datastore/settings.preferences_pb`, created via `preferencesDataStoreFile`). */
class SettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p -> p.writeAll(transform(p.toSettings())) }
    }
}
