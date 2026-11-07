package ru.madhare.videocompressor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    @Test
    fun min_scalesDownFullHdTo360() {
        val p = Planner.plan(Level.MIN, 1920, 1080, 8_000_000, hevc = false)
        assertEquals(360, p.targetShortSide)
        assertEquals(450_000, p.videoBitrate)
        assertEquals(64_000, p.audioBitrate)
    }

    @Test
    fun min_portraitUsesShortSide() {
        val p = Planner.plan(Level.MIN, 1080, 1920, 8_000_000, hevc = false)
        assertEquals(360, p.targetShortSide)
    }

    @Test
    fun min_doesNotUpscaleSmallVideo() {
        val p = Planner.plan(Level.MIN, 480, 270, 400_000, hevc = false)
        assertNull(p.targetShortSide)
        assertEquals(320_000, p.videoBitrate)
    }

    @Test
    fun mid_is720p() {
        val p = Planner.plan(Level.MID, 3840, 2160, 40_000_000, hevc = false)
        assertEquals(720, p.targetShortSide)
        assertEquals(2_500_000, p.videoBitrate)
        assertEquals(128_000, p.audioBitrate)
    }

    @Test
    fun mid_keepsExactly720() {
        val p = Planner.plan(Level.MID, 1280, 720, 5_000_000, hevc = false)
        assertNull(p.targetShortSide)
    }

    @Test
    fun max_keepsResolutionAndCapsBitrate() {
        val p = Planner.plan(Level.MAX, 3840, 2160, 60_000_000, hevc = false)
        assertNull(p.targetShortSide)
        assertEquals(12_000_000, p.videoBitrate)
    }

    @Test
    fun max_is65PercentOfSource() {
        val p = Planner.plan(Level.MAX, 1920, 1080, 10_000_000, hevc = false)
        assertEquals(6_500_000, p.videoBitrate)
        assertEquals(160_000, p.audioBitrate)
    }

    @Test
    fun max_unknownBitrateUsesFallback() {
        val p = Planner.plan(Level.MAX, 1920, 1080, 0, hevc = false)
        assertEquals(6_000_000, p.videoBitrate)
    }

    @Test
    fun hevc_reducesBitrate() {
        val avc = Planner.plan(Level.MID, 1920, 1080, 8_000_000, hevc = false)
        val hevc = Planner.plan(Level.MID, 1920, 1080, 8_000_000, hevc = true)
        assertTrue(hevc.videoBitrate < avc.videoBitrate)
        assertEquals(1_750_000, hevc.videoBitrate)
    }

    @Test
    fun bitrate_neverBelowFloor() {
        val p = Planner.plan(Level.MIN, 640, 360, 100_000, hevc = true)
        assertEquals(150_000, p.videoBitrate)
    }

    @Test
    fun estimate_sizeFromBitrateAndDuration() {
        val p = Plan(360, 450_000, 64_000)
        // (514000 бит/с * 60 с) / 8 = 3 855 000 байт
        assertEquals(3_855_000L, Planner.estimateSize(p, 60_000))
    }

    @Test
    fun naming_compressedName() {
        assertEquals("Отпуск_min.mp4", Naming.compressedName("Отпуск.mov", Level.MIN))
        assertEquals("a.b_mid.mp4", Naming.compressedName("a.b.mp4", Level.MID))
        assertEquals("video_max.mp4", Naming.compressedName("", Level.MAX))
        assertEquals("clip_min.mp4", Naming.compressedName("clip", Level.MIN))
    }

    @Test
    fun naming_uniqueAddsNumber() {
        val taken = setOf("a_min.mp4", "a_min_2.mp4")
        assertEquals("b.mp4", Naming.unique("b.mp4") { it in taken })
        assertEquals("a_min_3.mp4", Naming.unique("a_min.mp4") { it in taken })
    }

    @Test
    fun fmt_size() {
        assertEquals("12,4 МБ", Fmt.size(12_400_000))
        assertEquals("850 КБ", Fmt.size(850_000))
        assertEquals("1,25 ГБ", Fmt.size(1_250_000_000))
    }

    @Test
    fun fmt_duration() {
        assertEquals("0:45", Fmt.duration(45_000))
        assertEquals("1:05", Fmt.duration(65_000))
        assertEquals("1:02:03", Fmt.duration(3_723_000))
    }

    @Test
    fun fmt_saved() {
        assertEquals("−72 %", Fmt.saved(100, 28))
        assertEquals("+10 %", Fmt.saved(100, 110))
    }
}
