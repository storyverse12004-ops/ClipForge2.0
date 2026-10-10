package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CaptionPreset
import com.example.data.model.ClipEntity
import com.example.data.model.ReframeMode
import com.example.data.model.ShortsVideoRatio
import com.example.data.model.SocialPlatform
import com.example.data.model.WordTimestamp
import com.example.ui.components.AdMobBanner
import com.example.ui.components.AdMobConfig
import com.example.ui.components.ClipForgeTopAppBar
import com.example.ui.components.SoundEffectManager
import com.example.ui.components.VideoPlayerPreview
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralAmber

@Composable
fun EditorScreen(
    clip: ClipEntity?,
    words: List<WordTimestamp>,
    videoUriString: String? = null,
    platform: SocialPlatform,
    videoRatio: ShortsVideoRatio,
    showPlatformOverlay: Boolean,
    showSafeZoneGuide: Boolean,
    onNavigateBack: () -> Unit,
    onSelectPlatform: (SocialPlatform) -> Unit,
    onSelectRatio: (ShortsVideoRatio) -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleSafeZone: () -> Unit,
    onUpdateReframe: (ReframeMode) -> Unit,
    onUpdateCaptionPreset: (CaptionPreset) -> Unit,
    onUpdateCaptionSize: (Int) -> Unit,
    onUpdateCaptionPosition: (String) -> Unit,
    onToggleEmojis: () -> Unit = {},
    onToggleCutWord: (Int) -> Unit,
    onToggleSilenceRemoval: () -> Unit,
    onUpdateMusic: (String, Int) -> Unit,
    onUpdateTrim: (Long, Long) -> Unit,
    onOpenMetadata: (Long) -> Unit,
    onOpenExport: (ClipEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (clip == null) return

    var activeTab by remember { mutableIntStateOf(0) }
    val maxTimelineSec = remember(clip.endTimeMs) {
        maxOf((clip.endTimeMs / 1000f) + 30f, 120f)
    }
    var trimRange by remember(clip.id) {
        val s = (clip.startTimeMs / 1000f).coerceAtLeast(0f)
        val e = (clip.endTimeMs / 1000f).coerceAtLeast(s + 5f).coerceAtMost(maxTimelineSec)
        mutableStateOf(s..e)
    }
    var selectedMusic by remember(clip.id) { mutableStateOf(clip.backgroundMusic) }
    var duckingLevel by remember(clip.id) { mutableFloatStateOf(clip.duckingPercent.toFloat()) }

    Scaffold(
        topBar = {
            ClipForgeTopAppBar(
                title = "Shorts Editor",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            Surface(
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkSurfaceHighlight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onOpenMetadata(clip.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B4B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_btn_metadata")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Metadata", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
                    }
                    Button(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onOpenExport(clip)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_btn_export")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export (${platform.shortName})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlatformSelectorBar(
                selectedPlatform = platform,
                onSelectPlatform = onSelectPlatform
            )

            RatioAndOverlayBar(
                selectedRatio = videoRatio,
                showOverlay = showPlatformOverlay,
                showSafeZone = showSafeZoneGuide,
                onSelectRatio = onSelectRatio,
                onToggleOverlay = onToggleOverlay,
                onToggleSafeZone = onToggleSafeZone
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayerPreview(
                    reframeMode = clip.reframeMode,
                    captionPreset = clip.captionPreset,
                    captionFontSize = clip.captionFontSize,
                    captionPosition = clip.captionPosition,
                    backgroundMusic = clip.backgroundMusic,
                    emojisEnabled = clip.emojisEnabled,
                    platform = platform,
                    videoRatio = videoRatio,
                    showPlatformOverlay = showPlatformOverlay,
                    showSafeZoneGuide = showSafeZoneGuide,
                    videoUriString = videoUriString,
                    startTimeMs = clip.startTimeMs,
                    endTimeMs = clip.endTimeMs,
                    transcriptWords = words,
                    modifier = Modifier.fillMaxSize()
                )
            }

            TabRow(
                selectedTabIndex = activeTab,
                containerColor = DarkSurfaceElevated,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                        color = ElectricViolet
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 0
                    },
                    text = { Text("Platform", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_platform")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 1
                    },
                    text = { Text("Captions", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_captions")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 2
                    },
                    text = { Text("Framing", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_reframe")
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 3
                    },
                    text = { Text("Trim", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_trim")
                )
                Tab(
                    selected = activeTab == 4,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 4
                    },
                    text = { Text("Cut Words", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_cut_words")
                )
                Tab(
                    selected = activeTab == 5,
                    onClick = {
                        SoundEffectManager.playTabSelect()
                        activeTab = 5
                    },
                    text = { Text("Audio", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("tab_editor_audio")
                )
            }

            when (activeTab) {
                0 -> PlatformSpecsTabPanel(
                    currentPlatform = platform,
                    currentRatio = videoRatio,
                    onSelectRatio = onSelectRatio
                )
                1 -> CaptionsTabPanel(
                    currentPreset = clip.captionPreset,
                    currentFontSize = clip.captionFontSize,
                    currentPosition = clip.captionPosition,
                    emojisEnabled = clip.emojisEnabled,
                    onSelectPreset = onUpdateCaptionPreset,
                    onUpdateFontSize = onUpdateCaptionSize,
                    onUpdatePosition = onUpdateCaptionPosition,
                    onToggleEmojis = onToggleEmojis
                )
                2 -> ReframeTabPanel(
                    currentMode = clip.reframeMode,
                    onSelectMode = onUpdateReframe
                )
                3 -> TimelineTrimTabPanel(
                    trimRange = trimRange,
                    totalDurationSec = maxTimelineSec,
                    onRangeChanged = { newRange ->
                        trimRange = newRange
                        onUpdateTrim((newRange.start * 1000).toLong(), (newRange.endInclusive * 1000).toLong())
                    }
                )
                4 -> TranscriptCutTabPanel(
                    words = words,
                    onToggleCutWord = onToggleCutWord
                )
                5 -> AudioTabPanel(
                    selectedMusic = selectedMusic,
                    duckingPercent = duckingLevel,
                    silenceRemoved = clip.silenceRemoved,
                    onSelectMusic = {
                        selectedMusic = it
                        onUpdateMusic(it, duckingLevel.toInt())
                    },
                    onDuckingChanged = {
                        duckingLevel = it
                        onUpdateMusic(selectedMusic, it.toInt())
                    },
                    onToggleSilence = onToggleSilenceRemoval
                )
            }

            AdMobBanner(
                adUnitId = AdMobConfig.activeBannerUnitId,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PlatformSelectorBar(
    selectedPlatform: SocialPlatform,
    onSelectPlatform: (SocialPlatform) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "TARGET PLATFORM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SocialPlatform.entries.forEach { plat ->
                    val isSelected = plat == selectedPlatform
                    Surface(
                        onClick = {
                            SoundEffectManager.playTabSelect()
                            onSelectPlatform(plat)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(plat.brandColorHex).copy(alpha = 0.22f) else DarkSurface,
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (isSelected) Color(plat.brandColorHex) else DarkSurfaceHighlight,
                                RoundedCornerShape(10.dp)
                            )
                            .testTag("platform_chip_${plat.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = plat.iconResId),
                                contentDescription = plat.displayName,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = plat.shortName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatioAndOverlayBar(
    selectedRatio: ShortsVideoRatio,
    showOverlay: Boolean,
    showSafeZone: Boolean,
    onSelectRatio: (ShortsVideoRatio) -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleSafeZone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ShortsVideoRatio.entries.forEach { r ->
                val active = r == selectedRatio
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onSelectRatio(r)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) ElectricViolet.copy(alpha = 0.3f) else DarkSurfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (active) ElectricViolet else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = r.ratioKey,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Surface(
            onClick = {
                SoundEffectManager.playButtonTap()
                onToggleOverlay()
            },
            shape = RoundedCornerShape(8.dp),
            color = if (showOverlay) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
            modifier = Modifier.border(1.dp, if (showOverlay) NeonCyan else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (showOverlay) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (showOverlay) NeonCyan else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "UI Overlay",
                    fontSize = 10.sp,
                    color = if (showOverlay) Color.White else TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            onClick = {
                SoundEffectManager.playButtonTap()
                onToggleSafeZone()
            },
            shape = RoundedCornerShape(8.dp),
            color = if (showSafeZone) ViralAmber.copy(alpha = 0.2f) else DarkSurfaceElevated,
            modifier = Modifier.border(1.dp, if (showSafeZone) ViralAmber else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = null,
                    tint = if (showSafeZone) ViralAmber else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Margins",
                    fontSize = 10.sp,
                    color = if (showSafeZone) Color.White else TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlatformSpecsTabPanel(
    currentPlatform: SocialPlatform,
    currentRatio: ShortsVideoRatio,
    onSelectRatio: (ShortsVideoRatio) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = currentPlatform.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "${currentPlatform.displayName} Specifications",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Optimized for vertical short-form distribution",
                        fontSize = 11.sp,
                        color = NeonCyan
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpecRow(label = "Standard Video Ratio", value = "${currentRatio.ratioKey} (${currentRatio.resolutionLabel})")
                SpecRow(label = "Max Recommended Duration", value = "${currentPlatform.maxDurationSeconds} seconds")
                SpecRow(label = "Export Resolution", value = currentPlatform.maxResolution)
                SpecRow(label = "Safe Zone Recommendation", value = currentPlatform.safeZoneNote)
            }

            Text(
                text = "RATIO PRESETS FOR ${currentPlatform.shortName.uppercase()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            ShortsVideoRatio.entries.forEach { r ->
                val active = r == currentRatio
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onSelectRatio(r)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) ElectricViolet.copy(alpha = 0.25f) else DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (active) ElectricViolet else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "${r.label} - ${r.resolutionLabel}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextPrimary)
                            Text(text = r.bestFor, fontSize = 10.sp, color = TextMuted)
                        }
                        if (active) {
                            Text(text = "APPLIED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = TextMuted)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun CaptionsTabPanel(
    currentPreset: CaptionPreset,
    currentFontSize: Int,
    currentPosition: String,
    emojisEnabled: Boolean = true,
    onSelectPreset: (CaptionPreset) -> Unit,
    onUpdateFontSize: (Int) -> Unit,
    onUpdatePosition: (String) -> Unit,
    onToggleEmojis: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ANIMATED CAPTION OVERLAYS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Synced to word-level timestamps and burned in on export",
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricViolet.copy(alpha = 0.2f),
                    modifier = Modifier.border(0.5.dp, ElectricViolet, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Captions",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "SUBTITLE STYLES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            CaptionPreset.entries.forEach { preset ->
                val active = preset == currentPreset
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onSelectPreset(preset)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) ElectricViolet.copy(alpha = 0.25f) else DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (active) ElectricViolet else DarkSurfaceHighlight,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = preset.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextPrimary)
                            Text(text = preset.description, fontSize = 11.sp, color = TextMuted)
                        }
                        if (active) {
                            Text(text = "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Subtitle Font Size", fontSize = 12.sp, color = TextSecondary)
                    Text(text = "${currentFontSize}sp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
                Slider(
                    value = currentFontSize.toFloat(),
                    onValueChange = { onUpdateFontSize(it.toInt()) },
                    valueRange = 16f..36f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = ElectricViolet)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Vertical Alignment", fontSize = 12.sp, color = TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("TOP" to "Top", "CENTER" to "Center", "BOTTOM_THIRD" to "Bottom Third").forEach { (posKey, label) ->
                        val active = currentPosition == posKey
                        Surface(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onUpdatePosition(posKey)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) NeonCyan.copy(alpha = 0.2f) else DarkSurface,
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, if (active) NeonCyan else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) Color.White else TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Contextual Emoji Highlights", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(text = "Auto-inserts keyword emojis in preview and captions", fontSize = 11.sp, color = TextMuted)
                }
                Switch(
                    checked = emojisEnabled,
                    onCheckedChange = {
                        SoundEffectManager.playButtonTap()
                        onToggleEmojis()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet),
                    modifier = Modifier.testTag("switch_caption_emojis")
                )
            }
        }
    }
}

@Composable
private fun ReframeTabPanel(
    currentMode: ReframeMode,
    onSelectMode: (ReframeMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "9:16 VERTICAL REFRAMING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Smart center-crop and presentation modes",
                        fontSize = 10.sp,
                        color = NeonCyan
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = com.example.ui.theme.ViralEmerald.copy(alpha = 0.2f),
                    modifier = Modifier.border(0.5.dp, com.example.ui.theme.ViralEmerald, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Auto Framing",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.example.ui.theme.ViralEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "CROP & FIT MODES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            ReframeMode.entries.forEach { mode ->
                val active = mode == currentMode
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onSelectMode(mode)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) ElectricViolet.copy(alpha = 0.25f) else DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (active) ElectricViolet else DarkSurfaceHighlight,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = mode.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = mode.description, fontSize = 11.sp, color = TextMuted)
                        }
                        if (active) {
                            Text(text = "SELECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTrimTabPanel(
    trimRange: ClosedFloatingPointRange<Float>,
    totalDurationSec: Float,
    onRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "TRIM CLIP DURATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Start Timestamp", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "${trimRange.start.toInt()}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Clip Duration", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "${(trimRange.endInclusive - trimRange.start).toInt().coerceAtLeast(1)}s",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ViralAmber
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "End Timestamp", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = "${trimRange.endInclusive.toInt()}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            RangeSlider(
                value = trimRange,
                onValueChange = onRangeChanged,
                valueRange = 0f..totalDurationSec,
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = ElectricViolet,
                    inactiveTrackColor = DarkSurfaceHighlight
                )
            )

            Text(
                text = "Boundaries are aligned with natural speech breaks.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptCutTabPanel(
    words: List<WordTimestamp>,
    onToggleCutWord: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TAP WORDS TO CUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "Tap word to remove from clip",
                    fontSize = 11.sp,
                    color = ViralAmber
                )
            }
            Text(
                text = "Cut words will be automatically removed from the video.",
                fontSize = 11.sp,
                color = TextMuted
            )

            if (words.isEmpty()) {
                Text(
                    text = "No transcript words available for this clip.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    words.forEachIndexed { index, item ->
                        val isCut = item.isCut
                        Surface(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onToggleCutWord(index)
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isCut) Color(0xFF7F1D1D) else DarkSurfaceHighlight.copy(alpha = 0.6f),
                            modifier = Modifier.border(
                                0.5.dp,
                                if (isCut) Color(0xFFEF4444) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                        ) {
                            Text(
                                text = item.word,
                                fontSize = 12.sp,
                                fontWeight = if (isCut) FontWeight.Normal else FontWeight.Medium,
                                color = if (isCut) Color(0xFFFCA5A5) else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = if (isCut) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTabPanel(
    selectedMusic: String,
    duckingPercent: Float,
    silenceRemoved: Boolean,
    onSelectMusic: (String) -> Unit,
    onDuckingChanged: (Float) -> Unit,
    onToggleSilence: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "BACKGROUND MUSIC & AUDIO BALANCING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            listOf("None", "Lo-Fi Beats", "Upbeat Viral", "Dramatic Cinematic", "Tech Ambient").forEach { track ->
                val active = selectedMusic == track
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onSelectMusic(track)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) NeonCyan.copy(alpha = 0.2f) else DarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (active) NeonCyan else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (active) NeonCyan else TextMuted, modifier = Modifier.size(16.dp))
                            Text(text = track, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) Color.White else TextPrimary)
                        }
                        if (active) {
                            Text(text = "ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Speech Auto-Ducking", fontSize = 12.sp, color = TextSecondary)
                    Text(text = "${duckingPercent.toInt()}% attenuation", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                }
                Slider(
                    value = duckingPercent,
                    onValueChange = onDuckingChanged,
                    valueRange = 50f..95f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = ElectricViolet)
                )
                Text(text = "Lowers background backing music automatically during active speech.", fontSize = 11.sp, color = TextMuted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Auto-Cut Pauses & Dead Silences", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(text = "Automatically trims pauses and silent moments", fontSize = 11.sp, color = TextMuted)
                }
                Switch(
                    checked = silenceRemoved,
                    onCheckedChange = {
                        SoundEffectManager.playButtonTap()
                        onToggleSilence()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet)
                )
            }
        }
    }
}
