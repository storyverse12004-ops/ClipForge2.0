package com.example.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class VideoValidationResult(
    val isValid: Boolean,
    val persistedUriString: String = "",
    val fileName: String = "",
    val durationSeconds: Int = 0,
    val fileSizeMb: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String = "",
    val hasAudioTrack: Boolean = false,
    val errorMessage: String? = null
)

object VideoValidator {
    private const val MIN_DURATION_SEC = 5
    const val MAX_DURATION_SEC = 60 * 60 // 60 minutes max limit for processing
    private const val MAX_FILE_SIZE_MB = 2048f // 2 GB max

    suspend fun validateAndPersistVideo(
        context: Context,
        uri: Uri
    ): VideoValidationResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "video_${System.currentTimeMillis()}.mp4"
        val sizeBytes = queryFileSize(resolver, uri)
        val sizeMb = if (sizeBytes > 0) sizeBytes / (1024f * 1024f) else 0f

        if (sizeMb > MAX_FILE_SIZE_MB) {
            return@withContext VideoValidationResult(
                isValid = false,
                fileName = displayName,
                fileSizeMb = sizeMb,
                errorMessage = "Video is too large (${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB). Maximum supported size is 2,048 MB (2 GB)."
            )
        }

        var workingUri = uri
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            var persisted = false
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                persisted = true
            } catch (_: SecurityException) {
                persisted = false
            } catch (_: Exception) {
                persisted = false
            }

            if (!persisted) {
                try {
                    val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
                    val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val destFile = File(videosDir, "${System.currentTimeMillis()}_$safeName")
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    } ?: return@withContext VideoValidationResult(
                        isValid = false,
                        fileName = displayName,
                        errorMessage = "Unable to read video file. Please select another video from your phone."
                    )
                    workingUri = Uri.fromFile(destFile)
                } catch (e: Exception) {
                    return@withContext VideoValidationResult(
                        isValid = false,
                        fileName = displayName,
                        errorMessage = "Failed to prepare video for processing: ${e.localizedMessage ?: "File read error"}"
                    )
                }
            }
        }

        var hasVideoTrack = false
        var hasAudioTrack = false
        var width = 0
        var height = 0
        var durationMs = 0L
        var extractedThumbnailPath = ""

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, workingUri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    hasVideoTrack = true
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = maxOf(durationMs, format.getLong(MediaFormat.KEY_DURATION) / 1000L)
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudioTrack = true
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {}
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, workingUri)
            val hasVideoMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideoMeta == "yes") hasVideoTrack = true
            val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudioMeta == "yes") hasAudioTrack = true
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durStr.isNullOrBlank()) {
                durationMs = maxOf(durationMs, durStr.toLongOrNull() ?: 0L)
            }
            if (width == 0) {
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            }
            if (height == 0) {
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }

            if (hasVideoTrack) {
                val frameTimeUs = minOf(1_000_000L, (durationMs * 500L).coerceAtLeast(0L))
                val frameBitmap = retriever.getFrameAtTime(
                    frameTimeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: retriever.frameAtTime
                if (frameBitmap != null) {
                    try {
                        val thumbsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
                        val thumbFile = File(thumbsDir, "thumb_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(thumbFile).use { out ->
                            frameBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        extractedThumbnailPath = thumbFile.absolutePath
                    } catch (_: Exception) {
                    } finally {
                        try {
                            frameBitmap.recycle()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            if (!hasVideoTrack) {
                return@withContext VideoValidationResult(
                    isValid = false,
                    fileName = displayName,
                    errorMessage = "Unreadable video format (${e.localizedMessage ?: "invalid format"})."
                )
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        if (!hasVideoTrack) {
            return@withContext VideoValidationResult(
                isValid = false,
                fileName = displayName,
                errorMessage = "Selected file does not contain a valid video track. Please choose an MP4, MOV, or WebM video."
            )
        }

        val durationSeconds = (durationMs / 1000L).toInt()
        if (durationSeconds < MIN_DURATION_SEC) {
            return@withContext VideoValidationResult(
                isValid = false,
                fileName = displayName,
                durationSeconds = durationSeconds,
                errorMessage = "Video is too short (${durationSeconds}s). Please select a video at least $MIN_DURATION_SEC seconds long."
            )
        }
        if (durationSeconds > MAX_DURATION_SEC) {
            return@withContext VideoValidationResult(
                isValid = false,
                fileName = displayName,
                durationSeconds = durationSeconds,
                errorMessage = "Video exceeds the 60-minute limit (${durationSeconds / 60} minutes). Please select a video 60 minutes or less."
            )
        }

        val finalSizeMb = if (sizeMb > 0f) {
            sizeMb
        } else {
            val file = workingUri.path?.let { File(it) }
            if (file != null && file.exists()) file.length() / (1024f * 1024f) else 1f
        }

        VideoValidationResult(
            isValid = true,
            persistedUriString = workingUri.toString(),
            fileName = displayName,
            durationSeconds = durationSeconds,
            fileSizeMb = finalSizeMb,
            width = width,
            height = height,
            thumbnailPath = extractedThumbnailPath,
            hasAudioTrack = hasAudioTrack,
            errorMessage = null
        )
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) return cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    private fun queryFileSize(resolver: ContentResolver, uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx != -1) return cursor.getLong(idx)
                    }
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val f = uri.path?.let { File(it) }
            if (f != null && f.exists()) return f.length()
        }
        return 0L
    }
}
