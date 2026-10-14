package app.sparkbible.data

import java.time.LocalDate

data class SessionShape(val count: Int, val spill: Boolean, val truncated: Boolean)

data class KeptProgress(val trail: VersePin, val sparkDoneOn: String)

object Logic {
    val wins = listOf(
        "That counted.",
        "You showed up. That's the practice.",
        "Small is still real.",
        "Leaving when you're done is a skill.",
        "No catching up required.",
    )

    private val genericPrompts = listOf(
        "What one word here do you want to keep for the next hour?",
        "If this was written to you today, what's the smallest way it could matter?",
        "You don't have to apply all of it. What's one gentle takeaway?",
        "Where does this meet real life — a person, a task, or your own head?",
        "What would it look like to take this slowly, not perfectly?",
    )

    private val aliases = mapOf(
        "ps" to "Psalms",
        "psalm" to "Psalms",
        "psalms" to "Psalms",
        "song of songs" to "Song of Solomon",
        "canticles" to "Song of Solomon",
        "jn" to "John",
        "jhn" to "John",
        "gen" to "Genesis",
        "ex" to "Exodus",
        "exo" to "Exodus",
        "lev" to "Leviticus",
        "num" to "Numbers",
        "deut" to "Deuteronomy",
        "josh" to "Joshua",
        "judg" to "Judges",
        "1 sam" to "1 Samuel",
        "2 sam" to "2 Samuel",
        "1 kgs" to "1 Kings",
        "2 kgs" to "2 Kings",
        "1 chr" to "1 Chronicles",
        "2 chr" to "2 Chronicles",
        "neh" to "Nehemiah",
        "esth" to "Esther",
        "eccl" to "Ecclesiastes",
        "song" to "Song of Solomon",
        "isa" to "Isaiah",
        "jer" to "Jeremiah",
        "lam" to "Lamentations",
        "ezek" to "Ezekiel",
        "dan" to "Daniel",
        "hos" to "Hosea",
        "joel" to "Joel",
        "amos" to "Amos",
        "obad" to "Obadiah",
        "jonah" to "Jonah",
        "mic" to "Micah",
        "nah" to "Nahum",
        "hab" to "Habakkuk",
        "zeph" to "Zephaniah",
        "hag" to "Haggai",
        "zech" to "Zechariah",
        "mal" to "Malachi",
        "matt" to "Matthew",
        "mt" to "Matthew",
        "mk" to "Mark",
        "lk" to "Luke",
        "acts" to "Acts",
        "rom" to "Romans",
        "1 cor" to "1 Corinthians",
        "2 cor" to "2 Corinthians",
        "gal" to "Galatians",
        "eph" to "Ephesians",
        "phil" to "Philippians",
        "php" to "Philippians",
        "col" to "Colossians",
        "1 thess" to "1 Thessalonians",
        "2 thess" to "2 Thessalonians",
        "1 tim" to "1 Timothy",
        "2 tim" to "2 Timothy",
        "tit" to "Titus",
        "phlm" to "Philemon",
        "heb" to "Hebrews",
        "jas" to "James",
        "1 pet" to "1 Peter",
        "2 pet" to "2 Peter",
        "1 jn" to "1 John",
        "2 jn" to "2 John",
        "3 jn" to "3 John",
        "1jn" to "1 John",
        "2jn" to "2 John",
        "3jn" to "3 John",
        "rev" to "Revelation",
    )

    fun samePassage(openBook: Int, openChapter: Int, openVerse: Int, spark: Spark): Boolean =
        openBook >= 0 && openBook == spark.book && openChapter == spark.chapter && openVerse == spark.start

    fun plan(): List<Int> {
        val nt = (39..65).toList()
        val john = nt.indexOf(42)
        return nt.drop(john) + nt.take(john) + (0..38).toList()
    }

    fun daily(date: LocalDate = LocalDate.now()): Spark {
        val ids = SparkCatalog.dailyIds
        val id = ids[Math.floorMod(date.dayOfYear, ids.size)]
        return SparkCatalog.sparks.first { it.id == id }
    }

    fun spark(id: String): Spark? = SparkCatalog.sparks.find { it.id == id }

    fun locksLength(origin: Origin): Boolean =
        origin is Origin.Daily || origin is Origin.SparkItem || origin is Origin.Minute

    fun lengthLocked(origin: String): Boolean =
        origin == "daily" || origin == "spark" || origin == "minute"

    fun keptLine(style: ProgressStyle): String = when (style) {
        ProgressStyle.GARDEN -> "A new sprout is in your garden. It will not wilt."
        ProgressStyle.CONSTELLATION -> "A new star is in your sky. It will stay."
    }

    fun spokenLines(place: Place): List<String> {
        val lines = place.verses.map { it.text }
        return if (place.words.isBlank()) lines else lines + place.words
    }

    fun greeting(hour: Int): String = when (hour) {
        in 0..4 -> "Still up? That's okay."
        in 5..11 -> "Good morning."
        in 12..16 -> "Good afternoon."
        in 17..21 -> "Good evening."
        else -> "Good night."
    }

    fun supportLine(date: LocalDate = LocalDate.now()): String {
        val lines = listOf(
            "One small spark is enough.",
            "You don't have to catch up.",
            "Any amount counts.",
            "Restarts are normal.",
            "Leaving early still counts.",
        )
        return lines[Math.floorMod(date.dayOfYear, lines.size)]
    }

    fun genericPrompt(pin: VersePin): String =
        genericPrompts[Math.floorMod(pin.book * 3 + pin.chapter * 5 + pin.verse, genericPrompts.size)]

    fun label(bookName: String, verses: List<PassageVerse>): String {
        if (verses.isEmpty()) return bookName
        val a = verses.first().pin
        val b = verses.last().pin
        return if (a.chapter == b.chapter) {
            if (a.verse == b.verse) "$bookName ${a.chapter}:${a.verse}"
            else "$bookName ${a.chapter}:${a.verse}–${b.verse}"
        } else {
            "$bookName ${a.chapter}:${a.verse}–${b.chapter}:${b.verse}"
        }
    }

    fun sessionShape(energy: Energy, remainingInChapter: Int): SessionShape = when (energy) {
        Energy.LOW -> SessionShape(1, spill = false, truncated = false)
        Energy.MEDIUM -> SessionShape(8, spill = true, truncated = false)
        Energy.HIGH -> SessionShape(
            remainingInChapter.coerceAtMost(24),
            spill = false,
            truncated = remainingInChapter > 24,
        )
    }

    fun plantsAfterShowingUp(existing: Int): Int = existing + 1

    fun progressAfter(
        origin: String,
        trail: VersePin,
        nextOnTrail: VersePin,
        sparkDoneOn: String,
        today: String,
    ): KeptProgress = KeptProgress(
        trail = if (origin == "trail") nextOnTrail else trail,
        sparkDoneOn = if (origin == "daily") today else sparkDoneOn,
    )

    fun isLastVerse(loadBook: (Int) -> List<List<String>>, pin: VersePin): Boolean {
        val data = loadBook(pin.book)
        if (data.isEmpty()) return false
        return pin.chapter == data.size && pin.verse == data.last().size
    }

    fun rememberBooks(existing: String, books: Set<Int>): String {
        val kept = existing.split(',').mapNotNull { it.toIntOrNull() }.toMutableSet()
        kept.addAll(books)
        return kept.sorted().joinToString(",")
    }

    fun weekLine(count: Int, aim: Int): String = when {
        count == 0 -> "Nothing yet this week. That's a fine place to start."
        count < aim -> "$count of $aim — a soft aim, not a debt."
        count == aim -> "$count moments. You met a soft aim. Extra is optional."
        else -> "$count moments. More than the aim, and the aim was never a test."
    }

    fun take(loadBook: (Int) -> List<List<String>>, start: VersePin, count: Int, spill: Boolean): List<PassageVerse> {
        val order = plan()
        var book = start.book
        var chapter = start.chapter
        var verse = start.verse
        val out = ArrayList<PassageVerse>()
        var guard = 0
        while (out.size < count && guard < 4000) {
            guard++
            val data = loadBook(book)
            if (chapter > data.size) {
                if (!spill || out.isNotEmpty() && !spill) break
                if (!spill) break
                book = nextBook(book, order)
                chapter = 1
                verse = 1
                continue
            }
            if (chapter < 1) chapter = 1
            val ch = data[chapter - 1]
            if (verse > ch.size) {
                if (!spill && out.isNotEmpty()) break
                chapter += 1
                verse = 1
                continue
            }
            if (verse < 1) verse = 1
            out.add(PassageVerse(VersePin(book, chapter, verse), ch[verse - 1]))
            verse += 1
        }
        return out
    }

    fun nextAfter(loadBook: (Int) -> List<List<String>>, last: VersePin): VersePin {
        val data = loadBook(last.book)
        val len = data.getOrNull(last.chapter - 1)?.size ?: 1
        if (last.verse < len) return last.copy(verse = last.verse + 1)
        if (last.chapter < data.size) return VersePin(last.book, last.chapter + 1, 1)
        return VersePin(nextBook(last.book, plan()), 1, 1)
    }

    fun parse(raw: String, books: List<BookInfo>): VersePin? {
        if (raw.isBlank()) return null
        val normalized = raw.trim().lowercase()
            .replace(".", "")
            .replace(Regex("(\\d)([a-z])"), "$1 $2")
            .replace(Regex("([a-z])(\\d)"), "$1 $2")
            .replace(Regex("\\s+"), " ")
        val keys = (books.map { it.name.lowercase() } + aliases.keys)
            .distinct()
            .sortedByDescending { it.length }
        for (key in keys) {
            if (normalized == key || normalized.startsWith("$key ")) {
                val canonical = aliases[key] ?: books.first { it.name.equals(key, true) }.name
                val book = books.firstOrNull { it.name.equals(canonical, true) } ?: return null
                val rest = normalized.removePrefix(key).trim()
                if (rest.isEmpty()) return VersePin(book.i, 1, 1)
                val match = Regex("^(\\d+)(?::(\\d+))?").find(rest) ?: return VersePin(book.i, 1, 1)
                val chapter = match.groupValues[1].toInt()
                val verse = match.groupValues[2].toIntOrNull() ?: 1
                if (chapter !in 1..book.chapters) return null
                return VersePin(book.i, chapter, verse.coerceAtLeast(1))
            }
        }
        return null
    }

    private fun nextBook(book: Int, order: List<Int>): Int {
        val idx = order.indexOf(book)
        if (idx < 0) return order.first()
        return order[(idx + 1) % order.size]
    }
}
