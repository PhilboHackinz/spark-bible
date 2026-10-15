package app.sparkbible

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sparkbible.data.Energy
import app.sparkbible.data.Mode
import app.sparkbible.data.Origin
import app.sparkbible.data.PassageVerse
import app.sparkbible.data.Place
import app.sparkbible.data.ProgressEntity
import app.sparkbible.data.Settings
import app.sparkbible.data.VersePin
import app.sparkbible.data.Logic
import app.sparkbible.listen.KokoroFiles
import app.sparkbible.listen.ListenBus
import app.sparkbible.listen.ListenService
import app.sparkbible.remind.Reminder
import app.sparkbible.widget.SparkWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Today : Screen
    data object Sparks : Screen
    data object Garden : Screen
    data object More : Screen
    data object Library : Screen
    data class Chapters(val book: Int) : Screen
    data object Saved : Screen
    data object Settings : Screen
    data object Voices : Screen
    data object Minutes : Screen
    data class Topic(val topicId: String) : Screen
    data class Setup(val energy: Energy, val origin: Origin) : Screen
    data class Read(val place: Place) : Screen
    data class Reflect(val place: Place, val line: String) : Screen
}

class SparkViewModel(app: Application) : AndroidViewModel(app) {
    private val spark = app as SparkApp
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Today))
    val stack: StateFlow<List<Screen>> = _stack
    val draft = MutableStateFlow<Place?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val copyNote = MutableStateFlow("")

    val settings = spark.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val moments = spark.repo.moments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plants = spark.repo.plants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val highlights = spark.repo.highlights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = spark.repo.progress()
        .map { it ?: ProgressEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressEntity())
    val books = MutableStateFlow(emptyList<app.sparkbible.data.BookInfo>())
    val dailyWords = MutableStateFlow("")
    val devotionWords = MutableStateFlow("")

    private var remindKey = ""

    init {
        viewModelScope.launch(Dispatchers.IO) {
            spark.repo.ensure()
            val current = spark.settings.current()
            spark.bible.translation = current.translation
            books.value = spark.bible.index()
            refreshDailyWords()
            SparkWidget.refresh(spark)
        }
        viewModelScope.launch {
            spark.settings.flow.collect { value ->
                val key = "${value.reminderOn}:${value.reminderTime}"
                if (key == remindKey) return@collect
                remindKey = key
                if (value.reminderOn) Reminder.schedule(spark, value.reminderTime) else Reminder.cancel(spark)
            }
        }
    }

    fun showDevotion(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val devotion = app.sparkbible.data.Devotions.get(id) ?: return@launch
            val current = spark.settings.current()
            spark.bible.translation = current.translation
            val text = runCatching {
                val chapter = spark.bible.loadBook(devotion.book).getOrNull(devotion.chapter - 1) ?: return@runCatching ""
                (devotion.start..devotion.end).mapNotNull { verse -> chapter.getOrNull(verse - 1) }.joinToString("\n")
            }.getOrDefault("")
            devotionWords.value = text
        }
    }

    private fun note(caught: Exception, fallback: String) {
        error.value = (caught as? IllegalStateException)?.message ?: fallback
    }

    private fun refreshDailyWords() {
        viewModelScope.launch(Dispatchers.IO) {
            val sparkItem = Logic.daily()
            val text = runCatching {
                val chapter = spark.bible.loadBook(sparkItem.book).getOrNull(sparkItem.chapter - 1) ?: return@runCatching ""
                (sparkItem.start..sparkItem.end).mapNotNull { verse -> chapter.getOrNull(verse - 1) }.joinToString("\n")
            }.getOrDefault("")
            dailyWords.value = text
        }
    }

    fun tab(screen: Screen) {
        _stack.value = listOf(screen)
    }

    fun push(screen: Screen) {
        _stack.value = _stack.value + screen
    }

    fun back() {
        if (_stack.value.size > 1) _stack.value = _stack.value.dropLast(1)
    }

    fun home() {
        _stack.value = listOf(Screen.Today)
    }

    fun openSetup(energy: Energy, origin: Origin) {
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                val trail = progress.value.let { VersePin(it.trailBook, it.trailChapter, it.trailVerse) }
                val place = withContext(Dispatchers.IO) { spark.repo.build(origin, energy, Mode.READ, trail) }
                draft.value = place
                push(Screen.Setup(energy, origin))
            } catch (e: Exception) {
                note(e, "Couldn't open that passage. Try again in a moment.")
            } finally {
                busy.value = false
            }
        }
    }

    fun openDaily() = openSetup(Energy.LOW, Origin.Daily)

    fun openDailyRead() {
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                val trail = progress.value.let { VersePin(it.trailBook, it.trailChapter, it.trailVerse) }
                val place = withContext(Dispatchers.IO) { spark.repo.build(Origin.Daily, Energy.LOW, Mode.READ, trail) }
                withContext(Dispatchers.IO) { spark.repo.setOpen(place) }
                _stack.value = listOf(Screen.Today, Screen.Read(place))
            } catch (e: Exception) {
                note(e, "Couldn't open today's spark.")
            } finally {
                busy.value = false
            }
        }
    }

    fun setEnergy(energy: Energy) {
        val setup = _stack.value.lastOrNull() as? Screen.Setup ?: return
        if (setup.energy == energy) return
        viewModelScope.launch {
            busy.value = true
            try {
                val trail = progress.value.let { VersePin(it.trailBook, it.trailChapter, it.trailVerse) }
                val place = withContext(Dispatchers.IO) {
                    spark.repo.build(setup.origin, energy, draft.value?.mode ?: Mode.READ, trail)
                }
                draft.value = place
                _stack.value = _stack.value.dropLast(1) + Screen.Setup(energy, setup.origin)
            } catch (e: Exception) {
                note(e, "Couldn't resize that passage.")
            } finally {
                busy.value = false
            }
        }
    }

    fun begin(mode: Mode) {
        val place = draft.value?.copy(mode = mode) ?: return
        viewModelScope.launch {
            spark.repo.setOpen(place)
            draft.value = place
            push(Screen.Read(place))
        }
    }

    fun resume() {
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { spark.repo.reopen() } ?: return@launch
            push(Screen.Read(place))
        }
    }

    fun finish(place: Place) {
        viewModelScope.launch {
            val planted = spark.repo.complete(place)
            ListenService.stop(spark)
            if (planted) gentleTap()
            showReflect(place)
        }
    }

    fun showReflect(place: Place) {
        if (_stack.value.lastOrNull() is Screen.Reflect) return
        val line = Logic.keptLine(settings.value.progressStyle)
        _stack.value = listOf(Screen.Today, Screen.Reflect(place, line))
    }

    fun extendReading() {
        val last = _stack.value.lastOrNull() as? Screen.Read ?: return
        if (Logic.lengthLocked(last.place.origin)) return
        viewModelScope.launch {
            val before = last.place
            val place = withContext(Dispatchers.IO) { spark.repo.extend(before) }
            _stack.value = _stack.value.dropLast(1) + Screen.Read(place)
            val oldLines = Logic.spokenLines(before)
            val newLines = Logic.spokenLines(place)
            val resumeAt = Logic.extendListen(
                sameSession = ListenBus.title.value == before.label,
                playing = ListenBus.playing.value,
                spokenIndex = ListenBus.verse.value,
                oldLineCount = oldLines.size,
                firstNewIndex = before.verses.size,
                newLineCount = newLines.size,
            )
            if (resumeAt != null && newLines.isNotEmpty()) {
                ListenBus.title.value = place.label
                ListenService.extend(spark, newLines, place.label, resumeAt)
            }
        }
    }

    fun toggleHighlight(verse: PassageVerse) {
        viewModelScope.launch(Dispatchers.IO) { spark.repo.toggleHighlight(verse) }
    }

    fun saveNote(verse: PassageVerse, note: String) {
        viewModelScope.launch(Dispatchers.IO) { spark.repo.note(verse, note) }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch(Dispatchers.IO) { spark.repo.deleteHighlight(id) }
    }

    fun setTranslation(id: String) {
        viewModelScope.launch {
            val wasPlaying = ListenBus.playing.value
            val index = ListenBus.verse.value
            spark.settings.update { it.copy(translation = id) }
            spark.bible.translation = id
            books.value = spark.bible.index()
            refreshDailyWords()
            val last = _stack.value.lastOrNull()
            if (last is Screen.Read) {
                val place = withContext(Dispatchers.IO) { spark.repo.refresh(last.place) }
                _stack.value = _stack.value.dropLast(1) + Screen.Read(place)
                val current = spark.settings.current()
                val lines = Logic.spokenLines(place)
                if (wasPlaying && lines.isNotEmpty()) {
                    ListenService.play(
                        spark,
                        lines,
                        index.coerceIn(0, lines.lastIndex),
                        current.speechRate,
                        current.walking,
                        place.label,
                        current.voiceEngine,
                        current.voiceName,
                        0,
                        current.kokoroSize,
                        current.kokoroRun,
                    )
                } else {
                    ListenService.stop(spark)
                }
            } else {
                ListenService.stop(spark)
            }
            val open = draft.value
            if (open != null) {
                draft.value = withContext(Dispatchers.IO) { spark.repo.refresh(open) }
            }
            SparkWidget.refresh(spark)
        }
    }

    fun saveCopy(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val json = spark.repo.exportJson()
                spark.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
            }.getOrDefault(false)
            copyNote.value = if (ok) {
                "Saved. Your garden, highlights, and the way you read are in that file."
            } else {
                "Couldn't write that file."
            }
        }
    }

    fun bringCopyBack(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val text = spark.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                text != null && spark.repo.importJson(text)
            }.getOrDefault(false)
            if (ok) {
                val current = spark.settings.current()
                spark.bible.translation = current.translation
                books.value = spark.bible.index()
                SparkWidget.refresh(spark)
            }
            copyNote.value = if (ok) "Brought back. Your garden is here again." else "That file wasn't a Spark Bible copy."
        }
    }

    private fun gentleTap() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (spark.getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            spark.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(18, 40))
    }

    fun chooseKokoroSize(size: String) {
        val wanted = if (size == KokoroFiles.SMALL) KokoroFiles.SMALL else KokoroFiles.FULL
        if (!KokoroFiles.ready(spark, wanted)) {
            KokoroFiles.download(spark, wanted) { chooseKokoroSize(wanted) }
            return
        }
        viewModelScope.launch {
            spark.settings.update {
                it.copy(kokoroSize = wanted, voiceEngine = KokoroFiles.ENGINE, voiceName = "Kokoro")
            }
            restartListening()
        }
    }

    fun chooseKokoroRun(run: String) {
        val wanted = if (run == KokoroFiles.NNAPI && KokoroFiles.chipAvailable()) KokoroFiles.NNAPI else KokoroFiles.CPU
        viewModelScope.launch {
            spark.settings.update { it.copy(kokoroRun = wanted) }
            if (spark.settings.current().voiceEngine == KokoroFiles.ENGINE) restartListening()
        }
    }

    private suspend fun restartListening() {
        val current = spark.settings.current()
        if (!ListenBus.playing.value) return
        val place = (_stack.value.lastOrNull() as? Screen.Read)?.place
        val lines = when {
            place != null && ListenBus.title.value == place.label -> Logic.spokenLines(place)
            ListenBus.title.value == "Voice sample" -> listOf("Peace I leave with you. My peace I give to you.")
            else -> emptyList()
        }
        if (lines.isEmpty()) {
            if (current.voiceEngine == KokoroFiles.ENGINE) {
                ListenService.reload(spark, current.kokoroSize, current.kokoroRun)
            }
            return
        }
        ListenService.play(
            spark,
            lines,
            ListenBus.verse.value.coerceIn(0, lines.lastIndex),
            current.speechRate,
            current.walking && ListenBus.title.value != "Voice sample",
            ListenBus.title.value.ifBlank { place?.label ?: "Spark Bible" },
            current.voiceEngine,
            current.voiceName,
            0,
            current.kokoroSize,
            current.kokoroRun,
        )
    }

    fun updateSettings(block: (Settings) -> Settings) {
        viewModelScope.launch { spark.settings.update(block) }
    }
}
