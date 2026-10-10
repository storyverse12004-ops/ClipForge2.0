package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ClipEntity
import com.example.ui.components.AdMobBanner
import com.example.ui.components.AdMobConfig
import com.example.ui.components.ClipForgeTopAppBar
import com.example.ui.components.SoundEffectManager
import com.example.ui.components.ViralityScoreBadge
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralAmber

@Composable
fun ClipListScreen(
    projectId: Long,
    clips: List<ClipEntity>,
    onNavigateBack: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenMetadata: (Long) -> Unit,
    onOpenExportModal: (ClipEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredClips = when (selectedFilter) {
        "90+ Score" -> clips.filter { it.viralityScore >= 90 }
        "Controversial" -> clips.filter { it.emotionalPeak.contains("Controversial", ignoreCase = true) || it.emotionalPeak.contains("Hook", ignoreCase = true) }
        "Framework" -> clips.filter { it.emotionalPeak.contains("Framework", ignoreCase = true) || it.emotionalPeak.contains("Insight", ignoreCase = true) }
        else -> clips
    }

    Scaffold(
        topBar = {
            ClipForgeTopAppBar(
                title = "Viral Moments",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = DarkBg,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${clips.size} Viral Moments Found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Ranked by virality score and story boundaries",
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1B4B)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ViralAmber, modifier = Modifier.size(14.dp))
                            Text(text = "Top: ${clips.maxOfOrNull { it.viralityScore } ?: 0}/100", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ViralAmber)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "90+ Score", "Controversial", "Framework").forEach { filter ->
                        val active = selectedFilter == filter
                        Surface(
                            onClick = {
                                SoundEffectManager.playTabSelect()
                                selectedFilter = filter
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) ElectricViolet.copy(alpha = 0.25f) else DarkSurfaceElevated,
                            modifier = Modifier.border(
                                1.dp,
                                if (active) ElectricViolet else DarkSurfaceHighlight,
                                RoundedCornerShape(16.dp)
                            )
                        ) {
                            Text(
                                text = filter,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            items(filteredClips, key = { it.id }) { clip ->
                ViralClipCard(
                    clip = clip,
                    onOpenEditor = { onOpenEditor(clip.id) },
                    onOpenMetadata = { onOpenMetadata(clip.id) },
                    onExport = { onOpenExportModal(clip) }
                )
            }

            item {
                AdMobBanner(
                    adUnitId = AdMobConfig.activeBannerUnitId,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ViralClipCard(
    clip: ClipEntity,
    onOpenEditor: () -> Unit,
    onOpenMetadata: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viral_clip_card_${clip.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViralityScoreBadge(score = clip.viralityScore)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF164E63).copy(alpha = 0.6f)
                ) {
                    Text(
                        text = clip.emotionalPeak,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                val durationSec = ((clip.endTimeMs - clip.startTimeMs) / 1000L).coerceAtLeast(1L)
                Text(
                    text = "${durationSec}s (${clip.startTimeMs / 1000}s - ${clip.endTimeMs / 1000}s)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val customClipThumb = remember(clip.thumbnailPath) {
                    if (clip.thumbnailPath.isNotBlank()) {
                        try {
                            val file = java.io.File(clip.thumbnailPath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            } else null
                        } catch (_: Exception) { null }
                    } else null
                }

                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(10.dp))
                        .clickable {
                            SoundEffectManager.playButtonTap()
                            onOpenEditor()
                        }
                ) {
                    if (customClipThumb != null) {
                        Image(
                            bitmap = customClipThumb.asImageBitmap(),
                            contentDescription = clip.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.thumb_viral_clip),
                            contentDescription = clip.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = clip.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A))
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ViralAmber
                        )
                        Text(
                            text = clip.hookText,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 2
                        )
                    }
                    Text(
                        text = clip.reasoning,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 2,
                        lineHeight = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "9:16:", fontSize = 10.sp, color = TextMuted)
                        listOf(
                            Triple("Shorts", R.drawable.ic_platform_youtube, 0xFFFF0000),
                            Triple("TikTok", R.drawable.ic_platform_tiktok, 0xFF00F2FE),
                            Triple("Reels", R.drawable.ic_platform_instagram, 0xFFE1306C),
                            Triple("Facebook", R.drawable.ic_platform_facebook, 0xFF1877F2)
                        ).forEach { (pName, iconRes, colorHex) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(colorHex).copy(alpha = 0.15f),
                                modifier = Modifier.border(0.5.dp, Color(colorHex).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Image(painter = painterResource(id = iconRes), contentDescription = pName, modifier = Modifier.size(10.dp))
                                    Text(text = pName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onOpenEditor()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceHighlight,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clip_btn_editor_${clip.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor & Styles", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
                Surface(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onOpenMetadata()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1B4B),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clip_btn_metadata_${clip.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Metadata", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NeonCyan)
                    }
                }
                Button(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onExport()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("clip_btn_export_${clip.id}")
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
