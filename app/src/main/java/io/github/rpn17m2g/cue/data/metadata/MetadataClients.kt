package io.github.rpn17m2g.cue.data.metadata

object MetadataEndpoints {
    const val ITUNES = "https://itunes.apple.com/"
    const val MUSICBRAINZ = "https://musicbrainz.org/"
    const val ACOUSTID = "https://api.acoustid.org/"
}

/**
 * Production wiring for the metadata layer. Tests construct the clients directly
 * against MockWebServer instead of using this factory.
 */
class MetadataClients(
    policy: LookupPolicy = LookupPolicy(),
    config: NetworkConfig = NetworkConfig.from(policy),
    sleeper: Sleeper = Sleeper.System,
    delayProvider: DelayProvider = DelayProvider.Default,
    clock: WallClock = WallClock.System,
    http: MetadataHttp = MetadataHttp(config, sleeper),
) {
    private val gate = policy.networkGate

    val iTunes: ITunesClient = ITunesClient(
        api = http.api(MetadataEndpoints.ITUNES, ITunesApi::class.java),
        networkGate = gate,
    )

    val musicBrainz: MusicBrainzClient = MusicBrainzClient(
        api = http.api(MetadataEndpoints.MUSICBRAINZ, MusicBrainzApi::class.java),
        rateLimiter = RateLimiter(clock = clock, delay = delayProvider),
        networkGate = gate,
    )

    val acoustId: AcoustIdClient = AcoustIdClient(
        api = http.api(MetadataEndpoints.ACOUSTID, AcoustIdApi::class.java),
        defaultApiKey = policy.acoustIdKey,
        networkGate = gate,
    )

    val resolver: CompositeMetadataResolver = CompositeMetadataResolver(iTunes, musicBrainz, gate)

    val artworkSearch: ArtworkSearch get() = iTunes
}
