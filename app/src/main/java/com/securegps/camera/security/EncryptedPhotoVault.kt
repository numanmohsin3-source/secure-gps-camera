package com.securegps.camera.security

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.securegps.camera.data.GpsStampData
import com.securegps.camera.data.VaultPhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Stores every captured GPS photo in AES-256-GCM encrypted internal storage using
 * Android Keystore's MasterKey. Photos never enter the public gallery unless explicitly
 * exported or shared by the authenticated user.
 */
class EncryptedPhotoVault(private val context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val photosDir = File(vaultDir, "photos").apply { mkdirs() }
    private val thumbsDir = File(vaultDir, "thumbs").apply { mkdirs() }
    private val indexFile = File(vaultDir, "index.enc")
    private val shareCacheDir = File(context.cacheDir, "shared_exports").apply { mkdirs() }

    private val indexMutex = Mutex()

    suspend fun saveCapturedPhoto(bitmap: Bitmap, stamp: GpsStampData): VaultPhotoMeta =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val fullBytes = ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                baos.toByteArray()
            }
            val sha256 = sha256Hex(fullBytes)

            val thumbBitmap = scaleDown(bitmap, maxDim = 420)
            val thumbBytes = ByteArrayOutputStream().use { baos ->
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                baos.toByteArray()
            }
            if (thumbBitmap !== bitmap) {
                thumbBitmap.recycle()
            }

            writeEncryptedBytes(File(photosDir, "$id.enc"), fullBytes)
            writeEncryptedBytes(File(thumbsDir, "$id.enc"), thumbBytes)

            val meta = VaultPhotoMeta(
                id = id,
                createdAtMillis = stamp.timestampMillis,
                latitude = stamp.latitude,
                longitude = stamp.longitude,
                cityHeader = stamp.headerLine(),
                fullAddress = stamp.fullAddress,
                note = stamp.note,
                sha256Hex = sha256,
                isMockLocation = stamp.isMockLocation,
                fileSizeBytes = fullBytes.size.toLong()
            )

            indexMutex.withLock {
                val current = readIndexInternal().toMutableList()
                current.add(0, meta)
                writeIndexInternal(current)
            }
            meta
        }

    suspend fun listPhotos(): List<VaultPhotoMeta> = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            readIndexInternal()
        }
    }

    suspend fun loadThumbnail(id: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(thumbsDir, "$id.enc")
        if (!file.exists()) return@withContext null
        val bytes = readEncryptedBytes(file) ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    suspend fun loadFullPhoto(id: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(photosDir, "$id.enc")
        if (!file.exists()) return@withContext null
        val bytes = readEncryptedBytes(file) ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    suspend fun verifyIntegrity(id: String): Boolean = withContext(Dispatchers.IO) {
        val meta = indexMutex.withLock { readIndexInternal().firstOrNull { it.id == id } }
            ?: return@withContext false
        val file = File(photosDir, "$id.enc")
        if (!file.exists()) return@withContext false
        val bytes = readEncryptedBytes(file) ?: return@withContext false
        sha256Hex(bytes).equals(meta.sha256Hex, ignoreCase = true)
    }

    suspend fun exportToPublicGallery(id: String): Uri? = withContext(Dispatchers.IO) {
        val file = File(photosDir, "$id.enc")
        if (!file.exists()) return@withContext null
        val bytes = readEncryptedBytes(file) ?: return@withContext null

        val fileName = "GPS_CAM_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPSMapCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
            out.flush()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }

    suspend fun prepareTempShareUri(id: String): Uri? = withContext(Dispatchers.IO) {
        cleanupShareCache()
        val file = File(photosDir, "$id.enc")
        if (!file.exists()) return@withContext null
        val bytes = readEncryptedBytes(file) ?: return@withContext null
        val exportFile = File(shareCacheDir, "share_${id.take(8)}.jpg")
        exportFile.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
    }

    fun cleanupShareCache() {
        shareCacheDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun deletePhoto(id: String) = withContext(Dispatchers.IO) {
        File(photosDir, "$id.enc").delete()
        File(thumbsDir, "$id.enc").delete()
        indexMutex.withLock {
            val updated = readIndexInternal().filterNot { it.id == id }
            writeIndexInternal(updated)
        }
    }

    suspend fun wipeVault() = withContext(Dispatchers.IO) {
        indexMutex.withLock {
            photosDir.listFiles()?.forEach { it.delete() }
            thumbsDir.listFiles()?.forEach { it.delete() }
            if (indexFile.exists()) indexFile.delete()
            cleanupShareCache()
        }
    }

    private fun readIndexInternal(): List<VaultPhotoMeta> {
        if (!indexFile.exists()) return emptyList()
        val bytes = readEncryptedBytes(indexFile) ?: return emptyList()
        val arr = JSONArray(String(bytes, Charsets.UTF_8))
        val list = ArrayList<VaultPhotoMeta>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                VaultPhotoMeta(
                    id = obj.getString("id"),
                    createdAtMillis = obj.getLong("createdAtMillis"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    cityHeader = obj.optString("cityHeader", ""),
                    fullAddress = obj.optString("fullAddress", ""),
                    note = obj.optString("note", ""),
                    sha256Hex = obj.optString("sha256Hex", ""),
                    isMockLocation = obj.optBoolean("isMockLocation", false),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L)
                )
            )
        }
        return list
    }

    private fun writeIndexInternal(items: List<VaultPhotoMeta>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("createdAtMillis", item.createdAtMillis)
                    put("latitude", item.latitude)
                    put("longitude", item.longitude)
                    put("cityHeader", item.cityHeader)
                    put("fullAddress", item.fullAddress)
                    put("note", item.note)
                    put("sha256Hex", item.sha256Hex)
                    put("isMockLocation", item.isMockLocation)
                    put("fileSizeBytes", item.fileSizeBytes)
                }
            )
        }
        writeEncryptedBytes(indexFile, arr.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeEncryptedBytes(target: File, data: ByteArray) {
        if (target.exists()) target.delete()
        val encryptedFile = EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encryptedFile.openFileOutput().use { out ->
            out.write(data)
            out.flush()
        }
    }

    private fun readEncryptedBytes(source: File): ByteArray? {
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                source,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileInput().use { input ->
                input.readBytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
