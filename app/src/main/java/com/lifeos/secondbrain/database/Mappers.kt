package com.lifeos.secondbrain.database

import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.NoteType

fun LifeNote.toEntity() = NoteEntity(
    fileId, name, path, type.raw, status, title, summary, body, created, updated, due,
    project, priority, source, processed, tags.joinToString("\u001F"), modifiedTime, md5Checksum
)

fun NoteEntity.toDomain() = LifeNote(
    fileId = fileId,
    name = name,
    path = path,
    type = NoteType.from(type),
    status = status,
    title = title,
    summary = summary,
    body = body,
    created = created,
    updated = updated,
    due = due,
    project = project,
    priority = priority,
    source = source,
    processed = processed,
    tags = tagsEncoded.split("\u001F").filter { it.isNotBlank() },
    modifiedTime = modifiedTime,
    md5Checksum = md5Checksum
)
