package io.github.rpn17m2g.cue.domain.metadata

import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft

/**
 * Re-scores provider results against the text already known about the song.
 *
 * Title carries 75% of the score and artist 25% when both are available;
 * otherwise the title alone determines the score. When the query has no title,
 * the provider's own score is preserved (normalized to 0..1) so fingerprint
 * results keep their ranking. Returned matches expose the computed
 * [MetadataMatch.score] and are ordered best first.
 */
object MetadataMatcher {

    fun score(query: TagDraft, match: MetadataMatch): Double {
        if (query.title.isBlank()) return normalizedProviderScore(match.score)
        val titleScore = Similarity.textSimilarity(query.title, match.title)
        return if (query.artist.isNotBlank()) {
            0.75 * titleScore + 0.25 * Similarity.textSimilarity(query.artist, match.artist)
        } else {
            titleScore
        }
    }

    private fun normalizedProviderScore(score: Double): Double {
        if (!score.isFinite()) return 0.0
        val normalized = if (score > 1.0) score / 100.0 else score
        return normalized.coerceIn(0.0, 1.0)
    }

    fun rank(query: TagDraft, matches: List<MetadataMatch>): List<MetadataMatch> {
        if (matches.isEmpty()) return emptyList()
        return sort(matches.map { it.copy(score = score(query, it)) })
    }

    /**
     * Equal scores keep the provider's own relevance order. Sorting ties
     * alphabetically used to put a karaoke cover ("Drillr") above the original
     * artist for an exact-title search, because "Drillr" sorts before "Olivia
     * Rodrigo".
     */
    private fun sort(matches: List<MetadataMatch>): List<MetadataMatch> =
        matches.sortedByDescending { it.score }
}
