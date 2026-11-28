package com.securegps.camera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.securegps.camera.data.GpsStampData
import java.io.File

/**
 * Stamps the exact GPS Map Camera layout onto the captured photo:
 * - Top-right floating pill badge ("GPS Map Camera")
 * - Bottom translucent card with square satellite thumbnail + red pin on the left
 * - City/State/Country + Flag, full multi-line address, Lat/Long, Date/Time GMT, and Note on the right.
 * - Supports dual side-by-side split screen merging into a single composite image.
 */
object WatermarkRenderer {

    fun loadRotatedBitmap(imageFile: File): Bitmap {
        val raw = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: error("Failed to decode captured image")
        val exif = ExifInterface(imageFile.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return raw.copy(Bitmap.Config.ARGB_8888, true)
        }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    /**
     * Merges two captured photos side-by-side into a single composite high-resolution image.
     */
    fun mergeDualBitmaps(first: Bitmap, second: Bitmap): Bitmap {
        val targetHeight = maxOf(first.height, second.height)
        val scale1 = targetHeight.toFloat() / first.height.toFloat()
        val scale2 = targetHeight.toFloat() / second.height.toFloat()
        val w1 = (first.width * scale1).toInt().coerceAtLeast(1)
        val w2 = (second.width * scale2).toInt().coerceAtLeast(1)
        val dividerWidth = (targetHeight * 0.005f).toInt().coerceAtLeast(6)

        val merged = Bitmap.createBitmap(w1 + dividerWidth + w2, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        canvas.drawColor(Color.BLACK)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val srcRect1 = Rect(0, 0, first.width, first.height)
        val dstRect1 = Rect(0, 0, w1, targetHeight)
        canvas.drawBitmap(first, srcRect1, dstRect1, paint)

        val divPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = dividerWidth.toFloat()
        }
        val divX = w1 + dividerWidth / 2f
        canvas.drawLine(divX, 0f, divX, targetHeight.toFloat(), divPaint)

        val srcRect2 = Rect(0, 0, second.width, second.height)
        val dstRect2 = Rect(w1 + dividerWidth, 0, w1 + dividerWidth + w2, targetHeight)
        canvas.drawBitmap(second, srcRect2, dstRect2, paint)

        return merged
    }

    fun stampGpsOverlay(source: Bitmap, stamp: GpsStampData): Bitmap {
        val result = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val outerMargin = w * 0.025f
        val gap = w * 0.022f
        val mapSize = w * 0.21f
        val cardRadius = w * 0.018f
        val cardPadding = w * 0.020f

        val textAreaLeft = outerMargin + mapSize + gap + cardPadding
        val textAreaRight = w - outerMargin - cardPadding
        val textWidth = (textAreaRight - textAreaLeft).toInt().coerceAtLeast(100)

        // Typography scaled proportionally to image width
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.036f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 244, 248)
            textSize = w * 0.0235f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val headerLayout = buildStaticLayout(stamp.headerLine(), headerPaint, textWidth, 2)
        val addressLayout = buildStaticLayout(stamp.fullAddress, bodyPaint, textWidth, 2)
        val coordsLayout = buildStaticLayout(stamp.coordinatesLine(), bodyPaint, textWidth, 1)
        val dateLayout = buildStaticLayout(stamp.formattedDateTimeLine(), bodyPaint, textWidth, 1)
        val noteLayout = buildStaticLayout(stamp.noteLine(), bodyPaint, textWidth, 2)

        val lineGap = w * 0.006f
        val totalTextHeight = headerLayout.height +
            lineGap + addressLayout.height +
            lineGap + coordsLayout.height +
            lineGap + dateLayout.height +
            lineGap + noteLayout.height

        val cardHeight = maxOf(mapSize, totalTextHeight + cardPadding * 2f)
        val cardBottom = h - outerMargin
        val cardTop = cardBottom - cardHeight

        // Draw top-right "GPS Map Camera" pill badge sitting right above the card
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 16, 18, 22)
        }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.020f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pillText = "GPS Map Camera"
        val pillTextWidth = pillTextPaint.measureText(pillText)
        val iconSize = w * 0.022f
        val pillPadH = w * 0.015f
        val pillPadV = w * 0.008f
        val pillWidth = pillPadH * 2 + iconSize + w * 0.010f + pillTextWidth
        val pillHeight = iconSize + pillPadV * 2
        val pillRight = w - outerMargin
        val pillLeft = pillRight - pillWidth
        val pillBottom = cardTop - w * 0.004f
        val pillTop = pillBottom - pillHeight

        canvas.drawRoundRect(
            RectF(pillLeft, pillTop, pillRight, pillBottom),
            w * 0.009f, w * 0.009f, pillPaint
        )

        // Small camera/pin badge icon inside pill
        val iconLeft = pillLeft + pillPadH
        val iconTop = pillTop + pillPadV
        val iconBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 150, 243)
        }
        canvas.drawRoundRect(
            RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
            iconSize * 0.25f, iconSize * 0.25f, iconBg
        )
        val iconInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 185, 129)
        }
        canvas.drawCircle(
            iconLeft + iconSize * 0.5f,
            iconTop + iconSize * 0.5f,
            iconSize * 0.24f,
            iconInner
        )

        val textBaseline = pillTop + pillHeight * 0.5f - (pillTextPaint.ascent() + pillTextPaint.descent()) * 0.5f
        canvas.drawText(pillText, iconLeft + iconSize + w * 0.010f, textBaseline, pillTextPaint)

        // Draw left square satellite map thumbnail
        val mapLeft = outerMargin
        val mapTop = cardBottom - mapSize
        val mapRect = RectF(mapLeft, mapTop, mapLeft + mapSize, cardBottom)

        val clipPath = Path().apply {
            addRoundRect(mapRect, cardRadius * 0.65f, cardRadius * 0.65f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)
        val mapBmp = stamp.mapSnapshot
        if (mapBmp != null && !mapBmp.isRecycled) {
            canvas.drawBitmap(mapBmp, null, mapRect, null)
        } else {
            val fallbackBg = Paint().apply { color = Color.rgb(30, 48, 38) }
            canvas.drawRect(mapRect, fallbackBg)
        }
        canvas.restore()

        // Draw right dark translucent detail card
        val infoCardLeft = outerMargin + mapSize + gap
        val infoCardRect = RectF(infoCardLeft, cardTop, w - outerMargin, cardBottom)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 12, 14, 18)
        }
        canvas.drawRoundRect(infoCardRect, cardRadius, cardRadius, cardPaint)

        // Draw text layouts stacked cleanly inside the right card
        var currentY = cardTop + (cardHeight - totalTextHeight) * 0.5f
        drawLayout(canvas, headerLayout, textAreaLeft, currentY)
        currentY += headerLayout.height + lineGap
        drawLayout(canvas, addressLayout, textAreaLeft, currentY)
        currentY += addressLayout.height + lineGap
        drawLayout(canvas, coordsLayout, textAreaLeft, currentY)
        currentY += coordsLayout.height + lineGap
        drawLayout(canvas, dateLayout, textAreaLeft, currentY)
        currentY += dateLayout.height + lineGap
        drawLayout(canvas, noteLayout, textAreaLeft, currentY)

        return result
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        maxLines: Int
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.05f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
