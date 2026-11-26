package com.securegps.camera

import com.securegps.camera.data.GpsStampData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsStampDataTest {

    @Test
    fun countryCodeConvertsToFlagEmoji() {
        assertEquals("🇵🇰", GpsStampData.countryCodeToFlagEmoji("PK"))
        assertEquals("🇺🇸", GpsStampData.countryCodeToFlagEmoji("us"))
        assertEquals("", GpsStampData.countryCodeToFlagEmoji(""))
    }

    @Test
    fun formatsHeaderAndCoordinatesLikeReference() {
        val stamp = GpsStampData(
            latitude = 31.447002,
            longitude = 74.308973,
            city = "Lahore",
            state = "Punjab",
            country = "Pakistan",
            countryCode = "PK",
            fullAddress = "731, Township Block 2 Sector D1 Lahore, 54770, Pakistan",
            note = "Captured by GPS Map Camera",
            hasLocationFix = true
        )

        assertEquals("Lahore, Punjab, Pakistan 🇵🇰", stamp.headerLine())
        assertEquals("Lat 31.447002° Long 74.308973°", stamp.coordinatesLine())
        assertEquals("Note : Captured by GPS Map Camera", stamp.noteLine())
        assertTrue(stamp.formattedDateTimeLine().contains("GMT"))
    }
}
