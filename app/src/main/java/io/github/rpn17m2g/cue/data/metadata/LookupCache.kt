package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.MetadataMatch

/**
 * Short-lived memo for text lookups. A retry, or two OCR readings that
 * normalize to the same query, should not cost another provider round trip.
 *
 * Only successful non-empty answers are stored: a failure must be retried,
 * because the next attempt may be the one that works. Entries expire so a
 * provider that was missing a release earlier can still answer later.
 */
class LookupCache(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = MAX_ENTRIES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(val matches: List<MetadataMatch>, val storedAt: Long)

    /** Access-order [LinkedHashMap] is a tiny LRU without another dependency. */
    private val entries = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry>?,
        ): Boolean = size > maxEntries
    }

    fun get(query: MetadataQuery): List<MetadataMatch>? {
        val key = key(query)
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return entry.matches
    }

    fun put(query: MetadataQuery, matches: List<MetadataMatch>) {
        if (matches.isEmpty()) return
        entries[key(query)] = Entry(matches, clock())
    }

    fun clear() = entries.clear()

    private fun key(query: MetadataQuery): String = listOf(query.title, query.artist)
        .joinToString("\u0000") { MetadataScorer.normalize(it.orEmpty()) }

    private companion object {
        const val DEFAULT_TTL_MS = 10 * 60_000L
        const val MAX_ENTRIES = 32
    }
}
