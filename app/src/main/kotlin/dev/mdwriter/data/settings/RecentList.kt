package dev.mdwriter.data.settings

/** Pure LRU bookkeeping for [Settings.recentExternal] (T18): the list of recently-opened external `DocKey` values,
 * newest first, capped. No I/O, no Android — [dev.mdwriter.data.storage.ExternalDocStore] calls [push] and releases
 * the URI grant of whatever comes back [evicted]. */
object RecentList {
    /**
     * Moves [item] to the front (removing any earlier occurrence first, so it is never duplicated), then caps the
     * result at [cap]. Returns (the new list, the items that fell off the end).
     */
    fun push(
        list: List<String>,
        item: String,
        cap: Int = 100,
    ): Pair<List<String>, List<String>> {
        val deduped = listOf(item) + list.filterNot { it == item }
        val kept = deduped.take(cap)
        val evicted = deduped.drop(cap)
        return kept to evicted
    }
}
