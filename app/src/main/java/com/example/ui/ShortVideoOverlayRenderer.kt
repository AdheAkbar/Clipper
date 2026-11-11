package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Effects
import com.example.data.SettingsManager
import com.example.model.CaptionTemplate
import com.example.model.VideoClip
import com.google.common.collect.ImmutableList

/**
 * Timed BitmapOverlay that smoothly switches between pre-rendered editorial subtitle phases
 * across the duration of the exported Short clip.
 */
class TimedShortBitmapOverlay(
    private val phaseBitmaps: List<Bitmap>,
    private val clipDurationUs: Long
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (phaseBitmaps.isEmpty()) {
            return Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        }
        val safeDuration = clipDurationUs.coerceAtLeast(1_000_000L)
        val normalizedTime = (presentationTimeUs % safeDuration).coerceAtLeast(0L)
        val progress = (normalizedTime.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 0.999)
        val index = (progress * phaseBitmaps.size).toInt().coerceIn(0, phaseBitmaps.lastIndex)
        return phaseBitmaps[index]
    }
}

object ShortVideoOverlayRenderer {

    fun buildMedia3EffectsForClip(
        clip: VideoClip,
        aspectRatio: String,
        clipDurationMs: Long
    ): Effects {
        val videoEffects = mutableListOf<Effect>()
        val isVertical = aspectRatio == SettingsManager.ASPECT_VERTICAL

        // 1. Aspect Ratio Framing (9:16 Vertical Shorts)
        if (isVertical) {
            videoEffects.add(
                Presentation.createForAspectRatio(
                    9f / 16f,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )
        }

        // 2. Subtle Editorial Color Grading
        val filter = clip.colorFilter
        if (filter.isGrayscale) {
            videoEffects.add(RgbFilter.createGrayscaleFilter())
        }
        if (filter.contrast != 0f) {
            videoEffects.add(Contrast(filter.contrast))
        }
        if (filter.saturation != 0f || filter.hue != 0f) {
            val hsl = HslAdjustment.Builder()
                .adjustHue(filter.hue)
                .adjustSaturation(filter.saturation)
                .build()
            videoEffects.add(hsl)
        }

        // 3. Clean Editorial Typography Overlay (if enabled and template != NO_OVERLAY)
        val shouldRenderOverlay = clip.template != CaptionTemplate.NO_OVERLAY &&
                (clip.showTopHeader || clip.showBottomSubtitle)

        if (shouldRenderOverlay) {
            val phaseBitmaps = renderOverlayPhaseBitmaps(clip, isVertical)
            if (phaseBitmaps.isNotEmpty()) {
                val durationUs = (clipDurationMs.coerceAtLeast(3000L)) * 1000L
                val timedOverlay: TextureOverlay = TimedShortBitmapOverlay(phaseBitmaps, durationUs)
                videoEffects.add(OverlayEffect(ImmutableList.of(timedOverlay)))
            }
        }

        return Effects(emptyList(), videoEffects)
    }

    fun renderOverlayPhaseBitmaps(clip: VideoClip, isVertical: Boolean): List<Bitmap> {
        val width = if (isVertical) 720 else 1280
        val height = if (isVertical) 1280 else 720

        val captions = clip.captions.filter { it.isNotBlank() }.ifEmpty {
            listOfNotNull(clip.hook.takeIf { it.isNotBlank() }, clip.title.takeIf { it.isNotBlank() })
        }

        val phaseCount = captions.size.coerceAtLeast(1)
        val bitmaps = mutableListOf<Bitmap>()

        for (phaseIndex in 0 until phaseCount) {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawEditorialOverlayFrame(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                clip = clip,
                activeSubtitle = captions.getOrElse(phaseIndex) { "" }
            )
            bitmaps.add(bmp)
        }
        return bitmaps
    }

    private fun drawEditorialOverlayFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        clip: VideoClip,
        activeSubtitle: String
    ) {
        val template = clip.template
        if (template == CaptionTemplate.NO_OVERLAY) return

        val marginX = width * 0.08f
        val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val sansBold = Typeface.create("sans-serif", Typeface.BOLD)

        // Subtle bottom cinematic vignette so clean white subtitles are always legible
        if (clip.showBottomSubtitle && activeSubtitle.isNotBlank()) {
            val vignetteTop = height * 0.68f
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, vignetteTop, 0f, height,
                    Color.argb(0, 0, 0, 0),
                    Color.argb(145, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, vignetteTop, width, height, vignettePaint)
        }

        // 1. OPTIONAL MINIMAL TOP EDITORIAL HEADER
        if (clip.showTopHeader && clip.title.isNotBlank()) {
            val topY = height * 0.085f
            val maxHeaderWidth = (width - marginX * 2 - 48f).toInt().coerceAtLeast(100)

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = width * 0.036f
                typeface = sansMedium
                letterSpacing = 0.02f
                setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
            }

            val titleLayout = StaticLayout.Builder
                .obtain(clip.title, 0, clip.title.length, titlePaint, maxHeaderWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()

            val pillPaddingH = 28f
            val pillPaddingV = 16f
            val textWidth = (0 until titleLayout.lineCount)
                .maxOfOrNull { titleLayout.getLineWidth(it) } ?: (width * 0.5f)
            val pillW = (textWidth + pillPaddingH * 2).coerceAtMost(width - marginX * 2)
            val pillH = titleLayout.height + pillPaddingV * 2
            val pillLeft = (width - pillW) / 2f
            val pillRect = RectF(pillLeft, topY, pillLeft + pillW, topY + pillH)

            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 15, 23, 42)
            }
            canvas.drawRoundRect(pillRect, 18f, 18f, headerBgPaint)

            canvas.save()
            canvas.translate(
                (width - maxHeaderWidth) / 2f,
                topY + pillPaddingV
            )
            titleLayout.draw(canvas)
            canvas.restore()
        }

        // 2. LOWER-THIRD CLEAN EDITORIAL SUBTITLE
        if (clip.showBottomSubtitle && activeSubtitle.isNotBlank()) {
            val maxSubWidth = (width - marginX * 2 - 40f).toInt().coerceAtLeast(100)
            val primaryColor = template.primaryColorHex.toInt()
            val accentColor = template.accentColorHex.toInt()
            val pillBgColor = template.pillBgColorHex.toInt()

            val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                textSize = width * 0.044f
                typeface = sansBold
                letterSpacing = 0.01f
                setShadowLayer(10f, 0f, 3f, Color.argb(210, 0, 0, 0))
            }

            val subLayout = StaticLayout.Builder
                .obtain(activeSubtitle, 0, activeSubtitle.length, subTextPaint, maxSubWidth)
                .setAlignment(
                    if (template == CaptionTemplate.PODCAST_MINIMAL) Layout.Alignment.ALIGN_NORMAL
                    else Layout.Alignment.ALIGN_CENTER
                )
                .setLineSpacing(0f, 1.18f)
                .setIncludePad(false)
                .build()

            val subBottomY = height * 0.84f
            val subTopY = subBottomY - subLayout.height

            when (template) {
                CaptionTemplate.CLEAN_EDITORIAL -> {
                    // Subtle soft stroke for crisp legibility on bright backgrounds without any box
                    val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(190, 0, 0, 0)
                        textSize = width * 0.044f
                        typeface = sansBold
                        letterSpacing = 0.01f
                        style = Paint.Style.STROKE
                        strokeWidth = 4.5f
                    }
                    val strokeLayout = StaticLayout.Builder
                        .obtain(activeSubtitle, 0, activeSubtitle.length, strokePaint, maxSubWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0f, 1.18f)
                        .setIncludePad(false)
                        .build()

                    canvas.save()
                    canvas.translate((width - maxSubWidth) / 2f, subTopY)
                    strokeLayout.draw(canvas)
                    subLayout.draw(canvas)
                    canvas.restore()
                }

                CaptionTemplate.CINEMATIC_PILL -> {
                    val lineMaxWidth = (0 until subLayout.lineCount)
                        .maxOfOrNull { subLayout.getLineWidth(it) } ?: (width * 0.6f)
                    val padH = 28f
                    val padV = 18f
                    val boxW = (lineMaxWidth + padH * 2).coerceAtMost(width - marginX * 1.5f)
                    val boxLeft = (width - boxW) / 2f
                    val boxRect = RectF(
                        boxLeft,
                        subTopY - padV,
                        boxLeft + boxW,
                        subBottomY + padV
                    )
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = pillBgColor
                    }
                    canvas.drawRoundRect(boxRect, 18f, 18f, bgPaint)

                    canvas.save()
                    canvas.translate((width - maxSubWidth) / 2f, subTopY)
                    subLayout.draw(canvas)
                    canvas.restore()
                }

                CaptionTemplate.PODCAST_MINIMAL -> {
                    val padH = 26f
                    val padV = 18f
                    val boxRect = RectF(
                        marginX,
                        subTopY - padV,
                        width - marginX,
                        subBottomY + padV
                    )
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = pillBgColor
                    }
                    canvas.drawRoundRect(boxRect, 16f, 16f, bgPaint)

                    // Thin vertical accent bar on the left
                    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = accentColor
                    }
                    val barRect = RectF(
                        marginX + 10f,
                        subTopY - padV + 12f,
                        marginX + 15f,
                        subBottomY + padV - 12f
                    )
                    canvas.drawRoundRect(barRect, 4f, 4f, barPaint)

                    canvas.save()
                    canvas.translate(marginX + padH, subTopY)
                    subLayout.draw(canvas)
                    canvas.restore()
                }

                CaptionTemplate.NO_OVERLAY -> Unit
            }
        }
    }
}
