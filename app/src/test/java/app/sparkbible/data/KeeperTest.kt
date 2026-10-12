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
    }

    @Test
    fun aRandomFileIsNotACopy() {
        assertNull(Keeper.decode("hello"))
        assertNull(Keeper.decode("""{"kind":"other"}"""))
    }
}
