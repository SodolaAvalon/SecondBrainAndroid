package com.lifeos.secondbrain.database

import androidx.room.ColumnInfo
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
    val md5Checksum: String?,
    /** Recurring tasks: date the series becomes available. Added in schema v4. */
    val start: String? = null,
    /** Recurring tasks: cadence. Named `repeatCadence` because `repeat` is a Kotlin keyword. */
    @ColumnInfo(name = "repeat") val repeatCadence: String? = null,
    /**
     * Recurring tasks: date of the most recent completion. Added in schema v4.
     *
     * Pinned to the snake_case frontmatter key on purpose. Kotlin would default the column name to
     * `lastCompleted`, which would then disagree with both the frontmatter key and the migration SQL,
     * and Room validates the migrated schema against the entity — a mismatch fails at open time.
     */
    @ColumnInfo(name = "last_completed") val lastCompleted: String? = null
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
