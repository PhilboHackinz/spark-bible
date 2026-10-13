package app.sparkbible.data

import org.json.JSONArray
import org.json.JSONObject

object ScriptureText {
    fun fromApi(json: String): List<List<String>> {
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: root
        val content = data.opt("content")
        val chapters = ArrayList<MutableList<String>>()
        fun chapter(number: Int): MutableList<String> {
            while (chapters.size < number) chapters.add(ArrayList())
            return chapters[number - 1]
        }
        when (content) {
            is String -> parseMarked(content, ::chapter)
            is JSONArray -> walk(content, 1, ::chapter)
            is JSONObject -> walk(JSONArray().put(content), 1, ::chapter)
        }
        return chapters.filter { it.isNotEmpty() }
    }

    private fun parseMarked(text: String, chapter: (Int) -> MutableList<String>) {
        var current = 1
        val pattern = Regex("\\[(\\d+)\\]")
        val marks = pattern.findAll(text).toList()
        if (marks.isEmpty()) {
            val line = text.trim()
            if (line.isNotEmpty()) chapter(1).add(line)
            return
        }
        marks.forEachIndexed { index, mark ->
            val start = mark.range.last + 1
            val end = marks.getOrNull(index + 1)?.range?.first ?: text.length
            val body = text.substring(start, end).trim()
            if (body.isEmpty()) return@forEachIndexed
            chapter(current).add(body)
        }
    }

    private fun walk(node: Any?, chapterNumber: Int, chapter: (Int) -> MutableList<String>): Int {
        var current = chapterNumber
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) current = walk(node.opt(i), current, chapter)
            }
            is JSONObject -> {
                val type = node.optString("type")
                val number = node.optString("number").ifBlank { node.optString("verse") }
                if (type == "chapter" && number.toIntOrNull() != null) {
                    current = number.toInt()
                } else if ((type == "verse" || node.has("verse")) && number.isNotBlank()) {
                    val text = verseText(node)
                    if (text.isNotBlank()) chapter(current).add(text)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "type" || key == "number" || key == "verse" || key == "text") continue
                    current = walk(node.opt(key), current, chapter)
                }
            }
        }
        return current
    }

    private fun verseText(node: JSONObject): String {
        val direct = node.optString("text")
        if (direct.isNotBlank()) return direct.trim()
        val content = node.opt("content")
        return when (content) {
            is String -> content.trim()
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    val item = content.opt(i)
                    if (item is String) append(item)
                    else if (item is JSONObject) append(verseText(item))
                }
            }.trim()
            else -> ""
        }
    }
}
