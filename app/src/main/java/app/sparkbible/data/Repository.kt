package app.sparkbible.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.util.UUID

class Repository(
    private val bible: BibleStore,
    private val db: SparkDb,
    val settings: SettingsStore,
) {
    private val dao = db.dao()

    fun moments() = dao.moments()
    fun plants() = dao.plants()
    fun highlights() = dao.highlights()
    fun progress() = dao.progress()

    suspend fun ensure() {
        if (dao.progressOnce() == null) dao.upsertProgress(ProgressEntity())
    }

    suspend fun momentsOnce() = dao.momentsOnce()

    suspend fun progressRow() = dao.progressOnce()

    suspend fun build(origin: Origin, energy: Energy, mode: Mode, trail: VersePin): Place {
        bind()
        val spark = when (origin) {
            Origin.Daily -> Logic.daily()
            is Origin.SparkItem -> Logic.spark(origin.sparkId)
            else -> null
        }
        val originKey = when (origin) {
            Origin.Trail -> "trail"
            Origin.Daily -> "daily"
            is Origin.SparkItem -> "spark"
            is Origin.Jump -> "jump"
        }
        if (spark != null) {
            val start = VersePin(spark.book, spark.chapter, spark.start)
            val count = spark.end - spark.start + 1
            val verses = Logic.take({ bible.loadBook(it) }, start, count, spill = false)
            return place(verses, energy, mode, originKey, spark.prompt, truncated = false)
        }
        val start = when (origin) {
            is Origin.Jump -> origin.pin
            else -> trail
        }
        val data = bible.loadBook(start.book)
        val safeChapter = start.chapter.coerceIn(1, data.size.coerceAtLeast(1))
        val chapterLen = data.getOrNull(safeChapter - 1)?.size ?: 1
        val verse = start.verse.coerceIn(1, chapterLen)
        val pin = VersePin(start.book, safeChapter, verse)
        val remaining = chapterLen - verse + 1
        val shape = Logic.sessionShape(energy, remaining)
        val verses = Logic.take({ bible.loadBook(it) }, pin, shape.count, shape.spill)
        return place(verses, energy, mode, originKey, Logic.genericPrompt(verses.first().pin), shape.truncated)
    }

    suspend fun setOpen(place: Place) {
        val row = dao.progressOnce() ?: ProgressEntity()
        val first = place.verses.first().pin
        dao.upsertProgress(
            row.copy(
                openBook = first.book,
                openChapter = first.chapter,
                openVerse = first.verse,
                openCount = place.verses.size,
                openEnergy = place.energy.name,
                openMode = place.mode.name,
                openOrigin = place.origin,
                openPrompt = place.prompt,
                openLabel = place.label,
            ),
        )
    }

    suspend fun reopen(): Place? {
        bind()
        val row = dao.progressOnce() ?: return null
        if (row.openBook < 0 || row.openCount <= 0) return null
        return placeFrom(row)
    }

    suspend fun complete(place: Place): Boolean {
        bind()
        val row = dao.progressOnce() ?: ProgressEntity()
        if (row.openBook < 0 && row.openLabel == place.label && place.label.isNotBlank()) return false
        record(place, row)
        return true
    }

    suspend fun completeOpen(label: String): Place? {
        bind()
        val row = dao.progressOnce() ?: return null
        if (row.openBook < 0 || row.openLabel != label) return null
        val place = placeFrom(row) ?: return null
        record(place, row)
        return place
    }

    suspend fun markNotified(day: String) {
        val row = dao.progressOnce() ?: ProgressEntity()
        dao.upsertProgress(row.copy(lastNotified = day))
    }

    suspend fun refresh(place: Place): Place {
        bind()
        if (place.verses.isEmpty()) return place
        val verses = place.verses.mapNotNull { verse ->
            val text = verseText(verse.pin) ?: return@mapNotNull null
            verse.copy(text = text)
        }
        if (verses.isEmpty()) return place
        return place.copy(
            verses = verses,
            label = Logic.label(bible.bookName(verses.first().pin.book), verses),
        )
    }

    private suspend fun bind() {
        bible.translation = settings.current().translation
    }

    private fun verseText(pin: VersePin): String? {
        val book = runCatching { bible.loadBook(pin.book) }.getOrNull() ?: return null
        val chapter = book.getOrNull(pin.chapter - 1) ?: return null
        return chapter.getOrNull(pin.verse - 1)
    }

    suspend fun toggleHighlight(verse: PassageVerse) {
        val id = idOf(verse.pin)
        if (dao.highlight(id) != null) dao.deleteHighlight(id)
        else dao.upsertHighlight(
            HighlightEntity(id, verse.pin.book, verse.pin.chapter, verse.pin.verse, verse.text, "", System.currentTimeMillis()),
        )
    }

    suspend fun note(verse: PassageVerse, note: String) {
        val id = idOf(verse.pin)
        val existing = dao.highlight(id)
        dao.upsertHighlight(
            HighlightEntity(
                id = id,
                book = verse.pin.book,
                chapter = verse.pin.chapter,
                verse = verse.pin.verse,
                text = verse.text,
                note = note,
                created = existing?.created ?: System.currentTimeMillis(),
            ),
        )
    }

    suspend fun exportJson(): String {
        val copy = KeptCopy(
            moments = dao.momentsOnce(),
            plants = dao.plantsOnce(),
            highlights = dao.highlightsOnce(),
            progress = dao.progressOnce(),
        )
        return Keeper.encode(copy)
    }

    suspend fun importJson(text: String): Boolean {
        val copy = Keeper.decode(text) ?: return false
        db.withTransaction {
            dao.clearMoments()
            dao.clearPlants()
            dao.clearHighlights()
            if (copy.moments.isNotEmpty()) dao.insertMoments(copy.moments)
            if (copy.plants.isNotEmpty()) dao.insertPlants(copy.plants)
            if (copy.highlights.isNotEmpty()) dao.insertHighlights(copy.highlights)
            copy.progress?.let { dao.upsertProgress(it.copy(id = 1)) }
        }
        return true
    }

    suspend fun deleteHighlight(id: String) = dao.deleteHighlight(id)

    private suspend fun record(place: Place, row: ProgressEntity) {
        val now = System.currentTimeMillis()
        dao.insertMoment(
            MomentEntity(UUID.randomUUID().toString(), now, place.verses.size, place.label, place.energy.name),
        )
        dao.insertPlant(PlantEntity(UUID.randomUUID().toString(), (now % 6).toInt(), now))
        val kept = Logic.progressAfter(
            origin = place.origin,
            trail = VersePin(row.trailBook, row.trailChapter, row.trailVerse),
            nextOnTrail = Logic.nextAfter({ bible.loadBook(it) }, place.verses.last().pin),
            sparkDoneOn = row.sparkDoneOn,
            today = LocalDate.now().toString(),
        )
        dao.upsertProgress(
            row.copy(
                trailBook = kept.trail.book,
                trailChapter = kept.trail.chapter,
                trailVerse = kept.trail.verse,
                openBook = -1,
                openCount = 0,
                openLabel = place.label,
                sparkDoneOn = kept.sparkDoneOn,
            ),
        )
    }

    private fun placeFrom(row: ProgressEntity): Place? {
        val energy = runCatching { Energy.valueOf(row.openEnergy) }.getOrDefault(Energy.LOW)
        val mode = runCatching { Mode.valueOf(row.openMode) }.getOrDefault(Mode.READ)
        val verses = Logic.take(
            { bible.loadBook(it) },
            VersePin(row.openBook, row.openChapter, row.openVerse),
            row.openCount,
            spill = true,
        )
        if (verses.isEmpty()) return null
        return Place(
            verses = verses,
            label = row.openLabel.ifBlank { Logic.label(bible.bookName(verses.first().pin.book), verses) },
            energy = energy,
            mode = mode,
            origin = row.openOrigin,
            prompt = row.openPrompt.ifBlank { Logic.genericPrompt(verses.first().pin) },
            truncated = false,
        )
    }

    private fun place(
        verses: List<PassageVerse>,
        energy: Energy,
        mode: Mode,
        origin: String,
        prompt: String,
        truncated: Boolean,
    ): Place {
        require(verses.isNotEmpty())
        val name = bible.bookName(verses.first().pin.book)
        return Place(verses, Logic.label(name, verses), energy, mode, origin, prompt, truncated)
    }

    private fun idOf(pin: VersePin) = "${pin.book}:${pin.chapter}:${pin.verse}"
}
