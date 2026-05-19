package com.avangard.app.core.data.cloud

import com.avangard.app.core.data.auth.AuthRepository
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin OkHttp wrapper around Drive v3 + upload endpoint for our single
 * AppData backup file. Single-purpose: list-or-create, download bytes,
 * upload bytes. No batching, no resumable uploads — the snapshot is small
 * (~400 KB worst case after 2 years) so a multipart upload in one round-trip
 * is fine.
 *
 * Token sourcing routes through [AuthRepository.accessToken] on every call:
 * GoogleAuthUtil caches and refreshes the token internally, so re-requesting
 * is cheap and resilient to silent re-issuance.
 */
@Singleton
class DriveBackupClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val auth: AuthRepository,
    @Named(DRIVE_BASE_URL_QUALIFIER) private val baseUrl: String,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Returns the single backup file if it lives in AppData, or null when
     * the folder is empty. Throws [IOException] on transport / 5xx errors.
     */
    suspend fun fetchRemote(): RemoteBackup? = withContext(Dispatchers.IO) {
        val metadata = listBackupMetadata() ?: return@withContext null
        val bytes = downloadBytes(metadata.fileId)
        RemoteBackup(
            bytes = bytes,
            fileId = metadata.fileId,
            modifiedTimeMs = metadata.modifiedTimeMs,
        )
    }

    /**
     * Multipart upload to AppData. If a file with the canonical name already
     * exists it gets PATCH-updated in place; otherwise a fresh file is
     * created. Returns the new metadata so the caller can persist the
     * `modifiedTimeMs` as a sync marker.
     */
    suspend fun uploadOrReplace(bytes: ByteArray): RemoteMetadata =
        withContext(Dispatchers.IO) {
            val existing = listBackupMetadata()
            val token = auth.accessToken()
            val multipart = buildMultipart(bytes, includeMetadata = existing == null)
            val request = if (existing == null) {
                Request.Builder()
                    .url("$baseUrl/upload/drive/v3/files".toHttpUrl().newBuilder()
                        .addQueryParameter("uploadType", "multipart")
                        .addQueryParameter("fields", "id,modifiedTime")
                        .build())
                    .header("Authorization", "Bearer $token")
                    .post(multipart)
                    .build()
            } else {
                Request.Builder()
                    .url("$baseUrl/upload/drive/v3/files/${existing.fileId}".toHttpUrl().newBuilder()
                        .addQueryParameter("uploadType", "multipart")
                        .addQueryParameter("fields", "id,modifiedTime")
                        .build())
                    .header("Authorization", "Bearer $token")
                    .patch(multipart)
                    .build()
            }
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Drive upload failed: HTTP ${response.code}")
                }
                val node = json.parseToJsonElement(response.body!!.string()).jsonObject
                RemoteMetadata(
                    fileId = node["id"]!!.jsonPrimitive.content,
                    modifiedTimeMs = parseModifiedTime(node["modifiedTime"]?.jsonPrimitive?.content),
                )
            }
        }

    private data class FileMeta(val fileId: String, val modifiedTimeMs: Long)

    private suspend fun listBackupMetadata(): FileMeta? {
        val token = auth.accessToken()
        val url = "$baseUrl/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$BACKUP_FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,modifiedTime)")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive list failed: HTTP ${response.code}")
            }
            val node = json.parseToJsonElement(response.body!!.string()).jsonObject
            val files = node["files"]?.jsonArray ?: return null
            if (files.isEmpty()) return null
            val first = files.first().jsonObject
            return FileMeta(
                fileId = first["id"]!!.jsonPrimitive.content,
                modifiedTimeMs = parseModifiedTime(first["modifiedTime"]?.jsonPrimitive?.content),
            )
        }
    }

    private suspend fun downloadBytes(fileId: String): ByteArray {
        val token = auth.accessToken()
        val url = "$baseUrl/drive/v3/files/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Drive download failed: HTTP ${response.code}")
            }
            return response.body!!.bytes()
        }
    }

    private fun buildMultipart(bytes: ByteArray, includeMetadata: Boolean): RequestBody {
        // Both CREATE and UPDATE accept the same multipart shape; the
        // metadata part is required on CREATE (so Drive learns the name +
        // parent) and harmless on UPDATE (Drive just patches fields).
        val metadataJson = if (includeMetadata) {
            """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        } else {
            """{"name":"$BACKUP_FILE_NAME"}"""
        }
        return MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(
                Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                metadataJson.toRequestBody(),
            )
            .addPart(
                Headers.headersOf("Content-Type", "application/json"),
                bytes.toRequestBody("application/json".toMediaType()),
            )
            .build()
    }

    private fun parseModifiedTime(rfc3339: String?): Long {
        if (rfc3339 == null) return 0L
        return runCatching { Instant.parse(rfc3339).toEpochMilli() }.getOrElse { 0L }
    }

    companion object {
        const val BACKUP_FILE_NAME = "avangard-backup.json"
        const val DRIVE_BASE_URL_QUALIFIER = "drive_base_url"
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"
    }
}

