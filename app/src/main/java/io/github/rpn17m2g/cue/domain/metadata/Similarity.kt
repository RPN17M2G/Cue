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

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed.replace(MARKS, "")
            .lowercase(Locale.ROOT)
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

    fun tokenOverlap(a: String, b: String): Double = tokenOverlapNormalized(normalize(a), normalize(b))

    fun textSimilarity(a: String, b: String): Double {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() && right.isEmpty()) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val overlap = tokenOverlapNormalized(left, right)
        val edit = 1.0 - distance(left, right).toDouble() / maxOf(left.length, right.length)
        return 0.5 * overlap + 0.5 * edit
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
