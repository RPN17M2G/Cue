package io.github.rpn17m2g.cue.data.metadata

import okhttp3.OkHttpClient

object MetadataEndpoints {
    const val ITUNES = "https://itunes.apple.com/"
    const val MUSICBRAINZ = "https://musicbrainz.org/"
    const val ACOUSTID = "https://api.acoustid.org/"
    const val COVER_ART_ARCHIVE = "https://coverartarchive.org/"
}

/**
 * Provider pacing. MusicBrainz asks for at most one request per second; AcoustID
 * publishes three per second, but Cue sends at most one fingerprint lookup per
 * processed file and keeps a full 20 s between them, so a burst of retries can
 * never come close to either ceiling.
 */
internal object ProviderRateLimits {
    const val MUSICBRAINZ_MIN_INTERVAL_MS = 1_000L
    const val ACOUSTID_MIN_INTERVAL_MS = 20_000L
}

private const val COVER_ART_TIMEOUT_MS = 3_000L

/**
 * Production wiring for the metadata layer. Tests construct the clients directly
 * against MockWebServer instead of using this factory.
 */
class MetadataClients(
    policy: LookupPolicy = LookupPolicy(),
    config: NetworkConfig = NetworkConfig.from(policy),
    sleeper: Sleeper = Sleeper.System,
    clock: WallClock = WallClock.Monotonic,
    http: MetadataHttp = MetadataHttp(config, sleeper),
) {
    private val gate = policy.networkGate

    internal val musicBrainzHttp: OkHttpClient = http.clientWith(
        RateLimitInterceptor(
            RateLimiter(ProviderRateLimits.MUSICBRAINZ_MIN_INTERVAL_MS, clock, sleeper),
        ),
    )

    internal val acoustIdHttp: OkHttpClient = http.clientWith(
        RateLimitInterceptor(
            RateLimiter(ProviderRateLimits.ACOUSTID_MIN_INTERVAL_MS, clock, sleeper),
        ),
    )

    private val coverArtHttp = MetadataHttp(
        config = NetworkConfig(
            timeoutMs = COVER_ART_TIMEOUT_MS,
            retries = 0,
            backoffBaseMs = 0L,
            userAgent = config.userAgent,
        ),
        sleeper = sleeper,
    )

    private val coverArtArchive = CoverArtArchiveClient(
        api = coverArtHttp.api(
            MetadataEndpoints.COVER_ART_ARCHIVE,
            CoverArtArchiveApi::class.java,
        ),
        networkGate = gate,
    )

    val iTunes: ITunesClient = ITunesClient(
        api = http.api(MetadataEndpoints.ITUNES, ITunesApi::class.java),
        networkGate = gate,
    )

    val musicBrainz: MusicBrainzClient = MusicBrainzClient(
        api = http.api(
            MetadataEndpoints.MUSICBRAINZ,
            MusicBrainzApi::class.java,
            musicBrainzHttp,
        ),
        networkGate = gate,
        coverArtArchive = coverArtArchive,
    )

    val acoustId: AcoustIdClient = AcoustIdClient(
        api = http.api(MetadataEndpoints.ACOUSTID, AcoustIdApi::class.java, acoustIdHttp),
        defaultApiKey = policy.acoustIdKey,
        networkGate = gate,
    )

    val resolver: CompositeMetadataResolver = CompositeMetadataResolver(iTunes, musicBrainz, gate)

    val artworkSearch: ArtworkSearch get() = iTunes

    val artworkFetcher: ArtworkFetcher = HttpArtworkFetcher(coverArtHttp.client)
}
