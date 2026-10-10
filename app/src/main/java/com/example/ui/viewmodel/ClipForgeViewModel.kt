package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.ClipForgeDatabase
import com.example.data.media.VideoValidationResult
import com.example.data.media.VideoValidator
import com.example.data.model.CaptionPreset
import com.example.data.model.ClipEntity
import com.example.data.model.ExportEntity
import com.example.data.model.PipelineStep
import com.example.data.model.PipelineUiState
import com.example.data.model.PlatformMetadataEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.ReframeMode
import com.example.data.model.ShortsVideoRatio
import com.example.data.model.SocialPlatform
import com.example.data.model.SourceType
import com.example.data.model.WordTimestamp
import com.example.data.remote.ClipForgeCloudClient
import com.example.data.repository.ClipForgeRepository
import com.example.data.worker.ClipExportWorker
import com.example.data.worker.VideoPipelineWorker
import com.example.ui.components.AdMobManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailyUsageManager {
    private const val PREFS_NAME = "clipforge_daily_usage"
    private const val KEY_DATE = "usage_date"
    private const val KEY_COUNT = "usage_count"
    const val MAX_RUNS_PER_DAY = 3

    private fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getRunsRemaining(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, "")
        val today = getTodayDateString()
        val currentCount = if (savedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
        return (MAX_RUNS_PER_DAY - currentCount).coerceAtLeast(0)
    }

    fun canStartRun(context: Context): Boolean = getRunsRemaining(context) > 0

    fun recordRun(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val savedDate = prefs.getString(KEY_DATE, "")
        val currentCount = if (savedDate == today) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_COUNT, currentCount + 1)
            .apply()
    }
}

sealed class ScreenDestination {
    data object Dashboard : ScreenDestination()
    data object Ingestion : ScreenDestination()
    data class Processing(val projectTitle: String, val projectId: Long = 0L) : ScreenDestination()
    data class ClipList(val projectId: Long) : ScreenDestination()
    data class Editor(val clipId: Long) : ScreenDestination()
    data class Metadata(val clipId: Long) : ScreenDestination()
}

class ClipForgeViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ClipForgeDatabase.getDatabase(application)
    private val repository = ClipForgeRepository(
        context = application,
        projectDao = db.projectDao(),
        clipDao = db.clipDao(),
        metadataDao = db.platformMetadataDao(),
        exportDao = db.exportDao()
    )
    private val workManager = WorkManager.getInstance(application)

    val allProjects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExports: StateFlow<List<ExportEntity>> = repository.allExports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Dashboard)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Processing state
    private val _processingProjectId = MutableStateFlow<Long?>(null)
    val processingProjectId: StateFlow<Long?> = _processingProjectId.asStateFlow()

    private val _isJobActive = MutableStateFlow(false)
    val isJobActive: StateFlow<Boolean> = _isJobActive.asStateFlow()

    private val _dailyRunsRemaining = MutableStateFlow(DailyUsageManager.getRunsRemaining(application))
    val dailyRunsRemaining: StateFlow<Int> = _dailyRunsRemaining.asStateFlow()

    private val _processingStep = MutableStateFlow(0)
    val processingStep: StateFlow<Int> = _processingStep.asStateFlow()

    private val _processingStatusText = MutableStateFlow("")
    val processingStatusText: StateFlow<String> = _processingStatusText.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingError = MutableStateFlow<String?>(null)
    val processingError: StateFlow<String?> = _processingError.asStateFlow()

    // Step-by-step pipeline UI flow state
    private val _pipelineUiState = MutableStateFlow(PipelineUiState())
    val pipelineUiState: StateFlow<PipelineUiState> = _pipelineUiState.asStateFlow()

    private var pipelineStartTimeMs: Long = 0L
    private val stepStartTimes = mutableMapOf<PipelineStep, Long>()
    private val completedStepDurations = mutableListOf<Long>()
    private var clipsObserverJob: Job? = null
    private var isStartingPipeline = false

    // Video ingestion validation state
    private val _validationResult = MutableStateFlow<VideoValidationResult?>(null)
    val validationResult: StateFlow<VideoValidationResult?> = _validationResult.asStateFlow()

    private val _isValidatingVideo = MutableStateFlow(false)
    val isValidatingVideo: StateFlow<Boolean> = _isValidatingVideo.asStateFlow()

    // Active Clip & Project
    private val _selectedClip = MutableStateFlow<ClipEntity?>(null)
    val selectedClip: StateFlow<ClipEntity?> = _selectedClip.asStateFlow()

    private val _selectedClipSourceUri = MutableStateFlow<String?>(null)
    val selectedClipSourceUri: StateFlow<String?> = _selectedClipSourceUri.asStateFlow()

    // Platform & Ratio features
    private val _selectedPlatform = MutableStateFlow(SocialPlatform.TIKTOK)
    val selectedPlatform: StateFlow<SocialPlatform> = _selectedPlatform.asStateFlow()

    private val _selectedRatio = MutableStateFlow(ShortsVideoRatio.RATIO_9_16)
    val selectedRatio: StateFlow<ShortsVideoRatio> = _selectedRatio.asStateFlow()

    private val _showPlatformUiOverlay = MutableStateFlow(true)
    val showPlatformUiOverlay: StateFlow<Boolean> = _showPlatformUiOverlay.asStateFlow()

    private val _showSafeZoneGuides = MutableStateFlow(true)
    val showSafeZoneGuides: StateFlow<Boolean> = _showSafeZoneGuides.asStateFlow()

    private val _selectedMetadata = MutableStateFlow<PlatformMetadataEntity?>(null)
    val selectedMetadata: StateFlow<PlatformMetadataEntity?> = _selectedMetadata.asStateFlow()

    private val _transcriptWords = MutableStateFlow<List<WordTimestamp>>(emptyList())
    val transcriptWords: StateFlow<List<WordTimestamp>> = _transcriptWords.asStateFlow()

    // Export Modal State
    private val _showExportModal = MutableStateFlow(false)
    val showExportModal: StateFlow<Boolean> = _showExportModal.asStateFlow()

    private val _exportQuality = MutableStateFlow("1080p")
    val exportQuality: StateFlow<String> = _exportQuality.asStateFlow()

    private val _exportFormat = MutableStateFlow("MP4")
    val exportFormat: StateFlow<String> = _exportFormat.asStateFlow()

    private val _exportFps = MutableStateFlow(30)
    val exportFps: StateFlow<Int> = _exportFps.asStateFlow()

    private val _exportBitrate = MutableStateFlow("Balanced")
    val exportBitrate: StateFlow<String> = _exportBitrate.asStateFlow()

    private val _exportBurnCaptions = MutableStateFlow(true)
    val exportBurnCaptions: StateFlow<Boolean> = _exportBurnCaptions.asStateFlow()

    private val _exportRemoveWatermark = MutableStateFlow(true)
    val exportRemoveWatermark: StateFlow<Boolean> = _exportRemoveWatermark.asStateFlow()

    private val _exportIncludeSrt = MutableStateFlow(true)
    val exportIncludeSrt: StateFlow<Boolean> = _exportIncludeSrt.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0)
    val exportProgress: StateFlow<Int> = _exportProgress.asStateFlow()

    private val _exportDownloadUrl = MutableStateFlow<String?>(null)
    val exportDownloadUrl: StateFlow<String?> = _exportDownloadUrl.asStateFlow()

    private val _exportSrtUrl = MutableStateFlow<String?>(null)
    val exportSrtUrl: StateFlow<String?> = _exportSrtUrl.asStateFlow()

    private val _exportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = _exportError.asStateFlow()

    // Notification toast / snackbar
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var metadataJob: Job? = null
    private var pipelineObserverJob: Job? = null
    private var exportObserverJob: Job? = null

    init {
        ClipForgeCloudClient.init(application)
        viewModelScope.launch {
            try {
                repository.reconcileStuckProjects { projectId ->
                    val workInfoList = try {
                        workManager.getWorkInfosForUniqueWork(
                            VideoPipelineWorker.uniqueWorkName(projectId)
                        ).get()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    workInfoList.any {
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshDailyUsage() {
        _dailyRunsRemaining.value = DailyUsageManager.getRunsRemaining(getApplication())
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val freedMb = repository.clearTemporaryCache()
            val formatted = String.format(Locale.US, "%.1f MB", freedMb)
            _toastMessage.value = "Cache cleared ($formatted freed)"
        }
    }

    fun navigateTo(dest: ScreenDestination) {
        _currentScreen.value = dest
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun selectPlatform(platform: SocialPlatform) {
        _selectedPlatform.value = platform
        val current = _selectedClip.value
        if (current != null) {
            val updated = current.copy(targetPlatformName = platform.name)
            _selectedClip.value = updated
            viewModelScope.launch { repository.updateClip(updated) }
        }
        _toastMessage.value = "Switched to ${platform.displayName} mode"
    }

    fun selectRatio(ratio: ShortsVideoRatio) {
        _selectedRatio.value = ratio
        val current = _selectedClip.value
        if (current != null) {
            val updated = current.copy(videoRatioKey = ratio.ratioKey)
            _selectedClip.value = updated
            viewModelScope.launch { repository.updateClip(updated) }
        }
        _toastMessage.value = "Aspect ratio set to ${ratio.label}"
    }

    fun toggleEmojisEnabled() {
        val current = _selectedClip.value ?: return
        val updated = current.copy(emojisEnabled = !current.emojisEnabled)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun togglePlatformUiOverlay() {
        _showPlatformUiOverlay.value = !_showPlatformUiOverlay.value
    }

    fun toggleSafeZoneGuides() {
        _showSafeZoneGuides.value = !_showSafeZoneGuides.value
    }

    fun selectProject(projectId: Long) {
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            val isCompleted = project?.status == com.example.data.model.ProjectStatus.READY ||
                project?.status == com.example.data.model.ProjectStatus.DONE
            if (project != null && !isCompleted && project.status != com.example.data.model.ProjectStatus.FAILED) {
                _processingProjectId.value = projectId
                _isJobActive.value = true
                _currentScreen.value = ScreenDestination.Processing(project.title, projectId)
                val initialStep = when (project.status) {
                    com.example.data.model.ProjectStatus.INGESTING -> PipelineStep.IMPORTING
                    com.example.data.model.ProjectStatus.EXTRACTING_AUDIO -> PipelineStep.EXTRACTING_AUDIO
                    com.example.data.model.ProjectStatus.TRANSCRIBING -> PipelineStep.TRANSCRIBING
                    com.example.data.model.ProjectStatus.DETECTING -> PipelineStep.FINDING_MOMENTS
                    com.example.data.model.ProjectStatus.REFRAMING -> PipelineStep.REFRAMING
                    com.example.data.model.ProjectStatus.DONE, com.example.data.model.ProjectStatus.READY -> PipelineStep.DONE
                    else -> PipelineStep.IMPORTING
                }
                pipelineStartTimeMs = System.currentTimeMillis()
                stepStartTimes.clear()
                completedStepDurations.clear()
                stepStartTimes[initialStep] = pipelineStartTimeMs
                _pipelineUiState.value = PipelineUiState(
                    currentStep = initialStep,
                    stepProgressPercent = 50,
                    overallProgressPercent = (initialStep.ordinal * 11).coerceIn(5, 95),
                    completedSteps = PipelineStep.entries.filter { it.ordinal < initialStep.ordinal }.toSet(),
                    liveSubtitle = initialStep.defaultSubtitle,
                    isDone = false
                )
                observeExistingPipelineWork(projectId)
            } else if (project != null && project.status == com.example.data.model.ProjectStatus.FAILED) {
                _processingProjectId.value = projectId
                _isJobActive.value = false
                _currentScreen.value = ScreenDestination.Processing(project.title, projectId)
                _pipelineUiState.value = PipelineUiState(
                    currentStep = PipelineStep.IMPORTING,
                    failedStep = PipelineStep.IMPORTING,
                    errorMessage = project.errorMessage.ifBlank { "Processing failed. Tap Retry to restart." },
                    liveSubtitle = "Processing failed",
                    isDone = false
                )
            } else {
                _currentScreen.value = ScreenDestination.ClipList(projectId)
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            workManager.cancelUniqueWork(VideoPipelineWorker.uniqueWorkName(projectId))
            repository.deleteProject(projectId)
            _toastMessage.value = "Project deleted"
        }
    }

    fun getClipsForProject(projectId: Long) = repository.getClipsForProject(projectId)

    fun validateSelectedVideo(uri: Uri, onValidated: (VideoValidationResult) -> Unit = {}) {
        _isValidatingVideo.value = true
        viewModelScope.launch {
            val result = VideoValidator.validateAndPersistVideo(getApplication(), uri)
            _validationResult.value = result
            _isValidatingVideo.value = false
            onValidated(result)
        }
    }

    fun clearValidationResult() {
        _validationResult.value = null
    }

    fun loadClipForEditing(clipId: Long) {
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            val clip = repository.getClipById(clipId)
            _selectedClip.value = clip
            if (clip != null) {
                SocialPlatform.entries.find { it.name == clip.targetPlatformName }?.let {
                    _selectedPlatform.value = it
                }
                ShortsVideoRatio.entries.find { it.ratioKey == clip.videoRatioKey }?.let {
                    _selectedRatio.value = it
                }
                val project = repository.getProjectById(clip.projectId)
                _selectedClipSourceUri.value = project?.sourceUrl
                _transcriptWords.value = repository.getTranscriptWordsForClip(clipId)
            }
            repository.getMetadataForClip(clipId).collect {
                _selectedMetadata.value = it
            }
        }
    }

    fun updateClipReframeMode(mode: ReframeMode) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(reframeMode = mode)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun updateCaptionPreset(preset: CaptionPreset) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(captionPreset = preset)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun updateCaptionSize(fontSize: Int) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(captionFontSize = fontSize)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun updateCaptionPosition(pos: String) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(captionPosition = pos)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun toggleSilenceRemoval() {
        val current = _selectedClip.value ?: return
        val updated = current.copy(silenceRemoved = !current.silenceRemoved)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun updateBackgroundMusic(music: String, ducking: Int) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(backgroundMusic = music, duckingPercent = ducking)
        _selectedClip.value = updated
        viewModelScope.launch { repository.updateClip(updated) }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val current = _selectedClip.value ?: return
        val updated = current.copy(startTimeMs = startMs, endTimeMs = endMs)
        _selectedClip.value = updated
        viewModelScope.launch {
            repository.updateClip(updated)
            _toastMessage.value = "Trim updated: ${(startMs / 1000)}s - ${(endMs / 1000)}s"
        }
    }

    fun toggleCutWord(index: Int) {
        val clip = _selectedClip.value ?: return
        val list = _transcriptWords.value.toMutableList()
        if (index in list.indices) {
            val word = list[index]
            list[index] = word.copy(isCut = !word.isCut)
            _transcriptWords.value = list
            viewModelScope.launch {
                repository.saveTranscriptWordsForClip(clip.id, list)
            }
            _toastMessage.value = if (list[index].isCut) "Word '${word.word}' cut from clip" else "Word restored"
        }
    }

    fun startIngestionPipeline(
        title: String,
        sourceType: SourceType = SourceType.UPLOAD,
        sourceUrl: String,
        durationSeconds: Int = 120,
        videoSizeMb: Float = 20f,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        thumbnailPath: String = "",
        removeSilence: Boolean = true,
        snapSentences: Boolean = true,
        speakerDiarization: Boolean = true
    ) {
        if (isStartingPipeline || _isJobActive.value) {
            _toastMessage.value = "A video processing job is already in progress. Please wait for it to finish."
            return
        }

        val app = getApplication<Application>()
        if (!DailyUsageManager.canStartRun(app)) {
            _toastMessage.value = "Daily limit reached (3 runs per day). Please come back tomorrow!"
            return
        }

        if (durationSeconds > VideoValidator.MAX_DURATION_SEC) {
            _toastMessage.value = "Videos sent for AI processing must be 60 minutes or less."
            return
        }

        isStartingPipeline = true

        val validated = _validationResult.value
        val finalDuration = if (validated != null && validated.isValid && validated.persistedUriString == sourceUrl) {
            validated.durationSeconds
        } else durationSeconds
        val finalSizeMb = if (validated != null && validated.isValid && validated.persistedUriString == sourceUrl) {
            validated.fileSizeMb
        } else videoSizeMb
        val finalWidth = if (validated != null && validated.isValid && validated.persistedUriString == sourceUrl && validated.width > 0) {
            validated.width
        } else videoWidth
        val finalHeight = if (validated != null && validated.isValid && validated.persistedUriString == sourceUrl && validated.height > 0) {
            validated.height
        } else videoHeight
        val finalThumbPath = if (validated != null && validated.isValid && validated.persistedUriString == sourceUrl && validated.thumbnailPath.isNotBlank()) {
            validated.thumbnailPath
        } else thumbnailPath

        viewModelScope.launch {
            try {
                DailyUsageManager.recordRun(app)
                refreshDailyUsage()

                val projectId = repository.createPendingProject(
                    title = title,
                    sourceUri = sourceUrl,
                    durationSeconds = finalDuration,
                    videoSizeMb = finalSizeMb,
                    videoWidth = finalWidth,
                    videoHeight = finalHeight,
                    thumbnailPath = finalThumbPath
                )
                enqueuePipelineWorkForProject(
                    projectId = projectId,
                    projectTitle = title.ifBlank { "Video Project #$projectId" },
                    removeSilence = removeSilence,
                    snapSentences = snapSentences,
                    speakerDiarization = speakerDiarization
                )
            } finally {
                isStartingPipeline = false
            }
        }
    }

    fun retryProjectPipeline(projectId: Long) {
        if (_isJobActive.value) {
            _toastMessage.value = "A video processing job is already running."
            return
        }
        viewModelScope.launch {
            val project = repository.getProjectById(projectId) ?: return@launch
            enqueuePipelineWorkForProject(projectId, project.title)
        }
    }

    fun cancelActivePipeline() {
        val projectId = _processingProjectId.value ?: return
        workManager.cancelUniqueWork(VideoPipelineWorker.uniqueWorkName(projectId))
        AdMobManager.setProcessingActive(false)
        _isJobActive.value = false
        clipsObserverJob?.cancel()
        viewModelScope.launch {
            val project = repository.getProjectById(projectId)
            if (project != null) {
                db.projectDao().updateProject(
                    project.copy(
                        status = com.example.data.model.ProjectStatus.FAILED,
                        errorMessage = "Processing was cancelled by user."
                    )
                )
            }
        }
        _processingStatusText.value = "Pipeline cancelled by user."
        _processingError.value = "Processing was cancelled. You can tap Retry whenever you are ready."
        _toastMessage.value = "Video processing cancelled"
        _pipelineUiState.update { current ->
            current.copy(
                failedStep = current.currentStep,
                errorMessage = "Processing was cancelled by user. Tap Retry to restart.",
                isDone = false,
                estimatedTimeRemainingSeconds = null
            )
        }
    }

    private fun enqueuePipelineWorkForProject(
        projectId: Long,
        projectTitle: String,
        removeSilence: Boolean = true,
        snapSentences: Boolean = true,
        speakerDiarization: Boolean = true
    ) {
        _processingProjectId.value = projectId
        _isJobActive.value = true
        _processingStep.value = 0
        _processingProgress.value = 0.04f
        _processingError.value = null
        _processingStatusText.value = "Starting background video processing..."
        _currentScreen.value = ScreenDestination.Processing(projectTitle, projectId)
        AdMobManager.setProcessingActive(true)

        pipelineStartTimeMs = System.currentTimeMillis()
        stepStartTimes.clear()
        completedStepDurations.clear()
        stepStartTimes[PipelineStep.IMPORTING] = pipelineStartTimeMs

        _pipelineUiState.value = PipelineUiState(
            currentStep = PipelineStep.IMPORTING,
            stepProgressPercent = 5,
            overallProgressPercent = 4,
            liveSubtitle = PipelineStep.IMPORTING.defaultSubtitle,
            completedSteps = emptySet(),
            failedStep = null,
            errorMessage = null,
            isDone = false,
            estimatedTimeRemainingSeconds = null,
            detectedClipsPreview = emptyList()
        )

        val workName = VideoPipelineWorker.uniqueWorkName(projectId)
        val request = OneTimeWorkRequestBuilder<VideoPipelineWorker>()
            .setInputData(
                workDataOf(
                    VideoPipelineWorker.KEY_PROJECT_ID to projectId,
                    VideoPipelineWorker.KEY_REMOVE_SILENCE to removeSilence,
                    VideoPipelineWorker.KEY_SNAP_SENTENCES to snapSentences,
                    VideoPipelineWorker.KEY_SPEAKER_DIARIZATION to speakerDiarization
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )

        observeExistingPipelineWork(projectId)
    }

    private fun observeExistingPipelineWork(projectId: Long) {
        val workName = VideoPipelineWorker.uniqueWorkName(projectId)
        clipsObserverJob?.cancel()
        clipsObserverJob = viewModelScope.launch {
            repository.getClipsForProject(projectId).collect { clips ->
                _pipelineUiState.update { current ->
                    current.copy(detectedClipsPreview = clips)
                }
            }
        }

        pipelineObserverJob?.cancel()
        pipelineObserverJob = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(workName).collect { workInfos ->
                val info = workInfos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> {
                        _isJobActive.value = true
                        AdMobManager.setProcessingActive(true)
                        _processingStatusText.value = "Queued..."
                    }
                    WorkInfo.State.RUNNING -> {
                        _isJobActive.value = true
                        AdMobManager.setProcessingActive(true)
                        val stepIdx = info.progress.getInt(VideoPipelineWorker.KEY_STEP_INDEX, _processingStep.value)
                        val stepName = info.progress.getString(VideoPipelineWorker.KEY_STEP_NAME)
                        val prog = info.progress.getFloat(VideoPipelineWorker.KEY_PROGRESS, _processingProgress.value)
                        _processingStep.value = stepIdx
                        if (!stepName.isNullOrBlank()) {
                            _processingStatusText.value = stepName
                        }
                        _processingProgress.value = prog.coerceIn(0.02f, 0.99f)

                        val step = PipelineStep.entries.getOrElse(stepIdx) { PipelineStep.IMPORTING }
                        val now = System.currentTimeMillis()
                        val currentStepInUi = _pipelineUiState.value.currentStep
                        if (step != currentStepInUi) {
                            val previousStartTime = stepStartTimes[currentStepInUi] ?: pipelineStartTimeMs
                            val duration = (now - previousStartTime).coerceAtLeast(100L)
                            completedStepDurations.add(duration)
                            stepStartTimes[step] = now
                        }

                        val completed = PipelineStep.entries.filter { it.ordinal < step.ordinal }.toSet()
                        val (rangeStart, rangeEnd) = when (step) {
                            PipelineStep.IMPORTING -> 0.00f to 0.08f
                            PipelineStep.EXTRACTING_AUDIO -> 0.08f to 0.30f
                            PipelineStep.TRANSCRIBING -> 0.30f to 0.62f
                            PipelineStep.FINDING_MOMENTS -> 0.62f to 0.74f
                            PipelineStep.WRITING_TITLES -> 0.74f to 0.80f
                            PipelineStep.REFRAMING -> 0.80f to 0.85f
                            PipelineStep.ADDING_CAPTIONS -> 0.85f to 0.88f
                            PipelineStep.EXPORTING -> 0.88f to 0.98f
                            PipelineStep.DONE -> 0.98f to 1.00f
                        }

                        val stepPct = (((prog - rangeStart) / (rangeEnd - rangeStart).coerceAtLeast(0.01f)) * 100)
                            .toInt().coerceIn(0, 100)
                        val overallPct = (prog * 100).toInt().coerceIn(0, 100)

                        val stepsRemaining = (PipelineStep.entries.size - 1) - step.ordinal
                        val estimatedRemainingSeconds = if (stepsRemaining <= 0) {
                            0L
                        } else if (completedStepDurations.isNotEmpty()) {
                            val avgDuration = completedStepDurations.average()
                            ((avgDuration * stepsRemaining) / 1000.0).toLong().coerceAtLeast(1L)
                        } else if (stepPct > 10) {
                            val stepElapsed = now - (stepStartTimes[step] ?: pipelineStartTimeMs)
                            val estimatedStepTotal = (stepElapsed / (stepPct / 100.0)).toLong()
                            val remainingInStep = (estimatedStepTotal - stepElapsed).coerceAtLeast(0L)
                            ((remainingInStep + (estimatedStepTotal * (stepsRemaining - 1))) / 1000.0).toLong().coerceAtLeast(1L)
                        } else {
                            null
                        }

                        _pipelineUiState.update { current ->
                            current.copy(
                                currentStep = step,
                                stepProgressPercent = stepPct,
                                overallProgressPercent = overallPct,
                                liveSubtitle = if (!stepName.isNullOrBlank()) stepName else step.defaultSubtitle,
                                completedSteps = completed,
                                failedStep = null,
                                errorMessage = null,
                                isDone = false,
                                estimatedTimeRemainingSeconds = estimatedRemainingSeconds
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _isJobActive.value = false
                        AdMobManager.setProcessingActive(false)
                        _processingProgress.value = 1.0f
                        _processingStep.value = 8
                        _processingStatusText.value = "Complete!"
                        _toastMessage.value = "Viral moments detected!"
                        _pipelineUiState.update { current ->
                            current.copy(
                                currentStep = PipelineStep.DONE,
                                stepProgressPercent = 100,
                                overallProgressPercent = 100,
                                liveSubtitle = "Viral short-form clips forged and ready!",
                                completedSteps = PipelineStep.entries.toSet(),
                                failedStep = null,
                                errorMessage = null,
                                isDone = true,
                                estimatedTimeRemainingSeconds = 0L
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        _isJobActive.value = false
                        AdMobManager.setProcessingActive(false)
                        val err = info.outputData.getString(VideoPipelineWorker.KEY_ERROR)
                            ?: repository.getProjectById(projectId)?.errorMessage
                            ?: "Processing failed. Please check your network and video format."
                        _processingError.value = err
                        _processingStatusText.value = "Failed: $err"
                        _pipelineUiState.update { current ->
                            current.copy(
                                failedStep = current.currentStep,
                                errorMessage = err,
                                isDone = false,
                                estimatedTimeRemainingSeconds = null
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _isJobActive.value = false
                        AdMobManager.setProcessingActive(false)
                        _processingError.value = "Processing cancelled."
                        _processingStatusText.value = "Cancelled"
                        _pipelineUiState.update { current ->
                            current.copy(
                                failedStep = current.currentStep,
                                errorMessage = "Processing was cancelled by user.",
                                isDone = false,
                                estimatedTimeRemainingSeconds = null
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    // Export modal controls
    fun openExportModal(clip: ClipEntity) {
        _selectedClip.value = clip
        _showExportModal.value = true
        _exportProgress.value = 0
        _isExporting.value = false
        _exportDownloadUrl.value = null
        _exportSrtUrl.value = null
        _exportError.value = null
    }

    fun closeExportModal() {
        if (_isExporting.value) {
            cancelActiveExport()
        }
        _showExportModal.value = false
    }

    fun setExportQuality(quality: String) {
        _exportQuality.value = quality
    }

    fun setExportFormat(format: String) {
        _exportFormat.value = format
    }

    fun setExportFps(fps: Int) {
        _exportFps.value = fps
    }

    fun setExportBitrate(bitrate: String) {
        _exportBitrate.value = bitrate
    }

    fun setExportBurnCaptions(enabled: Boolean) {
        _exportBurnCaptions.value = enabled
    }

    fun setExportRemoveWatermark(remove: Boolean) {
        _exportRemoveWatermark.value = remove
    }

    fun setExportIncludeSrt(include: Boolean) {
        _exportIncludeSrt.value = include
    }

    fun startExport() {
        val clip = _selectedClip.value ?: return
        _isExporting.value = true
        _exportProgress.value = 1
        _exportError.value = null
        _exportDownloadUrl.value = null
        _exportSrtUrl.value = null
        AdMobManager.setExportActive(true)

        viewModelScope.launch {
            try {
                val exportId = repository.createPendingExportEntity(
                    clipId = clip.id,
                    quality = _exportQuality.value,
                    format = _exportFormat.value,
                    fps = _exportFps.value,
                    bitratePreset = _exportBitrate.value,
                    burnedCaptions = _exportBurnCaptions.value,
                    removeWatermark = _exportRemoveWatermark.value,
                    includeSrt = _exportIncludeSrt.value
                )

                val workName = ClipExportWorker.uniqueWorkName(clip.id)
                val workRequest = OneTimeWorkRequestBuilder<ClipExportWorker>()
                    .setInputData(
                        workDataOf(
                            ClipExportWorker.KEY_EXPORT_ID to exportId,
                            ClipExportWorker.KEY_CLIP_ID to clip.id
                        )
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                workManager.enqueueUniqueWork(
                    workName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                exportObserverJob?.cancel()
                exportObserverJob = viewModelScope.launch {
                    workManager.getWorkInfosForUniqueWorkFlow(workName).collect { infos ->
                        val info = infos.firstOrNull() ?: return@collect
                        when (info.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                val p = info.progress.getInt(ClipExportWorker.KEY_PROGRESS, _exportProgress.value)
                                _exportProgress.value = p.coerceIn(1, 99)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                _isExporting.value = false
                                AdMobManager.setExportActive(false)
                                _exportProgress.value = 100
                                val uriStr = info.outputData.getString(ClipExportWorker.KEY_DOWNLOAD_URI)
                                val srtStr = info.outputData.getString(ClipExportWorker.KEY_SRT_URI)
                                _exportDownloadUrl.value = uriStr
                                _exportSrtUrl.value = srtStr?.takeIf { it.isNotBlank() }
                                _toastMessage.value = "Saved to Gallery (Movies/ClipForge)!"
                            }
                            WorkInfo.State.FAILED -> {
                                _isExporting.value = false
                                AdMobManager.setExportActive(false)
                                val err = info.outputData.getString(ClipExportWorker.KEY_ERROR)
                                    ?: "Video export failed."
                                _exportError.value = err
                                _toastMessage.value = "Export failed: $err"
                            }
                            WorkInfo.State.CANCELLED -> {
                                _isExporting.value = false
                                AdMobManager.setExportActive(false)
                                _exportError.value = "Export cancelled."
                            }
                            else -> {}
                        }
                    }
                }
            } catch (e: Exception) {
                _isExporting.value = false
                AdMobManager.setExportActive(false)
                _exportError.value = e.localizedMessage ?: "Unable to start export."
            }
        }
    }

    fun cancelActiveExport() {
        val clip = _selectedClip.value ?: return
        workManager.cancelUniqueWork(ClipExportWorker.uniqueWorkName(clip.id))
        _isExporting.value = false
        AdMobManager.setExportActive(false)
        _exportError.value = "Export cancelled by user. Tap Retry Export to resume."
        _toastMessage.value = "Clip export cancelled"
    }

    fun shareExportedFile(uriString: String) {
        try {
            val context = getApplication<Application>()
            val uri = Uri.parse(uriString)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Viral Short Clip").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            _toastMessage.value = "Unable to open share sheet: ${e.localizedMessage}"
        }
    }

    fun openExportedFile(uriString: String) {
        try {
            val context = getApplication<Application>()
            val uri = Uri.parse(uriString)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            _toastMessage.value = "Saved in Movies/ClipForge (${e.localizedMessage ?: "no video player found"})"
        }
    }

    fun regenerateMetadata(clipId: Long) {
        viewModelScope.launch {
            _toastMessage.value = "Regenerating video metadata..."
            try {
                val updated = repository.regenerateMetadataWithAi(clipId)
                _selectedMetadata.value = updated
                _toastMessage.value = "New titles and descriptions generated!"
            } catch (e: Exception) {
                _toastMessage.value = "Metadata error: ${e.localizedMessage}"
            }
        }
    }

    fun updateMetadataField(updated: PlatformMetadataEntity) {
        _selectedMetadata.value = updated
        viewModelScope.launch {
            repository.updateMetadata(updated)
        }
    }
}
