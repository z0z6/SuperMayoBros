package pl.maja.game

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/** Proste efekty dźwiękowe generowane w kodzie (fale prostokątne) - bez plików audio. */
object Sfx {
    private val tracks = HashMap<String, AudioTrack>()
    var enabled = true

    @Suppress("DEPRECATION")
    private fun reg(name: String, vararg notes: Pair<Float, Int>) {
        val rate = 22050
        var total = 0
        for (n in notes) total += rate * n.second / 1000
        val data = ShortArray(total)
        var idx = 0
        for (n in notes) {
            val f = n.first
            val cnt = rate * n.second / 1000
            for (i in 0 until cnt) {
                val s = if (f <= 0f) 0f else {
                    val ph = (i * f / rate) % 1f
                    if (ph < 0.5f) 1f else -1f
                }
                val env = 1f - 0.5f * i / cnt
                data[idx++] = (s * env * 5000f).toInt().toShort()
            }
        }
        val t = AudioTrack(
            AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, data.size * 2, AudioTrack.MODE_STATIC
        )
        t.write(data, 0, data.size)
        tracks[name] = t
    }

    fun init() {
        try {
            reg("jump", 280f to 35, 440f to 35, 620f to 50)
            reg("stomp", 220f to 30, 120f to 60)
            reg("coin", 900f to 40, 1350f to 100)
            reg("bump", 140f to 60)
            reg("break", 300f to 25, 0f to 10, 200f to 30, 120f to 45)
            reg("item", 400f to 40, 500f to 40, 600f to 40, 800f to 80)
            reg("grow", 300f to 40, 400f to 40, 500f to 40, 600f to 40, 750f to 60)
            reg("shrink", 700f to 40, 550f to 40, 400f to 40, 300f to 60)
            reg("die", 500f to 90, 420f to 90, 340f to 90, 260f to 120, 160f to 220)
            reg("kick", 700f to 30, 480f to 60)
            reg("shoot", 800f to 25, 520f to 45)
            reg("clear", 520f to 90, 660f to 90, 780f to 90, 1040f to 90, 780f to 90, 1040f to 250)
            reg("boss", 160f to 100, 110f to 160)
            reg("oneup", 660f to 70, 880f to 70, 1100f to 70, 1320f to 120)
            reg("tick", 1200f to 12)
        } catch (e: Exception) {
            // brak dźwięku nie może zabić gry
        }
    }

    fun play(name: String) {
        if (!enabled) return
        val t = tracks[name] ?: return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
        }
    }
}
