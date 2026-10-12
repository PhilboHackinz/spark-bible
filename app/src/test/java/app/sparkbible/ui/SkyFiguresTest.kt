package app.sparkbible.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyFiguresTest {
    @Test
    fun firstVisitsDrawABloomNotALooseLine() {
        val one = skyPicture(1, 100f, 100f)
        assertEquals(1, one.stars.size)
        assertEquals(listOf("Bloom"), one.names)
        assertTrue(one.lines.isEmpty())
        val bloom = skyPicture(7, 100f, 100f)
        assertEquals(7, bloom.stars.size)
        assertEquals(6, bloom.lines.size)
        assertEquals(listOf("Bloom"), bloom.names)
    }

    @Test
    fun theNextVisitStartsASecondShape() {
        val picture = skyPicture(8, 200f, 200f)
        assertEquals(listOf("Bloom", "Lamp"), picture.names)
        assertEquals(8, picture.stars.size)
        assertTrue(picture.stars.last().newest)
        assertEquals("Bloom, Lamp. The lamp is still forming.", skyCaption(8))
    }
}
