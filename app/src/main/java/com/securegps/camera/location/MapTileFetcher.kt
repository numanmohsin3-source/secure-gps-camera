package com.securegps.camera.location

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Fetches real satellite map tiles centered precisely on the user's GPS coordinate
 * using Esri World Imagery (HTTPS, no API key required) and draws the red map pin
 * at the exact center. Includes an offline vector grid fallback when no network is available.
 */
class MapTileFetcher {

    private val tileCache = LruCache<String, Bitmap>(24)
    private val zoom = 17
    private val tileSize = 256

    fun fetchCenteredSatelliteThumbnail(lat: Double, lng: Double, sizePx: Int = 280): Bitmap {
        val worldCoords = latLngToWorldPixel(lat, lng, zoom)
        val centerPixelX = worldCoords.first
        val centerPixelY = worldCoords.second

        val half = sizePx / 2.0
        val minTileX = floor((centerPixelX - half) / tileSize).toInt()
        val maxTileX = floor((centerPixelX + half) / tileSize).toInt()
        val minTileY = floor((centerPixelY - half) / tileSize).toInt()
        val maxTileY = floor((centerPixelY + half) / tileSize).toInt()

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        drawOfflineSatelliteFallback(canvas, sizePx)

        var anyTileLoaded = false
        val maxIndex = (1 shl zoom) - 1

        for (tx in minTileX..maxTileX) {
            for (ty in minTileY..maxTileY) {
                if (ty < 0 || ty > maxIndex) continue
                val wrappedX = ((tx % (1 shl zoom)) + (1 shl zoom)) % (1 shl zoom)
                val tile = getOrDownloadTile(zoom, wrappedX, ty)
                if (tile != null) {
                    anyTileLoaded = true
                    val destLeft = (tx * tileSize - (centerPixelX - half)).toInt()
                    val destTop = (ty * tileSize - (centerPixelY - half)).toInt()
                    val destRect = Rect(destLeft, destTop, destLeft + tileSize, destTop + tileSize)
                    canvas.drawBitmap(tile, null, destRect, null)
                }
            }
        }

        if (anyTileLoaded) {
            // Subtle bottom shadow for clean map label contrast
            val shadowPaint = Paint().apply {
                color = Color.argb(80, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, sizePx * 0.78f, sizePx.toFloat(), sizePx.toFloat(), shadowPaint)
        }

        drawCenterRedPin(canvas, sizePx / 2f, sizePx / 2f, sizePx * 0.14f)
        drawSatelliteBadge(canvas, sizePx)
        return output
    }

    private fun getOrDownloadTile(z: Int, x: Int, y: Int): Bitmap? {
        val key = "$z/$x/$y"
        tileCache.get(key)?.let { return it }

        val urls = listOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x",
            "https://tile.openstreetmap.org/$z/$x/$y.png"
        )

        for (urlStr in urls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "SecureGPSCamera/1.0")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            tileCache.put(key, bmp)
                            return bmp
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Try next tile provider or fallback
            }
        }
        return null
    }

    private fun latLngToWorldPixel(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
        val sinLat = kotlin.math.sin(clampedLat * PI / 180.0)
        val n = 2.0.pow(zoom) * tileSize
        val x = (lng + 180.0) / 360.0 * n
        val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * n
        return x to y
    }

    private fun drawOfflineSatelliteFallback(canvas: Canvas, sizePx: Int) {
        canvas.drawColor(Color.rgb(26, 41, 34))
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 120, 175, 145)
            strokeWidth = 1.5f
        }
        val step = sizePx / 6f
        for (i in 1..5) {
            canvas.drawLine(i * step, 0f, i * step, sizePx.toFloat(), gridPaint)
            canvas.drawLine(0f, i * step, sizePx.toFloat(), i * step, gridPaint)
        }
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 220, 210, 175)
            strokeWidth = sizePx * 0.03f
        }
        canvas.drawLine(0f, sizePx * 0.65f, sizePx.toFloat(), sizePx * 0.35f, roadPaint)
        canvas.drawLine(sizePx * 0.3f, 0f, sizePx * 0.72f, sizePx.toFloat(), roadPaint)
    }

    private fun drawCenterRedPin(canvas: Canvas, cx: Float, tipY: Float, radius: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            cx - radius * 0.55f,
            tipY - radius * 0.15f,
            cx + radius * 0.55f,
            tipY + radius * 0.25f,
            shadowPaint
        )

        val circleCy = tipY - radius * 1.45f
        val pinPath = Path().apply {
            moveTo(cx, tipY)
            cubicTo(
                cx - radius * 0.9f, tipY - radius * 0.6f,
                cx - radius * 1.05f, circleCy + radius * 0.2f,
                cx - radius, circleCy
            )
            arcTo(
                cx - radius, circleCy - radius,
                cx + radius, circleCy + radius,
                180f, 180f, false
            )
            cubicTo(
                cx + radius * 1.05f, circleCy + radius * 0.2f,
                cx + radius * 0.9f, tipY - radius * 0.6f,
                cx, tipY
            )
            close()
        }

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 57, 53)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (radius * 0.14f).coerceAtLeast(1.5f)
        }
        canvas.drawPath(pinPath, pinPaint)
        canvas.drawPath(pinPath, borderPaint)

        val innerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 20, 20)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, circleCy, radius * 0.36f, innerDot)
    }

    private fun drawSatelliteBadge(canvas: Canvas, sizePx: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.115f
            isFakeBoldText = true
            setShadowLayer(4f, 0f, 1.5f, Color.BLACK)
        }
        canvas.drawText("Satellite", sizePx * 0.07f, sizePx * 0.93f, textPaint)
    }
}
