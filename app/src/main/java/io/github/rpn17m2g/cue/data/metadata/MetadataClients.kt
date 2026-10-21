package io.github.rpn17m2g.cue.data.metadata

object MetadataEndpoints {
    const val ITUNES = "https://itunes.apple.com/"
    const val MUSICBRAINZ = "https://musicbrainz.org/"
    const val ACOUSTID = "https://api.acoustid.org/"
    const val COVER_ART_ARCHIVE = "https://coverartarchive.org/"
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

    private val musicBrainzHttp = http.clientWith(
        RateLimitInterceptor(RateLimiter(clock = clock, sleeper = sleeper)),
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
        api = http.api(MetadataEndpoints.ACOUSTID, AcoustIdApi::class.java),
        defaultApiKey = policy.acoustIdKey,
        networkGate = gate,
    )

    val resolver: CompositeMetadataResolver = CompositeMetadataResolver(iTunes, musicBrainz, gate)

    val artworkSearch: ArtworkSearch get() = iTunes
}
