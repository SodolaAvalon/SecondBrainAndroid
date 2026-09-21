package com.lifeos.secondbrain.data.repository

import com.lifeos.secondbrain.data.search.SearchIndex
import com.lifeos.secondbrain.database.NoteDao
import com.lifeos.secondbrain.database.toDomain
import com.lifeos.secondbrain.domain.LifeNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val dao: NoteDao) {
    fun all(): Flow<List<LifeNote>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    fun tasks(): Flow<List<LifeNote>> = dao.observeActiveTasks().map { list -> list.map { it.toDomain() } }
    fun allTasks(): Flow<List<LifeNote>> = dao.observeByType("task").map { list -> list.map { it.toDomain() } }
    fun ideas(): Flow<List<LifeNote>> = dao.observeByType("idea").map { list -> list.map { it.toDomain() } }
    fun plans(): Flow<List<LifeNote>> = dao.observeByType("plan").map { list -> list.map { it.toDomain() } }
    fun writings(): Flow<List<LifeNote>> = dao.observeByType("writing").map { list -> list.map { it.toDomain() } }
    fun learnings(): Flow<List<LifeNote>> = dao.observeByType("learning").map { list -> list.map { it.toDomain() } }
    fun projects(): Flow<List<LifeNote>> = dao.observeByType("project").map { list -> list.map { it.toDomain() } }
    fun raw(): Flow<List<LifeNote>> = dao.observeByType("raw").map { list -> list.map { it.toDomain() } }
    suspend fun search(query: String): List<LifeNote> {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return emptyList()
        val match = SearchIndex.matchQuery(cleaned)
        if (match.isBlank()) return emptyList()
        return dao.search(match).map { it.toDomain() }
    }
}
