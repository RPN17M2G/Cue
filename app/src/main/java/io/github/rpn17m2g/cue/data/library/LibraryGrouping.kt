package io.github.rpn17m2g.cue.data.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class LibraryTab { RECENT, RECORDINGS, MUSIC }

/** One calendar day's rows, newest day first. */
data class DayGroup(
    val date: LocalDate,
    val entries: List<LibraryEntry>,
)

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

    /**
     * Splits an ordered list into calendar days, newest day first. A single
     * "Earlier" bucket made months of history look like one undated pile; the
     * screen needs the real date to label each day.
     *
     * The order inside a day is the order the tab produced, so Music keeps
     * Cue's own output above the rest of that day's files.
     */
    fun groupByDay(
        entries: List<LibraryEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DayGroup> = entries
        .groupBy { dayOf(it.dateAddedSeconds, zone) }
        .map { (date, dayEntries) -> DayGroup(date, dayEntries) }
        .sortedByDescending { it.date }
}

/** The calendar day a row belongs to, in the device's own time zone. */
fun dayOf(
    dateAddedSeconds: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDate = Instant.ofEpochSecond(dateAddedSeconds).atZone(zone).toLocalDate()
