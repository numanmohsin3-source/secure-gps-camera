package com.securegps.camera.data

import android.graphics.Bitmap
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class GpsStampData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val city: String = "Locating...",
    val state: String = "",
    val country: String = "",
    val countryCode: String = "",
    val fullAddress: String = "Waiting for GPS signal...",
    val note: String = "Captured by GPS Map Camera",
    val timestampMillis: Long = System.currentTimeMillis(),
    val isMockLocation: Boolean = false,
    val hasLocationFix: Boolean = false,
    val mapSnapshot: Bitmap? = null
) {
    fun headerLine(): String {
        val parts = listOf(city, state, country).filter { it.isNotBlank() }
        val base = if (parts.isEmpty()) "Acquiring GPS Location..." else parts.joinToString(", ")
        val flag = countryCodeToFlagEmoji(countryCode)
        return if (flag.isNotBlank()) "$base $flag" else base
    }

    fun coordinatesLine(): String {
        return if (hasLocationFix) {
            String.format(Locale.US, "Lat %.6f° Long %.6f°", latitude, longitude)
        } else {
            "Lat --.------° Long --.------°"
        }
    }

    fun formattedDateTimeLine(): String {
        val zdt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timestampMillis),
            java.time.ZoneId.systemDefault()
        )
        val datePart = zdt.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy hh:mm a", Locale.ENGLISH))
        val offsetPart = zdt.format(DateTimeFormatter.ofPattern("XXX", Locale.ENGLISH))
        val normalizedOffset = if (offsetPart == "Z") "+00:00" else offsetPart
        return "$datePart GMT $normalizedOffset"
    }

    fun noteLine(): String {
        val clean = note.ifBlank { "Captured by GPS Map Camera" }
        return "Note : $clean"
    }

    companion object {
        fun countryCodeToFlagEmoji(code: String): String {
            if (code.length != 2) return ""
            val upper = code.uppercase(Locale.US)
            if (upper[0] !in 'A'..'Z' || upper[1] !in 'A'..'Z') return ""
            val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }
    }
}
