package ru.madhare.videocompressor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryTest {
    private fun v(id: Long, own: Boolean = false, level: Level? = null) = GalleryVideo(
        id = id, name = "v$id.mp4", sizeBytes = 100, durationMs = 1000,
        width = 1280, height = 720, ownResult = own, compressed = level,
    )

    @Test
    fun history_encodeDecodeRoundTrip() {
        val raw = History.encode("a|b.mp4", 123, Level.MID)
        val d = History.decode(raw)!!
        assertEquals("a|b.mp4", d.first)
        assertEquals(123L, d.second)
        assertEquals(Level.MID, d.third)
    }

    @Test
    fun history_decodeRejectsGarbage() {
        assertNull(History.decode("oops"))
        assertNull(History.decode("BAD|1|x.mp4"))
        assertNull(History.decode("MIN|notanumber|x.mp4"))
    }

    @Test
    fun history_toMapKeyedByNameAndSize() {
        val map = History.toMap(listOf(History.encode("x.mp4", 10, Level.MIN), "junk"))
        assertEquals(Level.MIN, map["x.mp4" to 10L])
        assertNull(map["x.mp4" to 11L])
        assertEquals(1, map.size)
    }

    @Test
    fun isNew_excludesCompressedAndOwnResults() {
        assertTrue(isNew(v(1)))
        assertTrue(!isNew(v(2, level = Level.MIN)))
        assertTrue(!isNew(v(3, own = true)))
    }

    @Test
    fun visibleVideos_filtersWhenOnlyNew() {
        val all = listOf(v(1), v(2, level = Level.MAX), v(3, own = true), v(4))
        assertEquals(listOf(1L, 4L), visibleVideos(all, true).map { it.id })
        assertEquals(4, visibleVideos(all, false).size)
    }
}
