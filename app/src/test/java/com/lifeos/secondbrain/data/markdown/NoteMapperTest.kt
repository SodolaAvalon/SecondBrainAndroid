package com.lifeos.secondbrain.data.markdown

import com.lifeos.secondbrain.domain.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoteMapperTest {
    @Test
    fun classifiesByFrontmatterInsteadOfFolderPath() {
        val note = NoteMapper.fromMarkdown(
            fileId = "drive-1",
            name = "随便的文件名.md",
            path = "完全/不同/目录/随便的文件名.md",
            markdown = """---
type: task
status: active
tags: [school, chemistry]
---
# 化学作业
做第 3 章习题
""",
            modifiedTime = "2026-09-19T10:00:00Z",
            md5Checksum = "abc"
        )

        assertEquals(NoteType.TASK, note.type)
        assertEquals("化学作业", note.title)
        assertEquals("active", note.status)
    }

    @Test
    fun damagedFrontmatterDoesNotCrashIndexing() {
        val note = NoteMapper.fromMarkdown(
            fileId = "drive-2",
            name = "坏掉的笔记.md",
            path = null,
            markdown = "---\ntype: task\n没有结束分隔线",
            modifiedTime = null,
            md5Checksum = null
        )

        assertEquals(NoteType.UNKNOWN, note.type)
        assertEquals("坏掉的笔记", note.title)
        assertFalse(note.body.isBlank())
    }
}
