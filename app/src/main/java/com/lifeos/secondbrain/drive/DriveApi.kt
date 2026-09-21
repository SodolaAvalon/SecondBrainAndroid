package com.lifeos.secondbrain.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface AccessTokenProvider {
    suspend fun accessToken(): String
    fun invalidate() {}
}

class DriveApi(
    private val tokenProvider: AccessTokenProvider,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
) {
    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val BASE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }

    suspend fun findFolderByName(name: String): List<DriveFile> {
        val escaped = name.replace("'", "\\'")
        val q = "name = '$escaped' and mimeType = '$FOLDER_MIME' and trashed = false"
        return listFiles(q).files
    }

    suspend fun listChildren(folderId: String, pageToken: String? = null): FileListResponse {
        val q = "'$folderId' in parents and trashed = false"
        return listFiles(q, pageToken)
    }

    suspend fun getFile(fileId: String): DriveFile = get(
        "$BASE/files/$fileId?fields=id,name,mimeType,parents,modifiedTime,md5Checksum,trashed"
    )

    suspend fun listFiles(query: String, pageToken: String? = null): FileListResponse {
        val params = linkedMapOf(
            "q" to query,
            "spaces" to "drive",
            "pageSize" to "1000",
            "fields" to "nextPageToken,files(id,name,mimeType,parents,modifiedTime,md5Checksum,trashed)"
        )
        if (pageToken != null) params["pageToken"] = pageToken
        return get("$BASE/files?${params.queryString()}")
    }

    suspend fun downloadText(fileId: String): String = withAuthRetry {
        withContext(Dispatchers.IO) {
            val request = authorized(Request.Builder().url("$BASE/files/$fileId?alt=media")).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DriveException(response.code, response.body.string())
                response.body.string()
            }
        }
    }

    suspend fun updateText(fileId: String, content: String) = withAuthRetry {
        withContext(Dispatchers.IO) {
            val body = content.toRequestBody("text/markdown; charset=utf-8".toMediaType())
            val request = authorized(Request.Builder().url("$UPLOAD/files/$fileId?uploadType=media")).patch(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DriveException(response.code, response.body.string())
            }
        }
    }

    suspend fun createMarkdown(parentId: String, fileName: String, content: String): DriveFile = withAuthRetry {
        withContext(Dispatchers.IO) {
            val boundary = "secondbrain-${System.nanoTime()}"
            val metadata = json.encodeToString(CreateFileMetadata(fileName, listOf(parentId), "text/markdown"))
            val multipart = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata).append("\r\n")
                append("--$boundary\r\n")
                append("Content-Type: text/markdown; charset=UTF-8\r\n\r\n")
                append(content).append("\r\n")
                append("--$boundary--\r\n")
            }
            val request = authorized(
                Request.Builder().url("$UPLOAD/files?uploadType=multipart&fields=id,name,mimeType,parents,modifiedTime,md5Checksum,trashed")
            ).post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) throw DriveException(response.code, raw)
                json.decodeFromString<DriveFile>(raw)
            }
        }
    }

    suspend fun createFolder(parentId: String, name: String): DriveFile = withAuthRetry {
        withContext(Dispatchers.IO) {
            val metadata = json.encodeToString(CreateFileMetadata(name, listOf(parentId), FOLDER_MIME))
            val request = authorized(Request.Builder().url("$BASE/files?fields=id,name,mimeType,parents,modifiedTime,trashed"))
                .post(metadata.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) throw DriveException(response.code, raw)
                json.decodeFromString<DriveFile>(raw)
            }
        }
    }

    suspend fun startPageToken(): String = get<StartPageTokenResponse>("$BASE/changes/startPageToken").startPageToken

    suspend fun changes(pageToken: String): ChangesResponse {
        val params = linkedMapOf(
            "pageToken" to pageToken,
            "pageSize" to "1000",
            "spaces" to "drive",
            "fields" to "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,parents,modifiedTime,md5Checksum,trashed))"
        )
        return get("$BASE/changes?${params.queryString()}")
    }

    private suspend inline fun <reified T> get(url: String): T = withAuthRetry {
        withContext(Dispatchers.IO) {
            val request = authorized(Request.Builder().url(url)).get().build()
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) throw DriveException(response.code, raw)
                json.decodeFromString<T>(raw)
            }
        }
    }

    private suspend fun <T> withAuthRetry(block: suspend () -> T): T = try {
        block()
    } catch (error: DriveException) {
        if (error.statusCode != 401) throw error
        tokenProvider.invalidate()
        block()
    }

    private suspend fun authorized(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", "Bearer ${tokenProvider.accessToken()}")

    private fun Map<String, String>.queryString(): String = entries.joinToString("&") {
        "${it.key.url()}=${it.value.url()}"
    }

    private fun String.url() = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

@kotlinx.serialization.Serializable
private data class CreateFileMetadata(val name: String, val parents: List<String>, val mimeType: String)

class DriveException(val statusCode: Int, rawMessage: String) : Exception("Google Drive request failed ($statusCode): ${rawMessage.take(300)}")
