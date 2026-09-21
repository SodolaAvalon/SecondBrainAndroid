package com.lifeos.secondbrain.database

import com.lifeos.secondbrain.data.search.SearchIndex
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY COALESCE(updated, created, modifiedTime) DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = :type ORDER BY COALESCE(due, updated, created) ASC")
    fun observeByType(type: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = 'task' AND (status IS NULL OR status != 'done') ORDER BY CASE WHEN due IS NULL THEN 1 ELSE 0 END, due ASC")
    fun observeActiveTasks(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE fileId = :fileId LIMIT 1")
    suspend fun get(fileId: String): NoteEntity?

    @Query("SELECT fileId FROM notes")
    suspend fun allIds(): List<String>

    @Query(
        """
        SELECT notes.* FROM notes
        INNER JOIN note_fts ON notes.fileId = note_fts.fileId
        WHERE note_fts MATCH :matchQuery
        ORDER BY COALESCE(notes.updated, notes.created, notes.modifiedTime) DESC
        LIMIT :limit
        """
    )
    suspend fun search(matchQuery: String, limit: Int = 100): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert
    suspend fun insertSearchIndex(note: NoteFtsEntity)

    @Query("DELETE FROM note_fts WHERE fileId = :fileId")
    suspend fun deleteSearchIndex(fileId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("DELETE FROM notes WHERE fileId = :fileId")
    suspend fun deleteById(fileId: String)

    @Query("DELETE FROM notes")
    suspend fun clear()

    @Query("DELETE FROM note_fts")
    suspend fun clearSearchIndex()

    @Transaction
    suspend fun upsertIndexed(note: NoteEntity) {
        upsert(note)
        deleteSearchIndex(note.fileId)
        insertSearchIndex(
            NoteFtsEntity(
                fileId = note.fileId,
                tokens = SearchIndex.tokens(note.title, note.body, note.tagsEncoded)
            )
        )
    }

    @Transaction
    suspend fun deleteIndexed(fileId: String) {
        deleteById(fileId)
        deleteSearchIndex(fileId)
    }

    @Transaction
    suspend fun clearIndexed() {
        clear()
        clearSearchIndex()
    }
}

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations ORDER BY createdEpochMs ASC")
    suspend fun all(): List<PendingOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: PendingOperationEntity)

    @Delete
    suspend fun delete(operation: PendingOperationEntity)

    @Query("SELECT COUNT(*) FROM pending_operations")
    suspend fun count(): Int
}

@Dao
interface SyncMetaDao {
    @Query("SELECT value FROM sync_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(meta: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE `key` = :key")
    suspend fun delete(key: String)
}
