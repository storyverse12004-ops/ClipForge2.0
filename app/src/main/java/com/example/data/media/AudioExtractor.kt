package com.example.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AudioChunk(
    val file: File,
    val startOffsetMs: Long,
    val durationMs: Long,
    val mimeType: String = "audio/mp4"
)

object AudioExtractor {
    internal const val SPLIT_THRESHOLD_MS = 10 * 60 * 1000L // 10 minutes
    internal const val CHUNK_DURATION_MS = 5 * 60 * 1000L   // 5-minute chunks when video > 10 minutes
    internal const val TARGET_SAMPLE_RATE_HZ = 16_000       // 16 kHz speech-optimized sample rate

    internal fun computeChunkRanges(totalDurationMs: Long): List<Pair<Long, Long>> {
        val safeDuration = if (totalDurationMs > 0L) totalDurationMs else 60_000L
        if (safeDuration <= SPLIT_THRESHOLD_MS) {
            return listOf(0L to safeDuration)
        }
        val ranges = mutableListOf<Pair<Long, Long>>()
        var startMs = 0L
        while (startMs < safeDuration) {
            val endMs = minOf(startMs + CHUNK_DURATION_MS, safeDuration)
            ranges.add(startMs to endMs)
            startMs = endMs
        }
        return ranges
    }

    suspend fun extractAudioChunks(
        context: Context,
        videoUri: Uri,
        totalDurationMs: Long,
        onProgress: (Float) -> Unit
    ): List<AudioChunk> = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "audio_chunks_${System.currentTimeMillis()}").apply { mkdirs() }
        val ranges = computeChunkRanges(totalDurationMs)
        val totalChunks = ranges.size.coerceAtLeast(1)
        val chunks = mutableListOf<AudioChunk>()

        for ((chunkIndex, range) in ranges.withIndex()) {
            val (currentStartMs, currentEndMs) = range
            val chunkFile = File(outputDir, "chunk_${chunkIndex}.m4a")
            var extracted = try {
                transcodeAudioWithTransformer(
                    context = context,
                    videoUri = videoUri,
                    outputFile = chunkFile,
                    startMs = currentStartMs,
                    endMs = currentEndMs
                ) { segProgress ->
                    val overall = (chunkIndex + segProgress) / totalChunks.toFloat()
                    onProgress(overall.coerceIn(0f, 1f))
                }
                chunkFile.exists() && chunkFile.length() >= 256L
            } catch (_: Exception) {
                false
            }

            if (!extracted) {
                extracted = tryDemuxAudioSegment(
                    context = context,
                    videoUri = videoUri,
                    outputFile = chunkFile,
                    startUs = currentStartMs * 1000L,
                    endUs = currentEndMs * 1000L
                ) { segProgress ->
                    val overall = (chunkIndex + segProgress) / totalChunks.toFloat()
                    onProgress(overall.coerceIn(0f, 1f))
                }
            }

            if (extracted && chunkFile.exists() && chunkFile.length() > 0L) {
                chunks.add(
                    AudioChunk(
                        file = chunkFile,
                        startOffsetMs = currentStartMs,
                        durationMs = currentEndMs - currentStartMs,
                        mimeType = "audio/mp4"
                    )
                )
            }
        }
        onProgress(1.0f)
        chunks
    }

    fun cleanupAudioChunks(chunks: List<AudioChunk>) {
        chunks.forEach { chunk ->
            try {
                val parent = chunk.file.parentFile
                chunk.file.delete()
                if (parent != null && parent.exists() && parent.listFiles().isNullOrEmpty()) {
                    parent.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun tryDemuxAudioSegment(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            val totalSegmentUs = (endUs - startUs).coerceAtLeast(1L)

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    val progress = ((sampleTimeUs - startUs).toFloat() / totalSegmentUs.toFloat()).coerceIn(0f, 1f)
                    onProgress(progress)
                }
                extractor.advance()
            }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            try {
                muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun transcodeAudioWithTransformer(
        context: Context,
        videoUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: (Float) -> Unit
    ) = suspendCancellableCoroutine<Unit> { cont: CancellableContinuation<Unit> ->
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                if (outputFile.exists()) outputFile.delete()

                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(clippingConfig)
                    .build()

                val channelMixingProcessor = ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(ChannelMixingMatrix.create(2, 1))
                    putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1))
                }

                val resampleProcessor = SonicAudioProcessor().apply {
                    setOutputSampleRateHz(TARGET_SAMPLE_RATE_HZ)
                }

                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .setEffects(
                        Effects(
                            listOf(channelMixingProcessor, resampleProcessor),
                            emptyList()
                        )
                    )
                    .build()

                val progressHolder = ProgressHolder()
                var isFinished = false
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            isFinished = true
                            onProgress(1f)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            isFinished = true
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)

                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (isFinished) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(progressHolder.progress / 100f)
                        }
                        if (!isFinished) {
                            mainHandler.postDelayed(this, 250L)
                        }
                    }
                }
                mainHandler.postDelayed(pollRunnable, 250L)

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
}
