package io.github.ththemabit.questlog.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Kleine 8-Bit-Klänge, live synthetisiert (keine Audiodateien nötig). */
object Sfx {
    enum class Kind { TAP, DONE, MINI, LEVEL, LOOT, RECORD, UNDO, ERROR, SPIN, HIT }

    private val worker = Executors.newSingleThreadExecutor()
    private const val RATE = 22050

    fun play(kind: Kind) {
        worker.execute { runCatching { render(kind) } }
    }

    private fun notes(kind: Kind): List<Pair<Double, Int>> = when (kind) {
        Kind.TAP -> listOf(880.0 to 22)
        Kind.SPIN -> listOf(1320.0 to 14)
        Kind.HIT -> listOf(392.0 to 45, 587.33 to 70)
        Kind.DONE -> listOf(659.25 to 70, 783.99 to 70, 1046.5 to 150)
        Kind.MINI -> listOf(783.99 to 60, 1046.5 to 100)
        Kind.LEVEL -> listOf(523.25 to 90, 659.25 to 90, 783.99 to 90, 1046.5 to 90, 1318.5 to 260)
        Kind.LOOT -> listOf(1318.5 to 50, 1567.98 to 50, 2093.0 to 60, 1567.98 to 40, 2637.0 to 140)
        Kind.RECORD -> listOf(783.99 to 80, 987.77 to 80, 1174.66 to 80, 1567.98 to 240)
        Kind.UNDO -> listOf(659.25 to 60, 440.0 to 100)
        Kind.ERROR -> listOf(196.0 to 90, 174.61 to 140)
    }

    private fun render(kind: Kind) {
        val ns = notes(kind)
        val total = ns.sumOf { it.second } * RATE / 1000
        val buf = ShortArray(total)
        var i = 0
        for ((freq, ms) in ns) {
            val n = ms * RATE / 1000
            for (k in 0 until n) {
                if (i >= total) break
                val t = k.toDouble() / RATE
                val square = if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
                val attack = min(1.0, k / 60.0)
                val decay = 1.0 - k.toDouble() / n * 0.7
                buf[i++] = (square * attack * decay * 0.16 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep((ns.sumOf { it.second } + 60).toLong())
        track.release()
    }
}

/** Rückmeldung: Klang und Haptik, beides abschaltbar. */
class Fx(private val view: View?, private val sound: () -> Boolean, private val haptics: () -> Boolean) {
    private fun buzz(strong: Boolean) {
        if (!haptics()) return
        val v = view ?: return
        val c = if (strong && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        v.performHapticFeedback(c)
    }

    private fun play(k: Sfx.Kind) {
        if (sound()) Sfx.play(k)
    }

    fun tap() { buzz(false); play(Sfx.Kind.TAP) }
    fun done() { buzz(true); play(Sfx.Kind.DONE) }
    fun mini() { buzz(true); play(Sfx.Kind.MINI) }
    fun hit() { buzz(true); play(Sfx.Kind.HIT) }
    fun level() { buzz(true); play(Sfx.Kind.LEVEL) }
    fun loot() { buzz(true); play(Sfx.Kind.LOOT) }
    fun record() { buzz(true); play(Sfx.Kind.RECORD) }
    fun undo() { buzz(false); play(Sfx.Kind.UNDO) }
    fun error() { buzz(false); play(Sfx.Kind.ERROR) }
    fun spin() { play(Sfx.Kind.SPIN) }
}

val LocalFx = staticCompositionLocalOf { Fx(null, { false }, { false }) }
