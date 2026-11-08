package io.github.rpn17m2g.cue.di

import android.content.Context
import io.github.rpn17m2g.cue.audio.FfmpegProcessRunner
import io.github.rpn17m2g.cue.audio.FfmpegRuntime
import io.github.rpn17m2g.cue.data.artwork.ArtworkRepository
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
import io.github.rpn17m2g.cue.data.metadata.MetadataQuery
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
import io.github.rpn17m2g.cue.domain.identify.IdentificationCascade
import io.github.rpn17m2g.cue.domain.identify.IdentifySongUseCase
import io.github.rpn17m2g.cue.domain.identify.SongIdentification
import io.github.rpn17m2g.cue.domain.model.TagDraft
import io.github.rpn17m2g.cue.domain.ocr.DualOcrRecognizer
import io.github.rpn17m2g.cue.domain.ocr.FrameExtractor
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
        )
    }

    suspend fun identifySong(
        input: ResolvedInput,
        embeddedTags: TagDraft?,
        settings: AppSettings,
        preferHebrew: Boolean,
        forceFingerprint: Boolean = false,
    ): SongIdentification = identifySongUseCase.identify(
        input = input,
        embeddedTags = embeddedTags,
        settings = settings,
        preferHebrew = preferHebrew,
        cascade = identificationCascade(input.path),
        forceFingerprint = forceFingerprint,
    )
}
