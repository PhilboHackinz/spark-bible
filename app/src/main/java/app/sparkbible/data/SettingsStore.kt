package app.sparkbible.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sparkSettings by preferencesDataStore("spark-settings")

class SettingsStore(private val context: Context) {
    val flow: Flow<Settings> = context.sparkSettings.data.map { p ->
        Settings(
            fontSize = p[Keys.font] ?: 20f,
            lineHeight = p[Keys.leading] ?: 1.65f,
            theme = runCatching { ThemePref.valueOf(p[Keys.theme] ?: "LIGHT") }.getOrDefault(ThemePref.LIGHT),
            bionic = p[Keys.bionic] ?: false,
            dyslexia = p[Keys.dyslexia] ?: false,
            speechRate = p[Keys.rate] ?: 1f,
            voiceEngine = p[Keys.voiceEngine] ?: "",
            voiceName = p[Keys.voiceName] ?: "",
            translation = p[Keys.translation] ?: "web",
            ambient = p[Keys.ambient] ?: false,
            walking = p[Keys.walking] ?: false,
            progressStyle = runCatching {
                ProgressStyle.valueOf(p[Keys.style] ?: "GARDEN")
            }.getOrDefault(ProgressStyle.GARDEN),
            weeklyAim = p[Keys.aim] ?: 3,
            reminderOn = p[Keys.remind] ?: false,
            reminderTime = p[Keys.remindTime] ?: "08:30",
            seenIntro = p[Keys.intro] ?: false,
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(block: (Settings) -> Settings) {
        val next = block(current())
        context.sparkSettings.edit { p ->
            p[Keys.font] = next.fontSize
            p[Keys.leading] = next.lineHeight
            p[Keys.theme] = next.theme.name
            p[Keys.bionic] = next.bionic
            p[Keys.dyslexia] = next.dyslexia
            p[Keys.rate] = next.speechRate
            p[Keys.voiceEngine] = next.voiceEngine
            p[Keys.voiceName] = next.voiceName
            p[Keys.translation] = next.translation
            p[Keys.ambient] = next.ambient
            p[Keys.walking] = next.walking
            p[Keys.style] = next.progressStyle.name
            p[Keys.aim] = next.weeklyAim
            p[Keys.remind] = next.reminderOn
            p[Keys.remindTime] = next.reminderTime
            p[Keys.intro] = next.seenIntro
        }
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(WIDGET_TRANSLATION, next.translation)
            .apply()
    }

    private object Keys {
        val font = floatPreferencesKey("font")
        val leading = floatPreferencesKey("leading")
        val theme = stringPreferencesKey("theme")
        val bionic = booleanPreferencesKey("bionic")
        val dyslexia = booleanPreferencesKey("dyslexia")
        val rate = floatPreferencesKey("rate")
        val ambient = booleanPreferencesKey("ambient")
        val walking = booleanPreferencesKey("walking")
        val style = stringPreferencesKey("style")
        val aim = intPreferencesKey("aim")
        val remind = booleanPreferencesKey("remind")
        val remindTime = stringPreferencesKey("remindTime")
        val intro = booleanPreferencesKey("intro")
        val voiceEngine = stringPreferencesKey("voiceEngine")
        val voiceName = stringPreferencesKey("voiceName")
        val translation = stringPreferencesKey("translation")
    }

    companion object {
        const val WIDGET_PREFS = "spark-widget"
        const val WIDGET_TRANSLATION = "translation"
    }
}
