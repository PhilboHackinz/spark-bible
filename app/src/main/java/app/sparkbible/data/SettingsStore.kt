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
    val flow: Flow<Settings> = context.sparkSettings.data.map { prefs ->
        val blob = prefs[Keys.blob]
        if (!blob.isNullOrBlank()) {
            SettingsCodec.decode(blob)
        } else {
            Settings(
                fontSize = prefs[Keys.font] ?: 20f,
                lineHeight = prefs[Keys.leading] ?: 1.65f,
                theme = runCatching { ThemePref.valueOf(prefs[Keys.theme] ?: "LIGHT") }.getOrDefault(ThemePref.LIGHT),
                bionic = prefs[Keys.bionic] ?: false,
                dyslexia = prefs[Keys.dyslexia] ?: false,
                speechRate = prefs[Keys.rate] ?: 1f,
                voiceEngine = prefs[Keys.voiceEngine] ?: "",
                voiceName = prefs[Keys.voiceName] ?: "",
                translation = prefs[Keys.translation] ?: "web",
                ambient = prefs[Keys.ambient] ?: false,
                walking = prefs[Keys.walking] ?: false,
                progressStyle = runCatching { ProgressStyle.valueOf(prefs[Keys.style] ?: "GARDEN") }.getOrDefault(ProgressStyle.GARDEN),
                weeklyAim = prefs[Keys.aim] ?: 3,
                reminderOn = prefs[Keys.remind] ?: false,
                reminderTime = prefs[Keys.remindTime] ?: "08:30",
                seenIntro = prefs[Keys.intro] ?: false,
            )
        }
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(block: (Settings) -> Settings) {
        val next = block(current())
        val stored = SettingsCodec.encode(next)
        context.sparkSettings.edit { prefs ->
            prefs.clear()
            prefs[Keys.blob] = stored
        }
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(WIDGET_TRANSLATION, next.translation)
            .apply()
    }

    private object Keys {
        val blob = stringPreferencesKey("blob")
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
