package com.example.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

object SoundEffectManager {
    private const val TAG = "SoundEffectManager"
    private const val SAMPLE_RATE = 44100
    private const val BUTTON_VOLUME = 0.88f
    private const val TAB_VOLUME = 0.84f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var buttonTapPcm: ShortArray = ShortArray(0)
    @Volatile
    private var tabSelectPcm: ShortArray = ShortArray(0)
    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            buttonTapPcm = synthesizeButtonTapPcm()
            tabSelectPcm = synthesizeTabSelectPcm()
            isInitialized = true
        } catch (e: Exception) {
            Log.w(TAG, "SoundEffectManager init: ${e.message}")
        }
    }

    fun isSoundEnabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("clipforge_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("sound_effects_enabled", true)
        } catch (_: Exception) {
            true
        }
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        try {
            val prefs = context.getSharedPreferences("clipforge_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("sound_effects_enabled", enabled).apply()
        } catch (_: Exception) {}
    }

    fun playSuccessCelebration(context: Context) {
        if (!isSoundEnabled(context)) return
        val pcm = synthesizeCelebrationPcm()
        playPcmAsync(pcm, 0.90f)
    }

    internal fun synthesizeCelebrationPcm(): ShortArray {
        val durationSec = 0.28
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        val noteDur = durationSec / 4.0
        val freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val noteIdx = (t / noteDur).toInt().coerceIn(0, 3)
            val noteT = t - (noteIdx * noteDur)
            val freq = freqs[noteIdx]
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val env = exp(-noteT * 18.0)
            val sig = sin(phase) * env
            samples[i] = (tanh(sig * 1.2) * Short.MAX_VALUE * 0.90).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }

    fun playButtonTap() {
        val pcm = if (buttonTapPcm.isNotEmpty()) buttonTapPcm else synthesizeButtonTapPcm().also { buttonTapPcm = it }
        playPcmAsync(pcm, BUTTON_VOLUME)
    }

    fun playTabSelect() {
        val pcm = if (tabSelectPcm.isNotEmpty()) tabSelectPcm else synthesizeTabSelectPcm().also { tabSelectPcm = it }
        playPcmAsync(pcm, TAB_VOLUME)
    }

    private fun playPcmAsync(pcmData: ShortArray, volume: Float) {
        scope.launch {
            var track: AudioTrack? = null
            try {
                val byteLength = pcmData.size * 2
                val minBufSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(byteLength)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcmData, 0, pcmData.size)
                track.setVolume(volume)
                track.play()
            } catch (e: Exception) {
                try {
                    track?.release()
                } catch (_: Exception) {}
                return@launch
            }

            kotlinx.coroutines.delay(85)
            try {
                track.stop()
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}
        }
    }

    internal fun synthesizeButtonTapPcm(): ShortArray {
        val durationSec = 0.036
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        var phaseBody = 0.0
        var phaseClick = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.0008).coerceAtMost(1.0)
            val bodyEnv = attack * exp(-t * 145.0)
            val clickEnv = attack * exp(-t * 420.0)
            val instantFreq = 290.0 + 1060.0 * exp(-t * 260.0)
            phaseBody += 2.0 * PI * instantFreq / SAMPLE_RATE
            phaseClick += 2.0 * PI * 3100.0 / SAMPLE_RATE
            val rawSignal = (sin(phaseBody) * 0.78 * bodyEnv) +
                (sin(phaseClick) * 0.38 * clickEnv)
            val cleanSignal = tanh(rawSignal * 1.35)
            samples[i] = (cleanSignal * Short.MAX_VALUE * 0.94).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }

    internal fun synthesizeTabSelectPcm(): ShortArray {
        val durationSec = 0.044
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        var phaseTone1 = 0.0
        var phaseTone2 = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.0012).coerceAtMost(1.0)
            val env1 = attack * exp(-t * 115.0)
            val env2 = attack * exp(-t * 190.0)
            val freq1 = 660.0 + 220.0 * (1.0 - exp(-t * 180.0))
            val freq2 = freq1 * 1.5
            phaseTone1 += 2.0 * PI * freq1 / SAMPLE_RATE
            phaseTone2 += 2.0 * PI * freq2 / countOrOne(SAMPLE_RATE)
            val rawSignal = (sin(phaseTone1) * 0.72 * env1) +
                (sin(phaseTone2) * 0.34 * env2)
            val cleanSignal = tanh(rawSignal * 1.25)
            samples[i] = (cleanSignal * Short.MAX_VALUE * 0.92).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }

    private fun countOrOne(rate: Int): Double = rate.toDouble()
}
