package ru.madhare.videocompressor

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private data class Cfg(val state: UiState, val fontScale: Float, val dark: Boolean, val widthDp: Int)

    private fun item(
        id: Long, name: String, status: Status = Status.Ready, w: Int = 1920, h: Int = 1080, dur: Long = 125_000,
        size: Long = 184_000_000, result: Long = 0, progress: Float = 0f, msg: String? = null,
        level: Level = Level.MIN,
    ) = VideoItem(
        id = id, uri = Uri.parse("content://x/$id"), name = name, durationMs = dur, sizeBytes = size,
        width = w, height = h, videoBps = 8_000_000, dateTakenMs = null, status = status,
        progress = progress, level = level, resultSize = result, message = msg,
        resultUri = if (status == Status.Done) Uri.parse("content://x/r$id") else null,
    )

    private val longName = "Очень_длинное_название_видео_с_отпуска_на_море_2026_финальная_версия_часть_3.mp4"

    private val ready = UiState(
        listOf(
            item(1, "IMG_0412.mp4"),
            item(2, longName, w = 3840, h = 2160, dur = 3_723_000, size = 2_350_000_000),
            item(3, "Вертикальное.mp4", w = 1080, h = 1920, dur = 45_000, size = 52_400_000),
        ),
        Level.MIN, false, 0,
    )

    private val running = UiState(
        listOf(
            item(1, "IMG_0412.mp4", Status.Done, result = 21_300_000),
            item(2, longName, Status.Running, progress = 0.42f, w = 3840, h = 2160, size = 2_350_000_000),
            item(3, "Вертикальное.mp4", Status.Queued),
            item(4, "Ошибка.mp4", Status.Failed, msg = "Формат видео не поддерживается кодером этого телефона"),
            item(5, "Уже_сжатое.mp4", Status.Skipped, result = 18_000_000, size = 17_000_000,
                msg = "Результат не меньше оригинала, файл не сохранён"),
        ),
        Level.MID, false, 0,
    )

    private val finished = UiState(
        listOf(
            item(1, "IMG_0412.mp4", Status.Done, result = 21_300_000),
            item(2, longName, Status.Done, result = 140_000_000, w = 3840, h = 2160, size = 2_350_000_000),
            item(3, "Ошибка.mp4", Status.Failed, msg = "Недостаточно места на телефоне"),
        ),
        Level.MAX, true, 0,
    )

    private val dir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots").apply { mkdirs() }
    }

    @Test
    fun screens() {
        var cfg by mutableStateOf(Cfg(ready, 1f, false, 412))
        rule.setContent {
            val c = cfg
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(1.25f, c.fontScale)) {
                AppTheme(c.dark) {
                    Box(Modifier.testTag("frame").width(c.widthDp.dp).fillMaxHeight()) {
                        MainScreen(c.state, Actions())
                    }
                }
            }
        }
        val states = listOf("ready" to ready, "running" to running, "finished" to finished)
        for ((name, st) in states) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                for (dark in listOf(false, true)) {
                    for (width in listOf(360, 412)) {
                        rule.runOnIdle { cfg = Cfg(st, scale, dark, width) }
                        rule.waitForIdle()
                        val last = 2 + st.items.size
                        for ((si, idx) in listOf(0, 3, last).withIndex()) {
                            rule.onNodeWithTag("list").performScrollToIndex(idx)
                            rule.waitForIdle()
                            val bmp = rule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
                            val label = "${name}_f${(scale * 100).toInt()}_${if (dark) "dark" else "light"}_${width}_s$si"
                            File(dir, "$label.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun keyElementsVisibleAtLargeFont() {
        var scale by mutableStateOf(2f)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale)) {
                AppTheme(false) {
                    Box(Modifier.width(360.dp).height(820.dp)) { MainScreen(ready, Actions()) }
                }
            }
        }
        rule.onNodeWithText("Выбрать видео").assertIsDisplayed()
        rule.onNodeWithText("Сжать (3)").assertIsDisplayed()
        rule.onNodeWithText("Минимальный размер").assertIsDisplayed()
    }

    private fun gv(id: Long, name: String, own: Boolean = false, level: Level? = null, size: Long = 184_000_000) =
        GalleryVideo(id, name, size, 125_000, 1920, 1080, own, level)

    private val gallery = listOf(
        gv(1, "IMG_0412.mp4"),
        gv(2, longName, size = 2_350_000_000),
        gv(3, "Вертикальное.mp4", level = Level.MIN),
        gv(4, "IMG_0413_min.mp4", own = true, size = 21_300_000),
        gv(5, "Праздник.mp4", level = Level.MAX),
        gv(6, "Новое.mp4"),
    )

    @Test
    fun pickerScreens() {
        var access by mutableStateOf(true)
        var scale by mutableStateOf(1f)
        var dark by mutableStateOf(false)
        var onlyNew by mutableStateOf(true)
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(1.25f, scale)) {
                AppTheme(dark) {
                    Box(Modifier.testTag("frame").width(360.dp).fillMaxHeight()) {
                        PickerScreen(
                            PickerState(gallery, setOf(1L, 6L), onlyNew, access, false),
                            PickerActions(),
                        )
                    }
                }
            }
        }
        fun shoot(label: String, scrollTo: Int? = null) {
            rule.waitForIdle()
            if (scrollTo != null) {
                rule.onNodeWithTag("grid").performScrollToIndex(scrollTo)
                rule.waitForIdle()
            }
            val bmp = rule.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
            File(dir, "picker_$label.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        }
        for (sc in listOf(1f, 2f)) {
            for (dk in listOf(false, true)) {
                for (only in listOf(true, false)) {
                    rule.runOnIdle { scale = sc; dark = dk; onlyNew = only; access = true }
                    val l = "f${(sc * 100).toInt()}_${if (dk) "dark" else "light"}_${if (only) "new" else "all"}"
                    shoot(l)
                    shoot(l + "_tiles", scrollTo = 2)
                }
            }
        }
        rule.runOnIdle { scale = 1.5f; dark = false; access = false }
        shoot("noaccess_f150")
        rule.onNodeWithText("Разрешить доступ").assertExists()
    }
}
