package tw.dietbook

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/** Soft programmatic blips — no asset files; respects mute. */
object Sfx {
    private val main = Handler(Looper.getMainLooper())

    fun muted(ctx: Context): Boolean = Store.sfxMuted(ctx)
    fun setMuted(ctx: Context, v: Boolean) = Store.setSfxMuted(ctx, v)

    fun coin(ctx: Context) = play(ctx, 880.0, 90, soft = true)
    fun chew(ctx: Context) = play(ctx, 320.0, 70, soft = true)
    fun burp(ctx: Context) = play(ctx, 180.0, 140, soft = true)
    fun giggle(ctx: Context) = play(ctx, 620.0, 80, soft = true, chirp = true)
    fun nuzzle(ctx: Context) = play(ctx, 520.0, 100, soft = true)
    fun pop(ctx: Context) = play(ctx, 740.0, 60, soft = true)
    fun yawn(ctx: Context) = play(ctx, 240.0, 220, soft = true)
    fun sparkle(ctx: Context) {
        play(ctx, 988.0, 70, soft = true)
        main.postDelayed({ play(ctx, 1318.0, 90, soft = true) }, 90)
    }
    fun pour(ctx: Context) {
        play(ctx, 660.0, 50, soft = true)
        main.postDelayed({ play(ctx, 770.0, 50, soft = true) }, 70)
        main.postDelayed({ play(ctx, 880.0, 70, soft = true) }, 140)
    }

    private fun play(
        ctx: Context,
        freq: Double,
        ms: Int,
        soft: Boolean = true,
        chirp: Boolean = false,
    ) {
        if (muted(ctx)) return
        Thread {
            try {
                val sr = 22050
                val n = (sr * ms / 1000.0).toInt().coerceAtLeast(1)
                val buf = ShortArray(n)
                val vol = if (soft) 0.18 else 0.28
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    val env = sin(PI * i / n) // soft attack/release
                    val f = if (chirp) freq * (1.0 + 0.25 * i / n) else freq
                    val sample = (sin(2 * PI * f * t) * env * vol * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buf[i] = sample.toShort()
                }
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
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(ms.toLong() + 30)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }
}
