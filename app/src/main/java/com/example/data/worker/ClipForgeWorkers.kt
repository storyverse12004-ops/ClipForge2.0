package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.data.local.ClipForgeDatabase
import com.example.data.media.Media3ClipExporter
import com.example.data.remote.ClipForgeCloudClient
import com.example.data.repository.ClipForgeRepository

object WorkNotifications {
    const val CHANNEL_ID = "clipforge_media_processing"
    const val PIPELINE_NOTIFICATION_ID = 4101
    const val EXPORT_NOTIFICATION_ID = 4102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ClipForge Video Processing & Rendering",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows real-time progress for clip detection and video exports."
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    fun createForegroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
        status: String,
        progress: Int
    ): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(status)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}

class VideoPipelineWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return WorkNotifications.createForegroundInfo(
            context = applicationContext,
            notificationId = WorkNotifications.PIPELINE_NOTIFICATION_ID,
            title = "Processing Video",
            status = "Analyzing video and detecting moments...",
            progress = 5
        )
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, -1L)
        if (projectId <= 0L) return Result.failure(workDataOf(KEY_ERROR to "Invalid project ID"))

        ClipForgeCloudClient.init(applicationContext)
        val removeSilence = inputData.getBoolean(KEY_REMOVE_SILENCE, true)
        val snapSentences = inputData.getBoolean(KEY_SNAP_SENTENCES, true)
        val speakerDiarization = inputData.getBoolean(KEY_SPEAKER_DIARIZATION, true)

        val db = ClipForgeDatabase.getDatabase(applicationContext)
        val repository = ClipForgeRepository(
            context = applicationContext,
            projectDao = db.projectDao(),
            clipDao = db.clipDao(),
            metadataDao = db.platformMetadataDao(),
            exportDao = db.exportDao()
        )

        return try {
            try {
                setForeground(getForegroundInfo())
            } catch (_: Exception) {}

            repository.runVideoPipelineForExistingProject(
                projectId = projectId,
                removeSilenceDefault = removeSilence,
                snapSentencesDefault = snapSentences,
                speakerDiarizationDefault = speakerDiarization
            ) { stepIndex, stepName, progress ->
                val pct = (progress * 100).toInt().coerceIn(0, 100)
                setProgressAsync(
                    workDataOf(
                        KEY_STEP_INDEX to stepIndex,
                        KEY_STEP_NAME to stepName,
                        KEY_PROGRESS to progress
                    )
                )
                try {
                    setForegroundAsync(
                        WorkNotifications.createForegroundInfo(
                            context = applicationContext,
                            notificationId = WorkNotifications.PIPELINE_NOTIFICATION_ID,
                            title = "Processing Video",
                            status = stepName,
                            progress = pct
                        )
                    )
                } catch (_: Exception) {}
            }

            Result.success(workDataOf(KEY_PROJECT_ID to projectId))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Video processing failed."
            Result.failure(workDataOf(KEY_ERROR to msg, KEY_PROJECT_ID to projectId))
        }
    }

    companion object {
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_REMOVE_SILENCE = "remove_silence"
        const val KEY_SNAP_SENTENCES = "snap_sentences"
        const val KEY_SPEAKER_DIARIZATION = "speaker_diarization"
        const val KEY_STEP_INDEX = "step_index"
        const val KEY_STEP_NAME = "step_name"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        fun uniqueWorkName(projectId: Long) = "clipforge_pipeline_project_$projectId"
    }
}

class ClipExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return WorkNotifications.createForegroundInfo(
            context = applicationContext,
            notificationId = WorkNotifications.EXPORT_NOTIFICATION_ID,
            title = "Exporting Video",
            status = "Preparing export...",
            progress = 1
        )
    }

    override suspend fun doWork(): Result {
        val exportId = inputData.getLong(KEY_EXPORT_ID, -1L)
        val clipId = inputData.getLong(KEY_CLIP_ID, -1L)
        if (exportId <= 0L || clipId <= 0L) {
            return Result.failure(workDataOf(KEY_ERROR to "Missing export or clip ID"))
        }

        val db = ClipForgeDatabase.getDatabase(applicationContext)
        val clipDao = db.clipDao()
        val projectDao = db.projectDao()
        val exportDao = db.exportDao()

        val exportEntity = exportDao.getExportById(exportId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Export job record not found"))
        val clip = clipDao.getClipById(clipId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Clip record not found"))
        val project = projectDao.getProjectById(clip.projectId)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Source project not found"))

        return try {
            try {
                setForeground(getForegroundInfo())
            } catch (_: Exception) {}

            val words = ClipForgeCloudClient.deserializeWords(clip.wordsJson)
            val sourceUri = Uri.parse(project.sourceUrl)

            val output = Media3ClipExporter.exportClip(
                context = applicationContext,
                sourceVideoUri = sourceUri,
                clip = clip,
                words = words,
                quality = exportEntity.quality,
                format = exportEntity.format,
                fps = exportEntity.fps,
                burnedCaptions = exportEntity.burnedCaptions,
                removeWatermark = exportEntity.removeWatermark,
                includeSrt = exportEntity.includeSrt
            ) { progressPct ->
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to progressPct,
                        KEY_EXPORT_ID to exportId
                    )
                )
                try {
                    setForegroundAsync(
                        WorkNotifications.createForegroundInfo(
                            context = applicationContext,
                            notificationId = WorkNotifications.EXPORT_NOTIFICATION_ID,
                            title = "Exporting ${exportEntity.quality} ${exportEntity.format}",
                            status = "Exporting video ($progressPct%)...",
                            progress = progressPct
                        )
                    )
                } catch (_: Exception) {}
            }

            exportDao.updateExport(
                exportEntity.copy(
                    status = "COMPLETED",
                    progress = 100,
                    estimatedSizeMb = output.fileSizeMb,
                    downloadUrl = output.galleryUriString,
                    srtUri = output.srtUriString,
                    errorMessage = ""
                )
            )

            Result.success(
                workDataOf(
                    KEY_EXPORT_ID to exportId,
                    KEY_DOWNLOAD_URI to output.galleryUriString,
                    KEY_SRT_URI to output.srtUriString
                )
            )
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Export failed during video encoding."
            exportDao.updateExport(
                exportEntity.copy(
                    status = "FAILED",
                    errorMessage = errorMsg
                )
            )
            Result.failure(
                workDataOf(
                    KEY_EXPORT_ID to exportId,
                    KEY_ERROR to errorMsg
                )
            )
        }
    }

    companion object {
        const val KEY_EXPORT_ID = "export_id"
        const val KEY_CLIP_ID = "clip_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOAD_URI = "download_uri"
        const val KEY_SRT_URI = "srt_uri"
        const val KEY_ERROR = "error"

        fun uniqueWorkName(clipId: Long) = "clipforge_export_clip_$clipId"
    }
}
