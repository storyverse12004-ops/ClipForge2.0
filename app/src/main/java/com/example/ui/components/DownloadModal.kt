package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.ClipEntity
import com.example.ui.theme.DarkSurface
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
fun DownloadModal(
    clip: ClipEntity,
    quality: String,
    format: String,
    fps: Int,
    bitrate: String,
    burnCaptions: Boolean,
    removeWatermark: Boolean,
    includeSrt: Boolean,
    isExporting: Boolean,
    exportProgress: Int,
    downloadUrl: String?,
    exportError: String? = null,
    onQualitySelected: (String) -> Unit,
    onFormatSelected: (String) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onBitrateSelected: (String) -> Unit,
    onToggleBurnCaptions: (Boolean) -> Unit,
    onToggleRemoveWatermark: (Boolean) -> Unit,
    onToggleIncludeSrt: (Boolean) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit = {},
    onOpenDownloadedFile: (String) -> Unit = {},
    onShareDownloadedFile: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onStartExport()
    }

    fun requestNotifPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onStartExport()
    }

    Dialog(onDismissRequest = { if (!isExporting) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(20.dp))
                .testTag("download_modal_dialog"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Export Short Clip",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Rendered on-device in high quality",
                            fontSize = 11.sp,
                            color = NeonCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onDismiss()
                        },
                        enabled = !isExporting,
                        modifier = Modifier.size(32.dp).testTag("close_download_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close modal",
                            tint = TextMuted
                        )
                    }
                }

                // Platform preset buttons
                Text(
                    text = "PLATFORM PRESETS (9:16 OPTIMIZED)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlatformPresetChip(
                        name = "YouTube",
                        ratio = "9:16",
                        iconRes = R.drawable.ic_platform_youtube,
                        active = quality == "1080p" && fps == 60,
                        onClick = {
                            onQualitySelected("1080p")
                            onFpsSelected(60)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PlatformPresetChip(
                        name = "TikTok",
                        ratio = "9:16",
                        iconRes = R.drawable.ic_platform_tiktok,
                        active = quality == "1080p" && fps == 30,
                        onClick = {
                            onQualitySelected("1080p")
                            onFpsSelected(30)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PlatformPresetChip(
                        name = "Instagram",
                        ratio = "9:16",
                        iconRes = R.drawable.ic_platform_instagram,
                        active = quality == "720p" && fps == 30,
                        onClick = {
                            onQualitySelected("720p")
                            onFpsSelected(30)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    PlatformPresetChip(
                        name = "Facebook",
                        ratio = "9:16",
                        iconRes = R.drawable.ic_platform_facebook,
                        active = quality == "480p",
                        onClick = {
                            onQualitySelected("480p")
                            onFpsSelected(30)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Resolution / Quality Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RESOLUTION & QUALITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "Up to 1080p Full HD Free",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan.copy(alpha = 0.12f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Full HD 1080p, 720p and 480p save directly to Movies/ClipForge.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QualityOptionCard(
                            label = "480p",
                            resolution = "480x854",
                            fileSize = "Fast SD",
                            selected = quality == "480p",
                            badge = "FREE",
                            modifier = Modifier.weight(1f),
                            onClick = { onQualitySelected("480p") }
                        )
                        QualityOptionCard(
                            label = "720p",
                            resolution = "720x1280",
                            fileSize = "HD 9:16",
                            selected = quality == "720p",
                            badge = "FREE HD",
                            modifier = Modifier.weight(1f),
                            onClick = { onQualitySelected("720p") }
                        )
                        QualityOptionCard(
                            label = "1080p",
                            resolution = "1080x1920",
                            fileSize = "Full HD",
                            selected = quality == "1080p",
                            badge = "FREE FHD",
                            modifier = Modifier.weight(1f),
                            onClick = { onQualitySelected("1080p") }
                        )
                    }
                }

                // Format & FPS Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "CONTAINER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectableChip(label = "MP4", selected = format == "MP4", onClick = { onFormatSelected("MP4") })
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "TARGET FPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectableChip(label = "30 FPS", selected = fps == 30, onClick = { onFpsSelected(30) })
                            SelectableChip(label = "60 FPS", selected = fps == 60, onClick = { onFpsSelected(60) })
                        }
                    }
                }

                // Bitrate profile
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "ENCODING PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectableChip(label = "Compressed", selected = bitrate == "Compressed", onClick = { onBitrateSelected("Compressed") })
                        SelectableChip(label = "Balanced", selected = bitrate == "Balanced", onClick = { onBitrateSelected("Balanced") })
                        SelectableChip(label = "High", selected = bitrate == "High", onClick = { onBitrateSelected("High") })
                    }
                }

                // Toggles: Watermark, Burned Captions, SRT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Burn-in Captions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Renders chosen preset into video frames", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = burnCaptions,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                onToggleBurnCaptions(it)
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
                            Text(text = "Remove ClipForge Watermark", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Clean export without badge", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = removeWatermark,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                onToggleRemoveWatermark(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ViralAmber, checkedTrackColor = Color(0xFF78350F))
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Include Separate SRT Subtitles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = "Word-level timestamped subtitle file", fontSize = 11.sp, color = TextMuted)
                        }
                        Switch(
                            checked = includeSrt,
                            onCheckedChange = {
                                SoundEffectManager.playButtonTap()
                                onToggleIncludeSrt(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = ElectricViolet)
                        )
                    }
                }

                if (!exportError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ViralCoral.copy(alpha = 0.15f))
                            .border(1.dp, ViralCoral, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ViralCoral, modifier = Modifier.size(18.dp))
                        Text(text = exportError, fontSize = 12.sp, color = Color.White)
                    }
                }

                // Export Progress & Render Status
                if (isExporting || downloadUrl != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (downloadUrl != null) "Saved to Gallery (Movies/ClipForge)" else "Rendering $quality ($exportProgress%)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (downloadUrl != null) ViralEmerald else NeonCyan
                            )
                            Text(
                                text = if (downloadUrl != null) "100%" else "Processing",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        LinearProgressIndicator(
                            progress = { exportProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (downloadUrl != null) ViralEmerald else NeonCyan,
                            trackColor = DarkSurfaceHighlight
                        )
                    }
                }

                // Action Buttons
                if (downloadUrl != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onOpenDownloadedFile(downloadUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("download_completed_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ViralEmerald)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Open Video", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                onShareDownloadedFile(downloadUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_completed_button")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Video", tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Clip", fontWeight = FontWeight.Bold, color = NeonCyan, fontSize = 13.sp)
                        }
                    }
                } else if (isExporting) {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            onCancelExport()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("cancel_export_render_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Export", tint = ViralCoral)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cancel Export ($exportProgress%)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ViralCoral
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            requestNotifPermissionThenStart()
                        },
                        enabled = !isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_export_render_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (exportError != null) "Retry Export ($quality)" else "Render & Save to Gallery ($quality)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformPresetChip(
    name: String,
    ratio: String,
    iconRes: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = {
            SoundEffectManager.playButtonTap()
            onClick()
        },
        shape = RoundedCornerShape(10.dp),
        color = if (active) ElectricViolet.copy(alpha = 0.25f) else DarkSurfaceElevated,
        modifier = modifier.border(
            1.dp,
            if (active) ElectricViolet else DarkSurfaceHighlight,
            RoundedCornerShape(10.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = name,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else TextPrimary,
                maxLines = 1
            )
            Text(
                text = ratio,
                fontSize = 9.sp,
                color = if (active) NeonCyan else TextMuted
            )
        }
    }
}

@Composable
private fun QualityOptionCard(
    label: String,
    resolution: String,
    fileSize: String,
    selected: Boolean,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) ElectricViolet.copy(alpha = 0.25f) else DarkSurfaceElevated)
            .border(
                1.dp,
                if (selected) ElectricViolet else DarkSurfaceHighlight,
                RoundedCornerShape(10.dp)
            )
            .clickable {
                SoundEffectManager.playButtonTap()
                onClick()
            }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else TextPrimary
            )
            Text(text = resolution, fontSize = 8.sp, color = TextMuted)
            Text(text = fileSize, fontSize = 9.sp, color = if (selected) NeonCyan else TextSecondary)
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = {
            SoundEffectManager.playButtonTap()
            onClick()
        },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceElevated,
        modifier = Modifier.border(
            1.dp,
            if (selected) NeonCyan else DarkSurfaceHighlight,
            RoundedCornerShape(8.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else TextSecondary
            )
        }
    }
}
