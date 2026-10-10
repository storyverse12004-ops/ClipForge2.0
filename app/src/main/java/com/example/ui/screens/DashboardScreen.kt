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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ProjectEntity
import com.example.data.model.ProjectStatus
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
import com.example.ui.theme.ViralAmber
import com.example.ui.theme.ViralCoral
import com.example.ui.theme.ViralEmerald

@Composable
fun DashboardScreen(
    projects: List<ProjectEntity>,
    onSelectProject: (Long) -> Unit,
    onRetryProject: (Long) -> Unit = {},
    onDeleteProject: (Long) -> Unit = {},
    onClearCache: () -> Unit = {},
    onStartNewIngestion: () -> Unit,
    onShowInterstitial: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            ClipForgeTopAppBar(
                title = "ClipForge",
                canNavigateBack = false
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    SoundEffectManager.playButtonTap()
                    onStartNewIngestion()
                },
                containerColor = ElectricViolet,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("dashboard_fab_new_clip")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Clip Project"
                )
            }
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
                HeroStudioBanner(onStartNewIngestion = onStartNewIngestion)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Your Video Projects",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${projects.size} Projects",
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onClearCache()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dashboard_clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear temporary cache",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear Cache",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }
            }

            if (projects.isEmpty()) {
                item {
                    EmptyProjectsState(onStartNewIngestion = onStartNewIngestion)
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = {
                            if (project.status == ProjectStatus.FAILED) {
                                onRetryProject(project.id)
                            } else {
                                onSelectProject(project.id)
                            }
                        },
                        onRetry = { onRetryProject(project.id) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }

            item {
                AdMobBanner(
                    adUnitId = AdMobConfig.activeBannerUnitId,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun HeroStudioBanner(
    onStartNewIngestion: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF3B0764),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .border(1.dp, ElectricViolet.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ViralAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "VIRAL MOMENT ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ViralAmber
                )
            }
            Text(
                text = "Turn Long Videos Into Viral 9:16 Shorts",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 24.sp
            )
            Text(
                text = "Word-level timestamps • Viral moment detection • 9:16 vertical reframing • Burned-in captions.",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Ratios for:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                listOf(
                    Triple("Shorts", R.drawable.ic_platform_youtube, 0xFFFF0000),
                    Triple("TikTok", R.drawable.ic_platform_tiktok, 0xFF00F2FE),
                    Triple("Reels", R.drawable.ic_platform_instagram, 0xFFE1306C),
                    Triple("Facebook", R.drawable.ic_platform_facebook, 0xFF1877F2)
                ).forEach { (name, iconRes, colorHex) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(colorHex).copy(alpha = 0.18f),
                        modifier = Modifier.border(0.5.dp, Color(colorHex).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Image(painter = painterResource(id = iconRes), contentDescription = name, modifier = Modifier.size(11.dp))
                            Text(text = name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    SoundEffectManager.playButtonTap()
                    onStartNewIngestion()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("hero_forge_clips_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Select Video",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Video & Create Clips", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val isFailed = project.status == ProjectStatus.FAILED
    val isDone = project.status == ProjectStatus.READY || project.status == ProjectStatus.DONE
    val isProcessing = !isDone && !isFailed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                SoundEffectManager.playButtonTap()
                onClick()
            }
            .testTag("project_card_${project.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val customThumbBitmap = androidx.compose.runtime.remember(project.thumbnailPath) {
                    if (project.thumbnailPath.isNotBlank()) {
                        try {
                            val file = java.io.File(project.thumbnailPath)
                            if (file.exists()) {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            } else null
                        } catch (_: Exception) { null }
                    } else null
                }

                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (isFailed) ViralCoral else DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                ) {
                    if (customThumbBitmap != null) {
                        Image(
                            bitmap = customThumbBitmap.asImageBitmap(),
                            contentDescription = project.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.thumb_tech_podcast),
                            contentDescription = project.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        val mins = project.durationSeconds / 60
                        val secs = project.durationSeconds % 60
                        Text(
                            text = if (mins > 0) "${mins}m ${secs}s" else "${secs}s",
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = project.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeColor = when (project.status) {
                            ProjectStatus.READY, ProjectStatus.DONE -> ViralEmerald
                            ProjectStatus.FAILED -> ViralCoral
                            else -> NeonCyan
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (isProcessing) "PROCESSING" else project.status.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isDone) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Clips Count",
                                    tint = ViralAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${project.clipCount} Viral Clips",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ViralAmber
                                )
                            }
                        } else if (isProcessing) {
                            Text(
                                text = "Processing video...",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                        }
                    }

                    val resLabel = if (project.videoWidth > 0 && project.videoHeight > 0) {
                        " • ${project.videoWidth}x${project.videoHeight}"
                    } else ""
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f MB%s", project.videoSizeMb, resLabel),
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = {
                        SoundEffectManager.playButtonTap()
                        onDelete()
                    },
                    modifier = Modifier.size(32.dp).testTag("delete_project_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete project",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isFailed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ViralCoral.copy(alpha = 0.12f))
                        .border(0.5.dp, ViralCoral, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ViralCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = project.errorMessage.ifBlank { "Processing failed. Tap Retry to try again." },
                            fontSize = 11.sp,
                            color = Color.White,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onRetry()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("retry_project_${project.id}")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onStartNewIngestion: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(16.dp))
            .testTag("dashboard_empty_state_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ElectricViolet.copy(alpha = 0.15f))
                    .border(1.dp, ElectricViolet.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "No video projects yet",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Import a video from your device to automatically extract speech, detect highlights, and create vertical 9:16 clips.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    SoundEffectManager.playButtonTap()
                    onStartNewIngestion()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("empty_state_create_clip_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Video to Begin", fontWeight = FontWeight.Bold)
            }
        }
    }
}
