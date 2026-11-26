package com.securegps.camera.data

data class VaultPhotoMeta(
    val id: String,
    val createdAtMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val cityHeader: String,
    val fullAddress: String,
    val note: String,
    val sha256Hex: String,
    val isMockLocation: Boolean,
    val fileSizeBytes: Long
)
