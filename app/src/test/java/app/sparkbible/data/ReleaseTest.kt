package app.sparkbible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {
    @Test
    fun settingsRoundTripThroughOneCodec() {
        val settings = Settings(
            fontSize = 22.5f,
            lineHeight = 1.4f,
            theme = ThemePref.DARK,
            bionic = true,
            dyslexia = true,
            speechRate = 0.85f,
            voiceEngine = "eng",
            voiceName = "calm",
            translation = "kjv",
            ambient = true,
            walking = true,
            progressStyle = ProgressStyle.CONSTELLATION,
            weeklyAim = 5,
            reminderOn = true,
            reminderTime = "21:00",
            seenIntro = true,
        )
        val stored = SettingsCodec.encode(settings)
        val back = SettingsCodec.decode(stored)
        assertEquals(settings, back)
    }

    @Test
    fun anOldOnlineTranslationFallsBackToTheCopyOnThePhone() {
        val back = SettingsCodec.decode("""{"translation":"nlt","fontSize":20.5}""")
        assertEquals("web", back.translation)
        assertEquals(20.5f, back.fontSize)
    }

    @Test
    fun theDailySparkHidesAMatchingOpenPassage() {
        val spark = Spark("x", "hope", "Today", 4, 31, 8, 8, "")
        assertTrue(Logic.samePassage(4, 31, 8, spark))
        assertFalse(Logic.samePassage(42, 2, 8, spark))
        assertFalse(Logic.samePassage(-1, 31, 8, spark))
    }

    @Test
    fun devotionListHasNoDaysAndDropsTheDuplicateAnxietyPassage() {
        assertEquals(29, Devotions.all.size)
        assertTrue(Devotions.all.none { it.title.startsWith("Day") })
        assertFalse(Devotions.all.any { it.book == 49 && it.chapter == 4 && it.start == 6 })
        assertEquals(18, Devotions.get("little-things")?.book)
        assertEquals(34, Devotions.get("little-things")?.chapter)
    }

    @Test
    fun aDevotionDoesNotGrowAndATrailDoes() {
        assertTrue(Logic.lengthLocked("minute"))
        assertTrue(Logic.lengthLocked("spark"))
        assertFalse(Logic.lengthLocked("trail"))
        assertEquals(
            "A new star is in your sky. It will stay.",
            Logic.keptLine(ProgressStyle.CONSTELLATION),
        )
    }
}
