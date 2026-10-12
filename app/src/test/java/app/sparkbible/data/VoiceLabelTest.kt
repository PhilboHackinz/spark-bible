package app.sparkbible.data

import app.sparkbible.listen.RawVoice
import app.sparkbible.listen.labelVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLabelTest {
    @Test
    fun networkAndOfflineCopiesCollapseToOneNamedVoice() {
        val voices = labelVoices(
            listOf(
                raw("en-us-x-tpc-network", network = true),
                raw("en-us-x-tpc-local", network = false),
                raw("en-us-x-iom-network", network = true),
            ),
        )
        assertEquals(listOf("Female 1", "Male 1"), voices.map { it.label })
        assertEquals(listOf("US English", "US English"), voices.map { it.accent })
        assertTrue(voices[0].local)
        assertEquals("On this phone", voices[0].detail)
        assertTrue(voices[0].aliases.contains("en-us-x-tpc-network"))
        assertEquals("Needs a connection", voices[1].detail)
        assertTrue(voices.none { it.label.equals("Network", true) })
    }

    @Test
    fun openSourceNamesStayReadable() {
        val voices = labelVoices(
            listOf(raw("en-US-alan", network = false, engine = "rhvoice", label = "RHVoice")),
        )
        assertEquals("Alan", voices.single().label)
        assertEquals("US English", voices.single().accent)
    }

    private fun raw(
        name: String,
        network: Boolean,
        engine: String = "com.google.android.tts",
        label: String = "Speech Recognition and Synthesis from Google",
    ) = RawVoice(
        engine = engine,
        engineLabel = label,
        name = name,
        language = "en",
        country = if (name.contains("-gb-", true)) "GB" else "US",
        network = network,
        quality = 400,
        openSource = engine == "rhvoice",
    )
}
