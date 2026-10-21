package io.github.rpn17m2g.cue.domain.metadata

import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft

/**
 * Re-scores provider results against the text already known about the song.
 *
 * Title carries 75% of the score and artist 25% when both are available;
 * otherwise the title alone determines the score. Returned matches expose the
 * computed [MetadataMatch.score] and are ordered best first.
 */
object MetadataMatcher {

    fun score(query: TagDraft, match: MetadataMatch): Double {
        if (query.title.isBlank()) return 0.0
        val titleScore = Similarity.textSimilarity(query.title, match.title)
        return if (query.artist.isNotBlank()) {
            0.75 * titleScore + 0.25 * Similarity.textSimilarity(query.artist, match.artist)
        } else {
            titleScore
        }
    }

    fun rank(query: TagDraft, matches: List<MetadataMatch>): List<MetadataMatch> {
        if (matches.isEmpty()) return emptyList()
        return sort(matches.map { it.copy(score = score(query, it)) })
    }

    private fun sort(matches: List<MetadataMatch>): List<MetadataMatch> = matches.sortedWith(
        compareByDescending<MetadataMatch> { it.score }
            .thenBy { Similarity.normalize(it.title) }
            .thenBy { Similarity.normalize(it.artist) },
    )
}
