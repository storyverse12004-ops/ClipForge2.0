package com.example.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.data.model.CaptionPreset
import com.example.data.model.ClipEntity
import com.example.data.model.ReframeMode
import com.example.data.model.WordTimestamp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class ClipRenderOutput(
    val galleryUriString: String,
    val localFile: File,
    val srtUriString: String = "",
    val fileSizeMb: Float
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipForgeAudioProcessor(
    private val silenceRemoved: Boolean,
    private val backgroundMusic: String,
    private val duckingPercent: Int
) : AudioProcessor {
    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var sampleCounter: Long = 0L

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        this.inputAudioFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return inputAudioFormat != AudioFormat.NOT_SET &&
            (silenceRemoved || (backgroundMusic.isNotBlank() && backgroundMusic != "None"))
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val sampleCount = remaining / 2
        val shortSamples = ShortArray(sampleCount)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortSamples)
        inputBuffer.position(inputBuffer.position() + remaining)

        var sumSquares = 0.0
        for (s in shortSamples) {
            val norm = s / 32768.0
            sumSquares += norm * norm
        }
        val rms = kotlin.math.sqrt(sumSquares / sampleCount.coerceAtLeast(1))
        val isSilentFrame = rms < 0.015

        val effectiveSamples = if (silenceRemoved && isSilentFrame && sampleCount > 64) {
            val reducedCount = (sampleCount * 0.30).toInt().coerceAtLeast(2)
            val alignedCount = reducedCount - (reducedCount % inputAudioFormat.channelCount.coerceAtLeast(1))
            shortSamples.copyOfRange(0, alignedCount.coerceAtLeast(inputAudioFormat.channelCount))
        } else {
            shortSamples
        }

        val outBytes = effectiveSamples.size * 2
        if (outputBuffer.capacity() < outBytes) {
            outputBuffer = ByteBuffer.allocateDirect(outBytes).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            outputBuffer.clear()
        }

        val hasMusic = backgroundMusic.isNotBlank() && backgroundMusic != "None"
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(16000)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val baseMusicGain = 0.14
        val duckMultiplier = if (!isSilentFrame) {
            (1.0 - (duckingPercent.coerceIn(50, 95) / 100.0)).coerceIn(0.08, 0.50)
        } else {
            0.75
        }
        val musicGain = baseMusicGain * duckMultiplier

        for (i in effectiveSamples.indices) {
            var sampleVal = effectiveSamples[i].toInt()
            if (hasMusic) {
                val frameIdx = sampleCounter / channels
                val t = frameIdx.toDouble() / sampleRate.toDouble()
                val synth = synthesizeAmbientChord(backgroundMusic, t)
                val musicPcm = (synth * 32767.0 * musicGain).toInt()
                sampleVal = (sampleVal + musicPcm).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            }
            outputBuffer.putShort(sampleVal.toShort())
            sampleCounter++
        }
        outputBuffer.flip()
    }

    private fun synthesizeAmbientChord(style: String, t: Double): Double {
        return when (style) {
            "Lo-Fi Beats" -> {
                val pulse = 0.6 + 0.4 * sin(2.0 * PI * 1.5 * t)
                (sin(2.0 * PI * 220.0 * t) * 0.5 + sin(2.0 * PI * 277.18 * t) * 0.3 + sin(2.0 * PI * 329.63 * t) * 0.2) * pulse
            }
            "Upbeat Viral" -> {
                val beat = if (((t * 2.2).toInt() % 2) == 0) 1.0 else 0.55
                (sin(2.0 * PI * 261.63 * t) * 0.45 + sin(2.0 * PI * 329.63 * t) * 0.35 + sin(2.0 * PI * 392.00 * t) * 0.2) * beat
            }
            "Dramatic Cinematic" -> {
                val swell = 0.5 + 0.5 * abs(sin(2.0 * PI * 0.25 * t))
                (sin(2.0 * PI * 110.0 * t) * 0.6 + sin(2.0 * PI * 164.81 * t) * 0.4) * swell
            }
            else -> {
                (sin(2.0 * PI * 196.0 * t) * 0.5 + sin(2.0 * PI * 293.66 * t) * 0.3 + sin(2.0 * PI * 392.0 * t) * 0.2)
            }
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        sampleCounter = 0L
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BurnedCaptionTextOverlay(
    words: List<WordTimestamp>,
    private val clipStartMs: Long,
    private val preset: CaptionPreset,
    private val fontSize: Int,
    private val position: String,
    private val emojisEnabled: Boolean,
    private val showWatermark: Boolean
) : TextOverlay() {
    internal val shiftedWords: List<WordTimestamp> = Media3ClipExporter.shiftWordsToClipZero(
        words = words.filter { !it.isCut },
        clipStartMs = clipStartMs
    )

    private val overlaySettings: OverlaySettings by lazy {
        val yAnchor = when (position) {
            "TOP" -> 0.68f
            "CENTER" -> 0.0f
            else -> -0.56f
        }
        val scaleFactor = (fontSize.coerceIn(16, 36) / 22f).coerceIn(0.75f, 1.5f)
        OverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, yAnchor)
            .setBackgroundFrameAnchor(0f, 0f)
            .setScale(scaleFactor, scaleFactor)
            .build()
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return overlaySettings
    }

    override fun getText(presentationTimeUs: Long): SpannableString {
        val relativeTimeMs = (presentationTimeUs / 1000L).coerceAtLeast(0L)
        if (shiftedWords.isEmpty()) {
            return if (showWatermark) SpannableString("ClipForge") else SpannableString("")
        }

        val currentIdx = shiftedWords.indexOfFirst { relativeTimeMs in it.startMs..it.endMs }
            .takeIf { it != -1 }
            ?: shiftedWords.indexOfLast { it.startMs <= relativeTimeMs }.coerceAtLeast(0)

        val chunkStart = (currentIdx / 4) * 4
        val chunkEnd = minOf(chunkStart + 4, shiftedWords.size)
        if (chunkStart >= chunkEnd) return SpannableString("")

        val chunk = shiftedWords.subList(chunkStart, chunkEnd)
        val tokens = chunk.mapIndexed { relIdx, w ->
            val raw = if (preset == CaptionPreset.HORMOZI || preset == CaptionPreset.BOLD_YELLOW) {
                w.word.uppercase(Locale.getDefault())
            } else {
                w.word
            }
            val emoji = if (emojisEnabled && (chunkStart + relIdx == currentIdx)) {
                inferContextualEmoji(w.word)
            } else ""
            if (emoji.isNotEmpty()) "$raw $emoji" else raw
        }

        val joined = tokens.joinToString(" ")
        val spannable = SpannableString(joined)

        val (activeFg, inactiveFg, bgColor) = when (preset) {
            CaptionPreset.HORMOZI -> Triple(Color.rgb(255, 230, 0), Color.WHITE, Color.argb(210, 0, 0, 0))
            CaptionPreset.NEON -> Triple(Color.rgb(0, 240, 255), Color.rgb(226, 232, 240), Color.argb(180, 15, 23, 42))
            CaptionPreset.BOLD_YELLOW -> Triple(Color.rgb(255, 230, 0), Color.WHITE, Color.argb(220, 20, 20, 28))
            CaptionPreset.RED_ACCENT -> Triple(Color.rgb(255, 42, 42), Color.WHITE, Color.argb(200, 0, 0, 0))
            CaptionPreset.COMIC_EMOJI -> Triple(Color.rgb(34, 197, 94), Color.WHITE, Color.argb(200, 0, 0, 0))
            CaptionPreset.MINIMAL -> Triple(Color.WHITE, Color.rgb(180, 190, 205), Color.argb(140, 0, 0, 0))
        }

        spannable.setSpan(
            BackgroundColorSpan(bgColor),
            0,
            joined.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            joined.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        var cursor = 0
        tokens.forEachIndexed { relIdx, token ->
            val start = cursor
            val end = (cursor + token.length).coerceAtMost(joined.length)
            val isActiveWord = (chunkStart + relIdx) == currentIdx
            spannable.setSpan(
                ForegroundColorSpan(if (isActiveWord) activeFg else inactiveFg),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isActiveWord && preset == CaptionPreset.HORMOZI) {
                spannable.setSpan(
                    RelativeSizeSpan(1.12f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            cursor = end + 1
        }

        return spannable
    }

    private fun inferContextualEmoji(word: String): String {
        val clean = word.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        return when {
            clean in listOf("money", "million", "revenue", "profit", "wealth") -> "💰"
            clean in listOf("ai", "code", "robot", "agent", "agents", "tech") -> "⚡"
            clean in listOf("stop", "warning", "never", "wrong", "trap") -> "⚠️"
            clean in listOf("fast", "speed", "100x", "power", "viral") -> "🚀"
            clean in listOf("secret", "truth", "key", "unlock") -> "🔑"
            clean in listOf("win", "success", "top", "best", "first") -> "🏆"
            else -> ""
        }
    }
}

object Media3ClipExporter {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun exportClip(
        context: Context,
        sourceVideoUri: Uri,
        clip: ClipEntity,
        words: List<WordTimestamp>,
        quality: String,
        format: String,
        fps: Int,
        burnedCaptions: Boolean,
        removeWatermark: Boolean,
        includeSrt: Boolean,
        onProgress: (Int) -> Unit
    ): ClipRenderOutput = withContext(Dispatchers.IO) {
        try {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val availBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (availBytes < 40L * 1024L * 1024L) {
                throw IllegalStateException("Low storage space (${availBytes / (1024L * 1024L)} MB free). Please clear cache or free up storage.")
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {}

        try {
            if (sourceVideoUri.scheme == "file") {
                val f = sourceVideoUri.path?.let { File(it) }
                if (f == null || !f.exists() || !f.canRead()) {
                    throw IllegalStateException("Source video file was deleted or moved from storage.")
                }
            } else {
                context.contentResolver.openInputStream(sourceVideoUri)?.use {}
                    ?: throw IllegalStateException("Source video is no longer readable.")
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Video permission was lost.", e)
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val ext = "mp4"
        val fileName = "ClipForge_${clip.id}_${quality}_${System.currentTimeMillis()}.$ext"
        val outputFile = File(exportsDir, fileName)

        val (targetWidth, targetHeight) = when (clip.videoRatioKey) {
            "4:5" -> when (quality) {
                "480p" -> 480 to 600
                "720p" -> 720 to 900
                else -> 1080 to 1350
            }
            "1:1" -> when (quality) {
                "480p" -> 480 to 480
                "720p" -> 720 to 720
                else -> 1080 to 1080
            }
            "16:9" -> when (quality) {
                "480p" -> 854 to 480
                "720p" -> 1280 to 720
                else -> 1920 to 1080
            }
            else -> when (quality) {
                "480p" -> 480 to 854
                "720p" -> 720 to 1280
                else -> 1080 to 1920
            }
        }

        val videoEffects = mutableListOf<Effect>()
        when (clip.reframeMode) {
            ReframeMode.BLURRED_BG -> {
                videoEffects.add(
                    ScaleAndRotateTransformation.Builder()
                        .setScale(0.94f, 0.94f)
                        .build()
                )
                videoEffects.add(
                    Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            }
            ReframeMode.SPLIT_SCREEN, ReframeMode.SPEAKER_TRACK -> {
                videoEffects.add(
                    Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
            }
        }

        if (burnedCaptions || !removeWatermark) {
            val overlays = mutableListOf<TextureOverlay>()
            overlays.add(
                BurnedCaptionTextOverlay(
                    words = if (burnedCaptions) words else emptyList(),
                    clipStartMs = clip.startTimeMs,
                    preset = clip.captionPreset,
                    fontSize = clip.captionFontSize,
                    position = clip.captionPosition,
                    emojisEnabled = clip.emojisEnabled,
                    showWatermark = !removeWatermark
                )
            )
            videoEffects.add(OverlayEffect(overlays))
        }

        val audioProcessors = mutableListOf<AudioProcessor>()
        if (clip.silenceRemoved || (clip.backgroundMusic.isNotBlank() && clip.backgroundMusic != "None")) {
            audioProcessors.add(
                ClipForgeAudioProcessor(
                    silenceRemoved = clip.silenceRemoved,
                    backgroundMusic = clip.backgroundMusic,
                    duckingPercent = clip.duckingPercent
                )
            )
        }

        val effects = Effects(audioProcessors, videoEffects)
        val segments = buildKeptTimeSegments(
            clipStartMs = clip.startTimeMs.coerceAtLeast(0L),
            clipEndMs = clip.endTimeMs.coerceAtLeast(clip.startTimeMs + 1000L),
            words = words,
            removeSilenceGaps = clip.silenceRemoved
        )

        val cappedFps = fps.coerceIn(24, 30)
        val editedItems = segments.map { (segStartMs, segEndMs) ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(segStartMs)
                .setEndPositionMs(segEndMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(sourceVideoUri)
                .setClippingConfiguration(clipping)
                .build()
            EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .setFrameRate(cappedFps)
                .build()
        }

        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.Builder(editedItems).build())
        ).build()

        runTransformerSuspend(
            context = context,
            composition = composition,
            outputFile = outputFile,
            onProgress = onProgress
        )

        var srtUriString = ""
        if (includeSrt) {
            val srtFile = File(exportsDir, fileName.substringBeforeLast(".") + ".srt")
            writeSrtFile(srtFile, words, clip.startTimeMs)
            srtUriString = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                srtFile
            ).toString()
        }

        val sizeMb = (outputFile.length().toFloat() / (1024f * 1024f)).coerceAtLeast(0.1f)
        val galleryUri = saveVideoToGallery(context, outputFile, fileName)
        val finalUriString = if (galleryUri != null) {
            try {
                outputFile.delete()
            } catch (_: Exception) {}
            galleryUri.toString()
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            ).toString()
        }

        ClipRenderOutput(
            galleryUriString = finalUriString,
            localFile = outputFile,
            srtUriString = srtUriString,
            fileSizeMb = sizeMb
        )
    }

    private const val SILENCE_GAP_THRESHOLD_MS = 700L

    internal fun shiftWordsToClipZero(
        words: List<WordTimestamp>,
        clipStartMs: Long
    ): List<WordTimestamp> {
        return words.map { w ->
            val relStart = (w.startMs - clipStartMs).coerceAtLeast(0L)
            val relEnd = (w.endMs - clipStartMs).coerceAtLeast(relStart + 100L)
            w.copy(startMs = relStart, endMs = relEnd)
        }
    }

    internal fun buildKeptTimeSegments(
        clipStartMs: Long,
        clipEndMs: Long,
        words: List<WordTimestamp>,
        removeSilenceGaps: Boolean = false
    ): List<Pair<Long, Long>> {
        val omittedRanges = mutableListOf<Pair<Long, Long>>()
        val cutWords = words.filter { it.isCut && it.endMs > clipStartMs && it.startMs < clipEndMs }
            .sortedBy { it.startMs }
        for (cut in cutWords) {
            omittedRanges.add(
                cut.startMs.coerceAtLeast(clipStartMs) to cut.endMs.coerceAtMost(clipEndMs)
            )
        }
        if (removeSilenceGaps) {
            val activeInClip = words
                .filter { !it.isCut && it.endMs > clipStartMs && it.startMs < clipEndMs }
                .sortedBy { it.startMs }

            if (activeInClip.isNotEmpty()) {
                val firstWordStart = activeInClip.first().startMs.coerceAtLeast(clipStartMs)
                if (firstWordStart - clipStartMs > SILENCE_GAP_THRESHOLD_MS) {
                    omittedRanges.add(clipStartMs to (firstWordStart - 80L).coerceAtLeast(clipStartMs))
                }

                for (i in 0 until activeInClip.size - 1) {
                    val currentEnd = activeInClip[i].endMs.coerceIn(clipStartMs, clipEndMs)
                    val nextStart = activeInClip[i + 1].startMs.coerceIn(clipStartMs, clipEndMs)
                    if (nextStart - currentEnd > SILENCE_GAP_THRESHOLD_MS) {
                        val trimStart = (currentEnd + 60L).coerceAtMost(nextStart)
                        val trimEnd = (nextStart - 60L).coerceAtLeast(trimStart)
                        if (trimEnd > trimStart) {
                            omittedRanges.add(trimStart to trimEnd)
                        }
                    }
                }

                val lastWordEnd = activeInClip.last().endMs.coerceAtMost(clipEndMs)
                if (clipEndMs - lastWordEnd > SILENCE_GAP_THRESHOLD_MS) {
                    omittedRanges.add((lastWordEnd + 80L).coerceAtMost(clipEndMs) to clipEndMs)
                }
            }
        }

        if (omittedRanges.isEmpty()) {
            return listOf(clipStartMs to clipEndMs)
        }

        val sortedOmitted = omittedRanges.sortedBy { it.first }
        val segments = mutableListOf<Pair<Long, Long>>()
        var cursor = clipStartMs

        for ((omitStartRaw, omitEndRaw) in sortedOmitted) {
            val omitStart = omitStartRaw.coerceAtLeast(clipStartMs)
            val omitEnd = omitEndRaw.coerceAtMost(clipEndMs)
            if (omitStart - cursor >= 250L) {
                segments.add(cursor to omitStart)
            }
            cursor = maxOf(cursor, omitEnd)
        }

        if (clipEndMs - cursor >= 250L) {
            segments.add(cursor to clipEndMs)
        }

        return if (segments.isEmpty()) listOf(clipStartMs to clipEndMs) else segments
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun runTransformerSuspend(
        context: Context,
        composition: Composition,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) = suspendCancellableCoroutine<Unit> { cont: CancellableContinuation<Unit> ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                if (outputFile.exists()) outputFile.delete()
                val progressHolder = ProgressHolder()
                var isFinished = false
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            isFinished = true
                            onProgress(100)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            isFinished = true
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IllegalStateException(
                                        "Video encoding failed: ${exportException.localizedMessage ?: "codec error"}",
                                        exportException
                                    )
                                )
                            }
                        }
                    })
                    .build()

                transformer.start(composition, outputFile.absolutePath)

                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (isFinished) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress.coerceIn(1, 99))
                        }
                        if (!isFinished) {
                            mainHandler.postDelayed(this, 200L)
                        }
                    }
                }
                mainHandler.postDelayed(pollRunnable, 200L)

                cont.invokeOnCancellation {
                    isFinished = true
                    mainHandler.post {
                        try {
                            transformer.cancel()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    private fun saveVideoToGallery(context: Context, videoFile: File, displayName: String): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipForge")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, completeValues, null, null)
            }
            itemUri
        } catch (_: Exception) {
            null
        }
    }

    private fun writeSrtFile(srtFile: File, words: List<WordTimestamp>, clipStartMs: Long) {
        val active = words.filter { !it.isCut }
        val sb = StringBuilder()
        var index = 1
        var i = 0
        while (i < active.size) {
            val endIdx = minOf(i + 4, active.size)
            val chunk = active.subList(i, endIdx)
            val startRel = (chunk.first().startMs - clipStartMs).coerceAtLeast(0L)
            val endRel = (chunk.last().endMs - clipStartMs).coerceAtLeast(startRel + 400L)
            sb.append(index).append("\n")
            sb.append(formatSrtTimestamp(startRel)).append(" --> ").append(formatSrtTimestamp(endRel)).append("\n")
            sb.append(chunk.joinToString(" ") { it.word }).append("\n\n")
            index++
            i = endIdx
        }
        FileOutputStream(srtFile).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
    }

    private fun formatSrtTimestamp(ms: Long): String {
        val hours = ms / 3_600_000L
        val mins = (ms % 3_600_000L) / 60_000L
        val secs = (ms % 60_000L) / 1000L
        val millis = ms % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }
}
