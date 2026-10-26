package io.github.rpn17m2g.cue.di

import android.content.Context
import io.github.rpn17m2g.cue.audio.FfmpegProcessRunner
import io.github.rpn17m2g.cue.audio.FfmpegRuntime
import io.github.rpn17m2g.cue.data.input.InputResolver
import io.github.rpn17m2g.cue.data.metadata.LookupPolicy
import io.github.rpn17m2g.cue.data.metadata.MetadataClients
import io.github.rpn17m2g.cue.data.metadata.MetadataQuery
import io.github.rpn17m2g.cue.data.output.MediaStoreOutputStore
import io.github.rpn17m2g.cue.data.output.OutputStore
import io.github.rpn17m2g.cue.data.session.SessionRepository
import io.github.rpn17m2g.cue.data.settings.DataStoreSettingsRepository
import io.github.rpn17m2g.cue.domain.identify.IdentificationCascade
import io.github.rpn17m2g.cue.processing.ProcessingSession
import io.github.rpn17m2g.cue.processing.ProcessSongUseCase
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

    val outputStore: OutputStore by lazy { MediaStoreOutputStore(appContext) }

    val processSongUseCase: ProcessSongUseCase by lazy {
        ProcessSongUseCase(
            runner = ffmpegRunner,
            tagStore = tagStore,
            outputStore = outputStore,
            tempDirectory = File(appContext.cacheDir, "processing"),
        )
    }

    val processingSession = ProcessingSession()

    val sessionRepository = SessionRepository()

    val navigationRequests = NavigationRequests()

    val errorReporter = ErrorReporter()

    private val metadataLock = Mutex()
    private var metadataClients: MetadataClients? = null

    suspend fun metadataClients(): MetadataClients {
        metadataClients?.let { return it }
        return metadataLock.withLock {
            metadataClients ?: MetadataClients(
                LookupPolicy.from(settingsRepository.settings.first()),
            ).also { metadataClients = it }
        }
    }

    suspend fun identificationCascade(): IdentificationCascade {
        val clients = metadataClients()
        return IdentificationCascade(
            lookup = { seed -> clients.resolver.search(MetadataQuery(seed.title, seed.artist)) },
            fingerprintLookup = { emptyList() },
        )
    }
}
