package dev.mdwriter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.data.storage.Hashes
import kotlinx.coroutines.flow.first

/** Caret + scroll remembered per document (01 §5, §8), so reopening a note returns to where you were. */
data class Position(
    val caret: Int,
    val scrollY: Int,
)

private const val KEY_PREFIX = "p_"
private const val MAX_ENTRIES = 200

private fun keyFor(key: DocKey) = stringPreferencesKey(KEY_PREFIX + Hashes.sha1Hex(key.value))

/** DataStore "positions" (`filesDir/datastore/positions.preferences_pb`). LRU-200: [put] evicts the entry with the
 * smallest `usedAt` once there are more than [MAX_ENTRIES] `p_*` keys, inside the SAME `edit {}` as the write. */
class PositionStore(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun get(key: DocKey): Position? {
        val raw = store.data.first()[keyFor(key)] ?: return null
        return decode(raw)
    }

    suspend fun put(
        key: DocKey,
        position: Position,
    ) {
        store.edit { prefs ->
            val k = keyFor(key)
            prefs[k] = "${position.caret},${position.scrollY},${clock()}"
            evictIfNeeded(prefs, keep = k)
        }
    }

    suspend fun move(
        from: DocKey,
        to: DocKey,
    ) {
        store.edit { prefs ->
            val raw = prefs[keyFor(from)] ?: return@edit
            prefs.remove(keyFor(from))
            prefs[keyFor(to)] = raw
        }
    }

    suspend fun remove(key: DocKey) {
        store.edit { prefs -> prefs.remove(keyFor(key)) }
    }

    private fun evictIfNeeded(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        keep: Preferences.Key<String>,
    ) {
        val posEntries =
            prefs
                .asMap()
                .entries
                .filter { it.key.name.startsWith(KEY_PREFIX) }
                .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
        if (posEntries.size <= MAX_ENTRIES) return
        val toEvict =
            posEntries
                .filter { it.first != keep }
                .minByOrNull { (_, raw) -> usedAt(raw) }
                ?: return
        @Suppress("UNCHECKED_CAST")
        prefs.remove(toEvict.first as Preferences.Key<String>)
    }

    private fun usedAt(raw: String): Long = raw.substringAfterLast(',', "0").toLongOrNull() ?: 0L

    private fun decode(raw: String): Position? {
        val parts = raw.split(",")
        if (parts.size < 2) return null
        val caret = parts[0].toIntOrNull() ?: return null
        val scrollY = parts[1].toIntOrNull() ?: return null
        return Position(caret, scrollY)
    }
}
