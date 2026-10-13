package app.sparkbible.data

enum class Energy { LOW, MEDIUM, HIGH }

enum class Mode { READ, LISTEN, BOTH }

enum class ProgressStyle { GARDEN, CONSTELLATION }

enum class ThemePref { LIGHT, SEPIA, DARK }

data class VersePin(val book: Int, val chapter: Int, val verse: Int)

data class PassageVerse(val pin: VersePin, val text: String)

data class Spark(
    val id: String,
    val topicId: String,
    val title: String,
    val book: Int,
    val chapter: Int,
    val start: Int,
    val end: Int,
    val prompt: String,
)

data class Topic(val id: String, val name: String, val blurb: String)

data class BookInfo(
    val i: Int,
    val id: String,
    val name: String,
    val testament: String,
    val chapters: Int,
)

sealed interface Origin {
    data object Trail : Origin
    data object Daily : Origin
    data class SparkItem(val sparkId: String) : Origin
    data class Jump(val pin: VersePin) : Origin
    data class Minute(val id: String) : Origin
}

data class Place(
    val verses: List<PassageVerse>,
    val label: String,
    val energy: Energy,
    val mode: Mode,
    val origin: String,
    val prompt: String,
    val truncated: Boolean,
    val words: String = "",
)

data class Settings(
    val fontSize: Float = 20f,
    val lineHeight: Float = 1.65f,
    val theme: ThemePref = ThemePref.LIGHT,
    val bionic: Boolean = false,
    val dyslexia: Boolean = false,
    val speechRate: Float = 1f,
    val voiceEngine: String = "",
    val voiceName: String = "",
    val translation: String = "web",
    val ambient: Boolean = false,
    val walking: Boolean = false,
    val progressStyle: ProgressStyle = ProgressStyle.GARDEN,
    val weeklyAim: Int = 3,
    val reminderOn: Boolean = false,
    val reminderTime: String = "08:30",
    val seenIntro: Boolean = false,
    val scriptureKey: String = "",
)
