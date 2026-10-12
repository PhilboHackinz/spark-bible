package app.sparkbible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private val john = listOf(
        listOf("In the beginning", "The next day", "On the third day"),
        (1..30).map { "verse $it" },
    )
    private val acts = listOf(listOf("former", "treatise"))

    private fun load(book: Int): List<List<String>> = when (book) {
        42 -> john
        43 -> acts
        else -> error("no book $book")
    }

    @Test
    fun lowEnergyIsOneVerseAndDoesNotSpill() {
        val shape = Logic.sessionShape(Energy.LOW, remainingInChapter = 12)
        assertEquals(SessionShape(1, spill = false, truncated = false), shape)
        val verses = Logic.take(this::load, VersePin(42, 1, 3), shape.count, shape.spill)
        assertEquals(listOf(VersePin(42, 1, 3)), verses.map { it.pin })
    }

    @Test
    fun mediumEnergyCanCrossIntoTheNextChapter() {
        val shape = Logic.sessionShape(Energy.MEDIUM, remainingInChapter = 1)
        assertEquals(8, shape.count)
        assertTrue(shape.spill)
        val verses = Logic.take(this::load, VersePin(42, 1, 3), shape.count, shape.spill)
        assertEquals(8, verses.size)
        assertEquals(VersePin(42, 2, 7), verses.last().pin)
    }

    @Test
    fun highEnergyStopsAtTwentyFourAndDoesNotSpill() {
        val shape = Logic.sessionShape(Energy.HIGH, remainingInChapter = 30)
        assertEquals(SessionShape(24, spill = false, truncated = true), shape)
        val verses = Logic.take(this::load, VersePin(42, 2, 1), shape.count, shape.spill)
        assertEquals(24, verses.size)
        assertEquals(VersePin(42, 2, 24), verses.last().pin)
    }

    @Test
    fun highEnergyReadsAShortChapterWithoutPretendingItWasCut() {
        val shape = Logic.sessionShape(Energy.HIGH, remainingInChapter = 3)
        assertFalse(shape.truncated)
        assertEquals(3, shape.count)
    }

    @Test
    fun showingUpAddsAPlantAndAMissedDayRemovesNone() {
        assertEquals(1, Logic.plantsAfterShowingUp(0))
        assertEquals(4, Logic.plantsAfterShowingUp(3))
        val rested = 4
        assertEquals(4, rested)
    }

    @Test
    fun trailAdvancesOnlyOnTheTrailAndDailyDoesNotMoveIt() {
        val here = VersePin(42, 1, 1)
        val next = Logic.nextAfter(this::load, VersePin(42, 1, 3))
        assertEquals(VersePin(42, 2, 1), next)
        val trailVisit = Logic.progressAfter("trail", here, next, sparkDoneOn = "", today = "2026-10-12")
        assertEquals(next, trailVisit.trail)
        assertEquals("", trailVisit.sparkDoneOn)
        val daily = Logic.progressAfter("daily", here, next, sparkDoneOn = "2026-10-01", today = "2026-10-12")
        assertEquals(here, daily.trail)
        assertEquals("2026-10-12", daily.sparkDoneOn)
        val jump = Logic.progressAfter("jump", here, next, sparkDoneOn = "2026-10-01", today = "2026-10-12")
        assertEquals(here, jump.trail)
        assertEquals("2026-10-01", jump.sparkDoneOn)
    }

    @Test
    fun softAimDoesNotTurnAQuietWeekIntoADebt() {
        assertEquals("Nothing yet this week. That's a fine place to start.", Logic.weekLine(0, 3))
        assertEquals("1 of 3 — a soft aim, not a debt.", Logic.weekLine(1, 3))
        assertTrue(Logic.weekLine(5, 3).contains("never a test"))
    }

    @Test
    fun referenceJumpUnderstandsAShortName() {
        val books = listOf(BookInfo(42, "JHN", "John", "nt", 21))
        assertEquals(VersePin(42, 3, 16), Logic.parse("jn 3:16", books))
        assertEquals(VersePin(42, 1, 1), Logic.parse("John", books))
        assertNull(Logic.parse("jn 99:1", books))
    }

    @Test
    fun migrationsPreserveExistingRows() {
        assertEquals(1, SparkMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, SparkMigrations.MIGRATION_1_2.endVersion)
        assertEquals(listOf(1 to 2), SparkMigrations.ALL.map { it.startVersion to it.endVersion })
        val sql = SparkMigrations.INDEX_MOMENTS_AT.uppercase()
        assertTrue(sql.contains("CREATE INDEX"))
        assertFalse(sql.contains("DROP"))
        assertFalse(sql.contains("DELETE"))
    }
}
