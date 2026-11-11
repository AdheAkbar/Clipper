package com.example.model

enum class CaptionTemplate(
    val label: String,
    val description: String,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    val pillBgColorHex: Long
) {
    CLEAN_EDITORIAL(
        label = "Editorial Clean",
        description = "Tipografi bersih dengan bayangan halus tanpa kotak mencolok",
        primaryColorHex = 0xFFFFFFFF,
        accentColorHex = 0xFFE2E8F0,
        pillBgColorHex = 0x00000000
    ),
    CINEMATIC_PILL(
        label = "Cinema Glass",
        description = "Subtitle sinematik dengan latar gelap transparan lembut",
        primaryColorHex = 0xFFF8FAFC,
        accentColorHex = 0xFFCBD5E1,
        pillBgColorHex = 0x880F172A
    ),
    PODCAST_MINIMAL(
        label = "Podcast Minimal",
        description = "Gaya studio podcast modern dengan aksen garis tipis",
        primaryColorHex = 0xFFFFFFFF,
        accentColorHex = 0xFF38BDF8,
        pillBgColorHex = 0x99090D16
    ),
    NO_OVERLAY(
        label = "Tanpa Teks (Clean Cut)",
        description = "Hanya potong video & rasio 9:16 tanpa teks tambahan",
        primaryColorHex = 0xFFFFFFFF,
        accentColorHex = 0xFFFFFFFF,
        pillBgColorHex = 0x00000000
    )
}

enum class VideoColorFilter(
    val label: String,
    val contrast: Float,
    val saturation: Float,
    val hue: Float,
    val isGrayscale: Boolean = false
) {
    ORIGINAL(
        label = "Original",
        contrast = 0f,
        saturation = 0f,
        hue = 0f
    ),
    NATURAL_CRISP(
        label = "Natural Crisp",
        contrast = 0.06f,
        saturation = 6f,
        hue = 0f
    ),
    FILM_WARM(
        label = "Warm Editorial",
        contrast = 0.05f,
        saturation = 4f,
        hue = -4f
    ),
    SLATE_COOL(
        label = "Cool Slate",
        contrast = 0.07f,
        saturation = -4f,
        hue = 6f
    ),
    MONO_FILM(
        label = "Monochrome",
        contrast = 0.12f,
        saturation = -100f,
        hue = 0f,
        isGrayscale = true
    )
}

data class VideoClip(
    val startStr: String,
    val endStr: String,
    val score: Double,
    val title: String,
    val hook: String,
    val reason: String,
    val category: String,
    val captions: List<String> = emptyList(),
    val fypHashtags: String = "#shorts #reels #clips",
    val template: CaptionTemplate = CaptionTemplate.CLEAN_EDITORIAL,
    val colorFilter: VideoColorFilter = VideoColorFilter.NATURAL_CRISP,
    val showTopHeader: Boolean = false,
    val showBottomSubtitle: Boolean = true
)

fun parseMmSsToMs(timeStr: String): Long {
    val clean = timeStr.trim().replace("[", "").replace("]", "")
    val parts = clean.split(":")
    return when (parts.size) {
        2 -> {
            val m = parts[0].toLongOrNull() ?: 0L
            val s = parts[1].toLongOrNull() ?: 0L
            (m * 60 + s) * 1000
        }
        3 -> {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val s = parts[2].toLongOrNull() ?: 0L
            (h * 3600 + m * 60 + s) * 1000
        }
        else -> 0L
    }
}

fun formatMsToMmSs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L)) / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format("%02d:%02d", m, s)
}
