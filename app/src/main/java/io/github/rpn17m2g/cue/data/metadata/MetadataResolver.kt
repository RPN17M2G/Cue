package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.MetadataMatch

interface MetadataResolver {
    suspend fun search(query: MetadataQuery): List<MetadataMatch>
}

/**
 * iTunes first; MusicBrainz only when iTunes returns nothing or fails. Providers
 * already return typed failures, so the resolver simply degrades to an empty list
 * unless the caller asks for the typed result via [searchResult].
 */
class CompositeMetadataResolver(
    private val primary: MetadataProvider,
    private val fallback: MetadataProvider,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) : MetadataResolver {

    override suspend fun search(query: MetadataQuery): List<MetadataMatch> =
        when (val result = searchResult(query)) {
            is LookupResult.Success -> result.value
            is LookupResult.Failure -> emptyList()
        }

    suspend fun searchResult(query: MetadataQuery): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())

        val primaryResult = primary.search(query)
        val primaryMatches = primaryResult.matchesOrEmpty()
        if (primaryMatches.isNotEmpty()) {
            return LookupResult.Success(primaryMatches.mergeSorted())
        }

        val fallbackResult = fallback.search(query)
        val fallbackMatches = fallbackResult.matchesOrEmpty()
        if (fallbackMatches.isNotEmpty()) {
            return LookupResult.Success((primaryMatches + fallbackMatches).mergeSorted())
        }

        val error = (fallbackResult as? LookupResult.Failure)?.error
            ?: (primaryResult as? LookupResult.Failure)?.error
        return error?.let { LookupResult.Failure(it) } ?: LookupResult.Success(emptyList())
    }

    private fun LookupResult<List<MetadataMatch>>.matchesOrEmpty(): List<MetadataMatch> =
        (this as? LookupResult.Success)?.value.orEmpty()
}

internal fun List<MetadataMatch>.mergeSorted(): List<MetadataMatch> = groupBy { match ->
    MetadataScorer.normalize(match.title) to MetadataScorer.normalize(match.artist)
}.values
    .map { group -> group.maxBy { it.score } }
    .sortedByDescending { it.score }
