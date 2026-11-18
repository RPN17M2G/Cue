package io.github.rpn17m2g.cue.processing

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.rpn17m2g.cue.CueApplication
import io.github.rpn17m2g.cue.MainActivity
import io.github.rpn17m2g.cue.R
import io.github.rpn17m2g.cue.audio.FfmpegUnavailableException
import io.github.rpn17m2g.cue.data.input.InputIntentParser
import io.github.rpn17m2g.cue.data.output.SavedSong
import io.github.rpn17m2g.cue.di.AppContainer
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SongProcessingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var container: AppContainer
    private var runningJob: Job? = null
    private var runningInputId: String? = null
    private var runningInPlace = false
    private var lastStage: ProcessingStage? = null
    private var lastPercent = -1

    override fun onCreate() {
        super.onCreate()
        container = (application as CueApplication).container
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runningJob?.cancel()
            return START_NOT_STICKY
        }
        val job = intent?.let(ProcessingJobCodec::read)
        if (job == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (runningJob?.isActive == true) {
            return START_NOT_STICKY
        }
        lastStage = null
        lastPercent = -1
        runningInputId = job.input.id
        runningInPlace = job.mode != ProcessingMode.CONVERT_AND_SAVE
        container.processingSession.running(ProcessingStage.Preparing, 0f)
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            progressNotification(ProcessingStage.Preparing, 0f),
            foregroundServiceType(),
        )
        container.sessionRepository.remember(job)
        runningJob = scope.launch {
            try {
                if (job.needsConversion) {
                    container.ffmpegRuntime.verify()
                }
                val saved = container.processSongUseCase.execute(job) { stage, progress ->
                    publishProgress(stage, progress)
                }
                // Drop the cache copy before the Saved screen offers to delete
                // the original, so a delete leaves nothing behind.
                container.inputResolver.cleanup(job.input)
                container.processingSession.done(saved)
                showCompleted(saved, job.mode)
            } catch (cancelled: CancellationException) {
                container.processingSession.reset()
                finishForeground()
                throw cancelled
            } catch (e: Exception) {
                container.processingSession.failed(failureReason(e), failureDetails(e))
                showFailed()
            } finally {
                ProcessingJobCodec.cleanup(intent)
                runningJob = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun publishProgress(stage: ProcessingStage, progress: Float) {
        container.processingSession.running(stage, progress)
        val percent = (progress * 100).roundToInt().coerceIn(0, 100)
        if (stage == lastStage && percent == lastPercent) return
        lastStage = stage
        lastPercent = percent
        postNotification(FOREGROUND_NOTIFICATION_ID, progressNotification(stage, progress))
    }

    private fun showCompleted(song: SavedSong, mode: ProcessingMode) {
        finishForeground()
        postNotification(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_cue)
                .setContentTitle(
                    getString(
                        when {
                            mode == ProcessingMode.TRIM_IN_PLACE -> R.string.result_title_trimmed
                            song.inPlaceEdit -> R.string.notification_tags_updated_title
                            else -> R.string.notification_done_title
                        },
                    ),
                )
                .setContentText(song.displayName)
                .setAutoCancel(true)
                .setContentIntent(openApp(InputIntentParser.OPEN_RESULT, RESULT_REQUEST_CODE))
                .build(),
        )
    }

    private fun showFailed() {
        finishForeground()
        postNotification(
            COMPLETION_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_cue)
                .setContentTitle(getString(R.string.notification_failed_title))
                .setContentText(getString(R.string.notification_failed_text))
                .setAutoCancel(true)
                .setContentIntent(openApp(InputIntentParser.OPEN_ERROR, ERROR_REQUEST_CODE))
                .build(),
        )
    }

    @SuppressLint("MissingPermission") // Guarded by canPostNotifications().
    private fun postNotification(id: Int, notification: Notification) {
        if (!canPostNotifications()) return
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun finishForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun progressNotification(stage: ProcessingStage, progress: Float): Notification {
        val percent = (progress * 100).roundToInt().coerceIn(0, 100)
        val text = getString(R.string.notification_progress_text, stageLabel(stage), percent)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cue)
            .setContentTitle(
                getString(
                    if (runningInPlace) {
                        R.string.notification_updating_title
                    } else {
                        R.string.notification_processing_title
                    },
                ),
            )
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(InputIntentParser.OPEN_PROCESSING, RESULT_REQUEST_CODE, runningInputId))
            .addAction(
                R.drawable.ic_stat_cue,
                getString(R.string.processing_cancel),
                cancelAction(),
            )
        if (stage == ProcessingStage.Converting) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun stageLabel(stage: ProcessingStage): String = getString(
        when (stage) {
            ProcessingStage.Preparing -> R.string.processing_stage_prepare
            ProcessingStage.Converting -> R.string.processing_stage_convert
            ProcessingStage.Tagging -> R.string.processing_stage_tag
            ProcessingStage.Saving -> R.string.processing_stage_save
        },
    )

    private fun openApp(
        target: String,
        requestCode: Int,
        inputId: String? = null,
    ): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(InputIntentParser.EXTRA_OPEN_TARGET, target)
            inputId?.let { putExtra(InputIntentParser.EXTRA_OPEN_INPUT_ID, it) }
        }
        return PendingIntent.getActivity(this, requestCode, intent, PENDING_FLAGS)
    }

    private fun cancelAction(): PendingIntent {
        val intent = Intent(this, SongProcessingService::class.java).setAction(ACTION_CANCEL)
        return PendingIntent.getService(this, CANCEL_REQUEST_CODE, intent, PENDING_FLAGS)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_processing),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_processing_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * The manifest declares dataSync (copy and tag-only edits) as well as
     * mediaProcessing (transcoding). Passing the declared value keeps the
     * service valid: requesting mediaProcessing on its own is refused by
     * Android 16 (API 36) for a targetSdk 35 app with
     * InvalidForegroundServiceTypeException "type none".
     */
    private fun foregroundServiceType(): Int = packageManager
        .getServiceInfo(ComponentName(this, SongProcessingService::class.java), 0)
        .foregroundServiceType

    private fun failureReason(e: Exception): String = when (e) {
        is FfmpegUnavailableException -> getString(R.string.error_ffmpeg_unavailable)
        else -> getString(R.string.error_processing_failed)
    }

    private fun failureDetails(e: Exception): String = when (e) {
        is ProcessingException ->
            (e.details + listOfNotNull(e.message)).distinct().joinToString("\n")
        else -> e.stackTraceToString()
    }

    companion object {
        const val ACTION_START = "io.github.rpn17m2g.cue.action.START"
        const val ACTION_CANCEL = "io.github.rpn17m2g.cue.action.CANCEL"

        private const val CHANNEL_ID = "processing"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val RESULT_REQUEST_CODE = 2001
        private const val ERROR_REQUEST_CODE = 2002
        private const val CANCEL_REQUEST_CODE = 2003
        private const val PENDING_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun start(context: Context, job: ProcessingJob) {
            val intent = Intent(context, SongProcessingService::class.java).apply {
                action = ACTION_START
                ProcessingJobCodec.write(context, this, job)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, SongProcessingService::class.java)
                .setAction(ACTION_CANCEL)
            context.startService(intent)
        }
    }
}
