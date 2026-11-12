package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookupCacheTest {

    private var now = 0L
    private val cache = LookupCache(ttlMs = 1_000L, clock = { now })

    private val query = MetadataQuery("Hello", "Adele")

    @Test
    fun `a stored answer is returned for the same query`() {
        val matches = listOf(match("Hello", "Adele", 0.95))

        cache.put(query, matches)

        assertEquals(matches, cache.get(query))
    }

    @Test
    fun `case and punctuation do not create a second entry`() {
        val matches = listOf(match("Hello", "Adele", 0.95))
        cache.put(query, matches)

        assertEquals(matches, cache.get(MetadataQuery("hello!", "ADELE")))
    }

    @Test
    fun `an empty answer is never stored`() {
        cache.put(query, emptyList())

        assertNull(cache.get(query))
    }

    @Test
    fun `an entry expires after the ttl`() {
        cache.put(query, listOf(match("Hello", "Adele", 0.95)))
        now = 1_001L

        assertNull(cache.get(query))
    }

    @Test
    fun `an entry survives until the ttl`() {
        cache.put(query, listOf(match("Hello", "Adele", 0.95)))
        now = 1_000L

        assertEquals(1, cache.get(query)?.size)
    }

    @Test
    fun `the oldest entry is evicted past the limit`() {
        val small = LookupCache(ttlMs = 10_000L, maxEntries = 2, clock = { now })
        small.put(MetadataQuery("One"), listOf(match("One", "Artist", 0.9)))
        small.put(MetadataQuery("Two"), listOf(match("Two", "Artist", 0.9)))
        small.put(MetadataQuery("Three"), listOf(match("Three", "Artist", 0.9)))

        assertNull(small.get(MetadataQuery("One")))
        assertEquals(1, small.get(MetadataQuery("Two"))?.size)
        assertEquals(1, small.get(MetadataQuery("Three"))?.size)
    }

    @Test
    fun `clear drops every entry`() {
        cache.put(query, listOf(match("Hello", "Adele", 0.95)))

        cache.clear()

        assertNull(cache.get(query))
    }

    private fun match(title: String, artist: String, score: Double): MetadataMatch =
        MetadataMatch(
            title = title,
            artist = artist,
            score = score,
            source = IdentificationSource.ITUNES,
        )
}
