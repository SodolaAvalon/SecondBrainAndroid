package com.lifeos.secondbrain.data.markdown

import com.lifeos.secondbrain.domain.LifeNote
import com.lifeos.secondbrain.domain.NoteType

object NoteMapper {
    fun fromMarkdown(
        fileId: String,
        name: String,
        path: String?,
        markdown: String,
        modifiedTime: String?,
        md5Checksum: String?
    ): LifeNote {
        val document = FrontmatterParser.parse(markdown)
        val fallbackTitle = name.removeSuffix(".md")
        val title = document.scalar("title")?.takeIf { it.isNotBlank() }
            ?: document.body.lineSequence().firstOrNull { it.trimStart().startsWith("# ") }
                ?.trim()?.removePrefix("# ")?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackTitle
        val summary = document.body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
            ?.take(180)

        return LifeNote(
            fileId = fileId,
            name = name,
            path = path,
            type = NoteType.from(document.scalar("type")),
            status = document.scalar("status"),
            title = title,
            summary = summary,
            body = document.body,
            created = document.scalar("created"),
            updated = document.scalar("updated"),
            due = document.scalar("due"),
            project = document.scalar("project"),
            priority = document.scalar("priority"),
            source = document.scalar("source"),
            processed = document.scalar("processed")?.toBooleanStrictOrNull(),
            tags = document.list("tags"),
            modifiedTime = modifiedTime,
            md5Checksum = md5Checksum
        )
    }
}
