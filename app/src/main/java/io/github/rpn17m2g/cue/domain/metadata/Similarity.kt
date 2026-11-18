package io.github.rpn17m2g.cue.domain.metadata

import java.text.Normalizer
import java.util.Locale

/**
 * Comparison helpers shared by metadata ranking.
 *
 * Normalization folds case, strips punctuation and diacritics (including Hebrew
 * niqqud), and collapses whitespace. Similarity combines token overlap with
 * normalized Levenshtein distance so both word reordering and OCR typos are
 * tolerated.
 */
object Similarity {

    private val MARKS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]+")
    private val WHITESPACE = Regex("[\\s\\u00A0]+")

    /**
     * Apostrophes join their neighbours instead of splitting a word: OCR rarely
     * sees them, so "dont" must match "Don't" word for word rather than losing
     * to the "don t" two-token form a plain strip would produce.
     */
    private val APOSTROPHE = Regex("['\\u2019\\u02BC]")

    /** "… feat RAYE", "… ft RAYE", "… featuring RAYE" at the end. */
    private val FEATURE_TAIL = Regex("\\s+(?:feat|ft|featuring)\\s+.*$")

    /** Common version labels a provider appends to the canonical title. */
    private val VERSION_TAIL = Regex(
        "\\s+(?:remaster(?:ed)?(?:\\s+\\d{4})?|radio\\s+edit|single\\s+version|" +
            "album\\s+version|explicit|deluxe(?:\\s+edition)?|bonus\\s+track|mono|stereo)$",
    )

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed.replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(APOSTROPHE, "")
            .replace(NON_WORD, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    fun levenshteinDistance(a: String, b: String): Int = distance(normalize(a), normalize(b))

    fun levenshteinSimilarity(a: String, b: String): Double {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return 1.0 - distance(left, right).toDouble() / maxOf(left.length, right.length)
    }

    /**
     * True when every word of [reading] appears in [candidate], in order.
     *
     * OCR often catches only part of a long title ("I Do if you dont" for
     * "What Do I Do If You Don't"). Such a reading scores below the title
     * similarity gate even though the provider match is obviously the same
     * song. Requiring at least two words and a longer candidate keeps a
     * one-word reading from claiming any title that happens to contain it.
     */
    fun readsAsPartOf(reading: String, candidate: String): Boolean {
        val readingTokens = normalize(reading).split(' ').filter { it.isNotEmpty() }
        val candidateTokens = normalize(candidate).split(' ').filter { it.isNotEmpty() }
        if (readingTokens.size < 2 || candidateTokens.size <= readingTokens.size) return false
        var index = 0
        for (token in candidateTokens) {
            if (index < readingTokens.size && token == readingTokens[index]) index++
        }
        return index == readingTokens.size
    }

    fun tokenOverlap(a: String, b: String): Double = tokenOverlapNormalized(normalize(a), normalize(b))

    fun textSimilarity(a: String, b: String): Double {
        val left = comparable(normalize(a))
        val right = comparable(normalize(b))
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val overlap = tokenOverlapNormalized(left, right)
        val edit = 1.0 - distance(left, right).toDouble() / maxOf(left.length, right.length)
        return 0.5 * overlap + 0.5 * edit
    }

    /**
     * Drops a trailing feature credit or version qualifier from text that is
     * already normalized, so "By Your Side (feat. RAYE)" and "By Your Side"
     * compare as the same song.
     *
     * Without this the extra credit halves the title score, which is enough for
     * a same-titled song by a different artist to outrank the real one. The
     * match itself keeps the full title for display and tagging.
     */
    private fun comparable(normalized: String): String {
        var current = normalized
        repeat(2) {
            val next = current
                .replace(FEATURE_TAIL, "")
                .replace(VERSION_TAIL, "")
                .trim()
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun tokenOverlapNormalized(left: String, right: String): Double {
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val leftTokens = left.split(' ').filter { it.isNotEmpty() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotEmpty() }.toSet()
        val intersection = leftTokens.count { it in rightTokens }
        return intersection.toDouble() / (leftTokens.size + rightTokens.size - intersection)
    }

    private fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
