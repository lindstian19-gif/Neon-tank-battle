package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEffects {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var enabled: Boolean = true

    private val sampleRate = 22050

    private val shootSamples: ShortArray by lazy {
        generateSweep(startFreq = 760.0, endFreq = 240.0, durationMs = 95, volume = 0.25)
    }

    private val bounceSamples: ShortArray by lazy {
        generateSweep(startFreq = 520.0, endFreq = 860.0, durationMs = 60, volume = 0.18)
    }

    private val powerUpSamples: ShortArray by lazy {
        generateChime(freqs = doubleArrayOf(523.25, 659.25, 783.99), durationMs = 180, volume = 0.25)
    }

    private val shieldSamples: ShortArray by lazy {
        generateSweep(startFreq = 320.0, endFreq = 980.0, durationMs = 140, volume = 0.28)
    }

    private val explosionSamples: ShortArray by lazy {
        generateExplosion(durationMs = 320, volume = 0.42)
    }

    private val winSamples: ShortArray by lazy {
        generateChime(freqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.5), durationMs = 320, volume = 0.3)
    }

    private val dashSamples: ShortArray by lazy {
        generateSweep(startFreq = 220.0, endFreq = 640.0, durationMs = 110, volume = 0.22)
    }

    fun playShoot() = play(shootSamples)
    fun playBounce() = play(bounceSamples)
    fun playPowerUp() = play(powerUpSamples)
    fun playShieldBlock() = play(shieldSamples)
    fun playExplosion() = play(explosionSamples)
    fun playWin() = play(winSamples)
    fun playDash() = play(dashSamples)

    private fun play(samples: ShortArray) {
        if (!enabled) return
        executor.execute {
            try {
                val bufferBytes = samples.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                val sleepMs = (samples.size * 1000L) / sampleRate + 30L
                Thread.sleep(sleepMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Ignore audio errors on headless/test devices
            }
        }
    }

    private fun generateSweep(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * t
            phase += 2.0 * PI * freq / sampleRate
            val env = exp(-4.5 * t)
            val sample = (sin(phase) * env * volume * Short.MAX_VALUE).toInt()
            out[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun generateChime(
        freqs: DoubleArray,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val segment = (count / freqs.size).coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until count) {
            val idx = (i / segment).coerceAtMost(freqs.lastIndex)
            val localT = (i % segment).toDouble() / segment
            phase += 2.0 * PI * freqs[idx] / sampleRate
            val env = exp(-2.5 * localT)
            val sample = (sin(phase) * env * volume * Short.MAX_VALUE).toInt()
            out[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun generateExplosion(durationMs: Int, volume: Double): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val rng = Random(42)
        var lowPass = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            val white = rng.nextDouble() * 2.0 - 1.0
            lowPass = lowPass * 0.72 + white * 0.28
            val env = exp(-5.0 * t)
            val sample = (lowPass * env * volume * Short.MAX_VALUE).toInt()
            out[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    fun release() {
        executor.shutdownNow()
    }
}
