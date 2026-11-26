package com.securegps.camera.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.location.LocationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.securegps.camera.data.GpsStampData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

class LocationRepository(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val mapTileFetcher = MapTileFetcher()

    private var lastGeocodedLat: Double = Double.NaN
    private var lastGeocodedLng: Double = Double.NaN
    private var cachedAddress: ResolvedAddress? = null

    private data class ResolvedAddress(
        val city: String,
        val state: String,
        val country: String,
        val countryCode: String,
        val fullAddress: String
    )

    @SuppressLint("MissingPermission")
    fun observeGpsData(scope: CoroutineScope, customNoteProvider: () -> String): Flow<GpsStampData> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()

        suspend fun processLocation(location: Location) {
            val isMock = LocationCompat.isMock(location)
            val lat = location.latitude
            val lng = location.longitude

            val shouldReGeocode = cachedAddress == null ||
                lastGeocodedLat.isNaN() ||
                abs(lat - lastGeocodedLat) > 0.00025 ||
                abs(lng - lastGeocodedLng) > 0.00025

            val addr = if (shouldReGeocode) {
                val resolved = reverseGeocode(lat, lng)
                if (resolved != null) {
                    cachedAddress = resolved
                    lastGeocodedLat = lat
                    lastGeocodedLng = lng
                }
                resolved ?: cachedAddress
            } else {
                cachedAddress
            }

            val mapBitmap = withContext(Dispatchers.IO) {
                mapTileFetcher.fetchCenteredSatelliteThumbnail(lat, lng, sizePx = 280)
            }

            trySend(
                GpsStampData(
                    latitude = lat,
                    longitude = lng,
                    accuracyMeters = location.accuracy,
                    altitudeMeters = location.altitude,
                    city = addr?.city ?: String.format(Locale.US, "%.4f, %.4f", lat, lng),
                    state = addr?.state ?: "",
                    country = addr?.country ?: "",
                    countryCode = addr?.countryCode ?: "",
                    fullAddress = addr?.fullAddress ?: "Coordinates locked (offline address unavailable)",
                    note = customNoteProvider(),
                    timestampMillis = System.currentTimeMillis(),
                    isMockLocation = isMock,
                    hasLocationFix = true,
                    mapSnapshot = mapBitmap
                )
            )
        }

        // Seed with last known location immediately for instant UI responsiveness
        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                scope.launch { processLocation(lastLoc) }
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                scope.launch { processLocation(latest) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): ResolvedAddress? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.ENGLISH)

        val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                try {
                    geocoder.getFromLocation(lat, lng, 1)
                } catch (_: Exception) {
                    null
                }
            }
        }

        val a = addresses?.firstOrNull() ?: return null
        val city = a.locality ?: a.subAdminArea ?: a.featureName ?: ""
        val state = a.adminArea ?: ""
        val country = a.countryName ?: ""
        val countryCode = a.countryCode ?: ""

        val rawLine = buildString {
            val line0 = a.getAddressLine(0)
            if (!line0.isNullOrBlank()) {
                append(line0)
            } else {
                val parts = listOfNotNull(
                    a.subThoroughfare,
                    a.thoroughfare,
                    a.subLocality,
                    a.locality,
                    a.postalCode,
                    a.adminArea,
                    a.countryName
                ).filter { it.isNotBlank() }
                append(parts.joinToString(", "))
            }
        }

        return ResolvedAddress(
            city = city,
            state = state,
            country = country,
            countryCode = countryCode,
            fullAddress = rawLine.ifBlank { "$city, $state, $country" }
        )
    }
}
