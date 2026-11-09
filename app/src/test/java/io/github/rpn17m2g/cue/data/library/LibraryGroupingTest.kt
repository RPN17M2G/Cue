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
    fun `groups entries by real calendar day newest first`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 8)
        fun secondsOn(date: LocalDate, hour: Int = 12): Long =
            date.atTime(hour, 0).atZone(zone).toEpochSecond()

        val entries = listOf(
            entry(id = 1, kind = LibraryKind.VIDEO, added = secondsOn(today.minusDays(40))),
            entry(id = 2, kind = LibraryKind.VIDEO, added = secondsOn(today, hour = 9)),
            entry(id = 3, kind = LibraryKind.AUDIO, added = secondsOn(today.minusDays(1), 20)),
            entry(id = 4, kind = LibraryKind.VIDEO, added = secondsOn(today, hour = 21)),
        )

        val groups = LibraryGrouping.groupByDay(entries, zone = zone)

        assertEquals(
            listOf(today, today.minusDays(1), today.minusDays(40)),
            groups.map { it.date },
        )
        assertEquals(listOf(2L, 4L), groups[0].entries.map { it.id() })
        assertEquals(listOf(3L), groups[1].entries.map { it.id() })
        assertEquals(listOf(1L), groups[2].entries.map { it.id() })
    }

    @Test
    fun `day of a late-night row stays on its own date`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 10, 30)
        val seconds = date.atTime(23, 59).atZone(zone).toEpochSecond()

        assertEquals(date, dayOf(seconds, zone))
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
