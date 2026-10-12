package app.sparkbible.listen

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

object Ambient {
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun set(on: Boolean) {
        if (on) start() else stop()
    }

    private fun start() {
        if (running) return
        val sampleRate = 22050
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = created
        created.play()
        running = true
        thread = Thread {
            val buf = ShortArray(1024)
            var last = 0.0
            val rnd = Random(7)
            while (running) {
                for (i in buf.indices) {
                    val white = rnd.nextDouble() * 2.0 - 1.0
                    last = (last + 0.02 * white) / 1.02
                    buf[i] = (last * 5200).toInt().coerceIn(-32767, 32767).toShort()
                }
                created.write(buf, 0, buf.size)
            }
        }.apply {
            isDaemon = true
            name = "spark-ambient"
            start()
        }
    }

    fun stop() {
        running = false
        thread = null
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }
}
