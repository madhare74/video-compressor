package ru.madhare.videocompressor

import java.util.Locale

/** Уровень сжатия, выбираемый пользователем. */
enum class Level(
    val title: String,
    val subtitle: String,
    val shortSide: Int?,
    val baseVideoBps: Int,
    val audioBps: Int,
) {
    MIN("Минимальный размер", "360p, чтобы смотреть на телефоне", 360, 450_000, 64_000),
    MID("Средний", "720p, баланс размера и качества", 720, 2_500_000, 128_000),
    MAX("Максимальный", "исходное разрешение, почти без потери качества", null, 0, 160_000),
}

/** Параметры кодирования для одного видео. */
data class Plan(
    /** Целевая короткая сторона; null, если масштабировать не нужно. */
    val targetShortSide: Int?,
    val videoBitrate: Int,
    val audioBitrate: Int,
)

object Planner {
    private const val MAX_LEVEL_BPS = 12_000_000
    private const val MAX_FALLBACK_BPS = 6_000_000
    private const val MIN_BPS = 150_000
    private const val HEVC_FACTOR = 0.7

    fun plan(level: Level, width: Int, height: Int, srcVideoBps: Long, hevc: Boolean): Plan {
        val shortSide = minOf(width, height)
        val target = level.shortSide?.takeIf { shortSide <= 0 || shortSide > it }

        var bps: Double = if (level == Level.MAX) {
            if (srcVideoBps > 0) minOf(srcVideoBps * 0.65, MAX_LEVEL_BPS.toDouble())
            else MAX_FALLBACK_BPS.toDouble()
        } else {
            val base = level.baseVideoBps.toDouble()
            if (srcVideoBps > 0) minOf(base, srcVideoBps * 0.8) else base
        }
        if (hevc) bps *= HEVC_FACTOR
        val floor = if (level == Level.MAX) 300_000 else MIN_BPS
        return Plan(target, maxOf(bps.toInt(), floor), level.audioBps)
    }

    /** Примерный размер результата в байтах. */
    fun estimateSize(plan: Plan, durationMs: Long): Long =
        (plan.videoBitrate + plan.audioBitrate).toLong() * durationMs / 8000L
}

object Naming {
    /** «Отпуск.mov» → «Отпуск_compressed.mp4». */
    fun compressedName(original: String): String {
        val base = original.substringBeforeLast('.', original).ifBlank { "video" }
        return base + "_compressed.mp4"
    }

    /** Подбирает имя, которого ещё нет: «a.mp4», «a_2.mp4», «a_3.mp4». */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var n = 2
        while (true) {
            val candidate = "${base}_$n$ext"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}

object Fmt {
    private const val NBSP = " "
    private val ru = Locale("ru", "RU")

    fun size(bytes: Long): String = when {
        bytes < 1_000_000L -> "${maxOf(1L, (bytes + 500L) / 1000L)}${NBSP}КБ"
        bytes < 1_000_000_000L -> String.format(ru, "%.1f", bytes / 1_000_000.0) + NBSP + "МБ"
        else -> String.format(ru, "%.2f", bytes / 1_000_000_000.0) + NBSP + "ГБ"
    }

    fun duration(ms: Long): String {
        val total = (ms + 500L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(ru, "%d:%02d:%02d", h, m, s)
        else String.format(ru, "%d:%02d", m, s)
    }

    fun resolution(w: Int, h: Int): String = "$w×$h"

    /** «−72 %» с неразрывным пробелом. */
    fun saved(original: Long, result: Long): String {
        if (original <= 0) return ""
        val pct = ((original - result) * 100.0 / original).toInt()
        val sign = if (pct >= 0) "−" else "+"
        return "$sign${kotlin.math.abs(pct)}${NBSP}%"
    }
}
