package app.sparkbible.listen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
    val openSource: Boolean,
    val local: Boolean,
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
    val voices = tts.voices.orEmpty().filter { voice ->
        val lang = voice.locale?.language ?: return@filter false
        lang.equals(Locale.ENGLISH.language, true)
    }
    return voices
        .sortedWith(compareByDescending<Voice> { !it.isNetworkConnectionRequired }.thenByDescending { it.quality })
        .map { voice ->
            val local = !voice.isNetworkConnectionRequired
            ListedVoice(
                engine = engine,
                engineLabel = engineLabel,
                name = voice.name,
                label = prettyVoice(voice, local),
                openSource = OpenVoices.matches(engine) != null,
                local = local,
            )
        }
}

private fun prettyVoice(voice: Voice, local: Boolean): String {
    val locale = voice.locale?.displayName ?: "English"
    val raw = voice.name.substringAfterLast('-').substringBefore('#').replace('_', ' ').trim()
    val titled = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    val where = if (local) "Stays on this phone" else "Needs a connection"
    val name = if (titled.length in 3..28 && titled.any { it.isLetter() } && !titled.startsWith("en", true)) titled else locale
    return "$name · $where"
}
