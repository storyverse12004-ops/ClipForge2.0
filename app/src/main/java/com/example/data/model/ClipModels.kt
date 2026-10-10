package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.R

enum class SourceType {
    UPLOAD
}

enum class ProjectStatus {
    INGESTING,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    DETECTING,
    FINDING_VIRAL_MOMENTS,
    REFRAMING,
    DONE,
    READY,
    FAILED
}

enum class SocialPlatform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val standardRatio: String,
    val maxDurationSeconds: Int,
    val maxResolution: String,
    val brandColorHex: Long,
    val iconResId: Int,
    val safeZoneNote: String
) {
    YOUTUBE_SHORTS(
        id = "youtube_shorts",
        displayName = "YouTube Shorts",
        shortName = "Shorts",
        standardRatio = "9:16",
        maxDurationSeconds = 60,
        maxResolution = "1080p (1080x1920)",
        brandColorHex = 0xFFFF0000,
        iconResId = R.drawable.ic_platform_youtube,
        safeZoneNote = "Right rail (Like/Comment/Remix). Bottom 130px for video title."
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        shortName = "TikTok",
        standardRatio = "9:16",
        maxDurationSeconds = 180,
        maxResolution = "1080p (1080x1920)",
        brandColorHex = 0xFF00F2FE,
        iconResId = R.drawable.ic_platform_tiktok,
        safeZoneNote = "Right rail (Avatar/Heart/Comments/Vinyl disc). Bottom 160px for caption."
    ),
    INSTAGRAM(
        id = "instagram_reels",
        displayName = "Instagram Reels",
        shortName = "Reels",
        standardRatio = "9:16",
        maxDurationSeconds = 90,
        maxResolution = "1080p (1080x1920)",
        brandColorHex = 0xFFE1306C,
        iconResId = R.drawable.ic_platform_instagram,
        safeZoneNote = "Center 4:5 Feed Safe Zone. Right rail engagement buttons."
    ),
    FACEBOOK(
        id = "facebook_reels",
        displayName = "Facebook Reels",
        shortName = "FB Reels",
        standardRatio = "9:16",
        maxDurationSeconds = 90,
        maxResolution = "1080p (1080x1920)",
        brandColorHex = 0xFF1877F2,
        iconResId = R.drawable.ic_platform_facebook,
        safeZoneNote = "Right rail (Reaction bar/Share). Bottom 140px for page tag & sound."
    )
}

enum class ShortsVideoRatio(
    val ratioKey: String,
    val label: String,
    val aspectFloat: Float, // width / height
    val resolutionLabel: String,
    val bestFor: String
) {
    RATIO_9_16(
        ratioKey = "9:16",
        label = "9:16 Vertical",
        aspectFloat = 9f / 16f,
        resolutionLabel = "1080x1920",
        bestFor = "YouTube Shorts, TikTok, IG & FB Reels"
    ),
    RATIO_4_5(
        ratioKey = "4:5",
        label = "4:5 Portrait",
        aspectFloat = 4f / 5f,
        resolutionLabel = "1080x1350",
        bestFor = "Instagram & Facebook Feed Optimized"
    ),
    RATIO_1_1(
        ratioKey = "1:1",
        label = "1:1 Square",
        aspectFloat = 1f,
        resolutionLabel = "1080x1080",
        bestFor = "Facebook & Instagram Carousel/Post"
    ),
    RATIO_16_9(
        ratioKey = "16:9",
        label = "16:9 Landscape",
        aspectFloat = 16f / 9f,
        resolutionLabel = "1920x1080",
        bestFor = "Original Video / YouTube Long-Form"
    )
}

enum class ReframeMode(val label: String, val description: String) {
    SPEAKER_TRACK("Center Crop (9:16)", "9:16 vertical center crop for single speaker focus"),
    SPLIT_SCREEN("Split Screen (2 Speakers)", "Stacked 1:1 boxes ideal for two-speaker interviews"),
    BLURRED_BG("Blurred Background Fill", "Centered footage with scaled & dimmed 9:16 backdrop")
}

enum class CaptionPreset(val label: String, val description: String) {
    HORMOZI("Hormozi Punch", "Bold yellow & green highlight, pop scale, black stroke"),
    MINIMAL("Minimal Clean", "Crisp white sans-serif on frosted backdrop"),
    NEON("Neon Glow", "Vibrant electric cyan & violet with pulse effect"),
    BOLD_YELLOW("Punchy Yellow", "High-contrast energetic subtitle block"),
    RED_ACCENT("Red Urgency", "Highlight words in urgent crimson red"),
    COMIC_EMOJI("Emoji Pop", "Auto-inserted contextual emojis on key words")
}

data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val speaker: String = "Speaker 1",
    val isCut: Boolean = false
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: SourceType = SourceType.UPLOAD,
    val sourceUrl: String,
    val durationSeconds: Int,
    val videoSizeMb: Float,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val thumbnailPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: ProjectStatus = ProjectStatus.READY,
    val clipCount: Int = 0,
    val errorMessage: String = "",
    val transcriptJson: String = ""
)

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val hookText: String,
    val viralityScore: Int, // 0-100
    val reasoning: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val emotionalPeak: String,
    val reframeMode: ReframeMode = ReframeMode.SPEAKER_TRACK,
    val targetPlatformName: String = "TIKTOK",
    val videoRatioKey: String = "9:16",
    val captionPreset: CaptionPreset = CaptionPreset.HORMOZI,
    val captionFontSize: Int = 22,
    val captionPosition: String = "BOTTOM_THIRD", // TOP, CENTER, BOTTOM_THIRD
    val emojisEnabled: Boolean = true,
    val silenceRemoved: Boolean = true,
    val backgroundMusic: String = "None",
    val duckingPercent: Int = 80,
    val thumbnailPath: String = "",
    val wordsJson: String = ""
)

@Entity(tableName = "platform_metadata")
data class PlatformMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clipId: Long,
    val titlesJson: String, // JSON array of 3 titles
    val tiktokDescription: String,
    val instagramDescription: String,
    val youtubeShortsDescription: String,
    val facebookDescription: String = "",
    val broadHashtagsJson: String,
    val nicheHashtagsJson: String,
    val trendingHashtagsJson: String,
    val hookText: String,
    val cta: String
)

@Entity(tableName = "exports")
data class ExportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clipId: Long,
    val targetPlatform: String = "ALL",
    val quality: String, // "480p", "720p", "1080p"
    val format: String, // "MP4", "MOV"
    val fps: Int, // 30, 60
    val bitratePreset: String, // "Compressed", "Balanced", "High"
    val burnedCaptions: Boolean,
    val removeWatermark: Boolean,
    val includeSrt: Boolean,
    val status: String, // "RENDERING", "COMPLETED", "FAILED"
    val progress: Int, // 0-100
    val estimatedSizeMb: Float,
    val downloadUrl: String = "",
    val srtUri: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
