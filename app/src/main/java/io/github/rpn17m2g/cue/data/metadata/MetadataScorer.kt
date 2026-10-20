package io.github.rpn17m2g.cue.data.metadata

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes text so provider results can be ranked against an OCR/manual query
 * regardless of case, diacritics, or punctuation. Hebrew and other scripts are
 * preserved; only combining marks and non-letter/digit characters are dropped.
 */
object MetadataScorer {

    private val combiningMarks = Regex("\\p{M}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)
            .replace(nonWord, " ")
            .trim()
            .replace(whitespace, " ")
    }

    fun score(query: MetadataQuery, title: String, artist: String?): Double {
        val titleSimilarity = similarity(query.title, title)
        val queryArtist = query.artist?.takeIf { it.isNotBlank() }
        if (queryArtist == null) return titleSimilarity
        val candidateArtist = artist.orEmpty()
        if (candidateArtist.isBlank()) return titleSimilarity * TITLE_WEIGHT
        return titleSimilarity * TITLE_WEIGHT + similarity(queryArtist, candidateArtist) * ARTIST_WEIGHT
    }

    fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) {
            val shorter = minOf(a.length, b.length).toDouble()
            val longer = maxOf(a.length, b.length).toDouble()
            return CONTAINMENT_BASE + CONTAINMENT_RANGE * (shorter / longer)
        }
        val distance = levenshtein(a, b)
        return (1.0 - distance.toDouble() / maxOf(a.length, b.length)).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private const val TITLE_WEIGHT = 0.7
    private const val ARTIST_WEIGHT = 0.3
    private const val CONTAINMENT_BASE = 0.8
    private const val CONTAINMENT_RANGE = 0.2
}
