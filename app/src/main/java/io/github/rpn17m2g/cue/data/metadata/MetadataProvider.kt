package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch

/** A single online provider that can turn a query into ranked candidates. */
interface MetadataProvider {
    val source: IdentificationSource

    suspend fun search(query: MetadataQuery): LookupResult<List<MetadataMatch>>
}

/** The cover picker's data source. */
interface ArtworkSearch {
    suspend fun search(query: String): List<ArtworkCandidate>
}

/** Fingerprint lookup (AcoustID) kept separate from text search. */
interface FingerprintLookup {
    suspend fun lookup(input: FingerprintInput): LookupResult<List<MetadataMatch>>
}
