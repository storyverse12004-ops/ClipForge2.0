package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.CaptionPreset
import com.example.data.model.ReframeMode
import com.example.data.model.ShortsVideoRatio
import com.example.data.model.SocialPlatform
import com.example.data.model.WordTimestamp
import com.example.ui.theme.CaptionGreen
import com.example.ui.theme.CaptionNeonCyan
import com.example.ui.theme.CaptionYellow
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerPreview(
    reframeMode: ReframeMode,
    captionPreset: CaptionPreset,
    captionFontSize: Int,
    captionPosition: String,
    backgroundMusic: String,
    emojisEnabled: Boolean,
    platform: SocialPlatform = SocialPlatform.TIKTOK,
    videoRatio: ShortsVideoRatio = ShortsVideoRatio.RATIO_9_16,
    showPlatformOverlay: Boolean = true,
    showSafeZoneGuide: Boolean = true,
    videoUriString: String? = null,
    startTimeMs: Long = 0L,
    endTimeMs: Long = 30000L,
    transcriptWords: List<WordTimestamp> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var activeWordIndex by remember { mutableIntStateOf(0) }
    var currentPosMs by remember { mutableLongStateOf(startTimeMs) }

    val activeTranscriptWords = remember(transcriptWords) {
        transcriptWords.filter { !it.isCut }
    }

    val karaokeWords = remember(activeTranscriptWords) {
        if (activeTranscriptWords.isNotEmpty()) {
            activeTranscriptWords.map { w ->
                w.word.uppercase(Locale.getDefault()) to inferEmoji(w.word)
            }
        } else {
            listOf(
                "CAPTION" to "🔥",
                "PREVIEW" to "✨",
                "READY" to "⚡",
                "FOR" to "",
                "9:16" to "🚀",
                "EXPORT" to "🎬"
            )
        }
    }

    val exoPlayer = remember(videoUriString, startTimeMs, endTimeMs) {
        if (!videoUriString.isNullOrBlank()) {
            try {
                ExoPlayer.Builder(context).build().apply {
                    val clipping = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startTimeMs.coerceAtLeast(0L))
                        .setEndPositionMs(endTimeMs.coerceAtLeast(startTimeMs + 1000L))
                        .build()
                    val item = MediaItem.Builder()
                        .setUri(videoUriString)
                        .setClippingConfiguration(clipping)
                        .build()
                    setMediaItem(item)
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = isPlaying
                    prepare()
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying, exoPlayer, activeTranscriptWords) {
        if (exoPlayer != null) {
            if (isPlaying) exoPlayer.play() else exoPlayer.pause()
        }
        while (isPlaying) {
            delay(200)
            if (exoPlayer != null) {
                val relPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPosMs = startTimeMs + relPos
                if (activeTranscriptWords.isNotEmpty()) {
                    val idx = activeTranscriptWords.indexOfFirst { currentPosMs in it.startMs..it.endMs }
                        .takeIf { it != -1 }
                        ?: activeTranscriptWords.indexOfLast { it.startMs <= currentPosMs }.coerceAtLeast(0)
                    activeWordIndex = idx.coerceIn(0, (karaokeWords.size - 1).coerceAtLeast(0))
                } else {
                    activeWordIndex = (activeWordIndex + 1) % karaokeWords.size.coerceAtLeast(1)
                }
            } else {
                activeWordIndex = (activeWordIndex + 1) % karaokeWords.size.coerceAtLeast(1)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotate")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .aspectRatio(videoRatio.aspectFloat)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(
                1.5.dp,
                Color(platform.brandColorHex).copy(alpha = 0.6f),
                RoundedCornerShape(18.dp)
            )
            .testTag("video_player_preview_box"),
        contentAlignment = Alignment.Center
    ) {
        if (exoPlayer != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = when (reframeMode) {
                            ReframeMode.BLURRED_BG -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                    view.resizeMode = when (reframeMode) {
                        ReframeMode.BLURRED_BG -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                }
            )
        } else {
            when (reframeMode) {
                ReframeMode.BLURRED_BG -> {
                    Image(
                        painter = painterResource(id = R.drawable.thumb_viral_clip),
                        contentDescription = "Background fill",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(18.dp)
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(16f / 9f)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.thumb_tech_podcast),
                            contentDescription = "Main video",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                ReframeMode.SPLIT_SCREEN -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(0.5.dp, Color(0xFF334155))) {
                            Image(
                                painter = painterResource(id = R.drawable.thumb_viral_clip),
                                contentDescription = "Speaker 1",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            SpeakerTag(name = "Speaker 1", modifier = Modifier.align(Alignment.TopStart))
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(0.5.dp, Color(0xFF334155))) {
                            Image(
                                painter = painterResource(id = R.drawable.thumb_tech_podcast),
                                contentDescription = "Speaker 2",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            SpeakerTag(name = "Speaker 2", modifier = Modifier.align(Alignment.TopStart))
                        }
                    }
                }
                ReframeMode.SPEAKER_TRACK -> {
                    Image(
                        painter = painterResource(id = R.drawable.thumb_viral_clip),
                        contentDescription = "Vertical Crop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    SpeakerTag(name = "9:16 Vertical Focus", modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp))
                }
            }
        }

        if (showSafeZoneGuide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 36.dp,
                        bottom = if (platform == SocialPlatform.TIKTOK) 80.dp else 64.dp,
                        start = 12.dp,
                        end = if (platform == SocialPlatform.TIKTOK || platform == SocialPlatform.YOUTUBE_SHORTS) 56.dp else 48.dp
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            )
        }

        val verticalAlignment = when (captionPosition) {
            "TOP" -> Alignment.TopCenter
            "CENTER" -> Alignment.Center
            else -> Alignment.BottomCenter
        }

        Box(
            modifier = Modifier
                .align(verticalAlignment)
                .padding(
                    start = 14.dp,
                    end = if (showPlatformOverlay) 64.dp else 14.dp,
                    top = if (captionPosition == "TOP") 48.dp else 16.dp,
                    bottom = if (captionPosition == "BOTTOM_THIRD") 80.dp else 24.dp
                )
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            KaraokeCaptionsRenderer(
                preset = captionPreset,
                words = karaokeWords,
                activeWordIndex = activeWordIndex,
                fontSize = captionFontSize,
                emojisEnabled = emojisEnabled
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .border(1.dp, Color(platform.brandColorHex).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(id = platform.iconResId),
                contentDescription = platform.displayName,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = platform.shortName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = videoRatio.ratioKey,
                fontSize = 10.sp,
                color = NeonCyan,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (backgroundMusic != "None") {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(11.dp))
                Text(text = backgroundMusic, fontSize = 9.sp, color = Color.White)
            }
        }

        if (showPlatformOverlay) {
            when (platform) {
                SocialPlatform.YOUTUBE_SHORTS -> YouTubeShortsOverlay(modifier = Modifier.fillMaxSize())
                SocialPlatform.TIKTOK -> TikTokOverlay(vinylRotation = vinylRotation, modifier = Modifier.fillMaxSize())
                SocialPlatform.INSTAGRAM -> InstagramReelsOverlay(modifier = Modifier.fillMaxSize())
                SocialPlatform.FACEBOOK -> FacebookReelsOverlay(modifier = Modifier.fillMaxSize())
            }
        }

        val clipDurationSec = ((endTimeMs - startTimeMs) / 1000L).coerceAtLeast(1L)
        val elapsedSec = ((currentPosMs - startTimeMs) / 1000L).coerceIn(0L, clipDurationSec)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    SoundEffectManager.playButtonTap()
                    isPlaying = !isPlaying
                },
                modifier = Modifier.size(28.dp).testTag("play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                Text(
                    text = String.format(Locale.US, "%02d:%02d / %02d:%02d", elapsedSec / 60, elapsedSec % 60, clipDurationSec / 60, clipDurationSec % 60),
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1),
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = videoRatio.resolutionLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricViolet,
                modifier = Modifier
                    .background(ElectricViolet.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

private fun inferEmoji(word: String): String {
    val clean = word.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    return when {
        clean in listOf("money", "million", "revenue", "profit") -> "💰"
        clean in listOf("ai", "code", "agent", "agents", "tech") -> "⚡"
        clean in listOf("stop", "warning", "never", "wrong") -> "⚠️"
        clean in listOf("fast", "speed", "100x", "viral") -> "🚀"
        clean in listOf("secret", "truth", "key") -> "🔑"
        clean in listOf("win", "success", "top", "best") -> "🏆"
        else -> ""
    }
}

@Composable
private fun YouTubeShortsOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionGlyph(icon = Icons.Default.ThumbUp, label = "Like")
            ActionGlyph(icon = Icons.Default.ThumbDown, label = "Dislike")
            ActionGlyph(icon = Icons.AutoMirrored.Filled.Chat, label = "Comments")
            ActionGlyph(icon = Icons.Default.Share, label = "Share")
            ActionGlyph(icon = Icons.Default.Repeat, label = "Remix")
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 34.dp, end = 68.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "@ClipForgeCreator", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Surface(shape = RoundedCornerShape(12.dp), color = Color.Red) {
                    Text(text = "Subscribe", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(text = "Shorts Guide", fontSize = 10.sp, color = Color(0xFFE2E8F0), maxLines = 1)
        }
    }
}

@Composable
private fun TikTokOverlay(vinylRotation: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(34.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1))
                        .align(Alignment.Center)
                ) {
                    Text(text = "CF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFE2C55))
                        .align(Alignment.BottomCenter)
                ) {
                    Text(text = "+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }
            ActionGlyph(icon = Icons.Default.Favorite, label = "Like", tint = Color(0xFFFE2C55))
            ActionGlyph(icon = Icons.AutoMirrored.Filled.Chat, label = "Chat")
            ActionGlyph(icon = Icons.Default.Bookmark, label = "Save")
            ActionGlyph(icon = Icons.Default.Share, label = "Share")
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .rotate(vinylRotation)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 32.dp, end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = "@clipforge.studio", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = "TikTok Safe Zone Guide", fontSize = 10.sp, color = Color.White, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Text(text = "Original Audio - ClipForge", fontSize = 9.sp, color = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun InstagramReelsOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionGlyph(icon = Icons.Default.Favorite, label = "Like")
            ActionGlyph(icon = Icons.AutoMirrored.Filled.Chat, label = "Comment")
            ActionGlyph(icon = Icons.AutoMirrored.Filled.Send, label = "Share")
            ActionGlyph(icon = Icons.Default.MoreHoriz, label = "")
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 32.dp, end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "clipforge.studio", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    modifier = Modifier.border(0.5.dp, Color.White, RoundedCornerShape(4.dp))
                ) {
                    Text(text = "Follow", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            Text(text = "Reels Safe Zone Guide", fontSize = 10.sp, color = Color(0xFFE2E8F0), maxLines = 1)
        }
    }
}

@Composable
private fun FacebookReelsOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionGlyph(icon = Icons.Default.ThumbUp, label = "Like", tint = Color(0xFF1877F2))
            ActionGlyph(icon = Icons.AutoMirrored.Filled.Chat, label = "Comment")
            ActionGlyph(icon = Icons.Default.Share, label = "Share")
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 32.dp, end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "ClipForge Creator", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "• Follow", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1877F2))
            }
            Text(text = "Facebook Reels Safe Zone", fontSize = 10.sp, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun ActionGlyph(icon: ImageVector, label: String, tint: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        if (label.isNotEmpty()) {
            Text(text = label, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpeakerTag(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun KaraokeCaptionsRenderer(
    preset: CaptionPreset,
    words: List<Pair<String, String>>,
    activeWordIndex: Int,
    fontSize: Int,
    emojisEnabled: Boolean
) {
    if (words.isEmpty()) return
    val safeIndex = activeWordIndex.coerceIn(0, words.size - 1)
    val startIdx = (safeIndex / 4) * 4
    val endIdx = minOf(startIdx + 4, words.size)
    val currentChunk = words.subList(startIdx, endIdx)

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
    ) {
        currentChunk.forEachIndexed { relativeIdx, (word, emoji) ->
            val actualIdx = startIdx + relativeIdx
            val isActive = actualIdx == safeIndex
            when (preset) {
                CaptionPreset.HORMOZI -> {
                    val scale = if (isActive) 1.2f else 0.95f
                    val textColor = if (isActive) CaptionYellow else Color.White
                    val bg = if (isActive) Color.Black.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .scale(scale)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .border(if (isActive) 1.5.dp else 0.dp, if (isActive) CaptionYellow else Color.Transparent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = word + if (emojisEnabled && emoji.isNotEmpty() && isActive) " $emoji" else "",
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                CaptionPreset.NEON -> {
                    val scale = if (isActive) 1.15f else 1.0f
                    val color = if (isActive) CaptionNeonCyan else Color(0xFFE2E8F0)
                    Text(
                        text = word + if (emojisEnabled && emoji.isNotEmpty() && isActive) " $emoji" else "",
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Black,
                        color = color,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .scale(scale)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                CaptionPreset.BOLD_YELLOW -> {
                    val color = if (isActive) CaptionYellow else Color.White
                    Text(
                        text = word + if (emojisEnabled && emoji.isNotEmpty()) " $emoji" else "",
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(Color(0xFF1E1E24).copy(alpha = 0.8f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                CaptionPreset.RED_ACCENT -> {
                    val color = if (isActive) Color(0xFFFF2A2A) else Color.White
                    Text(
                        text = word,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                CaptionPreset.COMIC_EMOJI -> {
                    val scale = if (isActive) 1.15f else 1f
                    Text(
                        text = word + if (emoji.isNotEmpty()) " $emoji" else "",
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isActive) CaptionGreen else Color.White,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .scale(scale)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                CaptionPreset.MINIMAL -> {
                    Text(
                        text = word,
                        fontSize = (fontSize - 2).sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
