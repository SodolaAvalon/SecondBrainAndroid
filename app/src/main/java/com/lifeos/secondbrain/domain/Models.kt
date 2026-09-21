package com.lifeos.secondbrain.domain

enum class NoteType(val raw: String) {
    TASK("task"), IDEA("idea"), PLAN("plan"), WRITING("writing"), LEARNING("learning"),
    RAW("raw"), JOURNAL("journal"), DECISION("decision"), REFERENCE("reference"), PROJECT("project"), UNKNOWN("unknown");

    companion object {
        fun from(raw: String?): NoteType = entries.firstOrNull { it.raw == raw?.trim()?.lowercase() } ?: UNKNOWN
    }
}

data class LifeNote(
    val fileId: String,
    val name: String,
    val path: String?,
    val type: NoteType,
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
    val tags: List<String>,
    val modifiedTime: String?,
    val md5Checksum: String?
)

data class SyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val lastSuccessEpochMs: Long? = null,
    val error: String? = null
)
