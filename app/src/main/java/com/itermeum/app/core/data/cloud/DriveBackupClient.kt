package com.itermeum.app.core.data.cloud

import com.itermeum.app.core.data.auth.AuthRepository
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class RemoteBackup(val bytes: ByteArray, val fileId: String, val modifiedTimeMs: Long)
data class RemoteMetadata(val fileId: String, val modifiedTimeMs: Long)

/**
 * Drive v3 AppData: one file `itermeum-backup-v1.json.enc`. Files whose
 * name does not start with `itermeum-` are ignored (section 5.17), so an
 * old Prime Mover snapshot in the same folder is never read.
 */
@Singleton
class DriveBackupClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val auth: AuthRepository,
    @Named(DRIVE_BASE_URL_QUALIFIER) private val baseUrl: String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchRemote(): RemoteBackup? = withContext(Dispatchers.IO) {
        val meta = listBackup() ?: return@withContext null
        RemoteBackup(download(meta.fileId), meta.fileId, meta.modifiedTimeMs)
    }

    suspend fun uploadOrReplace(bytes: ByteArray): RemoteMetadata = withContext(Dispatchers.IO) {
        val existing = listBackup()
        val token = auth.accessToken()
        val metadataJson = if (existing == null) """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}""" else """{"name":"$BACKUP_FILE_NAME"}"""
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        val url = (if (existing == null) "$baseUrl/upload/drive/v3/files" else "$baseUrl/upload/drive/v3/files/${existing.fileId}").toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart").addQueryParameter("fields", "id,modifiedTime").build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").let { if (existing == null) it.post(body) else it.patch(body) }.build()
        httpClient.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Drive upload failed: HTTP ${r.code}")
            val node = json.parseToJsonElement(r.body!!.string()).jsonObject
            RemoteMetadata(node["id"]!!.jsonPrimitive.content, parseTime(node["modifiedTime"]?.jsonPrimitive?.content))
        }
    }

    private suspend fun listBackup(): RemoteMetadata? {
        val token = auth.accessToken()
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "trashed = false")
            .addQueryParameter("fields", "files(id,name,modifiedTime)")
            .build()
        httpClient.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Drive list failed: HTTP ${r.code}")
            val files = json.parseToJsonElement(r.body!!.string()).jsonObject["files"]?.jsonArray ?: return null
            val ours = files.map { it.jsonObject }.filter { (it["name"]?.jsonPrimitive?.content ?: "").startsWith(FILE_PREFIX) }
            val first = ours.firstOrNull { it["name"]?.jsonPrimitive?.content == BACKUP_FILE_NAME } ?: ours.firstOrNull() ?: return null
            return RemoteMetadata(first["id"]!!.jsonPrimitive.content, parseTime(first["modifiedTime"]?.jsonPrimitive?.content))
        }
    }

    private suspend fun download(fileId: String): ByteArray {
        val token = auth.accessToken()
        val url = "$baseUrl/drive/v3/files/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        httpClient.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Drive download failed: HTTP ${r.code}")
            return r.body!!.bytes()
        }
    }

    private fun parseTime(rfc3339: String?): Long = rfc3339?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    companion object {
        const val FILE_PREFIX = "itermeum-"
        const val BACKUP_FILE_NAME = "itermeum-backup-v1.json.enc"
        const val DRIVE_BASE_URL_QUALIFIER = "drive_base_url"
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"
    }
}
