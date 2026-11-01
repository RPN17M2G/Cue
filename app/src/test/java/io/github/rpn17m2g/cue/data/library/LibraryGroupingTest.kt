package io.github.rpn17m2g.cue.data.library

import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gallery is only as good as its ordering, and ordering bugs are invisible
 * in a screenshot. These tests pin the three tab rules and the day buckets
 * without needing a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryGroupingTest {

    @Test
    fun `recent mixes both kinds newest first`() {
        val entries = listOf(
            entry(id = 1, kind = LibraryKind.AUDIO, added = 100),
            entry(id = 2, kind = LibraryKind.VIDEO, added = 300),
            entry(id = 3, kind = LibraryKind.AUDIO, added = 200),
        )

        assertEquals(listOf(2L, 3L, 1L), LibraryGrouping.recent(entries).map { it.id() })
    }

    @Test
    fun `recent stops at its limit`() {
        val entries = (1..130).map { i ->
            entry(id = i.toLong(), kind = LibraryKind.VIDEO, added = i.toLong())
        }

        val recent = LibraryGrouping.recent(entries)

        assertEquals(LibraryGrouping.RECENT_LIMIT, recent.size)
        assertEquals(130L, recent.first().id())
        assertEquals(11L, recent.last().id())
    }

    @Test
    fun `recordings keeps videos only`() {
        val entries = listOf(
            entry(id = 1, kind = LibraryKind.AUDIO, added = 300),
            entry(id = 2, kind = LibraryKind.VIDEO, added = 100),
            entry(id = 3, kind = LibraryKind.VIDEO, added = 200),
        )

        assertEquals(listOf(3L, 2L), LibraryGrouping.recordings(entries).map { it.id() })
    }

    @Test
    fun `music pins cue output above older device music`() {
        val entries = listOf(
            entry(id = 1, kind = LibraryKind.AUDIO, added = 300, source = LibrarySource.MUSIC),
            entry(id = 2, kind = LibraryKind.AUDIO, added = 100, source = LibrarySource.CUE_OUTPUT),
            entry(id = 3, kind = LibraryKind.AUDIO, added = 200, source = LibrarySource.CUE_OUTPUT),
        )

        assertEquals(listOf(3L, 2L, 1L), LibraryGrouping.music(entries).map { it.id() })
    }

    @Test
    fun `for tab dispatches to the right rule`() {
        val entries = listOf(
            entry(id = 1, kind = LibraryKind.AUDIO, added = 100),
            entry(id = 2, kind = LibraryKind.VIDEO, added = 200),
        )

        assertEquals(2, LibraryGrouping.forTab(entries, LibraryTab.RECENT).size)
        assertEquals(listOf(2L), LibraryGrouping.forTab(entries, LibraryTab.RECORDINGS).map { it.id() })
        assertEquals(listOf(1L), LibraryGrouping.forTab(entries, LibraryTab.MUSIC).map { it.id() })
    }

    @Test
    fun `day buckets follow the device calendar`() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 11, 1)
        fun secondsOn(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()

        assertEquals(DayBucket.TODAY, dayBucket(secondsOn(now), now = now, zone = zone))
        assertEquals(
            DayBucket.YESTERDAY,
            dayBucket(secondsOn(now.minusDays(1)), now = now, zone = zone),
        )
        assertEquals(
            DayBucket.EARLIER,
            dayBucket(secondsOn(now.minusDays(2)), now = now, zone = zone),
        )
    }

    private fun entry(
        id: Long,
        kind: LibraryKind,
        added: Long,
        source: LibrarySource = if (kind == LibraryKind.VIDEO) {
            LibrarySource.RECORDING
        } else {
            LibrarySource.MUSIC
        },
    ): LibraryEntry = LibraryEntry(
        uri = Uri.parse("content://media/external/${if (kind == LibraryKind.VIDEO) "video" else "audio"}/media/$id"),
        title = "Track $id",
        artist = null,
        durationMs = 60_000,
        dateAddedSeconds = added,
        kind = kind,
        source = source,
    )

    private fun LibraryEntry.id(): Long = uri.lastPathSegment!!.toLong()
}
