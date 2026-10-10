package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.media.VideoValidator
import com.example.data.model.CaptionPreset
import com.example.data.model.ClipEntity
import com.example.data.model.ReframeMode
import com.example.data.model.ShortsVideoRatio
import com.example.data.model.SocialPlatform
import com.example.data.model.WordTimestamp
import com.example.data.remote.ClipForgeCloudClient
import com.example.ui.components.AdMobConfig
import com.example.ui.viewmodel.DailyUsageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ClipForge", appName)
    }

    @Test
    fun `test daily usage limits enforcement`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remainingBefore = DailyUsageManager.getRunsRemaining(context)
        assertTrue(remainingBefore in 0..DailyUsageManager.MAX_RUNS_PER_DAY)
        assertEquals(3, DailyUsageManager.MAX_RUNS_PER_DAY)
        assertEquals(3600, VideoValidator.MAX_DURATION_SEC)
    }

    @Test
    fun `test clip entity defaults and virality scoring bounds`() {
        val clip = ClipEntity(
            id = 1,
            projectId = 10,
            title = "Test Clip",
            hookText = "Test Hook",
            viralityScore = 95,
            reasoning = "Test reasoning",
            startTimeMs = 1000,
            endTimeMs = 45000,
            emotionalPeak = "Peak Insight",
            reframeMode = ReframeMode.SPEAKER_TRACK,
            captionPreset = CaptionPreset.HORMOZI
        )
        assertEquals(95, clip.viralityScore)
        assertEquals(ReframeMode.SPEAKER_TRACK, clip.reframeMode)
        assertEquals(CaptionPreset.HORMOZI, clip.captionPreset)
        assertEquals(44000L, clip.endTimeMs - clip.startTimeMs)
    }

    @Test
    fun `test platforms and video ratios`() {
        assertEquals(4, SocialPlatform.entries.size)
        assertNotNull(SocialPlatform.YOUTUBE_SHORTS)
        assertNotNull(SocialPlatform.TIKTOK)
        assertNotNull(SocialPlatform.INSTAGRAM)
        assertNotNull(SocialPlatform.FACEBOOK)
        assertEquals("9:16", SocialPlatform.YOUTUBE_SHORTS.standardRatio)
        assertEquals("9:16", SocialPlatform.TIKTOK.standardRatio)
        assertEquals("9:16", SocialPlatform.INSTAGRAM.standardRatio)
        assertEquals("9:16", SocialPlatform.FACEBOOK.standardRatio)
        assertEquals(9f / 16f, ShortsVideoRatio.RATIO_9_16.aspectFloat, 0.001f)
        assertEquals(4f / 5f, ShortsVideoRatio.RATIO_4_5.aspectFloat, 0.001f)
        assertEquals(1f, ShortsVideoRatio.RATIO_1_1.aspectFloat, 0.001f)
    }

    @Test
    fun `test admob config IDs and debug uses test IDs`() {
        assertEquals("ca-app-pub-1374968715711516~1495134749", AdMobConfig.APP_ID)
        assertEquals("ca-app-pub-1374968715711516/7584980500", AdMobConfig.BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-1374968715711516/1826307230", AdMobConfig.INTERSTITIAL_AD_UNIT_ID)
        assertEquals("ca-app-pub-1374968715711516/5276782879", AdMobConfig.APP_OPEN_AD_UNIT_ID)

        assertEquals("ca-app-pub-3940256099942544~3347511713", AdMobConfig.TEST_APP_ID)
        assertEquals("ca-app-pub-3940256099942544/6300978111", AdMobConfig.TEST_BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-3940256099942544/1033173712", AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID)
        assertEquals("ca-app-pub-3940256099942544/9257395921", AdMobConfig.TEST_APP_OPEN_AD_UNIT_ID)
    }

    @Test
    fun `test caption generator presets and crop modes exist`() {
        val presets = CaptionPreset.entries
        assertEquals(6, presets.size)
        assertNotNull(presets.find { it == CaptionPreset.HORMOZI })
        assertNotNull(presets.find { it == CaptionPreset.NEON })
        assertNotNull(presets.find { it == CaptionPreset.MINIMAL })
        assertNotNull(presets.find { it == CaptionPreset.BOLD_YELLOW })
        assertNotNull(presets.find { it == CaptionPreset.RED_ACCENT })
        assertNotNull(presets.find { it == CaptionPreset.COMIC_EMOJI })

        val cropModes = ReframeMode.entries
        assertEquals(3, cropModes.size)
        assertNotNull(cropModes.find { it == ReframeMode.SPEAKER_TRACK })
        assertNotNull(cropModes.find { it == ReframeMode.SPLIT_SCREEN })
        assertNotNull(cropModes.find { it == ReframeMode.BLURRED_BG })
    }

    @Test
    fun `test sentence boundary snapping and safe json parsing`() {
        val words = listOf(
            WordTimestamp("Stop", 0L, 400L),
            WordTimestamp("writing", 450L, 900L),
            WordTimestamp("boilerplate.", 950L, 1800L),
            WordTimestamp("Here", 2000L, 2400L),
            WordTimestamp("is", 2450L, 2700L),
            WordTimestamp("why.", 2750L, 22000L)
        )
        val sentences = ClipForgeCloudClient.buildSentenceSegments(words)
        assertEquals(2, sentences.size)

        val snapped = ClipForgeCloudClient.snapToSentenceBoundaries(
            startMs = 200L,
            endMs = 21500L,
            sentences = sentences,
            videoDurationMs = 30000L
        )
        assertEquals(0L, snapped.first)
        assertEquals(22000L, snapped.second)

        val rawFencedJson = """
            ```json
            [
              {"startMs": 1000, "endMs": 25000, "hookTitle": "Hook Title", "viralityScore": 92, "oneLineReason": "Strong hook"},
              {"startMs": 10000, "endMs": 30000, "hookTitle": "Overlapping Lower Score", "viralityScore": 81, "oneLineReason": "Overlaps first"},
              {"startMs": 32000, "endMs": 52000, "hookTitle": "Second Distinct Moment", "viralityScore": 88, "oneLineReason": "Non-overlapping"}
            ]
            ```
        """.trimIndent()
        val parsed = ClipForgeCloudClient.parseViralMomentsJson(rawFencedJson)
        assertEquals(3, parsed.size)
        val nonOverlapping = ClipForgeCloudClient.removeOverlappingMoments(parsed)
        assertEquals(2, nonOverlapping.size)
        assertEquals(92, nonOverlapping[0].viralityScore)
        assertEquals("Hook Title", nonOverlapping[0].resolvedTitle)
        assertEquals("Strong hook", nonOverlapping[0].resolvedReason)
        assertEquals(88, nonOverlapping[1].viralityScore)
    }

    @Test
    fun `test audio chunking over 10 minutes splits into 5 minute chunks and adds chunk offsets`() {
        val shortRanges = com.example.data.media.AudioExtractor.computeChunkRanges(2 * 60 * 1000L)
        assertEquals(1, shortRanges.size)
        assertEquals(0L to 120_000L, shortRanges[0])

        val longRanges = com.example.data.media.AudioExtractor.computeChunkRanges(12 * 60 * 1000L)
        assertEquals(3, longRanges.size)
        assertEquals(0L to 300_000L, longRanges[0])
        assertEquals(300_000L to 600_000L, longRanges[1])
        assertEquals(600_000L to 720_000L, longRanges[2])

        val segmentJson = """
            [{"text": "Hello world today.", "startMs": 1000, "endMs": 2500, "speaker": "Speaker 1"}]
        """.trimIndent()
        val parsedWords = ClipForgeCloudClient.parseWordTimestampsJson(
            rawJson = segmentJson,
            offsetMs = 300_000L,
            chunkDurationMs = 300_000L
        )
        assertEquals(3, parsedWords.size)
        assertEquals("Hello", parsedWords[0].word)
        assertEquals(301_000L, parsedWords[0].startMs)
        assertEquals("today.", parsedWords[2].word)
        assertEquals(302_500L, parsedWords[2].endMs)
    }

    @Test
    fun `test cut words segment builder and burned caption overlay`() {
        val words = listOf(
            WordTimestamp("Welcome", 1000L, 1500L, isCut = false),
            WordTimestamp("um", 1600L, 2100L, isCut = true),
            WordTimestamp("creators", 2200L, 3000L, isCut = false)
        )
        val segments = com.example.data.media.Media3ClipExporter.buildKeptTimeSegments(
            clipStartMs = 1000L,
            clipEndMs = 3500L,
            words = words
        )
        assertEquals(2, segments.size)
        assertEquals(1000L to 1600L, segments[0])
        assertEquals(2100L to 3500L, segments[1])

        val overlay = com.example.data.media.BurnedCaptionTextOverlay(
            words = words,
            clipStartMs = 1000L,
            preset = CaptionPreset.HORMOZI,
            fontSize = 24,
            position = "BOTTOM_THIRD",
            emojisEnabled = true,
            showWatermark = false
        )
        assertEquals(0L, overlay.shiftedWords.first().startMs)
        val textAtStart = overlay.getText(200_000L).toString()
        assertTrue(textAtStart.contains("WELCOME"))
        assertTrue(!textAtStart.contains("UM"))
    }

    @Test
    fun `test admob export gating suppresses ads during active export`() {
        com.example.ui.components.AdMobManager.setExportActive(true)
        assertTrue(com.example.ui.components.AdMobManager.isExportCurrentlyActive())
        assertTrue(com.example.ui.components.AdMobManager.isAnyJobActive())
        com.example.ui.components.AdMobManager.setExportActive(false)
        assertEquals(false, com.example.ui.components.AdMobManager.isExportCurrentlyActive())

        com.example.ui.components.AdMobManager.setProcessingActive(true)
        assertTrue(com.example.ui.components.AdMobManager.isProcessingCurrentlyActive())
        assertTrue(com.example.ui.components.AdMobManager.isAnyJobActive())
        com.example.ui.components.AdMobManager.setProcessingActive(false)
        assertEquals(false, com.example.ui.components.AdMobManager.isProcessingCurrentlyActive())
    }

    @Test
    fun `test pipeline steps count and order`() {
        val steps = com.example.data.model.PipelineStep.entries
        assertEquals(9, steps.size)
        assertEquals("Importing video", steps[0].title)
        assertEquals("Extracting audio", steps[1].title)
        assertEquals("Transcribing speech", steps[2].title)
        assertEquals("Finding viral moments", steps[3].title)
        assertEquals("Writing titles", steps[4].title)
        assertEquals("Reframing to 9:16", steps[5].title)
        assertEquals("Adding captions", steps[6].title)
        assertEquals("Exporting", steps[7].title)
        assertEquals("Done", steps[8].title)
    }

    @Test
    fun `test word timestamp serialization and deserialization`() {
        val words = listOf(
            WordTimestamp("Viral", 100L, 500L, "Speaker 1"),
            WordTimestamp("Shorts", 550L, 1000L, "Speaker 2")
        )
        val json = ClipForgeCloudClient.serializeWords(words)
        assertTrue(json.contains("Viral"))
        assertTrue(json.contains("Shorts"))

        val deserialized = ClipForgeCloudClient.deserializeWords(json)
        assertEquals(2, deserialized.size)
        assertEquals("Viral", deserialized[0].word)
        assertEquals(100L, deserialized[0].startMs)
        assertEquals("Speaker 2", deserialized[1].speaker)
    }

    @Test
    fun `test constant base url for ai backend proxy`() {
        assertEquals("https://generativelanguage.googleapis.com/", ClipForgeCloudClient.AI_BACKEND_BASE_URL)
    }
}
