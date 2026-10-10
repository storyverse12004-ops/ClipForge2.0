package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

enum class PipelineStep(
    val title: String,
    val defaultSubtitle: String,
    val icon: ImageVector
) {
    IMPORTING(
        title = "Importing video",
        defaultSubtitle = "Reading media file and preparing video stream",
        icon = Icons.Default.CloudUpload
    ),
    EXTRACTING_AUDIO(
        title = "Extracting audio",
        defaultSubtitle = "Processing clear voice audio track",
        icon = Icons.Default.GraphicEq
    ),
    TRANSCRIBING(
        title = "Transcribing speech",
        defaultSubtitle = "Transcribing spoken words with timestamps",
        icon = Icons.Default.Videocam
    ),
    FINDING_MOMENTS(
        title = "Finding viral moments",
        defaultSubtitle = "Scoring high-retention hooks and story peaks",
        icon = Icons.Default.Psychology
    ),
    WRITING_TITLES(
        title = "Writing titles",
        defaultSubtitle = "Crafting headlines, hooks and social tags",
        icon = Icons.Default.Subtitles
    ),
    REFRAMING(
        title = "Reframing to 9:16",
        defaultSubtitle = "Applying smart vertical crop and framing",
        icon = Icons.Default.CropPortrait
    ),
    ADDING_CAPTIONS(
        title = "Adding captions",
        defaultSubtitle = "Syncing animated subtitles with spoken words",
        icon = Icons.Default.ClosedCaption
    ),
    EXPORTING(
        title = "Exporting",
        defaultSubtitle = "Preparing short clips for high-quality export",
        icon = Icons.Default.Movie
    ),
    DONE(
        title = "Done",
        defaultSubtitle = "Viral short-form clips are ready to share",
        icon = Icons.Default.CheckCircle
    )
}

data class PipelineUiState(
    val currentStep: PipelineStep = PipelineStep.IMPORTING,
    val stepProgressPercent: Int = 0,
    val overallProgressPercent: Int = 0,
    val liveSubtitle: String = "",
    val completedSteps: Set<PipelineStep> = emptySet(),
    val failedStep: PipelineStep? = null,
    val errorMessage: String? = null,
    val isDone: Boolean = false,
    val estimatedTimeRemainingSeconds: Long? = null,
    val detectedClipsPreview: List<ClipEntity> = emptyList()
)
