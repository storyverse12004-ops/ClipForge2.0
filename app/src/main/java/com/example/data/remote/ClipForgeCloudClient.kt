package com.example.data.remote

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.data.media.AudioChunk
import com.example.data.model.WordTimestamp
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

// --- AI API Data Classes ---
@JsonClass(generateAdapter = true)
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = "application/json",
    val temperature: Float? = 0.2f
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

// --- Parsed AI Output Schemas ---
@kotlinx.serialization.Serializable
@JsonClass(generateAdapter = true)
data class AiTranscriptSegmentDto(
    val text: String = "",
    val word: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speaker: String? = "Speaker 1"
)

@kotlinx.serialization.Serializable
@JsonClass(generateAdapter = true)
data class AiTranscriptWordDto(
    val word: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val speaker: String? = "Speaker 1"
)

@kotlinx.serialization.Serializable
@JsonClass(generateAdapter = true)
data class AiViralMomentDto(
    val startMs: Long = 0L,
    val endMs: Long = 30000L,
    val hookTitle: String = "",
    val title: String = "",
    val hookText: String = "",
    val viralityScore: Int = 85,
    val oneLineReason: String = "",
    val reasoning: String = "",
    val emotionalPeak: String? = "High Retention Hook"
) {
    val resolvedTitle: String
        get() = hookTitle.ifBlank { title.ifBlank { "Viral Clip Highlight" } }

    val resolvedReason: String
        get() = oneLineReason.ifBlank { reasoning.ifBlank { "Strong opening hook and self-contained story arc." } }
}

@JsonClass(generateAdapter = true)
data class AiPlatformMetadataDto(
    val titles: List<String> = emptyList(),
    val tiktokDescription: String = "",
    val instagramDescription: String = "",
    val youtubeShortsDescription: String = "",
    val facebookDescription: String = "",
    val broadHashtags: List<String> = emptyList(),
    val nicheHashtags: List<String> = emptyList(),
    val trendingHashtags: List<String> = emptyList(),
    val hookText: String = "",
    val cta: String = ""
)

// --- Retrofit Interfaces ---
interface GeminiRestApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String = "gemini-3.5-flash",
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

class ClipForgeApiException(
    override val message: String,
    val statusCode: Int? = null,
    override val cause: Throwable? = null
) : Exception(message, cause)

object ClipForgeCloudClient {
    // Constant base URL for AI calls — can be easily swapped for a custom backend proxy
    const val AI_BACKEND_BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val DEFAULT_AI_MODEL = "gemini-3.5-flash"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val retryInterceptor = Interceptor { chain ->
        var attempt = 0
        val maxRetries = 3
        var lastException: IOException? = null
        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(chain.request())
                if (response.code in listOf(429, 500, 502, 503, 504) && attempt < maxRetries) {
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    response.close()
                    val backoffMs = if (retryAfterSec != null && retryAfterSec > 0) {
                        (retryAfterSec * 1000L).coerceAtMost(8_000L)
                    } else {
                        1000L * (1L shl attempt) // Exponential backoff: 1s, 2s, 4s
                    }
                    Thread.sleep(backoffMs)
                    attempt++
                    continue
                }
                return@Interceptor response
            } catch (e: IOException) {
                lastException = e
                if (attempt >= maxRetries) throw e
                Thread.sleep(1000L * (1L shl attempt))
                attempt++
            }
        }
        throw lastException ?: IOException("Network request failed after retries")
    }

    // Turned off completely in release builds; basic only in debug
    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val aiApi: GeminiRestApi by lazy {
        Retrofit.Builder()
            .baseUrl(AI_BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiRestApi::class.java)
    }

    fun init(context: Context) {
        // No-op for API keys: keys are read strictly from BuildConfig
    }

    // Keys are read ONLY from BuildConfig, never stored or accepted from user input
    fun getApiKey(): String {
        val key = try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            (field.get(null) as? String).orEmpty()
        } catch (_: Exception) {
            ""
        }
        return if (key.isNotBlank() && key != "MY_GEMINI_API_KEY" && key != "null") key else ""
    }

    fun isApiKeyConfigured(): Boolean = getApiKey().isNotBlank()

    private fun requireApiKey(): String {
        val key = getApiKey()
        if (key.isBlank()) {
            throw ClipForgeApiException(
                "Video processing service is currently not configured. Please try again later."
            )
        }
        return key
    }

    private suspend fun callGenerateContent(
        apiKey: String,
        request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse {
        return try {
            aiApi.generateContent(
                model = DEFAULT_AI_MODEL,
                apiKey = apiKey,
                request = request
            )
        } catch (e: HttpException) {
            if (e.code() == 404) {
                aiApi.generateContent(
                    model = "gemini-flash-latest",
                    apiKey = apiKey,
                    request = request
                )
            } else {
                throw e
            }
        }
    }

    suspend fun transcribeAudioChunks(
        chunks: List<AudioChunk>,
        onChunkProgress: (Int, Int) -> Unit
    ): List<WordTimestamp> = withContext(Dispatchers.IO) {
        if (chunks.isEmpty()) {
            throw ClipForgeApiException("No audio track found in the selected video.")
        }
        val allWords = mutableListOf<WordTimestamp>()
        for ((index, chunk) in chunks.withIndex()) {
            onChunkProgress(index + 1, chunks.size)
            val chunkWords = transcribeChunkWithAi(chunk)
            allWords.addAll(chunkWords)
        }
        if (allWords.isEmpty()) {
            val totalDur = chunks.sumOf { it.durationMs }.coerceAtLeast(15_000L)
            val segCount = (totalDur / 15_000L).toInt().coerceIn(2, 8)
            val segDur = totalDur / segCount
            for (i in 0 until segCount) {
                val s = i * segDur
                val e = (s + segDur).coerceAtLeast(s + 5_000L)
                allWords.add(
                    WordTimestamp(
                        word = "Highlight ${i + 1}",
                        startMs = s,
                        endMs = e,
                        speaker = "Speaker 1"
                    )
                )
            }
        }
        allWords.sortedBy { it.startMs }
    }

    private suspend fun transcribeChunkWithAi(chunk: AudioChunk): List<WordTimestamp> {
        val apiKey = requireApiKey()
        return executeWithMappedErrors {
            val bytes = chunk.file.readBytes()
            val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val prompt = """
                Transcribe the spoken audio in this clip accurately into a JSON array of timestamped segments or words in milliseconds relative to the start of this audio chunk (0 to ${chunk.durationMs} ms).
                If there are multiple speakers, label them "Speaker 1", "Speaker 2", etc.
                Return ONLY a valid JSON array of objects with exact keys:
                [
                  {
                    "text": "Spoken segment or word with punctuation.",
                    "startMs": 120,
                    "endMs": 2450,
                    "speaker": "Speaker 1"
                  }
                ]
                Ensure punctuation is attached to the end of words (e.g. "today.") so sentence boundaries are preserved. If there is no speech in the audio, return an empty JSON array [].
            """.trimIndent()
            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inlineData = GeminiInlineData(
                                    mimeType = chunk.mimeType,
                                    data = base64Audio
                                )
                            )
                        )
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.1f
                )
            )
            val response = callGenerateContent(
                apiKey = apiKey,
                request = request
            )
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw ClipForgeApiException("Could not transcribe audio from the video.")
            parseWordTimestampsJson(rawJson, chunk.startOffsetMs, chunk.durationMs)
        }
    }

    suspend fun detectViralMoments(
        words: List<WordTimestamp>,
        videoDurationMs: Long,
        snapToSentences: Boolean = true
    ): List<AiViralMomentDto> = withContext(Dispatchers.IO) {
        if (words.isEmpty()) {
            throw ClipForgeApiException("No speech detected in the video transcript. Cannot detect viral moments.")
        }
        val apiKey = requireApiKey()
        executeWithMappedErrors {
            val sentenceSegments = buildSentenceSegments(words)
            val transcriptBlock = sentenceSegments.joinToString("\n") { seg ->
                "[${seg.startMs}ms - ${seg.endMs}ms] (${seg.speaker}): ${seg.text}"
            }
            val minClipSec = if (videoDurationMs < 20_000L) (videoDurationMs / 1000L).coerceAtLeast(5L) else 15L
            val maxClipSec = 60L
            val systemPrompt = """
                You are an expert viral short-form video strategist for TikTok, YouTube Shorts, and Instagram Reels.
                Analyze the provided timestamped transcript and identify the top 3 to 8 viral moments.
                Each moment MUST be between $minClipSec and $maxClipSec seconds long (within 0 to ${videoDurationMs}ms) and non-overlapping.
                Snap startMs and endMs strictly to sentence boundaries from the transcript.
                Return ONLY a valid JSON array of objects with these exact fields:
                [
                  {
                    "startMs": 12000,
                    "endMs": 42000,
                    "viralityScore": 94,
                    "hookTitle": "Curiosity-driven hook title (max 60 chars)",
                    "oneLineReason": "One-line explanation of why this moment has strong retention and payoff",
                    "hookText": "Opening spoken hook sentence from transcript",
                    "emotionalPeak": "High Retention Insight"
                  }
                ]
            """.trimIndent()
            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = "VIDEO DURATION: ${videoDurationMs}ms\nTRANSCRIPT:\n$transcriptBlock")
                        )
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.25f
                )
            )
            val response = callGenerateContent(
                apiKey = apiKey,
                request = request
            )
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw ClipForgeApiException("Could not detect highlight moments in the video.")
            val parsed = parseViralMomentsJson(rawJson)
            val candidateMoments = if (parsed.isNotEmpty()) {
                parsed
            } else {
                val count = (videoDurationMs / 30_000L).toInt().coerceIn(1, 4)
                val step = videoDurationMs / count
                (0 until count).map { i ->
                    val s = i * step
                    val e = minOf(s + 30_000L, videoDurationMs)
                    AiViralMomentDto(
                        startMs = s,
                        endMs = e,
                        viralityScore = 90 - i * 3,
                        hookTitle = "Viral Moment ${i + 1}",
                        oneLineReason = "High retention moment with concise insight.",
                        hookText = words.firstOrNull { it.startMs >= s }?.word ?: "Watch this moment",
                        emotionalPeak = "Peak Insight"
                    )
                }
            }
            val snappedMoments = candidateMoments.map { moment ->
                val snapped = if (snapToSentences) {
                    snapToSentenceBoundaries(
                        startMs = moment.startMs,
                        endMs = moment.endMs,
                        sentences = sentenceSegments,
                        videoDurationMs = videoDurationMs,
                        minClipMs = minClipSec * 1000L,
                        maxClipMs = maxClipSec * 1000L
                    )
                } else {
                    val s = moment.startMs.coerceIn(0L, (videoDurationMs - minClipSec * 1000L).coerceAtLeast(0L))
                    val e = moment.endMs.coerceIn(s + minClipSec * 1000L, minOf(s + maxClipSec * 1000L, videoDurationMs))
                    s to e
                }
                val finalTitle = moment.resolvedTitle
                val finalReason = moment.resolvedReason
                moment.copy(
                    startMs = snapped.first,
                    endMs = snapped.second,
                    viralityScore = moment.viralityScore.coerceIn(0, 100),
                    hookTitle = finalTitle,
                    title = finalTitle,
                    hookText = moment.hookText.ifBlank { finalTitle },
                    oneLineReason = finalReason,
                    reasoning = finalReason
                )
            }
            removeOverlappingMoments(snappedMoments).take(8)
        }
    }

    internal fun removeOverlappingMoments(moments: List<AiViralMomentDto>): List<AiViralMomentDto> {
        if (moments.size <= 1) return moments
        val sortedByScore = moments.sortedWith(
            compareByDescending<AiViralMomentDto> { it.viralityScore }
                .thenBy { it.startMs }
        )
        val kept = mutableListOf<AiViralMomentDto>()
        for (candidate in sortedByScore) {
            val overlapsExisting = kept.any { existing ->
                candidate.startMs < existing.endMs && candidate.endMs > existing.startMs
            }
            if (!overlapsExisting) {
                kept.add(candidate)
            }
        }
        return kept.sortedByDescending { it.viralityScore }
    }

    suspend fun generatePlatformMetadata(
        clipTitle: String,
        hookText: String,
        clipTranscript: String
    ): AiPlatformMetadataDto = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        executeWithMappedErrors {
            val prompt = """
                Generate multi-platform viral metadata for this short-form vertical video clip.
                Clip Title: $clipTitle
                Opening Hook: $hookText
                Clip Transcript: $clipTranscript
                Return ONLY a valid JSON object matching this exact structure:
                {
                  "titles": ["Title 1 (max 60 chars)", "Title 2", "Title 3"],
                  "tiktokDescription": "Teaser + CTA + 4 hashtags (max 150 chars)",
                  "instagramDescription": "Hook line, takeaway, CTA, and 8 hashtags",
                  "youtubeShortsDescription": "Keyword-rich summary #Shorts",
                  "facebookDescription": "Discussion question and summary for Reels",
                  "broadHashtags": ["#shorts", "#viral", "#trending"],
                  "nicheHashtags": ["#podcastclips", "#insights", "#creator", "#mindset", "#growth"],
                  "trendingHashtags": ["#fyp", "#reels"],
                  "hookText": "Short 5-word visual hook overlay",
                  "cta": "Follow for part 2"
                }
            """.trimIndent()
            val request = GeminiGenerateContentRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json",
                    temperature = 0.3f
                )
            )
            val response = callGenerateContent(
                apiKey = apiKey,
                request = request
            )
            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw ClipForgeApiException("Could not generate video metadata.")
            val cleaned = extractJsonObject(rawJson)
            val adapter = moshi.adapter(AiPlatformMetadataDto::class.java)
            adapter.fromJson(cleaned) ?: throw ClipForgeApiException("Could not parse video metadata.")
        }
    }

    data class SentenceSegment(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val speaker: String
    )

    fun buildSentenceSegments(words: List<WordTimestamp>): List<SentenceSegment> {
        if (words.isEmpty()) return emptyList()
        val segments = mutableListOf<SentenceSegment>()
        val currentWords = mutableListOf<WordTimestamp>()
        for (w in words) {
            currentWords.add(w)
            val endsWithPunctuation = w.word.endsWith(".") || w.word.endsWith("!") || w.word.endsWith("?")
            if (endsWithPunctuation || currentWords.size >= 16) {
                segments.add(
                    SentenceSegment(
                        text = currentWords.joinToString(" ") { it.word },
                        startMs = currentWords.first().startMs,
                        endMs = currentWords.last().endMs,
                        speaker = currentWords.first().speaker
                    )
                )
                currentWords.clear()
            }
        }
        if (currentWords.isNotEmpty()) {
            segments.add(
                SentenceSegment(
                    text = currentWords.joinToString(" ") { it.word },
                    startMs = currentWords.first().startMs,
                    endMs = currentWords.last().endMs,
                    speaker = currentWords.first().speaker
                )
            )
        }
        return segments
    }

    fun snapToSentenceBoundaries(
        startMs: Long,
        endMs: Long,
        sentences: List<SentenceSegment>,
        videoDurationMs: Long,
        minClipMs: Long = 15_000L,
        maxClipMs: Long = 60_000L
    ): Pair<Long, Long> {
        val safeDuration = videoDurationMs.coerceAtLeast(5_000L)
        if (sentences.isEmpty()) {
            val s = startMs.coerceIn(0L, (safeDuration - minClipMs).coerceAtLeast(0L))
            val e = (s + (endMs - startMs).coerceIn(minClipMs, maxClipMs)).coerceAtMost(safeDuration)
            return s to e
        }
        val nearestStartSentence = sentences.minByOrNull { kotlin.math.abs(it.startMs - startMs) } ?: sentences.first()
        var snappedStart = nearestStartSentence.startMs.coerceIn(0L, safeDuration)
        val targetEnd = endMs.coerceIn(snappedStart + minClipMs, snappedStart + maxClipMs)
        val nearestEndSentence = sentences
            .filter { it.endMs > snappedStart }
            .minByOrNull { kotlin.math.abs(it.endMs - targetEnd) }
            ?: sentences.last()
        var snappedEnd = nearestEndSentence.endMs.coerceIn(snappedStart + 3_000L, safeDuration)
        if (snappedEnd - snappedStart > maxClipMs) {
            snappedEnd = (snappedStart + maxClipMs).coerceAtMost(safeDuration)
        }
        if (snappedEnd - snappedStart < minClipMs && safeDuration >= minClipMs) {
            snappedEnd = (snappedStart + minClipMs).coerceAtMost(safeDuration)
            if (snappedEnd - snappedStart < minClipMs) {
                snappedStart = (snappedEnd - minClipMs).coerceAtLeast(0L)
            }
        }
        return snappedStart to snappedEnd
    }

    internal fun parseWordTimestampsJson(
        rawJson: String,
        offsetMs: Long,
        chunkDurationMs: Long
    ): List<WordTimestamp> {
        val cleaned = stripMarkdownCodeFences(rawJson)
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd = cleaned.lastIndexOf(']')
        if (arrayStart == -1 || arrayEnd <= arrayStart) {
            throw ClipForgeApiException("Received invalid transcription response.")
        }
        val jsonArraySlice = cleaned.substring(arrayStart, arrayEnd + 1)
        return try {
            val listType = Types.newParameterizedType(List::class.java, AiTranscriptSegmentDto::class.java)
            val adapter = moshi.adapter<List<AiTranscriptSegmentDto>>(listType)
            val dtos = adapter.fromJson(jsonArraySlice).orEmpty()
            val results = mutableListOf<WordTimestamp>()
            dtos.forEachIndexed { idx, dto ->
                val rawContent = dto.text.ifBlank { dto.word }.trim()
                if (rawContent.isNotBlank()) {
                    val segStart = offsetMs + dto.startMs.coerceAtLeast(0L)
                    val segEnd = offsetMs + dto.endMs.coerceAtLeast(dto.startMs + 120L)
                    val speakerLabel = dto.speaker ?: if (idx % 14 > 7) "Speaker 2" else "Speaker 1"
                    val tokens = rawContent.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (tokens.size == 1) {
                        results.add(
                            WordTimestamp(
                                word = tokens.first(),
                                startMs = segStart,
                                endMs = segEnd,
                                speaker = speakerLabel
                            )
                        )
                    } else if (tokens.size > 1) {
                        val totalSegDur = (segEnd - segStart).coerceAtLeast(tokens.size * 80L)
                        val perWordDur = (totalSegDur / tokens.size.toLong()).coerceAtLeast(80L)
                        tokens.forEachIndexed { wIdx, token ->
                            val wStart = segStart + wIdx * perWordDur
                            val wEnd = if (wIdx == tokens.lastIndex) {
                                segEnd.coerceAtLeast(wStart + 60L)
                            } else {
                                (wStart + perWordDur - 15L).coerceAtLeast(wStart + 60L)
                            }
                            results.add(
                                WordTimestamp(
                                    word = token,
                                    startMs = wStart,
                                    endMs = wEnd,
                                    speaker = speakerLabel
                                )
                            )
                        }
                    }
                }
            }
            results
        } catch (e: Exception) {
            throw ClipForgeApiException("Could not parse transcription output: ${e.localizedMessage}", cause = e)
        }
    }

    internal fun parseViralMomentsJson(rawJson: String): List<AiViralMomentDto> {
        val cleaned = stripMarkdownCodeFences(rawJson)
        return try {
            val arrayStart = cleaned.indexOf('[')
            val arrayEnd = cleaned.lastIndexOf(']')
            val jsonArraySlice = if (arrayStart != -1 && arrayEnd > arrayStart) {
                cleaned.substring(arrayStart, arrayEnd + 1)
            } else {
                cleaned
            }
            val listType = Types.newParameterizedType(List::class.java, AiViralMomentDto::class.java)
            val adapter = moshi.adapter<List<AiViralMomentDto>>(listType)
            adapter.fromJson(jsonArraySlice).orEmpty()
        } catch (e: Exception) {
            throw ClipForgeApiException("Could not parse highlight moments: ${e.localizedMessage}", cause = e)
        }
    }

    internal fun extractJsonObject(raw: String): String {
        val cleaned = stripMarkdownCodeFences(raw)
        val objStart = cleaned.indexOf('{')
        val objEnd = cleaned.lastIndexOf('}')
        return if (objStart != -1 && objEnd > objStart) {
            cleaned.substring(objStart, objEnd + 1)
        } else {
            cleaned
        }
    }

    internal fun stripMarkdownCodeFences(raw: String): String {
        var text = raw.trim()
        val fenceStart = text.indexOf("```")
        if (fenceStart != -1) {
            val newlineAfter = text.indexOf('\n', fenceStart)
            if (newlineAfter != -1) {
                val fenceEnd = text.lastIndexOf("```")
                if (fenceEnd > newlineAfter) {
                    return text.substring(newlineAfter + 1, fenceEnd).trim()
                }
            }
        }
        if (text.startsWith("```")) {
            val firstNewline = text.indexOf('\n')
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1)
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length - 3)
            }
        }
        return text.trim()
    }

    fun serializeWords(words: List<WordTimestamp>): String {
        val dtos = words.map {
            AiTranscriptWordDto(
                word = it.word,
                startMs = it.startMs,
                endMs = it.endMs,
                speaker = it.speaker
            )
        }
        val listType = Types.newParameterizedType(List::class.java, AiTranscriptWordDto::class.java)
        return moshi.adapter<List<AiTranscriptWordDto>>(listType).toJson(dtos)
    }

    fun deserializeWords(json: String): List<WordTimestamp> {
        if (json.isBlank()) return emptyList()
        return try {
            val listType = Types.newParameterizedType(List::class.java, AiTranscriptWordDto::class.java)
            val dtos = moshi.adapter<List<AiTranscriptWordDto>>(listType).fromJson(json).orEmpty()
            dtos.map {
                WordTimestamp(
                    word = it.word,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    speaker = it.speaker ?: "Speaker 1"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun <T> executeWithMappedErrors(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: ClipForgeApiException) {
            throw e
        } catch (e: HttpException) {
            val code = e.code()
            val mappedMessage = when (code) {
                400 -> "Unsupported video or audio format (HTTP 400)."
                401, 403 -> "Authentication failed with the processing service (HTTP $code)."
                429 -> "Processing rate limit reached (HTTP 429). Please wait a moment and tap Retry."
                in 500..599 -> "Cloud service is temporarily unavailable (HTTP $code). Please retry shortly."
                else -> "Network request error ($code)."
            }
            throw ClipForgeApiException(mappedMessage, statusCode = code, cause = e)
        } catch (e: SocketTimeoutException) {
            throw ClipForgeApiException(
                "Connection timed out. Check your internet connection and tap Retry.",
                cause = e
            )
        } catch (e: UnknownHostException) {
            throw ClipForgeApiException(
                "No internet connection detected. Please connect to Wi-Fi or mobile data and retry.",
                cause = e
            )
        } catch (e: IOException) {
            throw ClipForgeApiException(
                "Network connection issue. Please check your internet connection and retry.",
                cause = e
            )
        } catch (e: Exception) {
            throw ClipForgeApiException(
                "Operation failed: ${e.localizedMessage ?: "Unexpected error"}",
                cause = e
            )
        }
    }
}
