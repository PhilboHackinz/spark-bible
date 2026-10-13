package app.sparkbible.data

import org.json.JSONObject

object SettingsCodec {
    fun encode(settings: Settings): String = JSONObject().apply {
        put("fontSize", settings.fontSize.toDouble())
        put("lineHeight", settings.lineHeight.toDouble())
        put("theme", settings.theme.name)
        put("bionic", settings.bionic)
        put("dyslexia", settings.dyslexia)
        put("speechRate", settings.speechRate.toDouble())
        put("voiceEngine", settings.voiceEngine)
        put("voiceName", settings.voiceName)
        put("translation", settings.translation)
        put("ambient", settings.ambient)
        put("walking", settings.walking)
        put("progressStyle", settings.progressStyle.name)
        put("weeklyAim", settings.weeklyAim)
        put("reminderOn", settings.reminderOn)
        put("reminderTime", settings.reminderTime)
        put("seenIntro", settings.seenIntro)
        put("scriptureKey", settings.scriptureKey)
    }.toString()

    fun decode(json: String?): Settings {
        if (json.isNullOrBlank()) return Settings()
        val stored = JSONObject(json)
        return Settings(
            fontSize = stored.optDouble("fontSize", 20.0).toFloat(),
            lineHeight = stored.optDouble("lineHeight", 1.65).toFloat(),
            theme = runCatching { ThemePref.valueOf(stored.optString("theme", "LIGHT")) }.getOrDefault(ThemePref.LIGHT),
            bionic = stored.optBoolean("bionic", false),
            dyslexia = stored.optBoolean("dyslexia", false),
            speechRate = stored.optDouble("speechRate", 1.0).toFloat(),
            voiceEngine = stored.optString("voiceEngine", ""),
            voiceName = stored.optString("voiceName", ""),
            translation = stored.optString("translation", "web").ifBlank { "web" },
            ambient = stored.optBoolean("ambient", false),
            walking = stored.optBoolean("walking", false),
            progressStyle = runCatching {
                ProgressStyle.valueOf(stored.optString("progressStyle", "GARDEN"))
            }.getOrDefault(ProgressStyle.GARDEN),
            weeklyAim = stored.optInt("weeklyAim", 3),
            reminderOn = stored.optBoolean("reminderOn", false),
            reminderTime = stored.optString("reminderTime", "08:30").ifBlank { "08:30" },
            seenIntro = stored.optBoolean("seenIntro", false),
            scriptureKey = stored.optString("scriptureKey", ""),
        )
    }
}
