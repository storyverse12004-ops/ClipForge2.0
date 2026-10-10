package com.example

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.remote.ClipForgeCloudClient
import com.example.ui.components.AdMobFullScreenOverlayHost
import com.example.ui.components.AdMobManager
import com.example.ui.components.DownloadModal
import com.example.ui.components.SoundEffectManager
import com.example.ui.screens.ClipListScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.MetadataScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.UploadIngestionScreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.ClipForgeViewModel
import com.example.ui.viewmodel.ScreenDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ClipForgeCloudClient.init(this)
        SoundEffectManager.initialize(this)
        AdMobManager.initialize(this, initialActivity = this)
        setContent {
            MyApplicationTheme {
                ClipForgeApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AdMobManager.showAppOpenAdIfAvailable(this)
    }
}

@Composable
fun ClipForgeApp(viewModel: ClipForgeViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val triggerInterstitial: (Boolean, () -> Unit) -> Unit = { showToast, onDone ->
        AdMobManager.showInterstitial(activity, showFeedbackToast = showToast, onAdClosed = onDone)
    }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedClip by viewModel.selectedClip.collectAsStateWithLifecycle()
    val selectedClipSourceUri by viewModel.selectedClipSourceUri.collectAsStateWithLifecycle()
    val selectedMetadata by viewModel.selectedMetadata.collectAsStateWithLifecycle()
    val transcriptWords by viewModel.transcriptWords.collectAsStateWithLifecycle()
    val selectedPlatform by viewModel.selectedPlatform.collectAsStateWithLifecycle()
    val selectedRatio by viewModel.selectedRatio.collectAsStateWithLifecycle()
    val showPlatformOverlay by viewModel.showPlatformUiOverlay.collectAsStateWithLifecycle()
    val showSafeZoneGuide by viewModel.showSafeZoneGuides.collectAsStateWithLifecycle()
    val pipelineUiState by viewModel.pipelineUiState.collectAsStateWithLifecycle()
    val isJobActive by viewModel.isJobActive.collectAsStateWithLifecycle()
    val dailyRunsRemaining by viewModel.dailyRunsRemaining.collectAsStateWithLifecycle()

    // Export Modal State
    val showExportModal by viewModel.showExportModal.collectAsStateWithLifecycle()
    val exportQuality by viewModel.exportQuality.collectAsStateWithLifecycle()
    val exportFormat by viewModel.exportFormat.collectAsStateWithLifecycle()
    val exportFps by viewModel.exportFps.collectAsStateWithLifecycle()
    val exportBitrate by viewModel.exportBitrate.collectAsStateWithLifecycle()
    val exportBurnCaptions by viewModel.exportBurnCaptions.collectAsStateWithLifecycle()
    val exportRemoveWatermark by viewModel.exportRemoveWatermark.collectAsStateWithLifecycle()
    val exportIncludeSrt by viewModel.exportIncludeSrt.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportDownloadUrl by viewModel.exportDownloadUrl.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentScreen !is ScreenDestination.Editor && currentScreen !is ScreenDestination.Processing) {
                ClipForgeBottomNavBar(
                    currentScreen = currentScreen,
                    firstProjectId = allProjects.firstOrNull()?.id,
                    onSelectTab = { dest ->
                        SoundEffectManager.playTabSelect()
                        viewModel.navigateTo(dest)
                    }
                )
            }
        },
        containerColor = DarkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (val screen = currentScreen) {
            is ScreenDestination.Dashboard -> {
                DashboardScreen(
                    projects = allProjects,
                    onSelectProject = { projectId -> viewModel.selectProject(projectId) },
                    onRetryProject = { projectId -> viewModel.retryProjectPipeline(projectId) },
                    onDeleteProject = { projectId -> viewModel.deleteProject(projectId) },
                    onClearCache = { viewModel.clearAppCache() },
                    onStartNewIngestion = {
                        if (isJobActive) {
                            val activeProjId = viewModel.processingProjectId.value ?: 0L
                            viewModel.navigateTo(ScreenDestination.Processing("Current Video", activeProjId))
                        } else {
                            viewModel.navigateTo(ScreenDestination.Ingestion)
                        }
                    },
                    onShowInterstitial = { triggerInterstitial(true) {} },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ScreenDestination.Ingestion -> {
                BackHandler { viewModel.navigateTo(ScreenDestination.Dashboard) }
                UploadIngestionScreen(
                    isJobRunning = isJobActive,
                    dailyRunsRemaining = dailyRunsRemaining,
                    onNavigateBack = { viewModel.navigateTo(ScreenDestination.Dashboard) },
                    onStartPipeline = { title, sourceType, sourceUrl, durationSeconds, videoSizeMb, videoWidth, videoHeight, thumbnailPath, removeSilence, snapSentences, speakerDiarization ->
                        viewModel.startIngestionPipeline(
                            title = title,
                            sourceType = sourceType,
                            sourceUrl = sourceUrl,
                            durationSeconds = durationSeconds,
                            videoSizeMb = videoSizeMb,
                            videoWidth = videoWidth,
                            videoHeight = videoHeight,
                            thumbnailPath = thumbnailPath,
                            removeSilence = removeSilence,
                            snapSentences = snapSentences,
                            speakerDiarization = speakerDiarization
                        )
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ScreenDestination.Processing -> {
                BackHandler { viewModel.navigateTo(ScreenDestination.Dashboard) }
                ProcessingScreen(
                    projectTitle = screen.projectTitle,
                    projectId = screen.projectId,
                    uiState = pipelineUiState,
                    onRetry = {
                        if (screen.projectId > 0L) {
                            viewModel.retryProjectPipeline(screen.projectId)
                        }
                    },
                    onCancel = { viewModel.cancelActivePipeline() },
                    onViewClips = {
                        viewModel.navigateTo(ScreenDestination.ClipList(screen.projectId))
                    },
                    onNavigateBack = { viewModel.navigateTo(ScreenDestination.Dashboard) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ScreenDestination.ClipList -> {
                BackHandler { viewModel.navigateTo(ScreenDestination.Dashboard) }
                val clipsForProject by viewModel.getClipsForProject(screen.projectId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                ClipListScreen(
                    projectId = screen.projectId,
                    clips = clipsForProject,
                    onNavigateBack = { viewModel.navigateTo(ScreenDestination.Dashboard) },
                    onOpenEditor = { clipId ->
                        viewModel.loadClipForEditing(clipId)
                        viewModel.navigateTo(ScreenDestination.Editor(clipId))
                    },
                    onOpenMetadata = { clipId ->
                        viewModel.loadClipForEditing(clipId)
                        viewModel.navigateTo(ScreenDestination.Metadata(clipId))
                    },
                    onOpenExportModal = { clip ->
                        viewModel.openExportModal(clip)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ScreenDestination.Editor -> {
                BackHandler {
                    selectedClip?.projectId?.let { pId ->
                        viewModel.navigateTo(ScreenDestination.ClipList(pId))
                    } ?: viewModel.navigateTo(ScreenDestination.Dashboard)
                }
                EditorScreen(
                    clip = selectedClip,
                    words = transcriptWords,
                    videoUriString = selectedClipSourceUri,
                    platform = selectedPlatform,
                    videoRatio = selectedRatio,
                    showPlatformOverlay = showPlatformOverlay,
                    showSafeZoneGuide = showSafeZoneGuide,
                    onNavigateBack = {
                        selectedClip?.projectId?.let { pId ->
                            viewModel.navigateTo(ScreenDestination.ClipList(pId))
                        } ?: viewModel.navigateTo(ScreenDestination.Dashboard)
                    },
                    onSelectPlatform = { p -> viewModel.selectPlatform(p) },
                    onSelectRatio = { r -> viewModel.selectRatio(r) },
                    onToggleOverlay = { viewModel.togglePlatformUiOverlay() },
                    onToggleSafeZone = { viewModel.toggleSafeZoneGuides() },
                    onUpdateReframe = { mode -> viewModel.updateClipReframeMode(mode) },
                    onUpdateCaptionPreset = { preset -> viewModel.updateCaptionPreset(preset) },
                    onUpdateCaptionSize = { size -> viewModel.updateCaptionSize(size) },
                    onUpdateCaptionPosition = { pos -> viewModel.updateCaptionPosition(pos) },
                    onToggleEmojis = { viewModel.toggleEmojisEnabled() },
                    onToggleCutWord = { index -> viewModel.toggleCutWord(index) },
                    onToggleSilenceRemoval = { viewModel.toggleSilenceRemoval() },
                    onUpdateMusic = { music, ducking -> viewModel.updateBackgroundMusic(music, ducking) },
                    onUpdateTrim = { start, end -> viewModel.updateTrimRange(start, end) },
                    onOpenMetadata = { clipId -> viewModel.navigateTo(ScreenDestination.Metadata(clipId)) },
                    onOpenExport = { clip -> viewModel.openExportModal(clip) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is ScreenDestination.Metadata -> {
                BackHandler {
                    viewModel.navigateTo(ScreenDestination.Editor(screen.clipId))
                }
                MetadataScreen(
                    metadata = selectedMetadata,
                    onNavigateBack = {
                        viewModel.navigateTo(ScreenDestination.Editor(screen.clipId))
                    },
                    onRegenerate = { viewModel.regenerateMetadata(screen.clipId) },
                    onSaveField = { updated -> viewModel.updateMetadataField(updated) },
                    onShowToast = { msg -> viewModel.showToast(msg) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        if (showExportModal && selectedClip != null) {
            DownloadModal(
                clip = selectedClip!!,
                quality = exportQuality,
                format = exportFormat,
                fps = exportFps,
                bitrate = exportBitrate,
                burnCaptions = exportBurnCaptions,
                removeWatermark = exportRemoveWatermark,
                includeSrt = exportIncludeSrt,
                isExporting = isExporting,
                exportProgress = exportProgress,
                downloadUrl = exportDownloadUrl,
                exportError = exportError,
                onQualitySelected = { q -> viewModel.setExportQuality(q) },
                onFormatSelected = { f -> viewModel.setExportFormat(f) },
                onFpsSelected = { fps -> viewModel.setExportFps(fps) },
                onBitrateSelected = { b -> viewModel.setExportBitrate(b) },
                onToggleBurnCaptions = { c -> viewModel.setExportBurnCaptions(c) },
                onToggleRemoveWatermark = { w -> viewModel.setExportRemoveWatermark(w) },
                onToggleIncludeSrt = { s -> viewModel.setExportIncludeSrt(s) },
                onStartExport = { viewModel.startExport() },
                onCancelExport = { viewModel.cancelActiveExport() },
                onOpenDownloadedFile = { uri ->
                    triggerInterstitial(false) {
                        viewModel.openExportedFile(uri)
                    }
                },
                onShareDownloadedFile = { uri ->
                    triggerInterstitial(false) {
                        viewModel.shareExportedFile(uri)
                    }
                },
                onDismiss = {
                    val finishedExport = exportDownloadUrl != null && !isExporting
                    viewModel.closeExportModal()
                    if (finishedExport) {
                        triggerInterstitial(false) {}
                    }
                }
            )
        }

        if (!isExporting && currentScreen !is ScreenDestination.Processing && !isJobActive) {
            AdMobFullScreenOverlayHost()
        }
    }
}

@Composable
fun ClipForgeBottomNavBar(
    currentScreen: ScreenDestination,
    firstProjectId: Long?,
    onSelectTab: (ScreenDestination) -> Unit
) {
    val isDashboard = currentScreen is ScreenDestination.Dashboard
    val isIngestion = currentScreen is ScreenDestination.Ingestion
    val isClips = currentScreen is ScreenDestination.ClipList || currentScreen is ScreenDestination.Metadata

    NavigationBar(
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        modifier = Modifier
            .border(0.5.dp, DarkSurfaceHighlight)
            .testTag("bottom_navigation_bar")
    ) {
        NavigationBarItem(
            selected = isDashboard,
            onClick = { onSelectTab(ScreenDestination.Dashboard) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Dashboard,
                    contentDescription = "Studio"
                )
            },
            label = {
                Text(
                    text = "Studio",
                    fontSize = 11.sp,
                    fontWeight = if (isDashboard) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("bottom_nav_studio")
        )

        NavigationBarItem(
            selected = isIngestion,
            onClick = { onSelectTab(ScreenDestination.Ingestion) },
            icon = {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "New Clip"
                )
            },
            label = {
                Text(
                    text = "New Clip",
                    fontSize = 11.sp,
                    fontWeight = if (isIngestion) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("bottom_nav_new_clip")
        )

        NavigationBarItem(
            selected = isClips,
            onClick = {
                if (firstProjectId != null) {
                    onSelectTab(ScreenDestination.ClipList(firstProjectId))
                } else {
                    onSelectTab(ScreenDestination.Ingestion)
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Viral Clips"
                )
            },
            label = {
                Text(
                    text = "Viral Clips",
                    fontSize = 11.sp,
                    fontWeight = if (isClips) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NeonCyan,
                selectedTextColor = NeonCyan,
                indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                unselectedIconColor = TextMuted,
                unselectedTextColor = TextMuted
            ),
            modifier = Modifier.testTag("bottom_nav_clips")
        )
    }
}
