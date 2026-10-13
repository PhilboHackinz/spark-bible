package app.sparkbible.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class OnlineBible(private val context: Context) {
    fun loadBook(info: TranslationInfo, book: Int, key: String): List<List<String>> {
        if (key.isBlank()) error("This one needs a connection key. Add it in More.")
        val cached = File(context.filesDir, "online/${info.id}/$book.json")
        if (cached.exists() && System.currentTimeMillis() - cached.lastModified() < MONTH) {
            return ScriptureText.fromApi(cached.readText()).ifEmpty { error("Couldn't read the saved chapter.") }
        }
        val bibleId = bibleId(info, key)
        val code = BOOK_CODES.getOrNull(book) ?: error("That book isn't in this translation.")
        val body = get("/bibles/$bibleId/passages/$code?content-type=json&include-notes=false&include-titles=false", key)
        val chapters = ScriptureText.fromApi(body)
        if (chapters.isEmpty()) error("The connection didn't have that passage.")
        cached.parentFile?.mkdirs()
        cached.writeText(body)
        return chapters
    }

    private fun bibleId(info: TranslationInfo, key: String): String {
        val file = File(context.filesDir, "online/${info.id}/bible-id.txt")
        if (file.exists()) return file.readText().trim()
        val body = get("/bibles?language=eng&abbreviation=${URLEncoder.encode(info.short, "UTF-8")}", key)
        val data = JSONObject(body).optJSONArray("data") ?: error("The connection didn't have ${info.short}.")
        var found = ""
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            val abbr = row.optString("abbreviation")
            if (abbr.equals(info.short, ignoreCase = true)) {
                found = row.optString("id")
                break
            }
        }
        if (found.isBlank() && data.length() > 0) found = data.optJSONObject(0)?.optString("id").orEmpty()
        if (found.isBlank()) error("The connection didn't have ${info.short}.")
        file.parentFile?.mkdirs()
        file.writeText(found)
        return found
    }

    private fun get(path: String, key: String): String {
        val connection = (URL("$ROOT$path").openConnection() as HttpURLConnection).apply {
            setRequestProperty("api-key", key)
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code == 401 || code == 403) error("That connection key was not accepted.")
        if (code !in 200..299) error("This translation needs a connection.")
        return text
    }

    companion object {
        private const val ROOT = "https://api.scripture.api.bible/v1"
        private const val MONTH = 29L * 24L * 60L * 60L * 1000L
        private val BOOK_CODES = listOf(
            "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA",
            "1KI", "2KI", "1CH", "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO",
            "ECC", "SNG", "ISA", "JER", "LAM", "EZK", "DAN", "HOS", "JOL", "AMO",
            "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL", "MAT",
            "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP",
            "COL", "1TH", "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE",
            "2PE", "1JN", "2JN", "3JN", "JUD", "REV",
        )
    }
}
