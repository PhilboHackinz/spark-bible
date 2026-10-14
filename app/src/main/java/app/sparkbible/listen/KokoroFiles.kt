package app.sparkbible.listen

import android.content.Context
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

object KokoroFiles {
    const val ENGINE = "kokoro"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2"

    val phase = MutableStateFlow("idle")
    val progress = MutableStateFlow(0f)
    val message = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun dir(context: Context): File = File(context.filesDir, "kokoro")

    fun root(context: Context): File? {
        val dir = dir(context)
        val direct = if (complete(dir)) dir else null
        if (direct != null) return direct
        val found = dir.walkTopDown().firstOrNull { it.name == "model.onnx" }?.parentFile ?: return null
        return if (complete(found)) found else null
    }

    fun ready(context: Context): Boolean = root(context) != null

    fun refresh(context: Context) {
        if (ready(context)) phase.value = "ready"
    }

    fun download(context: Context) {
        val app = context.applicationContext
        if (ready(app)) {
            phase.value = "ready"
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            phase.value = "downloading"
            progress.value = 0f
            message.value = ""
            val archive = File(app.cacheDir, "kokoro.tar.bz2")
            try {
                downloadTo(archive)
                extract(archive, dir(app))
                archive.delete()
                if (!ready(app)) error("The voice arrived incomplete.")
                phase.value = "ready"
                progress.value = 1f
            } catch (_: Exception) {
                archive.delete()
                phase.value = "failed"
                message.value = "The voice didn't finish downloading. You can try again."
            }
        }
    }

    private fun complete(dir: File): Boolean =
        File(dir, "model.onnx").isFile &&
            File(dir, "voices.bin").isFile &&
            File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory

    private fun downloadTo(dest: File) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
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
                    if (total > 0) progress.value = (read.toFloat() / total).coerceIn(0f, 1f)
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
