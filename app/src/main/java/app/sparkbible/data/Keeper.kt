package app.sparkbible.data

data class KeptCopy(
    val moments: List<MomentEntity>,
    val plants: List<PlantEntity>,
    val highlights: List<HighlightEntity>,
    val progress: ProgressEntity?,
)

object Keeper {
    const val KIND = "spark-bible-copy"

    fun encode(copy: KeptCopy): String = buildString {
        append("{\"kind\":").append(jsonString(KIND)).append(",\"version\":1")
        append(",\"moments\":[")
        copy.moments.forEachIndexed { index, it ->
            if (index > 0) append(',')
            append("{\"id\":").append(jsonString(it.id))
            append(",\"at\":").append(it.at)
            append(",\"verses\":").append(it.verses)
            append(",\"ref\":").append(jsonString(it.ref))
            append(",\"energy\":").append(jsonString(it.energy))
            append('}')
        }
        append("],\"plants\":[")
        copy.plants.forEachIndexed { index, it ->
            if (index > 0) append(',')
            append("{\"id\":").append(jsonString(it.id))
            append(",\"kind\":").append(it.kind)
            append(",\"at\":").append(it.at)
            append('}')
        }
        append("],\"highlights\":[")
        copy.highlights.forEachIndexed { index, it ->
            if (index > 0) append(',')
            append("{\"id\":").append(jsonString(it.id))
            append(",\"book\":").append(it.book)
            append(",\"chapter\":").append(it.chapter)
            append(",\"verse\":").append(it.verse)
            append(",\"text\":").append(jsonString(it.text))
            append(",\"note\":").append(jsonString(it.note))
            append(",\"created\":").append(it.created)
            append('}')
        }
        append(']')
        copy.progress?.let { row ->
            append(",\"progress\":{")
            append("\"trailBook\":").append(row.trailBook)
            append(",\"trailChapter\":").append(row.trailChapter)
            append(",\"trailVerse\":").append(row.trailVerse)
            append(",\"openBook\":").append(row.openBook)
            append(",\"openChapter\":").append(row.openChapter)
            append(",\"openVerse\":").append(row.openVerse)
            append(",\"openCount\":").append(row.openCount)
            append(",\"openEnergy\":").append(jsonString(row.openEnergy))
            append(",\"openMode\":").append(jsonString(row.openMode))
            append(",\"openOrigin\":").append(jsonString(row.openOrigin))
            append(",\"openPrompt\":").append(jsonString(row.openPrompt))
            append(",\"openLabel\":").append(jsonString(row.openLabel))
            append(",\"sparkDoneOn\":").append(jsonString(row.sparkDoneOn))
            append(",\"lastNotified\":").append(jsonString(row.lastNotified))
            append('}')
        }
        append('}')
    }

    fun decode(text: String): KeptCopy? {
        val root = Json.read(text) as? Map<*, *> ?: return null
        if (root.str("kind") != KIND) return null
        val moments = root.list("moments").mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val id = row.str("id")
            if (id.isBlank()) null else MomentEntity(id, row.long("at"), row.int("verses"), row.str("ref"), row.str("energy"))
        }
        val plants = root.list("plants").mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val id = row.str("id")
            if (id.isBlank()) null else PlantEntity(id, row.int("kind"), row.long("at"))
        }
        val highlights = root.list("highlights").mapNotNull { item ->
            val row = item as? Map<*, *> ?: return@mapNotNull null
            val id = row.str("id")
            if (id.isBlank()) null else HighlightEntity(
                id,
                row.int("book"),
                row.int("chapter"),
                row.int("verse"),
                row.str("text"),
                row.str("note"),
                row.long("created"),
            )
        }
        val progress = (root["progress"] as? Map<*, *>)?.let { row ->
            ProgressEntity(
                trailBook = row.int("trailBook", 42),
                trailChapter = row.int("trailChapter", 1),
                trailVerse = row.int("trailVerse", 1),
                openBook = row.int("openBook", -1),
                openChapter = row.int("openChapter", 1),
                openVerse = row.int("openVerse", 1),
                openCount = row.int("openCount", 0),
                openEnergy = row.str("openEnergy").ifBlank { "LOW" },
                openMode = row.str("openMode").ifBlank { "READ" },
                openOrigin = row.str("openOrigin").ifBlank { "trail" },
                openPrompt = row.str("openPrompt"),
                openLabel = row.str("openLabel"),
                sparkDoneOn = row.str("sparkDoneOn"),
                lastNotified = row.str("lastNotified"),
            )
        }
        return KeptCopy(moments, plants, highlights, progress)
    }
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

private fun Map<*, *>.str(key: String): String = this[key] as? String ?: ""

private fun Map<*, *>.int(key: String, default: Int = 0): Int = when (val value = this[key]) {
    is Long -> value.toInt()
    is Int -> value
    is Double -> value.toInt()
    else -> default
}

private fun Map<*, *>.long(key: String): Long = when (val value = this[key]) {
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    else -> 0L
}

private fun Map<*, *>.list(key: String): List<*> = (this[key] as? List<*>) ?: emptyList<Any?>()

private object Json {
    fun read(text: String): Any? = runCatching { Reader(text).value() }.getOrNull()

    private class Reader(private val source: String) {
        private var index = 0

        fun value(): Any? {
            skip()
            if (index >= source.length) error("end")
            return when (source[index]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> num()
            }
        }

        private fun obj(): Map<String, Any?> {
            index++
            val map = LinkedHashMap<String, Any?>()
            skip()
            if (take('}')) return map
            while (true) {
                skip()
                val key = str()
                skip()
                if (!take(':')) error("colon")
                map[key] = value()
                skip()
                if (take('}')) return map
                if (!take(',')) error("comma")
            }
        }

        private fun arr(): List<Any?> {
            index++
            val list = ArrayList<Any?>()
            skip()
            if (take(']')) return list
            while (true) {
                list.add(value())
                skip()
                if (take(']')) return list
                if (!take(',')) error("comma")
            }
        }

        private fun str(): String {
            if (!take('"')) error("string")
            val out = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= source.length) error("escape")
                        when (val esc = source[index++]) {
                            '"', '\\', '/' -> out.append(esc)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (index + 4 > source.length) error("unicode")
                                val hex = source.substring(index, index + 4)
                                out.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> error("escape")
                        }
                    }
                    else -> out.append(c)
                }
            }
            error("string")
        }

        private fun num(): Long {
            val start = index
            if (peek('-')) index++
            if (index >= source.length || !source[index].isDigit()) error("number")
            while (index < source.length && source[index].isDigit()) index++
            if (index < source.length && (source[index] == '.' || source[index] == 'e' || source[index] == 'E')) {
                error("number")
            }
            return source.substring(start, index).toLong()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!source.startsWith(word, index)) error(word)
            index += word.length
            return value
        }

        private fun skip() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun take(c: Char): Boolean {
            if (index < source.length && source[index] == c) {
                index++
                return true
            }
            return false
        }

        private fun peek(c: Char) = index < source.length && source[index] == c
    }
}
