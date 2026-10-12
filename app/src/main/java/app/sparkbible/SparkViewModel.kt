package app.sparkbible

import android.app.Application
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

    val settings = spark.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val moments = spark.repo.moments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val plants = spark.repo.plants().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val highlights = spark.repo.highlights().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val progress = spark.repo.progress()
        .map { it ?: ProgressEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressEntity())
    val books = MutableStateFlow(emptyList<app.sparkbible.data.BookInfo>())

    private var remindKey = ""

    init {
        viewModelScope.launch(Dispatchers.IO) {
            spark.repo.ensure()
            val current = spark.settings.current()
            spark.bible.translation = current.translation
            books.value = spark.bible.index()
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
            } catch (_: Exception) {
                error.value = "Couldn't open that passage. Try again in a moment."
            } finally {
                busy.value = false
            }
        }
    }

    fun openDaily() = openSetup(Energy.LOW, Origin.Daily)

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
            } catch (_: Exception) {
                error.value = "Couldn't resize that passage."
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
            ListenService.stop(spark)
            spark.repo.complete(place)
            showReflect(place)
        }
    }

    fun showReflect(place: Place) {
        if (_stack.value.lastOrNull() is Screen.Reflect) return
        _stack.value = listOf(Screen.Today, Screen.Reflect(place, app.sparkbible.data.Logic.wins.random()))
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
            spark.settings.update { it.copy(translation = id) }
            spark.bible.translation = id
            books.value = spark.bible.index()
            ListenService.stop(spark)
            val last = _stack.value.lastOrNull()
            if (last is Screen.Read) {
                val place = withContext(Dispatchers.IO) { spark.repo.refresh(last.place) }
                _stack.value = _stack.value.dropLast(1) + Screen.Read(place)
            }
            val open = draft.value
            if (open != null) {
                draft.value = withContext(Dispatchers.IO) { spark.repo.refresh(open) }
            }
            SparkWidget.refresh(spark)
        }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        viewModelScope.launch { spark.settings.update(block) }
    }
}
