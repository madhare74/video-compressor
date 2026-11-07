package ru.madhare.videocompressor

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Видео из галереи телефона для экрана выбора. */
data class GalleryVideo(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** Это результат нашего приложения (лежит в папке Video Compressor). */
    val ownResult: Boolean,
    /** Уровень, на котором это видео уже сжималось; null, если ещё нет. */
    val compressed: Level?,
) {
    fun uri(): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
}

/** Новое = ещё не сжималось и не является нашим результатом. */
fun isNew(v: GalleryVideo): Boolean = !v.ownResult && v.compressed == null

fun visibleVideos(videos: List<GalleryVideo>, onlyNew: Boolean): List<GalleryVideo> =
    if (onlyNew) videos.filter(::isNew) else videos

/** Память о том, какие оригиналы уже сжимались: ключ — имя и размер файла. */
object History {
    private const val PREFS = "history"
    private const val KEY = "items"

    fun encode(name: String, size: Long, level: Level): String = "${level.name}|$size|$name"

    fun decode(raw: String): Triple<String, Long, Level>? {
        val parts = raw.split("|", limit = 3)
        if (parts.size < 3) return null
        val level = Level.entries.firstOrNull { it.name == parts[0] } ?: return null
        val size = parts[1].toLongOrNull() ?: return null
        return Triple(parts[2], size, level)
    }

    /** Строит таблицу «имя|размер → уровень» из сохранённых записей. */
    fun toMap(raw: Collection<String>): Map<Pair<String, Long>, Level> {
        val map = HashMap<Pair<String, Long>, Level>()
        for (r in raw) decode(r)?.let { (n, s, l) -> map[n to s] = l }
        return map
    }

    fun load(context: Context): Map<Pair<String, Long>, Level> =
        toMap(prefs(context).getStringSet(KEY, emptySet()) ?: emptySet())

    @Synchronized
    fun mark(context: Context, name: String, size: Long, level: Level) {
        val p = prefs(context)
        val set = HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.removeAll { decode(it)?.let { (n, s, _) -> n == name && s == size } == true }
        set.add(encode(name, size, level))
        p.edit().putStringSet(KEY, set).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

object Gallery {
    fun query(context: Context): List<GalleryVideo> {
        val history = History.load(context)
        val out = ArrayList<GalleryVideo>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.RELATIVE_PATH
        else @Suppress("DEPRECATION") MediaStore.Video.Media.DATA
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            pathColumn,
        )
        context.contentResolver.query(
            collection, projection, "${MediaStore.Video.Media.SIZE}>0", null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val size = c.getLong(2)
                val path = c.getString(6) ?: ""
                out += GalleryVideo(
                    id = id,
                    name = name,
                    sizeBytes = size,
                    durationMs = c.getLong(3),
                    width = c.getInt(4),
                    height = c.getInt(5),
                    ownResult = path.contains(Saver.FOLDER),
                    compressed = history[name to size],
                )
            }
        }
        return out
    }
}
