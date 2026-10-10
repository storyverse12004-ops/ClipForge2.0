package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.example.data.local.ClipDao
import com.example.data.local.ExportDao
import com.example.data.local.PlatformMetadataDao
import com.example.data.local.ProjectDao
import com.example.data.media.AudioExtractor
import com.example.data.model.CaptionPreset
import com.example.data.model.ClipEntity
import com.example.data.model.ExportEntity
import com.example.data.model.PlatformMetadataEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.ProjectStatus
import com.example.data.model.ReframeMode
import com.example.data.model.SourceType
import com.example.data.model.WordTimestamp
import com.example.data.remote.ClipForgeCloudClient
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ClipForgeRepository(
    private val context: Context,
    private val projectDao: ProjectDao,
    private val clipDao: ClipDao,
    private val metadataDao: PlatformMetadataDao,
    private val exportDao: ExportDao
) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()
    val allExports: Flow<List<ExportEntity>> = exportDao.getAllExports()

    fun getClipsForProject(projectId: Long): Flow<List<ClipEntity>> = clipDao.getClipsForProject(projectId)
    fun observeProjectById(projectId: Long): Flow<ProjectEntity?> = projectDao.observeProjectById(projectId)
    suspend fun getProjectById(projectId: Long): ProjectEntity? = projectDao.getProjectById(projectId)
    suspend fun getClipById(clipId: Long): ClipEntity? = clipDao.getClipById(clipId)
    fun getMetadataForClip(clipId: Long): Flow<PlatformMetadataEntity?> = metadataDao.getMetadataForClip(clipId)
    fun observeExportById(exportId: Long): Flow<ExportEntity?> = exportDao.observeExportById(exportId)

    suspend fun updateClip(clip: ClipEntity) = clipDao.updateClip(clip)
    suspend fun updateMetadata(metadata: PlatformMetadataEntity) = metadataDao.updateMetadata(metadata)

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectById(projectId)
        if (project != null && project.thumbnailPath.isNotBlank()) {
            try {
                java.io.File(project.thumbnailPath).delete()
            } catch (_: Exception) {}
        }
        val clips = clipDao.getClipsForProjectDirect(projectId)
        for (clip in clips) {
            metadataDao.deleteMetadataForClip(clip.id)
            exportDao.deleteExportsForClip(clip.id)
        }
        clipDao.deleteClipsForProject(projectId)
        projectDao.deleteProjectById(projectId)
    }

    suspend fun createPendingProject(
        title: String,
        sourceUri: String,
        durationSeconds: Int,
        videoSizeMb: Float,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        thumbnailPath: String = ""
    ): Long = withContext(Dispatchers.IO) {
        projectDao.insertProject(
            ProjectEntity(
                title = title.ifBlank { "Video Project #${System.currentTimeMillis() % 1000}" },
                sourceType = SourceType.UPLOAD,
                sourceUrl = sourceUri,
                durationSeconds = durationSeconds,
                videoSizeMb = videoSizeMb,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                thumbnailPath = thumbnailPath,
                status = ProjectStatus.INGESTING,
                clipCount = 0,
                errorMessage = ""
            )
        )
    }

    suspend fun runVideoPipelineForExistingProject(
        projectId: Long,
        removeSilenceDefault: Boolean = true,
        snapSentencesDefault: Boolean = true,
        speakerDiarizationDefault: Boolean = true,
        onProgress: (stepIndex: Int, stepName: String, progress: Float) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        ClipForgeCloudClient.init(context)
        val project = projectDao.getProjectById(projectId)
            ?: throw IllegalStateException("Project #$projectId not found.")

        try {
            ensureSufficientDiskSpace(minFreeBytes = 50L * 1024L * 1024L)
            val videoUri = Uri.parse(project.sourceUrl)
            ensureSourceVideoAccessible(videoUri)

            val totalDurationMs = (project.durationSeconds * 1000L).coerceAtLeast(10_000L)

            val existingWords = if (project.transcriptJson.isNotBlank()) {
                ClipForgeCloudClient.deserializeWords(project.transcriptJson)
            } else {
                emptyList()
            }

            val allWords: List<WordTimestamp>
            val serializedFullTranscript: String

            if (existingWords.isNotEmpty()) {
                onProgress(
                    2,
                    "Resuming with saved transcript (${existingWords.size} words)...",
                    0.45f
                )
                allWords = existingWords
                serializedFullTranscript = project.transcriptJson
            } else {
                // Step 0: IMPORTING
                onProgress(0, "Importing video and preparing file...", 0.05f)

                // Step 1: EXTRACTING_AUDIO
                projectDao.updateProject(project.copy(status = ProjectStatus.EXTRACTING_AUDIO, errorMessage = ""))
                onProgress(1, "Extracting audio track from video...", 0.08f)

                val audioChunks = try {
                    AudioExtractor.extractAudioChunks(
                        context = context,
                        videoUri = videoUri,
                        totalDurationMs = totalDurationMs
                    ) { extractProg ->
                        val scaled = (0.08f + extractProg * 0.22f).coerceIn(0.08f, 0.30f)
                        onProgress(1, "Extracting audio (${(extractProg * 100).toInt()}%)...", scaled)
                    }
                } catch (e: SecurityException) {
                    throw IllegalStateException("Video permission was lost. Please re-import the video.", e)
                } catch (e: Exception) {
                    throw IllegalStateException("Audio extraction failed: ${e.localizedMessage ?: "unable to read audio"}", e)
                }

                if (audioChunks.isEmpty()) {
                    throw IllegalStateException("No readable audio track found in the video.")
                }

                // Step 2: TRANSCRIBING
                projectDao.updateProject(project.copy(status = ProjectStatus.TRANSCRIBING, errorMessage = ""))
                onProgress(2, "Transcribing speech from audio...", 0.33f)

                allWords = try {
                    ClipForgeCloudClient.transcribeAudioChunks(audioChunks) { chunkIdx, totalChunks ->
                        val ratio = chunkIdx.toFloat() / totalChunks.coerceAtLeast(1).toFloat()
                        val scaled = (0.33f + ratio * 0.25f).coerceIn(0.33f, 0.58f)
                        onProgress(2, "Transcribing speech (part $chunkIdx of $totalChunks)...", scaled)
                    }
                } finally {
                    AudioExtractor.cleanupAudioChunks(audioChunks)
                }

                if (allWords.isEmpty()) {
                    throw IllegalStateException("No spoken speech was detected in the video.")
                }

                serializedFullTranscript = ClipForgeCloudClient.serializeWords(allWords)
                projectDao.updateProject(
                    project.copy(
                        status = ProjectStatus.DETECTING,
                        transcriptJson = serializedFullTranscript,
                        errorMessage = ""
                    )
                )
                onProgress(2, "Transcribed ${allWords.size} words with timestamps", 0.60f)
            }

            // Step 3: FINDING_MOMENTS
            projectDao.updateProject(
                project.copy(
                    status = ProjectStatus.DETECTING,
                    transcriptJson = serializedFullTranscript
                )
            )
            onProgress(3, "Analyzing transcript for top viral moments...", 0.65f)

            val viralMoments = ClipForgeCloudClient.detectViralMoments(
                words = allWords,
                videoDurationMs = totalDurationMs,
                snapToSentences = snapSentencesDefault
            )
            onProgress(3, "Found ${viralMoments.size} viral moments", 0.72f)

            // Step 4: WRITING_TITLES
            onProgress(4, "Generating viral hooks, titles and hashtags...", 0.76f)

            // Step 5: REFRAMING
            projectDao.updateProject(
                project.copy(
                    status = ProjectStatus.REFRAMING,
                    transcriptJson = serializedFullTranscript
                )
            )
            onProgress(5, "Setting up 9:16 vertical framing...", 0.82f)

            // Step 6: ADDING_CAPTIONS
            onProgress(6, "Syncing animated subtitle presets and word timings...", 0.87f)

            clipDao.deleteClipsForProject(projectId)
            val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
            val stringListAdapter = ClipForgeCloudClient.moshi.adapter<List<String>>(stringListType)
            val presets = CaptionPreset.entries
            val reframeModes = ReframeMode.entries

            for ((idx, moment) in viralMoments.withIndex()) {
                val clipWords = allWords.filter { it.endMs >= moment.startMs && it.startMs <= moment.endMs }
                    .ifEmpty { allWords }
                val clipWordsJson = ClipForgeCloudClient.serializeWords(clipWords)
                val hookTitle = moment.resolvedTitle
                val oneLineReason = moment.resolvedReason

                val clipId = clipDao.insertClip(
                    ClipEntity(
                        projectId = projectId,
                        title = hookTitle,
                        hookText = moment.hookText.ifBlank { hookTitle },
                        viralityScore = moment.viralityScore,
                        reasoning = oneLineReason,
                        startTimeMs = moment.startMs,
                        endTimeMs = moment.endMs,
                        emotionalPeak = moment.emotionalPeak ?: "High Retention Hook",
                        reframeMode = reframeModes[idx % reframeModes.size],
                        captionPreset = presets[idx % presets.size],
                        captionFontSize = 22,
                        captionPosition = "BOTTOM_THIRD",
                        silenceRemoved = removeSilenceDefault,
                        backgroundMusic = "None",
                        duckingPercent = 80,
                        thumbnailPath = project.thumbnailPath,
                        wordsJson = clipWordsJson
                    )
                )

                // Step 7: EXPORTING
                val progressVal = 0.88f + (0.10f * ((idx + 1).toFloat() / viralMoments.size.coerceAtLeast(1)))
                onProgress(7, "Packaging clip ${idx + 1} of ${viralMoments.size}...", progressVal)

                val clipText = clipWords.joinToString(" ") { it.word }
                val metaDto = try {
                    ClipForgeCloudClient.generatePlatformMetadata(
                        clipTitle = hookTitle,
                        hookText = moment.hookText.ifBlank { hookTitle },
                        clipTranscript = clipText
                    )
                } catch (_: Exception) {
                    null
                }

                val titlesList = metaDto?.titles?.takeIf { it.isNotEmpty() }
                    ?: listOf(hookTitle, moment.hookText.take(58), "${hookTitle.take(45)} (Must Watch)")
                val broadTags = metaDto?.broadHashtags?.takeIf { it.isNotEmpty() }
                    ?: listOf("#shorts", "#viral", "#trending")
                val nicheTags = metaDto?.nicheHashtags?.takeIf { it.isNotEmpty() }
                    ?: listOf("#clips", "#podcastclips", "#insights", "#highlights", "#creator")
                val trendingTags = metaDto?.trendingHashtags?.takeIf { it.isNotEmpty() }
                    ?: listOf("#fyp", "#reels")

                metadataDao.insertMetadata(
                    PlatformMetadataEntity(
                        clipId = clipId,
                        titlesJson = stringListAdapter.toJson(titlesList),
                        tiktokDescription = metaDto?.tiktokDescription?.ifBlank {
                            "${moment.hookText}\nFollow for more!\n${nicheTags.take(4).joinToString(" ")}"
                        } ?: "${moment.hookText}\n${nicheTags.take(4).joinToString(" ")}",
                        instagramDescription = metaDto?.instagramDescription?.ifBlank {
                            "${moment.hookText}\n\n$oneLineReason\n\n${(broadTags + nicheTags).joinToString(" ")}"
                        } ?: "${moment.hookText}\n\n$oneLineReason",
                        youtubeShortsDescription = metaDto?.youtubeShortsDescription?.ifBlank {
                            "$hookTitle #Shorts ${broadTags.joinToString(" ")}"
                        } ?: "$hookTitle #Shorts",
                        facebookDescription = metaDto?.facebookDescription?.ifBlank {
                            "${moment.hookText} - $oneLineReason"
                        } ?: oneLineReason,
                        broadHashtagsJson = stringListAdapter.toJson(broadTags),
                        nicheHashtagsJson = stringListAdapter.toJson(nicheTags),
                        trendingHashtagsJson = stringListAdapter.toJson(trendingTags),
                        hookText = metaDto?.hookText?.ifBlank { moment.hookText } ?: moment.hookText,
                        cta = metaDto?.cta?.ifBlank { "Follow for more clips" } ?: "Follow for more clips"
                    )
                )
            }

            // Step 8: DONE
            projectDao.updateProject(
                project.copy(
                    status = ProjectStatus.DONE,
                    clipCount = viralMoments.size,
                    transcriptJson = serializedFullTranscript,
                    errorMessage = ""
                )
            )
            onProgress(8, "Done: ${viralMoments.size} viral moments detected.", 1.0f)
            projectId
        } catch (e: Exception) {
            val latestProject = projectDao.getProjectById(projectId) ?: project
            val readableError = e.localizedMessage ?: "Processing failed unexpectedly."
            projectDao.updateProject(
                latestProject.copy(
                    status = ProjectStatus.FAILED,
                    errorMessage = readableError
                )
            )
            throw e
        }
    }

    fun ensureSufficientDiskSpace(minFreeBytes: Long = 50L * 1024L * 1024L) {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (availableBytes < minFreeBytes) {
                val availMb = availableBytes / (1024L * 1024L)
                throw IllegalStateException("Low device storage (${availMb} MB free). Please free up space or tap 'Clear Cache' on the Studio dashboard.")
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {}
    }

    fun ensureSourceVideoAccessible(videoUri: Uri) {
        try {
            if (videoUri.scheme == "file") {
                val path = videoUri.path
                val file = if (path != null) java.io.File(path) else null
                if (file == null || !file.exists() || !file.canRead()) {
                    throw IllegalStateException("Source video file is not accessible. Please re-import the video.")
                }
            } else {
                context.contentResolver.openInputStream(videoUri)?.use {
                } ?: throw IllegalStateException("Source video is no longer readable. Please re-select the video.")
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Video permission was lost. Please re-import the video.", e)
        }
    }

    suspend fun reconcileStuckProjects(isWorkerRunning: suspend (Long) -> Boolean) = withContext(Dispatchers.IO) {
        val projects = projectDao.getAllProjectsDirect()
        for (proj in projects) {
            val isCompleted = proj.status == ProjectStatus.READY || proj.status == ProjectStatus.DONE
            val isFailed = proj.status == ProjectStatus.FAILED
            if (!isCompleted && !isFailed) {
                val active = isWorkerRunning(proj.id)
                if (!active) {
                    projectDao.updateProject(
                        proj.copy(
                            status = ProjectStatus.FAILED,
                            errorMessage = "Processing was interrupted when the app closed. Tap Retry to resume."
                        )
                    )
                }
            }
        }
    }

    suspend fun clearTemporaryCache(): Float = withContext(Dispatchers.IO) {
        var bytesFreed = 0L
        fun deleteFolderContents(dir: java.io.File) {
            if (!dir.exists() || !dir.isDirectory) return
            dir.listFiles()?.forEach { child ->
                try {
                    if (child.isDirectory) {
                        if (!child.absolutePath.contains("WebView")) {
                            deleteFolderContents(child)
                            child.delete()
                        }
                    } else {
                        val len = child.length()
                        if (child.delete()) {
                            bytesFreed += len
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        deleteFolderContents(java.io.File(context.cacheDir, "audio_chunks"))
        deleteFolderContents(java.io.File(context.filesDir, "exports"))
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile) {
                val len = f.length()
                if (f.delete()) bytesFreed += len
            }
        }
        (bytesFreed.toFloat() / (1024f * 1024f)).coerceAtLeast(0f)
    }

    suspend fun getTranscriptWordsForClip(clipId: Long): List<WordTimestamp> = withContext(Dispatchers.IO) {
        val clip = clipDao.getClipById(clipId) ?: return@withContext emptyList()
        if (clip.wordsJson.isNotBlank()) {
            return@withContext ClipForgeCloudClient.deserializeWords(clip.wordsJson)
        }
        val project = projectDao.getProjectById(clip.projectId) ?: return@withContext emptyList()
        val allWords = ClipForgeCloudClient.deserializeWords(project.transcriptJson)
        allWords.filter { it.endMs >= clip.startTimeMs && it.startMs <= clip.endTimeMs }
    }

    suspend fun saveTranscriptWordsForClip(clipId: Long, words: List<WordTimestamp>) = withContext(Dispatchers.IO) {
        val clip = clipDao.getClipById(clipId) ?: return@withContext
        val updatedJson = ClipForgeCloudClient.serializeWords(words)
        clipDao.updateClip(clip.copy(wordsJson = updatedJson))
    }

    suspend fun createPendingExportEntity(
        clipId: Long,
        quality: String,
        format: String,
        fps: Int,
        bitratePreset: String,
        burnedCaptions: Boolean,
        removeWatermark: Boolean,
        includeSrt: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val estimatedSizeMb = when (quality) {
            "480p" -> 8.5f
            "720p" -> 16.0f
            else -> 32.0f
        }
        exportDao.insertExport(
            ExportEntity(
                clipId = clipId,
                quality = quality,
                format = format,
                fps = fps,
                bitratePreset = bitratePreset,
                burnedCaptions = burnedCaptions,
                removeWatermark = removeWatermark,
                includeSrt = includeSrt,
                status = "RENDERING",
                progress = 0,
                estimatedSizeMb = estimatedSizeMb,
                downloadUrl = ""
            )
        )
    }

    suspend fun regenerateMetadataWithAi(clipId: Long): PlatformMetadataEntity = withContext(Dispatchers.IO) {
        val clip = clipDao.getClipById(clipId)
            ?: throw IllegalStateException("Clip not found.")
        val currentMeta = metadataDao.getMetadataDirect(clipId)
        val words = getTranscriptWordsForClip(clipId)
        val transcriptText = words.joinToString(" ") { it.word }.ifBlank { clip.hookText }

        val dto = ClipForgeCloudClient.generatePlatformMetadata(
            clipTitle = clip.title,
            hookText = clip.hookText,
            clipTranscript = transcriptText
        )
        val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = ClipForgeCloudClient.moshi.adapter<List<String>>(stringListType)

        val updated = PlatformMetadataEntity(
            id = currentMeta?.id ?: 0L,
            clipId = clipId,
            titlesJson = adapter.toJson(dto.titles.ifEmpty { listOf(clip.title) }),
            tiktokDescription = dto.tiktokDescription.ifBlank { currentMeta?.tiktokDescription ?: clip.hookText },
            instagramDescription = dto.instagramDescription.ifBlank { currentMeta?.instagramDescription ?: clip.reasoning },
            youtubeShortsDescription = dto.youtubeShortsDescription.ifBlank { "${clip.title} #Shorts" },
            facebookDescription = dto.facebookDescription.ifBlank { clip.reasoning },
            broadHashtagsJson = adapter.toJson(dto.broadHashtags.ifEmpty { listOf("#shorts", "#viral") }),
            nicheHashtagsJson = adapter.toJson(dto.nicheHashtags.ifEmpty { listOf("#podcastclips", "#insights") }),
            trendingHashtagsJson = adapter.toJson(dto.trendingHashtags.ifEmpty { listOf("#fyp") }),
            hookText = dto.hookText.ifBlank { clip.hookText },
            cta = dto.cta.ifBlank { "Follow for more!" }
        )

        if (currentMeta == null) {
            val id = metadataDao.insertMetadata(updated)
            updated.copy(id = id)
        } else {
            metadataDao.updateMetadata(updated)
            updated
        }
    }
}
