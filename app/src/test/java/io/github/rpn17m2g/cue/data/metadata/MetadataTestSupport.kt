package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import okhttp3.mockwebserver.MockResponse

internal fun jsonResponse(body: String): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Content-Type", "application/json")
    .setBody(body)

internal class FakeProvider(
    override val source: IdentificationSource,
    private val result: LookupResult<List<MetadataMatch>>,
) : MetadataProvider {
    var calls: Int = 0
        private set

    override suspend fun search(query: MetadataQuery): LookupResult<List<MetadataMatch>> {
        calls++
        return result
    }
}

internal fun match(
    title: String,
    artist: String,
    score: Double,
    source: IdentificationSource = IdentificationSource.ITUNES,
): MetadataMatch = MetadataMatch(
    title = title,
    artist = artist,
    source = source,
    score = score,
)
