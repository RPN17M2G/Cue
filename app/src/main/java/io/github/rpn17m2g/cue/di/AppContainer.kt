package io.github.rpn17m2g.cue.di

import android.content.Context
import android.util.Log
import io.github.rpn17m2g.cue.BuildConfig
import io.github.rpn17m2g.cue.audio.FfmpegProcessRunner
import io.github.rpn17m2g.cue.audio.FfmpegRuntime
import io.github.rpn17m2g.cue.data.artwork.ArtworkRepository
import io.github.rpn17m2g.cue.data.audio.FfmpegAudioSampleExtractor
import io.github.rpn17m2g.cue.data.fingerprint.FfmpegFingerprintExtractor
import io.github.rpn17m2g.cue.data.input.ContentResolverDocumentEditor
import io.github.rpn17m2g.cue.data.input.DocumentEditor
import io.github.rpn17m2g.cue.data.input.InputResolver
import io.github.rpn17m2g.cue.data.input.ResolvedInput
import io.github.rpn17m2g.cue.data.library.MediaLibrary
import io.github.rpn17m2g.cue.data.library.ThumbnailStore
import io.github.rpn17m2g.cue.data.metadata.FingerprintInput
import io.github.rpn17m2g.cue.data.metadata.LookupError
import io.github.rpn17m2g.cue.data.metadata.LookupPolicy
import io.github.rpn17m2g.cue.data.metadata.LookupResult
import io.github.rpn17m2g.cue.data.metadata.MetadataClients
import io.github.rpn17m2g.cue.data.metadata.MetadataEnricher
import io.github.rpn17m2g.cue.data.metadata.MetadataQuery
import io.github.rpn17m2g.cue.data.metadata.NetworkGate
import io.github.rpn17m2g.cue.data.ocr.MediaVideoFrameExtractor
import io.github.rpn17m2g.cue.data.ocr.MlKitOcrEngine
import io.github.rpn17m2g.cue.data.ocr.TesseractHebrewOcrEngine
import io.github.rpn17m2g.cue.data.output.MediaStoreOutputStore
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.output.SourceDeleter
import io.github.rpn17m2g.cue.data.session.SessionRepository
import io.github.rpn17m2g.cue.data.settings.DataStoreSettingsRepository
import io.github.rpn17m2g.cue.domain.identify.FingerprintAttempt
import io.github.rpn17m2g.cue.domain.identify.FingerprintExtractor
import io.github.rpn17m2g.cue.domain.identify.AudioRecognitionAttempt
import io.github.rpn17m2g.cue.domain.identify.Confidence
import io.github.rpn17m2g.cue.domain.identify.ConfidenceTier
import io.github.rpn17m2g.cue.domain.identify.IdentificationCascade
import io.github.rpn17m2g.cue.domain.identify.IdentifySongUseCase
import io.github.rpn17m2g.cue.domain.identify.LookupSeed
import io.github.rpn17m2g.cue.domain.identify.SongIdentification
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.ocr.DualOcrRecognizer
import io.github.rpn17m2g.cue.domain.ocr.FrameExtractor
import io.github.rpn17m2g.cue.domain.ocr.OcrScript
import io.github.rpn17m2g.cue.processing.ProcessingSession
import io.github.rpn17m2g.cue.processing.ProcessSongUseCase
import io.github.rpn17m2g.cue.settings.AppSettings
import io.github.rpn17m2g.cue.settings.SettingsRepository
import io.github.rpn17m2g.cue.tags.ArtworkEncoder
import io.github.rpn17m2g.cue.tags.BitmapArtworkEncoder
import io.github.rpn17m2g.cue.tags.Mp3TagStore
import io.github.rpn17m2g.cue.tags.TagStore
import io.github.rpn17m2g.cue.ui.navigation.ErrorReporter
import io.github.rpn17m2g.cue.ui.navigation.NavigationRequests
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }

    val inputResolver: InputResolver by lazy { InputResolver(appContext) }

    val mediaLibrary: MediaLibrary by lazy { MediaLibrary(appContext) }

    val thumbnailStore: ThumbnailStore by lazy { ThumbnailStore(appContext) }

    val ffmpegRuntime: FfmpegRuntime by lazy {
        FfmpegRuntime(appContext.applicationInfo.nativeLibraryDir)
    }

    val ffmpegRunner by lazy {
        FfmpegProcessRunner(
            binaryPath = ffmpegRuntime.binaryPath,
            ioDispatcher = Dispatchers.IO,
            environment = ffmpegRuntime.environment,
        )
    }

    val tagStore: TagStore by lazy { Mp3TagStore() }

    val artworkEncoder: ArtworkEncoder by lazy { BitmapArtworkEncoder() }

    val documentEditor: DocumentEditor by lazy { ContentResolverDocumentEditor(appContext) }

    val outputStore: OutputStore by lazy { MediaStoreOutputStore(appContext) }

    val sourceDeleter: SourceDeleter by lazy { SourceDeleter(appContext) }

    val artworkRepository: ArtworkRepository by lazy {
        ArtworkRepository(
            context = appContext,
            clients = { metadataClients() },
            encoder = artworkEncoder,
        )
    }

    val processSongUseCase: ProcessSongUseCase by lazy {
        ProcessSongUseCase(
            runner = ffmpegRunner,
            tagStore = tagStore,
            outputStore = outputStore,
            documentEditor = documentEditor,
            tempDirectory = File(appContext.cacheDir, "processing"),
        )
    }

    val processingSession = ProcessingSession()

    val sessionRepository = SessionRepository()

    val navigationRequests = NavigationRequests()

    val errorReporter = ErrorReporter()

    private val frameExtractor: FrameExtractor by lazy { MediaVideoFrameExtractor() }

    private val ocrRecognizer: DualOcrRecognizer by lazy {
        DualOcrRecognizer(
            latin = MlKitOcrEngine(appContext),
            hebrew = TesseractHebrewOcrEngine(appContext),
        )
    }

    private val identifySongUseCase: IdentifySongUseCase by lazy {
        IdentifySongUseCase(
            tagStore = tagStore,
            frameExtractor = frameExtractor,
            recognizer = ocrRecognizer,
            framesRoot = File(appContext.cacheDir, "frames"),
        )
    }

    private val fingerprintExtractor: FingerprintExtractor by lazy {
        FfmpegFingerprintExtractor(
            runner = ffmpegRunner,
            outputDirectory = File(appContext.cacheDir, "fingerprints"),
        )
    }

    private val audioSampleExtractor by lazy {
        FfmpegAudioSampleExtractor(
            runner = ffmpegRunner,
            outputDirectory = File(appContext.cacheDir, "samples"),
        )
    }

    private val metadataLock = Mutex()
    private var metadataCache: MetadataCache? = null

    private data class MetadataCache(
        val policy: LookupPolicy,
        val clients: MetadataClients,
    )

    /**
     * Rebuilds the clients when lookup settings change. A cached OkHttp client
     * would otherwise keep the old timeout, retry count, network gate, and
     * AcoustID key for the rest of the process.
     */
    suspend fun metadataClients(): MetadataClients {
        val policy = LookupPolicy.from(settingsRepository.settings.first())
        metadataCache?.let { cached ->
            if (cached.policy == policy) return cached.clients
        }
        return metadataLock.withLock {
            val cached = metadataCache
            if (cached != null && cached.policy == policy) {
                cached.clients
            } else {
                MetadataClients(policy).also { metadataCache = MetadataCache(policy, it) }
            }
        }
    }

    suspend fun identificationCascade(inputPath: String): IdentificationCascade {
        val clients = metadataClients()
        return IdentificationCascade(
            lookup = { seed -> clients.resolver.search(MetadataQuery(seed.title, seed.artist)) },
            fingerprintLookup = fingerprintLookup@{ seed ->
                val input = File(inputPath)
                if (!input.isFile) return@fingerprintLookup FingerprintAttempt()
                val fingerprint = fingerprintExtractor.extract(input, seed.durationMs)
                    ?: return@fingerprintLookup FingerprintAttempt()
                val result = clients.acoustId.lookup(
                    FingerprintInput(fingerprint.value, fingerprint.durationSeconds),
                )
                when (result) {
                    is LookupResult.Success -> FingerprintAttempt(result.value)
                    is LookupResult.Failure -> FingerprintAttempt(
                        keyRejected = result.error == LookupError.RejectedKey,
                    )
                }
            },
            audioLookup = audioLookup@{ durationMs ->
                val input = File(inputPath)
                if (!input.isFile) return@audioLookup AudioRecognitionAttempt()
                val client = clients.acrCloud ?: return@audioLookup AudioRecognitionAttempt()
                val sample = audioSampleExtractor.extract(input, durationMs)
                    ?: return@audioLookup AudioRecognitionAttempt()
                try {
                    when (val result = client.identify(sample)) {
                        is LookupResult.Success -> AudioRecognitionAttempt(result.value)
                        is LookupResult.Failure -> AudioRecognitionAttempt(
                            keyRejected = result.error == LookupError.RejectedKey,
                        )
                    }
                } finally {
                    sample.delete()
                }
            },
        )
    }

    suspend fun identifySong(
        input: ResolvedInput,
        embeddedTags: TagDraft?,
        settings: AppSettings,
        preferHebrew: Boolean,
        forceFingerprint: Boolean = false,
        manualQuery: LookupSeed? = null,
        ocrScript: OcrScript = OcrScript.AUTO,
    ): SongIdentification {
        val identification = identifySongUseCase.identify(
            input = input,
            embeddedTags = embeddedTags,
            settings = settings,
            preferHebrew = preferHebrew,
            cascade = identificationCascade(input.path),
            forceFingerprint = forceFingerprint,
            manualQuery = manualQuery,
            ocrScript = ocrScript,
        )
        if (!settings.networkLookups || !settings.enrichmentEnabled) {
            logTimings(input, identification)
            return identification
        }

        val outcome = identification.outcome
        val confident = outcome.usedEmbeddedTags ||
            outcome.bestMatch?.let { Confidence.tier(it.score) == ConfidenceTier.ACCEPTED } == true
        if (!confident) {
            logTimings(input, identification)
            return identification
        }

        val clients = metadataClients()
        val durationSeconds = (identification.trackDurationMs ?: input.durationMs)
            ?.takeIf { it > 0L }
            ?.let { (it / 1_000L).toInt() }
            ?.takeIf { it > 0 }
        val enrichmentStarted = System.nanoTime()
        val enriched = MetadataEnricher(
            lyrics = { title, artist, album, duration ->
                clients.lyrics.fetchOrNull(title, artist, album, duration)
            },
            bpm = { title, artist ->
                clients.deezer.lookupOrNull(title, artist)
            },
            networkGate = NetworkGate { settings.networkLookups },
        ).enrich(outcome.tag, durationSeconds)
        val enrichmentMs = (System.nanoTime() - enrichmentStarted) / 1_000_000L

        val enrichedIdentification = identification.copy(
            outcome = outcome.copy(
                tag = enriched.tag,
                sources = outcome.sources + enriched.sources,
            ),
            timings = identification.timings.copy(enrichmentMs = enrichmentMs),
        )
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "enriched ${input.displayName}: lyrics=${enriched.tag.lyrics != null} " +
                    "bpm=${enriched.tag.bpm ?: "-"} isrc=${enriched.tag.isrc ?: "-"} " +
                    "label=${enriched.tag.label ?: "-"} " +
                    "mbids=${enriched.tag.recordingMbid != null}",
            )
        }
        logTimings(input, enrichedIdentification)
        return enrichedIdentification
    }

    /**
     * One line per identification in debug builds. Stage timings make a slow
     * recording attributable: frame extraction, the parallel OCR passes, the
     * network cascade, and enrichment are reported separately.
     */
    private fun logTimings(input: ResolvedInput, identification: SongIdentification) {
        if (!BuildConfig.DEBUG) return
        val timings = identification.timings
        Log.d(
            TAG,
            "identify ${input.displayName}: frames=${timings.framesMs}ms " +
                "(${timings.frameCount} frames, ${timings.perFrameMs}ms/frame) " +
                "ocr=${timings.ocrMs}ms (latin=${timings.latinOcrMs}ms, " +
                "hebrew=${timings.hebrewOcrMs}ms) cascade=${timings.cascadeMs}ms " +
                "enrichment=${timings.enrichmentMs}ms total=${timings.totalMs}ms",
        )
    }

    private companion object {
        const val TAG = "CueTimings"
    }
}
