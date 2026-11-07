package ru.madhare.videocompressor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class CompressInstrumentedTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val target = inst.targetContext

    private fun sample(): File {
        val f = File(target.cacheDir, "sample_in.mp4")
        inst.context.assets.open("sample.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    private fun height(f: File): Int {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        r.release()
        return h
    }

    @Test
    fun minLevel_scalesTo360AndShrinks() {
        val src = sample()
        val out = File(target.cacheDir, "out_min.mp4")
        out.delete()
        val info = MetaReader.read(target, Uri.fromFile(src), 1)
        assertEquals(1280, info.width)
        assertEquals(720, info.height)
        val plan = Planner.plan(Level.MIN, info.width, info.height, info.videoBps, false)
        var last = 0f
        runBlocking {
            Compressor.compress(target, Uri.fromFile(src), plan, false, info.width, info.height, out) { last = it }
        }
        assertTrue("файл не создан", out.exists() && out.length() > 0)
        assertEquals(360, height(out))
        assertTrue("результат не меньше оригинала", out.length() < src.length())
    }

    @Test
    fun maxLevel_keepsResolution() {
        val src = sample()
        val out = File(target.cacheDir, "out_max.mp4")
        out.delete()
        val info = MetaReader.read(target, Uri.fromFile(src), 2)
        val plan = Planner.plan(Level.MAX, info.width, info.height, info.videoBps, false)
        runBlocking {
            Compressor.compress(target, Uri.fromFile(src), plan, false, info.width, info.height, out) { }
        }
        assertEquals(720, height(out))
        assertTrue(out.length() < src.length())
    }

    @Test
    fun saver_putsFileIntoGallery() {
        val src = sample()
        val uri = Saver.save(target, src, "vc_test_compressed.mp4", null)
        target.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getString(0).startsWith("vc_test_compressed"))
        }
        val second = Saver.save(target, src, "vc_test_compressed.mp4", null)
        assertTrue(second != uri)
        target.contentResolver.delete(uri, null, null)
        target.contentResolver.delete(second, null, null)
    }
}
