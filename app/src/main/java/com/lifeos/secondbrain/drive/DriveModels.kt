package com.lifeos.secondbrain.drive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val parents: List<String> = emptyList(),
    val modifiedTime: String? = null,
    val md5Checksum: String? = null,
    val trashed: Boolean = false
)

@Serializable
data class FileListResponse(
    val nextPageToken: String? = null,
    val files: List<DriveFile> = emptyList()
)

@Serializable
data class StartPageTokenResponse(val startPageToken: String)

@Serializable
data class ChangesResponse(
    val nextPageToken: String? = null,
    val newStartPageToken: String? = null,
    val changes: List<DriveChange> = emptyList()
)

@Serializable
data class DriveChange(
    val fileId: String,
    val removed: Boolean = false,
    val file: DriveFile? = null
)

@Serializable
data class DriveErrorEnvelope(val error: DriveError? = null)

@Serializable
data class DriveError(val code: Int? = null, val message: String? = null)
