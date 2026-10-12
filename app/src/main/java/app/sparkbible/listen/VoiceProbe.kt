package app.sparkbible.listen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.util.Locale

data class OpenEngine(
    val pkg: String,
    val name: String,
    val blurb: String,
    val also: List<String> = emptyList(),
)

data class ListedVoice(
    val engine: String,
    val engineLabel: String,
    val name: String,
    val label: String,
    val accent: String,
    val detail: String,
    val openSource: Boolean,
    val local: Boolean,
    val aliases: List<String> = emptyList(),
)

data class EngineVoices(
    val pkg: String,
    val label: String,
    val openSource: Boolean,
    val voices: List<ListedVoice>,
)

data class VoiceSnapshot(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val engines: List<EngineVoices> = emptyList(),
    val offers: List<OpenOffer> = emptyList(),
)

data class OpenOffer(
    val engine: OpenEngine,
    val installed: Boolean,
    val enabled: Boolean,
)

object OpenVoices {
    val engines = listOf(
        OpenEngine(
            pkg = "com.github.olga_yakovleva.rhvoice.android",
            name = "RHVoice",
            blurb = "Open source, offline, and closer to a real person. English voices include Alan, BDL, CLB, and SLT.",
        ),
        OpenEngine(
            pkg = "com.reecedunn.espeak",
            name = "eSpeak NG",
            blurb = "Tiny open-source voices and many accents. Clear and fast, more robotic than RHVoice.",
            also = listOf("com.googlecode.eyesfree.espeak"),
        ),
        OpenEngine(
            pkg = "org.woheller69.ttsengine",
            name = "SherpaTTS",
            blurb = "Piper and Coqui neural voices, open source. Install it, download one voice there, then pick it here. Offline after that.",
        ),
    )

    fun matches(pkg: String): OpenEngine? =
        engines.firstOrNull { it.pkg == pkg || pkg in it.also }

    fun openStore(context: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://f-droid.org/packages/$pkg/"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
    }

    fun openTtsSettings(context: Context) {
        val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

class VoiceProbe(
    private val context: Context,
    private val onResult: (VoiceSnapshot) -> Unit,
) {
    private val created = ArrayList<TextToSpeech>()
    private var alive = true

    fun start() {
        lateinit var probe: TextToSpeech
        probe = TextToSpeech(context) { status ->
            if (!alive) {
                probe.shutdown()
            } else if (status != TextToSpeech.SUCCESS) {
                onResult(snapshot(emptyList()))
                probe.shutdown()
            } else {
                val infos = probe.engines.orEmpty()
                probe.shutdown()
                if (infos.isEmpty()) {
                    onResult(snapshot(emptyList()))
                } else {
                    val found = ArrayList<EngineVoices>()
                    var left = infos.size
                    infos.forEach { info ->
                        lateinit var engine: TextToSpeech
                        engine = TextToSpeech(
                            context,
                            { st ->
                                if (!alive) {
                                    engine.shutdown()
                                } else {
                                    val voices = if (st == TextToSpeech.SUCCESS) {
                                        englishVoices(engine, info.name, info.label)
                                    } else {
                                        emptyList()
                                    }
                                    synchronized(found) {
                                        found.add(
                                            EngineVoices(
                                                pkg = info.name,
                                                label = info.label,
                                                openSource = OpenVoices.matches(info.name) != null,
                                                voices = voices,
                                            ),
                                        )
                                        left -= 1
                                        if (left == 0) {
                                            onResult(snapshot(found.sortedBy { it.label.lowercase() }))
                                            stop()
                                        }
                                    }
                                }
                            },
                            info.name,
                        )
                        created.add(engine)
                    }
                }
            }
        }
        created.add(probe)
    }

    fun stop() {
        alive = false
        created.forEach { runCatching { it.shutdown() } }
        created.clear()
    }

    private fun snapshot(engines: List<EngineVoices>): VoiceSnapshot {
        val enabled = engines.map { it.pkg }.toSet()
        val offers = OpenVoices.engines.map { spec ->
            val pkgs = listOf(spec.pkg) + spec.also
            val installed = pkgs.any { installed(it) }
            OpenOffer(spec, installed = installed, enabled = pkgs.any { it in enabled })
        }
        return VoiceSnapshot(ready = true, engines = engines, offers = offers)
    }

    private fun installed(pkg: String): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
}

private fun englishVoices(tts: TextToSpeech, engine: String, engineLabel: String): List<ListedVoice> {
    val raw = tts.voices.orEmpty().mapNotNull { voice ->
        val locale = voice.locale ?: return@mapNotNull null
        if (!locale.language.equals(Locale.ENGLISH.language, true)) return@mapNotNull null
        RawVoice(
            engine = engine,
            engineLabel = shortEngine(engineLabel),
            name = voice.name,
            language = locale.language,
            country = locale.country ?: "",
            network = voice.isNetworkConnectionRequired,
            quality = voice.quality,
            openSource = OpenVoices.matches(engine) != null,
        )
    }
    return labelVoices(raw)
}

data class RawVoice(
    val engine: String,
    val engineLabel: String,
    val name: String,
    val language: String,
    val country: String,
    val network: Boolean,
    val quality: Int,
    val openSource: Boolean,
)

private data class VoiceName(val accent: String, val label: String)

private val knownVoices = mapOf(
    "en-us-x-tpc" to VoiceName("US English", "Female 1"),
    "en-us-x-iob" to VoiceName("US English", "Female 2"),
    "en-us-x-iog" to VoiceName("US English", "Female 3"),
    "en-us-x-tpf" to VoiceName("US English", "Female 4"),
    "en-us-x-sfg" to VoiceName("US English", "Female 5"),
    "en-us-x-iom" to VoiceName("US English", "Male 1"),
    "en-us-x-iol" to VoiceName("US English", "Male 2"),
    "en-us-x-tpd" to VoiceName("US English", "Male 3"),
    "en-us-language" to VoiceName("US English", "Default"),
    "en-gb-x-gba" to VoiceName("British English", "Female 1"),
    "en-gb-x-gbc" to VoiceName("British English", "Female 2"),
    "en-gb-x-gbg" to VoiceName("British English", "Female 3"),
    "en-gb-x-rjs" to VoiceName("British English", "Male 1"),
    "en-gb-x-gbb" to VoiceName("British English", "Male 2"),
    "en-gb-x-gbd" to VoiceName("British English", "Male 3"),
    "en-au-x-aua" to VoiceName("Australian English", "Female 1"),
    "en-au-x-auc" to VoiceName("Australian English", "Female 2"),
    "en-au-x-aub" to VoiceName("Australian English", "Male 1"),
    "en-au-x-aud" to VoiceName("Australian English", "Male 2"),
    "en-in-x-ena" to VoiceName("Indian English", "Female 1"),
    "en-in-x-enc" to VoiceName("Indian English", "Female 2"),
    "en-in-x-end" to VoiceName("Indian English", "Male 1"),
    "en-in-x-ene" to VoiceName("Indian English", "Male 2"),
)

fun labelVoices(raw: List<RawVoice>): List<ListedVoice> {
    val collapsed = raw.groupBy { voiceIdentity(it.name) }.map { (identity, variants) ->
        val offline = variants.filter { !it.network }
        val chosen = (offline.ifEmpty { variants }).maxBy { it.quality }
        val known = knownVoices[identity]
        val named = readableToken(identity)
        ListedVoice(
            engine = chosen.engine,
            engineLabel = chosen.engineLabel,
            name = chosen.name,
            label = known?.label ?: named ?: "Voice",
            accent = known?.accent ?: accentName(chosen.country, chosen.language),
            detail = if (!chosen.network) "On this phone" else "Needs a connection",
            openSource = chosen.openSource,
            local = !chosen.network,
            aliases = variants.map { it.name }.distinct(),
        )
    }
    return collapsed.sortedWith(
        compareBy<ListedVoice> { accentOrder(it.accent) }
            .thenBy { it.label.substringBefore(" ") }
            .thenBy { it.label.substringAfter(" ").toIntOrNull() ?: 99 }
            .thenBy { it.label },
    )
}

private fun voiceIdentity(name: String): String =
    name.lowercase(Locale.US).removeSuffix("-local").removeSuffix("-network")

private fun shortEngine(label: String): String =
    if (label.contains("Google", ignoreCase = true)) "Google" else label

private fun readableToken(identity: String): String? {
    val token = identity.substringAfterLast('-').replace('_', ' ').trim()
    val skip = token.length !in 3..24 ||
        !token.any { it.isLetter() } ||
        token.startsWith("en") ||
        token in setOf("local", "network", "eng", "english", "language", "default")
    if (skip) return null
    return token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

private fun accentName(country: String, language: String): String = when (country.uppercase(Locale.US)) {
    "US" -> "US English"
    "GB" -> "British English"
    "AU" -> "Australian English"
    "IN" -> "Indian English"
    "IE" -> "Irish English"
    "ZA" -> "South African English"
    "NG" -> "Nigerian English"
    "CA" -> "Canadian English"
    "" -> "English"
    else -> Locale(language, country).displayName
}

private fun accentOrder(accent: String): Int = when (accent) {
    "US English" -> 0
    "British English" -> 1
    "Australian English" -> 2
    "Indian English" -> 3
    else -> 4
}
