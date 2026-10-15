package app.sparkbible.listen

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class KokoroPack(val phase: String, val progress: Float, val message: String)

object KokoroFiles {
    const val ENGINE = "kokoro"
    const val FULL = "full"
    const val SMALL = "small"
    const val CPU = "cpu"
    const val NNAPI = "nnapi"

    private val urls = mapOf(
        FULL to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2",
        SMALL to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2",
    )

    val packs = MutableStateFlow(
        mapOf(
            FULL to KokoroPack("idle", 0f, ""),
            SMALL to KokoroPack("idle", 0f, ""),
        ),
    )
    val chipNote = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun chipAvailable(): Boolean = Build.VERSION.SDK_INT >= 27

    fun noteChipFellBack() {
        chipNote.value = "This phone kept the main processor. The AI chip couldn't take the voice."
    }

    fun dir(context: Context, size: String = FULL): File =
        File(context.filesDir, if (size == SMALL) "kokoro-small" else "kokoro")

    fun root(context: Context, size: String = FULL): File? {
        val dir = dir(context, size)
        val direct = if (complete(dir)) dir else null
        if (direct != null) return direct
        val found = dir.walkTopDown().firstOrNull { it.name == "model.onnx" }?.parentFile ?: return null
        return if (complete(found)) found else null
    }

    fun ready(context: Context, size: String = FULL): Boolean = root(context, size) != null

    fun refresh(context: Context) {
        packs.value = packs.value.mapValues { (size, pack) ->
            if (ready(context, size)) pack.copy(phase = "ready", progress = 1f, message = "") else pack
        }
    }

    fun download(context: Context, size: String, onReady: () -> Unit = {}) {
        val wanted = if (size == SMALL) SMALL else FULL
        val app = context.applicationContext
        if (ready(app, wanted)) {
            setPack(wanted, KokoroPack("ready", 1f, ""))
            onReady()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            setPack(wanted, KokoroPack("downloading", 0f, ""))
            val archive = File(app.cacheDir, "kokoro-$wanted.tar.bz2")
            try {
                downloadTo(urls.getValue(wanted), archive) { fraction ->
                    setPack(wanted, KokoroPack("downloading", fraction, ""))
                }
                extract(archive, dir(app, wanted))
                archive.delete()
                if (!ready(app, wanted)) error("The voice arrived incomplete.")
                setPack(wanted, KokoroPack("ready", 1f, ""))
                onReady()
            } catch (_: Exception) {
                archive.delete()
                setPack(
                    wanted,
                    KokoroPack("failed", 0f, "The voice didn't finish downloading. You can try again."),
                )
            }
        }
    }

    private fun setPack(size: String, pack: KokoroPack) {
        packs.value = packs.value + (size to pack)
    }

    private fun complete(dir: File): Boolean =
        File(dir, "model.onnx").isFile &&
            File(dir, "voices.bin").isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory

    private fun downloadTo(url: String, dest: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "SparkBible")
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        if (connection.responseCode !in 200..299) error("The voice didn't download.")
        connection.inputStream.use { input ->
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    read += n
                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    }

    private fun extract(archive: File, dest: File) {
        dest.mkdirs()
        val root = dest.canonicalFile
        BZip2CompressorInputStream(archive.inputStream().buffered()).use { compressed ->
            TarArchiveInputStream(compressed).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val out = File(dest, entry.name).canonicalFile
                    if (out != root && !out.path.startsWith(root.path + File.separator)) {
                        entry = tar.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
