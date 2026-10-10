package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.media.VideoValidationResult
import com.example.data.media.VideoValidator
import com.example.data.model.SourceType
import com.example.ui.components.AdMobBanner
import com.example.ui.components.AdMobConfig
import com.example.ui.components.ClipForgeTopAppBar
import com.example.ui.components.SoundEffectManager
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralCoral
import com.example.ui.theme.ViralEmerald
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun UploadIngestionScreen(
    isJobRunning: Boolean = false,
    dailyRunsRemaining: Int = 3,
    onNavigateBack: () -> Unit,
    onStartPipeline: (
        title: String,
        sourceType: SourceType,
        sourceUrl: String,
        durationSeconds: Int,
        videoSizeMb: Float,
        videoWidth: Int,
        videoHeight: Int,
        thumbnailPath: String,
        removeSilence: Boolean,
        snapSentences: Boolean,
        speakerDiarization: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var videoTitle by remember { mutableStateOf("") }
    var validationState by remember { mutableStateOf<VideoValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var isStarting by remember { mutableStateOf(false) }

    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        isValidating = true
        coroutineScope.launch {
            val result = VideoValidator.validateAndPersistVideo(context, uri)
            validationState = result
            isValidating = false
            if (result.isValid && videoTitle.isBlank()) {
                val inferred = result.fileName.substringBeforeLast(".")
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                if (inferred.isNotBlank()) {
                    videoTitle = inferred
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        handlePickedUri(uri)
    }

    var removeSilence by remember { mutableStateOf(true) }
    var removeFillerWords by remember { mutableStateOf(true) }
    var speakerDiarization by remember { mutableStateOf(true) }

    val hasDailyRuns = dailyRunsRemaining > 0
    val isReadyToStart = validationState?.isValid == true && !isValidating && !isJobRunning && !isStarting && hasDailyRuns

    Scaffold(
        topBar = {
            ClipForgeTopAppBar(
                title = "Import Video",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = DarkBg,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(
                    1.dp,
                    when {
                        validationState?.isValid == true -> ViralEmerald
                        validationState?.isValid == false -> ViralCoral
                        else -> DarkSurfaceHighlight
                    },
                    RoundedCornerShape(14.dp)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentValidation = validationState
                    val previewBitmap = remember(currentValidation?.thumbnailPath) {
                        val path = currentValidation?.thumbnailPath
                        if (!path.isNullOrBlank()) {
                            try {
                                val file = java.io.File(path)
                                if (file.exists()) {
                                    android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                } else null
                            } catch (_: Exception) { null }
                        } else null
                    }

                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Video Preview Thumbnail",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 128.dp, height = 74.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ViralEmerald, RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        validationState?.isValid == true -> ViralEmerald.copy(alpha = 0.2f)
                                        validationState?.isValid == false -> ViralCoral.copy(alpha = 0.2f)
                                        else -> ElectricViolet.copy(alpha = 0.2f)
                                    }
                                )
                                .clickable(enabled = !isJobRunning) {
                                    SoundEffectManager.playButtonTap()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(
                                    color = NeonCyan,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = when {
                                        validationState?.isValid == true -> Icons.Default.CheckCircle
                                        validationState?.isValid == false -> Icons.Default.ErrorOutline
                                        else -> Icons.Default.CloudUpload
                                    },
                                    contentDescription = "Select Video",
                                    tint = when {
                                        validationState?.isValid == true -> ViralEmerald
                                        validationState?.isValid == false -> ViralCoral
                                        else -> ElectricViolet
                                    },
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = when {
                            isValidating -> "Validating video file..."
                            currentValidation != null -> currentValidation.fileName
                            else -> "Select a Video from Phone"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    if (currentValidation != null && currentValidation.isValid) {
                        val mins = currentValidation.durationSeconds / 60
                        val secs = currentValidation.durationSeconds % 60
                        Text(
                            text = String.format(
                                Locale.US,
                                "Validated: %dm %02ds   %.1f MB   %dx%d",
                                mins,
                                secs,
                                currentValidation.fileSizeMb,
                                currentValidation.width,
                                currentValidation.height
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ViralEmerald
                        )
                    } else {
                        Text(
                            text = "Select a video from your phone (5s to 60 minutes, max 2 GB)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    val errorToDisplay = currentValidation?.errorMessage
                    if (!errorToDisplay.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ViralCoral.copy(alpha = 0.15f))
                                .border(1.dp, ViralCoral, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                                .testTag("video_validation_error_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = ViralCoral,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorToDisplay,
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricViolet,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("choose_video_file_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (validationState?.isValid == true) "Change Video" else "Select Video",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PROJECT TITLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = videoTitle,
                        onValueChange = { videoTitle = it },
                        placeholder = { Text("e.g. Podcast Highlights", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricViolet,
                            unfocusedBorderColor = DarkSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_project_title")
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Options",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PROCESSING SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Auto-Cut Pauses & Silence", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Compresses silent pauses during export", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = removeSilence,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                removeSilence = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Snap to Sentence Boundaries", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Prevents mid-word cuts in 15-60s segments", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = removeFillerWords,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                removeFillerWords = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Multi-Speaker Labeling", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Identifies distinct speakers in transcript", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = speakerDiarization,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                speakerDiarization = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet)
                        )
                    }
                }
            }

            if (!hasDailyRuns) {
                Surface(
                    color = Color(0xFF78350F).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "You've reached your daily limit of 3 video processing runs. Please come back tomorrow!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (isJobRunning) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "A video processing job is currently running. Please wait for it to complete.",
                        fontSize = 12.sp,
                        color = NeonCyan,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (isJobRunning || isStarting) return@Button
                    val valid = validationState ?: return@Button
                    if (!valid.isValid) return@Button
                    isStarting = true
                    SoundEffectManager.playButtonTap()
                    val finalTitle = videoTitle.ifBlank { valid.fileName.substringBeforeLast(".") }
                    onStartPipeline(
                        finalTitle,
                        SourceType.UPLOAD,
                        valid.persistedUriString,
                        valid.durationSeconds,
                        valid.fileSizeMb,
                        valid.width,
                        valid.height,
                        valid.thumbnailPath,
                        removeSilence,
                        removeFillerWords,
                        speakerDiarization
                    )
                },
                enabled = isReadyToStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("start_viral_pipeline_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    disabledContainerColor = DarkSurfaceHighlight
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !hasDailyRuns -> "Daily Limit Reached (3/3)"
                        isJobRunning -> "Processing in Progress..."
                        isReadyToStart -> "Create Viral Clips"
                        else -> "Select a Video to Continue"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            AdMobBanner(
                adUnitId = AdMobConfig.activeBannerUnitId,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
