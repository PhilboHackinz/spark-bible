package app.sparkbible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeeperTest {
    @Test
    fun aCopyRoundTripsTheGardenAndHighlights() {
        val copy = KeptCopy(
            moments = listOf(MomentEntity("m", 10, 1, "John 1:1", "LOW")),
            plants = listOf(PlantEntity("p", 2, 10)),
            highlights = listOf(HighlightEntity("42:1:1", 42, 1, 1, "In the beginning \"God\"", "keep\nthis", 10)),
            progress = ProgressEntity(trailBook = 43, trailChapter = 2, trailVerse = 4, sparkDoneOn = "2026-10-12"),
        )
        val back = Keeper.decode(Keeper.encode(copy))
        assertEquals(copy.plants, back?.plants)
        assertEquals(copy.highlights, back?.highlights)
        assertEquals(43, back?.progress?.trailBook)
        assertEquals("2026-10-12", back?.progress?.sparkDoneOn)
        assertNull(back?.reading)
    }

    @Test
    fun anOlderCopyStillOpens() {
        val old = """
            {"kind":"spark-bible-copy","version":1,"moments":[],"plants":[],"highlights":[],
            "progress":{"trailBook":42,"trailChapter":1,"trailVerse":1,"openBook":-1,"openChapter":1,
            "openVerse":1,"openCount":0,"openEnergy":"LOW","openMode":"READ","openOrigin":"trail",
            "openPrompt":"","openLabel":"","sparkDoneOn":"","lastNotified":""}}
        """.trimIndent()
        val back = Keeper.decode(old)
        assertEquals(42, back?.progress?.trailBook)
        assertNull(back?.reading)
    }

    @Test
    fun aDecimalTextSizeSurvivesTheCopy() {
        val copy = KeptCopy(
            moments = emptyList(),
            plants = emptyList(),
            highlights = emptyList(),
            progress = null,
            reading = KeptReading("kjv", 20.5f, 1.8f, 0.85f, "engine", "voice"),
        )
        val back = Keeper.decode(Keeper.encode(copy))?.reading
        assertEquals("kjv", back?.translation)
        assertEquals(20.5f, back?.fontSize ?: 0f, 0.001f)
        assertEquals(1.8f, back?.lineHeight ?: 0f, 0.001f)
        assertEquals(0.85f, back?.speechRate ?: 0f, 0.001f)
        assertEquals("voice", back?.voiceName)
    }

    @Test
    fun aRandomFileIsNotACopy() {
        assertNull(Keeper.decode("hello"))
        assertNull(Keeper.decode("""{"kind":"other"}"""))
    }
}
