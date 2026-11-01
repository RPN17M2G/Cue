package io.github.rpn17m2g.cue.data.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LibraryTab { RECENT, RECORDINGS, MUSIC }

enum class DayBucket { TODAY, YESTERDAY, EARLIER }

/**
 * Pure list shaping, kept out of the ViewModel so the ordering rules can be
 * tested without a device: Recent is newest-first across both kinds, the
 * Recordings tab is videos only, and Music pins Cue's own output above the
 * rest of the device's music.
 */
object LibraryGrouping {

    const val RECENT_LIMIT = 120
    const val TAB_LIMIT = 400

    fun recent(entries: List<LibraryEntry>): List<LibraryEntry> =
        entries.sortedByDescending { it.dateAddedSeconds }.take(RECENT_LIMIT)

    fun recordings(entries: List<LibraryEntry>): List<LibraryEntry> =
        entries.filter { it.kind == LibraryKind.VIDEO }
            .sortedByDescending { it.dateAddedSeconds }
            .take(TAB_LIMIT)

    fun music(entries: List<LibraryEntry>): List<LibraryEntry> =
        entries.filter { it.kind == LibraryKind.AUDIO }
            .sortedWith(
                compareByDescending<LibraryEntry> { it.source == LibrarySource.CUE_OUTPUT }
                    .thenByDescending { it.dateAddedSeconds },
            )
            .take(TAB_LIMIT)

    fun forTab(entries: List<LibraryEntry>, tab: LibraryTab): List<LibraryEntry> = when (tab) {
        LibraryTab.RECENT -> recent(entries)
        LibraryTab.RECORDINGS -> recordings(entries)
        LibraryTab.MUSIC -> music(entries)
    }
}

/** Today / Yesterday / Earlier, in the device's own time zone. */
fun dayBucket(
    dateAddedSeconds: Long,
    now: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): DayBucket {
    val day = Instant.ofEpochSecond(dateAddedSeconds).atZone(zone).toLocalDate()
    return when (day) {
        now -> DayBucket.TODAY
        now.minusDays(1) -> DayBucket.YESTERDAY
        else -> DayBucket.EARLIER
    }
}
