package com.lifeos.secondbrain.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val fileId: String,
    val name: String,
    val path: String?,
    val type: String,
    val status: String?,
    val title: String,
    val summary: String?,
    val body: String,
    val created: String?,
    val updated: String?,
    val due: String?,
    val project: String?,
    val priority: String?,
    val source: String?,
    val processed: Boolean?,
    val tagsEncoded: String,
    val modifiedTime: String?,
    val md5Checksum: String?
)

/**
 * Dedicated full-text index. It intentionally has no rowid property: SQLite owns that key while
 * the stable Drive file id is stored as searchable text and used to join back to [NoteEntity].
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(
    val fileId: String,
    val tokens: String
)

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val fileId: String?,
    val targetFolderId: String?,
    val targetFolderName: String?,
    val fileName: String?,
    val payload: String,
    val createdEpochMs: Long,
    val attempts: Int = 0,
    val lastError: String? = null
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String
)
